package io.cosgriff.pantry.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.Consumption
import io.cosgriff.pantry.data.MealRepository
import io.cosgriff.pantry.domain.IngredientCheck
import io.cosgriff.pantry.domain.MealPlan
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealDetailsState(
    val today: LocalDate,
    val loading: Boolean = true,
    /** `null` once the plan is gone (removed from the plan): the screen closes. */
    val plan: MealPlan? = null,
    val checks: List<IngredientCheck> = emptyList(),
    /** How many lines "Add missing" just put on the shopping list, to say so once. */
    val addedToShopping: Int? = null,
    /** Set after "Cooked it": the screen closes. */
    val done: Boolean = false,
)

@HiltViewModel
class MealDetailsViewModel @Inject constructor(
    private val meals: MealRepository,
    private val consumption: Consumption,
    private val clock: Clock,
) : ViewModel() {
    private val planId = MutableStateFlow<Long?>(null)
    private val events = MutableStateFlow(Events())

    private data class Events(val added: Int? = null, val done: Boolean = false)

    val state: StateFlow<MealDetailsState> = combine(planId, meals.checkedPlans(), events) { id, plans, e ->
        val checked = plans.firstOrNull { it.plan.id == id }
        MealDetailsState(
            today = LocalDate.now(clock),
            loading = id == null,
            plan = checked?.plan,
            checks = checked?.checks.orEmpty(),
            addedToShopping = e.added,
            done = e.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailsState(today = LocalDate.now(clock)))

    fun load(id: Long) {
        planId.value = id
    }

    private fun current(): MealPlan? = state.value.plan

    fun onServings(delta: Int) {
        val plan = current() ?: return
        viewModelScope.launch { meals.stepServings(plan, delta) }
    }

    /** Sets ingredient [index]'s amount for this plan's servings ([amount] `null`: "some"). */
    fun onAmount(index: Int, amount: BigDecimal?) {
        val plan = current() ?: return
        viewModelScope.launch { meals.setAmount(plan, index, amount) }
    }

    fun onAddMissing() {
        val plan = current() ?: return
        viewModelScope.launch {
            val added = meals.addMissingToShopping(plan)
            events.update { it.copy(added = added) }
        }
    }

    fun onAddedShown() = events.update { it.copy(added = null) }

    fun onCooked() {
        val plan = current() ?: return
        viewModelScope.launch {
            consumption.cooked(plan)
            events.update { it.copy(done = true) }
        }
    }

    fun onUnplan() {
        val plan = current() ?: return
        viewModelScope.launch {
            meals.unplan(plan)
            events.update { it.copy(done = true) }
        }
    }
}
