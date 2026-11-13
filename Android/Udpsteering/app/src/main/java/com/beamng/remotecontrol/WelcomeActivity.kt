package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.AutoConnectDialog
import com.beamng.remotecontrol.ui.WelcomeScreen
import com.beamng.remotecontrol.ui.theme.NightGarageTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

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
        lifecycleScope.launch {
            // Off the main thread: full-size camera photos (12–108 MP) take
            // hundreds of ms to decode and can exhaust the heap.
            val bitmap = withContext(Dispatchers.IO) { loadPhoto(uri) }
            if (bitmap == null) {
                Toast.makeText(this@WelcomeActivity, getString(R.string.toast_no_qr_in_photo), Toast.LENGTH_LONG).show()
                return@launch
            }
            QrImageDecoder.decode(bitmap) { text ->
                if (text != null) {
                    connectManually(text)
                } else {
                    Toast.makeText(this@WelcomeActivity, getString(R.string.toast_no_qr_in_photo), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Decodes [uri] with power-of-two downsampling so the long edge stays ≤
     * [MAX_PHOTO_EDGE_PX]. Deliberately generous: the game draws its QR tiny, so
     * it often covers few pixels of the photo and aggressive downsampling would
     * make it unreadable. Retries smaller if the heap still runs out.
     */
    private fun loadPhoto(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return null

            var sample = 1
            while (longEdge / sample > MAX_PHOTO_EDGE_PX) sample *= 2

            while (sample <= 16) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    return contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                } catch (e: OutOfMemoryError) {
                    sample *= 2
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        phoneIp = NetworkUtils.phoneIpv4String(this)
    }

    /**
     * Camera-free connect: sweep the game's 5-digit code space over Wi-Fi and let
     * its reply reveal the code + host. Works on any phone regardless of camera.
     */
    private fun startAutoConnect() {
        if (sweeping) return
        // Only the broadcast address is required. The Wi-Fi client IP is merely
        // preferred: it's absent when the phone is the hotspot the PC joined, and
        // aborting on it broke hotspot play in v3.0.0–3.0.2.
        val local = NetworkUtils.localInetAddress(NetworkUtils.wifiIpv4String(this))
        val broadcast = try {
            NetworkUtils.broadcastAddress(local)
        } catch (e: Exception) {
            null
        }
        if (broadcast == null) {
            Toast.makeText(this, getString(R.string.toast_wifi_required), Toast.LENGTH_LONG).show()
            return
        }
        // Null on hotspot (no Wi-Fi client network) → sockets stay unbound and
        // route by subnet, which is the hotspot interface.
        val network = NetworkUtils.wifiNetwork(this)

        sweepProgress = 0f
        sweeping = true
        sweepJob = lifecycleScope.launch {
            val settings = SettingsManager.getInstance(this@WelcomeActivity)
            // Literal IP, so no DNS lookup happens here.
            val lastHost = settings.lastIP.takeIf { it.isNotEmpty() }
                ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            val slow = !NetworkUtils.isWifi5Ghz(this@WelcomeActivity)
            val result = UdpDiscovery.sweep(broadcast, lastHost, slow, network) { tried, total ->
                runOnUiThread { sweepProgress = tried.toFloat() / total }
            }
            sweeping = false
            when (result) {
                is UdpDiscovery.Result.Connected -> {
                    val app = application as RemoteControlApplication
                    app.hostAddress = result.host
                    app.securityCode = result.code
                    // Next time we sweep this PC by unicast first (fast, no flood).
                    result.host.hostAddress?.let { settings.lastIP = it }
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

    private companion object {
        const val MAX_PHOTO_EDGE_PX = 4096
    }
}
