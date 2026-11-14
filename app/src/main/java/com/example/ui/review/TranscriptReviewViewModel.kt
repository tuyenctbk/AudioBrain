package com.example.ui.review

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.AudioPlayerManager
import com.example.audio.LocalAudioStore
import com.example.audio.ReadAloudManager
import com.example.audio.ReadAloudState
import com.example.data.ai.AudioBrainAiService
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.NoteCategory
import com.example.data.model.NoteTagHelper
import com.example.data.model.VoiceNote
import com.example.data.repository.VoiceNotesRepository
import com.example.ui.detail.NoteDetailUiState
import com.example.ui.detail.replaceTranscriptSegment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranscriptReviewDraft(
    val title: String = "",
    val transcript: String = "",
    val summary: String = "",
    val category: NoteCategory = NoteCategory.JOURNAL,
    val tags: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val initializedFromNote: Boolean = false
) {
    fun matches(note: VoiceNote): Boolean {
        val normalizedDraftItems = actionItems.map { it.trim() }.filter { it.isNotBlank() }
        val normalizedNoteItems = note.actionItems.map { it.trim() }.filter { it.isNotBlank() }
        return title.trim() == note.title.trim() &&
            transcript.trim() == note.transcript.trim() &&
            summary.trim() == note.summary.trim() &&
            category == NoteCategory.fromLabel(note.category) &&
            NoteTagHelper.normalizeTags(tags) == NoteTagHelper.normalizeTags(note.tags) &&
            normalizedDraftItems == normalizedNoteItems
    }

    companion object {
        fun fromNote(note: VoiceNote): TranscriptReviewDraft = TranscriptReviewDraft(
            title = note.title,
            transcript = note.transcript,
            summary = note.summary,
            category = NoteCategory.fromLabel(note.category),
            tags = note.tags,
            actionItems = note.actionItems,
            initializedFromNote = true
        )
    }
}

class TranscriptReviewViewModel(
    appContext: Context,
    private val repository: VoiceNotesRepository,
    private val aiService: AudioBrainAiService,
    private val localAudioStore: LocalAudioStore,
    private val currentUserId: String,
    private val noteId: String
) : ViewModel() {

    private val context = appContext.applicationContext
    private val audioPlayer = AudioPlayerManager(viewModelScope, context)
    val playbackState: StateFlow<AudioPlaybackState> = audioPlayer.state

    private val readAloudManager = ReadAloudManager(appContext)
    val readAloudState: StateFlow<ReadAloudState> = readAloudManager.state

    val noteState: StateFlow<NoteDetailUiState> = repository.observeNote(currentUserId, noteId)
        .map { note ->
            if (note != null) NoteDetailUiState.Ready(note) else NoteDetailUiState.NotFound
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing note for transcript review", rootError)
            emit(NoteDetailUiState.Error(rootError.message ?: context.getString(R.string.err_load_note)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoteDetailUiState.Loading
        )

    private val _draftState = MutableStateFlow(TranscriptReviewDraft())
    val draftState: StateFlow<TranscriptReviewDraft> = _draftState.asStateFlow()

    val hasUnsavedChanges: StateFlow<Boolean> = combine(noteState, draftState) { state, draft ->
        val note = (state as? NoteDetailUiState.Ready)?.note ?: return@combine false
        draft.initializedFromNote && !draft.matches(note)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false
    )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isPolishingTranscript = MutableStateFlow(false)
    val isPolishingTranscript: StateFlow<Boolean> = _isPolishingTranscript.asStateFlow()

    private val _isFactCheckingTranscript = MutableStateFlow(false)
    val isFactCheckingTranscript: StateFlow<Boolean> = _isFactCheckingTranscript.asStateFlow()

    private val _factCheckMessage = MutableStateFlow<String?>(null)
    val factCheckMessage: StateFlow<String?> = _factCheckMessage.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            val audioFile = localAudioStore.getAudioFile(noteId)
            audioPlayer.loadFile(audioFile)
        }
        viewModelScope.launch {
            noteState.collect { state ->
                val note = (state as? NoteDetailUiState.Ready)?.note ?: return@collect
                if (!_draftState.value.initializedFromNote) {
                    _draftState.value = TranscriptReviewDraft.fromNote(note)
                }
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun consumeFactCheckMessage() {
        _factCheckMessage.value = null
    }

    fun consumeSaveSuccess() {
        _saveSuccess.value = false
    }

    fun onTitleChange(title: String) {
        _draftState.update { it.copy(title = title) }
    }

    fun onCategoryChange(category: NoteCategory) {
        _draftState.update { it.copy(category = category) }
    }

    fun onSummaryChange(summary: String) {
        _draftState.update { it.copy(summary = summary) }
    }

    fun onTranscriptChange(transcript: String) {
        _draftState.update { it.copy(transcript = transcript) }
    }

    fun onSegmentTextChange(segmentIndex: Int, newBody: String) {
        _draftState.update { current ->
            current.copy(
                transcript = replaceTranscriptSegment(
                    transcript = current.transcript,
                    segmentIndex = segmentIndex,
                    newBody = newBody
                )
            )
        }
    }

    fun addTags(rawInput: String) {
        val parsed = NoteTagHelper.parseTagInput(rawInput)
        if (parsed.isEmpty()) return
        _draftState.update { current ->
            current.copy(tags = NoteTagHelper.normalizeTags(current.tags + parsed))
        }
    }

    fun removeTag(tag: String) {
        val target = NoteTagHelper.normalizeTag(tag) ?: return
        _draftState.update { current ->
            current.copy(tags = current.tags.filterNot { it.equals(target, ignoreCase = true) })
        }
    }

    fun updateActionItem(index: Int, value: String) {
        _draftState.update { current ->
            if (index !in current.actionItems.indices) return@update current
            val mutable = current.actionItems.toMutableList()
            mutable[index] = value
            current.copy(actionItems = mutable)
        }
    }

    fun addActionItem(rawItem: String) {
        val trimmed = rawItem.trim()
        if (trimmed.isBlank()) return
        _draftState.update { current ->
            current.copy(actionItems = current.actionItems + trimmed)
        }
    }

    fun removeActionItem(index: Int) {
        _draftState.update { current ->
            if (index !in current.actionItems.indices) return@update current
            val mutable = current.actionItems.toMutableList()
            mutable.removeAt(index)
            current.copy(actionItems = mutable)
        }
    }

    fun polishDraftTranscript() {
        val rawTranscript = _draftState.value.transcript
        if (_isPolishingTranscript.value || rawTranscript.isBlank()) return
        viewModelScope.launch {
            _isPolishingTranscript.value = true
            _actionError.value = null
            aiService.polishTranscript(rawTranscript)
                .onSuccess { cleaned ->
                    _draftState.update { it.copy(transcript = cleaned) }
                }
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_clean_transcript)
                }
            _isPolishingTranscript.value = false
        }
    }

    fun factCheckDraftTranscript(specificClaim: String? = null) {
        val note = (noteState.value as? NoteDetailUiState.Ready)?.note ?: return
        val draftTranscript = _draftState.value.transcript
        if (_isFactCheckingTranscript.value || draftTranscript.isBlank()) return

        viewModelScope.launch {
            _isFactCheckingTranscript.value = true
            _actionError.value = null

            val result = if (!specificClaim.isNullOrBlank()) {
                aiService.factCheckClaims(
                    transcript = draftTranscript,
                    claims = listOf(specificClaim.trim())
                )
            } else {
                aiService.factCheckTranscribedJournal(
                    transcript = draftTranscript,
                    existingClaims = note.claimsToVerify
                )
            }

            result.onSuccess { checked ->
                val mergedClaims = (note.claimsToVerify + checked.verdicts.map { it.claim })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                repository.updateFactCheck(
                    noteId = note.id,
                    factCheckSummary = checked.summary,
                    factCheckStatus = checked.status,
                    searchQueries = checked.searchQueries,
                    sources = checked.sources,
                    claimsToVerify = mergedClaims,
                    claimVerdicts = checked.verdicts
                ).onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_fact_check)
                }

                val firstSource = checked.sources.firstOrNull()?.url.orEmpty()
                checked.verdicts.forEach { verdict ->
                    repository.recordFactCheckSearch(
                        query = verdict.claim,
                        status = verdict.normalizedStatus(),
                        explanation = verdict.explanation,
                        sourceUrl = firstSource,
                        noteId = note.id,
                        noteTitle = _draftState.value.title.ifBlank { note.title }
                    )
                }

                val issueCount = checked.verdicts.count {
                    val s = it.normalizedStatus()
                    s == ClaimVerdictStatus.DISPUTED || s == ClaimVerdictStatus.UPDATED
                }
                _factCheckMessage.value = if (issueCount > 0) {
                    context.getString(R.string.status_factcheck_disputed_found, issueCount)
                } else {
                    context.getString(R.string.status_factcheck_completed)
                }
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_fact_check_failed)
            }

            _isFactCheckingTranscript.value = false
        }
    }

    fun revertChanges() {
        val note = (noteState.value as? NoteDetailUiState.Ready)?.note ?: return
        _draftState.value = TranscriptReviewDraft.fromNote(note)
    }

    fun saveChanges() {
        val note = (noteState.value as? NoteDetailUiState.Ready)?.note ?: return
        val draft = _draftState.value
        if (_isSaving.value || draft.title.isBlank()) return

        val cleanedItems = draft.actionItems
            .map { it.trim() }
            .filter { it.isNotBlank() }

        viewModelScope.launch {
            _isSaving.value = true
            _actionError.value = null
            repository.updateNoteContent(
                noteId = note.id,
                title = draft.title,
                summary = draft.summary,
                transcript = draft.transcript,
                category = draft.category,
                actionItems = cleanedItems,
                claimsToVerify = note.claimsToVerify,
                tags = NoteTagHelper.normalizeTags(draft.tags)
            ).onSuccess {
                _draftState.update {
                    it.copy(
                        title = draft.title.trim(),
                        summary = draft.summary.trim(),
                        transcript = draft.transcript.trim(),
                        actionItems = cleanedItems,
                        tags = NoteTagHelper.normalizeTags(draft.tags)
                    )
                }
                _saveSuccess.value = true
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_note_edits)
            }
            _isSaving.value = false
        }
    }

    fun toggleAudioPlayback() {
        readAloudManager.stop()
        audioPlayer.togglePlayPause()
    }

    fun seekAudio(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun seekAudioToSeconds(seconds: Long) {
        readAloudManager.stop()
        audioPlayer.seekToSeconds(seconds, autoPlay = true)
    }

    fun cyclePlaybackSpeed() {
        audioPlayer.cycleSpeed()
    }

    fun toggleReadAloud(itemId: String, text: String) {
        if (playbackState.value.isPlaying) {
            audioPlayer.togglePlayPause()
        }
        readAloudManager.toggleSpeak(itemId, text)
    }

    override fun onCleared() {
        readAloudManager.release()
        audioPlayer.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "TranscriptReviewVM"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
