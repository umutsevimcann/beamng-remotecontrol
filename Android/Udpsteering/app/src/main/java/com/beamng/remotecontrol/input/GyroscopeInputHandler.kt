package com.beamng.remotecontrol.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager

import com.beamng.remotecontrol.settings.SettingsManager

/**
 * Tilt-based steering using Android's fused rotation vector sensor.
 * Uses GAME_ROTATION_VECTOR (accel+gyro fusion, no magnetometer).
 * Pipeline: Rotation Vector → Rotation Matrix → Landscape Remap → Orientation
 *           → Dead Zone → Smoothing → Power Curve
 */
class GyroscopeInputHandler(context: Context) : SteeringInputHandler, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val settings: SettingsManager = SettingsManager.getInstance(context)
    @Suppress("DEPRECATION")
    private val display: Display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    @Volatile
    private var currentSteering = 0f
    private var smoothedRoll = 0f
    private var calibrationOffset = 0f
    private var calibrated = false
    private var useAccelerometerFallback = false
    private var lastRotation = -1

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val accelSamples = FloatArray(8)
    private var accelSampleIndex = 0
    private var accelSampleCount = 0

    override fun getSteeringValue(): Float = currentSteering

    override fun start() {
        calibrated = false
        smoothedRoll = 0f
        currentSteering = 0f

        // Try best sensor first, fallback to accelerometer-only for phones without gyro
        var sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
        if (sensor != null) {
            useAccelerometerFallback = false
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            // No gyro on this phone — use raw accelerometer
            useAccelerometerFallback = true
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        currentSteering = 0f
        smoothedRoll = 0f
    }

    override fun requiresUIControls(): Boolean = false

    override fun onSensorChanged(event: SensorEvent) {
        // sensorLandscape allows both 90° and 270° rotations; the axis remap below
        // assumes ROTATION_90, so the roll sign must flip in reverse landscape.
        // The flip can happen mid-session (configChanges suppresses recreation),
        // so re-center the calibration whenever the rotation changes.
        val rotation = display.rotation
        if (rotation != lastRotation) {
            lastRotation = rotation
            calibrated = false
            smoothedRoll = 0f
        }

        var rollDegrees: Float

        if (useAccelerometerFallback && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Accelerometer fallback: use tilt angle from gravity.
            // Android reports accelerometer in device coordinates (portrait);
            // in landscape values[1] carries the steering tilt (positive = left).
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]
            var magnitude = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
            if (magnitude < 0.1f) magnitude = 1f
            // gy / magnitude gives sin of tilt angle. Positive = left tilt.
            val rawAngle = Math.toDegrees(kotlin.math.asin(gy / magnitude).toDouble()).toFloat()

            // Rolling average for noise reduction
            accelSamples[accelSampleIndex] = rawAngle
            accelSampleIndex = (accelSampleIndex + 1) % 8
            if (accelSampleCount < 8) accelSampleCount++

            var avg = 0f
            for (i in 0 until accelSampleCount) avg += accelSamples[i]
            rollDegrees = avg / accelSampleCount

        } else if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR
            || event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientation)
            rollDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat()
        } else {
            return
        }

        if (rotation == Surface.ROTATION_270) {
            rollDegrees = -rollDegrees
        }

        // Auto-calibrate: first reading = center position
        if (!calibrated) {
            calibrationOffset = rollDegrees
            calibrated = true
        }

        var rawAngle = rollDegrees - calibrationOffset

        // Dead zone with smooth transition
        val deadZone = settings.deadZone
        rawAngle = if (kotlin.math.abs(rawAngle) < deadZone) {
            0f
        } else {
            val sign = if (rawAngle > 0) 1f else -1f
            sign * (kotlin.math.abs(rawAngle) - deadZone)
        }

        // Low-pass filter
        smoothedRoll = smoothedRoll * (1f - SMOOTHING) + rawAngle * SMOOTHING

        // Normalize to -1..1
        val sensitivity = settings.sensitivity
        val effectiveMax = MAX_STEER_ANGLE / maxOf(0.1f, sensitivity)
        val normalized = (smoothedRoll / effectiveMax).coerceIn(-1f, 1f)

        // Power curve: precise near center, aggressive at extremes
        val sign = kotlin.math.sign(normalized)
        currentSteering = sign * Math.pow(
            kotlin.math.abs(normalized).toDouble(), STEERING_EXPONENT.toDouble()
        ).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    companion object {
        private const val SMOOTHING = 0.5f
        private const val MAX_STEER_ANGLE = 45f
        private const val STEERING_EXPONENT = 1.4f
    }
}
