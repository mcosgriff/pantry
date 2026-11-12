package io.cosgriff.pantry.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.Consumption
import io.cosgriff.pantry.data.ItemIntake
import io.cosgriff.pantry.data.LocationRepository
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.QuickPick
import io.cosgriff.pantry.domain.number
import io.cosgriff.pantry.domain.RemoveReason
import io.cosgriff.pantry.domain.stepped
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Item details shows. [draft] is `null` while a stored item loads; [done] once it is saved or removed. */
data class ItemDetailsState(
    val today: LocalDate,
    val draft: ItemDraft? = null,
    val locations: List<Location> = Location.defaults,
    val done: Boolean = false,
)

@HiltViewModel
class ItemDetailsViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val locations: LocationRepository,
    private val intake: ItemIntake,
    private val consumption: Consumption,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ItemDetailsState(today = LocalDate.now(clock)))
    val state: StateFlow<ItemDetailsState> = _state.asStateFlow()

    private var loaded = false

    /**
     * Opens item [id], or a new item with [name] and [barcode] (empty for none) when it is `null`. Only the first
     * call counts (the screen calls it on entry).
     */
    fun load(id: Long?, name: String = "", barcode: String = "") {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val all = locations.locations().first()
            val draft = id?.let { pantry.item(it) }?.let(ItemDraft::of) ?: intake.byHand(name, barcode.ifEmpty { null })
            _state.update { it.copy(today = today, draft = draft, locations = all) }
        }
    }

    private fun edit(change: (ItemDraft) -> ItemDraft) = _state.update { s -> s.copy(draft = s.draft?.let(change)) }

    fun onName(name: String) = edit { it.copy(name = name) }
    fun onLocation(location: Location) = edit { it.withLocation(location) }
    fun onStep(delta: Int) = edit { it.copy(quantity = it.quantity.stepped(delta)) }
    fun onUnit(unit: AmountUnit) = edit { it.copy(quantity = unit.of(it.quantity.number)) }
    fun onExpires(date: LocalDate?) = edit { it.withExpires(date) }
    fun onQuickPick(pick: QuickPick) = edit { it.withExpires(pick.from(_state.value.today)) }
    fun onOpened(opened: Boolean) = edit { it.copy(opened = opened) }
    fun onPurchased(date: LocalDate) = edit { it.withPurchased(date) }
    fun onLowStock(amount: BigDecimal?) = edit { it.copy(lowStockAt = amount) }
    fun onNotes(notes: String) = edit { it.copy(notes = notes) }

    /** Saves through `ItemIntake`, scan's save path (#49), which says how a new item and an edit differ. */
    fun onSave() {
        val draft = _state.value.draft?.takeIf { it.name.isNotBlank() } ?: return
        viewModelScope.launch {
            if (intake.save(draft)) _state.update { it.copy(done = true) }
        }
    }

    /**
     * Removes the item for [reason] (#18): `Consumption` decides what else that does. The stored item is removed,
     * by its saved name, not the draft's unsaved edits.
     */
    fun onRemove(reason: RemoveReason) {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            if (!draft.isNew) pantry.item(draft.id)?.let { consumption.remove(it, reason) }
            _state.update { it.copy(done = true) }
        }
    }
}
