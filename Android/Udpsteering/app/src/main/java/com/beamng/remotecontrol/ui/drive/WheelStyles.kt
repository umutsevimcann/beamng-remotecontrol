package com.beamng.remotecontrol.ui.drive

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import kotlin.math.cos
import kotlin.math.sin

/**
 * The selectable steering wheels: the kinds you find on sim rigs and in cars.
 * [key] is what gets persisted; adding one means an entry here and a branch in [drawWheel].
 */
enum class WheelStyle(val key: String, @StringRes val nameRes: Int) {
    SPORT("sport", R.string.wheel_sport),
    GT("gt", R.string.wheel_gt),
    FORMULA("formula", R.string.wheel_formula),
    CLASSIC("classic", R.string.wheel_classic),
    DRIFT("drift", R.string.wheel_drift),
    TRUCK("truck", R.string.wheel_truck),
    YOKE("yoke", R.string.wheel_yoke),
    RETRO("retro", R.string.wheel_retro);

    companion object {
        /** Unknown or missing keys (downgrade, future style) fall back to Sport. */
        fun fromKey(key: String?): WheelStyle = entries.firstOrNull { it.key == key } ?: SPORT
    }
}

/** Draws the wheel upright; the caller rotates the layer. */
internal fun DrawScope.drawWheel(style: WheelStyle) = when (style) {
    WheelStyle.SPORT -> drawSportWheel()
    WheelStyle.GT -> drawGtWheel()
    WheelStyle.FORMULA -> drawFormulaWheel()
    WheelStyle.CLASSIC -> drawClassicWheel()
    WheelStyle.DRIFT -> drawDriftWheel()
    WheelStyle.TRUCK -> drawTruckWheel()
    WheelStyle.YOKE -> drawYokeWheel()
    WheelStyle.RETRO -> drawRetroWheel()
}

// ---------- shared parts ----------

private val Carbon = Color(0xFF1B1C20)
private val Rubber = Color(0xFF2B2D32)
private val Shadow = Color(0xFF0B0C0E)
private val Steel = listOf(Color(0xFFDADDE2), Color(0xFF8E929A), Color(0xFF5B5F67))

private fun DrawScope.outer() = size.minDimension / 2f * 0.94f

/** A round rim with a highlight sweeping round it and dark edges either side. */
private fun DrawScope.roundRim(r: Float, width: Float, colors: List<Color>) {
    val mid = r - width / 2f
    drawCircle(Brush.sweepGradient(colors + colors.first(), center), mid, center, style = Stroke(width))
    drawCircle(Color.Black.copy(alpha = 0.7f), r, center, style = Stroke(r * 0.012f))
    drawCircle(Color.Black.copy(alpha = 0.6f), r - width, center, style = Stroke(r * 0.014f))
}

/** A straight spoke from [from] to [to] (distances from the centre) at [degrees]; 0 = right, 90 = down. */
private fun DrawScope.spoke(degrees: Float, from: Float, to: Float, width: Float, brush: Brush, edge: Color = Color(0xFF24262B)) {
    rotate(degrees, center) {
        val topLeft = Offset(center.x + from, center.y - width / 2f)
        val bar = Size(to - from, width)
        drawRoundRect(brush, topLeft, bar, CornerRadius(width * 0.25f))
        drawRoundRect(edge, topLeft, bar, CornerRadius(width * 0.25f), style = Stroke(width * 0.06f))
    }
}

private fun polar(c: Offset, r: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
}

/** The mark at twelve o'clock that makes the wheel's angle readable. */
private fun DrawScope.topMark(r: Float, height: Float, color: Color = Graphite.Orange) {
    drawRoundRect(color, Offset(center.x - r * 0.034f, center.y - r), Size(r * 0.068f, height), CornerRadius(r * 0.016f))
}

private fun DrawScope.plate(x: Float, y: Float, w: Float, h: Float, radius: Float, fill: Color, edge: Color = Color.Black) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(radius))
    drawRoundRect(edge, Offset(x, y), Size(w, h), CornerRadius(radius), style = Stroke(size.minDimension * 0.006f))
}

// ---------- GT: flat-bottomed, suede, thumb buttons ----------

private fun DrawScope.drawGtWheel() {
    val r = outer()
    val c = center
    val rimW = r * 0.20f
    val mid = r - rimW / 2f
    // A circle with the bottom cut flat.
    val rim = Path().apply {
        arcTo(Rect(c.x - mid, c.y - mid, c.x + mid, c.y + mid), 128f, 284f, true)
        close()
    }
    drawPath(rim, Color(0xFF17181B), style = Stroke(rimW, join = StrokeJoin.Round))
    drawPath(rim, Color.White.copy(alpha = 0.07f), style = Stroke(rimW * 0.18f, join = StrokeJoin.Round))
    val carbon = Brush.verticalGradient(listOf(Color(0xFF34363C), Carbon), startY = c.y - r * 0.2f, endY = c.y + r * 0.2f)
    spoke(0f, r * 0.24f, r - rimW * 0.9f, r * 0.24f, carbon)
    spoke(180f, r * 0.24f, r - rimW * 0.9f, r * 0.24f, carbon)
    spoke(90f, r * 0.24f, mid * 0.62f, r * 0.20f, carbon)
    // Thumb buttons on the two side spokes.
    val buttons = listOf(-0.56f to Graphite.Red, -0.42f to Graphite.Amber, 0.42f to Graphite.Green, 0.56f to Graphite.Blue)
    for ((x, color) in buttons) drawCircle(color, r * 0.045f, Offset(c.x + r * x, c.y))
    plate(c.x - r * 0.30f, c.y - r * 0.24f, r * 0.60f, r * 0.48f, r * 0.12f, Color(0xFF101114))
    drawCircle(Graphite.Red, r * 0.10f, c, style = Stroke(r * 0.02f))
    topMark(r, rimW, Graphite.Red)
}

// ---------- Formula: a plate with two grips, a display and a row of shift lights ----------

private fun DrawScope.drawFormulaWheel() {
    val r = outer()
    val c = center
    for (side in listOf(-1f, 1f)) {
        val x = if (side < 0) c.x - r * 0.98f else c.x + r * 0.68f
        plate(x, c.y - r * 0.52f, r * 0.30f, r * 1.10f, r * 0.14f, Rubber)
        // Finger grooves.
        for (i in 0..3) {
            val y = c.y - r * 0.30f + i * r * 0.20f
            drawLine(Shadow, Offset(x + r * 0.05f, y), Offset(x + r * 0.25f, y), r * 0.02f, StrokeCap.Round)
        }
    }
    plate(c.x - r * 0.74f, c.y - r * 0.44f, r * 1.48f, r * 0.88f, r * 0.16f, Carbon)
    // Display and shift lights.
    plate(c.x - r * 0.30f, c.y - r * 0.26f, r * 0.60f, r * 0.30f, r * 0.04f, Color(0xFF07080A), Graphite.Edge)
    val leds = 9
    for (i in 0 until leds) {
        val color = if (i < 4) Graphite.Green else if (i < 7) Graphite.Amber else Graphite.Red
        drawCircle(color, r * 0.022f, Offset(c.x - r * 0.26f + i * r * 0.065f, c.y - r * 0.335f))
    }
    val left = listOf(Graphite.Red, Graphite.Amber, Graphite.Green)
    val right = listOf(Graphite.Blue, Color(0xFFE8EAED), Graphite.Orange)
    for (i in 0..2) {
        drawCircle(left[i], r * 0.060f, Offset(c.x - r * 0.52f, c.y - r * 0.22f + i * r * 0.22f))
        drawCircle(right[i], r * 0.060f, Offset(c.x + r * 0.52f, c.y - r * 0.22f + i * r * 0.22f))
    }
    for (side in listOf(-1f, 1f)) {
        val knob = Offset(c.x + side * r * 0.20f, c.y + r * 0.24f)
        drawCircle(Brush.radialGradient(Steel, knob, r * 0.10f), r * 0.09f, knob)
        drawLine(Shadow, knob, Offset(knob.x, knob.y - r * 0.08f), r * 0.02f, StrokeCap.Round)
    }
    drawRoundRect(Graphite.Orange, Offset(c.x - r * 0.034f, c.y - r * 0.44f), Size(r * 0.068f, r * 0.07f), CornerRadius(r * 0.016f))
}

// ---------- Classic: thin wooden rim, drilled metal spokes ----------

private fun DrawScope.drawClassicWheel() {
    val r = outer()
    val c = center
    val rimW = r * 0.13f
    roundRim(r, rimW, listOf(Color(0xFF7A4A21), Color(0xFFB8803F), Color(0xFF5C3414), Color(0xFF8A5527), Color(0xFFC08A48), Color(0xFF61381A)))
    val metal = Brush.verticalGradient(Steel, startY = c.y - r * 0.08f, endY = c.y + r * 0.08f)
    for (angle in listOf(0f, 180f, 90f)) {
        spoke(angle, r * 0.18f, r - rimW * 0.9f, r * 0.13f, metal, Color(0xFF3A3D43))
        for (i in 0..2) drawCircle(Shadow, r * 0.035f, polar(c, r * (0.34f + i * 0.16f), angle))
    }
    drawCircle(Brush.radialGradient(Steel, c - Offset(r * 0.05f, r * 0.06f), r * 0.3f), r * 0.22f, c)
    drawCircle(Color(0xFF3A3D43), r * 0.22f, c, style = Stroke(r * 0.012f))
    drawCircle(Shadow, r * 0.14f, c)
    drawCircle(Color(0xFFB8803F), r * 0.14f, c, style = Stroke(r * 0.015f))
    topMark(r, rimW)
}

// ---------- Drift: thick coloured suede rim, deep-dish spokes, bolted hub ----------

private fun DrawScope.drawDriftWheel() {
    val r = outer()
    val c = center
    val rimW = r * 0.21f
    roundRim(r, rimW, listOf(Color(0xFF6B2FA8), Color(0xFF9B55E0), Color(0xFF4A1D78), Color(0xFF35145A), Color(0xFF7D3CC4), Color(0xFF431A6E)))
    val black = Brush.verticalGradient(listOf(Color(0xFF2E3035), Color(0xFF121315)), startY = c.y - r * 0.15f, endY = c.y + r * 0.15f)
    for (angle in listOf(0f, 180f, 90f)) {
        spoke(angle, r * 0.26f, r - rimW * 0.9f, r * 0.24f, black)
        rotate(angle, c) {
            drawRoundRect(Shadow, Offset(c.x + r * 0.40f, c.y - r * 0.045f), Size(r * 0.26f, r * 0.09f), CornerRadius(r * 0.045f))
        }
    }
    drawCircle(Color(0xFF15161A), r * 0.32f, c)
    drawCircle(Color.Black, r * 0.32f, c, style = Stroke(r * 0.016f))
    for (i in 0 until 6) drawCircle(Color(0xFFB9BDC4), r * 0.028f, polar(c, r * 0.24f, i * 60f + 30f))
    drawCircle(Color(0xFF9B55E0), r * 0.12f, c, style = Stroke(r * 0.025f))
    topMark(r, rimW, Graphite.Amber)
}

// ---------- Truck: big, thin, four spokes, a broad hub ----------

private fun DrawScope.drawTruckWheel() {
    val r = outer()
    val c = center
    val rimW = r * 0.11f
    roundRim(r, rimW, listOf(Color(0xFF3A3D43), Color(0xFF5A5E66), Color(0xFF26282C), Color(0xFF1D1F22), Color(0xFF474A51), Color(0xFF292B2F)))
    val grey = Brush.verticalGradient(listOf(Color(0xFF4A4D54), Color(0xFF25272B)), startY = c.y - r * 0.1f, endY = c.y + r * 0.1f)
    for (angle in listOf(12f, 168f, 62f, 118f)) spoke(angle, r * 0.20f, r - rimW * 0.9f, r * 0.13f, grey)
    plate(c.x - r * 0.34f, c.y - r * 0.20f, r * 0.68f, r * 0.44f, r * 0.14f, Color(0xFF2A2C31))
    drawCircle(Shadow, r * 0.10f, c + Offset(0f, r * 0.02f))
    drawCircle(Color(0xFF8E929A), r * 0.10f, c + Offset(0f, r * 0.02f), style = Stroke(r * 0.014f))
    topMark(r, rimW)
}

// ---------- Yoke: no rim at all, two grips on a bar ----------

private fun DrawScope.drawYokeWheel() {
    val r = outer()
    val c = center
    plate(c.x - r * 0.84f, c.y - r * 0.02f, r * 1.68f, r * 0.36f, r * 0.16f, Color(0xFF202226))
    for (side in listOf(-1f, 1f)) {
        val x = if (side < 0) c.x - r * 0.98f else c.x + r * 0.70f
        plate(x, c.y - r * 0.58f, r * 0.28f, r * 0.98f, r * 0.14f, Rubber)
        // The stub that turns in at the top of each grip.
        val stubX = if (side < 0) x else x - r * 0.14f
        plate(stubX, c.y - r * 0.58f, r * 0.42f, r * 0.20f, r * 0.10f, Rubber)
    }
    plate(c.x - r * 0.32f, c.y - r * 0.24f, r * 0.64f, r * 0.52f, r * 0.12f, Carbon)
    for (side in listOf(-1f, 1f)) {
        val wheel = Offset(c.x + side * r * 0.50f, c.y + r * 0.16f)
        drawCircle(Brush.radialGradient(Steel, wheel, r * 0.08f), r * 0.07f, wheel)
    }
    drawCircle(Color(0xFF8E929A), r * 0.11f, c + Offset(0f, r * 0.02f), style = Stroke(r * 0.014f))
    drawRoundRect(Graphite.Orange, Offset(c.x - r * 0.034f, c.y - r * 0.24f), Size(r * 0.068f, r * 0.07f), CornerRadius(r * 0.016f))
}

// ---------- Retro: two spokes, a padded hub and a chrome horn ring ----------

private fun DrawScope.drawRetroWheel() {
    val r = outer()
    val c = center
    val rimW = r * 0.14f
    roundRim(r, rimW, listOf(Color(0xFF18191B), Color(0xFF3A3C41), Color(0xFF101113), Color(0xFF0B0C0D), Color(0xFF2A2C30), Color(0xFF131416)))
    val black = Brush.verticalGradient(listOf(Color(0xFF303237), Color(0xFF131416)), startY = c.y - r * 0.1f, endY = c.y + r * 0.2f)
    spoke(8f, r * 0.26f, r - rimW * 0.9f, r * 0.22f, black)
    spoke(172f, r * 0.26f, r - rimW * 0.9f, r * 0.22f, black)
    // Chrome horn ring across the lower half.
    drawArc(
        Brush.horizontalGradient(listOf(Color(0xFF6A6E76), Color(0xFFE2E5EA), Color(0xFF6A6E76)), startX = c.x - r * 0.6f, endX = c.x + r * 0.6f),
        20f, 140f, false, Offset(c.x - r * 0.56f, c.y - r * 0.56f), Size(r * 1.12f, r * 1.12f),
        style = Stroke(r * 0.035f, cap = StrokeCap.Round),
    )
    drawOval(Color(0xFF1B1C1F), Offset(c.x - r * 0.38f, c.y - r * 0.24f), Size(r * 0.76f, r * 0.50f))
    drawOval(Color.Black, Offset(c.x - r * 0.38f, c.y - r * 0.24f), Size(r * 0.76f, r * 0.50f), style = Stroke(r * 0.016f))
    drawOval(Color(0xFFB9BDC4), Offset(c.x - r * 0.16f, c.y - r * 0.10f), Size(r * 0.32f, r * 0.22f), style = Stroke(r * 0.014f))
    topMark(r, rimW)
}
