package com.beamng.remotecontrol.ui.dash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.ui.TelemetryUiState

/** Everything a dash layout may read. Layouts draw gauges only; the frame owns the rest. */
data class DashState(
    val telemetry: TelemetryUiState,
    val metricUnits: Boolean,
    val perfTimerEnabled: Boolean,
    val rpmScale: RpmScale,
)

/** The centre of the drive screen, in the chosen layout. [u] is 1% of screen height. */
@Composable
fun DashArea(layout: DashLayout, state: DashState, u: Dp, modifier: Modifier = Modifier) {
    when (layout) {
        DashLayout.MODERN -> ModernDash(state, u, modifier)
        DashLayout.CLASSIC -> ClassicDash(state, u, modifier)
        DashLayout.RACE -> RaceDash(state, u, modifier)
        DashLayout.MINIMAL -> MinimalDash(state, u, modifier)
    }
}

/** Speed in the unit the user chose. */
internal fun DashState.speed(): Float =
    if (metricUnits) telemetry.speedMs * 3.6f else telemetry.speedMs * 2.23694f

/** RPM that is safe to draw: a garbage packet must never reach a layout pass as NaN. */
internal fun DashState.safeRpm(): Float =
    if (telemetry.rpm.isFinite()) telemetry.rpm.coerceAtLeast(0f) else 0f
