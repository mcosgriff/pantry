package io.cosgriff.pantry.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.pantry.ui.item.ItemDetailsScreen
import io.cosgriff.pantry.ui.pantry.PantryListActions
import io.cosgriff.pantry.ui.pantry.PantryListScreen
import io.cosgriff.pantry.ui.settings.SettingsScreen
import io.cosgriff.pantry.ui.usesoon.UseSoonScreen
import kotlinx.serialization.Serializable

@Serializable data object PantryListRoute : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object UseSoonRoute : NavKey

/** Item details for a stored item, or a new one when [id] is `null`. */
@Serializable data class ItemDetailsRoute(val id: Long? = null) : NavKey

/**
 * The app's screens. Each entry gets its own ViewModels, cleared when it leaves the back stack. Buttons for
 * screens not built yet go nowhere: Scan (#10), Sort and filter (#17),
 * Meal prep (#19), Shopping (#20).
 */
@Composable
fun PantryNavHost() {
    val backStack = rememberNavBackStack(PantryListRoute)
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<PantryListRoute> {
                PantryListScreen(
                    actions = PantryListActions(
                        onItem = { backStack.add(ItemDetailsRoute(it)) },
                        onAddByHand = { backStack.add(ItemDetailsRoute()) },
                        onSettings = { backStack.add(SettingsRoute) },
                        onViewExpiring = { backStack.add(UseSoonRoute) },
                    ),
                )
            }
            entry<UseSoonRoute> {
                UseSoonScreen(onBack = back, onItem = { backStack.add(ItemDetailsRoute(it)) })
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = back)
            }
            entry<ItemDetailsRoute> { route ->
                ItemDetailsScreen(id = route.id, onBack = back, onDone = back)
            }
        },
    )
}
