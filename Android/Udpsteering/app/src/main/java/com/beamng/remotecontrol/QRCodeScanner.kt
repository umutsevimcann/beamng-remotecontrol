package com.beamng.remotecontrol

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpExploreSenderFragment
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class QRCodeScanner : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var exploreSenderFragment: UdpExploreSenderFragment
    private lateinit var progressDialogFragment: ProgressDialogFragment

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

        val fm = supportFragmentManager
        exploreSenderFragment =
            (fm.findFragmentByTag("exploreSender") as? UdpExploreSenderFragment)
                ?: UdpExploreSenderFragment().also {
                    fm.beginTransaction().add(it, "exploreSender").commit()
                }

        progressDialogFragment =
            (fm.findFragmentByTag("progressDialog") as? ProgressDialogFragment)
                ?: ProgressDialogFragment()
        progressDialogFragment.setListener(exploreSenderFragment)
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

        if (!exploreSenderFragment.isRunning) {
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
            exploreSenderFragment.cancelTask()
        }
        barcodeView.pause()
    }

    private fun handleScanResult(rawResult: String) {
        val parts = rawResult.split("#")
        if (parts.size != 2) {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_LONG).show()
            barcodeView.resume()
            return
        }
        val securityCode = parts[1]

        // Validate securityCode: max 64 chars, alphanumeric + underscore + hyphen only
        if (securityCode.length > 64 || !securityCode.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
            barcodeView.resume()
            return
        }

        try {
            val ip = NetworkUtils.wifiIpv4String(this)
                ?: throw IllegalStateException("Wi-Fi is down")

            (application as RemoteControlApplication).ip = ip

            val broadcastAddress = NetworkUtils.broadcastAddress(NetworkUtils.localInetAddress())!!
            Log.i("Broadcast Address", broadcastAddress.hostAddress ?: "?")

            exploreSenderFragment.execute(broadcastAddress, this, ip, securityCode)
            progressDialogFragment.show(supportFragmentManager, "progressDialog")
        } catch (e: Exception) {
            Toast.makeText(this, "You must be connected to the same WiFi as your PC", Toast.LENGTH_LONG).show()
            barcodeView.resume()
        }
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
