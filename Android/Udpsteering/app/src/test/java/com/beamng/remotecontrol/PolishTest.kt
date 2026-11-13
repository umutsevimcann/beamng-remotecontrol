package com.beamng.remotecontrol

import android.view.Surface
import com.beamng.remotecontrol.input.tiltSide
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PolishTest {

    private fun outgauge(block: ByteBuffer.() -> Unit): Receivepacket {
        val buf = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
        buf.block()
        return Receivepacket(buf.array(), 96)
    }

    @Test
    fun telemetry_absurdFiniteValues_areClampedToPlausibleRanges() {
        // Finite but absurd: 3.6f * 3e38 overflowed to Infinity and the dash showed 2147483647.
        val p = outgauge {
            putFloat(12, 3.0e38f)  // speed m/s
            putFloat(16, 9.0e9f)   // rpm
            putFloat(20, -400f)    // turbo bar
            putFloat(24, 5.0e6f)   // engine temp
            putFloat(28, 7f)       // fuel 0..1
            putFloat(48, -3f)      // throttle
            putFloat(52, 12f)      // brake
            putFloat(56, 1.0e20f)  // clutch
        }
        assertTrue(p.isValid)
        assertEquals(150f, p.speed)       // 540 km/h ceiling
        assertEquals(25000f, p.rpm)
        assertEquals(-1f, p.turbo)
        assertEquals(300f, p.engineTemp)
        assertEquals(1f, p.fuel)
        assertEquals(0f, p.throttle)
        assertEquals(1f, p.brake)
        assertEquals(1f, p.clutch)
    }

    @Test
    fun telemetry_negativeSpeedAndRpm_readAsZero() {
        val p = outgauge {
            putFloat(12, -20f)
            putFloat(16, -900f)
        }
        assertEquals(0f, p.speed)
        assertEquals(0f, p.rpm)
    }

    @Test
    fun telemetry_ordinaryValues_passThroughUntouched() {
        val p = outgauge {
            putFloat(12, 27.5f); putFloat(16, 3500f); putFloat(20, 0.8f); putFloat(24, 91f); putFloat(28, 0.71f)
        }
        assertEquals(27.5f, p.speed); assertEquals(3500f, p.rpm); assertEquals(0.8f, p.turbo)
        assertEquals(91f, p.engineTemp); assertEquals(0.71f, p.fuel)
    }

    @Test
    fun tiltSide_phoneLandscapes_matchTheDeviceTestedBehaviour() {
        // Device-tested on a phone: ROTATION_90 uses +upY, ROTATION_270 uses -upY.
        assertEquals(0.4f, tiltSide(Surface.ROTATION_90, upX = 0.9f, upY = 0.4f))
        assertEquals(-0.4f, tiltSide(Surface.ROTATION_270, upX = 0.9f, upY = 0.4f))
    }

    @Test
    fun tiltSide_naturalLandscapeTablets_useTheOtherAxis() {
        // A tablet whose natural orientation is landscape reports ROTATION_0/180 on
        // a sensorLandscape screen; its sideways axis is device X, not Y.
        assertEquals(-0.4f, tiltSide(Surface.ROTATION_0, upX = 0.4f, upY = 0.9f))
        assertEquals(0.4f, tiltSide(Surface.ROTATION_180, upX = 0.4f, upY = 0.9f))
    }

    @Test
    fun themeMode_unknownKey_fallsBackToDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("sepia"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
    }
}
