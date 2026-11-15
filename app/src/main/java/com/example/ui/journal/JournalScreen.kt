package com.example.ui.journal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ExternalLaunchCommand
import com.example.R
import com.example.data.billing.QuotaFeature
import com.example.data.billing.SubscriptionTier
import com.example.ui.billing.PaywallBottomSheet
import com.example.audio.RecordingState
import com.example.data.export.NoteMarkdownExporter
import com.example.data.export.TaskActionUtils
import com.example.data.model.FactCheckStatus
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.VoiceNote
import com.example.data.offline.PendingMemo
import com.example.ui.common.FactCheckSearchHistorySheet
import com.example.ui.common.ManageTagsDialog
import com.example.ui.common.NoteTagPill
import com.example.ui.common.displayLabelRes
import com.example.ui.common.localizedCategoryLabel
import com.example.ui.common.localizedTimestamp
import com.example.ui.theme.StudioAmber
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    userDisplayName: String?,
    userEmail: String?,
    externalCommand: ExternalLaunchCommand?,
    onConsumeExternalCommand: () -> Unit,
    onNoteClick: (String) -> Unit,
    onReviewNoteClick: (String) -> Unit = onNoteClick,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val openSafeSourceUrl: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser))
            }
        }
    }

    val notesState by viewModel.filteredNotesState.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val pendingMemos by viewModel.pendingMemos.collectAsStateWithLifecycle()
    val playingDraftId by viewModel.playingDraftId.collectAsStateWithLifecycle()
    val draftPlaybackState by viewModel.draftPlaybackState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val digestState by viewModel.digestState.collectAsStateWithLifecycle()
    val noteStructureMode by viewModel.noteStructureMode.collectAsStateWithLifecycle()
    val contextualSummarization by viewModel.contextualSummarization.collectAsStateWithLifecycle()
    val reverifyingNoteIds by viewModel.reverifyingNoteIds.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val quotaState by viewModel.quotaState.collectAsStateWithLifecycle()
    val showPaywall by viewModel.showPaywall.collectAsStateWithLifecycle()
    val paywallFeature by viewModel.paywallFeature.collectAsStateWithLifecycle()

    var showTypeMemoSheet by remember { mutableStateOf(false) }
    var initialTypedMemo by remember { mutableStateOf("") }
    var showMicRationaleDialog by remember { mutableStateOf(false) }
    var notePendingDelete by remember { mutableStateOf<VoiceNote?>(null) }
    var noteEditingTags by remember { mutableStateOf<VoiceNote?>(null) }
    var draftPendingRename by remember { mutableStateOf<PendingMemo?>(null) }
    var showDigestSheet by remember { mutableStateOf(false) }
    var showOpenTasksSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSearchHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.createdNoteEvents.collect { noteId ->
            onNoteClick(noteId)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.startRecording()
        } else {
            showMicRationaleDialog = true
        }
    }

    val audioImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAndProcessAudio(uri)
        }
    }

    val handleStartRecordClick: () -> Unit = {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.startRecording()
        } else {
            val permissions = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            recordPermissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(externalCommand) {
        when (externalCommand) {
            is ExternalLaunchCommand.QuickRecord -> {
                onConsumeExternalCommand()
                handleStartRecordClick()
            }
            is ExternalLaunchCommand.ImportAudioUri -> {
                onConsumeExternalCommand()
                viewModel.importAndProcessAudio(externalCommand.uri)
            }
            is ExternalLaunchCommand.OpenNote -> {
                val targetId = externalCommand.noteId
                onConsumeExternalCommand()
                onNoteClick(targetId)
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = quotaState.tier == SubscriptionTier.FREE &&
                        !quotaState.ecoLiteMode &&
                        quotaState.anyNearLimit()
                ) {
                    val countdown = quotaState.resetCountdown()
                    Surface(
                        color = StudioAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = StudioAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.nudge_near_limit,
                                    countdown.days,
                                    countdown.hours
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    viewModel.setEcoLiteMode(true)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.snackbar_eco_lite_enabled)
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.nudge_switch_eco_lite),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = StudioAmber
                                )
                            }
                        }
                    }
                }
                StudioCaptureSheet(
                    captureState = captureState,
                    recordingState = recordingState,
                    onStartRecording = handleStartRecordClick,
                    onTogglePauseRecording = viewModel::togglePauseRecording,
                    onStopRecording = viewModel::stopAndProcessRecording,
                    onSaveDraft = viewModel::stopAndSaveForLater,
                    onCancelRecording = viewModel::cancelRecording,
                    onOpenTypeMemo = {
                        initialTypedMemo = ""
                        showTypeMemoSheet = true
                    },
                    onImportAudio = { audioImportLauncher.launch(arrayOf("audio/*")) },
                    onToggleAutoFactCheck = viewModel::setAutoFactCheck
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            StudioHeaderHolder(
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                noteStructureMode = noteStructureMode,
                contextualSummarization = contextualSummarization,
                searchQuery = searchQuery,
                selectedFilter = selectedFilter,
                selectedTag = selectedTag,
                availableTags = availableTags,
                allNotes = allNotes,
                onSearchChange = viewModel::setSearchQuery,
                onFilterSelected = viewModel::setFilter,
                onTagSelected = viewModel::toggleTagFilter,
                onClearTag = { viewModel.setSelectedTag(null) },
                onOpenTasksHub = { showOpenTasksSheet = true },
                onOpenDigest = {
                    showDigestSheet = true
                    if (digestState.content.isBlank() && !digestState.isStreaming) {
                        viewModel.generateDigest(
                            focusTitle = context.getString(R.string.digest_preset_weekly_title),
                            focusPrompt = context.getString(R.string.digest_preset_weekly_prompt)
                        )
                    }
                },
                onOpenSettings = { showSettingsSheet = true },
                onSignOutClick = onSignOutClick
            )

            AnimatedVisibility(visible = pendingMemos.isNotEmpty() || !isOnline) {
                VoiceDraftsAndQueueSection(
                    pendingMemos = pendingMemos,
                    isOnline = isOnline,
                    isSyncing = captureState.phase == CapturePhase.Transcribing || captureState.phase == CapturePhase.FactChecking,
                    playingDraftId = playingDraftId,
                    draftPlaybackState = draftPlaybackState,
                    onTogglePlayDraft = viewModel::togglePlayDraft,
                    onSeekDraftPlayback = viewModel::seekDraftPlayback,
                    onCycleDraftSpeed = viewModel::cycleDraftPlaybackSpeed,
                    onRenameDraftClick = { draft -> draftPendingRename = draft },
                    onTranscribeDraft = viewModel::transcribeSavedDraft,
                    onTranscribeAllDrafts = viewModel::transcribeAllSavedDrafts,
                    onSyncNow = viewModel::retryPendingMemos,
                    onDiscard = viewModel::discardPendingMemo
                )
            }

            val bannerMessage = captureState.errorMessage ?: captureState.infoMessage
            val isErrorBanner = captureState.errorMessage != null
            AnimatedVisibility(visible = bannerMessage != null) {
                Surface(
                    color = if (isErrorBanner) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = bannerMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorBanner) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::clearCaptureMessages,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_dismiss_message),
                                tint = if (isErrorBanner) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            when (val state = notesState) {
                is NotesListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.journal_syncing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is NotesListUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.widthIn(max = 460.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }

                is NotesListUiState.Success -> {
                    if (state.notes.isEmpty()) {
                        EmptyJournalState(
                            hasActiveFilter = searchQuery.isNotBlank() ||
                                selectedFilter != JournalViewModel.FILTER_ALL ||
                                selectedTag != null,
                            onResetFilter = {
                                viewModel.setSearchQuery("")
                                viewModel.setFilter(JournalViewModel.FILTER_ALL)
                                viewModel.setSelectedTag(null)
                            },
                            onRecordClick = handleStartRecordClick,
                            onTypeMemoClick = {
                                initialTypedMemo = ""
                                showTypeMemoSheet = true
                            },
                            onTrySamplePrompt = { sample ->
                                initialTypedMemo = sample
                                showTypeMemoSheet = true
                            }
                        )
                    } else {
                        val shareNoteAsPlainText: (VoiceNote) -> Unit = { note ->
                            val shared = NoteMarkdownExporter.shareNoteAsPlainText(context, note)
                            if (!shared) {
                                clipboardManager.setText(AnnotatedString(NoteMarkdownExporter.toPlainText(note, context)))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_note_copied))
                                }
                            }
                        }


                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isWide = maxWidth >= 680.dp
                            if (isWide) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item(
                                        key = "fact_check_dashboard_wide",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        FactCheckDashboardCarousel(
                                            notes = allNotes,
                                            reverifyingNoteIds = reverifyingNoteIds,
                                            onOpenNote = onNoteClick,
                                            onReverifyNote = viewModel::reverifyNoteFromDashboard,
                                            onOpenSourceUrl = openSafeSourceUrl,
                                            onOpenSearchHistory = { showSearchHistorySheet = true }
                                        )
                                    }
                                    items(state.notes, key = { it.id }) { note ->
                                        VoiceNoteCard(
                                            note = note,
                                            noteStructureMode = noteStructureMode,
                                            contextualSummarization = contextualSummarization,
                                            selectedTag = selectedTag,
                                            onClick = { onNoteClick(note.id) },
                                            onReviewClick = { onReviewNoteClick(note.id) },
                                            onTagClick = viewModel::toggleTagFilter,
                                            onManageTagsClick = { noteEditingTags = note },
                                            onShareClick = { shareNoteAsPlainText(note) },
                                            onPinToggle = { viewModel.togglePin(note) },
                                            onDeleteClick = { notePendingDelete = note },
                                            modifier = Modifier.animateItem(
                                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item(key = "fact_check_dashboard_compact") {
                                        FactCheckDashboardCarousel(
                                            notes = allNotes,
                                            reverifyingNoteIds = reverifyingNoteIds,
                                            onOpenNote = onNoteClick,
                                            onReverifyNote = viewModel::reverifyNoteFromDashboard,
                                            onOpenSourceUrl = openSafeSourceUrl,
                                            onOpenSearchHistory = { showSearchHistorySheet = true }
                                        )
                                    }
                                    items(state.notes, key = { it.id }) { note ->
                                        VoiceNoteCard(
                                            note = note,
                                            noteStructureMode = noteStructureMode,
                                            contextualSummarization = contextualSummarization,
                                            selectedTag = selectedTag,
                                            onClick = { onNoteClick(note.id) },
                                            onReviewClick = { onReviewNoteClick(note.id) },
                                            onTagClick = viewModel::toggleTagFilter,
                                            onManageTagsClick = { noteEditingTags = note },
                                            onShareClick = { shareNoteAsPlainText(note) },
                                            onPinToggle = { viewModel.togglePin(note) },
                                            onDeleteClick = { notePendingDelete = note },
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp)
                                                .animateItem(
                                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )
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

    if (showTypeMemoSheet) {
        TypeMemoBottomSheet(
            initialText = initialTypedMemo,
            autoFactCheck = captureState.autoFactCheck,
            onToggleAutoFactCheck = viewModel::setAutoFactCheck,
            onDismiss = { showTypeMemoSheet = false },
            onSubmit = { text ->
                showTypeMemoSheet = false
                viewModel.processTextMemo(text)
            }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            noteStructureMode = noteStructureMode,
            contextualSummarization = contextualSummarization,
            autoFactCheck = captureState.autoFactCheck,
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            quotaState = quotaState,
            onToggleEcoLiteMode = viewModel::setEcoLiteMode,
            onOpenPaywall = { viewModel.openPaywall(null) },
            onReplayOnboarding = viewModel::replayOnboarding,
            onSelectNoteStructure = { mode ->
                viewModel.setNoteStructureMode(mode)
            },
            onToggleContextualSummarization = viewModel::setContextualSummarization,
            onToggleAutoFactCheck = viewModel::setAutoFactCheck,
            onSignOutClick = onSignOutClick,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showPaywall) {
        PaywallBottomSheet(
            quotaState = quotaState,
            triggeredFeature = paywallFeature,
            onUpgradeToPro = {
                viewModel.upgradeToPro()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_pro_activated))
                }
            },
            onClaimBonusCredits = {
                val granted = viewModel.claimBonusCredits()
                coroutineScope.launch {
                    val msg = if (granted) {
                        context.getString(R.string.snackbar_bonus_credits_added)
                    } else {
                        context.getString(R.string.snackbar_grace_already_claimed)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            },
            onEnableEcoLite = {
                viewModel.setEcoLiteMode(true)
                viewModel.dismissPaywall()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_eco_lite_enabled))
                }
            },
            onSignInWithGoogle = if (quotaState.isGuest) {
                {
                    viewModel.dismissPaywall()
                    onSignOutClick()
                }
            } else null,
            onSaveOfflineFallback = if (paywallFeature == QuotaFeature.AI_MEMO) {
                {
                    viewModel.dismissPaywall()
                    showTypeMemoSheet = true
                }
            } else null,
            onDismiss = viewModel::dismissPaywall
        )
    }

    if (showOpenTasksSheet) {
        OpenTasksBottomSheet(
            notes = allNotes,
            onToggleTask = viewModel::toggleTaskCompletion,
            onAddToCalendar = { noteTitle, taskText, noteSummary ->
                val opened = TaskActionUtils.openCalendarForTask(
                    context = context,
                    noteTitle = noteTitle,
                    taskText = taskText,
                    noteSummary = noteSummary
                )
                if (!opened) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_calendar))
                    }
                }
            },
            onOpenNote = { noteId ->
                showOpenTasksSheet = false
                onNoteClick(noteId)
            },
            onDismiss = { showOpenTasksSheet = false }
        )
    }

    if (showDigestSheet) {
        val totalNotes = allNotes.size
        DigestBottomSheet(
            digestState = digestState,
            notesCount = totalNotes,
            onGenerateDigest = viewModel::generateDigest,
            onCopyContent = { text ->
                clipboardManager.setText(AnnotatedString(text))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_digest_copied))
                }
            },
            onShareContent = { text ->
                NoteMarkdownExporter.shareText(
                    context = context,
                    subject = context.getString(
                        R.string.digest_share_subject,
                        digestState.focusTitle.ifBlank { context.getString(R.string.digest_share_fallback_notes) }
                    ),
                    content = text
                )
            },
            onOpenUri = { url ->
                runCatching { uriHandler.openUri(url) }.onFailure {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser))
                    }
                }
            },
            onDismiss = {
                showDigestSheet = false
            }
        )
    }

    if (showSearchHistorySheet) {
        FactCheckSearchHistorySheet(
            notes = allNotes,
            searchHistory = searchHistory,
            onSelectTopic = { topic ->
                viewModel.setSearchQuery(topic)
                showSearchHistorySheet = false
            },
            onReverifyTopic = { topic, noteId ->
                showSearchHistorySheet = false
                val targetNote = allNotes.firstOrNull { it.id == noteId }
                    ?: allNotes.firstOrNull { note ->
                        note.claimsToVerify.any { it.equals(topic, ignoreCase = true) } ||
                            note.claimVerdicts.any { it.claim.equals(topic, ignoreCase = true) }
                    }
                if (targetNote != null) {
                    viewModel.reverifyNoteFromDashboard(targetNote)
                } else if (!noteId.isNullOrBlank()) {
                    onNoteClick(noteId)
                }
            },
            onOpenNote = { noteId ->
                showSearchHistorySheet = false
                onNoteClick(noteId)
            },
            onOpenSourceUrl = openSafeSourceUrl,
            onDeleteItem = viewModel::deleteSearchHistoryItem,
            onDismiss = { showSearchHistorySheet = false }
        )
    }

    if (showMicRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showMicRationaleDialog = false },
            title = { Text(stringResource(R.string.mic_permission_title)) },
            text = { Text(stringResource(R.string.mic_permission_body)) },
            confirmButton = {
                Button(onClick = {
                    showMicRationaleDialog = false
                    recordPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }) {
                    Text(stringResource(R.string.mic_permission_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMicRationaleDialog = false
                    initialTypedMemo = ""
                    showTypeMemoSheet = true
                }) {
                    Text(stringResource(R.string.action_type_memo))
                }
            }
        )
    }

    val editingTagNote = noteEditingTags
    if (editingTagNote != null) {
        ManageTagsDialog(
            noteTitle = editingTagNote.title,
            initialTags = editingTagNote.tags,
            suggestedTags = availableTags.map { it.tag },
            onDismiss = { noteEditingTags = null },
            onSaveTags = { updatedTags ->
                viewModel.updateNoteTags(editingTagNote.id, updatedTags)
                noteEditingTags = null
            }
        )
    }

    val renamingDraft = draftPendingRename
    if (renamingDraft != null) {
        RenameDraftDialog(
            initialLabel = renamingDraft.label.orEmpty(),
            onDismiss = { draftPendingRename = null },
            onSave = { updatedLabel ->
                viewModel.renameSavedDraft(renamingDraft.id, updatedLabel)
                draftPendingRename = null
            }
        )
    }

    val pendingDelete = notePendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { notePendingDelete = null },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_journal_body, pendingDelete.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(pendingDelete.id)
                        notePendingDelete = null
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
                TextButton(onClick = { notePendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun StudioHeaderHolder(
    userDisplayName: String?,
    userEmail: String?,
    noteStructureMode: NoteStructureMode,
    contextualSummarization: Boolean,
    searchQuery: String,
    selectedFilter: String,
    selectedTag: String?,
    availableTags: List<TagCount>,
    allNotes: List<VoiceNote>,
    onSearchChange: (String) -> Unit,
    onFilterSelected: (String) -> Unit,
    onTagSelected: (String) -> Unit,
    onClearTag: () -> Unit,
    onOpenTasksHub: () -> Unit,
    onOpenDigest: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val filters = remember {
        listOf(
            JournalViewModel.FILTER_ALL,
            JournalViewModel.FILTER_VERIFIED,
            NoteCategory.JOURNAL.label,
            NoteCategory.LECTURE.label,
            NoteCategory.MEETING.label,
            NoteCategory.RESEARCH.label,
            NoteCategory.COMMUTE.label
        )
    }

    val totalCount by animateIntAsState(targetValue = allNotes.size, label = "total_notes_anim")
    val verifiedCount by animateIntAsState(
        targetValue = allNotes.count { it.factCheckStatus == FactCheckStatus.VERIFIED.value || it.sources.isNotEmpty() },
        label = "verified_notes_anim"
    )
    val openTasksCount by animateIntAsState(
        targetValue = allNotes.sumOf { note ->
            val validDone = note.completedActionIndices.count { idx -> idx >= 0L && idx < note.actionItems.size }
            (note.actionItems.size - validDone).coerceAtLeast(0)
        },
        label = "open_tasks_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val baseSubtitle = userDisplayName?.takeIf { it.isNotBlank() }
                        ?: userEmail
                        ?: stringResource(R.string.journal_greeting)
                    val toneSuffix = if (contextualSummarization) {
                        stringResource(R.string.journal_contextual_tone_suffix)
                    } else {
                        ""
                    }
                    val structureLabel = stringResource(noteStructureMode.displayLabelRes())
                    Text(
                        text = "$baseSubtitle • $structureLabel$toneSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allNotes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenDigest,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("digest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.action_digest),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onSignOutClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = stringResource(R.string.action_sign_out),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(visible = allNotes.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                StudioStatPill(
                    label = stringResource(R.string.stat_memos),
                    value = totalCount.toString(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { onFilterSelected(JournalViewModel.FILTER_ALL) },
                    modifier = Modifier.weight(1f)
                )
                StudioStatPill(
                    label = stringResource(R.string.stat_verified),
                    value = verifiedCount.toString(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onFilterSelected(JournalViewModel.FILTER_VERIFIED) },
                    modifier = Modifier.weight(1f)
                )
                StudioStatPill(
                    label = stringResource(R.string.stat_open_tasks),
                    value = openTasksCount.toString(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenTasksHub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_tasks_hub_button")
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.journal_search_hint),
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
                    visible = searchQuery.isNotEmpty(),
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("journal_search_input")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            filters.forEach { filter ->
                val selected = selectedFilter == filter
                val filterLabel = when (filter) {
                    JournalViewModel.FILTER_ALL -> stringResource(R.string.filter_all)
                    JournalViewModel.FILTER_VERIFIED -> stringResource(R.string.filter_fact_checked)
                    else -> localizedCategoryLabel(filter)
                }
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = filterLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = if (filter == JournalViewModel.FILTER_VERIFIED) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.TravelExplore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_${filter.lowercase()}")
                )
            }
        }

        AnimatedVisibility(visible = availableTags.isNotEmpty() || selectedTag != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (selectedTag != null) {
                    FilterChip(
                        selected = true,
                        onClick = onClearTag,
                        label = {
                            Text(
                                text = stringResource(R.string.filter_all_tags),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_tag_filter),
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("tag_filter_clear")
                    )
                }
                availableTags.forEach { tagCount ->
                    val isSelected = selectedTag == tagCount.tag
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTagSelected(tagCount.tag) },
                        label = {
                            Text(
                                text = "#${tagCount.tag} (${tagCount.count})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("tag_filter_${tagCount.tag}")
                    )
                }
            }
        }
    }
}


@Composable
private fun StudioStatPill(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceNoteCard(
    note: VoiceNote,
    noteStructureMode: NoteStructureMode,
    contextualSummarization: Boolean,
    selectedTag: String?,
    onClick: () -> Unit,
    onReviewClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onManageTagsClick: () -> Unit,
    onShareClick: () -> Unit,
    onPinToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalTasks = note.actionItems.size
    val completedCount = note.completedActionIndices.count { idx -> idx >= 0L && idx < totalTasks }
    val isVerified = note.factCheckStatus == FactCheckStatus.VERIFIED.value || note.sources.isNotEmpty()
    val needsReview = !isVerified &&
        (note.factCheckStatus == FactCheckStatus.NEEDS_REVIEW.value || note.claimsToVerify.isNotEmpty())

    val borderColor by animateColorAsState(
        targetValue = if (note.pinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(220),
        label = "card_border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (note.pinned) 1.5.dp else 1.dp,
        animationSpec = tween(220),
        label = "card_border_width"
    )
    val pinRotation by animateFloatAsState(
        targetValue = if (note.pinned) 0f else -35f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pin_rotation"
    )

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(width = borderWidth, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.pinned) 3.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}")
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = localizedCategoryLabel(note.category).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (contextualSummarization) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(note.topicTone.displayLabelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (note.durationSeconds > 0L) {
                        Text(
                            text = formatDuration(note.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = localizedTimestamp(note.updatedAt ?: note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onManageTagsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tag_note_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tag,
                            contentDescription = stringResource(R.string.action_manage_tags),
                            tint = if (note.tags.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_note_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.action_share_note),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onPinToggle,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (note.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (note.pinned) R.string.cd_unpin_note else R.string.cd_pin_note),
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(19.dp)
                                .rotate(pinRotation)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (noteStructureMode == NoteStructureMode.BRIEF) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (note.summary.isNotBlank()) {
                Text(
                    text = note.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (noteStructureMode == NoteStructureMode.BRIEF) 1 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReviewClick)
                        .testTag("review_note_${note.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.review_entry_nav_chip),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (totalTasks > 0) {
                    val allDone = completedCount == totalTasks
                    Surface(
                        color = if (allDone) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TaskAlt,
                                contentDescription = null,
                                tint = if (allDone) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.note_tasks_ratio, completedCount, totalTasks),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isVerified) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (note.sources.isNotEmpty()) {
                                    stringResource(R.string.note_verified_sources, note.sources.size)
                                } else {
                                    stringResource(R.string.filter_fact_checked)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else if (needsReview) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (note.claimsToVerify.isNotEmpty()) {
                                    stringResource(R.string.note_claims_to_check, note.claimsToVerify.size)
                                } else {
                                    stringResource(R.string.note_needs_fact_check)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (note.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    note.tags.take(4).forEach { tag ->
                        NoteTagPill(
                            tag = tag,
                            selected = selectedTag == tag,
                            onClick = { onTagClick(tag) },
                            modifier = Modifier.testTag("note_card_tag_$tag")
                        )
                    }
                    if (note.tags.size > 4) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onManageTagsClick)
                        ) {
                            Text(
                                text = "+${note.tags.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
private fun EmptyJournalState(
    hasActiveFilter: Boolean,
    onResetFilter: () -> Unit,
    onRecordClick: () -> Unit,
    onTypeMemoClick: () -> Unit,
    onTrySamplePrompt: (String) -> Unit
) {
    val evPromptText = stringResource(R.string.sample_prompt_ev_text)
    val bioPromptText = stringResource(R.string.sample_prompt_bio_text)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            if (hasActiveFilter) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = stringResource(R.string.journal_empty_filtered_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.journal_empty_filtered_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onResetFilter) {
                    Text(stringResource(R.string.action_clear_filters))
                }
            } else {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_journal),
                        contentDescription = stringResource(R.string.cd_empty_journal),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(156.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.journal_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.journal_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onRecordClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_record))
                    }
                    OutlinedButton(
                        onClick = onTypeMemoClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_type_memo))
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { onTrySamplePrompt(evPromptText) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.sample_prompt_ev_chip), style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = { onTrySamplePrompt(bioPromptText) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.sample_prompt_bio_chip), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TypeMemoBottomSheet(
    initialText: String,
    autoFactCheck: Boolean,
    onToggleAutoFactCheck: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memoText by remember(initialText) { mutableStateOf(initialText) }

    val quickTemplates = listOf(
        stringResource(R.string.template_ev_label) to stringResource(R.string.template_ev_prompt),
        stringResource(R.string.template_meeting_label) to stringResource(R.string.template_meeting_prompt)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.type_memo_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.type_memo_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTemplates.forEach { (label, prompt) ->
                    AssistChip(
                        onClick = { memoText = prompt },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                minLines = 4,
                maxLines = 8,
                placeholder = {
                    Text(stringResource(R.string.type_memo_placeholder))
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("type_memo_input")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auto_fact_check_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoFactCheck,
                    onCheckedChange = onToggleAutoFactCheck
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onSubmit(memoText) },
                    enabled = memoText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("submit_memo_button")
                ) {
                    Text(stringResource(R.string.type_memo_submit))
                }
            }
        }
    }
}

private val TIMESTAMP_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    }
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatTimestamp(timestamp: Timestamp?, fallbackJustNow: String = "Just now"): String {
    val date = timestamp?.toDate() ?: return fallbackJustNow
    return TIMESTAMP_FORMATTER.get()?.format(date) ?: fallbackJustNow
}
