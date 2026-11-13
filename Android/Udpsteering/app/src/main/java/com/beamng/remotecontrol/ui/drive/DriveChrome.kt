package com.beamng.remotecontrol.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.Receivepacket
import com.beamng.remotecontrol.ui.TelemetryUiState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.abs

// The parts of the drive screen that stay the same whatever dash layout is chosen.

/** Top-left: green dot + "Connected" while telemetry flows, grey otherwise. */
@Composable
fun StatusChip(connected: Boolean, u: Dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(u * 2).background(if (connected) Graphite.Green else Graphite.Muted, RoundedCornerShape(50)))
        Spacer(Modifier.width(u * 1.6f))
        Text(
            stringResource(if (connected) R.string.status_connected else R.string.status_no_telemetry),
            color = if (connected) Graphite.Text else Graphite.Muted,
            fontSize = (u * 3.2f).asSp(), fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Dashboard warning lights, lit from the game's showLights bits. */
@Composable
fun WarningRow(telemetry: TelemetryUiState, u: Dp, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("◀", Receivepacket.INDEX_SIGNAL_L, Graphite.Green),
        Triple(stringResource(R.string.warn_highbeam), Receivepacket.INDEX_FULLBEAM, Graphite.Blue),
        Triple(stringResource(R.string.warn_handbrake), Receivepacket.INDEX_HANDBRAKE, Graphite.Red),
        Triple(stringResource(R.string.warn_abs), Receivepacket.INDEX_ABS, Graphite.Amber),
        Triple(stringResource(R.string.warn_tc), Receivepacket.INDEX_TC, Graphite.Amber),
        Triple(stringResource(R.string.warn_oil), Receivepacket.INDEX_OILWARN, Graphite.Red),
        Triple(stringResource(R.string.warn_battery), Receivepacket.INDEX_BATTERY, Graphite.Red),
        Triple("▶", Receivepacket.INDEX_SIGNAL_R, Graphite.Green),
    )
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for ((text, index, color) in items) {
            Text(
                text, color = if (telemetry.lights[index]) color else Graphite.Off,
                fontSize = (u * 3).asSp(), fontWeight = FontWeight.Bold, letterSpacing = (u * 0.3f).asSp(),
            )
        }
    }
}

/** How far the steering is turned, -1..1, as a bar growing from the centre mark. */
@Composable
fun SteerBar(value: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barH = size.height * 0.26f
        val y = (size.height - barH) / 2f
        val r = CornerRadius(barH / 2f)
        drawRoundRect(Graphite.Track, Offset(0f, y), Size(size.width, barH), r)
        val mid = size.width / 2f
        val v = value() // read while drawing: a per-frame value redraws, it doesn't recompose
        val len = (if (v.isFinite()) v else 0f).coerceIn(-1f, 1f) * mid
        drawRoundRect(Graphite.Orange, Offset(if (len >= 0) mid else mid + len, y), Size(abs(len), barH), r)
        drawRect(Graphite.Muted, Offset(mid - 1f, 0f), Size(2f, size.height))
    }
}

/**
 * Dashboard-only mode: what the game is applying (throttle / brake / clutch echo),
 * since the phone isn't the one driving.
 */
@Composable
fun EchoBars(telemetry: TelemetryUiState, u: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(u * 3), verticalAlignment = Alignment.Bottom) {
        EchoBar(stringResource(R.string.drive_thr), telemetry.throttleEcho, Graphite.Orange, u)
        EchoBar(stringResource(R.string.drive_brk), telemetry.brakeEcho, Graphite.Red, u)
        EchoBar(stringResource(R.string.drive_clt), telemetry.clutchEcho, Graphite.Blue, u)
    }
}

@Composable
private fun EchoBar(label: String, value: Float, color: Color, u: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(u * 3).height(u * 46).background(Graphite.Track, RoundedCornerShape(50)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val f = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().fillMaxHeight(f).background(color, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(u * 1.4f))
        Text(label, color = Graphite.Muted, fontSize = (u * 2.3f).asSp(), fontWeight = FontWeight.SemiBold)
    }
}
