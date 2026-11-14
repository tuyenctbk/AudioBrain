package com.example.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.data.model.VoiceNote
import com.example.ui.journal.formatDuration
import com.example.ui.journal.formatTimestamp

object NoteMarkdownExporter {

    private val markdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val markdownHeadingRegex = Regex("""(?m)^#{1,6}\s+""")
    private val markdownBoldRegex = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val markdownItalicRegex = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)|(?<!_)_([^_\n]+)_(?!_)""")
    private val markdownCodeRegex = Regex("""`([^`]+)`""")

    fun toMarkdown(note: VoiceNote): String = buildString {
        appendLine("# ${note.title}")
        val metaParts = buildList {
            add("Category: ${note.category}")
            if (note.durationSeconds > 0L) add("Duration: ${formatDuration(note.durationSeconds)}")
            add("Updated: ${formatTimestamp(note.updatedAt ?: note.createdAt)}")
        }
        appendLine("_${metaParts.joinToString(" • ")}_")
        if (note.tags.isNotEmpty()) {
            appendLine("Tags: ${note.tags.joinToString(" ") { "#$it" }}")
        }
        appendLine()

        if (note.summary.isNotBlank()) {
            appendLine("## Executive Summary")
            appendLine(note.summary.trim())
            appendLine()
        }

        if (note.decisions.isNotEmpty()) {
            appendLine("## Key Decisions")
            note.decisions.forEach { decision ->
                appendLine("- $decision")
            }
            appendLine()
        }

        if (note.actionItems.isNotEmpty()) {
            appendLine("## Action Items")
            note.actionItems.forEachIndexed { idx, task ->
                val done = note.completedActionIndices.contains(idx.toLong())
                appendLine("- [${if (done) "x" else " "}] $task")
            }
            appendLine()
        }

        if (note.factCheckSummary.isNotBlank() || note.sources.isNotEmpty()) {
            appendLine("## Fact-Check Verification")
            if (note.factCheckSummary.isNotBlank()) {
                appendLine(note.factCheckSummary.trim())
                appendLine()
            }
            if (note.sources.isNotEmpty()) {
                appendLine("### Sources")
                note.sources.forEachIndexed { idx, src ->
                    appendLine("${idx + 1}. [${src.title}](${src.url})")
                }
                appendLine()
            }
        }

        if (note.transcript.isNotBlank()) {
            appendLine("## Transcript")
            appendLine(note.transcript.trim())
        }
    }.trim()

    fun toPlainText(note: VoiceNote, context: Context? = null): String = buildString {
        val fallbackUntitled = context?.getString(R.string.fallback_untitled_voice_note) ?: "Untitled Voice Note"
        val titleText = note.title.trim().ifBlank { fallbackUntitled }
        appendLine(titleText)

        val formattedTime = formatTimestamp(
            timestamp = note.updatedAt ?: note.createdAt,
            fallbackJustNow = context?.getString(R.string.timestamp_just_now) ?: "Just now"
        )
        val metaParts = buildList {
            if (note.category.isNotBlank()) {
                add(context?.getString(R.string.export_category_prefix, note.category) ?: "Category: ${note.category}")
            }
            if (note.durationSeconds > 0L) {
                val dur = formatDuration(note.durationSeconds)
                add(context?.getString(R.string.export_duration_prefix, dur) ?: "Duration: $dur")
            }
            add(context?.getString(R.string.export_updated_prefix, formattedTime) ?: "Updated: $formattedTime")
        }
        appendLine(metaParts.joinToString(" • "))
        if (note.tags.isNotEmpty()) {
            val joinedTags = note.tags.joinToString(" ") { "#$it" }
            appendLine(context?.getString(R.string.export_tags_prefix, joinedTags) ?: "Tags: $joinedTags")
        }
        appendLine()

        if (note.summary.isNotBlank()) {
            appendLine(context?.getString(R.string.export_section_summary) ?: "SUMMARY")
            appendLine(stripMarkdown(note.summary))
            appendLine()
        }

        if (note.decisions.isNotEmpty()) {
            appendLine(context?.getString(R.string.export_section_decisions) ?: "KEY DECISIONS")
            note.decisions.forEach { decision ->
                appendLine("- ${stripMarkdown(decision)}")
            }
            appendLine()
        }

        if (note.actionItems.isNotEmpty()) {
            appendLine(context?.getString(R.string.export_section_actions) ?: "ACTION ITEMS")
            note.actionItems.forEachIndexed { idx, task ->
                val done = note.completedActionIndices.contains(idx.toLong())
                appendLine("- [${if (done) "x" else " "}] ${stripMarkdown(task)}")
            }
            appendLine()
        }

        if (note.factCheckSummary.isNotBlank() || note.sources.isNotEmpty()) {
            appendLine(context?.getString(R.string.export_section_factcheck) ?: "FACT-CHECK VERIFICATION")
            if (note.factCheckSummary.isNotBlank()) {
                appendLine(stripMarkdown(note.factCheckSummary))
                appendLine()
            }
            if (note.sources.isNotEmpty()) {
                appendLine(context?.getString(R.string.export_sources_heading) ?: "Sources:")
                note.sources.forEachIndexed { idx, src ->
                    val label = src.title.trim().ifBlank { src.url.trim() }
                    if (src.url.isNotBlank() && label != src.url.trim()) {
                        appendLine("${idx + 1}. $label - ${src.url.trim()}")
                    } else if (label.isNotBlank()) {
                        appendLine("${idx + 1}. $label")
                    }
                }
                appendLine()
            }
        }

        if (note.transcript.isNotBlank()) {
            appendLine(context?.getString(R.string.export_section_transcript) ?: "TRANSCRIPT")
            appendLine(note.transcript.trim())
        }
    }.trim()

    fun shareNoteAsPlainText(context: Context, note: VoiceNote): Boolean {
        val subject = note.title.trim().ifBlank { context.getString(R.string.fallback_voice_note_title) }
        return shareText(
            context = context,
            subject = subject,
            content = toPlainText(note, context),
            chooserTitle = context.getString(R.string.share_voice_note_chooser)
        )
    }

    fun shareText(
        context: Context,
        subject: String,
        content: String,
        chooserTitle: String = context.getString(R.string.share_via_chooser)
    ): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun stripMarkdown(input: String): String {
        var cleaned = input.trim()
        cleaned = markdownHeadingRegex.replace(cleaned, "")
        cleaned = markdownLinkRegex.replace(cleaned) { match ->
            val label = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            if (url.isNotBlank()) "$label ($url)" else label
        }
        cleaned = markdownBoldRegex.replace(cleaned) { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        cleaned = markdownItalicRegex.replace(cleaned) { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        cleaned = markdownCodeRegex.replace(cleaned) { match ->
            match.groupValues[1]
        }
        return cleaned.trim()
    }
}
