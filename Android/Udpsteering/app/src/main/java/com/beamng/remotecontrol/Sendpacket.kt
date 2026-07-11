package com.beamng.remotecontrol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds the 16-byte UDP control packet sent to BeamNG.drive.
 * Format (Big-Endian):
 *   [0-3]   float steering  (0.0=right, 0.5=center, 1.0=left)
 *   [4-7]   float throttle  (0.0-1.0)
 *   [8-11]  float brakes    (0.0-1.0)
 *   [12-15] float packetId  (the game parses all four fields as floats)
 *
 * Pre-allocates a single ByteBuffer to avoid GC pressure at high send rates.
 * Kotlin port of the Java original (Android/legacy-java) — byte-identical,
 * locked by GoldenProtocolTest.
 */
class Sendpacket {
    private val buffer: ByteBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)

    private var steeringAngle = 0f
    private var throttle = 0f
    private var brakes = 0f
    private var id = 0

    fun setSteeringAngle(steeringAngle: Float) { this.steeringAngle = steeringAngle }
    fun setThrottle(throttle: Float) { this.throttle = throttle }
    fun setBreaks(brakes: Float) { this.brakes = brakes }
    fun setID(id: Int) { this.id = id }

    val sendingByteArray: ByteArray
        get() {
            // BeamNG reverses all 16 bytes then reads them as struct {float w,x,y,z}:
            //   z (bytes 0-3 reversed)   = steering
            //   y (bytes 4-7 reversed)   = throttle
            //   x (bytes 8-11 reversed)  = brake
            //   w (bytes 12-15 reversed) = packet id — MUST be a float; an int here
            //     parses as a denormal ≈0 and kills the ID-echo/latency mechanism.
            buffer.clear()
            buffer.putFloat(steeringAngle)
            buffer.putFloat(throttle)
            buffer.putFloat(brakes)
            buffer.putFloat(id.toFloat())
            return buffer.array()
        }
}
