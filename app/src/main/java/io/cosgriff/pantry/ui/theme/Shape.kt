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
    val iconTiny = 14.dp
    val iconMedium = 20.dp
    val iconScanner = 22.dp // the scanner's round buttons
    val iconHero = 48.dp // the camera-permission screen's icon
    val heroBox = 96.dp // and the square it sits in
    val liveDot = 8.dp // batch scan's "on" dot
    val productPhoto = 64.dp // a known product's photo on the scan sheet

    // The scanner's frame: four rounded corners and the live line across it.
    val scanFrameWidth = 280.dp
    val scanFrameHeight = 150.dp
    val scanFrameStroke = 4.dp
    val scanFrameArm = 34.dp
    val scanFrameRadius = 14.dp
    val scanLineInset = 20.dp
    val scanLine = 2.dp
    val iconLarge = 32.dp // the icon in an empty result's circle
    val emptyIconCircle = 72.dp
    val sheetHandleWidth = 40.dp
    val sheetHandleHeight = 4.dp
    val chipHeight = 36.dp // location tabs; the banner's badge
    val tileSize = 40.dp // a location's letter tile

    // Meal prep (#19).
    val dayChipHeight = 56.dp // a day in the week strip
    val plannedDot = 5.dp // under a day with a meal planned
    val statusDot = 10.dp // an ingredient's pantry status
    val soonChipMinWidth = 112.dp // a Use soon chip
    val mealDayColumn = 44.dp // the day beside a planned meal
    val servingsStepperWidth = 150.dp
    val tileTiny = 24.dp // the letter tile beside a haul's location heading
    val tileSmall = 32.dp // the letter tile in a settings row
    val tileMedium = 36.dp // the letter tile in a Use soon row
    val rowTall = 60.dp // a settings row with a second line
    val photoSize = 60.dp // an item's photo on Item details
    val emptyArtWidth = 160.dp
    val emptyArtHeight = 128.dp
    val shareIconBox = 48.dp // the globe's square on the Open Food Facts share prompt (#42)
    val shareIcon = 26.dp

    // Material asks for 48dp targets; the design's 44px is its floor, so the larger wins.
    val tapMin = 48.dp
    val fieldHeight = 48.dp
    val buttonHeight = 52.dp
    val fabHeight = 56.dp
    val navHeight = 76.dp
}
