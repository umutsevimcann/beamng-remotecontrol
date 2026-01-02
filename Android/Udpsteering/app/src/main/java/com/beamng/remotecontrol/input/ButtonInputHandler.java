package com.beamng.remotecontrol.input;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Buton tabanlı direksiyon kontrolü.
 * Sol/Sağ butonlarına basarak araba yönlendirilir.
 */
public class ButtonInputHandler implements SteeringInputHandler {

    private final SettingsManager settings;
    
    private float currentSteering = 0f;
    private boolean leftPressed = false;
    private boolean rightPressed = false;
    
    public ButtonInputHandler(Context context) {
        this.settings = SettingsManager.getInstance(context);
    }
    
    @Override
    public float getSteeringValue() {
        float sensitivity = settings.getSensitivity();
        return currentSteering * sensitivity;
    }
    
    @Override
    public void start() {
        // Butonlar için özel başlatma gerekmez
    }
    
    @Override
    public void stop() {
        currentSteering = 0f;
        leftPressed = false;
        rightPressed = false;
    }
    
    @Override
    public boolean requiresUIControls() {
        return true; // Butonlar gerekiyor
    }
    
    /**
     * Sol butona bağlanacak OnTouchListener.
     */
    public View.OnTouchListener getLeftButtonListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    leftPressed = true;
                    updateSteering();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    leftPressed = false;
                    updateSteering();
                    break;
            }
            return true;
        };
    }
    
    /**
     * Sağ butona bağlanacak OnTouchListener.
     */
    public View.OnTouchListener getRightButtonListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    rightPressed = true;
                    updateSteering();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rightPressed = false;
                    updateSteering();
                    break;
            }
            return true;
        };
    }
    
    private void updateSteering() {
        if (leftPressed && !rightPressed) {
            currentSteering = -1f; // Tam sola
        } else if (rightPressed && !leftPressed) {
            currentSteering = 1f; // Tam sağa
        } else {
            currentSteering = 0f; // Ortada veya ikisi de basılı
        }
    }
}
