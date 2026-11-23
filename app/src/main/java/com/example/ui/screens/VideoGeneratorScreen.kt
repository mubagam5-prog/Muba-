package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaStorageHelper
import com.example.core.MubaNotificationHelper
import com.example.core.MubaStrings
import com.example.data.model.PromptIdeaCatalog
import com.example.data.model.VideoExportFormat
import com.example.data.model.VideoQuality
import com.example.ui.components.BatchVideoSection
import com.example.ui.components.CameraXCaptureDialog
import com.example.ui.components.MubaPrimaryButton
import com.example.ui.components.MubaShimmerBox
import com.example.ui.components.MubaVideoPlayer
import com.example.ui.components.NotificationPermissionTipBanner
import com.example.ui.components.PromptIdeasSection
import com.example.ui.components.SocialShareSheet
import com.example.ui.components.VideoEditorPanel
import com.example.ui.components.VideoExportFormatSheet
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.viewmodel.BatchVideoViewModel
import com.example.ui.viewmodel.VideoExportState
import com.example.ui.viewmodel.VideoGenUiState
import com.example.ui.viewmodel.VideoGenViewModel

@Composable
fun VideoGeneratorScreen(
    strings: MubaStrings,
    viewModel: VideoGenViewModel,
    batchViewModel: BatchVideoViewModel,
    isBatchMode: Boolean,
    onBatchModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val batchItems by batchViewModel.items.collectAsStateWithLifecycle()
    val selectedBitmap by viewModel.selectedPhotoBitmap.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val duration by viewModel.selectedDuration.collectAsStateWithLifecycle()
    val quality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // Editor state
    val trimStart by viewModel.trimStartSec.collectAsStateWithLifecycle()
    val trimEnd by viewModel.trimEndSec.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedMusic by viewModel.selectedMusic.collectAsStateWithLifecycle()
    val previewingTrack by viewModel.previewingTrack.collectAsStateWithLifecycle()
    val isApplyingEdits by viewModel.isApplyingEdits.collectAsStateWithLifecycle()
    val editProgress by viewModel.editProgress.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(true) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var lastExportFormat by remember { mutableStateOf(VideoExportFormat.MP4) }
    val scrollState = rememberScrollState()

    var showCameraXCapture by remember { mutableStateOf(false) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var dismissNotifTip by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(MubaNotificationHelper.areNotificationsEnabled(context))
    }
    var pendingGenerateAfterPerm by remember { mutableStateOf(false) }

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
            if (pendingGenerateAfterPerm) {
                pendingGenerateAfterPerm = false
                viewModel.generateVideo()
            }
        }
    )

    val startVideoGenerationWithNotifPrompt: () -> Unit = {
        val shouldPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MubaNotificationHelper.hasPostNotificationsPermission(context) &&
            !askedNotificationPermission &&
            selectedBitmap != null &&
            prompt.isNotBlank()
        if (shouldPrompt) {
            askedNotificationPermission = true
            pendingGenerateAfterPerm = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.generateVideo()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoGenUiState.Generating || uiState is VideoGenUiState.Ready) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.setPhotoFromUri(uri)
            }
        }
    )

    val launchGallery = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val launchCamera: () -> Unit = {
        showCameraXCapture = true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = strings.createAiVideo,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (isBatchMode) {
                    "Select multiple photos and generate unique AI video variations from one prompt."
                } else {
                    "Upload any photo, describe the motion, and generate a real MP4 video."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            // Single Photo vs Batch Studio Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSwitchTab(
                    label = strings.singleMode,
                    icon = Icons.Rounded.MovieCreation,
                    selected = !isBatchMode,
                    onClick = { onBatchModeChange(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("video_mode_single")
                )
                ModeSwitchTab(
                    label = if (batchItems.isNotEmpty()) "${strings.batchMode} (${batchItems.size})" else strings.batchMode,
                    icon = Icons.Rounded.BurstMode,
                    selected = isBatchMode,
                    onClick = { onBatchModeChange(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("video_mode_batch")
                )
            }

            Spacer(Modifier.height(16.dp))

            if (isBatchMode) {
                BatchVideoSection(
                    strings = strings,
                    viewModel = batchViewModel
                )
                Spacer(Modifier.height(32.dp))
                return@Column
            }

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

            // Photo Upload Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (selectedBitmap != null) 216.dp else 224.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.5.dp,
                        if (selectedBitmap != null) MubaPurple else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(id = R.string.cd_selected_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { launchGallery() }
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .testTag("video_upload_photo_button")
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { launchGallery() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = strings.uploadPhoto,
                                    tint = MubaPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Gallery",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .testTag("video_take_photo_button")
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { launchCamera() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = strings.takePhoto,
                                    tint = MubaPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = strings.takePhoto,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MubaPurple.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = strings.uploadPhoto,
                                tint = MubaPurple,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Add a Source Photo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Choose from your Gallery or snap a new photo with your Camera",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = launchGallery,
                                modifier = Modifier
                                    .testTag("video_upload_photo_button")
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
                                Text("Gallery")
                            }

                            OutlinedButton(
                                onClick = launchCamera,
                                modifier = Modifier
                                    .testTag("video_take_photo_button")
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
            }

            Spacer(Modifier.height(16.dp))

            // Prompt input
            OutlinedTextField(
                value = prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Motion & Scene Prompt") },
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
                    .testTag("video_prompt_input")
            )

            Spacer(Modifier.height(14.dp))

            PromptIdeasSection(
                templates = PromptIdeaCatalog.videoTemplates,
                currentPrompt = prompt,
                onSelectTemplate = { template ->
                    viewModel.setPrompt(template.promptText)
                },
                subtitle = "Curated motion & atmosphere templates to animate your photo smoothly."
            )

            Spacer(Modifier.height(16.dp))

            // Duration & Quality selectors
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
                            SelectorPill(
                                label = "${sec}s",
                                selected = selected,
                                onClick = { viewModel.setDuration(sec) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("video_duration_${sec}s")
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
                            SelectorPill(
                                label = q.label,
                                selected = selected,
                                onClick = { viewModel.setQuality(q) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("video_quality_${q.label.lowercase()}")
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            val isGenerating = uiState is VideoGenUiState.Generating
            val currentError = (uiState as? VideoGenUiState.Error)?.message
            if (currentError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            MubaPrimaryButton(
                text = if (isGenerating) "Generating Video..." else strings.generateVideo,
                icon = Icons.Rounded.AutoAwesome,
                isLoading = isGenerating,
                enabled = !isGenerating,
                onClick = startVideoGenerationWithNotifPrompt,
                modifier = Modifier.fillMaxWidth(),
                testTag = "video_generate_button"
            )

            if (isGenerating && !notificationsEnabled && !dismissNotifTip) {
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

            Spacer(Modifier.height(20.dp))

            when (val state = uiState) {
                is VideoGenUiState.Idle, is VideoGenUiState.Error -> Unit
                is VideoGenUiState.Generating -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MubaPurple.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            MubaShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp),
                                label = state.stageLabel
                            )
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { state.progress.coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MubaPurple,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${(state.progress * 100).toInt()}% Complete",
                                style = MaterialTheme.typography.labelMedium,
                                color = MubaPurple
                            )
                        }
                    }
                }
                is VideoGenUiState.Ready -> {
                    val videoFile = remember(state.localFileName) {
                        MediaStorageHelper.getCreationFile(context, state.localFileName)
                    }

                    Text(
                        text = "Generated Preview • ${state.durationSeconds}s • ${state.qualityLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "AI Style: ${state.motionStyle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MubaPurple
                    )
                    Spacer(Modifier.height(10.dp))

                    MubaVideoPlayer(
                        videoFile = videoFile,
                        previewFilter = selectedFilter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExportFormatSheet = true },
                            enabled = exportState is VideoExportState.Idle,
                            modifier = Modifier
                                .testTag("video_download_button")
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }

                        OutlinedButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier
                                .testTag("video_share_button")
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.share)
                        }

                        OutlinedButton(
                            onClick = { showEditor = !showEditor },
                            modifier = Modifier
                                .testTag("video_toggle_editor_button")
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                        ) {
                            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
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

                    if (showExportFormatSheet) {
                        VideoExportFormatSheet(
                            initialFormat = lastExportFormat,
                            onDismiss = { showExportFormatSheet = false },
                            onConfirmExport = { chosenFormat ->
                                lastExportFormat = chosenFormat
                                showExportFormatSheet = false
                                viewModel.downloadVideoToGallery(
                                    fileName = state.localFileName,
                                    title = state.prompt,
                                    format = chosenFormat
                                )
                            }
                        )
                    }

                    if (showShareSheet) {
                        SocialShareSheet(
                            mediaTitle = state.prompt.take(50),
                            mediaTypeLabel = "AI Video • MP4 (${state.durationSeconds}s)",
                            mimeType = "video/mp4",
                            defaultCaption = "Created with MUBA AI — ${state.prompt.take(120)} #MUBAAI",
                            onDismiss = { showShareSheet = false },
                            onSelectPlatform = { platform, caption ->
                                showShareSheet = false
                                viewModel.shareVideo(
                                    fileName = state.localFileName,
                                    title = state.prompt.take(60),
                                    caption = caption,
                                    platform = platform
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = viewModel::resetToIdle,
                        modifier = Modifier
                            .testTag("video_new_button")
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Another Video")
                    }

                    Spacer(Modifier.height(16.dp))

                    AnimatedVisibility(visible = showEditor) {
                        VideoEditorPanel(
                            maxDurationSec = state.durationSeconds.toFloat().coerceAtLeast(1f),
                            trimStartSec = trimStart,
                            trimEndSec = trimEnd,
                            selectedFilter = selectedFilter,
                            selectedMusic = selectedMusic,
                            previewingTrack = previewingTrack,
                            isApplyingEdits = isApplyingEdits,
                            editProgress = editProgress,
                            onTrimChanged = viewModel::updateTrimRange,
                            onFilterSelected = viewModel::selectFilter,
                            onMusicSelected = viewModel::selectAndPreviewMusic,
                            onStopMusicPreview = viewModel::stopMusicPreview,
                            onApplyEdits = {
                                viewModel.applyEditsToCurrentVideo(
                                    sourceFileName = state.localFileName,
                                    originalTitle = state.prompt.split(" ").take(5).joinToString(" "),
                                    originalPrompt = state.prompt,
                                    qualityLabel = state.qualityLabel
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        if (showCameraXCapture) {
            CameraXCaptureDialog(
                projectTitle = "AI Video Photo",
                onPhotoCaptured = { uri ->
                    showCameraXCapture = false
                    viewModel.setPhotoFromUri(uri)
                },
                onDismiss = { showCameraXCapture = false }
            )
        }
    }
}

@Composable
private fun SelectorPill(
    label: String,
    selected: Boolean,
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ModeSwitchTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MubaPureWhite else MubaPurple,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
