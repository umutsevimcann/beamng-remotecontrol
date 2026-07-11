package com.beamng.remotecontrol.input

import android.content.Context
import com.beamng.remotecontrol.settings.SettingsManager

/** Creates the right [SteeringInputHandler] for the user's settings. */
object InputHandlerFactory {

    @JvmStatic
    fun createHandler(context: Context): SteeringInputHandler =
        createHandler(context, SettingsManager.getInstance(context).controlType)

    @JvmStatic
    fun createHandler(context: Context, controlType: Int): SteeringInputHandler =
        when (controlType) {
            SettingsManager.CONTROL_BUTTONS -> ButtonInputHandler(context)
            SettingsManager.CONTROL_SLIDER -> SliderInputHandler(context)
            else -> GyroscopeInputHandler(context)
        }
}
