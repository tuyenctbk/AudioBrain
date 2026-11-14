package com.example.ui.detail

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
import com.example.audio.VoiceRecorderManager
import com.example.data.ai.AudioBrainAiService
import com.example.data.model.ChatMessage
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactSource
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.VoiceNote
import com.example.data.repository.VoiceNotesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NoteDetailUiState {
    data object Loading : NoteDetailUiState
    data class Ready(val note: VoiceNote) : NoteDetailUiState
    data object NotFound : NoteDetailUiState
    data class Error(val message: String) : NoteDetailUiState
}

data class StreamingChatBubble(
    val text: String = "",
    val searchQueries: List<String> = emptyList(),
    val sources: List<FactSource> = emptyList()
)

class NoteDetailViewModel(
    appContext: Context,
    private val repository: VoiceNotesRepository,
    private val aiService: AudioBrainAiService,
    private val localAudioStore: LocalAudioStore,
    private val recorderManager: VoiceRecorderManager,
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
            Log.w(TAG, "Error observing note detail", rootError)
            emit(NoteDetailUiState.Error(rootError.message ?: context.getString(R.string.err_load_note)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoteDetailUiState.Loading
        )

    val messagesState: StateFlow<List<ChatMessage>> = repository.observeMessages(currentUserId, noteId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing chat messages", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val noteStructureMode: StateFlow<NoteStructureMode> = repository.observeUserPreferences(currentUserId)
        .map { prefs -> prefs.structureMode }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user preferences", firestoreError ?: error)
            emit(NoteStructureMode.DETAILED)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoteStructureMode.DETAILED
        )

    val contextualSummarization: StateFlow<Boolean> = repository.observeUserPreferences(currentUserId)
        .map { prefs -> prefs.contextualSummarization }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing contextual summarization preference", firestoreError ?: error)
            emit(true)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = true
        )

    val searchHistory: StateFlow<List<FactCheckSearchItem>> = repository.observeFactCheckHistory(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing search history", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val allNotes: StateFlow<List<VoiceNote>> = repository.observeNotes(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing all notes", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _isFactChecking = MutableStateFlow(false)
    val isFactChecking: StateFlow<Boolean> = _isFactChecking.asStateFlow()

    private val _isGeneratingFlashcards = MutableStateFlow(false)
    val isGeneratingFlashcards: StateFlow<Boolean> = _isGeneratingFlashcards.asStateFlow()

    private val _isPolishingTranscript = MutableStateFlow(false)
    val isPolishingTranscript: StateFlow<Boolean> = _isPolishingTranscript.asStateFlow()

    private val _isAdaptingSummary = MutableStateFlow(false)
    val isAdaptingSummary: StateFlow<Boolean> = _isAdaptingSummary.asStateFlow()

    private val _isStreamingChat = MutableStateFlow(false)
    val isStreamingChat: StateFlow<Boolean> = _isStreamingChat.asStateFlow()

    private val _isListeningVoiceQuestion = MutableStateFlow(false)
    val isListeningVoiceQuestion: StateFlow<Boolean> = _isListeningVoiceQuestion.asStateFlow()

    private val _voiceQuestionElapsedSeconds = MutableStateFlow(0L)
    val voiceQuestionElapsedSeconds: StateFlow<Long> = _voiceQuestionElapsedSeconds.asStateFlow()

    private val _isTranscribingVoiceQuestion = MutableStateFlow(false)
    val isTranscribingVoiceQuestion: StateFlow<Boolean> = _isTranscribingVoiceQuestion.asStateFlow()

    private val _streamingBubble = MutableStateFlow<StreamingChatBubble?>(null)
    val streamingBubble: StateFlow<StreamingChatBubble?> = _streamingBubble.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        viewModelScope.launch {
            val audioFile = localAudioStore.getAudioFile(noteId)
            audioPlayer.loadFile(audioFile)
        }
        viewModelScope.launch {
            recorderManager.state.collect { rec ->
                if (_isListeningVoiceQuestion.value) {
                    _voiceQuestionElapsedSeconds.value = rec.elapsedSeconds
                    if (!rec.isRecording) {
                        _isListeningVoiceQuestion.value = false
                    }
                }
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun toggleNoteStructureMode() {
        val next = if (noteStructureMode.value == NoteStructureMode.BRIEF) {
            NoteStructureMode.DETAILED
        } else {
            NoteStructureMode.BRIEF
        }
        viewModelScope.launch {
            repository.updateNoteStructurePreference(next)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_update_structure_pref)
                }
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

    fun stopReadAloud() {
        readAloudManager.stop()
    }

    fun toggleActionItem(note: VoiceNote, index: Int) {
        val idxLong = index.toLong()
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
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_update_task)
            }
        }
    }

    fun togglePin(note: VoiceNote) {
        viewModelScope.launch {
            repository.toggleActionItemAndPin(
                noteId = note.id,
                completedActionIndices = note.completedActionIndices,
                pinned = !note.pinned
            ).onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_update_pin)
            }
        }
    }

    fun saveNoteEdits(
        note: VoiceNote,
        title: String,
        summary: String,
        transcript: String,
        category: NoteCategory,
        actionItems: List<String>,
        tags: List<String> = note.tags
    ) {
        viewModelScope.launch {
            repository.updateNoteContent(
                noteId = note.id,
                title = title,
                summary = summary,
                transcript = transcript,
                category = category,
                actionItems = actionItems,
                claimsToVerify = note.claimsToVerify,
                tags = tags
            ).onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_note_edits)
            }
        }
    }

    fun addTags(note: VoiceNote, rawInput: String) {
        val parsed = NoteTagHelper.parseTagInput(rawInput)
        if (parsed.isEmpty()) return
        val merged = NoteTagHelper.normalizeTags(note.tags + parsed)
        if (merged == note.tags) return
        updateTags(note, merged)
    }

    fun removeTag(note: VoiceNote, tag: String) {
        val target = NoteTagHelper.normalizeTag(tag) ?: return
        val updated = note.tags.filterNot { it.equals(target, ignoreCase = true) }
        updateTags(note, updated)
    }

    fun updateTags(note: VoiceNote, tags: List<String>) {
        val normalized = NoteTagHelper.normalizeTags(tags)
        viewModelScope.launch {
            repository.updateNoteTags(note.id, normalized)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_update_tags)
                }
        }
    }

    fun runFactCheck(note: VoiceNote, customClaim: String? = null) {
        if (_isFactChecking.value) return
        viewModelScope.launch {
            _isFactChecking.value = true
            _actionError.value = null

            val claims = if (!customClaim.isNullOrBlank()) {
                (listOf(customClaim.trim()) + note.claimsToVerify).distinct()
            } else {
                note.claimsToVerify
            }

            val result = if (customClaim.isNullOrBlank() && claims.isEmpty()) {
                aiService.factCheckTranscribedJournal(
                    transcript = note.transcript,
                    existingClaims = claims
                )
            } else {
                aiService.factCheckClaims(
                    transcript = note.transcript,
                    claims = claims
                )
            }

            result.onSuccess { checked ->
                val mergedClaims = (claims + checked.verdicts.map { it.claim })
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

                // Record each verified verdict or custom query into search history
                val firstSource = checked.sources.firstOrNull()?.url.orEmpty()
                if (checked.verdicts.isNotEmpty()) {
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
                } else if (!customClaim.isNullOrBlank()) {
                    repository.recordFactCheckSearch(
                        query = customClaim.trim(),
                        status = if (checked.sources.isNotEmpty()) ClaimVerdictStatus.SUPPORTED else ClaimVerdictStatus.UNVERIFIED,
                        explanation = checked.summary.take(200),
                        sourceUrl = firstSource,
                        noteId = note.id,
                        noteTitle = note.title
                    )
                }
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_fact_check_failed)
            }

            _isFactChecking.value = false
        }
    }

    fun generateFlashcards(note: VoiceNote) {
        if (_isGeneratingFlashcards.value) return
        viewModelScope.launch {
            _isGeneratingFlashcards.value = true
            _actionError.value = null

            aiService.generateFlashcards(note)
                .onSuccess { cards ->
                    repository.updateFlashcards(note.id, cards)
                        .onFailure { err ->
                            _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_flashcards)
                        }
                }
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_generate_flashcards)
                }

            _isGeneratingFlashcards.value = false
        }
    }

    fun polishNoteTranscript(note: VoiceNote) {
        if (_isPolishingTranscript.value || note.transcript.isBlank()) return
        viewModelScope.launch {
            _isPolishingTranscript.value = true
            _actionError.value = null

            aiService.polishTranscript(note.transcript)
                .onSuccess { cleanedTranscript ->
                    repository.updateNoteContent(
                        noteId = note.id,
                        title = note.title,
                        summary = note.summary,
                        transcript = cleanedTranscript,
                        category = NoteCategory.fromLabel(note.category),
                        actionItems = note.actionItems,
                        claimsToVerify = note.claimsToVerify,
                        tags = note.tags
                    ).onFailure { err ->
                        _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_cleaned_transcript)
                    }
                }
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_clean_transcript)
                }

            _isPolishingTranscript.value = false
        }
    }

    fun adaptSummaryTone(note: VoiceNote) {
        if (_isAdaptingSummary.value) return
        viewModelScope.launch {
            _isAdaptingSummary.value = true
            _actionError.value = null

            aiService.regenerateContextualSummary(
                note = note,
                structureMode = noteStructureMode.value,
                contextualSummarization = contextualSummarization.value
            ).onSuccess { (newSummary, detectedTone) ->
                repository.updateNoteSummaryAndTone(
                    noteId = note.id,
                    summary = newSummary,
                    detectedTone = detectedTone
                ).onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_save_adapted_summary)
                }
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_adapt_summary_tone)
            }

            _isAdaptingSummary.value = false
        }
    }

    fun deleteSearchHistoryItem(item: FactCheckSearchItem) {
        viewModelScope.launch {
            repository.deleteSearchHistoryItem(item.id)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_delete_search_history)
                }
        }
    }

    fun startVoiceQuestionRecording() {
        if (_isStreamingChat.value || _isTranscribingVoiceQuestion.value) return
        if (recorderManager.state.value.isRecording) {
            _actionError.value = context.getString(R.string.err_finish_active_recording_first)
            return
        }
        readAloudManager.stop()
        if (playbackState.value.isPlaying) {
            audioPlayer.togglePlayPause()
        }
        val started = recorderManager.startRecording(
            scope = viewModelScope,
            userId = currentUserId,
            autoFactCheck = false
        )
        if (started) {
            _voiceQuestionElapsedSeconds.value = 0L
            _isListeningVoiceQuestion.value = true
        } else {
            _actionError.value = recorderManager.state.value.errorMessage ?: context.getString(R.string.err_mic_unavailable)
        }
    }

    fun cancelVoiceQuestionRecording() {
        if (_isListeningVoiceQuestion.value) {
            recorderManager.cancelRecording()
            _isListeningVoiceQuestion.value = false
            _voiceQuestionElapsedSeconds.value = 0L
        }
    }

    fun stopAndSubmitVoiceQuestion(note: VoiceNote) {
        if (!_isListeningVoiceQuestion.value) return
        viewModelScope.launch {
            _isListeningVoiceQuestion.value = false
            _isTranscribingVoiceQuestion.value = true
            val recorded = recorderManager.stopRecording().getOrElse { err ->
                _isTranscribingVoiceQuestion.value = false
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_voice_question_failed)
                return@launch
            }

            val questionResult = aiService.transcribeVoiceQuestion(
                audioBytes = recorded.bytes,
                mimeType = recorded.mimeType
            )
            _isTranscribingVoiceQuestion.value = false

            questionResult.onSuccess { question ->
                sendChatMessage(note, "🎤 $question")
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: context.getString(R.string.err_voice_question_transcribe)
            }
        }
    }

    fun sendChatMessage(note: VoiceNote, promptText: String) {
        val prompt = promptText.trim()
        if (prompt.isBlank() || _isStreamingChat.value) return

        viewModelScope.launch {
            _isStreamingChat.value = true
            _actionError.value = null
            _streamingBubble.value = StreamingChatBubble(text = "")

            val historySnapshot = messagesState.value

            val saveUserResult = repository.addMessage(
                noteId = note.id,
                role = "user",
                text = prompt
            )
            if (saveUserResult.isFailure) {
                _actionError.value = saveUserResult.exceptionOrNull()?.localizedMessage
                    ?: context.getString(R.string.err_save_message)
                _streamingBubble.value = null
                _isStreamingChat.value = false
                return@launch
            }

            try {
                var finalUpdateText = ""
                var finalQueries = emptyList<String>()
                var finalSources = emptyList<FactSource>()

                aiService.streamNoteChat(
                    note = note,
                    history = historySnapshot,
                    userPrompt = prompt
                ).collect { update ->
                    finalUpdateText = update.text
                    finalQueries = update.searchQueries
                    finalSources = update.sources
                    _streamingBubble.value = StreamingChatBubble(
                        text = update.text,
                        searchQueries = update.searchQueries,
                        sources = update.sources
                    )
                }

                if (finalUpdateText.isNotBlank()) {
                    repository.addMessage(
                        noteId = note.id,
                        role = "model",
                        text = finalUpdateText,
                        searchQueries = finalQueries,
                        sources = finalSources
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat streaming timed out", e)
                _actionError.value = context.getString(R.string.err_chat_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat streaming failed", e)
                _actionError.value = e.localizedMessage ?: context.getString(R.string.err_assistant_reply)
            } finally {
                _streamingBubble.value = null
                _isStreamingChat.value = false
            }
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            readAloudManager.stop()
            audioPlayer.release()
            repository.deleteNote(noteId)
                .onSuccess {
                    localAudioStore.deleteAudio(noteId)
                    onDeleted()
                }
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: context.getString(R.string.err_delete_note)
                }
        }
    }

    override fun onCleared() {
        if (_isListeningVoiceQuestion.value) {
            recorderManager.cancelRecording()
        }
        readAloudManager.release()
        audioPlayer.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "NoteDetailViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
