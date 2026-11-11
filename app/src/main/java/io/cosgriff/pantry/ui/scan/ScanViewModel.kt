package io.cosgriff.pantry.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.readRetailCode
import io.cosgriff.pantry.domain.stepped
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the scanner is doing. */
sealed interface ScanPhase {
    data object Looking : ScanPhase

    /** A product was read: the sheet is open on [draft]. [known] when the pantry has had this barcode before. */
    data class Found(val draft: ItemDraft, val known: Boolean) : ScanPhase

    /** A store-packed label was read; it is added by hand (#13). */
    data object StorePacked : ScanPhase
}

data class ScanState(
    val today: LocalDate,
    val phase: ScanPhase = ScanPhase.Looking,
    val batch: Boolean = false,
    val added: Int = 0,
    val locations: List<Location> = Location.defaults,
    /** Set once an item is added outside batch scan: the screen closes. */
    val finished: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanState(today = LocalDate.now(clock)))
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Read on the analysis thread: frames are dropped while anything but [ScanPhase.Looking] is showing. */
    @Volatile var paused: Boolean = false
        private set

    init {
        viewModelScope.launch {
            val prefs = settings.settings().first()
            val locations = pantry.locations().first()
            _state.update { it.copy(batch = prefs.batchScanByDefault, locations = locations) }
        }
    }

    /** A barcode's text from the camera. Ignored while the sheet is open. */
    fun onRead(text: String) {
        if (paused) return
        val code = readRetailCode(text)
        if (code == ScannedCode.Invalid) return
        paused = true
        viewModelScope.launch {
            when (code) {
                is ScannedCode.VariableWeight -> _state.update { it.copy(phase = ScanPhase.StorePacked) }
                is ScannedCode.Product -> {
                    val today = LocalDate.now(clock)
                    val seen = pantry.lastWithBarcode(code.code)
                    val location = seen?.location ?: settings.newItemLocation()
                    val draft = ItemDraft.new(today, location, barcode = code.code).let { d ->
                        if (seen == null) d else d.copy(name = seen.name, quantity = d.quantity.copy(unit = seen.quantity.unit))
                    }
                    _state.update { it.copy(today = today, phase = ScanPhase.Found(draft, known = seen != null)) }
                }
                ScannedCode.Invalid -> Unit
            }
        }
    }

    private fun edit(change: (ItemDraft) -> ItemDraft) = _state.update { s ->
        val phase = s.phase as? ScanPhase.Found ?: return@update s
        s.copy(phase = phase.copy(draft = change(phase.draft)))
    }

    fun onName(name: String) = edit { it.copy(name = name) }
    fun onLocation(location: Location) = edit { it.withLocation(location) }
    fun onStep(delta: Int) = edit { it.copy(quantity = it.quantity.stepped(delta)) }
    fun onExpires(date: LocalDate?) = edit { it.withExpires(date) }

    /** Saves the item. In batch scan the camera goes on looking; otherwise the screen closes. */
    fun onAdd() {
        val found = _state.value.phase as? ScanPhase.Found ?: return
        val item = found.draft.toItem() ?: return
        viewModelScope.launch {
            pantry.save(item)
            settings.setLastUsedLocation(item.location)
            _state.update { it.copy(added = it.added + 1, phase = ScanPhase.Looking, finished = !it.batch) }
            paused = false
        }
    }

    /** Closes the sheet without adding, and looks again. */
    fun onDismiss() {
        _state.update { it.copy(phase = ScanPhase.Looking) }
        paused = false
    }
}
