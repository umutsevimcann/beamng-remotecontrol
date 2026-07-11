package com.beamng.remotecontrol.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beamng.remotecontrol.R
import com.beamng.remotecontrol.settings.SettingsManager
import com.beamng.remotecontrol.ui.theme.NightGarage
import com.beamng.remotecontrol.ui.theme.NightGarageTheme

@Composable
fun SettingsScreen(settings: SettingsManager) {
    var controlType by remember { mutableIntStateOf(settings.controlType) }
    var sensitivity by remember { mutableFloatStateOf(settings.sensitivity) }
    var deadZone by remember { mutableFloatStateOf(settings.deadZone) }
    var metric by remember { mutableStateOf(settings.useMetricUnits()) }
    var dashboardOnly by remember { mutableStateOf(settings.isDashboardOnly) }
    var haptics by remember { mutableStateOf(settings.isHapticEnabled) }
    var perfTimer by remember { mutableStateOf(settings.isPerfTimerEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cockpitBackground())
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(R.string.settings_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            color = NightGarage.Amber,
        )
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = NightGarage.TextBright,
        )
        Spacer(Modifier.height(18.dp))

        GarageCard(title = stringResource(R.string.settings_language)) {
            var current by remember { mutableStateOf(AppLanguage.current()) }
            AppLanguage.entries.forEach { language ->
                LanguageRow(
                    language = language,
                    selected = language == current,
                ) {
                    current = language
                    AppLanguage.apply(language)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        GarageCard(title = stringResource(R.string.settings_control_type)) {
            SegmentedRow(
                options = listOf(
                    stringResource(R.string.control_gyro),
                    stringResource(R.string.control_buttons),
                    stringResource(R.string.control_slider),
                ),
                selectedIndex = when (controlType) {
                    SettingsManager.CONTROL_BUTTONS -> 1
                    SettingsManager.CONTROL_SLIDER -> 2
                    else -> 0
                },
                onSelect = { index ->
                    controlType = when (index) {
                        1 -> SettingsManager.CONTROL_BUTTONS
                        2 -> SettingsManager.CONTROL_SLIDER
                        else -> SettingsManager.CONTROL_GYROSCOPE
                    }
                    settings.controlType = controlType
                },
            )
        }
        Spacer(Modifier.height(14.dp))

        GarageCard(title = "") {
            SettingRow(
                title = stringResource(R.string.settings_sensitivity),
                subtitle = stringResource(R.string.settings_sensitivity_sub),
                value = "${Math.round(sensitivity * 100)}%",
            ) {
                GarageSlider(
                    value = sensitivity,
                    range = 0.1f..1.0f,
                    onChange = {
                        sensitivity = it
                        settings.sensitivity = it
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_dead_zone),
                subtitle = stringResource(R.string.settings_dead_zone_sub),
                value = "${Math.round(deadZone)}°",
            ) {
                GarageSlider(
                    value = deadZone,
                    range = 0f..10f,
                    onChange = {
                        deadZone = it
                        settings.deadZone = it
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_units),
                subtitle = stringResource(R.string.settings_units_sub),
                value = "",
            ) {
                SegmentedRow(
                    options = listOf(
                        stringResource(R.string.unit_kmh),
                        stringResource(R.string.unit_mph),
                    ),
                    selectedIndex = if (metric) 0 else 1,
                    onSelect = {
                        metric = it == 0
                        settings.setUseMetricUnits(metric)
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        GarageCard(title = "") {
            ToggleRow(
                title = stringResource(R.string.settings_dashboard_only),
                subtitle = stringResource(R.string.settings_dashboard_only_sub),
                checked = dashboardOnly,
            ) {
                dashboardOnly = it
                settings.isDashboardOnly = it
            }
            ToggleRow(
                title = stringResource(R.string.settings_haptics),
                subtitle = stringResource(R.string.settings_haptics_sub),
                checked = haptics,
            ) {
                haptics = it
                settings.isHapticEnabled = it
            }
            ToggleRow(
                title = stringResource(R.string.settings_perf_timer),
                subtitle = stringResource(R.string.settings_perf_timer_sub),
                checked = perfTimer,
            ) {
                perfTimer = it
                settings.isPerfTimerEnabled = it
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_instant_save),
            style = MaterialTheme.typography.bodyMedium,
            color = NightGarage.TextFaint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// ==================== rows & controls ====================

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .height(16.dp)
                .width(16.dp)
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(Color(0xFFFF8A3A), Color(0xFFE05A00)))
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFF100C08), Color(0xFF100C08)),
                        )
                    },
                    androidx.compose.foundation.shape.CircleShape,
                ),
        )
        Text(
            language.nativeName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) NightGarage.TextBright else NightGarage.TextDim,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    value: String,
    control: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = NightGarage.Text)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NightGarage.TextFaint)
            }
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodyLarge, color = NightGarage.Amber)
            }
        }
        control()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = NightGarage.Text)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NightGarage.TextFaint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NightGarage.AmberHot,
                checkedThumbColor = NightGarage.TextBright,
                uncheckedTrackColor = Color(0xFF100C08),
                uncheckedThumbColor = NightGarage.TextDim,
                uncheckedBorderColor = NightGarage.PanelEdge,
            ),
        )
    }
}

@Composable
private fun GarageSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFFD9A0),
            activeTrackColor = NightGarage.AmberHot,
            inactiveTrackColor = Color(0xFF100C08),
        ),
    )
}

@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color(0xFF100C08), RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(Color(0xFFFF8A3A), Color(0xFFE05A00)))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                        RoundedCornerShape(7.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) NightGarage.OnAmber else NightGarage.TextDim,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SettingsScreenPreview() {
    // Preview note: uses a real SettingsManager only at runtime; preview shows layout.
    NightGarageTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(cockpitBackground())
                .padding(24.dp),
        ) {
            GarageCard(title = "CONTROL TYPE") {
                SegmentedRow(listOf("Gyro", "Buttons", "Slider"), 0) {}
            }
        }
    }
}
