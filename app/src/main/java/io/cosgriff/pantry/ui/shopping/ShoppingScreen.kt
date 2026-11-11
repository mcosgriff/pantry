package io.cosgriff.pantry.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.ShoppingEntry
import io.cosgriff.pantry.domain.ShoppingLine
import io.cosgriff.pantry.domain.ShoppingReason
import io.cosgriff.pantry.domain.shoppingList
import io.cosgriff.pantry.domain.tripProgress
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.PantryBottomBar
import io.cosgriff.pantry.ui.components.TopLevel
import io.cosgriff.pantry.ui.components.groupedRow
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class ShoppingActions(
    val onAdd: (String) -> Unit = {},
    val onRemove: (ShoppingEntry) -> Unit = {},
    val onToggle: (ShoppingLine, Boolean) -> Unit = { _, _ -> },
    val onStartTrip: () -> Unit = {},
    val onNavigate: (TopLevel) -> Unit = {},
)

@Composable
fun ShoppingScreen(onStartTrip: () -> Unit, onNavigate: (TopLevel) -> Unit, viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingContent(state, ShoppingActions(viewModel::onAdd, viewModel::onRemove, viewModel::onToggle, onStartTrip, onNavigate))
}

/** `design/source/Shopping.dc.html` (#20). "For this week's meals" waits for Meal prep (#19). */
@Composable
fun ShoppingContent(state: ShoppingState, actions: ShoppingActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        bottomBar = { PantryBottomBar(TopLevel.Shopping, actions.onNavigate) },
    ) { insets ->
        LazyColumn(contentPadding = PaddingValues(top = insets.calculateTopPadding() + PantryDimens.space6, bottom = insets.calculateBottomPadding() + PantryDimens.space6)) {
            item {
                Column(Modifier.padding(horizontal = PantryDimens.space5), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val n = state.lines.size
                            Text(pluralStringResource(R.plurals.shopping_count, n, n), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.inkMuted)
                            Text(stringResource(R.string.shopping_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        }
                        if (state.lines.isNotEmpty()) {
                            Button(
                                onClick = actions.onStartTrip,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            ) { Text(stringResource(R.string.shopping_start_trip), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    AddField(actions.onAdd)
                }
            }
            if (!state.loading && state.lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.shopping_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space5),
                    )
                }
            }
            state.lines.groupBy { it.reason }.forEach { (reason, lines) ->
                item(key = reason) {
                    Text(
                        stringResource(
                            when (reason) {
                                ShoppingReason.RunningLow -> R.string.shopping_running_low
                                ShoppingReason.UsedUp -> R.string.shopping_used_up
                                ShoppingReason.ByHand -> R.string.shopping_by_hand
                            },
                            lines.size,
                        ).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                        modifier = Modifier
                            .padding(horizontal = PantryDimens.space5)
                            .padding(top = 18.dp, bottom = PantryDimens.space2)
                            .semantics { heading() },
                    )
                }
                itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                    LineRow(
                        line = line,
                        inCart = line.key in state.cart,
                        onToggle = { actions.onToggle(line, it) },
                        onRemove = line.entry?.let { entry -> { actions.onRemove(entry) } },
                        modifier = Modifier
                            .padding(horizontal = PantryDimens.space5)
                            .groupedRow(index, lines.size, 18.dp, colors.surface, colors.border, colors.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddField(onAdd: (String) -> Unit) {
    val colors = PantryTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val label = stringResource(R.string.shopping_add_hint)
    val submit = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .height(PantryDimens.fieldHeight)
                    .background(colors.surface, MaterialTheme.shapes.medium)
                    .border(1.dp, colors.border, MaterialTheme.shapes.medium)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    field()
                }
                if (text.isNotBlank()) {
                    IconButton(onClick = submit) { Icon(painterResource(R.drawable.ic_check), label, tint = colors.accent) }
                }
            }
        },
    )
}

/** Why a line is on the list: "6 ea left · alert at 6 ea", "Finished Nov 8", "Added Nov 9". */
@Composable
private fun lineDetail(line: ShoppingLine): String {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    line.item?.let { item ->
        val at = item.lowStockAt ?: item.quantity.amount
        return stringResource(R.string.shopping_left, quantityText(item.quantity), quantityText(Quantity(at, item.quantity.unit)))
    }
    val entry = line.entry ?: return ""
    return stringResource(
        if (entry.reason == ShoppingReason.UsedUp) R.string.shopping_finished else R.string.shopping_added,
        date.format(entry.addedOn),
    )
}

@Composable
private fun LineRow(line: ShoppingLine, inCart: Boolean, onToggle: (Boolean) -> Unit, onRemove: (() -> Unit)?, modifier: Modifier = Modifier, showDetail: Boolean = true) {
    val colors = PantryTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .toggleable(value = inCart, role = Role.Checkbox, onValueChange = onToggle)
            .padding(start = PantryDimens.space2, end = PantryDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = inCart, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent), modifier = Modifier.padding(PantryDimens.space2))
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                line.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (inCart) FontWeight.Normal else FontWeight.SemiBold,
                color = if (inCart) colors.inkMuted else colors.ink,
                textDecoration = if (inCart) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDetail) Text(lineDetail(line), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.shopping_remove, line.name), tint = colors.inkMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ShoppingTripScreen(onBack: () -> Unit, onRestock: () -> Unit, viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingTripContent(state, onBack, onToggle = viewModel::onToggle, onDone = { viewModel.onFinish(onRestock) })
}

/** `design/source/ShoppingTrip.dc.html` (#20): check things off; what's left stays on the list. */
@Composable
fun ShoppingTripContent(state: ShoppingState, onBack: () -> Unit, onToggle: (ShoppingLine, Boolean) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val progress = tripProgress(state.lines, state.cart)
    // What's still to get first, then what's in the cart, each in list order.
    val lines = state.lines.sortedBy { it.key in state.cart }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        topBar = { BackTopBar(stringResource(R.string.trip_title), stringResource(R.string.trip_back), onBack, Modifier.statusBarsPadding()) },
        bottomBar = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space3, bottom = PantryDimens.space4),
                verticalArrangement = Arrangement.spacedBy(PantryDimens.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onDone,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
                ) {
                    Icon(painterResource(R.drawable.ic_scan), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.trip_done), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = PantryDimens.space2))
                }
                if (progress.left > 0) {
                    Text(pluralStringResource(R.plurals.trip_left, progress.left, progress.left), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
        },
    ) { insets ->
        LazyColumn(contentPadding = PaddingValues(start = PantryDimens.space5, end = PantryDimens.space5, top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())) {
            item {
                Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                        Text(stringResource(R.string.trip_count, progress.inCart, progress.total), style = PantryTheme.text.dateDisplay)
                        Text(stringResource(R.string.trip_in_cart), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    }
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.inCart.toFloat() / progress.total },
                        color = colors.accent,
                        trackColor = colors.surfaceMuted,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(PantryDimens.space2),
                    )
                }
            }
            itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                LineRow(
                    line = line,
                    inCart = line.key in state.cart,
                    onToggle = { onToggle(line, it) },
                    onRemove = null,
                    showDetail = false,
                    modifier = Modifier.groupedRow(index, lines.size, 18.dp, colors.surface, colors.border, colors.divider),
                )
            }
        }
    }
}

// Previews use made-up items, marked as samples.
private val previewToday = LocalDate.of(2026, 11, 10)
private val previewLines = shoppingList(
    listOf(
        PantryItem(id = 1, name = "Sample eggs", location = Location.Fridge, quantity = Quantity(BigDecimal("6"), QuantityUnit.Each), purchased = previewToday, lowStockAt = BigDecimal("6")),
    ),
    listOf(
        ShoppingEntry(2, "Sample yogurt", ShoppingReason.UsedUp, previewToday.minusDays(2)),
        ShoppingEntry(3, "Sample scallions", ShoppingReason.ByHand, previewToday),
    ),
)

@Preview
@Composable
private fun ShoppingPreview() = PantryTheme(darkTheme = false) { ShoppingContent(ShoppingState(false, previewLines, setOf("entry:2")), ShoppingActions()) }

@Preview
@Composable
private fun ShoppingTripDarkPreview() = PantryTheme(darkTheme = true) {
    ShoppingTripContent(ShoppingState(false, previewLines, setOf("entry:2")), onBack = {}, onToggle = { _, _ -> }, onDone = {})
}
