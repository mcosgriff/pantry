package io.cosgriff.pantry.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.domain.UnitSystem
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
        // Before #27 one "notifications" switch stood for both alerts; a value stored then still reads as both.
        val legacy = prefs[NOTIFICATIONS]
        PantrySettings(
            notifyExpiringSoon = prefs[NOTIFY_EXPIRING_SOON] ?: legacy ?: defaults.notifyExpiringSoon,
            notifyExpired = prefs[NOTIFY_EXPIRED] ?: legacy ?: defaults.notifyExpired,
            warningDays = prefs[WARNING_DAYS]?.takeIf { it >= 0 } ?: defaults.warningDays,
            dailyDigestAt = prefs[DIGEST_MINUTE]?.let { runCatching { LocalTime.ofSecondOfDay(it * 60L) }.getOrNull() }
                ?: defaults.dailyDigestAt,
            batchScanByDefault = prefs[BATCH_SCAN] ?: defaults.batchScanByDefault,
            defaultLocation = prefs[DEFAULT_LOCATION]?.let { key -> locations.firstOrNull { it.key() == key } }
                ?.let(DefaultLocation::Fixed) ?: DefaultLocation.LastUsed,
            unitSystem = prefs[UNIT_SYSTEM]?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
                ?: defaults.unitSystem,
            productLookup = prefs[PRODUCT_LOOKUP]?.let { name -> LookupChoice.entries.firstOrNull { it.name == name } }
                ?: defaults.productLookup,
        )
    }

    /** The daily digest's count of items expiring within the warning window, on or off (#27). */
    suspend fun setNotifyExpiringSoon(on: Boolean) = store.edit { it[NOTIFY_EXPIRING_SOON] = on }

    /** The daily digest's count of items already expired, on or off (#27). */
    suspend fun setNotifyExpired(on: Boolean) = store.edit { it[NOTIFY_EXPIRED] = on }

    suspend fun setWarningDays(days: Int) {
        require(days >= 0) { "Warning window must not be negative: $days" }
        store.edit { it[WARNING_DAYS] = days }
    }

    suspend fun setDailyDigestAt(time: LocalTime) = store.edit { it[DIGEST_MINUTE] = time.toSecondOfDay() / 60 }

    suspend fun setBatchScanByDefault(on: Boolean) = store.edit { it[BATCH_SCAN] = on }

    /** Product lookup on or off (#9). Once answered, the scan sheet doesn't ask again. */
    suspend fun setProductLookup(on: Boolean) = store.edit { it[PRODUCT_LOOKUP] = (if (on) LookupChoice.On else LookupChoice.Off).name }

    suspend fun setDefaultLocation(location: DefaultLocation) = store.edit {
        when (location) {
            DefaultLocation.LastUsed -> it.remove(DEFAULT_LOCATION)
            is DefaultLocation.Fixed -> it[DEFAULT_LOCATION] = location.location.key()
        }
    }

    /**
     * Where a new item starts: the default location, or with "Last used" the location of the last item added
     * (Dry before any). A location deleted since falls back to Dry.
     */
    suspend fun newItemLocation(): Location {
        val prefs = store.data.first()
        val locations = pantry.locations().first()
        val key = prefs[DEFAULT_LOCATION] ?: prefs[LAST_USED_LOCATION]
        return locations.firstOrNull { it.key() == key } ?: Location.Dry
    }

    /** Remembers where the last new item went, for "Last used". */
    suspend fun setLastUsedLocation(location: Location) = store.edit { it[LAST_USED_LOCATION] = location.key() }

    /** Names given to store-packed items (#13), most recent first, for the scan sheet's quick picks. */
    fun recentStorePacked(): Flow<List<String>> =
        store.data.map { prefs -> prefs[RECENT_STORE_PACKED]?.split(NAME_SEPARATOR)?.filter(String::isNotBlank).orEmpty() }

    /** Puts [name] first among the recent store-packed names, keeping [RECENT_STORE_PACKED_MAX]. */
    suspend fun rememberStorePacked(name: String) {
        val trimmed = name.trim().replace(NAME_SEPARATOR, " ")
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val now = prefs[RECENT_STORE_PACKED]?.split(NAME_SEPARATOR)?.filter(String::isNotBlank).orEmpty()
            val next = (listOf(trimmed) + now.filterNot { it.equals(trimmed, ignoreCase = true) }).take(RECENT_STORE_PACKED_MAX)
            prefs[RECENT_STORE_PACKED] = next.joinToString(NAME_SEPARATOR)
        }
    }

    suspend fun setUnitSystem(system: UnitSystem) = store.edit { it[UNIT_SYSTEM] = system.name }

    private companion object {
        /** Read only: the single switch from before #27. */
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val NOTIFY_EXPIRING_SOON = booleanPreferencesKey("notify_expiring_soon")
        val NOTIFY_EXPIRED = booleanPreferencesKey("notify_expired")
        val WARNING_DAYS = intPreferencesKey("warning_days")
        val DIGEST_MINUTE = intPreferencesKey("daily_digest_minute")
        val BATCH_SCAN = booleanPreferencesKey("batch_scan_by_default")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val PRODUCT_LOOKUP = stringPreferencesKey("product_lookup")
        val LAST_USED_LOCATION = stringPreferencesKey("last_used_location")
        val RECENT_STORE_PACKED = stringPreferencesKey("recent_store_packed")
        const val RECENT_STORE_PACKED_MAX = 6
        const val NAME_SEPARATOR = "\n"

        /** Stable key for a location in settings: the built-in name, or `custom:<id>`. */
        fun Location.key(): String = builtInKey() ?: "custom:${(this as Location.Custom).id}"
    }
}
