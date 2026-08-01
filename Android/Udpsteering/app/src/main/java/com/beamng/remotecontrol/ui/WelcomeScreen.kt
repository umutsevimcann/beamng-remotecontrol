package com.beamng.remotecontrol.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.NightGarage
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

/** Warm cockpit backdrop shared by the portrait screens. */
fun cockpitBackground() = Brush.radialGradient(
    colorStops = arrayOf(
        0.0f to Color(0xFF2B241D),
        0.55f to Color(0xFF16120E),
        1.0f to NightGarage.Shell,
    ),
    radius = 1400f,
)

@Composable
fun WelcomeScreen(
    phoneIp: String?,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGuideClick: () -> Unit,
    onManualClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground())
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ===== Brand =====
        Image(
            painter = painterResource(R.drawable.logo_beamng),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.brand_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = NightGarage.Amber,
        )
        Spacer(Modifier.height(24.dp))

        // ===== Connect card =====
        GarageCard(title = stringResource(R.string.connect_card_title)) {
            NumberedStep(1, stringResource(R.string.connect_step_1))
            NumberedStep(2, stringResource(R.string.connect_step_2))
            NumberedStep(3, stringResource(R.string.connect_step_3))
        }
        Spacer(Modifier.height(14.dp))

        // ===== Optional extras card (details live in the Setup Guide) =====
        GarageCard(title = stringResource(R.string.extras_card_title)) {
            FeatureLine(stringResource(R.string.extras_dashboard_line))
            FeatureLine(stringResource(R.string.extras_drift_line))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.extras_guide_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = NightGarage.TextFaint,
            )
        }
        Spacer(Modifier.height(28.dp))

        // ===== Actions =====
        AmberButton(stringResource(R.string.scan_qr_button), onClick = onScanClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.manual_button), onClick = onManualClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.guide_button), onClick = onGuideClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.settings_button), onClick = onSettingsClick)
    }
}

// ==================== Night Garage building blocks ====================

@Composable
fun GarageCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xDD241C12), Color(0xDD1A140E))),
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, NightGarage.PanelEdge, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = NightGarage.TextFaint,
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•", color = NightGarage.Amber, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = NightGarage.Text)
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.Amber,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = NightGarage.Text,
        )
    }
}

@Composable
fun AmberButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFF8A3A), Color(0xFFE05A00))),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = NightGarage.OnAmber,
            textAlign = TextAlign.Center,
        )
    }
}

/** Camera-free connect: paste the game QR's link (or type the code). */
@Composable
fun ManualCodeDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    val text = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NightGarage.Panel, RoundedCornerShape(16.dp))
                .border(1.dp, NightGarage.PanelEdge, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.manual_title),
                style = MaterialTheme.typography.titleLarge,
                color = NightGarage.TextBright,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.manual_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = NightGarage.TextDim,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.manual_field)) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NightGarage.Amber,
                    unfocusedBorderColor = NightGarage.PanelEdge,
                    focusedLabelColor = NightGarage.Amber,
                    unfocusedLabelColor = NightGarage.TextFaint,
                    focusedTextColor = NightGarage.TextBright,
                    unfocusedTextColor = NightGarage.Text,
                    cursorColor = NightGarage.Amber,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    GhostButton(stringResource(R.string.manual_cancel), onClick = onDismiss)
                }
                Box(Modifier.weight(1f)) {
                    AmberButton(stringResource(R.string.manual_connect)) {
                        if (text.value.isNotBlank()) onConnect(text.value.trim())
                    }
                }
            }
        }
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(1.dp, NightGarage.PanelEdge, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = NightGarage.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun WelcomeScreenPreview() {
    NightGarageTheme {
        WelcomeScreen(
            phoneIp = "192.168.0.23",
            onScanClick = {}, onSettingsClick = {}, onGuideClick = {}, onManualClick = {},
        )
    }
}
