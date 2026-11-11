package io.cosgriff.pantry.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius tokens: `radius-sm` 10, `-md` 12, `-lg` 14, `-xl` 18, `-sheet` 28. */
val PantryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // pills, badges
    small = RoundedCornerShape(12.dp), // inputs, segment buttons, location tiles
    medium = RoundedCornerShape(14.dp), // search field, full-width buttons
    large = RoundedCornerShape(18.dp), // grouped lists, cards, banners, the Scan FAB
    extraLarge = RoundedCornerShape(28.dp), // bottom sheets' top corners
)

/** Spacing and size tokens. Touch targets stay at least [tapMin]. */
object PantryDimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp // screen side gutter
    val space6 = 24.dp

    // Material asks for 48dp targets; the design's 44px is its floor, so the larger wins.
    val tapMin = 48.dp
    val fieldHeight = 48.dp
    val buttonHeight = 52.dp
    val fabHeight = 56.dp
    val navHeight = 76.dp
}
