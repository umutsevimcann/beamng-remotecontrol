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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.beamng.remotecontrol.input.ButtonInputHandler
import com.beamng.remotecontrol.input.InputHandlerFactory
import com.beamng.remotecontrol.input.SliderInputHandler
import com.beamng.remotecontrol.input.SteeringInputHandler
import com.beamng.remotecontrol.protocol.Ports
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.protocol.Sendpacket
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.DriveScreen
import com.beamng.remotecontrol.ui.TelemetryUiState
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    // Compose-observed state
    private val telemetry = TelemetryUiState()
    private var controlType by mutableIntStateOf(SettingsManager.CONTROL_GYROSCOPE)
    private var dashboardOnly by mutableStateOf(false)
    private var metricUnits by mutableStateOf(true)

    // Thread-safe control values (written UI thread, read sender coroutine)
    @Volatile private var thrpushed = 0f
    @Volatile private var brpushed = 0f

    // Networking (coroutine jobs on lifecycleScope, IO dispatcher)
    private val sendingTimeout = 10L // 100Hz for responsive controls
    private var senderJob: Job? = null
    private var receiverJob: Job? = null
    private var receiverSocket: DatagramSocket? = null
    private var hostAddress: InetAddress? = null

    // Modular Input System
    private lateinit var settingsManager: SettingsManager
    @Volatile private var steeringInputHandler: SteeringInputHandler? = null

    // Haptic Feedback
    private var vibrator: Vibrator? = null
    private var oldGearString = ""
    private var oldSpeedForImpact = 0
    private var lastImpactVibrationTime = 0L

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
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            NightGarageTheme {
                DriveScreen(
                    telemetry = telemetry,
                    metricUnits = metricUnits,
                    controlType = controlType,
                    dashboardOnly = dashboardOnly,
                    onThrottle = { pressed -> thrpushed = if (pressed) 1f else 0f },
                    onBrake = { pressed -> brpushed = if (pressed) 1f else 0f },
                    onSteerLeft = { pressed ->
                        (steeringInputHandler as? ButtonInputHandler)?.pressLeft(pressed)
                    },
                    onSteerRight = { pressed ->
                        (steeringInputHandler as? ButtonInputHandler)?.pressRight(pressed)
                    },
                    onSliderChange = { value ->
                        (steeringInputHandler as? SliderInputHandler)?.setValue(value)
                    },
                    onSliderRelease = {
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

        setupInputHandler()
        steeringInputHandler?.start()

        startUdpTasks()
    }

    override fun onPause() {
        super.onPause()
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

        // 2. Impact Detection with cooldown
        val speedDiff = abs(newSpeed - oldSpeedForImpact)
        if (speedDiff > 20 && (now - lastImpactVibrationTime) > IMPACT_VIBRATION_COOLDOWN_MS) {
            when {
                speedDiff > 50 -> doVibrate(600, 255)  // Severe crash
                speedDiff > 35 -> doVibrate(300, 180)  // Medium impact
                else -> doVibrate(150, 100)            // Minor bump
            }
            lastImpactVibrationTime = now
        }
        oldSpeedForImpact = newSpeed

        // 3. ABS feel (hard braking)
        if (p.brake > 0.85f && (now - lastImpactVibrationTime) > 100) {
            doVibrate(40, 60)
        }
    }

    // ==================== NETWORKING ====================

    private fun startUdpTasks() {
        stopUdpTasks()
        val host = hostAddress ?: return
        senderJob = lifecycleScope.launch(Dispatchers.IO) { runSender(host) }
        receiverJob = lifecycleScope.launch(Dispatchers.IO) { runReceiver() }
    }

    private fun stopUdpTasks() {
        senderJob?.cancel()
        senderJob = null
        receiverJob?.cancel()
        receiverJob = null
        // Closing the socket unblocks a receive() that is mid-wait.
        receiverSocket?.let { if (!it.isClosed) it.close() }
    }

    /**
     * Sends control packets to BeamNG on port 4444 at ~100Hz.
     * BeamNG's remoteController.lua reverses the byte array before parsing as
     * {w, x, y, z} floats where z=steering, y=throttle, x=brake, w=id.
     */
    private suspend fun runSender(host: InetAddress) {
        try {
            DatagramSocket().use { socket ->
                val sendpacket = Sendpacket()
                while (true) {
                    delay(sendingTimeout) // also our cancellation point

                    var steeringValue = 0.5f
                    val handler = steeringInputHandler
                    if (handler != null) {
                        // BeamNG protocol after byte-reverse: z maps to steering (0=right, 1=left)
                        steeringValue = ((1f - handler.getSteeringValue()) / 2f).coerceIn(0f, 1f)
                    }
                    sendpacket.setSteeringAngle(steeringValue)
                    sendpacket.setThrottle(thrpushed)
                    sendpacket.setBreaks(brpushed)
                    // Stock game never echoes the id (outgauge.lua: o.id = 0) — constant.
                    sendpacket.setID(0)

                    val buffer = sendpacket.sendingByteArray
                    socket.send(DatagramPacket(buffer, buffer.size, host, Ports.GAME))
                }
            }
        } catch (e: IOException) {
            if (coroutineContext.isActive) Log.e(TAG, "UDP send error", e)
        }
    }

    /**
     * Receives OutGauge telemetry packets from BeamNG on port 4445 —
     * sent by the game's built-in OutGauge protocol (Options > Other).
     */
    private suspend fun runReceiver() {
        try {
            val sock = DatagramSocket(null)
            receiverSocket = sock
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(Ports.APP))
            sock.soTimeout = 2000

            val buf = ByteArray(128)
            while (coroutineContext.isActive) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    sock.receive(dp)

                    if (dp.length < Receivepacket.MIN_PACKET_LENGTH) {
                        continue
                    }

                    val p = Receivepacket(buf, dp.length)
                    if (p.isValid) {
                        withContext(Dispatchers.Main) { onTelemetry(p) }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal, loop continues
                } catch (e: IOException) {
                    if (coroutineContext.isActive) Log.e(TAG, "Receive error: " + e.message)
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Cannot bind to port ${Ports.APP}", e)
        } finally {
            receiverSocket?.let { if (!it.isClosed) it.close() }
            receiverSocket = null
        }
    }

    /** UI-thread telemetry handler (called from the receiver coroutine). */
    private fun onTelemetry(p: Receivepacket) {
        if (isFinishing || isDestroyed) return

        telemetry.connected = true
        telemetry.speedMs = p.speed
        telemetry.rpm = p.rpm
        telemetry.gear = p.gear
        telemetry.fuel = p.fuel
        telemetry.engTemp = p.engineTemp
        telemetry.turbo = p.turbo
        telemetry.hasTurbo = p.hasTurbo

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

    companion object {
        private const val TAG = "BeamNG"
        private const val IMPACT_VIBRATION_COOLDOWN_MS = 500L
    }
}
