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
import com.example.data.model.CreationType
import com.example.data.model.ImageAspectRatioOption
import com.example.data.model.SocialPlatform
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.CreationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(
        val creationId: String,
        val localFileName: String,
        val bitmap: Bitmap,
        val prompt: String
    ) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class ImageGenViewModel(
    private val aiRepository: AiStudioRepository,
    private val creationRepository: CreationRepository,
    private val appContext: Context,
    private val currentUserId: String
) : ViewModel() {

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _aspectRatio = MutableStateFlow(ImageAspectRatioOption.SQUARE)
    val aspectRatio: StateFlow<ImageAspectRatioOption> = _aspectRatio.asStateFlow()

    private val _referencePhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val referencePhotoBitmap: StateFlow<Bitmap?> = _referencePhotoBitmap.asStateFlow()

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setPrompt(value: String) {
        _prompt.value = value
        if (_uiState.value is ImageGenUiState.Error) {
            _uiState.value = ImageGenUiState.Idle
        }
    }

    fun setAspectRatio(option: ImageAspectRatioOption) {
        _aspectRatio.value = option
    }

    fun setReferencePhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            val decoded = MediaStorageHelper.decodeSampledBitmapFromUri(appContext, uri, 1280)
            if (decoded != null) {
                _referencePhotoBitmap.value = decoded
                if (_uiState.value is ImageGenUiState.Error) {
                    _uiState.value = ImageGenUiState.Idle
                }
            } else {
                _statusMessage.value = "Could not load photo. Please try again."
            }
        }
    }

    fun clearReferencePhoto() {
        _referencePhotoBitmap.value = null
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun generateImage() {
        val promptText = _prompt.value.trim()
        if (promptText.isEmpty()) {
            _uiState.value = ImageGenUiState.Error("Please enter a prompt describing the image you want to create.")
            return
        }

        val chosenRatio = _aspectRatio.value
        val referenceBitmap = _referencePhotoBitmap.value
        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiRepository.generateImage(
                    prompt = promptText,
                    aspectRatio = chosenRatio,
                    referenceBitmap = referenceBitmap
                )
                val fileName = "muba_img_${System.currentTimeMillis()}.jpg"
                MediaStorageHelper.saveBitmapToCreations(appContext, bitmap, fileName)
                val thumbBase64 = MediaStorageHelper.encodeThumbnailBase64(bitmap)
                val shortTitle = promptText.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(5)
                    .joinToString(" ")
                    .ifBlank { "MUBA AI Image" }
                    .take(60)

                val saveResult = creationRepository.saveCreation(
                    userId = currentUserId,
                    type = CreationType.IMAGE,
                    title = shortTitle,
                    prompt = promptText,
                    motionStyle = chosenRatio.ratioLabel,
                    durationSeconds = 0L,
                    quality = "1K",
                    localFileName = fileName,
                    thumbnailBase64 = thumbBase64
                )
                val creationId = saveResult.getOrElse { syncErr ->
                    Log.w(TAG, "Cloud creation save failed; keeping local generated image", syncErr)
                    _statusMessage.value =
                        "Image created on your device, but cloud library sync failed: ${syncErr.localizedMessage ?: "Offline or permission error"}."
                    "local_${System.currentTimeMillis()}"
                }

                _uiState.value = ImageGenUiState.Success(
                    creationId = creationId,
                    localFileName = fileName,
                    bitmap = bitmap,
                    prompt = promptText
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "AI image generation failed. Please try again."
                )
            }
        }
    }

    fun saveImageToGallery(fileName: String, title: String) {
        viewModelScope.launch {
            val file = MediaStorageHelper.getCreationFile(appContext, fileName)
            val safeName = "MUBA_AI_${title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(24)}_${System.currentTimeMillis()}.jpg"
            val res = MediaStorageHelper.saveImageToGallery(appContext, file, safeName)
            _statusMessage.value = res.fold(
                onSuccess = { "Image saved to Pictures/MUBA AI in your Gallery!" },
                onFailure = { it.localizedMessage ?: "Failed to save image to Gallery." }
            )
        }
    }

    fun shareImage(
        fileName: String,
        title: String,
        caption: String = title,
        platform: SocialPlatform = SocialPlatform.SYSTEM_SHEET
    ) {
        val file = MediaStorageHelper.getCreationFile(appContext, fileName)
        val res = MediaStorageHelper.shareToSocialPlatform(
            context = appContext,
            file = file,
            mimeType = "image/jpeg",
            title = title,
            caption = caption,
            platform = platform
        )
        _statusMessage.value = res.fold(
            onSuccess = { it },
            onFailure = { it.localizedMessage ?: "Failed to share image." }
        )
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    companion object {
        private const val TAG = "ImageGenVM"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                ImageGenViewModel(
                    aiRepository = AiStudioRepository(),
                    creationRepository = CreationRepository(app),
                    appContext = app.applicationContext,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
