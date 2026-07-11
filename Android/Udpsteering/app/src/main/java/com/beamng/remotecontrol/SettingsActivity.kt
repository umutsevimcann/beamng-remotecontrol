package com.beamng.remotecontrol

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.beamng.remotecontrol.settings.SettingsManager

/**
 * Settings screen: control type and other preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    private lateinit var radioGroupControlType: RadioGroup
    private lateinit var radioGyroscope: RadioButton
    private lateinit var radioButtons: RadioButton
    private lateinit var radioSlider: RadioButton

    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var textSensitivityValue: TextView

    private lateinit var switchMetric: SwitchCompat
    private lateinit var switchHaptic: SwitchCompat
    private lateinit var switchAnalogPedals: SwitchCompat

    private lateinit var seekBarDeadZone: SeekBar
    private lateinit var textDeadZoneValue: TextView

    private lateinit var btnSaveSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager.getInstance(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveSettings()
            }
        })

        initViews()
        loadCurrentSettings()
        setupListeners()
    }

    private fun initViews() {
        radioGroupControlType = findViewById(R.id.radioGroupControlType)
        radioGyroscope = findViewById(R.id.radioGyroscope)
        radioButtons = findViewById(R.id.radioButtons)
        radioSlider = findViewById(R.id.radioSlider)

        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        textSensitivityValue = findViewById(R.id.textSensitivityValue)

        switchMetric = findViewById(R.id.switchMetric)
        switchHaptic = findViewById(R.id.switchHaptic)
        switchAnalogPedals = findViewById(R.id.switchAnalogPedals)

        seekBarDeadZone = findViewById(R.id.seekBarDeadZone)
        textDeadZoneValue = findViewById(R.id.textDeadZoneValue)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
    }

    private fun loadCurrentSettings() {
        // Control type
        when (settings.controlType) {
            SettingsManager.CONTROL_BUTTONS -> radioButtons.isChecked = true
            SettingsManager.CONTROL_SLIDER -> radioSlider.isChecked = true
            else -> radioGyroscope.isChecked = true
        }

        // Sensitivity (0.1 - 1.0 mapped to 10-100)
        val sensitivityPercent = Math.round(settings.sensitivity * 100)
        seekBarSensitivity.progress = sensitivityPercent
        textSensitivityValue.text = "$sensitivityPercent%"

        // Dead zone
        val deadZoneDegrees = Math.round(settings.deadZone)
        seekBarDeadZone.progress = deadZoneDegrees
        textDeadZoneValue.text = "$deadZoneDegrees°"

        // Toggles
        switchMetric.isChecked = settings.useMetricUnits()
        switchHaptic.isChecked = settings.isHapticEnabled
        switchAnalogPedals.isChecked = settings.isAnalogPedals
    }

    private fun setupListeners() {
        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Enforce a 10% minimum
                val adjusted = maxOf(10, progress)
                textSensitivityValue.text = "$adjusted%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        seekBarDeadZone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textDeadZoneValue.text = "$progress°"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        btnSaveSettings.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        // Control type
        settings.controlType = when (radioGroupControlType.checkedRadioButtonId) {
            R.id.radioButtons -> SettingsManager.CONTROL_BUTTONS
            R.id.radioSlider -> SettingsManager.CONTROL_SLIDER
            else -> SettingsManager.CONTROL_GYROSCOPE
        }

        // Sensitivity
        settings.sensitivity = maxOf(10, seekBarSensitivity.progress) / 100f

        // Dead zone
        settings.deadZone = seekBarDeadZone.progress.toFloat()

        // Toggles
        settings.setUseMetricUnits(switchMetric.isChecked)
        settings.isHapticEnabled = switchHaptic.isChecked
        settings.isAnalogPedals = switchAnalogPedals.isChecked

        Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
