package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.ui.WelcomeScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

class WelcomeActivity : AppCompatActivity() {

    private var phoneIp by mutableStateOf<String?>(null)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openScanner()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NightGarageTheme {
                var showManual by androidx.compose.runtime.remember { mutableStateOf(false) }
                WelcomeScreen(
                    phoneIp = phoneIp,
                    onScanClick = ::scanRequested,
                    onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onGuideClick = { startActivity(Intent(this, SetupGuideActivity::class.java)) },
                    onManualClick = { showManual = true },
                )
                if (showManual) {
                    com.beamng.remotecontrol.ui.ManualCodeDialog(
                        onDismiss = { showManual = false },
                        onConnect = { input ->
                            showManual = false
                            connectManually(input)
                        },
                    )
                }
            }
        }
    }

    private fun connectManually(input: String) {
        startActivity(
            Intent(this, QRCodeScanner::class.java)
                .putExtra(QRCodeScanner.EXTRA_CODE, input),
        )
    }

    override fun onResume() {
        super.onResume()
        phoneIp = NetworkUtils.wifiIpv4String(this)
    }

    private fun scanRequested() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openScanner()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openScanner() {
        startActivity(Intent(this, QRCodeScanner::class.java))
    }
}
