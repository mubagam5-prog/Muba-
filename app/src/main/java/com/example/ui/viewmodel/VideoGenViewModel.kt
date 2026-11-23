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
import com.example.core.MusicPreviewPlayer
import com.example.core.VideoGenerationKeepAliveService
import com.example.core.VideoSynthesisEngine
import com.example.data.model.CreationType
import com.example.data.model.MusicTrack
import com.example.data.model.SocialPlatform
import com.example.data.model.VideoExportFormat
import com.example.data.model.VideoFilter
import com.example.data.model.VideoQuality
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.CreationRepository
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoGenUiState {
    data object Idle : VideoGenUiState
    data class Generating(val progress: Float, val stageLabel: String) : VideoGenUiState
    data class Ready(
        val creationId: String,
        val localFileName: String,
        val durationSeconds: Int,
        val qualityLabel: String,
        val motionStyle: String,
        val prompt: String
    ) : VideoGenUiState
    data class Error(val message: String) : VideoGenUiState
}

sealed interface VideoExportState {
    data object Idle : VideoExportState
    data class Exporting(val format: VideoExportFormat, val progress: Float) : VideoExportState
}

class VideoGenViewModel(
    private val aiRepository: AiStudioRepository,
    private val creationRepository: CreationRepository,
    private val appContext: Context,
    private val currentUserId: String
) : ViewModel() {

    private val musicPreviewPlayer = MusicPreviewPlayer()

    private val _selectedPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedPhotoBitmap: StateFlow<Bitmap?> = _selectedPhotoBitmap.asStateFlow()

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _selectedDuration = MutableStateFlow(5)
    val selectedDuration: StateFlow<Int> = _selectedDuration.asStateFlow()

    private val _selectedQuality = MutableStateFlow(VideoQuality.HD)
    val selectedQuality: StateFlow<VideoQuality> = _selectedQuality.asStateFlow()

    private val _uiState = MutableStateFlow<VideoGenUiState>(VideoGenUiState.Idle)
    val uiState: StateFlow<VideoGenUiState> = _uiState.asStateFlow()

    // Post-generation Video Editor state
    private val _trimStartSec = MutableStateFlow(0f)
    val trimStartSec: StateFlow<Float> = _trimStartSec.asStateFlow()

    private val _trimEndSec = MutableStateFlow(5f)
    val trimEndSec: StateFlow<Float> = _trimEndSec.asStateFlow()

    private val _selectedFilter = MutableStateFlow(VideoFilter.ORIGINAL)
    val selectedFilter: StateFlow<VideoFilter> = _selectedFilter.asStateFlow()

    private val _selectedMusic = MutableStateFlow(MusicTrack.NONE)
    val selectedMusic: StateFlow<MusicTrack> = _selectedMusic.asStateFlow()

    private val _previewingTrack = MutableStateFlow<MusicTrack?>(null)
    val previewingTrack: StateFlow<MusicTrack?> = _previewingTrack.asStateFlow()

    private val _isApplyingEdits = MutableStateFlow(false)
    val isApplyingEdits: StateFlow<Boolean> = _isApplyingEdits.asStateFlow()

    private val _editProgress = MutableStateFlow(0f)
    val editProgress: StateFlow<Float> = _editProgress.asStateFlow()

    private val _exportState = MutableStateFlow<VideoExportState>(VideoExportState.Idle)
    val exportState: StateFlow<VideoExportState> = _exportState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setPrompt(value: String) {
        _prompt.value = value
        if (_uiState.value is VideoGenUiState.Error) {
            _uiState.value = VideoGenUiState.Idle
        }
    }

    fun setDuration(seconds: Int) {
        _selectedDuration.value = if (seconds == 10) 10 else 5
    }

    fun setQuality(quality: VideoQuality) {
        _selectedQuality.value = quality
    }

    fun setPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            val decoded = MediaStorageHelper.decodeSampledBitmapFromUri(appContext, uri, 1280)
            if (decoded != null) {
                _selectedPhotoBitmap.value = decoded
                if (_uiState.value is VideoGenUiState.Error) {
                    _uiState.value = VideoGenUiState.Idle
                }
            } else {
                _statusMessage.value = "Could not load selected photo. Please choose another image."
            }
        }
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun setSourceBitmapDirect(bitmap: Bitmap, suggestedPrompt: String = "") {
        _selectedPhotoBitmap.value = bitmap
        if (suggestedPrompt.isNotBlank() && _prompt.value.isBlank()) {
            _prompt.value = suggestedPrompt
        }
        _uiState.value = VideoGenUiState.Idle
    }

    fun generateVideo() {
        val source = _selectedPhotoBitmap.value
        val promptText = _prompt.value.trim()
        if (source == null) {
            _uiState.value = VideoGenUiState.Error("Please upload a photo from your gallery or take a photo first.")
            return
        }
        if (promptText.isEmpty()) {
            _uiState.value = VideoGenUiState.Error("Please enter a prompt describing the motion or effect.")
            return
        }

        val duration = _selectedDuration.value
        val quality = _selectedQuality.value

        viewModelScope.launch {
            musicPreviewPlayer.stop()
            _previewingTrack.value = null
            var lastNotifiedPct = -10
            fun reportProgress(pct: Int, label: String) {
                if (pct - lastNotifiedPct >= 8 || pct >= 96) {
                    lastNotifiedPct = pct
                    VideoGenerationKeepAliveService.startOrUpdate(
                        context = appContext,
                        title = "MUBA AI Video (${duration}s ${quality.label})",
                        body = label,
                        progressPercent = pct,
                        isBatch = false
                    )
                }
            }
            try {
                _uiState.value = VideoGenUiState.Generating(0.08f, "Directing cinematic motion with Gemini AI...")
                reportProgress(8, "Directing cinematic motion with Gemini AI...")
                val directive = aiRepository.planVideoMotion(source, promptText)

                _uiState.value = VideoGenUiState.Generating(0.26f, "Synthesizing AI transformation keyframe...")
                reportProgress(26, "Synthesizing AI transformation keyframe...")
                var keyframeFallbackNotice: String? = null
                val targetKeyframe = try {
                    aiRepository.generateAiKeyframe(
                        source,
                        directive.keyframeEditPrompt
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (kfErr: Exception) {
                    Log.w(TAG, "Optional AI keyframe synthesis skipped; animating source photo with Gemini motion", kfErr)
                    keyframeFallbackNotice = "Video created using Gemini Motion AI (AI keyframe restyle skipped: ${kfErr.localizedMessage ?: "model unavailable"})."
                    source
                }

                val fileName = "muba_vid_${System.currentTimeMillis()}.mp4"
                val outFile = MediaStorageHelper.getCreationFile(appContext, fileName)

                _uiState.value = VideoGenUiState.Generating(0.42f, "Encoding ${quality.label} ${duration}s MP4 video...")
                reportProgress(42, "Encoding ${quality.label} ${duration}s MP4 video...")
                val videoResult = VideoSynthesisEngine.generateMotionVideoMp4(
                    sourceBitmap = source,
                    targetKeyframeBitmap = targetKeyframe,
                    directive = directive,
                    durationSeconds = duration,
                    quality = quality,
                    outputFile = outFile
                ) { engineProgress ->
                    val scaled = 0.42f + engineProgress * 0.52f
                    val pct = (scaled * 100).toInt().coerceIn(42, 95)
                    val stageText = "Rendering AI frames (${(engineProgress * 100).toInt()}%)..."
                    _uiState.value = VideoGenUiState.Generating(
                        scaled.coerceIn(0.42f, 0.95f),
                        stageText
                    )
                    reportProgress(pct, stageText)
                }

                videoResult.getOrThrow()

                _uiState.value = VideoGenUiState.Generating(0.97f, "Saving creation to your MUBA AI library...")
                reportProgress(97, "Finalizing your MUBA AI video...")
                val thumbBase64 = MediaStorageHelper.extractVideoThumbnailBase64(outFile)
                val shortTitle = promptText.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(5)
                    .joinToString(" ")
                    .ifBlank { "MUBA AI Video" }
                    .take(60)
                val saveRes = creationRepository.saveCreation(
                    userId = currentUserId,
                    type = CreationType.VIDEO,
                    title = shortTitle,
                    prompt = promptText,
                    motionStyle = directive.motionStyle,
                    durationSeconds = duration.toLong(),
                    quality = quality.label,
                    localFileName = fileName,
                    thumbnailBase64 = thumbBase64
                )
                val creationId = saveRes.getOrElse { syncErr ->
                    Log.w(TAG, "Cloud creation save failed; keeping local generated video", syncErr)
                    keyframeFallbackNotice = keyframeFallbackNotice ?:
                        "Video created on your device, but cloud library sync failed: ${syncErr.localizedMessage ?: "Offline or permission error"}."
                    "local_${System.currentTimeMillis()}"
                }

                if (keyframeFallbackNotice != null) {
                    _statusMessage.value = keyframeFallbackNotice
                }

                _trimStartSec.value = 0f
                _trimEndSec.value = duration.toFloat()
                _selectedFilter.value = VideoFilter.ORIGINAL
                _selectedMusic.value = MusicTrack.NONE

                _uiState.value = VideoGenUiState.Ready(
                    creationId = creationId,
                    localFileName = fileName,
                    durationSeconds = duration,
                    qualityLabel = quality.label,
                    motionStyle = directive.motionStyle,
                    prompt = promptText
                )

                VideoGenerationKeepAliveService.stop(appContext)
                MubaNotificationHelper.notifyGenerationComplete(
                    context = appContext,
                    title = "AI Video Ready!",
                    body = "\"$shortTitle\" (${duration}s ${quality.label}) finished rendering. Tap to watch.",
                    isBatch = false,
                    isError = false
                )
            } catch (e: CancellationException) {
                VideoGenerationKeepAliveService.stop(appContext)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation failed", e)
                val errMsg = e.localizedMessage ?: "AI video generation failed. Please try again."
                _uiState.value = VideoGenUiState.Error(errMsg)
                VideoGenerationKeepAliveService.stop(appContext)
                MubaNotificationHelper.notifyGenerationComplete(
                    context = appContext,
                    title = "Video Generation Failed",
                    body = errMsg,
                    isBatch = false,
                    isError = true
                )
            }
        }
    }

    fun updateTrimRange(startSec: Float, endSec: Float) {
        _trimStartSec.value = startSec
        _trimEndSec.value = endSec
    }

    fun selectFilter(filter: VideoFilter) {
        _selectedFilter.value = filter
    }

    fun selectAndPreviewMusic(track: MusicTrack) {
        _selectedMusic.value = track
        if (track == MusicTrack.NONE) {
            musicPreviewPlayer.stop()
            _previewingTrack.value = null
        } else {
            _previewingTrack.value = track
            musicPreviewPlayer.playPreview(track) {
                _previewingTrack.value = null
            }
        }
    }

    fun stopMusicPreview() {
        musicPreviewPlayer.stop()
        _previewingTrack.value = null
    }

    fun applyEditsToCurrentVideo(
        sourceFileName: String,
        originalTitle: String,
        originalPrompt: String,
        qualityLabel: String,
        onEditedSaved: (newFileName: String, newDurationSec: Int) -> Unit = { _, _ -> }
    ) {
        val sourceFile = MediaStorageHelper.getCreationFile(appContext, sourceFileName)
        if (!sourceFile.exists()) {
            _statusMessage.value = "Video file not found on device."
            return
        }
        val start = _trimStartSec.value
        val end = _trimEndSec.value
        val filter = _selectedFilter.value
        val music = _selectedMusic.value

        viewModelScope.launch {
            stopMusicPreview()
            _isApplyingEdits.value = true
            _editProgress.value = 0.05f
            try {
                val editedFileName = "muba_edit_${System.currentTimeMillis()}.mp4"
                val editedFile = MediaStorageHelper.getCreationFile(appContext, editedFileName)

                val result = VideoSynthesisEngine.applyVideoEdits(
                    sourceVideoFile = sourceFile,
                    outputFile = editedFile,
                    trimStartSec = start,
                    trimEndSec = end,
                    filter = filter,
                    musicTrack = music
                ) { p ->
                    _editProgress.value = p
                }
                result.getOrThrow()

                val newDurInt = (end - start).toInt().coerceAtLeast(1)
                val persistedDur = if (newDurInt >= 8) 10L else 5L
                val thumbBase64 = MediaStorageHelper.extractVideoThumbnailBase64(editedFile)
                val filterTag = if (filter != VideoFilter.ORIGINAL) filter.displayName else null
                val musicTag = if (music != MusicTrack.NONE) music.displayName else null
                val styleLabel = listOfNotNull(filterTag, musicTag).joinToString(" • ").ifBlank { "Edited Cut" }

                val saveResult = creationRepository.saveCreation(
                    userId = currentUserId,
                    type = CreationType.VIDEO,
                    title = "$originalTitle (Edit)".take(115),
                    prompt = originalPrompt,
                    motionStyle = styleLabel,
                    durationSeconds = persistedDur,
                    quality = qualityLabel,
                    localFileName = editedFileName,
                    thumbnailBase64 = thumbBase64
                )
                val newId = saveResult.getOrElse { syncErr ->
                    Log.w(TAG, "Cloud save failed for edited video; keeping local edited file", syncErr)
                    "local_${System.currentTimeMillis()}"
                }

                val currentReady = _uiState.value
                if (currentReady is VideoGenUiState.Ready) {
                    _uiState.value = currentReady.copy(
                        creationId = newId,
                        localFileName = editedFileName,
                        durationSeconds = newDurInt,
                        motionStyle = styleLabel
                    )
                }
                _trimStartSec.value = 0f
                _trimEndSec.value = newDurInt.toFloat()
                _statusMessage.value = if (saveResult.isSuccess) {
                    "Edited video saved to My Creations!"
                } else {
                    "Edited video ready on device (cloud sync failed)."
                }
                onEditedSaved(editedFileName, newDurInt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Applying video edits failed", e)
                _statusMessage.value = e.localizedMessage ?: "Failed to apply video edits."
            } finally {
                _isApplyingEdits.value = false
                _editProgress.value = 0f
            }
        }
    }

    fun downloadVideoToGallery(
        fileName: String,
        title: String,
        format: VideoExportFormat = VideoExportFormat.MP4,
        onCompleteMessage: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val sourceFile = MediaStorageHelper.getCreationFile(appContext, fileName)
            if (!sourceFile.exists()) {
                val errMsg = "Video file not found on device."
                _statusMessage.value = errMsg
                onCompleteMessage?.invoke(errMsg)
                return@launch
            }

            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Creation" }.take(24)
            val safeName = "MUBA_AI_${cleanTitle}_${System.currentTimeMillis()}.${format.extension}"

            if (format == VideoExportFormat.MP4) {
                _exportState.value = VideoExportState.Exporting(format, 0.5f)
                val res = MediaStorageHelper.saveVideoToGallery(appContext, sourceFile, safeName, format.mimeType)
                _exportState.value = VideoExportState.Idle
                val msg = res.fold(
                    onSuccess = { "MP4 saved to Movies/MUBA AI in your Gallery!" },
                    onFailure = { it.localizedMessage ?: "Failed to save MP4 to Gallery." }
                )
                _statusMessage.value = msg
                onCompleteMessage?.invoke(msg)
                return@launch
            }

            val tempExport = File(appContext.cacheDir, "export_${System.currentTimeMillis()}.${format.extension}")
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

                val msg = saveRes.fold(
                    onSuccess = {
                        when (format) {
                            VideoExportFormat.GIF -> "Animated GIF saved to Pictures/MUBA AI in your Gallery!"
                            VideoExportFormat.WEBM -> "WebM video saved to Movies/MUBA AI in your Gallery!"
                            VideoExportFormat.MP4 -> "MP4 saved to Movies/MUBA AI in your Gallery!"
                        }
                    },
                    onFailure = { it.localizedMessage ?: "Failed to save ${format.extension.uppercase()} to Gallery." }
                )
                _statusMessage.value = msg
                onCompleteMessage?.invoke(msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video export failed for ${format.name}", e)
                val msg = e.localizedMessage ?: "Failed to export ${format.extension.uppercase()}."
                _statusMessage.value = msg
                onCompleteMessage?.invoke(msg)
            } finally {
                if (tempExport.exists()) tempExport.delete()
                _exportState.value = VideoExportState.Idle
            }
        }
    }

    fun shareVideo(
        fileName: String,
        title: String,
        caption: String = title,
        platform: SocialPlatform = SocialPlatform.SYSTEM_SHEET,
        onFeedback: ((String) -> Unit)? = null
    ) {
        val file = MediaStorageHelper.getCreationFile(appContext, fileName)
        val res = MediaStorageHelper.shareToSocialPlatform(
            context = appContext,
            file = file,
            mimeType = "video/mp4",
            title = title,
            caption = caption,
            platform = platform
        )
        val msg = res.fold(
            onSuccess = { it },
            onFailure = { it.localizedMessage ?: "Failed to share video." }
        )
        _statusMessage.value = msg
        onFeedback?.invoke(msg)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun resetToIdle() {
        stopMusicPreview()
        _uiState.value = VideoGenUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        musicPreviewPlayer.stop()
    }

    companion object {
        private const val TAG = "VideoGenVM"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                VideoGenViewModel(
                    aiRepository = AiStudioRepository(),
                    creationRepository = CreationRepository(app),
                    appContext = app.applicationContext,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
