package com.beamng.remotecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.protocol.Ports
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.Panel
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground

private const val RECOMMENDED_RATE = "60"

enum class GuideChapter { CONNECT, DASHBOARD, DRIFT, MOD, TROUBLE }

/**
 * In-app setup documentation, laid out like Settings: chapters on the left, one
 * chapter at a time on the right. Every value on it is the user's REAL value
 * (live Wi-Fi IP, exact ports), so nothing gets mistyped into the game; the
 * phone's address stays in view under the chapters whichever one is open.
 */
@Composable
fun SetupGuideScreen(phoneIp: String?, onBack: () -> Unit) {
    var chapter by rememberSaveable { mutableStateOf(GuideChapter.CONNECT) }
    BoxWithConstraints(Modifier.fillMaxSize().background(screenBackground()).safeDrawingPadding()) {
        val u = maxHeight / 100
        Column(Modifier.fillMaxSize().padding(horizontal = u * 6, vertical = u * 4)) {
            Row(Modifier.fillMaxWidth().height(u * 10), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", color = Graphite.Text, fontSize = (u * 7).asSp(),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(horizontal = u * 2.5f),
                )
                Spacer(Modifier.width(u * 1.5f))
                Text(stringResource(R.string.guide_title), color = Graphite.Text, fontSize = (u * 5).asSp(), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(u * 2))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                // ---- chapters, and this phone's address ----
                Column(
                    Modifier.width(u * 46).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(u * 1.2f),
                ) {
                    val tabs = listOf(
                        GuideChapter.CONNECT to R.string.guide_tab_connect,
                        GuideChapter.DASHBOARD to R.string.guide_tab_dash,
                        GuideChapter.DRIFT to R.string.guide_tab_drift,
                        GuideChapter.MOD to R.string.guide_tab_mod,
                        GuideChapter.TROUBLE to R.string.guide_tab_trouble,
                    )
                    for ((index, tab) in tabs.withIndex()) {
                        ChapterTab(index + 1, stringResource(tab.second), chapter == tab.first, u) { chapter = tab.first }
                    }
                    Spacer(Modifier.height(u * 1.2f))
                    Card(u) {
                        Label(stringResource(R.string.guide_ip_label), u)
                        Spacer(Modifier.height(u * 0.8f))
                        Text(
                            phoneIp ?: stringResource(R.string.guide_ip_missing),
                            color = if (phoneIp != null) Graphite.Text else Graphite.Red,
                            fontFamily = if (phoneIp != null) FontFamily.Monospace else FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = (if (phoneIp != null) u * 4.4f else u * 2.6f).asSp(),
                            lineHeight = (if (phoneIp != null) u * 5.4f else u * 3.6f).asSp(),
                        )
                    }
                }
                // ---- the selected chapter ----
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(u * 4),
                        verticalArrangement = Arrangement.spacedBy(u * 3),
                    ) {
                        when (chapter) {
                            GuideChapter.CONNECT -> ConnectChapter(u)
                            GuideChapter.DASHBOARD -> StreamChapter(
                                u, phoneIp, Ports.APP.toString(), R.string.guide_dash_title, R.string.guide_dash_what,
                                R.string.guide_dash_s1, R.string.guide_dash_s2, R.string.guide_dash_s3,
                            )
                            GuideChapter.DRIFT -> StreamChapter(
                                u, phoneIp, Ports.MOTION.toString(), R.string.guide_drift_title, R.string.guide_drift_what,
                                R.string.guide_drift_s1, R.string.guide_drift_s2, R.string.guide_drift_s3,
                            )
                            GuideChapter.MOD -> ModChapter(u)
                            GuideChapter.TROUBLE -> TroubleChapter(u)
                        }
                    }
                }
            }
        }
    }
}

// ==================== chapters ====================

@Composable
private fun ConnectChapter(u: Dp) {
    Label(stringResource(R.string.guide_connect_title), u)
    Lead(stringResource(R.string.guide_connect_what), u)
    Card(u) {
        Step(1, stringResource(R.string.guide_connect_s1), u)
        Step(2, stringResource(R.string.guide_connect_s2), u)
        Step(3, stringResource(R.string.guide_connect_s3), u, last = true)
    }
    Note(stringResource(R.string.guide_connect_s_note), u)

    Label(stringResource(R.string.guide_ways_title), u)
    Note(stringResource(R.string.guide_ways_intro), u)
    Card(u) {
        Way(stringResource(R.string.guide_way_scan_t), stringResource(R.string.guide_way_scan_b), u)
        Spacer(Modifier.height(u * 2.2f))
        Way(stringResource(R.string.guide_way_photo_t), stringResource(R.string.guide_way_photo_b), u)
        Spacer(Modifier.height(u * 2.2f))
        Way(stringResource(R.string.guide_way_code_t), stringResource(R.string.guide_way_code_b), u)
    }
}

/** OutGauge and MotionSim are set up the same way: tick a box, type three values, reload the car. */
@Composable
private fun StreamChapter(u: Dp, phoneIp: String?, port: String, title: Int, what: Int, s1: Int, s2: Int, s3: Int) {
    Label(stringResource(title), u)
    Lead(stringResource(what), u)
    Card(u) {
        Step(1, stringResource(s1), u)
        Step(2, stringResource(s2), u)
        Column(
            Modifier
                .padding(start = u * 7.2f, top = u * 0.6f, bottom = u * 2.2f)
                .fillMaxWidth()
                .background(Graphite.PanelBottom, RoundedCornerShape(u * 1.8f))
                .border(1.dp, Graphite.Edge, RoundedCornerShape(u * 1.8f))
                .padding(horizontal = u * 3, vertical = u * 2),
            verticalArrangement = Arrangement.spacedBy(u * 1.2f),
        ) {
            ValueRow(stringResource(R.string.guide_value_address), phoneIp ?: "—", phoneIp != null, u)
            ValueRow(stringResource(R.string.guide_value_port), port, true, u)
            ValueRow(stringResource(R.string.guide_value_rate), RECOMMENDED_RATE, true, u)
        }
        Step(3, stringResource(s3), u, last = true)
    }
    Note(stringResource(R.string.guide_intro), u)
}

/** The optional game mod: what you have without it, what you get with it, how to add it. */
@Composable
private fun ModChapter(u: Dp) {
    Label(stringResource(R.string.guide_mod_title), u)
    Lead(stringResource(R.string.guide_mod_what), u)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        Card(u, Modifier.weight(1f).fillMaxHeight()) {
            Label(stringResource(R.string.guide_mod_without_title), u)
            Spacer(Modifier.height(u * 1.6f))
            for (line in listOf(
                R.string.guide_mod_without_1, R.string.guide_mod_without_2, R.string.guide_mod_without_3,
                R.string.guide_mod_without_4,
            )) {
                Point(stringResource(line), Graphite.Muted, u)
            }
        }
        Card(u, Modifier.weight(1.25f).fillMaxHeight(), highlight = true) {
            Text(
                stringResource(R.string.guide_mod_with_title), color = Graphite.Orange, fontSize = (u * 2.6f).asSp(),
                letterSpacing = (u * 0.5f).asSp(), fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(u * 1.6f))
            val lines = listOf(
                R.string.mod_what_1, R.string.mod_what_2, R.string.mod_what_3, R.string.mod_what_4, R.string.mod_what_6,
                R.string.mod_what_7, R.string.mod_what_8, R.string.mod_what_9, R.string.mod_what_10, R.string.mod_what_5,
            )
            for (line in lines) Point(stringResource(line), Graphite.Orange, u)
        }
    }
    Label(stringResource(R.string.mod_install_title), u)
    Card(u) {
        Step(1, stringResource(R.string.mod_install_1), u)
        Step(2, stringResource(R.string.mod_install_2), u)
        Step(3, stringResource(R.string.mod_install_3), u, last = true)
    }
    Note(stringResource(R.string.mod_install_note), u)
    Note(stringResource(R.string.guide_mod_gauges_note), u)
}

@Composable
private fun TroubleChapter(u: Dp) {
    Label(stringResource(R.string.guide_trouble_title), u)
    val items = listOf(
        R.string.guide_trouble_1, R.string.guide_trouble_2, R.string.guide_trouble_3,
        R.string.guide_trouble_4, R.string.guide_trouble_5, R.string.guide_trouble_6,
    )
    for (item in items) {
        // Each line is "Question? Answer." — the question is what the eye looks for.
        val text = stringResource(item)
        val split = text.indexOf('?')
        Card(u) {
            if (split > 0 && split < text.length - 1) {
                Text(text.substring(0, split + 1), color = Graphite.Text, fontSize = (u * 3.2f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(u * 0.8f))
                Text(text.substring(split + 1).trim(), color = Graphite.Muted, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4.1f).asSp())
            } else {
                Text(text, color = Graphite.Text, fontSize = (u * 3f).asSp(), lineHeight = (u * 4.2f).asSp())
            }
        }
    }
}

// ==================== pieces ====================

@Composable
private fun ChapterTab(number: Int, label: String, selected: Boolean, u: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Graphite.PanelTop else Color.Transparent)
            .border(1.dp, if (selected) Graphite.Edge else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = u * 2.3f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(u * 0.9f).height(u * 5).background(if (selected) Graphite.Orange else Color.Transparent, RoundedCornerShape(50)))
        Spacer(Modifier.width(u * 2.4f))
        Text(
            "$number", color = if (selected) Graphite.Orange else Graphite.Muted, fontSize = (u * 3.2f).asSp(),
            fontWeight = FontWeight.Bold, modifier = Modifier.width(u * 4),
        )
        Text(
            label, color = if (selected) Graphite.Text else Graphite.Muted, fontSize = (u * 3.4f).asSp(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
        )
    }
}

@Composable
private fun Card(u: Dp, modifier: Modifier = Modifier.fillMaxWidth(), highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Column(
        modifier
            .background(if (highlight) Graphite.Selected else Graphite.Inset, shape)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Graphite.Orange else Graphite.Edge, shape)
            .padding(u * 3),
        content = content,
    )
}

@Composable
private fun Label(text: String, u: Dp) {
    Text(text, color = Graphite.Muted, fontSize = (u * 2.6f).asSp(), letterSpacing = (u * 0.5f).asSp(), fontWeight = FontWeight.SemiBold)
}

/** What this chapter gives you — the reason to bother with its steps. */
@Composable
private fun Lead(text: String, u: Dp) {
    Text(text, color = Graphite.Text, fontSize = (u * 3.5f).asSp(), lineHeight = (u * 4.8f).asSp(), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Note(text: String, u: Dp) {
    Text(text, color = Graphite.Muted, fontSize = (u * 2.8f).asSp(), lineHeight = (u * 4f).asSp())
}

/** A numbered step: the number in a ring, joined to the next one by the spacing below it. */
@Composable
private fun Step(number: Int, text: String, u: Dp, last: Boolean = false) {
    Row(Modifier.padding(bottom = if (last) 0.dp else u * 2.2f)) {
        Box(
            Modifier.size(u * 5.2f).border(2.dp, Graphite.Orange, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Graphite.Orange, fontSize = (u * 2.9f).asSp(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(u * 2))
        Text(
            text, color = Graphite.Text, fontSize = (u * 3f).asSp(), lineHeight = (u * 4.2f).asSp(),
            modifier = Modifier.padding(top = u * 0.5f),
        )
    }
}

/** A named alternative method: its name, then a plain-language explanation. */
@Composable
private fun Way(title: String, body: String, u: Dp) {
    Text(title, color = Graphite.Orange, fontSize = (u * 3.1f).asSp(), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(u * 0.6f))
    Text(body, color = Graphite.Text, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4.1f).asSp())
}

@Composable
private fun Point(text: String, dot: Color, u: Dp) {
    Row(Modifier.padding(bottom = u * 1.3f)) {
        Text("•", color = dot, fontSize = (u * 3f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(u * 1.6f))
        Text(text, color = Graphite.Text, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4f).asSp())
    }
}

/** One of the exact fields the game asks for, pre-filled with this phone's value. */
@Composable
private fun ValueRow(label: String, value: String, known: Boolean, u: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Graphite.Muted, fontSize = (u * 2.8f).asSp(), modifier = Modifier.weight(1f))
        Text(
            value, color = if (known) Graphite.Text else Graphite.Red, fontSize = (u * 3.4f).asSp(),
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        )
    }
}
