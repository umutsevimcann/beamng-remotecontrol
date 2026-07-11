package com.beamng.remotecontrol;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Ayarlar ekranı. Kullanıcı kontrol tipini ve diğer tercihleri buradan seçer.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    
    private RadioGroup radioGroupControlType;
    private RadioButton radioGyroscope;
    private RadioButton radioButtons;
    private RadioButton radioSlider;
    
    private SeekBar seekBarSensitivity;
    private TextView textSensitivityValue;
    
    private SwitchCompat switchMetric;
    private SwitchCompat switchHaptic;
    private SwitchCompat switchAnalogPedals;

    private SeekBar seekBarDeadZone;
    private TextView textDeadZoneValue;

    private Button btnSaveSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        settings = SettingsManager.getInstance(this);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveSettings();
            }
        });

        initViews();
        loadCurrentSettings();
        setupListeners();
    }
    
    private void initViews() {
        radioGroupControlType = findViewById(R.id.radioGroupControlType);
        radioGyroscope = findViewById(R.id.radioGyroscope);
        radioButtons = findViewById(R.id.radioButtons);
        radioSlider = findViewById(R.id.radioSlider);
        
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity);
        textSensitivityValue = findViewById(R.id.textSensitivityValue);
        
        switchMetric = findViewById(R.id.switchMetric);
        switchHaptic = findViewById(R.id.switchHaptic);
        switchAnalogPedals = findViewById(R.id.switchAnalogPedals);

        seekBarDeadZone = findViewById(R.id.seekBarDeadZone);
        textDeadZoneValue = findViewById(R.id.textDeadZoneValue);

        btnSaveSettings = findViewById(R.id.btnSaveSettings);
    }
    
    private void loadCurrentSettings() {
        // Kontrol tipi
        int currentType = settings.getControlType();
        switch (currentType) {
            case SettingsManager.CONTROL_BUTTONS:
                radioButtons.setChecked(true);
                break;
            case SettingsManager.CONTROL_SLIDER:
                radioSlider.setChecked(true);
                break;
            case SettingsManager.CONTROL_GYROSCOPE:
            default:
                radioGyroscope.setChecked(true);
                break;
        }
        
        // Hassasiyet (0.1 - 1.0 aralığını 10-100'e dönüştür)
        int sensitivityPercent = Math.round(settings.getSensitivity() * 100);
        seekBarSensitivity.setProgress(sensitivityPercent);
        textSensitivityValue.setText(sensitivityPercent + "%");
        
        // Dead zone
        int deadZoneDegrees = Math.round(settings.getDeadZone());
        seekBarDeadZone.setProgress(deadZoneDegrees);
        textDeadZoneValue.setText(deadZoneDegrees + "°");

        // Toggles
        switchMetric.setChecked(settings.useMetricUnits());
        switchHaptic.setChecked(settings.isHapticEnabled());
        switchAnalogPedals.setChecked(settings.isAnalogPedals());
    }
    
    private void setupListeners() {
        // Hassasiyet değişimi
        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Minimum %10 olsun
                int adjusted = Math.max(10, progress);
                textSensitivityValue.setText(adjusted + "%");
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Dead zone değişimi
        seekBarDeadZone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textDeadZoneValue.setText(progress + "°");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Kaydet butonu
        btnSaveSettings.setOnClickListener(v -> saveSettings());
    }
    
    private void saveSettings() {
        // Kontrol tipi
        int selectedType = SettingsManager.CONTROL_GYROSCOPE;
        int checkedId = radioGroupControlType.getCheckedRadioButtonId();
        if (checkedId == R.id.radioButtons) {
            selectedType = SettingsManager.CONTROL_BUTTONS;
        } else if (checkedId == R.id.radioSlider) {
            selectedType = SettingsManager.CONTROL_SLIDER;
        }
        settings.setControlType(selectedType);
        
        // Hassasiyet
        float sensitivity = Math.max(10, seekBarSensitivity.getProgress()) / 100f;
        settings.setSensitivity(sensitivity);
        
        // Dead zone
        settings.setDeadZone((float) seekBarDeadZone.getProgress());

        // Toggles
        settings.setUseMetricUnits(switchMetric.isChecked());
        settings.setHapticEnabled(switchHaptic.isChecked());
        settings.setAnalogPedals(switchAnalogPedals.isChecked());
        
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        finish(); // Ayarlar ekranını kapat
    }
    
}
