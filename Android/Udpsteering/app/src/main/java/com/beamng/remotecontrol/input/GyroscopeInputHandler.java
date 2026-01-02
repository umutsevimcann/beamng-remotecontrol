package com.beamng.remotecontrol.input;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.beamng.remotecontrol.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Jiroskop/Accelerometer tabanlı direksiyon kontrolü.
 * Telefonu sağa/sola eğerek araba yönlendirilir.
 */
public class GyroscopeInputHandler implements SteeringInputHandler, SensorEventListener {

    private static final int MAX_SAMPLE_SIZE = 5;
    
    private final Context context;
    private final SensorManager sensorManager;
    private final SettingsManager settings;
    private final Display display;
    
    private float currentAngle = 0f;
    private int orientationHandler = 1;
    private List<Float> rollingAverage = new ArrayList<>();
    
    public GyroscopeInputHandler(Context context) {
        this.context = context;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.settings = SettingsManager.getInstance(context);
        this.display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }
    
    @Override
    public float getSteeringValue() {
        float sensitivity = settings.getSensitivity();
        // -1 ile 1 arasına normalize et
        float normalized = Math.max(-1f, Math.min(1f, (currentAngle * sensitivity * orientationHandler) / 45f));
        return normalized;
    }
    
    @Override
    public void start() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
                
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
    }
    
    @Override
    public void stop() {
        sensorManager.unregisterListener(this);
    }
    
    @Override
    public boolean requiresUIControls() {
        return false; // Jiroskop için ekstra UI gerekmez
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // Açıyı hesapla
                currentAngle = (float) (Math.asin(
                    -event.values[1] / Math.sqrt(
                        event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                    )
                ) * 180 / Math.PI);
                
                // Yumuşatma için ortalama al
                rollingAverage = roll(rollingAverage, event.values[1]);
                break;
                
            case Sensor.TYPE_ROTATION_VECTOR:
                // Telefon yönünü algıla (landscape left/right)
                int rotation = display.getRotation();
                if (rotation == Surface.ROTATION_90) {
                    orientationHandler = 1;
                } else if (rotation == Surface.ROTATION_270) {
                    orientationHandler = -1;
                }
                break;
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Kullanılmıyor
    }
    
    private List<Float> roll(List<Float> list, float newMember) {
        if (list.size() >= MAX_SAMPLE_SIZE) {
            list.remove(0);
        }
        list.add(newMember);
        return list;
    }
}
