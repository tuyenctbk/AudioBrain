package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.NoteCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNotesRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveNote_authenticatedOwner_persistsAndStreams() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Commute Brainstorm",
                summary = "Checking EV tax credits.",
                transcript = "Check what the latest rules on EV tax credits are.",
                category = NoteCategory.COMMUTE,
                durationSeconds = 25L,
                actionItems = listOf("Check IRS EV rules"),
                claimsToVerify = listOf("Latest EV tax credit rules")
            )
        }
        assertTrue(createResult.isSuccess)
        val noteId = createResult.getOrThrow()
        assertTrue(noteId.isNotEmpty())

        val notes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes(uid).first { list -> list.any { it.id == noteId } }
        }
        val created = notes.first { it.id == noteId }
        assertEquals("Commute Brainstorm", created.title)
        assertEquals(uid, created.userId)
        assertNotNull(created.createdAt)
    }

    @Test
    fun updateFactCheckAndToggleAction_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "EV Memo",
                summary = "Summary",
                transcript = "Check EV tax credit rules.",
                category = NoteCategory.RESEARCH,
                durationSeconds = 15L,
                actionItems = listOf("Verify IRS link"),
                claimsToVerify = listOf("EV tax credits")
            ).getOrThrow()
        }

        val toggleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleActionItemAndPin(
                noteId = noteId,
                completedActionIndices = listOf(0L),
                pinned = true
            )
        }
        assertTrue(toggleResult.isSuccess)

        val factResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateFactCheck(
                noteId = noteId,
                factCheckSummary = "Up to $7,500 credit applies with MSRP & income caps.",
                factCheckStatus = FactCheckStatus.VERIFIED,
                searchQueries = listOf("IRS EV tax credit rules"),
                sources = listOf(FactSource("IRS Clean Vehicle Credit", "https://www.irs.gov")),
                claimsToVerify = listOf("EV tax credits")
            )
        }
        assertTrue(factResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getNoteById(noteId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals(true, fetched?.pinned)
        assertEquals(listOf(0L), fetched?.completedActionIndices)
        assertEquals(FactCheckStatus.VERIFIED.value, fetched?.factCheckStatus)
        assertEquals(1, fetched?.sources?.size)
    }

    @Test
    fun messagesAndCascadeDelete_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Chat Note",
                summary = "Summary",
                transcript = "Transcript",
                category = NoteCategory.JOURNAL,
                durationSeconds = 10L,
                actionItems = emptyList(),
                claimsToVerify = emptyList()
            ).getOrThrow()
        }

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(
                noteId = noteId,
                role = "user",
                text = "Summarize the key takeaway."
            )
        }
        assertTrue(msgResult.isSuccess)

        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(uid, noteId).first { it.isNotEmpty() }
        }
        assertEquals(1, msgs.size)
        assertEquals("Summarize the key takeaway.", msgs.first().text)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteNote(noteId)
        }
        assertTrue(deleteResult.isSuccess)

        val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getNoteById(noteId).getOrThrow()
        }
        assertNull(afterDelete)
    }

    @Test
    fun crossUserAccess_differentUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = VoiceNotesRepository(firestore, auth)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createNote(
                title = "Alice Private Note",
                summary = "Private",
                transcript = "Confidential notes",
                category = NoteCategory.MEETING,
                durationSeconds = 30L,
                actionItems = emptyList(),
                claimsToVerify = emptyList()
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var capturedError: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("notes").document(noteId)
                    .get().await()
            }
        } catch (e: Exception) {
            capturedError = e
        }
        val firestoreException = capturedError?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED for cross-user read", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = VoiceNotesRepository(firestore, auth)

        var capturedError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeNotes(aliceUid).first()
            }
        } catch (e: Exception) {
            capturedError = e
        }
        val firestoreException = capturedError?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $capturedError", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun updateTagsAndQueryByTag_authenticatedOwner_persistsAndFilters() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Sprint Planning",
                summary = "Q3 roadmap priorities.",
                transcript = "Discuss work milestones and product ideas.",
                category = NoteCategory.MEETING,
                durationSeconds = 40L,
                actionItems = listOf("Share roadmap doc"),
                claimsToVerify = emptyList(),
                tags = listOf("#Work", "Meeting")
            ).getOrThrow()
        }

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getNoteById(noteId).getOrThrow()
        }
        assertEquals(listOf("work", "meeting"), created?.tags)

        val tagUpdateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateNoteTags(noteId, listOf("#Ideas", "work", "Product Roadmap"))
        }
        assertTrue(tagUpdateResult.isSuccess)

        val taggedNotes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotesByTag(uid, "#ideas")
                .first { list -> list.any { it.id == noteId } }
        }
        assertTrue(taggedNotes.any { it.id == noteId })
        val updated = taggedNotes.first { it.id == noteId }
        assertEquals(listOf("ideas", "work", "product-roadmap"), updated.tags)
    }

    @Test
    fun updateVerdictsDecisionsAndFlashcards_authenticatedOwner_persistsCleanly() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Architecture Review",
                summary = "Agreed to adopt Glance widgets and TTS playback.",
                transcript = "[00:00] Speaker 1: Let's ship Glance widget.\n[00:14] Speaker 2: Agreed.",
                category = NoteCategory.MEETING,
                durationSeconds = 60L,
                actionItems = listOf("Build widget"),
                claimsToVerify = listOf("Glance requires minSdk 21"),
                decisions = listOf("Ship Glance widget for 1-tap capture")
            ).getOrThrow()
        }

        val factResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateFactCheck(
                noteId = noteId,
                factCheckSummary = "Glance works on Android minSdk 21+.",
                factCheckStatus = FactCheckStatus.VERIFIED,
                searchQueries = listOf("Jetpack Glance minSdk"),
                sources = listOf(FactSource("Android Developers", "https://developer.android.com")),
                claimsToVerify = listOf("Glance requires minSdk 21"),
                claimVerdicts = listOf(
                    com.example.data.model.ClaimVerdict(
                        claim = "Glance requires minSdk 21",
                        status = "supported",
                        explanation = "Jetpack Glance supports minSdk 21 and above."
                    )
                )
            )
        }
        assertTrue(factResult.isSuccess)

        val flashcardsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateFlashcards(
                noteId = noteId,
                flashcards = listOf(
                    com.example.data.model.FlashcardItem(
                        question = "What framework builds Compose-style home widgets?",
                        answer = "Jetpack Glance"
                    )
                )
            )
        }
        assertTrue(flashcardsResult.isSuccess)

        val loaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getNoteById(noteId).getOrThrow()
        }
        assertNotNull(loaded)
        assertEquals(listOf("Ship Glance widget for 1-tap capture"), loaded?.decisions)
        assertEquals(1, loaded?.claimVerdicts?.size)
        assertEquals("supported", loaded?.claimVerdicts?.first()?.status)
        assertEquals(1, loaded?.flashcards?.size)
        assertEquals("Jetpack Glance", loaded?.flashcards?.first()?.answer)
    }

    @Test
    fun observeAndUpdateNoteStructurePreference_authenticatedOwner_persistsAcrossSessions() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VoiceNotesRepository(firestore, auth)

        val defaultPrefs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserPreferences(uid).first()
        }
        assertEquals(com.example.data.model.NoteStructureMode.DETAILED, defaultPrefs.structureMode)

        val setBriefResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateNoteStructurePreference(com.example.data.model.NoteStructureMode.BRIEF)
        }
        assertTrue(setBriefResult.isSuccess)

        val briefPrefs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserPreferences(uid).first {
                it.structureMode == com.example.data.model.NoteStructureMode.BRIEF
            }
        }
        assertEquals(com.example.data.model.NoteStructureMode.BRIEF, briefPrefs.structureMode)

        val setDetailedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateNoteStructurePreference(com.example.data.model.NoteStructureMode.DETAILED)
        }
        assertTrue(setDetailedResult.isSuccess)

        val detailedPrefs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserPreferences(uid).first {
                it.structureMode == com.example.data.model.NoteStructureMode.DETAILED
            }
        }
        assertEquals(com.example.data.model.NoteStructureMode.DETAILED, detailedPrefs.structureMode)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_audiobrain@test.com"
        const val BOB_EMAIL = "bob_audiobrain@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
