package io.cosgriff.pantry.ui.usesoon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.Consumed
import io.cosgriff.pantry.data.Consumption
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.UseSoonGroup
import io.cosgriff.pantry.domain.useSoon
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UseSoonState(
    val today: LocalDate,
    val loading: Boolean = true,
    val groups: Map<UseSoonGroup, List<PantryItem>> = emptyMap(),
    val done: Consumed? = null,
)

@HiltViewModel
class UseSoonViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val consumption: Consumption,
    private val clock: Clock,
) : ViewModel() {
    private val done = MutableStateFlow<Consumed?>(null)

    val state: StateFlow<UseSoonState> = combine(pantry.items(), done) { items, last ->
        val today = LocalDate.now(clock)
        UseSoonState(today = today, loading = false, groups = useSoon(items, today), done = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UseSoonState(today = LocalDate.now(clock)))

    fun onAction(item: PantryItem) {
        viewModelScope.launch { done.value = consumption.takeQuickAction(item, LocalDate.now(clock)) }
    }

    /** Puts the item back as it was before the last quick action. */
    fun onUndo() {
        val last = done.value ?: return
        done.value = null
        viewModelScope.launch { consumption.undo(last) }
    }

    fun onDoneShown() {
        done.value = null
    }
}
