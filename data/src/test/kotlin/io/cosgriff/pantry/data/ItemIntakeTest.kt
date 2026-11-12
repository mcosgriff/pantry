package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.ProductInfo
import io.cosgriff.pantry.domain.ScannedCode
import io.cosgriff.pantry.domain.readRetailCode
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemIntakeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context).addCallback(PantryDatabase.SeedBuiltIns).build()
    private val locations = LocationRepository(db.locations(), db.items())
    private val pantry = PantryRepository(db.items(), locations, db.knownProducts())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "intake_test.preferences_pb").apply { delete() }
    private val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, locations)

    /** Every barcode sent to Open Food Facts, in order. */
    private val sent = mutableListOf<String>()
    private var answer: LookupResult = LookupResult.Found(ProductInfo("Test sparkling water", "Test brand", "12 fl oz"))
    private val products = ProductLookup { barcode -> sent += barcode; answer }

    /** The one clock the module reads (#52): today is 2026-11-10. */
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.now(clock)
    private val intake = ItemIntake(db, pantry, settings, products, clock)

    // Made-up codes with valid check digits.
    private val product = readRetailCode("096619321087") as ScannedCode.Product
    private val storePackedLabel = readRetailCode("204711018426") as ScannedCode.VariableWeight

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun scanProduct() = intake.scan(product) as Scanned.Product

    private suspend fun setLookup(choice: LookupChoice) {
        if (choice != LookupChoice.Ask) settings.setProductLookup(choice == LookupChoice.On)
    }

    // What a scanned code starts as.

    @Test
    fun `a known product fills in its name, location and unit, dated by the clock`() = runBlocking {
        pantry.rememberProduct(KnownProduct(product.code, "Test oats", Location.Fridge, AmountUnit.Packages(Package.Bag)))
        val scanned = scanProduct()
        assertTrue(scanned.known)
        assertEquals(today, scanned.today)
        assertEquals("Test oats", scanned.draft.name)
        assertEquals(Location.Fridge, scanned.draft.location)
        assertEquals(AmountUnit.Packages(Package.Bag).of(BigDecimal.ONE), scanned.draft.quantity)
        assertEquals(today, scanned.draft.purchased)
        assertEquals(product.code, scanned.draft.barcode)
        assertFalse(scanned.draft.storePacked)
    }

    @Test
    fun `before known products, the last item with the barcode fills it in`() = runBlocking {
        pantry.save(PantryItem(name = "Test rice", barcode = product.code, location = Location.Dry, quantity = Measure(BigDecimal("2"), MeasureUnit.Pound), purchased = today))
        val scanned = scanProduct()
        assertTrue(scanned.known)
        assertEquals("Test rice", scanned.draft.name)
        assertEquals(Measure(BigDecimal.ONE, MeasureUnit.Pound), scanned.draft.quantity)
    }

    @Test
    fun `a known product whose location was deleted starts where a new item does`() = runBlocking {
        val garage = locations.addLocation("Test garage", null)
        pantry.rememberProduct(KnownProduct(product.code, "Test soda", garage, AmountUnit.Measured(MeasureUnit.Each)))
        assertTrue(locations.deleteLocation(garage))
        settings.setDefaultLocation(DefaultLocation.Fixed(Location.Fridge))
        val scanned = scanProduct()
        assertEquals("Test soda", scanned.draft.name)
        assertEquals(Location.Fridge, scanned.draft.location)
    }

    @Test
    fun `an unknown product starts blank where a new item does`() = runBlocking {
        settings.setLastUsedLocation(Location.Freezer)
        val scanned = scanProduct()
        assertFalse(scanned.known)
        assertEquals("", scanned.draft.name)
        assertEquals(Location.Freezer, scanned.draft.location)
    }

    @Test
    fun `a store-packed label starts in the Freezer weighing nothing, with its price and the recent names`() = runBlocking {
        settings.rememberStorePacked("Test chicken thighs")
        val scanned = intake.scan(storePackedLabel) as Scanned.StorePacked
        assertEquals(storePackedLabel.code, scanned.code)
        assertNotNull(scanned.priceCents)
        assertEquals(Location.Freezer, scanned.draft.location)
        assertEquals(Measure(BigDecimal.ZERO, MeasureUnit.Pound), scanned.draft.quantity)
        assertNull(scanned.draft.barcode)
        assertTrue(scanned.draft.storePacked)
        assertEquals(listOf("Test chicken thighs"), scanned.recent)
    }

    @Test
    fun `an invalid code starts nothing`() = runBlocking {
        assertNull(intake.scan(ScannedCode.Invalid))
    }

    // docs/adr/0001: only a barcode the pantry doesn't know is looked up, only once the user said yes, and a
    // store-packed code never is. Only the barcode is sent.

    @Test
    fun `ADR-0001 - only an unknown barcode is looked up, and only once the user said yes`() = runBlocking {
        // Never answered: ask first, send nothing.
        assertEquals(LookupPermission.Ask, scanProduct().lookup)
        assertNull(intake.lookUp(product.code))

        // Said no: not offered, nothing sent, and the answer is kept.
        assertNull(intake.answerLookup(product.code, yes = false))
        assertEquals(LookupChoice.Off, settings.settings().first().productLookup)
        assertEquals(LookupPermission.No, scanProduct().lookup)
        assertNull(intake.lookUp(product.code))
        assertEquals(emptyList<String>(), sent)

        // Said yes: the barcode, and only it, goes out.
        assertEquals(answer, intake.answerLookup(product.code, yes = true))
        assertEquals(LookupChoice.On, settings.settings().first().productLookup)
        assertEquals(LookupPermission.Yes, scanProduct().lookup)
        assertEquals(answer, intake.lookUp(product.code))
        assertEquals(listOf(product.code, product.code), sent)
    }

    @Test
    fun `ADR-0001 - a barcode the pantry knows is never looked up, even with lookup on`() = runBlocking {
        setLookup(LookupChoice.On)
        pantry.rememberProduct(KnownProduct(product.code, "Test oats", Location.Dry, AmountUnit.Measured(MeasureUnit.Each)))
        assertEquals(LookupPermission.No, scanProduct().lookup)
        assertNull(intake.lookUp(product.code))
        assertNull(intake.answerLookup(product.code, yes = true))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `ADR-0001 - a store-packed code is never looked up, even with lookup on`() = runBlocking {
        setLookup(LookupChoice.On)
        intake.scan(storePackedLabel)
        assertNull(intake.lookUp(storePackedLabel.code))
        assertNull(intake.answerLookup(storePackedLabel.code, yes = true))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `a lookup fills in a blank name, keeps a typed one, and is ignored once the sheet moved on`() {
        val found = LookupResult.Found(ProductInfo("Test sparkling water", null, null))
        val draft = ItemDraft.new(today, barcode = product.code)
        assertEquals("Test sparkling water", intake.withLookup(draft, product.code, found)?.name)
        assertEquals("Typed", intake.withLookup(draft.copy(name = "Typed"), product.code, found)?.name)
        assertEquals(draft, intake.withLookup(draft, product.code, LookupResult.NotFound))
        assertNull(intake.withLookup(draft, "4006381333931", found))
    }

    @Test
    fun `a store-packed weight is a positive number of pounds`() {
        val draft = ItemDraft.new(today, Location.Freezer).copy(storePacked = true)
        assertEquals(Measure(BigDecimal("2.5"), MeasureUnit.Pound), intake.weighed(draft, " 2.5 ")?.quantity)
        listOf("", "0", "-1", "abc", "2.5 lb").forEach { assertNull(it, intake.weighed(draft, it)) }
    }

    // Adding: one save path for scan and item details.

    @Test
    fun `a new scanned product is saved, remembered by barcode, and sets the last used location`() = runBlocking {
        val draft = scanProduct().draft.copy(name = "Test oats").withLocation(Location.Fridge)
        assertTrue(intake.save(draft))
        assertEquals(listOf("Test oats"), pantry.items().first().map { it.name })
        assertEquals(KnownProduct(product.code, "Test oats", Location.Fridge, AmountUnit.Measured(MeasureUnit.Each)), pantry.knownProduct(product.code))
        assertEquals(Location.Fridge, settings.newItemLocation())
    }

    @Test
    fun `a store-packed item is saved and its name kept among the recent ones, never as a known product`() = runBlocking {
        val scanned = intake.scan(storePackedLabel) as Scanned.StorePacked
        val draft = checkNotNull(intake.weighed(scanned.draft.copy(name = "Test ground beef"), "1.2"))
        assertTrue(intake.save(draft))
        assertEquals(listOf("Test ground beef"), settings.recentStorePacked().first())
        assertNull(pantry.knownProduct(storePackedLabel.code))
        assertEquals(Location.Freezer, settings.newItemLocation())
    }

    @Test
    fun `editing a stored item updates its known product but not the last used location`() = runBlocking {
        val id = pantry.save(PantryItem(name = "Test oats", barcode = product.code, location = Location.Dry, quantity = Measure(BigDecimal.ONE, MeasureUnit.Each), purchased = today))
        settings.setLastUsedLocation(Location.Fridge)
        val edited = ItemDraft.of(checkNotNull(pantry.item(id))).copy(name = "Test rolled oats").withLocation(Location.Freezer)
        assertTrue(intake.save(edited))
        assertEquals(listOf("Test rolled oats"), pantry.items().first().map { it.name })
        assertEquals("Test rolled oats", pantry.knownProduct(product.code)?.name)
        assertEquals(Location.Fridge, settings.newItemLocation())
    }

    @Test
    fun `a new item typed in by hand starts where a new item does, dated by the clock`() = runBlocking {
        settings.setDefaultLocation(DefaultLocation.Fixed(Location.Fridge))
        val draft = intake.byHand("Test bread", product.code)
        assertEquals("Test bread", draft.name)
        assertEquals(Location.Fridge, draft.location)
        assertEquals(today, draft.purchased)
        assertEquals(product.code, draft.barcode)
    }

    @Test
    fun `nothing is saved while a name is blank`() = runBlocking {
        val named = ItemDraft.new(today).copy(name = "Test tea")
        assertFalse(intake.save(listOf(named, ItemDraft.new(today))))
        assertEquals(emptyList<PantryItem>(), pantry.items().first())
    }

    @Test
    fun `a haul is added in one go, the last new item's location used last`() = runBlocking {
        val oats = scanProduct().draft.copy(name = "Test oats")
        val beef = ItemDraft.new(today, Location.Freezer).copy(name = "Test ground beef", storePacked = true)
        val milk = ItemDraft.new(today, Location.Fridge).copy(name = "Test milk")
        assertTrue(intake.save(listOf(oats, beef, milk)))
        assertEquals(setOf("Test oats", "Test ground beef", "Test milk"), pantry.items().first().map { it.name }.toSet())
        assertEquals("Test oats", pantry.knownProduct(product.code)?.name)
        assertEquals(listOf("Test ground beef"), settings.recentStorePacked().first())
        assertEquals(Location.Fridge, settings.newItemLocation())
    }

    @Test
    fun `a haul that fails part way saves none of it`() = runBlocking {
        val oats = scanProduct().draft.copy(name = "Test oats")
        // A location that isn't in the database: its item can't be saved.
        val missing = ItemDraft.new(today, Location.Custom("Test gone", null, id = 999)).copy(name = "Test lost")
        val failed = runCatching { intake.save(listOf(oats, missing)) }
        assertTrue(failed.isFailure)
        assertEquals(emptyList<PantryItem>(), pantry.items().first())
        assertNull(pantry.knownProduct(product.code))
    }
}
