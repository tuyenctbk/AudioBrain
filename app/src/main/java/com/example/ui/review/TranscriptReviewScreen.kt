package com.example.ui.review

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.ReadAloudState
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.NoteCategory
import com.example.data.model.VoiceNote
import com.example.ui.common.MarkdownText
import com.example.ui.common.labelRes
import com.example.ui.common.localizedTimestamp
import com.example.ui.detail.ClaimVerdictCard
import com.example.ui.detail.NoteDetailUiState
import com.example.ui.detail.ReadAloudChipButton
import com.example.ui.detail.StudioAudioPlayerCard
import com.example.ui.detail.TranscriptSegment
import com.example.ui.detail.countTranscriptWords
import com.example.ui.detail.parseTranscriptSegments
import com.example.ui.journal.formatDuration
import kotlinx.coroutines.launch

private enum class TranscriptEditorMode {
    SEGMENTS,
    FULL_TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptReviewScreen(
    viewModel: TranscriptReviewViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noteState by viewModel.noteState.collectAsStateWithLifecycle()
    val draft by viewModel.draftState.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isPolishingTranscript by viewModel.isPolishingTranscript.collectAsStateWithLifecycle()
    val isFactCheckingTranscript by viewModel.isFactCheckingTranscript.collectAsStateWithLifecycle()
    val factCheckMessage by viewModel.factCheckMessage.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val readAloudState by viewModel.readAloudState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showDiscardDialog by remember { mutableStateOf(false) }

    val openExternalUrl: (String) -> Unit = { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser))
            }
        }
    }

    val handleBackNavigation: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBackNavigation)

    LaunchedEffect(actionError) {
        val err = actionError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    LaunchedEffect(factCheckMessage) {
        val msg = factCheckMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeFactCheckMessage()
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            viewModel.consumeSaveSuccess()
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_entry_saved))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = handleBackNavigation,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back_to_journal)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.review_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasUnsavedChanges) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.review_unsaved_badge),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (hasUnsavedChanges) {
                        IconButton(
                            onClick = viewModel::revertChanges,
                            enabled = !isSaving,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("review_revert_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.cd_revert_changes)
                            )
                        }
                    }
                    TextButton(
                        onClick = viewModel::saveChanges,
                        enabled = hasUnsavedChanges && draft.title.isNotBlank() && !isSaving,
                        modifier = Modifier.testTag("review_top_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = hasUnsavedChanges) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.review_unsaved_banner),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = viewModel::revertChanges,
                                enabled = !isSaving,
                                modifier = Modifier.testTag("review_bottom_discard_button")
                            ) {
                                Text(stringResource(R.string.action_discard))
                            }
                            Button(
                                onClick = viewModel::saveChanges,
                                enabled = draft.title.isNotBlank() && !isSaving,
                                modifier = Modifier.testTag("review_bottom_save_button")
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.review_saving))
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_save_entry))
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (val state = noteState) {
                is NoteDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is NoteDetailUiState.NotFound -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.detail_note_not_found),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(onClick = onBack) {
                                Text(stringResource(R.string.detail_back_to_journal_cta))
                            }
                        }
                    }
                }

                is NoteDetailUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is NoteDetailUiState.Ready -> {
                    val note = state.note
                    val copyTranscript: () -> Unit = {
                        clipboardManager.setText(AnnotatedString(draft.transcript))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transcript_copied))
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpandedScreen = maxWidth >= 840.dp
                        if (isExpandedScreen) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ReviewMetadataStrip(note = note, transcript = draft.transcript)
                                    if (playbackState.hasAudio) {
                                        StudioAudioPlayerCard(
                                            playbackState = playbackState,
                                            onTogglePlayPause = viewModel::toggleAudioPlayback,
                                            onSeekTo = viewModel::seekAudio,
                                            onCycleSpeed = viewModel::cyclePlaybackSpeed
                                        )
                                    }
                                    TranscriptEditorWorkspaceCard(
                                        noteId = note.id,
                                        transcript = draft.transcript,
                                        playbackState = playbackState,
                                        readAloudState = readAloudState,
                                        isPolishingTranscript = isPolishingTranscript,
                                        isFactCheckingTranscript = isFactCheckingTranscript,
                                        onTranscriptChange = viewModel::onTranscriptChange,
                                        onSegmentTextChange = viewModel::onSegmentTextChange,
                                        onSeekToSeconds = viewModel::seekAudioToSeconds,
                                        onToggleReadAloud = viewModel::toggleReadAloud,
                                        onPolishTranscript = viewModel::polishDraftTranscript,
                                        onFactCheckTranscript = { viewModel.factCheckDraftTranscript() },
                                        onCopyTranscript = copyTranscript
                                    )
                                    if (note.claimVerdicts.isNotEmpty() ||
                                        note.factCheckSummary.isNotBlank() ||
                                        note.sources.isNotEmpty()
                                    ) {
                                        TranscriptFactCheckResultsCard(
                                            note = note,
                                            isFactChecking = isFactCheckingTranscript,
                                            onVerifySingleClaim = { claim ->
                                                viewModel.factCheckDraftTranscript(claim)
                                            },
                                            onSourceClick = openExternalUrl
                                        )
                                    }
                                    Spacer(modifier = Modifier.navigationBarsPadding())
                                }

                                HorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    JournalEntryEditorCard(
                                        draft = draft,
                                        onTitleChange = viewModel::onTitleChange,
                                        onCategoryChange = viewModel::onCategoryChange,
                                        onAddTags = viewModel::addTags,
                                        onRemoveTag = viewModel::removeTag,
                                        onSummaryChange = viewModel::onSummaryChange,
                                        onUpdateActionItem = viewModel::updateActionItem,
                                        onAddActionItem = viewModel::addActionItem,
                                        onRemoveActionItem = viewModel::removeActionItem
                                    )
                                    Spacer(modifier = Modifier.navigationBarsPadding())
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .imePadding()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ReviewMetadataStrip(note = note, transcript = draft.transcript)

                                if (playbackState.hasAudio) {
                                    StudioAudioPlayerCard(
                                        playbackState = playbackState,
                                        onTogglePlayPause = viewModel::toggleAudioPlayback,
                                        onSeekTo = viewModel::seekAudio,
                                        onCycleSpeed = viewModel::cyclePlaybackSpeed
                                    )
                                }

                                TranscriptEditorWorkspaceCard(
                                    noteId = note.id,
                                    transcript = draft.transcript,
                                    playbackState = playbackState,
                                    readAloudState = readAloudState,
                                    isPolishingTranscript = isPolishingTranscript,
                                    isFactCheckingTranscript = isFactCheckingTranscript,
                                    onTranscriptChange = viewModel::onTranscriptChange,
                                    onSegmentTextChange = viewModel::onSegmentTextChange,
                                    onSeekToSeconds = viewModel::seekAudioToSeconds,
                                    onToggleReadAloud = viewModel::toggleReadAloud,
                                    onPolishTranscript = viewModel::polishDraftTranscript,
                                    onFactCheckTranscript = { viewModel.factCheckDraftTranscript() },
                                    onCopyTranscript = copyTranscript
                                )

                                if (note.claimVerdicts.isNotEmpty() ||
                                    note.factCheckSummary.isNotBlank() ||
                                    note.sources.isNotEmpty()
                                ) {
                                    TranscriptFactCheckResultsCard(
                                        note = note,
                                        isFactChecking = isFactCheckingTranscript,
                                        onVerifySingleClaim = { claim ->
                                            viewModel.factCheckDraftTranscript(claim)
                                        },
                                        onSourceClick = openExternalUrl
                                    )
                                }

                                JournalEntryEditorCard(
                                    draft = draft,
                                    onTitleChange = viewModel::onTitleChange,
                                    onCategoryChange = viewModel::onCategoryChange,
                                    onAddTags = viewModel::addTags,
                                    onRemoveTag = viewModel::removeTag,
                                    onSummaryChange = viewModel::onSummaryChange,
                                    onUpdateActionItem = viewModel::updateActionItem,
                                    onAddActionItem = viewModel::addActionItem,
                                    onRemoveActionItem = viewModel::removeActionItem
                                )

                                Spacer(modifier = Modifier.navigationBarsPadding())
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.review_discard_dialog_title)) },
            text = { Text(stringResource(R.string.review_discard_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }
}

@Composable
private fun ReviewMetadataStrip(
    note: VoiceNote,
    transcript: String
) {
    val wordCount = remember(transcript) { countTranscriptWords(transcript) }
    val charCount = transcript.length

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (note.durationSeconds > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = formatDuration(note.durationSeconds),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = localizedTimestamp(note.updatedAt ?: note.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = stringResource(R.string.review_word_char_count, wordCount, charCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("review_word_count")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptEditorWorkspaceCard(
    noteId: String,
    transcript: String,
    playbackState: AudioPlaybackState,
    readAloudState: ReadAloudState,
    isPolishingTranscript: Boolean,
    isFactCheckingTranscript: Boolean,
    onTranscriptChange: (String) -> Unit,
    onSegmentTextChange: (Int, String) -> Unit,
    onSeekToSeconds: (Long) -> Unit,
    onToggleReadAloud: (String, String) -> Unit,
    onPolishTranscript: () -> Unit,
    onFactCheckTranscript: () -> Unit,
    onCopyTranscript: () -> Unit
) {
    val segments = remember(transcript) { parseTranscriptSegments(transcript) }
    var editorMode by remember {
        mutableStateOf(
            if (segments.any { it.timestampLabel != null }) TranscriptEditorMode.SEGMENTS
            else TranscriptEditorMode.FULL_TEXT
        )
    }
    var editingSegmentIndex by remember { mutableIntStateOf(-1) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_full_transcript),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.badge_noise_filler_cleaned),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = editorMode == TranscriptEditorMode.SEGMENTS,
                    onClick = { editorMode = TranscriptEditorMode.SEGMENTS },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("review_mode_segments_button")
                ) {
                    Text(
                        text = stringResource(R.string.review_mode_segments),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = editorMode == TranscriptEditorMode.FULL_TEXT,
                    onClick = {
                        editingSegmentIndex = -1
                        editorMode = TranscriptEditorMode.FULL_TEXT
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("review_mode_full_text_button")
                ) {
                    Text(
                        text = stringResource(R.string.review_mode_full_text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onPolishTranscript,
                    enabled = !isPolishingTranscript && transcript.isNotBlank(),
                    modifier = Modifier.testTag("review_polish_button")
                ) {
                    if (isPolishingTranscript) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.status_cleaning_transcript))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_clean_fillers))
                    }
                }

                OutlinedButton(
                    onClick = onFactCheckTranscript,
                    enabled = !isFactCheckingTranscript && transcript.isNotBlank(),
                    modifier = Modifier.testTag("review_fact_check_button")
                ) {
                    if (isFactCheckingTranscript) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_searching_verifying))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_fact_check_transcript))
                    }
                }

                if (transcript.isNotBlank()) {
                    val speakId = "review_transcript_$noteId"
                    ReadAloudChipButton(
                        isSpeakingThis = readAloudState.isSpeaking && readAloudState.activeItemId == speakId,
                        onClick = { onToggleReadAloud(speakId, transcript) },
                        modifier = Modifier.testTag("review_read_aloud_button")
                    )
                }

                OutlinedButton(
                    onClick = onCopyTranscript,
                    enabled = transcript.isNotBlank(),
                    modifier = Modifier.testTag("review_copy_transcript_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            if (editorMode == TranscriptEditorMode.FULL_TEXT || segments.isEmpty()) {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = onTranscriptChange,
                    placeholder = { Text(stringResource(R.string.review_transcript_placeholder)) },
                    minLines = 7,
                    maxLines = 18,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_full_transcript_input")
                )
            } else {
                Text(
                    text = stringResource(R.string.review_segment_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val currentSec = playbackState.currentPositionMs / 1000L
                val activeSegmentIndex = remember(segments, currentSec, playbackState.hasAudio) {
                    if (!playbackState.hasAudio) -1
                    else {
                        segments.indexOfLast { seg ->
                            seg.timestampSeconds != null && seg.timestampSeconds <= currentSec
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    segments.forEachIndexed { idx, segment ->
                        EditableTranscriptSegmentRow(
                            index = idx,
                            segment = segment,
                            isActivePlayback = idx == activeSegmentIndex,
                            isEditing = idx == editingSegmentIndex,
                            hasAudio = playbackState.hasAudio && playbackState.isPrepared,
                            onSeekToSeconds = onSeekToSeconds,
                            onStartEditing = { editingSegmentIndex = idx },
                            onFinishEditing = { editingSegmentIndex = -1 },
                            onUpdateSegmentText = { newText ->
                                onSegmentTextChange(idx, newText)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableTranscriptSegmentRow(
    index: Int,
    segment: TranscriptSegment,
    isActivePlayback: Boolean,
    isEditing: Boolean,
    hasAudio: Boolean,
    onSeekToSeconds: (Long) -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onUpdateSegmentText: (String) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isEditing -> MaterialTheme.colorScheme.surfaceContainerLow
            isActivePlayback -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(180),
        label = "segment_edit_bg_$index"
    )
    var localText by remember(index, segment.text, isEditing) { mutableStateOf(segment.text) }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = if (isEditing || isActivePlayback) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !isEditing, onClick = onStartEditing)
            .testTag("review_segment_$index")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (segment.timestampLabel != null && segment.timestampSeconds != null) {
                    Surface(
                        color = if (isActivePlayback) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = hasAudio) {
                                onSeekToSeconds(segment.timestampSeconds)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (hasAudio) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(
                                        R.string.cd_jump_to_time,
                                        segment.timestampLabel
                                    ),
                                    tint = if (isActivePlayback) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = segment.timestampLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isActivePlayback) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isEditing) {
                    TextButton(
                        onClick = {
                            onUpdateSegmentText(localText)
                            onFinishEditing()
                        },
                        modifier = Modifier.testTag("review_segment_done_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.review_done_editing_segment))
                    }
                } else {
                    IconButton(
                        onClick = onStartEditing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_segment_edit_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.review_edit_segment_label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = localText,
                    onValueChange = {
                        localText = it
                        onUpdateSegmentText(it)
                    },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_segment_input_$index")
                )
            } else {
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEntryEditorCard(
    draft: TranscriptReviewDraft,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (NoteCategory) -> Unit,
    onAddTags: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onUpdateActionItem: (Int, String) -> Unit,
    onAddActionItem: (String) -> Unit,
    onRemoveActionItem: (Int) -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    var newActionItemInput by remember { mutableStateOf("") }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.review_journal_details_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.edit_field_title)) },
                singleLine = true,
                isError = draft.title.isBlank(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_title_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.review_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    NoteCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = draft.category == cat,
                            onClick = { onCategoryChange(cat) },
                            label = { Text(stringResource(cat.labelRes())) },
                            modifier = Modifier.testTag("review_category_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.review_tags_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (draft.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        draft.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveTag(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_remove_tag, tag),
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text(stringResource(R.string.tag_input_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("review_tag_input")
                    )
                    OutlinedButton(
                        onClick = {
                            onAddTags(tagInput)
                            tagInput = ""
                        },
                        enabled = tagInput.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_add_tag_button")
                    ) {
                        Text(stringResource(R.string.action_add_tag))
                    }
                }
            }

            OutlinedTextField(
                value = draft.summary,
                onValueChange = onSummaryChange,
                label = { Text(stringResource(R.string.edit_field_summary)) },
                minLines = 3,
                maxLines = 7,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_summary_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.edit_field_action_items),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                draft.actionItems.forEachIndexed { idx, task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = task,
                            onValueChange = { onUpdateActionItem(idx, it) },
                            placeholder = { Text(stringResource(R.string.review_action_item_hint)) },
                            singleLine = false,
                            maxLines = 3,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("review_action_item_input_$idx")
                        )
                        IconButton(
                            onClick = { onRemoveActionItem(idx) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("review_remove_action_item_$idx")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_remove_action_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newActionItemInput,
                        onValueChange = { newActionItemInput = it },
                        placeholder = { Text(stringResource(R.string.review_add_action_item_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("review_new_action_item_input")
                    )
                    IconButton(
                        onClick = {
                            onAddActionItem(newActionItemInput)
                            newActionItemInput = ""
                        },
                        enabled = newActionItemInput.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_add_action_item_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_action_item),
                            tint = if (newActionItemInput.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptFactCheckResultsCard(
    note: VoiceNote,
    isFactChecking: Boolean,
    onVerifySingleClaim: (String) -> Unit,
    onSourceClick: (String) -> Unit
) {
    val inaccuracyCount = remember(note.claimVerdicts) {
        note.claimVerdicts.count {
            val status = it.normalizedStatus()
            status == ClaimVerdictStatus.DISPUTED || status == ClaimVerdictStatus.UPDATED
        }
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (inaccuracyCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_fact_check_results_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (inaccuracyCount > 0) Icons.Rounded.WarningAmber else Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = if (inaccuracyCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.review_factcheck_results_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (note.claimVerdicts.isNotEmpty()) {
                    val badgeContainer = if (inaccuracyCount > 0) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                    val badgeContent = if (inaccuracyCount > 0) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer

                    Surface(
                        color = badgeContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (inaccuracyCount > 0) Icons.Rounded.WarningAmber
                                else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = badgeContent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (inaccuracyCount > 0) {
                                    stringResource(R.string.review_inaccuracies_found, inaccuracyCount)
                                } else {
                                    stringResource(R.string.review_all_claims_verified)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = badgeContent
                            )
                        }
                    }
                }
            }

            if (note.claimVerdicts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    note.claimVerdicts.forEach { verdict ->
                        ClaimVerdictCard(
                            verdict = verdict,
                            isFactChecking = isFactChecking,
                            onVerifySingleClaim = onVerifySingleClaim
                        )
                    }
                }
            }

            if (note.factCheckSummary.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.detail_verification_findings),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MarkdownText(markdown = note.factCheckSummary)
                    }
                }
            }

            if (note.sources.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.detail_attached_sources_count, note.sources.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    note.sources.forEach { source ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSourceClick(source.url) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.title.ifBlank { source.url },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = source.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = stringResource(R.string.cd_open_source_link),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
