package com.beamng.remotecontrol

import com.yanzhenjie.zbar.Config
import com.yanzhenjie.zbar.Image
import com.yanzhenjie.zbar.ImageScanner
import com.yanzhenjie.zbar.Symbol

/**
 * QR decoding via native ZBar.
 *
 * ZBar is the only engine that reliably reads BeamNG.drive's pairing QR: the game
 * renders a version-5 level-L code at ~180px (≈4.9px per module) with no quiet
 * zone, which sits right at the edge of decodability. zxing, Google ML Kit and
 * BoofCV all fail on it; ZBar (same lineage as the iOS decoder) reads it. We run
 * this next to ML Kit on every camera frame so whichever one succeeds wins.
 *
 * Feeds on the camera's Y (luminance) plane directly — ZBar's native "Y800"
 * grayscale format — so there is no colour conversion.
 */
object ZbarDecoder {

    private val scanner: ImageScanner by lazy {
        ImageScanner().apply {
            // QR only, at full sampling density (this QR needs every row scanned).
            setConfig(Symbol.NONE, Config.ENABLE, 0)
            setConfig(Symbol.QRCODE, Config.ENABLE, 1)
            setConfig(Symbol.NONE, Config.X_DENSITY, 1)
            setConfig(Symbol.NONE, Config.Y_DENSITY, 1)
        }
    }

    /**
     * Decodes a tightly packed Y800 (grayscale) buffer of [width]x[height].
     * Returns the QR text, or null if nothing was found. Synchronised because a
     * single [ImageScanner] instance is not re-entrant.
     */
    @Synchronized
    fun decode(y800: ByteArray, width: Int, height: Int): String? {
        val image = Image(width, height, "Y800")
        image.setData(y800)
        if (scanner.scanImage(image) == 0) return null
        for (symbol in scanner.results) {
            val data = symbol.data
            if (!data.isNullOrEmpty()) return data
        }
        return null
    }
}
