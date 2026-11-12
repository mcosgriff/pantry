package io.cosgriff.pantry.data.db

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.cosgriff.pantry.data.LocationRepository
import io.cosgriff.pantry.data.MealRepository
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.ShoppingRepository
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every schema the app has shipped upgrades to the current one with its rows intact.
 *
 * For each exported version in `data/schemas`, a database is made at that version with one location and one
 * item, then opened as the current database: Room runs each migration in between and checks the result against
 * the current schema. **The pantry lives only on the phone, so a migration that loses a row is the worst bug
 * this app can ship.** A new schema version is covered as soon as its file is in the folder; a version whose
 * tables change needs its seed below to follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // Gradle runs a module's tests from the module's folder.
    private val exported: List<Int> =
        File("schemas/${PantryDatabase::class.java.name}").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { it.nameWithoutExtension.toInt() }.sorted()

    @Test
    fun `the current schema is exported`() {
        assertTrue("no exported schemas found", exported.isNotEmpty())
        assertEquals(exported.indices.map { it + 1 }, exported)
    }

    @Test
    fun `every exported version opens as the current database with its rows`() = runBlocking {
        for (version in exported) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val helper = MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                context.getDatabasePath(name),
                AndroidSQLiteDriver(),
                PantryDatabase::class,
            )
            helper.createDatabase(version).use { c ->
                c.execSQL("INSERT INTO locations (id, builtIn, name, shelfLife, position) VALUES (1, 'FRIDGE', NULL, NULL, 0)")
                c.execSQL(
                    "INSERT INTO items (id, name, barcode, locationId, amount, unit, purchased, expires, opened, lowStockAt, notes) " +
                        "VALUES (1, 'Milk', '123', 1, '1.5', 'Each', 20000, 20007, 1, '1', 'x')",
                )
            }
            val db = Room.databaseBuilder<PantryDatabase>(context, name).build()
            try {
                val current = db.useReaderConnection { it.usePrepared("PRAGMA user_version") { s -> s.step(); s.getInt(0) } }
                assertEquals(exported.last(), current)
                val item = db.items().observeAll().first().single()
                assertEquals("Milk", item.name)
                assertEquals("1.5", item.amount)
                assertEquals(20007L, item.expires)
                assertEquals(1, db.locations().all().size)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    /**
     * Version 4 kept one flat unit name. Its `Each`, `Pound` and `Ounce` read as units, and `Bag`, `Pack` and `Tub`
     * as packages with no contents, in items, ingredients and known products alike (#56).
     */
    @Test
    fun `version 4 amounts read with every old unit`() = runBlocking {
        val name = "migration-units.db"
        context.deleteDatabase(name)
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            context.getDatabasePath(name),
            AndroidSQLiteDriver(),
            PantryDatabase::class,
        )
        val old = mapOf(
            "Each" to AmountUnit.Measured(MeasureUnit.Each),
            "Pound" to AmountUnit.Measured(MeasureUnit.Pound),
            "Ounce" to AmountUnit.Measured(MeasureUnit.Ounce),
            "Bag" to AmountUnit.Packages(Package.Bag),
            "Pack" to AmountUnit.Packages(Package.Pack),
            "Tub" to AmountUnit.Packages(Package.Tub),
        )
        helper.createDatabase(4).use { c ->
            c.execSQL("INSERT INTO locations (id, builtIn, name, shelfLife, position) VALUES (1, 'FRIDGE', NULL, NULL, 0)")
            c.execSQL("INSERT INTO meals (id, name, servings) VALUES (1, 'Test meal', 2)")
            old.keys.forEachIndexed { i, unit ->
                c.execSQL(
                    "INSERT INTO items (id, name, barcode, locationId, amount, unit, purchased, expires, opened, lowStockAt, notes) " +
                        "VALUES (${i + 1}, 'Test $unit', NULL, 1, '1.5', '$unit', 20000, NULL, 0, NULL, '')",
                )
                c.execSQL(
                    "INSERT INTO meal_ingredients (mealId, position, name, fromPantry, itemId, barcode, unit, amount) " +
                        "VALUES (1, $i, 'Test $unit', 1, ${i + 1}, NULL, '$unit', '2')",
                )
                c.execSQL("INSERT INTO known_products (barcode, name, locationId, unit) VALUES ('$i', 'Test $unit', 1, '$unit')")
            }
            c.execSQL(
                "INSERT INTO meal_ingredients (mealId, position, name, fromPantry, itemId, barcode, unit, amount) " +
                    "VALUES (1, 99, 'Test salt', 0, NULL, NULL, NULL, NULL)",
            )
        }
        val db = Room.databaseBuilder<PantryDatabase>(context, name).build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val pantry = PantryRepository(db.items(), LocationRepository(db.locations(), db.items()), db.knownProducts())
            val file = File(context.filesDir, "migration_units.preferences_pb").apply { delete() }
            val shopping = ShoppingRepository(db.shopping(), pantry, PreferenceDataStoreFactory.create(scope = scope) { file })
            val meals = MealRepository(db, pantry, shopping, Clock.systemUTC())

            val items = pantry.items().first().associateBy { it.name }
            val ingredients = meals.meals().first().single().ingredients.associateBy { it.name }
            old.entries.forEachIndexed { i, (stored, unit) ->
                assertEquals(stored, unit.of(BigDecimal("1.5")), items.getValue("Test $stored").quantity)
                assertNull(stored, items.getValue("Test $stored").density)
                assertEquals(stored, unit, ingredients.getValue("Test $stored").unit)
                assertEquals(stored, BigDecimal("2"), ingredients.getValue("Test $stored").amount)
                assertEquals(stored, unit, pantry.knownProduct("$i")?.unit)
            }
            assertEquals(Ingredient.typed("Test salt"), ingredients.getValue("Test salt"))
        } finally {
            scope.cancel()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
