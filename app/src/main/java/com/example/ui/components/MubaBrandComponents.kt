package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.MediaStorageHelper
import com.example.data.model.Creation
import com.example.data.model.CreationType
import com.example.data.model.VideoFilter
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleDeep
import com.example.ui.theme.MubaPurpleGlow
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MubaLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Outer rounded obsidian-purple badge
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(MubaPurpleDeep, MubaPurple)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.28f, h * 0.28f)
        )

        // Geometric play-prism triangle in white
        val playPath = Path().apply {
            moveTo(w * 0.34f, h * 0.26f)
            lineTo(w * 0.72f, h * 0.50f)
            lineTo(w * 0.34f, h * 0.74f)
            close()
        }
        drawPath(playPath, color = MubaPureWhite)

        // Crisp 4-point AI spark accent top-right
        val cx = w * 0.76f
        val cy = h * 0.24f
        val r = w * 0.11f
        val sparkPath = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.28f, cy - r * 0.28f)
            lineTo(cx + r, cy)
            lineTo(cx + r * 0.28f, cy + r * 0.28f)
            lineTo(cx, cy + r)
            lineTo(cx - r * 0.28f, cy + r * 0.28f)
            lineTo(cx - r, cy)
            lineTo(cx - r * 0.28f, cy - r * 0.28f)
            close()
        }
        drawPath(sparkPath, color = MubaPurpleGlow)
    }
}

@Composable
fun MubaWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 34.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MubaLogoMark(size = logoSize)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MUBA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(listOf(MubaPurpleDeep, MubaPurple))
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MubaPureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MubaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "btn_scale"
    )

    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(MubaPurpleDeep, MubaPurple))
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .scale(scale)
            .minimumInteractiveComponentSize()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MubaPureWhite,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MubaPureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MubaPureWhite else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MubaShimmerBox(
    modifier: Modifier = Modifier,
    label: String = "Synthesizing with MUBA AI..."
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MubaPurple.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset(offset - 300f, 0f),
        end = Offset(offset, 350f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .border(1.dp, MubaPurple.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = MubaPurple,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CreationThumbnail(
    creation: Creation,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localFile = remember(creation.localFileName) {
        MediaStorageHelper.getCreationFile(context, creation.localFileName)
    }
    val thumbBitmap = remember(creation.thumbnailBase64) {
        MediaStorageHelper.decodeThumbnailBase64(creation.thumbnailBase64)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            creation.creationType == CreationType.IMAGE && localFile.exists() -> {
                AsyncImage(
                    model = localFile,
                    contentDescription = creation.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            thumbBitmap != null -> {
                Image(
                    bitmap = thumbBitmap.asImageBitmap(),
                    contentDescription = creation.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = creation.title,
                    tint = MubaPurple,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun MubaVideoPlayer(
    videoFile: File,
    modifier: Modifier = Modifier,
    previewFilter: VideoFilter = VideoFilter.ORIGINAL
) {
    var isPlaying by remember(videoFile.absolutePath) { mutableStateOf(true) }
    var durationMs by remember(videoFile.absolutePath) { mutableIntStateOf(5000) }
    var positionMs by remember(videoFile.absolutePath) { mutableIntStateOf(0) }
    var sliderProgress by remember(videoFile.absolutePath) { mutableFloatStateOf(0f) }
    var videoViewRef by remember(videoFile.absolutePath) { mutableStateOf<VideoView?>(null) }
    var hasError by remember(videoFile.absolutePath) {
        mutableStateOf(!videoFile.exists() || videoFile.length() == 0L)
    }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (isPlaying && videoViewRef != null) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                val dur = vv.duration.coerceAtLeast(1)
                val pos = vv.currentPosition.coerceIn(0, dur)
                durationMs = dur
                positionMs = pos
                sliderProgress = pos.toFloat() / dur.toFloat()
            }
            delay(150L)
        }
    }

    DisposableEffect(videoFile.absolutePath) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    val filterTint = when (previewFilter) {
        VideoFilter.ORIGINAL -> Color.Transparent
        VideoFilter.NOIR -> Color.Black.copy(alpha = 0.25f)
        VideoFilter.VIOLET_DREAM -> MubaPurple.copy(alpha = 0.22f)
        VideoFilter.GOLDEN_GLOW -> Color(0xFFF59E0B).copy(alpha = 0.18f)
        VideoFilter.CYBER_CRISP -> Color(0xFF06B6D4).copy(alpha = 0.16f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        color = MubaObsidian
    ) {
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.VideocamOff,
                        contentDescription = null,
                        tint = MubaPurpleGlow,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Video file unavailable locally",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MubaPureWhite
                    )
                }
            }
        } else {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(MubaObsidian),
                    contentAlignment = Alignment.Center
                ) {
                    key(videoFile.absolutePath) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.fromFile(videoFile))
                                    setOnPreparedListener { mp: MediaPlayer ->
                                        mp.isLooping = true
                                        durationMs = mp.duration.coerceAtLeast(1000)
                                        start()
                                        isPlaying = true
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        hasError = true
                                        true
                                    }
                                    videoViewRef = this
                                }
                            },
                            update = { view ->
                                videoViewRef = view
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (filterTint != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(filterTint)
                        )
                    }

                    if (previewFilter != VideoFilter.ORIGINAL) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MubaObsidian.copy(alpha = 0.72f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = previewFilter.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MubaPurpleGlow
                            )
                        }
                    }
                }

                // Player controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MubaObsidian)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .testTag("video_play_pause_button")
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MubaPurple.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            tint = MubaPureWhite
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Slider(
                        value = sliderProgress,
                        onValueChange = { frac ->
                            sliderProgress = frac
                            val seekMs = (frac * durationMs).toInt()
                            videoViewRef?.seekTo(seekMs)
                            positionMs = seekMs
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MubaPurple,
                            activeTrackColor = MubaPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "${formatSeconds(positionMs)} / ${formatSeconds(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MubaPurpleGlow
                    )
                }
            }
        }
    }
}

private fun formatSeconds(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "0:${sec.toString().padStart(2, '0')}"
}
