package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.ContributeChoice
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.MeasureSystem
import io.cosgriff.pantry.domain.PantrySettings
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
    private val locations = LocationRepository(db.locations(), db.items())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "settings_test.preferences_pb").apply { delete() }
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val settings = SettingsRepository(store, locations)

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
        settings.setNotifyExpiringSoon(true)
        settings.setNotifyExpired(true)
        settings.setWarningDays(5)
        settings.setDailyDigestAt(LocalTime.of(18, 30))
        settings.setBatchScanByDefault(true)
        settings.setDefaultLocation(DefaultLocation.Fixed(Location.Fridge))
        settings.setMeasurements(MeasureSystem.Metric)
        settings.setProductLookup(true)
        settings.setContributing(ContributeChoice.Always)
        assertEquals(
            PantrySettings(
                notifyExpiringSoon = true,
                notifyExpired = true,
                warningDays = 5,
                dailyDigestAt = LocalTime.of(18, 30),
                batchScanByDefault = true,
                defaultLocation = DefaultLocation.Fixed(Location.Fridge),
                measurements = MeasureSystem.Metric,
                productLookup = LookupChoice.On,
                contributing = ContributeChoice.Always,
            ),
            settings.settings().first(),
        )
        settings.setProductLookup(false)
        assertEquals(LookupChoice.Off, settings.settings().first().productLookup)
        settings.setNotifyExpired(false)
        assertEquals(true to false, settings.settings().first().let { it.notifyExpiringSoon to it.notifyExpired })
    }

    @Test
    fun `the single notifications switch from before the two alerts reads as both`() = runBlocking {
        store.edit { it[booleanPreferencesKey("notifications")] = true }
        assertEquals(true to true, settings.settings().first().let { it.notifyExpiringSoon to it.notifyExpired })
        settings.setNotifyExpired(false)
        assertEquals(true to false, settings.settings().first().let { it.notifyExpiringSoon to it.notifyExpired })
    }

    @Test
    fun `measurements are US customary until metric is chosen, and back (#57)`() = runBlocking {
        assertEquals(MeasureSystem.UsCustomary, settings.settings().first().measurements)
        settings.setMeasurements(MeasureSystem.Metric)
        assertEquals(MeasureSystem.Metric, settings.settings().first().measurements)
        settings.setMeasurements(MeasureSystem.UsCustomary)
        assertEquals(MeasureSystem.UsCustomary, settings.settings().first().measurements)
    }

    @Test
    fun `the unit system's name from before #57 still reads`() = runBlocking {
        store.edit { it[stringPreferencesKey("unit_system")] = "Metric" }
        assertEquals(MeasureSystem.Metric, settings.settings().first().measurements)
        store.edit { it[stringPreferencesKey("unit_system")] = "Us" }
        assertEquals(MeasureSystem.UsCustomary, settings.settings().first().measurements)
    }

    @Test
    fun `custom default location and back to last used`() = runBlocking {
        val garage = locations.addLocation("Garage fridge", null)
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
    fun `recent store-packed names, newest first, no repeats, at most six`() = runBlocking {
        assertEquals(emptyList<String>(), settings.recentStorePacked().first())
        listOf("Ground beef", "Salmon", "Chicken thighs", "ground beef").forEach { settings.rememberStorePacked(it) }
        assertEquals(listOf("ground beef", "Chicken thighs", "Salmon"), settings.recentStorePacked().first())
        (1..8).forEach { settings.rememberStorePacked("Test cut $it") }
        assertEquals(6, settings.recentStorePacked().first().size)
        assertEquals("Test cut 8", settings.recentStorePacked().first().first())
    }
}
