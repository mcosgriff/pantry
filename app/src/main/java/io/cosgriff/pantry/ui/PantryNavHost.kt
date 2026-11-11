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
import io.cosgriff.pantry.ui.scan.ScanScreen
import io.cosgriff.pantry.ui.settings.SettingsScreen
import io.cosgriff.pantry.ui.usesoon.UseSoonScreen
import kotlinx.serialization.Serializable

@Serializable data object PantryListRoute : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object UseSoonRoute : NavKey

/** Item details for a stored item, or a new one when [id] is `null`, with [name] and [barcode] filled in. */
@Serializable data class ItemDetailsRoute(val id: Long? = null, val name: String = "", val barcode: String = "") : NavKey

@Serializable data object ScanRoute : NavKey

/**
 * The app's screens. Each entry gets its own ViewModels, cleared when it leaves the back stack. Buttons for
 * screens not built yet go nowhere: Meal prep (#19), Shopping (#20).
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
                        onAddByHand = { backStack.add(ItemDetailsRoute(name = it)) },
                        onSettings = { backStack.add(SettingsRoute) },
                        onViewExpiring = { backStack.add(UseSoonRoute) },
                        onScan = { backStack.add(ScanRoute) },
                    ),
                )
            }
            entry<ScanRoute> {
                ScanScreen(
                    onClose = back,
                    // Item details replaces the scanner, so saving there returns to the list.
                    onByHand = { name, barcode ->
                        backStack.removeAt(backStack.lastIndex)
                        backStack.add(ItemDetailsRoute(name = name, barcode = barcode))
                    },
                )
            }
            entry<UseSoonRoute> {
                UseSoonScreen(onBack = back, onItem = { backStack.add(ItemDetailsRoute(it)) })
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = back)
            }
            entry<ItemDetailsRoute> { route ->
                ItemDetailsScreen(id = route.id, name = route.name, barcode = route.barcode, onBack = back, onDone = back)
            }
        },
    )
}
