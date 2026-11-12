package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanEditingTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(id: Long, name: String, amount: String, unit: AmountUnit) =
        PantryItem(id = id, name = name, location = Location.Fridge, quantity = unit.of(BigDecimal(amount)), purchased = today)

    private val each = AmountUnit.Measured(MeasureUnit.Each)
    private val dozen = AmountUnit.Measured(MeasureUnit.Dozen)
    private val cup = AmountUnit.Measured(MeasureUnit.Cup)
    private val pack = AmountUnit.Packages(Package.Pack)
    private val fiveLbBag = AmountUnit.Packages(Package.Bag, Measure(BigDecimal("5"), MeasureUnit.Pound))

    private fun ingredient(name: String, unit: AmountUnit, amount: String? = null, itemId: Long = 1) =
        Ingredient(name, fromPantry = true, itemId = itemId, unit = unit, amount = amount?.let(::BigDecimal))

    /** Types [typed] for ingredient 0 of a [mealServings]-serving meal planned for [planServings]; what the plan reads back. */
    private fun roundTrip(typed: String, unit: AmountUnit, mealServings: Int, planServings: Int): BigDecimal? {
        val plan = MealPlan(Meal("Test meal", mealServings, listOf(ingredient("Test thing", unit))), today, planServings)
        val meal = plan.withAmount(0, BigDecimal(typed))
        val replanned = plan.copy(meal = meal)
        return replanned.needed(meal.ingredients.single())
    }

    @Test
    fun `servings stay within one bound`() {
        assertEquals(1, Servings.stepped(1, -1))
        assertEquals(99, Servings.stepped(99, 1))
        assertEquals(5, Servings.stepped(4, 1))
        assertEquals(99, Servings.bounded(240))
        assertEquals(1, Servings.bounded(0))
    }

    @Test
    fun `5 eggs typed on a 3-serving plan of a 2-serving meal read back as 5`() {
        assertEquals(BigDecimal("5"), roundTrip("5", each, mealServings = 2, planServings = 3))
        // 4 for 3 is 2.666667 for 2: just over 8/3, which must not round 4 up to 5.
        assertEquals(BigDecimal("4"), roundTrip("4", each, mealServings = 2, planServings = 3))
    }

    @Test
    fun `an amount typed at the plan's servings reads back unchanged, in every shape`() {
        val cases = listOf(
            each to listOf("1", "5", "12", "17"),
            pack to listOf("1", "2", "3"),
            dozen to listOf("0.5", "1", "1.5"),
            cup to listOf("0.25", "1", "1.333", "2.5"),
            fiveLbBag to listOf("0.2", "1", "1.5"),
        )
        val servings = listOf(1 to 1, 2 to 3, 3 to 2, 4 to 7, 7 to 4, 1 to 99, 99 to 1, 12 to 5)
        cases.forEach { (unit, amounts) ->
            amounts.forEach { typed ->
                servings.forEach { (meal, plan) ->
                    val back = roundTrip(typed, unit, meal, plan)
                    assertEquals("$typed $unit, meal $meal, plan $plan", 0, BigDecimal(typed).compareTo(back))
                }
            }
        }
    }

    @Test
    fun `a fraction of something counted whole reads back rounded up, the rounding stated`() {
        assertEquals(BigDecimal("3"), roundTrip("2.5", each, mealServings = 2, planServings = 3))
        assertEquals(BigDecimal("2"), roundTrip("1.2", pack, mealServings = 4, planServings = 4))
    }

    @Test
    fun `the meal keeps the amount for its own servings`() {
        val plan = MealPlan(Meal("Test meal", 2, listOf(ingredient("Test eggs", each))), today, servings = 4)
        assertEquals(BigDecimal("3"), plan.withAmount(0, BigDecimal("6")).ingredients.single().amount)
        assertEquals("10", roundTrip("10", cup, mealServings = 2, planServings = 2)?.toPlainString())
        assertEquals("100", roundTrip("100", cup, mealServings = 1, planServings = 3)?.toString())
    }

    @Test
    fun `a cross-unit amount round-trips and is checked against the item's unit`() {
        val flour = item(20, "Test flour", "5", AmountUnit.Measured(MeasureUnit.Pound))
        val plan = MealPlan(Meal("Test bread", 2, listOf(Ingredient.of(flour).copy(unit = cup))), today, servings = 3)
        val meal = plan.withAmount(0, BigDecimal("2"))
        val checks = checkPlan(plan.copy(meal = meal), listOf(flour), today)
        assertEquals(BigDecimal("2"), checks.single().needed)
        assertEquals(IngredientStatus.Have(flour), checks.single().status)
    }

    @Test
    fun `an amount typed in another unit round-trips in that unit, with its rounding (#57)`() {
        val eggs = item(21, "Test eggs", "18", each)
        val plan = MealPlan(Meal("Test omelettes", 2, listOf(Ingredient.of(eggs))), today, servings = 3)
        // 1.5 dozen typed for 3: kept on the meal as 1 dozen for 2, and read back as typed, in dozens.
        val meal = plan.withAmount(0, BigDecimal("1.5"), dozen)
        assertEquals(dozen, meal.ingredients.single().unit)
        assertEquals(0, BigDecimal.ONE.compareTo(meal.ingredients.single().amount))
        val check = checkPlan(plan.copy(meal = meal), listOf(eggs), today).single()
        assertEquals(0, BigDecimal("1.5").compareTo(check.needed))
        assertEquals(IngredientStatus.Have(eggs), check.status)
        // Changed to something counted whole, a fraction rounds up as for any whole count.
        val inPacks = plan.withAmount(0, BigDecimal("2.5"), pack)
        assertEquals(BigDecimal("3"), plan.copy(meal = inPacks).needed(inPacks.ingredients.single()))
        // Changed from whole each to a cup, a fraction stays a fraction.
        val inCups = plan.withAmount(0, BigDecimal("2.5"), cup)
        assertEquals(0, BigDecimal("2.5").compareTo(plan.copy(meal = inCups).needed(inCups.ingredients.single())))
    }

    @Test
    fun `clearing an amount keeps the unit, and a typed-in ingredient takes an amount with a unit`() {
        val plan = MealPlan(Meal("Test meal", 2, listOf(ingredient("Test flour", cup, "2"), Ingredient.typed("Test salt"))), today)
        assertEquals(cup, plan.withAmount(0, null, AmountUnit.Measured(MeasureUnit.Gram)).ingredients[0].unit)
        val salt = plan.withAmount(1, BigDecimal.ONE, AmountUnit.Measured(MeasureUnit.Teaspoon)).ingredients[1]
        assertEquals(AmountUnit.Measured(MeasureUnit.Teaspoon), salt.unit)
        assertEquals(0, BigDecimal.ONE.compareTo(salt.amount))
    }

    @Test
    fun `a draft's amount can be in any unit, at the meal's own servings`() {
        val eggs = item(22, "Test eggs", "18", each)
        val draft = MealDraft("Test bites", 4, listOf(Ingredient.of(eggs))).withAmount(0, BigDecimal("0.5"), dozen)
        assertEquals(dozen, draft.ingredients.single().unit)
        assertEquals(0, BigDecimal("0.5").compareTo(draft.ingredients.single().amount))
        val cleared = draft.withAmount(0, null)
        assertNull(cleared.ingredients.single().amount)
        assertEquals(dozen, cleared.ingredients.single().unit)
        assertSame(draft, draft.withAmount(3, BigDecimal.ONE))
    }

    @Test
    fun `no amount, zero or none typed is "some", and an ingredient without a unit takes none`() {
        val plan = MealPlan(Meal("Test meal", 2, listOf(ingredient("Test eggs", each, "4"), Ingredient.typed("Test salt"))), today)
        assertNull(plan.withAmount(0, null).ingredients[0].amount)
        assertNull(plan.withAmount(0, BigDecimal.ZERO).ingredients[0].amount)
        assertNull(plan.withAmount(1, BigDecimal.ONE).ingredients[1].amount)
        assertSame(plan.meal, plan.withAmount(5, BigDecimal.ONE))
    }

    @Test
    fun `a draft becomes a meal once it has a name, trimmed, with servings in bounds`() {
        assertNull(MealDraft(name = "  ").toMeal())
        assertFalse(MealDraft().canSave)
        val meal = MealDraft("  Test chili ", 240, listOf(Ingredient.typed("Test beans")), id = 4).toMeal()
        assertEquals(Meal("Test chili", 99, listOf(Ingredient.typed("Test beans")), id = 4), meal)
        assertTrue(MealDraft().isNew)
        assertEquals(MealDraft("Test chili", 99, listOf(Ingredient.typed("Test beans")), 4), MealDraft.of(meal!!))
    }

    @Test
    fun `a draft picks, drops and types ingredients, and steps servings within bounds`() {
        val eggs = item(1, "Test eggs", "12", each)
        val draft = MealDraft("Test omelette").toggled(eggs).withTyped("  Test salt ").withTyped(" ")
        assertEquals(listOf(Ingredient.of(eggs), Ingredient.typed("Test salt")), draft.ingredients)
        assertEquals(listOf(Ingredient.typed("Test salt")), draft.toggled(eggs).ingredients)
        assertEquals(listOf(Ingredient.of(eggs)), draft.without(1).ingredients)
        assertEquals(1, MealDraft(servings = 1).withServings(-1).servings)
        assertEquals(3, MealDraft().withServings(1).servings)
    }
}
