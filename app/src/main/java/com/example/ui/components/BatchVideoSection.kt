package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaStorageHelper
import com.example.core.MubaNotificationHelper
import com.example.core.MubaStrings
import com.example.data.model.BatchItemStatus
import com.example.data.model.PromptIdeaCatalog
import com.example.data.model.VideoExportFormat
import com.example.data.model.VideoQuality
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleGlow
import com.example.ui.viewmodel.BatchGenUiState
import com.example.ui.viewmodel.BatchPhotoItem
import com.example.ui.viewmodel.BatchVideoViewModel
import com.example.ui.viewmodel.VideoExportState

@Composable
fun BatchVideoSection(
    strings: MubaStrings,
    viewModel: BatchVideoViewModel
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val duration by viewModel.selectedDuration.collectAsStateWithLifecycle()
    val quality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val isBulkSaving by viewModel.isBulkSaving.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showCameraXCapture by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<BatchPhotoItem?>(null) }
    var exportTargetItem by remember { mutableStateOf<BatchPhotoItem?>(null) }
    var shareTargetItem by remember { mutableStateOf<BatchPhotoItem?>(null) }
    var lastExportFormat by remember { mutableStateOf(VideoExportFormat.MP4) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var dismissNotifTip by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(MubaNotificationHelper.areNotificationsEnabled(context))
    }
    var pendingBatchOnlyFailed by remember { mutableStateOf<Boolean?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = MubaNotificationHelper.areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            notificationsEnabled = granted && MubaNotificationHelper.areNotificationsEnabled(context)
            val onlyFailedArg = pendingBatchOnlyFailed
            if (onlyFailedArg != null) {
                pendingBatchOnlyFailed = null
                viewModel.startBatchGeneration(onlyFailed = onlyFailedArg)
            }
        }
    )

    val startBatchWithNotifPrompt: (Boolean) -> Unit = { onlyFailed ->
        val shouldPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MubaNotificationHelper.hasPostNotificationsPermission(context) &&
            !askedNotificationPermission &&
            items.isNotEmpty() &&
            prompt.isNotBlank()
        if (shouldPrompt) {
            askedNotificationPermission = true
            pendingBatchOnlyFailed = onlyFailed
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startBatchGeneration(onlyFailed = onlyFailed)
        }
    }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(BatchVideoViewModel.MAX_BATCH_PHOTOS),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.addPhotosFromUris(uris)
            }
        }
    )

    val launchMultiGallery = {
        multiPhotoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val launchCamera: () -> Unit = {
        showCameraXCapture = true
    }

    val isRunning = batchState is BatchGenUiState.Running
    val completedCount = items.count { it.status == BatchItemStatus.COMPLETED }
    val failedCount = items.count { it.status == BatchItemStatus.FAILED }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (statusMessage != null) {
            Surface(
                color = MubaPurple.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clickable { viewModel.clearStatusMessage() }
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Batch Queue Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (items.isNotEmpty()) MubaPurple else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(22.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MubaPurple.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BurstMode,
                                contentDescription = null,
                                tint = MubaPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Batch Photo Queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${items.size} / ${BatchVideoViewModel.MAX_BATCH_PHOTOS} photos selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MubaPurple
                            )
                        }
                    }

                    if (items.isNotEmpty() && !isRunning) {
                        TextButton(
                            onClick = viewModel::clearBatch,
                            modifier = Modifier.testTag("batch_clear_button")
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (items.isEmpty()) {
                    Text(
                        text = "Pick multiple photos at once (up to ${BatchVideoViewModel.MAX_BATCH_PHOTOS}) or snap photos with your camera to generate unique AI video variations using a single prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { idx, item ->
                            BatchThumbnailChip(
                                index = idx + 1,
                                item = item,
                                canRemove = !isRunning,
                                onRemove = { viewModel.removePhoto(item.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = launchMultiGallery,
                        enabled = !isRunning && items.size < BatchVideoViewModel.MAX_BATCH_PHOTOS,
                        modifier = Modifier
                            .testTag("batch_pick_photos_button")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MubaPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (items.isEmpty()) "Pick Photos" else "Add Photos")
                    }

                    OutlinedButton(
                        onClick = launchCamera,
                        enabled = !isRunning && items.size < BatchVideoViewModel.MAX_BATCH_PHOTOS,
                        modifier = Modifier
                            .testTag("batch_take_photo_button")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.takePhoto)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Shared Single Prompt for All Variations
        OutlinedTextField(
            value = prompt,
            onValueChange = viewModel::setPrompt,
            enabled = !isRunning,
            label = { Text("Single Prompt for All Variations") },
            placeholder = { Text(strings.promptHintVideo) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MubaPurple,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("batch_prompt_input")
        )

        Spacer(Modifier.height(14.dp))

        PromptIdeasSection(
            templates = PromptIdeaCatalog.videoTemplates,
            currentPrompt = prompt,
            onSelectTemplate = { template ->
                if (!isRunning) {
                    viewModel.setPrompt(template.promptText)
                }
            },
            subtitle = "Apply one curated cinematic direction across every queued photo."
        )

        Spacer(Modifier.height(16.dp))

        // Shared Duration & Quality Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.durationLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10).forEach { sec ->
                        val selected = duration == sec
                        BatchPill(
                            label = "${sec}s",
                            selected = selected,
                            enabled = !isRunning,
                            onClick = { viewModel.setDuration(sec) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_duration_${sec}s")
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Hd,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.qualityLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoQuality.entries.forEach { q ->
                        val selected = quality == q
                        BatchPill(
                            label = q.label,
                            selected = selected,
                            enabled = !isRunning,
                            onClick = { viewModel.setQuality(q) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_quality_${q.label.lowercase()}")
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val pendingCount = items.count { it.status == BatchItemStatus.QUEUED || it.status == BatchItemStatus.FAILED }
        val buttonLabel = when {
            isRunning -> "Processing Batch..."
            items.isEmpty() -> "Generate Batch Videos"
            pendingCount > 0 -> "Generate $pendingCount AI Video Variation${if (pendingCount == 1) "" else "s"}"
            else -> "Regenerate All ${items.size} Variations"
        }

        MubaPrimaryButton(
            text = buttonLabel,
            icon = Icons.Rounded.AutoAwesome,
            isLoading = isRunning,
            enabled = !isRunning,
            onClick = { startBatchWithNotifPrompt(false) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "batch_generate_button"
        )

        if (isRunning && !notificationsEnabled && !dismissNotifTip) {
            Spacer(Modifier.height(12.dp))
            NotificationPermissionTipBanner(
                onEnableClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !MubaNotificationHelper.hasPostNotificationsPermission(context)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        MubaNotificationHelper.openNotificationSettings(context)
                    }
                },
                onDismiss = { dismissNotifTip = true }
            )
        }

        val currentBatchState = batchState
        if (currentBatchState is BatchGenUiState.Running) {
            Spacer(Modifier.height(14.dp))
            val activeProgress = items.firstOrNull { it.status == BatchItemStatus.PROCESSING }?.progress ?: 0f
            val overallProgress = ((currentBatchState.completedCount + currentBatchState.failedCount + activeProgress) /
                currentBatchState.totalCount.coerceAtLeast(1)).coerceIn(0.04f, 1f)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MubaPurple.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Batch Progress • ${currentBatchState.completedCount + currentBatchState.failedCount} / ${currentBatchState.totalCount} done",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MubaPurple
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MubaPurple,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = viewModel::cancelBatchGeneration,
                        modifier = Modifier
                            .testTag("batch_cancel_button")
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel Batch")
                    }
                }
            }
        }

        if (completedCount > 0 || failedCount > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (completedCount > 0) {
                    OutlinedButton(
                        onClick = viewModel::saveAllCompletedToGallery,
                        enabled = !isRunning && !isBulkSaving,
                        modifier = Modifier
                            .testTag("batch_save_all_button")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                    ) {
                        if (isBulkSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MubaPurple
                            )
                        } else {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Save All ($completedCount)")
                    }
                }
                if (failedCount > 0 && !isRunning) {
                    OutlinedButton(
                        onClick = { startBatchWithNotifPrompt(true) },
                        modifier = Modifier
                            .testTag("batch_retry_failed_button")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Failed ($failedCount)")
                    }
                }
            }
        }

        val activeExport = exportState
        if (activeExport is VideoExportState.Exporting) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MubaPurple.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Exporting ${activeExport.format.displayName} (${(activeExport.progress * 100).toInt()}%)...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MubaPurple
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { activeExport.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MubaPurple,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Batch Queue & Variations (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { idx, item ->
                    BatchVariationResultCard(
                        index = idx + 1,
                        item = item,
                        onPlay = { previewItem = item },
                        onSave = { exportTargetItem = item },
                        onShare = { shareTargetItem = item }
                    )
                }
            }
        }
    }

    val activePreview = previewItem
    if (activePreview != null && !activePreview.localFileName.isNullOrBlank()) {
        val videoFile = remember(activePreview.localFileName) {
            MediaStorageHelper.getCreationFile(context, activePreview.localFileName!!)
        }
        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MubaObsidian.copy(alpha = 0.88f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth()
                        .border(1.dp, MubaPurple.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activePreview.label} • ${activePreview.durationSeconds}s ${activePreview.qualityLabel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = activePreview.motionStyle ?: "AI Video Variation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MubaPurple
                                )
                            }
                            IconButton(
                                onClick = { previewItem = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close preview",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        MubaVideoPlayer(
                            videoFile = videoFile,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportTargetItem = activePreview },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = { shareTargetItem = activePreview },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.share)
                            }
                        }
                    }
                }
            }
        }
    }

    val activeExportTarget = exportTargetItem
    if (activeExportTarget != null) {
        VideoExportFormatSheet(
            initialFormat = lastExportFormat,
            onDismiss = { exportTargetItem = null },
            onConfirmExport = { chosenFormat ->
                lastExportFormat = chosenFormat
                exportTargetItem = null
                viewModel.exportBatchItemToGallery(activeExportTarget, chosenFormat)
            }
        )
    }

    val activeShareTarget = shareTargetItem
    if (activeShareTarget != null) {
        SocialShareSheet(
            mediaTitle = "${activeShareTarget.label} • ${prompt.take(35)}",
            mediaTypeLabel = "Batch AI Video • MP4 (${activeShareTarget.durationSeconds}s)",
            mimeType = "video/mp4",
            defaultCaption = "Created with MUBA AI Batch Studio — ${prompt.take(110)} #MUBAAI",
            onDismiss = { shareTargetItem = null },
            onSelectPlatform = { platform, caption ->
                shareTargetItem = null
                viewModel.shareBatchItem(activeShareTarget, caption, platform)
            }
        )
    }

    if (showCameraXCapture) {
        CameraXCaptureDialog(
            projectTitle = "Batch AI Queue",
            onPhotoCaptured = { uri ->
                showCameraXCapture = false
                viewModel.addCapturedPhotoFromUri(uri)
            },
            onDismiss = { showCameraXCapture = false }
        )
    }
}

@Composable
private fun BatchThumbnailChip(
    index: Int,
    item: BatchPhotoItem,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = when (item.status) {
                    BatchItemStatus.COMPLETED -> Color(0xFF22C55E)
                    BatchItemStatus.PROCESSING -> MubaPurple
                    BatchItemStatus.FAILED -> MaterialTheme.colorScheme.error
                    BatchItemStatus.QUEUED -> MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            bitmap = item.bitmap.asImageBitmap(),
            contentDescription = item.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MubaObsidian.copy(alpha = 0.78f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "#$index",
                style = MaterialTheme.typography.labelSmall,
                color = MubaPurpleGlow,
                fontWeight = FontWeight.Bold
            )
        }

        if (canRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .testTag("batch_remove_photo_$index")
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MubaObsidian.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove ${item.label}",
                        tint = MubaPureWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchVariationResultCard(
    index: Int,
    item: BatchPhotoItem,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .testTag("batch_item_card_$index")
            .fillMaxWidth()
            .border(
                1.dp,
                when (item.status) {
                    BatchItemStatus.PROCESSING -> MubaPurple
                    BatchItemStatus.COMPLETED -> MubaPurple.copy(alpha = 0.4f)
                    BatchItemStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    BatchItemStatus.QUEUED -> MaterialTheme.colorScheme.outline
                },
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = item.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Variation #$index • ${item.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.stageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (item.status) {
                            BatchItemStatus.COMPLETED -> MubaPurple
                            BatchItemStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                when (item.status) {
                    BatchItemStatus.QUEUED -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "QUEUED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    BatchItemStatus.PROCESSING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MubaPurple,
                            strokeWidth = 2.5.dp
                        )
                    }
                    BatchItemStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    BatchItemStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (item.status == BatchItemStatus.PROCESSING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MubaPurple,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (item.status == BatchItemStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (item.status == BatchItemStatus.COMPLETED && !item.localFileName.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .testTag("batch_play_button_$index")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier
                            .testTag("batch_save_button_$index")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .testTag("batch_share_button_$index")
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MubaPurple else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) MubaPurple else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
        )
    }
}
