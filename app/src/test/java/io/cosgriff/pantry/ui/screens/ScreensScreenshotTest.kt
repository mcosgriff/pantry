package io.cosgriff.pantry.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.cosgriff.pantry.ui.pantry.PantryListActions
import io.cosgriff.pantry.ui.pantry.PantryListContent
import io.cosgriff.pantry.ui.pantry.PantryListState
import io.cosgriff.pantry.ui.pantry.previewState
import io.cosgriff.pantry.ui.pantry.previewToday
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shades() = Shade.entries.map { arrayOf<Any>(it) }
    }
}
