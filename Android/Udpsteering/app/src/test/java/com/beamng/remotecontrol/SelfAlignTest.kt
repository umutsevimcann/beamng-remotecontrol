package com.beamng.remotecontrol

import com.beamng.remotecontrol.protocol.SelfAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAlignTest {

    @Test
    fun noSlip_restsAtCentre() {
        assertEquals(0f, SelfAlign.restPosition(null), 0f)
        assertEquals(0f, SelfAlign.restPosition(0f), 0f)
    }

    @Test
    fun wheelPointsWhereTheCarTravels() {
        // Sliding 17.5° to the right of the nose: half of the 35° the front wheels turn.
        assertEquals(0.5f, SelfAlign.restPosition(17.5f), 1e-4f)
        assertEquals(-0.5f, SelfAlign.restPosition(-17.5f), 1e-4f)
    }

    @Test
    fun aDeepSlideStopsAtFullLock() {
        assertEquals(1f, SelfAlign.restPosition(80f), 0f)
        assertEquals(-1f, SelfAlign.restPosition(-80f), 0f)
    }

    @Test
    fun garbageTelemetry_restsAtCentre() {
        assertEquals(0f, SelfAlign.restPosition(Float.NaN), 0f)
        assertEquals(0f, SelfAlign.restPosition(Float.POSITIVE_INFINITY), 0f)
    }

    private fun run(seconds: Float, target: Float, strength: Float, from: SelfAlign.WheelMotion = SelfAlign.WheelMotion(0f, 0f)): SelfAlign.WheelMotion {
        var m = from
        repeat((seconds * 60).toInt()) { m = SelfAlign.step(m, target, strength, 1f / 60f) }
        return m
    }

    @Test
    fun aReleasedWheelHasWeight_itDoesNotJumpToItsRestPosition() {
        // 50 ms after letting go it has barely started to move.
        val m = run(0.05f, target = 100f, strength = 1f)
        assertTrue("moved " + m.angle, m.angle > 0f && m.angle < 10f)
    }

    @Test
    fun itSettlesOnTheRestPosition() {
        val m = run(2.5f, target = 100f, strength = 1f)
        assertEquals(100f, m.angle, 2f)
        assertEquals(0f, m.speed, 5f)
    }

    @Test
    fun theFasterTheCar_theHarderTheWheelIsPulled() {
        val fast = run(0.3f, target = 100f, strength = SelfAlign.strength(30f))
        val slow = run(0.3f, target = 100f, strength = SelfAlign.strength(6f))
        assertTrue(fast.angle > slow.angle * 1.5f)
    }

    @Test
    fun strength_staysWithinBounds_whateverTheTelemetrySays() {
        assertEquals(1f, SelfAlign.strength(500f), 0f)
        assertEquals(SelfAlign.MIN_STRENGTH, SelfAlign.strength(0f), 0f)
        assertEquals(SelfAlign.MIN_STRENGTH, SelfAlign.strength(Float.NaN), 0f)
        assertEquals(SelfAlign.MIN_STRENGTH, SelfAlign.strength(-40f), 0f)
    }

    @Test
    fun theWheelNeverSpinsFasterThanAHandCouldFollow() {
        var m = SelfAlign.WheelMotion(-450f, 0f)
        var top = 0f
        repeat(120) {
            m = SelfAlign.step(m, 450f, 1f, 1f / 60f)
            top = maxOf(top, kotlin.math.abs(m.speed))
        }
        assertTrue("top speed " + top, top <= SelfAlign.MAX_SPEED_DEG_S)
    }

    @Test
    fun aBadFrameTimeChangesNothing_andALongOneStaysStable() {
        val start = SelfAlign.WheelMotion(10f, 20f)
        assertEquals(start, SelfAlign.step(start, 100f, 1f, Float.NaN))
        assertEquals(start, SelfAlign.step(start, 100f, 1f, -1f))
        val after = SelfAlign.step(start, 100f, 1f, 30f) // app was in the background
        assertTrue(after.angle.isFinite() && after.angle in -50f..250f)
    }

    @Test
    fun aStrongerReturnSetting_pullsTheWheelBackFaster() {
        val normal = run(0.2f, target = 100f, strength = SelfAlign.strength(30f, returnPercent = 100))
        val strong = run(0.2f, target = 100f, strength = SelfAlign.strength(30f, returnPercent = 200))
        val weak = run(0.2f, target = 100f, strength = SelfAlign.strength(30f, returnPercent = 20))
        assertTrue(strong.angle > normal.angle * 1.3f)
        assertTrue(weak.angle < normal.angle * 0.5f)
    }
}
