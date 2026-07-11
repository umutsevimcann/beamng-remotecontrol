package com.beamng.remotecontrol.input

import android.content.Context

import com.beamng.remotecontrol.settings.SettingsManager

/**
 * Slider-based steering. Drag to steer; snaps back to center on release.
 */
class SliderInputHandler(context: Context) : SteeringInputHandler {

    private val settings: SettingsManager = SettingsManager.getInstance(context)

    @Volatile
    private var currentSteering = 0f

    override fun getSteeringValue(): Float = currentSteering

    override fun start() {
        // No special startup needed for the slider
    }

    override fun stop() {
        currentSteering = 0f
    }

    override fun requiresUIControls(): Boolean = true

    /** UI pushes the current slider position, -1 (left) .. 1 (right). */
    fun setValue(value: Float) {
        currentSteering = value.coerceIn(-1f, 1f)
    }

    /** Finger lifted — snap back to center. */
    fun release() {
        currentSteering = 0f
    }
}
