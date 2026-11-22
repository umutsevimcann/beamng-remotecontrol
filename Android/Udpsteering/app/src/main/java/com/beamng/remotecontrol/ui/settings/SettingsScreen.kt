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
import androidx.compose.ui.text.style.TextAlign
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
import com.beamng.remotecontrol.ui.drive.drawWheel
import com.beamng.remotecontrol.ui.drive.WheelStyle
import com.beamng.remotecontrol.protocol.ModProtocol
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

enum class SettingsSection { DRIVING, DASHBOARD, FEEDBACK, MOD, WALK, GENERAL }

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
                    SectionTab(stringResource(R.string.settings_sec_walk), section == SettingsSection.WALK, u) { section = SettingsSection.WALK }
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
                            SettingsSection.WALK -> WalkSection(settings, u)
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
    var selfAlign by remember { mutableStateOf(settings.isWheelSelfAlign) }
    var wheelStyle by remember { mutableStateOf(settings.wheelStyle) }
    var splitPedals by remember { mutableStateOf(settings.isSplitPedals) }
    var wheelReturn by remember { mutableFloatStateOf(settings.wheelReturnPercent.toFloat()) }

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
    // With tilt the phone steers itself, so both thumbs are free for pedals.
    if (controlType == SettingsManager.CONTROL_GYROSCOPE) {
        OptionCard(
            stringResource(R.string.settings_pedals), stringResource(R.string.settings_pedals_sub),
            listOf(stringResource(R.string.settings_pedals_together), stringResource(R.string.settings_pedals_split)),
            if (splitPedals) 1 else 0, u,
        ) {
            splitPedals = it == 1
            settings.isSplitPedals = splitPedals
        }
    }
    // Arrow keys draw no wheel.
    if (controlType != SettingsManager.CONTROL_BUTTONS) {
        Heading(stringResource(R.string.settings_wheel_style), u)
        for (rowStyles in WheelStyle.entries.chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
                for (option in rowStyles) {
                    val selected = option == wheelStyle
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(u * 2.5f))
                            .clickable {
                                wheelStyle = option
                                settings.wheelStyle = option
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.35f)
                                .background(Graphite.Inset, RoundedCornerShape(u * 2.5f))
                                .border(if (selected) 2.dp else 1.dp, if (selected) Graphite.Orange else Graphite.Edge, RoundedCornerShape(u * 2.5f))
                                .padding(u * 1.6f),
                        ) { drawWheel(option) }
                        Spacer(Modifier.height(u * 1.4f))
                        Text(
                            stringResource(option.nameRes), color = if (selected) Graphite.Text else Graphite.Muted,
                            fontSize = (u * 3).asSp(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    // Only the on-screen wheel can be let go of.
    if (controlType == SettingsManager.CONTROL_SLIDER) {
        ToggleCard(
            stringResource(R.string.settings_self_align), stringResource(R.string.settings_self_align_sub),
            selfAlign, u, Modifier.fillMaxWidth(),
        ) {
            selfAlign = it
            settings.isWheelSelfAlign = it
        }
        if (selfAlign) {
            CardBox(u) {
                SliderRow(
                    stringResource(R.string.settings_wheel_return), stringResource(R.string.settings_wheel_return_sub),
                    "${wheelReturn.roundToInt()}%", wheelReturn,
                    ModProtocol.WHEEL_RETURN_MIN.toFloat()..ModProtocol.WHEEL_RETURN_MAX.toFloat(), u,
                ) {
                    wheelReturn = it
                    settings.wheelReturnPercent = it.roundToInt()
                }
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

    Heading(stringResource(R.string.settings_dash_layout), u)
    val sample = remember(metric) { sampleDashState(metric) }
    // Four thumbnails to a row.
    for (rowLayouts in DashLayout.entries.chunked(4)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        for (option in rowLayouts) {
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
                    stringResource(option.nameRes), color = if (selected) Graphite.Text else Graphite.Muted,
                    fontSize = (u * 3).asSp(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
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
    // One switch per event; hidden while vibration is off altogether.
    if (haptics) {
        var gear by remember { mutableStateOf(settings.hapticGear) }
        var impact by remember { mutableStateOf(settings.hapticImpact) }
        var landing by remember { mutableStateOf(settings.hapticLanding) }
        var abs by remember { mutableStateOf(settings.hapticAbs) }
        var traction by remember { mutableStateOf(settings.hapticTraction) }
        var redline by remember { mutableStateOf(settings.hapticRedline) }
        Heading(stringResource(R.string.settings_haptics_when), u)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
            ToggleCard(
                stringResource(R.string.haptic_gear), stringResource(R.string.haptic_gear_sub),
                gear, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                gear = it
                settings.hapticGear = it
            }
            ToggleCard(
                stringResource(R.string.haptic_impact), stringResource(R.string.haptic_impact_sub),
                impact, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                impact = it
                settings.hapticImpact = it
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
            ToggleCard(
                stringResource(R.string.haptic_landing), stringResource(R.string.haptic_landing_sub),
                landing, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                landing = it
                settings.hapticLanding = it
            }
            ToggleCard(
                stringResource(R.string.haptic_abs), stringResource(R.string.haptic_abs_sub),
                abs, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                abs = it
                settings.hapticAbs = it
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
            ToggleCard(
                stringResource(R.string.haptic_traction), stringResource(R.string.haptic_traction_sub),
                traction, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                traction = it
                settings.hapticTraction = it
            }
            ToggleCard(
                stringResource(R.string.haptic_redline), stringResource(R.string.haptic_redline_sub),
                redline, u, Modifier.weight(1f).fillMaxHeight(),
            ) {
                redline = it
                settings.hapticRedline = it
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
    var driveMode by remember { mutableStateOf(settings.modShowDriveMode) }
    var fourWd by remember { mutableStateOf(settings.modShow4wd) }
    var nitrous by remember { mutableStateOf(settings.modShowNitrous) }
    var doors by remember { mutableStateOf(settings.modShowDoors) }
    var lowRange by remember { mutableStateOf(settings.modShowLowRange) }
    var diffLock by remember { mutableStateOf(settings.modShowDiffLock) }
    var camera by remember { mutableStateOf(settings.modShowCamera) }
    var gearbox by remember { mutableStateOf(settings.modShowGearbox) }
    var reset by remember { mutableStateOf(settings.modShowReset) }
    var pause by remember { mutableStateOf(settings.modShowPause) }
    var map by remember { mutableStateOf(settings.modShowMap) }
    var walk by remember { mutableStateOf(settings.modShowWalk) }
    var interact by remember { mutableStateOf(settings.modShowInteract) }
    var menu by remember { mutableStateOf(settings.modShowMenu) }
    var lookDrag by remember { mutableStateOf(settings.isLookDrag) }
    var handbrakeHold by remember { mutableStateOf(settings.isHandbrakeHold) }
    var hornPlace by remember { mutableIntStateOf(settings.hornPlace) }
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
            R.string.mod_what_1, R.string.mod_what_2, R.string.mod_what_3, R.string.mod_what_4, R.string.mod_what_6, R.string.mod_what_5,
            R.string.mod_what_7, R.string.mod_what_8, R.string.mod_what_9, R.string.mod_what_10,
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
    if (handbrake) {
        OptionCard(
            stringResource(R.string.settings_handbrake_style), stringResource(R.string.settings_handbrake_style_sub),
            listOf(stringResource(R.string.settings_handbrake_latch), stringResource(R.string.settings_handbrake_hold)),
            if (handbrakeHold) 1 else 0, u,
        ) {
            handbrakeHold = it == 1
            settings.isHandbrakeHold = handbrakeHold
        }
    }
    if (horn) {
        OptionCard(
            stringResource(R.string.settings_horn_place), stringResource(R.string.settings_horn_place_sub),
            listOf(
                stringResource(R.string.settings_horn_key), stringResource(R.string.settings_horn_hub),
                stringResource(R.string.settings_horn_both),
            ),
            hornPlace, u,
        ) {
            hornPlace = it
            settings.hornPlace = it
        }
    }
    // The second page of the key row (mod 2.1+): one switch per group.
    Heading(stringResource(R.string.mod_vehicle_title), u)
    Text(stringResource(R.string.mod_vehicle_note), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_drive_mode), stringResource(R.string.mod_show_drive_mode_sub),
            driveMode, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            driveMode = it
            settings.modShowDriveMode = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_4wd), stringResource(R.string.mod_show_4wd_sub),
            fourWd, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            fourWd = it
            settings.modShow4wd = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_nitrous), stringResource(R.string.mod_show_nitrous_sub),
            nitrous, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            nitrous = it
            settings.modShowNitrous = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_doors), stringResource(R.string.mod_show_doors_sub),
            doors, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            doors = it
            settings.modShowDoors = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_low_range), stringResource(R.string.mod_show_low_range_sub),
            lowRange, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            lowRange = it
            settings.modShowLowRange = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_diff_lock), stringResource(R.string.mod_show_diff_lock_sub),
            diffLock, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            diffLock = it
            settings.modShowDiffLock = it
        }
    }
    // The game page (mod 2.2+): one switch per key.
    Heading(stringResource(R.string.mod_game_title), u)
    Text(stringResource(R.string.mod_game_note), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_camera), stringResource(R.string.mod_show_camera_sub),
            camera, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            camera = it
            settings.modShowCamera = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_gearbox), stringResource(R.string.mod_show_gearbox_sub),
            gearbox, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            gearbox = it
            settings.modShowGearbox = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_reset), stringResource(R.string.mod_show_reset_sub),
            reset, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            reset = it
            settings.modShowReset = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_pause), stringResource(R.string.mod_show_pause_sub),
            pause, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            pause = it
            settings.modShowPause = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_map), stringResource(R.string.mod_show_map_sub),
            map, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            map = it
            settings.modShowMap = it
        }
        ToggleCard(
            stringResource(R.string.mod_show_walk), stringResource(R.string.mod_show_walk_sub),
            walk, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            walk = it
            settings.modShowWalk = it
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(
            stringResource(R.string.mod_show_interact), stringResource(R.string.mod_show_interact_sub),
            interact, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            interact = it
            settings.modShowInteract = it
        }
        ToggleCard(
            stringResource(R.string.mod_look_drag), stringResource(R.string.mod_look_drag_sub),
            lookDrag, u, Modifier.weight(1f).fillMaxHeight(),
        ) {
            lookDrag = it
            settings.isLookDrag = it
        }
    }
    ToggleCard(stringResource(R.string.mod_show_menu), stringResource(R.string.mod_show_menu_sub), menu, u, Modifier.fillMaxWidth()) {
        menu = it
        settings.modShowMenu = it
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

/** On foot (mod 2.3+): the walking screen that replaces the drive screen when you get out. */
@Composable
private fun WalkSection(settings: SettingsManager, u: Dp) {
    var sprintKey by remember { mutableStateOf(settings.isWalkSprintKey) }
    var stickRight by remember { mutableStateOf(settings.isWalkStickRight) }
    var lookSpeed by remember { mutableFloatStateOf(settings.walkLookSpeed) }
    var crouchToggle by remember { mutableStateOf(settings.isWalkCrouchToggle) }
    var invert by remember { mutableStateOf(settings.isWalkInvertLook) }
    var gyro by remember { mutableStateOf(settings.isWalkGyroAim) }
    var gyroSpeed by remember { mutableFloatStateOf(settings.walkGyroSpeed) }
    var interact by remember { mutableStateOf(settings.walkShowInteract) }
    var view by remember { mutableStateOf(settings.walkShowView) }

    // Said first and plainly: none of this exists without the mod.
    CardBox(u, selected = true) {
        Text(stringResource(R.string.walk_title), color = Graphite.Text, fontSize = (u * 3.6f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 0.8f))
        Text(stringResource(R.string.walk_mod_note), color = Graphite.Orange, fontSize = (u * 2.9f).asSp(), lineHeight = (u * 4f).asSp())
        Spacer(Modifier.height(u * 0.8f))
        Text(stringResource(R.string.walk_note), color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
    }
    OptionCard(
        stringResource(R.string.walk_stick), stringResource(R.string.walk_stick_sub),
        listOf(stringResource(R.string.walk_stick_left), stringResource(R.string.walk_stick_right)),
        if (stickRight) 1 else 0, u,
    ) {
        stickRight = it == 1
        settings.isWalkStickRight = stickRight
    }
    OptionCard(
        stringResource(R.string.walk_sprint), stringResource(R.string.walk_sprint_sub),
        listOf(stringResource(R.string.walk_sprint_edge), stringResource(R.string.walk_sprint_key_option)),
        if (sprintKey) 1 else 0, u,
    ) {
        sprintKey = it == 1
        settings.isWalkSprintKey = sprintKey
    }
    CardBox(u) {
        SliderRow(
            stringResource(R.string.walk_look), stringResource(R.string.walk_look_sub),
            "×%.1f".format(lookSpeed), lookSpeed, 0.3f..3f, u,
        ) {
            lookSpeed = it
            settings.walkLookSpeed = it
        }
    }
    OptionCard(
        stringResource(R.string.walk_crouch), stringResource(R.string.walk_crouch_sub),
        listOf(stringResource(R.string.walk_crouch_hold), stringResource(R.string.walk_crouch_toggle)),
        if (crouchToggle) 1 else 0, u,
    ) {
        crouchToggle = it == 1
        settings.isWalkCrouchToggle = crouchToggle
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(stringResource(R.string.walk_invert), stringResource(R.string.walk_invert_sub), invert, u, Modifier.weight(1f).fillMaxHeight()) {
            invert = it
            settings.isWalkInvertLook = it
        }
        ToggleCard(stringResource(R.string.walk_gyro), stringResource(R.string.walk_gyro_sub), gyro, u, Modifier.weight(1f).fillMaxHeight()) {
            gyro = it
            settings.isWalkGyroAim = it
        }
    }
    if (gyro) {
        CardBox(u) {
            SliderRow(
                stringResource(R.string.walk_gyro_speed), stringResource(R.string.walk_gyro_speed_sub),
                "×%.1f".format(gyroSpeed), gyroSpeed, 0.3f..3f, u,
            ) {
                gyroSpeed = it
                settings.walkGyroSpeed = it
            }
        }
    }
    Heading(stringResource(R.string.walk_keys_title), u)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 2.4f)) {
        ToggleCard(stringResource(R.string.walk_show_interact), stringResource(R.string.walk_show_interact_sub), interact, u, Modifier.weight(1f).fillMaxHeight()) {
            interact = it
            settings.walkShowInteract = it
        }
        ToggleCard(stringResource(R.string.walk_show_view), stringResource(R.string.walk_show_view_sub), view, u, Modifier.weight(1f).fillMaxHeight()) {
            view = it
            settings.walkShowView = it
        }
    }
}

/** A setting with a few named options side by side; the chosen one is lit. */
@Composable
private fun OptionCard(title: String, subtitle: String, options: List<String>, selected: Int, u: Dp, onSelect: (Int) -> Unit) {
    CardBox(u) {
        Text(title, color = Graphite.Text, fontSize = (u * 3.3f).asSp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(u * 0.6f))
        Text(subtitle, color = Graphite.Muted, fontSize = (u * 2.7f).asSp(), lineHeight = (u * 3.8f).asSp())
        Spacer(Modifier.height(u * 1.8f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(u * 1.6f)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val shape = RoundedCornerShape(u * 2f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (on) Graphite.Selected else Graphite.Inset)
                        .border(if (on) 2.dp else 1.dp, if (on) Graphite.Orange else Graphite.Edge, shape)
                        .clickable { onSelect(i) }
                        .padding(horizontal = u * 1.6f, vertical = u * 2f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, color = if (on) Graphite.Text else Graphite.Muted, fontSize = (u * 2.9f).asSp(),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
