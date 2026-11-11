package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.domain.UnitSystem
import java.io.File
import java.time.LocalTime
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
class SettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context)
        .addCallback(PantryDatabase.SeedBuiltIns)
        .build()
    private val pantry = PantryRepository(db.items(), db.locations())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "settings_test.preferences_pb").apply { delete() }
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val settings = SettingsRepository(store, pantry)

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `nothing stored reads as the defaults`() = runBlocking {
        assertEquals(PantrySettings(), settings.settings().first())
    }

    @Test
    fun `every value round-trips`() = runBlocking {
        settings.setNotifications(true)
        settings.setWarningDays(5)
        settings.setDailyDigestAt(LocalTime.of(18, 30))
        settings.setBatchScanByDefault(true)
        settings.setDefaultLocation(DefaultLocation.Fixed(Location.Fridge))
        settings.setUnitSystem(UnitSystem.Metric)
        assertEquals(
            PantrySettings(
                notifications = true,
                warningDays = 5,
                dailyDigestAt = LocalTime.of(18, 30),
                batchScanByDefault = true,
                defaultLocation = DefaultLocation.Fixed(Location.Fridge),
                unitSystem = UnitSystem.Metric,
            ),
            settings.settings().first(),
        )
    }

    @Test
    fun `custom default location and back to last used`() = runBlocking {
        val garage = pantry.addLocation("Garage fridge", null)
        settings.setDefaultLocation(DefaultLocation.Fixed(garage))
        assertEquals(DefaultLocation.Fixed(garage), settings.settings().first().defaultLocation)
        settings.setDefaultLocation(DefaultLocation.LastUsed)
        assertEquals(DefaultLocation.LastUsed, settings.settings().first().defaultLocation)
    }

    @Test
    fun `values the app no longer understands read as defaults`() = runBlocking {
        store.edit {
            it[intPreferencesKey("warning_days")] = -2
            it[stringPreferencesKey("unit_system")] = "Imperial"
            it[stringPreferencesKey("default_location")] = "custom:404"
        }
        assertEquals(PantrySettings(), settings.settings().first())
    }

    @Test
    fun `a new item starts in the last used location, or the default one when set`() = runBlocking {
        assertEquals(Location.Dry, settings.newItemLocation())
        settings.setLastUsedLocation(Location.Freezer)
        assertEquals(Location.Freezer, settings.newItemLocation())
        settings.setDefaultLocation(DefaultLocation.Fixed(Location.Fridge))
        assertEquals(Location.Fridge, settings.newItemLocation())
    }

    @Test
    fun `a deleted last used location falls back to Dry`() = runBlocking {
        val garage = pantry.addLocation("Garage", null)
        settings.setLastUsedLocation(garage)
        pantry.deleteLocation(garage)
        assertEquals(Location.Dry, settings.newItemLocation())
    }
}
