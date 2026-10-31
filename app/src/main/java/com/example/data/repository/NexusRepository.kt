package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.NexusSession
import com.example.data.model.NexusUserSettings
import com.example.data.model.TranscriptEntry
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NexusRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // 1. User Settings & Input History Real-Time Observation
    fun observeUserSettings(): Flow<NexusUserSettings?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            NexusUserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    suspend fun ensureOrSaveUserSettings(settings: NexusUserSettings): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "provider" to settings.provider,
                    "selectedModel" to settings.selectedModel,
                    "maxTokens" to settings.maxTokens,
                    "maxSteps" to settings.maxSteps,
                    "timeoutSec" to settings.timeoutSec,
                    "agentPersona" to settings.agentPersona,
                    "activeSessionId" to settings.activeSessionId,
                    "inputHistory" to settings.inputHistory.takeLast(100),
                    "displayName" to settings.displayName.take(80),
                    "avatarDataUri" to settings.avatarDataUri.take(350_000),
                    "fontSize" to settings.fontSize.ifBlank { "default" },
                    "responseStyle" to settings.responseStyle.ifBlank { "balanced" },
                    "personaPreset" to settings.personaPreset.ifBlank { "default" }.take(64),
                    "customPersonasJson" to settings.customPersonasJson.take(50_000),
                    "aboutUser" to settings.aboutUser.take(1500),
                    "responsePreference" to settings.responsePreference.take(1500),
                    "enableForNewChats" to settings.enableForNewChats,
                    "skillsJson" to settings.skillsJson.take(100_000),
                    "skillsTokenBudget" to settings.skillsTokenBudget.coerceIn(500L, 6000L),
                    "recentWorkspaces" to settings.recentWorkspaces.take(5),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "provider" to settings.provider,
                    "selectedModel" to settings.selectedModel,
                    "maxTokens" to settings.maxTokens,
                    "maxSteps" to settings.maxSteps,
                    "timeoutSec" to settings.timeoutSec,
                    "agentPersona" to settings.agentPersona,
                    "activeSessionId" to settings.activeSessionId,
                    "inputHistory" to settings.inputHistory.takeLast(100),
                    "displayName" to settings.displayName.take(80),
                    "avatarDataUri" to settings.avatarDataUri.take(350_000),
                    "fontSize" to settings.fontSize.ifBlank { "default" },
                    "responseStyle" to settings.responseStyle.ifBlank { "balanced" },
                    "personaPreset" to settings.personaPreset.ifBlank { "default" }.take(64),
                    "customPersonasJson" to settings.customPersonasJson.take(50_000),
                    "aboutUser" to settings.aboutUser.take(1500),
                    "responsePreference" to settings.responsePreference.take(1500),
                    "enableForNewChats" to settings.enableForNewChats,
                    "skillsJson" to settings.skillsJson.take(100_000),
                    "skillsTokenBudget" to settings.skillsTokenBudget.coerceIn(500L, 6000L),
                    "recentWorkspaces" to settings.recentWorkspaces.take(5),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // 2. Sessions Real-Time Observation & CRUD
    fun observeSessions(): Flow<List<NexusSession>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/sessions"
        emitAll(
            db.collection("users").document(uid).collection("sessions")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NexusSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createSession(
        title: String,
        cwd: String,
        provider: String = "Gemini",
        model: String = "gemini-3.8-flash",
        sessionId: String = "session_${UUID.randomUUID().toString().replace("-", "").take(16)}",
        projectSlug: String = "",
        storageLocation: String = "shared_storage"
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "New chat" }.take(120),
            "status" to "idle",
            "cwd" to cwd.ifBlank { "/" }.take(512),
            "commandsCount" to 0L,
            "redactionsCount" to 0L,
            "provider" to provider.take(40),
            "model" to model.take(120),
            "projectSlug" to projectSlug.take(64),
            "storageLocation" to storageLocation.ifBlank { "shared_storage" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getSessionById(ownerUid: String, sessionId: String): Result<NexusSession?> {
        val docRef = db.collection("users").document(ownerUid).collection("sessions").document(sessionId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    NexusSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun renameSession(sessionId: String, newTitle: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val cleanTitle = newTitle.trim().ifBlank { "Untitled chat" }.take(120)
        val updates = mapOf(
            "title" to cleanTitle,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionProjectBinding(
        sessionId: String,
        projectSlug: String,
        storageLocation: String,
        cwd: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val updates = mapOf(
            "projectSlug" to projectSlug.take(64),
            "storageLocation" to storageLocation.ifBlank { "shared_storage" },
            "cwd" to cwd.ifBlank { "/" }.take(512),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSessionWithBackup(sessionId: String): Result<Pair<NexusSession?, List<TranscriptEntry>>> {
        val uid = requireUserId()
        val sessionDoc = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val entriesCol = sessionDoc.collection("entries")
        return try {
            val sessionSnap = sessionDoc.get().await()
            val sessionObj = sessionSnap.toObject(
                NexusSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val entriesSnap = entriesCol.whereEqualTo("userId", uid).get().await()
            val entryList = entriesSnap.toObjects(
                TranscriptEntry::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            for (chunk in entriesSnap.documents.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            sessionDoc.delete().await()
            Result.success(sessionObj to entryList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionDoc.path)
            Result.failure(e)
        }
    }

    suspend fun restoreDeletedSession(session: NexusSession, entries: List<TranscriptEntry>): Result<Unit> {
        val uid = requireUserId()
        val sessionDoc = db.collection("users").document(uid).collection("sessions").document(session.id)
        return try {
            val sessionPayload = mapOf(
                "userId" to uid,
                "title" to session.title.ifBlank { "Restored chat" }.take(120),
                "status" to "idle",
                "cwd" to session.cwd.ifBlank { "/" }.take(512),
                "commandsCount" to session.commandsCount,
                "redactionsCount" to session.redactionsCount,
                "provider" to session.provider.take(40),
                "model" to session.model.take(120),
                "projectSlug" to session.projectSlug.take(64),
                "storageLocation" to session.storageLocation.ifBlank { "shared_storage" },
                "createdAt" to (session.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            sessionDoc.set(sessionPayload).await()
            restoreEntriesBatch(session.id, entries).getOrThrow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Chunked Firestore WriteBatch deletion (<= 450 operations per batch) for Edit & Resubmit truncation.
     */
    suspend fun truncateEntriesBatch(sessionId: String, entryIdsToDelete: List<String>): Result<Unit> {
        val cleanIds = entryIdsToDelete.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return Result.success(Unit)
        val uid = requireUserId()
        val entriesCol = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("entries")
        return try {
            for (chunk in cleanIds.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for (id in chunk) {
                    batch.delete(entriesCol.document(id))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, entriesCol.path)
            Result.failure(e)
        }
    }

    /**
     * Chunked Firestore WriteBatch restore (<= 450 operations per batch) when Undo is tapped within 10s.
     */
    suspend fun restoreEntriesBatch(sessionId: String, entries: List<TranscriptEntry>): Result<Unit> {
        if (entries.isEmpty()) return Result.success(Unit)
        val uid = requireUserId()
        val entriesCol = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("entries")
        return try {
            for (chunk in entries.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for (entry in chunk) {
                    val entryId = entry.id.ifBlank { "entry_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                    val entryRef = entriesCol.document(entryId)
                    val entryPayload = mapOf(
                        "userId" to uid,
                        "sessionId" to sessionId,
                        "type" to entry.type,
                        "text" to entry.text.take(20000),
                        "command" to entry.command.take(2000),
                        "reason" to entry.reason.take(1000),
                        "commandStatus" to entry.commandStatus,
                        "isRisky" to entry.isRisky,
                        "timeoutSec" to entry.timeoutSec.coerceIn(0L, 600L),
                        "cwd" to entry.cwd.take(512),
                        "exitCode" to entry.exitCode.coerceIn(-999L, 999L),
                        "output" to entry.output.take(20000),
                        "redactions" to entry.redactions.coerceAtLeast(0L),
                        "noticeKind" to entry.noticeKind.take(40),
                        "sequence" to entry.sequence.coerceAtLeast(0L),
                        "createdAt" to (entry.createdAt ?: FieldValue.serverTimestamp()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(entryRef, entryPayload)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, entriesCol.path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionStatusAndStats(
        sessionId: String,
        status: String,
        cwd: String,
        commandsCount: Long,
        redactionsCount: Long,
        provider: String,
        model: String,
        title: String? = null,
        projectSlug: String? = null,
        storageLocation: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "cwd" to cwd.ifBlank { "/" }.take(512),
            "commandsCount" to commandsCount,
            "redactionsCount" to redactionsCount,
            "provider" to provider.take(40),
            "model" to model.take(120),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!title.isNullOrBlank()) {
            updates["title"] = title.trim().take(120)
        }
        if (projectSlug != null) {
            updates["projectSlug"] = projectSlug.take(64)
        }
        if (!storageLocation.isNullOrBlank()) {
            updates["storageLocation"] = storageLocation
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // 3. Transcript Entries Real-Time Observation & CRUD
    fun observeEntries(sessionId: String): Flow<List<TranscriptEntry>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/sessions/$sessionId/entries"
        emitAll(
            db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .collection("entries")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TranscriptEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<TranscriptEntry> { it.sequence }
                            .thenBy { it.createdAt?.seconds ?: 0L }
                            .thenBy { it.createdAt?.nanoseconds ?: 0 }
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun appendEntry(
        sessionId: String,
        type: String,
        sequence: Long,
        text: String = "",
        command: String = "",
        reason: String = "",
        commandStatus: String = "",
        isRisky: Boolean = false,
        timeoutSec: Long = 10L,
        cwd: String = "",
        exitCode: Long = 0L,
        output: String = "",
        redactions: Long = 0L,
        noticeKind: String = "",
        entryId: String = "entry_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("entries").document(entryId)
        val payload = mapOf(
            "userId" to uid,
            "sessionId" to sessionId,
            "type" to type,
            "text" to text.take(20000),
            "command" to command.take(2000),
            "reason" to reason.take(1000),
            "commandStatus" to commandStatus,
            "isRisky" to isRisky,
            "timeoutSec" to timeoutSec.coerceIn(0L, 600L),
            "cwd" to cwd.take(512),
            "exitCode" to exitCode.coerceIn(-999L, 999L),
            "output" to output.take(20000),
            "redactions" to redactions.coerceAtLeast(0L),
            "noticeKind" to noticeKind.take(40),
            "sequence" to sequence.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(entryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateEntryTextOrStatus(
        sessionId: String,
        entryId: String,
        text: String? = null,
        commandStatus: String? = null,
        cwd: String? = null,
        exitCode: Long? = null,
        output: String? = null,
        redactions: Long? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("entries").document(entryId)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (text != null) updates["text"] = text.take(20000)
        if (commandStatus != null) updates["commandStatus"] = commandStatus
        if (cwd != null) updates["cwd"] = cwd.take(512)
        if (exitCode != null) updates["exitCode"] = exitCode.coerceIn(-999L, 999L)
        if (output != null) updates["output"] = output.take(20000)
        if (redactions != null) updates["redactions"] = redactions.coerceAtLeast(0L)

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearSessionEntries(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val entriesCol = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("entries")
        return try {
            val snap = entriesCol.whereEqualTo("userId", uid).get().await()
            for (chunk in snap.documents.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, entriesCol.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserSessions(): Result<Unit> {
        val uid = requireUserId()
        val sessionsCol = db.collection("users").document(uid).collection("sessions")
        return try {
            val sessionsSnap = sessionsCol.whereEqualTo("userId", uid).get().await()
            for (sessionDoc in sessionsSnap.documents) {
                val entriesSnap = sessionDoc.reference.collection("entries")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                for (chunk in entriesSnap.documents.chunked(MAX_BATCH_OPS)) {
                    val batch = db.batch()
                    for (entryDoc in chunk) {
                        batch.delete(entryDoc.reference)
                    }
                    batch.commit().await()
                }
                sessionDoc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionsCol.path)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccountAndCloudData(): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            deleteAllUserSessions()
            userDoc.delete().await()
            runCatching { auth.currentUser?.delete()?.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_BATCH_OPS = 450
    }
}
