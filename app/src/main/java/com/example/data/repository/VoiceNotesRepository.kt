package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ClaimVerdict
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckHistoryHelper
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.FlashcardItem
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.NoteTopicTone
import com.example.data.model.UserPreferences
import com.example.data.model.VoiceNote
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VoiceNotesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: GUEST_USER_ID
    }

    private fun notesCollection(userId: String) =
        db.collection("users").document(userId).collection("notes")

    private fun messagesCollection(userId: String, noteId: String) =
        notesCollection(userId).document(noteId).collection("messages")

    private fun settingsCollection(userId: String) =
        db.collection("users").document(userId).collection("settings")

    private fun factCheckHistoryCollection(userId: String) =
        db.collection("users").document(userId).collection("fact_check_history")

    fun observeNotes(userId: String): Flow<List<VoiceNote>> = flow {
        if (userId == GUEST_USER_ID) {
            emitAll(guestNotesFlow)
            return@flow
        }
        val path = "users/$userId/notes"
        emitAll(
            notesCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            VoiceNote::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedWith(
                        compareByDescending<VoiceNote> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeNotesByTag(userId: String, rawTag: String): Flow<List<VoiceNote>> = flow {
        val normalizedTag = NoteTagHelper.normalizeTag(rawTag)
        if (normalizedTag == null) {
            emit(emptyList())
            return@flow
        }
        if (userId == GUEST_USER_ID) {
            emitAll(guestNotesFlow.map { notes -> notes.filter { it.tags.contains(normalizedTag) } })
            return@flow
        }
        val path = "users/$userId/notes"
        emitAll(
            notesCollection(userId)
                .whereArrayContains("tags", normalizedTag)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            VoiceNote::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedWith(
                        compareByDescending<VoiceNote> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeNote(userId: String, noteId: String): Flow<VoiceNote?> = flow {
        if (userId == GUEST_USER_ID) {
            emitAll(guestNotesFlow.map { notes -> notes.firstOrNull { it.id == noteId } })
            return@flow
        }
        val docRef = notesCollection(userId).document(noteId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            VoiceNote::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, noteId: String): Flow<List<ChatMessage>> = flow {
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
            val flow = guestMessagesMap.getOrPut(noteId) { MutableStateFlow(emptyList()) }
            emitAll(flow)
            return@flow
        }
        val path = "users/$userId/notes/$noteId/messages"
        emitAll(
            messagesCollection(userId, noteId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            ChatMessage::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getNoteById(noteId: String): Result<VoiceNote?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            return Result.success(guestNotesFlow.value.firstOrNull { it.id == noteId })
        }
        val docRef = notesCollection(uid).document(noteId)
        return try {
            val snapshot = docRef.get().await()
            val note = if (snapshot.exists()) {
                snapshot.toObject(
                    VoiceNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        title: String,
        summary: String,
        transcript: String,
        category: NoteCategory,
        durationSeconds: Long,
        actionItems: List<String>,
        claimsToVerify: List<String>,
        decisions: List<String> = emptyList(),
        claimVerdicts: List<ClaimVerdict> = emptyList(),
        factCheckSummary: String = "",
        factCheckStatus: FactCheckStatus = FactCheckStatus.NONE,
        searchQueries: List<String> = emptyList(),
        sources: List<FactSource> = emptyList(),
        flashcards: List<FlashcardItem> = emptyList(),
        pinned: Boolean = false,
        tags: List<String> = emptyList(),
        detectedTone: NoteTopicTone = NoteTopicTone.PROFESSIONAL
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val fallbackTitle = appContext?.getString(R.string.fallback_untitled_voice_note) ?: "Untitled Voice Note"
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            val localId = "local_note_${System.currentTimeMillis()}"
            val newNote = VoiceNote(
                id = localId,
                userId = GUEST_USER_ID,
                title = title.trim().ifBlank { fallbackTitle }.take(200),
                summary = summary.trim().take(4000),
                transcript = transcript.trim().take(50000),
                category = category.label,
                detectedTone = detectedTone.value,
                durationSeconds = durationSeconds.coerceIn(0L, 86400L),
                actionItems = actionItems.take(30),
                completedActionIndices = emptyList(),
                decisions = decisions.map { it.trim() }.filter { it.isNotBlank() }.take(20),
                claimsToVerify = claimsToVerify.take(15),
                claimVerdicts = claimVerdicts.take(15),
                factCheckSummary = factCheckSummary.trim().take(20000),
                factCheckStatus = factCheckStatus.value,
                searchQueries = searchQueries.take(20),
                sources = sources.take(20),
                flashcards = flashcards.take(20),
                pinned = pinned,
                tags = NoteTagHelper.normalizeTags(tags),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            guestNotesFlow.value = listOf(newNote) + guestNotesFlow.value
            return Result.success(localId)
        }
        val docRef = notesCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { fallbackTitle }.take(200),
            "summary" to summary.trim().take(4000),
            "transcript" to transcript.trim().take(50000),
            "category" to category.label,
            "detectedTone" to detectedTone.value,
            "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
            "actionItems" to actionItems.take(30),
            "completedActionIndices" to emptyList<Long>(),
            "decisions" to decisions.map { it.trim() }.filter { it.isNotBlank() }.take(20),
            "claimsToVerify" to claimsToVerify.take(15),
            "claimVerdicts" to claimVerdicts.take(15).map { it.toMap() },
            "factCheckSummary" to factCheckSummary.trim().take(20000),
            "factCheckStatus" to factCheckStatus.value,
            "searchQueries" to searchQueries.take(20),
            "sources" to sources.take(20).map { it.toMap() },
            "flashcards" to flashcards.take(20).map { it.toMap() },
            "pinned" to pinned,
            "tags" to NoteTagHelper.normalizeTags(tags),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleActionItemAndPin(
        noteId: String,
        completedActionIndices: List<Long>,
        pinned: Boolean
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        completedActionIndices = completedActionIndices.distinct().take(30),
                        pinned = pinned,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = mapOf(
            "completedActionIndices" to completedActionIndices.distinct().take(30),
            "pinned" to pinned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNoteContent(
        noteId: String,
        title: String,
        summary: String,
        transcript: String,
        category: NoteCategory,
        actionItems: List<String>,
        claimsToVerify: List<String>,
        tags: List<String>,
        decisions: List<String>? = null,
        detectedTone: NoteTopicTone? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val fallbackTitle = appContext?.getString(R.string.fallback_untitled_voice_note) ?: "Untitled Voice Note"
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        title = title.trim().ifBlank { fallbackTitle }.take(200),
                        summary = summary.trim().take(4000),
                        transcript = transcript.trim().take(50000),
                        category = category.label,
                        detectedTone = detectedTone?.value ?: it.detectedTone,
                        actionItems = actionItems.take(30),
                        claimsToVerify = claimsToVerify.take(15),
                        tags = NoteTagHelper.normalizeTags(tags),
                        decisions = decisions?.map { d -> d.trim() }?.filter { d -> d.isNotBlank() }?.take(20) ?: it.decisions,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = buildMap<String, Any> {
            put("title", title.trim().ifBlank { fallbackTitle }.take(200))
            put("summary", summary.trim().take(4000))
            put("transcript", transcript.trim().take(50000))
            put("category", category.label)
            if (detectedTone != null) {
                put("detectedTone", detectedTone.value)
            }
            put("actionItems", actionItems.take(30))
            put("claimsToVerify", claimsToVerify.take(15))
            put("tags", NoteTagHelper.normalizeTags(tags))
            if (decisions != null) {
                put("decisions", decisions.map { it.trim() }.filter { it.isNotBlank() }.take(20))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNoteTags(
        noteId: String,
        tags: List<String>
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        tags = NoteTagHelper.normalizeTags(tags),
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = mapOf(
            "tags" to NoteTagHelper.normalizeTags(tags),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateFactCheck(
        noteId: String,
        factCheckSummary: String,
        factCheckStatus: FactCheckStatus,
        searchQueries: List<String>,
        sources: List<FactSource>,
        claimsToVerify: List<String>,
        claimVerdicts: List<ClaimVerdict> = emptyList()
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        factCheckSummary = factCheckSummary.trim().take(20000),
                        factCheckStatus = factCheckStatus.value,
                        searchQueries = searchQueries.take(20),
                        sources = sources.take(20),
                        claimsToVerify = claimsToVerify.take(15),
                        claimVerdicts = claimVerdicts.take(15),
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = mapOf(
            "factCheckSummary" to factCheckSummary.trim().take(20000),
            "factCheckStatus" to factCheckStatus.value,
            "searchQueries" to searchQueries.take(20),
            "sources" to sources.take(20).map { it.toMap() },
            "claimsToVerify" to claimsToVerify.take(15),
            "claimVerdicts" to claimVerdicts.take(15).map { it.toMap() },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateFlashcards(
        noteId: String,
        flashcards: List<FlashcardItem>
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        flashcards = flashcards.take(20),
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = mapOf(
            "flashcards" to flashcards.take(20).map { it.toMap() },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNoteSummaryAndTone(
        noteId: String,
        summary: String,
        detectedTone: NoteTopicTone
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.map {
                if (it.id == noteId) {
                    it.copy(
                        summary = summary.trim().take(4000),
                        detectedTone = detectedTone.value,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = notesCollection(uid).document(noteId)
        val payload = mapOf(
            "summary" to summary.trim().take(4000),
            "detectedTone" to detectedTone.value,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestNotesFlow.value = guestNotesFlow.value.filterNot { it.id == noteId }
            guestMessagesMap.remove(noteId)
            return Result.success(Unit)
        }
        val noteRef = notesCollection(uid).document(noteId)
        return try {
            val messagesSnap = messagesCollection(uid, noteId).get().await()
            val allRefs = messagesSnap.documents.map { it.reference } + noteRef
            for (chunk in allRefs.chunked(450)) {
                val batch = db.batch()
                for (ref in chunk) {
                    batch.delete(ref)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, noteRef.path)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        noteId: String,
        role: String,
        text: String,
        searchQueries: List<String> = emptyList(),
        sources: List<FactSource> = emptyList()
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            val msgId = "guest_msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
            val msg = ChatMessage(
                id = msgId,
                userId = GUEST_USER_ID,
                role = role,
                text = text.trim().ifBlank { " " }.take(30000),
                searchQueries = searchQueries.take(15),
                sources = sources.take(15),
                createdAt = Timestamp.now()
            )
            val flow = guestMessagesMap.getOrPut(noteId) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + msg
            return Result.success(msgId)
        }
        val msgRef = messagesCollection(uid, noteId).document()
        val payload = mapOf(
            "userId" to uid,
            "role" to role,
            "text" to text.trim().ifBlank { " " }.take(30000),
            "searchQueries" to searchQueries.take(15),
            "sources" to sources.take(15).map { it.toMap() },
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            msgRef.set(payload).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    fun observeUserPreferences(userId: String): Flow<UserPreferences> = flow {
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
            emitAll(guestPreferencesFlow)
            return@flow
        }
        val docRef = settingsCollection(userId).document(PREFS_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferences(userId = userId)
                    } else {
                        UserPreferences(
                            userId = userId,
                            noteStructure = NoteStructureMode.DETAILED.value
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateNoteStructurePreference(
        mode: NoteStructureMode,
        contextualSummarization: Boolean? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            val current = guestPreferencesFlow.value
            val resolvedContextual = contextualSummarization ?: current.contextualSummarization
            guestPreferencesFlow.value = current.copy(
                noteStructure = mode.value,
                contextualSummarization = resolvedContextual,
                updatedAt = Timestamp.now()
            )
            return Result.success(Unit)
        }
        val docRef = settingsCollection(uid).document(PREFS_DOC_ID)
        return try {
            val resolvedContextual = contextualSummarization ?: run {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    existingSnap.getBoolean("contextualSummarization") ?: true
                } else {
                    true
                }
            }
            val payload = mapOf(
                "userId" to uid,
                "noteStructure" to mode.value,
                "contextualSummarization" to resolvedContextual,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateContextualSummarizationPreference(
        enabled: Boolean,
        structureMode: NoteStructureMode? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            val current = guestPreferencesFlow.value
            val resolvedMode = structureMode ?: NoteStructureMode.fromValue(current.noteStructure)
            guestPreferencesFlow.value = current.copy(
                noteStructure = resolvedMode.value,
                contextualSummarization = enabled,
                updatedAt = Timestamp.now()
            )
            return Result.success(Unit)
        }
        val docRef = settingsCollection(uid).document(PREFS_DOC_ID)
        return try {
            val resolvedMode = structureMode ?: run {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    NoteStructureMode.fromValue(existingSnap.getString("noteStructure"))
                } else {
                    NoteStructureMode.DETAILED
                }
            }
            val payload = mapOf(
                "userId" to uid,
                "noteStructure" to resolvedMode.value,
                "contextualSummarization" to enabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeFactCheckHistory(userId: String): Flow<List<FactCheckSearchItem>> = flow {
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
            emitAll(guestFactCheckHistoryFlow)
            return@flow
        }
        val path = "users/$userId/fact_check_history"
        emitAll(
            factCheckHistoryCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            FactCheckSearchItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedWith(
                        compareByDescending<FactCheckSearchItem> { it.verificationCount }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordFactCheckSearch(
        query: String,
        status: ClaimVerdictStatus = ClaimVerdictStatus.SUPPORTED,
        explanation: String = "",
        sourceUrl: String = "",
        noteId: String? = null,
        noteTitle: String? = null,
        topic: String? = null
    ): Result<Unit> {
        val cleanQuery = query.trim().take(400)
        if (cleanQuery.isBlank()) return Result.success(Unit)

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            val historyId = FactCheckHistoryHelper.generateHistoryId(cleanQuery)
            val existing = guestFactCheckHistoryFlow.value.firstOrNull { it.id == historyId }
            val count = (existing?.verificationCount ?: 0L) + 1L
            val resolvedTopic = topic?.trim()?.take(200) ?: FactCheckHistoryHelper.cleanTopicLabel(cleanQuery)
            val item = FactCheckSearchItem(
                id = historyId,
                userId = GUEST_USER_ID,
                query = cleanQuery,
                topic = resolvedTopic,
                verificationCount = count,
                lastStatus = status.value,
                lastExplanation = explanation.trim().take(2000),
                sourceUrl = sourceUrl?.trim()?.take(2000) ?: "",
                lastNoteId = noteId?.trim()?.take(100) ?: "",
                lastNoteTitle = noteTitle?.trim()?.take(300) ?: "",
                createdAt = existing?.createdAt ?: Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            guestFactCheckHistoryFlow.value = listOf(item) + guestFactCheckHistoryFlow.value.filterNot { it.id == historyId }
            return Result.success(Unit)
        }

        val historyId = FactCheckHistoryHelper.generateHistoryId(cleanQuery)
        val docRef = factCheckHistoryCollection(uid).document(historyId)

        return try {
            val resolvedTopic = topic?.trim()?.take(200)
                ?: FactCheckHistoryHelper.cleanTopicLabel(cleanQuery)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val existingCount = if (snapshot.exists()) {
                    snapshot.getLong("verificationCount") ?: 1L
                } else {
                    0L
                }
                val newCount = (existingCount + 1L).coerceIn(1L, 10000L)

                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("query", cleanQuery)
                    put("topic", resolvedTopic)
                    put("verificationCount", newCount)
                    put("lastStatus", status.value)
                    if (explanation.isNotBlank()) put("lastExplanation", explanation.trim().take(2000))
                    if (sourceUrl.isNotBlank()) put("sourceUrl", sourceUrl.trim().take(2000))
                    if (!noteId.isNullOrBlank()) put("lastNoteId", noteId.trim().take(100))
                    if (!noteTitle.isNullOrBlank()) put("lastNoteTitle", noteTitle.trim().take(300))
                    if (!snapshot.exists()) {
                        put("createdAt", FieldValue.serverTimestamp())
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }

                transaction.set(docRef, payload, SetOptions.merge())
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSearchHistoryItem(historyId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (uid == GUEST_USER_ID || auth.currentUser == null) {
            guestFactCheckHistoryFlow.value = guestFactCheckHistoryFlow.value.filterNot { it.id == historyId }
            return Result.success(Unit)
        }
        val docRef = factCheckHistoryCollection(uid).document(historyId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val PREFS_DOC_ID = "preferences"
        const val GUEST_USER_ID = "guest_user"

        val guestNotesFlow = MutableStateFlow<List<VoiceNote>>(emptyList())
        val guestFactCheckHistoryFlow = MutableStateFlow<List<FactCheckSearchItem>>(emptyList())
        val guestMessagesMap = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()
        val guestPreferencesFlow = MutableStateFlow(
            UserPreferences(
                userId = GUEST_USER_ID,
                noteStructure = NoteStructureMode.DETAILED.value,
                contextualSummarization = true
            )
        )
    }
}
