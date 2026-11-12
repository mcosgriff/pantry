package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.IngredientStatus
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.MealDraft
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.ShoppingReason
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    /** The one clock the module reads (#52): today is 2026-11-10. */
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val meals = MealRepository(db, pantry, shopping, clock)
    private val today = LocalDate.now(clock)

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
        meals.stepServings(plan, 2)
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
        assertEquals(2, meals.addMissingToShopping(plan))
        assertEquals(0, meals.addMissingToShopping(plan))
        val lines = shopping.lines().first()
        assertEquals(listOf("Test cottage cheese", "Test eggs"), lines.map { it.name })
        assertTrue(lines.all { it.reason == ShoppingReason.ForMeal && it.entry?.meal == "Test egg bites" && it.entry?.mealDay == today.plusDays(1) })
        assertTrue(lines.all { it.entry?.addedOn == today })
    }

    @Test
    fun `"N added" counts only what wasn't on the list already`() = runBlocking {
        shopping.addByHand("test eggs", today)
        val id = meals.save(
            Meal(
                "Test omelette",
                1,
                listOf(
                    Ingredient("Test eggs", fromPantry = true, itemId = 98, unit = AmountUnit.Measured(MeasureUnit.Each), amount = BigDecimal("3")),
                    Ingredient("Test chives", fromPantry = true, itemId = 99, unit = AmountUnit.Measured(MeasureUnit.Each)),
                ),
            ),
        )
        meals.plan(id, today, 1)
        assertEquals(1, meals.addMissingToShopping(meals.plans().first().single()))
    }

    @Test
    fun `a new meal draft is saved and planned on the day for its servings, trimmed`() = runBlocking {
        val id = meals.save(MealDraft("  Test chili ", 4, listOf(Ingredient.typed("Test beans"))), today.plusDays(2))
        val plan = meals.plans().first().single()
        assertEquals(id, plan.meal.id)
        assertEquals("Test chili", plan.meal.name)
        assertEquals(today.plusDays(2), plan.day)
        assertEquals(4, plan.servings)
    }

    @Test
    fun `an edited meal draft is saved without planning it again`() = runBlocking {
        val id = checkNotNull(meals.save(MealDraft("Test chili", 4), today))
        val edited = MealDraft.of(meals.meals().first().single()).copy(name = "Test white chili")
        assertEquals(id, meals.save(edited, today.plusDays(1)))
        assertEquals(listOf("Test white chili"), meals.meals().first().map { it.name })
        assertEquals(listOf(today), meals.plans().first().map { it.day })
    }

    @Test
    fun `a draft with no name saves nothing`() = runBlocking {
        assertNull(meals.save(MealDraft("  "), today))
        assertTrue(meals.meals().first().isEmpty())
        assertTrue(meals.plans().first().isEmpty())
    }

    @Test
    fun `an amount set for the plan reads back the same, and the meal keeps it for its own servings`() = runBlocking {
        val eggs = stock("Test eggs", "18")
        meals.save(MealDraft("Test egg bites", 2, listOf(Ingredient.of(eggs))), today)
        val plan = meals.plans().first().single()
        meals.stepServings(plan, 1)
        meals.setAmount(meals.plans().first().single(), 0, BigDecimal("5"))
        val checked = meals.checkedPlans().first().single()
        assertEquals(3, checked.plan.servings)
        assertEquals(BigDecimal("5"), checked.checks.single().needed)
        assertEquals(IngredientStatus.Have(eggs), checked.checks.single().status)
        assertEquals(0, BigDecimal("3.333333").compareTo(meals.meals().first().single().ingredients.single().amount))
    }

    @Test
    fun `servings step within the bounds`() = runBlocking {
        meals.save(MealDraft("Test toast", 1), today)
        meals.stepServings(meals.plans().first().single(), -1)
        assertEquals(1, meals.plans().first().single().servings)
        meals.plan(meals.meals().first().single().id, today, 240)
        assertEquals(listOf(1, 99), meals.plans().first().map { it.servings })
    }
}
