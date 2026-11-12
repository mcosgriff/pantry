package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * One ingredient of a meal (#19). Picked from the pantry, it keeps that item's id, barcode and unit; typed in
 * (salt, a spice the pantry doesn't track), it has only a name.
 *
 * @property fromPantry picked from the pantry, so it is checked against it; `false`: "not tracked".
 * @property itemId the pantry item it was picked from. That item may be gone since (eaten, thrown out); the
 *   ingredient then matches another item with the same barcode or name.
 * @property amount how much the meal uses at its own servings, in [unit]; `null` for "some", which cooking
 *   leaves alone.
 */
data class Ingredient(
    val name: String,
    val fromPantry: Boolean = false,
    val itemId: Long? = null,
    val barcode: String? = null,
    val unit: AmountUnit? = null,
    val amount: BigDecimal? = null,
) {
    init {
        require(name.isNotBlank()) { "Ingredient name must not be blank" }
        require(!fromPantry || unit != null) { "An ingredient from the pantry carries its item's unit" }
        require(amount == null || (unit != null && amount.signum() > 0)) { "An amount is positive and has a unit" }
    }

    companion object {
        /** [item] as an ingredient, with no amount yet. */
        fun of(item: PantryItem) = Ingredient(item.name, fromPantry = true, itemId = item.id, barcode = item.barcode, unit = item.quantity.countedIn)

        /** A typed-in ingredient the pantry doesn't track. */
        fun typed(name: String) = Ingredient(name.trim())
    }
}

/** A meal the household cooks: kept after it's cooked, so planning it again is one tap. */
data class Meal(val name: String, val servings: Int, val ingredients: List<Ingredient>, val id: Long = 0) {
    init {
        require(name.isNotBlank()) { "Meal name must not be blank" }
        require(servings >= 1) { "A meal serves at least one: $servings" }
    }
}

/** [meal] planned on [day] for [servings], which scales its ingredient amounts. */
data class MealPlan(val meal: Meal, val day: LocalDate, val servings: Int = meal.servings, val cooked: Boolean = false, val id: Long = 0) {
    init {
        require(servings >= 1) { "A plan serves at least one: $servings" }
    }

    /**
     * [ingredient]'s amount at this plan's servings, or `null` for "some". Things counted whole (each, pack) round
     * up: 8 eggs for 3 are 6 for 2, not 5.333.
     */
    fun needed(ingredient: Ingredient): BigDecimal? = ingredient.amount?.let { amount ->
        val scaled = scaled(amount, meal.servings, servings)
        if (countedWhole(ingredient.unit)) scaled.setScale(0, RoundingMode.CEILING) else scaled
    }

    private fun countedWhole(unit: AmountUnit?): Boolean =
        unit == AmountUnit.Measured(MeasureUnit.Each) || (unit is AmountUnit.Packages && unit.pkg == Package.Pack)
}

/** [amount] for [from] servings, made for [to]: 12 eggs for 12 are 6 for 6. Kept to 3 decimals. */
fun scaled(amount: BigDecimal, from: Int, to: Int): BigDecimal =
    if (from == to) amount else amount.multiply(BigDecimal(to)).divide(BigDecimal(from), 3, RoundingMode.HALF_UP).stripTrailingZeros()

/** How an ingredient stands against the pantry, as Meal details shows it. */
sealed interface IngredientStatus {
    /** Typed in, so the pantry isn't checked. */
    data object NotTracked : IngredientStatus

    /** No item for it, or none left. */
    data object Missing : IngredientStatus

    data class Expired(val item: PantryItem, val daysAgo: Long) : IngredientStatus

    /** In the pantry, but less than the plan needs (same unit). */
    data class Short(val item: PantryItem) : IngredientStatus

    data class Have(val item: PantryItem) : IngredientStatus
}

/** One ingredient of a plan checked against the pantry: the amount it needs and its status. */
data class IngredientCheck(val ingredient: Ingredient, val needed: BigDecimal?, val status: IngredientStatus) {
    val item: PantryItem?
        get() = when (status) {
            is IngredientStatus.Have -> status.item
            is IngredientStatus.Short -> status.item
            is IngredientStatus.Expired -> status.item
            IngredientStatus.Missing, IngredientStatus.NotTracked -> null
        }

    /** Has to be bought: missing, or short of what the plan needs. */
    val toBuy: Boolean get() = status == IngredientStatus.Missing || status is IngredientStatus.Short
}

/**
 * The pantry item [ingredient] stands for: the item it was picked from while that is still there; otherwise the
 * items with its barcode or, failing that, its name (ignoring case), soonest-expiring first, the expired ones last.
 * A typed-in ingredient has none.
 */
fun pantryMatch(ingredient: Ingredient, items: List<PantryItem>, today: LocalDate): PantryItem? {
    if (!ingredient.fromPantry) return null
    ingredient.itemId?.let { id -> items.firstOrNull { it.id == id }?.let { return it } }
    val byBarcode = ingredient.barcode?.let { code -> items.filter { it.barcode == code } }.orEmpty()
    val candidates = byBarcode.ifEmpty { items.filter { it.name.trim().equals(ingredient.name.trim(), ignoreCase = true) } }
    return candidates.sortedWith(compareBy<PantryItem> { it.expires?.isBefore(today) == true }.then(PantryItem.BySoonestExpiry)).firstOrNull()
}

/** [plan]'s ingredients checked against [items] on [today], in the meal's order. */
fun checkPlan(plan: MealPlan, items: List<PantryItem>, today: LocalDate): List<IngredientCheck> =
    plan.meal.ingredients.map { ingredient ->
        val needed = plan.needed(ingredient)
        val item = pantryMatch(ingredient, items, today)
        val status = when {
            !ingredient.fromPantry -> IngredientStatus.NotTracked
            item == null || item.quantity.number.signum() == 0 -> IngredientStatus.Missing
            item.expires?.isBefore(today) == true -> IngredientStatus.Expired(item, ChronoUnit.DAYS.between(item.expires, today))
            needed != null && item.quantity.countedIn == ingredient.unit && item.quantity.number < needed -> IngredientStatus.Short(item)
            else -> IngredientStatus.Have(item)
        }
        IngredientCheck(ingredient, needed, status)
    }

/** What cooking a plan does to the pantry: items with less left, and items used up (to remove). */
data class Cooked(val updated: List<PantryItem>, val usedUp: List<PantryItem>)

/**
 * Takes each ingredient's needed amount out of its pantry item, when the units match. Ingredients with no
 * amount ("some"), typed-in ones and missing ones are left alone. Two ingredients from one item add up; an item
 * left with nothing is used up.
 */
fun cook(checks: List<IngredientCheck>): Cooked {
    val taken = linkedMapOf<Long, Pair<PantryItem, BigDecimal>>()
    checks.forEach { check ->
        val item = check.item ?: return@forEach
        val needed = check.needed ?: return@forEach
        if (item.quantity.countedIn != check.ingredient.unit) return@forEach
        val before = taken[item.id]?.second ?: BigDecimal.ZERO
        taken[item.id] = item to before + needed
    }
    val updated = mutableListOf<PantryItem>()
    val usedUp = mutableListOf<PantryItem>()
    taken.values.forEach { (item, amount) ->
        val left = item.quantity.number - amount
        if (left.signum() <= 0) usedUp += item else updated += item.copy(quantity = item.quantity.withNumber(left))
    }
    return Cooked(updated, usedUp)
}

/** The seven days of the week [day] falls in, starting on [firstDay] (the user's locale decides it). */
fun weekOf(day: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val start = day.with(TemporalAdjusters.previousOrSame(firstDay))
    return (0L..6L).map(start::plusDays)
}

/** [plans] on the days of [week], by day, then in the order they were planned. */
fun plansIn(plans: List<MealPlan>, week: List<LocalDate>): List<MealPlan> =
    plans.filter { it.day in week }.sortedWith(compareBy<MealPlan> { it.day }.thenBy { it.id })
