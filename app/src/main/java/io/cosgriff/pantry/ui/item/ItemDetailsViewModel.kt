package io.cosgriff.pantry.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.QuickPick
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
    private val settings: SettingsRepository,
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
            val locations = pantry.locations().first()
            val draft = id?.let { pantry.item(it) }?.let(ItemDraft::of) ?: ItemDraft.new(today, settings.newItemLocation(), barcode = barcode.ifEmpty { null }).copy(name = name)
            _state.update { it.copy(today = today, draft = draft, locations = locations) }
        }
    }

    private fun edit(change: (ItemDraft) -> ItemDraft) = _state.update { s -> s.copy(draft = s.draft?.let(change)) }

    fun onName(name: String) = edit { it.copy(name = name) }
    fun onLocation(location: Location) = edit { it.withLocation(location) }
    fun onStep(delta: Int) = edit { it.copy(quantity = it.quantity.stepped(delta)) }
    fun onUnit(unit: QuantityUnit) = edit { it.copy(quantity = it.quantity.copy(unit = unit)) }
    fun onExpires(date: LocalDate?) = edit { it.withExpires(date) }
    fun onQuickPick(pick: QuickPick) = edit { it.withExpires(pick.from(_state.value.today)) }
    fun onOpened(opened: Boolean) = edit { it.copy(opened = opened) }
    fun onPurchased(date: LocalDate) = edit { it.withPurchased(date) }
    fun onLowStock(amount: BigDecimal?) = edit { it.copy(lowStockAt = amount) }
    fun onNotes(notes: String) = edit { it.copy(notes = notes) }

    fun onSave() {
        val item = _state.value.draft?.toItem() ?: return
        viewModelScope.launch {
            pantry.save(item)
            // A named barcode fills in the next scan of it (#12).
            item.barcode?.let { pantry.rememberProduct(KnownProduct(it, item.name, item.location, item.quantity.unit)) }
            if (item.id == 0L) settings.setLastUsedLocation(item.location)
            _state.update { it.copy(done = true) }
        }
    }

    /** Removes the item. A correction, not consumption: no shopping line (`Consumption`, #48; reasons are #18). */
    fun onRemove() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            if (!draft.isNew) pantry.delete(draft.id)
            _state.update { it.copy(done = true) }
        }
    }
}
