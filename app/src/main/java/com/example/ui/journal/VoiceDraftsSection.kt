package com.example.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.audio.AudioPlaybackState
import com.example.data.offline.PendingMemo
import com.example.data.offline.PendingMemoKind
import com.example.ui.common.localizedEpochTimestamp

@Composable
fun VoiceDraftsAndQueueSection(
    pendingMemos: List<PendingMemo>,
    isOnline: Boolean,
    isSyncing: Boolean,
    playingDraftId: String?,
    draftPlaybackState: AudioPlaybackState,
    onTogglePlayDraft: (PendingMemo) -> Unit,
    onSeekDraftPlayback: (Long) -> Unit,
    onCycleDraftSpeed: () -> Unit,
    onRenameDraftClick: (PendingMemo) -> Unit,
    onTranscribeDraft: (String) -> Unit,
    onTranscribeAllDrafts: () -> Unit,
    onSyncNow: () -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val savedDrafts = remember(pendingMemos) { pendingMemos.filter { it.deferredForLater } }
    val queuedAuto = remember(pendingMemos) { pendingMemos.filter { !it.deferredForLater } }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (!isOnline || queuedAuto.isNotEmpty()) {
            OfflineQueueBanner(
                pendingMemos = queuedAuto,
                isOnline = isOnline,
                isSyncing = isSyncing,
                onSyncNow = onSyncNow,
                onDiscard = onDiscard
            )
        }

        if (savedDrafts.isNotEmpty()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("voice_drafts_section")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
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
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${stringResource(R.string.voice_drafts_title)} (${savedDrafts.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.voice_drafts_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (savedDrafts.size > 1 && isOnline && !isSyncing) {
                            OutlinedButton(
                                onClick = onTranscribeAllDrafts,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("transcribe_all_drafts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.action_transcribe_all),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    savedDrafts.forEach { draft ->
                        VoiceDraftItemCard(
                            memo = draft,
                            isOnline = isOnline,
                            isSyncing = isSyncing,
                            isActivePlayback = playingDraftId == draft.id,
                            playbackState = draftPlaybackState,
                            onTogglePlay = { onTogglePlayDraft(draft) },
                            onSeekTo = onSeekDraftPlayback,
                            onCycleSpeed = onCycleDraftSpeed,
                            onRenameClick = { onRenameDraftClick(draft) },
                            onTranscribe = { onTranscribeDraft(draft.id) },
                            onDelete = { onDiscard(draft.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceDraftItemCard(
    memo: PendingMemo,
    isOnline: Boolean,
    isSyncing: Boolean,
    isActivePlayback: Boolean,
    playbackState: AudioPlaybackState,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onRenameClick: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit
) {
    val isPlaying = isActivePlayback && playbackState.isPlaying
    val formattedDraftTime = localizedEpochTimestamp(memo.createdAtMs)
    val displayTitle = memo.label?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.draft_title_with_time, formattedDraftTime)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActivePlayback) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_draft_card_${memo.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (isActivePlayback) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlay)
                        .testTag("play_draft_${memo.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.cd_pause_voice_draft else R.string.cd_play_voice_draft
                            ),
                            tint = if (isActivePlayback) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = formatDuration(memo.durationSeconds),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formattedDraftTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rename_draft_${memo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.rename_draft_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_draft_${memo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_voice_draft),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (isActivePlayback && playbackState.hasAudio && playbackState.durationMs > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = playbackState.currentPositionMs.toFloat().coerceIn(0f, playbackState.durationMs.toFloat()),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..playbackState.durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatDuration(playbackState.currentPositionMs / 1000L)} / ${formatDuration(playbackState.durationMs / 1000L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCycleSpeed)
                        ) {
                            Text(
                                text = "${playbackState.playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (!memo.lastError.isNullOrBlank()) {
                Text(
                    text = memo.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (isOnline) R.string.draft_ready_online else R.string.draft_saved_offline
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onTranscribe,
                    enabled = isOnline && !isSyncing,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("transcribe_draft_${memo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_transcribe_now),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineQueueBanner(
    pendingMemos: List<PendingMemo>,
    isOnline: Boolean,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onDiscard: (String) -> Unit
) {
    Surface(
        color = if (!isOnline) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (!isOnline) Icons.Rounded.CloudOff else Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = if (!isOnline) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    !isOnline && pendingMemos.isEmpty() -> stringResource(R.string.offline_mode_banner)
                    pendingMemos.size == 1 -> {
                        val item = pendingMemos.first()
                        stringResource(
                            if (item.kind == PendingMemoKind.AUDIO) R.string.offline_one_audio_queued
                            else R.string.offline_one_text_queued
                        )
                    }
                    else -> stringResource(R.string.offline_many_queued, pendingMemos.size)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val firstError = pendingMemos.firstOrNull()?.lastError
                if (!firstError.isNullOrBlank()) {
                    Text(
                        text = firstError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pendingMemos.isNotEmpty() && isOnline && !isSyncing) {
                TextButton(onClick = onSyncNow) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_sync_now))
                }
            } else if (pendingMemos.size == 1 && !isSyncing) {
                IconButton(
                    onClick = { onDiscard(pendingMemos.first().id) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_discard_queued_memo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDraftDialog(
    initialLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftName by remember(initialLabel) { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_draft_title)) },
        text = {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.rename_draft_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_draft_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(draftName) },
                enabled = draftName.isNotBlank(),
                modifier = Modifier.testTag("confirm_rename_draft_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
