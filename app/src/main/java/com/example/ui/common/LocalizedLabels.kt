package com.example.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTopicTone
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@StringRes
fun NoteCategory.labelRes(): Int = when (this) {
    NoteCategory.JOURNAL -> R.string.category_journal
    NoteCategory.LECTURE -> R.string.category_lecture
    NoteCategory.MEETING -> R.string.category_meeting
    NoteCategory.RESEARCH -> R.string.category_research
    NoteCategory.COMMUTE -> R.string.category_commute
}

@Composable
fun localizedCategoryLabel(rawCategory: String): String {
    val match = NoteCategory.entries.firstOrNull { it.label.equals(rawCategory, ignoreCase = true) }
    return if (match != null) stringResource(match.labelRes()) else rawCategory
}

@StringRes
fun NoteStructureMode.displayLabelRes(): Int = when (this) {
    NoteStructureMode.BRIEF -> R.string.structure_brief_label
    NoteStructureMode.DETAILED -> R.string.structure_detailed_label
}

@StringRes
fun NoteTopicTone.displayLabelRes(): Int = when (this) {
    NoteTopicTone.ACADEMIC -> R.string.tone_academic
    NoteTopicTone.TECHNICAL -> R.string.tone_technical
    NoteTopicTone.PROFESSIONAL -> R.string.tone_professional
    NoteTopicTone.CASUAL -> R.string.tone_casual
}

@StringRes
fun ClaimVerdictStatus.displayLabelRes(): Int = when (this) {
    ClaimVerdictStatus.SUPPORTED -> R.string.verdict_confirmed
    ClaimVerdictStatus.UPDATED -> R.string.verdict_updated_nuanced
    ClaimVerdictStatus.DISPUTED -> R.string.verdict_disputed
    ClaimVerdictStatus.UNVERIFIED -> R.string.verdict_needs_check
}

private val UI_TIMESTAMP_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    }
}

@Composable
fun localizedTimestamp(timestamp: Timestamp?): String {
    val justNow = stringResource(R.string.timestamp_just_now)
    val date = timestamp?.toDate() ?: return justNow
    return UI_TIMESTAMP_FORMATTER.get()?.format(date) ?: justNow
}

@Composable
fun localizedEpochTimestamp(epochMs: Long): String {
    val fallback = stringResource(R.string.draft_saved_label_fallback)
    return UI_TIMESTAMP_FORMATTER.get()?.format(Date(epochMs)) ?: fallback
}
