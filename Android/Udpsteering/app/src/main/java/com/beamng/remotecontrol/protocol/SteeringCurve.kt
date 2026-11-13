package com.beamng.remotecontrol.protocol

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Pre-compensates the game's steering curve so the car's wheel angle is
 * proportional to our input.
 *
 * The stock binding (settings/inputmaps/bngremotectrlv1.json) and the mod's
 * inputmap both set steering linearity 2.5. Measured on BeamNG.drive v0.40 with a
 * test virtual device, the game applies exactly input^2.5 (0.5 -> 0.1768,
 * 0.8 -> 0.5724): half the travel gave 18% of the lock and the last fifth of the
 * travel gave the remaining 43%. Sending input^(1/2.5) cancels that.
 */
object SteeringCurve {
    const val GAME_LINEARITY = 2.5

    /** [v] is steering in -1..1; the result is what to transmit, also -1..1. */
    fun compensate(v: Float): Float {
        if (v.isNaN()) return 0f
        val magnitude = abs(v).coerceAtMost(1f)
        return sign(v) * magnitude.toDouble().pow(1.0 / GAME_LINEARITY).toFloat()
    }
}
