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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.LineMatch
import io.cosgriff.pantry.domain.LineUnit
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureSystem
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.FieldLabel
import io.cosgriff.pantry.ui.components.QuantityStepper
import io.cosgriff.pantry.ui.components.SectionHeading
import io.cosgriff.pantry.ui.components.TextFieldBox
import io.cosgriff.pantry.ui.components.UnitDialog
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.unitName
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal

data class ImportRecipeActions(
    val onBack: () -> Unit = {},
    val onLink: (String) -> Unit = {},
    val onRead: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onServings: (Int) -> Unit = {},
    val onAmount: (Int, String) -> Unit = { _, _ -> },
    val onUnit: (Int, LineUnit) -> Unit = { _, _ -> },
    val onLineName: (Int, String) -> Unit = { _, _ -> },
    val onMatch: (Int, PantryItem) -> Unit = { _, _ -> },
    val onNotTracked: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
)

/** Imports a recipe from [link] (shared from the browser, or empty to paste one); [onDone] once it's saved. */
@Composable
fun ImportRecipeScreen(link: String, onBack: () -> Unit, onDone: () -> Unit, viewModel: ImportRecipeViewModel = hiltViewModel()) {
    LaunchedEffect(link) { viewModel.start(link) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ImportRecipeContent(
        state,
        ImportRecipeActions(
            onBack, viewModel::onLink, viewModel::onRead, viewModel::onName, viewModel::onServings, viewModel::onAmount,
            viewModel::onUnit, viewModel::onLineName, viewModel::onMatch, viewModel::onNotTracked, viewModel::onSave,
        ),
    )
}

/**
 * Importing a recipe from a link (#43, `docs/adr/0003`), which has no board of its own: the link and what
 * reading it sends, then each ingredient line as read, with its pantry match to confirm. Built from Plan a
 * meal's pieces.
 */
@Composable
fun ImportRecipeContent(state: ImportRecipeState, actions: ImportRecipeActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        topBar = { BackTopBar(stringResource(R.string.import_title), stringResource(R.string.import_back), actions.onBack, Modifier.statusBarsPadding()) },
        bottomBar = {
            val enabled = if (state.reviewing) state.name.isNotBlank() else state.link.isNotBlank() && !state.reading
            Button(
                onClick = if (state.reviewing) actions.onSave else actions.onRead,
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
                // Disabled: still legible, as on Plan a meal.
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.inkMuted,
                ),
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space3)
                    .fillMaxWidth()
                    .height(PantryDimens.buttonHeight),
            ) {
                Text(
                    stringResource(
                        when {
                            state.reviewing -> R.string.import_save
                            state.reading -> R.string.import_reading
                            else -> R.string.import_read
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { insets ->
        if (state.reviewing) {
            Review(state, actions, PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + PantryDimens.space4))
        } else {
            LinkEntry(state, actions, Modifier.padding(insets))
        }
    }
}

@Composable
private fun LinkEntry(state: ImportRecipeState, actions: ImportRecipeActions, modifier: Modifier) {
    val colors = PantryTheme.colors
    Column(modifier.padding(horizontal = PantryDimens.space5), verticalArrangement = Arrangement.spacedBy(PantryDimens.space4)) {
        Text(stringResource(R.string.import_explain), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
        Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
            val label = stringResource(R.string.import_link)
            FieldLabel(label)
            TextFieldBox(state.link, actions.onLink, label, stringResource(R.string.import_link_hint))
        }
        state.problem?.let { problem ->
            Text(
                stringResource(
                    when (problem) {
                        ImportProblem.NotALink -> R.string.import_not_a_link
                        ImportProblem.NoRecipe -> R.string.import_no_recipe
                        ImportProblem.Failed -> R.string.import_failed
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun Review(state: ImportRecipeState, actions: ImportRecipeActions, padding: PaddingValues) {
    val colors = PantryTheme.colors
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
        item {
            Column(Modifier.padding(horizontal = PantryDimens.space5), verticalArrangement = Arrangement.spacedBy(PantryDimens.space4)) {
                Text(
                    stringResource(R.string.import_from, state.link.trim()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
                    val label = stringResource(R.string.import_name)
                    FieldLabel(label)
                    TextFieldBox(state.name, actions.onName, label, stringResource(R.string.import_name_hint))
                }
                Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
                    FieldLabel(stringResource(R.string.import_servings))
                    QuantityStepper(state.servings.toString(), actions.onServings, decreaseLabel = stringResource(R.string.meal_fewer), increaseLabel = stringResource(R.string.meal_more_servings))
                }
                Column(Modifier.padding(top = PantryDimens.space2), verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
                    SectionHeading(stringResource(R.string.import_ingredients))
                    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2), verticalAlignment = Alignment.CenterVertically) {
                        val toCheck = state.toCheck > 0
                        Text(
                            if (toCheck) pluralStringResource(R.plurals.import_to_check, state.toCheck, state.toCheck) else stringResource(R.string.import_all_matched),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (toCheck) colors.onWarningContainer else colors.ink,
                            modifier = Modifier
                                .background(if (toCheck) colors.warningContainer else colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = PantryDimens.space2, vertical = PantryDimens.space0),
                        )
                    }
                    Text(stringResource(R.string.import_remembered), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
        }
        itemsIndexed(state.lines) { index, line ->
            LineCard(index, line, state.pantry, state.measurements, actions, Modifier.padding(horizontal = PantryDimens.space5))
        }
    }
}

@Composable
private fun LineCard(index: Int, line: ImportLine, pantry: List<PantryItem>, measurements: MeasureSystem, actions: ImportRecipeActions, modifier: Modifier) {
    val colors = PantryTheme.colors
    val number = index + 1
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.large)
            .border(if (line.toCheck) PantryDimens.borderSelected else PantryDimens.hairline, if (line.toCheck) colors.warningContainerStrong else colors.border, MaterialTheme.shapes.large)
            .padding(PantryDimens.space3),
        verticalArrangement = Arrangement.spacedBy(PantryDimens.space2),
    ) {
        Text(line.text, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2), verticalAlignment = Alignment.CenterVertically) {
            TextFieldBox(
                line.amountText,
                { actions.onAmount(index, it) },
                stringResource(R.string.import_amount_label, number),
                stringResource(R.string.meal_some),
                Modifier.width(PantryDimens.amountFieldWidth),
            )
            UnitButton(number, line, measurements) { actions.onUnit(index, it) }
            TextFieldBox(
                line.name,
                { actions.onLineName(index, it) },
                stringResource(R.string.import_name_label, number),
                stringResource(R.string.import_ingredient_hint),
                Modifier.weight(1f),
            )
        }
        if (line.unknownUnit) {
            Text(
                stringResource(R.string.import_unit_ask, line.unitWord.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.warning,
            )
        }
        MatchButton(number, line, pantry, onMatch = { actions.onMatch(index, it) }, onNotTracked = { actions.onNotTracked(index) })
    }
}

/**
 * The line's unit, or the unknown word with "?"; tapping it opens the shared unit picker (#57). What a package
 * holds comes from the recipe's line, so it isn't asked here.
 */
@Composable
private fun UnitButton(number: Int, line: ImportLine, measurements: MeasureSystem, onPick: (LineUnit) -> Unit) {
    val colors = PantryTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    val unknown = line.unknownUnit
    val text = when {
        line.unit != null -> unitName(amountUnit(line.unit))
        line.unitWord != null -> stringResource(R.string.import_unit_unknown, line.unitWord)
        else -> stringResource(R.string.import_unit_none)
    }
    val label = stringResource(R.string.import_unit_label, number, text)
    Box {
        Box(
            Modifier
                .height(PantryDimens.fieldHeight)
                .widthIn(min = PantryDimens.unitButtonMinWidth)
                .background(if (unknown) colors.warningContainer else colors.surface, shape)
                .border(PantryDimens.hairline, if (unknown) colors.warningContainerStrong else colors.border, shape)
                .clickable(role = Role.DropdownList) { open = true }
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.DropdownList
                }
                .padding(horizontal = PantryDimens.space3),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (unknown) FontWeight.SemiBold else FontWeight.Normal, color = if (unknown) colors.onWarningContainer else colors.ink)
        }
    }
    if (open) {
        UnitDialog(
            line.unit?.let(::amountUnit),
            measurements,
            onPick = { picked ->
                open = false
                onPick(
                    when (picked) {
                        is AmountUnit.Measured -> LineUnit.Of(picked.unit)
                        is AmountUnit.Packages -> LineUnit.In(picked.pkg)
                    },
                )
            },
            onDismiss = { open = false },
            contents = false,
        )
    }
}

private fun amountUnit(unit: LineUnit): AmountUnit = when (unit) {
    is LineUnit.Of -> AmountUnit.Measured(unit.unit)
    is LineUnit.In -> AmountUnit.Packages(unit.pkg)
}

/** The line's pantry match, "Not tracked", or a prompt to pick one; tapping it lists the pantry. */
@Composable
private fun MatchButton(number: Int, line: ImportLine, pantry: List<PantryItem>, onMatch: (PantryItem) -> Unit, onNotTracked: () -> Unit) {
    val colors = PantryTheme.colors
    var open by remember { mutableStateOf(false) }
    val match = line.match
    val text = when (match) {
        is LineMatch.Item -> stringResource(R.string.import_match_item, match.item.name, locationName(match.item.location))
        is LineMatch.NotTracked -> stringResource(R.string.plan_not_tracked)
        LineMatch.None -> stringResource(R.string.import_match_none)
    }
    val none = match == LineMatch.None
    val label = stringResource(R.string.import_match_label, number, text)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PantryDimens.tapMin)
                .background(if (none) colors.warningContainer else colors.surfaceMuted, MaterialTheme.shapes.small)
                .clickable(role = Role.DropdownList) { open = true }
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.DropdownList
                }
                .padding(horizontal = PantryDimens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (none) FontWeight.SemiBold else FontWeight.Medium,
                color = if (none) colors.onWarningContainer else colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.import_change), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (none) colors.onWarningContainer else colors.accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.plan_not_tracked)) }, onClick = {
                open = false
                onNotTracked()
            })
            pantry.forEach { item ->
                DropdownMenuItem(text = { Text(stringResource(R.string.import_match_item, item.name, locationName(item.location))) }, onClick = {
                    open = false
                    onMatch(item)
                })
            }
        }
    }
}

// Previews and the screenshot test use a made-up recipe and link, marked as samples.
internal val previewImportLink = ImportRecipeState(today = previewToday, link = "https://recipes.example/sample-black-bean-soup")

internal val previewImportNoRecipe = previewImportLink.copy(problem = ImportProblem.NoRecipe)

internal val previewImportReview = previewImportLink.copy(
    name = "Sample black bean soup",
    servings = 4,
    pantry = listOf(previewSpinach, previewEggs, previewChicken, previewYogurt),
    lines = listOf(
        ImportLine(
            "1 (15 oz) can sample black beans, drained", "1", "can", LineUnit.In(Package.Can), "sample black beans", LineMatch.None,
            contents = Measure(BigDecimal(15), MeasureUnit.Ounce),
        ),
        ImportLine("3 sample eggs", "3", null, LineUnit.Of(MeasureUnit.Each), "sample eggs", LineMatch.Item(previewEggs)),
        ImportLine("2 cloves sample garlic, minced", "2", "cloves", null, "sample garlic", LineMatch.NotTracked(learned = true), askedUnit = true, decided = true),
        ImportLine("Sample salt and pepper to taste", "", null, null, "Sample salt and pepper", LineMatch.NotTracked(learned = true), decided = true),
    ),
)

@Preview
@Composable
private fun ImportRecipePreview() = PantryTheme(darkTheme = false) { ImportRecipeContent(previewImportReview, ImportRecipeActions()) }
