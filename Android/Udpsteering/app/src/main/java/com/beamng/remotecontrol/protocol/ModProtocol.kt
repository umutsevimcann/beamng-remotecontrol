package com.beamng.remotecontrol.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire format of the RemoteControlPro mod (protocol 1), shared bit-for-bit with
 * BeamNG-Mod/lua/ge/extensions/remotecontrolpro/main.lua. Change one, change both.
 *
 * UDP port [Ports.MOD]; the mod answers to the address a packet came from.
 *   phone -> mod   "RCP?|1|<deviceName>"                    hello, ~every 3 s
 *   mod   -> phone "RCP!|1|<pcName>|<modVersion>"
 *   phone -> mod   28 bytes little-endian                    control, ~100 Hz
 *                    "RCP", version, float steering (-1..1),
 *                    float throttle, brake, clutch, handbrake (0..1), uint32 buttons
 *   mod   -> phone "RCPV|<maxRpm>|<steeringLockDeg>|<vehicle>[|<features>]"
 *                    features (mod 2.1+): 1 drive modes/ESC, 2 4WD, 4 nitrous
 */
object ModProtocol {
    const val VERSION = 1
    const val CONTROL_SIZE = 28

    // Bit n = button n of the mod's virtual device; order fixed by bngremotectrlprov2.json.
    const val BUTTON_SHIFT_UP = 1 shl 0
    const val BUTTON_SHIFT_DOWN = 1 shl 1
    const val BUTTON_HORN = 1 shl 2
    const val BUTTON_HEADLIGHTS = 1 shl 3
    const val BUTTON_FOG = 1 shl 4
    const val BUTTON_SIGNAL_LEFT = 1 shl 5
    const val BUTTON_SIGNAL_RIGHT = 1 shl 6
    const val BUTTON_HAZARD = 1 shl 7
    const val BUTTON_RECOVER = 1 shl 8
    // bit 9 = reset physics (bound in the inputmap, no key in the app)
    // Vehicle keys, mod 2.1+. A 2.0 mod has ten buttons and ignores these bits.
    const val BUTTON_DRIVE_MODE = 1 shl 10
    const val BUTTON_4WD = 1 shl 11
    const val BUTTON_LOW_RANGE = 1 shl 12
    const val BUTTON_DIFF = 1 shl 13
    const val BUTTON_NITROUS = 1 shl 14
    const val BUTTON_NITROUS_HOLD = 1 shl 15
    const val BUTTON_DOORS_OPEN = 1 shl 16
    const val BUTTON_DOORS_CLOSE = 1 shl 17

    const val FEATURE_DRIVE_MODE = 1
    const val FEATURE_4WD = 2
    const val FEATURE_NITROUS = 4

    /** What the mod says about itself in its hello reply. */
    data class ModInfo(val pcName: String, val modVersion: String) {
        /** Drive mode, 4WD, nitrous and door keys arrived with mod 2.1. */
        val hasVehicleKeys: Boolean
            get() {
                val parts = modVersion.split('.')
                val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                return major > 2 || (major == 2 && minor >= 1)
            }
    }

    /** Per-vehicle data; null fields mean the car didn't report a usable value. */
    data class VehicleInfo(
        val maxRpm: Float?,
        val steeringLockDeg: Float?,
        val vehicle: String,
        /** FEATURE_* mask of what this car has; null when the mod (2.0) doesn't report it. */
        val features: Int? = null,
    ) {
        /** Unknown counts as "may have it": a key that does nothing beats a missing one. */
        fun has(feature: Int): Boolean = features == null || features and feature != 0
    }

    fun hello(deviceName: String): ByteArray {
        val safe = deviceName.replace(Regex("[|\\r\\n]"), "_").take(48)
        return "RCP?|$VERSION|$safe".toByteArray()
    }

    fun parseHelloReply(message: String): ModInfo? {
        val parts = message.split('|')
        if (parts.size < 4 || parts[0] != "RCP!" || parts[1].toIntOrNull() != VERSION) return null
        return ModInfo(pcName = parts[2].ifBlank { "PC" }, modVersion = parts[3])
    }

    fun control(steering: Float, throttle: Float, brake: Float, clutch: Float, handbrake: Float, buttons: Int): ByteArray {
        val buf = ByteBuffer.allocate(CONTROL_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('R'.code.toByte()).put('C'.code.toByte()).put('P'.code.toByte()).put(VERSION.toByte())
        buf.putFloat(sane(steering, -1f))
        buf.putFloat(sane(throttle, 0f))
        buf.putFloat(sane(brake, 0f))
        buf.putFloat(sane(clutch, 0f))
        buf.putFloat(sane(handbrake, 0f))
        buf.putInt(buttons)
        return buf.array()
    }

    /** Steering centred, everything released, no buttons — sent when the drive screen pauses. */
    fun neutral(): ByteArray = control(0f, 0f, 0f, 0f, 0f, 0)

    fun parseVehicleInfo(message: String): VehicleInfo? {
        val parts = message.split('|')
        if (parts.size < 4 || parts[0] != "RCPV") return null
        val rpm = parts[1].toFloatOrNull() ?: return null
        val lock = parts[2].toFloatOrNull() ?: return null
        return VehicleInfo(
            maxRpm = rpm.takeIf { it.isFinite() && it in 500f..25000f },
            steeringLockDeg = lock.takeIf { it.isFinite() && it in 45f..2000f },
            vehicle = parts[3],
            features = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it in 0..0xFFFF },
        )
    }

    private fun sane(v: Float, min: Float): Float = if (v.isFinite()) v.coerceIn(min, 1f) else 0f
}
