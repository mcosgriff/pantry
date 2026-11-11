package io.cosgriff.pantry.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.data.ShoppingRepository
import io.cosgriff.pantry.domain.ItemFilter
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.ShoppingReason
import io.cosgriff.pantry.domain.SortOrder
import io.cosgriff.pantry.domain.countByLocation
import io.cosgriff.pantry.domain.expirySummary
import io.cosgriff.pantry.domain.pantryList
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PantryListViewModel @Inject constructor(
    pantry: PantryRepository,
    settings: SettingsRepository,
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Location?>(null)
    private val view = MutableStateFlow(View())

    /** The sheet's choices, kept together so the list combines one flow for them. */
    private data class View(val sort: SortOrder = SortOrder.ExpiresSoonest, val filters: Set<ItemFilter> = emptySet())

    val state: StateFlow<PantryListState> =
        combine(pantry.items(), pantry.locations(), settings.settings(), combine(query, selected, view, ::Triple)) { items, locations, prefs, (q, sel, v) ->
            val today = LocalDate.now(clock)
            val counts = countByLocation(items)
            // A location deleted while chosen falls back to All.
            val chosen = sel?.takeIf { it in locations }
            PantryListState(
                loading = false,
                today = today,
                total = items.size,
                tabs = locations.map { LocationCount(it, counts[it] ?: 0) },
                selected = chosen,
                query = q,
                rows = pantryList(items, q, chosen, v.sort, v.filters, today),
                summary = expirySummary(items, today),
                warningDays = prefs.warningDays,
                sort = v.sort,
                filters = v.filters,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryListState(today = LocalDate.now(clock)))

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSelect(location: Location?) {
        selected.value = location
    }

    fun onSort(sort: SortOrder) = view.update { it.copy(sort = sort) }

    fun onToggleFilter(filter: ItemFilter) =
        view.update { it.copy(filters = if (filter in it.filters) it.filters - filter else it.filters + filter) }

    /** Puts the search's text on the shopping list (#20) and clears the search. */
    fun onAddToShopping(name: String) {
        viewModelScope.launch { shopping.add(name, ShoppingReason.ByHand, LocalDate.now(clock)) }
        query.value = ""
    }

    fun onResetView() {
        view.value = View()
    }
}
