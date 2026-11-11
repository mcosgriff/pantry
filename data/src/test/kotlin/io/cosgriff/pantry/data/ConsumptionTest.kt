package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.QuickAction
import io.cosgriff.pantry.domain.ShoppingReason
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
        unit: QuantityUnit = QuantityUnit.Each,
        location: Location = Location.Fridge,
        opened: Boolean = false,
        expires: LocalDate? = null,
    ): PantryItem {
        val id = pantry.save(PantryItem(name = name, location = location, quantity = Quantity(BigDecimal(amount), unit), purchased = today, opened = opened, expires = expires))
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
        val cheese = stock("Test mozzarella", "1", QuantityUnit.Bag)
        val id = meals.save(
            Meal(
                "Test egg bites",
                12,
                listOf(Ingredient.of(eggs).copy(amount = BigDecimal("12")), Ingredient.of(cheese).copy(amount = BigDecimal("1")), Ingredient.typed("Test salt")),
            ),
        )
        meals.plan(id, today, 12)
        consumption.cooked(meals.plans().first().single(), today)
        assertEquals(listOf("Test eggs" to "6"), pantry.items().first().map { it.name to it.quantity.amount.toPlainString() })
        assertEquals(listOf("Test mozzarella" to ShoppingReason.UsedUp), list())
        assertTrue(meals.plans().first().single().cooked)
    }

    @Test
    fun `cooking something already on the list doesn't add it twice`() = runBlocking {
        val cheese = stock("Test mozzarella", "1", QuantityUnit.Bag)
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
        assertEquals(listOf("9"), pantry.items().first().map { it.quantity.amount.toPlainString() })
    }

    @Test
    fun `used up on an item that was running low puts it on the list as used up`() = runBlocking {
        val id = pantry.save(PantryItem(name = "Test butter", location = Location.Fridge, quantity = Quantity(BigDecimal("1"), QuantityUnit.Each), purchased = today, opened = true, lowStockAt = BigDecimal("1")))
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
}
