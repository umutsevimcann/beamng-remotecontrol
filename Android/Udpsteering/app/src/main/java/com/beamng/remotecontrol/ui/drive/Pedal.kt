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

/**
 * A drawn pedal. Digital by default (any touch = 1); with [analog] the value follows
 * the finger's height, never below 0.05 while touched. The plate travels down with
 * the value and the side meter fills. A lifted or cancelled gesture always reports 0.
 */
@Composable
fun Pedal(
    label: String,
    tint: Color,
    width: Dp,
    height: Dp,
    plateFraction: Float,
    analog: Boolean,
    u: Dp,
    onValue: (Float) -> Unit,
) {
    var level by remember { mutableFloatStateOf(0f) }
    val currentOnValue by rememberUpdatedState(onValue)
    val shown by animateFloatAsState(level, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh), label = "pedal")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width + u * 3, height)
                .pointerInput(analog) {
                    fun valueAt(y: Float) = if (analog) (1f - y / size.height).coerceIn(0.05f, 1f) else 1f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        try {
                            level = valueAt(down.position.y)
                            currentOnValue(level)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (analog) {
                                    val v = valueAt(change.position.y)
                                    if (v != level) {
                                        level = v
                                        currentOnValue(v)
                                    }
                                }
                            }
                        } finally {
                            level = 0f
                            currentOnValue(0f)
                        }
                    }
                },
        ) { drawPedal(shown, plateFraction, tint, u.toPx()) }
        Spacer(Modifier.height(u * 1.4f))
        Text(label, color = tint, fontSize = (u * 2.6f).asSp(), fontWeight = FontWeight.Bold, letterSpacing = (u * 0.7f).asSp())
    }
}

private fun DrawScope.drawPedal(press: Float, plateFraction: Float, tint: Color, u: Float) {
    val meterW = u * 1.2f
    val plateW = size.width - u * 3f
    val travel = size.height * 0.085f
    val plateH = size.height * plateFraction
    val plateTop = size.height - plateH - size.height * 0.16f + press * travel
    val baseH = size.height * 0.085f

    // Floor bracket.
    drawRoundRect(Color(0xFF101114), Offset(-u, size.height - baseH), Size(plateW + u * 2f, baseH), CornerRadius(u * 1.2f))
    drawRoundRect(Graphite.Edge, Offset(-u, size.height - baseH), Size(plateW + u * 2f, baseH), CornerRadius(u * 1.2f), style = Stroke(1.5f))

    // Arm.
    val armW = plateW * 0.16f
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF5E626A), Color(0xFFC9CCD2), Color(0xFF5E626A)), (plateW - armW) / 2f, (plateW + armW) / 2f),
        Offset((plateW - armW) / 2f, plateTop + plateH * 0.7f), Size(armW, size.height - baseH * 0.5f - (plateTop + plateH * 0.7f)), CornerRadius(armW * 0.3f),
    )

    translate(0f, plateTop) {
        // Shadow under the plate grows as it lifts back up.
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(u * 0.6f, u * (1.6f - press)), Size(plateW, plateH), CornerRadius(u * 2.2f))
        // Plate.
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFC9CDD3), Color(0xFF7B8088), Color(0xFFAEB2B9)), Offset.Zero, Offset(plateW, plateH)),
            Offset.Zero, Size(plateW, plateH), CornerRadius(u * 2.2f),
        )
        // Rubber grip studs.
        val inset = plateW * 0.1f
        val cols = 3
        val cell = (plateW - inset * 2f) / cols
        val rows = ((plateH - inset * 2f) / cell).toInt().coerceAtLeast(1)
        val cellH = (plateH - inset * 2f) / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                drawRoundRect(
                    Color(0xFF15161A),
                    Offset(inset + col * cell + cell * 0.13f, inset + row * cellH + cellH * 0.13f),
                    Size(cell * 0.74f, cellH * 0.74f), CornerRadius(cell * 0.16f),
                )
            }
        }
        // Edge highlight; tinted while pressed.
        drawRoundRect(
            if (press > 0.02f) tint.copy(alpha = 0.35f + 0.5f * press) else Color.White.copy(alpha = 0.2f),
            Offset.Zero, Size(plateW, plateH), CornerRadius(u * 2.2f), style = Stroke(u * 0.35f),
        )
    }

    // Level meter beside the pedal.
    val meterX = plateW + u * 1.4f
    val meterTop = size.height - plateH - size.height * 0.16f
    val meterH = plateH + travel
    drawRoundRect(Color(0xFF1D2027), Offset(meterX, meterTop), Size(meterW, meterH), CornerRadius(meterW / 2f))
    if (press > 0.01f) {
        drawRoundRect(tint, Offset(meterX, meterTop + meterH * (1f - press)), Size(meterW, meterH * press), CornerRadius(meterW / 2f))
    }
}
