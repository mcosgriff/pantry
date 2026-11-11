package io.cosgriff.pantry.ui.usesoon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.QuickAction
import io.cosgriff.pantry.domain.UseSoonGroup
import io.cosgriff.pantry.domain.expiryStatus
import io.cosgriff.pantry.domain.quickAction
import io.cosgriff.pantry.domain.useSoon
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.LocationTile
import io.cosgriff.pantry.ui.components.expiryText
import io.cosgriff.pantry.ui.components.groupedRow
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.math.BigDecimal
import java.time.LocalDate

data class UseSoonActions(
    val onBack: () -> Unit = {},
    val onItem: (Long) -> Unit = {},
    val onAction: (PantryItem) -> Unit = {},
)

@Composable
fun UseSoonScreen(onBack: () -> Unit, onItem: (Long) -> Unit, viewModel: UseSoonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val done = state.done
    val message = done?.let {
        stringResource(if (it.action == QuickAction.Freeze) R.string.use_soon_frozen else R.string.use_soon_removed, it.before.name)
    }
    val undo = stringResource(R.string.use_soon_undo)
    LaunchedEffect(done) {
        if (done != null && message != null) {
            val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndo() else viewModel.onDoneShown()
        }
    }
    UseSoonContent(state, UseSoonActions(onBack, onItem, viewModel::onAction), snackbar = { SnackbarHost(snackbar) })
}

/**
 * `design/source/Expiring.dc.html` (#16). Its "Plan meals with these" button waits for Meal prep (#19), so it
 * isn't shown yet.
 */
@Composable
fun UseSoonContent(
    state: UseSoonState,
    actions: UseSoonActions,
    modifier: Modifier = Modifier,
    snackbar: @Composable () -> Unit = {},
) {
    val colors = PantryTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        topBar = { BackTopBar(stringResource(R.string.use_soon_title), stringResource(R.string.details_back), actions.onBack, Modifier.statusBarsPadding()) },
        snackbarHost = snackbar,
    ) { insets ->
        if (!state.loading && state.groups.isEmpty()) {
            Text(
                stringResource(R.string.use_soon_nothing),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkMuted,
                modifier = Modifier
                    .padding(insets)
                    .padding(horizontal = PantryDimens.space5, vertical = PantryDimens.space3),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + PantryDimens.space1,
                bottom = insets.calculateBottomPadding() + PantryDimens.space6,
            ),
        ) {
            state.groups.forEach { (group, items) ->
                item(key = group) {
                    val (title, color) = when (group) {
                        UseSoonGroup.Expired -> R.string.use_soon_expired to colors.danger
                        UseSoonGroup.ThisWeek -> R.string.use_soon_this_week to colors.warning
                        UseSoonGroup.NextWeek -> R.string.use_soon_next_week to colors.inkMuted
                    }
                    Text(
                        stringResource(title, items.size).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier
                            .padding(horizontal = PantryDimens.space5)
                            .then(if (group == state.groups.keys.first()) Modifier else Modifier.padding(top = PantryDimens.spaceSection))
                            .padding(bottom = PantryDimens.space2)
                            .semantics { heading() },
                    )
                }
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    Row(
                        Modifier
                            .padding(horizontal = PantryDimens.space5)
                            .groupedRow(index, items.size, MaterialTheme.shapes.large, colors.surface, colors.border, colors.divider)
                            .clickable { actions.onItem(item.id) }
                            .padding(start = PantryDimens.spaceInset, end = PantryDimens.space2, top = PantryDimens.spaceIcon, bottom = PantryDimens.spaceIcon),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
                    ) {
                        LocationTile(item.location, size = PantryDimens.tileMedium)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.use_soon_detail, expiryText(expiryStatus(item.expires, state.today), item.expires), quantityText(item.quantity)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
                            )
                        }
                        val action = stringResource(
                            when (quickAction(item, state.today)) {
                                QuickAction.ThrowOut -> R.string.use_soon_throw_out
                                QuickAction.UsedUp -> R.string.use_soon_used_up
                                QuickAction.Freeze -> R.string.use_soon_freeze
                            },
                        )
                        // TalkBack hears which item each button acts on: the buttons alone all read alike.
                        val label = stringResource(R.string.use_soon_action_on, action, item.name)
                        OutlinedButton(
                            onClick = { actions.onAction(item) },
                            shape = MaterialTheme.shapes.extraLarge,
                            border = BorderStroke(PantryDimens.hairline, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
                            contentPadding = PaddingValues(horizontal = PantryDimens.space3),
                            modifier = Modifier
                                .heightIn(min = PantryDimens.tapMin)
                                .semantics { contentDescription = label },
                        ) {
                            Text(
                                action,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Previews and the screenshot test use made-up items, marked as samples.
internal val previewToday = LocalDate.of(2026, 11, 10)
private fun sample(id: Long, name: String, days: Long, location: Location, opened: Boolean = false) = PantryItem(
    id = id,
    name = name,
    location = location,
    quantity = Measure(BigDecimal.ONE, MeasureUnit.Each),
    purchased = previewToday,
    expires = previewToday.plusDays(days),
    opened = opened,
)
internal val previewState = UseSoonState(
    today = previewToday,
    loading = false,
    groups = useSoon(
        listOf(
            sample(1, "Sample spinach", -1, Location.Fridge),
            sample(2, "Sample eggs", 2, Location.Fridge, opened = true),
            sample(3, "Sample chicken", 3, Location.Fridge),
            sample(4, "Sample hummus", 11, Location.Fridge, opened = true),
        ),
        previewToday,
    ),
)

@Preview
@Composable
private fun UseSoonLightPreview() = PantryTheme(darkTheme = false) { UseSoonContent(previewState, UseSoonActions()) }

@Preview
@Composable
private fun UseSoonDarkPreview() = PantryTheme(darkTheme = true) { UseSoonContent(previewState, UseSoonActions()) }
