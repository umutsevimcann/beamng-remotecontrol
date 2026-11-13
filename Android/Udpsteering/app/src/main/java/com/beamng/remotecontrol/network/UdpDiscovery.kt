package com.beamng.remotecontrol.network

import android.net.Network
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

    /** What the sweep is doing right now, for the UI. */
    enum class SweepPhase {
        /** Unicast to a known address: the saved PC, or the source of an OutGauge stream. */
        SAVED_PC,

        /** Broadcast to the whole network. */
        FULL_SEARCH,
    }

    sealed class Result {
        /** [code] is set when discovered by sweep; null when the caller already knew it. */
        data class Connected(val host: InetAddress, val code: String? = null) : Result()
        data class Failed(val message: String) : Result()
    }

    private const val RECEIVE_TIMEOUT_MS = 250
    private const val DISCOVERY_TIMEOUT_MS = 12_000L
    private const val DRAIN_MAX_MS = 30L
    private const val PRELISTEN_MS = 600L
    private const val FAST_BATCH = 300
    private const val SLOW_BATCH = 28
    private const val OUTGAUGE_MIN_LENGTH = 96
    private const val RETRY_DELAY_MS = 1000L

    /** The game picks its code as random(10000, 99999) — a fixed 5-digit space. */
    private val CODE_RANGE = 10000..99999
    private val REPLY_CODE = Regex("^beamng\\|(\\d{5})$")

    private fun bindToNetwork(network: Network?, socket: DatagramSocket) =
        NetworkUtils.bindToNetwork(network, socket)

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
        securityCode: String,
        network: Network? = null,
    ): Result = withContext(Dispatchers.IO) {
        val buffer = buildHello(securityCode)

        var socketS: DatagramSocket? = null
        var channel: DatagramChannel? = null
        try {
            socketS = DatagramSocket()
            bindToNetwork(network, socketS)
            channel = DatagramChannel.open()
            val socketR = channel.socket()
            socketR.reuseAddress = true
            // Wildcard bind (see sweep): the game's unicast reply must be caught
            // regardless of which local IP our detection reported.
            socketR.bind(InetSocketAddress(Ports.APP))
            bindToNetwork(network, socketR)
            socketR.soTimeout = RECEIVE_TIMEOUT_MS
            val waitingFor = "beamng|$securityCode"
            val receiveBuf = ByteArray(32)
            val packetR = DatagramPacket(receiveBuf, receiveBuf.size)
            val packetS = DatagramPacket(buffer, buffer.size, broadcastAddress, Ports.GAME)

            // Wall-clock deadline, not a failure counter: unrelated datagrams on
            // 4445 (e.g. OutGauge already streaming) would otherwise reset the
            // wait forever when the code is wrong or the game restarted.
            val deadline = System.nanoTime() + DISCOVERY_TIMEOUT_MS * 1_000_000L
            var nextSend = 0L
            while (true) {
                ensureActive()
                val now = System.nanoTime()
                if (now >= deadline) {
                    return@withContext Result.Failed("Connection timeout.")
                }
                if (now >= nextSend) {
                    try {
                        socketS.send(packetS)
                    } catch (e: IOException) {
                        // Transient; retried on the next resend tick.
                    }
                    nextSend = now + RETRY_DELAY_MS * 1_000_000L
                }
                try {
                    socketR.receive(packetR) // blocks ≤ RECEIVE_TIMEOUT_MS
                } catch (e: IOException) {
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
     * match (remoteController.lua:104-121). So we send "beamng|<name>|<code>"
     * across the whole 10000-99999 space; the game's single "beamng|<code>" reply
     * reveals both its address and the matching code, and connects us in one shot.
     *
     * Pacing is the whole game here. A Wi-Fi AP re-airs every broadcast to all
     * stations at its lowest basic rate, so a fast broadcast sweep saturates the
     * air and the game's one-shot reply is lost. Measured on a 2.4 GHz home AP
     * (PC-side flood, 40 unicast replies to the phone per run):
     *     ≤500 broadcasts/s → 40/40 arrive · 1000/s → 35 · 2000/s → 7 · 5000/s → 2
     * Unicast doesn't have this problem (verified: same sweep, same network,
     * connects first pass), but needs the PC's address. The game announces
     * nothing we could discover it by, so the passes are, in order:
     *  1. unicast to [lastHost] — the PC we connected to last time; pass null to
     *     skip it ("search for a new PC");
     *  2. unicast to the source of an OutGauge stream, the moment one is seen
     *     (only flows while the game is unpaused);
     *  3. broadcast — paced under the measured limit when [slowBroadcast].
     *
     * [onPhase] reports which of those is running; [onProgress] reports
     * (codesTried, total) within the current pass.
     */
    suspend fun sweep(
        broadcastAddress: InetAddress,
        lastHost: InetAddress?,
        slowBroadcast: Boolean,
        network: Network?,
        onPhase: (SweepPhase) -> Unit,
        onProgress: (tried: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val name = deviceName().replace(Regex("[|#\\n\\r]"), "_")
        val total = CODE_RANGE.count()

        var sendSocket: DatagramSocket? = null
        var recvChannel: DatagramChannel? = null
        try {
            sendSocket = DatagramSocket().apply { broadcast = true }
            bindToNetwork(network, sendSocket)
            recvChannel = DatagramChannel.open()
            val recv = recvChannel.socket()
            recv.reuseAddress = true
            // Bind the wildcard address, NOT a specific local IP: WifiManager-based
            // IP detection is unreliable on some phones/bands, and the game's reply
            // is unicast to whatever IP it actually saw. Listening on every
            // interface guarantees we catch it.
            recv.bind(InetSocketAddress(Ports.APP))
            bindToNetwork(network, recv)
            recv.soTimeout = 20

            // Large enough to see a whole OutGauge packet (96 bytes) as well.
            val replyBuf = ByteArray(128)
            val replyPacket = DatagramPacket(replyBuf, replyBuf.size)

            // Set when an OutGauge packet reveals the game PC's address.
            var outGaugeHost: InetAddress? = null

            // Listens on 4445 for [maxMs] — time-bounded both ways: a 60 Hz
            // OutGauge stream must not stall the sweep, and a quiet socket must
            // still hold the pace. Returns Connected the moment the game answers.
            fun drainReplies(maxMs: Long = DRAIN_MAX_MS): Result.Connected? {
                val until = System.nanoTime() + maxMs * 1_000_000L
                while (System.nanoTime() < until) {
                    replyPacket.length = replyBuf.size // receive() shrinks it
                    try {
                        recv.receive(replyPacket)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val len = replyPacket.length
                    if (isOutGauge(replyBuf, len)) {
                        if (outGaugeHost == null) outGaugeHost = replyPacket.address
                        continue
                    }
                    val msg = String(replyBuf, 0, len)
                    val found = REPLY_CODE.find(msg)?.groupValues?.get(1)
                    if (found != null) return Result.Connected(replyPacket.address, found)
                }
                return null
            }

            // A brief listen first: with OutGauge already flowing we never
            // need to broadcast at all.
            drainReplies(PRELISTEN_MS)?.let { return@withContext it }

            // One entry = one pass over the code space: (target, codes per batch).
            // A batch is followed by a ~40 ms listen, so 300 ≈ 5k codes/s (18 s
            // per pass) and 28 ≈ 600 codes/s (2.5 min per pass).
            val broadcastBatch = if (slowBroadcast) SLOW_BATCH else FAST_BATCH
            val plans = ArrayList<Pair<InetAddress, Int>>()
            var usedOutGauge = false
            outGaugeHost?.let {
                usedOutGauge = true
                repeat(2) { _ -> plans += it to FAST_BATCH }
            }
            // Two passes on the saved PC: UDP is lossy and this pass is cheap.
            if (!usedOutGauge && lastHost != null) repeat(2) { plans += lastHost to FAST_BATCH }
            repeat(2) { plans += broadcastAddress to broadcastBatch }
            Log.i("UdpDiscovery", "Sweep plan: ${plans.map { "${it.first.hostAddress}/${it.second}" }}")

            var planIndex = 0
            while (planIndex < plans.size) {
                val (target, batchSize) = plans[planIndex]
                onPhase(if (target == broadcastAddress) SweepPhase.FULL_SEARCH else SweepPhase.SAVED_PC)
                var restart = false
                var tried = 0
                for (code in CODE_RANGE) {
                    ensureActive()
                    val payload = "beamng|$name|$code".toByteArray()
                    try {
                        sendSocket.send(
                            DatagramPacket(payload, payload.size, target, Ports.GAME),
                        )
                    } catch (e: IOException) {
                        // A single dropped send is harmless — the sweep continues.
                    }
                    tried++
                    if (tried % batchSize == 0) {
                        drainReplies()?.let { return@withContext it }
                        if (tried % FAST_BATCH < batchSize) onProgress(tried, total)
                        // OutGauge showed up mid-sweep: that host is certain, so
                        // drop everything and sweep it directly.
                        val learned = outGaugeHost
                        if (!usedOutGauge && learned != null) {
                            usedOutGauge = true
                            Log.i("UdpDiscovery", "Game PC found via OutGauge: ${learned.hostAddress}")
                            repeat(2) { plans.add(planIndex, learned to FAST_BATCH) }
                            restart = true
                            break
                        }
                        delay(10)
                    }
                }
                if (restart) continue
                // Final grace window for stragglers before the next pass / giving up.
                repeat(30) {
                    ensureActive()
                    drainReplies()?.let { return@withContext it }
                }
                planIndex++
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

    /** OutGauge from BeamNG: ≥96 bytes with car = "beam" at offset 4 (outgauge.lua). */
    internal fun isOutGauge(data: ByteArray, length: Int): Boolean =
        length >= OUTGAUGE_MIN_LENGTH &&
            data[4] == 'b'.code.toByte() && data[5] == 'e'.code.toByte() &&
            data[6] == 'a'.code.toByte() && data[7] == 'm'.code.toByte()

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val name = if (model.startsWith(manufacturer)) model else "$manufacturer $model"
        return name.replaceFirstChar { it.uppercaseChar() }
    }
}
