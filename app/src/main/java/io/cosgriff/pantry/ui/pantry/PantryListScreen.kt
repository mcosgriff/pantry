package io.cosgriff.pantry.ui.pantry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ExpirySummary
import io.cosgriff.pantry.domain.ItemFilter
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import io.cosgriff.pantry.domain.SortOrder
import io.cosgriff.pantry.ui.components.LocationTile
import io.cosgriff.pantry.ui.components.PantryBottomBar
import io.cosgriff.pantry.ui.components.TopLevel
import io.cosgriff.pantry.ui.components.expiryText
import io.cosgriff.pantry.ui.components.groupedRow
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.time.LocalDate

/** What the Pantry list asks of the app: where each button goes. */
data class PantryListActions(
    val onQueryChange: (String) -> Unit = {},
    val onSelectLocation: (Location?) -> Unit = {},
    val onSort: (SortOrder) -> Unit = {},
    val onToggleFilter: (ItemFilter) -> Unit = {},
    val onResetView: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onViewExpiring: () -> Unit = {},
    val onItem: (Long) -> Unit = {},
    val onScan: () -> Unit = {},
    /** Add an item by hand, its name filled in with the text given (empty for none). */
    val onAddByHand: (String) -> Unit = {},
    val onNavigate: (TopLevel) -> Unit = {},
    val onAddToShopping: (String) -> Unit = {},
)

@Composable
fun PantryListScreen(actions: PantryListActions, viewModel: PantryListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PantryListContent(
        state = state,
        actions = actions.copy(
            onQueryChange = viewModel::onQueryChange,
            onSelectLocation = viewModel::onSelect,
            onSort = { viewModel.onSort(it) },
            onToggleFilter = { viewModel.onToggleFilter(it) },
            onResetView = viewModel::onResetView,
            onAddToShopping = viewModel::onAddToShopping,
        ),
    )
}

/** The home screen (`design/source/Main.dc.html`, #6), or the empty pantry (`PantryEmpty.dc.html`, #15). */
@Composable
fun PantryListContent(state: PantryListState, actions: PantryListActions, modifier: Modifier = Modifier) {
    val empty = !state.loading && state.total == 0
    var sorting by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PantryTheme.colors.ground,
        bottomBar = { PantryBottomBar(current = TopLevel.Pantry, onSelect = actions.onNavigate) },
        floatingActionButton = {
            if (!state.loading && !empty) {
                val scanLabel = stringResource(R.string.pantry_scan)
                ExtendedFloatingActionButton(
                    onClick = actions.onScan,
                    icon = { Icon(painterResource(R.drawable.ic_scan), contentDescription = null) },
                    text = { Text(stringResource(R.string.pantry_scan), style = MaterialTheme.typography.labelLarge) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = PantryTheme.colors.accent,
                    contentColor = PantryTheme.colors.onAccent,
                    // The text slot didn't reach the accessibility tree (TalkBack read an unlabelled button), so
                    // the label is set on the button itself.
                    modifier = Modifier
                        .height(PantryDimens.fabHeight)
                        .semantics { contentDescription = scanLabel },
                )
            }
        },
    ) { insets ->
        when {
            state.loading -> Box(Modifier.padding(insets))
            empty -> EmptyPantry(actions, Modifier.padding(insets))
            else -> PantryList(state, actions, PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())) { sorting = true }
        }
    }
    if (sorting) {
        SortFilterSheet(state, actions, onDismiss = { sorting = false })
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier, buttons: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.home_place),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = PantryTheme.colors.inkMuted,
            )
            Text(
                stringResource(R.string.pantry_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        buttons()
    }
}

@Composable
private fun PantryList(state: PantryListState, actions: PantryListActions, padding: PaddingValues, onSortFilter: () -> Unit) {
    val colors = PantryTheme.colors
    val gutter = PantryDimens.space5
    LazyColumn(
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + PantryDimens.space6,
            // Room under the last row for the Scan button.
            bottom = padding.calculateBottomPadding() + PantryDimens.fabHeight + PantryDimens.space6,
        ),
    ) {
        item {
            Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header {
                    IconButton(onClick = onSortFilter) {
                        Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.pantry_sort_filter))
                    }
                    IconButton(onClick = actions.onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.pantry_settings))
                    }
                }
                SearchField(state.query, state.total, actions.onQueryChange)
            }
        }
        item {
            LocationTabs(state, actions.onSelectLocation, Modifier.padding(top = 14.dp, bottom = PantryDimens.space3))
        }
        if (state.summary.thisWeek > 0) {
            item {
                ExpiringBanner(state.summary, actions.onViewExpiring, Modifier.padding(horizontal = gutter).padding(top = PantryDimens.space1))
            }
        }
        item {
            Text(
                stringResource(
                    when (state.sort) {
                        SortOrder.ExpiresSoonest -> R.string.pantry_expiring_first
                        SortOrder.NameAToZ -> R.string.pantry_sorted_name
                        SortOrder.RecentlyAdded -> R.string.pantry_sorted_recent
                        SortOrder.LowestStockFirst -> R.string.pantry_sorted_low_stock
                    },
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
                modifier = Modifier
                    .padding(horizontal = gutter)
                    .padding(top = 18.dp, bottom = PantryDimens.space2)
                    .semantics { heading() },
            )
        }
        if (state.rows.isEmpty() && state.query.isNotBlank()) {
            item {
                SearchEmpty(
                    state.query.trim(),
                    onAdd = { actions.onAddByHand(state.query.trim()) },
                    onAddToShopping = { actions.onAddToShopping(state.query.trim()) },
                )
            }
        } else if (state.rows.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.pantry_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = gutter, vertical = PantryDimens.space3),
                )
            }
        }
        itemsIndexed(state.rows, key = { _, item -> item.id }) { index, item ->
            ItemRow(
                item = item,
                status = item.expiryStatus(state.today, state.warningDays),
                onClick = { actions.onItem(item.id) },
                modifier = Modifier
                    .padding(horizontal = gutter)
                    .groupedRow(index, state.rows.size, 18.dp, colors.surface, colors.border, colors.divider),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, total: Int, onQueryChange: (String) -> Unit) {
    val colors = PantryTheme.colors
    val label = stringResource(R.string.pantry_search_label)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .height(PantryDimens.fieldHeight)
                    .background(colors.surface, MaterialTheme.shapes.medium)
                    .border(1.dp, colors.border, MaterialTheme.shapes.medium)
                    .padding(start = 14.dp, end = if (query.isEmpty()) 14.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.pantry_search_hint, total, total),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.inkMuted,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.pantry_clear_search), tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun LocationTabs(state: PantryListState, onSelect: (Location?) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pantry_locations)
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PantryDimens.space5)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2),
    ) {
        Tab(stringResource(R.string.pantry_tab, stringResource(R.string.pantry_tab_all), state.total), state.selected == null) { onSelect(null) }
        state.tabs.forEach { (location, count) ->
            Tab(stringResource(R.string.pantry_tab, locationName(location), count), state.selected == location) { onSelect(location) }
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PantryTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(36.dp)
                .background(if (selected) colors.ink else colors.surface, shape)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.border, shape))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.ground else colors.ink,
            )
        }
    }
}

@Composable
private fun ExpiringBanner(summary: ExpirySummary, onView: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.warningContainer, MaterialTheme.shapes.large)
            .padding(start = PantryDimens.space4, end = PantryDimens.space2, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(colors.warningContainerStrong, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = colors.onWarningContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pluralStringResource(R.plurals.pantry_expiring_this_week, summary.thisWeek, summary.thisWeek),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onWarningContainer,
            )
            if (summary.expired > 0) {
                Text(
                    pluralStringResource(R.plurals.pantry_already_expired, summary.expired, summary.expired),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                )
            }
        }
        TextButton(onClick = onView, shape = MaterialTheme.shapes.extraSmall) {
            Text(stringResource(R.string.pantry_view_expiring), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.onWarningContainer)
        }
    }
}

@Composable
private fun ItemRow(item: PantryItem, status: ExpiryStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    val (expiryColor, expiryWeight) = when (status) {
        is ExpiryStatus.Expired -> colors.danger to FontWeight.SemiBold
        is ExpiryStatus.ExpiresSoon -> colors.warning to FontWeight.SemiBold
        else -> colors.inkMuted to FontWeight.Normal
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.tapMin)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = PantryDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        LocationTile(item.location)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.expiry_with_location, expiryText(status, item.expires), locationName(item.location)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = expiryWeight,
                color = expiryColor,
            )
        }
        Text(
            quantityText(item.quantity),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 10.dp, vertical = PantryDimens.space1),
        )
    }
}

@Composable
private fun EmptyPantry(actions: PantryListActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Column(modifier.fillMaxSize()) {
        Header(Modifier.padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space6, bottom = PantryDimens.space3))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = PantryDimens.space6),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space5, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyShelves()
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.pantry_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.pantry_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = actions.onScan,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
                ) {
                    Icon(painterResource(R.drawable.ic_scan), contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.pantry_scan), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { actions.onAddByHand("") },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
                ) {
                    Text(stringResource(R.string.pantry_empty_add_by_hand), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** The empty-shelves drawing from the board: a cupboard with three shelves and a few tinted boxes. Decorative. */
@Composable
private fun EmptyShelves() {
    val colors = PantryTheme.colors
    Canvas(Modifier.size(width = 160.dp, height = 128.dp)) {
        val u = size.width / 160f
        fun box(x: Int, y: Int, w: Int, h: Int, color: Color) =
            drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(6 * u))
        drawRoundRect(colors.borderStrong, Offset(18 * u, 6 * u), Size(124 * u, 116 * u), CornerRadius(12 * u), style = Stroke(3 * u))
        drawLine(colors.borderStrong, Offset(18 * u, 46 * u), Offset(142 * u, 46 * u), 3 * u)
        drawLine(colors.borderStrong, Offset(18 * u, 84 * u), Offset(142 * u, 84 * u), 3 * u)
        box(32, 20, 22, 22, colors.dryContainer)
        box(60, 26, 30, 16, colors.dryContainer)
        box(32, 60, 36, 20, colors.fridgeContainer)
        box(96, 96, 30, 22, colors.freezerContainer)
    }
}

// Previews use made-up items, marked as samples.
private val previewToday: LocalDate = LocalDate.of(2026, 11, 10)

private fun sample(id: Long, name: String, location: Location, expires: LocalDate?, amount: String, unit: QuantityUnit) =
    PantryItem(id = id, name = name, location = location, quantity = Quantity(BigDecimal(amount), unit), purchased = previewToday, expires = expires)

private val previewState = PantryListState(
    today = previewToday,
    loading = false,
    total = 6,
    tabs = listOf(
        LocationCount(Location.Dry, 1),
        LocationCount(Location.Fridge, 3),
        LocationCount(Location.Freezer, 2),
    ),
    rows = listOf(
        sample(1, "Sample spinach", Location.Fridge, previewToday.minusDays(1), "1", QuantityUnit.Bag),
        sample(2, "Sample eggs", Location.Fridge, previewToday.plusDays(2), "18", QuantityUnit.Each),
        sample(3, "Sample yogurt", Location.Fridge, previewToday.plusDays(5), "2", QuantityUnit.Tub),
        sample(4, "Sample chicken thighs", Location.Freezer, LocalDate.of(2027, 1, 14), "4", QuantityUnit.Pack),
        sample(5, "Sample blueberries", Location.Freezer, LocalDate.of(2027, 3, 1), "2", QuantityUnit.Bag),
        sample(6, "Sample rice", Location.Dry, null, "0.6", QuantityUnit.Bag),
    ),
    summary = ExpirySummary(thisWeek = 3, expired = 1),
)

@Preview
@Composable
private fun PantryListLightPreview() = PantryTheme(darkTheme = false) { PantryListContent(previewState, PantryListActions()) }

@Preview
@Composable
private fun PantryListDarkPreview() = PantryTheme(darkTheme = true) { PantryListContent(previewState, PantryListActions()) }

@Preview
@Composable
private fun PantryEmptyPreview() =
    PantryTheme(darkTheme = false) { PantryListContent(PantryListState(today = previewToday, loading = false), PantryListActions()) }
