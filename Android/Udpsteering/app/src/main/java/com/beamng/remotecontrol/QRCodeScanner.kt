package com.beamng.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.beamng.remotecontrol.ui.ScanScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QRCodeScanner : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private var connecting by mutableStateOf(false)
    private var discoveryJob: Job? = null

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            if (result.text == null || connecting) return

            barcodeView.pause()
            handleScanResult(result.text)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        barcodeView = DecoratedBarcodeView(this).apply {
            setStatusText("")
            decodeContinuous(callback)
        }

        setContent {
            NightGarageTheme {
                ScanScreen(
                    barcodeView = barcodeView,
                    connecting = connecting,
                    onCancelConnecting = ::cancelDiscovery,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (connecting) {
            barcodeView.pause()
        } else {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            discoveryJob?.cancel()
            connecting = false
        }
        barcodeView.pause()
    }

    private fun handleScanResult(rawResult: String) {
        val parts = rawResult.split("#")
        if (parts.size != 2) {
            Toast.makeText(this, getString(R.string.toast_invalid_qr), Toast.LENGTH_LONG).show()
            barcodeView.resume()
            return
        }
        val securityCode = parts[1]

        // Validate securityCode: max 64 chars, alphanumeric + underscore + hyphen only
        if (securityCode.length > 64 || !securityCode.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            Toast.makeText(this, getString(R.string.toast_invalid_qr_format), Toast.LENGTH_LONG).show()
            barcodeView.resume()
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
                when (val result = UdpDiscovery.discover(broadcastAddress, ip, securityCode)) {
                    is UdpDiscovery.Result.Connected -> {
                        connecting = false
                        val app = application as RemoteControlApplication
                        app.hostAddress = result.host
                        app.securityCode = securityCode
                        startActivity(Intent(this@QRCodeScanner, MainActivity::class.java))
                    }
                    is UdpDiscovery.Result.Failed -> onError(result.message)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_wifi_required), Toast.LENGTH_LONG).show()
            barcodeView.resume()
        }
    }

    private fun cancelDiscovery() {
        discoveryJob?.cancel()
        onError(null)
    }

    private fun onError(message: String?) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        connecting = false
        barcodeView.resume()
    }
}
