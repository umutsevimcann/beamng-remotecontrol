package com.beamng.remotecontrol.ui.dash

import androidx.annotation.StringRes
import com.beamng.remotecontrol.R

/**
 * The selectable gauge layouts of the drive screen. [key] is what gets persisted;
 * adding a layout means adding an entry here (with its name) and a branch in DashArea.
 */
enum class DashLayout(val key: String, @StringRes val nameRes: Int) {
    MODERN("modern", R.string.dash_modern),
    CLASSIC("classic", R.string.dash_classic),
    RACE("race", R.string.dash_race),
    MINIMAL("minimal", R.string.dash_minimal),
    RETRO("retro", R.string.dash_retro),
    COCKPIT("cockpit", R.string.dash_cockpit),
    DRIFT("drift", R.string.dash_drift),
    TELEMETRY("telemetry", R.string.dash_telemetry);

    companion object {
        /** Unknown or missing keys (downgrade, future layout) fall back to Modern. */
        fun fromKey(key: String?): DashLayout = entries.firstOrNull { it.key == key } ?: MODERN
    }
}
