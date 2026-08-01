package com.beamng.remotecontrol

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Decodes a QR from a still photo, trying ZBar first and Google ML Kit second.
 *
 * ZBar is the only engine that reads the game's pairing QR (a version-5, level-L
 * code the game renders far too small); ML Kit and zxing can't. So a photo of the
 * in-game QR only decodes through ZBar — which is why the live scanner and this
 * photo path both run it. ML Kit stays as a fallback for clean, ordinary QRs.
 */
object QrImageDecoder {

    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Async decode; [onResult] is invoked on the main thread with the QR text, or null. */
    fun decode(bitmap: Bitmap, onResult: (String?) -> Unit) {
        // ZBar on a background thread (grayscale conversion + scan can be heavy).
        Thread {
            val zbarText = decodeWithZbar(bitmap)
            if (zbarText != null) {
                mainHandler.post { onResult(zbarText) }
            } else {
                mainHandler.post { decodeWithMlKit(bitmap, onResult) }
            }
        }.start()
    }

    /** Converts the bitmap to ZBar's Y800 (luminance) buffer and scans it. */
    private fun decodeWithZbar(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = ByteArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, integer approximation.
            luma[i] = ((77 * r + 150 * g + 29 * b) shr 8).toByte()
        }
        return ZbarDecoder.decode(luma, width, height)
    }

    private fun decodeWithMlKit(bitmap: Bitmap, onResult: (String?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes -> onResult(barcodes.firstOrNull()?.rawValue) }
            .addOnFailureListener { onResult(null) }
    }
}
