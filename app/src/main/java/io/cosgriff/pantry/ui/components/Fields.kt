package io.cosgriff.pantry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.cosgriff.pantry.R
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme

/**
 * A bordered "+ [label]" field that hands its text to [onAdd] on Done or its check button, then empties (the
 * shopping list's "Add an item", Plan a meal's typed-in ingredient).
 */
@Composable
fun AddField(label: String, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
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
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .height(PantryDimens.fieldHeight)
                    .background(colors.surface, MaterialTheme.shapes.medium)
                    .border(PantryDimens.hairline, colors.border, MaterialTheme.shapes.medium)
                    .padding(start = PantryDimens.spaceInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(PantryDimens.iconSmall))
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

/** A bordered one-line text field, named [label] for TalkBack, with [hint] while it's empty. */
@Composable
fun TextFieldBox(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = PantryTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .height(PantryDimens.fieldHeight)
                    .background(colors.surface, MaterialTheme.shapes.small)
                    .border(PantryDimens.hairline, colors.border, MaterialTheme.shapes.small)
                    .padding(horizontal = PantryDimens.space3),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                field()
            }
        },
    )
}
