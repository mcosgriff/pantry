package io.cosgriff.pantry.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
