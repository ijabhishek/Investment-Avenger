package com.example.portfoliopilot

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Options shown in Profile > Appearance
// ---------------------------------------------------------------------------
val THEME_MODES = listOf("System", "Light", "Dark", "Black & White", "Retro")

val ACCENT_OPTIONS: List<Pair<String, Color>> = listOf(
    "Emerald" to Color(0xFF2E7D32),
    "Blue" to Color(0xFF1976D2),
    "Purple" to Color(0xFF7B1FA2),
    "Gold" to Color(0xFFF9A825),
    "Ruby" to Color(0xFFC62828),
    "Teal" to Color(0xFF00897B),
    "Orange" to Color(0xFFEF6C00),
    "Rose" to Color(0xFFD81B60),
    "Indigo" to Color(0xFF3949AB),
    "Cyan" to Color(0xFF00ACC1),
    "Lime" to Color(0xFF7CB342),
    "Coffee" to Color(0xFF6D4C41),
    "Slate" to Color(0xFF546E7A),
    "Monochrome" to Color(0xFF616161)
)

val FONT_OPTIONS = listOf(
    "Default", "Poppins", "Ubuntu", "PT Serif", "Courier Prime", "Typewriter", "Handwritten", "Script"
)

val TEXT_SIZE_OPTIONS = listOf("Auto", "Small", "Normal", "Large", "Extra large")

fun accentColorOf(name: String): Color = ACCENT_OPTIONS.firstOrNull { it.first == name }?.second ?: ACCENT_OPTIONS[0].second

// ---------------------------------------------------------------------------
// Fonts (bundled in res/font, so they work offline)
// ---------------------------------------------------------------------------
private val PoppinsFamily by lazy {
    FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )
}
private val UbuntuFamily by lazy {
    FontFamily(Font(R.font.ubuntu_regular, FontWeight.Normal), Font(R.font.ubuntu_bold, FontWeight.Bold))
}
private val PtSerifFamily by lazy {
    FontFamily(Font(R.font.ptserif_regular, FontWeight.Normal), Font(R.font.ptserif_bold, FontWeight.Bold))
}
private val CourierFamily by lazy {
    FontFamily(Font(R.font.courierprime_regular, FontWeight.Normal), Font(R.font.courierprime_bold, FontWeight.Bold))
}
private val TypewriterFamily by lazy { FontFamily(Font(R.font.specialelite_regular, FontWeight.Normal)) }
private val HandwrittenFamily by lazy { FontFamily(Font(R.font.patrickhand_regular, FontWeight.Normal)) }
private val ScriptFamily by lazy { FontFamily(Font(R.font.pacifico_regular, FontWeight.Normal)) }

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "Poppins" -> PoppinsFamily
    "Ubuntu" -> UbuntuFamily
    "PT Serif" -> PtSerifFamily
    "Courier Prime" -> CourierFamily
    "Typewriter" -> TypewriterFamily
    "Handwritten" -> HandwrittenFamily
    "Script" -> ScriptFamily
    else -> FontFamily.Default
}

/** "Script" is only used for headings (it is hard to read in long text); everything else uses one family. */
private fun appTypography(fontName: String): Typography {
    val heading = fontFamilyFor(fontName)
    val body = if (fontName == "Script") FontFamily.Default else heading
    val base = Typography()
    fun TextStyle.heading() = copy(fontFamily = heading)
    fun TextStyle.body() = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading(),
        titleMedium = base.titleMedium.heading(),
        titleSmall = base.titleSmall.heading(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(),
        labelMedium = base.labelMedium.body(),
        labelSmall = base.labelSmall.body()
    )
}

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------
private val White = Color.White
private val Black = Color.Black

private fun contentOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF111111) else Color.White

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = contentOn(accent),
    primaryContainer = lerp(accent, White, 0.80f),
    onPrimaryContainer = lerp(accent, Black, 0.65f),
    secondary = lerp(accent, Color(0xFF455A64), 0.35f),
    onSecondary = White,
    secondaryContainer = lerp(accent, White, 0.88f),
    onSecondaryContainer = lerp(accent, Black, 0.60f),
    tertiary = lerp(accent, Color(0xFFFFB300), 0.45f),
    background = lerp(accent, White, 0.97f),
    onBackground = Color(0xFF1B1C1B),
    surface = lerp(accent, White, 0.97f),
    onSurface = Color(0xFF1B1C1B),
    surfaceVariant = lerp(accent, White, 0.90f),
    onSurfaceVariant = Color(0xFF444A44),
    surfaceContainerLowest = White,
    surfaceContainerLow = lerp(accent, White, 0.96f),
    surfaceContainer = lerp(accent, White, 0.94f),
    surfaceContainerHigh = lerp(accent, White, 0.92f),
    surfaceContainerHighest = lerp(accent, White, 0.90f),
    outline = Color(0xFF747974),
    outlineVariant = lerp(accent, White, 0.75f)
)

private fun darkScheme(accent: Color): ColorScheme {
    val primary = lerp(accent, White, 0.30f)
    return darkColorScheme(
        primary = primary,
        onPrimary = contentOn(primary),
        primaryContainer = lerp(accent, Black, 0.55f),
        onPrimaryContainer = lerp(accent, White, 0.85f),
        secondary = lerp(accent, White, 0.45f),
        onSecondary = Black,
        secondaryContainer = lerp(accent, Black, 0.60f),
        onSecondaryContainer = lerp(accent, White, 0.85f),
        tertiary = lerp(accent, Color(0xFFFFCA28), 0.45f),
        background = lerp(accent, Black, 0.93f),
        onBackground = Color(0xFFE6E6E6),
        surface = lerp(accent, Black, 0.93f),
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = lerp(accent, Black, 0.82f),
        onSurfaceVariant = Color(0xFFC4C8C4),
        surfaceContainerLowest = lerp(accent, Black, 0.96f),
        surfaceContainerLow = lerp(accent, Black, 0.92f),
        surfaceContainer = lerp(accent, Black, 0.90f),
        surfaceContainerHigh = lerp(accent, Black, 0.87f),
        surfaceContainerHighest = lerp(accent, Black, 0.84f),
        outline = Color(0xFF8E938E),
        outlineVariant = lerp(accent, Black, 0.70f)
    )
}

private fun blackWhiteScheme(): ColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = White,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Black,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFD0D0D0),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF212121),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3A3A3A)
)

/** Old-time look: parchment paper, ink brown text and a rust red accent. */
private fun retroScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF8B3A1A),
    onPrimary = Color(0xFFFFF4DC),
    primaryContainer = Color(0xFFE9D3A5),
    onPrimaryContainer = Color(0xFF3B1E0C),
    secondary = Color(0xFF5B6B3A),
    onSecondary = Color(0xFFFFF4DC),
    secondaryContainer = Color(0xFFD7D29C),
    onSecondaryContainer = Color(0xFF2B3114),
    tertiary = Color(0xFF9C6B1E),
    background = Color(0xFFF3E7C9),
    onBackground = Color(0xFF3B2A1A),
    surface = Color(0xFFF6ECD0),
    onSurface = Color(0xFF3B2A1A),
    surfaceVariant = Color(0xFFE7D6AB),
    onSurfaceVariant = Color(0xFF5A4630),
    surfaceContainerLowest = Color(0xFFFBF3DC),
    surfaceContainerLow = Color(0xFFF1E4C2),
    surfaceContainer = Color(0xFFEBDDB4),
    surfaceContainerHigh = Color(0xFFE5D4A6),
    surfaceContainerHighest = Color(0xFFE0CD9C),
    outline = Color(0xFF8A7352),
    outlineVariant = Color(0xFFCDB98A),
    error = Color(0xFFA32A1E)
)

private fun textScaleFor(option: String, screenWidthDp: Int): Float = when (option) {
    "Small" -> 0.90f
    "Normal" -> 1.00f
    "Large" -> 1.15f
    "Extra large" -> 1.30f
    // Auto: fits the screen. Narrow phones get slightly smaller text, tablets slightly larger.
    else -> (screenWidthDp / 392f).coerceIn(0.88f, 1.15f)
}

/**
 * Wraps the whole app: colors, font, text size and the status/navigation bar icon colors.
 * [darkBars] forces light bar icons (used on the dark splash screen).
 */
@Composable
fun AppTheme(
    themeMode: String,
    accent: String,
    fontStyle: String,
    textSize: String,
    darkBars: Boolean,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "Dark", "Black & White" -> true
        "Light", "Retro" -> false
        else -> systemDark
    }
    val colorScheme = remember(themeMode, accent, systemDark) {
        when (themeMode) {
            "Black & White" -> blackWhiteScheme()
            "Retro" -> retroScheme()
            else -> {
                val color = accentColorOf(accent)
                if (accent == "Monochrome" && isDark) blackWhiteScheme()
                else if (isDark) darkScheme(color) else lightScheme(color)
            }
        }
    }
    val effectiveFont = if (themeMode == "Retro" && fontStyle == "Default") "Typewriter" else fontStyle
    val typography = remember(effectiveFont) { appTypography(effectiveFont) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val lightIcons = !isDark && !darkBars
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }

    val density = LocalDensity.current
    val scale = textScaleFor(textSize, LocalConfiguration.current.screenWidthDp)
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
