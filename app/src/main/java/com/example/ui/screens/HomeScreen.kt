package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.MubaStrings
import com.example.data.model.Creation
import com.example.data.model.CreationType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CreationThumbnail
import com.example.ui.components.MubaWordmark
import com.example.ui.theme.MubaObsidian
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleDeep
import com.example.ui.theme.MubaPurpleGlow

@Composable
fun HomeScreen(
    strings: MubaStrings,
    userProfileState: UiState<UserProfile?>,
    creationsState: UiState<List<Creation>>,
    onCreateVideoClick: () -> Unit,
    onBatchVideoClick: () -> Unit,
    onCreateImageClick: () -> Unit,
    onSeeAllCreations: () -> Unit,
    onCreationClick: (Creation) -> Unit,
    onStartTutorial: () -> Unit
) {
    val userName = when (userProfileState) {
        is UiState.Success -> userProfileState.data?.displayName?.split(" ")?.firstOrNull()
        else -> null
    } ?: "Creator"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Brand Bar + Interactive Guide button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MubaWordmark()

                Surface(
                    onClick = onStartTutorial,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .testTag("home_tutorial_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = strings.interactiveTour,
                            tint = MubaPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.interactiveTour,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Hero Studio Banner + Welcome Message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MubaPurple.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            ) {
                AsyncImage(
                    model = R.drawable.img_hero_studio,
                    contentDescription = stringResource(id = R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MubaObsidian.copy(alpha = 0.25f),
                                    MubaObsidian.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "${strings.welcomeTitle}, $userName",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MubaPureWhite
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.welcomeSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MubaPurpleGlow,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Creation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioActionCard(
                    title = strings.createAiVideo,
                    subtitle = "Photo to 5s / 10s MP4",
                    icon = Icons.Rounded.MovieCreation,
                    isPrimaryGradient = true,
                    onClick = onCreateVideoClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_create_video_button")
                )

                StudioActionCard(
                    title = strings.createAiImage,
                    subtitle = "Text to 1K AI Art",
                    icon = Icons.Rounded.Image,
                    isPrimaryGradient = false,
                    onClick = onCreateImageClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_create_image_button")
                )
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                onClick = onBatchVideoClick,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .testTag("home_batch_video_button")
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .border(1.dp, MubaPurple.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MubaPurple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BurstMode,
                            contentDescription = strings.batchMode,
                            tint = MubaPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.batchMode} • Multi-Photo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select up to 8 photos & generate AI video variations with one prompt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Recent Creations Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recentCreations,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = onSeeAllCreations,
                    modifier = Modifier.testTag("home_see_all_button")
                ) {
                    Text(strings.seeAll, color = MubaPurple)
                }
            }

            Spacer(Modifier.height(8.dp))

            when (creationsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MubaPurple)
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = creationsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val recents = creationsState.data.take(8)
                    AnimatedVisibility(visible = recents.isEmpty(), enter = fadeIn()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(20.dp)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MubaPurple.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MubaPurple
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
                    }

                    if (recents.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(recents, key = { it.id }) { item ->
                                RecentCreationCard(
                                    creation = item,
                                    onClick = { onCreationClick(item) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StudioActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isPrimaryGradient: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgModifier = if (isPrimaryGradient) {
        Modifier.background(Brush.linearGradient(listOf(MubaPurpleDeep, MubaPurple)))
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
    }
    val contentColor = if (isPrimaryGradient) MubaPureWhite else MaterialTheme.colorScheme.onSurface
    val subColor = if (isPrimaryGradient) MubaPurpleGlow else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(22.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isPrimaryGradient) Color.White.copy(alpha = 0.18f)
                    else MubaPurple.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimaryGradient) MubaPureWhite else MubaPurple,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subColor
        )
    }
}

@Composable
private fun RecentCreationCard(
    creation: Creation,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(164.dp)
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
                    .height(126.dp)
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
                            "IMAGE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MubaPurpleGlow
                    )
                }
                if (creation.creationType == CreationType.VIDEO) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play video",
                        tint = MubaPureWhite.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = creation.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = creation.motionStyle ?: creation.quality,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
