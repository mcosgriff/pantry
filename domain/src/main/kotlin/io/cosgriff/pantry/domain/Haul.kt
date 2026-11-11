package io.cosgriff.pantry.domain

/** One scanned item in a batch-scan haul (#14), with its place in the haul so it can be found again. */
data class HaulEntry(val index: Int, val draft: ItemDraft)

/**
 * The Review haul screen's groups: entries by location, in the order the locations are given, each location's
 * entries in scan order. Locations with nothing scanned are left out.
 */
fun haulGroups(haul: List<ItemDraft>, locations: List<Location>): List<Pair<Location, List<HaulEntry>>> {
    val entries = haul.mapIndexed(::HaulEntry)
    val order = locations + haul.map { it.location }.filterNot { it in locations }.distinct()
    return order.mapNotNull { location ->
        entries.filter { it.draft.location == location }.takeIf { it.isNotEmpty() }?.let { location to it }
    }
}

/** How many scanned items still need a name before the haul can be added. */
fun needingName(haul: List<ItemDraft>): Int = haul.count { it.name.isBlank() }

/** The haul as items to save, or `null` while any still needs a name. */
fun haulItems(haul: List<ItemDraft>): List<PantryItem>? = haul.map { it.toItem() ?: return null }
