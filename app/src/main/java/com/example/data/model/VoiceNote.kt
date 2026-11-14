package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

enum class NoteCategory(val label: String) {
    JOURNAL("Journal"),
    LECTURE("Lecture"),
    MEETING("Meeting"),
    RESEARCH("Research"),
    COMMUTE("Commute");

    companion object {
        fun fromLabel(label: String?): NoteCategory {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: JOURNAL
        }
    }
}

enum class FactCheckStatus(val value: String) {
    NONE("none"),
    VERIFIED("verified"),
    NEEDS_REVIEW("needs_review");

    companion object {
        fun fromValue(value: String?): FactCheckStatus {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

enum class ClaimVerdictStatus(val value: String, val displayLabel: String) {
    SUPPORTED("supported", "Confirmed"),
    UPDATED("updated", "Updated / Nuanced"),
    DISPUTED("disputed", "Disputed"),
    UNVERIFIED("unverified", "Needs Check");

    companion object {
        fun fromValue(value: String?): ClaimVerdictStatus {
            return when (value?.trim()?.lowercase(Locale.US)) {
                "supported", "confirmed", "accurate", "true" -> SUPPORTED
                "updated", "nuanced", "partially_true", "partial" -> UPDATED
                "disputed", "false", "outdated", "incorrect" -> DISPUTED
                else -> UNVERIFIED
            }
        }
    }
}

enum class NoteStructureMode(val value: String, val displayLabel: String) {
    BRIEF("brief", "Brief"),
    DETAILED("detailed", "Detailed");

    companion object {
        fun fromValue(value: String?): NoteStructureMode {
            return when (value?.trim()?.lowercase(Locale.US)) {
                "brief", "concise", "short", "compact" -> BRIEF
                else -> DETAILED
            }
        }
    }
}

enum class NoteTopicTone(val value: String, val displayLabel: String) {
    ACADEMIC("academic", "Academic"),
    TECHNICAL("technical", "Technical"),
    PROFESSIONAL("professional", "Professional"),
    CASUAL("casual", "Casual");

    companion object {
        fun fromValue(value: String?): NoteTopicTone {
            return when (value?.trim()?.lowercase(Locale.US)) {
                "academic", "scholarly", "lecture", "research" -> ACADEMIC
                "technical", "engineering", "code", "system" -> TECHNICAL
                "casual", "personal", "conversational", "reflective" -> CASUAL
                else -> PROFESSIONAL
            }
        }
    }
}

@IgnoreExtraProperties
data class UserPreferences(
    val userId: String = "",
    val noteStructure: String = NoteStructureMode.DETAILED.value,
    val contextualSummarization: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val structureMode: NoteStructureMode
        get() = NoteStructureMode.fromValue(noteStructure)
}

object NoteTagHelper {
    const val MAX_TAGS_PER_NOTE = 10
    const val MAX_TAG_LENGTH = 32

    val DEFAULT_SUGGESTIONS: List<String> = listOf(
        "work",
        "ideas",
        "meeting",
        "research",
        "study",
        "personal",
        "todo",
        "finance"
    )

    private val invalidCharsRegex = Regex("[^a-z0-9_-]+")
    private val whitespaceRegex = Regex("\\s+")
    private val multiDashRegex = Regex("-{2,}")

    fun normalizeTag(raw: String): String? {
        val cleaned = raw
            .trim()
            .trimStart('#')
            .lowercase(Locale.US)
            .replace(whitespaceRegex, "-")
            .replace(invalidCharsRegex, "")
            .replace(multiDashRegex, "-")
            .trim('-', '_')
            .take(MAX_TAG_LENGTH)
        return cleaned.takeIf { it.isNotBlank() }
    }

    fun normalizeTags(rawTags: Iterable<String>): List<String> {
        return rawTags
            .mapNotNull { normalizeTag(it) }
            .distinct()
            .take(MAX_TAGS_PER_NOTE)
    }

    fun parseTagInput(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val tokens = input
            .split(',', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return normalizeTags(tokens)
    }

    fun formatTag(tag: String): String {
        val normalized = normalizeTag(tag) ?: return ""
        return "#$normalized"
    }
}

object FactCheckFreshnessHelper {
    const val STALE_FACT_CHECK_DAYS = 30L
    private const val MILLIS_PER_DAY = 86_400_000L

    fun daysSinceLastUpdate(note: VoiceNote, nowMillis: Long = System.currentTimeMillis()): Long {
        val ts = note.updatedAt ?: note.createdAt ?: return 0L
        val elapsed = (nowMillis - ts.toDate().time).coerceAtLeast(0L)
        return elapsed / MILLIS_PER_DAY
    }

    fun isFactCheckStale(note: VoiceNote, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val hasExistingCheck = note.factCheckSummary.isNotBlank() ||
            note.claimVerdicts.isNotEmpty() ||
            note.factCheckStatus == FactCheckStatus.VERIFIED.value
        if (!hasExistingCheck) return false
        return daysSinceLastUpdate(note, nowMillis) >= STALE_FACT_CHECK_DAYS
    }
}

@IgnoreExtraProperties
data class FactSource(
    val title: String = "",
    val url: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title.take(300),
        "url" to url.take(2000)
    )
}

@IgnoreExtraProperties
data class ClaimVerdict(
    val claim: String = "",
    val status: String = ClaimVerdictStatus.UNVERIFIED.value,
    val explanation: String = ""
) {
    fun normalizedStatus(): ClaimVerdictStatus = ClaimVerdictStatus.fromValue(status)

    fun toMap(): Map<String, Any> = mapOf(
        "claim" to claim.trim().ifBlank { "Claim" }.take(300),
        "status" to ClaimVerdictStatus.fromValue(status).value,
        "explanation" to explanation.trim().take(1000)
    )
}

@IgnoreExtraProperties
data class FlashcardItem(
    val question: String = "",
    val answer: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "question" to question.trim().ifBlank { "Question" }.take(500),
        "answer" to answer.trim().ifBlank { "Answer" }.take(1000)
    )
}

@IgnoreExtraProperties
data class VoiceNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val summary: String = "",
    val transcript: String = "",
    val category: String = NoteCategory.JOURNAL.label,
    val detectedTone: String = NoteTopicTone.PROFESSIONAL.value,
    val durationSeconds: Long = 0L,
    val actionItems: List<String> = emptyList(),
    val completedActionIndices: List<Long> = emptyList(),
    val decisions: List<String> = emptyList(),
    val claimsToVerify: List<String> = emptyList(),
    val claimVerdicts: List<ClaimVerdict> = emptyList(),
    val factCheckSummary: String = "",
    val factCheckStatus: String = FactCheckStatus.NONE.value,
    val searchQueries: List<String> = emptyList(),
    val sources: List<FactSource> = emptyList(),
    val flashcards: List<FlashcardItem> = emptyList(),
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val topicTone: NoteTopicTone
        get() = NoteTopicTone.fromValue(detectedTone)
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val searchQueries: List<String> = emptyList(),
    val sources: List<FactSource> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
