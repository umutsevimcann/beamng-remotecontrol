package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The phone as a gamepad for the game's menus (mod 2.4+), laid out like one: a round
 * D-pad under the left thumb, select and back as face buttons under the right, the
 * menu's tabs as shoulder keys in the top corners, the menu key in the middle. They
 * are the keys a controller uses, so every menu that works with a gamepad works from here.
 *
 * [onMenu]: a ModProtocol.MENU_* bit held (true) or let go. [onClose]: back to the
 * drive (or walking) screen; the game's menu stays as it is.
 */
@Composable
fun MenuPad(connected: Boolean, onMenu: (bit: Int, pressed: Boolean) -> Unit, onClose: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(screenBackground()).windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        // 1% of the height, held back on a squarish screen so both thumb clusters still fit side by side.
        val u = minOf(maxHeight / 100, maxWidth / MIN_WIDTH_UNITS)
        fun key(bit: Int): (Boolean) -> Unit = { pressed -> onMenu(bit, pressed) }

        // Shoulder keys in the top corners: the menu's tabs, where a gamepad has its bumpers.
        val soft = u * 2.4f
        val shoulder = Modifier.width(u * 38).height(u * 13)
        PadKey(
            RoundedCornerShape(topStart = u * 8, topEnd = soft, bottomEnd = soft, bottomStart = soft),
            Graphite.Orange, Modifier.align(Alignment.TopStart).padding(start = u * 6, top = u * 4).then(shoulder),
            onPress = key(ModProtocol.MENU_TAB_LEFT),
        ) { tint -> PadLabel(stringResource(R.string.menu_pad_tab_left), tint, u * 3.2f) }
        PadKey(
            RoundedCornerShape(topStart = soft, topEnd = u * 8, bottomEnd = soft, bottomStart = soft),
            Graphite.Orange, Modifier.align(Alignment.TopEnd).padding(end = u * 6, top = u * 4).then(shoulder),
            onPress = key(ModProtocol.MENU_TAB_RIGHT),
        ) { tint -> PadLabel(stringResource(R.string.menu_pad_tab_right), tint, u * 3.2f) }

        // Left thumb: one round D-pad. Sliding across it moves from one direction to the next.
        DPad(Modifier.align(Alignment.CenterStart).padding(start = u * 7, top = u * 12).size(u * 58), onMenu)

        // Right thumb: select low and near, back higher and further out, like A and B.
        Box(Modifier.align(Alignment.CenterEnd).padding(end = u * 7, top = u * 12).size(u * 58)) {
            PadKey(
                CircleShape, Graphite.Green, Modifier.align(Alignment.BottomStart).size(u * 34),
                round = true, ringed = true, onPress = key(ModProtocol.MENU_OK),
            ) { tint ->
                FaceLabel(stringResource(R.string.menu_pad_ok), tint, u * 11, u * 2.8f) { drawCheck(tint) }
            }
            PadKey(
                CircleShape, Graphite.Red, Modifier.align(Alignment.TopEnd).size(u * 26),
                round = true, ringed = true, onPress = key(ModProtocol.MENU_BACK),
            ) { tint ->
                FaceLabel(stringResource(R.string.menu_pad_back), tint, u * 8, u * 2.3f) { drawReturn(tint) }
            }
        }

        // Between the thumbs, out of both their ways: where we are, and the menu key.
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = u * 5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(u * 1.5f),
        ) {
            StatusChip(connected, true, u)
            Text(
                stringResource(R.string.menu_pad_title), color = Graphite.Orange.copy(alpha = 0.8f),
                fontSize = (u * 2.4f).asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            )
        }
        PadKey(
            CircleShape, Graphite.Blue, Modifier.align(Alignment.Center).padding(top = u * 12).size(u * 20),
            round = true, ringed = true, onPress = key(ModProtocol.MENU_TOGGLE),
        ) { tint ->
            FaceLabel(stringResource(R.string.menu_pad_menu), tint, u * 7, u * 2.2f) { drawGlyph(KeyGlyph.MENU, tint) }
        }

        // Back to driving: small, quiet, at the bottom edge between the thumbs, and it only
        // acts when the finger lifts while still on it, so a stray touch can be slid away.
        PadKey(
            RoundedCornerShape(50), Graphite.Orange,
            Modifier.align(Alignment.BottomCenter).padding(bottom = u * 4).height(u * 11).widthIn(min = u * 30),
            quiet = true, onPress = {}, onTap = onClose,
        ) { tint ->
            Box(Modifier.padding(horizontal = u * 4)) { PadLabel(stringResource(R.string.menu_pad_close), tint, u * 2.5f) }
        }
    }
}

/** The narrowest the screen may be, in units, before the whole pad scales down to fit. */
private const val MIN_WIDTH_UNITS = 168

@Composable
private fun PadLabel(text: String, tint: Color, size: Dp) {
    Text(text, color = tint, fontSize = size.asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
}

/** A face button's marking: a drawn symbol with its word underneath. */
@Composable
private fun FaceLabel(text: String, tint: Color, glyphSize: Dp, textSize: Dp, glyph: DrawScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(glyphSize), onDraw = glyph)
        PadLabel(text, tint, textSize)
    }
}

/**
 * One key of the pad in any [shape]; like HoldKey it is down for as long as it is held
 * and released on lift or a cancelled gesture, and it lights in [accent] while held.
 *
 * [round]: only touches inside the inscribed circle count. [ringed]: a face button,
 * marked in its accent colour even at rest so it is found at a glance. [quiet]: muted
 * at rest. [onTap]: called when the finger lifts while still on the key.
 */
@Composable
private fun PadKey(
    shape: Shape,
    accent: Color,
    modifier: Modifier,
    round: Boolean = false,
    ringed: Boolean = false,
    quiet: Boolean = false,
    onPress: (Boolean) -> Unit,
    onTap: (() -> Unit)? = null,
    content: @Composable (tint: Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnTap by rememberUpdatedState(onTap)
    val face = if (pressed) {
        // Pushed in and glowing in its own colour.
        Modifier.background(Graphite.PanelBottom, shape).background(accent.copy(alpha = 0.24f), shape).border(3.dp, accent, shape)
    } else {
        Modifier
            .background(Brush.verticalGradient(listOf(Graphite.PanelTop, Graphite.PanelBottom)), shape)
            .border(if (ringed) 2.dp else 1.dp, if (ringed) accent.copy(alpha = 0.6f) else Graphite.Edge, shape)
    }
    Box(
        modifier.then(face).pointerInput(round) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val middle = Offset(size.width / 2f, size.height / 2f)
                if (round && (down.position - middle).getDistance() > minOf(size.width, size.height) / 2f) return@awaitEachGesture
                var tapped = false
                try {
                    pressed = true
                    currentOnPress(true)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val at = change.position
                            tapped = at.x >= 0f && at.y >= 0f && at.x <= size.width && at.y <= size.height
                            break
                        }
                    }
                } finally {
                    pressed = false
                    currentOnPress(false)
                }
                if (tapped) currentOnTap?.invoke()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        content(if (pressed || ringed) accent else if (quiet) Graphite.Muted else Graphite.Text)
    }
}

// ==================== D-pad ====================

/** The four directions: the bit each sends and where it sits, in degrees clockwise from "right". */
private val DPAD_SEGMENTS = listOf(
    ModProtocol.MENU_RIGHT to 0f,
    ModProtocol.MENU_DOWN to 90f,
    ModProtocol.MENU_LEFT to 180f,
    ModProtocol.MENU_UP to 270f,
)

/** The hub in the middle presses nothing, as a share of the pad's radius. */
private const val DPAD_HUB = 0.24f

/** How far past a segment's edge the thumb must go before the next one takes over, in degrees. */
private const val DPAD_STICKY_DEG = 10f

private fun angleBetween(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/**
 * Which direction a touch at [at] means on a pad of [size]; 0 for none. [held] is the
 * direction already down: it stays down over the hub and a little past its own edges,
 * so a thumb resting on a boundary does not chatter between two directions.
 */
private fun dpadSegment(at: Offset, size: IntSize, held: Int, firstTouch: Boolean): Int {
    val radius = minOf(size.width, size.height) / 2f
    val dx = at.x - size.width / 2f
    val dy = at.y - size.height / 2f
    val distance = hypot(dx, dy)
    if (!distance.isFinite() || radius <= 0f) return held
    if (distance < radius * DPAD_HUB) return held
    // A touch that starts off the disc is not for the pad; one that slides off it still is.
    if (firstTouch && distance > radius) return 0
    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
    val heldAngle = DPAD_SEGMENTS.firstOrNull { it.first == held }?.second
    if (heldAngle != null && angleBetween(angle, heldAngle) < 45f + DPAD_STICKY_DEG) return held
    return DPAD_SEGMENTS.minByOrNull { angleBetween(angle, it.second) }?.first ?: 0
}

/**
 * A single round D-pad: four segments of one disc. One direction is down at a time;
 * sliding to another releases the first and presses the next, which the game's menus
 * take as a new step.
 */
@Composable
private fun DPad(modifier: Modifier, onMenu: (bit: Int, pressed: Boolean) -> Unit) {
    var held by remember { mutableStateOf(0) }
    val currentOnMenu by rememberUpdatedState(onMenu)
    Canvas(
        modifier.pointerInput(Unit) {
            fun hold(bit: Int) {
                if (bit == held) return
                if (held != 0) currentOnMenu(held, false)
                held = bit
                if (bit != 0) currentOnMenu(bit, true)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                try {
                    hold(dpadSegment(down.position, size, held, firstTouch = true))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        hold(dpadSegment(change.position, size, held, firstTouch = false))
                    }
                } finally {
                    hold(0)
                }
            }
        },
    ) {
        val radius = size.minDimension / 2f
        val c = center
        val accent = Graphite.Orange
        // The well the pad sits in.
        drawCircle(Graphite.Inset, radius, c)
        drawCircle(Graphite.Edge, radius - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))

        val outer = radius * 0.94f
        val inner = radius * 0.30f
        val sweep = 84f // of 90: the rest is the groove between two segments
        val raised = Brush.verticalGradient(listOf(Graphite.PanelTop, Graphite.PanelBottom))
        for ((bit, degrees) in DPAD_SEGMENTS) {
            val lit = bit == held
            val wedge = Path().apply {
                arcTo(Rect(c, outer), degrees - sweep / 2f, sweep, true)
                arcTo(Rect(c, inner), degrees + sweep / 2f, -sweep, false)
                close()
            }
            if (lit) {
                drawPath(wedge, Graphite.PanelBottom)
                drawPath(wedge, accent.copy(alpha = 0.24f))
                drawPath(wedge, accent, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            } else {
                drawPath(wedge, raised)
                drawPath(wedge, Graphite.Edge, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            }
            // An arrowhead pointing outwards.
            val a = Math.toRadians(degrees.toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val side = Offset(-dir.y, dir.x)
            val tip = c + dir * (radius * 0.80f)
            val base = c + dir * (radius * 0.57f)
            val head = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + side.x * radius * 0.15f, base.y + side.y * radius * 0.15f)
                lineTo(base.x - side.x * radius * 0.15f, base.y - side.y * radius * 0.15f)
                close()
            }
            val tint = if (lit) accent else Graphite.Text
            drawPath(head, tint)
            drawPath(head, tint, style = Stroke(radius * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // The hub: a resting place for the thumb that presses nothing.
        drawCircle(raised, radius * 0.20f, c)
        drawCircle(Graphite.Edge, radius * 0.20f, c, style = Stroke(1.dp.toPx()))
        drawCircle(if (held != 0) accent else Graphite.Off, radius * 0.05f, c)
    }
}

// ==================== glyphs ====================

/** A check mark: take this one. */
private fun DrawScope.drawCheck(color: Color) {
    val r = size.minDimension * 0.42f
    val c = center
    val mark = Path().apply {
        moveTo(c.x - r * 0.80f, c.y + r * 0.05f)
        lineTo(c.x - r * 0.25f, c.y + r * 0.60f)
        lineTo(c.x + r * 0.80f, c.y - r * 0.55f)
    }
    drawPath(mark, color, style = Stroke(size.minDimension * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A return arrow: one level back. */
private fun DrawScope.drawReturn(color: Color) {
    val r = size.minDimension * 0.42f
    val c = center
    val stroke = Stroke(size.minDimension * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val hook = Path().apply {
        moveTo(c.x - r * 0.80f, c.y - r * 0.25f)
        lineTo(c.x + r * 0.25f, c.y - r * 0.25f)
        cubicTo(c.x + r * 1.0f, c.y - r * 0.25f, c.x + r * 1.0f, c.y + r * 0.65f, c.x + r * 0.25f, c.y + r * 0.65f)
        lineTo(c.x - r * 0.40f, c.y + r * 0.65f)
    }
    drawPath(hook, color, style = stroke)
    val head = Path().apply {
        moveTo(c.x - r * 0.35f, c.y - r * 0.70f)
        lineTo(c.x - r * 0.80f, c.y - r * 0.25f)
        lineTo(c.x - r * 0.35f, c.y + r * 0.20f)
    }
    drawPath(head, color, style = stroke)
}
