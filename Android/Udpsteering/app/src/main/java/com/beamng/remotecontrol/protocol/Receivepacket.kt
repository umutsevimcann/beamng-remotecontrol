package com.beamng.remotecontrol.protocol

import android.util.Log
import org.apache.commons.io.EndianUtils
import java.nio.ByteBuffer

/**
 * Parses the OutGauge telemetry packet received from BeamNG.drive.
 *
 * Struct layout (Little-Endian; verified against the game's
 * lua/vehicle/protocols/outgauge.lua):
 *   0-3 time · 4-7 car[4] · 8-9 flags · 10 gear · 11 plid · 12-15 speed(m/s)
 *   16-19 rpm · 20-23 turbo · 24-27 engTemp · 28-31 fuel · 32-35 oilPressure
 *   36-39 oilTemp · 40-43 dashLights · 44-47 showLights · 48-51 throttle
 *   52-55 brake · 56-59 clutch · 60-75 display1 · 76-91 display2 · 92-95 id
 *   96-99 odometer (optional — companion mod extension)
 *
 * Kotlin port of the Java original (Android/legacy-java) — behavior identical,
 * locked by GoldenProtocolTest.
 */
class Receivepacket(data: ByteArray, length: Int) {

    private var gearByte: Byte = 0
    private val lightsArray = BooleanArray(11)
    private var showLights = 0

    @get:JvmName("getSpeed")
    var speed = 0f; private set

    @get:JvmName("getRPM")
    var rpm = 0f; private set

    /** Turbo pressure in BAR; meaningful only when [hasTurbo] is true. */
    var turbo = 0f; private set

    /** OG_TURBO flag — the game sets it only for turbocharged vehicles. */
    var hasTurbo = false; private set

    @get:JvmName("getEngineTemp")
    var engineTemp = 0f; private set

    @get:JvmName("getFuel")
    var fuel = 0f; private set

    @get:JvmName("getThrottle")
    var throttle = 0f; private set

    @get:JvmName("getBrake")
    var brake = 0f; private set

    var clutch = 0f; private set

    @get:JvmName("getOdometer")
    var odometer = 0; private set

    @get:JvmName("getID")
    var id = 0; private set

    @get:JvmName("isValid")
    var isValid = false; private set

    init {
        if (length < MIN_PACKET_LENGTH) {
            Log.w("Receivepacket", "Packet too short: $length bytes (need $MIN_PACKET_LENGTH)")
        } else {
            try {
                val bb = ByteBuffer.wrap(data)
                hasTurbo = (EndianUtils.readSwappedUnsignedShort(data, 8) and FLAG_TURBO) != 0
                gearByte = bb.get(10)
                speed = EndianUtils.readSwappedFloat(data, 12)
                rpm = EndianUtils.readSwappedFloat(data, 16)
                turbo = EndianUtils.readSwappedFloat(data, 20)
                engineTemp = EndianUtils.readSwappedFloat(data, 24)
                fuel = EndianUtils.readSwappedFloat(data, 28)
                throttle = EndianUtils.readSwappedFloat(data, 48)
                brake = EndianUtils.readSwappedFloat(data, 52)
                clutch = EndianUtils.readSwappedFloat(data, 56)
                showLights = EndianUtils.readSwappedInteger(data, 44)
                id = EndianUtils.readSwappedInteger(data, 92)
                odometer = if (length >= 100) EndianUtils.readSwappedInteger(data, 96) else 0
                isValid = true
            } catch (e: Exception) {
                Log.e("Receivepacket", "Failed to parse packet", e)
            }
        }
    }

    val activeLightsArr: BooleanArray
        get() {
            // Reset all lights first (fixes bug where lights never turn off)
            lightsArray.fill(false)
            if ((showLights and FLAG_SHIFTLIGHT) == FLAG_SHIFTLIGHT) lightsArray[0] = true
            if ((showLights and FLAG_FULLBEAM) == FLAG_FULLBEAM) lightsArray[1] = true
            if ((showLights and FLAG_HANDBREAK) == FLAG_HANDBREAK) lightsArray[2] = true
            if ((showLights and FLAG_TC) == FLAG_TC) lightsArray[4] = true
            if ((showLights and FLAG_SIGNAL_L) == FLAG_SIGNAL_L) lightsArray[5] = true
            if ((showLights and FLAG_SIGNAL_R) == FLAG_SIGNAL_R) lightsArray[6] = true
            // FLAG_SIGNAL_ANY (128) intentionally not mapped: BeamNG never sets it.
            if ((showLights and FLAG_OILWARN) == FLAG_OILWARN) lightsArray[8] = true
            if ((showLights and FLAG_BATTERY) == FLAG_BATTERY) lightsArray[9] = true
            if ((showLights and FLAG_ABS) == FLAG_ABS) lightsArray[10] = true
            return lightsArray
        }

    val gear: String
        get() = when (gearByte.toInt()) {
            0 -> "R"
            1 -> "N"
            in 2..11 -> (gearByte - 1).toString()
            else -> "?"
        }

    companion object {
        // Stock BeamNG OutGauge (Options > Other) sends the standard 96-byte LFS struct;
        // the companion mod appends a 4-byte odometer (100 bytes). Accept both.
        const val MIN_PACKET_LENGTH = 96

        // Indices into activeLightsArr (= bit positions in the game's showLights).
        // UI code maps icons with these instead of magic numbers.
        const val INDEX_SHIFT = 0
        const val INDEX_FULLBEAM = 1
        const val INDEX_HANDBRAKE = 2
        const val INDEX_TC = 4
        const val INDEX_SIGNAL_L = 5
        const val INDEX_SIGNAL_R = 6
        const val INDEX_OILWARN = 8
        const val INDEX_BATTERY = 9
        const val INDEX_ABS = 10

        // OG_TURBO from the game's flags field (lua/vehicle/protocols/outgauge.lua).
        private const val FLAG_TURBO = 8192

        // Bit values from the game's lua/vehicle/protocols/outgauge.lua (DL_x constants).
        private const val FLAG_SHIFTLIGHT = 1
        private const val FLAG_FULLBEAM = 2
        private const val FLAG_HANDBREAK = 4 // DL_HANDBRAKE = 2^2
        private const val FLAG_TC = 16       // DL_TC — traction control active/off
        private const val FLAG_SIGNAL_L = 32
        private const val FLAG_SIGNAL_R = 64
        private const val FLAG_OILWARN = 256
        private const val FLAG_BATTERY = 512
        private const val FLAG_ABS = 1024
    }
}
