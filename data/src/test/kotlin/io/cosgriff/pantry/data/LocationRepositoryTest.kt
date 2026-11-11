package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LocationKey
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The locations module (#51): the list, resolving a key or id, and what each use does once a location is deleted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context)
        .addCallback(PantryDatabase.SeedBuiltIns)
        .build()
    private val locations = LocationRepository(db.locations(), db.items())
    private val pantry = PantryRepository(db.items(), locations, db.knownProducts())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "locations_test.preferences_pb").apply { delete() }
    private val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, locations)

    private fun item(name: String, location: Location) = PantryItem(
        name = name,
        location = location,
        quantity = Quantity(BigDecimal.ONE, QuantityUnit.Each),
        purchased = LocalDate.of(2026, 11, 10),
    )

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `new database has the three built-in locations in order`() = runBlocking {
        assertEquals(listOf(Location.Dry, Location.Fridge, Location.Freezer), locations.locations().first())
    }

    @Test
    fun `custom location is added after the built-in ones`() = runBlocking {
        val garage = locations.addLocation("Garage fridge", Period.ofDays(5))
        assertEquals(Location.Freezer, locations.locations().first()[2])
        assertEquals(garage, locations.locations().first()[3])
    }

    @Test
    fun `a custom location can be renamed and its shelf life changed`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        val renamed = garage.copy(name = "Garage fridge", shelfLife = Period.ofDays(4))
        locations.updateLocation(renamed)
        assertEquals(renamed, locations.locations().first().last())
    }

    @Test
    fun `a key resolves to the location as it is now`() = runBlocking {
        assertEquals(Location.Fridge, locations.location(LocationKey.BuiltIn(Location.Fridge)))
        val garage = locations.addLocation("Garage", null)
        locations.updateLocation(garage.copy(name = "Garage fridge"))
        assertEquals(garage.copy(name = "Garage fridge"), locations.location(LocationKey.of(garage)))
        assertNull(locations.location(LocationKey.Custom(404)))
    }

    // What each use does once a location is deleted: `DeletedLocation`'s table, end to end.

    @Test
    fun `an item - a location holding items is not deleted, an empty one is`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        val id = pantry.save(item("Test soda", garage))
        assertEquals(false, locations.deleteLocation(garage))
        assertEquals(garage, pantry.item(id)?.location)
        pantry.delete(id)
        assertEquals(true, locations.deleteLocation(garage))
        assertEquals(listOf(Location.Dry, Location.Fridge, Location.Freezer), locations.locations().first())
        assertNull(locations.location(LocationKey.of(garage)))
    }

    @Test
    fun `settings - a deleted default location reads as last used`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        settings.setDefaultLocation(DefaultLocation.Fixed(garage))
        locations.deleteLocation(garage)
        assertEquals(DefaultLocation.LastUsed, settings.settings().first().defaultLocation)
    }

    @Test
    fun `a new item - a deleted last used location falls back to Dry`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        settings.setLastUsedLocation(garage)
        locations.deleteLocation(garage)
        assertEquals(Location.Dry, settings.newItemLocation())
    }

    @Test
    fun `a new item - a deleted default location falls back to the last used one`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        settings.setLastUsedLocation(Location.Freezer)
        settings.setDefaultLocation(DefaultLocation.Fixed(garage))
        assertEquals(garage, settings.newItemLocation())
        locations.deleteLocation(garage)
        assertEquals(Location.Freezer, settings.newItemLocation())
    }

    @Test
    fun `a known product - forgets a deleted location, keeps its name`() = runBlocking {
        val garage = locations.addLocation("Garage", null)
        pantry.rememberProduct(KnownProduct("123", "Test soda", garage, QuantityUnit.Each))
        locations.deleteLocation(garage)
        assertNull(pantry.knownProduct("123")?.location)
        assertEquals("Test soda", pantry.knownProduct("123")?.name)
    }
}
