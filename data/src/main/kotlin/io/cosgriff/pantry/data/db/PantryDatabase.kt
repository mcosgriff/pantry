package io.cosgriff.pantry.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [LocationEntity::class, ItemEntity::class, KnownProductEntity::class, ShoppingEntryEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // known_products (#12)
        AutoMigration(from = 2, to = 3), // shopping_entries (#20)
    ],
)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun items(): ItemDao

    abstract fun locations(): LocationDao

    abstract fun knownProducts(): KnownProductDao

    abstract fun shopping(): ShoppingDao

    /** Puts the three built-in locations in a new database, in the order the app shows them. */
    object SeedBuiltIns : Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) {
            BUILT_INS.forEachIndexed { position, builtIn ->
                connection.execSQL(
                    "INSERT INTO locations (builtIn, name, shelfLife, position) VALUES ('$builtIn', NULL, NULL, $position)",
                )
            }
        }
    }

    companion object {
        const val FILE_NAME = "pantry.db"

        /** Built-in location keys, as stored in `locations.builtIn`. */
        val BUILT_INS = listOf("DRY", "FRIDGE", "FREEZER")

        fun open(context: Context): PantryDatabase =
            Room.databaseBuilder<PantryDatabase>(context, FILE_NAME)
                .addCallback(SeedBuiltIns)
                .build()
    }
}
