package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleDeep

data class TutorialGuideStep(
    val badge: String,
    val title: String,
    val description: String,
    val callouts: List<ButtonExplanation>,
    val tryActionLabel: String? = null,
    val tryTargetRoute: String? = null
)

data class ButtonExplanation(
    val icon: ImageVector,
    val buttonName: String,
    val explanation: String
)

@Composable
fun OnboardingTutorialOverlay(
    onDismiss: () -> Unit,
    onJumpToStudio: (route: String) -> Unit
) {
    val steps = remember {
        listOf(
            TutorialGuideStep(
                badge = "STEP 1 OF 4 • WELCOME",
                title = "Welcome to MUBA AI Studio",
                description = "Create studio-grade AI videos from your own photos and generate crisp AI artwork in seconds. Everything saves automatically to your private account.",
                callouts = listOf(
                    ButtonExplanation(
                        icon = Icons.Rounded.MovieCreation,
                        buttonName = "Create AI Video",
                        explanation = "Opens the Photo-to-Video generator to animate any gallery photo using text prompts."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Image,
                        buttonName = "Create AI Image",
                        explanation = "Opens the Gemini Nano Banana image studio to generate artwork from text."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.VideoLibrary,
                        buttonName = "Creations Tab",
                        explanation = "Browse, play, trim, save to gallery, share, or delete all your past creations."
                    )
                )
            ),
            TutorialGuideStep(
                badge = "STEP 2 OF 4 • AI VIDEO GENERATOR",
                title = "How to Generate an AI Video",
                description = "Follow these controls in the AI Video tab to turn any still photo into a dynamic MP4 video:",
                callouts = listOf(
                    ButtonExplanation(
                        icon = Icons.Rounded.AddPhotoAlternate,
                        buttonName = "Upload Photo from Gallery",
                        explanation = "Tap to pick any photo from your phone using the secure Android Photo Picker."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Edit,
                        buttonName = "Motion Prompt Box",
                        explanation = "Describe how the scene should move (e.g., 'Golden sunset breeze with glowing particles')."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Timer,
                        buttonName = "Duration (5s / 10s)",
                        explanation = "Choose 5 seconds for quick clips or 10 seconds for longer storytelling."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Hd,
                        buttonName = "Quality (HD / 4K)",
                        explanation = "Select HD for fast rendering or 4K for maximum crisp bitrate."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.AutoAwesome,
                        buttonName = "Generate Video",
                        explanation = "Gemini directs camera motion, synthesizes an AI keyframe, and encodes your MP4."
                    )
                ),
                tryActionLabel = "Try AI Video Studio",
                tryTargetRoute = "video_gen"
            ),
            TutorialGuideStep(
                badge = "STEP 3 OF 4 • VIDEO EDITING TOOLS",
                title = "Edit, Add Music & Export",
                description = "Right after your video finishes generating, unlock the built-in Video Editor panel:",
                callouts = listOf(
                    ButtonExplanation(
                        icon = Icons.Rounded.ContentCut,
                        buttonName = "Trim Duration Slider",
                        explanation = "Drag the start and end handles to cut your video to the exact seconds you want."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.LibraryMusic,
                        buttonName = "Background Music Library",
                        explanation = "Tap any curated studio track (Midnight Velvet, Neon Pulse, Solaris Drift) to preview and mix it."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.FilterBAndW,
                        buttonName = "Cinematic Filters",
                        explanation = "Apply Obsidian Noir, Violet Dream, Golden Glow, or Cyber Crisp before saving."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Download,
                        buttonName = "Download & Share",
                        explanation = "Save directly to Movies/MUBA AI on your phone or share to any social app."
                    )
                )
            ),
            TutorialGuideStep(
                badge = "STEP 4 OF 4 • AI IMAGE GENERATOR",
                title = "How to Generate an AI Image",
                description = "Turn pure imagination into high-resolution visuals in the AI Image tab:",
                callouts = listOf(
                    ButtonExplanation(
                        icon = Icons.Rounded.Edit,
                        buttonName = "Image Prompt & Style Chips",
                        explanation = "Type your idea or tap a preset style pill like Cyberpunk, Minimalist 3D, or Studio Portrait."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Image,
                        buttonName = "Aspect Ratio Selector",
                        explanation = "Pick Square (1:1), Widescreen (16:9), Vertical (9:16), or Classic (4:3)."
                    ),
                    ButtonExplanation(
                        icon = Icons.Rounded.Download,
                        buttonName = "Save to Gallery / Animate",
                        explanation = "Save to Pictures/MUBA AI or send the generated image straight into the AI Video Generator!"
                    )
                ),
                tryActionLabel = "Try AI Image Studio",
                tryTargetRoute = "image_gen"
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MubaObsidian.copy(alpha = 0.86f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .border(1.dp, MubaPurple.copy(alpha = 0.45f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            steps.indices.forEach { idx ->
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (idx == currentIndex) 26.dp else 10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (idx == currentIndex) MubaPurple
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .testTag("tutorial_close_button")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close tutorial",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = steps[currentIndex],
                        label = "tutorial_step"
                    ) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MubaPurple.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = step.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MubaPurple
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                step.callouts.forEach { callout ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.linearGradient(listOf(MubaPurpleDeep, MubaPurple))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = callout.icon,
                                                contentDescription = null,
                                                tint = MubaPureWhite,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = callout.buttonName,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = callout.explanation,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (step.tryActionLabel != null && step.tryTargetRoute != null) {
                                Spacer(Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { onJumpToStudio(step.tryTargetRoute) },
                                    modifier = Modifier
                                        .testTag("tutorial_try_studio_button")
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MubaPurple)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(step.tryActionLabel)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Footer navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentIndex > 0) {
                            TextButton(
                                onClick = { currentIndex-- },
                                modifier = Modifier
                                    .testTag("tutorial_prev_button")
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Back")
                            }
                        } else {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .testTag("tutorial_skip_button")
                                    .height(48.dp)
                            ) {
                                Text("Skip")
                            }
                        }

                        MubaPrimaryButton(
                            text = if (currentIndex == steps.lastIndex) "Finish Tour" else "Next Step",
                            icon = if (currentIndex == steps.lastIndex) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.ArrowForward,
                            onClick = {
                                if (currentIndex < steps.lastIndex) {
                                    currentIndex++
                                } else {
                                    onDismiss()
                                }
                            },
                            testTag = "tutorial_next_button"
                        )
                }
            }
        }
    }
}
