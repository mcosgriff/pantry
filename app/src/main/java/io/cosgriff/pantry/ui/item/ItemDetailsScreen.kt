package io.cosgriff.pantry.ui.item

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.QuickPick
import io.cosgriff.pantry.domain.expiryStatus
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.DateDialog
import io.cosgriff.pantry.ui.components.FieldLabel
import io.cosgriff.pantry.ui.components.LocationPicker
import io.cosgriff.pantry.ui.components.QuantityStepper
import io.cosgriff.pantry.ui.components.barcodeText
import io.cosgriff.pantry.ui.components.CheckRow
import io.cosgriff.pantry.ui.components.PantryCard
import io.cosgriff.pantry.ui.components.ValueRow
import io.cosgriff.pantry.ui.components.locationColors
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.components.unitName
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything Item details can be told. */
data class ItemDetailsActions(
    val onBack: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onLocation: (Location) -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onUnit: (QuantityUnit) -> Unit = {},
    val onExpires: (LocalDate?) -> Unit = {},
    val onQuickPick: (QuickPick) -> Unit = {},
    val onOpened: (Boolean) -> Unit = {},
    val onPurchased: (LocalDate) -> Unit = {},
    val onLowStock: (BigDecimal?) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

/**
 * Item details for item [id], or a new item with [name] and [barcode] (empty for none) when it is `null`. Calls
 * [onDone] once it is saved or removed.
 */
@Composable
fun ItemDetailsScreen(
    id: Long?,
    name: String,
    barcode: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ItemDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id, name, barcode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ItemDetailsContent(
        state = state,
        actions = ItemDetailsActions(
            onBack = onBack,
            onName = viewModel::onName,
            onLocation = viewModel::onLocation,
            onStep = viewModel::onStep,
            onUnit = viewModel::onUnit,
            onExpires = viewModel::onExpires,
            onQuickPick = viewModel::onQuickPick,
            onOpened = viewModel::onOpened,
            onPurchased = viewModel::onPurchased,
            onLowStock = viewModel::onLowStock,
            onNotes = viewModel::onNotes,
            onSave = viewModel::onSave,
            onRemove = viewModel::onRemove,
        ),
    )
}

/** `design/source/ItemEdit.dc.html` (#7). Also adds an item by hand. */
@Composable
fun ItemDetailsContent(state: ItemDetailsState, actions: ItemDetailsActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = PantryTheme.colors.ground,
        topBar = {
            BackTopBar(
                title = stringResource(if (draft?.isNew == true) R.string.details_title_new else R.string.details_title),
                backLabel = stringResource(R.string.details_back),
                onBack = actions.onBack,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            if (draft != null) Footer(draft, onRemove = { confirmRemove = true }, onSave = actions.onSave)
        },
    ) { insets ->
        if (draft != null) {
            Column(
                Modifier
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space1),
                verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceInset),
            ) {
                NameCard(draft, actions.onName)
                LocationPicker(draft.location, state.locations, actions.onLocation)
                QuantityRow(draft.quantity, actions.onStep, actions.onUnit)
                ExpiryCard(draft, state.today, actions)
                MoreCard(draft, actions)
                Spacer(Modifier.height(PantryDimens.space3))
            }
        }
    }
    if (confirmRemove && draft != null) {
        RemoveDialog(draft, onConfirm = { confirmRemove = false; actions.onRemove() }, onDismiss = { confirmRemove = false })
    }
}

@Composable
private fun NameCard(draft: ItemDraft, onName: (String) -> Unit) {
    val colors = PantryTheme.colors
    val nameLabel = stringResource(R.string.details_name)
    PantryCard {
        Row(Modifier.padding(PantryDimens.spaceInset), horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceInset), verticalAlignment = Alignment.CenterVertically) {
            // The photo is a placeholder until items can have one.
            Box(
                Modifier
                    .size(PantryDimens.photoSize)
                    .background(colors.surfaceMuted, MaterialTheme.shapes.medium)
                    .border(PantryDimens.hairline, colors.borderStrong, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.details_photo), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = colors.inkMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
                BasicTextField(
                    value = draft.name,
                    onValueChange = onName,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PantryDimens.tapMin)
                        .semantics { contentDescription = nameLabel },
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.name.isEmpty()) Text(nameLabel, style = MaterialTheme.typography.titleLarge, color = colors.inkMuted)
                            field()
                        }
                    },
                )
                draft.barcode?.let { Text(barcodeText(it), style = PantryTheme.text.barcode, color = colors.inkMuted) }
            }
        }
    }
}

@Composable
private fun QuantityRow(quantity: Quantity, onStep: (Int) -> Unit, onUnit: (QuantityUnit) -> Unit) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.small
    val field = Modifier
        .fillMaxWidth()
        .height(PantryDimens.fieldHeight)
        .background(colors.surface, shape)
        .border(PantryDimens.hairline, colors.border, shape)
    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
            FieldLabel(stringResource(R.string.details_quantity))
            QuantityStepper(NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(quantity.amount), onStep)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
            FieldLabel(stringResource(R.string.details_unit))
            var open by remember { mutableStateOf(false) }
            Box {
                Box(
                    field
                        .clickable(role = Role.DropdownList) { open = true }
                        .padding(horizontal = PantryDimens.space3),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(unitName(quantity.unit), style = MaterialTheme.typography.bodyLarge)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    QuantityUnit.entries.forEach { unit ->
                        DropdownMenuItem(text = { Text(unitName(unit)) }, onClick = { open = false; onUnit(unit) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpiryCard(draft: ItemDraft, today: LocalDate, actions: ItemDetailsActions) {
    val colors = PantryTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    PantryCard {
        Column(Modifier.padding(PantryDimens.spaceInset), verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { FieldLabel(stringResource(R.string.details_expires)) }
                ExpiryBadge(expiryStatus(draft.expires, today))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PantryDimens.tapMin)
                    .clickable(onClickLabel = stringResource(R.string.details_pick_date)) { picking = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon),
            ) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(PantryDimens.iconMedium))
                Text(
                    draft.expires?.let { longDate(it) } ?: stringResource(R.string.details_no_date),
                    style = PantryTheme.text.dateDisplay,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip), verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceChip)) {
                QuickPick.entries.forEach { pick ->
                    val label = stringResource(
                        when (pick) {
                            QuickPick.ThreeDays -> R.string.details_plus_3_days
                            QuickPick.OneWeek -> R.string.details_plus_1_week
                            QuickPick.OneMonth -> R.string.details_plus_1_month
                            QuickPick.NoDate -> R.string.details_no_date
                        },
                    )
                    OutlinedButton(
                        onClick = { actions.onQuickPick(pick) },
                        shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(PantryDimens.hairline, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surfaceMuted, contentColor = colors.ink),
                        contentPadding = PaddingValues(horizontal = PantryDimens.space3),
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (picking) {
        DateDialog(initial = draft.expires ?: today, onPick = { picking = false; actions.onExpires(it) }, onDismiss = { picking = false })
    }
}

@Composable
private fun ExpiryBadge(status: ExpiryStatus) {
    val colors = PantryTheme.colors
    val (text, fg, bg) = when (status) {
        ExpiryStatus.NoDate -> return
        is ExpiryStatus.Expired -> Triple(pluralStringResource(R.plurals.details_days_ago, status.daysAgo.toInt(), status.daysAgo), colors.danger, colors.warningContainer)
        is ExpiryStatus.ExpiresSoon -> Triple(daysLeft(status.daysLeft), colors.warning, colors.warningContainer)
        is ExpiryStatus.Fine -> Triple(daysLeft(status.daysLeft), colors.inkMuted, colors.surfaceMuted)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
        color = fg,
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = PantryDimens.space2, vertical = PantryDimens.spaceBadge),
    )
}

@Composable
private fun daysLeft(days: Long): String =
    if (days == 0L) stringResource(R.string.details_today) else pluralStringResource(R.plurals.details_in_days, days.toInt(), days)

/** "Thu, Nov 12, 2026" in the user's locale. */
@Composable
private fun longDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy"), locale).format(date)
}

@Composable
private fun MoreCard(draft: ItemDraft, actions: ItemDetailsActions) {
    val colors = PantryTheme.colors
    var pickingPurchased by rememberSaveable { mutableStateOf(false) }
    var editingLowStock by rememberSaveable { mutableStateOf(false) }
    PantryCard {
        Column {
            CheckRow(stringResource(R.string.details_opened), draft.opened, actions.onOpened)
            HorizontalDivider(color = colors.divider)
            ValueRow(stringResource(R.string.details_purchased), DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(draft.purchased), onClick = { pickingPurchased = true })
            HorizontalDivider(color = colors.divider)
            ValueRow(
                stringResource(R.string.details_low_stock),
                draft.lowStockAt?.let { stringResource(R.string.details_low_stock_at, quantityText(Quantity(it, draft.quantity.unit))) }
                    ?: stringResource(R.string.details_low_stock_off),
                onClick = { editingLowStock = true },
            )
            HorizontalDivider(color = colors.divider)
            Column(Modifier.padding(start = PantryDimens.spaceInset, end = PantryDimens.spaceInset, top = PantryDimens.spaceIcon, bottom = PantryDimens.space3), verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
                val notesLabel = stringResource(R.string.details_notes)
                Text(notesLabel, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = draft.notes,
                    onValueChange = actions.onNotes,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = colors.ink, fontWeight = FontWeight.Normal),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PantryDimens.tapMin / 2)
                        .semantics { contentDescription = notesLabel },
                    decorationBox = { field ->
                        Box {
                            if (draft.notes.isEmpty()) {
                                Text(stringResource(R.string.details_notes_hint), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = colors.inkMuted)
                            }
                            field()
                        }
                    },
                )
            }
        }
    }
    if (pickingPurchased) {
        DateDialog(initial = draft.purchased, onPick = { pickingPurchased = false; it?.let(actions.onPurchased) }, onDismiss = { pickingPurchased = false })
    }
    if (editingLowStock) {
        LowStockDialog(draft.lowStockAt, onDone = { editingLowStock = false; actions.onLowStock(it) }, onDismiss = { editingLowStock = false })
    }
}

@Composable
private fun LowStockDialog(current: BigDecimal?, onDone: (BigDecimal?) -> Unit, onDismiss: () -> Unit) {
    val format = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 } }
    var text by rememberSaveable { mutableStateOf(current?.let(format::format).orEmpty()) }
    val parsed = parseAmount(text, format)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_low_stock)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                Text(stringResource(R.string.details_low_stock_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = text.isNotBlank() && parsed == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(parsed) }, enabled = parsed != null) { Text(stringResource(R.string.details_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.details_low_stock_clear)) }
        },
    )
}

/** A non-negative amount typed in the user's locale ("1,5" in German), or `null` if it isn't one. */
private fun parseAmount(text: String, format: NumberFormat): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val position = ParsePosition(0)
    val number = format.parse(trimmed, position) ?: return null
    if (position.index != trimmed.length) return null
    return BigDecimal(number.toString()).takeIf { it.signum() >= 0 }
}

@Composable
private fun Footer(draft: ItemDraft, onRemove: () -> Unit, onSave: () -> Unit) {
    val colors = PantryTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .navigationBarsPadding()
            .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space3, bottom = PantryDimens.space4),
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon),
    ) {
        if (!draft.isNew) {
            OutlinedButton(
                onClick = onRemove,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(PantryDimens.hairline, colors.dangerBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.danger),
                modifier = Modifier.height(PantryDimens.buttonHeight),
            ) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = null, modifier = Modifier.size(PantryDimens.iconSmall))
                Spacer(Modifier.size(PantryDimens.space2))
                Text(stringResource(R.string.details_remove), style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(
            onClick = onSave,
            enabled = draft.name.isNotBlank(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier
                .weight(1f)
                .height(PantryDimens.buttonHeight),
        ) {
            Text(stringResource(R.string.details_save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RemoveDialog(draft: ItemDraft, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = PantryTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.details_remove_title, draft.name.trim()), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                stringResource(R.string.details_remove_body, quantityText(draft.quantity), locationName(draft.location)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = colors.inkMuted,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.surface),
            ) { Text(stringResource(R.string.details_remove), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_cancel), style = MaterialTheme.typography.labelLarge, color = colors.ink) }
        },
    )
}

// Previews and the screenshot test use a made-up item, marked as a sample.
internal val previewToday = LocalDate.of(2026, 11, 10)
internal val previewState = ItemDetailsState(
    today = previewToday,
    draft = ItemDraft.new(previewToday, Location.Fridge, barcode = "096619321084").copy(
        id = 1,
        name = "Sample eggs",
        quantity = Quantity(BigDecimal("18"), QuantityUnit.Each),
        expires = previewToday.plusDays(2),
        opened = true,
        lowStockAt = BigDecimal("6"),
    ),
)

@Preview
@Composable
private fun ItemDetailsLightPreview() = PantryTheme(darkTheme = false) { ItemDetailsContent(previewState, ItemDetailsActions()) }

@Preview
@Composable
private fun ItemDetailsDarkPreview() = PantryTheme(darkTheme = true) { ItemDetailsContent(previewState, ItemDetailsActions()) }
