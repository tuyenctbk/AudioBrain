package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.R
import com.example.audio.TranscriptCleaner
import com.example.data.model.ChatMessage
import com.example.data.model.ClaimVerdict
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.FlashcardItem
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.NoteTopicTone
import com.example.data.model.VoiceNote
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class MemoAnalysisResult(
    val title: String,
    val summary: String,
    val transcript: String,
    val category: NoteCategory,
    val detectedTone: NoteTopicTone = NoteTopicTone.PROFESSIONAL,
    val actionItems: List<String>,
    val decisions: List<String> = emptyList(),
    val claimsToVerify: List<String>,
    val tags: List<String> = emptyList()
)

data class FactCheckResult(
    val summary: String,
    val status: FactCheckStatus,
    val searchQueries: List<String>,
    val sources: List<FactSource>,
    val verdicts: List<ClaimVerdict> = emptyList()
)

data class ChatStreamUpdate(
    val text: String,
    val searchQueries: List<String>,
    val sources: List<FactSource>,
    val isComplete: Boolean
)

class AudioBrainAiService(
    appContext: Context? = null
) {

    private val context: Context? = appContext?.applicationContext

    private fun msg(resId: Int, fallback: String): String =
        context?.getString(resId) ?: fallback

    private val analysisSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short 3 to 7 word title for the voice note"),
                "summary" to Schema.string(description = "Concise 1 to 3 sentence summary of key points"),
                "transcript" to Schema.string(
                    description = "Noise-suppressed, filler-free verbatim transcription with [MM:SS] timestamps and speaker labels like 'Speaker 1:'. Omit background noise tags, coughs, stutters, and verbal fillers ('um', 'uh', 'like', 'you know')."
                ),
                "category" to Schema.enumeration(
                    values = listOf("Journal", "Lecture", "Meeting", "Research", "Commute"),
                    description = "Best matching category for the note"
                ),
                "detectedTone" to Schema.enumeration(
                    values = listOf("academic", "technical", "professional", "casual"),
                    description = "Detected topic domain and contextual tone of the memo ('academic', 'technical', 'professional', or 'casual')"
                ),
                "actionItems" to Schema.array(
                    items = Schema.string(),
                    description = "Clear, actionable tasks or next steps mentioned in the memo"
                ),
                "decisions" to Schema.array(
                    items = Schema.string(),
                    description = "Key decisions, agreements, or conclusions reached (especially in meetings or planning sessions)"
                ),
                "claimsToVerify" to Schema.array(
                    items = Schema.string(),
                    description = "Factual claims, current rules, stats, or explicit verification requests like 'Check latest EV tax credits' that should be cross-referenced with Google Search"
                ),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "2 to 4 short lowercase hashtag keywords without # for retrieval, e.g. 'work', 'ideas', 'meeting', 'research', 'study'"
                )
            )
        )
    }

    private val verdictsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "verdicts" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "claim" to Schema.string(description = "Concise claim statement being evaluated"),
                            "status" to Schema.enumeration(
                                values = listOf("supported", "updated", "disputed", "unverified"),
                                description = "Verdict category for this claim"
                            ),
                            "explanation" to Schema.string(description = "1 to 2 sentence explanation citing key facts or updates")
                        )
                    ),
                    description = "Per-claim structured fact-check verdicts"
                )
            )
        )
    }

    private val flashcardsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "flashcards" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "Clear self-test question or recall prompt"),
                            "answer" to Schema.string(description = "Concise, accurate answer grounded in the note and verified facts")
                        )
                    ),
                    description = "4 to 8 high-yield study flashcards"
                )
            )
        )
    }

    private val voiceQuestionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "question" to Schema.string(
                    description = "Clean, punctuated transcription of the user's spoken follow-up question with background noise and filler words ('um', 'uh', 'like', 'you know') removed"
                )
            )
        )
    }

    private val polishTranscriptSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "cleanedTranscript" to Schema.string(
                    description = "Concise, noise-suppressed, filler-free transcript that preserves all [MM:SS] timestamps, speaker labels ('Speaker 1:'), and exact factual meaning"
                )
            )
        )
    }

    private val contextualSummarySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "detectedTone" to Schema.enumeration(
                    values = listOf("academic", "technical", "professional", "casual"),
                    description = "Detected topic domain and tone of the note ('academic', 'technical', 'professional', or 'casual')"
                ),
                "summary" to Schema.string(
                    description = "Summary tailored to the detected topic tone and requested Brief/Detailed structure"
                )
            )
        )
    }

    private val transcriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            },
            systemInstruction = content {
                text(
                    "You are AudioBrain's acoustic noise-suppression and clean-transcription engine. " +
                        "Before transcribing or summarizing any audio or memo: " +
                        "(1) NOISE SUPPRESSION: Ignore background chatter, room hum, wind/traffic noise, mic bumps, coughs, throat clearing, and non-speech sounds. Never output tags like [noise] or [cough]. " +
                        "(2) FILLER-WORD & DISFLUENCY REMOVAL: Strip verbal fillers ('um', 'uh', 'erm', 'er', 'ah', 'hmm'), discourse crutches ('you know', 'I mean', 'like', 'basically', 'sort of', 'kind of' when used as filler), stutters, and false starts while preserving the speaker's exact meaning, numbers, technical terms, [MM:SS] timestamps, and speaker attributions."
                )
            }
        )
    }

    private val verdictsStructuringModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verdictsSchema
            }
        )
    }

    private val flashcardsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashcardsSchema
            }
        )
    }

    private val voiceQuestionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceQuestionSchema
            },
            systemInstruction = content {
                text(
                    "Ignore all background noise, mic clicks, coughs, and non-speech sounds. " +
                        "Remove verbal fillers ('um', 'uh', 'like', 'you know') and stutters to produce a clean, concise spoken question."
                )
            }
        )
    }

    private val transcriptPolishingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = polishTranscriptSchema
            },
            systemInstruction = content {
                text(
                    "You are an expert transcript editor. Remove all residual filler words ('um', 'uh', 'erm', 'like', 'you know', 'I mean', 'basically'), " +
                        "stutters, false starts, and non-speech noise annotations while strictly preserving every [MM:SS] timestamp, Speaker label, and factual statement."
                )
            }
        )
    }

    private val contextualSummaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = contextualSummarySchema
            }
        )
    }

    private val groundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content {
                text(
                    "You are AudioBrain, an accurate, concise research and fact-checking voice journal assistant. " +
                        "Always cross-reference factual claims, dates, policies, prices, and questions using live Google Search grounding when available. " +
                        "Present clear, structured findings with direct facts and helpful context."
                )
            }
        )
    }

    suspend fun analyzeAudioMemo(
        audioBytes: ByteArray,
        mimeType: String,
        structureMode: NoteStructureMode = NoteStructureMode.DETAILED,
        contextualSummarization: Boolean = true
    ): Result<MemoAnalysisResult> {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                transcriptionModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(buildAudioAnalysisPrompt(structureMode, contextualSummarization))
                    }
                )
            }
            val jsonText = response.text
                ?: return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_empty_analysis, "Gemini returned an empty analysis response."))
                )
            Result.success(parseAnalysisJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_audio_timeout, "Audio analysis timed out after 60 seconds. Please try again."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_transcribe_failed, "Failed to transcribe audio."))
            )
        }
    }

    suspend fun analyzeTextMemo(
        memoText: String,
        structureMode: NoteStructureMode = NoteStructureMode.DETAILED,
        contextualSummarization: Boolean = true
    ): Result<MemoAnalysisResult> {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                transcriptionModel.generateContent(
                    content {
                        text("${buildTextAnalysisPrompt(structureMode, contextualSummarization)}\n\nMemo text:\n$memoText")
                    }
                )
            }
            val jsonText = response.text
                ?: return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_empty_analysis, "Gemini returned an empty analysis response."))
                )
            Result.success(parseAnalysisJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Text memo analysis timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_memo_timeout, "Memo analysis timed out after 60 seconds."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text memo analysis failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_analyze_memo_failed, "Failed to analyze memo."))
            )
        }
    }

    suspend fun regenerateContextualSummary(
        note: VoiceNote,
        structureMode: NoteStructureMode,
        contextualSummarization: Boolean
    ): Result<Pair<String, NoteTopicTone>> {
        if (note.transcript.isBlank() && note.summary.isBlank()) {
            return Result.failure(
                IllegalStateException(msg(R.string.err_ai_no_content_summary, "Note has no content to summarize."))
            )
        }
        val prompt = buildString {
            appendLine(buildContextualToneInstruction(contextualSummarization))
            appendLine(buildStructureInstruction(structureMode))
            appendLine()
            appendLine("Note Title: ${note.title}")
            appendLine("Category: ${note.category}")
            if (note.decisions.isNotEmpty()) {
                appendLine("Key Decisions: ${note.decisions.joinToString("; ")}")
            }
            appendLine("Transcript:")
            appendLine(note.transcript.ifBlank { note.summary }.take(10_000))
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                contextualSummaryModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_empty_summary, "Gemini returned an empty summary."))
                )
            val json = JSONObject(jsonText)
            val newSummary = json.optString("summary", "").trim()
            val detectedTone = NoteTopicTone.fromValue(json.optString("detectedTone", note.detectedTone))
            if (newSummary.isBlank()) {
                Result.failure(IllegalStateException(msg(R.string.err_ai_empty_summary, "Gemini returned an empty summary.")))
            } else {
                Result.success(newSummary to detectedTone)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Contextual summary regeneration timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_summary_timeout, "Contextual summary timed out after 60 seconds."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Contextual summary regeneration failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_adapt_summary_failed, "Failed to adapt summary."))
            )
        }
    }

    /**
     * Performs fact-checking directly on the transcribed journal text using Firebase AI Logic
     * with Google Search Grounding to identify potential factual inaccuracies, outdated stats,
     * or disputed assertions, returning structured verdicts and verifiable web sources.
     */
    suspend fun factCheckTranscribedJournal(
        transcript: String,
        existingClaims: List<String> = emptyList()
    ): Result<FactCheckResult> {
        val cleanedTranscript = transcript.trim()
        if (cleanedTranscript.isBlank() && existingClaims.isEmpty()) {
            return Result.failure(
                IllegalStateException(msg(R.string.err_ai_transcript_empty, "Transcript is empty."))
            )
        }
        val prompt = buildTranscriptFactCheckPrompt(
            transcript = cleanedTranscript,
            existingClaims = existingClaims
        )
        return executeGroundedFactCheck(prompt = prompt, claims = existingClaims)
    }

    suspend fun factCheckClaims(
        transcript: String,
        claims: List<String>
    ): Result<FactCheckResult> {
        if (transcript.isBlank() && claims.isEmpty()) {
            return Result.failure(
                IllegalStateException(msg(R.string.err_ai_transcript_empty, "Transcript is empty."))
            )
        }
        val prompt = buildString {
            appendLine("Verify the following claims or search requests from a user's voice note using live Google Search:")
            if (claims.isNotEmpty()) {
                claims.forEachIndexed { idx, claim ->
                    appendLine("${idx + 1}. $claim")
                }
            } else {
                appendLine("Review the transcript below for any factual statements, current regulations, or statistics and verify them.")
            }
            appendLine()
            appendLine("Transcript context:")
            appendLine(transcript.take(6000))
            appendLine()
            appendLine(
                "For each claim, clearly state whether it is Confirmed/Supported, Updated/Nuanced, Disputed, or Needs Check, " +
                    "and summarize the exact current facts concisely."
            )
        }
        return executeGroundedFactCheck(prompt = prompt, claims = claims)
    }

    private suspend fun executeGroundedFactCheck(
        prompt: String,
        claims: List<String>
    ): Result<FactCheckResult> {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                groundedModel.generateContent(prompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isBlank()) {
                return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_fact_check_empty, "Fact-check returned an empty response."))
                )
            }
            val (queries, sources) = extractGrounding(response)
            val status = if (sources.isNotEmpty()) FactCheckStatus.VERIFIED else FactCheckStatus.NEEDS_REVIEW
            val structuredVerdicts = extractStructuredVerdicts(
                claims = claims,
                groundedFindings = text,
                hasSources = sources.isNotEmpty()
            )
            Result.success(
                FactCheckResult(
                    summary = text,
                    status = status,
                    searchQueries = queries,
                    sources = sources,
                    verdicts = structuredVerdicts
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google Search fact-check timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_fact_check_timeout, "Fact-checking timed out after 60 seconds."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Search fact-check failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_fact_check_failed, "Fact-checking failed."))
            )
        }
    }

    private suspend fun extractStructuredVerdicts(
        claims: List<String>,
        groundedFindings: String,
        hasSources: Boolean
    ): List<ClaimVerdict> {
        val structured = runCatching {
            withTimeout(15_000L) {
                val prompt = buildString {
                    appendLine("Convert the grounded fact-check findings below into structured per-claim verdicts.")
                    appendLine("Status choices:")
                    appendLine("- supported: claim is accurate as stated")
                    appendLine("- updated: claim is partially true, changed recently, or needs important nuance/caps")
                    appendLine("- disputed: claim is inaccurate or contradicted by evidence")
                    appendLine("- unverified: insufficient evidence to confirm")
                    if (claims.isNotEmpty()) {
                        appendLine("\nOriginal claims:")
                        claims.forEachIndexed { idx, claim -> appendLine("${idx + 1}. $claim") }
                    }
                    appendLine("\nGrounded findings:\n${groundedFindings.take(6000)}")
                }
                val jsonText = verdictsStructuringModel.generateContent(prompt).text
                jsonText?.let { parseVerdictsJson(it) }.orEmpty()
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Verdict structuring fallback triggered: ${err.message}")
        }.getOrDefault(emptyList())

        if (structured.isNotEmpty()) {
            return structured.take(15)
        }
        return buildFallbackVerdicts(claims, groundedFindings, hasSources)
    }

    suspend fun transcribeVoiceQuestion(
        audioBytes: ByteArray,
        mimeType: String
    ): Result<String> {
        return try {
            val response = withTimeout(30_000L) {
                voiceQuestionModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(
                            "Suppress any background noise and strip filler words ('um', 'uh', 'like', 'you know') or stutters. " +
                                "Transcribe this spoken follow-up question cleanly and concisely."
                        )
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_no_speech_question, "No speech detected in voice question."))
                )
            val rawQuestion = JSONObject(rawJson).optString("question", "").trim()
            val question = TranscriptCleaner.cleanFillerWordsAndNoiseMarkers(rawQuestion)
                .ifBlank { rawQuestion }
            if (question.isBlank()) {
                Result.failure(
                    IllegalStateException(msg(R.string.err_ai_understand_question, "Could not understand the voice question. Please try again."))
                )
            } else {
                Result.success(question)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice question transcription timed out", e)
            Result.failure(IllegalStateException(msg(R.string.err_ai_question_timeout, "Voice question timed out.")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice question transcription failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_question_failed, "Failed to transcribe voice question."))
            )
        }
    }

    suspend fun polishTranscript(rawTranscript: String): Result<String> {
        val deterministicCleaned = TranscriptCleaner.cleanFillerWordsAndNoiseMarkers(rawTranscript)
        if (deterministicCleaned.isBlank()) {
            return Result.failure(IllegalStateException(msg(R.string.err_ai_transcript_empty, "Transcript is empty.")))
        }
        return try {
            val prompt = buildString {
                appendLine("Remove any remaining filler words, verbal crutches, false starts, stutters, or noise tags from this transcript.")
                appendLine("Keep all [MM:SS] timestamps, speaker labels (e.g. 'Speaker 1:'), and factual details intact.")
                appendLine()
                appendLine("Transcript:")
                appendLine(deterministicCleaned.take(12_000))
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                transcriptPolishingModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.success(deterministicCleaned)
            val aiCleaned = JSONObject(jsonText).optString("cleanedTranscript", "").trim()
            val finalTranscript = TranscriptCleaner.cleanFillerWordsAndNoiseMarkers(aiCleaned)
                .ifBlank { deterministicCleaned }
            Result.success(finalTranscript)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Transcript polish timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_polish_timeout, "Transcript cleanup timed out after 60 seconds."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transcript polish failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_polish_failed, "Failed to clean transcript."))
            )
        }
    }

    suspend fun generateFlashcards(note: VoiceNote): Result<List<FlashcardItem>> {
        val contextPrompt = buildString {
            appendLine("Create 4 to 8 concise, high-yield study flashcards (question + answer) from this voice note.")
            appendLine("Focus on core concepts, key decisions, verified facts, and important details.")
            appendLine()
            appendLine("Title: ${note.title}")
            appendLine("Category: ${note.category}")
            if (note.summary.isNotBlank()) {
                appendLine("Summary: ${note.summary}")
            }
            if (note.decisions.isNotEmpty()) {
                appendLine("Decisions: ${note.decisions.joinToString("; ")}")
            }
            if (note.factCheckSummary.isNotBlank()) {
                appendLine("Verified Findings: ${note.factCheckSummary.take(2500)}")
            }
            if (note.transcript.isNotBlank()) {
                appendLine("Transcript: ${note.transcript.take(6000)}")
            }
        }

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                flashcardsModel.generateContent(contextPrompt)
            }
            val jsonText = response.text
                ?: return Result.failure(
                    IllegalStateException(msg(R.string.err_ai_flashcards_empty, "Gemini returned empty flashcards."))
                )
            val cards = parseFlashcardsJson(jsonText)
            if (cards.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        msg(
                            R.string.err_ai_flashcards_insufficient,
                            "Not enough factual or topical content in this note to build flashcards."
                        )
                    )
                )
            } else {
                Result.success(cards)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flashcard generation timed out", e)
            Result.failure(
                IllegalStateException(msg(R.string.err_ai_flashcards_timeout, "Flashcard generation timed out after 60 seconds."))
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: msg(R.string.err_ai_flashcards_failed, "Failed to generate flashcards."))
            )
        }
    }

    fun streamNoteChat(
        note: VoiceNote,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<ChatStreamUpdate> = flow {
        val contextSeed = buildString {
            appendLine("Voice Note Context:")
            appendLine("Title: ${note.title}")
            appendLine("Category: ${note.category}")
            appendLine("Summary: ${note.summary}")
            appendLine("Transcript: ${note.transcript}")
            if (note.decisions.isNotEmpty()) {
                appendLine("Key Decisions: ${note.decisions.joinToString("; ")}")
            }
            if (note.actionItems.isNotEmpty()) {
                appendLine("Action Items: ${note.actionItems.joinToString("; ")}")
            }
            if (note.factCheckSummary.isNotBlank()) {
                appendLine("Existing Fact-Check: ${note.factCheckSummary}")
            }
        }

        val chatHistory = buildList {
            add(content("user") { text("Use this voice note as context for our conversation:\n$contextSeed") })
            add(content("model") { text("Got it. I have the full transcript, decisions, action items, and fact-check notes ready. How can I help?") })
            history.takeLast(14).forEach { msg ->
                val role = if (msg.role == "user") "user" else "model"
                add(content(role) { text(msg.text) })
            }
        }

        val chat = groundedModel.startChat(history = chatHistory)
        val accumulatedText = StringBuilder()
        val collectedQueries = linkedSetOf<String>()
        val collectedSources = linkedMapOf<String, FactSource>()

        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(userPrompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                accumulatedText.append(chunkText)
                val (queries, sources) = extractGrounding(chunk)
                collectedQueries.addAll(queries)
                sources.forEach { src -> collectedSources[src.url] = src }

                emit(
                    ChatStreamUpdate(
                        text = accumulatedText.toString(),
                        searchQueries = collectedQueries.toList(),
                        sources = collectedSources.values.toList(),
                        isComplete = false
                    )
                )
            }
        }

        val finalText = accumulatedText.toString().trim()
        if (finalText.isBlank()) {
            throw IllegalStateException(msg(R.string.err_ai_chat_empty, "Gemini returned an empty chat response."))
        }
        emit(
            ChatStreamUpdate(
                text = finalText,
                searchQueries = collectedQueries.toList(),
                sources = collectedSources.values.toList(),
                isComplete = true
            )
        )
    }

    fun streamJournalDigest(
        notes: List<VoiceNote>,
        focusPrompt: String
    ): Flow<ChatStreamUpdate> = flow {
        val selectedNotes = notes.take(18)
        val notesContext = buildString {
            appendLine("User Voice Journal Notes (${selectedNotes.size} notes):")
            selectedNotes.forEachIndexed { idx, note ->
                appendLine("--- Note ${idx + 1}: ${note.title} (${note.category}) ---")
                if (note.summary.isNotBlank()) {
                    appendLine("Summary: ${note.summary}")
                }
                if (note.decisions.isNotEmpty()) {
                    appendLine("Decisions: ${note.decisions.joinToString("; ")}")
                }
                if (note.actionItems.isNotEmpty()) {
                    val itemsText = note.actionItems.mapIndexed { taskIdx, task ->
                        val done = note.completedActionIndices.contains(taskIdx.toLong())
                        "${if (done) "[Done]" else "[Open]"} $task"
                    }.joinToString("; ")
                    appendLine("Tasks: $itemsText")
                }
                if (note.factCheckSummary.isNotBlank()) {
                    appendLine("Fact-Check: ${note.factCheckSummary.take(600)}")
                } else if (note.claimsToVerify.isNotEmpty()) {
                    appendLine("Unchecked Claims: ${note.claimsToVerify.joinToString("; ")}")
                }
                appendLine()
            }
        }

        val prompt = buildString {
            appendLine("Synthesize a helpful, well-structured Markdown digest across the voice notes below.")
            appendLine("Focus request: $focusPrompt")
            appendLine("Highlight key themes, decisions, prioritized open action items, and verify or note any questionable facts with Google Search where helpful.")
            appendLine()
            append(notesContext)
        }

        val accumulatedText = StringBuilder()
        val collectedQueries = linkedSetOf<String>()
        val collectedSources = linkedMapOf<String, FactSource>()

        withTimeout(AI_TIMEOUT_MS) {
            groundedModel.generateContentStream(prompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                accumulatedText.append(chunkText)
                val (queries, sources) = extractGrounding(chunk)
                collectedQueries.addAll(queries)
                sources.forEach { src -> collectedSources[src.url] = src }

                emit(
                    ChatStreamUpdate(
                        text = accumulatedText.toString(),
                        searchQueries = collectedQueries.toList(),
                        sources = collectedSources.values.toList(),
                        isComplete = false
                    )
                )
            }
        }

        val finalText = accumulatedText.toString().trim()
        if (finalText.isBlank()) {
            throw IllegalStateException(msg(R.string.err_ai_digest_empty, "Gemini returned an empty digest."))
        }
        emit(
            ChatStreamUpdate(
                text = finalText,
                searchQueries = collectedQueries.toList(),
                sources = collectedSources.values.toList(),
                isComplete = true
            )
        )
    }

    private fun parseAnalysisJson(jsonString: String): MemoAnalysisResult {
        val json = JSONObject(jsonString)
        val fallbackTitle = msg(R.string.fallback_voice_memo_title, "Voice Memo")
        val title = json.optString("title", fallbackTitle).ifBlank { fallbackTitle }
        val summary = json.optString("summary", "")
        val rawTranscript = json.optString("transcript", "")
        val transcript = TranscriptCleaner.cleanFillerWordsAndNoiseMarkers(rawTranscript)
            .ifBlank { rawTranscript.trim() }
        val category = NoteCategory.fromLabel(json.optString("category", "Journal"))
        val detectedTone = NoteTopicTone.fromValue(json.optString("detectedTone", "professional"))

        val actionItemsArray = json.optJSONArray("actionItems")
        val actionItems = if (actionItemsArray != null) {
            (0 until actionItemsArray.length())
                .map { actionItemsArray.optString(it).trim() }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val decisionsArray = json.optJSONArray("decisions")
        val decisions = if (decisionsArray != null) {
            (0 until decisionsArray.length())
                .map { decisionsArray.optString(it).trim() }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val claimsArray = json.optJSONArray("claimsToVerify")
        val claims = if (claimsArray != null) {
            (0 until claimsArray.length())
                .map { claimsArray.optString(it).trim() }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val tagsArray = json.optJSONArray("tags")
        val tags = if (tagsArray != null) {
            NoteTagHelper.normalizeTags(
                (0 until tagsArray.length()).map { tagsArray.optString(it) }
            )
        } else {
            emptyList()
        }

        return MemoAnalysisResult(
            title = title,
            summary = summary,
            transcript = transcript,
            category = category,
            detectedTone = detectedTone,
            actionItems = actionItems,
            decisions = decisions,
            claimsToVerify = claims,
            tags = tags
        )
    }

    private fun extractGrounding(response: GenerateContentResponse): Pair<List<String>, List<FactSource>> {
        val metadata = response.candidates.firstOrNull()?.groundingMetadata
            ?: return Pair(emptyList(), emptyList())

        val queries = metadata.webSearchQueries.filter { it.isNotBlank() }.distinct()
        val sources = metadata.groundingChunks.mapNotNull { chunk ->
            val web = chunk.web
            val uri = web?.uri
            val title = web?.title
            if (!uri.isNullOrBlank()) {
                FactSource(
                    title = title?.takeIf { it.isNotBlank() } ?: uri,
                    url = uri
                )
            } else {
                null
            }
        }.distinctBy { it.url }

        return Pair(queries, sources)
    }

    companion object {
        private const val TAG = "AudioBrainAiService"
        private const val MODEL_FLASH = "gemini-3.8-flash"
        private const val MODEL_FLASH_LITE = "gemini-3.5-flash-lite"
        private const val AI_TIMEOUT_MS = 60_000L

        private fun buildStructureInstruction(structureMode: NoteStructureMode): String {
            return when (structureMode) {
                NoteStructureMode.BRIEF ->
                    "Structure Style: BRIEF. Generate a crisp 1-sentence executive summary (under 25 words), " +
                        "at most the top 1 to 3 highest-priority action items, and at most 1 to 2 key decisions."
                NoteStructureMode.DETAILED ->
                    "Structure Style: DETAILED. Generate a comprehensive 2 to 4 sentence executive summary covering key context and takeaways, " +
                        "a complete list of all actionable tasks mentioned, and all key decisions or agreements reached."
            }
        }

        private fun buildContextualToneInstruction(contextualSummarization: Boolean): String {
            return if (contextualSummarization) {
                "Contextual Summarization: ENABLED. Analyze the topic of the note ('academic', 'technical', 'professional', or 'casual') " +
                    "and set 'detectedTone' accordingly. Automatically adjust the tone, vocabulary, and framing of 'summary' to match: " +
                    "if 'academic', use scholarly synthesis focusing on core thesis, concepts, and evidence; " +
                    "if 'technical', use precise engineering/system terminology focusing on architecture, specs, and trade-offs; " +
                    "if 'professional', use crisp executive business phrasing focusing on outcomes, alignment, and deliverables; " +
                    "if 'casual', use warm, natural, conversational phrasing that preserves personal voice."
            } else {
                "Contextual Summarization: STANDARD. Classify 'detectedTone' ('academic', 'technical', 'professional', or 'casual'), " +
                    "and write 'summary' in a clear, neutral, general-purpose summary tone."
            }
        }

        fun buildAudioAnalysisPrompt(
            structureMode: NoteStructureMode,
            contextualSummarization: Boolean = true
        ): String {
            val structureInstruction = buildStructureInstruction(structureMode)
            val contextualInstruction = buildContextualToneInstruction(contextualSummarization)
            return "Execute a 2-step AI audio processing pipeline on this recording: " +
                "Step 1 (Noise Suppression & Filler Removal): Filter out non-speech background noise, ambient hum, coughs, throat clearing, and mic artifacts (never emit [noise] or [cough] tags), " +
                "and remove all verbal filler words ('um', 'uh', 'erm', 'ah', 'like', 'you know', 'I mean', 'basically'), false starts, and word stutters. " +
                "Step 2 (Clean Transcription & Structuring): Produce a clean, concise, well-punctuated transcript " +
                "grouped into readable paragraphs prefixed with [MM:SS] timestamps at natural speaker or topic transitions " +
                "(e.g., '[00:00] First point...\\n[00:22] Next topic...'). Whenever multiple speakers are heard (e.g., in meetings or conversations), " +
                "include speaker labels right after the timestamp (e.g., '[00:00] Speaker 1: ...\\n[00:18] Speaker 2: ...'). " +
                "$structureInstruction $contextualInstruction Also select the best category, 2-4 topical tags, " +
                "and any factual claims, statistics, laws, current policies, or explicit verification requests " +
                "(e.g., 'check the latest rules on...') that should be cross-referenced with live Google Search."
        }

        fun buildTextAnalysisPrompt(
            structureMode: NoteStructureMode,
            contextualSummarization: Boolean = true
        ): String {
            val structureInstruction = buildStructureInstruction(structureMode)
            val contextualInstruction = buildContextualToneInstruction(contextualSummarization)
            return "Analyze this voice journal note text. First remove any verbal filler words ('um', 'uh', 'like', 'you know', 'I mean'), " +
                "stutters, or noise markers so the transcript is clean and concise (while preserving any [MM:SS] timestamps and speaker names/turns). " +
                "Then create a concise title, best category, and 2-4 topical tags. $structureInstruction $contextualInstruction " +
                "Also extract any factual claims, current rules, or explicit questions to verify with live Google Search."
        }

        fun buildTranscriptFactCheckPrompt(
            transcript: String,
            existingClaims: List<String> = emptyList()
        ): String = buildString {
            appendLine("Audit the transcribed journal text below using live Google Search Grounding to identify potential factual inaccuracies, outdated statistics, misquoted rules/dates, or unverified assertions.")
            appendLine()
            if (existingClaims.isNotEmpty()) {
                appendLine("Known claims or questions to also cross-check:")
                existingClaims.forEachIndexed { idx, claim ->
                    appendLine("${idx + 1}. $claim")
                }
                appendLine()
            }
            appendLine("Transcribed Journal Text:")
            appendLine(transcript.take(8_000))
            appendLine()
            appendLine("Instructions:")
            appendLine("1. Scan every factual assertion (dates, figures, laws, prices, historical/scientific facts, names, policies) in the journal text.")
            appendLine("2. Cross-check each factual statement with live Google Search.")
            appendLine("3. Explicitly flag potential factual inaccuracies as Disputed (contradicted by reliable sources) or Updated/Nuanced (outdated or missing key conditions), and cite accurate verified facts for Confirmed/Supported statements.")
            appendLine("4. Cite exact dates/figures clearly so the user can correct any inaccuracies in their journal entry.")
        }

        fun parseVerdictsJson(jsonString: String): List<ClaimVerdict> {
            val json = JSONObject(jsonString)
            val arr = json.optJSONArray("verdicts") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val claim = obj.optString("claim", "").trim()
                    val status = ClaimVerdictStatus.fromValue(obj.optString("status", "")).value
                    val explanation = obj.optString("explanation", "").trim()
                    if (claim.isNotBlank()) {
                        add(
                            ClaimVerdict(
                                claim = claim.take(300),
                                status = status,
                                explanation = explanation.take(1000)
                            )
                        )
                    }
                }
            }.take(15)
        }

        fun buildFallbackVerdicts(
            claims: List<String>,
            groundedFindings: String,
            hasSources: Boolean
        ): List<ClaimVerdict> {
            if (claims.isEmpty()) return emptyList()
            val snippet = groundedFindings
                .replace(Regex("[#*_`]"), "")
                .trim()
                .take(260)
            val status = if (hasSources) {
                ClaimVerdictStatus.SUPPORTED.value
            } else {
                ClaimVerdictStatus.UNVERIFIED.value
            }
            return claims.take(6).map { claim ->
                ClaimVerdict(
                    claim = claim.take(300),
                    status = status,
                    explanation = snippet
                )
            }
        }

        fun parseFlashcardsJson(jsonString: String): List<FlashcardItem> {
            val json = JSONObject(jsonString)
            val arr = json.optJSONArray("flashcards") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val q = obj.optString("question", "").trim()
                    val a = obj.optString("answer", "").trim()
                    if (q.isNotBlank() && a.isNotBlank()) {
                        add(
                            FlashcardItem(
                                question = q.take(500),
                                answer = a.take(1000)
                            )
                        )
                    }
                }
            }.take(20)
        }
    }
}
