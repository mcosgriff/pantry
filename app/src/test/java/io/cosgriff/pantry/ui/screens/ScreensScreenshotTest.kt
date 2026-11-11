package io.cosgriff.pantry.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.ui.item.ItemDetailsActions
import io.cosgriff.pantry.ui.item.ItemDetailsContent
import io.cosgriff.pantry.ui.item.previewState as itemDetailsState
import io.cosgriff.pantry.ui.pantry.PantryListActions
import io.cosgriff.pantry.ui.pantry.PantryListContent
import io.cosgriff.pantry.ui.pantry.PantryListState
import io.cosgriff.pantry.ui.pantry.previewState
import io.cosgriff.pantry.ui.pantry.previewToday
import io.cosgriff.pantry.ui.scan.CameraPermissionContent
import io.cosgriff.pantry.ui.scan.ReviewHaulContent
import io.cosgriff.pantry.ui.scan.ScanActions
import io.cosgriff.pantry.ui.scan.ScanContent
import io.cosgriff.pantry.ui.scan.ScanState
import io.cosgriff.pantry.ui.scan.Lookup
import io.cosgriff.pantry.ui.scan.previewFound
import io.cosgriff.pantry.ui.scan.previewFromOff
import io.cosgriff.pantry.ui.scan.previewStorePacked
import io.cosgriff.pantry.ui.settings.SettingsActions
import io.cosgriff.pantry.ui.settings.SettingsContent
import io.cosgriff.pantry.ui.settings.SettingsState
import io.cosgriff.pantry.ui.shopping.ShoppingActions
import io.cosgriff.pantry.ui.shopping.ShoppingContent
import io.cosgriff.pantry.ui.shopping.ShoppingState
import io.cosgriff.pantry.ui.shopping.ShoppingTripContent
import io.cosgriff.pantry.ui.shopping.previewLines
import io.cosgriff.pantry.ui.usesoon.UseSoonActions
import io.cosgriff.pantry.ui.usesoon.UseSoonContent
import io.cosgriff.pantry.ui.usesoon.previewState as useSoonState
import java.time.Period
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens in light and dark (#5), drawn from their `…Content` composables with no ViewModel, checked
 * for accessibility and compared with the references in `src/test/screenshots`.
 * `./gradlew :app:recordRoborazziDebug` writes new references after a change meant to alter how a screen
 * looks; look at every image it changes before committing it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE, sdk = [35])
class ScreensScreenshotTest(private val shade: Shade) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pantryList() = compose.shoot("pantry_list", shade) { PantryListContent(previewState, PantryListActions()) }

    @Test
    fun pantryEmpty() = compose.shoot("pantry_empty", shade) {
        PantryListContent(PantryListState(today = previewToday, loading = false), PantryListActions())
    }

    @Test
    fun itemDetails() = compose.shoot("item_details", shade) { ItemDetailsContent(itemDetailsState, ItemDetailsActions()) }

    @Test
    fun settings() = compose.shoot("settings", shade) {
        SettingsContent(
            SettingsState(
                settings = PantrySettings(batchScanByDefault = true),
                locations = Location.defaults + Location.Custom("Sample garage fridge", Period.ofDays(5), id = 4),
            ),
            SettingsActions(),
        )
    }

    @Test
    fun useSoon() = compose.shoot("use_soon", shade) { UseSoonContent(useSoonState, UseSoonActions()) }

    @Test
    fun scanKnown() = compose.shoot("scan_known", shade) { ScanContent(previewFound(known = true), torch = false, actions = ScanActions()) }

    @Test
    fun scanUnknown() = compose.shoot("scan_unknown", shade) { ScanContent(previewFound(known = false), torch = false, actions = ScanActions()) }

    @Test
    fun scanStorePacked() = compose.shoot("scan_store_packed", shade) { ScanContent(previewStorePacked, torch = false, actions = ScanActions()) }

    @Test
    fun scanLookupAsk() = compose.shoot("scan_lookup_ask", shade) { ScanContent(previewFound(known = false, lookup = Lookup.Ask), torch = false, actions = ScanActions()) }

    @Test
    fun scanFoundInOpenFoodFacts() = compose.shoot("scan_found_off", shade) { ScanContent(previewFound(known = false, lookup = previewFromOff), torch = false, actions = ScanActions()) }

    @Test
    fun cameraPermission() = compose.shoot("camera_permission", shade) {
        CameraPermissionContent(refused = false, onAllow = {}, onByHand = {}, onBack = {})
    }

    @Test
    fun reviewHaul() = compose.shoot("review_haul", shade) {
        val today = previewToday
        val haul = listOf(
            ItemDraft.new(today, Location.Fridge).copy(name = "Sample eggs"),
            ItemDraft.new(today, Location.Fridge).copy(name = "Sample yogurt"),
            ItemDraft.new(today, Location.Freezer, barcode = "041220576913"),
            ItemDraft.new(today, Location.Dry).copy(name = "Sample rice"),
        )
        ReviewHaulContent(ScanState(today = today, batch = true, haul = haul, reviewing = true), ScanActions())
    }

    @Test
    fun shopping() = compose.shoot("shopping", shade) { ShoppingContent(ShoppingState(false, previewLines, setOf("entry:2")), ShoppingActions()) }

    @Test
    fun shoppingTrip() = compose.shoot("shopping_trip", shade) {
        ShoppingTripContent(ShoppingState(false, previewLines, setOf("entry:2")), onBack = {}, onToggle = { _, _ -> }, onDone = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shades() = Shade.entries.map { arrayOf<Any>(it) }
    }
}
