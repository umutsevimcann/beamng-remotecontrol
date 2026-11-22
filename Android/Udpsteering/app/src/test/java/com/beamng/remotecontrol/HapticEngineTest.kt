package com.beamng.remotecontrol

import com.beamng.remotecontrol.feedback.HapticEngine
import com.beamng.remotecontrol.feedback.HapticEngine.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The g values here were measured in BeamNG.drive v0.40 on the MotionSim stream the
 * phone receives: standing 0.1 g, full throttle 2.1 g, hard braking 3.0 g, hitting a
 * wall 7.4 g and 15 g; landings from 0.5 / 1.5 / 4 m were 5.3 / 12.8 / 20.5 g vertical.
 */
class HapticEngineTest {

    private val g = 9.81f
    private val all = Options(gear = true, impact = true, landing = true, abs = true, traction = true, redline = true)

    private fun telemetry(
        engine: HapticEngine, now: Long, gear: String = "3", kmh: Float = 60f, abs: Boolean = false,
        traction: Boolean = false, rpm: Float = 3000f, redline: Float = 6500f, options: Options = all,
    ) = engine.onTelemetry(now, gear, kmh, abs, traction, rpm, redline, options)

    @Test
    fun gearChange_ticksOnce_butNotOnTheFirstPacket() {
        val e = HapticEngine()
        assertTrue(telemetry(e, 0, gear = "2").isEmpty())
        assertEquals(1, telemetry(e, 20, gear = "3").size)
        assertTrue(telemetry(e, 40, gear = "3").isEmpty())
    }

    @Test
    fun drivingAndBraking_doNotCountAsCrashes() {
        val e = HapticEngine()
        assertTrue(e.onMotion(0, 2.1f * g, 0f, 0f, all).isEmpty())
        assertTrue(e.onMotion(50, 0f, -3.0f * g, 0f, all).isEmpty())
    }

    @Test
    fun hittingAWall_vibrates_harderForABiggerHit() {
        val light = HapticEngine().onMotion(0, 7.4f * g, 0f, 0f, all).single()
        val heavy = HapticEngine().onMotion(0, 0f, 15f * g, 0f, all).single()
        assertTrue(heavy.ms > light.ms)
        assertTrue(heavy.amplitude > light.amplitude)
    }

    @Test
    fun oneCrash_isOneVibration_notOnePerPacket() {
        val e = HapticEngine()
        assertEquals(1, e.onMotion(0, 9f * g, 0f, 0f, all).size)
        assertTrue(e.onMotion(60, 8f * g, 0f, 0f, all).isEmpty())
        assertEquals(1, e.onMotion(900, 9f * g, 0f, 0f, all).size)
    }

    @Test
    fun aHardLanding_isFelt_andAKerbIsNot() {
        assertEquals(1, HapticEngine().onMotion(0, 0f, 0f, 5.3f * g, all).size)
        assertTrue(HapticEngine().onMotion(0, 0f, 0f, 2f * g, all).isEmpty())
    }

    @Test
    fun eachEvent_canBeSwitchedOff() {
        val none = Options(gear = false, impact = false, landing = false, abs = false, traction = false, redline = false)
        val e = HapticEngine()
        telemetry(e, 0, gear = "2", options = none)
        assertTrue(telemetry(e, 20, gear = "3", abs = true, traction = true, rpm = 7000f, options = none).isEmpty())
        assertTrue(e.onMotion(40, 15f * g, 0f, 20f * g, none).isEmpty())
    }

    @Test
    fun garbageAcceleration_isIgnored() {
        assertTrue(HapticEngine().onMotion(0, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, all).isEmpty())
    }

    @Test
    fun withoutTheMotionStream_aSuddenWheelSpeedDropStillCounts() {
        val e = HapticEngine()
        telemetry(e, 0, kmh = 80f)
        telemetry(e, 100, kmh = 70f)
        assertEquals(1, telemetry(e, 250, kmh = 30f).size)
    }

    @Test
    fun withTheMotionStream_wheelSpeedIsNotUsed() {
        // Wheels locking under braking drop the wheel speed; the body did not crash.
        val e = HapticEngine()
        e.onMotion(0, 0f, 2f * g, 0f, all)
        telemetry(e, 10, kmh = 80f)
        assertTrue(telemetry(e, 200, kmh = 20f).isEmpty())
    }

    @Test
    fun theRedline_buzzes_whileTheEngineIsThere() {
        val e = HapticEngine()
        telemetry(e, 0, rpm = 3000f)
        assertEquals(1, telemetry(e, 20, rpm = 6600f).size)
        assertTrue(telemetry(e, 40, rpm = 6600f).isEmpty()) // not every packet
        assertEquals(1, telemetry(e, 200, rpm = 6600f).size)
        assertTrue(telemetry(e, 400, rpm = 5000f).isEmpty())
    }

    @Test
    fun abs_and_traction_pulse_onlyWhileMoving() {
        val e = HapticEngine()
        telemetry(e, 0)
        assertEquals(1, telemetry(e, 20, abs = true).size)
        assertEquals(1, telemetry(e, 800, traction = true).size)
        // Standing still (a separate car, so no sudden speed drop reads as a crash).
        val parked = HapticEngine()
        telemetry(parked, 0, kmh = 2f)
        assertTrue(telemetry(parked, 20, abs = true, traction = true, kmh = 2f).isEmpty())
    }
}
