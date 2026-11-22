package com.beamng.remotecontrol

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
import com.beamng.remotecontrol.feedback.HapticEngine
import com.beamng.remotecontrol.input.GyroLook
import com.beamng.remotecontrol.protocol.MotionPacket
import com.beamng.remotecontrol.protocol.SelfAlign
import com.beamng.remotecontrol.protocol.Ports
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.protocol.Sendpacket
import com.beamng.remotecontrol.protocol.SteeringCurve
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.AppLanguage
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.RpmScale
import com.beamng.remotecontrol.ui.dash.RpmScaleTracker
import com.beamng.remotecontrol.ui.drive.MenuPad
import com.beamng.remotecontrol.ui.drive.WalkScreen
import com.beamng.remotecontrol.ui.drive.WheelStyle
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
    private var modShowDriveMode by mutableStateOf(true)
    private var modShow4wd by mutableStateOf(true)
    private var modShowNitrous by mutableStateOf(true)
    private var modShowDoors by mutableStateOf(true)
    private var modShowLowRange by mutableStateOf(true)
    private var modShowDiffLock by mutableStateOf(true)
    private var modShowCamera by mutableStateOf(true)
    private var modShowGearbox by mutableStateOf(true)
    private var modShowReset by mutableStateOf(true)
    private var modShowPause by mutableStateOf(true)
    private var modShowMap by mutableStateOf(true)
    private var modShowWalk by mutableStateOf(true)
    private var modHasMapKey = false // the linked mod is 2.3+
    private var splitPedals by mutableStateOf(false)
    private var handbrakeHold by mutableStateOf(false)
    private var hornPlace by mutableIntStateOf(SettingsManager.HORN_KEY)
    private var modHasGameKeys = false // the linked mod is 2.2+
    private var modHasVehicleKeys = false // the linked mod is 2.1+
    private var modFeatures by mutableStateOf<Int?>(null)
    private var handbrakeOn by mutableStateOf(false)
    private var wheelLockDeg by mutableFloatStateOf(WHEEL_LOCK_DEG)
    private var wheelSelfAlign by mutableStateOf(true)
    private var wheelStyle by mutableStateOf(WheelStyle.SPORT)
    private var modRpmScale: RpmScale? = null // UI thread only
    // Written on the UI thread, read by the sender coroutine.
    @Volatile private var handbrakeValue = 0f
    @Volatile private var modButtons = 0
    // Menu pad (mod 2.4+): the phone steers the game's menus.
    @Volatile private var menuButtons = 0
    private var menuPad by mutableStateOf(false)
    private var modHasMenuKeys = false
    private var modShowMenu by mutableStateOf(true)
    // Force-feedback wheel (mod 2.2+): the game turns the wheel while no finger holds it.
    @Volatile private var modHasWheelFeedback = false
    @Volatile private var wheelHeld = false
    @Volatile private var wheelReturnPercent = ModProtocol.WHEEL_RETURN_DEFAULT

    // On foot (mod 2.3+): the walking screen replaces the drive screen.
    private var walking by mutableStateOf(false)
    @Volatile private var walkingNow = false
    @Volatile private var stickX = 0f
    @Volatile private var stickY = 0f
    @Volatile private var stickSprint = false
    @Volatile private var sprintKey = false
    private var walkSprintKey by mutableStateOf(false)
    private var walkStickRight by mutableStateOf(false)
    private var walkCrouchToggle by mutableStateOf(false)
    private var walkShowInteract by mutableStateOf(true)
    private var walkShowView by mutableStateOf(true)
    private var modShowInteract by mutableStateOf(true)
    private var lookDrag by mutableStateOf(false)
    private var walkInvertLook = false
    private var walkGyroAim = false
    private var walkGyroSpeed = 1f
    // Aim by turning the phone on foot: gravity says which way is up.
    private val gravityNow = FloatArray(3)
    private var lastGyroNs = 0L
    private var gyroListening = false
    @Volatile private var walkLookSpeed = 1f
    // Look drag waiting for the next packet; written on the UI thread, taken by the sender.
    private val lookLock = Any()
    private var lookPendingX = 0f
    private var lookPendingY = 0f
    @Volatile private var wheelFeedbackWanted = false // on-screen wheel + the setting
    @Volatile private var gameWheelAngle = 0f
    @Volatile private var gameWheelAngleMs = 0L

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
    private val hapticEngine = HapticEngine()
    private var haptics: HapticEngine.Options? = null

    // Drift indicator (MotionSim protocol, optional second stream)
    private var motionJob: Job? = null
    @Volatile private var motionSocket: DatagramSocket? = null
    private var lastMotionMs = 0L

    // The app language this instance was built with (see AppLanguage.apply).
    private var builtLanguage = ""

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideSystemUI()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        builtLanguage = AppLanguage.requestedTags()

        hideSystemUI()

        // Validate host address - must be set via QR scan before reaching here
        hostAddress = (application as RemoteControlApplication).hostAddress
        if (hostAddress == null) {
            Toast.makeText(this, getString(R.string.toast_no_connection), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        settingsManager = SettingsManager.getInstance(this)
        val modInfo = (application as RemoteControlApplication).modInfo
        modLinked = modInfo != null
        modHasVehicleKeys = modInfo?.hasVehicleKeys == true
        modHasWheelFeedback = modInfo?.hasWheelFeedback == true
        modHasGameKeys = modInfo?.hasGameKeys == true
        modHasMapKey = modInfo?.hasMapKey == true
        modHasMenuKeys = modInfo?.hasMenuKeys == true
        // A latched handbrake survives the language rebuild (onResume drops it
        // again if the key was hidden or switched to hold meanwhile).
        if (modLinked && savedInstanceState?.getBoolean(STATE_HANDBRAKE_ON) == true) {
            handbrakeOn = true
            handbrakeValue = 1f
        }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RemoteTheme {
                if (modLinked && menuPad) {
                    MenuPad(
                        connected = telemetry.connected,
                        onMenu = { bit, pressed ->
                            menuButtons = if (pressed) menuButtons or bit else menuButtons and bit.inv()
                        },
                        onClose = {
                            menuButtons = 0
                            menuPad = false
                        },
                    )
                } else if (modLinked && walking && !dashboardOnly) {
                    WalkScreen(
                        connected = telemetry.connected,
                        sprintOnEdge = !walkSprintKey,
                        stickOnRight = walkStickRight,
                        crouchToggle = walkCrouchToggle,
                        showInteract = walkShowInteract,
                        showView = walkShowView,
                        showMap = modShowMap && modHasMapKey,
                        onStick = { x, y, sprint ->
                            stickX = x
                            stickY = y
                            stickSprint = sprint
                        },
                        onLook = { dx, dy -> addLook(dx * LOOK_UNITS_PER_DP * walkLookSpeed, dy * LOOK_UNITS_PER_DP * walkLookSpeed) },
                        onButton = { bit, pressed ->
                            modButtons = if (pressed) modButtons or bit else modButtons and bit.inv()
                        },
                        onSprintKey = { pressed -> sprintKey = pressed },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                    )
                } else {
                DriveScreen(
                    telemetry = telemetry,
                    metricUnits = metricUnits,
                    controlType = controlType,
                    dashboardOnly = dashboardOnly,
                    perfTimerEnabled = perfTimer,
                    analogPedals = analogPedals,
                    splitPedals = splitPedals,
                    layout = dashLayout,
                    mirrored = mirrored,
                    mod = if (modLinked) {
                        ModControls(
                            showShift = modShowShift, showHandbrake = modShowHandbrake,
                            showHorn = modShowHorn && hornPlace != SettingsManager.HORN_HUB, showLights = modShowLights,
                            showDriveMode = modShowDriveMode && modHasVehicleKeys,
                            show4wd = modShow4wd && modHasVehicleKeys,
                            showNitrous = modShowNitrous && modHasVehicleKeys,
                            showDoors = modShowDoors && modHasVehicleKeys,
                            showLowRange = modShowLowRange && modHasVehicleKeys,
                            showDiffLock = modShowDiffLock && modHasVehicleKeys,
                            showCamera = modShowCamera && modHasGameKeys,
                            showGearbox = modShowGearbox && modHasGameKeys,
                            // Reset has been in the mod's inputmap since 2.0.
                            showReset = modShowReset,
                            showPause = modShowPause && modHasGameKeys,
                            showMap = modShowMap && modHasMapKey,
                            // Walking mode came in the same mod release as the map key.
                            showWalk = modShowWalk && modHasMapKey,
                            showInteract = modShowInteract && modHasMapKey,
                            showMenu = modShowMenu && modHasMenuKeys,
                            onMenuPad = { openMenuPad() },
                            handbrakeHold = handbrakeHold,
                            hornOnHub = modShowHorn && hornPlace != SettingsManager.HORN_KEY,
                            features = modFeatures,
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
                    wheelStyle = wheelStyle,
                    // Null = plain centring. The slip angle is cleared when MotionSim goes
                    // quiet, so without that stream this is the centre as well.
                    wheelRest = if (wheelSelfAlign) {
                        { SelfAlign.restPosition(telemetry.driftDeg) }
                    } else {
                        null
                    },
                    steering = { steeringInputHandler?.getSteeringValue() ?: 0f },
                    wheelRestStrength = { SelfAlign.strength(telemetry.speedMs, wheelReturnPercent) },
                    // Fresh angle from the mod = the game is turning the wheel; stale or
                    // none = fall back to our own estimate above.
                    wheelGameAngle = {
                        if (SystemClock.elapsedRealtime() - gameWheelAngleMs < GAME_WHEEL_FRESH_MS) gameWheelAngle else null
                    },
                    onLookDrag = if (lookDrag && modHasMapKey) {
                        { dx, dy -> addLook(dx * LOOK_UNITS_PER_DP * walkLookSpeed, dy * LOOK_UNITS_PER_DP * walkLookSpeed) }
                    } else {
                        null
                    },
                    onWheelHeld = { held ->
                        wheelHeld = held
                        // An angle from before this grab says nothing about the next release.
                        gameWheelAngleMs = 0L
                    },
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
    }

    // ==================== LIFECYCLE ====================

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_HANDBRAKE_ON, handbrakeOn)
    }

    override fun onResume() {
        super.onResume()

        // The language was changed in Settings: rebuild instead of trusting the
        // in-place update. Nothing is started for the instance that is going away,
        // so the link comes up once, in the new one; the pedals were released and
        // the neutral state sent when Settings opened (onPause).
        if (AppLanguage.changedSince(builtLanguage)) {
            recreate()
            return
        }

        metricUnits = settingsManager.useMetricUnits()
        dashboardOnly = settingsManager.isDashboardOnly
        controlType = settingsManager.controlType
        perfTimer = settingsManager.isPerfTimerEnabled
        analogPedals = settingsManager.isAnalogPedals
        dashLayout = settingsManager.dashLayout
        mirrored = settingsManager.isMirrored
        wheelSelfAlign = settingsManager.isWheelSelfAlign
        haptics = hapticOptions()
        wheelStyle = settingsManager.wheelStyle
        wheelReturnPercent = settingsManager.wheelReturnPercent
        walkSprintKey = settingsManager.isWalkSprintKey
        walkStickRight = settingsManager.isWalkStickRight
        walkCrouchToggle = settingsManager.isWalkCrouchToggle
        walkShowInteract = settingsManager.walkShowInteract
        walkShowView = settingsManager.walkShowView
        modShowInteract = settingsManager.modShowInteract
        lookDrag = settingsManager.isLookDrag
        walkInvertLook = settingsManager.isWalkInvertLook
        walkGyroAim = settingsManager.isWalkGyroAim
        walkGyroSpeed = settingsManager.walkGyroSpeed
        setGyroAim(walkGyroAim && modLinked)
        walkLookSpeed = settingsManager.walkLookSpeed
        wheelFeedbackWanted = wheelSelfAlign && controlType == SettingsManager.CONTROL_SLIDER
        wheelHeld = false
        gameWheelAngleMs = 0L
        modShowShift = settingsManager.modShowShift
        modShowHandbrake = settingsManager.modShowHandbrake
        modShowHorn = settingsManager.modShowHorn
        modShowLights = settingsManager.modShowLights
        modShowDriveMode = settingsManager.modShowDriveMode
        modShow4wd = settingsManager.modShow4wd
        modShowNitrous = settingsManager.modShowNitrous
        modShowDoors = settingsManager.modShowDoors
        modShowLowRange = settingsManager.modShowLowRange
        modShowDiffLock = settingsManager.modShowDiffLock
        modShowCamera = settingsManager.modShowCamera
        modShowGearbox = settingsManager.modShowGearbox
        modShowReset = settingsManager.modShowReset
        modShowPause = settingsManager.modShowPause
        modShowMap = settingsManager.modShowMap
        modShowWalk = settingsManager.modShowWalk
        modShowMenu = settingsManager.modShowMenu
        splitPedals = settingsManager.isSplitPedals
        hornPlace = settingsManager.hornPlace
        // Switched to hold while the handbrake was latched on: let it off.
        val hold = settingsManager.isHandbrakeHold
        if (hold && !handbrakeHold) {
            handbrakeOn = false
            handbrakeValue = 0f
        }
        handbrakeHold = hold
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
        setGyroAim(false)
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

    /** What the player wants to feel, read in onResume; null = vibration off altogether. */
    private fun hapticOptions(): HapticEngine.Options? {
        val s = settingsManager
        if (!s.isHapticEnabled) return null
        return HapticEngine.Options(
            gear = s.hapticGear, impact = s.hapticImpact, landing = s.hapticLanding,
            abs = s.hapticAbs, traction = s.hapticTraction, redline = s.hapticRedline,
        )
    }

    private fun play(pulses: List<HapticEngine.Pulse>) {
        // Two in one packet (a gear change on the limiter): the stronger one.
        val p = pulses.maxByOrNull { it.amplitude * it.ms } ?: return
        doVibrate(p.ms, p.amplitude)
    }

    private fun processHapticFeedback(p: Receivepacket) {
        val options = haptics ?: return
        play(
            hapticEngine.onTelemetry(
                SystemClock.elapsedRealtime(), p.gear, p.speed * 3.6f,
                p.activeLightsArr[Receivepacket.INDEX_ABS], p.activeLightsArr[Receivepacket.INDEX_TC],
                p.rpm, telemetry.rpmScale.redline, options,
            ),
        )
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
                val hello = ModProtocol.hello(UdpDiscovery.deviceName(), wheelReturnPercent)
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
                                val buffer = if (walkingNow) {
                                    // On foot the stick walks; the mod reads it from the same axes.
                                    val w = ModProtocol.walkAxes(stickX, stickY, stickSprint || sprintKey)
                                    val look = synchronized(lookLock) {
                                        val l = lookPendingX to lookPendingY
                                        lookPendingX = 0f
                                        lookPendingY = 0f
                                        l
                                    }
                                    ModProtocol.control(
                                        steering = w.steering, throttle = w.throttle, brake = w.brake,
                                        clutch = 0f, handbrake = w.handbrake, buttons = modButtons,
                                        lookX = look.first, lookY = look.second, menuButtons = menuButtons,
                                    )
                                } else {
                                val steering = steeringInputHandler?.getSteeringValue() ?: 0f
                                val look = synchronized(lookLock) {
                                    val l = lookPendingX to lookPendingY
                                    lookPendingX = 0f
                                    lookPendingY = 0f
                                    l
                                }
                                ModProtocol.control(
                                    steering = steering, throttle = thrpushed, brake = brpushed,
                                    clutch = 0f, handbrake = handbrakeValue,
                                    buttons = if (modHasWheelFeedback && wheelFeedbackWanted && !wheelHeld) {
                                        modButtons or ModProtocol.FLAG_WHEEL_FREE
                                    } else {
                                        modButtons
                                    },
                                    lookX = look.first, lookY = look.second, menuButtons = menuButtons,
                                )
                                }
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
            val message = String(buf, 0, packet.length)
            val onFoot = ModProtocol.parseWalking(message)
            if (onFoot != null) {
                if (onFoot != walkingNow) withContext(Dispatchers.Main) { onWalkingChanged(onFoot) }
                continue
            }
            val angle = ModProtocol.parseWheelAngle(message)
            if (angle != null) {
                gameWheelAngle = angle
                gameWheelAngleMs = SystemClock.elapsedRealtime()
                continue
            }
            val info = ModProtocol.parseVehicleInfo(message) ?: continue
            withContext(Dispatchers.Main) { onVehicleInfo(info) }
        }
    }

    /** UI thread: look drag for the next packet; [y] is down-positive, flipped if the player inverted it. */
    private fun addLook(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        synchronized(lookLock) {
            lookPendingX += x
            lookPendingY += if (walkInvertLook) -y else y
        }
    }

    private val gyroAimListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> System.arraycopy(event.values, 0, gravityNow, 0, 3)
                Sensor.TYPE_GYROSCOPE -> {
                    val last = lastGyroNs
                    lastGyroNs = event.timestamp
                    // Only on foot; a stale timestamp after a pause would be one huge jump.
                    if (!walkingNow || last == 0L) return
                    val dt = (event.timestamp - last) / 1e9f
                    if (dt > 0.1f) return
                    @Suppress("DEPRECATION")
                    val rotation = windowManager.defaultDisplay.rotation
                    val (x, y) = GyroLook.delta(event.values, gravityNow, rotation, dt, walkGyroSpeed)
                    addLook(x, y)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun setGyroAim(on: Boolean) {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (on && !gyroListening) {
            val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
            val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: return
            lastGyroNs = 0L
            sm.registerListener(gyroAimListener, gravity, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(gyroAimListener, gyro, SensorManager.SENSOR_DELAY_GAME)
            gyroListening = true
        } else if (!on && gyroListening) {
            sm.unregisterListener(gyroAimListener)
            gyroListening = false
        }
    }

    /** UI thread: the player got out of the car or back in. Nothing held carries over. */
    /**
     * UI thread: the MENU key. Lets go of the pedals (they are off screen now) and
     * opens the game's menu with one tap of its menu key.
     */
    private fun openMenuPad() {
        thrpushed = 0f
        brpushed = 0f
        modButtons = 0
        menuPad = true
        lifecycleScope.launch {
            menuButtons = ModProtocol.MENU_TOGGLE
            delay(MENU_TAP_MS)
            menuButtons = menuButtons and ModProtocol.MENU_TOGGLE.inv()
        }
    }

    private fun onWalkingChanged(onFoot: Boolean) {
        if (isFinishing || isDestroyed) return
        walkingNow = onFoot
        walking = onFoot
        stickX = 0f
        stickY = 0f
        stickSprint = false
        sprintKey = false
        modButtons = 0
        thrpushed = 0f
        brpushed = 0f
        synchronized(lookLock) {
            lookPendingX = 0f
            lookPendingY = 0f
        }
    }

    /** UI thread: the car the player sits in told us its rev limit and steering lock. */
    private fun onVehicleInfo(info: ModProtocol.VehicleInfo) {
        if (isFinishing || isDestroyed) return
        modRpmScale = info.maxRpm?.let { limit ->
            RpmScale(max = ceil(limit * 1.08f / 500f) * 500f, redline = limit)
        }
        wheelLockDeg = info.steeringLockDeg ?: WHEEL_LOCK_DEG
        modFeatures = info.features
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
        haptics?.let { play(hapticEngine.onMotion(SystemClock.elapsedRealtime(), p.accX, p.accY, p.accZ, it)) }
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
        processHapticFeedback(p)
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
        private const val STATE_HANDBRAKE_ON = "handbrake_on"
        private const val HEARTBEAT_EVERY_TICKS = 300 // ~3s at the 10ms send rate
        private const val SEND_RETRY_DELAY_MS = 250L
        private const val NEUTRAL_PACKETS = 3
        private const val MENU_TAP_MS = 120L // long enough for several packets
        // Look drag sent per dp of finger travel at speed 1: a 200 dp swipe turns about 80°.
        private const val LOOK_UNITS_PER_DP = 5f
        private const val GAME_WHEEL_FRESH_MS = 250L // the mod reports the free wheel ~30 times a second
        private const val TELEMETRY_STALE_MS = 2000L
    }
}
