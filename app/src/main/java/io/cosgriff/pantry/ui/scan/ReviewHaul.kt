package io.cosgriff.pantry.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.HaulEntry
import io.cosgriff.pantry.domain.haulGroups
import io.cosgriff.pantry.domain.needingName
import io.cosgriff.pantry.ui.components.LocationTile
import io.cosgriff.pantry.ui.components.barcodeText
import io.cosgriff.pantry.ui.components.groupedRow
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * `design/source/ScanReview.dc.html` (#14): everything batch-scanned, by location, with the unnamed ones flagged.
 * Tapping a row names it (or takes it out of the haul); "Add N to pantry" waits until every item has a name.
 */
@Composable
internal fun ReviewHaulContent(state: ScanState, actions: ScanActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val groups = haulGroups(state.haul, state.locations)
    val missing = needingName(state.haul)
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.padding(PantryDimens.space2), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onScanMore) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.review_back)) }
            Column(Modifier.padding(start = PantryDimens.space1)) {
                Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(pluralStringResource(R.plurals.review_scanned, state.haul.size, state.haul.size), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PantryDimens.space5, vertical = PantryDimens.space1)) {
            if (missing > 0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .background(colors.warningContainer, MaterialTheme.shapes.large)
                            .padding(start = PantryDimens.space4, end = PantryDimens.space2, top = PantryDimens.space1, bottom = PantryDimens.space1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(R.plurals.review_need_name, missing, missing),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onWarningContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            state.haul.indexOfFirst { it.name.isBlank() }.takeIf { it >= 0 }?.let { editing = it }
                        }) {
                            Text(stringResource(R.string.review_fix), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.onWarningContainer)
                        }
                    }
                }
            }
            groups.forEach { (location, entries) ->
                item(key = "head-$location") {
                    Row(
                        Modifier.padding(top = PantryDimens.space2, bottom = PantryDimens.space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2),
                    ) {
                        LocationTile(location, size = 24.dp)
                        Text(
                            stringResource(R.string.review_group, locationName(location), entries.size).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
                entries.forEachIndexed { i, entry ->
                    item(key = "item-${entry.index}") {
                        HaulRow(entry, Modifier.groupedRow(i, entries.size, 18.dp, colors.surface, colors.border, colors.divider)) { editing = entry.index }
                    }
                }
                item(key = "gap-$location") { Row(Modifier.height(PantryDimens.space3)) {} }
            }
        }
        Row(
            Modifier.padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space3, bottom = PantryDimens.space4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = actions.onScanMore,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                modifier = Modifier.weight(1f).height(PantryDimens.buttonHeight),
            ) { Text(stringResource(R.string.review_scan_more), style = MaterialTheme.typography.labelLarge) }
            Button(
                onClick = actions.onAddAll,
                enabled = state.haul.isNotEmpty() && missing == 0,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.weight(1.6f).height(PantryDimens.buttonHeight),
            ) { Text(pluralStringResource(R.plurals.review_add_all, state.haul.size, state.haul.size), style = MaterialTheme.typography.labelLarge) }
        }
    }
    editing?.let { index ->
        val draft = state.haul.getOrNull(index)
        if (draft == null) {
            editing = null
        } else {
            NameDialog(
                initial = draft.name,
                onSave = { actions.onRename(index, it); editing = null },
                onRemove = { actions.onRemoveFromHaul(index); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun HaulRow(entry: HaulEntry, modifier: Modifier, onClick: () -> Unit) {
    val colors = PantryTheme.colors
    val draft = entry.draft
    val unnamed = draft.name.isBlank()
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = PantryDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (unnamed) stringResource(R.string.review_needs_name) else draft.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unnamed) FontWeight.SemiBold else FontWeight.Medium,
                color = if (unnamed) colors.warning else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    unnamed && draft.barcode != null -> barcodeText(draft.barcode!!)
                    draft.expires != null -> stringResource(R.string.review_expires, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(draft.expires))
                    else -> stringResource(R.string.details_no_date)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        Text(
            quantityText(draft.quantity),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 10.dp, vertical = PantryDimens.space1),
        )
    }
}

@Composable
private fun NameDialog(initial: String, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.details_name)) }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.review_remove), color = PantryTheme.colors.danger) }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ReviewHaulPreview() = PantryTheme(darkTheme = false) {
    val today = java.time.LocalDate.of(2026, 11, 10)
    ReviewHaulContent(
        ScanState(
            today = today,
            batch = true,
            reviewing = true,
            haul = listOf(
                io.cosgriff.pantry.domain.ItemDraft.new(today, io.cosgriff.pantry.domain.Location.Fridge, "096619321087").copy(name = "Sample eggs"),
                io.cosgriff.pantry.domain.ItemDraft.new(today, io.cosgriff.pantry.domain.Location.Fridge, "041220576913"),
                io.cosgriff.pantry.domain.ItemDraft.new(today, io.cosgriff.pantry.domain.Location.Dry).copy(name = "Sample rice"),
            ),
        ),
        ScanActions(),
    )
}
