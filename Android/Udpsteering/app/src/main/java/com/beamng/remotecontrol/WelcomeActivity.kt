package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.beamng.remotecontrol.ui.AutoConnectDialog
import com.beamng.remotecontrol.ui.WelcomeScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    private var phoneIp by mutableStateOf<String?>(null)

    // Auto-connect sweep state: null job = idle; progress is 0f..1f for the dialog.
    private var sweepJob: Job? = null
    private var sweepProgress by mutableFloatStateOf(0f)
    private var sweeping by mutableStateOf(false)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openScanner()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) decodeFromPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NightGarageTheme {
                var showManual by androidx.compose.runtime.remember { mutableStateOf(false) }
                WelcomeScreen(
                    phoneIp = phoneIp,
                    onAutoConnectClick = ::startAutoConnect,
                    onScanClick = ::scanRequested,
                    onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onGuideClick = { startActivity(Intent(this, SetupGuideActivity::class.java)) },
                    onManualClick = { showManual = true },
                )
                if (sweeping) {
                    AutoConnectDialog(progress = sweepProgress, onCancel = ::cancelAutoConnect)
                }
                if (showManual) {
                    com.beamng.remotecontrol.ui.ManualCodeDialog(
                        onDismiss = { showManual = false },
                        onConnect = { input ->
                            showManual = false
                            connectManually(input)
                        },
                        onPickPhoto = {
                            showManual = false
                            pickImage.launch("image/*")
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

    /** Decode the game QR from a photo the user took (works when live scan can't). */
    private fun decodeFromPhoto(uri: Uri) {
        val bitmap = try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.toast_no_qr_in_photo), Toast.LENGTH_LONG).show()
            return
        }
        QrImageDecoder.decode(bitmap) { text ->
            if (text != null) {
                connectManually(text)
            } else {
                Toast.makeText(this, getString(R.string.toast_no_qr_in_photo), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phoneIp = NetworkUtils.wifiIpv4String(this)
    }

    /**
     * Camera-free connect: sweep the game's 5-digit code space over Wi-Fi and let
     * its reply reveal the code + host. Works on any phone regardless of camera.
     */
    private fun startAutoConnect() {
        if (sweeping) return
        val ip = NetworkUtils.wifiIpv4String(this)
        val broadcast = try {
            NetworkUtils.broadcastAddress(NetworkUtils.localInetAddress())
        } catch (e: Exception) {
            null
        }
        if (ip == null || broadcast == null) {
            Toast.makeText(this, getString(R.string.toast_wifi_required), Toast.LENGTH_LONG).show()
            return
        }
        (application as RemoteControlApplication).ip = ip

        sweepProgress = 0f
        sweeping = true
        sweepJob = lifecycleScope.launch {
            val result = UdpDiscovery.sweep(broadcast, ip) { tried, total ->
                runOnUiThread { sweepProgress = tried.toFloat() / total }
            }
            sweeping = false
            when (result) {
                is UdpDiscovery.Result.Connected -> {
                    val app = application as RemoteControlApplication
                    app.hostAddress = result.host
                    app.securityCode = result.code
                    startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                }
                is UdpDiscovery.Result.Failed -> {
                    Toast.makeText(
                        this@WelcomeActivity,
                        getString(R.string.toast_auto_connect_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun cancelAutoConnect() {
        sweepJob?.cancel()
        sweepJob = null
        sweeping = false
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
