package com.beamng.remotecontrol.input

import android.content.Context
import android.widget.SeekBar

import com.beamng.remotecontrol.settings.SettingsManager

/**
 * Slider (SeekBar) based steering. Drag to steer; snaps back to center on release.
 */
class SliderInputHandler(context: Context) : SteeringInputHandler {

    private val settings: SettingsManager = SettingsManager.getInstance(context)

    @Volatile
    private var currentSteering = 0f
    private var steeringSlider: SeekBar? = null

    override fun getSteeringValue(): Float = currentSteering

    override fun start() {
        // No special startup needed for the slider
    }

    override fun stop() {
        currentSteering = 0f
        steeringSlider?.progress = 50 // Back to center
    }

    override fun requiresUIControls(): Boolean = true

    /**
     * Attach the steering slider (0-100 range, 50 = center) to this handler.
     */
    fun attachSlider(seekBar: SeekBar) {
        steeringSlider = seekBar
        seekBar.max = 100
        seekBar.progress = 50

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Map 0-100 to -1..1
                    currentSteering = (progress - 50) / 50f
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Unused
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Snap back to center when the finger lifts
                seekBar.progress = 50
                currentSteering = 0f
            }
        })
    }
}
