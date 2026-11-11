package io.cosgriff.pantry.domain

import java.time.LocalDate

/** Days ahead the list's banner counts as "this week" (Pantry list board, #6). */
const val THIS_WEEK_DAYS = 7L

/**
 * The Pantry list's rows: items whose name contains [query] (ignoring case and surrounding spaces), in
 * [location] when one is chosen, soonest expiry first.
 */
fun pantryList(items: List<PantryItem>, query: String, location: Location?): List<PantryItem> {
    val q = query.trim()
    return items
        .filter { location == null || it.location == location }
        .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
        .sortedWith(PantryItem.BySoonestExpiry)
}

/** Number of items in each location; a location with none is left out. */
fun countByLocation(items: List<PantryItem>): Map<Location, Int> = items.groupingBy { it.location }.eachCount()

/** The list's banner: items expired or expiring within [THIS_WEEK_DAYS], and how many of them already expired. */
data class ExpirySummary(val thisWeek: Int, val expired: Int)

fun expirySummary(items: List<PantryItem>, today: LocalDate): ExpirySummary {
    val dated = items.mapNotNull { it.expires }
    return ExpirySummary(
        thisWeek = dated.count { !it.isAfter(today.plusDays(THIS_WEEK_DAYS)) },
        expired = dated.count { it.isBefore(today) },
    )
}
