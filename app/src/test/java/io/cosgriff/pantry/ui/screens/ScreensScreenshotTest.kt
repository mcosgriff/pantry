package io.cosgriff.pantry.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import io.cosgriff.pantry.ui.scan.ScanActions
import io.cosgriff.pantry.ui.scan.ScanContent
import io.cosgriff.pantry.ui.scan.previewFound
import io.cosgriff.pantry.ui.settings.SettingsActions
import io.cosgriff.pantry.ui.settings.SettingsContent
import io.cosgriff.pantry.ui.settings.SettingsState
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
    fun cameraPermission() = compose.shoot("camera_permission", shade) {
        CameraPermissionContent(refused = false, onAllow = {}, onByHand = {}, onBack = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shades() = Shade.entries.map { arrayOf<Any>(it) }
    }
}
