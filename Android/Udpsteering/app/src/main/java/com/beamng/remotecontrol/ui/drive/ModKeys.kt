package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.ui.TelemetryUiState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The extra controls the RemoteControlPro mod makes possible. Null on the drive
 * screen means "connected to the stock game": none of these exist there.
 */
data class ModControls(
    val showShift: Boolean,
    val showHandbrake: Boolean,
    val showHorn: Boolean,
    val showLights: Boolean,
    /** Drive mode, 4WD, nitrous, doors: a second page of the key row (mod 2.1+). */
    val showVehicle: Boolean,
    /** ModProtocol.FEATURE_* the car has; null = the mod did not say, assume all. */
    val features: Int?,
    val handbrakeOn: Boolean,
    /** One of the ModProtocol.BUTTON_* bits went down or up. */
    val onButton: (bit: Int, pressed: Boolean) -> Unit,
    val onHandbrake: (Boolean) -> Unit,
)

/** The symbols on the keys: the ones a car's own switches carry, so no language is needed. */
enum class KeyGlyph { HEADLIGHTS, FOG, SIGNAL_LEFT, SIGNAL_RIGHT, HAZARD, RECOVER, HANDBRAKE, HORN, SHIFT_UP, SHIFT_DOWN, MORE }

/**
 * A key that is "down" for as long as it is held. Releases on lift and on a cancelled
 * gesture. [lit] lights it from outside too (a turn signal that is blinking in the game).
 */
@Composable
fun HoldKey(
    u: Dp,
    modifier: Modifier,
    accent: Color = Graphite.Orange,
    lit: Boolean = false,
    onPress: (Boolean) -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    KeyFace(pressed || lit, pressed, accent, u, modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            try {
                pressed = true
                currentOnPress(true)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            } finally {
                pressed = false
                currentOnPress(false)
            }
        }
    }, content)
}

/** A key that latches: one tap on, one tap off (the handbrake). */
@Composable
fun LatchKey(
    on: Boolean,
    u: Dp,
    modifier: Modifier,
    accent: Color = Graphite.Red,
    onChange: (Boolean) -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOn by rememberUpdatedState(on)
    KeyFace(on, on, accent, u, modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnChange(!currentOn)
        }
    }, content)
}

@Composable
private fun KeyFace(lit: Boolean, sunk: Boolean, accent: Color, u: Dp, modifier: Modifier, content: @Composable (Color) -> Unit) {
    val shape = RoundedCornerShape(u * 2.4f)
    // A raised cap: lighter on top; pressed, the gradient flips and it reads as pushed in.
    val colours = if (sunk) listOf(Graphite.PanelBottom, Graphite.Selected) else listOf(Graphite.PanelTop, Graphite.PanelBottom)
    Box(
        modifier
            .background(Brush.verticalGradient(colours), shape)
            .border(if (lit) 2.dp else 1.dp, if (lit) accent else Graphite.Edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        content(if (lit) accent else Graphite.Text)
    }
}

/** A glyph with an optional caption beside it. */
@Composable
fun KeyLabel(glyph: KeyGlyph, tint: Color, u: Dp, glyphSize: Dp, caption: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(glyphSize)) { drawGlyph(glyph, tint) }
        if (caption != null) {
            Spacer(Modifier.width(u * 1.4f))
            Text(
                caption, color = tint, fontSize = (u * 2.9f).asSp(), fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/**
 * The key row under the gauges. Page 0: lights, signals and recover — the signal
 * keys light up with the car's own indicators, so the row doubles as a repeater.
 * Page 1: the car's optional systems, only those this car has. The last, narrow
 * key flips between the two.
 */
@Composable
fun ModKeyRow(mod: ModControls, telemetry: TelemetryUiState, page: Int, onPage: (Int) -> Unit, u: Dp, modifier: Modifier) {
    val twoPages = mod.showLights && mod.showVehicle
    val vehiclePage = mod.showVehicle && (page == 1 || !mod.showLights)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 1.6f)) {
        if (vehiclePage) VehicleKeys(mod, u) else LightKeys(mod, telemetry, u)
        if (twoPages) {
            val currentOnPage by rememberUpdatedState(onPage)
            val currentPage by rememberUpdatedState(page)
            HoldKey(u, Modifier.weight(0.62f).fillMaxHeight(), lit = vehiclePage, onPress = { pressed ->
                if (pressed) currentOnPage(1 - currentPage)
            }) { tint -> KeyLabel(KeyGlyph.MORE, tint, u, u * 5.4f) }
        }
    }
}

@Composable
private fun RowScope.LightKeys(mod: ModControls, telemetry: TelemetryUiState, u: Dp) {
    val left = telemetry.lights[Receivepacket.INDEX_SIGNAL_L]
    val right = telemetry.lights[Receivepacket.INDEX_SIGNAL_R]
    val keys = listOf(
        Triple(KeyGlyph.HEADLIGHTS, ModProtocol.BUTTON_HEADLIGHTS, false),
        Triple(KeyGlyph.FOG, ModProtocol.BUTTON_FOG, false),
        Triple(KeyGlyph.SIGNAL_LEFT, ModProtocol.BUTTON_SIGNAL_LEFT, left && !right),
        Triple(KeyGlyph.HAZARD, ModProtocol.BUTTON_HAZARD, left && right),
        Triple(KeyGlyph.SIGNAL_RIGHT, ModProtocol.BUTTON_SIGNAL_RIGHT, right && !left),
        Triple(KeyGlyph.RECOVER, ModProtocol.BUTTON_RECOVER, false),
    )
    for ((glyph, bit, lit) in keys) {
        val accent = when (glyph) {
            KeyGlyph.SIGNAL_LEFT, KeyGlyph.SIGNAL_RIGHT -> Graphite.Green
            KeyGlyph.HAZARD -> Graphite.Red
            KeyGlyph.HEADLIGHTS, KeyGlyph.FOG -> Graphite.Blue
            else -> Graphite.Orange
        }
        HoldKey(u, Modifier.weight(1f).fillMaxHeight(), accent, lit, { pressed -> mod.onButton(bit, pressed) }) { tint ->
            KeyLabel(glyph, tint, u, u * 6.4f)
        }
    }
}

/**
 * ESC / 4WD / LO / DIFF / N₂O are the legends a car's own switches carry, so they
 * are not translated. Each is one press of the game's action; the game cycles or
 * toggles, and shows the new state in its own message.
 */
@Composable
private fun RowScope.VehicleKeys(mod: ModControls, u: Dp) {
    fun has(feature: Int) = mod.features == null || mod.features and feature != 0
    val keys = buildList {
        if (has(ModProtocol.FEATURE_DRIVE_MODE)) add("ESC" to ModProtocol.BUTTON_DRIVE_MODE)
        if (has(ModProtocol.FEATURE_4WD)) {
            add("4WD" to ModProtocol.BUTTON_4WD)
            add("LO" to ModProtocol.BUTTON_LOW_RANGE)
            add("DIFF" to ModProtocol.BUTTON_DIFF)
        }
        if (has(ModProtocol.FEATURE_NITROUS)) add("N₂O" to ModProtocol.BUTTON_NITROUS)
    }
    for ((label, bit) in keys) {
        val accent = if (bit == ModProtocol.BUTTON_NITROUS) Graphite.Red else Graphite.Orange
        HoldKey(u, Modifier.weight(1f).fillMaxHeight(), accent, onPress = { pressed -> mod.onButton(bit, pressed) }) { tint ->
            KeyText(label, tint, u)
        }
    }
    // Doors: the game has separate "open" and "close" actions; one key alternates.
    var doorsOpen by remember { mutableStateOf(false) }
    HoldKey(u, Modifier.weight(1.5f).fillMaxHeight(), Graphite.Blue, lit = doorsOpen, onPress = { pressed ->
        val bit = if (doorsOpen) ModProtocol.BUTTON_DOORS_CLOSE else ModProtocol.BUTTON_DOORS_OPEN
        mod.onButton(bit, pressed)
        if (!pressed) doorsOpen = !doorsOpen
    }) { tint -> KeyText(stringResource(R.string.mod_key_doors), tint, u) }
}

@Composable
private fun KeyText(label: String, tint: Color, u: Dp) {
    Text(label, color = tint, fontSize = (u * 3.1f).asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
}

/** Horn (hold) and handbrake (latch) side by side: the two keys the steering hand reaches. */
@Composable
fun ModWheelKeys(mod: ModControls, u: Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 2)) {
        if (mod.showHorn) {
            HoldKey(u, Modifier.weight(1f).fillMaxHeight(), onPress = { pressed -> mod.onButton(ModProtocol.BUTTON_HORN, pressed) }) { tint ->
                KeyLabel(KeyGlyph.HORN, tint, u, u * 6f, stringResource(R.string.mod_key_horn))
            }
        }
        if (mod.showHandbrake) {
            LatchKey(mod.handbrakeOn, u, Modifier.weight(1f).fillMaxHeight(), onChange = mod.onHandbrake) { tint ->
                KeyLabel(KeyGlyph.HANDBRAKE, tint, u, u * 6f, stringResource(R.string.mod_key_handbrake))
            }
        }
    }
}

/** One shift paddle: big enough to hit without looking. */
@Composable
fun ShiftKey(up: Boolean, mod: ModControls, u: Dp, modifier: Modifier) {
    val bit = if (up) ModProtocol.BUTTON_SHIFT_UP else ModProtocol.BUTTON_SHIFT_DOWN
    HoldKey(u, modifier, Graphite.Blue, onPress = { pressed -> mod.onButton(bit, pressed) }) { _ ->
        // Always blue, pressed or not: the two keys you hit most, findable at a glance.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(u * 10f)) { drawGlyph(if (up) KeyGlyph.SHIFT_UP else KeyGlyph.SHIFT_DOWN, Graphite.Blue) }
            Spacer(Modifier.width(u * 1.5f))
            Text(
                if (up) "+" else "−", color = Graphite.Text, fontSize = (u * 6.5f).asSp(),
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            )
        }
    }
}

// ==================== glyphs ====================

private fun DrawScope.drawGlyph(glyph: KeyGlyph, color: Color) {
    val s = size.minDimension
    val c = center
    val r = s * 0.42f
    val line = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun lamp() {
        // A headlamp seen from the side: flat glass to the left, bowl to the right.
        drawArc(color, -90f, 180f, true, Offset(c.x - r * 0.55f, c.y - r * 0.8f), Size(r * 1.5f, r * 1.6f))
    }

    fun arrow(dir: Float) {
        val p = Path().apply {
            moveTo(c.x - dir * r, c.y)
            lineTo(c.x - dir * r * 0.05f, c.y - r * 0.85f)
            lineTo(c.x - dir * r * 0.05f, c.y - r * 0.36f)
            lineTo(c.x + dir * r, c.y - r * 0.36f)
            lineTo(c.x + dir * r, c.y + r * 0.36f)
            lineTo(c.x - dir * r * 0.05f, c.y + r * 0.36f)
            lineTo(c.x - dir * r * 0.05f, c.y + r * 0.85f)
            close()
        }
        drawPath(p, color)
    }

    when (glyph) {
        KeyGlyph.HEADLIGHTS -> {
            lamp()
            for (i in -1..1) {
                val y = c.y + i * r * 0.5f
                drawLine(color, Offset(c.x - r * 0.3f, y), Offset(c.x - r * 1.05f, y), line.width, StrokeCap.Round)
            }
        }
        KeyGlyph.FOG -> {
            lamp()
            for (i in -1..1) {
                val y = c.y + i * r * 0.5f
                drawLine(color, Offset(c.x - r * 0.3f, y), Offset(c.x - r * 1.05f, y), line.width, StrokeCap.Round)
            }
            // The wavy line through the beams that makes it the fog-lamp symbol.
            val wave = Path().apply {
                moveTo(c.x - r * 0.68f, c.y - r * 0.95f)
                cubicTo(c.x - r * 0.95f, c.y - r * 0.45f, c.x - r * 0.4f, c.y - r * 0.2f, c.x - r * 0.68f, c.y)
                cubicTo(c.x - r * 0.95f, c.y + r * 0.2f, c.x - r * 0.4f, c.y + r * 0.45f, c.x - r * 0.68f, c.y + r * 0.95f)
            }
            drawPath(wave, color, style = Stroke(width = line.width * 0.8f, cap = StrokeCap.Round))
        }
        KeyGlyph.SIGNAL_LEFT -> arrow(1f)
        KeyGlyph.SIGNAL_RIGHT -> arrow(-1f)
        KeyGlyph.HAZARD -> {
            fun triangle(k: Float) = Path().apply {
                moveTo(c.x, c.y - r * k)
                lineTo(c.x + r * k * 1.05f, c.y + r * k * 0.8f)
                lineTo(c.x - r * k * 1.05f, c.y + r * k * 0.8f)
                close()
            }
            drawPath(triangle(1f), color, style = line)
            drawPath(triangle(0.42f).apply { translate(Offset(0f, r * 0.14f)) }, color, style = Stroke(line.width * 0.8f, join = StrokeJoin.Round))
        }
        KeyGlyph.RECOVER -> {
            // A turning arrow: put the car back.
            val rr = r * 0.78f
            drawArc(color, 30f, 285f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = line)
            val a = (315.0 * PI / 180.0)
            val end = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
            val tangent = Offset(-sin(a).toFloat(), cos(a).toFloat())
            val radial = Offset(cos(a).toFloat(), sin(a).toFloat())
            val head = Path().apply {
                moveTo(end.x + tangent.x * r * 0.62f, end.y + tangent.y * r * 0.62f)
                lineTo(end.x + radial.x * r * 0.42f, end.y + radial.y * r * 0.42f)
                lineTo(end.x - radial.x * r * 0.42f, end.y - radial.y * r * 0.42f)
                close()
            }
            drawPath(head, color)
        }
        KeyGlyph.HANDBRAKE -> {
            // The parking-brake symbol: (P) — a ring, brake shoes either side, a P.
            drawCircle(color, r * 0.62f, c, style = line)
            drawArc(color, 140f, 80f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = line)
            drawArc(color, -40f, 80f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = line)
            val p = Path().apply {
                moveTo(c.x - r * 0.16f, c.y + r * 0.32f)
                lineTo(c.x - r * 0.16f, c.y - r * 0.3f)
                lineTo(c.x + r * 0.04f, c.y - r * 0.3f)
                cubicTo(c.x + r * 0.32f, c.y - r * 0.3f, c.x + r * 0.32f, c.y + r * 0.04f, c.x + r * 0.04f, c.y + r * 0.04f)
                lineTo(c.x - r * 0.16f, c.y + r * 0.04f)
            }
            drawPath(p, color, style = Stroke(line.width * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        KeyGlyph.HORN -> {
            // A horn: mouthpiece, flared bell, sound waves.
            val bell = Path().apply {
                moveTo(c.x - r * 0.95f, c.y - r * 0.22f)
                lineTo(c.x - r * 0.45f, c.y - r * 0.22f)
                lineTo(c.x + r * 0.15f, c.y - r * 0.72f)
                lineTo(c.x + r * 0.15f, c.y + r * 0.72f)
                lineTo(c.x - r * 0.45f, c.y + r * 0.22f)
                lineTo(c.x - r * 0.95f, c.y + r * 0.22f)
                close()
            }
            drawPath(bell, color)
            drawArc(color, -40f, 80f, false, Offset(c.x - r * 0.35f, c.y - r * 0.55f), Size(r * 1.1f, r * 1.1f), style = line)
            drawArc(color, -40f, 80f, false, Offset(c.x - r * 0.55f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f), style = line)
        }
        KeyGlyph.MORE -> {
            for (i in -1..1) drawCircle(color, s * 0.085f, Offset(c.x + i * r * 0.72f, c.y))
        }
        KeyGlyph.SHIFT_UP, KeyGlyph.SHIFT_DOWN -> {
            // Two stacked chevrons, like the arrows on a sequential shifter.
            val dir = if (glyph == KeyGlyph.SHIFT_UP) -1f else 1f
            val stroke = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (row in listOf(-0.42f, 0.42f)) {
                val y = c.y + row * r
                val chevron = Path().apply {
                    moveTo(c.x - r * 0.85f, y - dir * r * 0.32f)
                    lineTo(c.x, y + dir * r * 0.38f)
                    lineTo(c.x + r * 0.85f, y - dir * r * 0.32f)
                }
                drawPath(chevron, color, style = stroke)
            }
        }
    }
}
