package io.cosgriff.pantry.data.db

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    /** Soonest expiry first, undated last, then by name: the order of `PantryItem.BySoonestExpiry`. */
    @Query("SELECT * FROM items ORDER BY expires IS NULL, expires, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE barcode = :barcode ORDER BY id DESC LIMIT 1")
    suspend fun lastWithBarcode(barcode: String): ItemEntity?

    @Upsert
    suspend fun upsert(item: ItemEntity): Long

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM items WHERE locationId = :locationId")
    suspend fun countIn(locationId: Long): Int
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY position, id")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY position, id")
    suspend fun all(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE builtIn = :builtIn")
    suspend fun builtIn(builtIn: String): LocationEntity?

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM locations")
    suspend fun nextPosition(): Int
}

@Dao
interface KnownProductDao {
    @Query("SELECT * FROM known_products WHERE barcode = :barcode")
    suspend fun get(barcode: String): KnownProductEntity?

    @Upsert
    suspend fun upsert(product: KnownProductEntity)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_entries ORDER BY id")
    fun observeAll(): Flow<List<ShoppingEntryEntity>>

    @Insert
    suspend fun insert(entry: ShoppingEntryEntity): Long

    @Query("DELETE FROM shopping_entries WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
