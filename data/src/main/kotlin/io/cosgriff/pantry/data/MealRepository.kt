package io.cosgriff.pantry.data

import io.cosgriff.pantry.data.db.MealDao
import io.cosgriff.pantry.data.db.MealEntity
import io.cosgriff.pantry.data.db.MealIngredientEntity
import io.cosgriff.pantry.data.db.MealPlanEntity
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.ShoppingReason
import io.cosgriff.pantry.domain.checkPlan
import io.cosgriff.pantry.domain.cook
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Meals and the days they're planned on (#19). Cooking a plan takes its amounts out of the pantry and puts what
 * it used up on the shopping list, as Use soon's "Used up" does.
 */
@Singleton
class MealRepository @Inject constructor(
    private val dao: MealDao,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
) {
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

    /** Saves [meal] (new when its id is 0) with its ingredients; returns its id. */
    suspend fun save(meal: Meal): Long =
        dao.save(MealEntity(meal.id, meal.name.trim(), meal.servings), meal.ingredients.map { it.toEntity() })

    /** Plans meal [mealId] on [day] for [servings]; returns the plan's id. */
    suspend fun plan(mealId: Long, day: LocalDate, servings: Int): Long =
        dao.insertPlan(MealPlanEntity(mealId = mealId, day = day.toEpochDay(), servings = servings, cooked = false))

    suspend fun setServings(plan: MealPlan, servings: Int) = dao.updatePlan(plan.toEntity().copy(servings = servings))

    /** Takes [plan] off its day. The meal stays saved, to be planned again. */
    suspend fun unplan(plan: MealPlan) = dao.deletePlan(plan.id)

    /** Puts [plan]'s missing and short ingredients on the shopping list, under "For this week's meals". */
    suspend fun addMissingToShopping(plan: MealPlan, today: LocalDate) {
        val names = checkPlan(plan, pantry.items().first(), today).filter { it.toBuy }.map { it.ingredient.name }
        shopping.addForMeal(names, plan.meal.name, plan.day, today)
    }

    /**
     * "Cooked it": takes [plan]'s amounts out of the pantry, removes what that uses up and puts it on the shopping
     * list, and marks the plan cooked.
     */
    suspend fun cooked(plan: MealPlan, today: LocalDate) {
        val cooked = cook(checkPlan(plan, pantry.items().first(), today))
        cooked.updated.forEach { pantry.save(it) }
        cooked.usedUp.forEach {
            pantry.delete(it.id)
            shopping.add(it.name, ShoppingReason.UsedUp, today)
        }
        dao.updatePlan(plan.toEntity().copy(cooked = true))
    }
}

private fun MealEntity.toDomain(ingredients: List<MealIngredientEntity>) =
    Meal(name = name, servings = servings, ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() }, id = id)

private fun MealIngredientEntity.toDomain() = Ingredient(
    name = name,
    fromPantry = fromPantry,
    itemId = itemId,
    barcode = barcode,
    unit = unit?.let(QuantityUnit::valueOf),
    amount = amount?.let(::BigDecimal),
)

private fun Ingredient.toEntity() = MealIngredientEntity(
    mealId = 0,
    position = 0,
    name = name.trim(),
    fromPantry = fromPantry,
    itemId = itemId,
    barcode = barcode,
    unit = unit?.name,
    amount = amount?.toPlainString(),
)

private fun MealPlan.toEntity() = MealPlanEntity(id = id, mealId = meal.id, day = day.toEpochDay(), servings = servings, cooked = cooked)
