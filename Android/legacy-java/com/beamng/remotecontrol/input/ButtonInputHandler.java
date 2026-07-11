package com.beamng.remotecontrol.input;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.beamng.remotecontrol.settings.SettingsManager;

/**
 * Button-based steering control with ramp-up.
 * Holding a button gradually increases steering from 0 to 1 over ~300ms,
 * giving analog-like feel from digital buttons.
 */
public class ButtonInputHandler implements SteeringInputHandler {

    private static final long RAMP_TICK_MS = 50;
    private static final float RAMP_STEP = 0.15f; // ~7 ticks (350ms) to reach 1.0

    private final SettingsManager settings;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile float currentSteering = 0f;
    private boolean leftPressed = false;
    private boolean rightPressed = false;
    private float targetSteering = 0f;

    private final Runnable rampRunnable = new Runnable() {
        @Override
        public void run() {
            if (!leftPressed && !rightPressed) {
                currentSteering = 0f;
                return;
            }

            // Ramp toward target
            if (currentSteering < targetSteering) {
                currentSteering = Math.min(targetSteering, currentSteering + RAMP_STEP);
            } else if (currentSteering > targetSteering) {
                currentSteering = Math.max(targetSteering, currentSteering - RAMP_STEP);
            }

            // Keep ticking while buttons are held
            if (leftPressed || rightPressed) {
                handler.postDelayed(this, RAMP_TICK_MS);
            }
        }
    };

    public ButtonInputHandler(Context context) {
        this.settings = SettingsManager.getInstance(context);
    }

    @Override
    public float getSteeringValue() {
        return currentSteering; // Full range ±1.0, sensitivity affects ramp speed
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        handler.removeCallbacks(rampRunnable);
        currentSteering = 0f;
        targetSteering = 0f;
        leftPressed = false;
        rightPressed = false;
    }

    @Override
    public boolean requiresUIControls() {
        return true;
    }

    public View.OnTouchListener getLeftButtonListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    leftPressed = true;
                    updateTarget();
                    startRamp();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    leftPressed = false;
                    updateTarget();
                    break;
            }
            return true;
        };
    }

    public View.OnTouchListener getRightButtonListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    rightPressed = true;
                    updateTarget();
                    startRamp();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rightPressed = false;
                    updateTarget();
                    break;
            }
            return true;
        };
    }

    private void updateTarget() {
        if (leftPressed && !rightPressed) {
            targetSteering = -1f;
        } else if (rightPressed && !leftPressed) {
            targetSteering = 1f;
        } else {
            targetSteering = 0f;
            currentSteering = 0f; // Instant center on release
        }
    }

    private void startRamp() {
        handler.removeCallbacks(rampRunnable);
        handler.post(rampRunnable);
    }
}
