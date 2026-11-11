package io.cosgriff.pantry.data

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PantryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context)
        .addCallback(PantryDatabase.SeedBuiltIns)
        .build()
    private val repo = PantryRepository(db.items(), db.locations())

    private val today = LocalDate.of(2026, 11, 10)

    private fun item(name: String, location: Location = Location.Fridge, expires: LocalDate? = null) = PantryItem(
        name = name,
        location = location,
        quantity = Quantity(BigDecimal("1.24"), QuantityUnit.Pound),
        purchased = today,
        expires = expires,
    )

    @After
    fun close() = db.close()

    @Test
    fun `new database has the three built-in locations in order`() = runBlocking {
        assertEquals(listOf(Location.Dry, Location.Fridge, Location.Freezer), repo.locations().first())
    }

    @Test
    fun `an item round-trips with every field`() = runBlocking {
        val saved = PantryItem(
            name = "Ground beef",
            barcode = "0096619321084",
            location = Location.Freezer,
            quantity = Quantity(BigDecimal("1.240"), QuantityUnit.Pound),
            purchased = today,
            expires = today.plusMonths(3),
            opened = true,
            lowStockAt = BigDecimal("0.5"),
            notes = "Made-up test item",
        )
        val id = repo.save(saved)
        assertEquals(saved.copy(id = id), repo.item(id))
    }

    @Test
    fun `items come soonest expiry first, undated last`() = runBlocking {
        repo.save(item("Rice", Location.Dry))
        repo.save(item("Milk", expires = today.plusDays(5)))
        repo.save(item("eggs", expires = today.plusDays(2)))
        repo.save(item("Butter", expires = today.plusDays(2)))
        assertEquals(listOf("Butter", "eggs", "Milk", "Rice"), repo.items().first().map { it.name })
    }

    @Test
    fun `saving an item with its id updates it`() = runBlocking {
        val id = repo.save(item("Milk"))
        repo.save(item("Oat milk").copy(id = id))
        assertEquals(listOf("Oat milk"), repo.items().first().map { it.name })
    }

    @Test
    fun `delete removes the item`() = runBlocking {
        val id = repo.save(item("Milk"))
        repo.delete(id)
        assertNull(repo.item(id))
        assertTrue(repo.items().first().isEmpty())
    }

    @Test
    fun `custom location is added after the built-in ones and holds items`() = runBlocking {
        val garage = repo.addLocation("Garage fridge", Period.ofDays(5))
        assertEquals(Location.Freezer, repo.locations().first()[2])
        assertEquals(garage, repo.locations().first()[3])
        val id = repo.save(item("Soda", location = garage))
        assertEquals(garage, repo.item(id)?.location)
    }

    @Test
    fun `last item with a barcode`() = runBlocking {
        repo.save(item("Old name").copy(barcode = "123"))
        repo.save(item("New name").copy(barcode = "123"))
        assertEquals("New name", repo.lastWithBarcode("123")?.name)
        assertNull(repo.lastWithBarcode("999"))
    }
}
