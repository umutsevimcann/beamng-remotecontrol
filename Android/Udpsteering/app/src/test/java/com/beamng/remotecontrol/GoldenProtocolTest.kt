package com.beamng.remotecontrol

import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.protocol.Sendpacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Golden wire-format tests. Byte layouts verified against the game source
 * (v0.38.6): lua/ge/extensions/core/remoteController.lua (control packet,
 * reversed into ori_t{w,x,y,z} floats) and lua/vehicle/protocols/outgauge.lua
 * (96-byte little-endian OutGauge struct; DL_HANDBRAKE = 4).
 *
 * These vectors lock the protocol: the Kotlin ports of Sendpacket/Receivepacket
 * must keep every one of these assertions green, byte for byte.
 */
class GoldenProtocolTest {

    private fun controlPacket(steering: Float, throttle: Float, brake: Float, id: Int): ByteArray {
        val p = Sendpacket()
        p.setSteeringAngle(steering)
        p.setThrottle(throttle)
        p.setBreaks(brake)
        p.setID(id)
        return p.sendingByteArray
    }

    private fun beFloats(vararg values: Float): ByteArray {
        val buf = ByteBuffer.allocate(4 * values.size).order(ByteOrder.BIG_ENDIAN)
        values.forEach { buf.putFloat(it) }
        return buf.array()
    }

    @Test
    fun controlPacket_centerSteering_fullThrottle_id7() {
        // 0.5f=3F000000, 1.0f=3F800000, 0.0f=00000000, 7f=40E00000 (all Big-Endian)
        assertArrayEquals(beFloats(0.5f, 1.0f, 0.0f, 7.0f), controlPacket(0.5f, 1.0f, 0.0f, 7))
    }

    @Test
    fun controlPacket_fullLeft_brake_id127() {
        assertArrayEquals(beFloats(1.0f, 0.0f, 1.0f, 127.0f), controlPacket(1.0f, 0.0f, 1.0f, 127))
    }

    @Test
    fun controlPacket_idZero_partialPedals() {
        assertArrayEquals(beFloats(0.0f, 0.25f, 0.75f, 0.0f), controlPacket(0.0f, 0.25f, 0.75f, 0))
    }

    @Test
    fun controlPacket_gameSideParse_roundTrip() {
        // Reproduce the game's parse: reverse all 16 bytes, read {w,x,y,z} floats LE.
        val data = controlPacket(steering = 0.5f, throttle = 1.0f, brake = 0.25f, id = 42)
        val reversed = data.reversedArray()
        val buf = ByteBuffer.wrap(reversed).order(ByteOrder.LITTLE_ENDIAN)
        val w = buf.float; val x = buf.float; val y = buf.float; val z = buf.float
        assertEquals(42.0f, w)      // packet id
        assertEquals(0.25f, x)      // brake
        assertEquals(1.0f, y)       // throttle
        assertEquals(0.5f, z)       // steering
    }

    // ---------------- OutGauge (game -> app) ----------------

    private fun outgaugePacket(size: Int, turbo: Boolean = false): ByteArray {
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        val flags = 16384 or if (turbo) 8192 else 0 // OG_KM (+ OG_TURBO)
        buf.putShort(8, flags.toShort())
        buf.put(10, 5)                          // gear byte 5 -> "4"
        buf.putFloat(12, 27.5f)                 // speed m/s
        buf.putFloat(16, 3500f)                 // rpm
        if (turbo) buf.putFloat(20, 0.8f)       // turbo BAR
        buf.putFloat(56, 0.3f)                  // clutch
        buf.putInt(44, 4 or 16 or 32)           // showLights: HANDBRAKE(4) | TC(16) | SIGNAL_L(32)
        buf.putInt(92, 42)                      // id
        if (size >= 100) buf.putInt(96, 1284)   // odometer (companion mod extension)
        return buf.array()
    }

    /** outgauge.lua: o.gear = gearIndex + 1 (signed char) — reverse 0, neutral 1, first 2. */
    private fun gearOf(gearByte: Int): String {
        val data = outgaugePacket(96)
        data[10] = gearByte.toByte()
        return Receivepacket(data, 96).gear
    }

    @Test
    fun outgauge_gearMapping_coversTrucksAndMultiReverse() {
        assertEquals("R", gearOf(0))    // gearIndex -1
        assertEquals("N", gearOf(1))    // gearIndex 0
        assertEquals("1", gearOf(2))
        assertEquals("10", gearOf(11))
        assertEquals("12", gearOf(13))  // 12+ speed truck gearboxes
        assertEquals("18", gearOf(19))
        assertEquals("R2", gearOf(-1))  // gearIndex -2: second reverse gear
        assertEquals("R3", gearOf(-2))
    }

    @Test
    fun outgauge_turboFlag_and_pressure() {
        val withTurbo = Receivepacket(outgaugePacket(96, turbo = true), 96)
        assertTrue(withTurbo.hasTurbo)
        assertEquals(0.8f, withTurbo.turbo)

        val withoutTurbo = Receivepacket(outgaugePacket(96, turbo = false), 96)
        assertFalse("non-turbo car must hide the boost gauge", withoutTurbo.hasTurbo)
    }

    @Test
    fun outgauge_standard96Byte_stockGame() {
        val p = Receivepacket(outgaugePacket(96), 96)
        assertTrue(p.isValid)
        assertEquals("4", p.gear)
        assertEquals(27.5f, p.speed)
        assertEquals(3500f, p.rpm)
        assertEquals(42, p.id)
        assertEquals(0, p.odometer) // no odometer in the stock 96-byte packet
        assertEquals(0.3f, p.clutch)
        val lights = p.activeLightsArr
        assertTrue("handbrake (bit 4) must map to index 2", lights[2])
        assertTrue("TC (bit 16) must map to index 4", lights[4])
        assertTrue("left signal (bit 32) must map to index 5", lights[5])
        assertFalse("full beam must stay off", lights[1])
        assertFalse("ABS must stay off", lights[10])
    }

    @Test
    fun outgauge_extended100Byte_companionMod() {
        val p = Receivepacket(outgaugePacket(100), 100)
        assertTrue(p.isValid)
        assertEquals(1284, p.odometer)
    }

    @Test
    fun outgauge_tooShort_isInvalid() {
        val p = Receivepacket(ByteArray(64), 64)
        assertFalse(p.isValid)
    }

    @Test
    fun outgauge_nonFiniteFloats_areZeroed() {
        // Anyone on the LAN can send a datagram to 4445; NaN/Infinity must never
        // reach the UI, where Float.roundToInt() throws on NaN.
        val buf = ByteBuffer.wrap(outgaugePacket(96, turbo = true)).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(12, Float.NaN)               // speed
        buf.putFloat(16, Float.NaN)               // rpm
        buf.putFloat(20, Float.POSITIVE_INFINITY) // turbo
        buf.putFloat(24, Float.NEGATIVE_INFINITY) // engine temp
        buf.putFloat(28, Float.NaN)               // fuel
        buf.putFloat(48, Float.NaN)               // throttle
        buf.putFloat(52, Float.NaN)               // brake
        buf.putFloat(56, Float.NaN)               // clutch
        val p = Receivepacket(buf.array(), 96)
        assertTrue(p.isValid)
        for (v in listOf(p.speed, p.rpm, p.turbo, p.engineTemp, p.fuel, p.throttle, p.brake, p.clutch)) {
            assertEquals(0f, v)
        }
    }

    // ---------------- MotionSim (drift stream) ----------------

    private fun motionPacket(velX: Float, velY: Float, yawPos: Float): ByteArray {
        val buf = ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("BNG1".toByteArray())
        buf.putFloat(16, velX)
        buf.putFloat(20, velY)
        buf.putFloat(60, yawPos)
        return buf.array()
    }

    @Test
    fun motion_slipAngle_30degrees() {
        // Travelling 30° off the heading (yaw=0): velX=sin(30°)*10, velY=cos(30°)*10
        val p = com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(5f, 8.6603f, 0f), 88)
        assertTrue(p.isValid)
        assertEquals(30f, p.slipAngleDeg()!!, 0.5f)
    }

    @Test
    fun motion_slowOrReverse_noDrift() {
        // Too slow (< ~20 km/h)
        val slow = com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(1f, 1f, 0f), 88)
        assertTrue(slow.isValid)
        assertEquals(null, slow.slipAngleDeg())
        // Reversing (travel ~180° from heading)
        val reverse = com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(0f, -10f, 0f), 88)
        assertEquals(null, reverse.slipAngleDeg())
    }

    @Test
    fun motion_wrongMagic_isInvalid() {
        val bad = motionPacket(5f, 5f, 0f)
        bad[0] = 'X'.code.toByte()
        assertFalse(com.beamng.remotecontrol.protocol.MotionPacket(bad, 88).isValid)
    }

    @Test(timeout = 2000)
    fun motion_nonFiniteValues_noDriftAndNoHang() {
        // Infinity in the angle used to spin the normalising while-loop forever.
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(bad, 8f, 0f), 88).slipAngleDeg())
            assertNull(com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(5f, bad, 0f), 88).slipAngleDeg())
            assertNull(com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(5f, 8.6603f, bad), 88).slipAngleDeg())
        }
    }

    @Test(timeout = 2000)
    fun motion_hugeFiniteYaw_staysBoundedAndReturns() {
        // Finite but enormous: subtracting 360 from 1e31 changes nothing in float
        // precision, so a subtract-until-in-range loop never ends.
        for (yaw in listOf(1.0e30f, -1.0e30f, 1.0e12f)) {
            val slip = com.beamng.remotecontrol.protocol.MotionPacket(motionPacket(5f, 8.6603f, yaw), 88).slipAngleDeg()
            if (slip != null) assertTrue(slip in -180f..180f)
        }
    }
}
