package com.beamng.remotecontrol

import com.beamng.remotecontrol.protocol.SteeringCurve
import org.junit.Assert.assertEquals
import org.junit.Test

class SteeringCurveTest {

    @Test
    fun halfLockIn_halfLockOutOfTheGame() {
        // The game applies input^2.5 (measured on v0.40: 0.5 -> 0.1768, 0.8 -> 0.5724).
        assertEquals(0.7579f, SteeringCurve.compensate(0.5f), 0.0005f)
    }

    @Test
    fun cancelsTheGameCurveAcrossTheRange() {
        for (i in -20..20) {
            val x = i / 20f
            val sent = SteeringCurve.compensate(x)
            val game = Math.signum(sent) * Math.pow(Math.abs(sent).toDouble(), 2.5).toFloat()
            assertEquals(x, game, 0.001f)
        }
    }

    @Test
    fun clampsAndKeepsSign() {
        assertEquals(1f, SteeringCurve.compensate(1.7f), 0f)
        assertEquals(-1f, SteeringCurve.compensate(-3f), 0f)
        assertEquals(0f, SteeringCurve.compensate(0f), 0f)
        assertEquals(0f, SteeringCurve.compensate(Float.NaN), 0f)
    }
}
