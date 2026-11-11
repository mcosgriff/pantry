package io.cosgriff.pantry.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.MealRepository
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.PantryItem
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The meal being planned or edited. */
data class MealDraft(
    val name: String = "",
    val servings: Int = DEFAULT_SERVINGS,
    val ingredients: List<Ingredient> = emptyList(),
    /** The saved meal being edited; 0 for a new one. */
    val id: Long = 0,
)

const val DEFAULT_SERVINGS = 2

data class PlanMealState(
    val today: LocalDate,
    val day: LocalDate = today,
    val editing: Boolean = false,
    val loading: Boolean = true,
    val draft: MealDraft = MealDraft(),
    /** The pantry, soonest expiry first, to pick ingredients from. */
    val pantry: List<PantryItem> = emptyList(),
    /** Saved meals to plan again in one tap; none while editing. */
    val saved: List<Meal> = emptyList(),
    /** Set once the meal is planned or saved: the screen closes. */
    val done: Boolean = false,
)

@HiltViewModel
class PlanMealViewModel @Inject constructor(
    pantry: PantryRepository,
    private val meals: MealRepository,
    private val clock: Clock,
) : ViewModel() {
    private val form = MutableStateFlow(PlanMealState(today = LocalDate.now(clock)))
    private var loaded = false

    val state: StateFlow<PlanMealState> = combine(form, pantry.items(), meals.meals()) { f, items, saved ->
        f.copy(pantry = items.sortedWith(PantryItem.BySoonestExpiry), saved = if (f.editing) emptyList() else saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    /** Plans a meal on [day], or edits saved meal [mealId]. Only the first call counts (the screen calls it on entry). */
    fun load(day: LocalDate, mealId: Long?) {
        if (loaded) return
        loaded = true
        if (mealId == null) {
            form.update { it.copy(day = day, loading = false) }
            return
        }
        viewModelScope.launch {
            val meal = meals.meals().first().firstOrNull { it.id == mealId }
            form.update {
                it.copy(
                    day = day,
                    editing = meal != null,
                    loading = false,
                    draft = meal?.let { m -> MealDraft(m.name, m.servings, m.ingredients, m.id) } ?: it.draft,
                )
            }
        }
    }

    private fun edit(change: (MealDraft) -> MealDraft) = form.update { it.copy(draft = change(it.draft)) }

    fun onName(name: String) = edit { it.copy(name = name) }

    fun onServings(delta: Int) = edit { it.copy(servings = (it.servings + delta).coerceIn(1, MAX_SERVINGS)) }

    /** Picks [item] as an ingredient, or drops it if it's picked already. */
    fun onToggle(item: PantryItem) = edit { d ->
        val picked = d.ingredients.indexOfFirst { it.fromPantry && it.itemId == item.id }
        d.copy(ingredients = if (picked >= 0) d.ingredients.filterIndexed { i, _ -> i != picked } else d.ingredients + Ingredient.of(item))
    }

    fun onAddTyped(name: String) {
        if (name.isBlank()) return
        edit { it.copy(ingredients = it.ingredients + Ingredient.typed(name)) }
    }

    fun onRemove(index: Int) = edit { d -> d.copy(ingredients = d.ingredients.filterIndexed { i, _ -> i != index }) }

    /** Plans saved [meal] on the day as it is, and closes. */
    fun onCookAgain(meal: Meal) {
        viewModelScope.launch {
            meals.plan(meal.id, form.value.day, meal.servings)
            form.update { it.copy(done = true) }
        }
    }

    /** Saves the meal; a new one is also planned on the day. */
    fun onSave() {
        val f = form.value
        val d = f.draft
        if (d.name.isBlank()) return
        viewModelScope.launch {
            val id = meals.save(Meal(d.name.trim(), d.servings, d.ingredients, d.id))
            if (!f.editing) meals.plan(id, f.day, d.servings)
            form.update { it.copy(done = true) }
        }
    }

    private companion object {
        const val MAX_SERVINGS = 99
    }
}
