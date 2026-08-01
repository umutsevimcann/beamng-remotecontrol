package com.beamng.remotecontrol.network

import android.os.Build
import android.util.Log
import com.beamng.remotecontrol.protocol.Ports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.channels.DatagramChannel

/**
 * Coroutine-based discovery handshake with the game
 * (verified against remoteController.lua): broadcasts
 * "beamng|<deviceName>|<code>" to port 4444 and waits for the game's
 * "beamng|<code>" reply on port 4445.
 */
object UdpDiscovery {

    sealed class Result {
        /** [code] is set when discovered by sweep; null when the caller already knew it. */
        data class Connected(val host: InetAddress, val code: String? = null) : Result()
        data class Failed(val message: String) : Result()
    }

    private const val RECEIVE_TIMEOUT_MS = 250
    private const val MAX_TRIES = 10
    private const val RETRY_DELAY_MS = 1000L

    /** The game picks its code as random(10000, 99999) — a fixed 5-digit space. */
    private val CODE_RANGE = 10000..99999
    private val REPLY_CODE = Regex("^beamng\\|(\\d{5})$")

    /**
     * Discovery/heartbeat payload: "beamng|<deviceName>|<code>". Also sent
     * periodically by the drive screen so the game re-registers the virtual
     * device after its 10s idle timeout (mod-free auto-reconnect).
     */
    fun buildHello(securityCode: String): ByteArray {
        val safeName = deviceName().replace(Regex("[|#\\n\\r]"), "_")
        return "beamng|$safeName|$securityCode".toByteArray()
    }

    /** Runs the handshake on IO; cancellable via normal coroutine cancellation. */
    suspend fun discover(
        broadcastAddress: InetAddress,
        localIp: String,
        securityCode: String
    ): Result = withContext(Dispatchers.IO) {
        val buffer = buildHello(securityCode)

        var socketS: DatagramSocket? = null
        var channel: DatagramChannel? = null
        try {
            socketS = DatagramSocket()
            channel = DatagramChannel.open()
            val socketR = channel.socket()
            socketR.reuseAddress = true
            socketR.bind(InetSocketAddress(localIp, Ports.APP))
            socketR.soTimeout = RECEIVE_TIMEOUT_MS
            val waitingFor = "beamng|$securityCode"
            val receiveBuf = ByteArray(32)
            val packetR = DatagramPacket(receiveBuf, receiveBuf.size)
            val packetS = DatagramPacket(buffer, buffer.size, broadcastAddress, Ports.GAME)

            var tries = 0
            while (true) {
                ensureActive()
                try {
                    socketS.send(packetS)
                    socketR.receive(packetR)
                } catch (e: IOException) {
                    if (++tries > MAX_TRIES) {
                        return@withContext Result.Failed("Connection timeout.")
                    }
                    Thread.sleep(RETRY_DELAY_MS)
                    continue
                }
                val message = String(receiveBuf, 0, packetR.length)
                if (Log.isLoggable("BeamNG", Log.DEBUG)) {
                    Log.i("UdpDiscovery", "Received: $message / waiting for: $waitingFor")
                }
                if (message == waitingFor) {
                    return@withContext Result.Connected(packetR.address)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Result.Failed("unreachable")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Failed(e.toString())
        } finally {
            try {
                channel?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            socketS?.close()
        }
    }

    /**
     * Camera-free auto-connect. The game rejects any discovery packet whose code
     * doesn't match its secret 5-digit code, and echoes the code back only on a
     * match (remoteController.lua:104-121). So we broadcast "beamng|<name>|<code>"
     * across the whole 10000-99999 space; the game's single "beamng|<code>" reply
     * reveals both its address and the matching code, and connects us in one shot.
     *
     * Rate-limited (~5k packets/s) and drained after every batch so an in-flight
     * reply is never missed. [onProgress] reports (codesTried, total) for the UI.
     */
    suspend fun sweep(
        broadcastAddress: InetAddress,
        localIp: String,
        onProgress: (tried: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val name = deviceName().replace(Regex("[|#\\n\\r]"), "_")
        val total = CODE_RANGE.count()
        val batchSize = 300

        var sendSocket: DatagramSocket? = null
        var recvChannel: DatagramChannel? = null
        try {
            sendSocket = DatagramSocket().apply { broadcast = true }
            recvChannel = DatagramChannel.open()
            val recv = recvChannel.socket()
            recv.reuseAddress = true
            recv.bind(InetSocketAddress(localIp, Ports.APP))
            recv.soTimeout = 20

            val replyBuf = ByteArray(32)
            val replyPacket = DatagramPacket(replyBuf, replyBuf.size)

            // Drains everything currently queued on the reply socket; returns a
            // Connected result the moment the game answers, else null.
            fun drainReplies(): Result.Connected? {
                while (true) {
                    try {
                        recv.receive(replyPacket)
                    } catch (e: SocketTimeoutException) {
                        return null
                    }
                    val msg = String(replyBuf, 0, replyPacket.length)
                    val found = REPLY_CODE.find(msg)?.groupValues?.get(1)
                    if (found != null) return Result.Connected(replyPacket.address, found)
                }
            }

            // Two passes: UDP is lossy, and our own flood can drop the one reply
            // that matters, so a miss on pass 1 gets a second chance.
            repeat(2) {
                var tried = 0
                for (code in CODE_RANGE) {
                    ensureActive()
                    val payload = "beamng|$name|$code".toByteArray()
                    try {
                        sendSocket.send(
                            DatagramPacket(payload, payload.size, broadcastAddress, Ports.GAME),
                        )
                    } catch (e: IOException) {
                        // A single dropped send is harmless — the sweep continues.
                    }
                    tried++
                    if (tried % batchSize == 0) {
                        drainReplies()?.let { return@withContext it }
                        onProgress(tried, total)
                        delay(40)
                    }
                }
                // Final grace window for stragglers before the next pass / giving up.
                repeat(50) {
                    ensureActive()
                    drainReplies()?.let { return@withContext it }
                    delay(20)
                }
            }
            Result.Failed("BeamNG.drive not found — is remote control enabled in the game?")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Failed(e.toString())
        } finally {
            try {
                recvChannel?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            sendSocket?.close()
        }
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val name = if (model.startsWith(manufacturer)) model else "$manufacturer $model"
        return name.replaceFirstChar { it.uppercaseChar() }
    }
}
