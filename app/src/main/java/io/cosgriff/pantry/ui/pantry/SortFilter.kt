package io.cosgriff.pantry.ui.pantry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ItemFilter
import io.cosgriff.pantry.domain.SortOrder
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme

/**
 * `design/source/SortFilter.dc.html` (#17). Choices apply as they are made, so "Show N items" counts the list as
 * it now is and just closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SortFilterSheet(state: PantryListState, actions: PantryListActions, onDismiss: () -> Unit) {
    val colors = PantryTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        // Explicit: the theme's errorContainer has the surface's value, so contentColorFor(surface) would give
        // the error text color.
        contentColor = colors.ink,
        // The sheet token's radius on the top corners only.
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = PantryDimens.spaceIcon)
                    .size(width = PantryDimens.sheetHandleWidth, height = PantryDimens.sheetHandleHeight)
                    .background(colors.borderStrong, MaterialTheme.shapes.extraSmall),
            )
        },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space4, bottom = PantryDimens.space6),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
        ) {
            Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                Legend(stringResource(R.string.sort_by))
                SortOrder.entries.forEachIndexed { index, sort ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    val selected = sort == state.sort
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = PantryDimens.buttonHeight)
                            .selectable(selected = selected, role = Role.RadioButton) { actions.onSort(sort) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sortLabel(sort), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
                    }
                }
            }
            Column {
                Legend(stringResource(R.string.sort_show_only))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    ItemFilter.entries.forEach { filter ->
                        val on = filter in state.filters
                        val shape = MaterialTheme.shapes.extraLarge
                        Box(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .toggleable(value = on, role = Role.Checkbox) { actions.onToggleFilter(filter) },
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
                                Text(
                                    filterLabel(filter),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = PantryDimens.space2), horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon)) {
                OutlinedButton(
                    onClick = actions.onResetView,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(PantryDimens.hairline, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
                    modifier = Modifier
                        .weight(1f)
                        .height(PantryDimens.buttonHeight),
                ) { Text(stringResource(R.string.sort_reset), style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier
                        .weight(1.6f)
                        .height(PantryDimens.buttonHeight),
                ) {
                    val count = state.rows.size
                    Text(pluralStringResource(R.plurals.sort_show, count, count), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PantryTheme.colors.inkMuted,
        modifier = Modifier.padding(bottom = PantryDimens.space2),
    )
}

@Composable
private fun sortLabel(sort: SortOrder) = stringResource(
    when (sort) {
        SortOrder.ExpiresSoonest -> R.string.sort_expires_soonest
        SortOrder.NameAToZ -> R.string.sort_name
        SortOrder.RecentlyAdded -> R.string.sort_recent
        SortOrder.LowestStockFirst -> R.string.sort_low_stock
    },
)

@Composable
private fun filterLabel(filter: ItemFilter) = stringResource(
    when (filter) {
        ItemFilter.Opened -> R.string.filter_opened
        ItemFilter.LowStock -> R.string.filter_low_stock
        ItemFilter.NoExpiryDate -> R.string.filter_no_date
        ItemFilter.AddedThisWeek -> R.string.filter_added_this_week
    },
)

/**
 * A search with no results (`design/source/SearchEmpty.dc.html`, #17). "Add to shopping list" waits for the
 * shopping list (#20).
 */
@Composable
internal fun SearchEmpty(query: String, onAdd: () -> Unit) {
    val colors = PantryTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PantryDimens.space6, vertical = PantryDimens.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PantryDimens.space5),
    ) {
        Box(
            Modifier
                .size(PantryDimens.emptyIconCircle)
                .background(colors.surfaceMuted, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(PantryDimens.iconLarge))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
            Text(
                stringResource(R.string.search_empty_title, query),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.search_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = onAdd,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(PantryDimens.hairline, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .height(PantryDimens.buttonHeight),
        ) { Text(stringResource(R.string.search_empty_add), style = MaterialTheme.typography.labelLarge) }
    }
}
