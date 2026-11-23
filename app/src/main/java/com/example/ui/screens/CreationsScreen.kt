package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.MediaStorageHelper
import com.example.core.MubaStrings
import com.example.data.model.Creation
import com.example.data.model.CreationType
import com.example.data.model.SocialPlatform
import com.example.data.model.UiState
import com.example.data.model.VideoExportFormat
import com.example.ui.components.CreationThumbnail
import com.example.ui.components.MubaVideoPlayer
import com.example.ui.components.SocialShareSheet
import com.example.ui.components.VideoEditorPanel
import com.example.ui.components.VideoExportFormatSheet
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleGlow
import com.example.ui.viewmodel.VideoExportState
import com.example.ui.viewmodel.VideoGenViewModel
import kotlinx.coroutines.launch

private enum class CreationFilterTab(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    IMAGES("Images")
}

@Composable
fun CreationsScreen(
    strings: MubaStrings,
    creationsState: UiState<List<Creation>>,
    selectedCreation: Creation?,
    onSelectCreation: (Creation?) -> Unit,
    onRenameCreation: (String, String) -> Unit,
    onDeleteCreation: (Creation) -> Unit,
    videoGenViewModel: VideoGenViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeFilter by remember { mutableStateOf(CreationFilterTab.ALL) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = strings.navCreations,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your private AI videos and images synced to your MUBA AI account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreationFilterTab.entries.forEach { tab ->
                    val selected = activeFilter == tab
                    Box(
                        modifier = Modifier
                            .testTag("creations_filter_${tab.name.lowercase()}")
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MubaPurple else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (selected) MubaPurple else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { activeFilter = tab }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (actionFeedback != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MubaPurple.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actionFeedback = null }
                ) {
                    Text(
                        text = actionFeedback.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (creationsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MubaPurple)
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = creationsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = creationsState.data.filter { item ->
                        when (activeFilter) {
                            CreationFilterTab.ALL -> true
                            CreationFilterTab.VIDEOS -> item.creationType == CreationType.VIDEO
                            CreationFilterTab.IMAGES -> item.creationType == CreationType.IMAGE
                        }
                    }
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MubaPurple.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MubaPurple,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = strings.noCreationsYet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 158.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filtered, key = { it.id }) { creation ->
                                CreationGridCard(
                                    creation = creation,
                                    onClick = { onSelectCreation(creation) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCreation != null) {
        BackHandler { onSelectCreation(null) }
        CreationDetailDialog(
            creation = selectedCreation,
            strings = strings,
            videoGenViewModel = videoGenViewModel,
            onDismiss = {
                videoGenViewModel.stopMusicPreview()
                onSelectCreation(null)
            },
            onDownload = { item, format ->
                if (item.creationType == CreationType.VIDEO) {
                    videoGenViewModel.downloadVideoToGallery(
                        fileName = item.localFileName,
                        title = item.title,
                        format = format
                    ) { msg ->
                        actionFeedback = msg
                    }
                } else {
                    scope.launch {
                        val file = MediaStorageHelper.getCreationFile(context, item.localFileName)
                        val safeName = "MUBA_AI_${item.title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(24)}_${System.currentTimeMillis()}"
                        val res = MediaStorageHelper.saveImageToGallery(context, file, "$safeName.jpg")
                        actionFeedback = res.fold(
                            onSuccess = { "Saved image to Pictures/MUBA AI!" },
                            onFailure = { it.localizedMessage ?: "Failed to save to Gallery." }
                        )
                    }
                }
            },
            onShare = { item, caption, platform ->
                val file = MediaStorageHelper.getCreationFile(context, item.localFileName)
                val mime = if (item.creationType == CreationType.VIDEO) "video/mp4" else "image/jpeg"
                val res = MediaStorageHelper.shareToSocialPlatform(
                    context = context,
                    file = file,
                    mimeType = mime,
                    title = item.title,
                    caption = caption,
                    platform = platform
                )
                actionFeedback = res.fold(
                    onSuccess = { it },
                    onFailure = { it.localizedMessage ?: "Unable to share this creation." }
                )
            },
            onRename = { newTitle ->
                onRenameCreation(selectedCreation.id, newTitle)
                onSelectCreation(selectedCreation.copy(title = newTitle))
            },
            onDelete = {
                onDeleteCreation(selectedCreation)
                onSelectCreation(null)
                actionFeedback = "Creation deleted."
            }
        )
    }
}

@Composable
private fun CreationGridCard(
    creation: Creation,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .testTag("creation_card_${creation.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
            ) {
                CreationThumbnail(
                    creation = creation,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MubaObsidian.copy(alpha = 0.78f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (creation.creationType == CreationType.VIDEO) {
                            "${creation.durationSeconds}s • ${creation.quality}"
                        } else {
                            creation.motionStyle ?: "1K"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MubaPurpleGlow
                    )
                }
                if (creation.creationType == CreationType.VIDEO) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play video",
                        tint = MubaPureWhite.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(38.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = creation.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = creation.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreationDetailDialog(
    creation: Creation,
    strings: MubaStrings,
    videoGenViewModel: VideoGenViewModel,
    onDismiss: () -> Unit,
    onDownload: (Creation, VideoExportFormat) -> Unit,
    onShare: (Creation, String, SocialPlatform) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val localFile = remember(creation.localFileName) {
        MediaStorageHelper.getCreationFile(context, creation.localFileName)
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var lastExportFormat by remember { mutableStateOf(VideoExportFormat.MP4) }

    val trimStart by videoGenViewModel.trimStartSec.collectAsStateWithLifecycle()
    val trimEnd by videoGenViewModel.trimEndSec.collectAsStateWithLifecycle()
    val selectedFilter by videoGenViewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedMusic by videoGenViewModel.selectedMusic.collectAsStateWithLifecycle()
    val previewingTrack by videoGenViewModel.previewingTrack.collectAsStateWithLifecycle()
    val isApplyingEdits by videoGenViewModel.isApplyingEdits.collectAsStateWithLifecycle()
    val editProgress by videoGenViewModel.editProgress.collectAsStateWithLifecycle()
    val exportState by videoGenViewModel.exportState.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MubaObsidian.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .border(1.dp, MubaPurple.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = creation.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = { showRenameDialog = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename",
                                    tint = MubaPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .testTag("creation_detail_close")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (creation.creationType == CreationType.VIDEO) {
                        MubaVideoPlayer(
                            videoFile = localFile,
                            previewFilter = if (showEditor) selectedFilter else com.example.data.model.VideoFilter.ORIGINAL,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MubaObsidian),
                            contentAlignment = Alignment.Center
                        ) {
                            if (localFile.exists()) {
                                AsyncImage(
                                    model = localFile,
                                    contentDescription = creation.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CreationThumbnail(
                                    creation = creation,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = creation.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (creation.creationType == CreationType.VIDEO) {
                                    showExportFormatSheet = true
                                } else {
                                    onDownload(creation, VideoExportFormat.MP4)
                                }
                            },
                            enabled = exportState is VideoExportState.Idle,
                            modifier = Modifier
                                .testTag("creation_download_button")
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
                                .testTag("creation_share_button")
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.share)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .testTag("creation_delete_button")
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = strings.delete, Modifier.size(18.dp))
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

                    if (creation.creationType == CreationType.VIDEO && localFile.exists()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (!showEditor) {
                                    val dur = creation.durationSeconds.toFloat().coerceAtLeast(1f)
                                    videoGenViewModel.updateTrimRange(0f, dur)
                                }
                                showEditor = !showEditor
                            },
                            modifier = Modifier
                                .testTag("creation_edit_video_button")
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                        ) {
                            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (showEditor) "Hide Video Editor" else strings.editVideo)
                        }

                        if (showEditor) {
                            Spacer(Modifier.height(12.dp))
                            VideoEditorPanel(
                                maxDurationSec = creation.durationSeconds.toFloat().coerceAtLeast(1f),
                                trimStartSec = trimStart,
                                trimEndSec = trimEnd,
                                selectedFilter = selectedFilter,
                                selectedMusic = selectedMusic,
                                previewingTrack = previewingTrack,
                                isApplyingEdits = isApplyingEdits,
                                editProgress = editProgress,
                                onTrimChanged = videoGenViewModel::updateTrimRange,
                                onFilterSelected = videoGenViewModel::selectFilter,
                                onMusicSelected = videoGenViewModel::selectAndPreviewMusic,
                                onStopMusicPreview = videoGenViewModel::stopMusicPreview,
                                onApplyEdits = {
                                    videoGenViewModel.applyEditsToCurrentVideo(
                                        sourceFileName = creation.localFileName,
                                        originalTitle = creation.title,
                                        originalPrompt = creation.prompt,
                                        qualityLabel = creation.quality,
                                        onEditedSaved = { _, _ ->
                                            showEditor = false
                                            onDismiss()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
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
                onDownload(creation, chosenFormat)
            }
        )
    }

    if (showShareSheet) {
        val isVideo = creation.creationType == CreationType.VIDEO
        val mime = if (isVideo) "video/mp4" else "image/jpeg"
        val typeLabel = if (isVideo) {
            "AI Video • MP4 (${creation.durationSeconds}s)"
        } else {
            "AI Image • JPG"
        }
        SocialShareSheet(
            mediaTitle = creation.title,
            mediaTypeLabel = typeLabel,
            mimeType = mime,
            defaultCaption = "Created with MUBA AI — ${creation.prompt.take(120)} #MUBAAI",
            onDismiss = { showShareSheet = false },
            onSelectPlatform = { platform, caption ->
                showShareSheet = false
                onShare(creation, caption, platform)
            }
        )
    }

    if (showRenameDialog) {
        var titleInput by remember(creation.title) { mutableStateOf(creation.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Creation") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            onRename(titleInput.trim())
                            showRenameDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Creation?") },
            text = { Text("This removes \"${creation.title}\" from your MUBA AI library and device storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
