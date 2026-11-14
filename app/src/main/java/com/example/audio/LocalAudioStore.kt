package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalAudioStore(context: Context) {
    private val audioDir = File(context.applicationContext.filesDir, "note_audio")

    suspend fun saveAudio(noteId: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        if (noteId.isBlank() || bytes.isEmpty()) return@withContext null
        val safeId = sanitizeId(noteId)
        if (!audioDir.exists()) audioDir.mkdirs()
        val target = File(audioDir, "$safeId.audio")
        runCatching {
            target.writeBytes(bytes)
            target
        }.getOrNull()
    }

    suspend fun getAudioFile(noteId: String): File? = withContext(Dispatchers.IO) {
        if (noteId.isBlank()) return@withContext null
        val file = File(audioDir, "${sanitizeId(noteId)}.audio")
        file.takeIf { it.exists() && it.length() > 0L }
    }

    suspend fun deleteAudio(noteId: String) = withContext(Dispatchers.IO) {
        if (noteId.isBlank()) return@withContext
        val file = File(audioDir, "${sanitizeId(noteId)}.audio")
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }

    private fun sanitizeId(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }
}
