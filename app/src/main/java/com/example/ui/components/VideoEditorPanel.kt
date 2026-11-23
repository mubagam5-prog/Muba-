package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MusicTrack
import com.example.data.model.VideoFilter
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleGlow
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditorPanel(
    maxDurationSec: Float,
    trimStartSec: Float,
    trimEndSec: Float,
    selectedFilter: VideoFilter,
    selectedMusic: MusicTrack,
    previewingTrack: MusicTrack?,
    isApplyingEdits: Boolean,
    editProgress: Float,
    onTrimChanged: (Float, Float) -> Unit,
    onFilterSelected: (VideoFilter) -> Unit,
    onMusicSelected: (MusicTrack) -> Unit,
    onStopMusicPreview: () -> Unit,
    onApplyEdits: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MubaPurple.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    tint = MubaPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Video Editing Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            // 1. Trim Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Trim Duration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "%.1fs – %.1fs (%.1fs)",
                        trimStartSec,
                        trimEndSec,
                        (trimEndSec - trimStartSec).coerceAtLeast(0.5f)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MubaPurple
                )
            }

            RangeSlider(
                value = trimStartSec.coerceIn(0f, maxDurationSec)..trimEndSec.coerceIn(0.5f, maxDurationSec),
                onValueChange = { range ->
                    val start = range.start
                    val end = range.endInclusive.coerceAtLeast((start + 1.0f).coerceAtMost(maxDurationSec))
                    onTrimChanged(start, end)
                },
                valueRange = 0f..maxDurationSec.coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MubaPurple,
                    activeTrackColor = MubaPurple,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.testTag("editor_trim_slider")
            )

            Spacer(Modifier.height(14.dp))

            // 2. Simple Filters
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FilterBAndW,
                    contentDescription = null,
                    tint = MubaPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Color Filter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoFilter.entries.forEach { filter ->
                    val active = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .testTag("editor_filter_${filter.name.lowercase()}")
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MubaPurple else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (active) MubaPurple else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onFilterSelected(filter) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 3. Background Music Library
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Background Music Library",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (previewingTrack != null) {
                    Surface(
                        onClick = onStopMusicPreview,
                        shape = RoundedCornerShape(10.dp),
                        color = MubaPurple.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Stop music preview",
                                tint = MubaPurple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Stop Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MubaPurple
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicTrack.entries.forEach { track ->
                    val selected = track == selectedMusic
                    val isPlayingThis = track == previewingTrack
                    Row(
                        modifier = Modifier
                            .testTag("editor_music_${track.id}")
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MubaPurple.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                1.dp,
                                if (selected) MubaPurple else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onMusicSelected(track) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MubaPurple else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Rounded.VolumeUp else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (selected) MubaPureWhite else MubaPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = track.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (track.bpm > 0) "${track.subtitle} • ${track.bpm} BPM" else track.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selected) {
                            Text(
                                text = if (isPlayingThis) "Playing" else "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MubaPurpleGlow.takeIf { isPlayingThis } ?: MubaPurple
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (isApplyingEdits) {
                LinearProgressIndicator(
                    progress = { editProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MubaPurple,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Rendering trimmed video, filter & audio track (${(editProgress * 100).toInt()}%)...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            MubaPrimaryButton(
                text = if (isApplyingEdits) "Rendering Edits..." else "Apply Edits & Save Copy",
                icon = Icons.Rounded.AutoFixHigh,
                isLoading = isApplyingEdits,
                enabled = !isApplyingEdits,
                onClick = onApplyEdits,
                modifier = Modifier.fillMaxWidth(),
                testTag = "editor_apply_edits_button"
            )
        }
    }
}
