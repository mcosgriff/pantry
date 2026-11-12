package io.cosgriff.pantry.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ContributeChoice
import io.cosgriff.pantry.domain.DefaultLocation
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.domain.LookupChoice
import io.cosgriff.pantry.domain.MeasureSystem
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.notify.mayNotify
import io.cosgriff.pantry.ui.components.BackTopBar
import io.cosgriff.pantry.ui.components.CheckRow
import io.cosgriff.pantry.ui.components.LocationTile
import io.cosgriff.pantry.ui.components.PantryCard
import io.cosgriff.pantry.ui.components.SectionHeading
import io.cosgriff.pantry.ui.components.ValueRow
import io.cosgriff.pantry.ui.components.locationName
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.time.LocalTime
import java.time.Period
import java.time.format.DateTimeFormatter

/** Warning windows the "Warn me" row offers. */
private val WARNING_CHOICES = listOf(1, 2, 3, 5, 7)

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onAddLocation: (name: String, shelfLifeDays: Int?) -> Unit = { _, _ -> },
    val onUpdateLocation: (Location.Custom, name: String, shelfLifeDays: Int?) -> Unit = { _, _, _ -> },
    val onDeleteLocation: (Location.Custom) -> Unit = {},
    val onDismissInUse: () -> Unit = {},
    val onWarningDays: (Int) -> Unit = {},
    val onNotifyExpiringSoon: (Boolean) -> Unit = {},
    val onNotifyExpired: (Boolean) -> Unit = {},
    val onDigestAt: (LocalTime) -> Unit = {},
    /** Opens Android's notification settings for the app, where a refused permission is turned back on. */
    val onNotificationSettings: () -> Unit = {},
    val onBatchScan: (Boolean) -> Unit = {},
    val onProductLookup: (Boolean) -> Unit = {},
    val onDefaultLocation: (DefaultLocation) -> Unit = {},
    val onMeasurements: (MeasureSystem) -> Unit = {},
    val onContributing: (ContributeChoice) -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.mayNotify()) }
    // Android 13 and later ask, in context, when an alert is turned on (#27). The choice is kept either way;
    // while Android refuses, the screen says so and links to its settings.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    // Coming back from Android settings with notifications switched on or off.
    LifecycleResumeEffect(Unit) {
        allowed = context.mayNotify()
        onPauseOrDispose {}
    }
    val turnOn: (Boolean, (Boolean) -> Unit) -> Unit = { on, save ->
        save(on)
        if (on && !allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    SettingsContent(
        state.copy(notificationsAllowed = allowed),
        SettingsActions(
            onBack = onBack,
            onNotifyExpiringSoon = { turnOn(it) { on -> viewModel.onNotifyExpiringSoon(on) } },
            onNotifyExpired = { turnOn(it) { on -> viewModel.onNotifyExpired(on) } },
            onDigestAt = { viewModel.onDigestAt(it) },
            onNotificationSettings = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onAddLocation = { name, days -> viewModel.onAddLocation(name, days) },
            onUpdateLocation = { location, name, days -> viewModel.onUpdateLocation(location, name, days) },
            onDeleteLocation = { viewModel.onDeleteLocation(it) },
            onDismissInUse = viewModel::onDismissInUse,
            onWarningDays = { viewModel.onWarningDays(it) },
            onBatchScan = { viewModel.onBatchScan(it) },
            onProductLookup = { viewModel.onProductLookup(it) },
            onDefaultLocation = { viewModel.onDefaultLocation(it) },
            onMeasurements = { viewModel.onMeasurements(it) },
            onContributing = { viewModel.onContributing(it) },
            onSignOut = { viewModel.onSignOut() },
        ),
    )
}

/**
 * `design/source/Settings.dc.html` (#8), with product lookup (#9; the board `OffSettings.dc.html`'s first switch) and
 * contributing to Open Food Facts (#42; that board's Contributing section).
 * The board's one Notifications switch is two here, expiring soon and already expired (#27). The board's Units
 * row is Measurements (#57): US customary or metric.
 */
@Composable
fun SettingsContent(state: SettingsState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    var editing by rememberSaveable { mutableStateOf<Long?>(null) } // a custom location's id, or NEW
    var choosingWarning by rememberSaveable { mutableStateOf(false) }
    var choosingDefault by rememberSaveable { mutableStateOf(false) }
    var choosingMeasurements by rememberSaveable { mutableStateOf(false) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.ground,
        topBar = {
            BackTopBar(
                stringResource(R.string.settings_title),
                stringResource(R.string.details_back),
                actions.onBack,
                Modifier.statusBarsPadding(),
            )
        },
    ) { insets ->
        Column(
            Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space1, bottom = PantryDimens.space6),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.spaceSection),
        ) {
            Section(stringResource(R.string.settings_locations)) {
                state.locations.forEachIndexed { index, location ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    LocationRow(location, onEdit = (location as? Location.Custom)?.let { { editing = it.id } })
                }
                HorizontalDivider(color = colors.divider)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PantryDimens.buttonHeight)
                        .clickable { editing = NEW }
                        .padding(horizontal = PantryDimens.spaceInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.accent, modifier = Modifier.size(PantryDimens.iconMedium))
                    Text(
                        stringResource(R.string.settings_add_location),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.accent,
                        modifier = Modifier.padding(start = PantryDimens.space3),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                Section(stringResource(R.string.settings_alerts)) {
                    val prefs = state.settings
                    CheckRow(stringResource(R.string.settings_notify_soon), prefs.notifyExpiringSoon, actions.onNotifyExpiringSoon)
                    HorizontalDivider(color = colors.divider)
                    CheckRow(stringResource(R.string.settings_notify_expired), prefs.notifyExpired, actions.onNotifyExpired)
                    if (prefs.digestOn && !state.notificationsAllowed) {
                        HorizontalDivider(color = colors.divider)
                        ValueRow(
                            stringResource(R.string.settings_notify_blocked),
                            stringResource(R.string.settings_notify_allow),
                            onClick = actions.onNotificationSettings,
                        )
                    }
                    HorizontalDivider(color = colors.divider)
                    val days = prefs.warningDays
                    ValueRow(
                        stringResource(R.string.settings_warn_me),
                        pluralStringResource(R.plurals.settings_days_before, days, days),
                        onClick = { choosingWarning = true },
                    )
                    HorizontalDivider(color = colors.divider)
                    ValueRow(stringResource(R.string.settings_digest), timeText(prefs.dailyDigestAt), onClick = { choosingTime = true })
                }
                Text(
                    stringResource(R.string.settings_alerts_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = PantryDimens.space1),
                )
            }
            Section(stringResource(R.string.settings_scanning)) {
                CheckRow(stringResource(R.string.settings_batch_scan), state.settings.batchScanByDefault, actions.onBatchScan)
                HorizontalDivider(color = colors.divider)
                ValueRow(
                    stringResource(R.string.settings_default_location),
                    defaultLocationName(state.settings.defaultLocation),
                    onClick = { choosingDefault = true },
                )
                HorizontalDivider(color = colors.divider)
                ValueRow(
                    stringResource(R.string.settings_measurements),
                    measurementsName(state.settings.measurements),
                    onClick = { choosingMeasurements = true },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                Section(stringResource(R.string.settings_lookup)) {
                    CheckRow(stringResource(R.string.settings_lookup_on), state.settings.productLookup == LookupChoice.On, actions.onProductLookup)
                }
                Text(
                    stringResource(R.string.settings_lookup_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = PantryDimens.space1),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
                Section(stringResource(R.string.settings_contributing)) {
                    ContributingChoices(state.settings.contributing, actions.onContributing)
                    HorizontalDivider(color = colors.divider)
                    val account = state.offAccount
                    if (account != null) {
                        ValueRow(stringResource(R.string.settings_contributing_account), account, onClick = { signingOut = true })
                    } else {
                        InfoRow(stringResource(R.string.settings_contributing_account), stringResource(R.string.settings_contributing_signed_out))
                    }
                    HorizontalDivider(color = colors.divider)
                    InfoRow(stringResource(R.string.settings_contributing_added), "${state.offShared}")
                }
                Text(
                    stringResource(R.string.settings_contributing_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = PantryDimens.space1),
                )
            }
        }
    }

    editing?.let { id ->
        val existing = state.locations.filterIsInstance<Location.Custom>().firstOrNull { it.id == id }
        LocationDialog(
            existing = existing,
            onSave = { name, days ->
                if (existing == null) actions.onAddLocation(name, days) else actions.onUpdateLocation(existing, name, days)
                editing = null
            },
            onDelete = existing?.let { { actions.onDeleteLocation(it); editing = null } },
            onDismiss = { editing = null },
        )
    }
    if (choosingWarning) {
        val options = (WARNING_CHOICES + state.settings.warningDays).distinct().sorted()
        ChoiceDialog(
            title = stringResource(R.string.settings_warn_me),
            options = options,
            selected = state.settings.warningDays,
            label = { pluralStringResource(R.plurals.settings_days_before, it, it) },
            onChoose = { choosingWarning = false; actions.onWarningDays(it) },
            onDismiss = { choosingWarning = false },
        )
    }
    if (choosingTime) {
        TimeDialog(
            initial = state.settings.dailyDigestAt,
            onChoose = { choosingTime = false; actions.onDigestAt(it) },
            onDismiss = { choosingTime = false },
        )
    }
    if (choosingDefault) {
        ChoiceDialog(
            title = stringResource(R.string.settings_default_location),
            options = listOf<DefaultLocation>(DefaultLocation.LastUsed) + state.locations.map(DefaultLocation::Fixed),
            selected = state.settings.defaultLocation,
            label = { defaultLocationName(it) },
            onChoose = { choosingDefault = false; actions.onDefaultLocation(it) },
            onDismiss = { choosingDefault = false },
        )
    }
    if (choosingMeasurements) {
        ChoiceDialog(
            title = stringResource(R.string.settings_measurements),
            options = MeasureSystem.entries,
            selected = state.settings.measurements,
            label = { measurementsName(it) },
            onChoose = { choosingMeasurements = false; actions.onMeasurements(it) },
            onDismiss = { choosingMeasurements = false },
        )
    }
    if (signingOut) {
        AlertDialog(
            onDismissRequest = { signingOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { signingOut = false; actions.onSignOut() }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { signingOut = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    state.locationInUse?.let { location ->
        AlertDialog(
            onDismissRequest = actions.onDismissInUse,
            text = { Text(stringResource(R.string.settings_location_in_use, location.name)) },
            confirmButton = { TextButton(onClick = actions.onDismissInUse) { Text(stringResource(R.string.details_ok)) } },
        )
    }
}

private const val NEW = -1L

/** "When a product isn't found": Ask me, Always share or Never share, as on `OffSettings.dc.html`. */
@Composable
private fun ContributingChoices(selected: ContributeChoice, onChoose: (ContributeChoice) -> Unit) {
    Column(
        Modifier
            .selectableGroup()
            .padding(start = PantryDimens.spaceInset, end = PantryDimens.spaceInset, top = PantryDimens.spaceIcon, bottom = PantryDimens.space1),
    ) {
        Text(stringResource(R.string.settings_contributing_when), style = MaterialTheme.typography.bodyLarge)
        ContributeChoice.entries.forEach { choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PantryDimens.tapMin)
                    .selectable(selected = choice == selected, role = Role.RadioButton) { onChoose(choice) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        when (choice) {
                            ContributeChoice.Ask -> R.string.settings_contributing_ask
                            ContributeChoice.Always -> R.string.settings_contributing_always
                            ContributeChoice.Never -> R.string.settings_contributing_never
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PantryTheme.colors.inkMuted,
                    modifier = Modifier.weight(1f),
                )
                RadioButton(selected = choice == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = PantryTheme.colors.accent))
            }
        }
    }
}

/** A card row with a label and a value that can't be changed here. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.buttonHeight)
            .padding(horizontal = PantryDimens.spaceInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = PantryTheme.colors.inkMuted)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space2)) {
        SectionHeading(title)
        PantryCard { Column { content() } }
    }
}

@Composable
private fun LocationRow(location: Location, onEdit: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PantryDimens.rowTall)
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(horizontal = PantryDimens.spaceInset, vertical = PantryDimens.spaceIcon),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3),
    ) {
        LocationTile(location, size = PantryDimens.tileSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space0)) {
            Text(locationName(location), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_shelf_life, shelfLifeText(location.shelfLife)),
                style = MaterialTheme.typography.bodySmall,
                color = PantryTheme.colors.inkMuted,
            )
        }
        if (onEdit != null) {
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = PantryTheme.colors.inkMuted, modifier = Modifier.size(PantryDimens.iconSmall))
        }
    }
}

@Composable
private fun shelfLifeText(period: Period?): String = when {
    period == null || period.isZero -> stringResource(R.string.settings_shelf_life_none)
    period.years > 0 && period.months == 0 && period.days == 0 -> pluralStringResource(R.plurals.settings_shelf_years, period.years, period.years)
    period.days == 0 -> period.toTotalMonths().toInt().let { pluralStringResource(R.plurals.settings_shelf_months, it, it) }
    else -> pluralStringResource(R.plurals.settings_shelf_days, period.days, period.days)
}

@Composable
private fun measurementsName(system: MeasureSystem): String = stringResource(
    when (system) {
        MeasureSystem.UsCustomary -> R.string.settings_measurements_us
        MeasureSystem.Metric -> R.string.settings_measurements_metric
    },
)

@Composable
private fun defaultLocationName(location: DefaultLocation): String = when (location) {
    DefaultLocation.LastUsed -> stringResource(R.string.settings_last_used)
    is DefaultLocation.Fixed -> locationName(location.location)
}

/** [time] as the phone shows times: the user's locale, and 24-hour if they chose it in Android's settings. */
@Composable
private fun timeText(time: LocalTime): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hma"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onChoose: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_digest)) },
        text = { TimePicker(picker) },
        confirmButton = {
            TextButton(onClick = { onChoose(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = PantryDimens.tapMin)
                            .selectable(selected = option == selected, role = Role.RadioButton) { onChoose(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = PantryTheme.colors.accent))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = PantryDimens.space3))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun LocationDialog(
    existing: Location.Custom?,
    onSave: (name: String, shelfLifeDays: Int?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var days by rememberSaveable { mutableStateOf(existing?.shelfLife?.days?.takeIf { it > 0 }?.toString().orEmpty()) }
    val parsedDays = days.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val daysValid = days.isBlank() || (parsedDays != null && parsedDays > 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.settings_add_location else R.string.settings_edit_location)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.settings_location_name)) })
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    singleLine = true,
                    isError = !daysValid,
                    label = { Text(stringResource(R.string.settings_location_days)) },
                    supportingText = { Text(stringResource(R.string.settings_location_days_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, parsedDays) }, enabled = name.isNotBlank() && daysValid) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.settings_location_delete), color = PantryTheme.colors.danger, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
            }
        },
    )
}

@Preview
@Composable
private fun SettingsLightPreview() = PantryTheme(darkTheme = false) { SettingsContent(SettingsState(), SettingsActions()) }

@Preview
@Composable
private fun SettingsDarkPreview() = PantryTheme(darkTheme = true) {
    SettingsContent(
        SettingsState(
            settings = PantrySettings(batchScanByDefault = true, notifyExpiringSoon = true),
            locations = Location.defaults + Location.Custom("Sample garage fridge", Period.ofDays(5), id = 4),
        ),
        SettingsActions(),
    )
}
