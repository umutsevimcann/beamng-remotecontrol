package com.beamng.remotecontrol.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            StatusRow(telemetry, onOpenSettings)

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
                AuxColumn(telemetry)
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
private fun StatusRow(telemetry: TelemetryUiState, onOpenSettings: () -> Unit) {
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
private fun WarningLights(lights: List<Boolean>) {
    // Only lights the stock game actually transmits (DL_x bits).
    val spec = listOf(
        Receivepacket.INDEX_FULLBEAM to NightGarage.Blue,
        Receivepacket.INDEX_HANDBRAKE to NightGarage.Red,
        Receivepacket.INDEX_SIGNAL_L to NightGarage.Amber,
        Receivepacket.INDEX_SIGNAL_R to NightGarage.Amber,
        Receivepacket.INDEX_OILWARN to NightGarage.Red,
        Receivepacket.INDEX_BATTERY to NightGarage.Amber,
        Receivepacket.INDEX_ABS to NightGarage.Amber,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        spec.forEach { (index, color) ->
            val on = lights.getOrElse(index) { false }
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer { if (on) shadowElevation = 8f }
                    .background(if (on) color else NightGarage.Panel, CircleShape),
            )
        }
    }
}

// ==================== gauges ====================

private const val MAX_RPM = 8000f
private const val REDLINE_RPM = 6500f
private const val GAUGE_START = 135f  // degrees, arc opens downward
private const val GAUGE_SWEEP = 270f

@Composable
fun RpmGauge(rpm: Float, modifier: Modifier = Modifier) {
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
            // Red zone (static)
            val redStart = GAUGE_START + GAUGE_SWEEP * (REDLINE_RPM / MAX_RPM)
            drawArc(
                color = NightGarage.Red.copy(alpha = 0.35f),
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
                    color = NightGarage.AmberHot,
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

@Composable
private fun AuxColumn(telemetry: TelemetryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AuxBar(
            label = stringResource(R.string.drive_fuel),
            fraction = telemetry.fuel,
            color = NightGarage.Amber,
        )
        AuxBar(
            label = stringResource(R.string.drive_temp) + " " + telemetry.engTemp.roundToInt() + "°",
            fraction = (telemetry.engTemp / 130f).coerceIn(0f, 1f),
            color = if (telemetry.engTemp > 110f) NightGarage.Red else NightGarage.Green,
        )
        if (telemetry.hasTurbo) {
            AuxBar(
                label = stringResource(R.string.drive_boost) + " " + String.format("%.1f", telemetry.turbo),
                fraction = (telemetry.turbo / 2f).coerceIn(0f, 1f),
                color = NightGarage.Blue,
            )
        }
    }
}

@Composable
private fun AuxBar(label: String, fraction: Float, color: Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = NightGarage.TextDim,
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
                    .background(color, RoundedCornerShape(3.dp)),
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
