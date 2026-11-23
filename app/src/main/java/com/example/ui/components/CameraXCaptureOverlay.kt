package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.core.MediaStorageHelper
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleGlow
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val TAG = "CameraXCapture"

/**
 * Full-screen modal wrapper around [CameraXPhotoCapture] so any AI screen (Video, Batch, or Image)
 * can capture photos directly inside MUBA AI without leaving the app.
 */
@Composable
fun CameraXCaptureDialog(
    projectTitle: String = "MUBA AI Studio",
    onPhotoCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CameraXPhotoCapture(
            projectTitle = projectTitle,
            onPhotoCaptured = onPhotoCaptured,
            onDismiss = onDismiss
        )
    }
}

/**
 * Reusable CameraX Compose capture experience:
 * - Handles runtime CAMERA permission gracefully
 * - Binds CameraX [Preview] + [ImageCapture] to the active LifecycleOwner
 * - Supports tap-to-focus, flash mode cycling, front/rear camera switching, and rule-of-thirds grid
 * - Shows an instant photo review step (Retake / Use Photo) before delivering the captured [Uri]
 */
@Composable
fun CameraXPhotoCapture(
    projectTitle: String = "MUBA AI Studio",
    onPhotoCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var showGrid by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var showShutterFlash by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedFileName by remember { mutableStateOf<String?>(null) }

    var canSwitchLens by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var focusIndicatorOffset by remember { mutableStateOf<Offset?>(null) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    LaunchedEffect(focusIndicatorOffset) {
        if (focusIndicatorOffset != null) {
            delay(950L)
            focusIndicatorOffset = null
        }
    }

    LaunchedEffect(showShutterFlash) {
        if (showShutterFlash) {
            delay(130L)
            showShutterFlash = false
        }
    }

    // Bind CameraX whenever permission is granted, lens changes, or user returns from review
    LaunchedEffect(hasCameraPermission, lensFacing, capturedUri) {
        if (!hasCameraPermission || capturedUri != null) return@LaunchedEffect
        cameraError = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    canSwitchLens = hasBack && hasFront

                    val resolvedFacing = when {
                        lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> CameraSelector.LENS_FACING_BACK
                        lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.LENS_FACING_FRONT
                        hasBack -> CameraSelector.LENS_FACING_BACK
                        hasFront -> CameraSelector.LENS_FACING_FRONT
                        else -> null
                    }

                    if (resolvedFacing == null) {
                        cameraError = "No camera hardware was detected on this device."
                        return@addListener
                    }

                    if (resolvedFacing != lensFacing) {
                        lensFacing = resolvedFacing
                    }

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(resolvedFacing)
                        .build()

                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    imageCapture.targetRotation = rotation

                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                    boundCamera = camera
                    hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind CameraX use cases", e)
                    cameraError = e.localizedMessage ?: "Unable to start camera preview."
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            capturedFileName?.let { oldFile ->
                MediaStorageHelper.deleteCameraCaptureFile(context, oldFile)
            }
        }
    }

    fun capturePhoto() {
        if (isCapturing || !hasCameraPermission || cameraError != null) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        isCapturing = true
        showShutterFlash = true

        val (targetFile, _) = MediaStorageHelper.createCameraCaptureTarget(context)
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    val savedUri = outputFileResults.savedUri ?: FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetFile
                    )
                    capturedFileName = targetFile.name
                    capturedUri = savedUri
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    if (targetFile.exists()) targetFile.delete()
                    Log.e(TAG, "CameraX photo capture error", exception)
                    cameraError = exception.localizedMessage ?: "Failed to capture photo. Please try again."
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MubaObsidian)
    ) {
        when {
            !hasCameraPermission -> {
                CameraPermissionScreen(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onClose = onDismiss
                )
            }

            capturedUri != null -> {
                val reviewUri = capturedUri!!
                CapturedPhotoReviewContent(
                    photoUri = reviewUri,
                    projectTitle = projectTitle,
                    onRetake = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val oldFile = capturedFileName
                        capturedFileName = null
                        capturedUri = null
                        if (oldFile != null) {
                            MediaStorageHelper.deleteCameraCaptureFile(context, oldFile)
                        }
                    },
                    onConfirm = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Hand URI back to parent; clear local name so onDispose won't delete it
                        capturedFileName = null
                        onPhotoCaptured(reviewUri)
                    },
                    onClose = onDismiss
                )
            }

            else -> {
                // Live CameraX Preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boundCamera) {
                            detectTapGestures { offset ->
                                focusIndicatorOffset = offset
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                )
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                boundCamera?.cameraControl?.startFocusAndMetering(action)
                            }
                        }
                )

                // Optional Rule-of-Thirds Studio Grid
                if (showGrid) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 1.dp.toPx()
                        val gridColor = MubaPureWhite.copy(alpha = 0.28f)
                        drawLine(
                            color = gridColor,
                            start = Offset(size.width / 3f, 0f),
                            end = Offset(size.width / 3f, size.height),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(size.width * 2f / 3f, 0f),
                            end = Offset(size.width * 2f / 3f, size.height),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height / 3f),
                            end = Offset(size.width, size.height / 3f),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height * 2f / 3f),
                            end = Offset(size.width, size.height * 2f / 3f),
                            strokeWidth = stroke
                        )
                    }
                }

                // Tap-to-focus ring
                focusIndicatorOffset?.let { tapOffset ->
                    val ringHalfPx = with(density) { 28.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (tapOffset.x - ringHalfPx).roundToInt(),
                                    (tapOffset.y - ringHalfPx).roundToInt()
                                )
                            }
                            .size(56.dp)
                            .border(2.dp, MubaPurpleGlow, CircleShape)
                    )
                }

                // Brief shutter flash feedback
                AnimatedVisibility(
                    visible = showShutterFlash,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MubaPureWhite.copy(alpha = 0.55f))
                    )
                }

                // Top Studio Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MubaObsidian.copy(alpha = 0.45f))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("camerax_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close camera",
                            tint = MubaPureWhite
                        )
                    }

                    Surface(
                        color = MubaObsidian.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.border(
                            1.dp,
                            MubaPurple.copy(alpha = 0.5f),
                            RoundedCornerShape(999.dp)
                        )
                    ) {
                        Text(
                            text = projectTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MubaPureWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showGrid = !showGrid
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("camerax_grid_button")
                        ) {
                            Icon(
                                imageVector = if (showGrid) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                                contentDescription = if (showGrid) "Hide studio grid" else "Show studio grid",
                                tint = if (showGrid) MubaPurpleGlow else MubaPureWhite
                            )
                        }

                        if (hasFlashUnit) {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    flashMode = when (flashMode) {
                                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                        else -> ImageCapture.FLASH_MODE_AUTO
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("camerax_flash_button")
                            ) {
                                val flashIcon = when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                                    ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                                    else -> Icons.Rounded.FlashAuto
                                }
                                Icon(
                                    imageVector = flashIcon,
                                    contentDescription = "Cycle camera flash mode",
                                    tint = if (flashMode == ImageCapture.FLASH_MODE_ON) MubaPurpleGlow else MubaPureWhite
                                )
                            }
                        }
                    }
                }

                // Hardware or Initialization Error Banner
                if (cameraError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = MubaPurple,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Camera Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = cameraError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = MubaPurple)
                            ) {
                                Text("Close", color = MubaPureWhite)
                            }
                        }
                    }
                }

                // Bottom Capture Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MubaObsidian.copy(alpha = 0.62f))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tap preview to focus • Capture photo for AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MubaPureWhite.copy(alpha = 0.78f)
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Cancel", color = MubaPureWhite)
                            }
                        }

                        // Shutter Button
                        val shutterScale by animateFloatAsState(
                            targetValue = if (isCapturing) 0.88f else 1f,
                            animationSpec = spring(),
                            label = "shutter_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .scale(shutterScale)
                                .clip(CircleShape)
                                .border(4.dp, MubaPureWhite, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing) MubaPurple else MubaPureWhite)
                                .clickable(enabled = !isCapturing && cameraError == null) {
                                    capturePhoto()
                                }
                                .testTag("camerax_shutter_button")
                                .semantics { },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = MubaPureWhite,
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        // Flip Camera Button
                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            if (canSwitchLens) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                            CameraSelector.LENS_FACING_FRONT
                                        } else {
                                            CameraSelector.LENS_FACING_BACK
                                        }
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MubaPureWhite.copy(alpha = 0.16f))
                                        .testTag("camerax_flip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "Switch front or rear camera",
                                        tint = MubaPureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturedPhotoReviewContent(
    photoUri: Uri,
    projectTitle: String,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MubaObsidian)
    ) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Captured photo preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 76.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close photo review",
                    tint = MubaPureWhite
                )
            }
            Text(
                text = "Review Shot • $projectTitle",
                style = MaterialTheme.typography.titleSmall,
                color = MubaPureWhite,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(48.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MubaObsidian.copy(alpha = 0.8f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("camerax_retake_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPureWhite)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retake")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("camerax_use_photo_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MubaPurple,
                    contentColor = MubaPureWhite
                )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Photo", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CameraPermissionScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close camera",
                tint = MubaPureWhite
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MubaPurple.copy(alpha = 0.22f))
                    .border(1.5.dp, MubaPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MubaPurpleGlow,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Camera Access Needed",
                style = MaterialTheme.typography.titleLarge,
                color = MubaPureWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Allow MUBA AI to use your camera so you can snap photos directly inside the app for your AI video and image projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MubaPureWhite.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("camerax_grant_permission_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MubaPurple,
                    contentColor = MubaPureWhite
                )
            ) {
                Text("Allow Camera", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Open App Settings", color = Color(0xFFD8B4FE))
            }
        }
    }
}
