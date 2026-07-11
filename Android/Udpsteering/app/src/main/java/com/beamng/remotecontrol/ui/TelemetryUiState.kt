package com.beamng.remotecontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Compose-observable snapshot of the game's OutGauge stream (UI-thread only). */
class TelemetryUiState {
    var connected by mutableStateOf(false)
    var speedMs by mutableFloatStateOf(0f)
    var rpm by mutableFloatStateOf(0f)
    var gear by mutableStateOf("N")
    var fuel by mutableFloatStateOf(0f)       // 0..1
    var engTemp by mutableFloatStateOf(0f)    // °C
    var turbo by mutableFloatStateOf(0f)      // BAR
    var hasTurbo by mutableStateOf(false)
    val lights = mutableStateListOf(*Array(11) { false })

    // What the game actually applies (input echo, useful in dashboard-only mode)
    var throttleEcho by mutableFloatStateOf(0f)
    var brakeEcho by mutableFloatStateOf(0f)
    var clutchEcho by mutableFloatStateOf(0f)

    // 0-100 km/h performance timer (opt-in via settings)
    var timerLiveSec by mutableStateOf<Float?>(null)
    var timerLastSec by mutableStateOf<Float?>(null)
    var timerBestSec by mutableStateOf<Float?>(null)
}
