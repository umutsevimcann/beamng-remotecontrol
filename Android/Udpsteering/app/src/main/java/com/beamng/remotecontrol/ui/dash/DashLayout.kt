package com.beamng.remotecontrol.ui.dash

/**
 * The selectable gauge layouts of the drive screen. [key] is what gets persisted;
 * adding a layout means adding an entry here and a branch in DashArea.
 */
enum class DashLayout(val key: String) {
    MODERN("modern"),
    CLASSIC("classic"),
    RACE("race"),
    MINIMAL("minimal");

    companion object {
        /** Unknown or missing keys (downgrade, future layout) fall back to Modern. */
        fun fromKey(key: String?): DashLayout = entries.firstOrNull { it.key == key } ?: MODERN
    }
}
