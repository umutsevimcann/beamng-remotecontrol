package com.beamng.remotecontrol.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
private fun ArrowButton(glyph: String, u: Dp, modifier: Modifier, onPress: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val shape = RoundedCornerShape(u * 3)
    Box(
        modifier
            .background(
                Brush.verticalGradient(if (pressed) listOf(Graphite.Selected, Graphite.Selected) else listOf(Graphite.PanelTop, Graphite.PanelBottom)),
                shape,
            )
            .border(if (pressed) 2.dp else 1.dp, if (pressed) Graphite.Orange else Graphite.Edge, shape)
            .pointerInput(Unit) {
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
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (pressed) Graphite.Orange else Graphite.Text, fontSize = (u * 11).asSp(), fontWeight = FontWeight.Bold)
    }
}


/** Arrow-buttons control: two hold buttons in the wheel's place. */
@Composable
fun ArrowButtons(
    size: Dp,
    u: Dp,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.size(size),
        horizontalArrangement = Arrangement.spacedBy(u * 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton("◀", u, Modifier.weight(1f).height(size * 0.7f), onLeft)
        ArrowButton("▶", u, Modifier.weight(1f).height(size * 0.7f), onRight)
    }
}
