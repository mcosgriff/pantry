package io.cosgriff.pantry.ui.meals

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Dates as Meal prep words them, in the user's locale.

@Composable
@ReadOnlyComposable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "Nov 9" */
@Composable
@ReadOnlyComposable
fun monthDay(day: LocalDate): String = pattern("MMMd").format(day)

/** "Mon, Nov 10" */
@Composable
@ReadOnlyComposable
fun shortDate(day: LocalDate): String = pattern("EEEMMMd").format(day)

/** "Monday, Nov 10" */
@Composable
@ReadOnlyComposable
fun fullDay(day: LocalDate): String = pattern("EEEEMMMd").format(day)

/** "Mon" */
@Composable
@ReadOnlyComposable
fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale())

@Composable
@ReadOnlyComposable
private fun pattern(skeleton: String): DateTimeFormatter {
    val locale = locale()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
