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
    var yawPos = 0f; private set

    init {
        if (length >= PACKET_LENGTH &&
            data[0] == 'B'.code.toByte() && data[1] == 'N'.code.toByte() &&
            data[2] == 'G'.code.toByte() && data[3] == '1'.code.toByte()
        ) {
            velX = EndianUtils.readSwappedFloat(data, 16)
            velY = EndianUtils.readSwappedFloat(data, 20)
            yawPos = EndianUtils.readSwappedFloat(data, 60)
            isValid = true
        }
    }

    /**
     * Signed slip angle in degrees, or null when not meaningfully drifting:
     * below ~20 km/h, or pointing backwards (reverse gear reads ~180°).
     */
    fun slipAngleDeg(): Float? {
        val speedH = hypot(velX, velY)
        if (speedH < 5.5f) return null // ~20 km/h

        val travelDir = atan2(velX, velY)
        val heading = -yawPos // protocols.lua negates yaw ("standard for motion sims")
        var deg = Math.toDegrees((travelDir - heading).toDouble()).toFloat()
        while (deg > 180f) deg -= 360f
        while (deg < -180f) deg += 360f

        if (abs(deg) > 120f) return null // reversing, not drifting
        return deg
    }

    companion object {
        const val PACKET_LENGTH = 88
    }
}
