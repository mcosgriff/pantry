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

    // Between the scale's steps, where the boards draw them.
    val hairline = 1.dp // borders and dividers
    val borderSelected = 2.dp // a chosen option's outline
    val space0 = 2.dp // a title and the line under it
    val spaceBadge = 3.dp // a small badge's top and bottom
    val spaceChip = 6.dp // between quick-pick chips; a label and its field
    val spaceIcon = 10.dp // an icon and its text; stacked buttons
    val spaceInset = 14.dp // inside rows, chips and the search field
    val spaceSection = 18.dp // above a list section

    /** Flat by default: borders, not shadows (CLAUDE.md). */
    val flat = 0.dp

    val iconSmall = 18.dp
    val iconMedium = 20.dp
    val iconLarge = 32.dp // the icon in an empty result's circle
    val emptyIconCircle = 72.dp
    val sheetHandleWidth = 40.dp
    val sheetHandleHeight = 4.dp
    val chipHeight = 36.dp // location tabs; the banner's badge
    val tileSize = 40.dp // a location's letter tile
    val tileSmall = 32.dp // the letter tile in a settings row
    val tileMedium = 36.dp // the letter tile in a Use soon row
    val rowTall = 60.dp // a settings row with a second line
    val photoSize = 60.dp // an item's photo on Item details
    val emptyArtWidth = 160.dp
    val emptyArtHeight = 128.dp

    // Material asks for 48dp targets; the design's 44px is its floor, so the larger wins.
    val tapMin = 48.dp
    val fieldHeight = 48.dp
    val buttonHeight = 52.dp
    val fabHeight = 56.dp
    val navHeight = 76.dp
}
