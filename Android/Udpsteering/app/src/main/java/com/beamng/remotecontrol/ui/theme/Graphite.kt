package com.beamng.remotecontrol.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Graphite" design language (approved on device, 2026-11-14): near-black
 * graphite panels, off-white type, one orange accent, and four data colours.
 * Single-theme on purpose — a cockpit is dark regardless of the system theme.
 */
object Graphite {
    // Grounds
    val Bg0 = Color(0xFF1B1E25)         // centre of the screen vignette
    val Bg1 = Color(0xFF0D0E12)
    val Bg2 = Color(0xFF060608)         // screen edge
    val PanelTop = Color(0xFF1A1D24)
    val PanelBottom = Color(0xFF101216)
    val Edge = Color(0xFF262A32)        // hairline borders
    val Track = Color(0xFF262A32)       // unlit gauge segments, bar tracks

    // Text
    val Text = Color(0xFFF5F6F7)
    val Muted = Color(0xFF8A8F99)       // labels, fine print
    val Off = Color(0xFF30343C)         // unlit indicators

    // Accent + data
    val Orange = Color(0xFFFF7A1A)
    val OnOrange = Color(0xFF1A0D02)    // text on orange buttons
    val Blue = Color(0xFF4AA8FF)
    val Green = Color(0xFF3DDC84)
    val Red = Color(0xFFFF4D4D)
    val Amber = Color(0xFFFFC23D)
}

private val GraphiteColorScheme = darkColorScheme(
    primary = Graphite.Orange,
    onPrimary = Graphite.OnOrange,
    secondary = Graphite.Green,
    onSecondary = Graphite.OnOrange,
    tertiary = Graphite.Blue,
    onTertiary = Graphite.OnOrange,
    error = Graphite.Red,
    onError = Graphite.OnOrange,
    background = Graphite.Bg1,
    onBackground = Graphite.Text,
    surface = Graphite.PanelTop,
    onSurface = Graphite.Text,
    surfaceVariant = Graphite.PanelBottom,
    onSurfaceVariant = Graphite.Muted,
    outline = Graphite.Edge,
)

private val GraphiteTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 64.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.sp),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraphiteColorScheme,
        typography = GraphiteTypography,
        content = content,
    )
}

/** The screen vignette every screen sits on. */
fun screenBackground(): Brush = Brush.radialGradient(listOf(Graphite.Bg0, Graphite.Bg1, Graphite.Bg2), radius = 1800f)

/** A graphite panel: vertical gradient, hairline edge, 14dp corners. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Graphite.PanelTop, Graphite.PanelBottom)), shape)
            .border(1.dp, Graphite.Edge, shape),
        content = content,
    )
}

/** Layout sizes are expressed in Dp derived from screen height; text needs them as sp. */
@Composable
fun Dp.asSp(): TextUnit = with(LocalDensity.current) { toSp() }
