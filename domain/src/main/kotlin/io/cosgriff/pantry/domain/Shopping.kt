package io.cosgriff.pantry.domain

import java.time.LocalDate

/** Why something is on the shopping list (#20), in the list's group order. */
enum class ShoppingReason { RunningLow, UsedUp, ByHand }

/** A line the user put on the list, or one added for them when they used something up. */
data class ShoppingEntry(val id: Long, val name: String, val reason: ShoppingReason, val addedOn: LocalDate) {
    init {
        require(reason != ShoppingReason.RunningLow) { "Running-low lines come from the pantry, not the list" }
        require(name.isNotBlank()) { "Name must not be blank" }
    }
}

/**
 * One row of the shopping list. [key] stays the same while the row is on the list, so the trip can remember what
 * is in the cart. A running-low row carries its [item]; a stored one its [entry].
 */
data class ShoppingLine(val key: String, val name: String, val reason: ShoppingReason, val item: PantryItem? = null, val entry: ShoppingEntry? = null)

/**
 * The list: items at or under their low-stock alert (worked out from the pantry, so a restocked item leaves the
 * list by itself), then what was used up, then what was added by hand, each group by name. A name already on the
 * list from an earlier group isn't repeated.
 */
fun shoppingList(items: List<PantryItem>, entries: List<ShoppingEntry>): List<ShoppingLine> {
    val low = items.filter { it.isLowStock }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .map { ShoppingLine("low:${it.id}", it.name, ShoppingReason.RunningLow, item = it) }
    val stored = entries.sortedWith(compareBy<ShoppingEntry> { it.reason }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .map { ShoppingLine("entry:${it.id}", it.name, it.reason, entry = it) }
    val seen = mutableSetOf<String>()
    return (low + stored).filter { seen.add(it.name.trim().lowercase()) }
}

/** The trip's count: lines in the cart out of the whole list. Keys of lines no longer on the list don't count. */
data class TripProgress(val inCart: Int, val total: Int) {
    val left: Int get() = total - inCart
}

fun tripProgress(lines: List<ShoppingLine>, cart: Set<String>): TripProgress =
    TripProgress(inCart = lines.count { it.key in cart }, total = lines.size)
