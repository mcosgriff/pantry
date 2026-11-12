package io.cosgriff.pantry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Amount
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureSystem
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.UnitGroup
import io.cosgriff.pantry.domain.contentsUnits
import io.cosgriff.pantry.domain.countedIn
import io.cosgriff.pantry.domain.number
import io.cosgriff.pantry.domain.unitChoices
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParsePosition

// The unit picker (#57), shared by Item details, the scan sheet, Review haul, the meal screens and recipe import.

/**
 * The bordered field showing [unit] ("lb", "bag × 5 lb"), labelled [label] for TalkBack; tapping it opens
 * [UnitDialog]. [contents] lets a package take what one holds.
 */
@Composable
fun UnitField(
    unit: AmountUnit,
    system: MeasureSystem,
    onPick: (AmountUnit) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.units_title),
    contents: Boolean = true,
) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.small
    var open by rememberSaveable { mutableStateOf(false) }
    val name = unitName(unit)
    val described = stringResource(R.string.units_field, label, name)
    Box(
        modifier
            .fillMaxWidth()
            .height(PantryDimens.fieldHeight)
            .background(colors.surface, shape)
            .border(PantryDimens.hairline, colors.border, shape)
            .clickable { open = true }
            .clearAndSetSemantics {
                contentDescription = described
                role = Role.DropdownList
            }
            .padding(horizontal = PantryDimens.space3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
    if (open) {
        UnitDialog(unit, system, onPick = { open = false; onPick(it) }, onDismiss = { open = false }, contents = contents)
    }
}

/**
 * The unit picker (#57): every unit and package, grouped by count, volume, weight and package, [system]'s units
 * first. A package can take what one holds ("5 lb") when [contents]; it is optional. Done picks.
 */
@Composable
fun UnitDialog(
    current: AmountUnit?,
    system: MeasureSystem,
    onPick: (AmountUnit) -> Unit,
    onDismiss: () -> Unit,
    contents: Boolean = true,
) {
    val format = remember { amountFormat() }
    var selected by remember { mutableStateOf(current?.let(::withoutContents)) }
    val held = (current as? AmountUnit.Packages)?.contents
    var contentsText by rememberSaveable { mutableStateOf(held?.amount?.let(format::format).orEmpty()) }
    var contentsUnit by remember { mutableStateOf(held?.unit ?: contentsUnits(system).first { it != MeasureUnit.Each && it != MeasureUnit.Dozen }) }
    val pkg = selected as? AmountUnit.Packages
    val parsed = parseDecimal(contentsText, format)?.takeIf { it.signum() > 0 }
    val contentsValid = contentsText.isBlank() || parsed != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.units_title)) },
        text = {
            UnitPickerBody(
                system = system,
                selected = selected,
                onSelect = { selected = it },
                contentsOf = pkg?.takeIf { contents },
                contentsText = contentsText,
                onContentsText = { contentsText = it },
                contentsUnit = contentsUnit,
                onContentsUnit = { contentsUnit = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = selected ?: return@TextButton
                    onPick(if (chosen is AmountUnit.Packages && contents) chosen.copy(contents = parsed?.let { Measure(it, contentsUnit) }) else chosen)
                },
                enabled = selected != null && (pkg == null || !contents || contentsValid),
            ) { Text(stringResource(R.string.units_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_cancel)) } },
    )
}

/**
 * The unit picker's choices, and what one package holds when [contentsOf] is the package picked. The screenshot
 * test draws it on its own: a text field in a dialog never settles there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitPickerBody(
    system: MeasureSystem,
    selected: AmountUnit?,
    onSelect: (AmountUnit) -> Unit,
    contentsOf: AmountUnit.Packages?,
    contentsText: String,
    onContentsText: (String) -> Unit,
    contentsUnit: MeasureUnit,
    onContentsUnit: (MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The units scroll; what a package holds stays in view under them.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space3),
        ) {
            unitChoices(system).forEach { choices ->
                Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
                    Text(
                        unitGroupName(choices.group),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PantryTheme.colors.inkMuted,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                        choices.units.forEach { unit -> UnitChip(unitName(unit), unit == selected) { onSelect(unit) } }
                    }
                }
            }
        }
        if (contentsOf != null) {
            ContentsRow(
                pkgName = unitName(contentsOf),
                text = contentsText,
                onText = onContentsText,
                unit = contentsUnit,
                onUnit = onContentsUnit,
                system = system,
            )
        }
    }
}

/** What one package holds: an optional amount and its unit, "One bag holds 5 lb". */
@Composable
private fun ContentsRow(
    pkgName: String,
    text: String,
    onText: (String) -> Unit,
    unit: MeasureUnit,
    onUnit: (MeasureUnit) -> Unit,
    system: MeasureSystem,
) {
    var open by remember { mutableStateOf(false) }
    val unitText = unitName(AmountUnit.Measured(unit))
    val unitLabel = stringResource(R.string.units_contents_unit, pkgName, unitText)
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
        Text(
            stringResource(R.string.units_contents, pkgName),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = PantryTheme.colors.inkMuted,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
            // A plain bordered field: Material's outlined one never settles in the screenshot test's dialog.
            TextFieldBox(
                text,
                onText,
                stringResource(R.string.units_contents_amount),
                stringResource(R.string.units_contents_amount),
                Modifier.weight(1f),
                KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Box {
                val shape = MaterialTheme.shapes.small
                Box(
                    Modifier
                        .widthIn(min = PantryDimens.unitButtonMinWidth)
                        .height(PantryDimens.fieldHeight)
                        .border(PantryDimens.hairline, PantryTheme.colors.border, shape)
                        .clickable { open = true }
                        .clearAndSetSemantics {
                            contentDescription = unitLabel
                            role = Role.DropdownList
                        }
                        .padding(horizontal = PantryDimens.space3),
                    contentAlignment = Alignment.Center,
                ) { Text(unitText, style = MaterialTheme.typography.bodyLarge) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    contentsUnits(system).forEach { option ->
                        DropdownMenuItem(text = { Text(unitName(AmountUnit.Measured(option))) }, onClick = { open = false; onUnit(option) })
                    }
                }
            }
        }
        Text(stringResource(R.string.units_contents_hint), style = MaterialTheme.typography.bodySmall, color = PantryTheme.colors.inkMuted)
    }
}

/** A choice in the picker: a chip drawn at the chip height inside a 48dp touch target. */
@Composable
private fun UnitChip(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(PantryDimens.chipHeight)
                .background(if (on) colors.surfaceMuted else colors.surface, shape)
                .border(if (on) PantryDimens.borderSelected else PantryDimens.hairline, if (on) colors.accent else colors.border, shape)
                .padding(horizontal = PantryDimens.spaceInset),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

/**
 * How much of something, and in what unit: a number and a [UnitField]. Empty means "some" ([onDone] gets
 * `null`). Plan a meal and Meal details use it for an ingredient's amount, in any unit (#57).
 */
@Composable
fun AmountDialog(
    title: String,
    hint: String,
    current: Amount?,
    unit: AmountUnit,
    system: MeasureSystem,
    onDone: (Amount?) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = remember { amountFormat() }
    var text by rememberSaveable { mutableStateOf(current?.number?.let(format::format).orEmpty()) }
    var chosen by remember { mutableStateOf(current?.countedIn ?: unit) }
    val parsed = parseDecimal(text, format)?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.units_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = text.isNotBlank() && parsed == null,
                        modifier = Modifier.weight(1f),
                    )
                    UnitField(chosen, system, onPick = { chosen = it }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(parsed?.let(chosen::of)) }, enabled = text.isBlank() || parsed != null) { Text(stringResource(R.string.details_ok)) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.meal_amount_clear)) } },
    )
}

private fun withoutContents(unit: AmountUnit): AmountUnit = if (unit is AmountUnit.Packages) unit.copy(contents = null) else unit

/** Numbers as the user's locale writes them, to 3 decimals. */
internal fun amountFormat(): NumberFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }

/** A non-negative number typed in the user's locale ("1,5" in German), or `null` if it isn't one. */
internal fun parseDecimal(text: String, format: NumberFormat): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val number = format.parse(trimmed, position) ?: return null
    if (position.index != trimmed.length) return null
    return BigDecimal(number.toString()).takeIf { it.signum() >= 0 }
}

@Composable
private fun unitGroupName(group: UnitGroup): String = stringResource(
    when (group) {
        UnitGroup.Count -> R.string.units_group_count
        UnitGroup.Volume -> R.string.units_group_volume
        UnitGroup.Weight -> R.string.units_group_weight
        UnitGroup.Package -> R.string.units_group_package
    },
)
