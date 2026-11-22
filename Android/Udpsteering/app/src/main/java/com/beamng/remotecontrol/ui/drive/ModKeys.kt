package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    // The second page of the key row (mod 2.1+), one switch per group.
    val showDriveMode: Boolean,
    val show4wd: Boolean,
    val showNitrous: Boolean,
    val showDoors: Boolean,
    val showLowRange: Boolean,
    val showDiffLock: Boolean,
    // The game page (mod 2.2+), one switch per key.
    val showCamera: Boolean,
    val showGearbox: Boolean,
    val showReset: Boolean,
    val showPause: Boolean,
    val showMap: Boolean,
    val showWalk: Boolean,
    val showInteract: Boolean,
    val showMenu: Boolean,
    /** The MENU key was tapped: open the game's menu and the phone's menu pad. */
    val onMenuPad: () -> Unit,
    /** Handbrake key applies only while held, instead of tap on / tap off. */
    val handbrakeHold: Boolean,
    /** The wheel's hub is a horn button. */
    val hornOnHub: Boolean,
    /** ModProtocol.FEATURE_* the car has; null = the mod did not say, assume all. */
    val features: Int?,
    val handbrakeOn: Boolean,
    /** One of the ModProtocol.BUTTON_* bits went down or up. */
    val onButton: (bit: Int, pressed: Boolean) -> Unit,
    val onHandbrake: (Boolean) -> Unit,
) {
    private fun has(feature: Int) = features == null || features and feature != 0

    // Wanted in settings AND present on this car.
    val driveModeKey get() = showDriveMode && has(ModProtocol.FEATURE_DRIVE_MODE)
    val fourWdKey get() = show4wd && has(ModProtocol.FEATURE_4WD)
    val lowRangeKey get() = showLowRange && has(ModProtocol.FEATURE_4WD)
    val diffLockKey get() = showDiffLock && has(ModProtocol.FEATURE_4WD)
    val nitrousKey get() = showNitrous && has(ModProtocol.FEATURE_NITROUS)

    /** Is there anything to put on the vehicle page for this car? */
    val showVehicle get() = driveModeKey || fourWdKey || lowRangeKey || diffLockKey || nitrousKey || showDoors

    /** Is any key of the game page switched on? */
    val showGame get() = showCamera || showGearbox || showReset || showPause || showMap || showWalk || showInteract || showMenu
}

/** The symbols on the keys: the ones a car's own switches carry, so no language is needed. */
enum class KeyGlyph {
    HEADLIGHTS, FOG, SIGNAL_LEFT, SIGNAL_RIGHT, HAZARD, RECOVER, HANDBRAKE, HORN, SHIFT_UP, SHIFT_DOWN, MORE,
    STABILITY, FOUR_WD, LOW_RANGE, DIFF_LOCK, NITROUS, DOORS,
    CAMERA, GEARBOX, RESET, PAUSE, MAP, WALK,
    JUMP, CROUCH, SPRINT, ENTER_CAR, INTERACT, VIEW, MENU,
}

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

/** One key of the game pages: what it shows and what a press does. */
private class GameKey(val glyph: KeyGlyph, val label: Int, val accent: Color, val onPress: (Boolean) -> Unit)

/** What one page of the key row holds. */
private sealed interface KeyPage {
    data object Lights : KeyPage
    data object Vehicle : KeyPage
    class Game(val keys: List<GameKey>) : KeyPage
}

/** The most keys one page may hold: what the lights page has, the width a thumb is used to. */
private const val MAX_KEYS_PER_PAGE = 6

/** The page key's share of the row, against 1 for an ordinary key. */
private const val PAGE_KEY_WEIGHT = 0.8f

/** Splits [keys] into as few pages as [MAX_KEYS_PER_PAGE] allows, as evenly as possible (8 = 4 + 4, not 6 + 2). */
private fun <T> evenPages(keys: List<T>): List<List<T>> {
    if (keys.isEmpty()) return emptyList()
    val pages = (keys.size + MAX_KEYS_PER_PAGE - 1) / MAX_KEYS_PER_PAGE
    return keys.chunked((keys.size + pages - 1) / pages)
}

/**
 * The key row under the gauges, one page at a time. Lights: lights, signals and
 * recover; the signal keys light up with the car's own indicators, so the row
 * doubles as a repeater. Vehicle: the car's optional systems, only those this car
 * has. Game: the keys you would otherwise reach for the keyboard for, split over
 * as many pages as it takes to keep every key thumb-sized.
 *
 * The last key steps through the pages and shows where you are: one dot per page,
 * the current one lit.
 */
@Composable
fun ModKeyRow(mod: ModControls, telemetry: TelemetryUiState, page: Int, onPage: (Int) -> Unit, u: Dp, modifier: Modifier) {
    val pages = buildList {
        if (mod.showLights) add(KeyPage.Lights)
        if (mod.showVehicle) add(KeyPage.Vehicle)
        for (keys in evenPages(gameKeys(mod))) add(KeyPage.Game(keys))
    }
    if (pages.isEmpty()) return
    // A page can vanish under us (another car, a setting): fall back to the first.
    val index = if (page in pages.indices) page else 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 1.6f)) {
        // Keyed by page: a key still held when the page turns is released as itself,
        // not as whichever key takes its place.
        key(index) {
            when (val current = pages[index]) {
                KeyPage.Lights -> LightKeys(mod, telemetry, u)
                KeyPage.Vehicle -> VehicleKeys(mod, u)
                is KeyPage.Game -> GameKeys(current.keys, u)
            }
        }
        if (pages.size > 1) PageKey(pages.size, index, onPage, u, Modifier.weight(PAGE_KEY_WEIGHT).fillMaxHeight())
    }
}

/**
 * Steps through the pages of the key row. A tap goes to the next page (and round
 * to the first); dragging the thumb off it to the right goes back one instead.
 * It acts on release, so the drag can be told from the tap.
 */
@Composable
private fun PageKey(count: Int, index: Int, onPage: (Int) -> Unit, u: Dp, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPage by rememberUpdatedState(onPage)
    val currentCount by rememberUpdatedState(count)
    val currentIndex by rememberUpdatedState(index)
    KeyFace(false, pressed, Graphite.Orange, u, modifier.pointerInput(u) {
        val backDrag = (u * 5).toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dx = 0f
            var lifted = false
            try {
                pressed = true
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    dx = change.position.x - down.position.x
                    if (!change.pressed) {
                        lifted = true
                        break
                    }
                }
            } finally {
                pressed = false
            }
            // A cancelled gesture turns no page.
            if (lifted) currentOnPage((currentIndex + if (dx > backDrag) -1 else 1).mod(currentCount))
        }
    }) { tint ->
        val here = Graphite.Orange
        val elsewhere = Graphite.Muted
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // A chevron: there is more this way.
            val cy = h * 0.37f
            val k = h * 0.13f
            val chevron = Path().apply {
                moveTo(w / 2f - k * 0.5f, cy - k)
                lineTo(w / 2f + k * 0.5f, cy)
                lineTo(w / 2f - k * 0.5f, cy + k)
            }
            drawPath(chevron, tint, style = Stroke(h * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // One dot per page, the current one bigger and lit.
            val step = minOf(h * 0.2f, w * 0.8f / count)
            val dot = minOf(h * 0.04f, step * 0.3f)
            for (i in 0 until count) {
                val x = w / 2f + (i - (count - 1) / 2f) * step
                drawCircle(if (i == index) here else elsewhere, if (i == index) dot * 1.5f else dot, Offset(x, h * 0.72f))
            }
        }
    }
}

/** Things you would otherwise reach for the keyboard for: the keys switched on, in row order. */
private fun gameKeys(mod: ModControls): List<GameKey> = buildList {
    fun button(glyph: KeyGlyph, label: Int, bit: Int, accent: Color = Graphite.Orange) =
        add(GameKey(glyph, label, accent) { pressed -> mod.onButton(bit, pressed) })
    // MENU sends nothing itself: on release it opens the game's menu and the phone's menu pad.
    if (mod.showMenu) add(GameKey(KeyGlyph.MENU, R.string.mod_key_menu, Graphite.Blue) { pressed -> if (!pressed) mod.onMenuPad() })
    if (mod.showCamera) button(KeyGlyph.CAMERA, R.string.mod_key_camera, ModProtocol.BUTTON_CAMERA)
    if (mod.showGearbox) button(KeyGlyph.GEARBOX, R.string.mod_key_gearbox, ModProtocol.BUTTON_GEARBOX_MODE)
    if (mod.showReset) button(KeyGlyph.RESET, R.string.mod_key_reset, ModProtocol.BUTTON_RESET, Graphite.Red)
    if (mod.showPause) button(KeyGlyph.PAUSE, R.string.mod_key_pause, ModProtocol.BUTTON_PAUSE)
    if (mod.showMap) button(KeyGlyph.MAP, R.string.mod_key_map, ModProtocol.BUTTON_MAP)
    if (mod.showWalk) button(KeyGlyph.WALK, R.string.mod_key_walk, ModProtocol.BUTTON_WALK)
    if (mod.showInteract) button(KeyGlyph.INTERACT, R.string.mod_key_interact, ModProtocol.BUTTON_INTERACT)
}

@Composable
private fun RowScope.GameKeys(keys: List<GameKey>, u: Dp) {
    for (gameKey in keys) {
        HoldKey(u, Modifier.weight(1f).fillMaxHeight(), gameKey.accent, onPress = gameKey.onPress) { tint ->
            KeyStack(gameKey.glyph, stringResource(gameKey.label), tint, u)
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
    val keys = buildList {
        if (mod.driveModeKey) add(Triple(KeyGlyph.STABILITY, "ESC", ModProtocol.BUTTON_DRIVE_MODE))
        if (mod.fourWdKey) add(Triple(KeyGlyph.FOUR_WD, "4WD", ModProtocol.BUTTON_4WD))
        if (mod.lowRangeKey) add(Triple(KeyGlyph.LOW_RANGE, "LO", ModProtocol.BUTTON_LOW_RANGE))
        if (mod.diffLockKey) add(Triple(KeyGlyph.DIFF_LOCK, "DIFF", ModProtocol.BUTTON_DIFF))
        if (mod.nitrousKey) add(Triple(KeyGlyph.NITROUS, "N₂O", ModProtocol.BUTTON_NITROUS))
    }
    for ((glyph, label, bit) in keys) {
        val accent = if (bit == ModProtocol.BUTTON_NITROUS) Graphite.Red else Graphite.Orange
        HoldKey(u, Modifier.weight(1f).fillMaxHeight(), accent, onPress = { pressed -> mod.onButton(bit, pressed) }) { tint ->
            KeyStack(glyph, label, tint, u)
        }
    }
    if (!mod.showDoors) return
    // Doors: the game has separate "open" and "close" actions; one key alternates.
    // Wider while there is room; on a car with every system it is a key like the others.
    val doorsWeight = if (keys.size < MAX_KEYS_PER_PAGE - 1) 1.5f else 1f
    var doorsOpen by remember { mutableStateOf(false) }
    HoldKey(u, Modifier.weight(doorsWeight).fillMaxHeight(), Graphite.Blue, lit = doorsOpen, onPress = { pressed ->
        val bit = if (doorsOpen) ModProtocol.BUTTON_DOORS_CLOSE else ModProtocol.BUTTON_DOORS_OPEN
        mod.onButton(bit, pressed)
        if (!pressed) doorsOpen = !doorsOpen
    }) { tint -> KeyStack(KeyGlyph.DOORS, stringResource(R.string.mod_key_doors), tint, u) }
}

/** A symbol with its legend underneath, the way a car's own switch is marked. */
@Composable
private fun KeyStack(glyph: KeyGlyph, label: String, tint: Color, u: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(u * 5.6f)) { drawGlyph(glyph, tint) }
        Text(label, color = tint.copy(alpha = 0.8f), fontSize = (u * 1.9f).asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** Horn (hold) and handbrake (latch) side by side: the two keys the steering hand reaches. */
@Composable
fun ModWheelKeys(mod: ModControls, u: Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 2)) {
        if (mod.showHorn) {
            HoldKey(u, Modifier.weight(1f).fillMaxHeight(), onPress = { pressed -> mod.onButton(ModProtocol.BUTTON_HORN, pressed) }) { tint ->
                KeyLabel(KeyGlyph.HORN, tint, u, u * 8f)
            }
        }
        if (mod.showHandbrake) {
            if (mod.handbrakeHold) {
                // Pulled for as long as the thumb stays on it, like a drift handbrake.
                HoldKey(u, Modifier.weight(1f).fillMaxHeight(), Graphite.Red, onPress = mod.onHandbrake) { tint ->
                    KeyLabel(KeyGlyph.HANDBRAKE, tint, u, u * 8f)
                }
            } else {
                LatchKey(mod.handbrakeOn, u, Modifier.weight(1f).fillMaxHeight(), onChange = mod.onHandbrake) { tint ->
                    KeyLabel(KeyGlyph.HANDBRAKE, tint, u, u * 8f)
                }
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
        Canvas(Modifier.size(u * 13f)) { drawGlyph(if (up) KeyGlyph.SHIFT_UP else KeyGlyph.SHIFT_DOWN, Graphite.Blue) }
    }
}

// ==================== glyphs ====================

internal fun DrawScope.drawGlyph(glyph: KeyGlyph, color: Color) {
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
            // The dashboard parking-brake light: a disc, a brake shoe either side, a P.
            drawCircle(color, r * 0.66f, c, style = line)
            drawArc(color, 135f, 90f, false, Offset(c.x - r * 1.04f, c.y - r * 1.04f), Size(r * 2.08f, r * 2.08f), style = line)
            drawArc(color, -45f, 90f, false, Offset(c.x - r * 1.04f, c.y - r * 1.04f), Size(r * 2.08f, r * 2.08f), style = line)
            val p = Path().apply {
                moveTo(c.x - r * 0.17f, c.y + r * 0.36f)
                lineTo(c.x - r * 0.17f, c.y - r * 0.34f)
                lineTo(c.x + r * 0.05f, c.y - r * 0.34f)
                cubicTo(c.x + r * 0.36f, c.y - r * 0.34f, c.x + r * 0.36f, c.y + r * 0.04f, c.x + r * 0.05f, c.y + r * 0.04f)
                lineTo(c.x - r * 0.17f, c.y + r * 0.04f)
            }
            drawPath(p, color, style = Stroke(line.width * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
        KeyGlyph.STABILITY -> {
            // The ESC light: a car from behind with two snaking skid marks under it.
            val body = Path().apply {
                moveTo(c.x - r * 0.75f, c.y - r * 0.05f)
                lineTo(c.x - r * 0.5f, c.y - r * 0.8f)
                lineTo(c.x + r * 0.5f, c.y - r * 0.8f)
                lineTo(c.x + r * 0.75f, c.y - r * 0.05f)
                close()
            }
            drawPath(body, color, style = line)
            for (side in listOf(-1f, 1f)) {
                val x = c.x + side * r * 0.48f
                val skid = Path().apply {
                    moveTo(x, c.y + r * 0.15f)
                    cubicTo(x + r * 0.4f, c.y + r * 0.4f, x - r * 0.4f, c.y + r * 0.7f, x, c.y + r * 0.98f)
                }
                drawPath(skid, color, style = line)
            }
        }
        KeyGlyph.FOUR_WD, KeyGlyph.DIFF_LOCK -> {
            // The drivetrain from above: two axles, a prop shaft, four wheels.
            // 4WD: every wheel filled (all driven). Diff lock: a cross on the shaft.
            val ax = r * 0.62f
            val ay = r * 0.66f
            val thin = line.width * 0.8f
            drawLine(color, Offset(c.x - ax, c.y - ay), Offset(c.x + ax, c.y - ay), thin, StrokeCap.Round)
            drawLine(color, Offset(c.x - ax, c.y + ay), Offset(c.x + ax, c.y + ay), thin, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y - ay), Offset(c.x, c.y + ay), thin, StrokeCap.Round)
            val wheel = Size(r * 0.36f, r * 0.68f)
            for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
                val topLeft = Offset(c.x + sx * ax - wheel.width / 2f, c.y + sy * ay - wheel.height / 2f)
                val corner = androidx.compose.ui.geometry.CornerRadius(r * 0.1f)
                if (glyph == KeyGlyph.FOUR_WD) drawRoundRect(color, topLeft, wheel, corner)
                else drawRoundRect(color, topLeft, wheel, corner, style = Stroke(thin))
            }
            if (glyph == KeyGlyph.DIFF_LOCK) {
                val k = r * 0.3f
                drawLine(color, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), line.width, StrokeCap.Round)
                drawLine(color, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), line.width, StrokeCap.Round)
            }
        }
        KeyGlyph.LOW_RANGE -> {
            // A steep climb with a wheel on it: what low range is for.
            val hill = Path().apply {
                moveTo(c.x - r, c.y + r * 0.85f)
                lineTo(c.x + r, c.y + r * 0.85f)
                lineTo(c.x + r, c.y - r * 0.5f)
                close()
            }
            drawPath(hill, color, style = line)
            drawCircle(color, r * 0.3f, Offset(c.x - r * 0.28f, c.y - r * 0.38f))
        }
        KeyGlyph.NITROUS -> {
            // A gas bottle: body, shoulder, valve.
            val w = r * 0.6f
            drawRoundRect(
                color, Offset(c.x - w, c.y - r * 0.35f), Size(w * 2, r * 1.3f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.3f), style = line,
            )
            drawLine(color, Offset(c.x, c.y - r * 0.35f), Offset(c.x, c.y - r * 0.75f), line.width * 1.6f, StrokeCap.Butt)
            drawLine(color, Offset(c.x - r * 0.45f, c.y - r * 0.85f), Offset(c.x + r * 0.45f, c.y - r * 0.85f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x - w * 0.5f, c.y + r * 0.3f), Offset(c.x + w * 0.5f, c.y + r * 0.3f), line.width * 0.8f, StrokeCap.Round)
        }
        KeyGlyph.DOORS -> {
            // A car from above with both front doors swung open.
            val w = r * 0.42f
            drawRoundRect(
                color, Offset(c.x - w, c.y - r * 0.95f), Size(w * 2, r * 1.9f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.32f), style = line,
            )
            for (side in listOf(-1f, 1f)) {
                drawLine(
                    color, Offset(c.x + side * w, c.y - r * 0.3f), Offset(c.x + side * r * 1.02f, c.y + r * 0.12f),
                    line.width, StrokeCap.Round,
                )
            }
            drawLine(color, Offset(c.x - w * 0.55f, c.y - r * 0.4f), Offset(c.x + w * 0.55f, c.y - r * 0.4f), line.width * 0.8f, StrokeCap.Round)
        }
        KeyGlyph.CAMERA -> {
            // A camera: body, viewfinder hump, lens.
            drawRoundRect(
                color, Offset(c.x - r, c.y - r * 0.5f), Size(r * 2, r * 1.3f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.2f), style = line,
            )
            drawLine(color, Offset(c.x - r * 0.35f, c.y - r * 0.72f), Offset(c.x + r * 0.35f, c.y - r * 0.72f), line.width * 1.5f, StrokeCap.Round)
            drawCircle(color, r * 0.36f, Offset(c.x, c.y + r * 0.15f), style = line)
        }
        KeyGlyph.GEARBOX -> {
            // The H of a shift gate.
            val x = r * 0.7f
            val y = r * 0.75f
            for (i in -1..1) drawLine(color, Offset(c.x + i * x, c.y - y), Offset(c.x + i * x, c.y + if (i == 1) 0f else y), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x - x, c.y), Offset(c.x + x, c.y), line.width, StrokeCap.Round)
            for (i in -1..1) drawCircle(color, line.width * 1.15f, Offset(c.x + i * x, c.y - y))
        }
        KeyGlyph.RESET -> {
            // Back to the start: an arrow running anticlockwise into a dot.
            val rr = r * 0.8f
            drawArc(color, -140f, 290f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = line)
            val a = (-140.0 * PI / 180.0)
            val end = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
            val head = Path().apply {
                moveTo(end.x - r * 0.42f, end.y - r * 0.5f)
                lineTo(end.x + r * 0.42f, end.y - r * 0.2f)
                lineTo(end.x - r * 0.1f, end.y + r * 0.4f)
                close()
            }
            drawPath(head, color)
            drawCircle(color, r * 0.16f, c)
        }
        KeyGlyph.PAUSE -> {
            for (side in listOf(-1f, 1f)) {
                drawRoundRect(
                    color, Offset(c.x + side * r * 0.42f - r * 0.2f, c.y - r * 0.8f), Size(r * 0.4f, r * 1.6f),
                    androidx.compose.ui.geometry.CornerRadius(r * 0.1f),
                )
            }
        }
        KeyGlyph.MAP -> {
            // A folded map: three panels in a zigzag, and a pin.
            val fold = Path().apply {
                moveTo(c.x - r, c.y - r * 0.62f)
                lineTo(c.x - r * 0.33f, c.y - r * 0.82f)
                lineTo(c.x + r * 0.33f, c.y - r * 0.62f)
                lineTo(c.x + r, c.y - r * 0.82f)
                lineTo(c.x + r, c.y + r * 0.62f)
                lineTo(c.x + r * 0.33f, c.y + r * 0.82f)
                lineTo(c.x - r * 0.33f, c.y + r * 0.62f)
                lineTo(c.x - r, c.y + r * 0.82f)
                close()
            }
            drawPath(fold, color, style = line)
            drawLine(color, Offset(c.x - r * 0.33f, c.y - r * 0.82f), Offset(c.x - r * 0.33f, c.y + r * 0.62f), line.width * 0.7f)
            drawLine(color, Offset(c.x + r * 0.33f, c.y - r * 0.62f), Offset(c.x + r * 0.33f, c.y + r * 0.82f), line.width * 0.7f)
            drawCircle(color, r * 0.2f, Offset(c.x, c.y - r * 0.05f))
        }
        KeyGlyph.WALK -> {
            // A walking figure: head, body, a leg forward and an arm swinging back.
            drawCircle(color, r * 0.18f, Offset(c.x + r * 0.08f, c.y - r * 0.78f))
            val hip = Offset(c.x - r * 0.02f, c.y + r * 0.15f)
            val neck = Offset(c.x + r * 0.10f, c.y - r * 0.50f)
            drawLine(color, neck, hip, line.width * 1.15f, StrokeCap.Round)
            drawLine(color, hip, Offset(c.x + r * 0.42f, c.y + r * 0.55f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.42f, c.y + r * 0.55f), Offset(c.x + r * 0.40f, c.y + r * 0.95f), line.width, StrokeCap.Round)
            drawLine(color, hip, Offset(c.x - r * 0.30f, c.y + r * 0.60f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x - r * 0.30f, c.y + r * 0.60f), Offset(c.x - r * 0.55f, c.y + r * 0.92f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.07f, c.y - r * 0.38f), Offset(c.x + r * 0.50f, c.y - r * 0.05f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.07f, c.y - r * 0.38f), Offset(c.x - r * 0.38f, c.y - r * 0.12f), line.width, StrokeCap.Round)
        }
        KeyGlyph.JUMP -> {
            // An arrow leaving a line: off the ground.
            drawLine(color, Offset(c.x - r * 0.8f, c.y + r * 0.85f), Offset(c.x + r * 0.8f, c.y + r * 0.85f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y + r * 0.5f), Offset(c.x, c.y - r * 0.75f), line.width * 1.2f, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x, c.y - r * 1.0f); lineTo(c.x + r * 0.5f, c.y - r * 0.35f); lineTo(c.x - r * 0.5f, c.y - r * 0.35f); close()
            }
            drawPath(head, color)
        }
        KeyGlyph.CROUCH -> {
            // An arrow pressing down onto a line.
            drawLine(color, Offset(c.x - r * 0.8f, c.y + r * 0.85f), Offset(c.x + r * 0.8f, c.y + r * 0.85f), line.width, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y - r * 0.9f), Offset(c.x, c.y + r * 0.1f), line.width * 1.2f, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x, c.y + r * 0.6f); lineTo(c.x + r * 0.5f, c.y - r * 0.05f); lineTo(c.x - r * 0.5f, c.y - r * 0.05f); close()
            }
            drawPath(head, color)
        }
        KeyGlyph.SPRINT -> {
            // Three chevrons pointing ahead: faster.
            for (i in -1..1) {
                val x = c.x + i * r * 0.5f
                val p = Path().apply {
                    moveTo(x - r * 0.3f, c.y - r * 0.6f); lineTo(x + r * 0.2f, c.y); lineTo(x - r * 0.3f, c.y + r * 0.6f)
                }
                drawPath(p, color, style = Stroke(line.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        KeyGlyph.ENTER_CAR -> {
            // A car from the side with an arrow going into it.
            val body = Path().apply {
                moveTo(c.x - r, c.y + r * 0.35f)
                lineTo(c.x - r, c.y - r * 0.05f)
                lineTo(c.x - r * 0.55f, c.y - r * 0.1f)
                lineTo(c.x - r * 0.25f, c.y - r * 0.55f)
                lineTo(c.x + r * 0.45f, c.y - r * 0.55f)
                lineTo(c.x + r * 0.75f, c.y - r * 0.1f)
                lineTo(c.x + r, c.y - r * 0.02f)
                lineTo(c.x + r, c.y + r * 0.35f)
                close()
            }
            drawPath(body, color, style = line)
            drawCircle(color, r * 0.2f, Offset(c.x - r * 0.55f, c.y + r * 0.45f))
            drawCircle(color, r * 0.2f, Offset(c.x + r * 0.55f, c.y + r * 0.45f))
            drawLine(color, Offset(c.x, c.y - r * 1.0f), Offset(c.x, c.y - r * 0.7f), line.width, StrokeCap.Round)
        }
        KeyGlyph.INTERACT -> {
            // A pointing tap: a ring, a dot, and short rays — "use this".
            drawCircle(color, r * 0.5f, c, style = line)
            drawCircle(color, r * 0.16f, c)
            for (k in 0 until 4) {
                val a = (k * 90.0 + 45.0) * PI / 180.0
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                drawLine(color, Offset(c.x + dx * r * 0.72f, c.y + dy * r * 0.72f), Offset(c.x + dx * r * 1.0f, c.y + dy * r * 1.0f), line.width, StrokeCap.Round)
            }
        }
        KeyGlyph.VIEW -> {
            // A magnifying glass: zoom.
            drawCircle(color, r * 0.55f, Offset(c.x - r * 0.15f, c.y - r * 0.15f), style = line)
            drawLine(color, Offset(c.x + r * 0.25f, c.y + r * 0.25f), Offset(c.x + r * 0.85f, c.y + r * 0.85f), line.width * 1.4f, StrokeCap.Round)
            drawLine(color, Offset(c.x - r * 0.42f, c.y - r * 0.15f), Offset(c.x + r * 0.12f, c.y - r * 0.15f), line.width * 0.8f, StrokeCap.Round)
            drawLine(color, Offset(c.x - r * 0.15f, c.y - r * 0.42f), Offset(c.x - r * 0.15f, c.y + r * 0.12f), line.width * 0.8f, StrokeCap.Round)
        }
        KeyGlyph.MENU -> {
            // Three lines: a menu.
            for (k in -1..1) drawLine(color, Offset(c.x - r * 0.8f, c.y + k * r * 0.55f), Offset(c.x + r * 0.8f, c.y + k * r * 0.55f), line.width * 1.1f, StrokeCap.Round)
        }
        KeyGlyph.MORE -> {
            for (i in -1..1) drawCircle(color, s * 0.085f, Offset(c.x + i * r * 0.72f, c.y))
        }
        KeyGlyph.SHIFT_UP, KeyGlyph.SHIFT_DOWN -> {
            // A paddle-shifter mark: a bold + or - in a ring, and an arrowhead on the
            // side the gear goes (up above the ring, down below it).
            val up = glyph == KeyGlyph.SHIFT_UP
            val dir = if (up) -1f else 1f
            val ringR = r * 0.56f
            val ringC = Offset(c.x, c.y - dir * r * 0.30f)
            drawCircle(color, ringR, ringC, style = Stroke(s * 0.06f))
            val bar = ringR * 0.52f
            val thick = s * 0.085f
            drawLine(color, Offset(ringC.x - bar, ringC.y), Offset(ringC.x + bar, ringC.y), thick, StrokeCap.Round)
            if (up) drawLine(color, Offset(ringC.x, ringC.y - bar), Offset(ringC.x, ringC.y + bar), thick, StrokeCap.Round)
            val tipY = c.y + dir * r * 1.02f
            val baseY = c.y + dir * r * 0.52f
            val head = Path().apply {
                moveTo(c.x, tipY)
                lineTo(c.x + r * 0.50f, baseY)
                lineTo(c.x - r * 0.50f, baseY)
                close()
            }
            drawPath(head, color)
            drawPath(head, color, style = Stroke(s * 0.04f, join = StrokeJoin.Round))
        }
    }
}
