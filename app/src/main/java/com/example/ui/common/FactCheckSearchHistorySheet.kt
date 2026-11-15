package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckHistoryHelper
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactCheckSearchSuggestion
import com.example.data.model.VoiceNote
import com.example.ui.common.displayLabelRes
import com.example.ui.common.localizedTimestamp
import com.google.firebase.Timestamp
import java.util.Date

enum class HistoryFilterTab(@param:androidx.annotation.StringRes val labelRes: Int) {
    ALL(R.string.history_tab_all),
    RECURRING(R.string.history_tab_recurring),
    NEEDS_ATTENTION(R.string.history_tab_needs_attention),
    CONFIRMED(R.string.history_tab_confirmed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactCheckSearchHistorySheet(
    notes: List<VoiceNote>,
    searchHistory: List<FactCheckSearchItem>,
    onSelectTopic: (String) -> Unit,
    onReverifyTopic: (String, String?) -> Unit, // query, noteId
    onOpenNote: ((String) -> Unit)? = null,
    onOpenSourceUrl: ((String) -> Unit)? = null,
    onDeleteItem: ((FactCheckSearchItem) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterText by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(HistoryFilterTab.ALL) }

    // Merge notes + recorded history via helper
    val allSuggestions = remember(notes, searchHistory) {
        FactCheckHistoryHelper.buildSuggestions(
            notes = notes,
            recordedHistory = searchHistory,
            inputQuery = "",
            limit = 100
        )
    }

    val filteredSuggestions = remember(allSuggestions, filterText, selectedTab) {
        val base = if (filterText.isBlank()) {
            allSuggestions
        } else {
            val lower = filterText.trim().lowercase()
            allSuggestions.filter {
                it.query.lowercase().contains(lower) || it.displayTopic.lowercase().contains(lower)
            }
        }

        when (selectedTab) {
            HistoryFilterTab.ALL -> base
            HistoryFilterTab.RECURRING -> base.filter { it.verificationCount >= 2 }
            HistoryFilterTab.NEEDS_ATTENTION -> base.filter {
                it.status == ClaimVerdictStatus.UPDATED || it.status == ClaimVerdictStatus.DISPUTED
            }
            HistoryFilterTab.CONFIRMED -> base.filter { it.status == ClaimVerdictStatus.SUPPORTED }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("fact_check_search_history_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.search_history_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.search_history_tracked_count, allSuggestions.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_search_history)
                    )
                }
            }

            // Filter Text Field
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_history_filter_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = filterText.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { filterText = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search_filter)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_history_filter_input")
            )

            // Filter Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                HistoryFilterTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("history_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Results List
            if (filteredSuggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TravelExplore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (filterText.isNotBlank()) stringResource(R.string.search_history_empty_filter, filterText)
                            else stringResource(R.string.search_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSuggestions, key = { it.query }) { item ->
                        SearchHistoryTopicCard(
                            item = item,
                            onSelect = {
                                onSelectTopic(item.query)
                                onDismiss()
                            },
                            onReverify = {
                                onReverifyTopic(item.query, item.lastNoteId)
                                onDismiss()
                            },
                            onOpenNote = { noteId ->
                                onOpenNote?.invoke(noteId)
                                onDismiss()
                            },
                            onOpenSourceUrl = onOpenSourceUrl,
                            onDelete = {
                                val matchItem = searchHistory.firstOrNull { it.query.equals(item.query, ignoreCase = true) }
                                if (matchItem != null) {
                                    onDeleteItem?.invoke(matchItem)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryTopicCard(
    item: FactCheckSearchSuggestion,
    onSelect: () -> Unit,
    onReverify: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSourceUrl: ((String) -> Unit)?,
    onDelete: () -> Unit
) {
    val (statusLabel, statusColor, statusContainer, statusIcon) = when (item.status) {
        ClaimVerdictStatus.SUPPORTED -> Quadruple(
            stringResource(item.status.displayLabelRes()),
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Rounded.CheckCircle
        )
        ClaimVerdictStatus.UPDATED -> Quadruple(
            stringResource(item.status.displayLabelRes()),
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Rounded.WarningAmber
        )
        ClaimVerdictStatus.DISPUTED -> Quadruple(
            stringResource(item.status.displayLabelRes()),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            Icons.Rounded.Info
        )
        ClaimVerdictStatus.UNVERIFIED -> Quadruple(
            stringResource(item.status.displayLabelRes()),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            Icons.Rounded.TravelExplore
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .testTag("history_item_${item.query.hashCode()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = statusContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = statusColor
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_verified_count, item.verificationCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (item.updatedAtMillis > 0L) {
                        Text(
                            text = localizedTimestamp(Timestamp(Date(item.updatedAtMillis))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_remove_history_topic),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Query text
            Text(
                text = item.query,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Explanation if available
            if (item.explanation.isNotBlank()) {
                Text(
                    text = item.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Citations & Parent Note Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val source = item.sourceUrl
                val sourceFallback = stringResource(R.string.history_source_fallback)
                if (!source.isNullOrBlank() && onOpenSourceUrl != null) {
                    val domain = runCatching {
                        val host = java.net.URI(source).host
                        host?.removePrefix("www.") ?: source.take(25)
                    }.getOrDefault(sourceFallback)

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenSourceUrl(source) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                val lastNoteTitle = item.lastNoteTitle
                val lastNoteId = item.lastNoteId
                if (!lastNoteTitle.isNullOrBlank() && !lastNoteId.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.history_in_note, lastNoteTitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenNote(lastNoteId) }
                            .weight(1f, fill = false)
                    )
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                OutlinedButton(
                    onClick = onSelect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(stringResource(R.string.action_use_topic), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onReverify,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_reverify_topic), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
