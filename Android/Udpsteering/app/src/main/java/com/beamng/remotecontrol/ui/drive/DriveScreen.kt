package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 *  - [mod] non-null = linked to the RemoteControlPro mod: two shift paddles stacked
 *    above the brake (the space a short pedal leaves, a thumb's move from the gas),
 *    horn and handbrake under the wheel, and a row of light keys under the gauges. [wheelLockDeg] is the car's steering lock when the mod knows it.
 */
@Composable
fun DriveScreen(
    telemetry: TelemetryUiState,
    metricUnits: Boolean,
    controlType: Int,
    dashboardOnly: Boolean,
    perfTimerEnabled: Boolean,
    analogPedals: Boolean,
    splitPedals: Boolean,
    layout: DashLayout,
    mirrored: Boolean,
    mod: ModControls?,
    wheelLockDeg: Float,
    wheelStyle: WheelStyle,
    wheelRest: (() -> Float)?,
    wheelRestStrength: () -> Float,
    wheelGameAngle: () -> Float?,
    onWheelHeld: (Boolean) -> Unit,
    /** Non-null: dragging the gauges turns the camera (mod 2.3+), in dp. */
    onLookDrag: ((Float, Float) -> Unit)?,
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
        // Which page of the mod's key row is showing: 0 lights, 1 vehicle systems.
        var keyPage by rememberSaveable { mutableIntStateOf(0) }
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
                val look = onLookDrag
                val lookModifier = if (look == null) Modifier else Modifier.pointerInput(Unit) {
                    val density = this.density
                    detectDragGestures { change, drag ->
                        change.consume()
                        look(drag.x / density, drag.y / density)
                    }
                }
                DashArea(
                    layout,
                    DashState(telemetry, metricUnits, perfTimerEnabled, telemetry.rpmScale),
                    u,
                    Modifier.fillMaxWidth().weight(1f).then(lookModifier),
                )
                if (!dashboardOnly && mod != null && (mod.showLights || mod.showVehicle || mod.showGame)) {
                    ModKeyRow(mod, telemetry, keyPage, { keyPage = it }, u, Modifier.fillMaxWidth().height(u * 11))
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
                val wheelKeys = mod != null && (mod.showHorn || mod.showHandbrake)
                val wheelSize = minOf(h * (if (wheelKeys) 0.62f else 0.74f), cu * 60)
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
                            style = wheelStyle,
                            restValue = wheelRest,
                            restStrength = wheelRestStrength,
                            gameAngle = wheelGameAngle,
                            onHeld = onWheelHeld,
                            onHub = if (mod != null && mod.hornOnHub) {
                                { pressed -> mod.onButton(ModProtocol.BUTTON_HORN, pressed) }
                            } else {
                                null
                            },
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
                    if (mod != null && wheelKeys) {
                        Spacer(Modifier.height(u * 2.5f))
                        val both = mod.showHorn && mod.showHandbrake
                        ModWheelKeys(mod, u, Modifier.width(if (both) wheelSize * 1.08f else wheelSize * 0.6f).height(u * 12))
                    }
                }
            }
            val pedalSide: @Composable RowScope.() -> Unit = {
                // Brake left of gas, like a real pedal box — also when mirrored.
                Row(
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = u * 2),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(cu * 3),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(u * 2)) {
                        if (mod != null && mod.showShift) {
                            ShiftKey(up = true, mod, u, Modifier.width(cu * 21).height(u * 17))
                            ShiftKey(up = false, mod, u, Modifier.width(cu * 21).height(u * 17))
                        }
                        Pedal(
                            stringResource(R.string.drive_brake), Graphite.Red, cu * 21, u * 46,
                            plateFraction = 0.52f, analog = analogPedals, u = cu, onValue = onBrake,
                        )
                    }
                    Pedal(
                        stringResource(R.string.drive_gas), Graphite.Orange, cu * 16, u * 72,
                        plateFraction = 0.78f, analog = analogPedals, u = cu, onValue = onThrottle,
                    )
                }
            }
            // Split pedals (tilt only, the phone steers itself): one thumb per pedal,
            // the brake with the shift paddles above it, the gas with horn and handbrake.
            val brakeSide: @Composable RowScope.() -> Unit = {
                Column(
                    Modifier.align(Alignment.Bottom).padding(bottom = u * 2),
                    verticalArrangement = Arrangement.spacedBy(u * 2),
                ) {
                    if (mod != null && mod.showShift) {
                        // A little shorter than beside the wheel: mirrored, this column sits right
                        // under the settings gear, and the top paddle must stay clear of it.
                        ShiftKey(up = true, mod, u, Modifier.width(cu * 24).height(u * 15))
                        ShiftKey(up = false, mod, u, Modifier.width(cu * 24).height(u * 15))
                    }
                    Pedal(
                        stringResource(R.string.drive_brake), Graphite.Red, cu * 24, u * 46,
                        plateFraction = 0.52f, analog = analogPedals, u = cu, onValue = onBrake,
                    )
                }
            }
            val gasSide: @Composable RowScope.() -> Unit = {
                Column(
                    Modifier.align(Alignment.Bottom).padding(bottom = u * 2),
                    verticalArrangement = Arrangement.spacedBy(u * 2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val keys = mod != null && (mod.showHorn || mod.showHandbrake)
                    if (mod != null && keys) {
                        ModWheelKeys(mod, u, Modifier.width(cu * 24).height(u * 12))
                    }
                    // With keys above it the gas is shorter, so the keys start well below
                    // the settings gear in the corner and a thumb cannot catch both.
                    Pedal(
                        stringResource(R.string.drive_gas), Graphite.Orange, cu * 24, if (keys) u * 58 else u * 72,
                        plateFraction = 0.78f, analog = analogPedals, u = cu, onValue = onThrottle,
                    )
                }
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = cu * 3),
                horizontalArrangement = Arrangement.spacedBy(cu * 3),
            ) {
                if (isTilt && splitPedals) {
                    // Mirrored puts the gas under the left thumb.
                    if (mirrored) gasSide() else brakeSide()
                    centre()
                    if (mirrored) brakeSide() else gasSide()
                } else {
                    if (mirrored) pedalSide() else steerSide()
                    centre()
                    if (mirrored) steerSide() else pedalSide()
                }
            }
        }

        // Drawn last so they stay tappable above everything.
        StatusChip(telemetry.connected, mod != null, u, Modifier.padding(start = u * 4, top = u * 4))
        SettingsGear(u, onOpenSettings, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun SettingsGear(u: Dp, onClick: () -> Unit, modifier: Modifier) {
    Text(
        "⚙", color = Graphite.Muted, fontSize = (u * 6).asSp(),
        modifier = modifier
            .padding(end = u * 3, top = u * 1)
            .clickable(onClick = onClick)
            .padding(u * 1.5f),
    )
}
