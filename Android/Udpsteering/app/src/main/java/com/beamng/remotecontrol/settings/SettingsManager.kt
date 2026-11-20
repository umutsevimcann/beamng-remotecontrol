package com.beamng.remotecontrol.settings

import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.ui.drive.WheelStyle
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.theme.ThemeMode
import android.content.Context
import android.content.SharedPreferences

/**
 * Central settings store. All app settings are read/written through this class.
 * Kotlin port of the Java original (see Android/legacy-java) — behavior identical.
 */
class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== CONTROL TYPE ====================

    var controlType: Int
        get() = prefs.getInt(KEY_CONTROL_TYPE, DEFAULT_CONTROL_TYPE)
        set(type) = prefs.edit().putInt(KEY_CONTROL_TYPE, type).apply()

    // ==================== GYRO LOCK ANGLE ====================

    /** Phone tilt, in degrees, that gives full steering lock in gyro mode. */
    var steeringLockDeg: Float
        get() = prefs.getFloat(KEY_STEERING_LOCK, DEFAULT_STEERING_LOCK)
        set(value) = prefs.edit().putFloat(KEY_STEERING_LOCK, value.coerceIn(30f, 120f)).apply()

    // ==================== UNITS ====================

    fun useMetricUnits(): Boolean = prefs.getBoolean(KEY_USE_METRIC, DEFAULT_USE_METRIC)

    fun setUseMetricUnits(useMetric: Boolean) =
        prefs.edit().putBoolean(KEY_USE_METRIC, useMetric).apply()

    // ==================== HAPTIC ====================

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_HAPTIC_ENABLED)
        set(enabled) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()

    // ==================== DEAD ZONE ====================

    var deadZone: Float
        get() = prefs.getFloat(KEY_DEAD_ZONE, DEFAULT_DEAD_ZONE)
        set(degrees) = prefs.edit().putFloat(KEY_DEAD_ZONE, degrees.coerceIn(0f, 10f)).apply()

    // ==================== DASHBOARD-ONLY MODE ====================

    /** Controls hidden, phone acts as a pure telemetry dashboard. */
    var isDashboardOnly: Boolean
        get() = prefs.getBoolean(KEY_DASHBOARD_ONLY, DEFAULT_DASHBOARD_ONLY)
        set(enabled) = prefs.edit().putBoolean(KEY_DASHBOARD_ONLY, enabled).apply()

    // ==================== 0-100 PERFORMANCE TIMER ====================

    var isPerfTimerEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERF_TIMER, DEFAULT_PERF_TIMER)
        set(enabled) = prefs.edit().putBoolean(KEY_PERF_TIMER, enabled).apply()

    // ==================== ANALOG PEDALS ====================

    /**
     * Pedal value follows finger position instead of on/off. Only useful with
     * the RemoteControlPro mod: the stock game reads each pedal as a button
     * (value > 0.5), so off by default.
     */
    var isAnalogPedals: Boolean
        get() = prefs.getBoolean(KEY_ANALOG_PEDALS, DEFAULT_ANALOG_PEDALS)
        set(enabled) = prefs.edit().putBoolean(KEY_ANALOG_PEDALS, enabled).apply()

    // ==================== DRIVE SCREEN LAYOUT ====================

    /** Which gauge layout the drive screen shows. */
    var dashLayout: DashLayout
        get() = DashLayout.fromKey(prefs.getString(KEY_DASH_LAYOUT, null))
        set(layout) = prefs.edit().putString(KEY_DASH_LAYOUT, layout.key).apply()

    /** Which steering wheel the drive screen draws. */
    var wheelStyle: WheelStyle
        get() = WheelStyle.fromKey(prefs.getString(KEY_WHEEL_STYLE, null))
        set(style) = prefs.edit().putString(KEY_WHEEL_STYLE, style.key).apply()

    /** Wheel on the right, pedals on the left — for left-handed players. */
    var isMirrored: Boolean
        get() = prefs.getBoolean(KEY_MIRRORED, DEFAULT_MIRRORED)
        set(enabled) = prefs.edit().putBoolean(KEY_MIRRORED, enabled).apply()

    /** Released on-screen wheel follows the car (counter-steers in a slide) instead of snapping to the centre. */
    var isWheelSelfAlign: Boolean
        get() = prefs.getBoolean(KEY_WHEEL_SELF_ALIGN, true)
        set(enabled) = prefs.edit().putBoolean(KEY_WHEEL_SELF_ALIGN, enabled).apply()

    /** How hard the released wheel is pulled back, percent (the force-feedback strength of a wheel set). */
    var wheelReturnPercent: Int
        get() = prefs.getInt(KEY_WHEEL_RETURN, ModProtocol.WHEEL_RETURN_DEFAULT)
            .coerceIn(ModProtocol.WHEEL_RETURN_MIN, ModProtocol.WHEEL_RETURN_MAX)
        set(value) = prefs.edit().putInt(
            KEY_WHEEL_RETURN, value.coerceIn(ModProtocol.WHEEL_RETURN_MIN, ModProtocol.WHEEL_RETURN_MAX),
        ).apply()

    // ==================== THEME ====================

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(KEY_THEME, null))
        set(mode) = prefs.edit().putString(KEY_THEME, mode.key).apply()

    // ==================== MOD KEYS ====================

    /** Which RemoteControlPro keys the drive screen shows (they exist only with the mod). */
    var modShowShift: Boolean
        get() = prefs.getBoolean(KEY_MOD_SHIFT, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_SHIFT, v).apply()
    var modShowHandbrake: Boolean
        get() = prefs.getBoolean(KEY_MOD_HANDBRAKE, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_HANDBRAKE, v).apply()
    var modShowHorn: Boolean
        get() = prefs.getBoolean(KEY_MOD_HORN, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_HORN, v).apply()
    var modShowLights: Boolean
        get() = prefs.getBoolean(KEY_MOD_LIGHTS, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_LIGHTS, v).apply()

    var modShowDriveMode: Boolean
        get() = prefs.getBoolean(KEY_MOD_DRIVE_MODE, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_DRIVE_MODE, v).apply()
    var modShow4wd: Boolean
        get() = prefs.getBoolean(KEY_MOD_4WD, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_4WD, v).apply()
    var modShowNitrous: Boolean
        get() = prefs.getBoolean(KEY_MOD_NITROUS, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_NITROUS, v).apply()
    var modShowDoors: Boolean
        get() = prefs.getBoolean(KEY_MOD_DOORS, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_DOORS, v).apply()

    var modShowLowRange: Boolean
        get() = prefs.getBoolean(KEY_MOD_LOW_RANGE, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_LOW_RANGE, v).apply()
    var modShowDiffLock: Boolean
        get() = prefs.getBoolean(KEY_MOD_DIFF_LOCK, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_DIFF_LOCK, v).apply()
    var modShowCamera: Boolean
        get() = prefs.getBoolean(KEY_MOD_CAMERA, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_CAMERA, v).apply()
    var modShowGearbox: Boolean
        get() = prefs.getBoolean(KEY_MOD_GEARBOX, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_GEARBOX, v).apply()
    var modShowReset: Boolean
        get() = prefs.getBoolean(KEY_MOD_RESET, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_RESET, v).apply()
    var modShowPause: Boolean
        get() = prefs.getBoolean(KEY_MOD_PAUSE, true)
        set(v) = prefs.edit().putBoolean(KEY_MOD_PAUSE, v).apply()

    /** Name of the PC the mod reported last time (empty when we only know its IP). */
    var lastPcName: String
        get() = prefs.getString(KEY_LAST_PC_NAME, "") ?: ""
        set(name) = prefs.edit().putString(KEY_LAST_PC_NAME, name).apply()

    /** Version of the mod we last connected to; empty = never seen. */
    var lastModVersion: String
        get() = prefs.getString(KEY_LAST_MOD_VERSION, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_MOD_VERSION, v).apply()

    // ==================== NETWORK ====================

    var lastIP: String
        get() = prefs.getString(KEY_LAST_IP, "") ?: ""
        set(ip) = prefs.edit().putString(KEY_LAST_IP, ip).apply()

    companion object {
        private const val PREFS_NAME = "BeamNGRemoteSettings"

        // Setting keys
        const val KEY_CONTROL_TYPE = "control_type"
        const val KEY_STEERING_LOCK = "steering_lock_deg"
        const val KEY_USE_METRIC = "use_metric"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_LAST_IP = "last_ip"
        const val KEY_DEAD_ZONE = "dead_zone"
        const val KEY_DASHBOARD_ONLY = "dashboard_only"
        const val KEY_PERF_TIMER = "perf_timer"
        const val KEY_ANALOG_PEDALS = "analog_pedals"
        const val KEY_DASH_LAYOUT = "dash_layout"
        const val KEY_MIRRORED = "mirrored_layout"
        const val KEY_WHEEL_SELF_ALIGN = "wheel_self_align"
        const val KEY_WHEEL_STYLE = "wheel_style"
        const val KEY_WHEEL_RETURN = "wheel_return_percent"
        const val KEY_THEME = "theme_mode"
        const val KEY_MOD_SHIFT = "mod_show_shift"
        const val KEY_MOD_HANDBRAKE = "mod_show_handbrake"
        const val KEY_MOD_HORN = "mod_show_horn"
        const val KEY_MOD_LIGHTS = "mod_show_lights"
        const val KEY_MOD_DRIVE_MODE = "mod_show_drive_mode"
        const val KEY_MOD_4WD = "mod_show_4wd"
        const val KEY_MOD_NITROUS = "mod_show_nitrous"
        const val KEY_MOD_DOORS = "mod_show_doors"
        const val KEY_MOD_LOW_RANGE = "mod_show_low_range"
        const val KEY_MOD_DIFF_LOCK = "mod_show_diff_lock"
        const val KEY_MOD_CAMERA = "mod_show_camera"
        const val KEY_MOD_GEARBOX = "mod_show_gearbox"
        const val KEY_MOD_RESET = "mod_show_reset"
        const val KEY_MOD_PAUSE = "mod_show_pause"
        const val KEY_LAST_PC_NAME = "last_pc_name"
        const val KEY_LAST_MOD_VERSION = "last_mod_version"

        // Control types
        const val CONTROL_GYROSCOPE = 0
        const val CONTROL_BUTTONS = 1
        const val CONTROL_SLIDER = 2

        // Defaults
        private const val DEFAULT_CONTROL_TYPE = CONTROL_GYROSCOPE
        private const val DEFAULT_STEERING_LOCK = 70f
        private const val DEFAULT_USE_METRIC = true
        private const val DEFAULT_HAPTIC_ENABLED = true
        private const val DEFAULT_ANALOG_PEDALS = false
        private const val DEFAULT_MIRRORED = false
        private const val DEFAULT_DEAD_ZONE = 3.0f
        private const val DEFAULT_DASHBOARD_ONLY = false
        private const val DEFAULT_PERF_TIMER = false

        @Volatile
        private var instance: SettingsManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): SettingsManager =
            instance ?: SettingsManager(context).also { instance = it }
    }
}
