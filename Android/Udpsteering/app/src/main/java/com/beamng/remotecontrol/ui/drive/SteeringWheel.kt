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

/** Finger-driven lock: a road car turns about 900° lock to lock, 450° each way. */
const val WHEEL_LOCK_DEG = 450f

/** Fraction of the wheel radius around the hub where dragging does not steer. */
private const val HUB_DEAD_RADIUS = 0.22f

/**
 * The steering wheel of the drive screen.
 *
 * [draggable] = false: a display. It shows [displayValue] (-1..1) as the angle the
 * car's wheel is at, value × [WHEEL_LOCK_DEG]. It is a lambda read in the draw
 * phase, so a value that changes every frame (tilt) redraws without recomposing.
 * [draggable] = true: the control. Drag it around its centre, up to ±450°
 * (multi-turn); it emits -1..1 through [onValue] and springs back on release.
 */
@Composable
fun SteeringWheel(
    size: Dp,
    draggable: Boolean,
    displayValue: () -> Float,
    onValue: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) } // degrees, touch modes only
    val currentOnValue by rememberUpdatedState(onValue)
    val currentOnRelease by rememberUpdatedState(onRelease)

    // The gesture coroutine outlives recompositions, hence currentOnValue/currentOnRelease:
    // with stale lambdas the wheel kept driving the previous control type's handler.
    val input = if (!draggable) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            var last = atan2(down.position.y - cy, down.position.x - cx)
            // Grabbed while still springing back: carry on from where the wheel is
            // and tell the handler, which was already reset to 0 on release —
            // otherwise the first move would jump from 0 to that angle.
            var current = drag.value
            scope.launch { drag.stop() }
            currentOnValue(current / WHEEL_LOCK_DEG)
            val deadRadius = minOf(cx, cy) * HUB_DEAD_RADIUS
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - cx
                    val dy = change.position.y - cy
                    // Near the hub a few pixels are a huge angle, and crossing the
                    // centre flips it by 180°: ignore movement in there.
                    if (dx * dx + dy * dy < deadRadius * deadRadius) continue
                    val a = atan2(dy, dx)
                    var delta = Math.toDegrees((a - last).toDouble()).toFloat()
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    last = a
                    current = (current + delta).coerceIn(-WHEEL_LOCK_DEG, WHEEL_LOCK_DEG)
                    scope.launch { drag.snapTo(current) }
                    currentOnValue(current / WHEEL_LOCK_DEG)
                }
            } finally {
                // Self-centring, like a real wheel let go at speed.
                currentOnRelease()
                scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    Box(modifier.size(size).then(input)) {
        // Soft contact shadow — drawn outside the rotating layer so it stays put.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(0.78f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, radius = this.size.minDimension * 0.56f),
                radius = this.size.minDimension * 0.56f, center = center + Offset(0f, this.size.minDimension * 0.03f),
            )
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                rotationZ = if (draggable) drag.value else displayValue() * WHEEL_LOCK_DEG
            },
        ) { drawWheel() }
    }
}

private fun DrawScope.drawWheel() {
    val r = size.minDimension / 2f * 0.94f
    val c = center
    val rimW = r * 0.21f
    val rimR = r - rimW / 2f

    // Rim: leather with a soft highlight sweeping round it.
    drawCircle(
        Brush.sweepGradient(
            listOf(Color(0xFF2B2D32), Color(0xFF45484F), Color(0xFF1A1B1E), Color(0xFF121315), Color(0xFF2E3035), Color(0xFF17181B), Color(0xFF2B2D32)),
            c,
        ),
        rimR, c, style = Stroke(rimW),
    )
    drawCircle(Color.Black.copy(alpha = 0.7f), r, c, style = Stroke(r * 0.012f))
    drawCircle(Color.White.copy(alpha = 0.10f), r - rimW * 0.22f, c, style = Stroke(r * 0.01f))
    drawCircle(Color.Black.copy(alpha = 0.6f), r - rimW, c, style = Stroke(r * 0.014f))

    // Spokes: brushed metal, left / right / bottom.
    val metal = Brush.verticalGradient(
        listOf(Color(0xFFDADDE2), Color(0xFF8E929A), Color(0xFF5B5F67)),
        startY = c.y - r * 0.14f, endY = c.y + r * 0.2f,
    )
    val metalH = Brush.horizontalGradient(
        listOf(Color(0xFF6A6E76), Color(0xFFCDD0D6), Color(0xFF6A6E76)),
        startX = c.x - r * 0.14f, endX = c.x + r * 0.14f,
    )
    val inner = r - rimW * 0.92f
    fun spoke(sign: Float) = Path().apply {
        moveTo(c.x + sign * inner, c.y - r * 0.07f)
        lineTo(c.x + sign * r * 0.26f, c.y - r * 0.12f)
        lineTo(c.x + sign * r * 0.26f, c.y + r * 0.14f)
        lineTo(c.x + sign * inner * 0.985f, c.y + r * 0.17f)
        close()
    }
    for (sign in listOf(-1f, 1f)) {
        drawPath(spoke(sign), metal)
        drawPath(spoke(sign), Color(0xFF24262B), style = Stroke(r * 0.012f))
        // Lightening slot
        drawRoundRect(
            Color(0xFF0E0F12),
            Offset(c.x + (if (sign < 0) -r * 0.76f else r * 0.40f), c.y - r * 0.005f),
            Size(r * 0.36f, r * 0.055f), CornerRadius(r * 0.03f),
        )
    }
    val bottom = Path().apply {
        moveTo(c.x - r * 0.135f, c.y + r * 0.24f)
        lineTo(c.x + r * 0.135f, c.y + r * 0.24f)
        lineTo(c.x + r * 0.10f, c.y + inner)
        lineTo(c.x - r * 0.10f, c.y + inner)
        close()
    }
    drawPath(bottom, metalH)
    drawPath(bottom, Color(0xFF24262B), style = Stroke(r * 0.012f))
    drawRoundRect(Color(0xFF0E0F12), Offset(c.x - r * 0.028f, c.y + r * 0.38f), Size(r * 0.056f, r * 0.36f), CornerRadius(r * 0.03f))

    // Hub.
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3C3F46), Color(0xFF0D0E11)), c - Offset(r * 0.08f, r * 0.1f), r * 0.5f), r * 0.36f, c)
    drawCircle(Color.Black, r * 0.36f, c, style = Stroke(r * 0.016f))
    drawCircle(Color.White.copy(alpha = 0.14f), r * 0.345f, c, style = Stroke(r * 0.008f))
    drawCircle(Color(0xFF0B0C0E), r * 0.135f, c)
    drawCircle(Graphite.Orange, r * 0.135f, c, style = Stroke(r * 0.02f))
    drawPath(
        Path().apply {
            moveTo(c.x, c.y - r * 0.062f); lineTo(c.x + r * 0.054f, c.y + r * 0.04f); lineTo(c.x - r * 0.054f, c.y + r * 0.04f); close()
        },
        Graphite.Orange,
    )

    // Top-centre marker so the turn angle reads at a glance.
    drawRoundRect(Graphite.Orange, Offset(c.x - r * 0.034f, c.y - r - r * 0.004f), Size(r * 0.068f, rimW * 1.02f), CornerRadius(r * 0.016f))
}
