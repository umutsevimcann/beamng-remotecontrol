package com.beamng.remotecontrol

import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Locale

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

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        try {
            @Suppress("DEPRECATION")
            val ipAddress = wifiManager.connectionInfo.ipAddress
            val ip = String.format(
                Locale.US, "%d.%d.%d.%d",
                ipAddress and 0xff, ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff
            )

            (application as RemoteControlApplication).ip = ip

            val broadcastAddress = getBroadcastAddress(ipAddress())!!
            Log.i("Broadcast Address", broadcastAddress.hostAddress ?: "?")

            exploreSenderFragment.execute(broadcastAddress, this, ip, securityCode)
            progressDialogFragment.show(supportFragmentManager, "progressDialog")
        } catch (e: Exception) {
            Toast.makeText(this, "You must be connected to the same WiFi as your PC", Toast.LENGTH_LONG).show()
            barcodeView.resume()
        }
    }

    private fun getBroadcastAddress(inetAddr: InetAddress?): InetAddress? {
        try {
            val temp = NetworkInterface.getByInetAddress(inetAddr) ?: return null

            val addresses: List<InterfaceAddress> = temp.interfaceAddresses
            for (interfaceAddress in addresses) {
                if (interfaceAddress.broadcast != null) {
                    return interfaceAddress.broadcast
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return null
    }

    private fun ipAddress(): InetAddress? {
        try {
            for (singleInterface in NetworkInterface.getNetworkInterfaces()) {
                for (inetAddress in singleInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress &&
                        (singleInterface.displayName.contains("wlan0") ||
                            singleInterface.displayName.contains("eth0") ||
                            singleInterface.displayName.contains("ap0"))
                    ) {
                        return inetAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
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
