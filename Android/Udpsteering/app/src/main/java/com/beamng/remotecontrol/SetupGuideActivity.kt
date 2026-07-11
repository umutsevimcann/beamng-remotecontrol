package com.beamng.remotecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.ui.SetupGuideScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

/** Personalized in-app setup documentation (real IP, exact ports). */
class SetupGuideActivity : ComponentActivity() {

    private var phoneIp by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NightGarageTheme {
                SetupGuideScreen(phoneIp = phoneIp)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phoneIp = NetworkUtils.wifiIpv4String(this)
    }
}
