package io.cosgriff.pantry.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.cosgriff.pantry.R

// Inter and Space Grotesk are bundled variable fonts (OFL, texts in assets/licences): no font is
// fetched at runtime. Each weight the tokens use is one instance of the variable file.
private fun variable(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Inter = variable(R.font.inter, 400, 500, 600, 700)
val SpaceGrotesk = variable(R.font.space_grotesk, 500, 600, 700)

/** Barcode digits only (`barcode` token). The tokens name the platform monospace face. */
val BarcodeMono: FontFamily = FontFamily.Monospace

private fun inter(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = if (tracking == 0.0) TextStyle.Default.letterSpacing else (tracking / size).em,
)

/**
 * Material roles from the design system's text styles:
 * headlineMedium `screen-title`, titleLarge `title`, titleMedium and labelLarge `body-strong`,
 * bodyLarge and bodyMedium `body`, bodySmall `caption`, labelMedium `label`, labelSmall `overline`
 * (uppercase it where it is used).
 */
val PantryTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5 / 30).em,
    ),
    titleLarge = inter(18, 24, 600),
    titleMedium = inter(15, 20, 600),
    bodyLarge = inter(15, 20, 400),
    bodyMedium = inter(15, 20, 400),
    bodySmall = inter(13, 18, 400),
    labelLarge = inter(15, 20, 600),
    labelMedium = inter(14, 20, 500),
    labelSmall = inter(12, 16, 600, tracking = 0.8),
)

/** Text styles with no Material role. */
@Immutable
data class PantryTextStyles(
    val dateDisplay: TextStyle,
    val locationLetter: TextStyle,
    val barcode: TextStyle,
)

val DefaultPantryTextStyles = PantryTextStyles(
    dateDisplay = TextStyle(fontFamily = SpaceGrotesk, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    locationLetter = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    barcode = TextStyle(fontFamily = BarcodeMono, fontSize = 13.sp, lineHeight = 18.sp),
)

val LocalPantryTextStyles = staticCompositionLocalOf { DefaultPantryTextStyles }
