package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScanClick(view: View) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("BeamNG", "No Camera Permission")

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAM_PERMISSION_REQUEST
            )
            return
        }
        startActivity(Intent(this, QRCodeScanner::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSettingsClick(view: View) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.textTelemetryHint)?.text =
            "In BeamNG: Options → Other → OutGauge support\n" +
                "Enable it and set:\n" +
                "IP: ${wifiIpAddress()}     Port: 4445"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAM_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(this, QRCodeScanner::class.java))
        }
    }

    private fun wifiIpAddress(): String {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                return String.format(
                    Locale.US, "%d.%d.%d.%d",
                    ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
                )
            }
        } catch (ignored: Exception) {
        }
        return "(connect phone to Wi-Fi)"
    }

    companion object {
        const val CAM_PERMISSION_REQUEST = 100
    }
}
