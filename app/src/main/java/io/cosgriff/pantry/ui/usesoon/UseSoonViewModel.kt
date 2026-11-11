package io.cosgriff.pantry.ui.usesoon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.QuickAction
import io.cosgriff.pantry.domain.UseSoonGroup
import io.cosgriff.pantry.domain.freeze
import io.cosgriff.pantry.domain.quickAction
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

/** A quick action just taken, with the item as it was so it can be undone. */
data class Done(val action: QuickAction, val before: PantryItem)

data class UseSoonState(
    val today: LocalDate,
    val loading: Boolean = true,
    val groups: Map<UseSoonGroup, List<PantryItem>> = emptyMap(),
    val done: Done? = null,
)

@HiltViewModel
class UseSoonViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val done = MutableStateFlow<Done?>(null)

    val state: StateFlow<UseSoonState> = combine(pantry.items(), done) { items, last ->
        val today = LocalDate.now(clock)
        UseSoonState(today = today, loading = false, groups = useSoon(items, today), done = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UseSoonState(today = LocalDate.now(clock)))

    fun onAction(item: PantryItem) {
        val today = LocalDate.now(clock)
        val action = quickAction(item, today)
        viewModelScope.launch {
            when (action) {
                QuickAction.ThrowOut, QuickAction.UsedUp -> pantry.delete(item.id)
                QuickAction.Freeze -> pantry.save(freeze(item, today))
            }
            done.value = Done(action, item)
        }
    }

    /** Puts the item back as it was before the last quick action. */
    fun onUndo() {
        val last = done.value ?: return
        done.value = null
        viewModelScope.launch { pantry.save(last.before) }
    }

    fun onDoneShown() {
        done.value = null
    }
}
