package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.TelemetryUiState
import com.beamng.remotecontrol.ui.dash.DashArea
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.DashState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground

/**
 * The drive screen frame: steering control on one side, brake + gas on the other,
 * the chosen dash layout in the centre. Everything is sized from u = 1% of the
 * screen height, so it scales from small phones to tablets and survives either
 * landscape rotation.
 *
 *  - [controlType] decides the steering slot: Tilt shows the wheel as a display fed
 *    by [steering]; Steering wheel makes it draggable ([onWheel]/[onWheelRelease]);
 *    Arrow buttons replaces it with two hold buttons.
 *  - [mirrored] swaps the two sides (left-handed players). Brake stays left of gas.
 *  - [dashboardOnly] removes both sides; the phone is then a pure display.
 */
@Composable
fun DriveScreen(
    telemetry: TelemetryUiState,
    metricUnits: Boolean,
    controlType: Int,
    dashboardOnly: Boolean,
    perfTimerEnabled: Boolean,
    analogPedals: Boolean,
    layout: DashLayout,
    mirrored: Boolean,
    steering: () -> Float,
    onThrottle: (Float) -> Unit,
    onBrake: (Float) -> Unit,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onWheel: (Float) -> Unit,
    onWheelRelease: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(screenBackground())) {
        val w = maxWidth
        val h = maxHeight
        val u = h / 100

        val isTilt = controlType == SettingsManager.CONTROL_GYROSCOPE
        // Tilt: mirror the sensor every frame. Touch modes: the control's own state.
        val tiltValue by produceState(0f, isTilt) {
            while (isTilt) {
                withFrameNanos { }
                value = steering()
            }
        }
        var touchValue by remember(controlType) { mutableFloatStateOf(0f) }
        val steerValue = if (isTilt) tiltValue else touchValue

        StatusChip(telemetry.connected, u, Modifier.padding(start = u * 4, top = u * 4))
        Text(
            "⚙", color = Graphite.Muted, fontSize = (u * 6).asSp(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = u * 3, top = u * 1)
                .clickable(onClick = onOpenSettings)
                .padding(u * 1.5f),
        )

        // ---- centre: warning lights, dash layout, steering bar ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(if (dashboardOnly) w * 0.62f else w * 0.41f)
                .fillMaxHeight()
                .padding(top = u * 3.5f, bottom = u * 4),
            verticalArrangement = Arrangement.spacedBy(u * 2.2f),
        ) {
            WarningRow(telemetry, u, Modifier.fillMaxWidth().height(u * 7))
            DashArea(
                layout,
                DashState(telemetry, metricUnits, perfTimerEnabled, telemetry.rpmScale),
                u,
                Modifier.fillMaxWidth().weight(1f),
            )
            if (!dashboardOnly) SteerBar(steerValue, Modifier.fillMaxWidth().height(u * 5))
        }

        if (dashboardOnly) {
            // Nobody steers from this phone: show what the game is applying instead.
            EchoBars(
                telemetry, u,
                Modifier.align(Alignment.BottomEnd).padding(end = u * 6, bottom = u * 4),
            )
            return@BoxWithConstraints
        }

        // ---- steering side ----
        val wheelSize = minOf(h * 0.74f, w * 0.27f)
        val steerSlot = Modifier
            .align(if (mirrored) Alignment.CenterEnd else Alignment.CenterStart)
            .padding(start = u * 3, end = u * 3, top = u * 8)
        if (controlType == SettingsManager.CONTROL_BUTTONS) {
            ArrowButtons(
                size = wheelSize, u = u,
                onLeft = { pressed ->
                    touchValue = if (pressed) -1f else 0f
                    onSteerLeft(pressed)
                },
                onRight = { pressed ->
                    touchValue = if (pressed) 1f else 0f
                    onSteerRight(pressed)
                },
                modifier = steerSlot,
            )
        } else {
            SteeringWheel(
                size = wheelSize,
                draggable = !isTilt,
                displayValue = tiltValue,
                onValue = { v ->
                    touchValue = v
                    onWheel(v)
                },
                onRelease = {
                    touchValue = 0f
                    onWheelRelease()
                },
                modifier = steerSlot,
            )
        }

        // ---- pedal side: brake left of gas, like a real pedal box ----
        Row(
            modifier = Modifier
                .align(if (mirrored) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(start = u * 4, end = u * 4, bottom = u * 2),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(u * 3),
        ) {
            Pedal(
                stringResource(R.string.drive_brake), Color(0xFFFF6B6B), u * 21, u * 46,
                plateFraction = 0.52f, analog = analogPedals, u = u, onValue = onBrake,
            )
            Pedal(
                stringResource(R.string.drive_gas), Graphite.Orange, u * 16, u * 72,
                plateFraction = 0.78f, analog = analogPedals, u = u, onValue = onThrottle,
            )
        }
    }
}
