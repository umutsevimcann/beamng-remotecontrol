package com.beamng.remotecontrol.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.AppLanguage
import com.beamng.remotecontrol.ui.dash.DashArea
import com.beamng.remotecontrol.ui.dash.DashLayout
import com.beamng.remotecontrol.ui.dash.sampleDashState
import com.beamng.remotecontrol.ui.theme.Graphite
import com.beamng.remotecontrol.ui.theme.Panel
import com.beamng.remotecontrol.ui.theme.Palette
import com.beamng.remotecontrol.ui.theme.LightPalette
import com.beamng.remotecontrol.ui.theme.DarkPalette
import androidx.compose.ui.graphics.drawscope.clipPath
import com.beamng.remotecontrol.ui.theme.ThemeMode
import com.beamng.remotecontrol.ui.theme.ThemeState
import com.beamng.remotecontrol.ui.theme.asSp
import com.beamng.remotecontrol.ui.theme.screenBackground
import kotlin.math.roundToInt

enum class SettingsSection { DRIVING, DASHBOARD, FEEDBACK, MOD, GENERAL }

/**
 * Two-pane settings: sections on the left, only the selected section's settings on
 * the right. Every change is written at once; there is no Save.
 */
@Composable
fun SettingsScreen(settings: SettingsManager, onBack: () -> Unit, onSetupGuide: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.DRIVING) }
    BoxWithConstraints(Modifier.fillMaxSize().background(screenBackground()).safeDrawingPadding()) {
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
                    SectionTab(stringResource(R.string.settings_sec_mod), section == SettingsSection.MOD, u) { section = SettingsSection.MOD }
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
                            SettingsSection.MOD -> ModSection(settings, u)
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
    // IntrinsicSize.Max: all three cards take the height of the tallest one,
    // whatever length the subtitles have in each language.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val options = listOf(
            Triple(SettingsManager.CONTROL_GYROSCOPE, R.string.control_gyro, R.string.control_gyro_sub),
            Triple(SettingsManager.CONTROL_SLIDER, R.string.control_slider, R.string.control_slider_sub),
            Triple(SettingsManager.CONTROL_BUTTONS, R.string.control_buttons, R.string.control_buttons_sub),
        )
        for ((type, title, sub) in options) {
            ChoiceCard(
                title = stringResource(title), subtitle = stringResource(sub), selected = controlType == type, u = u,
                glyph = type, modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                controlType = type
                settings.controlType = type
            }
        }
    }
    // These two only shape tilt steering; elsewhere they would be dead controls.
    if (controlType == SettingsManager.CONTROL_GYROSCOPE) {
        CardBox(u) {
        SliderRow(
            stringResource(R.string.settings_lock_angle), stringResource(R.string.settings_lock_angle_sub),
            "${lockAngle.roundToInt()}°", lockAngle, 30f..120f, u,
        ) {
            lockAngle = it
            settings.steeringLockDeg = it
        }
        Spacer(Modifier.height(u * 2.6f))
        SliderRow(
            stringResource(R.string.settings_dead_zone), stringResource(R.string.settings_dead_zone_sub),
            "${deadZone.roundToInt()}°", deadZone, 0f..10f, u,
        ) {
            deadZone = it
            settings.deadZone = it
        }
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.settings_analog_pedals), stringResource(R.string.settings_analog_pedals_sub),
            analogPedals, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            analogPedals = it
            settings.isAnalogPedals = it
        }
        ToggleCard(
            stringResource(R.string.settings_mirrored), stringResource(R.string.settings_mirrored_sub),
            mirrored, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            mirrored = it
            settings.isMirrored = it
        }
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
                        .background(Graphite.Inset, RoundedCornerShape(u * 2.5f))
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
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.settings_dashboard_only), stringResource(R.string.settings_dashboard_only_sub),
            dashboardOnly, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            dashboardOnly = it
            settings.isDashboardOnly = it
        }
        ToggleCard(
            stringResource(R.string.settings_perf_timer), stringResource(R.string.settings_perf_timer_sub),
            perfTimer, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            perfTimer = it
            settings.isPerfTimerEnabled = it
        }
    }
    CardBox(u) {
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
}

@Composable
private fun FeedbackSection(settings: SettingsManager, u: Dp) {
    var haptics by remember { mutableStateOf(settings.isHapticEnabled) }
    ToggleCard(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_sub), haptics, u, Modifier.fillMaxWidth()) {
        haptics = it
        settings.isHapticEnabled = it
    }
    // What the phone actually reacts to, so the switch isn't a mystery.
    Heading(stringResource(R.string.settings_haptics_when), u)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val events = listOf(
            R.string.haptic_gear to R.string.haptic_gear_sub,
            R.string.haptic_impact to R.string.haptic_impact_sub,
            R.string.haptic_abs to R.string.haptic_abs_sub,
        )
        for ((title, sub) in events) {
            CardBox(u, Modifier.weight(1f).fillMaxHeight().alpha(if (haptics) 1f else 0.45f)) {
                Text(stringResource(title), color = Graphite.Text, fontSize = (u * 3.3f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(u * 0.8f))
                Text(stringResource(sub), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
            }
        }
    }
}

/**
 * The optional RemoteControlPro game mod: whether we have ever met it, what it adds,
 * how to install it, and which of its keys the drive screen shows.
 */
@Composable
private fun ModSection(settings: SettingsManager, u: Dp) {
    var shift by remember { mutableStateOf(settings.modShowShift) }
    var handbrake by remember { mutableStateOf(settings.modShowHandbrake) }
    var horn by remember { mutableStateOf(settings.modShowHorn) }
    var lights by remember { mutableStateOf(settings.modShowLights) }
    val modVersion = settings.lastModVersion
    val seen = modVersion.isNotEmpty()

    CardBox(u, selected = seen) {
        Text(stringResource(R.string.mod_status_title), color = Graphite.Text, fontSize = (u * 3.6f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 0.8f))
        Text(
            if (seen) stringResource(R.string.mod_status_seen, settings.lastPcName.ifEmpty { "PC" }, modVersion)
            else stringResource(R.string.mod_status_never),
            color = if (seen) Graphite.Orange else Graphite.Muted, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4f).asSp(),
        )
    }

    Heading(stringResource(R.string.mod_what_title), u)
    CardBox(u) {
        val points = listOf(
            R.string.mod_what_1, R.string.mod_what_2, R.string.mod_what_3, R.string.mod_what_4, R.string.mod_what_5,
        )
        for ((i, point) in points.withIndex()) {
            if (i > 0) Spacer(Modifier.height(u * 1.2f))
            Row {
                Text("•", color = Graphite.Orange, fontSize = (u * 3f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(u * 1.6f))
                Text(stringResource(point), color = Graphite.Text, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4f).asSp())
            }
        }
    }

    Heading(stringResource(R.string.mod_install_title), u)
    CardBox(u) {
        val steps = listOf(R.string.mod_install_1, R.string.mod_install_2, R.string.mod_install_3)
        for ((i, step) in steps.withIndex()) {
            if (i > 0) Spacer(Modifier.height(u * 1.6f))
            Row {
                Text("${i + 1}", color = Graphite.Orange, fontSize = (u * 3.4f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(u * 2f))
                Text(stringResource(step), color = Graphite.Text, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4f).asSp())
            }
        }
        Spacer(Modifier.height(u * 1.6f))
        Text(stringResource(R.string.mod_install_note), color = Graphite.Muted, fontSize = (u * 2.6f).asSp(), lineHeight = (u * 3.7f).asSp())
    }

    Heading(stringResource(R.string.mod_keys_title), u)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_shift), stringResource(R.string.mod_show_shift_sub),
            shift, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            shift = it
            settings.modShowShift = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_handbrake), stringResource(R.string.mod_show_handbrake_sub),
            handbrake, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            handbrake = it
            settings.modShowHandbrake = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_horn), stringResource(R.string.mod_show_horn_sub),
            horn, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            horn = it
            settings.modShowHorn = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_lights), stringResource(R.string.mod_show_lights_sub),
            lights, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            lights = it
            settings.modShowLights = it
        }
    }
}

@Composable
private fun GeneralSection(u: Dp, onSetupGuide: () -> Unit) {
    val context = LocalContext.current

    // ---- appearance: three swatches that look like what they select ----
    Heading(stringResource(R.string.settings_theme), u)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val options = listOf(
            ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.SYSTEM to R.string.theme_system,
        )
        for ((mode, label) in options) {
            ThemeSwatch(mode, stringResource(label), ThemeState.mode == mode, u, Modifier.weight(1f)) {
                ThemeState.mode = mode // repaints every screen at once
                SettingsManager.getInstance(context).themeMode = mode
            }
        }
    }

    // ---- language ----
    var current by remember { mutableStateOf(AppLanguage.current()) }
    Heading(stringResource(R.string.settings_language), u)
    // Three to a row: nine languages make a 3×3 grid.
    for (rowLanguages in AppLanguage.entries.chunked(3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
            for (language in rowLanguages) {
                val selected = language == current
                val shape = RoundedCornerShape(u * 2.5f)
                Row(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (selected) Graphite.Selected else Graphite.Inset)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, shape)
                        .clickable {
                            current = language
                            AppLanguage.apply(language)
                        }
                        .padding(horizontal = u * 2.4f, vertical = u * 2.2f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(language.flag, fontSize = (u * 4.4f).asSp())
                    Spacer(Modifier.width(u * 1.8f))
                    Text(
                        language.nativeName, color = Graphite.Text, fontSize = (u * 3.1f).asSp(),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
                    )
                }
            }
            // keep the last row's cards the same width as the others
            repeat(3 - rowLanguages.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---- help + about, side by side ----
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        val shape = RoundedCornerShape(u * 2.5f)
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(Graphite.Inset)
                .border(1.dp, Graphite.Edge, shape)
                .clickable(onClick = onSetupGuide)
                .padding(u * 3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.guide_title), color = Graphite.Text, fontSize = (u * 3.5f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(u * 0.8f))
                Text(stringResource(R.string.settings_guide_sub), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
            }
            Text("›", color = Graphite.Orange, fontSize = (u * 6).asSp(), fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Graphite.Inset, shape)
                .border(1.dp, Graphite.Edge, shape)
                .padding(u * 3),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BeamNG ", color = Graphite.Text, fontSize = (u * 3.5f).asSp(), fontWeight = FontWeight.Bold)
                Text("Remote", color = Graphite.Orange, fontSize = (u * 3.5f).asSp(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(u * 1.6f))
                Text(
                    version, color = Graphite.Muted, fontSize = (u * 2.5f).asSp(), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Graphite.Track, RoundedCornerShape(u * 1.2f)).padding(horizontal = u * 1.4f, vertical = u * 0.5f),
                )
            }
            Spacer(Modifier.height(u * 0.8f))
            Text(stringResource(R.string.settings_about_sub), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
        }
    }
}

/** A miniature screen in the palette it selects; System shows both, split diagonally. */
@Composable
private fun ThemeSwatch(mode: ThemeMode, label: String, selected: Boolean, u: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Graphite.Selected else Graphite.Inset)
            .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, shape)
            .clickable(onClick = onClick)
            .padding(u * 2.2f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(u * 11)) {
            val r = CornerRadius(size.height * 0.16f)
            fun screen(p: Palette) {
                drawRoundRect(p.bg1, Offset.Zero, size, r)
                // a panel, a line of text and the accent — enough to read as a screen
                drawRoundRect(p.panelTop, Offset(size.width * 0.08f, size.height * 0.18f), Size(size.width * 0.50f, size.height * 0.64f), CornerRadius(size.height * 0.10f))
                drawRoundRect(p.text, Offset(size.width * 0.14f, size.height * 0.32f), Size(size.width * 0.26f, size.height * 0.11f), CornerRadius(size.height * 0.05f))
                drawRoundRect(p.muted, Offset(size.width * 0.14f, size.height * 0.54f), Size(size.width * 0.36f, size.height * 0.07f), CornerRadius(size.height * 0.04f))
                drawRoundRect(p.orange, Offset(size.width * 0.66f, size.height * 0.36f), Size(size.width * 0.26f, size.height * 0.28f), CornerRadius(size.height * 0.10f))
            }
            when (mode) {
                ThemeMode.DARK -> screen(DarkPalette)
                ThemeMode.LIGHT -> screen(LightPalette)
                ThemeMode.SYSTEM -> {
                    screen(DarkPalette)
                    val half = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    clipPath(half) { screen(LightPalette) }
                }
            }
            drawRoundRect(Graphite.Edge, Offset.Zero, size, r, style = Stroke(1.5f))
        }
        Spacer(Modifier.height(u * 1.6f))
        Text(
            label, color = if (selected) Graphite.Text else Graphite.Muted, fontSize = (u * 3.1f).asSp(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
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
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, u: Dp, glyph: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Graphite.Selected else Graphite.Inset)
            .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 3, vertical = u * 3.4f),
    ) {
        ControlGlyph(glyph, if (selected) Graphite.Orange else Graphite.Muted, Modifier.size(u * 9))
        Spacer(Modifier.height(u * 1.6f))
        Text(title, color = Graphite.Text, fontSize = (u * 3.5f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 0.8f))
        Text(subtitle, color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
    }
}

/** A small drawing of what the steering side becomes: tilted phone, wheel, or two arrows. */
@Composable
private fun ControlGlyph(controlType: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(w * 0.07f, cap = StrokeCap.Round)
        when (controlType) {
            SettingsManager.CONTROL_GYROSCOPE -> rotate(-18f) {
                // a phone, tilted
                drawRoundRect(color, Offset(w * 0.12f, h * 0.30f), Size(w * 0.76f, h * 0.40f), CornerRadius(w * 0.08f), style = stroke)
                drawCircle(color, w * 0.03f, Offset(w * 0.78f, h * 0.50f))
            }
            SettingsManager.CONTROL_SLIDER -> {
                // a steering wheel
                drawCircle(color, w * 0.40f, center, style = stroke)
                drawCircle(color, w * 0.10f, center)
                drawLine(color, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.88f, h * 0.50f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(w * 0.50f, h * 0.88f), stroke.width, StrokeCap.Round)
            }
            else -> {
                // two arrows
                val left = Path().apply { moveTo(w * 0.40f, h * 0.22f); lineTo(w * 0.10f, h * 0.50f); lineTo(w * 0.40f, h * 0.78f); close() }
                val right = Path().apply { moveTo(w * 0.60f, h * 0.22f); lineTo(w * 0.90f, h * 0.50f); lineTo(w * 0.60f, h * 0.78f); close() }
                drawPath(left, color)
                drawPath(right, color)
            }
        }
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

/** The recessed card every settings group sits in. */
@Composable
private fun CardBox(u: Dp, modifier: Modifier = Modifier.fillMaxWidth(), selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Column(
        modifier
            .background(if (selected) Graphite.Selected else Graphite.Inset, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, shape)
            .padding(u * 3),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** An on/off setting as a card: the whole card is the target, and it lights up when on. */
@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, u: Dp, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(u * 2.5f)
    Row(
        modifier
            .clip(shape)
            .background(if (checked) Graphite.Selected else Graphite.Inset)
            .border(if (checked) 2.dp else 1.dp, if (checked) Graphite.Orange else Graphite.Edge, shape)
            .clickable { onChange(!checked) }
            .padding(u * 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Graphite.Text, fontSize = (u * 3.4f).asSp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(u * 0.8f))
            Text(subtitle, color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
        }
        Spacer(Modifier.width(u * 2))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Graphite.OnOrange, checkedTrackColor = Graphite.Orange,
                uncheckedThumbColor = Graphite.Muted, uncheckedTrackColor = Graphite.Track, uncheckedBorderColor = Graphite.Edge,
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
