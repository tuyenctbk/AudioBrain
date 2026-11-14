package com.example

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AudioBrainAiService
import com.example.data.export.TaskActionUtils
import com.example.data.model.ClaimVerdictStatus
import com.example.data.offline.PendingMemoKind
import com.example.data.offline.PendingMemoStore
import com.example.widget.WidgetSnapshot
import com.example.widget.WidgetSnapshotStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AudioBrainAndroidFeaturesTest {

    @Test
    fun parseVerdictsJson_parsesStructuredVerdictsAndNormalizesStatus() {
        val rawJson = """
            {
              "verdicts": [
                {
                  "claim": "EV credit limit is $7,500",
                  "status": "supported",
                  "explanation": "IRS Form 8936 confirms $7,500 max credit."
                },
                {
                  "claim": "Rule never expires",
                  "status": "disputed",
                  "explanation": "Income and MSRP caps apply."
                },
                {
                  "claim": "Lease loophole updated",
                  "status": "nuanced",
                  "explanation": "Commercial clean vehicle rules differ."
                }
              ]
            }
        """.trimIndent()

        val verdicts = AudioBrainAiService.parseVerdictsJson(rawJson)

        assertEquals(3, verdicts.size)
        assertEquals(ClaimVerdictStatus.SUPPORTED.value, verdicts[0].status)
        assertEquals(ClaimVerdictStatus.DISPUTED.value, verdicts[1].status)
        assertEquals(ClaimVerdictStatus.UPDATED.value, verdicts[2].status)
        assertEquals("IRS Form 8936 confirms \$7,500 max credit.", verdicts[0].explanation)
    }

    @Test
    fun parseFlashcardsJson_filtersBlankCardsAndPreservesValidOnes() {
        val rawJson = """
            {
              "flashcards": [
                { "question": "What is MSRP cap for SUVs?", "answer": "$80,000 under IRC 30D." },
                { "question": "   ", "answer": "Should be skipped" },
                { "question": "What is MAGI cap for joint filers?", "answer": "$300,000." }
              ]
            }
        """.trimIndent()

        val cards = AudioBrainAiService.parseFlashcardsJson(rawJson)

        assertEquals(2, cards.size)
        assertEquals("What is MSRP cap for SUVs?", cards[0].question)
        assertEquals("\$80,000 under IRC 30D.", cards[0].answer)
        assertEquals("What is MAGI cap for joint filers?", cards[1].question)
    }

    @Test
    fun createCalendarInsertIntent_buildsZeroPermissionCalendarInsertIntent() {
        val baseTime = 1_750_000_000_000L
        val intent = TaskActionUtils.createCalendarInsertIntent(
            noteTitle = "Board Sync",
            taskText = "**Send** final Q3 deck",
            noteSummary = "Reviewed launch timeline.",
            baseTimeMillis = baseTime
        )

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Send final Q3 deck", intent.getStringExtra(CalendarContract.Events.TITLE))
        val description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty()
        assertTrue(description.contains("Board Sync"))
        assertTrue(description.contains("Reviewed launch timeline."))
        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L)
        assertTrue(end > begin)
        assertEquals(30L * 60L * 1000L, end - begin)
    }

    @Test
    fun widgetSnapshotStore_saveAndLoad_roundTripsCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = WidgetSnapshot(
            openTasksCount = 4,
            totalNotesCount = 9,
            latestNoteId = "note-xyz",
            latestNoteTitle = "Architecture Sync",
            latestNoteCategory = "Meeting",
            isSignedIn = true
        )

        WidgetSnapshotStore.saveSnapshot(context, original)
        val loaded = WidgetSnapshotStore.loadSnapshot(context)

        assertEquals(original, loaded)
    }

    @Test
    fun pendingMemoStore_deferredVoiceDrafts_lifecycleAndAudioFileWorkCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PendingMemoStore.getInstance(context)
        val userId = "test-user-draft-${System.nanoTime()}"
        val sampleAudio = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        val savedDraft = store.enqueueAudio(
            userId = userId,
            bytes = sampleAudio,
            mimeType = "audio/mp4",
            durationSeconds = 18L,
            autoFactCheck = true,
            deferredForLater = true,
            label = "Walk note"
        )

        assertTrue(savedDraft.deferredForLater)
        assertEquals("Walk note", savedDraft.label)
        assertEquals(PendingMemoKind.AUDIO, savedDraft.kind)

        val audioFile = store.getAudioFile(savedDraft)
        assertNotNull(audioFile)
        assertTrue(audioFile!!.exists())
        assertEquals(sampleAudio.size, audioFile.readBytes().size)

        store.updateLabel(userId, savedDraft.id, "Evening commute brainstorm")
        val renamedList = store.getPending(userId)
        assertEquals(1, renamedList.size)
        assertEquals("Evening commute brainstorm", renamedList.first().label)

        store.markReadyForTranscription(userId, savedDraft.id)
        val readyList = store.getPending(userId)
        assertFalse(readyList.first().deferredForLater)

        store.remove(userId, savedDraft.id)
        assertTrue(store.getPending(userId).isEmpty())
        assertNull(store.getAudioFile(savedDraft))
    }
}
