package com.beamng.remotecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Night Garage" design language (approved mockups, 2026-07-11):
 * warm amber cockpit backlight on deep charcoal — a car interior at night.
 * Deliberately single-theme: a cockpit is dark regardless of system theme.
 */
object NightGarage {
    // Accents
    val Amber = Color(0xFFFFB03A)      // primary backlight
    val AmberHot = Color(0xFFFF6A2A)   // needle / hot end of gradients
    val Red = Color(0xFFFF5A3C)        // warnings, redline
    val Green = Color(0xFF9FE08A)      // ok / connected / temp
    val Blue = Color(0xFF6FB4FF)       // boost / full beam

    // Grounds
    val Shell = Color(0xFF0C0A08)      // deepest background
    val Panel = Color(0xFF241C12)      // card / gauge housing
    val PanelDeep = Color(0xFF1D1712)  // inner gauge face
    val PanelEdge = Color(0xFF3A2F1F)  // hairline borders

    // Text
    val Text = Color(0xFFF3E2C8)       // warm off-white
    val TextBright = Color(0xFFFFE9C4) // speed digits
    val TextDim = Color(0xFFC9A876)    // labels
    val TextFaint = Color(0xFF8A7358)  // fine print
    val OnAmber = Color(0xFF1A0E04)    // text on amber buttons
}

private val NightGarageColorScheme = darkColorScheme(
    primary = NightGarage.Amber,
    onPrimary = NightGarage.OnAmber,
    secondary = NightGarage.Green,
    onSecondary = NightGarage.OnAmber,
    tertiary = NightGarage.Blue,
    onTertiary = NightGarage.OnAmber,
    error = NightGarage.Red,
    onError = NightGarage.OnAmber,
    background = NightGarage.Shell,
    onBackground = NightGarage.Text,
    surface = NightGarage.Panel,
    onSurface = NightGarage.Text,
    surfaceVariant = NightGarage.PanelDeep,
    onSurfaceVariant = NightGarage.TextDim,
    outline = NightGarage.PanelEdge,
)

/** Gauge-face feel: condensed sans for numbers and labels. */
private val Condensed = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))

private val NightGarageTypography = Typography(
    displayLarge = TextStyle(   // speedometer digits
        fontFamily = Condensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 64.sp,
    ),
    headlineMedium = TextStyle( // gear letter, RPM value
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
    ),
    titleLarge = TextStyle(     // screen titles
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(     // buttons
        fontFamily = Condensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        letterSpacing = 1.5.sp,
    ),
    labelSmall = TextStyle(     // card eyebrows: "CONNECT", "LIVE DASHBOARD"
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    ),
)

@Composable
fun NightGarageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightGarageColorScheme,
        typography = NightGarageTypography,
        content = content,
    )
}
