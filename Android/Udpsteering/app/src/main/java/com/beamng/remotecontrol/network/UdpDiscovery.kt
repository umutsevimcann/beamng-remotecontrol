package com.beamng.remotecontrol.network

import android.os.Build
import android.util.Log
import com.beamng.remotecontrol.protocol.Ports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

/**
 * Coroutine-based discovery handshake with the game
 * (verified against remoteController.lua): broadcasts
 * "beamng|<deviceName>|<code>" to port 4444 and waits for the game's
 * "beamng|<code>" reply on port 4445.
 */
object UdpDiscovery {

    sealed class Result {
        data class Connected(val host: InetAddress) : Result()
        data class Failed(val message: String) : Result()
    }

    private const val RECEIVE_TIMEOUT_MS = 250
    private const val MAX_TRIES = 10
    private const val RETRY_DELAY_MS = 1000L

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

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val name = if (model.startsWith(manufacturer)) model else "$manufacturer $model"
        return name.replaceFirstChar { it.uppercaseChar() }
    }
}
