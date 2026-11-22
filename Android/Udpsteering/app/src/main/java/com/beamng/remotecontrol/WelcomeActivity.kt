package com.beamng.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.network.UdpDiscovery.SweepPhase
import com.beamng.remotecontrol.ui.AppLanguage
import com.beamng.remotecontrol.ui.ManualCodeDialog
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.menu.MainMenuScreen
import com.beamng.remotecontrol.ui.menu.SearchUi
import com.beamng.remotecontrol.ui.theme.RemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class WelcomeActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    // Mirrors of the settings the menu shows; refreshed in onResume.
    private var savedPcIp by mutableStateOf<String?>(null)
    private var savedPcName by mutableStateOf<String?>(null)
    private var dashLayout by mutableStateOf(DashLayout.MODERN)
    private var controlType by mutableIntStateOf(SettingsManager.CONTROL_GYROSCOPE)

    // Search state: null = idle.
    private var sweepJob: Job? = null
    private var search by mutableStateOf<SearchUi?>(null)
    /** Bumped per search; late callbacks from an older sweep are ignored. */
    private var searchGeneration = 0

    // The app language this instance was built with (see AppLanguage.apply).
    private var builtLanguage = ""

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
        builtLanguage = AppLanguage.requestedTags()
        // Draw edge to edge on every Android version; the screens pad themselves
        // with safeDrawingPadding (system bars + display cutout).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settings = SettingsManager.getInstance(this)
        setContent {
            RemoteTheme {
                var showManual by remember { mutableStateOf(false) }
                MainMenuScreen(
                    savedPcIp = savedPcIp,
                    savedPcName = savedPcName,
                    layout = dashLayout,
                    controlTypeLabel = stringResource(
                        when (controlType) {
                            SettingsManager.CONTROL_BUTTONS -> R.string.control_buttons
                            SettingsManager.CONTROL_SLIDER -> R.string.control_slider
                            else -> R.string.control_gyro
                        },
                    ),
                    search = search,
                    onConnect = { startSearch(useSavedPc = true) },
                    onNewPc = { startSearch(useSavedPc = false) },
                    onCancel = ::cancelSearch,
                    onScanQr = ::scanRequested,
                    onEnterCode = { showManual = true },
                    onLayout = { layout ->
                        dashLayout = layout
                        settings.dashLayout = layout
                    },
                    onControlType = {
                        // Three options: tapping the row steps to the next one.
                        controlType = when (controlType) {
                            SettingsManager.CONTROL_GYROSCOPE -> SettingsManager.CONTROL_SLIDER
                            SettingsManager.CONTROL_SLIDER -> SettingsManager.CONTROL_BUTTONS
                            else -> SettingsManager.CONTROL_GYROSCOPE
                        }
                        settings.controlType = controlType
                    },
                    onSetupGuide = { startActivity(Intent(this, SetupGuideActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
                if (showManual) {
                    ManualCodeDialog(
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
        // The language was changed in Settings: rebuild instead of trusting the
        // in-place update. No search can be running here (onStop cancels it).
        if (AppLanguage.changedSince(builtLanguage)) {
            recreate()
            return
        }
        savedPcIp = settings.lastIP.takeIf { it.isNotEmpty() }
        savedPcName = settings.lastPcName.takeIf { it.isNotEmpty() }
        dashLayout = settings.dashLayout
        controlType = settings.controlType
    }

    override fun onStop() {
        super.onStop()
        // A search that finishes while we're in the background can't open the drive
        // screen (background activity starts are blocked), so don't leave one running.
        cancelSearch()
    }

    /**
     * Camera-free connect: try codes until the game answers (see UdpDiscovery.sweep).
     * [useSavedPc] = false skips the saved address and searches the whole network.
     */
    private fun startSearch(useSavedPc: Boolean) {
        if (search != null) return
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
        // Literal IP, so no DNS lookup happens here.
        val lastHost = if (useSavedPc) {
            settings.lastIP.takeIf { it.isNotEmpty() }?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
        } else {
            null
        }
        val slow = !NetworkUtils.isWifi5Ghz(this)

        val generation = ++searchGeneration
        search = SearchUi(if (lastHost != null) SweepPhase.SAVED_PC else SweepPhase.FULL_SEARCH, 0f)
        sweepJob = lifecycleScope.launch {
            val app = application as RemoteControlApplication
            // The mod pairs without a code and answers at once: ask it first.
            val mod = UdpDiscovery.findMod(broadcast, lastHost, network)
            if (mod != null) {
                search = null
                app.hostAddress = mod.host
                app.securityCode = null
                app.modInfo = mod.info
                mod.host.hostAddress?.let {
                    settings.lastIP = it
                    savedPcIp = it
                }
                settings.lastPcName = mod.info.pcName
                settings.lastModVersion = mod.info.modVersion
                savedPcName = mod.info.pcName
                startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                return@launch
            }
            app.modInfo = null
            val result = UdpDiscovery.sweep(
                broadcast, lastHost, slow, network,
                onPhase = { phase ->
                    runOnUiThread { if (generation == searchGeneration) search = search?.copy(phase = phase, progress = 0f) }
                },
                onProgress = { tried, total ->
                    runOnUiThread { if (generation == searchGeneration) search = search?.copy(progress = tried.toFloat() / total) }
                },
            )
            search = null
            when (result) {
                is UdpDiscovery.Result.Connected -> {
                    app.hostAddress = result.host
                    app.securityCode = result.code
                    // Next time we sweep this PC by unicast first (fast, no flood).
                    result.host.hostAddress?.let {
                        settings.lastIP = it
                        savedPcIp = it
                    }
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

    private fun cancelSearch() {
        searchGeneration++
        sweepJob?.cancel()
        sweepJob = null
        search = null
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
