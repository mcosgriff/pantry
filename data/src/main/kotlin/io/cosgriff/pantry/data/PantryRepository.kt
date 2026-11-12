package io.cosgriff.pantry.data

import io.cosgriff.pantry.data.db.ItemDao
import io.cosgriff.pantry.data.db.ItemEntity
import io.cosgriff.pantry.data.db.KnownProductDao
import io.cosgriff.pantry.data.db.KnownProductEntity
import io.cosgriff.pantry.domain.KnownProduct
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.countedIn
import io.cosgriff.pantry.domain.number
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The pantry as :domain sees it: items and remembered products, read as Flows for the screens. Locations are
 * [LocationRepository]'s (#51).
 */
@Singleton
class PantryRepository @Inject constructor(
    private val items: ItemDao,
    private val locations: LocationRepository,
    private val known: KnownProductDao,
) {
    /** Every item, soonest expiry first and undated last. */
    fun items(): Flow<List<PantryItem>> =
        combine(items.observeAll(), locations.byId()) { itemRows, byId ->
            itemRows.map { it.toDomain(byId.getValue(it.locationId)) }
        }

    suspend fun item(id: Long): PantryItem? = items.get(id)?.let { it.toDomain(locationOf(it)) }

    /** The most recently added item with [barcode], to fill in a scanned product the pantry has seen (#12). */
    suspend fun lastWithBarcode(barcode: String): PantryItem? = items.lastWithBarcode(barcode)?.let { it.toDomain(locationOf(it)) }

    /** The name the user gave [barcode] before (#12), or `null`. Its location is `null` once deleted (`DeletedLocation`). */
    suspend fun knownProduct(barcode: String): KnownProduct? {
        val row = known.get(barcode) ?: return null
        return KnownProduct(
            row.barcode,
            row.name,
            row.locationId?.let { locations.location(it) },
            amountUnit(row.unit, row.contentsAmount, row.contentsUnit),
        )
    }

    /** Remembers [product]'s name, location and unit (with any contents size) for its barcode, replacing what was remembered. */
    suspend fun rememberProduct(product: KnownProduct) {
        known.upsert(
            KnownProductEntity(
                barcode = product.barcode,
                name = product.name,
                locationId = product.location?.let { locations.idOf(it) },
                unit = product.unit.storedName,
                contentsAmount = product.unit.storedContentsAmount,
                contentsUnit = product.unit.storedContentsUnit,
            ),
        )
    }

    /** Saves [item]: a new one when its id is 0, otherwise the one with its id. Returns the id. */
    suspend fun save(item: PantryItem): Long {
        val id = items.upsert(item.toEntity(locations.idOf(item.location)))
        return if (item.id == 0L) id else item.id
    }

    suspend fun delete(id: Long) = items.delete(id)

    /** An item's location always exists: the foreign key refuses to delete a location that holds items. */
    private suspend fun locationOf(row: ItemEntity): Location =
        checkNotNull(locations.location(row.locationId)) { "Item ${row.id} is in missing location ${row.locationId}" }
}

internal fun ItemEntity.toDomain(location: Location) = PantryItem(
    id = id,
    name = name,
    barcode = barcode,
    location = location,
    quantity = amountUnit(unit, contentsAmount, contentsUnit).of(BigDecimal(amount)),
    purchased = LocalDate.ofEpochDay(purchased),
    expires = expires?.let(LocalDate::ofEpochDay),
    opened = opened,
    lowStockAt = lowStockAt?.let(::BigDecimal),
    notes = notes,
    density = density?.let(::density),
)

internal fun PantryItem.toEntity(locationId: Long) = ItemEntity(
    id = id,
    name = name,
    barcode = barcode,
    locationId = locationId,
    amount = quantity.number.toPlainString(),
    unit = quantity.countedIn.storedName,
    contentsAmount = quantity.countedIn.storedContentsAmount,
    contentsUnit = quantity.countedIn.storedContentsUnit,
    density = density?.stored(),
    purchased = purchased.toEpochDay(),
    expires = expires?.toEpochDay(),
    opened = opened,
    lowStockAt = lowStockAt?.toPlainString(),
    notes = notes,
)
