package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PromptCategory
import com.example.data.model.PromptTemplate
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.example.ui.theme.MubaPurpleGlow

@Composable
fun PromptIdeasSection(
    templates: List<PromptTemplate>,
    currentPrompt: String,
    onSelectTemplate: (PromptTemplate) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "Curated prompt templates crafted for sharper lighting, framing, and mood."
) {
    var selectedCategory by rememberSaveable { mutableStateOf(PromptCategory.ALL) }
    var showBrowseAllSheet by remember { mutableStateOf(false) }

    val availableCategories = remember(templates) {
        val present = templates.map { it.category }.toSet()
        PromptCategory.entries.filter { it == PromptCategory.ALL || it in present }
    }

    val filteredTemplates = remember(templates, selectedCategory) {
        if (selectedCategory == PromptCategory.ALL) {
            templates
        } else {
            templates.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("prompt_ideas_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MubaPurple.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Prompt Ideas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            TextButton(
                onClick = { showBrowseAllSheet = true },
                modifier = Modifier
                    .testTag("prompt_ideas_browse_all_button")
                    .minimumInteractiveComponentSize()
            ) {
                Text(
                    text = "Browse All (${templates.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MubaPurple
                )
            }
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableCategories, key = { it.name }) { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .testTag("prompt_idea_category_${category.name.lowercase()}")
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MubaPurple else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MubaPurple else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(filteredTemplates, key = { it.id }) { template ->
                val isApplied = currentPrompt.trim().equals(template.promptText.trim(), ignoreCase = true)
                PromptIdeaCard(
                    template = template,
                    isApplied = isApplied,
                    onClick = { onSelectTemplate(template) },
                    modifier = Modifier
                        .width(268.dp)
                        .testTag("prompt_idea_card_${template.id}")
                )
            }
        }
    }

    if (showBrowseAllSheet) {
        PromptIdeasBrowserSheet(
            templates = templates,
            currentPrompt = currentPrompt,
            initialCategory = selectedCategory,
            onDismiss = { showBrowseAllSheet = false },
            onSelectTemplate = { template ->
                showBrowseAllSheet = false
                onSelectTemplate(template)
            }
        )
    }
}

@Composable
private fun PromptIdeaCard(
    template: PromptTemplate,
    isApplied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isApplied) {
            MubaPurple.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isApplied) 1.5.dp else 1.dp,
                color = if (isApplied) MubaPurple else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MubaPurple.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = template.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MubaPurpleGlow
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = template.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = template.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = template.promptText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isApplied) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isApplied) "Applied" else "Use Template",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MubaPurple
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptIdeasBrowserSheet(
    templates: List<PromptTemplate>,
    currentPrompt: String,
    initialCategory: PromptCategory,
    onDismiss: () -> Unit,
    onSelectTemplate: (PromptTemplate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    val availableCategories = remember(templates) {
        val present = templates.map { it.category }.toSet()
        PromptCategory.entries.filter { it == PromptCategory.ALL || it in present }
    }

    val visibleTemplates = remember(templates, activeCategory) {
        if (activeCategory == PromptCategory.ALL) {
            templates
        } else {
            templates.filter { it.category == activeCategory }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MubaPurple.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MubaPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prompt Ideas Library",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select any full template below, then customize words to make it yours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableCategories, key = { it.name }) { category ->
                    val isSelected = activeCategory == category
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MubaPurple else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { activeCategory = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visibleTemplates.forEach { template ->
                    val isApplied = currentPrompt.trim().equals(template.promptText.trim(), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isApplied) {
                            MubaPurple.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier
                            .testTag("prompt_ideas_sheet_card_${template.id}")
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = if (isApplied) 1.5.dp else 1.dp,
                                color = if (isApplied) MubaPurple else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectTemplate(template) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MubaPurple.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${template.category.label} • ${template.badge}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MubaPurpleGlow
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = template.promptText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tip: ${template.tip}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (isApplied) "Applied" else "Apply",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MubaPurple
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag("prompt_ideas_sheet_close_button")
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Close")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
