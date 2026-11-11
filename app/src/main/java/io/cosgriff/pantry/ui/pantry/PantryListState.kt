package io.cosgriff.pantry.ui.pantry

import io.cosgriff.pantry.domain.DEFAULT_WARNING_DAYS
import io.cosgriff.pantry.domain.ExpirySummary
import io.cosgriff.pantry.domain.ItemFilter
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.SortOrder
import java.time.LocalDate

/** What the Pantry list shows. [total] counts every item; [rows] are the ones the search and tab leave. */
data class PantryListState(
    val today: LocalDate,
    val loading: Boolean = true,
    val total: Int = 0,
    val tabs: List<LocationCount> = emptyList(),
    val selected: Location? = null,
    val query: String = "",
    val rows: List<PantryItem> = emptyList(),
    val summary: ExpirySummary = ExpirySummary(thisWeek = 0, expired = 0),
    val warningDays: Int = DEFAULT_WARNING_DAYS,
    val sort: SortOrder = SortOrder.ExpiresSoonest,
    val filters: Set<ItemFilter> = emptySet(),
)

data class LocationCount(val location: Location, val count: Int)
