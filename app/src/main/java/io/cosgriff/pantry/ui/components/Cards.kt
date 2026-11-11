package io.cosgriff.pantry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.cosgriff.pantry.R
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme

/** A bordered surface card (grouped settings, form sections). Flat: a border, no shadow. */
@Composable
fun PantryCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = PantryTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.large)
            .border(PantryDimens.hairline, colors.border, MaterialTheme.shapes.large),
    ) { content() }
}

/** An uppercase section title over a card, as on the settings board. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PantryTheme.colors.inkMuted,
        modifier = modifier
            .padding(start = PantryDimens.space1)
            .semantics { heading() },
    )
}

/** A card row with a label and its value on the right; tapping it changes the value. */
@Composable
fun ValueRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PantryDimens.spaceInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = PantryTheme.colors.inkMuted)
    }
}

/** A card row with a label and a checkbox; the whole row toggles it. */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = PantryDimens.spaceInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = PantryTheme.colors.accent))
    }
}

/** The back arrow and title at the top of a pushed screen. */
@Composable
fun BackTopBar(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PantryDimens.space2, vertical = PantryDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), backLabel)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = PantryDimens.space1)
                .semantics { heading() },
        )
    }
}
