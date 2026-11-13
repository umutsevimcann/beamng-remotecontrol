package com.beamng.remotecontrol.ui.dash

import kotlin.math.ceil

/** Tachometer range in RPM. */
data class RpmScale(val max: Float, val redline: Float)

/**
 * OutGauge reports no redline, so the scale starts at a typical petrol engine
 * (8000 / 6500) and grows when the session shows a higher RPM. It never shrinks:
 * a scale that jumped back at idle would make every gauge twitch.
 *
 * Known limit: low-revving engines (diesel trucks) never reach the default
 * redline, so shift lights come on late or not at all.
 */
class RpmScaleTracker {
    var scale = RpmScale(DEFAULT_MAX, DEFAULT_REDLINE)
        private set

    fun observe(rpm: Float): RpmScale {
        // Not finite, negative or absurd: a corrupt packet must not rescale the dash.
        if (!rpm.isFinite() || rpm <= scale.max || rpm > SANITY_LIMIT) return scale
        val max = ceil(rpm / STEP) * STEP
        scale = RpmScale(max, max * REDLINE_FRACTION)
        return scale
    }

    private companion object {
        const val DEFAULT_MAX = 8000f
        const val DEFAULT_REDLINE = 6500f
        const val STEP = 500f
        const val REDLINE_FRACTION = 0.81f
        const val SANITY_LIMIT = 25000f
    }
}
