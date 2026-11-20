package com.beamng.remotecontrol.ui.dash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/*
 * Four layouts drawn as one canvas each, so every element is placed in fractions
 * of the area it gets, whatever its shape. Each is its own idea of a dashboard:
 * Retro (an eighties LCD), Cockpit (aircraft tapes), Drift (the car from above)
 * and Telemetry (a rolling graph).
 */

private const val LOW_FUEL = 0.15f
private const val HOT_ENGINE_C = 110f
private const val BLINK_MS = 110L

/** The typefaces and the one Paint a layout writes with; made once per composition. */
private class Ink {
    val paint = Paint().apply { isAntiAlias = true }
    val regular: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val label: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Text centred vertically on [y]; [x] is the left, centre or right edge according to [align]. */
private fun DrawScope.write(
    ink: Ink, text: String, x: Float, y: Float, px: Float, color: Color,
    face: Typeface = ink.regular, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f,
) {
    val p = ink.paint
    p.typeface = face
    p.textSize = px
    p.color = color.toArgb()
    p.textAlign = align
    p.letterSpacing = spacing
    drawContext.canvas.nativeCanvas.drawText(text, x, y - (p.descent() + p.ascent()) / 2f, p)
}

/** A small spaced-out caption. */
private fun DrawScope.caption(ink: Ink, text: String, x: Float, y: Float, px: Float, align: Paint.Align = Paint.Align.CENTER) =
    write(ink, text, x, y, px, Graphite.Muted, ink.label, align, 0.12f)

private fun DrawScope.ring(color: Color, c: Offset, r: Float, from: Float, sweep: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
    if (sweep <= 0f) return
    drawArc(color, from, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(width, cap = cap))
}

private fun polar(c: Offset, r: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color?, edge: Color? = null, edgeWidth: Float = 2f) {
    if (w <= 0f || h <= 0f) return
    if (fill != null) drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
    if (edge != null) drawRoundRect(edge, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(edgeWidth))
}

/** What the four layouts read, already sanitised: nothing here is NaN or out of range. */
private class Reading(state: DashState, val rpm: Float, val blinkOn: Boolean) {
    private val t = state.telemetry
    val scale = state.rpmScale
    val speed = state.speed().let { if (it.isFinite()) it.coerceIn(0f, 999f) else 0f }
    val gear: String = t.gear
    val fuel = if (t.fuel.isFinite()) t.fuel.coerceIn(0f, 1f) else 0f
    val temp = if (t.engTemp.isFinite()) t.engTemp.coerceIn(-50f, 300f) else 0f
    val turbo = if (t.turbo.isFinite()) t.turbo.coerceIn(-1f, 9f) else 0f
    val hasTurbo = t.hasTurbo
    val throttle = if (t.throttleEcho.isFinite()) t.throttleEcho.coerceIn(0f, 1f) else 0f
    val brake = if (t.brakeEcho.isFinite()) t.brakeEcho.coerceIn(0f, 1f) else 0f
    /** Slip angle with its sign: positive = the car travels to the right of its nose. */
    val driftSigned: Float? = t.driftDeg?.takeIf { it.isFinite() }
    val drift: Float? = driftSigned?.let { abs(it) }
    /** Seconds of the 0-100 run to show: the live run, else the last one, else the best. */
    val timer: Float? = if (state.perfTimerEnabled) (t.timerLiveSec ?: t.timerLastSec ?: t.timerBestSec)?.takeIf { it.isFinite() } else null
    val overRev = rpm >= scale.redline
    // Before the first packet fuel reads 0: that is "no data", not an empty tank.
    val lowFuel = t.connected && fuel < LOW_FUEL
    val hot = temp > HOT_ENGINE_C
    val rpmFraction = (rpm / scale.max).coerceIn(0f, 1f)
    val redFraction = (scale.redline / scale.max).coerceIn(0f, 1f)
    /** 0..1 across the temperature gauge, 50 °C to 130 °C. */
    val tempFraction = ((temp - 50f) / 80f).coerceIn(0f, 1f)
    val rpmText = "${(rpm / 10f).roundToInt() * 10}"
    val speedText = "${speed.roundToInt()}"
}

/** Strings a canvas layout needs, read while still in composition. */
private class Words(
    val unit: String, val gear: String, val rpm: String, val rpmX1000: String, val fuel: String,
    val temp: String, val boost: String, val drift: String, val timer: String, val gas: String, val brake: String,
)

@Composable
private fun words(state: DashState) = Words(
    unit = stringResource(if (state.metricUnits) R.string.speed_kmh else R.string.speed_mph),
    gear = stringResource(R.string.drive_gear), rpm = stringResource(R.string.drive_rpm),
    rpmX1000 = stringResource(R.string.drive_rpm_x1000), fuel = stringResource(R.string.drive_fuel),
    temp = stringResource(R.string.drive_temp), boost = stringResource(R.string.drive_boost),
    drift = stringResource(R.string.drive_drift), timer = stringResource(R.string.drive_timer_label),
    gas = stringResource(R.string.drive_gas), brake = stringResource(R.string.drive_brake),
)

/** Everything the four layouts have in common: the smoothed needle, the blink clock, the pen. */
@Composable
private fun DashCanvas(state: DashState, modifier: Modifier, draw: DrawScope.(Reading, Words, Ink) -> Unit) {
    val rpm by animateFloatAsState(state.safeRpm(), spring(stiffness = Spring.StiffnessMediumLow), label = "rpm")
    val blinkOn by produceState(true) {
        while (true) {
            delay(BLINK_MS)
            value = !value
        }
    }
    val ink = remember { Ink() }
    val w = words(state)
    Canvas(modifier) { draw(Reading(state, rpm, blinkOn), w, ink) }
}

/** The one optional readout: the drift angle while sliding, else the 0-100 run, else boost. */
private fun extraReadout(v: Reading, words: Words): Triple<String, String, Color>? = when {
    v.drift != null -> Triple(words.drift, "${v.drift.roundToInt()}°", Graphite.Amber)
    v.timer != null -> Triple(words.timer, "%.2f".format(v.timer), Graphite.Green)
    v.hasTurbo -> Triple(words.boost, "%.1f".format(v.turbo), Graphite.Amber)
    else -> null
}

// ==================== Retro ====================

/** Seven-segment patterns for 0-9, bits a b c d e f g from the top bit down. */
private val SEGMENTS = intArrayOf(0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011)

/** One LCD digit; unlit segments stay faintly visible, as on a real display. Null = blank. */
private fun DrawScope.sevenSegment(digit: Int?, x: Float, y: Float, w: Float, h: Float, color: Color) {
    val t = w * 0.17f
    val bits = digit?.let { SEGMENTS[it.coerceIn(0, 9)] } ?: 0
    val half = h / 2f
    fun segment(index: Int, sx: Float, sy: Float, sw: Float, sh: Float) {
        val on = (bits shr (6 - index)) and 1 == 1
        drawRoundRect(if (on) color else color.copy(alpha = 0.07f), Offset(sx, sy), Size(sw, sh), CornerRadius(t * 0.4f))
    }
    segment(0, x + t, y, w - 2 * t, t)
    segment(1, x + w - t, y + t * 0.6f, t, half - t * 1.1f)
    segment(2, x + w - t, y + half + t * 0.5f, t, half - t * 1.1f)
    segment(3, x + t, y + h - t, w - 2 * t, t)
    segment(4, x, y + half + t * 0.5f, t, half - t * 1.1f)
    segment(5, x, y + t * 0.6f, t, half - t * 1.1f)
    segment(6, x + t, y + half - t / 2f, w - 2 * t, t)
}

/** [value] right-aligned at [right] in [digits] LCD digits, leading zeros blank. */
private fun DrawScope.sevenSegmentNumber(value: Int, digits: Int, right: Float, y: Float, dw: Float, dh: Float, color: Color) {
    var rest = value.coerceAtLeast(0)
    for (i in 0 until digits) {
        val x = right - dw - i * dw * 1.24f
        sevenSegment(if (i == 0 || rest > 0) rest % 10 else null, x, y, dw, dh, color)
        rest /= 10
    }
}

/** Ten blocks, lit from the left. */
private fun DrawScope.blockBar(x: Float, y: Float, w: Float, h: Float, fraction: Float, color: Color) {
    val n = 10
    val bw = w / n
    for (i in 0 until n) {
        val on = (i + 0.5f) / n <= fraction.coerceIn(0f, 1f) || (i == 0 && fraction > 0f)
        drawRect(if (on) color else color.copy(alpha = 0.10f), Offset(x + i * bw + bw * 0.12f, y), Size(bw * 0.76f, h))
    }
}

/** "Retro": an eighties digital dash — a climbing bar-graph tachometer and LCD digits. */
@Composable
fun RetroDash(state: DashState, modifier: Modifier = Modifier) = DashCanvas(state, modifier) { v, words, ink ->
    val w = size.width
    val h = size.height
    val main = Graphite.Green
    // Tachometer: bars that get taller towards the redline.
    val bars = 40
    val x0 = w * 0.04f
    val barW = (w * 0.92f) / bars
    val floor = h * 0.38f
    val tallest = h * 0.31f
    for (i in 0 until bars) {
        val p = (i + 0.5f) / bars
        val height = tallest * (0.22f + 0.78f * Math.pow(p.toDouble(), 1.5).toFloat())
        val lit = p <= v.rpmFraction
        val base = when {
            p >= v.redFraction -> Graphite.Red
            p >= v.redFraction - 0.12f -> Graphite.Amber
            else -> main
        }
        val shown = if (lit && !(v.overRev && !v.blinkOn)) base else base.copy(alpha = 0.12f)
        drawRect(shown, Offset(x0 + i * barW + barW * 0.14f, floor - height), Size(barW * 0.72f, height))
    }
    val thousands = (v.scale.max / 1000f).toInt()
    for (k in 0..thousands) {
        if (thousands > 10 && k % 2 != 0) continue
        write(ink, "$k", x0 + w * 0.92f * (k * 1000f / v.scale.max), h * 0.435f, h * 0.04f, if (k * 1000f >= v.scale.redline) Graphite.Red else Graphite.Muted)
    }
    caption(ink, words.rpmX1000, w * 0.96f, h * 0.05f, h * 0.03f, Paint.Align.RIGHT)

    // Speed in LCD digits, the gear in its own window.
    val dh = min(h * 0.34f, w * 0.20f)
    val dw = dh * 0.52f
    val digitsRight = w * 0.58f
    sevenSegmentNumber(v.speed.roundToInt(), 3, digitsRight, h * 0.51f, dw, dh, main)
    caption(ink, words.unit, digitsRight + w * 0.02f, h * 0.51f + dh - h * 0.02f, h * 0.034f, Paint.Align.LEFT)
    val gx = w * 0.72f
    val gw = w * 0.22f
    box(gx, h * 0.49f, gw, dh + h * 0.04f, h * 0.015f, null, Graphite.Amber.copy(alpha = 0.5f), h * 0.005f)
    caption(ink, words.gear, gx + gw / 2f, h * 0.49f - h * 0.035f, h * 0.03f)
    val gearDigit = v.gear.toIntOrNull()
    val gearColor = if (v.overRev) Graphite.Red else Graphite.Amber
    if (gearDigit != null && gearDigit in 0..9) {
        sevenSegment(gearDigit, gx + gw / 2f - dw * 0.42f, h * 0.51f + dh * 0.08f, dw * 0.84f, dh * 0.84f, gearColor)
    } else {
        write(ink, v.gear, gx + gw / 2f, h * 0.51f + dh / 2f, dh * 0.70f * gearScale(v.gear), gearColor, ink.bold)
    }

    // Fuel and temperature as block bars along the bottom.
    val by = h * 0.92f
    val bh = h * 0.04f
    caption(ink, words.fuel, w * 0.04f, by - h * 0.035f, h * 0.028f, Paint.Align.LEFT)
    blockBar(w * 0.04f, by, w * 0.40f, bh, v.fuel, if (v.lowFuel) (if (v.blinkOn) Graphite.Red else Graphite.Track) else main)
    caption(ink, words.temp, w * 0.96f, by - h * 0.035f, h * 0.028f, Paint.Align.RIGHT)
    blockBar(w * 0.56f, by, w * 0.40f, bh, v.tempFraction, if (v.hot) (if (v.blinkOn) Graphite.Red else Graphite.Track) else Graphite.Amber)
}

// ==================== Cockpit ====================

/**
 * A vertical instrument tape: the scale slides behind a fixed window that shows
 * the current value. [unitsVisible] is how much of the scale fits in the tape.
 */
private fun DrawScope.tape(
    ink: Ink, x: Float, y: Float, w: Float, h: Float, value: Float, unitsVisible: Float,
    tickEvery: Float, labelEvery: Int, redFrom: Float?, pointerText: String, labelsOnRight: Boolean,
) {
    box(x, y, w, h, w * 0.08f, Graphite.Inset, Graphite.Edge, w * 0.02f)
    val mid = y + h / 2f
    val perUnit = h / unitsVisible
    clipRect(x, y, x + w, y + h) {
        val first = (((value - unitsVisible / 2f) / tickEvery).toInt() - 1).coerceAtLeast(0)
        val last = ((value + unitsVisible / 2f) / tickEvery).toInt() + 1
        for (i in first..last) {
            val at = i * tickEvery
            val ty = mid + (value - at) * perUnit
            val major = i % labelEvery == 0
            val color = if (redFrom != null && at >= redFrom) Graphite.Red else Graphite.Text
            val edge = if (labelsOnRight) x else x + w
            val inward = if (labelsOnRight) 1f else -1f
            drawLine(color, Offset(edge, ty), Offset(edge + inward * w * (if (major) 0.26f else 0.14f), ty), w * 0.02f)
            if (major) {
                write(
                    ink, "${at.roundToInt()}", edge + inward * w * 0.34f, ty, w * 0.20f, color,
                    align = if (labelsOnRight) Paint.Align.LEFT else Paint.Align.RIGHT,
                )
            }
        }
    }
    val ph = h * 0.13f
    box(x - w * 0.04f, mid - ph / 2f, w * 1.08f, ph, w * 0.08f, Graphite.PanelTop, Graphite.Orange, w * 0.03f)
    write(ink, pointerText, x + w / 2f, mid, ph * 0.62f, Graphite.Text, ink.bold)
}

/** "Cockpit": aircraft instruments — a speed tape, a rev tape and a slip scale across the top. */
@Composable
fun CockpitDash(state: DashState, modifier: Modifier = Modifier) = DashCanvas(state, modifier) { v, words, ink ->
    val w = size.width
    val h = size.height
    val tapeW = min(w * 0.19f, h * 0.36f)
    val top = h * 0.20f
    val tapeH = h * 0.74f
    caption(ink, words.unit, w * 0.03f + tapeW / 2f, h * 0.13f, h * 0.032f)
    tape(ink, w * 0.03f, top, tapeW, tapeH, v.speed, 110f, 10f, 2, null, v.speedText, labelsOnRight = false)
    caption(ink, words.rpm + " ×100", w * 0.97f - tapeW / 2f, h * 0.13f, h * 0.032f)
    tape(ink, w * 0.97f - tapeW, top, tapeW, tapeH, v.rpm / 100f, 60f, 5f, 2, v.scale.redline / 100f, "${(v.rpm / 100f).roundToInt()}", labelsOnRight = true)

    // Slip scale: fixed marks from -60° to 60°, a caret that slides with the slide.
    val cx = w / 2f
    val scaleW = w - (tapeW + w * 0.06f) * 2f - w * 0.06f
    val sx = cx - scaleW / 2f
    val sy = h * 0.11f
    drawLine(Graphite.Muted, Offset(sx, sy), Offset(sx + scaleW, sy), h * 0.004f)
    for (deg in -60..60 step 10) {
        val tx = cx + scaleW / 2f * (deg / 60f)
        val major = deg % 30 == 0
        drawLine(Graphite.Text, Offset(tx, sy), Offset(tx, sy - h * (if (major) 0.045f else 0.025f)), h * 0.004f)
    }
    caption(ink, words.drift, cx, h * 0.035f, h * 0.028f)
    val slip = (v.driftSigned ?: 0f).coerceIn(-60f, 60f)
    val caretX = cx + scaleW / 2f * (slip / 60f)
    val caret = Path().apply {
        moveTo(caretX, sy + h * 0.01f)
        lineTo(caretX + h * 0.03f, sy + h * 0.06f)
        lineTo(caretX - h * 0.03f, sy + h * 0.06f)
        close()
    }
    drawPath(caret, if (v.drift != null) Graphite.Amber else Graphite.Muted)

    // Centre column: gear, then fuel and temperature, then whatever extra there is.
    caption(ink, words.gear, cx, h * 0.25f, h * 0.032f)
    write(ink, v.gear, cx, h * 0.43f, min(h * 0.30f, scaleW * 0.55f) * gearScale(v.gear), if (v.overRev) Graphite.Red else Graphite.Orange, ink.bold)
    val barW = scaleW * 0.80f
    val barX = cx - barW / 2f
    val thick = h * 0.022f
    fun meter(y: Float, label: String, value: String, fraction: Float, color: Color) {
        caption(ink, label, barX, y - h * 0.038f, h * 0.028f, Paint.Align.LEFT)
        write(ink, value, barX + barW, y - h * 0.038f, h * 0.04f, Graphite.Text, align = Paint.Align.RIGHT)
        box(barX, y, barW, thick, thick / 2f, Graphite.Track)
        box(barX, y, barW * fraction.coerceIn(0f, 1f), thick, thick / 2f, color)
    }
    meter(h * 0.68f, words.fuel, "${(v.fuel * 100).roundToInt()}%", v.fuel, if (v.lowFuel) (if (v.blinkOn) Graphite.Red else Graphite.Track) else Graphite.Green)
    meter(h * 0.80f, words.temp, "${v.temp.roundToInt()}°", v.tempFraction, if (v.hot) (if (v.blinkOn) Graphite.Red else Graphite.Track) else Graphite.Blue)
    val extra = if (v.timer != null) Triple(words.timer, "%.2f".format(v.timer), Graphite.Green)
    else if (v.hasTurbo) Triple(words.boost, "%.1f".format(v.turbo), Graphite.Amber) else null
    if (extra != null) write(ink, "${extra.first}  ${extra.second}", cx, h * 0.92f, h * 0.045f, extra.third)
}

// ==================== Drift ====================

/** "Drift": the car seen from above, turning with the slide over a fan of angles. */
@Composable
fun DriftDash(state: DashState, modifier: Modifier = Modifier) = DashCanvas(state, modifier) { v, words, ink ->
    val w = size.width
    val h = size.height
    // Revs: a block strip across the top.
    val blocks = 30
    val bw = (w * 0.92f) / blocks
    for (i in 0 until blocks) {
        val p = (i + 0.5f) / blocks
        val base = if (p >= v.redFraction) Graphite.Red else if (p >= v.redFraction - 0.12f) Graphite.Amber else Graphite.Text
        val lit = p <= v.rpmFraction && !(v.overRev && !v.blinkOn)
        drawRect(if (lit) base else base.copy(alpha = 0.12f), Offset(w * 0.04f + i * bw + bw * 0.12f, h * 0.04f), Size(bw * 0.76f, h * 0.05f))
    }

    // Pedals: brake up the left edge, throttle up the right.
    val pedalW = w * 0.045f
    val pedalTop = h * 0.16f
    val pedalH = h * 0.78f
    box(w * 0.04f, pedalTop, pedalW, pedalH, pedalW / 2f, Graphite.Track)
    if (v.brake > 0.01f) box(w * 0.04f, pedalTop + pedalH * (1f - v.brake), pedalW, pedalH * v.brake, pedalW / 2f, Graphite.Red)
    box(w * 0.96f - pedalW, pedalTop, pedalW, pedalH, pedalW / 2f, Graphite.Track)
    if (v.throttle > 0.01f) box(w * 0.96f - pedalW, pedalTop + pedalH * (1f - v.throttle), pedalW, pedalH * v.throttle, pedalW / 2f, Graphite.Green)

    // The fan: 60° either side of straight ahead, which is up.
    val pivot = Offset(w / 2f, h * 0.84f)
    val reach = min(w * 0.34f, h * 0.62f)
    ring(Graphite.Track, pivot, reach, 210f, 120f, reach * 0.02f)
    for (deg in -60..60 step 10) {
        val major = deg % 30 == 0
        drawLine(Graphite.Text, polar(pivot, reach, 270f + deg), polar(pivot, reach * (if (major) 0.90f else 0.95f), 270f + deg), reach * (if (major) 0.014f else 0.008f))
        if (major) {
            val at = polar(pivot, reach * 1.09f, 270f + deg)
            write(ink, "${abs(deg)}", at.x, at.y, reach * 0.085f, Graphite.Muted)
        }
    }
    val slip = (v.driftSigned ?: 0f).coerceIn(-60f, 60f)
    if (slip != 0f) ring(Graphite.Amber, pivot, reach, 270f + min(0f, slip), abs(slip), reach * 0.045f)
    // Where the car is going: straight up. Where it points: turned against the slide.
    drawLine(Graphite.Muted, polar(pivot, reach * 0.30f, 270f), polar(pivot, reach * 0.86f, 270f), reach * 0.008f)
    val carC = polar(pivot, reach * 0.55f, 270f)
    rotate(-slip, carC) {
        val cw = reach * 0.20f
        val ch = reach * 0.44f
        val tyre = Size(cw * 0.22f, ch * 0.22f)
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-0.30f, 0.30f)) {
            drawRoundRect(Graphite.Text, Offset(carC.x + sx * cw * 0.56f - tyre.width / 2f, carC.y + sy * ch - tyre.height / 2f), tyre, CornerRadius(tyre.width * 0.3f))
        }
        box(carC.x - cw / 2f, carC.y - ch / 2f, cw, ch, cw * 0.28f, Graphite.PanelTop, if (v.drift != null) Graphite.Amber else Graphite.Text, reach * 0.012f)
        box(carC.x - cw * 0.34f, carC.y - ch * 0.26f, cw * 0.68f, ch * 0.18f, cw * 0.08f, Graphite.Blue.copy(alpha = 0.55f))
    }
    write(ink, "${(v.drift ?: 0f).roundToInt()}°", pivot.x, h * 0.90f, h * 0.13f, if (v.drift != null) Graphite.Amber else Graphite.Muted, ink.bold)

    // Speed and gear in the two upper corners, inside the pedal bars.
    val leftX = w * 0.04f + pedalW + w * 0.03f
    val rightX = w * 0.96f - pedalW - w * 0.03f
    write(ink, v.speedText, leftX, h * 0.25f, h * 0.15f, Graphite.Text, ink.light, Paint.Align.LEFT)
    caption(ink, words.unit, leftX, h * 0.36f, h * 0.03f, Paint.Align.LEFT)
    write(ink, v.gear, rightX, h * 0.25f, h * 0.15f * gearScale(v.gear), if (v.overRev) Graphite.Red else Graphite.Orange, ink.bold, Paint.Align.RIGHT)
    caption(ink, words.gear, rightX, h * 0.36f, h * 0.03f, Paint.Align.RIGHT)
}

// ==================== Telemetry ====================

/** The last few seconds of the drive, oldest first when read through [at]. */
private class History(val capacity: Int) {
    val rpm = FloatArray(capacity)
    val speed = FloatArray(capacity)
    val throttle = FloatArray(capacity)
    val brake = FloatArray(capacity)
    private var head = 0
    var count = 0
        private set

    fun push(rpmF: Float, speedF: Float, thr: Float, brk: Float) {
        if (count == 0) {
            // Start as a flat line at the present values, not as a ramp up from zero.
            rpm.fill(rpmF); speed.fill(speedF); throttle.fill(thr); brake.fill(brk)
            count = capacity
        }
        rpm[head] = rpmF; speed[head] = speedF; throttle[head] = thr; brake[head] = brk
        head = (head + 1) % capacity
    }

    /** Index into the arrays of the [i]-th oldest sample. */
    fun at(i: Int) = (head + i) % capacity
}

private const val HISTORY_SAMPLES = 160
private const val HISTORY_PERIOD_MS = 60L // ~10 seconds across the graph

/** "Telemetry": a rolling graph of revs, speed and both pedals, like a data logger. */
@Composable
fun TelemetryDash(state: DashState, modifier: Modifier = Modifier) {
    val dialMax = if (state.metricUnits) 280f else 180f
    val history = remember { History(HISTORY_SAMPLES) }
    val current by rememberUpdatedState(state)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
        while (true) {
            val s = current
            history.push(
                unit(s.safeRpm() / s.rpmScale.max), unit(s.speed() / dialMax),
                unit(s.telemetry.throttleEcho), unit(s.telemetry.brakeEcho),
            )
            tick++
            delay(HISTORY_PERIOD_MS)
        }
    }
    DashCanvas(state, modifier) { v, words, ink ->
        @Suppress("UNUSED_EXPRESSION") tick // redraw on every new sample
        val w = size.width
        val h = size.height
        val gx = w * 0.03f
        val gw = w * 0.66f
        val top = h * 0.08f
        val upperH = h * 0.54f
        val lowerTop = top + upperH + h * 0.07f
        val lowerH = h * 0.23f
        box(gx, top, gw, upperH, h * 0.015f, Graphite.Inset, Graphite.Edge, h * 0.004f)
        box(gx, lowerTop, gw, lowerH, h * 0.015f, Graphite.Inset, Graphite.Edge, h * 0.004f)
        for (i in 1..3) drawLine(Graphite.Edge, Offset(gx, top + upperH * i / 4f), Offset(gx + gw, top + upperH * i / 4f), h * 0.002f)
        // The redline, as a level on the rev trace.
        val redY = top + upperH * (1f - v.redFraction)
        drawLine(Graphite.Red.copy(alpha = 0.6f), Offset(gx, redY), Offset(gx + gw, redY), h * 0.003f)

        fun trace(values: FloatArray, y0: Float, height: Float, color: Color, fill: Boolean) {
            if (history.count == 0) return
            val path = Path()
            for (i in 0 until history.capacity) {
                val px = gx + gw * i / (history.capacity - 1f)
                val py = y0 + height * (1f - values[history.at(i)] * 0.94f - 0.03f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (fill) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(gx + gw, y0 + height)
                    lineTo(gx, y0 + height)
                    close()
                }
                drawPath(area, color.copy(alpha = 0.22f))
            }
            drawPath(path, color, style = Stroke(h * 0.007f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        clipRect(gx, top, gx + gw, top + upperH) {
            trace(history.speed, top, upperH, Graphite.Text, fill = false)
            trace(history.rpm, top, upperH, Graphite.Orange, fill = false)
        }
        clipRect(gx, lowerTop, gx + gw, lowerTop + lowerH) {
            trace(history.throttle, lowerTop, lowerH, Graphite.Green, fill = true)
            trace(history.brake, lowerTop, lowerH, Graphite.Red, fill = true)
        }
        write(ink, words.rpm, gx + gw * 0.02f, top - h * 0.035f, h * 0.028f, Graphite.Orange, ink.label, Paint.Align.LEFT, 0.12f)
        write(ink, words.unit, gx + gw * 0.20f, top - h * 0.035f, h * 0.028f, Graphite.Text, ink.label, Paint.Align.LEFT, 0.12f)
        write(ink, words.gas, gx + gw * 0.02f, lowerTop - h * 0.032f, h * 0.028f, Graphite.Green, ink.label, Paint.Align.LEFT, 0.12f)
        write(ink, words.brake, gx + gw * 0.20f, lowerTop - h * 0.032f, h * 0.028f, Graphite.Red, ink.label, Paint.Align.LEFT, 0.12f)

        // The present, in a column on the right.
        val cx = gx + gw + (w - gx - gw) / 2f
        val col = w - gx - gw
        write(ink, v.speedText, cx, h * 0.20f, min(h * 0.22f, col * 0.48f), Graphite.Text, ink.light)
        caption(ink, words.unit, cx, h * 0.33f, h * 0.03f)
        write(ink, v.gear, cx, h * 0.52f, min(h * 0.22f, col * 0.50f) * gearScale(v.gear), if (v.overRev) Graphite.Red else Graphite.Orange, ink.bold)
        caption(ink, words.gear, cx, h * 0.65f, h * 0.03f)
        write(ink, v.rpmText, cx, h * 0.79f, min(h * 0.10f, col * 0.26f), if (v.overRev) Graphite.Red else Graphite.Text)
        caption(ink, words.rpm, cx, h * 0.87f, h * 0.03f)
        val warn = if (v.lowFuel) "${words.fuel} ${(v.fuel * 100).roundToInt()}%" else if (v.hot) "${words.temp} ${v.temp.roundToInt()}°" else null
        if (warn != null && v.blinkOn) write(ink, warn, cx, h * 0.95f, h * 0.036f, Graphite.Red, ink.label)
    }
}
