package com.example.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.data.export.TaskActionUtils
import com.example.data.model.VoiceNote

data class WidgetSnapshot(
    val openTasksCount: Int = 0,
    val totalNotesCount: Int = 0,
    val latestNoteId: String = "",
    val latestNoteTitle: String = "",
    val latestNoteCategory: String = "",
    val isSignedIn: Boolean = false
)

object WidgetSnapshotStore {
    private const val PREFS_NAME = "audiobrain_widget_snapshot"
    private const val KEY_OPEN_TASKS = "open_tasks_count"
    private const val KEY_TOTAL_NOTES = "total_notes_count"
    private const val KEY_LATEST_ID = "latest_note_id"
    private const val KEY_LATEST_TITLE = "latest_note_title"
    private const val KEY_LATEST_CATEGORY = "latest_note_category"
    private const val KEY_SIGNED_IN = "is_signed_in"

    fun buildSnapshot(notes: List<VoiceNote>, isSignedIn: Boolean): WidgetSnapshot {
        val openTasksCount = TaskActionUtils.collectOpenTasks(notes).size
        val featured = notes.firstOrNull()
        return WidgetSnapshot(
            openTasksCount = openTasksCount,
            totalNotesCount = notes.size,
            latestNoteId = featured?.id.orEmpty(),
            latestNoteTitle = featured?.title.orEmpty(),
            latestNoteCategory = featured?.category.orEmpty(),
            isSignedIn = isSignedIn
        )
    }

    fun saveSnapshot(context: Context, snapshot: WidgetSnapshot) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_OPEN_TASKS, snapshot.openTasksCount)
            .putInt(KEY_TOTAL_NOTES, snapshot.totalNotesCount)
            .putString(KEY_LATEST_ID, snapshot.latestNoteId)
            .putString(KEY_LATEST_TITLE, snapshot.latestNoteTitle)
            .putString(KEY_LATEST_CATEGORY, snapshot.latestNoteCategory)
            .putBoolean(KEY_SIGNED_IN, snapshot.isSignedIn)
            .apply()
    }

    fun loadSnapshot(context: Context): WidgetSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            openTasksCount = prefs.getInt(KEY_OPEN_TASKS, 0),
            totalNotesCount = prefs.getInt(KEY_TOTAL_NOTES, 0),
            latestNoteId = prefs.getString(KEY_LATEST_ID, "").orEmpty(),
            latestNoteTitle = prefs.getString(KEY_LATEST_TITLE, "").orEmpty(),
            latestNoteCategory = prefs.getString(KEY_LATEST_CATEGORY, "").orEmpty(),
            isSignedIn = prefs.getBoolean(KEY_SIGNED_IN, false)
        )
    }

    suspend fun saveAndRefresh(context: Context, notes: List<VoiceNote>, isSignedIn: Boolean) {
        val appContext = context.applicationContext
        val snapshot = buildSnapshot(notes, isSignedIn)
        saveSnapshot(appContext, snapshot)
        runCatching {
            AudioBrainWidget().updateAll(appContext)
        }.onFailure { err ->
            Log.d("WidgetSnapshotStore", "Widget refresh skipped: ${err.message}")
        }
    }
}
