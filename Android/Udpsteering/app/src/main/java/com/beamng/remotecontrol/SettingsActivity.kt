package com.beamng.remotecontrol

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.beamng.remotecontrol.settings.SettingsManager
import android.content.Intent
import com.beamng.remotecontrol.ui.settings.SettingsScreen
import com.beamng.remotecontrol.ui.theme.RemoteTheme

/**
 * Settings screen: control type and preferences. Changes save instantly
 * (SettingsScreen writes straight to SettingsManager on every change).
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemoteTheme {
                SettingsScreen(
                    settings = SettingsManager.getInstance(this),
                    onBack = ::finish,
                    onSetupGuide = { startActivity(Intent(this, SetupGuideActivity::class.java)) },
                )
            }
        }
    }
}
