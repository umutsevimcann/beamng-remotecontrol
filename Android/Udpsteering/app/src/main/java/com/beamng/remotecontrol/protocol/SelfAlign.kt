package com.beamng.remotecontrol.protocol

import kotlin.math.sqrt

/**
 * Where a steering wheel that has been let go comes to rest.
 *
 * A real car's front wheels are pulled into line with the direction the car is
 * travelling (caster): going straight that is the centre, but in a slide it is
 * counter-steer. The on-screen wheel does the same after release, using the slip
 * angle the game's MotionSim stream already gives us for the drift meter.
 */
object SelfAlign {
    /** How far a road car's front wheels turn each way; a slide this deep means full lock. */
    const val FRONT_WHEEL_LOCK_DEG = 35f

    /** Angle (degrees) and turning speed (degrees a second) of the on-screen wheel. */
    data class WheelMotion(val angle: Float, val speed: Float)

    /** Natural frequency of the released wheel at full strength, rad/s: about half a second to swing across. */
    private const val STIFFNESS_RATE = 7f

    /** Just under critical damping: the wheel arrives with a small overshoot, as a real one does. */
    private const val DAMPING_RATIO = 0.75f

    /** Faster than this and no hand could catch it again. */
    const val MAX_SPEED_DEG_S = 900f

    /** The pull at walking pace: enough to bring the wheel back, lazily. */
    const val MIN_STRENGTH = 0.2f

    private const val MIN_PULL = MIN_STRENGTH * ModProtocol.WHEEL_RETURN_MIN / 100f
    private const val MAX_PULL = ModProtocol.WHEEL_RETURN_MAX / 100f

    /** Speed (m/s, 90 km/h) at which the pull is at its full strength. */
    private const val FULL_STRENGTH_SPEED = 25f

    /**
     * Steering in -1..1 for a slip angle in degrees (positive = the car travels to
     * the right of where it points). Null or non-finite slip rests at the centre.
     */
    fun restPosition(slipDeg: Float?): Float {
        if (slipDeg == null || !slipDeg.isFinite()) return 0f
        return (slipDeg / FRONT_WHEEL_LOCK_DEG).coerceIn(-1f, 1f)
    }

    /**
     * How hard the road pulls the wheel into line: [MIN_STRENGTH]..1 with the car's
     * speed, times the player's "wheel return" setting (20..200 %).
     */
    fun strength(speedMs: Float, returnPercent: Int = ModProtocol.WHEEL_RETURN_DEFAULT): Float {
        val gain = returnPercent.coerceIn(ModProtocol.WHEEL_RETURN_MIN, ModProtocol.WHEEL_RETURN_MAX) / 100f
        if (!speedMs.isFinite()) return MIN_STRENGTH * gain
        return (speedMs / FULL_STRENGTH_SPEED).coerceIn(MIN_STRENGTH, 1f) * gain
    }

    /**
     * One frame of the released wheel, as force feedback would move it: the wheel has
     * weight, the road pulls it towards [targetDeg] with [strength], friction slows it.
     * So it gathers speed instead of jumping, and settles instead of stopping dead.
     */
    fun step(motion: WheelMotion, targetDeg: Float, strength: Float, dtSeconds: Float): WheelMotion {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f || !targetDeg.isFinite()) return motion
        val pull = if (strength.isFinite()) strength.coerceIn(MIN_PULL, MAX_PULL) else MIN_STRENGTH
        val rate = STIFFNESS_RATE * sqrt(pull)
        var angle = motion.angle
        var speed = motion.speed
        // Small fixed steps keep the spring stable whatever the frame time was;
        // a frame longer than 0.1 s (app in the background) is cut short.
        var left = dtSeconds.coerceAtMost(0.1f)
        while (left > 0f) {
            val dt = minOf(left, 0.004f)
            left -= dt
            val accel = rate * rate * (targetDeg - angle) - 2f * DAMPING_RATIO * rate * speed
            speed = (speed + accel * dt).coerceIn(-MAX_SPEED_DEG_S, MAX_SPEED_DEG_S)
            angle += speed * dt
        }
        return WheelMotion(angle, speed)
    }
}
