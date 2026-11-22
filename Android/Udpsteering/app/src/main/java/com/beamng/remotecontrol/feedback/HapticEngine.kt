package com.beamng.remotecontrol.feedback

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Decides when the phone vibrates. Pure logic (no Android), fed from the two
 * telemetry streams and asked for the pulses to play; MainActivity plays them.
 *
 * Crashes are read from the MotionSim body acceleration (gravity excluded), which
 * the game smooths but does not hide. Measured in v0.40: driving and hard braking
 * stay under 3 g, hitting a wall gave 7.4 g and 15 g, a landing from half a metre
 * 5.3 g vertical. OutGauge's speed is the *wheel* speed, so a speed drop is only a
 * fallback for when MotionSim is off: wheels keep turning, lock or leave the
 * ground in a crash, which is why the old speed-only detection rarely fired.
 */
class HapticEngine {

    /** One vibration: how long, and how strong (1..255). */
    data class Pulse(val ms: Long, val amplitude: Int)

    /** Which events the player wants to feel; one switch each in Settings. */
    data class Options(
        val gear: Boolean,
        val impact: Boolean,
        val landing: Boolean,
        val abs: Boolean,
        val traction: Boolean,
        val redline: Boolean,
    )

    private var lastGear: String? = null
    private var lastMotionMs = Long.MIN_VALUE / 2
    private var lastBumpMs = Long.MIN_VALUE / 2
    private var lastAbsMs = Long.MIN_VALUE / 2
    private var lastTractionMs = Long.MIN_VALUE / 2
    private var lastRedlineMs = Long.MIN_VALUE / 2

    // Wheel speed over the last SPEED_WINDOW_MS, for the fallback crash check.
    private val speedTimes = LongArray(SPEED_SAMPLES)
    private val speeds = FloatArray(SPEED_SAMPLES)
    private var speedCount = 0
    private var speedHead = 0

    /** An OutGauge packet. [kmh] is the wheel speed in km/h, whatever unit the dash shows. */
    fun onTelemetry(
        nowMs: Long, gear: String, kmh: Float, absActive: Boolean, tractionActive: Boolean,
        rpm: Float, redlineRpm: Float, options: Options,
    ): List<Pulse> {
        val out = ArrayList<Pulse>(2)
        val speed = if (kmh.isFinite()) kmh.coerceIn(0f, 1000f) else 0f

        if (gear != lastGear) {
            if (lastGear != null && options.gear) out += Pulse(50, 150)
            lastGear = gear
        }

        val motionFresh = nowMs - lastMotionMs < MOTION_STALE_MS
        val drop = recordSpeed(nowMs, speed)
        if (!motionFresh && options.impact && drop >= 20f && nowMs - lastBumpMs >= BUMP_COOLDOWN_MS) {
            out += when {
                drop >= 55f -> Pulse(600, 255)
                drop >= 35f -> Pulse(300, 190)
                else -> Pulse(150, 110)
            }
            lastBumpMs = nowMs
        }

        val moving = speed > 5f
        if (options.abs && absActive && moving && nowMs - lastAbsMs >= ABS_PERIOD_MS) {
            out += Pulse(40, 60)
            lastAbsMs = nowMs
        }
        if (options.traction && tractionActive && moving && nowMs - lastTractionMs >= TRACTION_PERIOD_MS) {
            out += Pulse(30, 50)
            lastTractionMs = nowMs
        }
        val atLimit = rpm.isFinite() && redlineRpm.isFinite() && redlineRpm > 0f && rpm >= redlineRpm
        if (options.redline && atLimit && nowMs - lastRedlineMs >= REDLINE_PERIOD_MS) {
            out += Pulse(25, 80)
            lastRedlineMs = nowMs
        }
        return out
    }

    /** A MotionSim packet: body acceleration in m/s², gravity excluded; z is up. */
    fun onMotion(nowMs: Long, accX: Float, accY: Float, accZ: Float, options: Options): List<Pulse> {
        if (!accX.isFinite() || !accY.isFinite() || !accZ.isFinite()) return emptyList()
        lastMotionMs = nowMs
        if (nowMs - lastBumpMs < BUMP_COOLDOWN_MS) return emptyList()
        val sideways = hypot(accX, accY) / G
        val vertical = abs(accZ) / G
        // A crash usually has both; play the one that is stronger, once.
        val pulse = when {
            options.impact && sideways >= IMPACT_G -> when {
                sideways >= 12f -> Pulse(600, 255)
                sideways >= 8f -> Pulse(300, 190)
                else -> Pulse(150, 110)
            }
            options.landing && vertical >= LANDING_G -> when {
                vertical >= 15f -> Pulse(300, 220)
                vertical >= 8f -> Pulse(160, 150)
                else -> Pulse(80, 90)
            }
            else -> return emptyList()
        }
        lastBumpMs = nowMs
        return listOf(pulse)
    }

    /** Stores [kmh] and returns how far the speed has fallen from its highest point in the window. */
    private fun recordSpeed(nowMs: Long, kmh: Float): Float {
        speedTimes[speedHead] = nowMs
        speeds[speedHead] = kmh
        speedHead = (speedHead + 1) % SPEED_SAMPLES
        if (speedCount < SPEED_SAMPLES) speedCount++
        var highest = kmh
        for (i in 0 until speedCount) {
            if (nowMs - speedTimes[i] <= SPEED_WINDOW_MS && speeds[i] > highest) highest = speeds[i]
        }
        return highest - kmh
    }

    private companion object {
        const val G = 9.81f
        const val IMPACT_G = 5f
        const val LANDING_G = 4f
        const val BUMP_COOLDOWN_MS = 600L
        const val ABS_PERIOD_MS = 150L
        const val TRACTION_PERIOD_MS = 180L
        const val REDLINE_PERIOD_MS = 150L
        const val MOTION_STALE_MS = 1000L
        const val SPEED_WINDOW_MS = 400L
        const val SPEED_SAMPLES = 64 // OutGauge runs at up to ~60 Hz
    }
}
