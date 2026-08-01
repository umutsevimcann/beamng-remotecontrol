package com.beamng.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.beamng.remotecontrol.ui.cockpitBackground
import com.beamng.remotecontrol.ui.theme.NightGarage
import com.beamng.remotecontrol.ui.theme.NightGarageTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Connects to the game. Two entry paths:
 *  - camera scan of the game's QR (zxing CaptureActivity via ScanContract), or
 *  - a manual code passed in via [EXTRA_CODE] (the Welcome screen's "enter code"
 *    fallback), for devices where the in-app camera is blocked (e.g. some MIUI
 *    builds silently deny the camera to sideloaded apps).
 * Either way the security code drives the same UDP discovery handshake.
 */
class QRCodeScanner : AppCompatActivity() {

    private var connecting by mutableStateOf(false)
    private var discoveryJob: Job? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) finish() else handleScanResult(contents)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        setContent {
            NightGarageTheme {
                if (connecting) ConnectingScreen()
            }
        }

        val manualCode = intent.getStringExtra(EXTRA_CODE)
        when {
            manualCode != null -> connectWithCode(manualCode)
            state == null -> launchScanner()
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_hint))
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    /** Extract the security code from a raw scan (Play Store URL with #code) or manual input. */
    private fun handleScanResult(rawResult: String) {
        val code = extractCode(rawResult)
        if (code == null) {
            Toast.makeText(this, getString(R.string.toast_invalid_qr), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        connectWithCode(code)
    }

    private fun connectWithCode(securityCode: String) {
        val code = extractCode(securityCode)
        if (code == null) {
            Toast.makeText(this, getString(R.string.toast_invalid_qr_format), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            val ip = NetworkUtils.wifiIpv4String(this)
                ?: throw IllegalStateException("Wi-Fi is down")

            (application as RemoteControlApplication).ip = ip

            val broadcastAddress = NetworkUtils.broadcastAddress(NetworkUtils.localInetAddress())!!
            Log.i("Broadcast Address", broadcastAddress.hostAddress ?: "?")

            connecting = true
            discoveryJob = lifecycleScope.launch {
                when (val result = UdpDiscovery.discover(broadcastAddress, ip, code)) {
                    is UdpDiscovery.Result.Connected -> {
                        connecting = false
                        val app = application as RemoteControlApplication
                        app.hostAddress = result.host
                        app.securityCode = code
                        startActivity(Intent(this@QRCodeScanner, MainActivity::class.java))
                        finish()
                    }
                    is UdpDiscovery.Result.Failed -> {
                        Toast.makeText(this@QRCodeScanner, result.message, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_wifi_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryJob?.cancel()
    }

    companion object {
        const val EXTRA_CODE = "manual_code"

        /**
         * Pulls the security code out of either the game's QR payload
         * (https://play.google.com/...#12345), a pasted URL, or a bare code.
         * Returns null if nothing valid (<=64 chars, [A-Za-z0-9_-]) is found.
         */
        fun extractCode(raw: String): String? {
            val candidate = raw.substringAfterLast('#').trim()
            return candidate.takeIf {
                it.isNotEmpty() && it.length <= 64 && it.matches(Regex("^[a-zA-Z0-9_\\-]+$"))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConnectingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = NightGarage.Amber)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.connecting_dialog_title),
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.Text,
        )
    }
}
