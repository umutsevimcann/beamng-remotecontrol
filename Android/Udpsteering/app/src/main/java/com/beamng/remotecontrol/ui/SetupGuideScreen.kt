package com.beamng.remotecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.Ports
import com.beamng.remotecontrol.ui.theme.NightGarage

private const val MOTION_PORT = 4446 // keep in sync with MainActivity.MOTION_PORT
private const val RECOMMENDED_RATE = "60"

/**
 * In-app, personalized setup documentation: every value on this page is the
 * user's REAL value (live Wi-Fi IP, exact ports), so nothing gets mistyped
 * into the game.
 */
@Composable
fun SetupGuideScreen(phoneIp: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground())
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(R.string.guide_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            color = NightGarage.Amber,
        )
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.titleLarge,
            color = NightGarage.TextBright,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.guide_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = NightGarage.TextDim,
        )
        Spacer(Modifier.height(14.dp))

        // Live identity box — the single source of truth for this phone
        GarageCard(title = stringResource(R.string.guide_ip_label)) {
            Text(
                text = phoneIp ?: stringResource(R.string.guide_ip_missing),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (phoneIp != null) 26.sp else 13.sp,
                ),
                color = if (phoneIp != null) Color(0xFFFFD9A0) else NightGarage.Red,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))

        // 1 · Connect
        GarageCard(title = stringResource(R.string.guide_connect_title)) {
            WhatYouGet(stringResource(R.string.guide_connect_what))
            GuideStep(1, stringResource(R.string.guide_connect_s1))
            GuideStep(2, stringResource(R.string.guide_connect_s2))
            GuideStep(3, stringResource(R.string.guide_connect_s3))
        }
        Spacer(Modifier.height(14.dp))

        // 2 · Live dashboard (OutGauge)
        GarageCard(title = stringResource(R.string.guide_dash_title)) {
            WhatYouGet(stringResource(R.string.guide_dash_what))
            GuideStep(1, stringResource(R.string.guide_dash_s1))
            GuideStep(2, stringResource(R.string.guide_dash_s2))
            ValueTable(phoneIp, port = Ports.APP.toString())
            GuideStep(3, stringResource(R.string.guide_dash_s3))
        }
        Spacer(Modifier.height(14.dp))

        // 3 · Drift meter (MotionSim)
        GarageCard(title = stringResource(R.string.guide_drift_title)) {
            WhatYouGet(stringResource(R.string.guide_drift_what))
            GuideStep(1, stringResource(R.string.guide_drift_s1))
            GuideStep(2, stringResource(R.string.guide_drift_s2))
            ValueTable(phoneIp, port = MOTION_PORT.toString())
            GuideStep(3, stringResource(R.string.guide_drift_s3))
        }
        Spacer(Modifier.height(14.dp))

        // Troubleshooting
        GarageCard(title = stringResource(R.string.guide_trouble_title)) {
            Bullet(stringResource(R.string.guide_trouble_1))
            Bullet(stringResource(R.string.guide_trouble_2))
            Bullet(stringResource(R.string.guide_trouble_3))
            Bullet(stringResource(R.string.guide_trouble_4))
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun WhatYouGet(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = NightGarage.Green,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.Amber,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.Text,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•", color = NightGarage.Amber, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = NightGarage.Text)
    }
}

/** The exact fields the game asks for, pre-filled with this phone's values. */
@Composable
private fun ValueTable(phoneIp: String?, port: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF100C08), RoundedCornerShape(9.dp))
            .border(1.dp, NightGarage.PanelEdge, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ValueRow(
            stringResource(R.string.guide_value_address),
            phoneIp ?: stringResource(R.string.guide_ip_missing),
            highlight = phoneIp != null,
        )
        ValueRow(stringResource(R.string.guide_value_port), port, highlight = true)
        ValueRow(stringResource(R.string.guide_value_rate), RECOMMENDED_RATE, highlight = true)
    }
}

@Composable
private fun ValueRow(label: String, value: String, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = NightGarage.TextFaint,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = if (highlight) Color(0xFFFFD9A0) else NightGarage.Red,
        )
    }
}
