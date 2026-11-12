package io.cosgriff.pantry.ui.meals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Amount
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Ingredient
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.Packaged
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.IngredientCheck
import io.cosgriff.pantry.domain.IngredientStatus
import io.cosgriff.pantry.domain.MealPlan
import io.cosgriff.pantry.domain.MeasureSystem
import io.cosgriff.pantry.domain.checkPlan
import io.cosgriff.pantry.domain.neededFigure
import io.cosgriff.pantry.ui.components.AmountDialog
import io.cosgriff.pantry.ui.components.PantryCard
import io.cosgriff.pantry.ui.components.QuantityStepper
import io.cosgriff.pantry.ui.components.SectionHeading
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.components.shownText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal

data class MealDetailsActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onUnplan: () -> Unit = {},
    val onServings: (Int) -> Unit = {},
    val onAmount: (Int, Amount?) -> Unit = { _, _ -> },
    val onAddMissing: () -> Unit = {},
    val onAddedShown: () -> Unit = {},
    val onCooked: () -> Unit = {},
)

@Composable
fun MealDetailsScreen(planId: Long, onBack: () -> Unit, onEdit: (MealPlan) -> Unit, viewModel: MealDetailsViewModel = hiltViewModel()) {
    LaunchedEffect(planId) { viewModel.load(planId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onBack() }
    MealDetailsContent(
        state,
        MealDetailsActions(
            onBack = onBack,
            onEdit = { state.plan?.let(onEdit) },
            onUnplan = viewModel::onUnplan,
            onServings = viewModel::onServings,
            onAmount = viewModel::onAmount,
            onAddMissing = viewModel::onAddMissing,
            onAddedShown = viewModel::onAddedShown,
            onCooked = viewModel::onCooked,
        ),
    )
}

/** `design/source/MealDetail.dc.html` (#19): a planned meal's ingredients checked against the pantry. */
@Composable
fun MealDetailsContent(state: MealDetailsState, actions: MealDetailsActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val plan = state.plan
    val snackbar = remember { SnackbarHostState() }
    val added = state.addedToShopping?.let { pluralStringResource(R.plurals.meal_added_missing, it, it) }
    LaunchedEffect(added) {
        if (added == null) return@LaunchedEffect
        snackbar.showSnackbar(added)
        actions.onAddedShown()
    }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopBar(plan?.let { fullDay(it.day) }.orEmpty(), actions) },
        bottomBar = { if (plan != null) Footer(cooked = plan.cooked, canAdd = state.checks.any { it.toBuy }, actions = actions) },
    ) { insets ->
        if (plan == null) return@Scaffold
        Column(
            Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PantryDimens.space5),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                Text(plan.meal.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.meal_servings), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted, modifier = Modifier.weight(1f))
                    QuantityStepper(
                        plan.servings.toString(),
                        actions.onServings,
                        Modifier.width(PantryDimens.servingsStepperWidth),
                        stringResource(R.string.meal_fewer),
                        stringResource(R.string.meal_more_servings),
                    )
                }
            }
            if (state.checks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    SectionHeading(stringResource(R.string.meal_ingredients))
                    PantryCard {
                        Column {
                            state.checks.forEachIndexed { index, check ->
                                if (index > 0) HorizontalDivider(color = colors.divider)
                                // A typed-in ingredient has no unit to count an amount in.
                                CheckRow(check, state.measurements, onClick = if (check.ingredient.fromPantry) ({ editing = index }) else null)
                            }
                        }
                    }
                }
            }
            state.checks.firstNotNullOfOrNull { it.status as? IngredientStatus.Expired }?.let { expired ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.warningContainer, MaterialTheme.shapes.large)
                        .padding(horizontal = PantryDimens.space4, vertical = PantryDimens.spaceInset),
                    verticalArrangement = Arrangement.spacedBy(PantryDimens.space0),
                ) {
                    val days = expired.daysAgo.toInt()
                    Text(pluralStringResource(R.plurals.meal_expired, days, days, expired.item.name), style = MaterialTheme.typography.titleMedium, color = colors.onWarningContainer)
                    Text(stringResource(R.string.meal_expired_body), style = MaterialTheme.typography.bodySmall, color = colors.onWarningContainer)
                }
            }
        }
    }
    editing?.let { index ->
        val check = state.checks.getOrNull(index)
        val unit = check?.ingredient?.unit
        if (check == null || unit == null || plan == null) {
            editing = null
        } else {
            AmountDialog(
                title = stringResource(R.string.meal_amount_title, check.ingredient.name),
                hint = pluralStringResource(R.plurals.meal_amount_hint, plan.servings, plan.servings),
                current = check.neededAmount,
                unit = unit,
                system = state.measurements,
                onDone = { amount ->
                    actions.onAmount(index, amount)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun TopBar(date: String, actions: MealDetailsActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PantryDimens.space2, vertical = PantryDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space1),
    ) {
        IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.meal_back)) }
        Text(date, style = MaterialTheme.typography.bodySmall, color = PantryTheme.colors.inkMuted, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.meal_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = PantryTheme.colors.surface) {
                DropdownMenuItem(text = { Text(stringResource(R.string.meal_edit)) }, onClick = { menu = false; actions.onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.meal_unplan)) }, onClick = { menu = false; actions.onUnplan() })
            }
        }
    }
}

@Composable
private fun CheckRow(check: IngredientCheck, measurements: MeasureSystem, onClick: (() -> Unit)?) {
    val colors = PantryTheme.colors
    val status = check.status
    val (dot, statusColor, strong) = when (status) {
        is IngredientStatus.Have -> Triple(colors.accent, colors.inkMuted, false)
        is IngredientStatus.Expired -> Triple(colors.danger, colors.danger, true)
        is IngredientStatus.Short, IngredientStatus.Missing -> Triple(colors.borderStrong, colors.warning, true)
        // Neither a match nor a shortage (#55): a plain dot, and the status line says why.
        is IngredientStatus.CantCompare -> Triple(colors.border, colors.inkMuted, false)
        IngredientStatus.NotTracked -> Triple(Color.Transparent, colors.inkMuted, false)
    }
    // In another unit than the item's, the needed amount also shows in the item's terms; "≈" through a density (#57).
    val amount = check.neededAmount?.let { quantityText(it) }
    val needed = amount?.let { text -> check.neededFigure(measurements)?.let { stringResource(R.string.amount_converted, text, shownText(it)) } ?: text }
    val label = stringResource(R.string.meal_set_amount, check.ingredient.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.tapMin)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier)
            .padding(horizontal = PantryDimens.spaceInset, vertical = PantryDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        Box(Modifier.size(PantryDimens.statusDot).background(dot, MaterialTheme.shapes.extraLarge))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
            Text(check.ingredient.name, style = MaterialTheme.typography.titleMedium)
            Text(
                statusText(check, needed),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                color = statusColor,
            )
        }
        Text(amount ?: stringResource(R.string.meal_some), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** "18 ea in Fridge · uses 12 ea", "1 tub in Dry · can’t compare with 1 tbsp", "Expired yesterday", "Not in pantry", "Not tracked". */
@Composable
private fun statusText(check: IngredientCheck, needed: String?): String = when (val status = check.status) {
    is IngredientStatus.Have -> {
        val have = quantityText(status.item.quantity)
        val where = locationName(status.item.location)
        if (needed == null) stringResource(R.string.meal_have, have, where) else stringResource(R.string.meal_have_uses, have, where, needed)
    }
    is IngredientStatus.Short -> stringResource(R.string.meal_short, quantityText(status.item.quantity), locationName(status.item.location), needed.orEmpty())
    is IngredientStatus.CantCompare ->
        stringResource(R.string.meal_cant_compare, quantityText(status.item.quantity), locationName(status.item.location), needed.orEmpty())
    is IngredientStatus.Expired -> pluralStringResource(R.plurals.expiry_days_ago, status.daysAgo.toInt(), status.daysAgo)
    IngredientStatus.Missing -> stringResource(R.string.meal_not_in_pantry)
    IngredientStatus.NotTracked -> stringResource(R.string.meal_not_tracked)
}

@Composable
private fun Footer(cooked: Boolean, canAdd: Boolean, actions: MealDetailsActions) {
    val colors = PantryTheme.colors
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space3),
        verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon),
    ) {
        if (canAdd && !cooked) {
            OutlinedButton(
                onClick = actions.onAddMissing,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(PantryDimens.hairline, colors.borderStrong),
                modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
            ) { Text(stringResource(R.string.meal_add_missing), style = MaterialTheme.typography.labelLarge, color = colors.ink) }
        }
        Button(
            onClick = actions.onCooked,
            enabled = !cooked,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                disabledContainerColor = colors.surfaceMuted,
                disabledContentColor = colors.inkMuted,
            ),
            modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
        ) {
            Text(stringResource(if (cooked) R.string.meal_already_cooked else R.string.meal_cooked_it), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Previews and the screenshot test use made-up items, marked as samples.
// A cup of flour from a bag of known weight shows what it comes to, through a typical density (#57).
private val previewFlour = PantryItem(
    id = 5, name = "Sample flour", location = Location.Dry, purchased = previewToday,
    quantity = Packaged(BigDecimal.ONE, Package.Bag, Measure(BigDecimal(5), MeasureUnit.Pound)),
)
private val previewMealPlan = previewEggBites.copy(
    meal = previewEggBites.meal.copy(
        ingredients = previewEggBites.meal.ingredients +
            Ingredient.of(previewFlour).copy(unit = AmountUnit.Measured(MeasureUnit.Cup), amount = BigDecimal.ONE),
    ),
)
internal val previewMealDetails = MealDetailsState(
    today = previewToday,
    loading = false,
    plan = previewMealPlan,
    checks = checkPlan(previewMealPlan, listOf(previewEggs, previewSpinach, previewYogurt, previewFlour), previewToday),
)

@Preview
@Composable
private fun MealDetailsPreview() = PantryTheme(darkTheme = false) { MealDetailsContent(previewMealDetails, MealDetailsActions()) }
