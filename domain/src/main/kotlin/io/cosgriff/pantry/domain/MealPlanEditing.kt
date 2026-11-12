package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Meal plan editing (#50): how many a meal or plan may serve, how an amount typed for a plan is kept on its meal,
 * and when a draft is a meal that can be saved. The meal screens and `MealRepository` go through here, so the
 * rules are stated once.
 */
object Servings {
    const val MIN = 1

    /** The steppers stop here; a recipe that says it serves more is planned for this many. */
    const val MAX = 99

    /** A new meal's servings, and an imported recipe's when it doesn't say. */
    const val DEFAULT = 2

    /** [servings] kept within [MIN]..[MAX]. */
    fun bounded(servings: Int): Int = servings.coerceIn(MIN, MAX)

    /** [servings] stepped by [delta] (a stepper's -1 or +1), kept within the bounds. */
    fun stepped(servings: Int, delta: Int): Int = bounded(servings + delta)
}

/**
 * The rounding policy for an ingredient's amount between its meal's servings and a plan's (#50), in one place:
 *
 * - **Kept on the meal** ([atMeal]): an amount typed for the plan is scaled to the meal's servings and kept to
 *   [STORED_SCALE] decimals. That is finer than what is shown, so reading it back for the plan gives what was typed.
 * - **Needed by the plan** ([atPlan]): the meal's amount scaled to the plan's servings, kept to [SHOWN_SCALE]
 *   decimals (half up). Things counted whole (each, pack) then round up: 8 eggs for 3 are 6 for 2, not 5.333.
 *
 * So an amount typed with up to [SHOWN_SCALE] decimals reads back unchanged, except that a fraction of something
 * counted whole reads back rounded up: 2.5 eggs is 3. Scaling never goes the other way: what [atPlan] shows is
 * never kept, only what was typed.
 */
internal object PlanAmounts {
    const val SHOWN_SCALE = 3
    const val STORED_SCALE = 6

    fun atPlan(amount: BigDecimal, unit: AmountUnit?, mealServings: Int, planServings: Int): BigDecimal {
        val scaled = scale(amount, mealServings, planServings, SHOWN_SCALE)
        return if (countedWhole(unit)) scaled.setScale(0, RoundingMode.CEILING) else scaled
    }

    fun atMeal(amount: BigDecimal, mealServings: Int, planServings: Int): BigDecimal =
        scale(amount, planServings, mealServings, STORED_SCALE)

    /** [amount] for [from] servings, made for [to]: 12 eggs for 12 are 6 for 6. */
    private fun scale(amount: BigDecimal, from: Int, to: Int, decimals: Int): BigDecimal {
        val scaled = amount.multiply(BigDecimal(to)).divide(BigDecimal(from), decimals, RoundingMode.HALF_UP)
        // Without trailing zeros, but never in exponent form: 10, not 1E+1.
        return scaled.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
    }

    private fun countedWhole(unit: AmountUnit?): Boolean =
        unit == AmountUnit.Measured(MeasureUnit.Each) || (unit is AmountUnit.Packages && unit.pkg == Package.Pack)
}

/**
 * [meal] with ingredient [index]'s amount set to [amount], typed for this plan's servings ([amount] `null`, zero or
 * negative: "some"). The meal keeps it for its own servings ([PlanAmounts]), so planning it again scales from there
 * and [MealPlan.needed] reads back what was typed. An ingredient with no unit can't take an amount; an index out of
 * range changes nothing.
 */
fun MealPlan.withAmount(index: Int, amount: BigDecimal?): Meal {
    val ingredient = meal.ingredients.getOrNull(index) ?: return meal
    val forMeal = amount
        ?.takeIf { it.signum() > 0 && ingredient.unit != null }
        ?.let { PlanAmounts.atMeal(it, meal.servings, servings) }
        ?.takeIf { it.signum() > 0 }
    return meal.copy(ingredients = meal.ingredients.mapIndexed { i, g -> if (i == index) g.copy(amount = forMeal) else g })
}

/**
 * A meal being written or edited on Plan a meal or Import a recipe: anything typed so far, not yet valid. [toMeal]
 * makes it one. [id] is the saved meal being edited, 0 for a new one.
 */
data class MealDraft(
    val name: String = "",
    val servings: Int = Servings.DEFAULT,
    val ingredients: List<Ingredient> = emptyList(),
    val id: Long = 0,
) {
    /** A new meal; saving it also plans it (`MealRepository`). */
    val isNew: Boolean get() = id == 0L

    /** Has what saving needs: a name. */
    val canSave: Boolean get() = name.isNotBlank()

    fun withServings(delta: Int): MealDraft = copy(servings = Servings.stepped(servings, delta))

    /** Picks [item] as an ingredient, or drops it if it's picked already. */
    fun toggled(item: PantryItem): MealDraft {
        val picked = ingredients.indexOfFirst { it.fromPantry && it.itemId == item.id }
        return copy(ingredients = if (picked >= 0) ingredients.filterIndexed { i, _ -> i != picked } else ingredients + Ingredient.of(item))
    }

    /** Adds [name] as a typed-in ingredient; a blank name adds nothing. */
    fun withTyped(name: String): MealDraft = if (name.isBlank()) this else copy(ingredients = ingredients + Ingredient.typed(name))

    fun without(index: Int): MealDraft = copy(ingredients = ingredients.filterIndexed { i, _ -> i != index })

    /** The meal to save: the name trimmed, servings within bounds. `null` until it [canSave]. */
    fun toMeal(): Meal? = if (!canSave) null else Meal(name.trim(), Servings.bounded(servings), ingredients, id)

    companion object {
        /** [meal] to edit. */
        fun of(meal: Meal) = MealDraft(meal.name, meal.servings, meal.ingredients, meal.id)
    }
}
