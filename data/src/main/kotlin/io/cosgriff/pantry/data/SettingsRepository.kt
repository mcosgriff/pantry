package io.cosgriff.pantry.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.domain.UnitSystem
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The Pantry settings screen's values, in DataStore. A value never set reads as [PantrySettings]' default, and
 * a stored value the app no longer understands does too, rather than failing the screen.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val pantry: PantryRepository,
) {
    fun settings(): Flow<PantrySettings> = combine(store.data, pantry.locations()) { prefs, locations ->
        val defaults = PantrySettings()
        PantrySettings(
            notifications = prefs[NOTIFICATIONS] ?: defaults.notifications,
            warningDays = prefs[WARNING_DAYS]?.takeIf { it >= 0 } ?: defaults.warningDays,
            dailyDigestAt = prefs[DIGEST_MINUTE]?.let { runCatching { LocalTime.ofSecondOfDay(it * 60L) }.getOrNull() }
                ?: defaults.dailyDigestAt,
            batchScanByDefault = prefs[BATCH_SCAN] ?: defaults.batchScanByDefault,
            defaultLocation = prefs[DEFAULT_LOCATION]?.let { key -> locations.firstOrNull { it.key() == key } }
                ?.let(DefaultLocation::Fixed) ?: DefaultLocation.LastUsed,
            unitSystem = prefs[UNIT_SYSTEM]?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
                ?: defaults.unitSystem,
        )
    }

    suspend fun setNotifications(on: Boolean) = store.edit { it[NOTIFICATIONS] = on }

    suspend fun setWarningDays(days: Int) {
        require(days >= 0) { "Warning window must not be negative: $days" }
        store.edit { it[WARNING_DAYS] = days }
    }

    suspend fun setDailyDigestAt(time: LocalTime) = store.edit { it[DIGEST_MINUTE] = time.toSecondOfDay() / 60 }

    suspend fun setBatchScanByDefault(on: Boolean) = store.edit { it[BATCH_SCAN] = on }

    suspend fun setDefaultLocation(location: DefaultLocation) = store.edit {
        when (location) {
            DefaultLocation.LastUsed -> it.remove(DEFAULT_LOCATION)
            is DefaultLocation.Fixed -> it[DEFAULT_LOCATION] = location.location.key()
        }
    }

    suspend fun setUnitSystem(system: UnitSystem) = store.edit { it[UNIT_SYSTEM] = system.name }

    private companion object {
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val WARNING_DAYS = intPreferencesKey("warning_days")
        val DIGEST_MINUTE = intPreferencesKey("daily_digest_minute")
        val BATCH_SCAN = booleanPreferencesKey("batch_scan_by_default")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")

        /** Stable key for a location in settings: the built-in name, or `custom:<id>`. */
        fun Location.key(): String = builtInKey() ?: "custom:${(this as Location.Custom).id}"
    }
}
