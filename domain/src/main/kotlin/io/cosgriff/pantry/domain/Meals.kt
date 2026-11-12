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

    /** In the pantry, but less than the plan needs, compared in the item's unit. */
    data class Short(val item: PantryItem) : IngredientStatus

    /**
     * In the pantry, but the plan's amount doesn't convert to the item's: "1 tbsp" against "1 tub" with no
     * contents (docs/adr/0002, #55). Neither a match nor a shortage; cooking leaves the item alone.
     */
    data class CantCompare(val item: PantryItem) : IngredientStatus

    data class Have(val item: PantryItem) : IngredientStatus
}

/** One ingredient of a plan checked against the pantry: the amount it needs and its status. */
data class IngredientCheck(val ingredient: Ingredient, val needed: BigDecimal?, val status: IngredientStatus) {
    val item: PantryItem?
        get() = when (status) {
            is IngredientStatus.Have -> status.item
            is IngredientStatus.Short -> status.item
            is IngredientStatus.Expired -> status.item
            is IngredientStatus.CantCompare -> status.item
            IngredientStatus.Missing, IngredientStatus.NotTracked -> null
        }

    /** [needed] in the ingredient's unit, or `null` for "some". */
    val neededAmount: Amount? get() = needed?.let { amount -> ingredient.unit?.of(amount) }

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

/**
 * [plan]'s ingredients checked against [items] on [today], in the meal's order. An amount is compared after
 * converting it to the item's unit (#55); one that doesn't convert is [IngredientStatus.CantCompare].
 */
fun checkPlan(plan: MealPlan, items: List<PantryItem>, today: LocalDate): List<IngredientCheck> =
    plan.meal.ingredients.map { ingredient ->
        val needed = plan.needed(ingredient)
        val item = pantryMatch(ingredient, items, today)
        val status = when {
            !ingredient.fromPantry -> IngredientStatus.NotTracked
            item == null || item.quantity.number.signum() == 0 -> IngredientStatus.Missing
            item.expires?.isBefore(today) == true -> IngredientStatus.Expired(item, ChronoUnit.DAYS.between(item.expires, today))
            else -> {
                val amount = needed?.let { ingredient.unit?.of(it) }
                when (amount?.let { item.quantity.covers(it, densityFor(ingredient, item)) }) {
                    null -> if (amount == null) IngredientStatus.Have(item) else IngredientStatus.CantCompare(item)
                    false -> IngredientStatus.Short(item)
                    true -> IngredientStatus.Have(item)
                }
            }
        }
        IngredientCheck(ingredient, needed, status)
    }

/** The density that converts between [ingredient]'s volume and [item]'s mass: the item's own, else the table's. */
internal fun densityFor(ingredient: Ingredient, item: PantryItem): Density? =
    densityOf(item.name, item.density) ?: densityOf(ingredient.name)

/**
 * What cooking a plan does to the pantry: items with less left, and items used up (to remove). [approximate]
 * holds the ids of updated items whose amount left went through a density, so is an estimate (≈, #55).
 */
data class Cooked(val updated: List<PantryItem>, val usedUp: List<PantryItem>, val approximate: Set<Long> = emptySet())

/**
 * Takes each ingredient's needed amount out of its pantry item, in the item's own unit (#55): a packaged item
 * loses contents, leaving fractional packages. Ingredients with no amount ("some"), typed-in ones, missing
 * ones and ones whose amount doesn't convert are left alone. Two ingredients from one item add up; an item left
 * with nothing is used up. What's left is kept to 3 decimals.
 */
fun cook(checks: List<IngredientCheck>): Cooked {
    val left = linkedMapOf<Long, Pair<PantryItem, Taken.Left>>()
    checks.forEach { check ->
        val item = check.item ?: return@forEach
        val used = check.neededAmount ?: return@forEach
        val before = left[item.id]?.second ?: Taken.Left(item.quantity, approximate = false)
        when (val taken = before.amount.take(used, densityFor(check.ingredient, item))) {
            Taken.NotComparable -> return@forEach
            is Taken.Left -> left[item.id] = item to Taken.Left(taken.amount, before.approximate || taken.approximate)
        }
    }
    val updated = mutableListOf<PantryItem>()
    val usedUp = mutableListOf<PantryItem>()
    val approximate = mutableSetOf<Long>()
    left.values.forEach { (item, taken) ->
        val number = taken.amount.number.let { if (it.scale() > 3) it.setScale(3, RoundingMode.HALF_UP) else it }
        if (number.signum() <= 0) {
            usedUp += item
        } else {
            updated += item.copy(quantity = taken.amount.withNumber(number))
            if (taken.approximate) approximate += item.id
        }
    }
    return Cooked(updated, usedUp, approximate)
}

/** The seven days of the week [day] falls in, starting on [firstDay] (the user's locale decides it). */
fun weekOf(day: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val start = day.with(TemporalAdjusters.previousOrSame(firstDay))
    return (0L..6L).map(start::plusDays)
}

/** [plans] on the days of [week], by day, then in the order they were planned. */
fun plansIn(plans: List<MealPlan>, week: List<LocalDate>): List<MealPlan> =
    plans.filter { it.day in week }.sortedWith(compareBy<MealPlan> { it.day }.thenBy { it.id })
