package com.beamng.remotecontrol.ui.dash

import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.ui.TelemetryUiState

/**
 * Believable driving values for layout previews (main menu, settings thumbnails):
 * 96 km/h in 4th at 4600 rpm, traction control lit.
 */
fun sampleDashState(metricUnits: Boolean = true): DashState {
    val telemetry = TelemetryUiState().apply {
        connected = true
        speedMs = 96f / 3.6f
        rpm = 4600f
        gear = "4"
        fuel = 0.71f
        engTemp = 91f
        throttleEcho = 0.7f
        driftDeg = 18f
        driftMaxDeg = 31f
        lights[Receivepacket.INDEX_TC] = true
    }
    return DashState(telemetry, metricUnits, perfTimerEnabled = false, rpmScale = RpmScale(8000f, 6500f))
}
