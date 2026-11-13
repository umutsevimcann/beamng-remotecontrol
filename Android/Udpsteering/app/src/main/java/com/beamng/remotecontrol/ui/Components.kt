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
