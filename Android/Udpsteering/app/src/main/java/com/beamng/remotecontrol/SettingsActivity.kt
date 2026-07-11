package com.beamng.remotecontrol

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.SettingsScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

/**
 * Settings screen: control type and preferences. Changes save instantly
 * (SettingsScreen writes straight to SettingsManager on every change).
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NightGarageTheme {
                SettingsScreen(SettingsManager.getInstance(this))
            }
        }
    }
}
