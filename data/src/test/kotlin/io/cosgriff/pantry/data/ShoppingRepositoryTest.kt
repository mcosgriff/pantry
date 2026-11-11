package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.ShoppingReason
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
    private val pantry = PantryRepository(db.items(), db.locations(), db.knownProducts())
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
        shopping.add("Test scallions", ShoppingReason.ByHand, today)
        shopping.add("Test yogurt", ShoppingReason.UsedUp, today)
        assertEquals(listOf("Test eggs", "Test yogurt", "Test scallions"), shopping.lines().first().map { it.name })
    }

    @Test
    fun `finishing a trip takes off what was in the cart and keeps the rest`() = runBlocking {
        shopping.add("Test scallions", ShoppingReason.ByHand, today)
        shopping.add("Test cottage cheese", ShoppingReason.ByHand, today)
        val scallions = shopping.lines().first().first { it.name == "Test scallions" }
        shopping.setInCart(scallions.key, true)
        assertEquals(setOf(scallions.key), shopping.cart().first())
        shopping.finishTrip()
        assertEquals(listOf("Test cottage cheese"), shopping.lines().first().map { it.name })
        assertEquals(emptySet<String>(), shopping.cart().first())
    }
}
