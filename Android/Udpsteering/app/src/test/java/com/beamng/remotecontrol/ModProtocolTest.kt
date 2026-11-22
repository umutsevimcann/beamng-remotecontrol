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
        assertEquals(ModProtocol.CONTROL_SIZE, packet.size) // the first 28 bytes are protocol 1 as ever
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

    @Test
    fun vehicleKeyBits_followTheModsInputmapOrder() {
        // bngremotectrlprov2.json (mod 2.1): button10 drive mode/ESC, 11 4WD, 12 low range,
        // 13 diff lock, 14 nitrous arm, 15 nitrous hold, 16 latches open, 17 latches close
        assertEquals(1 shl 10, ModProtocol.BUTTON_DRIVE_MODE)
        assertEquals(1 shl 11, ModProtocol.BUTTON_4WD)
        assertEquals(1 shl 12, ModProtocol.BUTTON_LOW_RANGE)
        assertEquals(1 shl 13, ModProtocol.BUTTON_DIFF)
        assertEquals(1 shl 14, ModProtocol.BUTTON_NITROUS)
        assertEquals(1 shl 15, ModProtocol.BUTTON_NITROUS_HOLD)
        assertEquals(1 shl 16, ModProtocol.BUTTON_DOORS_OPEN)
        assertEquals(1 shl 17, ModProtocol.BUTTON_DOORS_CLOSE)
    }

    @Test
    fun vehicleInfo_carriesWhatTheCarHas_whenTheModSaysSo() {
        // mod 2.1 appends a feature mask: 1 drive modes/ESC, 2 4WD, 4 nitrous
        val v = ModProtocol.parseVehicleInfo("RCPV|7200|270|vivace|5")
        assertEquals(5, v?.features)
        assertEquals(true, v?.has(ModProtocol.FEATURE_DRIVE_MODE))
        assertEquals(false, v?.has(ModProtocol.FEATURE_4WD))
        assertEquals(true, v?.has(ModProtocol.FEATURE_NITROUS))

        // mod 2.0 sends no mask: unknown, so nothing is ruled out
        val old = ModProtocol.parseVehicleInfo("RCPV|7200|270|vivace")
        assertNull(old?.features)
        assertEquals(true, old?.has(ModProtocol.FEATURE_4WD))

        // garbage in the mask is "unknown", not a crash
        assertNull(ModProtocol.parseVehicleInfo("RCPV|7200|270|vivace|x")?.features)
    }

    @Test
    fun modInfo_knowsWhetherTheModHasVehicleKeys() {
        assertEquals(false, ModProtocol.ModInfo("PC", "2.0.0").hasVehicleKeys)
        assertEquals(true, ModProtocol.ModInfo("PC", "2.1.0").hasVehicleKeys)
        assertEquals(true, ModProtocol.ModInfo("PC", "3.0").hasVehicleKeys)
        assertEquals(false, ModProtocol.ModInfo("PC", "nonsense").hasVehicleKeys)
    }

    @Test
    fun wheelFreeFlag_isTheTopBit_clearOfEveryButton() {
        // bit 31: not a button of the virtual device, a flag the mod reads itself
        assertEquals(0x80000000.toInt(), ModProtocol.FLAG_WHEEL_FREE)
        val packet = ModProtocol.control(0f, 0f, 0f, 0f, 0f, ModProtocol.FLAG_WHEEL_FREE or ModProtocol.BUTTON_HORN)
        assertEquals(0x04.toByte(), packet[24])
        assertEquals(0x80.toByte(), packet[27])
    }

    @Test
    fun wheelAngle_isWhereTheGameHasTheReleasedWheel() {
        assertEquals(-0.4312f, ModProtocol.parseWheelAngle("RCPS|-0.4312")!!, 1e-6f)
        assertEquals(1f, ModProtocol.parseWheelAngle("RCPS|1.7")!!, 0f) // past the lock: clamp
        assertNull(ModProtocol.parseWheelAngle("RCPS|nan"))
        assertNull(ModProtocol.parseWheelAngle("RCPS|"))
        assertNull(ModProtocol.parseWheelAngle("RCPV|7200|270|vivace"))
    }

    @Test
    fun modInfo_knowsWhetherTheModHasForceFeedback() {
        assertEquals(false, ModProtocol.ModInfo("PC", "2.1.0").hasWheelFeedback)
        assertEquals(true, ModProtocol.ModInfo("PC", "2.2.0").hasWheelFeedback)
        assertEquals(true, ModProtocol.ModInfo("PC", "3.0").hasWheelFeedback)
        assertEquals(false, ModProtocol.ModInfo("PC", "").hasWheelFeedback)
    }

    @Test
    fun hello_canCarryHowHardTheWheelReturns() {
        // percent of the game's force feedback; older mods never read a fourth field
        assertEquals("RCP?|1|Phone|150", String(ModProtocol.hello("Phone", wheelReturnPercent = 150)))
        assertEquals("RCP?|1|Phone|200", String(ModProtocol.hello("Phone", wheelReturnPercent = 900)))
        assertEquals("RCP?|1|Phone|20", String(ModProtocol.hello("Phone", wheelReturnPercent = -5)))
    }

    @Test
    fun gameKeyBits_followTheModsInputmapOrder() {
        assertEquals(1 shl 9, ModProtocol.BUTTON_RESET)
        assertEquals(1 shl 18, ModProtocol.BUTTON_CAMERA)
        assertEquals(1 shl 19, ModProtocol.BUTTON_GEARBOX_MODE)
        assertEquals(1 shl 20, ModProtocol.BUTTON_PAUSE)
        assertEquals(false, ModProtocol.ModInfo("PC", "2.1.0").hasGameKeys)
        assertEquals(true, ModProtocol.ModInfo("PC", "2.2.0").hasGameKeys)
    }

    @Test
    fun mapKey_isButton21_andNeedsMod23() {
        assertEquals(1 shl 21, ModProtocol.BUTTON_MAP)
        assertEquals(false, ModProtocol.ModInfo("PC", "2.2.0").hasMapKey)
        assertEquals(true, ModProtocol.ModInfo("PC", "2.3.0").hasMapKey)
    }

    @Test
    fun walkKey_isButton22() {
        assertEquals(1 shl 22, ModProtocol.BUTTON_WALK)
    }

    @Test
    fun control_carriesTheLookDrag_afterTheOriginal28Bytes() {
        // A mod older than 2.3 reads only the first 28 bytes; the look drag rides behind them.
        val packet = ModProtocol.control(0f, 0f, 0f, 0f, 0f, 0, lookX = 12.5f, lookY = -4f)
        assertEquals(36, packet.size)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12.5f, buf.getFloat(28), 0f)
        assertEquals(-4f, buf.getFloat(32), 0f)
        // Garbage never goes on the wire, and one packet cannot spin the camera round.
        val wild = ByteBuffer.wrap(ModProtocol.control(0f, 0f, 0f, 0f, 0f, 0, lookX = Float.NaN, lookY = 1e9f))
            .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0f, wild.getFloat(28), 0f)
        assertEquals(ModProtocol.LOOK_LIMIT, wild.getFloat(32), 0f)
    }

    @Test
    fun walking_isWhatTheModSaysAboutThePlayer() {
        assertEquals(true, ModProtocol.parseWalking("RCPW|1"))
        assertEquals(false, ModProtocol.parseWalking("RCPW|0"))
        assertNull(ModProtocol.parseWalking("RCPW|x"))
        assertNull(ModProtocol.parseWalking("RCPV|7200|270|vivace"))
    }

    @Test
    fun walkStick_turnsIntoTheAxesTheModWalksWith() {
        // Forward half-way, a little right: throttle forward, no brake, steering sideways.
        val w = ModProtocol.walkAxes(x = 0.3f, y = 0.5f, sprint = false)
        assertEquals(0.3f, w.steering, 1e-6f)
        assertEquals(0.5f, w.throttle, 1e-6f)
        assertEquals(0f, w.brake, 0f)
        assertEquals(0f, w.handbrake, 0f)
        // Backwards and sprinting.
        val back = ModProtocol.walkAxes(x = 0f, y = -1f, sprint = true)
        assertEquals(0f, back.throttle, 0f)
        assertEquals(1f, back.brake, 0f)
        assertEquals(1f, back.handbrake, 0f)
    }

    @Test
    fun interactAndViewKeys_areButtons23And24() {
        assertEquals(1 shl 23, ModProtocol.BUTTON_INTERACT)
        assertEquals(1 shl 24, ModProtocol.BUTTON_VIEW)
    }
}
