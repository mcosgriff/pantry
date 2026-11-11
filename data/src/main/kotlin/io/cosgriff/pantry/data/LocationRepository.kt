package io.cosgriff.pantry.data

import io.cosgriff.pantry.data.db.ItemDao
import io.cosgriff.pantry.data.db.LocationDao
import io.cosgriff.pantry.data.db.LocationEntity
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LocationKey
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The locations module (#51): the list of locations, adding, renaming and deleting one, and resolving a
 * [LocationKey] or a row id to a [Location]. Items, known products and settings ask it rather than reading the
 * `locations` table. What each of those does once a location is deleted is `DeletedLocation`'s, in :domain.
 */
@Singleton
class LocationRepository @Inject constructor(
    private val locations: LocationDao,
    private val items: ItemDao,
) {
    /** Every location, built-in ones first in their seeded order. */
    fun locations(): Flow<List<Location>> = locations.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Every location by its row id, for reading many items at once. */
    internal fun byId(): Flow<Map<Long, Location>> = locations.observeAll().map { rows -> rows.associate { it.id to it.toDomain() } }

    /** The location in row [id], or `null` if there is none (it was deleted). */
    internal suspend fun location(id: Long): Location? = locations.get(id)?.toDomain()

    /** The location with [key] as it is now, or `null` if there is none (it was deleted). */
    suspend fun location(key: LocationKey): Location? = when (key) {
        is LocationKey.BuiltIn -> locations.builtIn(key.text)?.toDomain()
        is LocationKey.Custom -> location(key.id)
    }

    /** [location]'s row id, for a row that refers to it. A location the user added must be saved first. */
    internal suspend fun idOf(location: Location): Long = when (val key = LocationKey.of(location)) {
        is LocationKey.Custom -> key.id
        is LocationKey.BuiltIn -> checkNotNull(locations.builtIn(key.text)) { "Built-in location missing: $location" }.id
    }

    /** Adds a location the user named, after the others. Returns it with its id. */
    suspend fun addLocation(name: String, shelfLife: Period?): Location.Custom {
        val draft = Location.Custom(name = name, shelfLife = shelfLife)
        val id = locations.insert(
            LocationEntity(builtIn = null, name = draft.name, shelfLife = shelfLife?.toString(), position = locations.nextPosition()),
        )
        return draft.copy(id = id)
    }

    /** Renames a location the user added, or changes its shelf life. */
    suspend fun updateLocation(location: Location.Custom) {
        val row = checkNotNull(locations.get(location.id)) { "No location ${location.id}" }
        locations.update(row.copy(name = location.name, shelfLife = location.shelfLife?.toString()))
    }

    /**
     * Deletes a location the user added, if it holds no items. Returns false, deleting nothing, if it does: the
     * user moves or removes them first. A deleted location is gone for good; see `DeletedLocation` for what
     * refers to it afterwards.
     */
    suspend fun deleteLocation(location: Location.Custom): Boolean {
        if (items.countIn(location.id) > 0) return false
        locations.get(location.id)?.let { locations.delete(it) }
        return true
    }
}

internal fun LocationEntity.toDomain(): Location = when (builtIn) {
    null -> Location.Custom(name = checkNotNull(name), shelfLife = shelfLife?.let(Period::parse), id = id)
    else -> (LocationKey.parse(builtIn) as? LocationKey.BuiltIn ?: error("Unknown built-in location: $builtIn")).location
}
