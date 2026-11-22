package com.beamng.remotecontrol.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire format of the RemoteControlPro mod (protocol 1), shared bit-for-bit with
 * BeamNG-Mod/lua/ge/extensions/remotecontrolpro/main.lua. Change one, change both.
 *
 * UDP port [Ports.MOD]; the mod answers to the address a packet came from.
 *   phone -> mod   "RCP?|1|<deviceName>[|<wheelReturn%>]"   hello, ~every 3 s
 *                    wheelReturn (mod 2.2+): 20..200, how hard the released wheel
 *                    is pulled, in percent of the game's force feedback
 *   mod   -> phone "RCP!|1|<pcName>|<modVersion>"
 *   phone -> mod   36 bytes little-endian                    control, ~100 Hz
 *                    "RCP", version, float steering (-1..1),
 *                    float throttle, brake, clutch, handbrake (0..1), uint32 buttons,
 *                    then (mod 2.3+) float lookX, lookY: the look drag since the last
 *                    packet; older mods read the first 28 bytes and ignore the rest
 *                    then (mod 2.4+) uint32 menu keys (MENU_*), which the mod passes
 *                    straight to the game's UI navigation
 *   mod   -> phone "RCPV|<maxRpm>|<steeringLockDeg>|<vehicle>[|<features>]"
 *                    features (mod 2.1+): 1 drive modes/ESC, 2 4WD, 4 nitrous
 *   mod   -> phone "RCPS|<angle>"                          released wheel, ~30 Hz (mod 2.2+)
 *   mod   -> phone "RCPW|<0|1>"                            the player is on foot (mod 2.3+)
 *                    angle -1..1: where the game's force feedback has turned the
 *                    wheel while buttons bit 31 (wheel free) is set
 */
object ModProtocol {
    const val VERSION = 1
    const val CONTROL_SIZE = 40

    /** Largest look drag one packet may carry; more is a glitch, not a flick. */
    const val LOOK_LIMIT = 2000f

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
    const val BUTTON_RESET = 1 shl 9
    // Vehicle keys, mod 2.1+. A 2.0 mod has ten buttons and ignores these bits.
    const val BUTTON_DRIVE_MODE = 1 shl 10
    const val BUTTON_4WD = 1 shl 11
    const val BUTTON_LOW_RANGE = 1 shl 12
    const val BUTTON_DIFF = 1 shl 13
    const val BUTTON_NITROUS = 1 shl 14
    const val BUTTON_NITROUS_HOLD = 1 shl 15
    const val BUTTON_DOORS_OPEN = 1 shl 16
    const val BUTTON_DOORS_CLOSE = 1 shl 17
    // Game keys, mod 2.2+.
    const val BUTTON_CAMERA = 1 shl 18
    const val BUTTON_GEARBOX_MODE = 1 shl 19
    const val BUTTON_PAUSE = 1 shl 20
    // Mod 2.3+.
    const val BUTTON_MAP = 1 shl 21
    const val BUTTON_WALK = 1 shl 22
    const val BUTTON_INTERACT = 1 shl 23
    /** On foot: zoom the view in and out (the game has no third-person walking camera). */
    const val BUTTON_VIEW = 1 shl 24

    // Menu keys (mod 2.4+), in a second field after the look drag: the first one has
    // only bits 25-30 left. The mod raises the game's UI navigation events for them
    // (menu, focus_u/d/l/r, ok, back, tab_l/r), not device buttons: its device has 32.
    const val MENU_TOGGLE = 1 shl 0
    const val MENU_UP = 1 shl 1
    const val MENU_DOWN = 1 shl 2
    const val MENU_LEFT = 1 shl 3
    const val MENU_RIGHT = 1 shl 4
    const val MENU_OK = 1 shl 5
    const val MENU_BACK = 1 shl 6
    const val MENU_TAB_LEFT = 1 shl 7
    const val MENU_TAB_RIGHT = 1 shl 8

    /**
     * Not a button: "nobody is holding the wheel". Mod 2.2+ then turns the wheel
     * itself with the game's force feedback and reports its angle; older mods
     * have 18 buttons and never look at this bit.
     */
    const val FLAG_WHEEL_FREE = 1 shl 31

    const val FEATURE_DRIVE_MODE = 1
    const val FEATURE_4WD = 2
    const val FEATURE_NITROUS = 4

    /** What the mod says about itself in its hello reply. */
    data class ModInfo(val pcName: String, val modVersion: String) {
        /** Drive mode, 4WD, nitrous and door keys arrived with mod 2.1. */
        val hasVehicleKeys: Boolean get() = atLeast(2, 1)

        /** Camera, gearbox mode and pause keys arrived with mod 2.2 (reset works on any mod). */
        val hasGameKeys: Boolean get() = atLeast(2, 2)

        /** Steering the game's menus from the phone arrived with mod 2.4. */
        val hasMenuKeys: Boolean get() = atLeast(2, 4)

        /** The map key arrived with mod 2.3. */
        val hasMapKey: Boolean get() = atLeast(2, 3)

        /** The released wheel is turned by the game's force feedback from mod 2.2. */
        val hasWheelFeedback: Boolean get() = atLeast(2, 2)

        private fun atLeast(wantMajor: Int, wantMinor: Int): Boolean {
            val parts = modVersion.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return major > wantMajor || (major == wantMajor && minor >= wantMinor)
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

    const val WHEEL_RETURN_MIN = 20
    const val WHEEL_RETURN_MAX = 200
    const val WHEEL_RETURN_DEFAULT = 100

    fun hello(deviceName: String, wheelReturnPercent: Int? = null): ByteArray {
        val safe = deviceName.replace(Regex("[|\\r\\n]"), "_").take(48)
        val extra = wheelReturnPercent?.let { "|" + it.coerceIn(WHEEL_RETURN_MIN, WHEEL_RETURN_MAX) } ?: ""
        return "RCP?|$VERSION|$safe$extra".toByteArray()
    }

    fun parseHelloReply(message: String): ModInfo? {
        val parts = message.split('|')
        if (parts.size < 4 || parts[0] != "RCP!" || parts[1].toIntOrNull() != VERSION) return null
        return ModInfo(pcName = parts[2].ifBlank { "PC" }, modVersion = parts[3])
    }

    fun control(
        steering: Float, throttle: Float, brake: Float, clutch: Float, handbrake: Float, buttons: Int,
        lookX: Float = 0f, lookY: Float = 0f, menuButtons: Int = 0,
    ): ByteArray {
        val buf = ByteBuffer.allocate(CONTROL_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('R'.code.toByte()).put('C'.code.toByte()).put('P'.code.toByte()).put(VERSION.toByte())
        buf.putFloat(sane(steering, -1f))
        buf.putFloat(sane(throttle, 0f))
        buf.putFloat(sane(brake, 0f))
        buf.putFloat(sane(clutch, 0f))
        buf.putFloat(sane(handbrake, 0f))
        buf.putInt(buttons)
        buf.putFloat(look(lookX))
        buf.putFloat(look(lookY))
        buf.putInt(menuButtons)
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

    /** "RCPS|<angle>": the released wheel's position, -1..1; null for anything else. */
    fun parseWheelAngle(message: String): Float? {
        if (!message.startsWith("RCPS|")) return null
        val angle = message.substring(5).trim().toFloatOrNull() ?: return null
        return if (angle.isFinite()) angle.coerceIn(-1f, 1f) else null
    }

    private fun look(v: Float): Float = if (v.isFinite()) v.coerceIn(-LOOK_LIMIT, LOOK_LIMIT) else 0f

    /** "RCPW|1" / "RCPW|0": whether the player this phone drives is on foot; null for anything else. */
    fun parseWalking(message: String): Boolean? = when (message.trim()) {
        "RCPW|1" -> true
        "RCPW|0" -> false
        else -> null
    }

    /** What the control packet carries while walking; the mod reads it back the same way. */
    data class WalkAxes(val steering: Float, val throttle: Float, val brake: Float, val handbrake: Float)

    /**
     * The walk stick ([x] right, [y] forward, both -1..1) as the axes the mod walks with:
     * forward = throttle - brake, sideways = steering, sprint = handbrake.
     */
    fun walkAxes(x: Float, y: Float, sprint: Boolean): WalkAxes {
        val sx = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        val sy = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
        return WalkAxes(sx, sy.coerceAtLeast(0f), (-sy).coerceAtLeast(0f), if (sprint) 1f else 0f)
    }

    private fun sane(v: Float, min: Float): Float = if (v.isFinite()) v.coerceIn(min, 1f) else 0f
}
