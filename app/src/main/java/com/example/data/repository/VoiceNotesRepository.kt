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
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
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
                    emitAll(guestNotesFlow)
                }
        )
    }

    fun observeNotesByTag(userId: String, rawTag: String): Flow<List<VoiceNote>> = flow {
        val normalizedTag = NoteTagHelper.normalizeTag(rawTag)
        if (normalizedTag == null) {
            emit(emptyList())
            return@flow
        }
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
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
                    emitAll(guestNotesFlow.map { notes -> notes.filter { it.tags.contains(normalizedTag) } })
                }
        )
    }

    fun observeNote(userId: String, noteId: String): Flow<VoiceNote?> = flow {
        if (userId == GUEST_USER_ID || auth.currentUser == null) {
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
                    emitAll(guestNotesFlow.map { notes -> notes.firstOrNull { it.id == noteId } })
                }
        )
    }

    fun observeMessages(userId: String, noteId: String): Flow<List<ChatMessage>> = flow {
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
        val docRef = notesCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Untitled Voice Note" }.take(200),
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
        val docRef = notesCollection(uid).document(noteId)
        val payload = buildMap<String, Any> {
            put("title", title.trim().ifBlank { "Untitled Voice Note" }.take(200))
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
        val noteRef = notesCollection(uid).document(noteId)
        return try {
            val messagesSnap = messagesCollection(uid, noteId).get().await()
            val batch = db.batch()
            for (msgDoc in messagesSnap.documents) {
                batch.delete(msgDoc.reference)
            }
            batch.delete(noteRef)
            batch.commit().await()
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
            emit(UserPreferences(userId = userId, noteStructure = NoteStructureMode.DETAILED.value, contextualSummarization = true))
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
            emit(demoFactCheckHistory)
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

        val historyId = FactCheckHistoryHelper.generateHistoryId(cleanQuery)
        val docRef = factCheckHistoryCollection(uid).document(historyId)

        return try {
            val snapshot = docRef.get().await()
            val existingCount = if (snapshot.exists()) {
                snapshot.getLong("verificationCount") ?: 1L
            } else {
                0L
            }
            val newCount = (existingCount + 1L).coerceIn(1L, 10000L)
            val resolvedTopic = topic?.trim()?.take(200)
                ?: FactCheckHistoryHelper.cleanTopicLabel(cleanQuery)

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

            docRef.set(payload, SetOptions.merge()).await()
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
        const val GUEST_USER_ID = "guest_demo_user"

        val demoNotes: List<VoiceNote> = listOf(
            VoiceNote(
                id = "demo_note_1",
                userId = GUEST_USER_ID,
                title = "EV Tax Credit & Clean Energy Incentives (2026)",
                summary = "Comprehensive review of the federal Clean Vehicle Tax Credit rules for 2026. Includes price caps, critical mineral sourcing requirements, and point-of-sale dealer discount mechanisms verified against IRS and Energy.gov guidelines.",
                transcript = "[00:00] Today we're reviewing the 2026 Clean Vehicle Tax Credit rules.\n[00:08] The critical mineral and battery component requirements have reached 80% this year.\n[00:19] Buyers can transfer the $7,500 credit directly to registered dealers at point of sale.\n[00:32] Income caps remain $150k for single filers and $300k for married couples.\n[00:45] Next steps: verify eligible vehicle VINs on the fueleconomy.gov portal.",
                category = NoteCategory.RESEARCH.label,
                detectedTone = NoteTopicTone.TECHNICAL.value,
                durationSeconds = 52L,
                pinned = true,
                tags = listOf("research", "finance", "tax", "ideas"),
                actionItems = listOf(
                    "Verify vehicle VIN eligibility on fueleconomy.gov portal",
                    "Download IRS Form 8936 for clean vehicle tax credits",
                    "Confirm dealer point-of-sale transfer registration"
                ),
                completedActionIndices = listOf(0L),
                decisions = listOf(
                    "Utilize point-of-sale tax credit transfer rather than tax return filing",
                    "Focus search on vehicles meeting 80% critical mineral domestic sourcing"
                ),
                claimsToVerify = listOf(
                    "Federal EV tax credit value is up to $7,500 in 2026",
                    "Point-of-sale credit transfer is available at registered dealerships",
                    "Single filer income cap is $150,000"
                ),
                claimVerdicts = listOf(
                    ClaimVerdict(
                        claim = "Federal EV tax credit value is up to $7,500 in 2026",
                        status = ClaimVerdictStatus.SUPPORTED.value,
                        explanation = "Confirmed via IRS Section 30D guidance. Eligible clean vehicles qualify for up to $7,500 divided between critical minerals and battery component criteria."
                    ),
                    ClaimVerdict(
                        claim = "Point-of-sale credit transfer is available at registered dealerships",
                        status = ClaimVerdictStatus.SUPPORTED.value,
                        explanation = "IRS Energy Credits Online allows registered dealers to apply the credit directly at time of purchase as cash down payment."
                    ),
                    ClaimVerdict(
                        claim = "Single filer income cap is $150,000",
                        status = ClaimVerdictStatus.UPDATED.value,
                        explanation = "Modified AGI limits are $150,000 for single filers, $225,000 for head of household, and $300,000 for married couples filing jointly."
                    )
                ),
                factCheckSummary = "Verified 3 claims against IRS Section 30D and Energy.gov guidelines. Mineral requirements confirmed at current threshold, and point-of-sale dealer credit transfer verified.",
                factCheckStatus = FactCheckStatus.VERIFIED.value,
                sources = listOf(
                    FactSource(title = "IRS Clean Vehicle Credit Guidance (Section 30D)", url = "https://www.irs.gov/clean-vehicles"),
                    FactSource(title = "FuelEconomy.gov Eligible Clean Vehicles", url = "https://www.fueleconomy.gov")
                ),
                flashcards = listOf(
                    FlashcardItem(question = "What is the maximum federal EV tax credit amount?", answer = "Up to $7,500 under IRS Section 30D."),
                    FlashcardItem(question = "What are the modified AGI income caps for single filers?", answer = "$150,000 modified adjusted gross income.")
                ),
                createdAt = Timestamp(System.currentTimeMillis() / 1000 - 3600, 0),
                updatedAt = Timestamp(System.currentTimeMillis() / 1000 - 1800, 0)
            ),
            VoiceNote(
                id = "demo_note_2",
                userId = GUEST_USER_ID,
                title = "AI Architecture & Streaming Latency Review",
                summary = "System design notes on Gemini Flash audio transcription and Firestore sync pipeline. Target end-to-end processing latency under 1.8 seconds with background noise filtering.",
                transcript = "[00:00] Meeting on AudioBrain mobile performance and AI inference pipeline.\n[00:10] Current Gemini Flash audio chunking achieves 1.4s average turnaround.\n[00:22] We verified that App Check attestation provides secure client access without leaking keys.\n[00:35] Need to ensure offline drafts queue cleanly when airplane mode is enabled.",
                category = NoteCategory.MEETING.label,
                detectedTone = NoteTopicTone.TECHNICAL.value,
                durationSeconds = 42L,
                pinned = true,
                tags = listOf("meeting", "work", "todo"),
                actionItems = listOf(
                    "Benchmark audio chunking streaming latency under 1.5 seconds",
                    "Verify offline queue persistence in PendingMemoStore"
                ),
                completedActionIndices = emptyList(),
                decisions = listOf(
                    "Standardize on 16kHz mono audio recording for optimal speech transcription",
                    "Enable local SQLite/Room caching for pending voice drafts"
                ),
                claimsToVerify = listOf(
                    "Gemini Flash supports direct audio transcription without server transcoding"
                ),
                claimVerdicts = listOf(
                    ClaimVerdict(
                        claim = "Gemini Flash supports direct audio transcription",
                        status = ClaimVerdictStatus.SUPPORTED.value,
                        explanation = "Gemini 1.5/2.0 Flash natively accepts raw audio PCM/AAC waveforms in multipart requests."
                    )
                ),
                factCheckSummary = "Confirmed multimodal streaming support in Gemini API documentation.",
                factCheckStatus = FactCheckStatus.VERIFIED.value,
                sources = listOf(
                    FactSource(title = "Google AI Gemini Multimodal Audio Docs", url = "https://ai.google.dev")
                ),
                flashcards = listOf(
                    FlashcardItem(question = "What audio sample rate is optimal for speech transcription?", answer = "16,000 Hz (16kHz) mono PCM or AAC.")
                ),
                createdAt = Timestamp(System.currentTimeMillis() / 1000 - 7200, 0),
                updatedAt = Timestamp(System.currentTimeMillis() / 1000 - 3600, 0)
            ),
            VoiceNote(
                id = "demo_note_3",
                userId = GUEST_USER_ID,
                title = "Morning Reflections & Book Notes: Atomic Habits",
                summary = "Reflections on habit stacking and implementation intentions. Focus on 1% daily compounding improvements rather than ambitious sporadic goals.",
                transcript = "[00:00] Quick morning commute thought while listening to James Clear.\n[00:09] The two-minute rule: when you start a new habit, it should take less than two minutes.\n[00:25] Pair recording a voice memo immediately after morning coffee.",
                category = NoteCategory.JOURNAL.label,
                detectedTone = NoteTopicTone.CASUAL.value,
                durationSeconds = 31L,
                pinned = false,
                tags = listOf("journal", "ideas", "personal"),
                actionItems = listOf(
                    "Practice the 2-minute habit rule for morning voice journaling",
                    "Review daily digest summary each evening at 8 PM"
                ),
                completedActionIndices = listOf(0L),
                decisions = listOf(
                    "Journal every morning directly after coffee using the quick record home widget"
                ),
                claimsToVerify = emptyList(),
                claimVerdicts = emptyList(),
                factCheckStatus = FactCheckStatus.NONE.value,
                flashcards = listOf(
                    FlashcardItem(question = "What is the 2-minute rule in Atomic Habits?", answer = "Scale down new habits until they take under two minutes to start.")
                ),
                createdAt = Timestamp(System.currentTimeMillis() / 1000 - 86400, 0),
                updatedAt = Timestamp(System.currentTimeMillis() / 1000 - 86400, 0)
            )
        )

        val demoFactCheckHistory: List<FactCheckSearchItem> = listOf(
            FactCheckSearchItem(
                id = "history_1",
                userId = GUEST_USER_ID,
                query = "irs clean vehicle tax credit rules 2026",
                topic = "EV Tax Credit Rules (2026)",
                verificationCount = 5L,
                lastStatus = ClaimVerdictStatus.SUPPORTED.value,
                lastExplanation = "Confirmed via IRS Section 30D guidance. Eligible clean vehicles qualify for up to $7,500.",
                sourceUrl = "https://www.irs.gov/clean-vehicles",
                lastNoteId = "demo_note_1",
                lastNoteTitle = "EV Tax Credit & Clean Energy Incentives (2026)"
            ),
            FactCheckSearchItem(
                id = "history_2",
                userId = GUEST_USER_ID,
                query = "gemini flash audio multimodal streaming latency",
                topic = "Gemini Flash Audio Latency",
                verificationCount = 3L,
                lastStatus = ClaimVerdictStatus.SUPPORTED.value,
                lastExplanation = "Gemini natively accepts raw audio PCM/AAC waveforms in multipart requests.",
                sourceUrl = "https://ai.google.dev",
                lastNoteId = "demo_note_2",
                lastNoteTitle = "AI Architecture & Streaming Latency Review"
            )
        )

        val guestNotesFlow = MutableStateFlow(demoNotes)
    }
}
