package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.ModProtocol
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
 * the chosen dash layout in the centre.
 *
 * The three parts sit in one Row and the centre takes what the two sides leave, so
 * they cannot overlap on any aspect ratio. Heights come from u = 1% of the screen
 * height; the controls use cu, which shrinks on narrow screens (tablets, foldables)
 * so the dash keeps a usable width.
 *
 *  - [controlType] decides the steering slot: Tilt shows the wheel as a display fed
 *    by [steering]; Steering wheel makes it draggable ([onWheel]/[onWheelRelease]);
 *    Arrow buttons replaces it with two hold buttons.
 *  - [mirrored] swaps the two sides (left-handed players). Brake stays left of gas.
 *  - [dashboardOnly] removes both sides; the phone is then a pure display.
 *  - [mod] non-null = linked to the RemoteControlPro mod: shift paddles beside the
 *    pedals, a handbrake above them, a horn under the wheel and a row of light keys
 *    under the gauges. [wheelLockDeg] is the car's steering lock when the mod knows it.
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
    mod: ModControls?,
    wheelLockDeg: Float,
    steering: () -> Float,
    onThrottle: (Float) -> Unit,
    onBrake: (Float) -> Unit,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onWheel: (Float) -> Unit,
    onWheelRelease: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Background runs under the display cutout; content stays clear of it.
    BoxWithConstraints(
        Modifier.fillMaxSize().background(screenBackground()).windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        val w = maxWidth
        val h = maxHeight
        val u = h / 100
        // Controls are designed for ~2:1 phones (w ≈ 200u). Narrower: scale them down.
        val cu = minOf(u, w / 190)

        val isTilt = controlType == SettingsManager.CONTROL_GYROSCOPE
        // Tilt: mirror the sensor every frame. Touch modes: the control's own state.
        val tiltState = produceState(0f, isTilt) {
            while (isTilt) {
                withFrameNanos { }
                value = steering()
            }
        }
        var touchValue by remember(controlType) { mutableFloatStateOf(0f) }
        // Both arrows can be down at once; the bar shows their sum.
        var leftDown by remember(controlType) { mutableStateOf(false) }
        var rightDown by remember(controlType) { mutableStateOf(false) }
        // Read lazily (draw phase) so tilt, which changes every frame, doesn't recompose the screen.
        val steerValue = { if (isTilt) tiltState.value else touchValue }

        val centre: @Composable RowScope.() -> Unit = {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(top = u * 3.5f, bottom = u * 4),
                verticalArrangement = Arrangement.spacedBy(u * 2.2f),
            ) {
                WarningRow(telemetry, u, Modifier.fillMaxWidth().height(u * 7))
                DashArea(
                    layout,
                    DashState(telemetry, metricUnits, perfTimerEnabled, telemetry.rpmScale),
                    u,
                    Modifier.fillMaxWidth().weight(1f),
                )
                if (!dashboardOnly && mod != null && mod.showLights) {
                    ModLightsRow(mod, u, Modifier.fillMaxWidth().height(u * 9))
                }
                if (!dashboardOnly) SteerBar(steerValue, Modifier.fillMaxWidth().height(u * 5))
            }
        }

        if (dashboardOnly) {
            // Nobody steers from this phone: the gauges take the whole width, with
            // what the game is applying alongside.
            Row(Modifier.fillMaxSize().padding(horizontal = u * 6), horizontalArrangement = Arrangement.spacedBy(u * 6)) {
                centre()
                EchoBars(telemetry, u, Modifier.align(Alignment.Bottom).padding(bottom = u * 4))
            }
        } else {
            val steerSide: @Composable RowScope.() -> Unit = {
                val horn = mod != null && mod.showHorn
                val wheelSize = minOf(h * (if (horn) 0.64f else 0.74f), cu * 60)
                Column(
                    Modifier.fillMaxHeight().padding(top = u * 8, bottom = u * 2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (controlType == SettingsManager.CONTROL_BUTTONS) {
                        ArrowButtons(
                            size = wheelSize, u = u,
                            onLeft = { pressed ->
                                leftDown = pressed
                                touchValue = (if (rightDown) 1f else 0f) - (if (leftDown) 1f else 0f)
                                onSteerLeft(pressed)
                            },
                            onRight = { pressed ->
                                rightDown = pressed
                                touchValue = (if (rightDown) 1f else 0f) - (if (leftDown) 1f else 0f)
                                onSteerRight(pressed)
                            },
                        )
                    } else {
                        SteeringWheel(
                            size = wheelSize,
                            draggable = !isTilt,
                            displayValue = { tiltState.value },
                            lockDeg = wheelLockDeg,
                            onValue = { v ->
                                touchValue = v
                                onWheel(v)
                            },
                            onRelease = {
                                touchValue = 0f
                                onWheelRelease()
                            },
                        )
                    }
                    if (mod != null && horn) {
                        Spacer(Modifier.height(u * 2))
                        HoldKey(stringResource(R.string.mod_key_horn), u, Modifier.width(wheelSize * 0.6f).height(u * 9)) { pressed ->
                            mod.onButton(ModProtocol.BUTTON_HORN, pressed)
                        }
                    }
                }
            }
            val pedalSide: @Composable RowScope.() -> Unit = {
                // Brake left of gas, like a real pedal box — also when mirrored.
                Column(
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = u * 2),
                    horizontalAlignment = Alignment.End,
                ) {
                if (mod != null && mod.showHandbrake) {
                    LatchKey(
                        stringResource(R.string.mod_key_handbrake), mod.handbrakeOn, u,
                        Modifier.width(cu * 24).height(u * 10), onChange = mod.onHandbrake,
                    )
                    Spacer(Modifier.height(u * 3))
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(cu * 3),
                ) {
                    if (mod != null && mod.showShift) {
                        HoldKey("−", u, Modifier.width(cu * 11).height(u * 34), Graphite.Blue) { pressed ->
                            mod.onButton(ModProtocol.BUTTON_SHIFT_DOWN, pressed)
                        }
                    }
                    Pedal(
                        stringResource(R.string.drive_brake), Graphite.Red, cu * 21, u * 46,
                        plateFraction = 0.52f, analog = analogPedals, u = cu, onValue = onBrake,
                    )
                    Pedal(
                        stringResource(R.string.drive_gas), Graphite.Orange, cu * 16, u * 72,
                        plateFraction = 0.78f, analog = analogPedals, u = cu, onValue = onThrottle,
                    )
                    if (mod != null && mod.showShift) {
                        HoldKey("+", u, Modifier.width(cu * 11).height(u * 34), Graphite.Blue) { pressed ->
                            mod.onButton(ModProtocol.BUTTON_SHIFT_UP, pressed)
                        }
                    }
                }
                }
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = cu * 3),
                horizontalArrangement = Arrangement.spacedBy(cu * 3),
            ) {
                if (mirrored) pedalSide() else steerSide()
                centre()
                if (mirrored) steerSide() else pedalSide()
            }
        }

        // Drawn last so they stay tappable above everything.
        StatusChip(telemetry.connected, mod != null, u, Modifier.padding(start = u * 4, top = u * 4))
        SettingsGear(u, onOpenSettings, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun SettingsGear(u: Dp, onClick: () -> Unit, modifier: Modifier) {
    Text(
        "⚙", color = Graphite.Muted, fontSize = (u * 6).asSp(),
        modifier = modifier
            .padding(end = u * 3, top = u * 1)
            .clickable(onClick = onClick)
            .padding(u * 1.5f),
    )
}
