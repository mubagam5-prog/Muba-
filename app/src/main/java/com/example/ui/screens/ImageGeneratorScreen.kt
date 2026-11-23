package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaStorageHelper
import com.example.core.MubaStrings
import com.example.data.model.ImageAspectRatioOption
import com.example.data.model.PromptIdeaCatalog
import com.example.ui.components.CameraXCaptureDialog
import com.example.ui.components.MubaPrimaryButton
import com.example.ui.components.MubaShimmerBox
import com.example.ui.components.PromptIdeasSection
import com.example.ui.components.SocialShareSheet
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.ImageGenViewModel

@Composable
fun ImageGeneratorScreen(
    strings: MubaStrings,
    viewModel: ImageGenViewModel,
    onAnimateIntoVideo: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val referenceBitmap by viewModel.referencePhotoBitmap.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showShareSheet by remember { mutableStateOf(false) }
    var showCameraXCapture by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is ImageGenUiState.Loading || uiState is ImageGenUiState.Success) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.setReferencePhotoFromUri(uri)
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
                text = strings.createAiImage,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Powered by Gemini Nano Banana — generate studio art and save to your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

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

            OutlinedTextField(
                value = prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Image Prompt") },
                placeholder = { Text(strings.promptHintImage) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MubaPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_prompt_input")
            )

            Spacer(Modifier.height(14.dp))

            PromptIdeasSection(
                templates = PromptIdeaCatalog.imageTemplates,
                currentPrompt = prompt,
                onSelectTemplate = { template ->
                    viewModel.setPrompt(template.promptText)
                },
                subtitle = "Curated image templates with lighting, lens, and composition cues."
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Framing & Aspect Ratio",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageAspectRatioOption.entries.forEach { option ->
                    val selected = option == aspectRatio
                    Box(
                        modifier = Modifier
                            .testTag("image_ratio_${option.name.lowercase()}")
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MubaPurple else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (selected) MubaPurple else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.setAspectRatio(option) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.ratioLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MubaPureWhite.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Optional Reference Photo (Take Photo or Gallery)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (referenceBitmap != null) MubaPurple else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reference Photo (Optional)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (referenceBitmap != null) {
                                    "AI will restyle & transform your photo using your prompt."
                                } else {
                                    "Take a photo or pick one from gallery to restyle with AI."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (referenceBitmap != null) {
                            IconButton(
                                onClick = viewModel::clearReferencePhoto,
                                modifier = Modifier
                                    .testTag("image_clear_photo_button")
                                    .size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove reference photo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (referenceBitmap != null) {
                        Spacer(Modifier.height(10.dp))
                        Image(
                            bitmap = referenceBitmap!!.asImageBitmap(),
                            contentDescription = "Reference photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = launchCamera,
                            modifier = Modifier
                                .testTag("image_take_photo_button")
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

                        OutlinedButton(
                            onClick = launchGallery,
                            modifier = Modifier
                                .testTag("image_pick_photo_button")
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
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            val isLoading = uiState is ImageGenUiState.Loading
            val currentError = (uiState as? ImageGenUiState.Error)?.message
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
                text = if (isLoading) "Generating Image..." else strings.generateImage,
                icon = Icons.Rounded.AutoAwesome,
                isLoading = isLoading,
                enabled = !isLoading,
                onClick = viewModel::generateImage,
                modifier = Modifier.fillMaxWidth(),
                testTag = "image_generate_button"
            )

            Spacer(Modifier.height(20.dp))

            when (val state = uiState) {
                is ImageGenUiState.Idle, is ImageGenUiState.Error -> Unit
                is ImageGenUiState.Loading -> {
                    MubaShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        label = "Generating high-resolution AI image (may take 30-60s)..."
                    )
                }
                is ImageGenUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MubaPurple.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = stringResource(id = R.string.cd_generated_image),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                            )

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.saveImageToGallery(state.localFileName, state.prompt) },
                                    modifier = Modifier
                                        .testTag("image_save_gallery_button")
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.saveToGallery)
                                }

                                OutlinedButton(
                                    onClick = { showShareSheet = true },
                                    modifier = Modifier
                                        .testTag("image_share_button")
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.share)
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { onAnimateIntoVideo(state.bitmap, state.prompt) },
                                modifier = Modifier
                                    .testTag("image_animate_video_button")
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                            ) {
                                Icon(Icons.Rounded.MovieCreation, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Animate This Image into AI Video")
                            }
                        }
                    }

                    if (showShareSheet) {
                        SocialShareSheet(
                            mediaTitle = state.prompt.take(50),
                            mediaTypeLabel = "AI Image • JPG",
                            mimeType = "image/jpeg",
                            defaultCaption = "Created with MUBA AI — ${state.prompt.take(120)} #MUBAAI",
                            onDismiss = { showShareSheet = false },
                            onSelectPlatform = { platform, caption ->
                                showShareSheet = false
                                viewModel.shareImage(
                                    fileName = state.localFileName,
                                    title = state.prompt.take(60),
                                    caption = caption,
                                    platform = platform
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
                projectTitle = "AI Reference Photo",
                onPhotoCaptured = { uri ->
                    showCameraXCapture = false
                    viewModel.setReferencePhotoFromUri(uri)
                },
                onDismiss = { showCameraXCapture = false }
            )
        }
    }
}
