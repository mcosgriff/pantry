package io.cosgriff.pantry.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.ItemIntake
import io.cosgriff.pantry.data.LocationRepository
import io.cosgriff.pantry.data.LookupPermission
import io.cosgriff.pantry.data.Scanned
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ContributeChoice
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.NewProduct
import io.cosgriff.pantry.domain.ProductInfo
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.needingName
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
    /**
     * Set with [finished] when the item just added was named by the user and Open Food Facts doesn't have it:
     * the screen offers to share it there instead of closing (#42, `docs/adr/0004`).
     */
    val share: NewProduct? = null,
)

/**
 * The scanner's phase and sheet state. What a scanned code starts as, whether it may be looked up and what adding
 * saves are `ItemIntake`'s (#49).
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val intake: ItemIntake,
    private val locations: LocationRepository,
    private val settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanState(today = LocalDate.now(clock)))
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Read on the analysis thread: frames are dropped while anything but [ScanPhase.Looking] is showing. */
    @Volatile var paused: Boolean = false
        private set

    init {
        viewModelScope.launch {
            val prefs = settings.settings().first()
            val all = locations.locations().first()
            _state.update { it.copy(batch = prefs.batchScanByDefault, locations = all) }
        }
    }

    /** A barcode's text from the camera. Ignored while the sheet is open. */
    fun onRead(text: String) {
        if (paused) return
        val code = readRetailCode(text)
        if (code == ScannedCode.Invalid) return
        paused = true
        viewModelScope.launch {
            when (val scanned = intake.scan(code)) {
                is Scanned.StorePacked -> _state.update {
                    it.copy(today = scanned.today, phase = ScanPhase.StorePacked(scanned.code, scanned.priceCents, scanned.draft, recent = scanned.recent))
                }
                is Scanned.Product -> {
                    val lookup = when (scanned.lookup) {
                        LookupPermission.Yes -> Lookup.Running
                        LookupPermission.Ask -> Lookup.Ask
                        LookupPermission.No -> Lookup.None
                    }
                    _state.update { it.copy(today = scanned.today, phase = ScanPhase.Found(scanned.draft, known = scanned.known, lookup = lookup)) }
                    if (lookup == Lookup.Running) scanned.draft.barcode?.let { showLookup(it, intake.lookUp(it)) }
                }
                null -> paused = false
            }
        }
    }

    /** The sheet's answer to "Look it up in Open Food Facts?": kept in settings, and acted on for this barcode. */
    fun onLookupAnswer(yes: Boolean) {
        val found = _state.value.phase as? ScanPhase.Found ?: return
        val barcode = found.draft.barcode ?: return
        setLookup(barcode, if (yes) Lookup.Running else Lookup.None)
        viewModelScope.launch { showLookup(barcode, intake.answerLookup(barcode, yes)) }
    }

    /** Shows what looking [barcode] up found, if the sheet is still on it; `null` means it wasn't looked up. */
    private fun showLookup(barcode: String, result: LookupResult?) = _state.update { s ->
        if (result == null) return@update s.withLookup(barcode, Lookup.None)
        val phase = s.phase as? ScanPhase.Found ?: return@update s
        val draft = intake.withLookup(phase.draft, barcode, result) ?: return@update s
        val lookup = when (result) {
            is LookupResult.Found -> Lookup.Found(result.info)
            LookupResult.NotFound -> Lookup.NotFound
            LookupResult.Failed -> Lookup.Failed
        }
        s.copy(phase = phase.copy(draft = draft, lookup = lookup))
    }

    private fun setLookup(barcode: String, lookup: Lookup) = _state.update { it.withLookup(barcode, lookup) }

    private fun ScanState.withLookup(barcode: String, lookup: Lookup): ScanState {
        val phase = phase as? ScanPhase.Found ?: return this
        return if (phase.draft.barcode != barcode) this else copy(phase = phase.copy(lookup = lookup))
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
            is ScanPhase.StorePacked -> intake.weighed(phase.draft, phase.weight)
            ScanPhase.Looking -> null
        } ?: return
        if (_state.value.batch) {
            _state.update { it.copy(haul = it.haul + draft, phase = ScanPhase.Looking) }
            paused = false
            return
        }
        if (draft.name.isBlank()) return
        // Looked up and not found, then named here: Open Food Facts could have it too, unless the user said never.
        val notInOff = (phase as? ScanPhase.Found)?.takeIf { !it.known && it.lookup == Lookup.NotFound }
        viewModelScope.launch {
            if (!intake.save(draft)) return@launch
            val share = draft.barcode?.takeIf { notInOff != null }?.let { NewProduct(it, draft.name.trim()) }
                ?.takeIf { it.shareable && settings.settings().first().contributing != ContributeChoice.Never }
            _state.update { it.copy(phase = ScanPhase.Looking, finished = true, share = share) }
        }
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
        if (needingName(haul) > 0) return
        viewModelScope.launch {
            // One transaction: the whole haul is added, or none of it.
            if (intake.save(haul)) _state.update { it.copy(haul = emptyList(), finished = true) }
        }
    }

    /** Closes the sheet without adding ("Skip"), and looks again. */
    fun onDismiss() {
        _state.update { it.copy(phase = ScanPhase.Looking) }
        paused = false
    }
}
