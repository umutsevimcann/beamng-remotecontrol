package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground
import kotlin.math.hypot

/** How far past the ring, as a fraction of its radius, the stick must go to sprint. */
private const val SPRINT_AT = 0.97f

/**
 * The screen while the player is on foot (mod 2.3+), laid out like a mobile shooter:
 * a stick on the left half (it appears where the thumb lands), the right half drags
 * the camera, and the action keys sit at the bottom right where the thumb rests.
 *
 * [onStick]: x right, y forward, both -1..1, and whether the stick is pushed to its
 * edge (sprint, when [sprintOnEdge]). [onLook]: drag in dp since the last call.
 * [onButton]: a ModProtocol bit held (true) or let go. [onSprintKey]: the separate
 * sprint key, shown only when sprinting is not on the stick's edge.
 */
@Composable
fun WalkScreen(
    connected: Boolean,
    sprintOnEdge: Boolean,
    /** Left-handed: the stick on the right, the look area and keys on the left. */
    stickOnRight: Boolean,
    showMap: Boolean,
    /** Crouch latches (tap on, tap off) instead of only while held. */
    crouchToggle: Boolean,
    showInteract: Boolean,
    showView: Boolean,
    onStick: (x: Float, y: Float, sprint: Boolean) -> Unit,
    onLook: (dxDp: Float, dyDp: Float) -> Unit,
    onButton: (bit: Int, pressed: Boolean) -> Unit,
    onSprintKey: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(screenBackground()).windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        val u = maxHeight / 100
        // Latched crouch (crouch mode "tap on, tap off"); a new screen starts standing.
        var crouched by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxSize()) {
            if (stickOnRight) {
                LookPad(u, onLook, Modifier.weight(0.55f).fillMaxHeight())
                WalkStick(u, sprintOnEdge, true, onStick, Modifier.weight(0.45f).fillMaxHeight())
            } else {
                WalkStick(u, sprintOnEdge, false, onStick, Modifier.weight(0.45f).fillMaxHeight())
                LookPad(u, onLook, Modifier.weight(0.55f).fillMaxHeight())
            }
        }

        // Action keys, drawn after the look pad so a press on them never turns the camera.
        Column(
            if (stickOnRight) {
                Modifier.align(Alignment.BottomStart).padding(start = u * 5, bottom = u * 6)
            } else {
                Modifier.align(Alignment.BottomEnd).padding(end = u * 5, bottom = u * 6)
            },
            horizontalAlignment = if (stickOnRight) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(u * 3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(u * 3), verticalAlignment = Alignment.Bottom) {
                if (!sprintOnEdge) {
                    HoldKey(u, Modifier.size(u * 17), onPress = onSprintKey) { tint ->
                        WalkKeyFace(KeyGlyph.SPRINT, stringResource(R.string.walk_key_sprint), tint, u)
                    }
                }
                if (showInteract) {
                    HoldKey(u, Modifier.size(u * 17), onPress = { pressed -> onButton(ModProtocol.BUTTON_INTERACT, pressed) }) { tint ->
                        WalkKeyFace(KeyGlyph.INTERACT, stringResource(R.string.walk_key_interact), tint, u)
                    }
                }
                HoldKey(u, Modifier.size(u * 17), Graphite.Blue, onPress = { pressed -> onButton(ModProtocol.BUTTON_WALK, pressed) }) { tint ->
                    WalkKeyFace(KeyGlyph.ENTER_CAR, stringResource(R.string.walk_key_enter), tint, u)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(u * 3), verticalAlignment = Alignment.Bottom) {
                val crouch: @Composable () -> Unit = {
                    if (crouchToggle) {
                        LatchKey(crouched, u, Modifier.size(u * 17), Graphite.Orange, onChange = { on ->
                            crouched = on
                            onButton(ModProtocol.BUTTON_SHIFT_DOWN, on)
                        }) { tint -> WalkKeyFace(KeyGlyph.CROUCH, stringResource(R.string.walk_key_crouch), tint, u) }
                    } else {
                        HoldKey(u, Modifier.size(u * 17), onPress = { pressed -> onButton(ModProtocol.BUTTON_SHIFT_DOWN, pressed) }) { tint ->
                            WalkKeyFace(KeyGlyph.CROUCH, stringResource(R.string.walk_key_crouch), tint, u)
                        }
                    }
                }
                val jump: @Composable () -> Unit = {
                    HoldKey(u, Modifier.size(u * 24), Graphite.Orange, onPress = { pressed -> onButton(ModProtocol.BUTTON_SHIFT_UP, pressed) }) { tint ->
                        WalkKeyFace(KeyGlyph.JUMP, stringResource(R.string.walk_key_jump), tint, u)
                    }
                }
                if (stickOnRight) {
                    jump()
                    crouch()
                } else {
                    crouch()
                    jump()
                }
            }
        }
        if (showMap) {
            HoldKey(
                u,
                if (stickOnRight) {
                    Modifier.align(Alignment.TopStart).padding(start = u * 5, top = u * 18).size(u * 14)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(end = u * 5, top = u * 18).size(u * 14)
                },
                onPress = { pressed -> onButton(ModProtocol.BUTTON_MAP, pressed) },
            ) { tint -> WalkKeyFace(KeyGlyph.MAP, stringResource(R.string.mod_key_map), tint, u) }
        }

        if (showView) {
            HoldKey(
                u,
                if (stickOnRight) {
                    Modifier.align(Alignment.TopStart).padding(start = u * 5, top = if (showMap) u * 35 else u * 18).size(u * 14)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(end = u * 5, top = if (showMap) u * 35 else u * 18).size(u * 14)
                },
                onPress = { pressed -> onButton(ModProtocol.BUTTON_VIEW, pressed) },
            ) { tint -> WalkKeyFace(KeyGlyph.VIEW, stringResource(R.string.walk_key_view), tint, u) }
        }
        StatusChip(connected, true, u, Modifier.padding(start = u * 4, top = u * 4))
        SettingsGear(u, onOpenSettings, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun WalkKeyFace(glyph: KeyGlyph, label: String, tint: androidx.compose.ui.graphics.Color, u: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(u * 7)) { drawGlyph(glyph, tint) }
        Text(label, color = tint.copy(alpha = 0.85f), fontSize = (u * 2.2f).asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * A floating stick: it centres where the thumb lands in its half of the screen and
 * rests in the lower corner otherwise, so it is where the thumb already is.
 */
@Composable
private fun WalkStick(
    u: Dp, sprintOnEdge: Boolean, onRight: Boolean, onStick: (Float, Float, Boolean) -> Unit, modifier: Modifier,
) {
    val currentOnStick by rememberUpdatedState(onStick)
    val currentEdge by rememberUpdatedState(sprintOnEdge)
    var centre by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    var sprinting by remember { mutableStateOf(false) }
    val radiusPx = with(LocalDensity.current) { (u * 16).toPx() }
    val hint = stringResource(R.string.walk_hint_move)

    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Keep the whole ring on screen whatever spot the thumb lands on.
                val c = Offset(
                    down.position.x.coerceIn(radiusPx, size.width - radiusPx),
                    down.position.y.coerceIn(radiusPx, size.height - radiusPx),
                )
                centre = c
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.position - c
                        val len = hypot(d.x, d.y)
                        val reach = (len / radiusPx).coerceAtMost(1f)
                        val unit = if (len > 0f) d / len else Offset.Zero
                        knob = unit * (reach * radiusPx)
                        sprinting = currentEdge && len / radiusPx >= SPRINT_AT
                        // Screen y grows downwards; forward is up.
                        currentOnStick(unit.x * reach, -unit.y * reach, sprinting)
                        change.consume()
                    }
                } finally {
                    centre = null
                    knob = Offset.Zero
                    sprinting = false
                    currentOnStick(0f, 0f, false)
                }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // At rest it waits in the outer lower corner, where the thumb hangs.
            val c = centre ?: Offset(
                if (onRight) size.width - radiusPx * 1.6f else radiusPx * 1.6f,
                size.height - radiusPx * 1.5f,
            )
            val ring = if (sprinting) Graphite.Orange else Graphite.Edge
            drawCircle(Graphite.Inset.copy(alpha = 0.7f), radiusPx, c)
            drawCircle(ring, radiusPx, c, style = Stroke(radiusPx * (if (sprinting) 0.07f else 0.035f)))
            drawCircle(if (sprinting) Graphite.Orange else Graphite.Text.copy(alpha = 0.85f), radiusPx * 0.42f, c + knob)
        }
        Text(
            stringResource(R.string.walk_mod_badge), color = Graphite.Orange.copy(alpha = 0.8f),
            fontSize = (u * 2.4f).asSp(), fontWeight = FontWeight.Bold,
            modifier = Modifier.align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                .padding(top = u * 12, start = if (onRight) 0.dp else u * 4, end = if (onRight) u * 4 else 0.dp),
        )
        if (centre == null) {
            Text(
                hint, color = Graphite.Muted, fontSize = (u * 2.6f).asSp(),
                modifier = if (onRight) {
                    Modifier.align(Alignment.BottomEnd).padding(end = u * 6, bottom = u * 3)
                } else {
                    Modifier.align(Alignment.BottomStart).padding(start = u * 6, bottom = u * 3)
                },
            )
        }
    }
}

/** The right half: drag to look, like a mouse. Several fingers each drag on their own. */
@Composable
private fun LookPad(u: Dp, onLook: (Float, Float) -> Unit, modifier: Modifier) {
    val currentOnLook by rememberUpdatedState(onLook)
    val density = LocalDensity.current.density
    val hint = stringResource(R.string.walk_hint_look)
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var last = down.position
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val d = change.position - last
                    last = change.position
                    if (d != Offset.Zero) currentOnLook(d.x / density, d.y / density)
                    change.consume()
                }
            }
        },
    ) {
        Text(
            hint, color = Graphite.Muted.copy(alpha = 0.7f), fontSize = (u * 2.6f).asSp(),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.5f),
        )
    }
}
