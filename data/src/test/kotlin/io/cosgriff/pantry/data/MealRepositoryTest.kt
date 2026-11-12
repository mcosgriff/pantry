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
import io.cosgriff.pantry.domain.PantryItem
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
class MealRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<PantryDatabase>(context).addCallback(PantryDatabase.SeedBuiltIns).build()
    private val pantry = PantryRepository(db.items(), LocationRepository(db.locations(), db.items()), db.knownProducts())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "meal_test.preferences_pb").apply { delete() }
    private val shopping = ShoppingRepository(db.shopping(), pantry, PreferenceDataStoreFactory.create(scope = scope) { file })
    private val meals = MealRepository(db.meals(), pantry, shopping)
    private val today = LocalDate.of(2026, 11, 10)

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun stock(name: String, amount: String, unit: AmountUnit = AmountUnit.Measured(MeasureUnit.Each)): PantryItem {
        val id = pantry.save(PantryItem(name = name, location = Location.Fridge, quantity = unit.of(BigDecimal(amount)), purchased = today))
        return checkNotNull(pantry.item(id))
    }

    @Test
    fun `a meal saves with its ingredients in order, and saving again replaces them`() = runBlocking {
        val eggs = stock("Test eggs", "18")
        val meal = Meal("Test egg bites", 12, listOf(Ingredient.of(eggs).copy(amount = BigDecimal("12")), Ingredient.typed("Test salt")))
        val id = meals.save(meal)
        assertEquals(meal.copy(id = id), meals.meals().first().single())
        meals.save(meal.copy(id = id, name = "Test frittata", ingredients = listOf(Ingredient.typed("Test pepper"))))
        assertEquals(listOf("Test pepper"), meals.meals().first().single().ingredients.map { it.name })
    }

    @Test
    fun `ingredients round-trip every amount shape, and "some" keeps its unit`() = runBlocking {
        val units = MeasureUnit.entries.map { AmountUnit.Measured(it) } +
            Package.entries.map { AmountUnit.Packages(it) } +
            Package.entries.map { AmountUnit.Packages(it, Measure(BigDecimal("5"), MeasureUnit.Pound)) }
        val ingredients = units.flatMapIndexed { i, unit ->
            listOf(
                Ingredient("Test ingredient $i", fromPantry = true, itemId = i.toLong(), unit = unit, amount = BigDecimal("1.25")),
                Ingredient("Test ingredient $i, some", fromPantry = true, itemId = i.toLong(), unit = unit),
            )
        }
        val meal = Meal("Test everything", 4, ingredients)
        val id = meals.save(meal)
        assertEquals(meal.copy(id = id), meals.meals().first().single())
    }

    @Test
    fun `a plan carries its meal, day and servings, and unplanning keeps the meal`() = runBlocking {
        val id = meals.save(Meal("Test parfaits", 2, emptyList()))
        meals.plan(id, today, 4)
        val plan = meals.plans().first().single()
        assertEquals(today, plan.day)
        assertEquals(4, plan.servings)
        meals.setServings(plan, 6)
        assertEquals(6, meals.plans().first().single().servings)
        meals.unplan(plan)
        assertTrue(meals.plans().first().isEmpty())
        assertEquals(1, meals.meals().first().size)
    }

    @Test
    fun `missing ingredients go on the list for the meal, once`() = runBlocking {
        val eggs = stock("Test eggs", "4")
        val id = meals.save(
            Meal(
                "Test egg bites",
                12,
                listOf(
                    Ingredient.of(eggs).copy(amount = BigDecimal("12")),
                    Ingredient("Test cottage cheese", fromPantry = true, itemId = 99, unit = AmountUnit.Packages(Package.Tub)),
                    Ingredient.typed("Test salt"),
                ),
            ),
        )
        meals.plan(id, today.plusDays(1), 12)
        val plan = meals.plans().first().single()
        meals.addMissingToShopping(plan, today)
        meals.addMissingToShopping(plan, today)
        val lines = shopping.lines().first()
        assertEquals(listOf("Test cottage cheese", "Test eggs"), lines.map { it.name })
        assertTrue(lines.all { it.reason == ShoppingReason.ForMeal && it.entry?.meal == "Test egg bites" && it.entry?.mealDay == today.plusDays(1) })
    }
}
