package io.cosgriff.pantry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme, from the Pantry design system's tokens. Dynamic color (Material You) is off on
 * purpose: the wallpaper palette would replace the brand and the location colors' meaning.
 */
@Composable
fun PantryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkPantryColors else LightPantryColors
    CompositionLocalProvider(
        LocalPantryColors provides colors,
        LocalPantryTextStyles provides DefaultPantryTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = PantryTypography,
            shapes = PantryShapes,
            content = content,
        )
    }
}

/** Tokens Material has no role for: `PantryTheme.colors.fridge`, `PantryTheme.text.barcode`. */
object PantryTheme {
    val colors: PantryColors
        @Composable @ReadOnlyComposable
        get() = LocalPantryColors.current

    val text: PantryTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalPantryTextStyles.current
}
