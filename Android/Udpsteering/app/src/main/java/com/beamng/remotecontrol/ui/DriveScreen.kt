package com.beamng.remotecontrol.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.theme.NightGarage
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DriveScreen(
    telemetry: TelemetryUiState,
    metricUnits: Boolean,
    controlType: Int,
    dashboardOnly: Boolean,
    perfTimerEnabled: Boolean,
    onThrottle: (Boolean) -> Unit,
    onBrake: (Boolean) -> Unit,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderRelease: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground()),
    ) {
        if (!dashboardOnly) {
            Pedal(
                label = stringResource(R.string.drive_brake),
                baseTop = Color(0xFF3A1410),
                baseBottom = Color(0xFF200B08),
                textColor = Color(0xFFFF9D8A),
                onPress = onBrake,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            StatusRow(telemetry, perfTimerEnabled, onOpenSettings)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RpmGauge(
                    rpm = telemetry.rpm,
                    modifier = Modifier.size(if (dashboardOnly) 185.dp else 150.dp),
                )
                SpeedCluster(telemetry, metricUnits, big = dashboardOnly)
                AuxColumn(telemetry, dashboardOnly)
            }

            if (!dashboardOnly) {
                SteeringArea(controlType, onSteerLeft, onSteerRight, onSliderChange, onSliderRelease)
            }
        }

        if (!dashboardOnly) {
            Pedal(
                label = stringResource(R.string.drive_gas),
                baseTop = Color(0xFF12300F),
                baseBottom = Color(0xFF0A1C08),
                textColor = Color(0xFFB9F0A8),
                onPress = onThrottle,
            )
        }
    }
}

// ==================== status ====================

@Composable
private fun StatusRow(
    telemetry: TelemetryUiState,
    perfTimerEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (telemetry.connected) {
                "● " + stringResource(R.string.status_connected)
            } else {
                "● " + stringResource(R.string.connecting_dialog_title)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (telemetry.connected) NightGarage.Green else NightGarage.TextDim,
        )
        if (perfTimerEnabled) {
            Spacer(Modifier.width(14.dp))
            TimerChip(telemetry)
        }
        Spacer(Modifier.weight(1f))
        WarningLights(telemetry.lights)
        Spacer(Modifier.width(14.dp))
        Text(
            "⚙",
            fontSize = 18.sp,
            color = NightGarage.TextDim,
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
    }
}

@Composable
private fun TimerChip(telemetry: TelemetryUiState) {
    val live = telemetry.timerLiveSec
    val last = telemetry.timerLastSec
    val best = telemetry.timerBestSec
    Row(
        modifier = Modifier
            .background(NightGarage.Panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.drive_timer_label),
            fontSize = 9.sp, color = NightGarage.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = when {
                live != null -> String.format("%.2f", live)
                last != null -> String.format("%.2fs", last)
                else -> stringResource(R.string.drive_timer_ready)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (live != null) NightGarage.AmberHot else NightGarage.TextBright,
        )
        if (best != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.drive_timer_best),
                fontSize = 9.sp, color = NightGarage.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                String.format("%.2fs", best),
                style = MaterialTheme.typography.bodyMedium,
                color = NightGarage.Green,
            )
        }
    }
}

// ==================== warning lights (real dash icons) ====================
// The game pulses blinker/ABS bits itself, so rendering raw state blinks in
// sync with the real car; a short tween just smooths the edges.

@Composable
private fun lightAlpha(on: Boolean): Float {
    val alpha by animateFloatAsState(if (on) 1f else 0.22f, tween(120), label = "light")
    return alpha
}

@Composable
private fun WarningLights(lights: List<Boolean>) {
    fun on(i: Int) = lights.getOrElse(i) { false }
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalIcon(pointLeft = true, on = on(Receivepacket.INDEX_SIGNAL_L))
        HighBeamIcon(on = on(Receivepacket.INDEX_FULLBEAM))
        HandbrakeBadge(on = on(Receivepacket.INDEX_HANDBRAKE))
        TextPill("ABS", NightGarage.Amber, on = on(Receivepacket.INDEX_ABS))
        TextPill("TC", NightGarage.Amber, on = on(Receivepacket.INDEX_TC))
        TextPill("OIL", NightGarage.Red, on = on(Receivepacket.INDEX_OILWARN))
        BatteryIcon(on = on(Receivepacket.INDEX_BATTERY))
        SignalIcon(pointLeft = false, on = on(Receivepacket.INDEX_SIGNAL_R))
    }
}

@Composable
private fun SignalIcon(pointLeft: Boolean, on: Boolean) {
    val alpha = lightAlpha(on)
    Canvas(Modifier.size(width = 17.dp, height = 13.dp)) {
        val path = Path().apply {
            if (pointLeft) {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            }
            close()
        }
        drawPath(path, NightGarage.Amber.copy(alpha = alpha))
    }
}

@Composable
private fun HighBeamIcon(on: Boolean) {
    val alpha = lightAlpha(on)
    Canvas(Modifier.size(width = 20.dp, height = 13.dp)) {
        val color = NightGarage.Blue.copy(alpha = alpha)
        // Lamp: filled half-disc on the right
        drawArc(
            color, startAngle = -90f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(size.width * 0.42f, 0f),
            size = Size(size.width * 0.58f, size.height),
        )
        // Parallel beam lines to the left
        val stroke = size.height * 0.16f
        for (i in 0..2) {
            val y = size.height * (0.18f + 0.32f * i)
            drawLine(color, Offset(0f, y), Offset(size.width * 0.34f, y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun HandbrakeBadge(on: Boolean) {
    val alpha = lightAlpha(on)
    val color = NightGarage.Red.copy(alpha = alpha)
    Box(
        modifier = Modifier
            .size(17.dp)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "P", fontSize = 9.sp, color = color,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        )
    }
}

@Composable
private fun TextPill(label: String, color: Color, on: Boolean) {
    val c = color.copy(alpha = lightAlpha(on))
    Box(
        modifier = Modifier
            .border(1.dp, c, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            label, fontSize = 8.sp, color = c,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
        )
    }
}

@Composable
private fun BatteryIcon(on: Boolean) {
    val alpha = lightAlpha(on)
    Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
        val color = NightGarage.Amber.copy(alpha = alpha)
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(0f, size.height * 0.25f),
            size = Size(size.width, size.height * 0.75f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
        // Terminals
        drawLine(color, Offset(size.width * 0.20f, 0f), Offset(size.width * 0.38f, 0f), stroke)
        drawLine(color, Offset(size.width * 0.62f, 0f), Offset(size.width * 0.80f, 0f), stroke)
    }
}

// ==================== gauges ====================

private const val MAX_RPM = 8000f
private const val REDLINE_RPM = 6500f
private const val GAUGE_START = 135f  // degrees, arc opens downward
private const val GAUGE_SWEEP = 270f

@Composable
fun RpmGauge(rpm: Float, modifier: Modifier = Modifier) {
    val overRedline = rpm >= REDLINE_RPM
    val flash by rememberInfiniteTransition(label = "redline").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse),
        label = "flash",
    )
    val redZoneAlpha = if (overRedline) flash else 0.35f
    val needleColor = if (overRedline) NightGarage.Red else NightGarage.AmberHot

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2 + 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - inset * 2, size.height - inset * 2,
            )
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = NightGarage.Panel,
                startAngle = GAUGE_START, sweepAngle = GAUGE_SWEEP, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Red zone (flashes while over the redline)
            val redStart = GAUGE_START + GAUGE_SWEEP * (REDLINE_RPM / MAX_RPM)
            drawArc(
                color = NightGarage.Red.copy(alpha = redZoneAlpha),
                startAngle = redStart,
                sweepAngle = GAUGE_START + GAUGE_SWEEP - redStart, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            // Live arc
            val frac = (rpm / MAX_RPM).coerceIn(0f, 1f)
            if (frac > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to NightGarage.Amber, 1f to NightGarage.AmberHot,
                    ),
                    startAngle = GAUGE_START, sweepAngle = GAUGE_SWEEP * frac, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // Needle
            val needleAngle = GAUGE_START + GAUGE_SWEEP * frac
            rotate(needleAngle + 90f, pivot = center) {
                drawLine(
                    color = needleColor,
                    start = center,
                    end = Offset(center.x, center.y - size.minDimension * 0.38f),
                    strokeWidth = stroke * 0.4f,
                    cap = StrokeCap.Round,
                )
            }
            // Hub
            drawCircle(NightGarage.PanelDeep, radius = size.minDimension * 0.13f, center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.1f", rpm / 1000f),
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFD9A0),
            )
            Text(
                stringResource(R.string.drive_rpm_x1000),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
                color = NightGarage.TextFaint,
            )
        }
    }
}

@Composable
private fun SpeedCluster(telemetry: TelemetryUiState, metricUnits: Boolean, big: Boolean) {
    val speed = if (metricUnits) {
        (3.6f * telemetry.speedMs).roundToInt()
    } else {
        (2.23694f * telemetry.speedMs).roundToInt()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DriftBadge(telemetry)
        Text(
            text = String.format("%03d", min(speed, 999)),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = if (big) 88.sp else 64.sp,
            ),
            color = NightGarage.TextBright,
        )
        Text(
            text = stringResource(if (metricUnits) R.string.speed_kmh else R.string.speed_mph),
            style = MaterialTheme.typography.labelSmall,
            color = NightGarage.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                telemetry.gear,
                style = MaterialTheme.typography.headlineMedium,
                color = NightGarage.Amber,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.drive_gear),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
                color = NightGarage.TextFaint,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * Drift slip-angle badge above the speedometer. Appears only while the
 * MotionSim stream is flowing AND the car is actually sliding (>=8°).
 */
@Composable
private fun DriftBadge(telemetry: TelemetryUiState) {
    val drift = telemetry.driftDeg
    val active = drift != null && kotlin.math.abs(drift) >= 8f
    val pulse by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = if (active) 1f else 0f }
            .background(NightGarage.Panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.drive_drift),
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall,
            color = NightGarage.AmberHot.copy(alpha = if (active) pulse else 1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${kotlin.math.abs(drift ?: 0f).roundToInt()}°",
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.TextBright,
        )
        telemetry.driftMaxDeg?.let { max ->
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.drive_drift_max) + " ${max.roundToInt()}°",
                fontSize = 9.sp,
                style = MaterialTheme.typography.labelSmall,
                color = NightGarage.TextFaint,
            )
        }
    }
}

@Composable
private fun AuxColumn(telemetry: TelemetryUiState, dashboardOnly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AuxBar(
            label = stringResource(R.string.drive_fuel),
            fraction = telemetry.fuel,
            color = NightGarage.Amber,
            flashing = telemetry.connected && telemetry.fuel < 0.1f,
        )
        AuxBar(
            label = stringResource(R.string.drive_temp) + " " + telemetry.engTemp.roundToInt() + "°",
            fraction = (telemetry.engTemp / 130f).coerceIn(0f, 1f),
            color = if (telemetry.engTemp > 110f) NightGarage.Red else NightGarage.Green,
            flashing = telemetry.engTemp > 110f,
        )
        if (telemetry.hasTurbo) {
            AuxBar(
                label = stringResource(R.string.drive_boost) + " " + String.format("%.1f", telemetry.turbo),
                fraction = (telemetry.turbo / 2f).coerceIn(0f, 1f),
                color = NightGarage.Blue,
            )
        }
        if (dashboardOnly) {
            // Input echo: what the game is actually applying right now.
            Spacer(Modifier.height(2.dp))
            AuxBar(stringResource(R.string.drive_thr), telemetry.throttleEcho, NightGarage.Green)
            AuxBar(stringResource(R.string.drive_brk), telemetry.brakeEcho, NightGarage.Red)
            AuxBar(stringResource(R.string.drive_clt), telemetry.clutchEcho, NightGarage.Blue)
        }
    }
}

@Composable
private fun AuxBar(label: String, fraction: Float, color: Color, flashing: Boolean = false) {
    val alpha = if (flashing) {
        val a by rememberInfiniteTransition(label = "warn").animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
            label = "a",
        )
        a
    } else {
        1f
    }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = if (flashing) color.copy(alpha = alpha) else NightGarage.TextDim,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(96.dp)
                .height(6.dp)
                .background(NightGarage.Panel, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color.copy(alpha = alpha), RoundedCornerShape(3.dp)),
            )
        }
    }
}

// ==================== controls ====================

@Composable
private fun Pedal(
    label: String,
    baseTop: Color,
    baseBottom: Color,
    textColor: Color,
    onPress: (Boolean) -> Unit,
) {
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .padding(10.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (pressed) baseTop.copy(alpha = 1f) else baseTop.copy(alpha = 0.7f),
                        baseBottom,
                    ),
                ),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress(true)
                        tryAwaitRelease()
                        pressed = false
                        onPress(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (pressed) Color.White else textColor,
            modifier = Modifier.graphicsLayer { rotationZ = 90f },
        )
    }
}

@Composable
private fun SteeringArea(
    controlType: Int,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderRelease: () -> Unit,
) {
    when (controlType) {
        SettingsManager.CONTROL_BUTTONS -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HoldButton("◀", Modifier.weight(1f), onSteerLeft)
                HoldButton("▶", Modifier.weight(1f), onSteerRight)
            }
        }
        SettingsManager.CONTROL_SLIDER -> {
            var pos by remember { mutableFloatStateOf(0f) }
            Slider(
                value = pos,
                onValueChange = {
                    pos = it
                    onSliderChange(it)
                },
                onValueChangeFinished = {
                    pos = 0f
                    onSliderRelease()
                },
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = NightGarage.Amber,
                    activeTrackColor = NightGarage.Panel,
                    inactiveTrackColor = NightGarage.Panel,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
        else -> {
            // Gyroscope: no on-screen steering — tilt the phone.
            Text(
                stringResource(R.string.drive_gyro_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = NightGarage.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HoldButton(glyph: String, modifier: Modifier, onPress: (Boolean) -> Unit) {
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (pressed) {
                    Brush.verticalGradient(listOf(Color(0xFFFF8A3A), Color(0xFFE05A00)))
                } else {
                    Brush.verticalGradient(listOf(NightGarage.Panel, Color(0xFF161009)))
                },
                RoundedCornerShape(14.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress(true)
                        tryAwaitRelease()
                        pressed = false
                        onPress(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 20.sp,
            color = if (pressed) NightGarage.OnAmber else NightGarage.Amber,
        )
    }
}
