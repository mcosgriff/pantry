package io.cosgriff.pantry.data

import androidx.room3.withWriteTransaction
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.DeletedLocation
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.countedIn
import io.cosgriff.pantry.domain.number
import io.cosgriff.pantry.domain.readRetailCode
import io.cosgriff.pantry.domain.storePackedPriceCents
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** A weight typed in the user's locale ("1,5" in German), if it is a positive number. */
fun parseWeight(text: String): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val number = NumberFormat.getNumberInstance().parse(trimmed, position) ?: return null
    if (position.index != trimmed.length) return null
    return BigDecimal(number.toString()).takeIf { it.signum() > 0 }
}

/** Whether a scanned product may be looked up in Open Food Facts (`docs/adr/0001`). */
enum class LookupPermission {
    /** Not looked up: the pantry knows the barcode, or the user said no. */
    No,

    /** Lookup was never answered: ask first, saying only the barcode is sent. */
    Ask,

    /** The user said yes and the pantry doesn't know the barcode: look it up now. */
    Yes,
}

/** What the scan sheet starts on for a code read from a label (#49). */
sealed interface Scanned {
    /** The day the draft was started on, from the module's clock. */
    val today: LocalDate

    /**
     * A retail product: [draft] is filled in from what the pantry knows of the barcode ([known]), and [lookup]
     * says whether Open Food Facts may be asked.
     */
    data class Product(
        override val today: LocalDate,
        val draft: ItemDraft,
        val known: Boolean,
        val lookup: LookupPermission,
    ) : Scanned

    /**
     * A store-packed label (#13): [code] as printed, the label's price if the code carries one, a [draft] in the
     * Freezer weighing nothing yet, and the [recent] store-packed names to pick from.
     */
    data class StorePacked(
        override val today: LocalDate,
        val code: String,
        val priceCents: Int?,
        val draft: ItemDraft,
        val recent: List<String>,
    ) : Scanned
}

/**
 * Item intake (#49): from a code read off a label, or a name typed by hand, to items saved in the pantry. Its
 * interface answers two questions:
 *
 * - "Given this scanned code, what's the starting draft, and may it be looked up?" ([scan], [lookUp],
 *   [answerLookup], [withLookup]). The `docs/adr/0001` privacy rule lives here: only a barcode the pantry doesn't
 *   know is looked up, only once the user said yes, and a store-packed code never is. Open Food Facts stays
 *   behind the [ProductLookup] seam.
 * - "Add these items" ([save]): scan's single add, batch scan's haul and Item details' save all go through it.
 *
 * Saving new items and editing a stored one differ on purpose:
 *
 * - **Known product (#12):** a draft with a barcode is remembered, new or edited, so renaming an item fixes what
 *   the next scan of it fills in. A store-packed item never is: its code changes with every package.
 * - **Recent store-packed names (#13):** only from a store-packed label's draft ([ItemDraft.storePacked]).
 * - **Last used location:** only from a new item; moving a stored item isn't where the last new item went.
 *
 * The clock is read here, through the injected [Clock] (#52), for the day a draft starts on.
 */
@Singleton
class ItemIntake @Inject constructor(
    private val db: PantryDatabase,
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
    private val products: ProductLookup,
    private val clock: Clock,
) {
    /** The draft a scanned [code] starts on; `null` for a code that isn't a retail barcode. */
    suspend fun scan(code: ScannedCode): Scanned? {
        val today = LocalDate.now(clock)
        return when (code) {
            is ScannedCode.VariableWeight -> {
                // Never looked up or remembered as a product: the code changes with every package.
                val draft = ItemDraft.new(today, Location.Freezer)
                    .copy(quantity = Measure(BigDecimal.ZERO, MeasureUnit.Pound), storePacked = true)
                Scanned.StorePacked(today, code.code, storePackedPriceCents(code), draft, settings.recentStorePacked().first())
            }
            is ScannedCode.Product -> {
                val known = known(code.code)
                val location = DeletedLocation.scannedProduct(known?.location, settings.newItemLocation())
                val draft = ItemDraft.new(today, location, barcode = code.code).let { d ->
                    if (known == null) d else d.copy(name = known.name, quantity = known.unit.of(d.quantity.number))
                }
                Scanned.Product(today, draft, known = known != null, lookup = if (known == null) permission() else LookupPermission.No)
            }
            ScannedCode.Invalid -> null
        }
    }

    /** A new item typed in by hand, with [name] and [barcode] if the scanner handed them over. */
    suspend fun byHand(name: String = "", barcode: String? = null): ItemDraft =
        ItemDraft.new(LocalDate.now(clock), settings.newItemLocation(), barcode = barcode).copy(name = name)

    /**
     * Looks [barcode] up in Open Food Facts, if `docs/adr/0001` allows it now: a retail product's code, not known
     * to the pantry, with lookup switched on. `null` when it doesn't, and nothing is sent.
     */
    suspend fun lookUp(barcode: String): LookupResult? {
        if (readRetailCode(barcode) !is ScannedCode.Product) return null
        if (known(barcode) != null || permission() != LookupPermission.Yes) return null
        return products.lookUp(barcode)
    }

    /** The answer to "Look it up in Open Food Facts?", kept for next time; a yes looks [barcode] up as [lookUp] does. */
    suspend fun answerLookup(barcode: String, yes: Boolean): LookupResult? {
        settings.setProductLookup(yes)
        return if (yes) lookUp(barcode) else null
    }

    /**
     * [draft] with what a lookup of [barcode] found: its name, unless the user typed one meanwhile. `null` when
     * the draft is for another barcode now (the sheet moved on while the lookup was out): ignore the result.
     */
    fun withLookup(draft: ItemDraft, barcode: String, result: LookupResult): ItemDraft? {
        if (draft.barcode != barcode) return null
        val name = (result as? LookupResult.Found)?.info?.suggestedName
        return if (name != null && draft.name.isBlank()) draft.copy(name = name) else draft
    }

    /** A store-packed [draft] weighing [weight] pounds as typed, or `null` until that is a positive number. */
    fun weighed(draft: ItemDraft, weight: String): ItemDraft? =
        parseWeight(weight)?.let { pounds -> draft.copy(quantity = Measure(pounds, MeasureUnit.Pound)) }

    /**
     * Saves [drafts], new or edited: returns false, saving nothing, while any of them has no name. The items and
     * known products go in one transaction, so a haul is added whole or not at all. The settings that follow
     * (recent store-packed names, last used location) are written once it commits.
     */
    suspend fun save(drafts: List<ItemDraft>): Boolean {
        val items = drafts.map { draft -> (draft.toItem() ?: return false) to draft.storePacked }
        db.withWriteTransaction {
            items.forEach { (item, storePacked) ->
                pantry.save(item)
                if (!storePacked) item.barcode?.let { pantry.rememberProduct(KnownProduct(it, item.name, item.location, item.quantity.countedIn)) }
            }
        }
        items.filter { (_, storePacked) -> storePacked }.forEach { (item, _) -> settings.rememberStorePacked(item.name) }
        items.lastOrNull { (item, _) -> item.id == 0L }?.let { (item, _) -> settings.setLastUsedLocation(item.location) }
        return true
    }

    /** Saves one [draft]; see [save]. */
    suspend fun save(draft: ItemDraft): Boolean = save(listOf(draft))

    /** A name the user gave [barcode] (#12); before that table existed, the last item with it. */
    private suspend fun known(barcode: String): KnownProduct? = pantry.knownProduct(barcode)
        ?: pantry.lastWithBarcode(barcode)?.let { KnownProduct(barcode, it.name, it.location, it.quantity.countedIn) }

    private suspend fun permission(): LookupPermission = when (settings.settings().first().productLookup) {
        LookupChoice.On -> LookupPermission.Yes
        LookupChoice.Ask -> LookupPermission.Ask
        LookupChoice.Off -> LookupPermission.No
    }
}
