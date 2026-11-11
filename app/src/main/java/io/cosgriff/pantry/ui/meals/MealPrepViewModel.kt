package io.cosgriff.pantry.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.MealRepository
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.UseSoonGroup
import io.cosgriff.pantry.domain.checkPlan
import io.cosgriff.pantry.domain.plansIn
import io.cosgriff.pantry.domain.useSoon
import io.cosgriff.pantry.domain.weekOf
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A meal planned this week, with how many of its ingredients are still to buy. */
data class WeekMeal(val plan: MealPlan, val toBuy: Int)

data class MealPrepState(
    val today: LocalDate,
    val week: List<LocalDate>,
    val selected: LocalDate = today,
    val loading: Boolean = true,
    /** Days of [week] with a meal planned. */
    val planned: Set<LocalDate> = emptySet(),
    /** Expired and this week's items, soonest first, and how many Use soon lists in all. */
    val soon: List<PantryItem> = emptyList(),
    val soonCount: Int = 0,
    val meals: List<WeekMeal> = emptyList(),
)

@HiltViewModel
class MealPrepViewModel @Inject constructor(
    pantry: PantryRepository,
    meals: MealRepository,
    private val clock: Clock,
) : ViewModel() {
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val today = LocalDate.now(clock)
    private val selected = MutableStateFlow(today)

    val state: StateFlow<MealPrepState> = combine(pantry.items(), meals.plans(), selected) { items, plans, day ->
        val now = LocalDate.now(clock)
        val week = weekOf(day, firstDay)
        val soonGroups = useSoon(items, now)
        val inWeek = plansIn(plans, week)
        MealPrepState(
            today = now,
            week = week,
            selected = day,
            loading = false,
            planned = inWeek.map { it.day }.toSet(),
            soon = soonGroups[UseSoonGroup.Expired].orEmpty() + soonGroups[UseSoonGroup.ThisWeek].orEmpty(),
            soonCount = soonGroups.values.sumOf { it.size },
            meals = inWeek.map { plan -> WeekMeal(plan, checkPlan(plan, items, now).count { it.toBuy }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPrepState(today = today, week = weekOf(today, firstDay)))

    fun onSelectDay(day: LocalDate) {
        selected.value = day
    }

    /** Moves a week back ([weeks] -1) or ahead, keeping the selected weekday. */
    fun onWeek(weeks: Long) {
        selected.value = selected.value.plusWeeks(weeks)
    }
}
