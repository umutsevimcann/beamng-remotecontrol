package com.beamng.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QRCodeScanner : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var progressDialogFragment: ProgressDialogFragment
    private var discoveryJob: Job? = null

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            if (result.text == null) return

            barcodeView.pause()
            handleScanResult(result.text)
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_qr_scanner)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.decodeContinuous(callback)

        progressDialogFragment =
            (supportFragmentManager.findFragmentByTag("progressDialog") as? ProgressDialogFragment)
                ?: ProgressDialogFragment()
        progressDialogFragment.onCancelAction = { cancelDiscovery() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()

        if (discoveryJob?.isActive != true) {
            if (progressDialogFragment.isShowing) {
                progressDialogFragment.dismiss()
            }
        } else {
            barcodeView.pause()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            discoveryJob?.cancel()
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

            progressDialogFragment.show(supportFragmentManager, "progressDialog")
            discoveryJob = lifecycleScope.launch {
                when (val result = UdpDiscovery.discover(broadcastAddress, ip, securityCode)) {
                    is UdpDiscovery.Result.Connected -> {
                        (application as RemoteControlApplication).hostAddress = result.host
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

    fun onError(message: String?) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        if (progressDialogFragment.isShowing) {
            progressDialogFragment.dismiss()
        }
        barcodeView.resume()
    }
}
