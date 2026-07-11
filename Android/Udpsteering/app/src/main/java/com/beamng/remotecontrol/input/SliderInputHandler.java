package com.beamng.remotecontrol.input;

import android.content.Context;
import android.widget.SeekBar;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Slider (SeekBar) tabanlı direksiyon kontrolü.
 * Parmağı kaydırarak araba yönlendirilir. Bırakınca merkeze döner.
 */
public class SliderInputHandler implements SteeringInputHandler {

    private final SettingsManager settings;
    
    private volatile float currentSteering = 0f;
    private SeekBar steeringSlider;
    
    public SliderInputHandler(Context context) {
        this.settings = SettingsManager.getInstance(context);
    }
    
    @Override
    public float getSteeringValue() {
        return currentSteering; // Full range ±1.0
    }
    
    @Override
    public void start() {
        // Slider için özel başlatma gerekmez
    }
    
    @Override
    public void stop() {
        currentSteering = 0f;
        if (steeringSlider != null) {
            steeringSlider.setProgress(50); // Ortaya getir
        }
    }
    
    @Override
    public boolean requiresUIControls() {
        return true; // Slider gerekiyor
    }
    
    /**
     * Slider'ı bu handler'a bağla.
     * @param seekBar Direksiyon slider'ı (0-100, 50 = orta)
     */
    public void attachSlider(SeekBar seekBar) {
        this.steeringSlider = seekBar;
        seekBar.setMax(100);
        seekBar.setProgress(50);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 0-100 aralığını 1 ile -1 arasına dönüştür (Tersine çevrildi)
                    currentSteering = (progress - 50) / 50f;
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Kullanılmıyor
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Parmak bırakıldığında merkeze dön
                seekBar.setProgress(50);
                currentSteering = 0f;
            }
        });
    }
}
