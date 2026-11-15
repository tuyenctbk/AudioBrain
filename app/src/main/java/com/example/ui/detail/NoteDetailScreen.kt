package com.example.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.ReadAloudState
import com.example.audio.RecordingState
import com.example.data.export.NoteMarkdownExporter
import com.example.data.export.TaskActionUtils
import com.example.data.model.ChatMessage
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckFreshnessHelper
import com.example.data.model.FactCheckHistoryHelper
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.VoiceNote
import com.example.ui.common.FactCheckSearchHistorySheet
import com.example.ui.common.ManageTagsDialog
import com.example.ui.common.MarkdownText
import com.example.ui.common.displayLabelRes
import com.example.ui.common.labelRes
import com.example.ui.common.localizedCategoryLabel
import com.example.ui.common.localizedTimestamp
import com.example.ui.journal.CrossScreenRecordingSheet
import com.example.ui.journal.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    viewModel: NoteDetailViewModel,
    recordingState: RecordingState = RecordingState(),
    onTogglePauseRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onOpenTranscriptReview: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val noteState by viewModel.noteState.collectAsStateWithLifecycle()
    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val isFactChecking by viewModel.isFactChecking.collectAsStateWithLifecycle()
    val isGeneratingFlashcards by viewModel.isGeneratingFlashcards.collectAsStateWithLifecycle()
    val isPolishingTranscript by viewModel.isPolishingTranscript.collectAsStateWithLifecycle()
    val isAdaptingSummary by viewModel.isAdaptingSummary.collectAsStateWithLifecycle()
    val isStreamingChat by viewModel.isStreamingChat.collectAsStateWithLifecycle()
    val isListeningVoiceQuestion by viewModel.isListeningVoiceQuestion.collectAsStateWithLifecycle()
    val voiceQuestionSeconds by viewModel.voiceQuestionElapsedSeconds.collectAsStateWithLifecycle()
    val isTranscribingVoiceQuestion by viewModel.isTranscribingVoiceQuestion.collectAsStateWithLifecycle()
    val streamingBubble by viewModel.streamingBubble.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val readAloudState by viewModel.readAloudState.collectAsStateWithLifecycle()
    val noteStructureMode by viewModel.noteStructureMode.collectAsStateWithLifecycle()
    val contextualSummarization by viewModel.contextualSummarization.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val voiceQuestionPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceQuestionRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_mic_required_voice_qa))
            }
        }
    }

    val openSafeUri: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser))
            }
        }
    }

    val addTaskToCalendar: (VoiceNote, String) -> Unit = { note, task ->
        val opened = TaskActionUtils.openCalendarForTask(
            context = context,
            noteTitle = note.title,
            taskText = task,
            noteSummary = note.summary
        )
        if (!opened) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_calendar))
            }
        }
    }

    val copyToClipboard: (String, String) -> Unit = { text, toastMessage ->
        clipboardManager.setText(AnnotatedString(text))
        coroutineScope.launch {
            snackbarHostState.showSnackbar(toastMessage)
        }
    }

    LaunchedEffect(actionError) {
        val err = actionError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    val currentNote = (noteState as? NoteDetailUiState.Ready)?.note
    val pinRotation by animateFloatAsState(
        targetValue = if (currentNote?.pinned == true) 0f else -35f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "detail_pin_rotation"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = recordingState.isRecording && !isListeningVoiceQuestion) {
                CrossScreenRecordingSheet(
                    recordingState = recordingState,
                    onTogglePauseRecording = onTogglePauseRecording,
                    onStopRecording = {
                        onStopRecording()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_recording_queued))
                        }
                    },
                    onSaveDraft = {
                        onSaveDraft()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_draft_saved))
                        }
                    },
                    onCancelRecording = onCancelRecording
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back_to_journal)
                        )
                    }
                },
                title = {
                    Text(
                        text = currentNote?.title ?: stringResource(R.string.fallback_voice_note_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (currentNote != null) {
                        IconButton(
                            onClick = {
                                val plainText = NoteMarkdownExporter.toPlainText(currentNote, context)
                                val shared = NoteMarkdownExporter.shareNoteAsPlainText(
                                    context = context,
                                    note = currentNote
                                )
                                if (!shared) {
                                    copyToClipboard(plainText, context.getString(R.string.snackbar_note_copied))
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("export_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.action_share_note)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.togglePin(currentNote) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (currentNote.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (currentNote.pinned) R.string.detail_unpin else R.string.detail_pin
                                ),
                                tint = if (currentNote.pinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(pinRotation)
                            )
                        }
                        IconButton(
                            onClick = onOpenTranscriptReview,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_edit_entry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                }
            )
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
                            Text(stringResource(R.string.detail_note_not_found), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onBack) { Text(stringResource(R.string.detail_back_to_journal_cta)) }
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
                    val sourceCountSuffix = if (note.sources.isNotEmpty()) " (${note.sources.size})" else ""
                    val chatCountSuffix = if (messages.isNotEmpty()) " (${messages.size})" else ""

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpandedScreen = maxWidth >= 840.dp
                        if (isExpandedScreen) {
                            val leftTabs = listOf(
                                stringResource(R.string.tab_overview),
                                stringResource(R.string.tab_factcheck) + sourceCountSuffix,
                                stringResource(R.string.tab_transcript)
                            )
                            val leftPagerState = rememberPagerState(pageCount = { leftTabs.size })

                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                ) {
                                    PrimaryScrollableTabRow(
                                        selectedTabIndex = leftPagerState.currentPage,
                                        edgePadding = 20.dp
                                    ) {
                                        leftTabs.forEachIndexed { idx, label ->
                                            Tab(
                                                selected = leftPagerState.currentPage == idx,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        leftPagerState.animateScrollToPage(idx)
                                                    }
                                                },
                                                text = { Text(label) }
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = leftPagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        when (page) {
                                            0 -> OverviewTab(
                                                note = note,
                                                noteStructureMode = noteStructureMode,
                                                contextualSummarization = contextualSummarization,
                                                isAdaptingSummary = isAdaptingSummary,
                                                playbackState = playbackState,
                                                readAloudState = readAloudState,
                                                isGeneratingFlashcards = isGeneratingFlashcards,
                                                onToggleStructureMode = viewModel::toggleNoteStructureMode,
                                                onAdaptSummaryTone = { viewModel.adaptSummaryTone(note) },
                                                onTogglePlayPause = viewModel::toggleAudioPlayback,
                                                onSeekTo = viewModel::seekAudio,
                                                onCycleSpeed = viewModel::cyclePlaybackSpeed,
                                                onToggleReadAloud = viewModel::toggleReadAloud,
                                                onToggleTask = { idx -> viewModel.toggleActionItem(note, idx) },
                                                onAddTaskToCalendar = { task -> addTaskToCalendar(note, task) },
                                                onCopyTasks = {
                                                    val tasksMarkdown = note.actionItems.mapIndexed { idx, task ->
                                                        val done = note.completedActionIndices.contains(idx.toLong())
                                                        "${if (done) "[x]" else "[ ]"} $task"
                                                    }.joinToString("\n")
                                                    copyToClipboard(tasksMarkdown, context.getString(R.string.snackbar_tasks_copied))
                                                },
                                                onGenerateFlashcards = { viewModel.generateFlashcards(note) },
                                                onRemoveTag = { tag -> viewModel.removeTag(note, tag) },
                                                onOpenManageTags = { showManageTagsDialog = true },
                                                onOpenFactCheckTab = {
                                                    coroutineScope.launch { leftPagerState.animateScrollToPage(1) }
                                                }
                                            )
                                            1 -> FactCheckTab(
                                                note = note,
                                                allNotes = allNotes,
                                                searchHistory = searchHistory,
                                                isFactChecking = isFactChecking,
                                                readAloudState = readAloudState,
                                                onToggleReadAloud = viewModel::toggleReadAloud,
                                                onRunFactCheck = { custom -> viewModel.runFactCheck(note, custom) },
                                                onOpenUri = openSafeUri,
                                                onDeleteSearchHistoryItem = viewModel::deleteSearchHistoryItem,
                                                onOpenNote = onOpenNote
                                            )
                                            else -> TranscriptTab(
                                                note = note,
                                                playbackState = playbackState,
                                                isPolishingTranscript = isPolishingTranscript,
                                                onTogglePlayPause = viewModel::toggleAudioPlayback,
                                                onSeekTo = viewModel::seekAudio,
                                                onSeekToSeconds = viewModel::seekAudioToSeconds,
                                                onCycleSpeed = viewModel::cyclePlaybackSpeed,
                                                onPolishTranscript = { viewModel.polishNoteTranscript(note) },
                                                onReviewAndEdit = onOpenTranscriptReview,
                                                onCopyTranscript = {
                                                    copyToClipboard(note.transcript, context.getString(R.string.snackbar_transcript_copied))
                                                }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .fillMaxHeight()
                                ) {
                                    ChatTab(
                                        messages = messages,
                                        streamingBubble = streamingBubble,
                                        isStreaming = isStreamingChat,
                                        isListeningVoice = isListeningVoiceQuestion,
                                        voiceElapsedSeconds = voiceQuestionSeconds,
                                        isTranscribingVoice = isTranscribingVoiceQuestion,
                                        readAloudState = readAloudState,
                                        onToggleReadAloud = viewModel::toggleReadAloud,
                                        onToggleVoiceQuestion = {
                                            if (isListeningVoiceQuestion) {
                                                viewModel.stopAndSubmitVoiceQuestion(note)
                                            } else {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) {
                                                    viewModel.startVoiceQuestionRecording()
                                                } else {
                                                    voiceQuestionPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        },
                                        onCancelVoiceQuestion = viewModel::cancelVoiceQuestionRecording,
                                        onSendMessage = { msg -> viewModel.sendChatMessage(note, msg) },
                                        onCopyMessage = { text -> copyToClipboard(text, context.getString(R.string.snackbar_reply_copied)) },
                                        onOpenUri = openSafeUri
                                    )
                                }
                            }
                        } else {
                            val tabs = listOf(
                                stringResource(R.string.tab_overview),
                                stringResource(R.string.tab_factcheck) + sourceCountSuffix,
                                stringResource(R.string.tab_transcript),
                                stringResource(R.string.tab_chat) + chatCountSuffix
                            )
                            val pagerState = rememberPagerState(pageCount = { tabs.size })

                            Column(modifier = Modifier.fillMaxSize()) {
                                PrimaryScrollableTabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    edgePadding = 16.dp
                                ) {
                                    tabs.forEachIndexed { idx, label ->
                                        Tab(
                                            selected = pagerState.currentPage == idx,
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(idx)
                                                }
                                            },
                                            text = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            },
                                            modifier = Modifier.testTag("detail_tab_$idx")
                                        )
                                    }
                                }

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    when (page) {
                                        0 -> OverviewTab(
                                            note = note,
                                            noteStructureMode = noteStructureMode,
                                            contextualSummarization = contextualSummarization,
                                            isAdaptingSummary = isAdaptingSummary,
                                            playbackState = playbackState,
                                            readAloudState = readAloudState,
                                            isGeneratingFlashcards = isGeneratingFlashcards,
                                            onToggleStructureMode = viewModel::toggleNoteStructureMode,
                                            onAdaptSummaryTone = { viewModel.adaptSummaryTone(note) },
                                            onTogglePlayPause = viewModel::toggleAudioPlayback,
                                            onSeekTo = viewModel::seekAudio,
                                            onCycleSpeed = viewModel::cyclePlaybackSpeed,
                                            onToggleReadAloud = viewModel::toggleReadAloud,
                                            onToggleTask = { idx -> viewModel.toggleActionItem(note, idx) },
                                            onAddTaskToCalendar = { task -> addTaskToCalendar(note, task) },
                                            onCopyTasks = {
                                                val tasksMarkdown = note.actionItems.mapIndexed { idx, task ->
                                                    val done = note.completedActionIndices.contains(idx.toLong())
                                                    "${if (done) "[x]" else "[ ]"} $task"
                                                }.joinToString("\n")
                                                copyToClipboard(tasksMarkdown, context.getString(R.string.snackbar_tasks_copied))
                                            },
                                            onGenerateFlashcards = { viewModel.generateFlashcards(note) },
                                            onRemoveTag = { tag -> viewModel.removeTag(note, tag) },
                                            onOpenManageTags = { showManageTagsDialog = true },
                                            onOpenFactCheckTab = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                            }
                                        )
                                        1 -> FactCheckTab(
                                            note = note,
                                            allNotes = allNotes,
                                            searchHistory = searchHistory,
                                            isFactChecking = isFactChecking,
                                            readAloudState = readAloudState,
                                            onToggleReadAloud = viewModel::toggleReadAloud,
                                            onRunFactCheck = { custom -> viewModel.runFactCheck(note, custom) },
                                            onOpenUri = openSafeUri,
                                            onDeleteSearchHistoryItem = viewModel::deleteSearchHistoryItem,
                                            onOpenNote = onOpenNote
                                        )
                                        2 -> TranscriptTab(
                                            note = note,
                                            playbackState = playbackState,
                                            isPolishingTranscript = isPolishingTranscript,
                                            onTogglePlayPause = viewModel::toggleAudioPlayback,
                                            onSeekTo = viewModel::seekAudio,
                                            onSeekToSeconds = viewModel::seekAudioToSeconds,
                                            onCycleSpeed = viewModel::cyclePlaybackSpeed,
                                            onPolishTranscript = { viewModel.polishNoteTranscript(note) },
                                            onReviewAndEdit = onOpenTranscriptReview,
                                            onCopyTranscript = {
                                                copyToClipboard(note.transcript, context.getString(R.string.snackbar_transcript_copied))
                                            }
                                        )
                                        else -> ChatTab(
                                            messages = messages,
                                            streamingBubble = streamingBubble,
                                            isStreaming = isStreamingChat,
                                            isListeningVoice = isListeningVoiceQuestion,
                                            voiceElapsedSeconds = voiceQuestionSeconds,
                                            isTranscribingVoice = isTranscribingVoiceQuestion,
                                            readAloudState = readAloudState,
                                            onToggleReadAloud = viewModel::toggleReadAloud,
                                            onToggleVoiceQuestion = {
                                                if (isListeningVoiceQuestion) {
                                                    viewModel.stopAndSubmitVoiceQuestion(note)
                                                } else {
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        viewModel.startVoiceQuestionRecording()
                                                    } else {
                                                        voiceQuestionPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                }
                                            },
                                            onCancelVoiceQuestion = viewModel::cancelVoiceQuestionRecording,
                                            onSendMessage = { msg -> viewModel.sendChatMessage(note, msg) },
                                            onCopyMessage = { text -> copyToClipboard(text, context.getString(R.string.snackbar_reply_copied)) },
                                            onOpenUri = openSafeUri
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

    if (showManageTagsDialog && currentNote != null) {
        ManageTagsDialog(
            noteTitle = currentNote.title,
            initialTags = currentNote.tags,
            onDismiss = { showManageTagsDialog = false },
            onSaveTags = { updatedTags ->
                viewModel.updateTags(currentNote, updatedTags)
                showManageTagsDialog = false
            }
        )
    }

    if (showDeleteDialog && currentNote != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_detail_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteNote(onDeleted = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    note: VoiceNote,
    noteStructureMode: NoteStructureMode,
    contextualSummarization: Boolean,
    isAdaptingSummary: Boolean,
    playbackState: AudioPlaybackState,
    readAloudState: ReadAloudState,
    isGeneratingFlashcards: Boolean,
    onToggleStructureMode: () -> Unit,
    onAdaptSummaryTone: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleReadAloud: (String, String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onAddTaskToCalendar: (String) -> Unit,
    onCopyTasks: () -> Unit,
    onGenerateFlashcards: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onOpenManageTags: () -> Unit,
    onOpenFactCheckTab: () -> Unit
) {
    val isBriefMode = noteStructureMode == NoteStructureMode.BRIEF
    val totalTasks = note.actionItems.size
    val validCompletedCount = note.completedActionIndices.count { it >= 0L && it < totalTasks }
    val targetProgress = if (totalTasks > 0) validCompletedCount.toFloat() / totalTasks.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "task_progress_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = localizedCategoryLabel(note.category),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (contextualSummarization) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.detail_tone_prefix,
                                stringResource(note.topicTone.displayLabelRes())
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
                if (note.durationSeconds > 0L) {
                    Text(
                        text = formatDuration(note.durationSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = localizedTimestamp(note.updatedAt ?: note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleStructureMode)
                    .testTag("detail_structure_mode_chip")
            ) {
                Text(
                    text = stringResource(
                        R.string.detail_structure_prefix,
                        stringResource(noteStructureMode.displayLabelRes())
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Text(
            text = note.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            note.tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = onOpenManageTags,
                    label = {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_tag, tag),
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onRemoveTag(tag) }
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("detail_tag_$tag")
                )
            }
            AssistChip(
                onClick = onOpenManageTags,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(if (note.tags.isEmpty()) R.string.action_add_tag else R.string.action_edit_tags),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("detail_manage_tags_button")
            )
        }

        if (playbackState.hasAudio) {
            StudioAudioPlayerCard(
                playbackState = playbackState,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onCycleSpeed = onCycleSpeed
            )
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(if (isBriefMode) R.string.detail_summary_brief else R.string.detail_summary_full),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (contextualSummarization) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(note.topicTone.displayLabelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (contextualSummarization && note.transcript.isNotBlank()) {
                            AssistChip(
                                onClick = onAdaptSummaryTone,
                                enabled = !isAdaptingSummary,
                                leadingIcon = {
                                    if (isAdaptingSummary) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (isAdaptingSummary) stringResource(R.string.status_adapting_summary) else stringResource(R.string.action_adapt_summary),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("adapt_summary_tone_button")
                            )
                        }
                        if (note.summary.isNotBlank()) {
                            val summarySpeakId = "summary_${note.id}"
                            ReadAloudChipButton(
                                isSpeakingThis = readAloudState.isSpeaking && readAloudState.activeItemId == summarySpeakId,
                                onClick = { onToggleReadAloud(summarySpeakId, note.summary) },
                                modifier = Modifier.testTag("read_aloud_summary")
                            )
                        }
                    }
                }
                Text(
                    text = note.summary.ifBlank { stringResource(R.string.detail_no_summary) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isBriefMode) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val displayedDecisions = if (isBriefMode) note.decisions.take(2) else note.decisions
        KeyDecisionsCard(decisions = displayedDecisions)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.detail_action_items_count, validCompletedCount, totalTasks),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (totalTasks > 0) {
                    TextButton(onClick = onCopyTasks) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.detail_copy_tasks), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (totalTasks > 0) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (validCompletedCount == totalTasks) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (note.actionItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_no_action_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val visibleItems = if (isBriefMode) note.actionItems.take(3) else note.actionItems
                visibleItems.forEachIndexed { index, task ->
                    val checked = note.completedActionIndices.contains(index.toLong())
                    val rowBg by animateColorAsState(
                        targetValue = if (checked) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(180),
                        label = "task_row_bg_$index"
                    )
                    Surface(
                        color = rowBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (checked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onToggleTask(index) }
                            .testTag("action_item_$index")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = stringResource(
                                    if (checked) R.string.cd_task_completed else R.string.cd_task_incomplete
                                ),
                                tint = if (checked) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = task,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp)
                            )
                            IconButton(
                                onClick = { onAddTaskToCalendar(task) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("calendar_task_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Event,
                                    contentDescription = stringResource(R.string.action_add_to_calendar),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
                if (isBriefMode && note.actionItems.size > 3) {
                    TextButton(
                        onClick = onToggleStructureMode,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.detail_switch_to_detailed_tasks, note.actionItems.size))
                    }
                }
            }
        }

        val isVerified = note.factCheckStatus == FactCheckStatus.VERIFIED.value || note.sources.isNotEmpty()
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isVerified) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenFactCheckTab)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Rounded.CheckCircle else Icons.Rounded.TravelExplore,
                        contentDescription = null,
                        tint = if (isVerified) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Column {
                        Text(
                            text = stringResource(
                                if (isVerified) R.string.detail_factcheck_complete
                                else R.string.detail_verify_claims_prompt
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isVerified) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (isVerified) {
                                stringResource(R.string.detail_sources_attached_subtitle, note.sources.size)
                            } else if (note.claimsToVerify.isNotEmpty()) {
                                stringResource(R.string.detail_claims_ready_subtitle, note.claimsToVerify.size)
                            } else {
                                stringResource(R.string.detail_tap_to_verify_subtitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isVerified) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        StudyFlashcardsCard(
            category = note.category,
            flashcards = note.flashcards,
            isGenerating = isGeneratingFlashcards,
            onGenerate = onGenerateFlashcards
        )

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactCheckTab(
    note: VoiceNote,
    allNotes: List<VoiceNote>,
    searchHistory: List<FactCheckSearchItem>,
    isFactChecking: Boolean,
    readAloudState: ReadAloudState,
    onToggleReadAloud: (String, String) -> Unit,
    onRunFactCheck: (String?) -> Unit,
    onOpenUri: (String) -> Unit,
    onDeleteSearchHistoryItem: (FactCheckSearchItem) -> Unit,
    onOpenNote: (String) -> Unit
) {
    var customClaim by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }

    val suggestions = remember(customClaim, allNotes, searchHistory) {
        FactCheckHistoryHelper.buildSuggestions(
            notes = allNotes,
            recordedHistory = searchHistory,
            inputQuery = customClaim,
            limit = 4
        )
    }

    val commonTopics = remember(allNotes, searchHistory) {
        FactCheckHistoryHelper.buildSuggestions(
            notes = allNotes,
            recordedHistory = searchHistory,
            inputQuery = "",
            limit = 5
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
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
                            imageVector = Icons.Rounded.TravelExplore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(R.string.detail_live_search_grounding),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { showHistorySheet = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(36.dp)
                            .testTag("fact_check_open_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.search_history_title), style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (note.claimsToVerify.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_detected_claims_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        note.claimsToVerify.forEach { claim ->
                            AssistChip(
                                onClick = {
                                    if (!isFactChecking) onRunFactCheck(claim)
                                },
                                enabled = !isFactChecking,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = claim,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customClaim,
                    onValueChange = { customClaim = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.detail_custom_claim_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (customClaim.isNotEmpty()) {
                                IconButton(onClick = { customClaim = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_input)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showHistorySheet = true },
                                modifier = Modifier.testTag("custom_claim_history_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = stringResource(R.string.search_history_title),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_claim_input")
                )

                // Auto-complete suggestion card when user types
                AnimatedVisibility(visible = customClaim.isNotBlank() && suggestions.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("autocomplete_suggestions_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.autocomplete_suggestions_title),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = { showHistorySheet = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(stringResource(R.string.action_all_topics), style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            suggestions.forEachIndexed { idx, suggestion ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { customClaim = suggestion.query }
                                        .testTag("autocomplete_item_$idx")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val statusIcon = when (suggestion.status) {
                                                ClaimVerdictStatus.SUPPORTED -> Icons.Rounded.CheckCircle
                                                ClaimVerdictStatus.UPDATED -> Icons.Rounded.WarningAmber
                                                ClaimVerdictStatus.DISPUTED -> Icons.Rounded.Info
                                                ClaimVerdictStatus.UNVERIFIED -> Icons.Rounded.History
                                            }
                                            val statusColor = when (suggestion.status) {
                                                ClaimVerdictStatus.SUPPORTED -> MaterialTheme.colorScheme.secondary
                                                ClaimVerdictStatus.UPDATED -> MaterialTheme.colorScheme.tertiary
                                                ClaimVerdictStatus.DISPUTED -> MaterialTheme.colorScheme.error
                                                ClaimVerdictStatus.UNVERIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = suggestion.query,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${suggestion.verificationCount}×",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    onRunFactCheck(suggestion.query)
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                                                    contentDescription = stringResource(R.string.cd_verify_now),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent topics quick chips when input is empty
                if (customClaim.isBlank() && commonTopics.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = stringResource(R.string.recent_topics_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        commonTopics.take(3).forEach { topic ->
                            AssistChip(
                                onClick = { customClaim = topic.query },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "${topic.displayTopic} (${topic.verificationCount}×)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("recent_topic_chip_${topic.query.hashCode()}")
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val trimmed = customClaim.trim().takeIf { it.isNotBlank() }
                        onRunFactCheck(trimmed)
                        customClaim = ""
                    },
                    enabled = !isFactChecking && (
                        note.transcript.isNotBlank() ||
                            note.summary.isNotBlank() ||
                            note.claimsToVerify.isNotEmpty() ||
                            customClaim.isNotBlank()
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("fact_check_button")
                ) {
                    if (isFactChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.detail_searching_verifying))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (note.factCheckSummary.isBlank()) {
                                stringResource(R.string.action_fact_check)
                            } else {
                                stringResource(R.string.action_reverify)
                            }
                        )
                    }
                }
            }
        }

        if (FactCheckFreshnessHelper.isFactCheckStale(note)) {
            StaleFactCheckBanner(
                daysOld = FactCheckFreshnessHelper.daysSinceLastUpdate(note),
                isFactChecking = isFactChecking,
                onReverifyClick = { onRunFactCheck(null) }
            )
        }

        if (note.claimVerdicts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.detail_structured_verdicts_count, note.claimVerdicts.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                note.claimVerdicts.forEachIndexed { idx, verdict ->
                    ClaimVerdictCard(
                        verdict = verdict,
                        isFactChecking = isFactChecking,
                        onVerifySingleClaim = { claimText -> onRunFactCheck(claimText) },
                        modifier = Modifier.testTag("claim_verdict_$idx")
                    )
                }
            }
        }

        AnimatedVisibility(visible = note.factCheckSummary.isNotBlank()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.detail_verification_findings),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        val factSpeakId = "factcheck_${note.id}"
                        ReadAloudChipButton(
                            isSpeakingThis = readAloudState.isSpeaking && readAloudState.activeItemId == factSpeakId,
                            onClick = { onToggleReadAloud(factSpeakId, note.factCheckSummary) },
                            modifier = Modifier.testTag("read_aloud_factcheck")
                        )
                    }
                    MarkdownText(
                        markdown = note.factCheckSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        if (note.searchQueries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.detail_search_queries_used),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    note.searchQueries.forEach { q ->
                        AssistChip(
                            onClick = {
                                val encoded = android.net.Uri.encode(q)
                                onOpenUri("https://www.google.com/search?q=$encoded")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(q, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }

        if (note.sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.detail_attached_sources_count, note.sources.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                note.sources.forEachIndexed { idx, source ->
                    SourceLinkCard(
                        index = idx + 1,
                        source = source,
                        onClick = { onOpenUri(source.url) },
                        modifier = Modifier.testTag("fact_source_$idx")
                    )
                }
            }
        }

        if (showHistorySheet) {
            FactCheckSearchHistorySheet(
                notes = allNotes,
                searchHistory = searchHistory,
                onSelectTopic = { topic ->
                    customClaim = topic
                    showHistorySheet = false
                },
                onReverifyTopic = { topic, _ ->
                    showHistorySheet = false
                    onRunFactCheck(topic)
                },
                onOpenNote = onOpenNote,
                onOpenSourceUrl = onOpenUri,
                onDeleteItem = onDeleteSearchHistoryItem,
                onDismiss = { showHistorySheet = false }
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SourceLinkCard(
    index: Int,
    source: FactSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
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
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptTab(
    note: VoiceNote,
    playbackState: AudioPlaybackState,
    isPolishingTranscript: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToSeconds: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onPolishTranscript: () -> Unit,
    onReviewAndEdit: () -> Unit,
    onCopyTranscript: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.detail_full_transcript),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("noise_filler_cleaned_badge")
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
            if (playbackState.hasAudio) {
                Text(
                    text = stringResource(R.string.detail_tap_timestamp_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onReviewAndEdit,
                    modifier = Modifier.testTag("transcript_review_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.review_entry_nav_chip))
                }
                OutlinedButton(
                    onClick = onPolishTranscript,
                    enabled = !isPolishingTranscript && note.transcript.isNotBlank(),
                    modifier = Modifier.testTag("polish_transcript_button")
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
                    onClick = onCopyTranscript,
                    modifier = Modifier.testTag("copy_transcript_button")
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
        }

        if (playbackState.hasAudio) {
            StudioAudioPlayerCard(
                playbackState = playbackState,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onCycleSpeed = onCycleSpeed
            )
        }

        TimestampedTranscriptCard(
            transcript = note.transcript,
            playbackState = playbackState,
            onSeekToSeconds = onSeekToSeconds
        )

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ChatTab(
    messages: List<ChatMessage>,
    streamingBubble: StreamingChatBubble?,
    isStreaming: Boolean,
    isListeningVoice: Boolean,
    voiceElapsedSeconds: Long,
    isTranscribingVoice: Boolean,
    readAloudState: ReadAloudState,
    onToggleReadAloud: (String, String) -> Unit,
    onToggleVoiceQuestion: () -> Unit,
    onCancelVoiceQuestion: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onOpenUri: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingBubble?.text) {
        val total = messages.size + if (streamingBubble != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    val quickPrompts = listOf(
        stringResource(R.string.chat_quick_prompt_verify),
        stringResource(R.string.chat_quick_prompt_email),
        stringResource(R.string.chat_quick_prompt_gaps)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingBubble == null) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.chat_empty_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = stringResource(R.string.chat_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                quickPrompts.forEach { prompt ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !isStreaming) {
                                                onSendMessage(prompt)
                                            }
                                    ) {
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val speakId = "chat_${msg.id}"
                ChatBubble(
                    isUser = msg.role == "user",
                    text = msg.text,
                    sources = msg.sources,
                    isSpeakingThis = readAloudState.isSpeaking && readAloudState.activeItemId == speakId,
                    onToggleReadAloud = if (msg.role != "user") {
                        { onToggleReadAloud(speakId, msg.text) }
                    } else null,
                    onCopy = if (msg.role != "user") { { onCopyMessage(msg.text) } } else null,
                    onOpenSource = onOpenUri
                )
            }

            if (streamingBubble != null) {
                item(key = "streaming_bubble") {
                    ChatBubble(
                        isUser = false,
                        text = streamingBubble.text.ifBlank { stringResource(R.string.chat_thinking_sources) },
                        sources = streamingBubble.sources,
                        isStreaming = true,
                        isSpeakingThis = false,
                        onToggleReadAloud = null,
                        onCopy = null,
                        onOpenSource = onOpenUri
                    )
                }
            }
        }

        AnimatedVisibility(visible = isListeningVoice || isTranscribingVoice) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isTranscribingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.chat_transcribing_voice),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.chat_listening_duration, formatDuration(voiceElapsedSeconds)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancelVoiceQuestion) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    color = if (isListeningVoice) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isStreaming && !isTranscribingVoice, onClick = onToggleVoiceQuestion)
                        .testTag("chat_voice_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListeningVoice) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.action_ask_by_voice),
                            tint = if (isListeningVoice) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.chat_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input")
                )
                Surface(
                    color = if (input.isNotBlank() && !isStreaming && !isListeningVoice) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = input.isNotBlank() && !isStreaming && !isListeningVoice) {
                            val toSend = input
                            input = ""
                            onSendMessage(toSend)
                        }
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_message),
                                tint = if (input.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubble(
    isUser: Boolean,
    text: String,
    sources: List<FactSource>,
    isStreaming: Boolean = false,
    isSpeakingThis: Boolean = false,
    onToggleReadAloud: (() -> Unit)? = null,
    onCopy: (() -> Unit)?,
    onOpenSource: (String) -> Unit
) {
    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUser) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    MarkdownText(
                        markdown = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }

                if (isStreaming) {
                    val streamPulse = rememberInfiniteTransition(label = "stream_dot_pulse")
                    val dotAlpha by streamPulse.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(550, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "stream_dot_alpha"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha))
                        )
                        Text(
                            text = stringResource(R.string.chat_streaming_live),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (onCopy != null || onToggleReadAloud != null) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (onToggleReadAloud != null) {
                            ReadAloudChipButton(
                                isSpeakingThis = isSpeakingThis,
                                onClick = onToggleReadAloud
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (onCopy != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onCopy)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_reply),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.action_copy),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (sources.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sources.forEach { src ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenSource(src.url) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = src.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 180.dp)
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
