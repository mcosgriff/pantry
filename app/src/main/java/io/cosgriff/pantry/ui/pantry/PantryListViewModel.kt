package io.cosgriff.pantry.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.Location
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

@HiltViewModel
class PantryListViewModel @Inject constructor(
    pantry: PantryRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Location?>(null)

    val state: StateFlow<PantryListState> =
        combine(pantry.items(), pantry.locations(), settings.settings(), query, selected) { items, locations, prefs, q, sel ->
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
                rows = pantryList(items, q, chosen),
                summary = expirySummary(items, today),
                warningDays = prefs.warningDays,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryListState(today = LocalDate.now(clock)))

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSelect(location: Location?) {
        selected.value = location
    }
}
