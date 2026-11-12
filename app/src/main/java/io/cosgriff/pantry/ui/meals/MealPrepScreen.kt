package io.cosgriff.pantry.ui.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.expiryStatus
import io.cosgriff.pantry.domain.weekOf
import io.cosgriff.pantry.ui.components.PantryBottomBar
import io.cosgriff.pantry.ui.components.TopLevel
import io.cosgriff.pantry.ui.components.expiryText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

data class MealPrepActions(
    val onSelectDay: (LocalDate) -> Unit = {},
    val onWeek: (Long) -> Unit = {},
    val onPlan: (LocalDate) -> Unit = {},
    val onMeal: (MealPlan) -> Unit = {},
    val onSeeAllSoon: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onNavigate: (TopLevel) -> Unit = {},
)

@Composable
fun MealPrepScreen(
    onPlan: (LocalDate) -> Unit,
    onMeal: (MealPlan) -> Unit,
    onSeeAllSoon: () -> Unit,
    onImport: () -> Unit,
    onNavigate: (TopLevel) -> Unit,
    viewModel: MealPrepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MealPrepContent(state, MealPrepActions(viewModel::onSelectDay, viewModel::onWeek, onPlan, onMeal, onSeeAllSoon, onImport, onNavigate))
}

/** `design/source/MealPrep.dc.html` (#19): the week's days, what to use soon, and the week's meals. */
@Composable
fun MealPrepContent(state: MealPrepState, actions: MealPrepActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val planLabel = stringResource(R.string.meals_plan)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        bottomBar = { PantryBottomBar(TopLevel.MealPrep, actions.onNavigate) },
        floatingActionButton = {
            if (!state.loading) {
                ExtendedFloatingActionButton(
                    onClick = { actions.onPlan(state.selected) },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(planLabel, style = MaterialTheme.typography.labelLarge) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    // As on the list's Scan button: the text slot doesn't reach TalkBack by itself.
                    modifier = Modifier
                        .height(PantryDimens.fabHeight)
                        .semantics { contentDescription = planLabel },
                )
            }
        },
    ) { insets ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + PantryDimens.space6,
                // Room under the last meal for the Plan a meal button.
                bottom = insets.calculateBottomPadding() + PantryDimens.fabHeight + PantryDimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceSection),
        ) {
            item { WeekHeader(state, actions) }
            if (state.soon.isNotEmpty()) item { UseSoonRow(state, actions.onSeeAllSoon) }
            item {
                Column(Modifier.padding(horizontal = PantryDimens.space5), verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    Text(
                        stringResource(R.string.meals_this_week).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!state.loading && state.meals.isEmpty()) {
                        Text(stringResource(R.string.meals_nothing_planned), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    }
                    state.meals.forEach { MealCard(it, state.selected) { actions.onMeal(it.plan) } }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(state: MealPrepState, actions: MealPrepActions) {
    val colors = PantryTheme.colors
    Column(Modifier.padding(horizontal = PantryDimens.space5), verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceInset)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
                Text(
                    stringResource(R.string.meals_week_of, monthDay(state.week.first())),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted,
                )
                Text(stringResource(R.string.meals_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            // Not on the board: a recipe from a link (#43).
            IconButton(onClick = actions.onImport) { Icon(painterResource(R.drawable.ic_import), stringResource(R.string.import_open)) }
            // The board shows one week; these reach the next one to plan ahead, and back.
            IconButton(onClick = { actions.onWeek(-1) }) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.meals_previous_week)) }
            IconButton(onClick = { actions.onWeek(1) }) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.meals_next_week))
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
            state.week.forEach { day -> DayChip(day, day == state.selected, day in state.planned, Modifier.weight(1f)) { actions.onSelectDay(day) } }
        }
    }
}

@Composable
private fun DayChip(day: LocalDate, selected: Boolean, planned: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.small
    val content = if (selected) colors.ground else colors.ink
    val label = fullDay(day)
    Column(
        modifier
            .height(PantryDimens.dayChipHeight)
            .background(if (selected) colors.ink else colors.surface, shape)
            .then(if (selected) Modifier else Modifier.border(PantryDimens.hairline, colors.border, shape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // TalkBack reads the full date once, not "Mon" and "10" after it.
            .clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PantryDimens.space0, Alignment.CenterVertically),
    ) {
        Text(shortDay(day.dayOfWeek), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), fontWeight = FontWeight.Medium, color = content)
        Text(day.dayOfMonth.toString(), style = PantryTheme.text.dayNumber, color = content)
        Box(
            Modifier
                .size(PantryDimens.plannedDot)
                .background(if (!planned) Color.Transparent else if (selected) colors.ground else colors.accent, MaterialTheme.shapes.extraLarge),
        )
    }
}

@Composable
private fun UseSoonRow(state: MealPrepState, onSeeAll: () -> Unit) {
    val colors = PantryTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
        Row(Modifier.padding(start = PantryDimens.space5, end = PantryDimens.space2), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.meals_use_soon).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.meals_see_all, state.soonCount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.accent)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = PantryDimens.space5), horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
            items(state.soon, key = { it.id }) { item ->
                val status = expiryStatus(item.expires, state.today)
                Column(
                    Modifier
                        .widthIn(min = PantryDimens.soonChipMinWidth)
                        .background(colors.surface, MaterialTheme.shapes.medium)
                        .border(PantryDimens.hairline, colors.border, MaterialTheme.shapes.medium)
                        .padding(horizontal = PantryDimens.space3, vertical = PantryDimens.spaceIcon),
                    verticalArrangement = Arrangement.spacedBy(PantryDimens.space0),
                ) {
                    Text(item.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        expiryText(status, item.expires),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status is ExpiryStatus.Expired) colors.danger else colors.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCard(meal: WeekMeal, selected: LocalDate, onClick: () -> Unit) {
    val colors = PantryTheme.colors
    val plan = meal.plan
    val badge = when {
        plan.cooked -> stringResource(R.string.meals_cooked)
        meal.toBuy == 0 -> stringResource(R.string.meals_all_in_pantry)
        else -> pluralStringResource(R.plurals.meals_to_buy, meal.toBuy, meal.toBuy)
    }
    val label = stringResource(R.string.meals_card_label, plan.meal.name, fullDay(plan.day), badge)
    val names = plan.meal.ingredients.joinToString { it.name }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.large)
            .border(if (plan.day == selected) PantryDimens.borderSelected else PantryDimens.hairline, if (plan.day == selected) colors.borderStrong else colors.border, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = label }
            .padding(PantryDimens.spaceInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        Column(Modifier.width(PantryDimens.mealDayColumn), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shortDay(plan.day.dayOfWeek), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.inkMuted)
            Text(plan.day.dayOfMonth.toString(), style = PantryTheme.text.dayNumberLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
            Text(plan.meal.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (names.isEmpty()) {
                    pluralStringResource(R.plurals.meals_servings_only, plan.servings, plan.servings)
                } else {
                    pluralStringResource(R.plurals.meals_uses, plan.servings, plan.servings, names)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val toBuy = !plan.cooked && meal.toBuy > 0
            Text(
                badge,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (toBuy) colors.onWarningContainer else colors.ink,
                modifier = Modifier
                    .background(if (toBuy) colors.warningContainer else colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = PantryDimens.space2, vertical = PantryDimens.space0),
            )
        }
    }
}

// Previews and the screenshot test use made-up items and meals, marked as samples.
internal val previewToday: LocalDate = LocalDate.of(2026, 11, 10)

private fun sample(id: Long, name: String, days: Long, amount: String, unit: AmountUnit = AmountUnit.Measured(MeasureUnit.Each)) =
    PantryItem(id = id, name = name, location = Location.Fridge, quantity = unit.of(BigDecimal(amount)), purchased = previewToday, expires = previewToday.plusDays(days))

internal val previewEggs = sample(1, "Sample eggs", 2, "18")
internal val previewSpinach = sample(2, "Sample spinach", -1, "1", AmountUnit.Packages(Package.Bag))
internal val previewChicken = sample(3, "Sample rotisserie chicken", 3, "1", AmountUnit.Packages(Package.Pack))
internal val previewYogurt = sample(4, "Sample Greek yogurt", 5, "2", AmountUnit.Packages(Package.Tub))

internal val previewEggBites = MealPlan(
    Meal(
        "Sample egg bites",
        12,
        listOf(
            Ingredient.of(previewEggs).copy(amount = BigDecimal("12")),
            Ingredient.of(previewSpinach),
            Ingredient("Sample cottage cheese", fromPantry = true, itemId = 9, unit = AmountUnit.Packages(Package.Tub), amount = BigDecimal.ONE),
            Ingredient.typed("Sample salt and pepper"),
        ),
        id = 1,
    ),
    previewToday,
    id = 1,
)

internal val previewWeek = MealPrepState(
    today = previewToday,
    week = weekOf(previewToday, DayOfWeek.SUNDAY),
    loading = false,
    planned = setOf(previewToday, previewToday.plusDays(2)),
    soon = listOf(previewSpinach, previewEggs, previewChicken),
    soonCount = 4,
    meals = listOf(
        WeekMeal(previewEggBites, toBuy = 1),
        WeekMeal(MealPlan(Meal("Sample chicken rice bowls", 4, listOf(Ingredient.of(previewChicken)), id = 2), previewToday.plusDays(2), id = 2), toBuy = 0),
    ),
)

@Preview
@Composable
private fun MealPrepPreview() = PantryTheme(darkTheme = false) { MealPrepContent(previewWeek, MealPrepActions()) }
