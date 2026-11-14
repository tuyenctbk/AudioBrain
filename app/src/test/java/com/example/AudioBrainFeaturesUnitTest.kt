package com.example

import com.example.audio.TranscriptCleaner
import com.example.data.ai.AudioBrainAiService
import com.example.data.export.NoteMarkdownExporter
import com.example.data.export.TaskActionUtils
import com.example.data.model.ClaimVerdict
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckFreshnessHelper
import com.example.data.model.FactCheckHistoryHelper
import com.example.data.model.FactCheckSearchItem
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.NoteCategory
import com.example.data.model.NoteStructureMode
import com.example.data.model.NoteTagHelper
import com.example.data.model.NoteTopicTone
import com.example.data.model.UserPreferences
import com.example.data.model.VoiceNote
import com.example.ui.detail.parseTranscriptSegments
import com.example.ui.journal.buildFactCheckDashboardItems
import com.example.widget.WidgetSnapshotStore
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBrainFeaturesUnitTest {

    @Test
    fun parseTranscriptSegments_extractsTimestampsAndPlainLines() {
        val raw = """
            [00:00] Opening thoughts on EV incentives.
            [01:15] Check IRS 2026 MSRP cap rules.
            Plain continuation paragraph without timestamp.
        """.trimIndent()

        val segments = parseTranscriptSegments(raw)

        assertEquals(3, segments.size)
        assertEquals(0L, segments[0].timestampSeconds)
        assertEquals("00:00", segments[0].timestampLabel)
        assertEquals("Opening thoughts on EV incentives.", segments[0].text)

        assertEquals(75L, segments[1].timestampSeconds)
        assertEquals("01:15", segments[1].timestampLabel)
        assertEquals("Check IRS 2026 MSRP cap rules.", segments[1].text)

        assertNull(segments[2].timestampSeconds)
        assertNull(segments[2].timestampLabel)
        assertEquals("Plain continuation paragraph without timestamp.", segments[2].text)
    }

    @Test
    fun markdownExporter_formatsTasksSourcesDecisionsAndTranscript() {
        val note = VoiceNote(
            id = "note123",
            userId = "user1",
            title = "EV Tax Credit Notes",
            summary = "Key takeaways from commute memo.",
            transcript = "[00:00] Check EV tax credit rules.",
            category = NoteCategory.RESEARCH.label,
            durationSeconds = 95L,
            decisions = listOf("Prioritize lease over purchase for 2026"),
            actionItems = listOf("Email CPA", "Compare lease terms"),
            completedActionIndices = listOf(0L),
            factCheckSummary = "Verified credit caps apply.",
            factCheckStatus = FactCheckStatus.VERIFIED.value,
            sources = listOf(FactSource(title = "IRS Clean Vehicle Credits", url = "https://irs.gov/ev"))
        )

        val md = NoteMarkdownExporter.toMarkdown(note)

        assertTrue(md.contains("# EV Tax Credit Notes"))
        assertTrue(md.contains("Category: Research"))
        assertTrue(md.contains("01:35"))
        assertTrue(md.contains("## Key Decisions\n- Prioritize lease over purchase for 2026"))
        assertTrue(md.contains("- [x] Email CPA"))
        assertTrue(md.contains("- [ ] Compare lease terms"))
        assertTrue(md.contains("1. [IRS Clean Vehicle Credits](https://irs.gov/ev)"))
        assertTrue(md.contains("[00:00] Check EV tax credit rules."))
    }

    @Test
    fun plainTextExporter_formatsStructuredSectionsWithoutMarkdownSyntax() {
        val note = VoiceNote(
            id = "note456",
            userId = "user1",
            title = "EV Tax Credit Notes",
            summary = "**Key takeaways** from [IRS guidance](https://irs.gov/ev).",
            transcript = "[00:00] Check EV tax credit rules.",
            category = NoteCategory.RESEARCH.label,
            durationSeconds = 95L,
            decisions = listOf("**Freeze** price cap at $55k"),
            actionItems = listOf("**Email** CPA", "Compare lease terms"),
            completedActionIndices = listOf(0L),
            factCheckSummary = "## Verified\nCredit caps apply per *current* rules.",
            factCheckStatus = FactCheckStatus.VERIFIED.value,
            sources = listOf(FactSource(title = "IRS Clean Vehicle Credits", url = "https://irs.gov/ev")),
            tags = listOf("work", "ev-credits")
        )

        val plain = NoteMarkdownExporter.toPlainText(note)

        assertTrue(plain.startsWith("EV Tax Credit Notes\nCategory: Research • Duration: 01:35 • Updated:"))
        assertTrue(plain.contains("Tags: #work #ev-credits"))
        assertTrue(plain.contains("SUMMARY\nKey takeaways from IRS guidance (https://irs.gov/ev)."))
        assertTrue(plain.contains("KEY DECISIONS\n- Freeze price cap at $55k"))
        assertTrue(plain.contains("ACTION ITEMS\n- [x] Email CPA\n- [ ] Compare lease terms"))
        assertTrue(plain.contains("FACT-CHECK VERIFICATION\nVerified\nCredit caps apply per current rules."))
        assertTrue(plain.contains("Sources:\n1. IRS Clean Vehicle Credits - https://irs.gov/ev"))
        assertTrue(plain.contains("TRANSCRIPT\n[00:00] Check EV tax credit rules."))

        assertFalse(plain.contains("# EV Tax Credit Notes"))
        assertFalse(plain.contains("_Category:"))
        assertFalse(plain.contains("[IRS Clean Vehicle Credits](https://irs.gov/ev)"))
        assertFalse(plain.contains("**Key takeaways**"))
    }

    @Test
    fun noteTagHelper_normalizesAndParsesHashtagInputs() {
        assertEquals(
            listOf("work", "ideas", "product-meeting"),
            NoteTagHelper.parseTagInput("#Work, ideas  #Product-Meeting, work, !!!")
        )
        assertEquals("#research", NoteTagHelper.formatTag("##Research!!"))
        assertNull(NoteTagHelper.normalizeTag("   ###   "))
        val tooMany = (1..15).map { "tag$it" }
        assertEquals(NoteTagHelper.MAX_TAGS_PER_NOTE, NoteTagHelper.normalizeTags(tooMany).size)
    }

    @Test
    fun factCheckFreshnessHelper_detectsStaleVsFreshNotes() {
        val nowMillis = 1_750_000_000_000L
        val tenDaysAgo = Timestamp(Date(nowMillis - 10L * 86_400_000L))
        val thirtyFiveDaysAgo = Timestamp(Date(nowMillis - 35L * 86_400_000L))

        val freshNote = VoiceNote(
            id = "n1",
            factCheckStatus = FactCheckStatus.VERIFIED.value,
            factCheckSummary = "Confirmed",
            updatedAt = tenDaysAgo
        )
        val staleNote = VoiceNote(
            id = "n2",
            claimVerdicts = listOf(ClaimVerdict("CPI rose 2.4%", ClaimVerdictStatus.SUPPORTED.value, "BLS")),
            updatedAt = thirtyFiveDaysAgo
        )
        val uncheckedOldNote = VoiceNote(
            id = "n3",
            factCheckStatus = FactCheckStatus.NONE.value,
            updatedAt = thirtyFiveDaysAgo
        )

        assertFalse(FactCheckFreshnessHelper.isFactCheckStale(freshNote, nowMillis))
        assertTrue(FactCheckFreshnessHelper.isFactCheckStale(staleNote, nowMillis))
        assertFalse(FactCheckFreshnessHelper.isFactCheckStale(uncheckedOldNote, nowMillis))
    }

    @Test
    fun taskActionUtils_collectOpenTasks_excludesCompletedAndBlank() {
        val notes = listOf(
            VoiceNote(
                id = "n1",
                title = "Sprint Sync",
                category = NoteCategory.MEETING.label,
                actionItems = listOf("Ship beta", "Write changelog", "   "),
                completedActionIndices = listOf(0L)
            ),
            VoiceNote(
                id = "n2",
                title = "Research",
                category = NoteCategory.RESEARCH.label,
                actionItems = listOf("Verify 2026 numbers"),
                completedActionIndices = emptyList()
            )
        )

        val openTasks = TaskActionUtils.collectOpenTasks(notes)

        assertEquals(2, openTasks.size)
        assertEquals("n1", openTasks[0].noteId)
        assertEquals(1, openTasks[0].taskIndex)
        assertEquals("Write changelog", openTasks[0].taskText)
        assertEquals("n2", openTasks[1].noteId)
        assertEquals(0, openTasks[1].taskIndex)
        assertEquals("Verify 2026 numbers", openTasks[1].taskText)
    }

    @Test
    fun buildFallbackVerdicts_mapsClaimsAndStripMarkdown() {
        val verdicts = AudioBrainAiService.buildFallbackVerdicts(
            claims = listOf("Claim A", "Claim B"),
            groundedFindings = "## Summary\n**Confirmed** by official report.",
            hasSources = true
        )

        assertEquals(2, verdicts.size)
        assertEquals(ClaimVerdictStatus.SUPPORTED.value, verdicts[0].status)
        assertEquals("Claim A", verdicts[0].claim)
        assertFalse(verdicts[0].explanation.contains("**"))
    }

    @Test
    fun widgetSnapshotStore_buildSnapshot_summarizesOpenTasksAndFeaturedNote() {
        val notes = listOf(
            VoiceNote(
                id = "featured-1",
                title = "Q3 Product Review",
                category = NoteCategory.MEETING.label,
                actionItems = listOf("Task 1", "Task 2"),
                completedActionIndices = listOf(1L)
            ),
            VoiceNote(
                id = "second-2",
                title = "Commute Ideas",
                category = NoteCategory.COMMUTE.label,
                actionItems = listOf("Task 3"),
                completedActionIndices = emptyList()
            )
        )

        val snapshot = WidgetSnapshotStore.buildSnapshot(notes, isSignedIn = true)

        assertEquals(2, snapshot.openTasksCount)
        assertEquals(2, snapshot.totalNotesCount)
        assertEquals("featured-1", snapshot.latestNoteId)
        assertEquals("Q3 Product Review", snapshot.latestNoteTitle)
        assertEquals("Meeting", snapshot.latestNoteCategory)
        assertTrue(snapshot.isSignedIn)
    }

    @Test
    fun transcriptCleaner_removesNoiseMarkersFillersAndStuttersWhilePreservingTimestamps() {
        val noisyRaw = """
            [00:00] Speaker 1: Um, uh, [background noise] we we need to finalize the the Q3 roadmap by Friday.
            [00:18] Speaker 2: (clears throat) You know, I mean, like, the budget cap is $45,000, uh, per team.
            [noise] [cough]
        """.trimIndent()

        val cleaned = TranscriptCleaner.cleanFillerWordsAndNoiseMarkers(noisyRaw)

        assertEquals(
            "[00:00] Speaker 1: We need to finalize the Q3 roadmap by Friday.\n" +
                "[00:18] Speaker 2: The budget cap is $45,000, per team.",
            cleaned
        )
    }

    @Test
    fun audioAnalysisPrompt_includesNoiseSuppressionFillerRemovalAndStructureMode() {
        val briefPrompt = AudioBrainAiService.buildAudioAnalysisPrompt(NoteStructureMode.BRIEF)
        val detailedPrompt = AudioBrainAiService.buildAudioAnalysisPrompt(NoteStructureMode.DETAILED)

        assertTrue(briefPrompt.contains("Noise Suppression & Filler Removal"))
        assertTrue(briefPrompt.contains("Structure Style: BRIEF"))
        assertTrue(detailedPrompt.contains("Noise Suppression & Filler Removal"))
        assertTrue(detailedPrompt.contains("Structure Style: DETAILED"))
    }

    @Test
    fun noteTopicTone_fromValue_mapsLabelsAndDefaultsSafely() {
        assertEquals(NoteTopicTone.ACADEMIC, NoteTopicTone.fromValue("academic"))
        assertEquals(NoteTopicTone.ACADEMIC, NoteTopicTone.fromValue("Scholarly"))
        assertEquals(NoteTopicTone.TECHNICAL, NoteTopicTone.fromValue("technical"))
        assertEquals(NoteTopicTone.CASUAL, NoteTopicTone.fromValue("casual"))
        assertEquals(NoteTopicTone.PROFESSIONAL, NoteTopicTone.fromValue("professional"))
        assertEquals(NoteTopicTone.PROFESSIONAL, NoteTopicTone.fromValue("unknown_random_tone"))
        assertEquals(NoteTopicTone.PROFESSIONAL, NoteTopicTone.fromValue(null))
    }

    @Test
    fun analysisPrompts_reflectContextualSummarizationInstructions() {
        val promptWithTone = AudioBrainAiService.buildAudioAnalysisPrompt(
            structureMode = NoteStructureMode.DETAILED,
            contextualSummarization = true
        )
        val promptWithoutTone = AudioBrainAiService.buildAudioAnalysisPrompt(
            structureMode = NoteStructureMode.BRIEF,
            contextualSummarization = false
        )

        assertTrue(promptWithTone.contains("Contextual Summarization: ENABLED"))
        assertTrue(promptWithTone.contains("if 'academic'"))
        assertTrue(promptWithTone.contains("if 'technical'"))
        assertTrue(promptWithTone.contains("if 'casual'"))

        assertTrue(promptWithoutTone.contains("Contextual Summarization: STANDARD"))

        val textPrompt = AudioBrainAiService.buildTextAnalysisPrompt(
            structureMode = NoteStructureMode.BRIEF,
            contextualSummarization = true
        )
        assertTrue(textPrompt.contains("Contextual Summarization: ENABLED"))
        assertTrue(textPrompt.contains("Structure Style: BRIEF"))
    }

    @Test
    fun buildFactCheckDashboardItems_prioritizesDisputedAndUpdatedBeforeConfirmed() {
        val nowMillis = 1_750_000_000_000L
        val tenDaysAgo = Timestamp(Date(nowMillis - 10L * 86_400_000L))

        val noteConfirmed = VoiceNote(
            id = "note_c",
            title = "Confirmed Memo",
            claimVerdicts = listOf(
                ClaimVerdict("Earth orbits the Sun", ClaimVerdictStatus.SUPPORTED.value, "Confirmed Astronomy")
            ),
            updatedAt = tenDaysAgo
        )
        val noteDisputed = VoiceNote(
            id = "note_d",
            title = "Disputed Memo",
            claimVerdicts = listOf(
                ClaimVerdict("Mars is larger than Jupiter", ClaimVerdictStatus.DISPUTED.value, "Contradicted by planetary data")
            ),
            updatedAt = tenDaysAgo
        )
        val noteUpdated = VoiceNote(
            id = "note_u",
            title = "Updated Memo",
            claimVerdicts = listOf(
                ClaimVerdict("EV credit is $7500", ClaimVerdictStatus.UPDATED.value, "Subject to battery sourcing caps")
            ),
            updatedAt = tenDaysAgo
        )

        val items = buildFactCheckDashboardItems(
            notes = listOf(noteConfirmed, noteDisputed, noteUpdated),
            nowMillis = nowMillis
        )

        assertEquals(3, items.size)
        assertEquals("note_d", items[0].noteId)
        assertEquals(ClaimVerdictStatus.DISPUTED, items[0].status)
        assertEquals("note_u", items[1].noteId)
        assertEquals(ClaimVerdictStatus.UPDATED, items[1].status)
        assertEquals("note_c", items[2].noteId)
        assertEquals(ClaimVerdictStatus.SUPPORTED, items[2].status)
    }

    @Test
    fun userPreferences_defaultsContextualSummarizationToTrue() {
        val prefs = UserPreferences(userId = "user_abc")
        assertTrue(prefs.contextualSummarization)
        assertEquals(NoteStructureMode.DETAILED, prefs.structureMode)
    }

    @Test
    fun factCheckHistoryHelper_generateHistoryIdIsDeterministicAndSafe() {
        val id1 = FactCheckHistoryHelper.generateHistoryId("  What is the 2026 EV tax credit?  ")
        val id2 = FactCheckHistoryHelper.generateHistoryId("what is the 2026 ev tax credit?")
        assertEquals(id1, id2)
        assertTrue(id1.startsWith("hist_what_is_the_2026_ev_tax_cr"))
        assertTrue(id1.matches(Regex("hist_[a-z0-9_]+_[0-9a-f]+")))

        val blankId = FactCheckHistoryHelper.generateHistoryId("   !@#$%   ")
        assertTrue(blankId.startsWith("hist_query_"))
    }

    @Test
    fun factCheckHistoryHelper_cleanTopicLabelRemovesCommonQuestionPrefixes() {
        assertEquals("2026 EV credit caps", FactCheckHistoryHelper.cleanTopicLabel("check 2026 EV credit caps"))
        assertEquals("Solar battery subsidy rules", FactCheckHistoryHelper.cleanTopicLabel("verify solar battery subsidy rules"))
        assertEquals("Mortgage interest deduction rules", FactCheckHistoryHelper.cleanTopicLabel("what are the mortgage interest deduction rules"))
        assertEquals("General AI latency", FactCheckHistoryHelper.cleanTopicLabel("General AI latency"))
    }

    @Test
    fun factCheckHistoryHelper_buildSuggestionsPrioritizesPrefixAndSortsFrequency() {
        val nowMillis = 1_750_000_000_000L
        val historyItem1 = FactCheckSearchItem(
            id = "h1",
            userId = "user1",
            query = "EV tax credit 2026",
            verificationCount = 5L,
            lastStatus = ClaimVerdictStatus.SUPPORTED.value,
            lastExplanation = "Subject to MSRP caps",
            updatedAt = Timestamp(Date(nowMillis))
        )
        val historyItem2 = FactCheckSearchItem(
            id = "h2",
            userId = "user1",
            query = "Federal solar credit rules",
            verificationCount = 2L,
            lastStatus = ClaimVerdictStatus.UPDATED.value,
            lastExplanation = "30% rate through 2032",
            updatedAt = Timestamp(Date(nowMillis - 10000L))
        )

        val note = VoiceNote(
            id = "note_ev",
            title = "Tax Prep",
            claimVerdicts = listOf(
                ClaimVerdict(
                    claim = "EV tax credit leasing loophole",
                    status = ClaimVerdictStatus.SUPPORTED.value,
                    explanation = "Commercial clean vehicle exception applies"
                )
            ),
            searchQueries = listOf("EV battery sourcing guidelines"),
            updatedAt = Timestamp(Date(nowMillis))
        )

        // Query "ev" - should match EV tax credit 2026, EV tax credit leasing loophole, and EV battery sourcing guidelines
        val evSuggestions = FactCheckHistoryHelper.buildSuggestions(
            notes = listOf(note),
            recordedHistory = listOf(historyItem1, historyItem2),
            inputQuery = "ev",
            limit = 5
        )

        assertTrue(evSuggestions.isNotEmpty())
        assertEquals("EV tax credit 2026", evSuggestions[0].query)
        assertEquals(5, evSuggestions[0].verificationCount)
        assertEquals(ClaimVerdictStatus.SUPPORTED, evSuggestions[0].status)
        assertEquals(0..1, evSuggestions[0].matchRange)

        // Blank query should return items sorted by verification count descending
        val allSuggestions = FactCheckHistoryHelper.buildSuggestions(
            notes = listOf(note),
            recordedHistory = listOf(historyItem1, historyItem2),
            inputQuery = "",
            limit = 5
        )

        assertTrue(allSuggestions.size >= 2)
        assertEquals("EV tax credit 2026", allSuggestions[0].query) // count 5
        assertEquals("Federal solar credit rules", allSuggestions[1].query) // count 2
        assertNull(allSuggestions[0].matchRange)
    }

    @Test
    fun replaceTranscriptSegment_preservesTimestampsAndRemovesBlankPlainLines() {
        val original = """
            [00:00] Opening remarks on Q4 goals.
            Plain line without timestamp.
            [00:45] Final wrap up.
        """.trimIndent()

        val updatedSegment = com.example.ui.detail.replaceTranscriptSegment(
            transcript = original,
            segmentIndex = 0,
            newBody = "Refined opening remarks\non Q4 goals."
        )
        assertEquals(
            "[00:00] Refined opening remarks on Q4 goals.\n" +
                "Plain line without timestamp.\n" +
                "[00:45] Final wrap up.",
            updatedSegment
        )

        val removedPlain = com.example.ui.detail.replaceTranscriptSegment(
            transcript = updatedSegment,
            segmentIndex = 1,
            newBody = "   "
        )
        assertEquals(
            "[00:00] Refined opening remarks on Q4 goals.\n" +
                "[00:45] Final wrap up.",
            removedPlain
        )
    }

    @Test
    fun countTranscriptWords_ignoresTimestampTokens() {
        val transcript = """
            [00:00] First thought recorded today.
            [01:12] Second follow up note.
        """.trimIndent()

        assertEquals(8, com.example.ui.detail.countTranscriptWords(transcript))
        assertEquals(0, com.example.ui.detail.countTranscriptWords("   \n  "))
    }

    @Test
    fun transcriptReviewDraft_matchesTracksUnsavedChanges() {
        val note = VoiceNote(
            id = "n_review",
            title = "Morning Commute",
            summary = "Ideas for mobile app.",
            transcript = "[00:00] Ideas for mobile app.",
            category = NoteCategory.COMMUTE.label,
            tags = listOf("mobile", "ideas"),
            actionItems = listOf("Draft wireframe", "Review copy")
        )

        val draft = com.example.ui.review.TranscriptReviewDraft.fromNote(note)
        assertTrue(draft.matches(note))

        val trimmedDraft = draft.copy(
            title = "  Morning Commute  ",
            tags = listOf("#MOBILE", "ideas"),
            actionItems = listOf("Draft wireframe", "  ", "Review copy")
        )
        assertTrue(trimmedDraft.matches(note))

        val editedDraft = draft.copy(transcript = "[00:00] Updated ideas for mobile app.")
        assertFalse(editedDraft.matches(note))
    }

    @Test
    fun buildTranscriptFactCheckPrompt_includesJournalTextExistingClaimsAndGroundingDirectives() {
        val transcript = "[00:00] The federal EV tax credit cap is $80,000 for all sedans in 2026."
        val promptWithClaims = AudioBrainAiService.buildTranscriptFactCheckPrompt(
            transcript = transcript,
            existingClaims = listOf("IRS sedan MSRP limit is $80,000")
        )
        val promptNoClaims = AudioBrainAiService.buildTranscriptFactCheckPrompt(
            transcript = transcript
        )

        assertTrue(promptWithClaims.contains("Google Search Grounding"))
        assertTrue(promptWithClaims.contains("potential factual inaccuracies"))
        assertTrue(promptWithClaims.contains("IRS sedan MSRP limit is $80,000"))
        assertTrue(promptWithClaims.contains(transcript))
        assertTrue(promptWithClaims.contains("Disputed"))
        assertTrue(promptWithClaims.contains("Updated/Nuanced"))

        assertFalse(promptNoClaims.contains("Known claims or questions"))
        assertTrue(promptNoClaims.contains(transcript))
    }
}

