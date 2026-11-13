package com.beamng.remotecontrol

import com.beamng.remotecontrol.protocol.ModProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wire format shared with BeamNG-Mod/lua/ge/extensions/remotecontrolpro/main.lua (protocol 1). */
class ModProtocolTest {

    @Test
    fun hello_carriesProtocolVersionAndASafeDeviceName() {
        assertEquals("RCP?|1|Xiaomi M2006C3MG", String(ModProtocol.hello("Xiaomi M2006C3MG")))
        // '|' and line breaks would break the mod's split()
        assertEquals("RCP?|1|a_b_c", String(ModProtocol.hello("a|b\nc")))
    }

    @Test
    fun helloReply_isParsed() {
        val info = ModProtocol.parseHelloReply("RCP!|1|UMUT-PC|2.0.0")
        assertEquals("UMUT-PC", info?.pcName)
        assertEquals("2.0.0", info?.modVersion)
    }

    @Test
    fun helloReply_rejectsOtherTrafficAndOtherProtocolVersions() {
        assertNull(ModProtocol.parseHelloReply("beamng|12345"))   // the stock game's reply
        assertNull(ModProtocol.parseHelloReply("RCP!|2|PC|9.0"))  // a protocol we don't speak
        assertNull(ModProtocol.parseHelloReply("RCP!|1"))         // truncated
        assertNull(ModProtocol.parseHelloReply(""))
    }

    @Test
    fun control_matchesTheModsPackedStruct() {
        val packet = ModProtocol.control(
            steering = -0.5f, throttle = 0.25f, brake = 1f, clutch = 0f, handbrake = 1f,
            buttons = ModProtocol.BUTTON_SHIFT_UP or ModProtocol.BUTTON_HORN,
        )
        assertEquals(28, packet.size)
        assertArrayEquals(byteArrayOf('R'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte(), 1), packet.copyOfRange(0, 4))
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(-0.5f, buf.getFloat(4))
        assertEquals(0.25f, buf.getFloat(8))
        assertEquals(1f, buf.getFloat(12))
        assertEquals(0f, buf.getFloat(16))
        assertEquals(1f, buf.getFloat(20))
        assertEquals(0b101, buf.getInt(24)) // bit 0 shift up, bit 2 horn
    }

    @Test
    fun control_clampsAndNeverSendsNaN() {
        val buf = ByteBuffer.wrap(ModProtocol.control(9f, -3f, Float.NaN, 2f, Float.POSITIVE_INFINITY, 0)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1f, buf.getFloat(4))   // steering -1..1
        assertEquals(0f, buf.getFloat(8))   // throttle 0..1
        assertEquals(0f, buf.getFloat(12))  // NaN -> 0
        assertEquals(1f, buf.getFloat(16))
        assertEquals(0f, buf.getFloat(20))  // Infinity -> 0
    }

    @Test
    fun buttonBits_followTheModsInputmapOrder() {
        // bngremotectrlprov2.json: button0 shiftUp, 1 shiftDown, 2 horn, 3 headlights, 4 fog,
        // 5 left signal, 6 right signal, 7 hazards, 8 recover, 9 reset
        assertEquals(1 shl 0, ModProtocol.BUTTON_SHIFT_UP)
        assertEquals(1 shl 1, ModProtocol.BUTTON_SHIFT_DOWN)
        assertEquals(1 shl 2, ModProtocol.BUTTON_HORN)
        assertEquals(1 shl 3, ModProtocol.BUTTON_HEADLIGHTS)
        assertEquals(1 shl 4, ModProtocol.BUTTON_FOG)
        assertEquals(1 shl 5, ModProtocol.BUTTON_SIGNAL_LEFT)
        assertEquals(1 shl 6, ModProtocol.BUTTON_SIGNAL_RIGHT)
        assertEquals(1 shl 7, ModProtocol.BUTTON_HAZARD)
        assertEquals(1 shl 8, ModProtocol.BUTTON_RECOVER)
    }

    @Test
    fun vehicleInfo_isParsedAndSanitised() {
        val v = ModProtocol.parseVehicleInfo("RCPV|6300|495|vivace")
        assertEquals(6300f, v?.maxRpm)
        assertEquals(495f, v?.steeringLockDeg)
        assertEquals("vivace", v?.vehicle)

        // zero / absurd values mean "unknown", not a scale of 0 or a billion
        val unknown = ModProtocol.parseVehicleInfo("RCPV|0|999999|x")
        assertNull(unknown?.maxRpm)
        assertNull(unknown?.steeringLockDeg)

        assertNull(ModProtocol.parseVehicleInfo("RCPV|abc"))
        assertNull(ModProtocol.parseVehicleInfo("beam"))
    }
}
