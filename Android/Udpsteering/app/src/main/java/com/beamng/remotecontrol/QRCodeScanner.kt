package com.beamng.remotecontrol

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.beamng.remotecontrol.network.NetworkUtils
import com.beamng.remotecontrol.network.UdpDiscovery
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Live QR scanner (CameraX preview + Google ML Kit decoder) plus a manual-code
 * entry point via [EXTRA_CODE].
 *
 * Uses ML Kit rather than zxing on purpose: the game's pairing QR (version 4,
 * error-correction level L, tight quiet zone) is one zxing cannot decode, while
 * ML Kit reads it fine — same class of decoder as iOS and zbar. Either the scan
 * or the passed-in code drives the same UDP discovery handshake.
 */
class QRCodeScanner : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var connectingOverlay: LinearLayout
    private lateinit var connectingText: TextView

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var handled = false
    private var discoveryJob: Job? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        val manualCode = intent.getStringExtra(EXTRA_CODE)
        if (manualCode != null) {
            // No camera needed — connect straight from the entered code.
            setContentView(R.layout.activity_scan)
            previewView = findViewById(R.id.previewView)
            connectingOverlay = findViewById(R.id.connectingOverlay)
            connectingText = findViewById(R.id.connectingText)
            connectWithCode(manualCode)
            return
        }

        setContentView(R.layout.activity_scan)
        previewView = findViewById(R.id.previewView)
        connectingOverlay = findViewById(R.id.connectingOverlay)
        connectingText = findViewById(R.id.connectingText)
        findViewById<TextView>(R.id.scanHint).text = getString(R.string.scan_hint)

        startCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                Toast.makeText(this, getString(R.string.toast_wifi_required), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || handled) {
            imageProxy.close()
            return
        }

        // 1) ZBar on the raw luminance plane — the only decoder that reads the
        //    game's marginal QR. Runs synchronously on this analysis thread.
        val luminance = extractLuminance(imageProxy)
        if (luminance != null && !handled) {
            val zbarText = ZbarDecoder.decode(luminance, imageProxy.width, imageProxy.height)
            if (zbarText != null && !handled) {
                handled = true
                runOnUiThread { handleScanResult(zbarText) }
                imageProxy.close()
                return
            }
        }

        // 2) ML Kit as a second opinion — strong on clean/large QRs.
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val text = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                if (text != null && !handled) {
                    handled = true
                    handleScanResult(text)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Copies the camera frame's Y (luminance) plane into a tightly packed Y800 buffer. */
    private fun extractLuminance(imageProxy: ImageProxy): ByteArray? = try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height
        val out = ByteArray(width * height)
        if (rowStride == width && pixelStride == 1) {
            buffer.get(out, 0, out.size)
        } else {
            val rowBytes = ByteArray(rowStride)
            var offset = 0
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(rowBytes, 0, minOf(rowStride, buffer.remaining()))
                if (pixelStride == 1) {
                    System.arraycopy(rowBytes, 0, out, offset, width)
                } else {
                    var col = 0
                    var i = 0
                    while (col < width) {
                        out[offset + col] = rowBytes[i]
                        col++
                        i += pixelStride
                    }
                }
                offset += width
            }
        }
        out
    } catch (e: Exception) {
        null
    }

    private fun handleScanResult(rawResult: String) {
        val code = extractCode(rawResult)
        if (code == null) {
            Toast.makeText(this, getString(R.string.toast_invalid_qr), Toast.LENGTH_LONG).show()
            handled = false // let the user keep scanning
            return
        }
        connectWithCode(code)
    }

    @SuppressLint("SetTextI18n")
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

            connectingText.text = getString(R.string.connecting_dialog_title)
            connectingOverlay.visibility = View.VISIBLE

            val network = NetworkUtils.wifiNetwork(this)
            discoveryJob = lifecycleScope.launch {
                when (val result = UdpDiscovery.discover(broadcastAddress, ip, code, network)) {
                    is UdpDiscovery.Result.Connected -> {
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
        analysisExecutor.shutdown()
        barcodeScanner.close()
    }

    companion object {
        private const val TAG = "QRCodeScanner"
        const val EXTRA_CODE = "manual_code"

        /**
         * Pulls the security code out of the game's QR payload
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
