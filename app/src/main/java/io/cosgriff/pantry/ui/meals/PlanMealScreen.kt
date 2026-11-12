package io.cosgriff.pantry.ui.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Amount
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.expiryStatus
import io.cosgriff.pantry.ui.components.AddField
import io.cosgriff.pantry.ui.components.AmountDialog
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.FieldLabel
import io.cosgriff.pantry.ui.components.QuantityStepper
import io.cosgriff.pantry.ui.components.SectionHeading
import io.cosgriff.pantry.ui.components.TextFieldBox
import io.cosgriff.pantry.ui.components.expiryText
import io.cosgriff.pantry.ui.components.groupedRow
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.time.LocalDate

data class PlanMealActions(
    val onBack: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onServings: (Int) -> Unit = {},
    val onToggle: (PantryItem) -> Unit = {},
    val onAddTyped: (String) -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onAmount: (Int, Amount?) -> Unit = { _, _ -> },
    val onCookAgain: (Meal) -> Unit = {},
    val onSave: () -> Unit = {},
)

/** Plans a new meal on [day], or edits saved meal [mealId]; [onDone] once it's saved. */
@Composable
fun PlanMealScreen(day: LocalDate, mealId: Long?, onBack: () -> Unit, onDone: () -> Unit, viewModel: PlanMealViewModel = hiltViewModel()) {
    LaunchedEffect(day, mealId) { viewModel.load(day, mealId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    PlanMealContent(
        state,
        PlanMealActions(
            onBack, viewModel::onName, viewModel::onServings, viewModel::onToggle, viewModel::onAddTyped, viewModel::onRemove,
            viewModel::onAmount, viewModel::onCookAgain, viewModel::onSave,
        ),
    )
}

/**
 * Planning a meal (#19), which has no board of its own: a saved meal in one tap, or a name, servings and
 * ingredients picked from the pantry (use-soon first) or typed in. Built from Item details' and the shopping
 * list's pieces.
 */
@Composable
fun PlanMealContent(state: PlanMealState, actions: PlanMealActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val draft = state.draft
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        topBar = {
            BackTopBar(
                stringResource(if (state.editing) R.string.plan_edit_title else R.string.plan_title),
                stringResource(R.string.plan_back),
                actions.onBack,
                Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            Button(
                onClick = actions.onSave,
                enabled = draft.name.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                // Disabled until named: still legible, as on the scan sheet.
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.inkMuted,
                ),
                // Above the keyboard while a field is being typed in, as Item details' Save.
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space3)
                    .fillMaxWidth()
                    .height(PantryDimens.buttonHeight),
            ) {
                Text(
                    if (state.editing) stringResource(R.string.plan_save_edit) else stringResource(R.string.plan_save, shortDate(state.day)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { insets ->
        if (state.loading) return@Scaffold
        LazyColumn(contentPadding = PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + PantryDimens.space4)) {
            item {
                Column(
                    Modifier.padding(horizontal = PantryDimens.space5),
                    verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
                ) {
                    if (!state.editing) Text(fullDay(state.day), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                    if (state.saved.isNotEmpty()) CookAgain(state.saved, actions.onCookAgain)
                    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
                        val label = stringResource(R.string.plan_name)
                        FieldLabel(label)
                        TextFieldBox(draft.name, actions.onName, label, stringResource(R.string.plan_name_hint))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
                        FieldLabel(stringResource(R.string.plan_servings))
                        QuantityStepper(draft.servings.toString(), actions.onServings, decreaseLabel = stringResource(R.string.meal_fewer), increaseLabel = stringResource(R.string.meal_more_servings))
                    }
                    SectionHeading(stringResource(R.string.plan_ingredients), Modifier.padding(top = PantryDimens.space2))
                    if (draft.ingredients.isEmpty()) {
                        Text(stringResource(R.string.plan_no_ingredients), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    }
                }
            }
            itemsIndexed(draft.ingredients) { index, ingredient ->
                IngredientRow(
                    ingredient,
                    state.pantry.firstOrNull { it.id == ingredient.itemId },
                    onRemove = { actions.onRemove(index) },
                    // A typed-in ingredient isn't checked against the pantry, so has no amount to set.
                    onAmount = if (ingredient.fromPantry) ({ editing = index }) else null,
                    modifier = Modifier
                        .padding(horizontal = PantryDimens.space5)
                        .padding(top = if (index == 0) PantryDimens.space2 else PantryDimens.flat)
                        .groupedRow(index, draft.ingredients.size, MaterialTheme.shapes.large, colors.surface, colors.border, colors.divider),
                )
            }
            item {
                Column(
                    Modifier.padding(horizontal = PantryDimens.space5).padding(top = PantryDimens.space3),
                    verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
                ) {
                    AddField(stringResource(R.string.plan_add_typed), actions.onAddTyped)
                    SectionHeading(stringResource(R.string.plan_from_pantry), Modifier.padding(top = PantryDimens.space2))
                    if (state.pantry.isEmpty()) {
                        Text(stringResource(R.string.plan_pantry_empty), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    }
                }
            }
            itemsIndexed(state.pantry, key = { _, item -> item.id }) { index, item ->
                PantryPickRow(
                    item,
                    picked = draft.ingredients.any { it.fromPantry && it.itemId == item.id },
                    today = state.today,
                    onToggle = { actions.onToggle(item) },
                    modifier = Modifier
                        .padding(horizontal = PantryDimens.space5)
                        .padding(top = if (index == 0) PantryDimens.space2 else PantryDimens.flat)
                        .groupedRow(index, state.pantry.size, MaterialTheme.shapes.large, colors.surface, colors.border, colors.divider),
                )
            }
        }
    }
    editing?.let { index ->
        val ingredient = draft.ingredients.getOrNull(index)
        val unit = ingredient?.unit
        if (ingredient == null || unit == null) {
            editing = null
        } else {
            AmountDialog(
                title = stringResource(R.string.meal_amount_title, ingredient.name),
                hint = pluralStringResource(R.plurals.meal_amount_hint, draft.servings, draft.servings),
                current = ingredient.amount?.let(unit::of),
                unit = unit,
                system = state.measurements,
                onDone = { actions.onAmount(index, it); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookAgain(saved: List<Meal>, onPick: (Meal) -> Unit) {
    val colors = PantryTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
        SectionHeading(stringResource(R.string.plan_cook_again))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
            saved.forEach { meal ->
                val shape = MaterialTheme.shapes.extraLarge
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button) { onPick(meal) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .height(PantryDimens.chipHeight)
                            .background(colors.surface, shape)
                            .border(PantryDimens.hairline, colors.border, shape)
                            .padding(horizontal = PantryDimens.spaceInset),
                        contentAlignment = Alignment.Center,
                    ) { Text(meal.name, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, item: PantryItem?, onRemove: () -> Unit, onAmount: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val label = stringResource(R.string.meal_set_amount, ingredient.name)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .then(if (onAmount != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onAmount) else Modifier)
            .padding(start = PantryDimens.spaceInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = PantryDimens.spaceIcon), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
            Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !ingredient.fromPantry -> stringResource(R.string.plan_not_tracked)
                    item != null -> locationName(item.location)
                    else -> stringResource(R.string.meal_not_in_pantry)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        if (ingredient.fromPantry) {
            val amount = ingredient.amount?.let { number -> ingredient.unit?.let { quantityText(it.of(number)) } }
            Text(amount ?: stringResource(R.string.meal_some), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.plan_remove, ingredient.name), tint = colors.inkMuted)
        }
    }
}

@Composable
private fun PantryPickRow(item: PantryItem, picked: Boolean, today: LocalDate, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val status = expiryStatus(item.expires, today)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .toggleable(value = picked, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = PantryDimens.spaceInset, vertical = PantryDimens.spaceIcon),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        Checkbox(checked = picked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.plan_pick_detail, expiryText(status, item.expires), locationName(item.location)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (status is ExpiryStatus.Expired || status is ExpiryStatus.ExpiresSoon) FontWeight.SemiBold else FontWeight.Normal,
                color = when (status) {
                    is ExpiryStatus.Expired -> colors.danger
                    is ExpiryStatus.ExpiresSoon -> colors.warning
                    else -> colors.inkMuted
                },
            )
        }
    }
}

// Previews and the screenshot test use made-up items and meals, marked as samples.
internal val previewPlanMeal = PlanMealState(
    today = previewToday,
    loading = false,
    draft = MealDraft("Sample egg bites", 12, listOf(Ingredient.of(previewEggs).copy(amount = BigDecimal(12)), Ingredient.of(previewSpinach), Ingredient.typed("Sample salt and pepper"))),
    pantry = listOf(previewSpinach, previewEggs, previewChicken, previewYogurt),
    saved = listOf(previewEggBites.meal.copy(name = "Sample frittata", id = 5), Meal("Sample yogurt parfaits", 2, emptyList(), id = 6)),
)

@Preview
@Composable
private fun PlanMealPreview() = PantryTheme(darkTheme = false) { PlanMealContent(previewPlanMeal, PlanMealActions()) }
