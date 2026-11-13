package com.beamng.remotecontrol.ui.dash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Classic": two round instruments like a road car's cluster. A needle tachometer
 * on the left, a speed dial on the right, the gear between them.
 */
@Composable
fun ClassicDash(state: DashState, u: Dp, modifier: Modifier = Modifier) {
    val t = state.telemetry
    Column(modifier, verticalArrangement = Arrangement.spacedBy(u * 2.2f)) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Tachometer(state, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = false))
            Column(
                Modifier.width(u * 15),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t.gear, color = Graphite.Text, fontSize = (u * 15 * gearScale(t.gear)).asSp(),
                    lineHeight = (u * 15).asSp(), fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, softWrap = false,
                )
                DashLabel(stringResource(R.string.drive_gear), u)
                Spacer(Modifier.height(u * 3))
                Text(
                    "%${(t.fuel * 100).roundToInt()}", color = Graphite.Green,
                    fontSize = (u * 3.2f).asSp(), fontWeight = FontWeight.Bold,
                )
                DashLabel(stringResource(R.string.drive_fuel), u)
                Spacer(Modifier.height(u * 1.6f))
                if (t.hasTurbo) {
                    Text("%.1f".format(t.turbo), color = Graphite.Amber, fontSize = (u * 3.2f).asSp(), fontWeight = FontWeight.Bold)
                    DashLabel(stringResource(R.string.drive_boost), u)
                } else {
                    Text("${t.engTemp.roundToInt()}°", color = Graphite.Blue, fontSize = (u * 3.2f).asSp(), fontWeight = FontWeight.Bold)
                    DashLabel(stringResource(R.string.drive_temp), u)
                }
            }
            SpeedDial(state, u, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = false))
        }
        TimerDriftStrip(state, u)
    }
}

private const val DIAL_START = 135f  // degrees; the dial opens downward
private const val DIAL_SWEEP = 270f

private fun DrawScope.dialFace(r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF16181D), Color(0xFF0A0B0D)), center, r), r, center)
    drawCircle(Graphite.Edge, r, center, style = Stroke(r * 0.02f))
    drawCircle(Color.White.copy(alpha = 0.06f), r * 0.97f, center, style = Stroke(r * 0.008f))
}

private fun DrawScope.arc(color: Color, r: Float, from: Float, sweep: Float, width: Float) {
    drawArc(
        color, from, sweep, false,
        Offset(center.x - r, center.y - r), Size(r * 2f, r * 2f),
        style = Stroke(width, cap = StrokeCap.Butt),
    )
}

@Composable
private fun Tachometer(state: DashState, modifier: Modifier) {
    val rpm by animateFloatAsState(state.safeRpm(), spring(stiffness = Spring.StiffnessMediumLow), label = "needle")
    val scale = state.rpmScale
    val label = stringResource(R.string.drive_rpm_x1000)
    Canvas(modifier.padding(4.dpCompat())) {
        val r = size.minDimension / 2f
        dialFace(r)
        val track = r * 0.86f
        val redFrom = DIAL_START + DIAL_SWEEP * (scale.redline / scale.max)
        arc(Graphite.Track, track, DIAL_START, DIAL_SWEEP, r * 0.05f)
        arc(Graphite.Red, track, redFrom, DIAL_START + DIAL_SWEEP - redFrom, r * 0.05f)

        // Ticks and numbers every 1000 rpm.
        val thousands = (scale.max / 1000f).toInt()
        val paint = Paint().apply {
            isAntiAlias = true
            color = Graphite.Text.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = r * 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        for (k in 0..thousands) {
            val a = Math.toRadians((DIAL_START + DIAL_SWEEP * (k * 1000f / scale.max)).toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(Graphite.Text, center + dir * (r * 0.74f), center + dir * (r * 0.82f), r * 0.018f)
            val p = center + dir * (r * 0.60f)
            // Skip every other number when the scale is long, or they collide.
            if (thousands <= 10 || k % 2 == 0) {
                drawContext.canvas.nativeCanvas.drawText("$k", p.x, p.y + paint.textSize * 0.35f, paint)
            }
        }
        paint.color = Graphite.Muted.toArgb()
        paint.textSize = r * 0.085f
        paint.typeface = Typeface.DEFAULT
        drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y + r * 0.55f, paint)

        // Needle.
        val a = Math.toRadians((DIAL_START + DIAL_SWEEP * (rpm / scale.max).coerceIn(0f, 1f)).toDouble())
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(Graphite.Orange, center - dir * (r * 0.12f), center + dir * (r * 0.78f), r * 0.03f, StrokeCap.Round)
        drawCircle(Color(0xFF1D2027), r * 0.11f, center)
        drawCircle(Graphite.Edge, r * 0.11f, center, style = Stroke(r * 0.015f))
    }
}

@Composable
private fun SpeedDial(state: DashState, u: Dp, modifier: Modifier) {
    val speed = state.speed()
    val dialMax = if (state.metricUnits) 280f else 180f
    val shown by animateFloatAsState(
        if (speed.isFinite()) speed.coerceAtLeast(0f) else 0f,
        spring(stiffness = Spring.StiffnessMediumLow), label = "speed",
    )
    Box(modifier.padding(4.dpCompat()), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            dialFace(r)
            val track = r * 0.86f
            arc(Graphite.Track, track, DIAL_START, DIAL_SWEEP, r * 0.05f)
            arc(Graphite.Blue, track, DIAL_START, DIAL_SWEEP * (shown / dialMax).coerceIn(0f, 1f), r * 0.05f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${speed.roundToInt()}", color = Graphite.Text, fontSize = (u * 17).asSp(),
                lineHeight = (u * 17).asSp(), fontWeight = FontWeight.ExtraBold, letterSpacing = (-u * 0.5f).asSp(),
            )
            DashLabel(stringResource(if (state.metricUnits) R.string.speed_kmh else R.string.speed_mph), u)
        }
    }
}

/** A hairline inset so adjacent dials don't touch; not a layout size. */
private fun Int.dpCompat(): Dp = Dp(this.toFloat())
