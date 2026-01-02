package com.beamng.remotecontrol.input;

import android.content.Context;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Ayarlara göre doğru SteeringInputHandler'ı oluşturur.
 */
public class InputHandlerFactory {

    /**
     * Kullanıcının ayarlarına göre uygun input handler'ı oluşturur.
     */
    public static SteeringInputHandler createHandler(Context context) {
        SettingsManager settings = SettingsManager.getInstance(context);
        int controlType = settings.getControlType();
        
        switch (controlType) {
            case SettingsManager.CONTROL_BUTTONS:
                return new ButtonInputHandler(context);
            case SettingsManager.CONTROL_SLIDER:
                return new SliderInputHandler(context);
            case SettingsManager.CONTROL_GYROSCOPE:
            default:
                return new GyroscopeInputHandler(context);
        }
    }
    
    /**
     * Belirli bir tip için handler oluşturur.
     */
    public static SteeringInputHandler createHandler(Context context, int controlType) {
        switch (controlType) {
            case SettingsManager.CONTROL_BUTTONS:
                return new ButtonInputHandler(context);
            case SettingsManager.CONTROL_SLIDER:
                return new SliderInputHandler(context);
            case SettingsManager.CONTROL_GYROSCOPE:
            default:
                return new GyroscopeInputHandler(context);
        }
    }
}
