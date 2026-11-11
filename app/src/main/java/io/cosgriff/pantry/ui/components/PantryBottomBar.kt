package io.cosgriff.pantry.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.R
import io.cosgriff.pantry.ui.theme.PantryTheme

/** The app's top-level places, in bottom-bar order. */
enum class TopLevel(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Pantry(R.string.nav_pantry, R.drawable.ic_nav_pantry),
    MealPrep(R.string.nav_meal_prep, R.drawable.ic_nav_meal_prep),
    Shopping(R.string.nav_shopping, R.drawable.ic_nav_shopping),
}

/** Bottom navigation: a surface bar with a top border, the current place in the accent color. */
@Composable
fun PantryBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Column(modifier) {
        HorizontalDivider(color = colors.border)
        NavigationBar(containerColor = colors.surface, tonalElevation = PantryDimens.flat) {
            TopLevel.entries.forEach { place ->
                val selected = place == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(place) },
                    icon = { Icon(painterResource(place.icon), contentDescription = null) },
                    label = {
                        Text(
                            stringResource(place.label),
                            // 12sp as on the board; the overline style without its tracking.
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = TextUnit.Unspecified),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        unselectedIconColor = colors.inkMuted,
                        unselectedTextColor = colors.inkMuted,
                        indicatorColor = colors.surface,
                    ),
                )
            }
        }
    }
}
