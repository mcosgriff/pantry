package io.cosgriff.pantry.data

import androidx.room3.withWriteTransaction
import io.cosgriff.pantry.data.db.MealEntity
import io.cosgriff.pantry.data.db.MealIngredientEntity
import io.cosgriff.pantry.data.db.MealPlanEntity
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.domain.CheckedPlan
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.IngredientCheck
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.MealDraft
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.Servings
import io.cosgriff.pantry.domain.checkPlan
import io.cosgriff.pantry.domain.withAmount
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Meals and the days they're planned on (#19), and meal plan editing (#50): saving a draft (a new meal is planned
 * on its day in the same write), a plan's servings and amounts, and its ingredients checked against the pantry,
 * which only this class works out. Rounding and bounds are `:domain`'s (`PlanAmounts`, `Servings`). Cooking a plan
 * is [Consumption]'s. The day plans are checked on comes from the injected [Clock] (#52).
 */
@Singleton
class MealRepository @Inject constructor(
    private val db: PantryDatabase,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) {
    private val dao = db.meals()

    /** Every saved meal, by name. */
    fun meals(): Flow<List<Meal>> = combine(dao.observeMeals(), dao.observeIngredients()) { meals, ingredients ->
        val byMeal = ingredients.groupBy { it.mealId }
        meals.map { it.toDomain(byMeal[it.id].orEmpty()) }
    }

    /** Every plan, by day. */
    fun plans(): Flow<List<MealPlan>> = combine(meals(), dao.observePlans()) { meals, plans ->
        val byId = meals.associateBy { it.id }
        plans.mapNotNull { p -> byId[p.mealId]?.let { MealPlan(it, LocalDate.ofEpochDay(p.day), p.servings, p.cooked, p.id) } }
    }

    /** Every plan, by day, with its ingredients checked against the pantry today. */
    fun checkedPlans(): Flow<List<CheckedPlan>> = combine(plans(), pantry.items()) { plans, items ->
        val today = LocalDate.now(clock)
        plans.map { CheckedPlan(it, checkPlan(it, items, today)) }
    }

    /** [plan]'s ingredients checked against the pantry as it is now. */
    internal suspend fun check(plan: MealPlan): List<IngredientCheck> = checkPlan(plan, pantry.items().first(), LocalDate.now(clock))

    /** Saves [meal] (new when its id is 0) with its ingredients; returns its id. */
    suspend fun save(meal: Meal): Long =
        dao.save(MealEntity(meal.id, meal.name, meal.servings), meal.ingredients.map { it.toEntity() })

    /**
     * Saves [draft] as a meal; a new one is also planned on [day] for its servings, in the same write. An edited
     * meal keeps the plans it has. Returns the meal's id, or `null` when the draft can't be saved yet (no name).
     */
    suspend fun save(draft: MealDraft, day: LocalDate): Long? {
        val meal = draft.toMeal() ?: return null
        if (!draft.isNew) return save(meal)
        return db.withWriteTransaction {
            val id = save(meal)
            plan(id, day, meal.servings)
            id
        }
    }

    /** Plans meal [mealId] on [day] for [servings]; returns the plan's id. */
    suspend fun plan(mealId: Long, day: LocalDate, servings: Int): Long =
        dao.insertPlan(MealPlanEntity(mealId = mealId, day = day.toEpochDay(), servings = Servings.bounded(servings), cooked = false))

    /** Steps [plan]'s servings by [delta], within `Servings`' bounds. */
    suspend fun stepServings(plan: MealPlan, delta: Int) {
        val servings = Servings.stepped(plan.servings, delta)
        if (servings != plan.servings) dao.updatePlan(plan.toEntity().copy(servings = servings))
    }

    /**
     * Sets ingredient [index]'s amount, typed for [plan]'s servings ([amount] `null`: "some"). The meal keeps it for
     * its own servings (`withAmount`), so planning it again scales from there.
     */
    suspend fun setAmount(plan: MealPlan, index: Int, amount: BigDecimal?) {
        save(plan.withAmount(index, amount))
    }

    /** Takes [plan] off its day. The meal stays saved, to be planned again. */
    suspend fun unplan(plan: MealPlan) = dao.deletePlan(plan.id)

    /**
     * Puts [plan]'s missing and short ingredients on the shopping list, under "For this week's meals". Returns how
     * many lines it added: a name already on the list isn't added again, so isn't counted.
     */
    suspend fun addMissingToShopping(plan: MealPlan): Int {
        val names = check(plan).filter { it.toBuy }.map { it.ingredient.name }
        return shopping.addForMeal(names, plan.meal.name, plan.day, LocalDate.now(clock)).size
    }

    /** Marks [plan] cooked. Only [Consumption] calls this, as part of "Cooked it" (#48). */
    internal suspend fun markCooked(plan: MealPlan) = dao.updatePlan(plan.toEntity().copy(cooked = true))
}

private fun MealEntity.toDomain(ingredients: List<MealIngredientEntity>) =
    Meal(name = name, servings = servings, ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() }, id = id)

private fun MealIngredientEntity.toDomain() = Ingredient(
    name = name,
    fromPantry = fromPantry,
    itemId = itemId,
    barcode = barcode,
    unit = unit?.let { amountUnit(it, contentsAmount, contentsUnit) },
    amount = amount?.let(::BigDecimal),
)

private fun Ingredient.toEntity() = MealIngredientEntity(
    mealId = 0,
    position = 0,
    name = name.trim(),
    fromPantry = fromPantry,
    itemId = itemId,
    barcode = barcode,
    unit = unit?.storedName,
    amount = amount?.toPlainString(),
    contentsAmount = unit?.storedContentsAmount,
    contentsUnit = unit?.storedContentsUnit,
)

private fun MealPlan.toEntity() = MealPlanEntity(id = id, mealId = meal.id, day = day.toEpochDay(), servings = servings, cooked = cooked)
