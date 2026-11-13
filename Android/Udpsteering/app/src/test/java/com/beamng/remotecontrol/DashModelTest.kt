package com.beamng.remotecontrol

import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.RpmScale
import com.beamng.remotecontrol.ui.dash.RpmScaleTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class DashModelTest {

    @Test
    fun unknownLayoutKeyFallsBackToModern() {
        assertEquals(DashLayout.MODERN, DashLayout.fromKey(null))
        assertEquals(DashLayout.MODERN, DashLayout.fromKey("neon-2031"))
        assertEquals(DashLayout.RACE, DashLayout.fromKey("race"))
    }

    @Test
    fun rpmScaleStartsAt8000AndGrowsInSteps() {
        val t = RpmScaleTracker()
        assertEquals(RpmScale(8000f, 6500f), t.scale)
        assertEquals(RpmScale(8000f, 6500f), t.observe(7200f))
        val grown = t.observe(9200f)
        assertEquals(9500f, grown.max, 0f) // rounded up to the next 500
        assertEquals(9500f * 0.81f, grown.redline, 1f)
        assertEquals(9500f, t.observe(3000f).max, 0f) // never shrinks within a session
    }

    @Test
    fun rpmScaleIgnoresGarbage() {
        val t = RpmScaleTracker()
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, -500f, 1.0e9f)) {
            assertEquals(RpmScale(8000f, 6500f), t.observe(bad))
        }
    }
}
