package com.example.data.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PendingMemoKind(val value: String) {
    AUDIO("audio"),
    TEXT("text");

    companion object {
        fun fromValue(v: String?): PendingMemoKind =
            entries.firstOrNull { it.value == v } ?: TEXT
    }
}

data class PendingMemo(
    val id: String,
    val userId: String,
    val kind: PendingMemoKind,
    val audioFilePath: String? = null,
    val mimeType: String? = null,
    val durationSeconds: Long = 0L,
    val textContent: String? = null,
    val autoFactCheck: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val deferredForLater: Boolean = false,
    val label: String? = null
)

class PendingMemoStore private constructor(context: Context) {
    private val rootDir = File(context.applicationContext.filesDir, "pending_memos")
    private val refreshTick = MutableStateFlow(0L)

    fun observePending(userId: String): Flow<List<PendingMemo>> = refreshTick.map {
        loadForUser(userId)
    }

    suspend fun getPending(userId: String): List<PendingMemo> = withContext(Dispatchers.IO) {
        loadForUser(userId)
    }

    suspend fun enqueueAudio(
        userId: String,
        bytes: ByteArray,
        mimeType: String,
        durationSeconds: Long,
        autoFactCheck: Boolean,
        deferredForLater: Boolean = false,
        label: String? = null
    ): PendingMemo = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val userDir = userFolder(userId)
        val audioFile = File(userDir, "$id.audio")
        audioFile.writeBytes(bytes)

        val memo = PendingMemo(
            id = id,
            userId = userId,
            kind = PendingMemoKind.AUDIO,
            audioFilePath = audioFile.absolutePath,
            mimeType = mimeType,
            durationSeconds = durationSeconds,
            autoFactCheck = autoFactCheck,
            createdAtMs = System.currentTimeMillis(),
            deferredForLater = deferredForLater,
            label = label?.trim()?.takeIf { it.isNotBlank() }
        )
        writeMetadata(memo)
        notifyChanged()
        memo
    }

    suspend fun enqueueText(
        userId: String,
        text: String,
        autoFactCheck: Boolean
    ): PendingMemo = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        userFolder(userId)
        val memo = PendingMemo(
            id = id,
            userId = userId,
            kind = PendingMemoKind.TEXT,
            textContent = text,
            autoFactCheck = autoFactCheck,
            createdAtMs = System.currentTimeMillis()
        )
        writeMetadata(memo)
        notifyChanged()
        memo
    }

    suspend fun updateLabel(userId: String, memoId: String, newLabel: String) = withContext(Dispatchers.IO) {
        val metaFile = File(userFolder(userId), "$memoId.json")
        if (!metaFile.exists()) return@withContext
        val existing = parseMetadata(metaFile) ?: return@withContext
        val cleaned = newLabel.trim().take(120).takeIf { it.isNotBlank() }
        writeMetadata(existing.copy(label = cleaned))
        notifyChanged()
    }

    suspend fun markReadyForTranscription(userId: String, memoId: String) = withContext(Dispatchers.IO) {
        val metaFile = File(userFolder(userId), "$memoId.json")
        if (!metaFile.exists()) return@withContext
        val existing = parseMetadata(metaFile) ?: return@withContext
        writeMetadata(existing.copy(deferredForLater = false, lastError = null))
        notifyChanged()
    }

    suspend fun markError(userId: String, memoId: String, errorMessage: String?) = withContext(Dispatchers.IO) {
        val metaFile = File(userFolder(userId), "$memoId.json")
        if (!metaFile.exists()) return@withContext
        val existing = parseMetadata(metaFile) ?: return@withContext
        writeMetadata(existing.copy(lastError = errorMessage))
        notifyChanged()
    }

    suspend fun remove(userId: String, memoId: String) = withContext(Dispatchers.IO) {
        val dir = userFolder(userId)
        File(dir, "$memoId.json").delete()
        File(dir, "$memoId.audio").delete()
        notifyChanged()
    }

    suspend fun readAudioBytes(memo: PendingMemo): ByteArray? = withContext(Dispatchers.IO) {
        val path = memo.audioFilePath ?: return@withContext null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) null else file.readBytes()
    }

    fun getAudioFile(memo: PendingMemo): File? {
        val path = memo.audioFilePath ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    private fun userFolder(userId: String): File {
        val safeUid = userId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val dir = File(rootDir, safeUid)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadForUser(userId: String): List<PendingMemo> {
        if (userId.isBlank()) return emptyList()
        val dir = userFolder(userId)
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { parseMetadata(it) }.sortedByDescending { it.createdAtMs }
    }

    private fun writeMetadata(memo: PendingMemo) {
        val json = JSONObject().apply {
            put("id", memo.id)
            put("userId", memo.userId)
            put("kind", memo.kind.value)
            put("audioFilePath", memo.audioFilePath ?: "")
            put("mimeType", memo.mimeType ?: "")
            put("durationSeconds", memo.durationSeconds)
            put("textContent", memo.textContent ?: "")
            put("autoFactCheck", memo.autoFactCheck)
            put("createdAtMs", memo.createdAtMs)
            put("lastError", memo.lastError ?: "")
            put("deferredForLater", memo.deferredForLater)
            put("label", memo.label ?: "")
        }
        File(userFolder(memo.userId), "${memo.id}.json").writeText(json.toString())
    }

    private fun parseMetadata(file: File): PendingMemo? {
        return runCatching {
            val json = JSONObject(file.readText())
            PendingMemo(
                id = json.getString("id"),
                userId = json.getString("userId"),
                kind = PendingMemoKind.fromValue(json.optString("kind")),
                audioFilePath = json.optString("audioFilePath").takeIf { it.isNotBlank() },
                mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
                durationSeconds = json.optLong("durationSeconds", 0L),
                textContent = json.optString("textContent").takeIf { it.isNotBlank() },
                autoFactCheck = json.optBoolean("autoFactCheck", true),
                createdAtMs = json.optLong("createdAtMs", file.lastModified()),
                lastError = json.optString("lastError").takeIf { it.isNotBlank() },
                deferredForLater = json.optBoolean("deferredForLater", false),
                label = json.optString("label").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    private fun notifyChanged() {
        refreshTick.value = refreshTick.value + 1L
    }

    companion object {
        @Volatile
        private var INSTANCE: PendingMemoStore? = null

        fun getInstance(context: Context): PendingMemoStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PendingMemoStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
