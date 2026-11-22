package com.beamng.remotecontrol.input

import kotlin.math.sqrt

/**
 * Aim by turning the phone, on the walking screen: the gyroscope's rotation rate
 * becomes look drag in the units the mod passes to the game's mouse-look.
 *
 * Left/right is the rotation about "up" (the gravity direction), so it works the same
 * whether the phone is held upright, tilted or flat. Up/down is the rotation about the
 * screen's horizontal axis, which in landscape is device +y (rotation 90) or -y (270).
 */
object GyroLook {
    /** Look units per degree turned at the game's default field of view (≈ 4500 / 65). */
    const val UNITS_PER_DEGREE = 70f

    private const val DEG_PER_RAD = 57.29578f

    /**
     * [omega]: gyroscope rad/s about device x, y, z. [gravity]: the gravity sensor's
     * vector (pointing up). [rotation]: Surface.ROTATION_*. Returns (x, y) look drag:
     * x right, y down — the same sign as dragging the screen.
     */
    fun delta(omega: FloatArray, gravity: FloatArray, rotation: Int, dtSeconds: Float, speed: Float): Pair<Float, Float> {
        if (omega.size < 3 || gravity.size < 3 || !dtSeconds.isFinite() || dtSeconds <= 0f || !speed.isFinite()) return 0f to 0f
        if (omega.any { !it.isFinite() } || gravity.any { !it.isFinite() }) return 0f to 0f
        val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (g < 1f) return 0f to 0f // no gravity reading yet
        // Counter-clockwise about up = turning left.
        val yawRate = (omega[0] * gravity[0] + omega[1] * gravity[1] + omega[2] * gravity[2]) / g
        // About the screen's horizontal axis: positive tips the top edge towards you = look up.
        val pitchRate = when (rotation) {
            1 -> omega[1]   // ROTATION_90
            3 -> -omega[1]  // ROTATION_270
            2 -> -omega[0]  // ROTATION_180
            else -> omega[0]
        }
        val k = dtSeconds * DEG_PER_RAD * UNITS_PER_DEGREE * speed
        return (-yawRate * k) to (-pitchRate * k)
    }
}
