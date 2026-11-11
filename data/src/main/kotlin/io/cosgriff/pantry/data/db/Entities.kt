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

/**
 * A barcode the user has named (#12). Its location goes back to `null` when that location is deleted, and the
 * scan then starts the item in the default one.
 */
@Entity(
    tableName = "known_products",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("locationId")],
)
data class KnownProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val locationId: Long?,
    val unit: String,
)

/**
 * A line on the shopping list that isn't worked out from the pantry (#20): used up, added by hand, or missing for
 * a planned meal (#19), which names the [meal] and the [mealDay] (epoch day) it's planned for.
 */
@Entity(tableName = "shopping_entries")
data class ShoppingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** `USED_UP`, `BY_HAND` or `FOR_MEAL`. */
    val reason: String,
    val addedOn: Long,
    val meal: String? = null,
    val mealDay: Long? = null,
)

/** A meal (#19): kept after it's cooked, so it can be planned again. */
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Int,
)

/**
 * One ingredient of a meal, in the meal's order ([position]). [itemId] is the pantry item it was picked from, with
 * no foreign key: the item may be eaten or thrown out, and the ingredient then matches by [barcode] or name.
 * [amount] is decimal text in [unit], `null` for "some".
 */
@Entity(
    tableName = "meal_ingredients",
    foreignKeys = [ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mealId")],
)
data class MealIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val position: Int,
    val name: String,
    val fromPantry: Boolean,
    val itemId: Long?,
    val barcode: String?,
    val unit: String?,
    val amount: String?,
)

/** A meal planned on a [day] (epoch day) for [servings]. Deleting the meal deletes its plans. */
@Entity(
    tableName = "meal_plans",
    foreignKeys = [ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mealId"), Index("day")],
)
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val day: Long,
    val servings: Int,
    val cooked: Boolean,
)
