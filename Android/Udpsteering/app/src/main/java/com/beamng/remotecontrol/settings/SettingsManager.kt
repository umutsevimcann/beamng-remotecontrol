package com.beamng.remotecontrol.settings

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

    val controlTypeName: String
        get() = when (controlType) {
            CONTROL_BUTTONS -> "Buttons"
            CONTROL_SLIDER -> "Slider"
            else -> "Gyroscope"
        }

    // ==================== SENSITIVITY ====================

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0.1f, 1.0f)).apply()

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

    // ==================== ANALOG PEDALS ====================

    var isAnalogPedals: Boolean
        get() = prefs.getBoolean(KEY_ANALOG_PEDALS, DEFAULT_ANALOG_PEDALS)
        set(analog) = prefs.edit().putBoolean(KEY_ANALOG_PEDALS, analog).apply()

    // ==================== NETWORK ====================

    var lastIP: String
        get() = prefs.getString(KEY_LAST_IP, "") ?: ""
        set(ip) = prefs.edit().putString(KEY_LAST_IP, ip).apply()

    companion object {
        private const val PREFS_NAME = "BeamNGRemoteSettings"

        // Setting keys
        const val KEY_CONTROL_TYPE = "control_type"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_USE_METRIC = "use_metric"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_LAST_IP = "last_ip"
        const val KEY_DEAD_ZONE = "dead_zone"
        const val KEY_ANALOG_PEDALS = "analog_pedals"

        // Control types
        const val CONTROL_GYROSCOPE = 0
        const val CONTROL_BUTTONS = 1
        const val CONTROL_SLIDER = 2

        // Defaults
        private const val DEFAULT_CONTROL_TYPE = CONTROL_GYROSCOPE
        private const val DEFAULT_SENSITIVITY = 0.5f
        private const val DEFAULT_USE_METRIC = true
        private const val DEFAULT_HAPTIC_ENABLED = true
        private const val DEFAULT_DEAD_ZONE = 3.0f
        private const val DEFAULT_ANALOG_PEDALS = true

        @Volatile
        private var instance: SettingsManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): SettingsManager =
            instance ?: SettingsManager(context).also { instance = it }
    }
}
