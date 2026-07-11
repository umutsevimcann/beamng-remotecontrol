package com.beamng.remotecontrol.input;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Tilt-based steering using Android's fused rotation vector sensor.
 * Uses GAME_ROTATION_VECTOR (accel+gyro fusion, no magnetometer).
 * Pipeline: Rotation Vector → Rotation Matrix → Landscape Remap → Orientation → Dead Zone → Smoothing → Power Curve
 */
public class GyroscopeInputHandler implements SteeringInputHandler, SensorEventListener {

    private static final float SMOOTHING = 0.5f;
    private static final float MAX_STEER_ANGLE = 45f;
    private static final float STEERING_EXPONENT = 1.4f;

    private final SensorManager sensorManager;
    private final SettingsManager settings;
    private final Display display;

    private volatile float currentSteering = 0f;
    private float smoothedRoll = 0f;
    private float calibrationOffset = 0f;
    private boolean calibrated = false;
    private boolean useAccelerometerFallback = false;
    private int lastRotation = -1;

    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final float[] accelSamples = new float[8];
    private int accelSampleIndex = 0;
    private int accelSampleCount = 0;

    public GyroscopeInputHandler(Context context) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.settings = SettingsManager.getInstance(context);
        this.display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }

    @Override
    public float getSteeringValue() {
        return currentSteering;
    }

    @Override
    public void start() {
        calibrated = false;
        smoothedRoll = 0f;
        currentSteering = 0f;

        // Try best sensor first, fallback to accelerometer-only for phones without gyro
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (sensor != null) {
            useAccelerometerFallback = false;
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // No gyro on this phone — use raw accelerometer
            useAccelerometerFallback = true;
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    @Override
    public void stop() {
        sensorManager.unregisterListener(this);
        currentSteering = 0f;
        smoothedRoll = 0f;
    }

    @Override
    public boolean requiresUIControls() {
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // sensorLandscape allows both 90° and 270° rotations; the axis remap below
        // assumes ROTATION_90, so the roll sign must flip in reverse landscape.
        // The flip can happen mid-session (configChanges suppresses recreation),
        // so re-center the calibration whenever the rotation changes.
        int rotation = display.getRotation();
        if (rotation != lastRotation) {
            lastRotation = rotation;
            calibrated = false;
            smoothedRoll = 0f;
        }

        float rollDegrees;

        if (useAccelerometerFallback && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Accelerometer fallback: use tilt angle from gravity
            // Android reports accelerometer in device coordinates (portrait).
            // In landscape (sensorLandscape), we use values[1] for steering.
            // Positive values[1] = tilt left, negative = tilt right
            float gx = event.values[0];
            float gy = event.values[1];
            float gz = event.values[2];
            float magnitude = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (magnitude < 0.1f) magnitude = 1f;
            // gy / magnitude gives sin of tilt angle. Positive = left tilt.
            float rawAngle = (float) (Math.asin(gy / magnitude) * 180 / Math.PI);

            // Rolling average for noise reduction
            accelSamples[accelSampleIndex] = rawAngle;
            accelSampleIndex = (accelSampleIndex + 1) % 8;
            if (accelSampleCount < 8) accelSampleCount++;

            float avg = 0f;
            for (int i = 0; i < accelSampleCount; i++) avg += accelSamples[i];
            rollDegrees = avg / accelSampleCount;

        } else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR
                || event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.remapCoordinateSystem(rotationMatrix,
                    SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, remappedMatrix);
            SensorManager.getOrientation(remappedMatrix, orientation);
            rollDegrees = (float) Math.toDegrees(orientation[2]);
        } else {
            return;
        }

        if (rotation == Surface.ROTATION_270) {
            rollDegrees = -rollDegrees;
        }

        // Auto-calibrate: first reading = center position
        if (!calibrated) {
            calibrationOffset = rollDegrees;
            calibrated = true;
        }

        float rawAngle = rollDegrees - calibrationOffset;

        // Dead zone with smooth transition
        float deadZone = settings.getDeadZone();
        if (Math.abs(rawAngle) < deadZone) {
            rawAngle = 0f;
        } else {
            float sign = rawAngle > 0 ? 1f : -1f;
            rawAngle = sign * (Math.abs(rawAngle) - deadZone);
        }

        // Low-pass filter
        smoothedRoll = smoothedRoll * (1f - SMOOTHING) + rawAngle * SMOOTHING;

        // Normalize to -1..1
        float sensitivity = settings.getSensitivity();
        float effectiveMax = MAX_STEER_ANGLE / Math.max(0.1f, sensitivity);
        float normalized = Math.max(-1f, Math.min(1f, smoothedRoll / effectiveMax));

        // Power curve: precise near center, aggressive at extremes
        float sign = Math.signum(normalized);
        currentSteering = sign * (float) Math.pow(Math.abs(normalized), STEERING_EXPONENT);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
