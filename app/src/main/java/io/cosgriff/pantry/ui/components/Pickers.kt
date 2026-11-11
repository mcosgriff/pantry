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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Form pieces shared by Item details (#7) and the Scan sheet (#10).

/** A small muted label over a field. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = PantryTheme.colors.inkMuted)
}

/** A barcode as printed on the pack: a 12-digit UPC-A grouped "UPC 0 96619 32108 4", others as they are. */
@Composable
fun barcodeText(barcode: String): String =
    if (barcode.length == 12 && barcode.all(Char::isDigit)) {
        stringResource(R.string.details_upc, "${barcode[0]} ${barcode.substring(1, 6)} ${barcode.substring(6, 11)} ${barcode[11]}")
    } else {
        barcode
    }

/** One button per location, three to a row; the chosen one in its location's colors. [label] heads it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationPicker(selected: Location, locations: List<Location>, onSelect: (Location) -> Unit, label: String = stringResource(R.string.details_location)) {
    val colors = PantryTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
        FieldLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space2),
            maxItemsInEachRow = 3,
        ) {
            locations.forEach { location ->
                val on = location == selected
                val (container, content) = locationColors(location)
                val shape = MaterialTheme.shapes.small
                Box(
                    Modifier
                        .weight(1f)
                        .height(PantryDimens.tapMin)
                        .background(if (on) container else colors.surface, shape)
                        .border(if (on) 2.dp else 1.dp, if (on) content else colors.border, shape)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(location) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        locationName(location),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) content else colors.ink,
                    )
                }
            }
        }
    }
}

/** The bordered − [value] + field. */
@Composable
fun QuantityStepper(value: String, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .height(PantryDimens.fieldHeight)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = PantryDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", stringResource(R.string.details_decrease)) { onStep(-1) }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        StepButton("+", stringResource(R.string.details_increase)) { onStep(1) }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(PantryDimens.tapMin)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = PantryTheme.colors.ink)
    }
}

/** Material's date picker in a dialog, starting on [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC milliseconds at midnight.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text(stringResource(R.string.details_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_cancel)) } },
    ) {
        DatePicker(state = picker)
    }
}
