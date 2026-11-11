package io.cosgriff.pantry.data

import io.cosgriff.pantry.data.db.ItemDao
import io.cosgriff.pantry.data.db.ItemEntity
import io.cosgriff.pantry.data.db.LocationDao
import io.cosgriff.pantry.data.db.LocationEntity
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The pantry as :domain sees it: items and locations, read as Flows for the screens. */
@Singleton
class PantryRepository @Inject constructor(
    private val items: ItemDao,
    private val locations: LocationDao,
) {
    /** Every location, built-in ones first in their seeded order. */
    fun locations(): Flow<List<Location>> = locations.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Every item, soonest expiry first and undated last. */
    fun items(): Flow<List<PantryItem>> =
        combine(items.observeAll(), locations.observeAll()) { itemRows, locationRows ->
            val byId = locationRows.associate { it.id to it.toDomain() }
            itemRows.map { it.toDomain(byId.getValue(it.locationId)) }
        }

    suspend fun item(id: Long): PantryItem? {
        val row = items.get(id) ?: return null
        return row.toDomain(locationById(row.locationId))
    }

    /** The most recently added item with [barcode], to fill in a scanned product the pantry has seen (#12). */
    suspend fun lastWithBarcode(barcode: String): PantryItem? {
        val row = items.lastWithBarcode(barcode) ?: return null
        return row.toDomain(locationById(row.locationId))
    }

    /** Saves [item]: a new one when its id is 0, otherwise the one with its id. Returns the id. */
    suspend fun save(item: PantryItem): Long {
        val id = items.upsert(item.toEntity(locationId(item.location)))
        return if (item.id == 0L) id else item.id
    }

    suspend fun delete(id: Long) = items.delete(id)

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
        val row = locations.all().first { it.id == location.id }
        locations.update(row.copy(name = location.name, shelfLife = location.shelfLife?.toString()))
    }

    /**
     * Deletes a location the user added, if it holds no items. Returns false, deleting nothing, if it does: the
     * user moves or removes them first.
     */
    suspend fun deleteLocation(location: Location.Custom): Boolean {
        if (items.countIn(location.id) > 0) return false
        locations.delete(locations.all().first { it.id == location.id })
        return true
    }

    private suspend fun locationById(id: Long): Location =
        locations.all().first { it.id == id }.toDomain()

    private suspend fun locationId(location: Location): Long = when (location) {
        is Location.Custom -> location.id.also { require(it != 0L) { "Save a new location before an item in it" } }
        else -> checkNotNull(locations.builtIn(checkNotNull(location.builtInKey()))) { "Built-in location missing: $location" }.id
    }
}

/** `locations.builtIn` of a built-in location; `null` for one the user added. */
internal fun Location.builtInKey(): String? = when (this) {
    Location.Dry -> "DRY"
    Location.Fridge -> "FRIDGE"
    Location.Freezer -> "FREEZER"
    is Location.Custom -> null
}

internal fun LocationEntity.toDomain(): Location = when (builtIn) {
    "DRY" -> Location.Dry
    "FRIDGE" -> Location.Fridge
    "FREEZER" -> Location.Freezer
    null -> Location.Custom(name = checkNotNull(name), shelfLife = shelfLife?.let(Period::parse), id = id)
    else -> error("Unknown built-in location: $builtIn")
}

internal fun ItemEntity.toDomain(location: Location) = PantryItem(
    id = id,
    name = name,
    barcode = barcode,
    location = location,
    quantity = Quantity(BigDecimal(amount), QuantityUnit.valueOf(unit)),
    purchased = LocalDate.ofEpochDay(purchased),
    expires = expires?.let(LocalDate::ofEpochDay),
    opened = opened,
    lowStockAt = lowStockAt?.let(::BigDecimal),
    notes = notes,
)

internal fun PantryItem.toEntity(locationId: Long) = ItemEntity(
    id = id,
    name = name,
    barcode = barcode,
    locationId = locationId,
    amount = quantity.amount.toPlainString(),
    unit = quantity.unit.name,
    purchased = purchased.toEpochDay(),
    expires = expires?.toEpochDay(),
    opened = opened,
    lowStockAt = lowStockAt?.toPlainString(),
    notes = notes,
)
