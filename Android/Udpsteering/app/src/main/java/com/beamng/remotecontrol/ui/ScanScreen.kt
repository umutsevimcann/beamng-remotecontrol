package com.beamng.remotecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.ui.theme.NightGarage
import com.journeyapps.barcodescanner.DecoratedBarcodeView

@Composable
fun ScanScreen(
    barcodeView: DecoratedBarcodeView,
    connecting: Boolean,
    onCancelConnecting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground())
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.scan_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = NightGarage.Amber,
            )
            Text(
                stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleLarge,
                color = NightGarage.TextBright,
            )
        }
        Spacer(Modifier.height(16.dp))

        AndroidView(
            factory = { barcodeView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, NightGarage.PanelEdge, RoundedCornerShape(16.dp)),
        )

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.scan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = NightGarage.TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.scan_camera_note),
            style = MaterialTheme.typography.bodyMedium,
            color = NightGarage.TextFaint,
            textAlign = TextAlign.Center,
        )
    }

    if (connecting) {
        Dialog(onDismissRequest = onCancelConnecting) {
            GarageCard(title = "") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NightGarage.Amber)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.connecting_dialog_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NightGarage.Text,
                    )
                }
            }
        }
    }
}
