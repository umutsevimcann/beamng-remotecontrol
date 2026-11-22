package com.beamng.remotecontrol

import com.beamng.remotecontrol.input.GyroLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device axes (Android): x across the short edge, y along the long edge, z out of the
 * screen. Gravity as the gravity sensor reports it points up, away from the earth.
 */
class GyroLookTest {

    private val upright = floatArrayOf(0f, 0f, 9.81f) // phone lying flat, screen up
    private val rot90 = 1  // Surface.ROTATION_90
    private val rot270 = 3 // Surface.ROTATION_270

    @Test
    fun turningLeft_looksLeft_howeverThePhoneIsHeld() {
        // Counter-clockwise about "up" (z when flat) for 0.1 s at 1 rad/s ≈ 5.7°.
        val flat = GyroLook.delta(floatArrayOf(0f, 0f, 1f), upright, rot90, 0.1f, 1f)
        assertTrue(flat.first < 0f)
        // Held upright in landscape, "up" is along the device x axis.
        val held = GyroLook.delta(floatArrayOf(1f, 0f, 0f), floatArrayOf(9.81f, 0f, 0f), rot90, 0.1f, 1f)
        assertEquals(flat.first, held.first, 1e-3f)
    }

    @Test
    fun aBiggerTurn_looksFurther_andTheSpeedSettingScalesIt() {
        val small = GyroLook.delta(floatArrayOf(0f, 0f, 0.5f), upright, rot90, 0.1f, 1f).first
        val big = GyroLook.delta(floatArrayOf(0f, 0f, 1f), upright, rot90, 0.1f, 1f).first
        val fast = GyroLook.delta(floatArrayOf(0f, 0f, 1f), upright, rot90, 0.1f, 2f).first
        assertEquals(big, small * 2f, 1e-3f)
        assertEquals(fast, big * 2f, 1e-3f)
    }

    @Test
    fun tiltingTheTopTowardsYou_looksUp_inBothLandscapeRotations() {
        // Rotation 90: the screen's horizontal axis is device +y; 270: device -y.
        val r90 = GyroLook.delta(floatArrayOf(0f, 1f, 0f), upright, rot90, 0.1f, 1f)
        val r270 = GyroLook.delta(floatArrayOf(0f, -1f, 0f), upright, rot270, 0.1f, 1f)
        assertTrue("look up is negative y, like dragging up", r90.second < 0f)
        assertEquals(r90.second, r270.second, 1e-3f)
    }

    @Test
    fun noGravityYet_orGarbage_looksNowhere() {
        assertEquals(0f to 0f, GyroLook.delta(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, 0f), rot90, 0.1f, 1f))
        assertEquals(0f to 0f, GyroLook.delta(floatArrayOf(Float.NaN, 0f, 1f), upright, rot90, 0.1f, 1f))
        assertEquals(0f to 0f, GyroLook.delta(floatArrayOf(0f, 0f, 1f), upright, rot90, -1f, 1f))
    }
}
