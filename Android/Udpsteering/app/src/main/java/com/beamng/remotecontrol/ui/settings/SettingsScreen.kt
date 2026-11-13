package com.beamng.remotecontrol.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.AppLanguage
import com.beamng.remotecontrol.ui.dash.DashArea
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.sampleDashState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.Panel
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground
import kotlin.math.roundToInt

enum class SettingsSection { DRIVING, DASHBOARD, FEEDBACK, GENERAL }

/**
 * Two-pane settings: sections on the left, only the selected section's settings on
 * the right. Every change is written at once; there is no Save.
 */
@Composable
fun SettingsScreen(settings: SettingsManager, onBack: () -> Unit, onSetupGuide: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.DRIVING) }
    BoxWithConstraints(Modifier.fillMaxSize().background(screenBackground()).systemBarsPadding()) {
        val u = maxHeight / 100
        Column(Modifier.fillMaxSize().padding(horizontal = u * 6, vertical = u * 4)) {
            Row(Modifier.fillMaxWidth().height(u * 10), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", color = Graphite.Text, fontSize = (u * 7).asSp(),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(horizontal = u * 2.5f),
                )
                Spacer(Modifier.width(u * 1.5f))
                Text(stringResource(R.string.settings_heading), color = Graphite.Text, fontSize = (u * 5).asSp(), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(u * 2))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                // ---- sections ----
                Column(Modifier.width(u * 46).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 1.6f)) {
                    SectionTab(stringResource(R.string.settings_sec_driving), section == SettingsSection.DRIVING, u) { section = SettingsSection.DRIVING }
                    SectionTab(stringResource(R.string.settings_sec_dashboard), section == SettingsSection.DASHBOARD, u) { section = SettingsSection.DASHBOARD }
                    SectionTab(stringResource(R.string.settings_sec_feedback), section == SettingsSection.FEEDBACK, u) { section = SettingsSection.FEEDBACK }
                    SectionTab(stringResource(R.string.settings_sec_general), section == SettingsSection.GENERAL, u) { section = SettingsSection.GENERAL }
                }
                // ---- the selected section ----
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(u * 4),
                        verticalArrangement = Arrangement.spacedBy(u * 3.2f),
                    ) {
                        when (section) {
                            SettingsSection.DRIVING -> DrivingSection(settings, u)
                            SettingsSection.DASHBOARD -> DashboardSection(settings, u)
                            SettingsSection.FEEDBACK -> FeedbackSection(settings, u)
                            SettingsSection.GENERAL -> GeneralSection(u, onSetupGuide)
                        }
                    }
                }
            }
        }
    }
}

// ==================== sections ====================

@Composable
private fun DrivingSection(settings: SettingsManager, u: Dp) {
    var controlType by remember { mutableIntStateOf(settings.controlType) }
    var lockAngle by remember { mutableFloatStateOf(settings.steeringLockDeg) }
    var deadZone by remember { mutableFloatStateOf(settings.deadZone) }
    var analogPedals by remember { mutableStateOf(settings.isAnalogPedals) }
    var mirrored by remember { mutableStateOf(settings.isMirrored) }

    Heading(stringResource(R.string.settings_control_type), u)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val options = listOf(
            Triple(SettingsManager.CONTROL_GYROSCOPE, R.string.control_gyro, R.string.control_gyro_sub),
            Triple(SettingsManager.CONTROL_SLIDER, R.string.control_slider, R.string.control_slider_sub),
            Triple(SettingsManager.CONTROL_BUTTONS, R.string.control_buttons, R.string.control_buttons_sub),
        )
        for ((type, title, sub) in options) {
            ChoiceCard(
                title = stringResource(title), subtitle = stringResource(sub), selected = controlType == type, u = u,
                modifier = Modifier.weight(1f),
            ) {
                controlType = type
                settings.controlType = type
            }
        }
    }
    // These two only shape tilt steering; elsewhere they would be dead controls.
    if (controlType == SettingsManager.CONTROL_GYROSCOPE) {
        SliderRow(
            stringResource(R.string.settings_lock_angle), stringResource(R.string.settings_lock_angle_sub),
            "${lockAngle.roundToInt()}°", lockAngle, 30f..120f, u,
        ) {
            lockAngle = it
            settings.steeringLockDeg = it
        }
        SliderRow(
            stringResource(R.string.settings_dead_zone), stringResource(R.string.settings_dead_zone_sub),
            "${deadZone.roundToInt()}°", deadZone, 0f..10f, u,
        ) {
            deadZone = it
            settings.deadZone = it
        }
    }
    ToggleRow(stringResource(R.string.settings_analog_pedals), stringResource(R.string.settings_analog_pedals_sub), analogPedals, u) {
        analogPedals = it
        settings.isAnalogPedals = it
    }
    ToggleRow(stringResource(R.string.settings_mirrored), stringResource(R.string.settings_mirrored_sub), mirrored, u) {
        mirrored = it
        settings.isMirrored = it
    }
}

@Composable
private fun DashboardSection(settings: SettingsManager, u: Dp) {
    var layout by remember { mutableStateOf(settings.dashLayout) }
    var dashboardOnly by remember { mutableStateOf(settings.isDashboardOnly) }
    var perfTimer by remember { mutableStateOf(settings.isPerfTimerEnabled) }
    var metric by remember { mutableStateOf(settings.useMetricUnits()) }
    val names = mapOf(
        DashLayout.MODERN to R.string.dash_modern, DashLayout.CLASSIC to R.string.dash_classic,
        DashLayout.RACE to R.string.dash_race, DashLayout.MINIMAL to R.string.dash_minimal,
    )

    Heading(stringResource(R.string.settings_dash_layout), u)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val sample = remember(metric) { sampleDashState(metric) }
        for (option in DashLayout.entries) {
            val selected = option == layout
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(u * 2.5f))
                    .clickable {
                        layout = option
                        settings.dashLayout = option
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .background(Graphite.Bg2, RoundedCornerShape(u * 2.5f))
                        .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, RoundedCornerShape(u * 2.5f))
                        .padding(u * 1.6f),
                ) {
                    // A real, scaled-down instance of the layout — the thumbnail can't drift from the truth.
                    DashArea(option, sample, maxHeight / 82, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(u * 1.4f))
                Text(
                    stringResource(names.getValue(option)), color = if (selected) Graphite.Text else Graphite.Muted,
                    fontSize = (u * 3).asSp(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    ToggleRow(stringResource(R.string.settings_dashboard_only), stringResource(R.string.settings_dashboard_only_sub), dashboardOnly, u) {
        dashboardOnly = it
        settings.isDashboardOnly = it
    }
    ToggleRow(stringResource(R.string.settings_perf_timer), stringResource(R.string.settings_perf_timer_sub), perfTimer, u) {
        perfTimer = it
        settings.isPerfTimerEnabled = it
    }
    RowLabel(stringResource(R.string.settings_units), stringResource(R.string.settings_units_sub), u) {
        Row(horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
            Pill(stringResource(R.string.unit_kmh), metric, u) {
                metric = true
                settings.setUseMetricUnits(true)
            }
            Pill(stringResource(R.string.unit_mph), !metric, u) {
                metric = false
                settings.setUseMetricUnits(false)
            }
        }
    }
}

@Composable
private fun FeedbackSection(settings: SettingsManager, u: Dp) {
    var haptics by remember { mutableStateOf(settings.isHapticEnabled) }
    ToggleRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_sub), haptics, u) {
        haptics = it
        settings.isHapticEnabled = it
    }
}

@Composable
private fun GeneralSection(u: Dp, onSetupGuide: () -> Unit) {
    var current by remember { mutableStateOf(AppLanguage.current()) }
    Heading(stringResource(R.string.settings_language), u)
    Row(horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
        for (language in AppLanguage.entries) {
            Pill(language.nativeName, language == current, u) {
                current = language
                AppLanguage.apply(language)
            }
        }
    }
    RowLabel(stringResource(R.string.guide_title), "", u, Modifier.clip(RoundedCornerShape(u * 2)).clickable(onClick = onSetupGuide)) {
        Text("›", color = Graphite.Muted, fontSize = (u * 5).asSp())
    }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    RowLabel(stringResource(R.string.settings_version), "", u) {
        Text(version, color = Graphite.Muted, fontSize = (u * 3.2f).asSp())
    }
}

// ==================== building blocks ====================

@Composable
private fun SectionTab(label: String, selected: Boolean, u: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Graphite.PanelTop else Color.Transparent)
            .border(1.dp, if (selected) Graphite.Edge else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = u * 3.2f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(u * 0.9f).height(u * 5).background(if (selected) Graphite.Orange else Color.Transparent, RoundedCornerShape(50)))
        Spacer(Modifier.width(u * 3))
        Text(
            label, color = if (selected) Graphite.Text else Graphite.Muted, fontSize = (u * 3.6f).asSp(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Heading(text: String, u: Dp) {
    Text(text, color = Graphite.Muted, fontSize = (u * 2.6f).asSp(), letterSpacing = (u * 0.5f).asSp(), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, u: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0xFF2A1C10) else Graphite.Bg2)
            .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 3, vertical = u * 3.4f),
    ) {
        Text(title, color = Graphite.Text, fontSize = (u * 3.5f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 0.8f))
        Text(subtitle, color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
    }
}

@Composable
private fun RowLabel(title: String, subtitle: String, u: Dp, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Graphite.Text, fontSize = (u * 3.4f).asSp(), fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
            }
        }
        Spacer(Modifier.width(u * 3))
        trailing()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, u: Dp, onChange: (Boolean) -> Unit) {
    RowLabel(title, subtitle, u) {
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Graphite.OnOrange, checkedTrackColor = Graphite.Orange,
                uncheckedThumbColor = Graphite.Muted, uncheckedTrackColor = Graphite.Bg2, uncheckedBorderColor = Graphite.Edge,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    title: String, subtitle: String, value: String, position: Float,
    range: ClosedFloatingPointRange<Float>, u: Dp, onChange: (Float) -> Unit,
) {
    RowLabel(title, subtitle, u) {
        Slider(
            value = position, onValueChange = onChange, valueRange = range,
            modifier = Modifier.width(u * 44),
            colors = SliderDefaults.colors(
                thumbColor = Graphite.Text, activeTrackColor = Graphite.Orange, inactiveTrackColor = Graphite.Track,
            ),
        )
        Spacer(Modifier.width(u * 2))
        Text(value, color = Graphite.Text, fontSize = (u * 3.4f).asSp(), fontWeight = FontWeight.Bold, modifier = Modifier.width(u * 9))
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, u: Dp, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Graphite.OnOrange else Graphite.Text, fontSize = (u * 3.1f).asSp(),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Graphite.Orange else Graphite.Track)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 3.5f, vertical = u * 1.8f),
    )
}
