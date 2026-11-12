package io.cosgriff.pantry.ui.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.NewProduct
import io.cosgriff.pantry.ui.components.FieldLabel
import io.cosgriff.pantry.ui.components.PantryCard
import io.cosgriff.pantry.ui.components.SectionHeading
import io.cosgriff.pantry.ui.components.barcodeText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme

/** Open Food Facts' page for creating an account; opened in the browser, not by the app. */
private const val CREATE_ACCOUNT_URL = "https://world.openfoodfacts.org/cgi/user.pl"

data class ShareActions(
    val onAlwaysShare: (Boolean) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onBrand: (String) -> Unit = {},
    val onSize: (String) -> Unit = {},
    val onUser: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onShare: () -> Unit = {},
    val onCreateAccount: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * Offers to add [barcode], named [name] by the user, to Open Food Facts (#42, `docs/adr/0004`), and closes with
 * [onDone] once shared, skipped or declined.
 */
@Composable
fun ShareProductScreen(barcode: String, name: String, onDone: () -> Unit, viewModel: ShareProductViewModel = hiltViewModel()) {
    LaunchedEffect(barcode, name) { viewModel.load(barcode, name) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onDone() }
    BackHandler { viewModel.onClose() }
    val uris = LocalUriHandler.current
    ShareProductContent(
        state,
        ShareActions(
            onAlwaysShare = viewModel::onAlwaysShare,
            onContinue = viewModel::onContinue,
            onName = viewModel::onName,
            onBrand = viewModel::onBrand,
            onSize = viewModel::onSize,
            onUser = viewModel::onUser,
            onPassword = viewModel::onPassword,
            onShare = viewModel::onShare,
            onCreateAccount = { uris.openUri(CREATE_ACCOUNT_URL) },
            onClose = viewModel::onClose,
        ),
    )
}

/**
 * The prompt (`design/source/OffShare.dc.html`), the form (`OffDetails.dc.html`) and thanks. Text only: no photos
 * are taken or sent (`docs/adr/0004`), so the boards' photo row and photo line are left out.
 */
@Composable
fun ShareProductContent(state: ShareState, actions: ShareActions, modifier: Modifier = Modifier) {
    when (state.step) {
        ShareStep.Ask -> SheetOverScanner(modifier) { AskSheet(state, actions) }
        ShareStep.Details -> DetailsPage(state, actions, modifier)
        ShareStep.Done -> SheetOverScanner(modifier) { DoneSheet(actions) }
    }
}

/** As the scan sheet: a sheet over the scanner's dark ground, which the user has just come from. */
@Composable
private fun SheetOverScanner(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = PantryTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.scannerGround),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.spaceIcon, bottom = PantryDimens.space5),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = PantryDimens.sheetHandleWidth, height = PantryDimens.sheetHandleHeight)
                    .background(colors.borderStrong, MaterialTheme.shapes.extraSmall),
            )
            content()
        }
    }
}

@Composable
private fun AskSheet(state: ShareState, actions: ShareActions) {
    val colors = PantryTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceInset), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(PantryDimens.shareIconBox)
                .background(colors.surfaceMuted, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_globe), null, tint = colors.accent, modifier = Modifier.size(PantryDimens.shareIcon))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(state.product.name, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
    }
    Text(stringResource(R.string.share_body), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
        SectionHeading(stringResource(R.string.share_what))
        Column(Modifier.background(colors.surfaceMuted, MaterialTheme.shapes.large)) {
            val lines = listOf(
                R.string.share_sent_name to true,
                R.string.share_sent_details to true,
                R.string.share_sent_account to true,
                R.string.share_never_sent to false,
            )
            lines.forEachIndexed { index, (text, sent) ->
                if (index > 0) HorizontalDivider(color = colors.border)
                Row(
                    Modifier.padding(horizontal = PantryDimens.spaceInset, vertical = PantryDimens.spaceIcon),
                    horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (sent) R.drawable.ic_check else R.drawable.ic_clear),
                        null,
                        tint = if (sent) colors.accent else colors.inkMuted,
                        modifier = Modifier.size(PantryDimens.iconSmall),
                    )
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = if (sent) colors.ink else colors.inkMuted)
                }
            }
        }
        Text(stringResource(R.string.share_licence), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.tapMin)
            .toggleable(value = state.alwaysShare, role = Role.Checkbox, onValueChange = actions.onAlwaysShare),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.share_always), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = state.alwaysShare, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
    }
    ButtonRow(stringResource(R.string.share_not_now), actions.onClose, stringResource(R.string.share_continue), enabled = true, actions.onContinue)
}

@Composable
private fun DoneSheet(actions: ShareActions) {
    val colors = PantryTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceInset), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(PantryDimens.shareIconBox)
                .background(colors.surfaceMuted, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_check), null, tint = colors.accent, modifier = Modifier.size(PantryDimens.shareIcon))
        }
        Text(
            stringResource(R.string.share_done_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
    }
    Text(stringResource(R.string.share_done_body), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
    PrimaryButton(stringResource(R.string.share_done), enabled = true, onClick = actions.onClose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DetailsPage(state: ShareState, actions: ShareActions, modifier: Modifier) {
    val colors = PantryTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(PantryDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PantryDimens.space1),
        ) {
            IconButton(onClick = actions.onClose) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.share_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.share_details_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(barcodeText(state.product.barcode), style = PantryTheme.text.barcode, color = colors.inkMuted)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space1, bottom = PantryDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
        ) {
            PantryCard {
                Column {
                    CardField(stringResource(R.string.share_name), state.product.name, actions.onName, hint = "")
                    HorizontalDivider(color = colors.divider)
                    CardField(stringResource(R.string.share_brand), state.product.brand, actions.onBrand, hint = stringResource(R.string.share_brand_hint))
                    HorizontalDivider(color = colors.divider)
                    CardField(stringResource(R.string.share_size), state.product.size, actions.onSize, hint = stringResource(R.string.share_size_hint))
                }
            }
            val account = state.account
            if (account == null) {
                Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                    SectionHeading(stringResource(R.string.share_account))
                    PantryCard {
                        Column {
                            CardField(stringResource(R.string.share_user), state.user, actions.onUser, hint = "", keyboard = KeyboardType.Email)
                            HorizontalDivider(color = colors.divider)
                            CardField(stringResource(R.string.share_password), state.password, actions.onPassword, hint = "", keyboard = KeyboardType.Password)
                        }
                    }
                    Text(stringResource(R.string.share_account_body), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted, modifier = Modifier.padding(horizontal = PantryDimens.space1))
                    TextButton(onClick = actions.onCreateAccount) {
                        Text(stringResource(R.string.share_create_account), style = MaterialTheme.typography.labelLarge, color = colors.accent)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceMuted, MaterialTheme.shapes.large)
                        .padding(horizontal = PantryDimens.space4, vertical = PantryDimens.space3),
                    horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
                ) {
                    Icon(painterResource(R.drawable.ic_info), null, tint = colors.inkMuted, modifier = Modifier.size(PantryDimens.iconSmall))
                    Text(stringResource(R.string.share_as, account), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
            state.problem?.let { problem ->
                Text(
                    stringResource(problemText(problem)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Box(Modifier.padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space3, bottom = PantryDimens.space5)) {
            ButtonRow(
                stringResource(R.string.share_skip),
                actions.onClose,
                stringResource(if (state.sending) R.string.share_sending else R.string.share_send),
                enabled = state.canSend,
                actions.onShare,
            )
        }
    }
}

private fun problemText(problem: ShareProblem): Int = when (problem) {
    ShareProblem.WrongDetails -> R.string.share_wrong_details
    ShareProblem.Offline -> R.string.share_offline
    ShareProblem.AlreadyThere -> R.string.share_already_there
    ShareProblem.SignedOut -> R.string.share_signed_out
    ShareProblem.Rejected -> R.string.share_rejected
}

/** A labelled one-line field inside a card, as on the OffDetails board; [keyboard] Password hides what is typed. */
@Composable
private fun CardField(label: String, value: String, onChange: (String) -> Unit, hint: String, keyboard: KeyboardType = KeyboardType.Text) {
    val colors = PantryTheme.colors
    val password = keyboard == KeyboardType.Password
    Column(
        Modifier.padding(horizontal = PantryDimens.spaceInset, vertical = PantryDimens.spaceIcon),
        verticalArrangement = Arrangement.spacedBy(PantryDimens.space0),
    ) {
        FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = !password && keyboard == KeyboardType.Text),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PantryDimens.tapMin)
                .semantics { contentDescription = label },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                    field()
                }
            },
        )
    }
}

/** An outlined button and the main one beside it, as at the foot of the boards. */
@Composable
private fun ButtonRow(secondLabel: String, onSecond: () -> Unit, mainLabel: String, enabled: Boolean, onMain: () -> Unit) {
    val colors = PantryTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.spaceIcon)) {
        OutlinedButton(
            onClick = onSecond,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(PantryDimens.hairline, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            modifier = Modifier
                .weight(1f)
                .height(PantryDimens.buttonHeight),
        ) { Text(secondLabel, style = MaterialTheme.typography.labelLarge) }
        PrimaryButton(mainLabel, enabled, onMain, Modifier.weight(1.6f))
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        // Disabled until it can be sent: still legible, with no accent to call it ready, as on the scan sheet.
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.inkMuted,
        ),
        modifier = modifier.height(PantryDimens.buttonHeight),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Made-up product for previews and screenshots. */
internal val previewShareProduct = NewProduct("041220576913", "Sample sparkling water, lime")

@Preview
@Composable
private fun ShareAskPreview() = PantryTheme(darkTheme = false) { ShareProductContent(ShareState(product = previewShareProduct), ShareActions()) }

@Preview
@Composable
private fun ShareDetailsDarkPreview() = PantryTheme(darkTheme = true) {
    ShareProductContent(ShareState(step = ShareStep.Details, product = previewShareProduct), ShareActions())
}
