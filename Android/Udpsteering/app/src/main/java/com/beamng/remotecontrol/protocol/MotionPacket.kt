package com.beamng.remotecontrol.protocol

import org.apache.commons.io.EndianUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Parses the game's MotionSim protocol packet
 * (lua/vehicle/protocols/motionSim.lua): "BNG1" magic + 21 little-endian
 * floats — world position/velocity/acceleration, up vector, roll/pitch/yaw
 * position/velocity/acceleration. 88 bytes total.
 *
 * Used for the drift indicator: slip = angle between where the car points
 * (yawPos) and where it travels (atan2 of the horizontal world velocity).
 */
class MotionPacket(data: ByteArray, length: Int) {

    var isValid = false; private set
    var velX = 0f; private set
    var velY = 0f; private set
    /** Body acceleration in m/s², gravity excluded (z up): what a crash or a landing shows up in. */
    var accX = 0f; private set
    var accY = 0f; private set
    var accZ = 0f; private set
    var yawPos = 0f; private set

    init {
        if (length >= PACKET_LENGTH &&
            data[0] == 'B'.code.toByte() && data[1] == 'N'.code.toByte() &&
            data[2] == 'G'.code.toByte() && data[3] == '1'.code.toByte()
        ) {
            velX = EndianUtils.readSwappedFloat(data, 16)
            velY = EndianUtils.readSwappedFloat(data, 20)
            accX = EndianUtils.readSwappedFloat(data, 28)
            accY = EndianUtils.readSwappedFloat(data, 32)
            accZ = EndianUtils.readSwappedFloat(data, 36)
            yawPos = EndianUtils.readSwappedFloat(data, 60)
            isValid = true
        }
    }

    /**
     * Signed slip angle in degrees, or null when not meaningfully drifting:
     * below ~20 km/h, or pointing backwards (reverse gear reads ~180°).
     */
    fun slipAngleDeg(): Float? {
        // Non-finite input: no drift. Infinity here would also never leave the
        // normalising loops below.
        if (!velX.isFinite() || !velY.isFinite() || !yawPos.isFinite()) return null

        val speedH = hypot(velX, velY)
        if (speedH < 5.5f) return null // ~20 km/h

        val travelDir = atan2(velX, velY)
        val heading = -yawPos // protocols.lua negates yaw ("standard for motion sims")
        // Wrap into [-180, 180] in one bounded step. Never by repeated subtraction:
        // for a huge angle, subtracting 360 changes nothing in float precision and
        // such a loop would spin forever on the main thread.
        val deg = Math.toDegrees(Math.IEEEremainder((travelDir - heading).toDouble(), 2 * Math.PI)).toFloat()
        if (!deg.isFinite()) return null

        if (abs(deg) > 120f) return null // reversing, not drifting
        return deg
    }

    companion object {
        const val PACKET_LENGTH = 88
    }
}
