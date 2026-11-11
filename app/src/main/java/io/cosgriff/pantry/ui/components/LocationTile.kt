package io.cosgriff.pantry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.ui.theme.PantryTheme

/** A location's name: the app's wording for a built-in one, the user's own for one they added. */
@Composable
@ReadOnlyComposable
fun locationName(location: Location): String = when (location) {
    Location.Dry -> stringResource(R.string.location_dry)
    Location.Fridge -> stringResource(R.string.location_fridge)
    Location.Freezer -> stringResource(R.string.location_freezer)
    is Location.Custom -> location.name
}

@Composable
@ReadOnlyComposable
private fun locationLetter(location: Location): String = when (location) {
    Location.Dry -> stringResource(R.string.location_dry_letter)
    Location.Fridge -> stringResource(R.string.location_fridge_letter)
    Location.Freezer -> stringResource(R.string.location_freezer_letter)
    is Location.Custom -> location.name.trim().take(1).uppercase()
}

/** Container and content colors of a location. A location the user added takes the neutral pair. */
@Composable
@ReadOnlyComposable
fun locationColors(location: Location): Pair<Color, Color> {
    val c = PantryTheme.colors
    return when (location) {
        Location.Dry -> c.dryContainer to c.dry
        Location.Fridge -> c.fridgeContainer to c.fridge
        Location.Freezer -> c.freezerContainer to c.freezer
        is Location.Custom -> c.surfaceMuted to c.ink
    }
}

/**
 * The square tile with a location's letter. Color is never the only cue: the letter is there, and the row it
 * sits in names the location too. The tile itself is decorative to TalkBack.
 */
@Composable
fun LocationTile(location: Location, modifier: Modifier = Modifier) {
    val (container, content) = locationColors(location)
    Box(
        modifier = modifier
            .size(PantryDimens.tileSize)
            .background(container, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = locationLetter(location), style = PantryTheme.text.locationLetter, color = content)
    }
}
