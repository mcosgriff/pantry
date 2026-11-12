package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealsTest {
    private val today = LocalDate.of(2026, 11, 10) // a Tuesday

    private fun item(id: Long, name: String, amount: String, unit: AmountUnit = AmountUnit.Measured(MeasureUnit.Each), expires: LocalDate? = null, barcode: String? = null) =
        PantryItem(id = id, name = name, location = Location.Fridge, quantity = unit.of(BigDecimal(amount)), purchased = today, expires = expires, barcode = barcode)

    private val eggs = item(1, "Test eggs", "18")
    private val spinach = item(2, "Test spinach", "1", AmountUnit.Packages(Package.Bag), expires = today.minusDays(1))
    private val cheese = item(3, "Test mozzarella", "1", AmountUnit.Packages(Package.Bag))

    private fun meal(vararg ingredients: Ingredient, servings: Int = 12) = Meal("Test egg bites", servings, ingredients.toList(), id = 7)

    @Test
    fun `an ingredient picked from the pantry keeps its item's id, barcode and unit`() {
        val picked = Ingredient.of(eggs.copy(barcode = "096619321087"))
        assertEquals(Ingredient("Test eggs", fromPantry = true, itemId = 1, barcode = "096619321087", unit = AmountUnit.Measured(MeasureUnit.Each)), picked)
        assertEquals(Ingredient("Test salt"), Ingredient.typed("  Test salt "))
    }

    @Test
    fun `amounts scale with the plan's servings`() {
        val plan = MealPlan(meal(Ingredient.of(eggs).copy(amount = BigDecimal("12"))), today, servings = 6)
        assertEquals(BigDecimal("6"), plan.needed(plan.meal.ingredients.single()))
        val third = MealPlan(meal(Ingredient.of(cheese).copy(amount = BigDecimal.ONE), servings = 3), today, servings = 1)
        assertEquals(BigDecimal("0.333"), third.needed(third.meal.ingredients.single()))
        assertNull(plan.needed(Ingredient.of(cheese)))
    }

    @Test
    fun `things counted whole round up when scaled, others keep their fraction`() {
        val plan = MealPlan(meal(Ingredient.of(eggs).copy(amount = BigDecimal("8")), Ingredient.of(cheese).copy(amount = BigDecimal("1")), servings = 3), today, servings = 2)
        assertEquals(BigDecimal("6"), plan.needed(plan.meal.ingredients[0]))
        assertEquals(BigDecimal("0.667"), plan.needed(plan.meal.ingredients[1]))
    }

    @Test
    fun `statuses follow the board - have, expired, missing, not tracked, short`() {
        val plan = MealPlan(
            meal(
                Ingredient.of(eggs).copy(amount = BigDecimal("12")),
                Ingredient.of(spinach),
                Ingredient("Test cottage cheese", fromPantry = true, itemId = 99, unit = AmountUnit.Packages(Package.Tub)),
                Ingredient.typed("Test salt"),
                Ingredient.of(cheese).copy(amount = BigDecimal("2")),
            ),
            today,
        )
        val statuses = checkPlan(plan, listOf(eggs, spinach, cheese), today).map { it.status }
        assertEquals(
            listOf(
                IngredientStatus.Have(eggs),
                IngredientStatus.Expired(spinach, 1),
                IngredientStatus.Missing,
                IngredientStatus.NotTracked,
                IngredientStatus.Short(cheese),
            ),
            statuses,
        )
    }

    @Test
    fun `an item that's gone is matched again by barcode, then by name, freshest unexpired first`() {
        val picked = Ingredient("Test eggs", fromPantry = true, itemId = 1, barcode = "096619321087", unit = AmountUnit.Measured(MeasureUnit.Each))
        val old = item(10, "Test eggs", "2", expires = today.minusDays(2))
        val fresh = item(11, "TEST EGGS", "12", expires = today.plusDays(9))
        val sooner = item(12, "test eggs", "6", expires = today.plusDays(4))
        assertEquals(sooner, pantryMatch(picked, listOf(old, fresh, sooner), today))
        val scanned = item(13, "Eggs, large", "12", barcode = "096619321087")
        assertEquals(scanned, pantryMatch(picked, listOf(fresh, scanned), today))
        assertNull(pantryMatch(Ingredient.typed("Test eggs"), listOf(fresh), today))
    }

    @Test
    fun `an item with nothing left counts as missing`() {
        val plan = MealPlan(meal(Ingredient.of(eggs)), today)
        assertEquals(IngredientStatus.Missing, checkPlan(plan, listOf(eggs.copy(quantity = Measure(BigDecimal.ZERO, MeasureUnit.Each))), today).single().status)
    }

    @Test
    fun `missing and short ingredients are to buy`() {
        val plan = MealPlan(meal(Ingredient.of(eggs).copy(amount = BigDecimal("24")), Ingredient.of(spinach), Ingredient.typed("Test salt"), Ingredient("Test scallions", true, 50, null, AmountUnit.Measured(MeasureUnit.Each))), today)
        assertEquals(listOf(true, false, false, true), checkPlan(plan, listOf(eggs, spinach), today).map { it.toBuy })
    }

    @Test
    fun `cooking takes amounts out, adds up two uses of one item, and uses up what reaches zero`() {
        val plan = MealPlan(
            meal(
                Ingredient.of(eggs).copy(amount = BigDecimal("12")),
                Ingredient.of(eggs).copy(name = "Test egg whites", amount = BigDecimal("2")),
                Ingredient.of(cheese).copy(amount = BigDecimal("1")),
                Ingredient.of(spinach),
                Ingredient.typed("Test salt"),
            ),
            today,
        )
        val cooked = cook(checkPlan(plan, listOf(eggs, spinach, cheese), today))
        assertEquals(listOf(eggs.copy(quantity = Measure(BigDecimal("4"), MeasureUnit.Each))), cooked.updated)
        assertEquals(listOf(cheese), cooked.usedUp)
    }

    @Test
    fun `cooking skips an amount in another unit than the item now has`() {
        val plan = MealPlan(meal(Ingredient("Test eggs", true, 1, null, AmountUnit.Packages(Package.Pack), BigDecimal("1"))), today)
        val cooked = cook(checkPlan(plan, listOf(eggs), today))
        assertTrue(cooked.updated.isEmpty() && cooked.usedUp.isEmpty())
    }

    private fun uses(item: PantryItem, amount: String, unit: AmountUnit) =
        MealPlan(meal(Ingredient.of(item).copy(unit = unit, amount = BigDecimal(amount)), servings = 1), today)

    @Test
    fun `2 cup flour against 5 lb flour is enough, and cooking leaves about 4_47 lb`() {
        val flour = item(20, "Test flour", "5", AmountUnit.Measured(MeasureUnit.Pound))
        val checks = checkPlan(uses(flour, "2", AmountUnit.Measured(MeasureUnit.Cup)), listOf(flour), today)
        assertEquals(IngredientStatus.Have(flour), checks.single().status)
        val cooked = cook(checks)
        val left = cooked.updated.single().quantity as Measure
        assertEquals(MeasureUnit.Pound, left.unit)
        assertEquals(BigDecimal("4.47"), left.amount.setScale(2, RoundingMode.HALF_UP))
        assertEquals(setOf(20L), cooked.approximate)
    }

    @Test
    fun `1 lb rice against 2 bags of 2 lb leaves 1_5 bags`() {
        val twoPounds = Measure(BigDecimal("2"), MeasureUnit.Pound)
        val rice = item(21, "Test rice", "2", AmountUnit.Packages(Package.Bag, twoPounds))
        val checks = checkPlan(uses(rice, "1", AmountUnit.Measured(MeasureUnit.Pound)), listOf(rice), today)
        assertEquals(IngredientStatus.Have(rice), checks.single().status)
        val cooked = cook(checks)
        assertEquals(listOf(rice.copy(quantity = Packaged(BigDecimal("1.5"), Package.Bag, twoPounds))), cooked.updated)
        assertTrue(cooked.approximate.isEmpty())
    }

    @Test
    fun `1 tbsp salt against 1 tub with no contents is can't compare, not to buy, and left alone by cooking`() {
        val salt = item(22, "Test salt", "1", AmountUnit.Packages(Package.Tub))
        val checks = checkPlan(uses(salt, "1", AmountUnit.Measured(MeasureUnit.Tablespoon)), listOf(salt), today)
        assertEquals(IngredientStatus.CantCompare(salt), checks.single().status)
        assertFalse(checks.single().toBuy)
        val cooked = cook(checks)
        assertTrue(cooked.updated.isEmpty() && cooked.usedUp.isEmpty())
    }

    @Test
    fun `an amount in another unit is short when it converts to more than the item has`() {
        val milk = item(23, "Test milk", "1", AmountUnit.Measured(MeasureUnit.Cup))
        val check = checkPlan(uses(milk, "500", AmountUnit.Measured(MeasureUnit.Milliliter)), listOf(milk), today).single()
        assertEquals(IngredientStatus.Short(milk), check.status)
        assertTrue(check.toBuy)
    }

    @Test
    fun `an item's own density wins over the table`() {
        // 2 cup at 2 g/ml is about 2.09 lb, more than the 1 lb there; at the table's 0.51 it would be enough.
        val flour = item(24, "Test flour", "1", AmountUnit.Measured(MeasureUnit.Pound)).copy(density = Density(BigDecimal("2")))
        val check = checkPlan(uses(flour, "2", AmountUnit.Measured(MeasureUnit.Cup)), listOf(flour), today).single()
        assertEquals(IngredientStatus.Short(flour), check.status)
    }

    @Test
    fun `the week starts on the locale's first day`() {
        assertEquals(LocalDate.of(2026, 11, 8), weekOf(today, DayOfWeek.SUNDAY).first())
        assertEquals(LocalDate.of(2026, 11, 9), weekOf(today, DayOfWeek.MONDAY).first())
        assertEquals(LocalDate.of(2026, 11, 15), weekOf(today, DayOfWeek.MONDAY).last())
    }

    @Test
    fun `a week's plans by day, then in planning order`() {
        val m = meal()
        val plans = listOf(
            MealPlan(m, today.plusDays(2), id = 1),
            MealPlan(m, today, id = 3),
            MealPlan(m, today, id = 2),
            MealPlan(m, today.plusDays(9), id = 4),
        )
        assertEquals(listOf(2L, 3L, 1L), plansIn(plans, weekOf(today, DayOfWeek.SUNDAY)).map { it.id })
    }
}
