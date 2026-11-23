package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.MediaStorageHelper
import com.example.core.MubaNotificationHelper
import com.example.core.VideoGenerationKeepAliveService
import com.example.core.VideoSynthesisEngine
import com.example.data.model.BatchItemStatus
import com.example.data.model.CreationType
import com.example.data.model.SocialPlatform
import com.example.data.model.VideoExportFormat
import com.example.data.model.VideoQuality
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.CreationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class BatchPhotoItem(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val label: String,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val progress: Float = 0f,
    val stageLabel: String = "Queued",
    val creationId: String? = null,
    val localFileName: String? = null,
    val motionStyle: String? = null,
    val durationSeconds: Int = 5,
    val qualityLabel: String = "HD",
    val errorMessage: String? = null
)

sealed interface BatchGenUiState {
    data object Idle : BatchGenUiState
    data class Running(
        val completedCount: Int,
        val failedCount: Int,
        val totalCount: Int,
        val activeIndex: Int
    ) : BatchGenUiState

    data class Finished(
        val completedCount: Int,
        val failedCount: Int,
        val totalCount: Int
    ) : BatchGenUiState
}

class BatchVideoViewModel(
    private val aiRepository: AiStudioRepository,
    private val creationRepository: CreationRepository,
    private val appContext: Context,
    private val currentUserId: String
) : ViewModel() {

    private var batchJob: Job? = null

    private val _items = MutableStateFlow<List<BatchPhotoItem>>(emptyList())
    val items: StateFlow<List<BatchPhotoItem>> = _items.asStateFlow()

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _selectedDuration = MutableStateFlow(5)
    val selectedDuration: StateFlow<Int> = _selectedDuration.asStateFlow()

    private val _selectedQuality = MutableStateFlow(VideoQuality.HD)
    val selectedQuality: StateFlow<VideoQuality> = _selectedQuality.asStateFlow()

    private val _batchState = MutableStateFlow<BatchGenUiState>(BatchGenUiState.Idle)
    val batchState: StateFlow<BatchGenUiState> = _batchState.asStateFlow()

    private val _exportState = MutableStateFlow<VideoExportState>(VideoExportState.Idle)
    val exportState: StateFlow<VideoExportState> = _exportState.asStateFlow()

    private val _isBulkSaving = MutableStateFlow(false)
    val isBulkSaving: StateFlow<Boolean> = _isBulkSaving.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setPrompt(value: String) {
        _prompt.value = value
    }

    fun setDuration(seconds: Int) {
        _selectedDuration.value = if (seconds == 10) 10 else 5
    }

    fun setQuality(quality: VideoQuality) {
        _selectedQuality.value = quality
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun addPhotosFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_batchState.value is BatchGenUiState.Running) {
            _statusMessage.value = "Wait for or cancel the active batch before adding more photos."
            return
        }
        viewModelScope.launch {
            val existingCount = _items.value.size
            val remainingSlots = (MAX_BATCH_PHOTOS - existingCount).coerceAtLeast(0)
            if (remainingSlots == 0) {
                _statusMessage.value = "Batch limit reached ($MAX_BATCH_PHOTOS photos max)."
                return@launch
            }
            val selectedSlice = uris.take(remainingSlots)
            val newItems = mutableListOf<BatchPhotoItem>()
            var failedDecode = 0
            selectedSlice.forEachIndexed { idx, uri ->
                val decoded = MediaStorageHelper.decodeSampledBitmapFromUri(appContext, uri, 1280)
                if (decoded != null) {
                    newItems.add(
                        BatchPhotoItem(
                            bitmap = decoded,
                            label = "Photo ${existingCount + newItems.size + 1}"
                        )
                    )
                } else {
                    failedDecode++
                }
            }
            if (newItems.isNotEmpty()) {
                _items.update { current -> current + newItems }
                if (_batchState.value is BatchGenUiState.Finished) {
                    _batchState.value = BatchGenUiState.Idle
                }
            }
            val skippedByLimit = uris.size - selectedSlice.size
            _statusMessage.value = when {
                skippedByLimit > 0 ->
                    "Added ${newItems.size} photo(s). $skippedByLimit skipped (max $MAX_BATCH_PHOTOS photos per batch)."
                failedDecode > 0 && newItems.isEmpty() ->
                    "Could not load selected photos. Please choose different images."
                failedDecode > 0 ->
                    "Added ${newItems.size} photo(s) ($failedDecode could not be decoded)."
                else -> null
            }
        }
    }

    fun addCapturedPhotoFromUri(uri: Uri) {
        addPhotosFromUris(listOf(uri))
    }

    fun removePhoto(itemId: String) {
        if (_batchState.value is BatchGenUiState.Running) return
        _items.update { list ->
            list.filterNot { it.id == itemId }.mapIndexed { idx, item ->
                item.copy(label = "Photo ${idx + 1}")
            }
        }
        if (_items.value.isEmpty()) {
            _batchState.value = BatchGenUiState.Idle
        }
    }

    fun clearBatch() {
        if (_batchState.value is BatchGenUiState.Running) return
        _items.value = emptyList()
        _batchState.value = BatchGenUiState.Idle
    }

    fun startBatchGeneration(onlyFailed: Boolean = false) {
        if (batchJob?.isActive == true) return
        val currentItems = _items.value
        if (currentItems.isEmpty()) {
            _statusMessage.value = "Please select 2 or more photos (at least 1) to start batch processing."
            return
        }
        val promptText = _prompt.value.trim()
        if (promptText.isEmpty()) {
            _statusMessage.value = "Please enter a single prompt to guide all video variations."
            return
        }

        // If all items were already completed and user taps Generate again, reset all to queued for fresh variations
        val preparedItems = if (!onlyFailed && currentItems.all { it.status == BatchItemStatus.COMPLETED }) {
            currentItems.map {
                it.copy(
                    status = BatchItemStatus.QUEUED,
                    progress = 0f,
                    stageLabel = "Queued",
                    errorMessage = null
                )
            }
        } else {
            currentItems.map { item ->
                val shouldRun = if (onlyFailed) item.status == BatchItemStatus.FAILED
                else item.status != BatchItemStatus.COMPLETED
                if (shouldRun) {
                    item.copy(
                        status = BatchItemStatus.QUEUED,
                        progress = 0f,
                        stageLabel = "Queued",
                        errorMessage = null
                    )
                } else {
                    item
                }
            }
        }
        _items.value = preparedItems

        val targetIds = preparedItems.filter { it.status == BatchItemStatus.QUEUED }.map { it.id }
        if (targetIds.isEmpty()) {
            _statusMessage.value = "No queued or failed photos to process."
            return
        }

        val duration = _selectedDuration.value
        val quality = _selectedQuality.value

        batchJob = viewModelScope.launch {
            var completedCount = preparedItems.count { it.status == BatchItemStatus.COMPLETED }
            var failedCount = preparedItems.count { it.status == BatchItemStatus.FAILED }
            val totalCount = preparedItems.size
            var keyframeFallbackUsed = false
            var cloudSyncFailedCount = 0
            var lastNotifiedPct = -10

            fun reportBatchProgress(stepIdx: Int, itemProgress: Float, stage: String) {
                val overall = ((completedCount + failedCount + itemProgress) / totalCount.coerceAtLeast(1))
                    .coerceIn(0.03f, 0.99f)
                val pct = (overall * 100).toInt()
                if (pct - lastNotifiedPct >= 6 || pct >= 96) {
                    lastNotifiedPct = pct
                    VideoGenerationKeepAliveService.startOrUpdate(
                        context = appContext,
                        title = "Batch AI Video (${completedCount + failedCount + 1}/$totalCount)",
                        body = stage,
                        progressPercent = pct,
                        isBatch = true
                    )
                }
            }

            try {
                for ((stepIdx, itemId) in targetIds.withIndex()) {
                    val snapshot = _items.value.firstOrNull { it.id == itemId } ?: continue
                    val variationIndex = _items.value.indexOfFirst { it.id == itemId }.coerceAtLeast(0)
                    val variationCue = VARIATION_CUES[variationIndex % VARIATION_CUES.size]

                    _batchState.value = BatchGenUiState.Running(
                        completedCount = completedCount,
                        failedCount = failedCount,
                        totalCount = totalCount,
                        activeIndex = stepIdx
                    )
                    updateItem(itemId) {
                        it.copy(
                            status = BatchItemStatus.PROCESSING,
                            progress = 0.08f,
                            stageLabel = "Directing variation ${variationIndex + 1} with Gemini AI...",
                            errorMessage = null
                        )
                    }
                    reportBatchProgress(stepIdx, 0.08f, "Directing variation ${variationIndex + 1}...")

                    try {
                        val directive = aiRepository.planVideoMotion(
                            sourceBitmap = snapshot.bitmap,
                            userPrompt = promptText,
                            variationHint = "Variation ${variationIndex + 1} of $totalCount: $variationCue"
                        )

                        updateItem(itemId) {
                            it.copy(
                                progress = 0.26f,
                                stageLabel = "Synthesizing AI transformation keyframe..."
                            )
                        }

                        val targetKeyframe = try {
                            aiRepository.generateAiKeyframe(
                                snapshot.bitmap,
                                directive.keyframeEditPrompt
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (kfErr: Exception) {
                            Log.w(TAG, "Batch item $itemId keyframe skipped; using motion on source photo", kfErr)
                            keyframeFallbackUsed = true
                            snapshot.bitmap
                        }

                        val fileName = "muba_batch_${System.currentTimeMillis()}_${variationIndex + 1}.mp4"
                        val outFile = MediaStorageHelper.getCreationFile(appContext, fileName)

                        updateItem(itemId) {
                            it.copy(
                                progress = 0.40f,
                                stageLabel = "Encoding ${quality.label} ${duration}s MP4..."
                            )
                        }

                        val videoResult = VideoSynthesisEngine.generateMotionVideoMp4(
                            sourceBitmap = snapshot.bitmap,
                            targetKeyframeBitmap = targetKeyframe,
                            directive = directive,
                            durationSeconds = duration,
                            quality = quality,
                            outputFile = outFile
                        ) { engineProgress ->
                            val scaled = 0.40f + engineProgress * 0.54f
                            val stageText = "Rendering variation ${variationIndex + 1} (${(engineProgress * 100).toInt()}%)..."
                            updateItem(itemId) {
                                it.copy(
                                    progress = scaled.coerceIn(0.40f, 0.94f),
                                    stageLabel = "Rendering frames (${(engineProgress * 100).toInt()}%)..."
                                )
                            }
                            reportBatchProgress(stepIdx, scaled, stageText)
                        }
                        videoResult.getOrThrow()

                        updateItem(itemId) {
                            it.copy(
                                progress = 0.96f,
                                stageLabel = "Saving to MUBA AI library..."
                            )
                        }

                        val thumbBase64 = MediaStorageHelper.extractVideoThumbnailBase64(outFile)
                        val shortBase = promptText.split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .take(4)
                            .joinToString(" ")
                            .ifBlank { "MUBA Batch Video" }
                        val itemTitle = "$shortBase (Var #${variationIndex + 1})".take(60)

                        val saveRes = creationRepository.saveCreation(
                            userId = currentUserId,
                            type = CreationType.VIDEO,
                            title = itemTitle,
                            prompt = promptText,
                            motionStyle = directive.motionStyle,
                            durationSeconds = duration.toLong(),
                            quality = quality.label,
                            localFileName = fileName,
                            thumbnailBase64 = thumbBase64
                        )
                        val creationId = saveRes.getOrElse { syncErr ->
                            Log.w(TAG, "Cloud save failed for batch item $itemId", syncErr)
                            cloudSyncFailedCount++
                            "local_${System.currentTimeMillis()}_${variationIndex + 1}"
                        }

                        completedCount++
                        updateItem(itemId) {
                            it.copy(
                                status = BatchItemStatus.COMPLETED,
                                progress = 1.0f,
                                stageLabel = "Ready • ${directive.motionStyle}",
                                creationId = creationId,
                                localFileName = fileName,
                                motionStyle = directive.motionStyle,
                                durationSeconds = duration,
                                qualityLabel = quality.label,
                                errorMessage = null
                            )
                        }
                    } catch (ce: CancellationException) {
                        updateItem(itemId) {
                            it.copy(
                                status = BatchItemStatus.QUEUED,
                                progress = 0f,
                                stageLabel = "Cancelled — ready to resume"
                            )
                        }
                        throw ce
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch item $itemId failed", e)
                        failedCount++
                        updateItem(itemId) {
                            it.copy(
                                status = BatchItemStatus.FAILED,
                                progress = 0f,
                                stageLabel = "Generation failed",
                                errorMessage = e.localizedMessage ?: "Failed to generate this variation."
                            )
                        }
                    }
                }

                _batchState.value = BatchGenUiState.Finished(
                    completedCount = completedCount,
                    failedCount = failedCount,
                    totalCount = totalCount
                )

                val summaryParts = buildList {
                    add("Batch finished: $completedCount of $totalCount AI video variations created!")
                    if (failedCount > 0) add("$failedCount failed — tap Retry Failed.")
                    if (keyframeFallbackUsed) add("Used Gemini Motion AI direct animation where Nano Banana keyframe was unavailable.")
                    if (cloudSyncFailedCount > 0) add("$cloudSyncFailedCount saved locally only (cloud sync offline).")
                }
                _statusMessage.value = summaryParts.joinToString(" ")

                VideoGenerationKeepAliveService.stop(appContext)
                if (completedCount > 0) {
                    val shortPrompt = promptText.take(40)
                    val extraFailed = if (failedCount > 0) " ($failedCount failed)" else ""
                    MubaNotificationHelper.notifyGenerationComplete(
                        context = appContext,
                        title = "Batch Videos Ready ($completedCount/$totalCount)",
                        body = "AI variations for \"$shortPrompt\" finished$extraFailed. Tap to watch.",
                        isBatch = true,
                        isError = false
                    )
                } else if (failedCount > 0) {
                    MubaNotificationHelper.notifyGenerationComplete(
                        context = appContext,
                        title = "Batch Generation Failed",
                        body = "Unable to generate AI video variations. Tap to review and retry.",
                        isBatch = true,
                        isError = true
                    )
                }
            } catch (ce: CancellationException) {
                VideoGenerationKeepAliveService.stop(appContext)
                _items.update { list ->
                    list.map { item ->
                        if (item.status == BatchItemStatus.PROCESSING) {
                            item.copy(status = BatchItemStatus.QUEUED, progress = 0f, stageLabel = "Paused")
                        } else item
                    }
                }
                _batchState.value = BatchGenUiState.Idle
                throw ce
            }
        }
    }

    fun cancelBatchGeneration() {
        batchJob?.cancel()
        batchJob = null
        VideoGenerationKeepAliveService.stop(appContext)
        _items.update { list ->
            list.map { item ->
                if (item.status == BatchItemStatus.PROCESSING) {
                    item.copy(status = BatchItemStatus.QUEUED, progress = 0f, stageLabel = "Cancelled")
                } else item
            }
        }
        val itemsNow = _items.value
        val done = itemsNow.count { it.status == BatchItemStatus.COMPLETED }
        val failed = itemsNow.count { it.status == BatchItemStatus.FAILED }
        _batchState.value = if (done > 0 || failed > 0) {
            BatchGenUiState.Finished(done, failed, itemsNow.size)
        } else {
            BatchGenUiState.Idle
        }
        _statusMessage.value = "Batch cancelled. $done completed variation(s) kept."
    }

    fun saveAllCompletedToGallery() {
        val completed = _items.value.filter {
            it.status == BatchItemStatus.COMPLETED && !it.localFileName.isNullOrBlank()
        }
        if (completed.isEmpty()) {
            _statusMessage.value = "No completed batch videos to save yet."
            return
        }
        viewModelScope.launch {
            _isBulkSaving.value = true
            var savedCount = 0
            var failCount = 0
            try {
                completed.forEachIndexed { idx, item ->
                    val file = MediaStorageHelper.getCreationFile(appContext, item.localFileName!!)
                    val safeName = "MUBA_AI_Batch_${idx + 1}_${System.currentTimeMillis()}.mp4"
                    val res = MediaStorageHelper.saveVideoToGallery(appContext, file, safeName, "video/mp4")
                    if (res.isSuccess) savedCount++ else failCount++
                }
                _statusMessage.value = if (failCount == 0) {
                    "Saved all $savedCount batch video(s) to Movies/MUBA AI in your Gallery!"
                } else {
                    "Saved $savedCount video(s) to Gallery ($failCount failed)."
                }
            } finally {
                _isBulkSaving.value = false
            }
        }
    }

    fun exportBatchItemToGallery(
        item: BatchPhotoItem,
        format: VideoExportFormat
    ) {
        val fileName = item.localFileName ?: return
        val sourceFile = MediaStorageHelper.getCreationFile(appContext, fileName)
        if (!sourceFile.exists()) {
            _statusMessage.value = "Video file not found on device."
            return
        }
        viewModelScope.launch {
            val cleanTitle = _prompt.value.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Batch" }.take(20)
            val safeName = "MUBA_AI_${cleanTitle}_${item.label.replace(" ", "_")}_${System.currentTimeMillis()}.${format.extension}"

            if (format == VideoExportFormat.MP4) {
                _exportState.value = VideoExportState.Exporting(format, 0.5f)
                val res = MediaStorageHelper.saveVideoToGallery(appContext, sourceFile, safeName, format.mimeType)
                _exportState.value = VideoExportState.Idle
                _statusMessage.value = res.fold(
                    onSuccess = { "${item.label} MP4 saved to Movies/MUBA AI!" },
                    onFailure = { it.localizedMessage ?: "Failed to save MP4." }
                )
                return@launch
            }

            val tempExport = File(appContext.cacheDir, "batch_exp_${System.currentTimeMillis()}.${format.extension}")
            _exportState.value = VideoExportState.Exporting(format, 0.05f)
            try {
                val convertRes = when (format) {
                    VideoExportFormat.GIF -> VideoSynthesisEngine.exportVideoToGif(sourceFile, tempExport) { p ->
                        _exportState.value = VideoExportState.Exporting(format, p)
                    }
                    VideoExportFormat.WEBM -> VideoSynthesisEngine.exportVideoToWebm(sourceFile, tempExport) { p ->
                        _exportState.value = VideoExportState.Exporting(format, p)
                    }
                    VideoExportFormat.MP4 -> Result.success(sourceFile)
                }
                convertRes.getOrThrow()

                val saveRes = when (format) {
                    VideoExportFormat.GIF -> MediaStorageHelper.saveGifToGallery(appContext, tempExport, safeName)
                    VideoExportFormat.WEBM -> MediaStorageHelper.saveVideoToGallery(appContext, tempExport, safeName, format.mimeType)
                    VideoExportFormat.MP4 -> MediaStorageHelper.saveVideoToGallery(appContext, sourceFile, safeName, format.mimeType)
                }
                _statusMessage.value = saveRes.fold(
                    onSuccess = { "${item.label} exported as ${format.displayName} to Gallery!" },
                    onFailure = { it.localizedMessage ?: "Export save failed." }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Failed to export ${format.extension.uppercase()}."
            } finally {
                if (tempExport.exists()) tempExport.delete()
                _exportState.value = VideoExportState.Idle
            }
        }
    }

    fun shareBatchItem(
        item: BatchPhotoItem,
        caption: String,
        platform: SocialPlatform
    ) {
        val fileName = item.localFileName ?: return
        val file = MediaStorageHelper.getCreationFile(appContext, fileName)
        val res = MediaStorageHelper.shareToSocialPlatform(
            context = appContext,
            file = file,
            mimeType = "video/mp4",
            title = "${item.label} • ${item.motionStyle ?: "MUBA AI"}",
            caption = caption,
            platform = platform
        )
        _statusMessage.value = res.fold(
            onSuccess = { it },
            onFailure = { it.localizedMessage ?: "Failed to share video." }
        )
    }

    private fun updateItem(itemId: String, transform: (BatchPhotoItem) -> BatchPhotoItem) {
        _items.update { list ->
            list.map { if (it.id == itemId) transform(it) else it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        batchJob?.cancel()
    }

    companion object {
        private const val TAG = "BatchVideoVM"
        const val MAX_BATCH_PHOTOS = 8

        private val VARIATION_CUES = listOf(
            "Emphasize a dramatic hero slow push-in with luminous golden-violet light highlights.",
            "Emphasize a smooth sweeping lateral orbit pan with shimmering atmospheric particles.",
            "Emphasize a gentle pull-back reveal with deep parallax motion and subtle nebula glow.",
            "Emphasize a dynamic upward tilt drift with crisp studio contrast and floating sparks.",
            "Emphasize a steady horizontal glide with warm volumetric light rays.",
            "Emphasize a dreamy slow zoom-in with radiant edge lighting and soft depth blur.",
            "Emphasize a sweeping diagonal drift with vibrant cinematic color mood.",
            "Emphasize an intimate floating camera breathe with ethereal ambient particles."
        )

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                BatchVideoViewModel(
                    aiRepository = AiStudioRepository(),
                    creationRepository = CreationRepository(app),
                    appContext = app.applicationContext,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
