package io.cosgriff.pantry.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Pantry design system's color tokens, one object per theme. Names match `tokens.json`
 * (`ground`, `ink-muted`, …) so a value can be checked against the design system by eye.
 * Design system: https://claude.ai/artifact/Xu1pTgesVxAXhGgB4w8P7e
 */
@Immutable
data class PantryColors(
    val ground: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val ink: Color,
    val inkMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val dryContainer: Color,
    val dry: Color,
    val fridgeContainer: Color,
    val fridge: Color,
    val freezerContainer: Color,
    val freezer: Color,
    val warningContainer: Color,
    val warningContainerStrong: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerBorder: Color,
    val scannerGround: Color,
    val scannerLive: Color,
    /** Text and icons over the camera picture. */
    val scannerOn: Color,
    /** The round buttons and pill over the camera picture. */
    val scannerScrim: Color,
)

val LightPantryColors = PantryColors(
    ground = Color(0xFFF4F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF0F2EC),
    ink = Color(0xFF18211C),
    inkMuted = Color(0xFF5B655F),
    border = Color(0xFFE2E5DF),
    borderStrong = Color(0xFFC9CEC6),
    divider = Color(0xFFEEF0EA),
    accent = Color(0xFF2D6A4F),
    accentPressed = Color(0xFF1B4332),
    onAccent = Color(0xFFFFFFFF),
    dryContainer = Color(0xFFF6E7C8),
    dry = Color(0xFF7A4E0B),
    fridgeContainer = Color(0xFFDCEBFA),
    fridge = Color(0xFF1E4F86),
    freezerContainer = Color(0xFFE4E1F7),
    freezer = Color(0xFF43358F),
    warningContainer = Color(0xFFFFF4E0),
    warningContainerStrong = Color(0xFFF5D9A8),
    warning = Color(0xFF8F5400),
    onWarningContainer = Color(0xFF5A3906),
    danger = Color(0xFFB3261E),
    dangerBorder = Color(0xFFE8B4AF),
    scannerGround = Color(0xFF0E1411),
    scannerLive = Color(0xFF7BD3A4),
    scannerOn = Color(0xFFFFFFFF),
    scannerScrim = Color(0x73000000),
)

val DarkPantryColors = PantryColors(
    ground = Color(0xFF111613),
    surface = Color(0xFF1A201C),
    surfaceMuted = Color(0xFF252C27),
    ink = Color(0xFFE6EBE7),
    inkMuted = Color(0xFFA3ADA6),
    border = Color(0xFF2E3631),
    borderStrong = Color(0xFF6B756E),
    divider = Color(0xFF232A25),
    accent = Color(0xFF7BC8A0),
    accentPressed = Color(0xFF9AD8B6),
    onAccent = Color(0xFF0B2618),
    dryContainer = Color(0xFF574012),
    dry = Color(0xFFF5D08A),
    fridgeContainer = Color(0xFF183C5E),
    fridge = Color(0xFFB0D3F7),
    freezerContainer = Color(0xFF2A2360),
    freezer = Color(0xFFCDC6F7),
    warningContainer = Color(0xFF3A2A0C),
    warningContainerStrong = Color(0xFF5A4214),
    warning = Color(0xFFF0B75C),
    onWarningContainer = Color(0xFFFFDCA0),
    danger = Color(0xFFF2A19A),
    dangerBorder = Color(0xFF6B2C27),
    scannerGround = Color(0xFF0E1411),
    scannerLive = Color(0xFF7BD3A4),
    scannerOn = Color(0xFFFFFFFF),
    scannerScrim = Color(0x73000000),
)

val LocalPantryColors = staticCompositionLocalOf { LightPantryColors }

/**
 * Material roles from the tokens, as the design system's Android mapping lays out. Every surface and
 * container role is set: one left to Material's default shows its baseline purple.
 */
fun PantryColors.toColorScheme(dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surfaceMuted,
        onPrimaryContainer = ink,
        inversePrimary = if (dark) LightPantryColors.accent else DarkPantryColors.accent,
        secondary = ink,
        onSecondary = ground,
        secondaryContainer = surfaceMuted,
        onSecondaryContainer = ink,
        tertiary = fridge,
        onTertiary = fridgeContainer,
        tertiaryContainer = fridgeContainer,
        onTertiaryContainer = fridge,
        background = ground,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = inkMuted,
        // No tonal tint on raised surfaces: elevation shows as surface steps, as the tokens lay out.
        surfaceTint = surface,
        inverseSurface = ink,
        inverseOnSurface = ground,
        error = danger,
        onError = surface,
        errorContainer = surface,
        onErrorContainer = danger,
        outline = borderStrong,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = ground,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceMuted,
        surfaceContainerHighest = surfaceMuted,
        // "Fixed" roles keep one value in both themes, so they come from the light tokens.
        primaryFixed = LightPantryColors.surfaceMuted,
        primaryFixedDim = LightPantryColors.border,
        onPrimaryFixed = LightPantryColors.ink,
        onPrimaryFixedVariant = LightPantryColors.inkMuted,
        secondaryFixed = LightPantryColors.surfaceMuted,
        secondaryFixedDim = LightPantryColors.border,
        onSecondaryFixed = LightPantryColors.ink,
        onSecondaryFixedVariant = LightPantryColors.inkMuted,
        tertiaryFixed = LightPantryColors.fridgeContainer,
        tertiaryFixedDim = LightPantryColors.fridgeContainer,
        onTertiaryFixed = LightPantryColors.fridge,
        onTertiaryFixedVariant = LightPantryColors.fridge,
    )
