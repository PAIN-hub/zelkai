package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.NexusUserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class NexusRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSessionAndObserveRealtimeEntries_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NexusRepository(firestore)

        // 1. Save user settings
        val settingsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureOrSaveUserSettings(
                NexusUserSettings(
                    userId = uid,
                    provider = "gemini",
                    selectedModel = "gemini-3.8-flash",
                    inputHistory = listOf("uname -a", "df -h")
                )
            )
        }
        assertTrue(settingsResult.isSuccess)

        // 2. Create session with unique ID
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val createSessionResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSession(
                title = "Diagnostic Session",
                cwd = "/data/local/tmp",
                sessionId = sessionId
            )
        }
        assertTrue(createSessionResult.isSuccess)

        // 3. Append command entry and observe via real-time Flow
        val entryResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.appendEntry(
                sessionId = sessionId,
                type = "command",
                sequence = 1L,
                command = "df -h",
                reason = "Check disk usage",
                commandStatus = "pending",
                cwd = "/data/local/tmp"
            )
        }
        assertTrue(entryResult.isSuccess)
        val entryId = entryResult.getOrThrow()

        val emittedEntries = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEntries(sessionId).first { list -> list.any { it.id == entryId } }
        }
        assertTrue(emittedEntries.any { it.id == entryId && it.command == "df -h" })
    }

    @Test
    fun getSessionById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NexusRepository(firestore)
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSession(title = "Alice Private Session", cwd = "/", sessionId = sessionId).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NexusRepository(firestore)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getSessionById(aliceUid, sessionId).getOrThrow()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun observeSessions_unauthenticatedUser_fails() = runBlocking {
        auth.signOut()
        val repository = NexusRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSessions().first()
            }
            fail("Expected exception when observing sessions while signed out")
        } catch (e: Exception) {
            assertNotNull(e.message)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@nexus.test"
        const val BOB_EMAIL = "bob@nexus.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
