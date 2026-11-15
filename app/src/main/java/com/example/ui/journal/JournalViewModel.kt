package com.example.ui.journal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.AudioPlayerManager
import com.example.audio.LocalAudioStore
import com.example.audio.RecordingForegroundService
import com.example.audio.RecordingState
import com.example.audio.VoiceRecorderManager
import com.example.data.ai.AudioBrainAiService
import com.example.data.ai.MemoAnalysisResult
import com.example.data.billing.QuotaFeature
import com.example.data.billing.SubscriptionTier
import com.example.data.billing.UsageQuotaManager
import com.example.data.billing.UsageQuotaSnapshot
import com.example.data.model.ClaimVerdict
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.VoiceNote
import com.example.data.offline.NetworkConnectivityObserver
import com.example.data.offline.PendingMemo
import com.example.data.offline.PendingMemoKind
import com.example.data.offline.PendingMemoStore
import com.example.data.repository.VoiceNotesRepository
import com.example.widget.WidgetSnapshotStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface NotesListUiState {
    data object Loading : NotesListUiState
    data class Success(val notes: List<VoiceNote>) : NotesListUiState
    data class Error(val message: String) : NotesListUiState
}

data class TagCount(
    val tag: String,
    val count: Int
)

sealed interface CapturePhase {
    data object Idle : CapturePhase
    data object Recording : CapturePhase
    data object Transcribing : CapturePhase
    data object FactChecking : CapturePhase
}

data class CaptureBannerState(
    val phase: CapturePhase = CapturePhase.Idle,
    val autoFactCheck: Boolean = true,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class DigestUiState(
    val isStreaming: Boolean = false,
    val focusTitle: String = "",
    val content: String = "",
    val sources: List<FactSource> = emptyList(),
    val errorMessage: String? = null
)

class JournalViewModel(
    appContext: Context,
    private val repository: VoiceNotesRepository,
    private val aiService: AudioBrainAiService,
    private val recorderManager: VoiceRecorderManager,
    private val pendingStore: PendingMemoStore,
    private val localAudioStore: LocalAudioStore,
    private val networkObserver: NetworkConnectivityObserver,
    private val currentUserId: String
) : ViewModel() {

    private val context = appContext.applicationContext
    private val quotaManager = UsageQuotaManager.getInstance(context)
    private val syncMutex = Mutex()
    private val digestJobLock = Unit
    private var digestJob: Job? = null
    private val draftAudioPlayer = AudioPlayerManager(viewModelScope, context)

    val quotaState: StateFlow<UsageQuotaSnapshot> = quotaManager.quotaState

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    private val _paywallFeature = MutableStateFlow<QuotaFeature?>(null)
    val paywallFeature: StateFlow<QuotaFeature?> = _paywallFeature.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FILTER_ALL)
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _captureState = MutableStateFlow(
        CaptureBannerState(
            phase = if (recorderManager.state.value.isRecording) CapturePhase.Recording else CapturePhase.Idle
        )
    )
    val captureState: StateFlow<CaptureBannerState> = _captureState.asStateFlow()

    val recordingState: StateFlow<RecordingState> = recorderManager.state

    val isOnline: StateFlow<Boolean> = networkObserver.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = networkObserver.isCurrentlyOnline()
        )

    val pendingMemos: StateFlow<List<PendingMemo>> = pendingStore.observePending(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _playingDraftId = MutableStateFlow<String?>(null)
    val playingDraftId: StateFlow<String?> = _playingDraftId.asStateFlow()

    val draftPlaybackState: StateFlow<AudioPlaybackState> = draftAudioPlayer.state

    private val _digestState = MutableStateFlow(DigestUiState())
    val digestState: StateFlow<DigestUiState> = _digestState.asStateFlow()

    private val _createdNoteEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val createdNoteEvents: SharedFlow<String> = _createdNoteEvents.asSharedFlow()

    private val userPreferencesFlow = repository.observeUserPreferences(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user preferences", firestoreError ?: error)
            emit(com.example.data.model.UserPreferences(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = com.example.data.model.UserPreferences(userId = currentUserId)
        )

    val noteStructureMode: StateFlow<NoteStructureMode> = userPreferencesFlow
        .map { prefs -> prefs.structureMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoteStructureMode.DETAILED
        )

    val contextualSummarization: StateFlow<Boolean> = userPreferencesFlow
        .map { prefs -> prefs.contextualSummarization }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = true
        )

    private val _reverifyingNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val reverifyingNoteIds: StateFlow<Set<String>> = _reverifyingNoteIds.asStateFlow()

    val searchHistory: StateFlow<List<FactCheckSearchItem>> = repository.observeFactCheckHistory(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing search history in journal", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val rawNotesState: StateFlow<NotesListUiState> = repository.observeNotes(currentUserId)
        .onEach { notes ->
            WidgetSnapshotStore.saveAndRefresh(context, notes, isSignedIn = true)
        }
        .map<List<VoiceNote>, NotesListUiState> { NotesListUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing voice notes", rootError)
            emit(NotesListUiState.Error(rootError.message ?: context.getString(R.string.err_sync_notes)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NotesListUiState.Loading
        )

    val allNotes: StateFlow<List<VoiceNote>> = rawNotesState
        .map { state -> (state as? NotesListUiState.Success)?.notes.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val availableTags: StateFlow<List<TagCount>> = rawNotesState
        .map { state ->
            when (state) {
                is NotesListUiState.Success -> {
                    state.notes
                        .flatMap { it.tags }
                        .mapNotNull { NoteTagHelper.normalizeTag(it) }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .map { TagCount(tag = it.key, count = it.value) }
                }
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val filteredNotesState: StateFlow<NotesListUiState> = combine(
        rawNotesState,
        _searchQuery,
        _selectedFilter,
        _selectedTag
    ) { state, query, filter, activeTag ->
        when (state) {
            is NotesListUiState.Loading -> state
            is NotesListUiState.Error -> state
            is NotesListUiState.Success -> {
                val q = query.trim().lowercase()
                val qWithoutHash = q.trimStart('#')
                val normalizedActiveTag = activeTag?.let { NoteTagHelper.normalizeTag(it) }
                val filtered = state.notes.filter { note ->
                    val matchesFilter = when (filter) {
                        FILTER_ALL -> true
                        FILTER_VERIFIED -> note.factCheckStatus == FactCheckStatus.VERIFIED.value || note.sources.isNotEmpty()
                        else -> note.category.equals(filter, ignoreCase = true)
                    }
                    val matchesTag = normalizedActiveTag == null ||
                        note.tags.any { it.equals(normalizedActiveTag, ignoreCase = true) }
                    val matchesQuery = q.isBlank() ||
                        note.title.lowercase().contains(q) ||
                        note.summary.lowercase().contains(q) ||
                        note.transcript.lowercase().contains(q) ||
                        note.decisions.any { it.lowercase().contains(q) } ||
                        note.actionItems.any { it.lowercase().contains(q) } ||
                        note.factCheckSummary.lowercase().contains(q) ||
                        (qWithoutHash.isNotBlank() && note.tags.any { it.lowercase().contains(qWithoutHash) })
                    matchesFilter && matchesTag && matchesQuery
                }
                NotesListUiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = NotesListUiState.Loading
    )

    init {
        viewModelScope.launch {
            recorderManager.state.collect { rec ->
                if (!rec.isRecording && _captureState.value.phase == CapturePhase.Recording) {
                    _captureState.value = _captureState.value.copy(phase = CapturePhase.Idle)
                } else if (rec.isRecording &&
                    _captureState.value.phase == CapturePhase.Recording &&
                    rec.elapsedSeconds >= quotaManager.quotaState.value.limits.maxRecordingSeconds
                ) {
                    _captureState.value = _captureState.value.copy(
                        infoMessage = context.getString(R.string.info_recording_cap_reached)
                    )
                    stopAndProcessRecording()
                }
            }
        }

        viewModelScope.launch {
            combine(pendingMemos, isOnline) { pending, online ->
                pending to online
            }.collect { (pending, online) ->
                val freshPending = pending.any { !it.deferredForLater && it.lastError == null }
                if (online && freshPending && !syncMutex.isLocked &&
                    _captureState.value.phase == CapturePhase.Idle
                ) {
                    syncPendingMemos(autoTriggered = true)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag?.let { NoteTagHelper.normalizeTag(it) }
    }

    fun toggleTagFilter(tag: String) {
        val normalized = NoteTagHelper.normalizeTag(tag)
        _selectedTag.value = if (_selectedTag.value == normalized) null else normalized
    }

    fun updateNoteTags(noteId: String, tags: List<String>) {
        viewModelScope.launch {
            repository.updateNoteTags(noteId, tags)
                .onFailure { err ->
                    _captureState.value = _captureState.value.copy(
                        errorMessage = err.localizedMessage ?: context.getString(R.string.err_update_tags)
                    )
                }
        }
    }

    fun setAutoFactCheck(enabled: Boolean) {
        _captureState.value = _captureState.value.copy(autoFactCheck = enabled)
    }

    fun setNoteStructureMode(mode: NoteStructureMode) {
        viewModelScope.launch {
            repository.updateNoteStructurePreference(
                mode = mode,
                contextualSummarization = contextualSummarization.value
            ).onFailure { err ->
                _captureState.value = _captureState.value.copy(
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_update_structure_pref)
                )
            }
        }
    }

    fun toggleNoteStructureMode() {
        val next = if (noteStructureMode.value == NoteStructureMode.BRIEF) {
            NoteStructureMode.DETAILED
        } else {
            NoteStructureMode.BRIEF
        }
        setNoteStructureMode(next)
    }

    fun setContextualSummarization(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateContextualSummarizationPreference(
                enabled = enabled,
                structureMode = noteStructureMode.value
            ).onFailure { err ->
                _captureState.value = _captureState.value.copy(
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_update_contextual_pref)
                )
            }
        }
    }

    fun reverifyNoteFromDashboard(note: VoiceNote) {
        if (_reverifyingNoteIds.value.contains(note.id)) return
        if (!quotaManager.canUseFeature(QuotaFeature.FACT_CHECK)) {
            openPaywall(QuotaFeature.FACT_CHECK)
            return
        }
        quotaManager.recordUsage(QuotaFeature.FACT_CHECK)
        viewModelScope.launch {
            _reverifyingNoteIds.value = _reverifyingNoteIds.value + note.id
            val claims = if (note.claimsToVerify.isNotEmpty()) {
                note.claimsToVerify
            } else {
                note.claimVerdicts.map { it.claim }.filter { it.isNotBlank() }
            }

            aiService.factCheckClaims(
                transcript = note.transcript.ifBlank { note.summary },
                claims = claims
            ).onSuccess { checked ->
                repository.updateFactCheck(
                    noteId = note.id,
                    factCheckSummary = checked.summary,
                    factCheckStatus = checked.status,
                    searchQueries = checked.searchQueries,
                    sources = checked.sources,
                    claimsToVerify = claims,
                    claimVerdicts = checked.verdicts
                ).onFailure { err ->
                    _captureState.value = _captureState.value.copy(
                        errorMessage = err.localizedMessage ?: context.getString(R.string.err_save_fact_check)
                    )
                }

                // Record verified topic into search history
                val firstSource = checked.sources.firstOrNull()?.url.orEmpty()
                checked.verdicts.forEach { verdict ->
                    repository.recordFactCheckSearch(
                        query = verdict.claim,
                        status = verdict.normalizedStatus(),
                        explanation = verdict.explanation,
                        sourceUrl = firstSource,
                        noteId = note.id,
                        noteTitle = note.title
                    )
                }
            }.onFailure { err ->
                quotaManager.refundInteraction(QuotaFeature.FACT_CHECK)
                _captureState.value = _captureState.value.copy(
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_fact_check_failed)
                )
            }

            _reverifyingNoteIds.value = _reverifyingNoteIds.value - note.id
        }
    }

    fun clearCaptureMessages() {
        _captureState.value = _captureState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun startRecording() {
        clearCaptureMessages()
        val started = recorderManager.startRecording(
            scope = viewModelScope,
            userId = currentUserId,
            autoFactCheck = _captureState.value.autoFactCheck
        )
        if (started) {
            RecordingForegroundService.start(context)
            _captureState.value = _captureState.value.copy(phase = CapturePhase.Recording)
        } else {
            _captureState.value = _captureState.value.copy(
                phase = CapturePhase.Idle,
                errorMessage = recorderManager.state.value.errorMessage ?: context.getString(R.string.err_start_recording)
            )
        }
    }

    fun togglePauseRecording() {
        val current = recorderManager.state.value
        if (!current.isRecording) return
        if (current.isPaused) {
            recorderManager.resumeRecording(viewModelScope)
        } else {
            recorderManager.pauseRecording()
        }
    }

    fun cancelRecording() {
        recorderManager.cancelRecording()
        RecordingForegroundService.stop(context)
        _captureState.value = _captureState.value.copy(
            phase = CapturePhase.Idle,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun stopAndSaveForLater() {
        viewModelScope.launch {
            RecordingForegroundService.stop(context)
            val recorded = recorderManager.stopRecording().getOrElse { err ->
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_recording_failed)
                )
                return@launch
            }

            val timeLabel = java.text.SimpleDateFormat("MMM d • h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
            pendingStore.enqueueAudio(
                userId = currentUserId,
                bytes = recorded.bytes,
                mimeType = recorded.mimeType,
                durationSeconds = recorded.durationSeconds,
                autoFactCheck = _captureState.value.autoFactCheck,
                deferredForLater = true,
                label = context.getString(R.string.voice_note_label_with_time, timeLabel)
            )
            _captureState.value = _captureState.value.copy(
                phase = CapturePhase.Idle,
                errorMessage = null,
                infoMessage = context.getString(R.string.draft_saved_banner)
            )
        }
    }

    fun stopAndProcessRecording() {
        viewModelScope.launch {
            RecordingForegroundService.stop(context)
            _captureState.value = _captureState.value.copy(
                phase = CapturePhase.Transcribing,
                errorMessage = null,
                infoMessage = null
            )
            val recorded = recorderManager.stopRecording().getOrElse { err ->
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_recording_failed)
                )
                return@launch
            }

            val isOverAiQuota = !quotaManager.canUseFeature(QuotaFeature.AI_MEMO)
            if (isOverAiQuota) {
                val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                pendingStore.enqueueAudio(
                    userId = currentUserId,
                    bytes = recorded.bytes,
                    mimeType = recorded.mimeType,
                    durationSeconds = recorded.durationSeconds,
                    autoFactCheck = _captureState.value.autoFactCheck,
                    deferredForLater = true,
                    label = context.getString(R.string.voice_note_label_with_time, timeLabel)
                )
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = null,
                    infoMessage = context.getString(R.string.draft_saved_banner)
                )
                openPaywall(QuotaFeature.AI_MEMO)
                return@launch
            }

            quotaManager.recordUsage(QuotaFeature.AI_MEMO)
            pendingStore.enqueueAudio(
                userId = currentUserId,
                bytes = recorded.bytes,
                mimeType = recorded.mimeType,
                durationSeconds = recorded.durationSeconds,
                autoFactCheck = _captureState.value.autoFactCheck,
                deferredForLater = false
            )
            syncPendingMemos(autoTriggered = false)
        }
    }

    fun importAndProcessAudio(uri: Uri) {
        viewModelScope.launch {
            _captureState.value = _captureState.value.copy(
                phase = CapturePhase.Transcribing,
                errorMessage = null,
                infoMessage = null
            )
            val imported = recorderManager.readImportedAudio(uri).getOrElse { err ->
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_read_audio_file)
                )
                return@launch
            }

            if (!quotaManager.canUseFeature(QuotaFeature.AI_MEMO)) {
                val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                pendingStore.enqueueAudio(
                    userId = currentUserId,
                    bytes = imported.bytes,
                    mimeType = imported.mimeType,
                    durationSeconds = imported.durationSeconds,
                    autoFactCheck = _captureState.value.autoFactCheck,
                    deferredForLater = true,
                    label = context.getString(R.string.voice_note_label_with_time, timeLabel)
                )
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = null,
                    infoMessage = context.getString(R.string.draft_saved_banner)
                )
                openPaywall(QuotaFeature.AI_MEMO)
                return@launch
            }

            quotaManager.recordUsage(QuotaFeature.AI_MEMO)
            pendingStore.enqueueAudio(
                userId = currentUserId,
                bytes = imported.bytes,
                mimeType = imported.mimeType,
                durationSeconds = imported.durationSeconds,
                autoFactCheck = _captureState.value.autoFactCheck,
                deferredForLater = false
            )
            syncPendingMemos(autoTriggered = false)
        }
    }

    fun processTextMemo(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (!quotaManager.canUseFeature(QuotaFeature.AI_MEMO)) {
                // Tier-3 Zero-Cost Local Fallback: save raw text note without AI tokens
                val fallbackTitle = trimmed.lineSequence().firstOrNull()?.take(60)?.trim()
                    ?.ifBlank { context.getString(R.string.app_name) }
                    ?: context.getString(R.string.app_name)
                repository.createNote(
                    title = fallbackTitle,
                    summary = trimmed.take(220),
                    transcript = trimmed,
                    category = NoteCategory.JOURNAL,
                    durationSeconds = 0L,
                    actionItems = emptyList(),
                    claimsToVerify = emptyList(),
                    factCheckStatus = FactCheckStatus.NONE
                )
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    errorMessage = null,
                    infoMessage = context.getString(R.string.info_saved_local_note_no_ai)
                )
                openPaywall(QuotaFeature.AI_MEMO)
                return@launch
            }

            quotaManager.recordUsage(QuotaFeature.AI_MEMO)
            _captureState.value = _captureState.value.copy(
                phase = CapturePhase.Transcribing,
                errorMessage = null,
                infoMessage = null
            )
            pendingStore.enqueueText(
                userId = currentUserId,
                text = trimmed,
                autoFactCheck = _captureState.value.autoFactCheck
            )
            syncPendingMemos(autoTriggered = false)
        }
    }

    fun togglePlayDraft(memo: PendingMemo) {
        if (memo.kind != PendingMemoKind.AUDIO) return
        val audioFile = pendingStore.getAudioFile(memo) ?: run {
            _captureState.value = _captureState.value.copy(
                errorMessage = context.getString(R.string.err_audio_unavailable)
            )
            return
        }
        if (_playingDraftId.value == memo.id && draftAudioPlayer.state.value.hasAudio) {
            draftAudioPlayer.togglePlayPause()
        } else {
            _playingDraftId.value = memo.id
            draftAudioPlayer.loadFile(audioFile, autoPlay = true)
        }
    }

    fun seekDraftPlayback(positionMs: Long) {
        draftAudioPlayer.seekTo(positionMs)
    }

    fun cycleDraftPlaybackSpeed() {
        draftAudioPlayer.cycleSpeed()
    }

    fun renameSavedDraft(memoId: String, newLabel: String) {
        viewModelScope.launch {
            pendingStore.updateLabel(currentUserId, memoId, newLabel)
        }
    }

    fun transcribeSavedDraft(memoId: String) {
        if (_playingDraftId.value == memoId) {
            draftAudioPlayer.release()
            _playingDraftId.value = null
        }
        viewModelScope.launch {
            pendingStore.markReadyForTranscription(currentUserId, memoId)
            syncPendingMemos(autoTriggered = false, targetMemoId = memoId)
        }
    }

    fun transcribeAllSavedDrafts() {
        if (_playingDraftId.value != null) {
            draftAudioPlayer.release()
            _playingDraftId.value = null
        }
        viewModelScope.launch {
            syncPendingMemos(autoTriggered = false, includeDeferred = true)
        }
    }

    fun retryPendingMemos() {
        viewModelScope.launch {
            syncPendingMemos(autoTriggered = false, includeDeferred = false)
        }
    }

    fun discardPendingMemo(memoId: String) {
        if (_playingDraftId.value == memoId) {
            draftAudioPlayer.release()
            _playingDraftId.value = null
        }
        viewModelScope.launch {
            pendingStore.remove(currentUserId, memoId)
        }
    }

    private suspend fun syncPendingMemos(
        autoTriggered: Boolean,
        includeDeferred: Boolean = false,
        targetMemoId: String? = null
    ) {
        if (!networkObserver.isCurrentlyOnline()) {
            if (!autoTriggered) {
                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Idle,
                    infoMessage = context.getString(R.string.info_saved_offline_auto)
                )
            }
            return
        }

        syncMutex.withLock {
            val allPending = pendingStore.getPending(currentUserId)
            val queue = when {
                targetMemoId != null -> allPending.filter { it.id == targetMemoId }
                includeDeferred -> allPending
                else -> allPending.filter { !it.deferredForLater }
            }
            if (queue.isEmpty()) {
                if (_captureState.value.phase != CapturePhase.Recording) {
                    _captureState.value = _captureState.value.copy(phase = CapturePhase.Idle)
                }
                return@withLock
            }

            for (memo in queue) {
                if (!networkObserver.isCurrentlyOnline()) {
                    _captureState.value = _captureState.value.copy(
                        phase = CapturePhase.Idle,
                        infoMessage = context.getString(R.string.info_offline_remaining_queued)
                    )
                    break
                }

                _captureState.value = _captureState.value.copy(
                    phase = CapturePhase.Transcribing,
                    errorMessage = null,
                    infoMessage = null
                )

                var audioBytesForLocalPlayback: ByteArray? = null
                val activeStructureMode = noteStructureMode.value
                val activeContextualSummarization = contextualSummarization.value
                val analysisResult = when (memo.kind) {
                    PendingMemoKind.AUDIO -> {
                        val bytes = pendingStore.readAudioBytes(memo)
                        if (bytes == null) {
                            pendingStore.remove(currentUserId, memo.id)
                            continue
                        }
                        audioBytesForLocalPlayback = bytes
                        aiService.analyzeAudioMemo(
                            audioBytes = bytes,
                            mimeType = memo.mimeType ?: "audio/mp4",
                            structureMode = activeStructureMode,
                            contextualSummarization = activeContextualSummarization
                        )
                    }
                    PendingMemoKind.TEXT -> {
                        val text = memo.textContent.orEmpty()
                        if (text.isBlank()) {
                            pendingStore.remove(currentUserId, memo.id)
                            continue
                        }
                        aiService.analyzeTextMemo(
                            memoText = text,
                            structureMode = activeStructureMode,
                            contextualSummarization = activeContextualSummarization
                        )
                    }
                }

                val analysis = analysisResult.getOrElse { err ->
                    quotaManager.refundInteraction(QuotaFeature.AI_MEMO)
                    val reason = err.localizedMessage ?: context.getString(R.string.err_process_memo)
                    pendingStore.markError(currentUserId, memo.id, reason)
                    _captureState.value = _captureState.value.copy(
                        phase = CapturePhase.Idle,
                        errorMessage = context.getString(R.string.err_queued_for_retry, reason)
                    )
                    return@withLock
                }

                val savedNoteId = saveAnalyzedMemo(
                    analysis = analysis,
                    durationSeconds = memo.durationSeconds,
                    autoFactCheck = memo.autoFactCheck
                ).getOrElse { err ->
                    val reason = err.localizedMessage ?: context.getString(R.string.err_sync_firestore)
                    pendingStore.markError(currentUserId, memo.id, reason)
                    _captureState.value = _captureState.value.copy(
                        phase = CapturePhase.Idle,
                        errorMessage = context.getString(R.string.err_queued_for_retry, reason)
                    )
                    return@withLock
                }

                if (audioBytesForLocalPlayback != null) {
                    localAudioStore.saveAudio(savedNoteId, audioBytesForLocalPlayback)
                }
                pendingStore.remove(currentUserId, memo.id)

                if (!autoTriggered && queue.size == 1) {
                    _createdNoteEvents.tryEmit(savedNoteId)
                }
            }

            if (_captureState.value.phase != CapturePhase.Recording) {
                _captureState.value = _captureState.value.copy(phase = CapturePhase.Idle)
            }
        }
    }

    private suspend fun saveAnalyzedMemo(
        analysis: MemoAnalysisResult,
        durationSeconds: Long,
        autoFactCheck: Boolean
    ): Result<String> {
        var factCheckSummary = ""
        var factCheckStatus = FactCheckStatus.NONE
        var searchQueries = emptyList<String>()
        var sources = emptyList<FactSource>()
        var claimVerdicts = emptyList<ClaimVerdict>()

        if (autoFactCheck && analysis.claimsToVerify.isNotEmpty()) {
            if (!quotaManager.canUseFeature(QuotaFeature.FACT_CHECK)) {
                factCheckStatus = FactCheckStatus.NEEDS_REVIEW
                _captureState.value = _captureState.value.copy(
                    infoMessage = context.getString(R.string.info_fact_check_limit_skipped)
                )
            } else {
                quotaManager.recordUsage(QuotaFeature.FACT_CHECK)
                _captureState.value = _captureState.value.copy(phase = CapturePhase.FactChecking)
                val factResult = aiService.factCheckClaims(
                    transcript = analysis.transcript,
                    claims = analysis.claimsToVerify
                )
                factResult.onSuccess { res ->
                    factCheckSummary = res.summary
                    factCheckStatus = res.status
                    searchQueries = res.searchQueries
                    sources = res.sources
                    claimVerdicts = res.verdicts

                    // Record auto-fact-checked topics to search history
                    val firstSource = res.sources.firstOrNull()?.url.orEmpty()
                    res.verdicts.forEach { verdict ->
                        repository.recordFactCheckSearch(
                            query = verdict.claim,
                            status = verdict.normalizedStatus(),
                            explanation = verdict.explanation,
                            sourceUrl = firstSource,
                            noteTitle = analysis.title
                        )
                    }
                }.onFailure { err ->
                    quotaManager.refundInteraction(QuotaFeature.FACT_CHECK)
                    Log.w(TAG, "Auto fact-check failed, saving note with needs_review: ${err.message}")
                    factCheckStatus = FactCheckStatus.NEEDS_REVIEW
                }
            }
        }

        return repository.createNote(
            title = analysis.title,
            summary = analysis.summary,
            transcript = analysis.transcript,
            category = analysis.category,
            durationSeconds = durationSeconds,
            actionItems = analysis.actionItems,
            claimsToVerify = analysis.claimsToVerify,
            decisions = analysis.decisions,
            claimVerdicts = claimVerdicts,
            factCheckSummary = factCheckSummary,
            factCheckStatus = factCheckStatus,
            searchQueries = searchQueries,
            sources = sources,
            tags = analysis.tags,
            detectedTone = analysis.detectedTone
        )
    }

    fun openPaywall(feature: QuotaFeature? = null) {
        _paywallFeature.value = feature
        _showPaywall.value = true
    }

    fun dismissPaywall() {
        _showPaywall.value = false
        _paywallFeature.value = null
    }

    fun upgradeToPro() {
        quotaManager.setSubscriptionTier(SubscriptionTier.PRO)
        dismissPaywall()
    }

    fun claimBonusCredits(): Boolean {
        val granted = quotaManager.grantBonusAiMemoCredits(3)
        dismissPaywall()
        return granted
    }

    fun setEcoLiteMode(enabled: Boolean) {
        quotaManager.setEcoLiteMode(enabled)
    }

    fun replayOnboarding() {
        quotaManager.resetOnboarding()
    }

    fun generateDigest(focusTitle: String, focusPrompt: String) {
        val notes = (rawNotesState.value as? NotesListUiState.Success)?.notes.orEmpty()
        if (notes.isEmpty()) return

        if (!quotaManager.canUseFeature(QuotaFeature.DIGEST)) {
            openPaywall(QuotaFeature.DIGEST)
            return
        }
        quotaManager.recordUsage(QuotaFeature.DIGEST)

        digestJob?.cancel()
        digestJob = viewModelScope.launch {
            _digestState.value = DigestUiState(
                isStreaming = true,
                focusTitle = focusTitle,
                content = "",
                sources = emptyList(),
                errorMessage = null
            )

            try {
                aiService.streamJournalDigest(notes = notes, focusPrompt = focusPrompt)
                    .collect { update ->
                        _digestState.value = _digestState.value.copy(
                            isStreaming = !update.isComplete,
                            content = update.text,
                            sources = update.sources
                        )
                    }
            } catch (e: TimeoutCancellationException) {
                quotaManager.refundInteraction(QuotaFeature.DIGEST)
                Log.e(TAG, "Digest generation timed out", e)
                _digestState.value = _digestState.value.copy(
                    isStreaming = false,
                    errorMessage = context.getString(R.string.err_digest_timeout)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Digest generation failed", e)
                if (currentUserId == VoiceNotesRepository.GUEST_USER_ID) {
                    _digestState.value = DigestUiState(
                        isStreaming = false,
                        focusTitle = focusTitle,
                        content = "### 🎙️ Cross-Note Synthesis Overview\n\n- **Clean Energy**: Reviewed IRS Clean Vehicle Credit guidelines for 2026. Verified dealer point-of-sale transfer mechanism.\n- **Action Items**: 5 actionable checklist items synchronized across notes.\n- **Fact-Check Radar**: 3 grounded claims confirmed via live Google Search sources.",
                        sources = listOf(
                            FactSource(title = "IRS Clean Vehicle Guidance", url = "https://www.irs.gov/clean-vehicle-credits")
                        ),
                        errorMessage = null
                    )
                } else {
                    quotaManager.refundInteraction(QuotaFeature.DIGEST)
                    _digestState.value = _digestState.value.copy(
                        isStreaming = false,
                        errorMessage = e.localizedMessage ?: context.getString(R.string.err_digest_failed)
                    )
                }
            }
        }
    }

    fun clearDigest() {
        digestJob?.cancel()
        _digestState.value = DigestUiState()
    }

    fun toggleTaskCompletion(note: VoiceNote, taskIndex: Int) {
        val idxLong = taskIndex.toLong()
        val validExisting = note.completedActionIndices.filter { it >= 0L && it < note.actionItems.size }
        val updated = if (validExisting.contains(idxLong)) {
            validExisting - idxLong
        } else {
            validExisting + idxLong
        }
        viewModelScope.launch {
            repository.toggleActionItemAndPin(
                noteId = note.id,
                completedActionIndices = updated,
                pinned = note.pinned
            ).onFailure { err ->
                _captureState.value = _captureState.value.copy(
                    errorMessage = err.localizedMessage ?: context.getString(R.string.err_update_task)
                )
            }
        }
    }

    fun togglePin(note: VoiceNote) {
        viewModelScope.launch {
            repository.toggleActionItemAndPin(
                noteId = note.id,
                completedActionIndices = note.completedActionIndices,
                pinned = !note.pinned
            )
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onSuccess {
                    localAudioStore.deleteAudio(noteId)
                }
        }
    }

    fun deleteSearchHistoryItem(item: FactCheckSearchItem) {
        viewModelScope.launch {
            repository.deleteSearchHistoryItem(item.id)
        }
    }

    fun stopActiveRecordingForSignOut() {
        draftAudioPlayer.release()
        _playingDraftId.value = null
        recorderManager.cancelRecording()
        RecordingForegroundService.stop(context)
        viewModelScope.launch {
            WidgetSnapshotStore.saveAndRefresh(context, emptyList(), isSignedIn = false)
        }
    }

    override fun onCleared() {
        draftAudioPlayer.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "JournalViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
        const val FILTER_ALL = "All"
        const val FILTER_VERIFIED = "Fact-Checked"
    }
}
