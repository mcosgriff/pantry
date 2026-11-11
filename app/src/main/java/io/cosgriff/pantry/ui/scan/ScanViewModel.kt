package io.cosgriff.pantry.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.readRetailCode
import io.cosgriff.pantry.domain.storePackedPriceCents
import io.cosgriff.pantry.domain.stepped
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A weight typed in the user's locale ("1,5" in German), if it is a positive number. */
fun parseWeight(text: String): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val number = NumberFormat.getNumberInstance().parse(trimmed, position) ?: return null
    if (position.index != trimmed.length) return null
    return BigDecimal(number.toString()).takeIf { it.signum() > 0 }
}

/** What the scanner is doing. */
sealed interface ScanPhase {
    data object Looking : ScanPhase

    /** A product was read: the sheet is open on [draft]. [known] when the pantry has had this barcode before. */
    data class Found(val draft: ItemDraft, val known: Boolean) : ScanPhase

    /**
     * A store-packed label was read (#13): [code] as printed, the label's price if the code carries one, the
     * item being named ([draft], in the Freezer by default) and its [weight] as typed, in pounds.
     */
    data class StorePacked(
        val code: String,
        val priceCents: Int?,
        val draft: ItemDraft,
        val weight: String = "",
        val recent: List<String> = emptyList(),
    ) : ScanPhase
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
                is ScannedCode.VariableWeight -> {
                    val today = LocalDate.now(clock)
                    // Never looked up or remembered: the code changes with every package.
                    val draft = ItemDraft.new(today, Location.Freezer).copy(quantity = Quantity(BigDecimal.ZERO, QuantityUnit.Pound))
                    val recent = settings.recentStorePacked().first()
                    _state.update {
                        it.copy(today = today, phase = ScanPhase.StorePacked(code.code, storePackedPriceCents(code), draft, recent = recent))
                    }
                }
                is ScannedCode.Product -> {
                    val today = LocalDate.now(clock)
                    // A name the user gave this barcode (#12); before that table, the last item with it.
                    val known = pantry.knownProduct(code.code)
                        ?: pantry.lastWithBarcode(code.code)?.let { KnownProduct(code.code, it.name, it.location, it.quantity.unit) }
                    val location = known?.location ?: settings.newItemLocation()
                    val draft = ItemDraft.new(today, location, barcode = code.code).let { d ->
                        if (known == null) d else d.copy(name = known.name, quantity = d.quantity.copy(unit = known.unit))
                    }
                    _state.update { it.copy(today = today, phase = ScanPhase.Found(draft, known = known != null)) }
                }
                ScannedCode.Invalid -> Unit
            }
        }
    }

    private fun edit(change: (ItemDraft) -> ItemDraft) = _state.update { s ->
        when (val phase = s.phase) {
            is ScanPhase.Found -> s.copy(phase = phase.copy(draft = change(phase.draft)))
            is ScanPhase.StorePacked -> s.copy(phase = phase.copy(draft = change(phase.draft)))
            ScanPhase.Looking -> s
        }
    }

    fun onName(name: String) = edit { it.copy(name = name) }
    fun onLocation(location: Location) = edit { it.withLocation(location) }
    fun onStep(delta: Int) = edit { it.copy(quantity = it.quantity.stepped(delta)) }
    fun onExpires(date: LocalDate?) = edit { it.withExpires(date) }

    /** The store-packed sheet's weight, as typed. */
    fun onWeight(text: String) = _state.update { s ->
        val phase = s.phase as? ScanPhase.StorePacked ?: return@update s
        s.copy(phase = phase.copy(weight = text))
    }

    /** Saves the item. In batch scan the camera goes on looking; otherwise the screen closes. */
    fun onAdd() {
        val phase = _state.value.phase
        val item = when (phase) {
            is ScanPhase.Found -> phase.draft.toItem()
            is ScanPhase.StorePacked -> parseWeight(phase.weight)?.let { pounds ->
                phase.draft.copy(quantity = Quantity(pounds, QuantityUnit.Pound)).toItem()
            }
            ScanPhase.Looking -> null
        } ?: return
        viewModelScope.launch {
            pantry.save(item)
            when (phase) {
                is ScanPhase.Found -> item.barcode?.let { pantry.rememberProduct(KnownProduct(it, item.name, item.location, item.quantity.unit)) }
                is ScanPhase.StorePacked -> settings.rememberStorePacked(item.name)
                ScanPhase.Looking -> Unit
            }
            settings.setLastUsedLocation(item.location)
            _state.update { it.copy(added = it.added + 1, phase = ScanPhase.Looking, finished = !it.batch) }
            paused = false
        }
    }

    /** Closes the sheet without adding ("Skip"), and looks again. */
    fun onDismiss() {
        _state.update { it.copy(phase = ScanPhase.Looking) }
        paused = false
    }
}
