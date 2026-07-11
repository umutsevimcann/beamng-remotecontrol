package com.beamng.remotecontrol.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Merkezi ayarlar yöneticisi.
 * Tüm uygulama ayarları bu sınıf üzerinden okunur/yazılır.
 */
public class SettingsManager {

    private static final String PREFS_NAME = "BeamNGRemoteSettings";
    
    // Ayar Anahtarları
    public static final String KEY_CONTROL_TYPE = "control_type";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_USE_METRIC = "use_metric";
    public static final String KEY_HAPTIC_ENABLED = "haptic_enabled";
    public static final String KEY_LAST_IP = "last_ip";
    public static final String KEY_DEAD_ZONE = "dead_zone";
    public static final String KEY_ANALOG_PEDALS = "analog_pedals";

    // Kontrol Tipleri
    public static final int CONTROL_GYROSCOPE = 0;
    public static final int CONTROL_BUTTONS = 1;
    public static final int CONTROL_SLIDER = 2;
    
    // Varsayılan Değerler
    private static final int DEFAULT_CONTROL_TYPE = CONTROL_GYROSCOPE;
    private static final float DEFAULT_SENSITIVITY = 0.5f;
    private static final boolean DEFAULT_USE_METRIC = true;
    private static final boolean DEFAULT_HAPTIC_ENABLED = true;
    private static final float DEFAULT_DEAD_ZONE = 3.0f;
    private static final boolean DEFAULT_ANALOG_PEDALS = true;
    
    private final SharedPreferences prefs;
    private static SettingsManager instance;
    
    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Singleton instance al.
     */
    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }
    
    // ==================== CONTROL TYPE ====================
    
    public int getControlType() {
        return prefs.getInt(KEY_CONTROL_TYPE, DEFAULT_CONTROL_TYPE);
    }
    
    public void setControlType(int type) {
        prefs.edit().putInt(KEY_CONTROL_TYPE, type).apply();
    }
    
    public String getControlTypeName() {
        switch (getControlType()) {
            case CONTROL_BUTTONS: return "Buttons";
            case CONTROL_SLIDER: return "Slider";
            case CONTROL_GYROSCOPE:
            default: return "Gyroscope";
        }
    }
    
    // ==================== SENSITIVITY ====================
    
    public float getSensitivity() {
        return prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY);
    }
    
    public void setSensitivity(float value) {
        prefs.edit().putFloat(KEY_SENSITIVITY, Math.max(0.1f, Math.min(1.0f, value))).apply();
    }
    
    // ==================== UNITS ====================
    
    public boolean useMetricUnits() {
        return prefs.getBoolean(KEY_USE_METRIC, DEFAULT_USE_METRIC);
    }
    
    public void setUseMetricUnits(boolean useMetric) {
        prefs.edit().putBoolean(KEY_USE_METRIC, useMetric).apply();
    }
    
    // ==================== HAPTIC ====================
    
    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_HAPTIC_ENABLED);
    }
    
    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply();
    }
    
    // ==================== DEAD ZONE ====================

    public float getDeadZone() {
        return prefs.getFloat(KEY_DEAD_ZONE, DEFAULT_DEAD_ZONE);
    }

    public void setDeadZone(float degrees) {
        prefs.edit().putFloat(KEY_DEAD_ZONE, Math.max(0f, Math.min(10f, degrees))).apply();
    }

    // ==================== ANALOG PEDALS ====================

    public boolean isAnalogPedals() {
        return prefs.getBoolean(KEY_ANALOG_PEDALS, DEFAULT_ANALOG_PEDALS);
    }

    public void setAnalogPedals(boolean analog) {
        prefs.edit().putBoolean(KEY_ANALOG_PEDALS, analog).apply();
    }

    // ==================== NETWORK ====================
    
    public String getLastIP() {
        return prefs.getString(KEY_LAST_IP, "");
    }
    
    public void setLastIP(String ip) {
        prefs.edit().putString(KEY_LAST_IP, ip).apply();
    }
}
