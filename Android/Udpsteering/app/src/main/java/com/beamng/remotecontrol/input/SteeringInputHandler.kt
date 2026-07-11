package com.beamng.remotecontrol.input

/**
 * Contract every control type implements, so steering methods
 * (Gyroscope, Buttons, Slider) stay interchangeable.
 */
interface SteeringInputHandler {

    /** Current steering angle: -1.0 (full left) .. 1.0 (full right), 0 = straight. */
    fun getSteeringValue(): Float

    /** Start the handler (register sensor listeners etc.). */
    fun start()

    /** Stop the handler and release resources. */
    fun stop()

    /** Whether this handler needs on-screen controls (Buttons/Slider: yes, Gyro: no). */
    fun requiresUIControls(): Boolean
}
