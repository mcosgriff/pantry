package io.cosgriff.pantry.domain

import java.time.LocalDate

/** Days ahead the list's banner counts as "this week" (Pantry list board, #6). */
const val THIS_WEEK_DAYS = 7L

/** The list's sort orders (Sort and filter sheet, #17). */
enum class SortOrder { ExpiresSoonest, NameAToZ, RecentlyAdded, LowestStockFirst }

/** The sheet's "Show only" filters. Several together keep items that match all of them. */
enum class ItemFilter { Opened, LowStock, NoExpiryDate, AddedThisWeek }

/**
 * The Pantry list's rows. A [query] (ignoring case and surrounding spaces) matches names and notes, and
 * barcodes ignoring spaces; it searches every location, so [location] applies only while there is none.
 * [filters] keep items that match all of them, then [sort] orders them. [today] is needed only for
 * [ItemFilter.AddedThisWeek].
 */
fun pantryList(
    items: List<PantryItem>,
    query: String,
    location: Location?,
    sort: SortOrder = SortOrder.ExpiresSoonest,
    filters: Set<ItemFilter> = emptySet(),
    today: LocalDate? = null,
): List<PantryItem> {
    require(today != null || ItemFilter.AddedThisWeek !in filters) { "AddedThisWeek needs today" }
    val q = query.trim()
    return items
        .filter { q.isNotEmpty() || location == null || it.location == location }
        .filter { q.isEmpty() || it.matches(q) }
        .filter { item -> filters.all { item.passes(it, today) } }
        .sortedWith(sort.comparator)
}

private fun PantryItem.matches(q: String): Boolean {
    val digits = q.filterNot(Char::isWhitespace)
    return name.contains(q, ignoreCase = true) ||
        notes.contains(q, ignoreCase = true) ||
        (digits.isNotEmpty() && barcode?.contains(digits) == true)
}

private fun PantryItem.passes(filter: ItemFilter, today: LocalDate?): Boolean = when (filter) {
    ItemFilter.Opened -> opened
    ItemFilter.LowStock -> isLowStock
    ItemFilter.NoExpiryDate -> expires == null
    // Bought today or in the six days before.
    ItemFilter.AddedThisWeek -> today != null && !purchased.isBefore(today.minusDays(THIS_WEEK_DAYS - 1)) && !purchased.isAfter(today)
}

private val byName: Comparator<PantryItem> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }

private val SortOrder.comparator: Comparator<PantryItem>
    get() = when (this) {
        SortOrder.ExpiresSoonest -> PantryItem.BySoonestExpiry
        SortOrder.NameAToZ -> byName
        // Ids grow as items are stored, so the newest has the highest.
        SortOrder.RecentlyAdded -> compareByDescending<PantryItem> { it.id }
        // Items at or under their alert first, then the smallest amounts.
        SortOrder.LowestStockFirst ->
            compareByDescending<PantryItem> { it.isLowStock }.thenBy { it.quantity.number }.then(byName)
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
