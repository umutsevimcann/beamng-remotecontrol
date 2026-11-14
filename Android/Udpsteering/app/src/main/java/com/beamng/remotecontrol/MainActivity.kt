package com.beamng.remotecontrol

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.beamng.remotecontrol.input.ButtonInputHandler
import com.beamng.remotecontrol.input.InputHandlerFactory
import com.beamng.remotecontrol.input.SliderInputHandler
import com.beamng.remotecontrol.input.SteeringInputHandler
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.protocol.MotionPacket
import com.beamng.remotecontrol.protocol.Ports
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.protocol.Sendpacket
import com.beamng.remotecontrol.protocol.SteeringCurve
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.RpmScale
import com.beamng.remotecontrol.ui.dash.RpmScaleTracker
import com.beamng.remotecontrol.ui.drive.DriveScreen
import com.beamng.remotecontrol.ui.drive.ModControls
import com.beamng.remotecontrol.ui.drive.WHEEL_LOCK_DEG
import com.beamng.remotecontrol.ui.TelemetryUiState
import com.beamng.remotecontrol.ui.theme.RemoteTheme

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // Compose-observed state
    private val telemetry = TelemetryUiState()
    private var controlType by mutableIntStateOf(SettingsManager.CONTROL_GYROSCOPE)
    private var dashboardOnly by mutableStateOf(false)
    private var metricUnits by mutableStateOf(true)
    private var perfTimer by mutableStateOf(false)
    private var analogPedals by mutableStateOf(false)
    private var dashLayout by mutableStateOf(DashLayout.MODERN)
    private var mirrored by mutableStateOf(false)
    private val rpmScaleTracker = RpmScaleTracker() // UI thread only

    // RemoteControlPro mod link (null modInfo = stock game, none of this is used)
    private var modLinked by mutableStateOf(false)
    private var modShowShift by mutableStateOf(true)
    private var modShowHandbrake by mutableStateOf(true)
    private var modShowHorn by mutableStateOf(true)
    private var modShowLights by mutableStateOf(true)
    private var handbrakeOn by mutableStateOf(false)
    private var wheelLockDeg by mutableFloatStateOf(WHEEL_LOCK_DEG)
    private var modRpmScale: RpmScale? = null // UI thread only
    // Written on the UI thread, read by the sender coroutine.
    @Volatile private var handbrakeValue = 0f
    @Volatile private var modButtons = 0

    // 0-100 timer state machine (UI thread only)
    private var timerArmed = false
    private var timerStartMs = 0L

    // Thread-safe control values (written UI thread, read sender coroutine)
    @Volatile private var thrpushed = 0f
    @Volatile private var brpushed = 0f

    // Networking (coroutine jobs on lifecycleScope, IO dispatcher)
    private val sendingTimeout = 10L // 100Hz for responsive controls
    private var senderJob: Job? = null
    private var receiverJob: Job? = null
    // Written on IO (receiver coroutines), read on main (stopUdpTasks).
    @Volatile private var receiverSocket: DatagramSocket? = null
    private var hostAddress: InetAddress? = null

    // Modular Input System
    private lateinit var settingsManager: SettingsManager
    @Volatile private var steeringInputHandler: SteeringInputHandler? = null

    // Haptic Feedback
    private var vibrator: Vibrator? = null
    private var oldGearString = ""
    private var impactWindowSpeed = 0
    private var impactWindowStartMs = 0L
    private var lastImpactVibrationTime = 0L
    private var lastAbsVibrationTime = 0L

    // Drift indicator (MotionSim protocol, optional second stream)
    private var motionJob: Job? = null
    @Volatile private var motionSocket: DatagramSocket? = null
    private var lastMotionMs = 0L

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideSystemUI()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()

        // Validate host address - must be set via QR scan before reaching here
        hostAddress = (application as RemoteControlApplication).hostAddress
        if (hostAddress == null) {
            Toast.makeText(this, getString(R.string.toast_no_connection), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        settingsManager = SettingsManager.getInstance(this)
        modLinked = (application as RemoteControlApplication).modInfo != null
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RemoteTheme {
                DriveScreen(
                    telemetry = telemetry,
                    metricUnits = metricUnits,
                    controlType = controlType,
                    dashboardOnly = dashboardOnly,
                    perfTimerEnabled = perfTimer,
                    analogPedals = analogPedals,
                    layout = dashLayout,
                    mirrored = mirrored,
                    mod = if (modLinked) {
                        ModControls(
                            showShift = modShowShift, showHandbrake = modShowHandbrake,
                            showHorn = modShowHorn, showLights = modShowLights,
                            handbrakeOn = handbrakeOn,
                            onButton = { bit, pressed ->
                                modButtons = if (pressed) modButtons or bit else modButtons and bit.inv()
                            },
                            onHandbrake = { on ->
                                handbrakeOn = on
                                handbrakeValue = if (on) 1f else 0f
                            },
                        )
                    } else {
                        null
                    },
                    wheelLockDeg = wheelLockDeg,
                    steering = { steeringInputHandler?.getSteeringValue() ?: 0f },
                    onThrottle = { value -> thrpushed = value },
                    onBrake = { value -> brpushed = value },
                    onSteerLeft = { pressed ->
                        (steeringInputHandler as? ButtonInputHandler)?.pressLeft(pressed)
                    },
                    onSteerRight = { pressed ->
                        (steeringInputHandler as? ButtonInputHandler)?.pressRight(pressed)
                    },
                    onWheel = { value ->
                        (steeringInputHandler as? SliderInputHandler)?.setValue(value)
                    },
                    onWheelRelease = {
                        (steeringInputHandler as? SliderInputHandler)?.release()
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }

    // ==================== LIFECYCLE ====================

    override fun onResume() {
        super.onResume()

        metricUnits = settingsManager.useMetricUnits()
        dashboardOnly = settingsManager.isDashboardOnly
        controlType = settingsManager.controlType
        perfTimer = settingsManager.isPerfTimerEnabled
        analogPedals = settingsManager.isAnalogPedals
        dashLayout = settingsManager.dashLayout
        mirrored = settingsManager.isMirrored
        modShowShift = settingsManager.modShowShift
        modShowHandbrake = settingsManager.modShowHandbrake
        modShowHorn = settingsManager.modShowHorn
        modShowLights = settingsManager.modShowLights
        // A key hidden in settings must not stay applied with no way to release it.
        if (!modShowHandbrake) {
            handbrakeOn = false
            handbrakeValue = 0f
        }

        setupInputHandler()
        steeringInputHandler?.start()

        startUdpTasks()
    }

    override fun onPause() {
        super.onPause()
        // Release the controls before the sender goes away: a pedal held while
        // the screen pauses must not stay pressed in the game. The sender's
        // finally block then transmits the neutral state.
        thrpushed = 0f
        brpushed = 0f
        modButtons = 0
        stopUdpTasks()
        steeringInputHandler?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUdpTasks()
    }

    private fun setupInputHandler() {
        steeringInputHandler?.stop()
        // Dashboard-only: no steering input at all; the sender keeps sending a
        // centered wheel so the game keeps the device registered (10s timeout).
        steeringInputHandler = if (dashboardOnly) {
            null
        } else {
            InputHandlerFactory.createHandler(this, controlType)
        }
        thrpushed = 0f
        brpushed = 0f
    }

    private fun hideSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ==================== HAPTIC FEEDBACK ====================

    private fun doVibrate(ms: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms)
        }
    }

    private fun processHapticFeedback(p: Receivepacket, newSpeed: Int) {
        if (!settingsManager.isHapticEnabled) return

        val now = System.currentTimeMillis()

        // 1. Gear Change
        val currentGear = p.gear
        if (currentGear != oldGearString) {
            doVibrate(50, VibrationEffect.DEFAULT_AMPLITUDE)
            oldGearString = currentGear
        }

        // 2. Impact detection over a ~300ms window. Telemetry arrives at up to
        // 60Hz, so the per-packet delta of even a violent crash is only a few
        // km/h — the old per-packet comparison could never fire.
        if (impactWindowStartMs == 0L) {
            impactWindowStartMs = now
            impactWindowSpeed = newSpeed
        } else if (now - impactWindowStartMs >= IMPACT_WINDOW_MS) {
            val drop = abs(impactWindowSpeed - newSpeed)
            if (drop > 25 && (now - lastImpactVibrationTime) > IMPACT_VIBRATION_COOLDOWN_MS) {
                when {
                    drop > 60 -> doVibrate(600, 255)  // Severe crash
                    drop > 40 -> doVibrate(300, 180)  // Medium impact
                    else -> doVibrate(150, 100)       // Minor bump
                }
                lastImpactVibrationTime = now
            }
            impactWindowStartMs = now
            impactWindowSpeed = newSpeed
        }

        // 3. ABS feel: pulse only while ABS is actually intervening. The game sets
        // this showLights bit from electrics absActive (outgauge.lua), so a held
        // brake at standstill — or on a car without ABS — stays silent.
        if (p.activeLightsArr[Receivepacket.INDEX_ABS] && newSpeed > 5 &&
            (now - lastAbsVibrationTime) > ABS_VIBRATION_COOLDOWN_MS
        ) {
            doVibrate(40, 60)
            lastAbsVibrationTime = now
        }
    }

    // ==================== NETWORKING ====================

    private fun startUdpTasks() {
        stopUdpTasks()
        val host = hostAddress ?: return
        senderJob = lifecycleScope.launch(Dispatchers.IO) {
            if (modLinked) runModLink(host) else runSender(host)
        }
        receiverJob = lifecycleScope.launch(Dispatchers.IO) { runReceiver() }
        motionJob = lifecycleScope.launch(Dispatchers.IO) { runMotionReceiver() }
    }

    private fun stopUdpTasks() {
        senderJob?.cancel()
        senderJob = null
        receiverJob?.cancel()
        receiverJob = null
        motionJob?.cancel()
        motionJob = null
        // Closing the sockets unblocks a receive() that is mid-wait.
        receiverSocket?.let { if (!it.isClosed) it.close() }
        motionSocket?.let { if (!it.isClosed) it.close() }
    }

    /**
     * Sends control packets to BeamNG on port 4444 at ~100Hz.
     * BeamNG's remoteController.lua reverses the byte array before parsing as
     * {w, x, y, z} floats where z=steering, y=throttle, x=brake, w=id.
     */
    private suspend fun runSender(host: InetAddress) {
        try {
            DatagramSocket().use { socket ->
                // Same Wi-Fi pinning as discovery: otherwise, with mobile data on
                // and a LAN without internet, control packets leave via cellular.
                NetworkUtils.bindToNetwork(NetworkUtils.wifiNetwork(this), socket)
                val sendpacket = Sendpacket()
                // Reconnect heartbeat: the game deletes idle devices after 10s
                // (e.g. app backgrounded). Re-sending the discovery hello makes
                // remoteController.lua re-register us and is a no-op otherwise.
                val hello = (application as RemoteControlApplication).securityCode
                    ?.let { UdpDiscovery.buildHello(it) }
                var tick = 0
                var failing = false
                try {
                while (true) {
                    delay(sendingTimeout) // also our cancellation point

                    // A send can fail transiently (Wi-Fi roam, band steering, AP
                    // blip). Never let that end the loop — the dashboard would keep
                    // updating while steering/pedals silently die.
                    try {
                        if (hello != null && tick++ % HEARTBEAT_EVERY_TICKS == 0) {
                            socket.send(DatagramPacket(hello, hello.size, host, Ports.GAME))
                        }

                        var steeringValue = 0.5f
                        val handler = steeringInputHandler
                        if (handler != null) {
                            // BeamNG protocol after byte-reverse: z maps to steering (0=right, 1=left)
                            steeringValue = ((1f - SteeringCurve.compensate(handler.getSteeringValue())) / 2f).coerceIn(0f, 1f)
                        }
                        sendpacket.setSteeringAngle(steeringValue)
                        sendpacket.setThrottle(thrpushed)
                        sendpacket.setBreaks(brpushed)
                        // Stock game never echoes the id (outgauge.lua: o.id = 0) — constant.
                        sendpacket.setID(0)

                        val buffer = sendpacket.sendingByteArray
                        socket.send(DatagramPacket(buffer, buffer.size, host, Ports.GAME))
                        if (failing) {
                            failing = false
                            Log.i(TAG, "UDP send recovered")
                        }
                    } catch (e: IOException) {
                        if (!failing) {
                            failing = true
                            Log.w(TAG, "UDP send failed, retrying", e)
                        }
                        // Re-send the hello first on recovery: the game may have
                        // dropped us (10s idle timeout) while the link was down.
                        tick = 0
                        delay(SEND_RETRY_DELAY_MS)
                    }
                }
                } finally {
                    // Leaving (pause, settings, screen off, incoming call): let go
                    // of everything on the game's side. Sent a few times because
                    // UDP is lossy, and non-cancellably because we are here
                    // precisely because the coroutine was cancelled.
                    withContext(NonCancellable) {
                        val neutral = Sendpacket.neutral()
                        repeat(NEUTRAL_PACKETS) {
                            try {
                                socket.send(DatagramPacket(neutral, neutral.size, host, Ports.GAME))
                            } catch (e: IOException) {
                                // Link is down; the game's 10 s timeout is the fallback.
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Only socket creation can land here now.
            if (coroutineContext.isActive) Log.e(TAG, "UDP sender could not start", e)
        }
    }

    /**
     * The RemoteControlPro link: one socket to the mod's port. Sends a hello every
     * ~3 s (the mod unplugs a silent phone after 10 s, and a restarted game has
     * forgotten us) and the control state at ~100 Hz; a child coroutine reads the
     * per-vehicle data the mod sends back on the same socket.
     */
    private suspend fun runModLink(host: InetAddress) {
        try {
            DatagramSocket().use { socket ->
                NetworkUtils.bindToNetwork(NetworkUtils.wifiNetwork(this), socket)
                socket.soTimeout = 500
                val hello = ModProtocol.hello(UdpDiscovery.deviceName())
                coroutineScope {
                    val reader = launch { readModReplies(socket) }
                    var tick = 0
                    var failing = false
                    try {
                        while (true) {
                            delay(sendingTimeout) // also our cancellation point
                            try {
                                if (tick++ % HEARTBEAT_EVERY_TICKS == 0) {
                                    socket.send(DatagramPacket(hello, hello.size, host, Ports.MOD))
                                }
                                // The mod's inputmap is linear (linearity 1): no SteeringCurve here.
                                val steering = steeringInputHandler?.getSteeringValue() ?: 0f
                                val buffer = ModProtocol.control(
                                    steering = steering, throttle = thrpushed, brake = brpushed,
                                    clutch = 0f, handbrake = handbrakeValue, buttons = modButtons,
                                )
                                socket.send(DatagramPacket(buffer, buffer.size, host, Ports.MOD))
                                if (failing) {
                                    failing = false
                                    Log.i(TAG, "Mod link recovered")
                                }
                            } catch (e: IOException) {
                                if (!failing) {
                                    failing = true
                                    Log.w(TAG, "Mod send failed, retrying", e)
                                }
                                tick = 0 // hello first on recovery
                                delay(SEND_RETRY_DELAY_MS)
                            }
                        }
                    } finally {
                        reader.cancel()
                        withContext(NonCancellable) {
                            val neutral = ModProtocol.neutral()
                            repeat(NEUTRAL_PACKETS) {
                                try {
                                    socket.send(DatagramPacket(neutral, neutral.size, host, Ports.MOD))
                                } catch (e: IOException) {
                                    // Link is down; the mod's 10 s timeout is the fallback.
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (coroutineContext.isActive) Log.e(TAG, "Mod link could not start", e)
        }
    }

    private suspend fun readModReplies(socket: DatagramSocket) {
        val buf = ByteArray(256)
        val packet = DatagramPacket(buf, buf.size)
        while (coroutineContext.isActive) {
            try {
                packet.length = buf.size
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                return // socket closed: the link is ending
            }
            val info = ModProtocol.parseVehicleInfo(String(buf, 0, packet.length)) ?: continue
            withContext(Dispatchers.Main) { onVehicleInfo(info) }
        }
    }

    /** UI thread: the car the player sits in told us its rev limit and steering lock. */
    private fun onVehicleInfo(info: ModProtocol.VehicleInfo) {
        if (isFinishing || isDestroyed) return
        modRpmScale = info.maxRpm?.let { limit ->
            RpmScale(max = ceil(limit * 1.08f / 500f) * 500f, redline = limit)
        }
        wheelLockDeg = info.steeringLockDeg ?: WHEEL_LOCK_DEG
    }

    /**
     * Receives OutGauge telemetry packets from BeamNG on port 4445 —
     * sent by the game's built-in OutGauge protocol (Options > Other).
     */
    private suspend fun runReceiver() {
        var sock: DatagramSocket? = null
        try {
            val s = DatagramSocket(null)
            sock = s
            receiverSocket = s
            s.reuseAddress = true
            s.bind(InetSocketAddress(Ports.APP))
            s.soTimeout = 1000

            val buf = ByteArray(128)
            // Staleness tracking stays local to this coroutine: "Connected" must
            // clear when the game closes / Wi-Fi drops, instead of latching green
            // over frozen gauges. Heartbeat replies also land on this port, so a
            // receive timeout alone isn't a reliable silence signal.
            var lastTelemetryMs = 0L
            var live = false
            while (coroutineContext.isActive) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    s.receive(dp)

                    if (dp.length >= Receivepacket.MIN_PACKET_LENGTH) {
                        val p = Receivepacket(buf, dp.length)
                        if (p.isValid) {
                            lastTelemetryMs = System.currentTimeMillis()
                            live = true
                            withContext(Dispatchers.Main) { onTelemetry(p) }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal, loop continues
                } catch (e: IOException) {
                    if (coroutineContext.isActive) Log.e(TAG, "Receive error: " + e.message)
                    break
                }
                if (live && System.currentTimeMillis() - lastTelemetryMs > TELEMETRY_STALE_MS) {
                    live = false
                    withContext(Dispatchers.Main) { telemetry.connected = false }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Cannot bind to port ${Ports.APP}", e)
        } finally {
            // Close our own socket, and clear the field only if a newer job
            // (fast pause/resume) hasn't already replaced it.
            sock?.let { if (!it.isClosed) it.close() }
            if (receiverSocket === sock) receiverSocket = null
        }
    }

    /**
     * Optional drift stream: the game's MotionSim protocol (Options > Other),
     * pointed at this phone on port 4446. Purely additive — when the user
     * hasn't enabled it, nothing arrives and the drift badge stays hidden.
     */
    private suspend fun runMotionReceiver() {
        var sock: DatagramSocket? = null
        try {
            val s = DatagramSocket(null)
            sock = s
            motionSocket = s
            s.reuseAddress = true
            s.bind(InetSocketAddress(Ports.MOTION))
            s.soTimeout = 2000

            val buf = ByteArray(160)
            while (coroutineContext.isActive) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    s.receive(dp)
                    val p = MotionPacket(buf, dp.length)
                    if (p.isValid) {
                        withContext(Dispatchers.Main) { onMotion(p) }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal, loop continues
                } catch (e: IOException) {
                    if (coroutineContext.isActive) Log.e(TAG, "Motion receive error: " + e.message)
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Cannot bind to port ${Ports.MOTION}", e)
        } finally {
            sock?.let { if (!it.isClosed) it.close() }
            if (motionSocket === sock) motionSocket = null
        }
    }

    private fun onMotion(p: MotionPacket) {
        if (isFinishing || isDestroyed) return
        lastMotionMs = System.currentTimeMillis()
        val slip = p.slipAngleDeg()
        telemetry.driftDeg = slip
        if (slip != null) {
            val a = abs(slip)
            telemetry.driftMaxDeg = telemetry.driftMaxDeg?.let { maxOf(it, a) } ?: a
        }
    }

    /** UI-thread telemetry handler (called from the receiver coroutine). */
    private fun onTelemetry(p: Receivepacket) {
        if (isFinishing || isDestroyed) return

        telemetry.connected = true
        telemetry.speedMs = p.speed
        telemetry.rpm = p.rpm
        // The tracker keeps observing so a later stock session starts from a learned scale.
        val learned = rpmScaleTracker.observe(p.rpm)
        telemetry.rpmScale = modRpmScale ?: learned
        telemetry.gear = p.gear
        telemetry.fuel = p.fuel
        telemetry.engTemp = p.engineTemp
        telemetry.turbo = p.turbo
        telemetry.hasTurbo = p.hasTurbo
        telemetry.throttleEcho = p.throttle
        telemetry.brakeEcho = p.brake
        telemetry.clutchEcho = p.clutch

        if (perfTimer) updatePerfTimer(3.6f * p.speed)

        // Hide the drift badge when the MotionSim stream goes quiet.
        if (telemetry.driftDeg != null &&
            System.currentTimeMillis() - lastMotionMs > 2000
        ) {
            telemetry.driftDeg = null
        }

        val lightsarray = p.activeLightsArr
        for (i in 0 until 11) {
            if (telemetry.lights[i] != lightsarray[i]) {
                telemetry.lights[i] = lightsarray[i]
            }
        }

        val newSpeed = if (metricUnits) {
            (3.6f * p.speed).roundToInt()
        } else {
            (2.23694f * p.speed).roundToInt()
        }
        processHapticFeedback(p, newSpeed)
    }

    /** 0-100 km/h stopwatch: arms at standstill, stops at 100, keeps session best. */
    private fun updatePerfTimer(kmh: Float) {
        val now = System.currentTimeMillis()
        when {
            kmh < 1f -> {
                timerArmed = true
                timerStartMs = 0L
                telemetry.timerLiveSec = null
            }
            timerArmed && timerStartMs == 0L && kmh >= 3f -> {
                timerStartMs = now
            }
            timerStartMs > 0L -> {
                val live = (now - timerStartMs) / 1000f
                telemetry.timerLiveSec = live
                if (kmh >= 100f) {
                    telemetry.timerLastSec = live
                    telemetry.timerBestSec =
                        telemetry.timerBestSec?.let { minOf(it, live) } ?: live
                    timerStartMs = 0L
                    timerArmed = false
                    telemetry.timerLiveSec = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "BeamNG"
        private const val IMPACT_VIBRATION_COOLDOWN_MS = 500L
        private const val HEARTBEAT_EVERY_TICKS = 300 // ~3s at the 10ms send rate
        private const val SEND_RETRY_DELAY_MS = 250L
        private const val NEUTRAL_PACKETS = 3
        private const val ABS_VIBRATION_COOLDOWN_MS = 150L
        private const val TELEMETRY_STALE_MS = 2000L
        private const val IMPACT_WINDOW_MS = 300L
    }
}
