package com.beamng.remotecontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.beamng.remotecontrol.input.ButtonInputHandler;
import com.beamng.remotecontrol.input.InputHandlerFactory;
import com.beamng.remotecontrol.input.SliderInputHandler;
import com.beamng.remotecontrol.input.SteeringInputHandler;
import com.beamng.remotecontrol.settings.SettingsManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BeamNG";

    // UI Elements
    private RelativeLayout mainLayout;
    private View throttle;
    private View breaks;
    private View throttleFill;
    private View brakeFill;
    private TextView textSpeed;
    private TextView textGear;
    private TextView textUnit;
    private TextView textDelay;
    private boolean telemetryConnected = false;
    private ImageView[] lightViews;
    private ImageButton menu;
    private LinearLayout buttonControlsContainer;
    private Button btnSteerLeft;
    private Button btnSteerRight;
    private SeekBar steeringSlider;

    // Thread-safe control values (written UI thread, read sender thread)
    private volatile float thrpushed;
    private volatile float brpushed;

    // Networking
    private String Iadress;
    private int sendingTimeout = 10; // 100Hz for responsive controls
    private UdpSessionSender sessionsender;
    private UdpSessionReceiver sessionreceiver;
    private InetAddress hostAddress;

    // Packet tracking
    private long lpTime;
    private long timeDiff;
    private long oldDiff = 1L;
    private int pID = 1;
    private int lastID = 0;

    // Thread pool
    private ThreadPoolExecutor executor;
    private BlockingQueue<Runnable> mDecodeWorkQueue;

    // Modular Input System
    private SettingsManager settingsManager;
    private volatile SteeringInputHandler steeringInputHandler;

    // Haptic Feedback
    private Vibrator vibrator;
    private String oldGearString = "";
    private int oldSpeedForImpact = 0;
    private long lastImpactVibrationTime = 0;
    private static final long IMPACT_VIBRATION_COOLDOWN_MS = 500;

    // Settings
    private int useKMH = 0;

    public static String id = "";

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            hideSystemUI();
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemUI();
        setContentView(R.layout.activity_main);

        // Validate host address - must be set via QR scan before reaching here
        hostAddress = ((RemoteControlApplication) getApplication()).getHostAddress();
        Iadress = ((RemoteControlApplication) getApplication()).getIp();

        if (hostAddress == null) {
            Toast.makeText(this, "No connection - please scan QR code first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        initThreadPool();
        initInputSystem();
        initControls();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        lpTime = System.currentTimeMillis();
        timeDiff = 0L;
    }

    private void initViews() {
        mainLayout = findViewById(R.id.main);
        textSpeed = findViewById(R.id.Textspeed);
        textGear = findViewById(R.id.Textgear);
        textUnit = findViewById(R.id.Textunit);
        textDelay = findViewById(R.id.Textdelay);

        lightViews = new ImageView[11];
        // Index = bit position in the game's showLights field. Stock OutGauge has no
        // low-beam bit (index 0 is the shift light), so there is no headlight icon.
        lightViews[10] = findViewById(R.id.light_abs);
        lightViews[2] = findViewById(R.id.light_break);
        lightViews[1] = findViewById(R.id.light_fullbeam);
        lightViews[5] = findViewById(R.id.light_leftindicator);
        lightViews[6] = findViewById(R.id.light_rightindicator);

        throttle = findViewById(R.id.throttlecontrol);
        breaks = findViewById(R.id.breakcontrol);
        throttleFill = findViewById(R.id.throttleFill);
        brakeFill = findViewById(R.id.brakeFill);
        menu = findViewById(R.id.menuButton);
        buttonControlsContainer = findViewById(R.id.buttonControlsContainer);
        btnSteerLeft = findViewById(R.id.btnSteerLeft);
        btnSteerRight = findViewById(R.id.btnSteerRight);
        steeringSlider = findViewById(R.id.steeringSlider);
    }

    private void initThreadPool() {
        mDecodeWorkQueue = new LinkedBlockingQueue<>();
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor = new ThreadPoolExecutor(cores, cores, 1, TimeUnit.SECONDS, mDecodeWorkQueue);
    }

    private void initControls() {
        throttle.setOnTouchListener((v, event) -> {
            boolean analog = settingsManager != null && settingsManager.isAnalogPedals();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    if (analog) {
                        // Analog: Y position determines value (bottom=0, top=1)
                        float ratio = 1f - (event.getY() / v.getHeight());
                        thrpushed = Math.max(0f, Math.min(1f, ratio));
                    } else {
                        thrpushed = 1f;
                    }
                    updatePedalFill(throttleFill, thrpushed);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thrpushed = 0f;
                    updatePedalFill(throttleFill, 0f);
                    break;
            }
            return true;
        });

        breaks.setOnTouchListener((v, event) -> {
            boolean analog = settingsManager != null && settingsManager.isAnalogPedals();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    if (analog) {
                        float ratio = 1f - (event.getY() / v.getHeight());
                        brpushed = Math.max(0f, Math.min(1f, ratio));
                    } else {
                        brpushed = 1f;
                    }
                    updatePedalFill(brakeFill, brpushed);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    brpushed = 0f;
                    updatePedalFill(brakeFill, 0f);
                    break;
            }
            return true;
        });

        menu.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void initInputSystem() {
        settingsManager = SettingsManager.getInstance(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        setupInputHandler();
    }

    /**
     * Creates the correct input handler based on settings and configures UI.
     */
    private void setupInputHandler() {
        if (steeringInputHandler != null) {
            steeringInputHandler.stop();
        }

        steeringInputHandler = InputHandlerFactory.createHandler(this);

        buttonControlsContainer.setVisibility(View.GONE);
        steeringSlider.setVisibility(View.GONE);

        int controlType = settingsManager.getControlType();

        if (controlType == SettingsManager.CONTROL_BUTTONS) {
            buttonControlsContainer.setVisibility(View.VISIBLE);
            ButtonInputHandler buttonHandler = (ButtonInputHandler) steeringInputHandler;
            btnSteerLeft.setOnTouchListener(buttonHandler.getLeftButtonListener());
            btnSteerRight.setOnTouchListener(buttonHandler.getRightButtonListener());

        } else if (controlType == SettingsManager.CONTROL_SLIDER) {
            steeringSlider.setVisibility(View.VISIBLE);
            SliderInputHandler sliderHandler = (SliderInputHandler) steeringInputHandler;
            sliderHandler.attachSlider(steeringSlider);
        }

        useKMH = settingsManager.useMetricUnits() ? 1 : 0;
        textUnit.setText(useKMH == 1 ? "Km/h" : "MPH");
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onResume() {
        super.onResume();
        setupInputHandler();

        if (steeringInputHandler != null) {
            steeringInputHandler.start();
        }

        startUdpTasks();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUdpTasks();

        if (steeringInputHandler != null) {
            steeringInputHandler.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUdpTasks();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ==================== PEDAL VISUALS ====================

    private void updatePedalFill(View fill, float value) {
        if (fill == null) return;
        View parent = (View) fill.getParent();
        int parentHeight = parent.getHeight();
        if (parentHeight <= 0) return;
        int fillHeight = Math.round(parentHeight * value);
        fill.getLayoutParams().height = fillHeight;
        fill.requestLayout();
    }

    // ==================== IMMERSIVE UI ====================

    private void hideSystemUI() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // ==================== HAPTIC FEEDBACK ====================

    @SuppressWarnings("deprecation")
    private void doVibrate(long ms, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void processHapticFeedback(Receivepacket packet, int newSpeed) {
        if (settingsManager == null || !settingsManager.isHapticEnabled()) return;

        long now = System.currentTimeMillis();

        // 1. Gear Change
        String currentGear = packet.getGear();
        if (currentGear != null && !currentGear.equals(oldGearString)) {
            doVibrate(50, VibrationEffect.DEFAULT_AMPLITUDE);
            oldGearString = currentGear;
        }

        // 2. Impact Detection with cooldown
        int speedDiff = Math.abs(newSpeed - oldSpeedForImpact);
        if (speedDiff > 20 && (now - lastImpactVibrationTime) > IMPACT_VIBRATION_COOLDOWN_MS) {
            if (speedDiff > 50) {
                doVibrate(600, 255);       // Severe crash
            } else if (speedDiff > 35) {
                doVibrate(300, 180);       // Medium impact
            } else {
                doVibrate(150, 100);       // Minor bump
            }
            lastImpactVibrationTime = now;
        }
        oldSpeedForImpact = newSpeed;

        // 3. ABS feel (hard braking)
        float brakeValue = packet.getBrake();
        if (brakeValue > 0.85f && (now - lastImpactVibrationTime) > 100) {
            doVibrate(40, 60);
        }
    }

    // ==================== NETWORKING ====================

    public void connectionTimeout() {
        if (sessionsender != null) sessionsender.cancel(true);
        if (sessionreceiver != null) sessionreceiver.cancel(true);
        Toast.makeText(this, "Connection timed out", Toast.LENGTH_LONG).show();
        executor.shutdownNow();
        mDecodeWorkQueue = new LinkedBlockingQueue<>();
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor = new ThreadPoolExecutor(cores, cores, 1, TimeUnit.SECONDS, mDecodeWorkQueue);
    }

    private void startUdpTasks() {
        stopUdpTasks();
        if (hostAddress == null) return;
        sessionsender = new UdpSessionSender(hostAddress);
        sessionsender.executeOnExecutor(executor);
        sessionreceiver = new UdpSessionReceiver(hostAddress);
        sessionreceiver.executeOnExecutor(executor);
    }

    private void stopUdpTasks() {
        if (sessionsender != null) {
            sessionsender.cancel(true);
        }
        if (sessionreceiver != null) {
            sessionreceiver.cancel(); // Custom cancel that also closes socket
        }
    }

    // ==================== UDP SENDER ====================

    /**
     * Sends control packets to BeamNG on port 4444.
     * BeamNG's remoteController.lua listens on port 4444 and reverses the byte array
     * before parsing as {w, x, y, z} floats where z=steering, x=throttle, y=brake, w=id.
     */
    public class UdpSessionSender extends AsyncTask<String, String, String> {
        static final int SEND_PORT = 4444;
        final InetAddress receiverAddress;

        public UdpSessionSender(InetAddress iadrSend) {
            this.receiverAddress = iadrSend;
        }

        @Override
        protected String doInBackground(String... arg0) {
            try (DatagramSocket socket = new DatagramSocket()) {
                Sendpacket sendpacket = new Sendpacket();
                while (!isCancelled()) {
                    try {
                        Thread.sleep(sendingTimeout);
                    } catch (InterruptedException e) {
                        break;
                    }

                    float steeringValue = 0.5f;
                    SteeringInputHandler handler = steeringInputHandler;
                    if (handler != null) {
                        // BeamNG protocol after byte-reverse: z maps to steering (0=right, 1=left)
                        steeringValue = Math.max(0f, Math.min(1f, (1f - handler.getSteeringValue()) / 2f));
                    }
                    sendpacket.setSteeringAngle(steeringValue);
                    sendpacket.setThrottle(thrpushed);
                    sendpacket.setBreaks(brpushed);
                    sendpacket.setID(pID);

                    if (lastID != pID) {
                        lastID = pID;
                        lpTime = System.currentTimeMillis();
                    }

                    byte[] buffer = sendpacket.getSendingByteArray();
                    socket.send(new DatagramPacket(buffer, buffer.length, receiverAddress, SEND_PORT));
                }
            } catch (IOException e) {
                if (!isCancelled()) Log.e(TAG, "UDP send error", e);
            }
            return null;
        }
    }

    // ==================== UDP RECEIVER ====================

    /**
     * Receives OutGauge telemetry packets from BeamNG on port 4445.
     * BeamNG's remoteController.lua sends outgauge data to appPort (listenPort+1 = 4445).
     */
    public class UdpSessionReceiver extends AsyncTask<String, String, String> {
        static final int RECEIVE_PORT = 4445; // BeamNG sends outgauge here (listenPort + 1)
        final InetAddress expectedHost;
        Receivepacket packet;
        DatagramSocket socket;

        public UdpSessionReceiver(InetAddress iadrSend) {
            this.expectedHost = iadrSend;
        }

        public void cancel() {
            super.cancel(true);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }

        @Override
        protected String doInBackground(String... arg0) {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(RECEIVE_PORT));
                socket.setSoTimeout(2000);

                byte[] buf = new byte[128];
                while (!isCancelled()) {
                    try {
                        DatagramPacket dp = new DatagramPacket(buf, buf.length);
                        socket.receive(dp);

                        if (dp.getLength() < Receivepacket.MIN_PACKET_LENGTH) {
                            continue;
                        }

                        packet = new Receivepacket(buf, dp.getLength());
                        if (packet.isValid()) {
                            publishProgress("");
                        }
                    } catch (SocketTimeoutException e) {
                        // Normal, loop continues
                    } catch (IOException e) {
                        if (!isCancelled()) Log.e(TAG, "Receive error: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Cannot bind to port " + RECEIVE_PORT, e);
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (isFinishing() || isDestroyed()) return;

            if (values != null && values.length > 0 && "TIMEOUT".equals(values[0])) {
                cancel(true);
                connectionTimeout();
                return;
            }

            if (packet == null || !packet.isValid()) return;

            // Stock-game OutGauge (Options > Other) never echoes our packet id
            // (id is always 0), so flowing telemetry itself is the connect signal.
            if (!telemetryConnected) {
                telemetryConnected = true;
                textDelay.setText("Connected");
                textDelay.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_connected));
            }

            // Latency tracking (works only when the id echo round-trips, e.g. companion mod)
            if (packet.getID() == pID) {
                timeDiff = System.currentTimeMillis() - lpTime;
                int disDiff = Math.round((oldDiff + timeDiff) / 2);
                disDiff /= 2;
                if (timeDiff != 0)
                    textDelay.setText("Delay: " + disDiff + "ms");
                pID++;
                if (pID == 128) pID = 0;
                oldDiff = timeDiff;
            }

            // Speed conversion - direct calculation
            int newSpeed;
            if (useKMH == 1) {
                newSpeed = Math.round(3.6f * packet.getSpeed());
            } else {
                newSpeed = Math.round(2.23694f * packet.getSpeed());
            }

            textSpeed.setText(String.format("%03d", newSpeed));
            textGear.setText(packet.getGear());

            // Haptic feedback with proper debouncing
            processHapticFeedback(packet, newSpeed);

            // Update dashboard lights
            boolean[] lightsarray = packet.getActiveLightsArr();
            for (int i = 0; i < 11; i++) {
                if (lightViews[i] != null) {
                    lightViews[i].setVisibility(lightsarray[i] ? View.VISIBLE : View.INVISIBLE);
                }
            }
        }
    }
}
