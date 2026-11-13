package com.beamng.remotecontrol

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.ui.SetupGuideScreen
import com.beamng.remotecontrol.ui.theme.RemoteTheme

/** Personalized in-app setup documentation (real IP, exact ports). */
class SetupGuideActivity : AppCompatActivity() {

    private var phoneIp by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge to edge on every Android version; the screens pad themselves
        // with safeDrawingPadding (system bars + display cutout).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            RemoteTheme {
                SetupGuideScreen(phoneIp = phoneIp)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phoneIp = NetworkUtils.phoneIpv4String(this)
    }
}
