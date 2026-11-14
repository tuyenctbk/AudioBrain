package com.example.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.R
import com.example.data.model.VoiceNote
import java.util.Calendar

data class OpenTaskEntry(
    val noteId: String,
    val noteTitle: String,
    val noteCategory: String,
    val taskIndex: Int,
    val taskText: String
)

object TaskActionUtils {

    fun collectOpenTasks(
        notes: List<VoiceNote>,
        untitledTitle: String = "Untitled Voice Note"
    ): List<OpenTaskEntry> {
        return notes.flatMap { note ->
            val completed = note.completedActionIndices.toSet()
            note.actionItems.mapIndexedNotNull { idx, task ->
                val trimmed = task.trim()
                if (trimmed.isBlank() || completed.contains(idx.toLong())) {
                    null
                } else {
                    OpenTaskEntry(
                        noteId = note.id,
                        noteTitle = note.title.ifBlank { untitledTitle },
                        noteCategory = note.category,
                        taskIndex = idx,
                        taskText = trimmed
                    )
                }
            }
        }
    }

    fun createCalendarInsertIntent(
        noteTitle: String,
        taskText: String,
        noteSummary: String = "",
        baseTimeMillis: Long = System.currentTimeMillis(),
        context: Context? = null
    ): Intent {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseTimeMillis
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        val endMillis = startMillis + 30L * 60L * 1000L

        val cleanedTask = NoteMarkdownExporter.stripMarkdown(taskText).trim()
        val fallbackTitle = context?.getString(R.string.fallback_voice_memo_title) ?: "Voice Memo"
        val resolvedTitle = noteTitle.ifBlank { fallbackTitle }
        val description = buildString {
            append(
                context?.getString(R.string.calendar_from_audiobrain_note, resolvedTitle)
                    ?: "From AudioBrain note: $resolvedTitle"
            )
            if (noteSummary.isNotBlank()) {
                append("\n\n")
                append(NoteMarkdownExporter.stripMarkdown(noteSummary).take(400))
            }
        }

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, cleanedTask.ifBlank { noteTitle })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
    }

    fun openCalendarForTask(
        context: Context,
        noteTitle: String,
        taskText: String,
        noteSummary: String = ""
    ): Boolean {
        val intent = createCalendarInsertIntent(
            noteTitle = noteTitle,
            taskText = taskText,
            noteSummary = noteSummary,
            context = context
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
