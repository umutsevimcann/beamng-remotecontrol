package com.beamng.remotecontrol.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.beamng.remotecontrol.settings.SettingsManager

/** Which palette the app uses. [key] is what gets persisted. */
enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        /** Dark is the default: the drive screen is used at night, in dim rooms. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

/** One complete set of colours. Every colour the UI draws with comes from here. */
data class Palette(
    val dark: Boolean,
    // Grounds
    val bg0: Color,          // centre of the screen vignette
    val bg1: Color,
    val bg2: Color,          // screen edge
    val panelTop: Color,
    val panelBottom: Color,
    val edge: Color,         // hairline borders
    val track: Color,        // unlit gauge segments, bar tracks
    val inset: Color,        // recessed surfaces: data boxes, dial faces, thumbnails
    val selected: Color,     // fill of a selected card
    // Text
    val text: Color,
    val muted: Color,        // labels, fine print
    val off: Color,          // unlit indicators
    // Accent + data
    val orange: Color,
    val onOrange: Color,     // text on orange buttons
    val blue: Color,
    val green: Color,
    val red: Color,
    val amber: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg0 = Color(0xFF1B1E25), bg1 = Color(0xFF0D0E12), bg2 = Color(0xFF060608),
    panelTop = Color(0xFF1A1D24), panelBottom = Color(0xFF101216),
    edge = Color(0xFF262A32), track = Color(0xFF262A32),
    inset = Color(0xFF0B0C0E), selected = Color(0xFF2A1C10),
    text = Color(0xFFF5F6F7), muted = Color(0xFF8A8F99), off = Color(0xFF30343C),
    orange = Color(0xFFFF7A1A), onOrange = Color(0xFF1A0D02),
    blue = Color(0xFF4AA8FF), green = Color(0xFF3DDC84), red = Color(0xFFFF4D4D), amber = Color(0xFFFFC23D),
)

// Daylight palette: same roles, tuned for contrast on a pale ground (data colours
// are darker than their night versions so thin text and bars stay legible).
val LightPalette = Palette(
    dark = false,
    bg0 = Color(0xFFFFFFFF), bg1 = Color(0xFFF1F3F6), bg2 = Color(0xFFE4E7EC),
    panelTop = Color(0xFFFFFFFF), panelBottom = Color(0xFFF6F7F9),
    edge = Color(0xFFD3D8E0), track = Color(0xFFDCE0E6),
    inset = Color(0xFFECEFF3), selected = Color(0xFFFFEAD8),
    text = Color(0xFF14161A), muted = Color(0xFF5E6571), off = Color(0xFFC2C7CF),
    orange = Color(0xFFF26A00), onOrange = Color(0xFFFFFFFF),
    blue = Color(0xFF1977D8), green = Color(0xFF14964F), red = Color(0xFFD92B2B), amber = Color(0xFFB87D00),
)

/**
 * "Graphite" design language: quiet panels, one orange accent, four data colours,
 * in a night and a daylight palette.
 *
 * The colours are properties backed by snapshot state, so code keeps writing
 * `Graphite.Text` — in composables and inside Canvas draw blocks alike — and
 * everything recomposes or redraws when the palette changes.
 */
object Graphite {
    var palette by mutableStateOf(DarkPalette)
        internal set

    val isDark get() = palette.dark
    val Bg0 get() = palette.bg0
    val Bg1 get() = palette.bg1
    val Bg2 get() = palette.bg2
    val PanelTop get() = palette.panelTop
    val PanelBottom get() = palette.panelBottom
    val Edge get() = palette.edge
    val Track get() = palette.track
    val Inset get() = palette.inset
    val Selected get() = palette.selected
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Off get() = palette.off
    val Orange get() = palette.orange
    val OnOrange get() = palette.onOrange
    val Blue get() = palette.blue
    val Green get() = palette.green
    val Red get() = palette.red
    val Amber get() = palette.amber
}

/** The user's theme choice, observable so a change in Settings repaints at once. */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.DARK)
    internal var loaded = false
}

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
    val context = LocalContext.current
    // First composition of the process: adopt the saved choice.
    if (!ThemeState.loaded) {
        ThemeState.mode = SettingsManager.getInstance(context).themeMode
        ThemeState.loaded = true
    }
    val dark = when (ThemeState.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    // Set before any child reads it in this composition.
    if (Graphite.palette !== palette) Graphite.palette = palette

    // The window behind Compose (cutout strip, first frame) and the system bar
    // icons must follow the palette too.
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        window.setBackgroundDrawable(ColorDrawable(palette.bg1.toArgb()))
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = palette.bg1.toArgb()
            window.navigationBarColor = palette.bg1.toArgb()
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    val scheme = remember(palette) {
        if (dark) {
            darkColorScheme(
                primary = palette.orange, onPrimary = palette.onOrange, secondary = palette.green, tertiary = palette.blue,
                error = palette.red, background = palette.bg1, onBackground = palette.text, surface = palette.panelTop,
                onSurface = palette.text, surfaceVariant = palette.panelBottom, onSurfaceVariant = palette.muted, outline = palette.edge,
            )
        } else {
            lightColorScheme(
                primary = palette.orange, onPrimary = palette.onOrange, secondary = palette.green, tertiary = palette.blue,
                error = palette.red, background = palette.bg1, onBackground = palette.text, surface = palette.panelTop,
                onSurface = palette.text, surfaceVariant = palette.panelBottom, onSurfaceVariant = palette.muted, outline = palette.edge,
            )
        }
    }
    MaterialTheme(colorScheme = scheme, typography = GraphiteTypography, content = content)
}

/** The screen vignette every screen sits on. */
fun screenBackground(): Brush = Brush.radialGradient(listOf(Graphite.Bg0, Graphite.Bg1, Graphite.Bg2), radius = 1800f)

/** A panel: vertical gradient, hairline edge, 14dp corners. */
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
