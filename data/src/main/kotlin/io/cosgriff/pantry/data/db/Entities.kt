package io.cosgriff.pantry.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A storage location. The three built-in ones are rows too (seeded when the database is made), so every item
 * points at a row.
 *
 * @property builtIn `DRY`, `FRIDGE` or `FREEZER` for a built-in location, `null` for one the user added.
 * @property name the user's name for a location they added; `null` for a built-in one, which the app words.
 * @property shelfLife ISO-8601 period (`P7D`) of a location the user added; built-in ones take theirs from
 *   :domain.
 */
@Entity(tableName = "locations", indices = [Index(value = ["builtIn"], unique = true)])
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtIn: String?,
    val name: String?,
    val shelfLife: String?,
    val position: Int,
)

/**
 * An item. Amounts are stored as decimal text so a weight such as 1.24 lb stays exact; dates as epoch days.
 * A location with items in it can't be deleted (RESTRICT): the user moves them first.
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId"), Index("expires"), Index("barcode")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String?,
    val locationId: Long,
    val amount: String,
    val unit: String,
    val purchased: Long,
    val expires: Long?,
    val opened: Boolean,
    val lowStockAt: String?,
    val notes: String,
)
