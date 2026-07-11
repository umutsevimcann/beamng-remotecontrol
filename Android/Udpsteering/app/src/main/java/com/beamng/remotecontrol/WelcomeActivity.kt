package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beamng.remotecontrol.network.NetworkUtils

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
        val ip = NetworkUtils.wifiIpv4String(this) ?: "(connect phone to Wi-Fi)"
        findViewById<TextView>(R.id.textTelemetryHint)?.text =
            "In BeamNG: Options → Other → OutGauge support\n" +
                "Enable it and set:\n" +
                "IP: $ip     Port: 4445"
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

    companion object {
        const val CAM_PERMISSION_REQUEST = 100
    }
}
