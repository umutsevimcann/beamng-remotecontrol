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
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.RemoteTheme

/** Warm cockpit backdrop shared by the portrait screens. */
fun cockpitBackground() = Brush.radialGradient(
    colorStops = arrayOf(
        0.0f to Graphite.Bg0,
        0.55f to Graphite.Bg1,
        1.0f to Graphite.Bg2,
    ),
    radius = 1400f,
)

@Composable
fun WelcomeScreen(
    phoneIp: String?,
    onAutoConnectClick: () -> Unit,
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
            color = Graphite.Orange,
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
                color = Graphite.Muted,
            )
        }
        Spacer(Modifier.height(28.dp))

        // ===== Actions =====
        // Auto-connect is the hero path: the game's QR is too dense for most phone
        // cameras, so we discover its code over Wi-Fi instead. QR scan stays as an
        // option for good cameras; manual code entry is the last resort.
        AmberButton(stringResource(R.string.auto_connect_button), onClick = onAutoConnectClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.scan_qr_button), onClick = onScanClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.manual_button), onClick = onManualClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.guide_button), onClick = onGuideClick)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.settings_button), onClick = onSettingsClick)
    }
}

/**
 * Modal shown while the auto-connect sweep runs. [progress] is 0f..1f; the dialog
 * is non-dismissable except via Cancel so the sweep can't be orphaned by a stray tap.
 */
@Composable
fun AutoConnectDialog(progress: Float, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* only Cancel ends the sweep */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite.PanelTop, RoundedCornerShape(16.dp))
                .border(1.dp, Graphite.Edge, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.auto_connect_title),
                style = MaterialTheme.typography.titleLarge,
                color = Graphite.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.auto_connect_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                color = Graphite.Orange,
                trackColor = Graphite.Edge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.auto_connect_progress, (progress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = Graphite.Orange,
            )
            Spacer(Modifier.height(18.dp))
            GhostButton(stringResource(R.string.manual_cancel), onClick = onCancel)
        }
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
            .border(1.dp, Graphite.Edge, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = Graphite.Muted,
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•", color = Graphite.Orange, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Graphite.Text)
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.bodyLarge,
            color = Graphite.Orange,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Graphite.Text,
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
                Brush.verticalGradient(listOf(Color(0xFFFF8A33), Graphite.Orange)),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Graphite.OnOrange,
            textAlign = TextAlign.Center,
        )
    }
}

/** Camera-free connect: photo of the QR, or paste its link / type the code. */
@Composable
fun ManualCodeDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit, onPickPhoto: () -> Unit) {
    val text = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite.PanelTop, RoundedCornerShape(16.dp))
                .border(1.dp, Graphite.Edge, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.manual_title),
                style = MaterialTheme.typography.titleLarge,
                color = Graphite.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.manual_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite.Muted,
            )
            Spacer(Modifier.height(14.dp))
            AmberButton(stringResource(R.string.photo_button), onClick = onPickPhoto)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.manual_or),
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite.Muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.manual_field)) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Graphite.Orange,
                    unfocusedBorderColor = Graphite.Edge,
                    focusedLabelColor = Graphite.Orange,
                    unfocusedLabelColor = Graphite.Muted,
                    focusedTextColor = Graphite.Text,
                    unfocusedTextColor = Graphite.Text,
                    cursorColor = Graphite.Orange,
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
            .border(1.dp, Graphite.Edge, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Graphite.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun WelcomeScreenPreview() {
    RemoteTheme {
        WelcomeScreen(
            phoneIp = "192.168.0.23",
            onAutoConnectClick = {},
            onScanClick = {}, onSettingsClick = {}, onGuideClick = {}, onManualClick = {},
        )
    }
}

@Composable
private fun manualDialogPreviewHost() {
    RemoteTheme {
        ManualCodeDialog(onDismiss = {}, onConnect = {}, onPickPhoto = {})
    }
}
