package com.beamng.remotecontrol.ui.dash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.asSp
import kotlin.math.roundToInt

/** "Minimal": speed, gear, one thin RPM bar. Nothing else competes for attention. */
@Composable
fun MinimalDash(state: DashState, u: Dp, modifier: Modifier = Modifier) {
    val rpm by animateFloatAsState(state.safeRpm(), spring(stiffness = Spring.StiffnessMediumLow), label = "rpm")
    val scale = state.rpmScale
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            "${state.speed().roundToInt()}", color = Graphite.Text, fontSize = (u * 34).asSp(),
            lineHeight = (u * 34).asSp(), fontWeight = FontWeight.Light, letterSpacing = (-u * 1f).asSp(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashLabel(stringResource(if (state.metricUnits) R.string.speed_kmh else R.string.speed_mph), u)
            Spacer(Modifier.width(u * 5))
            Text(state.telemetry.gear, color = Graphite.Orange, fontSize = (u * 8).asSp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(u * 1.5f))
            DashLabel(stringResource(R.string.drive_gear), u)
        }
        Spacer(Modifier.height(u * 6))
        Canvas(Modifier.fillMaxWidth(0.8f).height(u * 3)) {
            val barH = size.height * 0.34f
            val y = (size.height - barH) / 2f
            val r = CornerRadius(barH / 2f)
            val fraction = (rpm / scale.max).coerceIn(0f, 1f)
            val over = rpm >= scale.redline
            drawRoundRect(Graphite.Track, Offset(0f, y), Size(size.width, barH), r)
            drawRoundRect(if (over) Graphite.Red else Graphite.Text, Offset(0f, y), Size(size.width * fraction, barH), r)
            // Redline tick.
            val x = size.width * (scale.redline / scale.max)
            drawRect(Graphite.Red, Offset(x - 1.5f, 0f), Size(3f, size.height))
        }
    }
}
