package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.data.db.ShoppingEntryEntity
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShoppingRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context).addCallback(PantryDatabase.SeedBuiltIns).build()
    private val pantry = PantryRepository(db.items(), LocationRepository(db.locations(), db.items()), db.knownProducts())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "shopping_test.preferences_pb").apply { delete() }
    private val shopping = ShoppingRepository(db.shopping(), pantry, PreferenceDataStoreFactory.create(scope = scope) { file })
    private val today = LocalDate.of(2026, 11, 10)

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `list joins running-low items and stored lines`() = runBlocking {
        pantry.save(PantryItem(name = "Test eggs", location = Location.Fridge, quantity = Quantity(BigDecimal("6"), QuantityUnit.Each), purchased = today, lowStockAt = BigDecimal("6")))
        shopping.addByHand("Test scallions", today)
        shopping.addUsedUp("Test yogurt", today)
        assertEquals(listOf("Test eggs", "Test yogurt", "Test scallions"), shopping.lines().first().map { it.name })
    }

    @Test
    fun `finishing a trip takes off what was in the cart and keeps the rest`() = runBlocking {
        shopping.addByHand("Test scallions", today)
        shopping.addByHand("Test cottage cheese", today)
        val scallions = shopping.lines().first().first { it.name == "Test scallions" }
        shopping.setInCart(scallions.key, true)
        assertEquals(setOf(scallions.key), shopping.cart().first())
        shopping.finishTrip()
        assertEquals(listOf("Test cottage cheese"), shopping.lines().first().map { it.name })
        assertEquals(emptySet<String>(), shopping.cart().first())
    }

    @Test
    fun `a name already on the list isn't added again, whatever its reason`() = runBlocking {
        pantry.save(PantryItem(name = "Test milk", location = Location.Fridge, quantity = Quantity(BigDecimal("1"), QuantityUnit.Each), purchased = today, lowStockAt = BigDecimal("1")))
        assertEquals(null, shopping.addUsedUp(" test MILK ", today))
        val first = shopping.addByHand("Test bread", today)
        assertEquals(null, shopping.addUsedUp("Test bread", today))
        assertEquals(null, shopping.addByHand("test bread", today))
        assertEquals(listOf("Test milk", "Test bread"), shopping.lines().first().map { it.name })
        assertEquals(first, shopping.lines().first().last().entry?.id)
    }

    @Test
    fun `finishing a trip leaves no hidden duplicate behind`() = runBlocking {
        // Entries stored before names were deduplicated on insert: the list shows one "Test milk", the database
        // holds two.
        db.shopping().insert(ShoppingEntryEntity(name = "Test milk", reason = "USED_UP", addedOn = today.toEpochDay()))
        db.shopping().insert(ShoppingEntryEntity(name = "test milk", reason = "BY_HAND", addedOn = today.toEpochDay()))
        val milk = shopping.lines().first().single()
        shopping.setInCart(milk.key, true)
        shopping.finishTrip()
        assertEquals(emptyList<String>(), shopping.lines().first().map { it.name })
    }

    @Test
    fun `finishing a trip clears a used-up entry hidden behind a running-low line`() = runBlocking {
        val id = pantry.save(PantryItem(name = "Test milk", location = Location.Fridge, quantity = Quantity(BigDecimal("1"), QuantityUnit.Each), purchased = today, lowStockAt = BigDecimal("1")))
        db.shopping().insert(ShoppingEntryEntity(name = "Test milk", reason = "USED_UP", addedOn = today.toEpochDay()))
        val milk = shopping.lines().first().single()
        shopping.setInCart(milk.key, true)
        shopping.finishTrip()
        // Restocked after the trip: the running-low line leaves, and the hidden entry mustn't take its place.
        pantry.save(checkNotNull(pantry.item(id)).copy(quantity = Quantity(BigDecimal("4"), QuantityUnit.Each)))
        assertEquals(emptyList<String>(), shopping.lines().first().map { it.name })
    }
}
