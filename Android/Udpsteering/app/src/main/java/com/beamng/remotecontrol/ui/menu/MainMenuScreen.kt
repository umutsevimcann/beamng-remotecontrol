package com.beamng.remotecontrol.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.network.UdpDiscovery.SweepPhase
import com.beamng.remotecontrol.ui.dash.DashArea
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.sampleDashState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.Panel
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground

/** A search in progress, shown inside the computer card. [progress] is 0..1 of the current pass. */
data class SearchUi(val phase: SweepPhase, val progress: Float)

/**
 * The landscape main menu: one primary action (connect), the chosen dash layout
 * previewed on the left, everything else small. [savedPcIp] null = first run.
 */
@Composable
fun MainMenuScreen(
    savedPcIp: String?,
    layout: DashLayout,
    controlTypeLabel: String,
    search: SearchUi?,
    onConnect: () -> Unit,
    onNewPc: () -> Unit,
    onCancel: () -> Unit,
    onScanQr: () -> Unit,
    onEnterCode: () -> Unit,
    onLayout: (DashLayout) -> Unit,
    onControlType: () -> Unit,
    onSetupGuide: () -> Unit,
    onSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(screenBackground()).systemBarsPadding()) {
        val u = maxHeight / 100
        Column(Modifier.fillMaxSize().padding(horizontal = u * 6, vertical = u * 5)) {
            // ---- title bar ----
            Row(Modifier.fillMaxWidth().height(u * 9), verticalAlignment = Alignment.CenterVertically) {
                Text("BeamNG ", color = Graphite.Text, fontSize = (u * 5.4f).asSp(), fontWeight = FontWeight.Bold)
                Text("Remote", color = Graphite.Orange, fontSize = (u * 5.4f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "⚙", color = Graphite.Muted, fontSize = (u * 6).asSp(),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSettings).padding(u * 1.5f),
                )
            }
            Spacer(Modifier.height(u * 3))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 5)) {
                // ---- left: layout preview, or the three setup steps on first run ----
                Column(Modifier.weight(1.25f).fillMaxHeight()) {
                    Panel(Modifier.fillMaxWidth().weight(1f)) {
                        if (savedPcIp == null) {
                            FirstRunSteps(u)
                        } else {
                            BoxWithConstraints(Modifier.fillMaxSize().padding(u * 3)) {
                                // The dash area is ~82u tall on the drive screen; scale to fit here.
                                DashArea(layout, sampleDashState(), maxHeight / 82, Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(Modifier.height(u * 2.5f))
                    if (savedPcIp == null) {
                        Text(
                            stringResource(R.string.menu_same_wifi), color = Graphite.Muted, fontSize = (u * 3).asSp(),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(u * 6),
                        )
                    } else {
                        LayoutSelector(layout, onLayout, u, Modifier.fillMaxWidth().height(u * 6))
                    }
                }

                // ---- right: the computer card and the small stuff ----
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2.5f)) {
                    ComputerCard(savedPcIp, search, onConnect, onCancel, u)
                    if (search == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.menu_other_ways), color = Graphite.Muted, fontSize = (u * 3).asSp())
                            LinkText(stringResource(R.string.menu_qr), u, onScanQr)
                            LinkText(stringResource(R.string.menu_code), u, onEnterCode)
                            if (savedPcIp != null) LinkText(stringResource(R.string.menu_new_pc), u, onNewPc)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    MenuRow(stringResource(R.string.menu_control), controlTypeLabel, u, onControlType)
                    MenuRow(stringResource(R.string.guide_title), "", u, onSetupGuide)
                }
            }
        }
    }
}

@Composable
private fun ComputerCard(savedPcIp: String?, search: SearchUi?, onConnect: () -> Unit, onCancel: () -> Unit, u: Dp) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(u * 4)) {
            Text(
                stringResource(R.string.menu_computer), color = Graphite.Muted, fontSize = (u * 2.6f).asSp(),
                letterSpacing = (u * 0.5f).asSp(), fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(u * 1.2f))
            Text(
                savedPcIp ?: stringResource(R.string.menu_not_connected),
                color = Graphite.Text, fontSize = (u * 4.6f).asSp(), fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(u * 0.8f))
            val note = when {
                search?.phase == SweepPhase.SAVED_PC -> stringResource(R.string.menu_phase_saved)
                search?.phase == SweepPhase.FULL_SEARCH -> stringResource(R.string.menu_phase_full)
                savedPcIp == null -> stringResource(R.string.menu_first_note)
                else -> stringResource(R.string.menu_saved_note)
            }
            Text(note, color = Graphite.Muted, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4).asSp())
            Spacer(Modifier.height(u * 3))

            if (search == null) {
                PrimaryButton(stringResource(if (savedPcIp == null) R.string.menu_find else R.string.menu_connect), u, onConnect)
            } else {
                Row(Modifier.fillMaxWidth().height(u * 12), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(u * 1.8f).background(Graphite.Track, RoundedCornerShape(50))) {
                        val f = if (search.progress.isFinite()) search.progress.coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Graphite.Orange, RoundedCornerShape(50)))
                    }
                    Spacer(Modifier.width(u * 3))
                    Text(
                        stringResource(R.string.manual_cancel), color = Graphite.Text, fontSize = (u * 3.2f).asSp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(u * 2))
                            .background(Graphite.Track)
                            .clickable(onClick = onCancel)
                            .padding(horizontal = u * 3.5f, vertical = u * 2.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, u: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(u * 12)
            .clip(RoundedCornerShape(u * 2.6f))
            .background(Brush.verticalGradient(listOf(Color(0xFFFF8A33), Graphite.Orange)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Graphite.OnOrange, fontSize = (u * 3.8f).asSp(), fontWeight = FontWeight.ExtraBold, letterSpacing = (u * 0.3f).asSp())
    }
}

@Composable
private fun LinkText(text: String, u: Dp, onClick: () -> Unit) {
    Text(
        text, color = Graphite.Text, fontSize = (u * 3).asSp(), fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = u * 2)
            .clip(RoundedCornerShape(u * 1.5f))
            .background(Graphite.Track)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 2.2f, vertical = u * 1.4f),
    )
}

@Composable
private fun MenuRow(title: String, value: String, u: Dp, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = u * 3.5f, vertical = u * 2.8f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Graphite.Text, fontSize = (u * 3.3f).asSp(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("$value  ›".trim(), color = Graphite.Muted, fontSize = (u * 3.1f).asSp())
        }
    }
}

@Composable
private fun LayoutSelector(selected: DashLayout, onLayout: (DashLayout) -> Unit, u: Dp, modifier: Modifier) {
    val names = mapOf(
        DashLayout.MODERN to R.string.dash_modern,
        DashLayout.CLASSIC to R.string.dash_classic,
        DashLayout.RACE to R.string.dash_race,
        DashLayout.MINIMAL to R.string.dash_minimal,
    )
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (layout in DashLayout.entries) {
            val isSelected = layout == selected
            Column(
                Modifier.clip(RoundedCornerShape(u * 1.5f)).clickable { onLayout(layout) }.padding(horizontal = u * 2.5f, vertical = u * 0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(names.getValue(layout)),
                    color = if (isSelected) Graphite.Text else Graphite.Muted,
                    fontSize = (u * 3.1f).asSp(), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(u * 0.6f))
                Box(Modifier.width(u * 6).height(u * 0.5f).background(if (isSelected) Graphite.Orange else Color.Transparent))
            }
        }
    }
}

@Composable
private fun FirstRunSteps(u: Dp) {
    Column(
        Modifier.fillMaxSize().padding(u * 5),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.menu_steps_title), color = Graphite.Text, fontSize = (u * 5.2f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 4))
        val steps = listOf(R.string.connect_step_1, R.string.connect_step_2, R.string.menu_step_3)
        steps.forEachIndexed { i, res ->
            Row(Modifier.padding(vertical = u * 1.3f), verticalAlignment = Alignment.Top) {
                Text("${i + 1}", color = Graphite.Orange, fontSize = (u * 3.6f).asSp(), fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(u * 5))
                Text(stringResource(res), color = Graphite.Text, fontSize = (u * 3.3f).asSp(), lineHeight = (u * 4.6f).asSp())
            }
        }
    }
}
