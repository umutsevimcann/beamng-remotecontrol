package com.beamng.remotecontrol

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.beamng.remotecontrol.input.ButtonInputHandler
import com.beamng.remotecontrol.input.InputHandlerFactory
import com.beamng.remotecontrol.input.SliderInputHandler
import com.beamng.remotecontrol.input.SteeringInputHandler
import com.beamng.remotecontrol.settings.SettingsManager

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    // UI Elements
    private var mainLayout: RelativeLayout? = null
    private lateinit var throttle: View
    private lateinit var breaks: View
    private var throttleFill: View? = null
    private var brakeFill: View? = null
    private lateinit var textSpeed: TextView
    private lateinit var textGear: TextView
    private lateinit var textUnit: TextView
    private lateinit var textDelay: TextView
    private var telemetryConnected = false
    private var lightViews = arrayOfNulls<ImageView>(11)
    private lateinit var menu: ImageButton
    private lateinit var buttonControlsContainer: LinearLayout
    private lateinit var btnSteerLeft: Button
    private lateinit var btnSteerRight: Button
    private lateinit var steeringSlider: SeekBar

    // Thread-safe control values (written UI thread, read sender thread)
    @Volatile private var thrpushed = 0f
    @Volatile private var brpushed = 0f

    // Networking
    private var iadress: String? = null
    private val sendingTimeout = 10L // 100Hz for responsive controls
    private var sessionsender: UdpSessionSender? = null
    private var sessionreceiver: UdpSessionReceiver? = null
    private var hostAddress: InetAddress? = null

    // Packet tracking
    private var lpTime = 0L
    private var timeDiff = 0L
    private var oldDiff = 1L
    private var pID = 1
    private var lastID = 0

    // Thread pool
    private lateinit var executor: ThreadPoolExecutor
    private var mDecodeWorkQueue: BlockingQueue<Runnable>? = null

    // Modular Input System
    private lateinit var settingsManager: SettingsManager
    @Volatile private var steeringInputHandler: SteeringInputHandler? = null

    // Haptic Feedback
    private var vibrator: Vibrator? = null
    private var oldGearString = ""
    private var oldSpeedForImpact = 0
    private var lastImpactVibrationTime = 0L

    // Settings
    private var useKMH = 0

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideSystemUI()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()
        setContentView(R.layout.activity_main)

        // Validate host address - must be set via QR scan before reaching here
        hostAddress = (application as RemoteControlApplication).hostAddress
        iadress = (application as RemoteControlApplication).ip

        if (hostAddress == null) {
            Toast.makeText(this, "No connection - please scan QR code first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        initThreadPool()
        initInputSystem()
        initControls()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lpTime = System.currentTimeMillis()
        timeDiff = 0L
    }

    private fun initViews() {
        mainLayout = findViewById(R.id.main)
        textSpeed = findViewById(R.id.Textspeed)
        textGear = findViewById(R.id.Textgear)
        textUnit = findViewById(R.id.Textunit)
        textDelay = findViewById(R.id.Textdelay)

        lightViews = arrayOfNulls(11)
        // Index = bit position in the game's showLights field. Stock OutGauge has no
        // low-beam bit (index 0 is the shift light), so there is no headlight icon.
        lightViews[10] = findViewById(R.id.light_abs)
        lightViews[2] = findViewById(R.id.light_break)
        lightViews[1] = findViewById(R.id.light_fullbeam)
        lightViews[5] = findViewById(R.id.light_leftindicator)
        lightViews[6] = findViewById(R.id.light_rightindicator)

        throttle = findViewById(R.id.throttlecontrol)
        breaks = findViewById(R.id.breakcontrol)
        throttleFill = findViewById(R.id.throttleFill)
        brakeFill = findViewById(R.id.brakeFill)
        menu = findViewById(R.id.menuButton)
        buttonControlsContainer = findViewById(R.id.buttonControlsContainer)
        btnSteerLeft = findViewById(R.id.btnSteerLeft)
        btnSteerRight = findViewById(R.id.btnSteerRight)
        steeringSlider = findViewById(R.id.steeringSlider)
    }

    private fun initThreadPool() {
        val queue = LinkedBlockingQueue<Runnable>()
        mDecodeWorkQueue = queue
        val cores = maxOf(2, Runtime.getRuntime().availableProcessors())
        executor = ThreadPoolExecutor(cores, cores, 1, TimeUnit.SECONDS, queue)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initControls() {
        throttle.setOnTouchListener { v, event ->
            val analog = settingsManager.isAnalogPedals
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    thrpushed = if (analog) {
                        // Analog: Y position determines value (bottom=0, top=1)
                        (1f - (event.y / v.height)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    updatePedalFill(throttleFill, thrpushed)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    thrpushed = 0f
                    updatePedalFill(throttleFill, 0f)
                }
            }
            true
        }

        breaks.setOnTouchListener { v, event ->
            val analog = settingsManager.isAnalogPedals
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    brpushed = if (analog) {
                        (1f - (event.y / v.height)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    updatePedalFill(brakeFill, brpushed)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    brpushed = 0f
                    updatePedalFill(brakeFill, 0f)
                }
            }
            true
        }

        menu.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initInputSystem() {
        settingsManager = SettingsManager.getInstance(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        setupInputHandler()
    }

    /**
     * Creates the correct input handler based on settings and configures UI.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupInputHandler() {
        steeringInputHandler?.stop()

        val handler = InputHandlerFactory.createHandler(this)
        steeringInputHandler = handler

        buttonControlsContainer.visibility = View.GONE
        steeringSlider.visibility = View.GONE

        when (settingsManager.controlType) {
            SettingsManager.CONTROL_BUTTONS -> {
                buttonControlsContainer.visibility = View.VISIBLE
                val buttonHandler = handler as ButtonInputHandler
                btnSteerLeft.setOnTouchListener(buttonHandler.leftButtonListener)
                btnSteerRight.setOnTouchListener(buttonHandler.rightButtonListener)
            }
            SettingsManager.CONTROL_SLIDER -> {
                steeringSlider.visibility = View.VISIBLE
                (handler as SliderInputHandler).attachSlider(steeringSlider)
            }
        }

        useKMH = if (settingsManager.useMetricUnits()) 1 else 0
        textUnit.text = if (useKMH == 1) "Km/h" else "MPH"
    }

    // ==================== LIFECYCLE ====================

    public override fun onResume() {
        super.onResume()
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
        if (this::executor.isInitialized) {
            executor.shutdownNow()
        }
    }

    // ==================== PEDAL VISUALS ====================

    private fun updatePedalFill(fill: View?, value: Float) {
        if (fill == null) return
        val parent = fill.parent as View
        val parentHeight = parent.height
        if (parentHeight <= 0) return
        fill.layoutParams.height = Math.round(parentHeight * value)
        fill.requestLayout()
    }

    // ==================== IMMERSIVE UI ====================

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
            vib.vibrate(ms)
        }
    }

    private fun processHapticFeedback(packet: Receivepacket, newSpeed: Int) {
        if (!settingsManager.isHapticEnabled) return

        val now = System.currentTimeMillis()

        // 1. Gear Change
        val currentGear = packet.gear
        if (currentGear != oldGearString) {
            doVibrate(50, VibrationEffect.DEFAULT_AMPLITUDE)
            oldGearString = currentGear
        }

        // 2. Impact Detection with cooldown
        val speedDiff = kotlin.math.abs(newSpeed - oldSpeedForImpact)
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
        if (packet.brake > 0.85f && (now - lastImpactVibrationTime) > 100) {
            doVibrate(40, 60)
        }
    }

    // ==================== NETWORKING ====================

    fun connectionTimeout() {
        sessionsender?.cancel(true)
        sessionreceiver?.cancel(true)
        Toast.makeText(this, "Connection timed out", Toast.LENGTH_LONG).show()
        executor.shutdownNow()
        initThreadPool()
    }

    private fun startUdpTasks() {
        stopUdpTasks()
        val host = hostAddress ?: return
        sessionsender = UdpSessionSender(host).also { it.executeOnExecutor(executor) }
        sessionreceiver = UdpSessionReceiver(host).also { it.executeOnExecutor(executor) }
    }

    private fun stopUdpTasks() {
        sessionsender?.cancel(true)
        sessionreceiver?.cancel() // Custom cancel that also closes socket
    }

    // ==================== UDP SENDER ====================

    /**
     * Sends control packets to BeamNG on port 4444.
     * BeamNG's remoteController.lua listens on port 4444 and reverses the byte
     * array before parsing as {w, x, y, z} floats where z=steering, y=throttle,
     * x=brake, w=id.
     */
    inner class UdpSessionSender(private val receiverAddress: InetAddress) :
        AsyncTask<String, String, String>() {

        override fun doInBackground(vararg arg0: String?): String? {
            try {
                DatagramSocket().use { socket ->
                    val sendpacket = Sendpacket()
                    while (!isCancelled) {
                        try {
                            Thread.sleep(sendingTimeout)
                        } catch (e: InterruptedException) {
                            break
                        }

                        var steeringValue = 0.5f
                        val handler = steeringInputHandler
                        if (handler != null) {
                            // BeamNG protocol after byte-reverse: z maps to steering (0=right, 1=left)
                            steeringValue = ((1f - handler.getSteeringValue()) / 2f).coerceIn(0f, 1f)
                        }
                        sendpacket.setSteeringAngle(steeringValue)
                        sendpacket.setThrottle(thrpushed)
                        sendpacket.setBreaks(brpushed)
                        sendpacket.setID(pID)

                        if (lastID != pID) {
                            lastID = pID
                            lpTime = System.currentTimeMillis()
                        }

                        val buffer = sendpacket.sendingByteArray
                        socket.send(DatagramPacket(buffer, buffer.size, receiverAddress, SEND_PORT))
                    }
                }
            } catch (e: IOException) {
                if (!isCancelled) Log.e(TAG, "UDP send error", e)
            }
            return null
        }
    }

    // ==================== UDP RECEIVER ====================

    /**
     * Receives OutGauge telemetry packets from BeamNG on port 4445.
     * Sent either by the game's built-in OutGauge protocol (Options > Other)
     * or by the companion mod (which also echoes the packet id).
     */
    inner class UdpSessionReceiver(
        @Suppress("unused") private val expectedHost: InetAddress
    ) : AsyncTask<String, String, String>() {

        private var packet: Receivepacket? = null
        private var socket: DatagramSocket? = null

        fun cancel() {
            super.cancel(true)
            socket?.let { if (!it.isClosed) it.close() }
        }

        override fun doInBackground(vararg arg0: String?): String? {
            try {
                val sock = DatagramSocket(null)
                socket = sock
                sock.reuseAddress = true
                sock.bind(InetSocketAddress(RECEIVE_PORT))
                sock.soTimeout = 2000

                val buf = ByteArray(128)
                while (!isCancelled) {
                    try {
                        val dp = DatagramPacket(buf, buf.size)
                        sock.receive(dp)

                        if (dp.length < Receivepacket.MIN_PACKET_LENGTH) {
                            continue
                        }

                        packet = Receivepacket(buf, dp.length)
                        if (packet?.isValid == true) {
                            publishProgress("")
                        }
                    } catch (e: SocketTimeoutException) {
                        // Normal, loop continues
                    } catch (e: IOException) {
                        if (!isCancelled) Log.e(TAG, "Receive error: " + e.message)
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Cannot bind to port $RECEIVE_PORT", e)
            } finally {
                socket?.let { if (!it.isClosed) it.close() }
            }
            return null
        }

        override fun onProgressUpdate(vararg values: String?) {
            if (isFinishing || isDestroyed) return

            if (values.isNotEmpty() && "TIMEOUT" == values[0]) {
                cancel(true)
                connectionTimeout()
                return
            }

            val p = packet
            if (p == null || !p.isValid) return

            // Stock-game OutGauge (Options > Other) never echoes our packet id
            // (id is always 0), so flowing telemetry itself is the connect signal.
            if (!telemetryConnected) {
                telemetryConnected = true
                textDelay.text = "Connected"
                textDelay.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_connected))
            }

            // Latency tracking (works only when the id echo round-trips, e.g. companion mod)
            if (p.id == pID) {
                timeDiff = System.currentTimeMillis() - lpTime
                var disDiff = Math.round(((oldDiff + timeDiff) / 2).toFloat())
                disDiff /= 2
                if (timeDiff != 0L) {
                    textDelay.text = "Delay: ${disDiff}ms"
                }
                pID++
                if (pID == 128) pID = 0
                oldDiff = timeDiff
            }

            // Speed conversion - direct calculation
            val newSpeed = if (useKMH == 1) {
                Math.round(3.6f * p.speed)
            } else {
                Math.round(2.23694f * p.speed)
            }

            textSpeed.text = String.format("%03d", newSpeed)
            textGear.text = p.gear

            // Haptic feedback with proper debouncing
            processHapticFeedback(p, newSpeed)

            // Update dashboard lights
            val lightsarray = p.activeLightsArr
            for (i in 0 until 11) {
                lightViews[i]?.visibility = if (lightsarray[i]) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    companion object {
        private const val TAG = "BeamNG"
        private const val IMPACT_VIBRATION_COOLDOWN_MS = 500L
        private const val SEND_PORT = 4444
        private const val RECEIVE_PORT = 4445 // BeamNG sends OutGauge here (listenPort + 1)

        @JvmField
        var id: String = ""
    }
}
