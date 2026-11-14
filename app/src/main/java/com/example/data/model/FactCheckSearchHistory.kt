package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

@IgnoreExtraProperties
data class FactCheckSearchItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val query: String = "",
    val topic: String = "",
    val verificationCount: Long = 1L,
    val lastStatus: String = ClaimVerdictStatus.UNVERIFIED.value,
    val lastExplanation: String = "",
    val sourceUrl: String = "",
    val lastNoteId: String = "",
    val lastNoteTitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun normalizedStatus(): ClaimVerdictStatus = ClaimVerdictStatus.fromValue(lastStatus)

    fun toMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("query", query.trim().take(400))
        if (topic.isNotBlank()) put("topic", topic.trim().take(200))
        put("verificationCount", verificationCount.coerceIn(1L, 10000L))
        put("lastStatus", ClaimVerdictStatus.fromValue(lastStatus).value)
        if (lastExplanation.isNotBlank()) put("lastExplanation", lastExplanation.trim().take(2000))
        if (sourceUrl.isNotBlank()) put("sourceUrl", sourceUrl.trim().take(2000))
        if (lastNoteId.isNotBlank()) put("lastNoteId", lastNoteId.trim().take(100))
        if (lastNoteTitle.isNotBlank()) put("lastNoteTitle", lastNoteTitle.trim().take(300))
    }
}

data class FactCheckSearchSuggestion(
    val query: String,
    val displayTopic: String,
    val verificationCount: Int,
    val status: ClaimVerdictStatus,
    val explanation: String,
    val sourceUrl: String?,
    val lastNoteId: String?,
    val lastNoteTitle: String?,
    val updatedAtMillis: Long,
    val matchRange: IntRange? = null
)

object FactCheckHistoryHelper {

    fun generateHistoryId(query: String): String {
        val normalized = query.trim().lowercase(Locale.US)
        val prefix = normalized
            .take(28)
            .replace(Regex("[^a-z0-9]"), "_")
            .trim('_')
            .ifBlank { "query" }
        val hashHex = (normalized.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        return "hist_${prefix}_$hashHex"
    }

    fun cleanTopicLabel(query: String): String {
        return query
            .replace(Regex("^(check|verify|what are the|is there|how does|what is the)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .take(60)
            .ifBlank { query.take(60) }
    }

    fun buildSuggestions(
        notes: List<VoiceNote>,
        recordedHistory: List<FactCheckSearchItem> = emptyList(),
        inputQuery: String = "",
        limit: Int = 10
    ): List<FactCheckSearchSuggestion> {
        val trimmedInput = inputQuery.trim()
        val lowerInput = trimmedInput.lowercase(Locale.US)

        // Aggregation bucket keyed by normalized lowercase query text
        class TopicBucket(
            val canonicalQuery: String,
            var count: Int = 0,
            var status: ClaimVerdictStatus = ClaimVerdictStatus.UNVERIFIED,
            var explanation: String = "",
            var sourceUrl: String? = null,
            var lastNoteId: String? = null,
            var lastNoteTitle: String? = null,
            var latestTimestampMillis: Long = 0L
        )

        val buckets = linkedMapOf<String, TopicBucket>()

        // 1. Ingest recorded history first
        for (item in recordedHistory) {
            val key = item.query.trim().lowercase(Locale.US)
            if (key.isBlank()) continue
            val itemTime = (item.updatedAt ?: item.createdAt)?.toDate()?.time ?: 0L
            val b = buckets.getOrPut(key) {
                TopicBucket(
                    canonicalQuery = item.query.trim(),
                    count = item.verificationCount.toInt().coerceAtLeast(1),
                    status = item.normalizedStatus(),
                    explanation = item.lastExplanation,
                    sourceUrl = item.sourceUrl.takeIf { it.isNotBlank() },
                    lastNoteId = item.lastNoteId.takeIf { it.isNotBlank() },
                    lastNoteTitle = item.lastNoteTitle.takeIf { it.isNotBlank() },
                    latestTimestampMillis = itemTime
                )
            }
            if (item.verificationCount > b.count) {
                b.count = item.verificationCount.toInt()
            }
            if (itemTime >= b.latestTimestampMillis) {
                b.latestTimestampMillis = itemTime
                b.status = item.normalizedStatus()
                if (item.lastExplanation.isNotBlank()) b.explanation = item.lastExplanation
                if (item.sourceUrl.isNotBlank()) b.sourceUrl = item.sourceUrl
                if (item.lastNoteId.isNotBlank()) b.lastNoteId = item.lastNoteId
                if (item.lastNoteTitle.isNotBlank()) b.lastNoteTitle = item.lastNoteTitle
            }
        }

        // 2. Ingest past verified queries and claims from all notes
        for (note in notes) {
            val noteTime = (note.updatedAt ?: note.createdAt)?.toDate()?.time ?: 0L
            val primarySource = note.sources.firstOrNull()?.url

            // Claims with verdicts
            for (verdict in note.claimVerdicts) {
                val q = verdict.claim.trim()
                val key = q.lowercase(Locale.US)
                if (key.isBlank()) continue
                val b = buckets.getOrPut(key) {
                    TopicBucket(
                        canonicalQuery = q,
                        count = 0,
                        status = verdict.normalizedStatus(),
                        explanation = verdict.explanation,
                        sourceUrl = primarySource,
                        lastNoteId = note.id,
                        lastNoteTitle = note.title,
                        latestTimestampMillis = noteTime
                    )
                }
                b.count += 1
                if (noteTime >= b.latestTimestampMillis) {
                    b.latestTimestampMillis = noteTime
                    b.status = verdict.normalizedStatus()
                    if (verdict.explanation.isNotBlank()) b.explanation = verdict.explanation
                    if (b.sourceUrl == null) b.sourceUrl = primarySource
                    b.lastNoteId = note.id
                    b.lastNoteTitle = note.title
                }
            }

            // Explicit searchQueries
            for (query in note.searchQueries) {
                val q = query.trim()
                val key = q.lowercase(Locale.US)
                if (key.isBlank()) continue
                val b = buckets.getOrPut(key) {
                    TopicBucket(
                        canonicalQuery = q,
                        count = 0,
                        status = if (note.sources.isNotEmpty()) ClaimVerdictStatus.SUPPORTED else ClaimVerdictStatus.UNVERIFIED,
                        explanation = note.factCheckSummary.take(200),
                        sourceUrl = primarySource,
                        lastNoteId = note.id,
                        lastNoteTitle = note.title,
                        latestTimestampMillis = noteTime
                    )
                }
                b.count += 1
                if (noteTime >= b.latestTimestampMillis) {
                    b.latestTimestampMillis = noteTime
                    if (b.sourceUrl == null) b.sourceUrl = primarySource
                    b.lastNoteId = note.id
                    b.lastNoteTitle = note.title
                }
            }

            // Raw claimsToVerify if not yet in buckets
            for (claim in note.claimsToVerify) {
                val q = claim.trim()
                val key = q.lowercase(Locale.US)
                if (key.isBlank()) continue
                val b = buckets.getOrPut(key) {
                    TopicBucket(
                        canonicalQuery = q,
                        count = 0,
                        status = ClaimVerdictStatus.UNVERIFIED,
                        explanation = note.factCheckSummary.take(200),
                        sourceUrl = primarySource,
                        lastNoteId = note.id,
                        lastNoteTitle = note.title,
                        latestTimestampMillis = noteTime
                    )
                }
                b.count += 1
                if (noteTime >= b.latestTimestampMillis) {
                    b.latestTimestampMillis = noteTime
                    if (b.sourceUrl == null) b.sourceUrl = primarySource
                    b.lastNoteId = note.id
                    b.lastNoteTitle = note.title
                }
            }
        }

        // 3. Filter by search input
        val filtered = if (lowerInput.isBlank()) {
            buckets.values.toList()
        } else {
            buckets.values.filter { bucket ->
                bucket.canonicalQuery.lowercase(Locale.US).contains(lowerInput)
            }
        }

        // 4. Sort:
        // When input is present: prefix matches first, then frequency, then recency
        // When input is blank: frequency first, then recency
        val sorted = filtered.sortedWith { a, b ->
            if (lowerInput.isNotBlank()) {
                val aLower = a.canonicalQuery.lowercase(Locale.US)
                val bLower = b.canonicalQuery.lowercase(Locale.US)
                val aStarts = aLower.startsWith(lowerInput)
                val bStarts = bLower.startsWith(lowerInput)
                if (aStarts && !bStarts) return@sortedWith -1
                if (!aStarts && bStarts) return@sortedWith 1
            }
            val countCompare = b.count.compareTo(a.count)
            if (countCompare != 0) return@sortedWith countCompare
            b.latestTimestampMillis.compareTo(a.latestTimestampMillis)
        }

        return sorted.take(limit).map { b ->
            val matchRange = if (lowerInput.isNotBlank()) {
                val idx = b.canonicalQuery.lowercase(Locale.US).indexOf(lowerInput)
                if (idx >= 0) idx until (idx + lowerInput.length) else null
            } else null

            FactCheckSearchSuggestion(
                query = b.canonicalQuery,
                displayTopic = cleanTopicLabel(b.canonicalQuery),
                verificationCount = b.count.coerceAtLeast(1),
                status = b.status,
                explanation = b.explanation,
                sourceUrl = b.sourceUrl,
                lastNoteId = b.lastNoteId,
                lastNoteTitle = b.lastNoteTitle,
                updatedAtMillis = b.latestTimestampMillis,
                matchRange = matchRange
            )
        }
    }
}
