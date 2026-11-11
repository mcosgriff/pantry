package io.cosgriff.pantry.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.ProductLookup
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.ProductInfo
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.haulItems
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

    /**
     * A product was read: the sheet is open on [draft]. [known] when the pantry has had this barcode before;
     * otherwise [lookup] says where looking it up in Open Food Facts stands (#9).
     */
    data class Found(val draft: ItemDraft, val known: Boolean, val lookup: Lookup = Lookup.None) : ScanPhase

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

/** Open Food Facts lookup on the scan sheet (#9, `docs/adr/0001`). */
sealed interface Lookup {
    /** Not looked up: the pantry knows it, or lookup is off. */
    data object None : Lookup

    /** Lookup has never been answered: the sheet offers it, saying only the barcode is sent. */
    data object Ask : Lookup

    data object Running : Lookup

    data class Found(val info: ProductInfo) : Lookup

    data object NotFound : Lookup

    /** Offline, slow or over the rate limit: named by hand. */
    data object Failed : Lookup
}

data class ScanState(
    val today: LocalDate,
    val phase: ScanPhase = ScanPhase.Looking,
    val batch: Boolean = false,
    /** Batch scan's items so far, not yet in the pantry (#14). Lost if Android ends the app before they're added. */
    val haul: List<ItemDraft> = emptyList(),
    /** The Review haul screen is showing. */
    val reviewing: Boolean = false,
    val locations: List<Location> = Location.defaults,
    /** Set once items are in the pantry (one outside batch scan, the haul in it): the screen closes. */
    val finished: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
    private val products: ProductLookup,
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
                    // Only a barcode the pantry doesn't know is looked up, and only once the user has said yes.
                    val lookup = if (known != null) {
                        Lookup.None
                    } else {
                        when (settings.settings().first().productLookup) {
                            LookupChoice.On -> Lookup.Running
                            LookupChoice.Ask -> Lookup.Ask
                            LookupChoice.Off -> Lookup.None
                        }
                    }
                    _state.update { it.copy(today = today, phase = ScanPhase.Found(draft, known = known != null, lookup = lookup)) }
                    if (lookup == Lookup.Running) lookUp(code.code)
                }
                ScannedCode.Invalid -> Unit
            }
        }
    }

    /** The sheet's answer to "Look it up in Open Food Facts?": kept in settings, and acted on for this barcode. */
    fun onLookupAnswer(yes: Boolean) {
        val found = _state.value.phase as? ScanPhase.Found ?: return
        viewModelScope.launch { settings.setProductLookup(yes) }
        val barcode = found.draft.barcode
        if (yes && barcode != null) {
            setLookup(barcode, Lookup.Running)
            viewModelScope.launch { lookUp(barcode) }
        } else {
            setLookup(barcode, Lookup.None)
        }
    }

    /** Looks [barcode] up, and fills in the name unless the user has typed one meanwhile. */
    private suspend fun lookUp(barcode: String) {
        val result = when (val r = products.lookUp(barcode)) {
            is LookupResult.Found -> Lookup.Found(r.info)
            LookupResult.NotFound -> Lookup.NotFound
            LookupResult.Failed -> Lookup.Failed
        }
        _state.update { s ->
            val phase = s.phase as? ScanPhase.Found ?: return@update s
            // The sheet may have moved on to another barcode while this one was out.
            if (phase.draft.barcode != barcode) return@update s
            val name = (result as? Lookup.Found)?.info?.suggestedName
            val draft = if (name != null && phase.draft.name.isBlank()) phase.draft.copy(name = name) else phase.draft
            s.copy(phase = phase.copy(draft = draft, lookup = result))
        }
    }

    private fun setLookup(barcode: String?, lookup: Lookup) = _state.update { s ->
        val phase = s.phase as? ScanPhase.Found ?: return@update s
        if (phase.draft.barcode != barcode) s else s.copy(phase = phase.copy(lookup = lookup))
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

    /**
     * In batch scan, puts the sheet's item in the haul (a name can wait for the review) and looks again; otherwise
     * saves it and closes the screen.
     */
    fun onAdd() {
        val phase = _state.value.phase
        val draft = when (phase) {
            is ScanPhase.Found -> phase.draft
            is ScanPhase.StorePacked -> parseWeight(phase.weight)?.let { pounds -> phase.draft.copy(quantity = Quantity(pounds, QuantityUnit.Pound)) }
            ScanPhase.Looking -> null
        } ?: return
        if (_state.value.batch) {
            _state.update { it.copy(haul = it.haul + draft, phase = ScanPhase.Looking) }
            paused = false
            return
        }
        val item = draft.toItem() ?: return
        viewModelScope.launch {
            save(item, storePacked = phase is ScanPhase.StorePacked)
            _state.update { it.copy(phase = ScanPhase.Looking, finished = true) }
        }
    }

    private suspend fun save(item: PantryItem, storePacked: Boolean) {
        pantry.save(item)
        if (storePacked) {
            settings.rememberStorePacked(item.name)
        } else {
            item.barcode?.let { pantry.rememberProduct(KnownProduct(it, item.name, item.location, item.quantity.unit)) }
        }
        settings.setLastUsedLocation(item.location)
    }

    /** Opens Review haul; the camera stops reading meanwhile. */
    fun onReview() {
        paused = true
        _state.update { it.copy(reviewing = true, phase = ScanPhase.Looking) }
    }

    /** Back from Review haul to the scanner. */
    fun onScanMore() {
        _state.update { it.copy(reviewing = false) }
        paused = false
    }

    fun onRename(index: Int, name: String) = _state.update { s ->
        s.copy(haul = s.haul.mapIndexed { i, d -> if (i == index) d.copy(name = name) else d })
    }

    fun onRemoveFromHaul(index: Int) = _state.update { s -> s.copy(haul = s.haul.filterIndexed { i, _ -> i != index }) }

    /** Adds the whole haul once every item has a name, then closes the screen. */
    fun onAddAll() {
        val haul = _state.value.haul
        val items = haulItems(haul) ?: return
        viewModelScope.launch {
            // Every scanned product has its barcode; only a store-packed item has none.
            items.forEach { save(it, storePacked = it.barcode == null) }
            _state.update { it.copy(haul = emptyList(), finished = true) }
        }
    }

    /** Closes the sheet without adding ("Skip"), and looks again. */
    fun onDismiss() {
        _state.update { it.copy(phase = ScanPhase.Looking) }
        paused = false
    }
}
