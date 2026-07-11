package com.beamng.remotecontrol.input

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

import com.beamng.remotecontrol.settings.SettingsManager

/**
 * Button-based steering control with ramp-up.
 * Holding a button gradually increases steering from 0 to 1 over ~350ms,
 * giving analog-like feel from digital buttons.
 */
class ButtonInputHandler(context: Context) : SteeringInputHandler {

    private val settings: SettingsManager = SettingsManager.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentSteering = 0f
    private var leftPressed = false
    private var rightPressed = false
    private var targetSteering = 0f

    private val rampRunnable = object : Runnable {
        override fun run() {
            if (!leftPressed && !rightPressed) {
                currentSteering = 0f
                return
            }

            // Ramp toward target
            if (currentSteering < targetSteering) {
                currentSteering = minOf(targetSteering, currentSteering + RAMP_STEP)
            } else if (currentSteering > targetSteering) {
                currentSteering = maxOf(targetSteering, currentSteering - RAMP_STEP)
            }

            // Keep ticking while buttons are held
            if (leftPressed || rightPressed) {
                handler.postDelayed(this, RAMP_TICK_MS)
            }
        }
    }

    override fun getSteeringValue(): Float = currentSteering

    override fun start() {
    }

    override fun stop() {
        handler.removeCallbacks(rampRunnable)
        currentSteering = 0f
        targetSteering = 0f
        leftPressed = false
        rightPressed = false
    }

    override fun requiresUIControls(): Boolean = true

    @get:SuppressLint("ClickableViewAccessibility")
    val leftButtonListener: View.OnTouchListener
        get() = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    leftPressed = true
                    updateTarget()
                    startRamp()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    leftPressed = false
                    updateTarget()
                }
            }
            true
        }

    @get:SuppressLint("ClickableViewAccessibility")
    val rightButtonListener: View.OnTouchListener
        get() = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rightPressed = true
                    updateTarget()
                    startRamp()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rightPressed = false
                    updateTarget()
                }
            }
            true
        }

    private fun updateTarget() {
        if (leftPressed && !rightPressed) {
            targetSteering = -1f
        } else if (rightPressed && !leftPressed) {
            targetSteering = 1f
        } else {
            targetSteering = 0f
            currentSteering = 0f // Instant center on release
        }
    }

    private fun startRamp() {
        handler.removeCallbacks(rampRunnable)
        handler.post(rampRunnable)
    }

    companion object {
        private const val RAMP_TICK_MS = 50L
        private const val RAMP_STEP = 0.15f // ~7 ticks (350ms) to reach 1.0
    }
}
