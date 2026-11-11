package io.cosgriff.pantry.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ItemDraft
import io.cosgriff.pantry.domain.Location
import io.cosgriff.pantry.ui.components.DateDialog
import io.cosgriff.pantry.ui.components.FieldLabel
import io.cosgriff.pantry.ui.components.LocationPicker
import io.cosgriff.pantry.ui.components.QuantityStepper
import io.cosgriff.pantry.ui.components.barcodeText
import io.cosgriff.pantry.ui.components.quantityText
import io.cosgriff.pantry.ui.theme.PantryDimens
import io.cosgriff.pantry.ui.theme.PantryTheme
import java.time.LocalDate
import java.util.concurrent.Executors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.awaitCancellation

/**
 * The Scan screen (#10): the camera permission page until the camera may be used, then the camera with the scan
 * frame, and the sheet for each product read. [onByHand] opens Item details for a new item with that name and
 * barcode (empty when there is none).
 */
@Composable
fun ScanScreen(onClose: () -> Unit, onByHand: (name: String, barcode: String) -> Unit, viewModel: ScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCamera()) }
    // Set once Android's prompt was answered with no: asking again shows nothing, so Settings is offered instead.
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
    }
    // Coming back from Android settings with the permission switched on.
    LifecycleResumeEffect(Unit) {
        granted = context.hasCamera()
        onPauseOrDispose {}
    }
    if (!granted) {
        CameraPermissionContent(
            refused = refused,
            onAllow = {
                if (refused) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else {
                    ask.launch(Manifest.permission.CAMERA)
                }
            },
            onByHand = { onByHand("", "") },
            onBack = onClose,
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    var torch by rememberSaveable { mutableStateOf(false) }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    DisposableEffect(analysis) {
        val executor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor, RetailBarcodeAnalyzer(paused = { viewModel.paused }, onRead = viewModel::onRead))
        onDispose {
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
    ScanContent(
        state = state,
        torch = torch,
        actions = ScanActions(
            onClose = onClose,
            onTorch = { torch = !torch },
            onName = viewModel::onName,
            onLocation = viewModel::onLocation,
            onStep = viewModel::onStep,
            onExpires = viewModel::onExpires,
            onAdd = viewModel::onAdd,
            onDismiss = viewModel::onDismiss,
            onMoreDetails = { draft -> viewModel.onDismiss(); onByHand(draft.name, draft.barcode.orEmpty()) },
            onByHand = { viewModel.onDismiss(); onByHand("", "") },
        ),
        camera = { CameraFeed(analysis, torch) },
    )
}

private fun Context.hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

data class ScanActions(
    val onClose: () -> Unit = {},
    val onTorch: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onLocation: (Location) -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onExpires: (LocalDate?) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMoreDetails: (ItemDraft) -> Unit = {},
    val onByHand: () -> Unit = {},
)

/** `design/source/Scan.dc.html`. [camera] is the live picture; previews leave it dark. */
@Composable
fun ScanContent(state: ScanState, torch: Boolean, actions: ScanActions, modifier: Modifier = Modifier, camera: @Composable () -> Unit = {}) {
    val colors = PantryTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.scannerGround),
    ) {
        camera()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = PantryDimens.space4, vertical = PantryDimens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(R.drawable.ic_clear, stringResource(R.string.scan_close), actions.onClose)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.batch) {
                        Row(
                            Modifier
                                .height(36.dp)
                                .background(colors.scannerScrim, MaterialTheme.shapes.extraLarge)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PantryDimens.space2),
                        ) {
                            Box(Modifier.size(8.dp).background(colors.scannerLive, MaterialTheme.shapes.extraLarge))
                            Text(
                                pluralStringResource(R.plurals.scan_batch_count, state.added, state.added),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.scannerOn,
                            )
                        }
                    }
                }
                RoundButton(R.drawable.ic_flash, stringResource(R.string.scan_torch), actions.onTorch, on = torch)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                ScanFrame()
                Text(stringResource(R.string.scan_point), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = colors.scannerOn)
            }
            when (val phase = state.phase) {
                is ScanPhase.Found -> Sheet { FoundSheet(phase, state, actions) }
                ScanPhase.StorePacked -> Sheet { StorePackedSheet(actions) }
                ScanPhase.Looking -> Unit
            }
        }
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, onClick: () -> Unit, on: Boolean = false) {
    val colors = PantryTheme.colors
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (on) colors.scannerOn else colors.scannerScrim,
            contentColor = if (on) colors.scannerGround else colors.scannerOn,
        ),
    ) {
        Icon(painterResource(icon), label, modifier = Modifier.size(22.dp))
    }
}

/** The four white corners and the live line of the board's scan frame. Decorative. */
@Composable
private fun ScanFrame() {
    val colors = PantryTheme.colors
    Canvas(Modifier.size(width = 280.dp, height = 150.dp)) {
        val w = 4.dp.toPx()
        val arm = 34.dp.toPx()
        val r = 14.dp.toPx()
        val corners = listOf(Offset(0f, 0f), Offset(size.width - arm, 0f), Offset(0f, size.height - arm), Offset(size.width - arm, size.height - arm))
        corners.forEachIndexed { i, o ->
            // Each corner is a quarter of a rounded square, drawn by clipping a full one.
            val left = i % 2 == 0
            val top = i < 2
            drawContext.canvas.save()
            drawContext.canvas.clipRect(o.x, o.y, o.x + arm, o.y + arm)
            drawRoundRect(
                color = colors.scannerOn,
                topLeft = Offset(if (left) w / 2 else o.x - arm + w / 2, if (top) w / 2 else o.y - arm + w / 2),
                size = Size(2 * arm - w, 2 * arm - w),
                cornerRadius = CornerRadius(r),
                style = Stroke(w),
            )
            drawContext.canvas.restore()
        }
        val inset = 20.dp.toPx()
        drawLine(colors.scannerLive, Offset(inset, size.height / 2), Offset(size.width - inset, size.height / 2), 2.dp.toPx())
    }
}

@Composable
private fun Sheet(content: @Composable () -> Unit) {
    val colors = PantryTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)))
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = PantryDimens.space5, end = PantryDimens.space5, top = 10.dp, bottom = PantryDimens.space5),
        verticalArrangement = Arrangement.spacedBy(PantryDimens.space4),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .background(colors.borderStrong, MaterialTheme.shapes.extraSmall),
        )
        content()
    }
}

@Composable
private fun FoundSheet(found: ScanPhase.Found, state: ScanState, actions: ScanActions) {
    val colors = PantryTheme.colors
    val draft = found.draft
    var picking by rememberSaveable { mutableStateOf(false) }
    val nameLabel = stringResource(R.string.details_name)
    val nameField: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(
            value = draft.name,
            onValueChange = actions.onName,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            modifier = modifier
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
    }
    if (found.known) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The photo waits for product lookup (#9).
            Box(
                Modifier
                    .size(64.dp)
                    .background(colors.surfaceMuted, MaterialTheme.shapes.medium)
                    .border(1.dp, colors.borderStrong, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.scan_product_photo), style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.scan_known), style = MaterialTheme.typography.labelSmall, color = colors.accent)
                }
                nameField(Modifier)
                draft.barcode?.let { Text(barcodeText(it), style = PantryTheme.text.barcode, color = colors.inkMuted) }
            }
        }
    } else {
        // `design/source/ScanNotFound.dc.html` (#12): name it once, and the next scan fills it in.
        Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space1)) {
            Text(stringResource(R.string.scan_no_match), style = MaterialTheme.typography.labelSmall, color = colors.warning)
            Text(stringResource(R.string.scan_name_once), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.scan_name_once_body), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            draft.barcode?.let { Text(barcodeText(it), style = PantryTheme.text.barcode, color = colors.inkMuted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(nameLabel)
            nameField(
                Modifier
                    .border(1.dp, colors.border, MaterialTheme.shapes.small)
                    .padding(horizontal = PantryDimens.space3),
            )
        }
    }
    LocationPicker(draft.location, state.locations, actions.onLocation, label = stringResource(R.string.scan_store_in))
    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.details_quantity))
            QuantityStepper(quantityText(draft.quantity), actions.onStep)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.details_expires))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(PantryDimens.fieldHeight)
                    .background(colors.surface, MaterialTheme.shapes.small)
                    .border(1.dp, colors.border, MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.details_pick_date)) { picking = true }
                    .padding(horizontal = PantryDimens.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    draft.expires?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) } ?: stringResource(R.string.details_no_date),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (!draft.expiresByHand && draft.expires != null) {
                    Text(stringResource(R.string.scan_suggested), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = colors.inkMuted)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A known product can go on to Item details; an unknown one is skipped or named here, as on its board.
        OutlinedButton(
            onClick = { if (found.known) actions.onMoreDetails(draft) else actions.onDismiss() },
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            modifier = Modifier
                .weight(1f)
                .height(PantryDimens.buttonHeight),
        ) { Text(stringResource(if (found.known) R.string.scan_more_details else R.string.scan_skip), style = MaterialTheme.typography.labelLarge) }
        Button(
            onClick = actions.onAdd,
            enabled = draft.name.isNotBlank(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier
                .weight(1.6f)
                .height(PantryDimens.buttonHeight),
        ) { Text(stringResource(if (state.batch) R.string.scan_add_keep else R.string.scan_add), style = MaterialTheme.typography.labelLarge) }
    }
    if (picking) {
        DateDialog(initial = draft.expires ?: state.today, onPick = { picking = false; actions.onExpires(it) }, onDismiss = { picking = false })
    }
}

@Composable
private fun StorePackedSheet(actions: ScanActions) {
    val colors = PantryTheme.colors
    Text(stringResource(R.string.scan_store_packed), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = actions.onDismiss,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            modifier = Modifier
                .weight(1f)
                .height(PantryDimens.buttonHeight),
        ) { Text(stringResource(R.string.details_cancel), style = MaterialTheme.typography.labelLarge) }
        Button(
            onClick = actions.onByHand,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier
                .weight(1.6f)
                .height(PantryDimens.buttonHeight),
        ) { Text(stringResource(R.string.pantry_empty_add_by_hand), style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * The back camera's picture filling the screen with [analysis] bound beside it; [torch] is the flashlight.
 * As in glovebox's camera screens.
 */
@Composable
private fun CameraFeed(analysis: ImageAnalysis, torch: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val preview = remember { CameraPreview.Builder().build().apply { setSurfaceProvider { request = it } } }
    LaunchedEffect(analysis, lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // No back camera: the screen stays dark; the sheet's "by hand" path still works.
        } finally {
            provider.unbind(preview, analysis)
            camera = null
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    val surface = request ?: return
    CameraXViewfinder(
        surfaceRequest = surface,
        implementationMode = ImplementationMode.EMBEDDED,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** `design/source/ScanPermission.dc.html`: why the camera is needed, before Android's prompt. */
@Composable
fun CameraPermissionContent(refused: Boolean, onAllow: () -> Unit, onByHand: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PantryTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(PantryDimens.space2)) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.details_back))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PantryDimens.space6),
            verticalArrangement = Arrangement.spacedBy(PantryDimens.space5, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(96.dp).background(colors.surfaceMuted, MaterialTheme.shapes.large), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_scan), null, tint = colors.accent, modifier = Modifier.size(48.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.scan_permission_body), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                listOf(R.string.scan_permission_point_batch, R.string.scan_permission_point_remember, R.string.scan_permission_point_settings).forEach {
                    Row(horizontalArrangement = Arrangement.spacedBy(PantryDimens.space3)) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Column(
            Modifier.padding(start = PantryDimens.space5, end = PantryDimens.space5, top = PantryDimens.space4, bottom = PantryDimens.space5),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onAllow,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
            ) { Text(stringResource(if (refused) R.string.scan_permission_settings else R.string.scan_permission_allow), style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = onByHand,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                modifier = Modifier.fillMaxWidth().height(PantryDimens.buttonHeight),
            ) { Text(stringResource(R.string.scan_permission_by_hand), style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(PantryDimens.space1))
    }
}

// Previews use made-up products, marked as samples. The camera stays dark in a preview.
private val previewToday = LocalDate.of(2026, 11, 10)

private fun previewFound(known: Boolean) = ScanState(
    today = previewToday,
    batch = true,
    added = 6,
    phase = ScanPhase.Found(
        ItemDraft.new(previewToday, Location.Dry, barcode = "041220576913").copy(name = if (known) "Sample sparkling water" else ""),
        known = known,
    ),
)

@Preview
@Composable
private fun ScanKnownPreview() = PantryTheme(darkTheme = false) { ScanContent(previewFound(known = true), torch = false, actions = ScanActions()) }

@Preview
@Composable
private fun ScanUnknownDarkPreview() = PantryTheme(darkTheme = true) { ScanContent(previewFound(known = false), torch = false, actions = ScanActions()) }

@Preview
@Composable
private fun CameraPermissionPreview() = PantryTheme(darkTheme = false) { CameraPermissionContent(refused = false, onAllow = {}, onByHand = {}, onBack = {}) }
