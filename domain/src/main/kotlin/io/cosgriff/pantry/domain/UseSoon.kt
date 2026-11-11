package io.cosgriff.pantry.domain

import java.time.LocalDate

/** The Use soon screen's groups (#16), in screen order. */
enum class UseSoonGroup { Expired, ThisWeek, NextWeek }

/** The one quick action a Use soon row offers. */
enum class QuickAction { ThrowOut, UsedUp, Freeze }

/**
 * Items for Use soon, soonest first within each group: expired, expiring within [THIS_WEEK_DAYS] (today
 * included), and the week after. Undated items and those further out are left out; empty groups too.
 */
fun useSoon(items: List<PantryItem>, today: LocalDate): Map<UseSoonGroup, List<PantryItem>> =
    items.sortedWith(PantryItem.BySoonestExpiry)
        .mapNotNull { item ->
            val expires = item.expires ?: return@mapNotNull null
            val group = when {
                expires.isBefore(today) -> UseSoonGroup.Expired
                !expires.isAfter(today.plusDays(THIS_WEEK_DAYS)) -> UseSoonGroup.ThisWeek
                !expires.isAfter(today.plusDays(2 * THIS_WEEK_DAYS)) -> UseSoonGroup.NextWeek
                else -> return@mapNotNull null
            }
            group to item
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

/**
 * The row's quick action: Throw out once it has expired; Used up for what is already frozen or opened; otherwise
 * Freeze, which keeps it for the Freezer's shelf life.
 */
fun quickAction(item: PantryItem, today: LocalDate): QuickAction = when {
    item.expires?.isBefore(today) == true -> QuickAction.ThrowOut
    item.location == Location.Freezer || item.opened -> QuickAction.UsedUp
    else -> QuickAction.Freeze
}

/** [item] moved to the Freezer, with a new date from the Freezer's shelf life counted from [today]. */
fun freeze(item: PantryItem, today: LocalDate): PantryItem =
    item.copy(location = Location.Freezer, expires = suggestedExpiry(today, Location.Freezer))
