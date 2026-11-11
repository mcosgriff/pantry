@file:OptIn(ExperimentalRoborazziApi::class)

package io.cosgriff.pantry.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.Image
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.Mask
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import io.cosgriff.pantry.ui.theme.PantryTheme
import kotlin.math.abs
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

/**
 * Exact: any pixel that differs fails. Roborazzi's default allows each pixel a small colour distance,
 * which would let a theme token moved by one step pass unseen.
 */
private val exact = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(imageComparator = SimpleImageComparator(maxDistance = 0f), changeThreshold = 0f),
)

/**
 * For a sheet or dialog: a pixel may be one step out of 255 in a channel, and no more. Those shots blend
 * translucent windows (a scrim, rounded corners) over the page, and that blend rounds differently on a Mac
 * than on CI's Linux. Anything a change could make, such as a token moved by one tone, is many steps.
 */
private val exactBut1 = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(imageComparator = WithinOneStep, changeThreshold = 0f),
)

private object WithinOneStep : ImageComparator {
    private const val STEP = 1.5f / 255f // one step, with room for the float conversion

    override fun compare(left: Image, right: Image, diff: Mask?): ImageComparator.ComparisonResult {
        var differences = 0
        for (y in 0 until left.height) {
            for (x in 0 until left.width) {
                val a = left.getPixel(x, y)
                val b = right.getPixel(x, y)
                if (abs(a.r - b.r) > STEP || abs(a.g - b.g) > STEP || abs(a.b - b.b) > STEP || abs(a.a - b.a) > STEP) {
                    differences++
                    diff?.setValue(x, y, 1f)
                }
            }
        }
        return ImageComparator.ComparisonResult(differences, left.width * left.height, left.width, left.height)
    }
}

/**
 * Text contrast inside a text field the user types in. ATF guesses a text's colour from the two most common
 * colours in its bounds, and a field's 1dp border outnumbers the anti-aliased text, so it measures the border
 * against the field (1.3:1) where the text is ink on surface. Only that check, only on editable fields; a
 * field's label and placeholder are their own nodes and still checked.
 */
private val borderedFieldContrast = CoreMatchers.allOf(
    AccessibilityCheckResultUtils.matchesCheck(TextContrastCheck::class.java),
    AccessibilityCheckResultUtils.matchesElements(
        object : TypeSafeMatcher<ViewHierarchyElement>() {
            override fun matchesSafely(element: ViewHierarchyElement) = element.isEditable == true
            override fun describeTo(description: Description) {
                description.appendText("an editable text field")
            }
        },
    ),
)

/**
 * Every check in the Accessibility Test Framework's latest set (#5): touch targets under 48dp, text contrast
 * under 4.5:1, missing labels, duplicates. A warning fails the test as an error does. A deliberate finding
 * is suppressed for the one kind of element it is about (`suppressions`), with a comment saying why.
 */
private val accessibility = RoborazziATFAccessibilityCheckOptions(
    checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST, suppressions = borderedFieldContrast),
    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning,
)

/** Light and dark, which every screen is drawn in. */
enum class Shade(val dark: Boolean) { LIGHT(false), DARK(true) }

/**
 * The phone every screen is drawn on: a common width, tall enough that most screens fit without
 * scrolling, at twice the density to keep the files small.
 */
const val PHONE = "w411dp-h1100dp-xhdpi"

/** Where the references live, relative to `app/`. */
private fun reference(name: String, shade: Shade) = "src/test/screenshots/${name}_${shade.name.lowercase()}.png"

/**
 * Draws [content] (a `…Content` composable) as the app shows a screen: in the Pantry theme, on the page
 * colour. Checks it for accessibility, then compares it with `src/test/screenshots/<name>_<shade>.png`.
 */
fun ComposeContentTestRule.shoot(name: String, shade: Shade, content: @Composable () -> Unit) {
    setContent {
        PantryTheme(darkTheme = shade.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
    // Accessibility first: a finding says what is wrong, where an image that differs only says that it does.
    onRoot().checkRoboAccessibility(accessibility)
    onRoot().captureRoboImage(reference(name, shade), roborazziOptions = exact)
}

/**
 * As [shoot], for a bottom sheet or dialog: those are windows of their own, so the whole screen is
 * captured, with the sheet over the page, and each window is checked for accessibility.
 */
fun ComposeContentTestRule.shootScreen(name: String, shade: Shade, content: @Composable () -> Unit) {
    setContent {
        PantryTheme(darkTheme = shade.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
    waitForIdle()
    val roots = onAllNodes(isRoot())
    repeat(roots.fetchSemanticsNodes().size) { roots[it].checkRoboAccessibility(accessibility) }
    captureScreenRoboImage(reference(name, shade), roborazziOptions = exactBut1)
}
