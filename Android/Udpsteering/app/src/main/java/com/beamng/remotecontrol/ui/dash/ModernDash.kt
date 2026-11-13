package com.beamng.remotecontrol.ui.dash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.Panel
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Modern": a tiled panel. Segmented RPM arc with the speed inside it, a gear tile,
 * fuel and temperature bars, and a strip for the 0-100 timer and drift readout.
 */
@Composable
fun ModernDash(state: DashState, u: Dp, modifier: Modifier = Modifier) {
    val t = state.telemetry
    Column(modifier, verticalArrangement = Arrangement.spacedBy(u * 2.2f)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 2.2f)) {
            SpeedTile(state, u, Modifier.weight(2.7f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2.2f)) {
                Panel(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DashLabel(stringResource(R.string.drive_gear), u)
                        Text(
                            t.gear, color = Graphite.Blue, fontSize = (u * 17 * gearScale(t.gear)).asSp(),
                            lineHeight = (u * 17).asSp(), fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
                Panel(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = u * 2.4f), verticalArrangement = Arrangement.Center) {
                        Meter(stringResource(R.string.drive_fuel), "%${(t.fuel * 100).roundToInt()}", t.fuel, Graphite.Green, u)
                        Spacer(Modifier.height(u * 2.4f))
                        if (t.hasTurbo) {
                            Meter(
                                stringResource(R.string.drive_boost), "%.1f".format(t.turbo),
                                t.turbo / 2f, Graphite.Amber, u,
                            )
                        } else {
                            Meter(
                                stringResource(R.string.drive_temp), "${t.engTemp.roundToInt()}°",
                                t.engTemp / 130f, Graphite.Blue, u,
                            )
                        }
                    }
                }
            }
        }
        TimerDriftStrip(state, u)
    }
}

/** 0-100 timer and drift readout; renders nothing when neither applies. */
@Composable
internal fun TimerDriftStrip(state: DashState, u: Dp) {
    val t = state.telemetry
    val showDrift = t.driftMaxDeg != null
    if (!state.perfTimerEnabled && !showDrift) return
    Panel(Modifier.fillMaxWidth().height(u * 13)) {
        Row(Modifier.fillMaxSize().padding(horizontal = u * 3), verticalAlignment = Alignment.CenterVertically) {
            if (state.perfTimerEnabled) {
                Column(Modifier.weight(1f)) {
                    DashLabel(stringResource(R.string.drive_timer_label), u)
                    val shown = t.timerLiveSec ?: t.timerLastSec
                    val now = shown?.let { stringResource(R.string.drive_timer_value, it) }
                        ?: stringResource(R.string.drive_timer_ready)
                    val best = t.timerBestSec?.let { "  ·  " + stringResource(R.string.drive_timer_best_value, it) } ?: ""
                    Text(now + best, color = Graphite.Text, fontSize = (u * 4.2f).asSp(), fontWeight = FontWeight.Bold)
                }
            }
            if (showDrift) {
                Column(Modifier.weight(1f)) {
                    DashLabel(stringResource(R.string.drive_drift), u)
                    Text(
                        stringResource(R.string.drive_drift_value, abs(t.driftDeg ?: 0f).roundToInt()) + "  ·  " +
                            stringResource(R.string.drive_drift_best, (t.driftMaxDeg ?: 0f).roundToInt()),
                        color = Graphite.Amber, fontSize = (u * 4.2f).asSp(), fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DashLabel(text: String, u: Dp, color: Color = Graphite.Muted) {
    Text(text, color = color, fontSize = (u * 2.3f).asSp(), letterSpacing = (u * 0.5f).asSp(), fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun Meter(label: String, value: String, fraction: Float, color: Color, u: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        DashLabel(label, u)
        Text(value, color = Graphite.Text, fontSize = (u * 3).asSp(), fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(u * 1.2f))
    Box(Modifier.fillMaxWidth().height(u * 1.5f).background(Graphite.Track, RoundedCornerShape(50))) {
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
    }
}

@Composable
private fun SpeedTile(state: DashState, u: Dp, modifier: Modifier) {
    val safeRpm = state.safeRpm()
    val rpm by animateFloatAsState(safeRpm, spring(stiffness = Spring.StiffnessMediumLow), label = "rpm")
    val scale = state.rpmScale
    Panel(modifier) {
        Canvas(Modifier.fillMaxSize().padding(u * 3)) {
            // Segmented arc, 200° sweep opening downward.
            val segments = 46
            val start = 170f
            val sweep = 200f
            val thick = size.minDimension * 0.105f
            val d = minOf(size.width, size.height * 1.55f) - thick
            val topLeft = Offset((size.width - d) / 2f, size.height * 0.16f)
            val step = sweep / segments
            val lit = (rpm / scale.max).coerceIn(0f, 1f) * segments
            val redFrom = scale.redline / scale.max * segments
            for (i in 0 until segments) {
                val red = i >= redFrom
                val on = i < lit
                val color = when {
                    on && red -> Graphite.Red
                    on -> Graphite.Text
                    red -> Graphite.Red.copy(alpha = 0.28f)
                    else -> Graphite.Track
                }
                drawArc(color, start + i * step, step * 0.62f, false, topLeft, Size(d, d), style = Stroke(thick, cap = StrokeCap.Butt))
            }
        }
        Column(Modifier.padding(start = u * 3.4f, top = u * 2.6f)) {
            Text("${(safeRpm / 10).roundToInt() * 10}", color = Graphite.Text, fontSize = (u * 4.6f).asSp(), fontWeight = FontWeight.Bold)
            DashLabel(stringResource(R.string.drive_rpm), u)
        }
        Column(
            Modifier.fillMaxSize().padding(top = u * 12),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${state.speed().roundToInt()}", color = Graphite.Text, fontSize = (u * 21).asSp(), lineHeight = (u * 21).asSp(),
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-u * 0.6f).asSp(), textAlign = TextAlign.Center,
            )
            DashLabel(stringResource(if (state.metricUnits) R.string.speed_kmh else R.string.speed_mph), u)
        }
    }
}
