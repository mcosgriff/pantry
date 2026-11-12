package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.Packaged
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.QuickAction
import io.cosgriff.pantry.domain.RemoveReason
import io.cosgriff.pantry.domain.ShoppingReason
import io.cosgriff.pantry.domain.number
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsumptionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context).addCallback(PantryDatabase.SeedBuiltIns).build()
    private val pantry = PantryRepository(db.items(), LocationRepository(db.locations(), db.items()), db.knownProducts())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "consumption_test.preferences_pb").apply { delete() }
    private val shopping = ShoppingRepository(db.shopping(), pantry, PreferenceDataStoreFactory.create(scope = scope) { file })
    private val meals = MealRepository(db.meals(), pantry, shopping)
    private val consumption = Consumption(db, pantry, shopping, meals)
    private val today = LocalDate.of(2026, 11, 10)

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun stock(
        name: String,
        amount: String = "1",
        unit: AmountUnit = AmountUnit.Measured(MeasureUnit.Each),
        location: Location = Location.Fridge,
        opened: Boolean = false,
        expires: LocalDate? = null,
    ): PantryItem {
        val id = pantry.save(PantryItem(name = name, location = location, quantity = unit.of(BigDecimal(amount)), purchased = today, opened = opened, expires = expires))
        return checkNotNull(pantry.item(id))
    }

    private suspend fun names() = pantry.items().first().map { it.name }

    private suspend fun list() = shopping.lines().first().map { it.name to it.reason }

    @Test
    fun `used up takes the item out and puts it on the list, and undo puts both back`() = runBlocking {
        val milk = stock("Test milk", opened = true)
        val done = consumption.takeQuickAction(milk, today)
        assertEquals(QuickAction.UsedUp, done.action)
        assertEquals(emptyList<String>(), names())
        assertEquals(listOf("Test milk" to ShoppingReason.UsedUp), list())

        consumption.undo(done)
        assertEquals(listOf(milk), pantry.items().first())
        assertEquals(emptyList<Pair<String, ShoppingReason>>(), list())
    }

    @Test
    fun `used up with the name already on the list adds nothing, and undo leaves that line alone`() = runBlocking {
        shopping.addByHand("test MILK", today)
        val milk = stock("Test milk", opened = true)
        val done = consumption.takeQuickAction(milk, today)
        assertEquals(listOf("test MILK" to ShoppingReason.ByHand), list())

        consumption.undo(done)
        assertEquals(listOf("Test milk"), names())
        assertEquals(listOf("test MILK" to ShoppingReason.ByHand), list())
    }

    @Test
    fun `thrown out takes the item out without a list line, and undo puts it back`() = runBlocking {
        val yogurt = stock("Test yogurt", expires = today.minusDays(1))
        val done = consumption.takeQuickAction(yogurt, today)
        assertEquals(QuickAction.ThrowOut, done.action)
        assertEquals(emptyList<String>(), names())
        assertEquals(emptyList<Pair<String, ShoppingReason>>(), list())

        consumption.undo(done)
        assertEquals(listOf(yogurt), pantry.items().first())
    }

    @Test
    fun `frozen moves the item to the Freezer, and undo moves it back`() = runBlocking {
        val chicken = stock("Test chicken", expires = today.plusDays(2))
        val done = consumption.takeQuickAction(chicken, today)
        assertEquals(QuickAction.Freeze, done.action)
        assertEquals(Location.Freezer, pantry.item(chicken.id)?.location)
        assertEquals(emptyList<Pair<String, ShoppingReason>>(), list())

        consumption.undo(done)
        assertEquals(chicken, pantry.item(chicken.id))
    }

    @Test
    fun `cooking takes amounts out, puts what it used up on the list, and marks the plan cooked`() = runBlocking {
        val eggs = stock("Test eggs", "18")
        val cheese = stock("Test mozzarella", "1", AmountUnit.Packages(Package.Bag))
        val id = meals.save(
            Meal(
                "Test egg bites",
                12,
                listOf(Ingredient.of(eggs).copy(amount = BigDecimal("12")), Ingredient.of(cheese).copy(amount = BigDecimal("1")), Ingredient.typed("Test salt")),
            ),
        )
        meals.plan(id, today, 12)
        consumption.cooked(meals.plans().first().single(), today)
        assertEquals(listOf("Test eggs" to "6"), pantry.items().first().map { it.name to it.quantity.number.toPlainString() })
        assertEquals(listOf("Test mozzarella" to ShoppingReason.UsedUp), list())
        assertTrue(meals.plans().first().single().cooked)
    }

    @Test
    fun `cooking subtracts across units in the item's own unit and stores it`() = runBlocking {
        val flour = stock("Test flour", "5", AmountUnit.Measured(MeasureUnit.Pound), location = Location.Dry)
        val rice = stock("Test rice", "2", AmountUnit.Packages(Package.Bag, Measure(BigDecimal("2"), MeasureUnit.Pound)), location = Location.Dry)
        val salt = stock("Test salt", "1", AmountUnit.Packages(Package.Tub), location = Location.Dry)
        val id = meals.save(
            Meal(
                "Test rice bake",
                4,
                listOf(
                    Ingredient.of(flour).copy(unit = AmountUnit.Measured(MeasureUnit.Cup), amount = BigDecimal("2")),
                    Ingredient.of(rice).copy(unit = AmountUnit.Measured(MeasureUnit.Pound), amount = BigDecimal("1")),
                    Ingredient.of(salt).copy(unit = AmountUnit.Measured(MeasureUnit.Tablespoon), amount = BigDecimal("1")),
                ),
            ),
        )
        meals.plan(id, today, 4)
        consumption.cooked(meals.plans().first().single(), today)

        assertEquals(Measure(BigDecimal("4.468"), MeasureUnit.Pound), pantry.item(flour.id)?.quantity)
        assertEquals(Packaged(BigDecimal("1.5"), Package.Bag, Measure(BigDecimal("2"), MeasureUnit.Pound)), pantry.item(rice.id)?.quantity)
        // "1 tbsp" can't come out of a tub of unknown size: left as it was, not used up.
        assertEquals(salt, pantry.item(salt.id))
        assertEquals(emptyList<Pair<String, ShoppingReason>>(), list())
        assertTrue(meals.plans().first().single().cooked)
    }

    @Test
    fun `cooking something already on the list doesn't add it twice`() = runBlocking {
        val cheese = stock("Test mozzarella", "1", AmountUnit.Packages(Package.Bag))
        shopping.addByHand("Test mozzarella", today)
        val id = meals.save(Meal("Test pizza", 2, listOf(Ingredient.of(cheese).copy(amount = BigDecimal("1")))))
        meals.plan(id, today, 2)
        consumption.cooked(meals.plans().first().single(), today)
        assertEquals(listOf("Test mozzarella" to ShoppingReason.ByHand), list())
    }

    @Test
    fun `cooking matches a restocked item by name when the planned one was deleted`() = runBlocking {
        val eggs = stock("Test eggs", "6")
        val id = meals.save(Meal("Test omelette", 1, listOf(Ingredient.of(eggs).copy(amount = BigDecimal("3")))))
        meals.plan(id, today, 1)
        pantry.delete(eggs.id)
        stock("Test eggs", "12")
        consumption.cooked(meals.plans().first().single(), today)
        assertEquals(listOf("9"), pantry.items().first().map { it.quantity.number.toPlainString() })
    }

    @Test
    fun `used up on an item that was running low puts it on the list as used up`() = runBlocking {
        val id = pantry.save(PantryItem(name = "Test butter", location = Location.Fridge, quantity = Measure(BigDecimal("1"), MeasureUnit.Each), purchased = today, opened = true, lowStockAt = BigDecimal("1")))
        assertEquals(listOf("Test butter" to ShoppingReason.RunningLow), list())
        consumption.takeQuickAction(checkNotNull(pantry.item(id)), today)
        assertEquals(listOf("Test butter" to ShoppingReason.UsedUp), list())
    }

    @Test
    fun `cooking that empties two items of one name puts it on the list once`() = runBlocking {
        val a = stock("Test milk")
        val b = stock("Test milk")
        val id = meals.save(Meal("Test custard", 1, listOf(Ingredient.of(a).copy(amount = BigDecimal("1")), Ingredient.of(b).copy(amount = BigDecimal("1")))))
        meals.plan(id, today, 1)
        consumption.cooked(meals.plans().first().single(), today)
        assertEquals(emptyList<String>(), names())
        assertEquals(listOf("Test milk" to ShoppingReason.UsedUp), list())
        assertEquals(1, db.shopping().observeAll().first().size)
    }

    @Test
    fun `removing as used up takes it out and puts it on the list`() = runBlocking {
        val eggs = stock("Test eggs", "18")
        consumption.remove(eggs, RemoveReason.UsedUp, today)
        assertEquals(emptyList<String>(), names())
        assertEquals(listOf("Test eggs" to ShoppingReason.UsedUp), list())
    }

    @Test
    fun `removing as used up with the name already on the list adds nothing`() = runBlocking {
        shopping.addByHand("test EGGS", today)
        val eggs = stock("Test eggs", "18")
        consumption.remove(eggs, RemoveReason.UsedUp, today)
        assertEquals(emptyList<String>(), names())
        assertEquals(listOf("test EGGS" to ShoppingReason.ByHand), list())
    }

    @Test
    fun `removing as thrown out or added by mistake only takes it out`() = runBlocking {
        val yogurt = stock("Test yogurt")
        val rice = stock("Test rice", location = Location.Dry)
        val kept = stock("Test butter")
        consumption.remove(yogurt, RemoveReason.ThrewOut, today)
        consumption.remove(rice, RemoveReason.AddedByMistake, today)
        assertEquals(listOf(kept), pantry.items().first())
        assertEquals(emptyList<Pair<String, ShoppingReason>>(), list())
    }
}
