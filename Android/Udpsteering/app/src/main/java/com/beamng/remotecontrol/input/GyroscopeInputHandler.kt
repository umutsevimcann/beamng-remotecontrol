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
 * Pipeline: rotation vector → world-up vector → sideways tilt angle
 *           → dead zone → smoothing → linear map to the full-lock tilt
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
    private var useAccelerometerFallback = false
    private var lastRotation = -1

    private val rotationMatrix = FloatArray(9)
    private val accelSamples = FloatArray(8)
    private var accelSampleIndex = 0
    private var accelSampleCount = 0

    override fun getSteeringValue(): Float = currentSteering

    override fun start() {
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

    override fun onSensorChanged(event: SensorEvent) {
        // sensorLandscape allows both 90° and 270° rotations (handled below).
        // The flip can happen mid-session (configChanges suppresses recreation),
        // so reset the smoothing whenever the rotation changes.
        val rotation = display.rotation
        if (rotation != lastRotation) {
            lastRotation = rotation
            smoothedRoll = 0f
        }

        // "Up" (opposite of gravity) in device coordinates. Both sensor paths
        // yield the same physical vector, so both share one steering formula.
        val upX: Float
        val upY: Float
        val upZ: Float
        if (useAccelerometerFallback && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // At rest the accelerometer reads +g along the axis pointing up.
            upX = event.values[0]
            upY = event.values[1]
            upZ = event.values[2]
        } else if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR
            || event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Row 2 of the device→world rotation matrix is world-up in device coords.
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            upX = rotationMatrix[6]
            upY = rotationMatrix[7]
            upZ = rotationMatrix[8]
        } else {
            return
        }

        // Steering = how far the screen's left/right edge dips: the sideways share
        // of "up", as an angle. This is the one measure that behaves the same
        // however the phone is held — flat like a tray, tilted back, or upright
        // like a wheel — with neutral at 0° and no singularity. (An in-screen-plane
        // atan2 was tried and breaks when the phone lies flat-ish: tiny tilts read
        // as ±90° there.) Positive = right.
        var magnitude = kotlin.math.sqrt(upX * upX + upY * upY + upZ * upZ)
        if (magnitude < 0.1f) magnitude = 1f
        val side = tiltSide(rotation, upX / magnitude, upY / magnitude).coerceIn(-1f, 1f)
        var rollDegrees = Math.toDegrees(kotlin.math.asin(side.toDouble())).toFloat()

        if (useAccelerometerFallback) {
            // Rolling average for noise reduction (raw accelerometer is jittery)
            accelSamples[accelSampleIndex] = rollDegrees
            accelSampleIndex = (accelSampleIndex + 1) % 8
            if (accelSampleCount < 8) accelSampleCount++

            var avg = 0f
            for (i in 0 until accelSampleCount) avg += accelSamples[i]
            rollDegrees = avg / accelSampleCount
        }

        // No "first reading = center" calibration: this angle is absolute (0° =
        // screen level side to side), so level is always straight ahead. The old
        // auto-calibration latched whatever tilt the phone had when the screen
        // opened, which shifted the centre and left one direction short of full
        // lock (reported on device: left easy, right weak).
        var rawAngle = rollDegrees

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

        // Normalize to -1..1: full lock at the user's chosen tilt angle. Linear on
        // purpose — the game's own binding already softens the centre (linearity
        // 2.5 in bngremotectrlv1.json); stacking a second curve here made small
        // tilts do nothing and the last few degrees do everything.
        val lockAngle = settings.steeringLockDeg
        currentSteering = (smoothedRoll / lockAngle).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    companion object {
        private const val SMOOTHING = 0.5f
    }
}

/**
 * The component of "up" along the screen's left edge, for a unit up-vector in device
 * coordinates — i.e. how far the screen is tilted to the right, as a sine.
 *
 * Which device axis is "sideways" depends on how the screen is rotated. Phones on a
 * sensorLandscape screen report ROTATION_90 or ROTATION_270 (device Y is sideways;
 * signs confirmed on a device). Tablets whose natural orientation is landscape
 * report ROTATION_0 or ROTATION_180, where device X is sideways instead.
 */
internal fun tiltSide(rotation: Int, upX: Float, upY: Float): Float = when (rotation) {
    Surface.ROTATION_90 -> upY
    Surface.ROTATION_270 -> -upY
    Surface.ROTATION_180 -> upX
    else -> -upX // ROTATION_0
}
