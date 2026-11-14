package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.ModProtocol
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp

/**
 * The extra controls the RemoteControlPro mod makes possible. Null on the drive
 * screen means "connected to the stock game": none of these exist there.
 */
data class ModControls(
    val showShift: Boolean,
    val showHandbrake: Boolean,
    val showHorn: Boolean,
    val showLights: Boolean,
    val handbrakeOn: Boolean,
    /** One of the ModProtocol.BUTTON_* bits went down or up. */
    val onButton: (bit: Int, pressed: Boolean) -> Unit,
    val onHandbrake: (Boolean) -> Unit,
)

/** A key that is "down" for as long as it is held. Releases on lift and on a cancelled gesture. */
@Composable
fun HoldKey(label: String, u: Dp, modifier: Modifier, accent: Color = Graphite.Orange, onPress: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    KeyFace(label, pressed, accent, u, modifier.pointerInput(Unit) {
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
    })
}

/** A key that latches: one tap on, one tap off (the handbrake). */
@Composable
fun LatchKey(label: String, on: Boolean, u: Dp, modifier: Modifier, accent: Color = Graphite.Red, onChange: (Boolean) -> Unit) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOn by rememberUpdatedState(on)
    KeyFace(label, on, accent, u, modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnChange(!currentOn)
        }
    })
}

@Composable
private fun KeyFace(label: String, lit: Boolean, accent: Color, u: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(u * 2)
    Box(
        modifier
            .background(
                Brush.verticalGradient(if (lit) listOf(Graphite.Selected, Graphite.Selected) else listOf(Graphite.PanelTop, Graphite.PanelBottom)),
                shape,
            )
            .border(if (lit) 2.dp else 1.dp, if (lit) accent else Graphite.Edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (lit) accent else Graphite.Text, fontSize = (u * 3).asSp(),
            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        )
    }
}

/** Lights, signals and recover: a row of small keys under the gauges. */
@Composable
fun ModLightsRow(mod: ModControls, u: Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 1.6f)) {
        val keys = listOf(
            stringResource(R.string.mod_key_lights) to ModProtocol.BUTTON_HEADLIGHTS,
            stringResource(R.string.mod_key_fog) to ModProtocol.BUTTON_FOG,
            "◀" to ModProtocol.BUTTON_SIGNAL_LEFT,
            "⚠" to ModProtocol.BUTTON_HAZARD,
            "▶" to ModProtocol.BUTTON_SIGNAL_RIGHT,
            stringResource(R.string.mod_key_recover) to ModProtocol.BUTTON_RECOVER,
        )
        for ((label, bit) in keys) {
            HoldKey(label, u, Modifier.weight(1f).fillMaxHeight()) { pressed -> mod.onButton(bit, pressed) }
        }
    }
}
