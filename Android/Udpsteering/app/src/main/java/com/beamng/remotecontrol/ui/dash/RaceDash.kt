package com.beamng.remotecontrol.ui.dash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.roundToInt

private const val LED_COUNT = 12
private const val LEDS_START_AT = 0.60f // fraction of redline where the first LED lights

private val LedGreen = Color(0xFF2BD45C)
private val LedAmber = Color(0xFFF2C230)
private val LedOff = Color(0xFF1C1E23)

/**
 * "Race": a race-wheel display. Shift lights on top, a very large gear in the
 * centre, framed monospace boxes for the numbers a driver glances at.
 */
@Composable
fun RaceDash(state: DashState, u: Dp, modifier: Modifier = Modifier) {
    val t = state.telemetry
    val rpm = state.safeRpm()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(u * 2)) {
        ShiftLights(rpm, state.rpmScale, u, Modifier.fillMaxWidth().height(u * 5))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 2)) {
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2)) {
                DataBox(
                    stringResource(if (state.metricUnits) R.string.speed_kmh else R.string.speed_mph),
                    "%03d".format(state.speed().roundToInt().coerceIn(0, 999)), u * 11, Graphite.Text, u, Modifier.weight(1f),
                )
                DataBox(stringResource(R.string.drive_rpm), "${(rpm / 10).roundToInt() * 10}", u * 8, Graphite.Text, u, Modifier.weight(1f))
            }
            // The gear: the one thing read at a glance mid-corner.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0B0C0E), RoundedCornerShape(6.dp))
                    .border(2.dp, Graphite.Orange, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DashLabel(stringResource(R.string.drive_gear), u, modifier = Modifier.align(Alignment.TopCenter).padding(top = u * 1.5f))
                Text(
                    t.gear, color = Color(0xFFFFE14D), fontFamily = FontFamily.Monospace,
                    fontSize = (u * 30 * gearScale(t.gear)).asSp(), lineHeight = (u * 30).asSp(), fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false,
                )
            }
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2)) {
                val timer = (t.timerLiveSec ?: t.timerLastSec)?.let { "%.2f".format(it) } ?: "-.--"
                if (state.perfTimerEnabled) {
                    DataBox(stringResource(R.string.drive_timer_label), timer, u * 8, LedGreen, u, Modifier.weight(1f))
                } else if (t.hasTurbo) {
                    DataBox(stringResource(R.string.drive_boost), "%.1f".format(t.turbo), u * 8, Graphite.Amber, u, Modifier.weight(1f))
                } else {
                    DataBox(
                        stringResource(R.string.drive_drift), "${kotlin.math.abs(t.driftDeg ?: 0f).roundToInt()}°",
                        u * 8, Graphite.Amber, u, Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 2)) {
                    DataBox(stringResource(R.string.drive_fuel), "${(t.fuel * 100).roundToInt()}", u * 6, Graphite.Text, u, Modifier.weight(1f).fillMaxHeight())
                    DataBox(stringResource(R.string.drive_temp), "${t.engTemp.roundToInt()}", u * 6, Graphite.Text, u, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun DashLabel(text: String, u: Dp, modifier: Modifier) {
    Box(modifier) { DashLabel(text, u) }
}

@Composable
private fun DataBox(label: String, value: String, size: Dp, color: Color, u: Dp, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0C0E), RoundedCornerShape(6.dp))
            .border(1.5.dp, Color(0xFF3A3F49), RoundedCornerShape(6.dp))
            .padding(horizontal = u * 1.8f, vertical = u * 1f),
        verticalArrangement = Arrangement.Center,
    ) {
        DashLabel(label, u)
        Text(
            value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = size.asSp(), lineHeight = size.asSp(), maxLines = 1,
        )
    }
}

/**
 * Twelve LEDs filling from 60% of the redline up to it: 5 green, 4 amber, 3 red.
 * At or above the redline the whole row flashes — shift now.
 */
@Composable
private fun ShiftLights(rpm: Float, scale: RpmScale, u: Dp, modifier: Modifier) {
    val from = scale.redline * LEDS_START_AT
    val fraction = ((rpm - from) / (scale.redline - from)).coerceIn(0f, 1f)
    val lit = (fraction * LED_COUNT).roundToInt()
    val over = rpm >= scale.redline

    val flash by rememberInfiniteTransition(label = "shift").animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "flash",
    )
    Row(
        modifier.alpha(if (over) flash else 1f),
        horizontalArrangement = Arrangement.spacedBy(u * 1.2f, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until LED_COUNT) {
            val on = i < lit
            val color = when {
                !on -> LedOff
                over -> Graphite.Red
                i < 5 -> LedGreen
                i < 9 -> LedAmber
                else -> Graphite.Red
            }
            Box(Modifier.weight(1f).fillMaxHeight(0.62f).background(color, RoundedCornerShape(3.dp)))
        }
    }
}
