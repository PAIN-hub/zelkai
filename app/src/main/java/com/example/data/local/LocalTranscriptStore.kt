package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.NexusSession
import com.example.data.model.TranscriptEntry
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-first JSON persistence store for chat sessions and transcript entries.
 * Ensures that:
 * 1. Completed AI responses and user prompts are immediately saved locally before any cloud sync.
 * 2. Reopening the app restores isolated chat sessions and their entries without mixing new chats into old ones.
 * 3. Offline or Firestore PERMISSION_DENIED states never lose or hide messages.
 */
class LocalTranscriptStore(context: Context? = null) {

    private val prefs: SharedPreferences? = runCatching {
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private val memoryFallback = ConcurrentHashMap<String, String>()

    private fun readRaw(key: String): String? {
        val p = prefs
        return if (p != null) {
            p.getString(key, null)
        } else {
            memoryFallback[key]
        }
    }

    private fun writeRaw(key: String, value: String) {
        val p = prefs
        if (p != null) {
            p.edit().putString(key, value).apply()
        } else {
            memoryFallback[key] = value
        }
    }

    private fun removeRaw(key: String) {
        val p = prefs
        if (p != null) {
            p.edit().remove(key).apply()
        } else {
            memoryFallback.remove(key)
        }
    }

    fun loadActiveSessionId(userId: String): String? {
        val cleanUser = userId.ifBlank { "guest" }
        return readRaw("active_session_$cleanUser")?.takeIf { it.isNotBlank() }
    }

    fun saveActiveSessionId(userId: String, sessionId: String) {
        if (sessionId.isBlank()) return
        val cleanUser = userId.ifBlank { "guest" }
        writeRaw("active_session_$cleanUser", sessionId)
    }

    fun loadSessions(userId: String): List<NexusSession> {
        val cleanUser = userId.ifBlank { "guest" }
        val raw = readRaw("sessions_$cleanUser") ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val result = ArrayList<NexusSession>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(deserializeSession(obj))
            }
            result
        }.getOrDefault(emptyList())
    }

    fun saveSessions(userId: String, sessions: List<NexusSession>) {
        val cleanUser = userId.ifBlank { "guest" }
        runCatching {
            val arr = JSONArray()
            sessions.filter { !it.isTemporary }.forEach { session ->
                arr.put(serializeSession(session))
            }
            writeRaw("sessions_$cleanUser", arr.toString())
        }
    }

    fun loadEntries(sessionId: String): List<TranscriptEntry> {
        if (sessionId.isBlank()) return emptyList()
        val raw = readRaw("entries_$sessionId") ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val result = ArrayList<TranscriptEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(deserializeEntry(obj))
            }
            result.sortedBy { it.sequence }
        }.getOrDefault(emptyList())
    }

    fun saveEntries(sessionId: String, entries: List<TranscriptEntry>) {
        if (sessionId.isBlank()) return
        runCatching {
            val arr = JSONArray()
            entries.sortedBy { it.sequence }.forEach { entry ->
                arr.put(serializeEntry(entry))
            }
            writeRaw("entries_$sessionId", arr.toString())
        }
    }

    fun deleteSessionEntries(sessionId: String) {
        if (sessionId.isBlank()) return
        removeRaw("entries_$sessionId")
    }

    private fun serializeSession(s: NexusSession): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("userId", s.userId)
        put("title", s.title)
        put("status", s.status)
        put("cwd", s.cwd)
        put("commandsCount", s.commandsCount)
        put("redactionsCount", s.redactionsCount)
        put("provider", s.provider)
        put("model", s.model)
        put("projectSlug", s.projectSlug)
        put("storageLocation", s.storageLocation)
        put("isPinned", s.isPinned)
        put("createdAtSec", s.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
        put("updatedAtSec", s.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
    }

    private fun deserializeSession(obj: JSONObject): NexusSession {
        val createdSec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
        val updatedSec = obj.optLong("updatedAtSec", createdSec)
        return NexusSession(
            id = obj.optString("id", ""),
            userId = obj.optString("userId", ""),
            title = obj.optString("title", "New chat"),
            status = obj.optString("status", "idle"),
            cwd = obj.optString("cwd", "/"),
            commandsCount = obj.optLong("commandsCount", 0L),
            redactionsCount = obj.optLong("redactionsCount", 0L),
            provider = obj.optString("provider", "Gemini"),
            model = obj.optString("model", "gemini-3.8-flash"),
            projectSlug = obj.optString("projectSlug", ""),
            storageLocation = obj.optString("storageLocation", "shared_storage"),
            createdAt = Timestamp(createdSec, 0),
            updatedAt = Timestamp(updatedSec, 0),
            isPinned = obj.optBoolean("isPinned", false),
            isTemporary = false
        )
    }

    private fun serializeEntry(e: TranscriptEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("userId", e.userId)
        put("sessionId", e.sessionId)
        put("type", e.type)
        put("text", e.text)
        put("command", e.command)
        put("reason", e.reason)
        put("commandStatus", e.commandStatus)
        put("isRisky", e.isRisky)
        put("timeoutSec", e.timeoutSec)
        put("cwd", e.cwd)
        put("exitCode", e.exitCode)
        put("output", e.output)
        put("redactions", e.redactions)
        put("noticeKind", e.noticeKind)
        put("sequence", e.sequence)
        put("title", e.title)
        put("reasoningText", e.reasoningText)
        put("thinkingDurationSec", e.thinkingDurationSec)
        put("jobId", e.jobId)
        put("createdAtSec", e.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
        put("updatedAtSec", e.updatedAt?.seconds ?: (System.currentTimeMillis() / 1000L))
    }

    private fun deserializeEntry(obj: JSONObject): TranscriptEntry {
        val createdSec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
        val updatedSec = obj.optLong("updatedAtSec", createdSec)
        return TranscriptEntry(
            id = obj.optString("id", ""),
            userId = obj.optString("userId", ""),
            sessionId = obj.optString("sessionId", ""),
            type = obj.optString("type", "user"),
            text = obj.optString("text", ""),
            command = obj.optString("command", ""),
            reason = obj.optString("reason", ""),
            commandStatus = obj.optString("commandStatus", ""),
            isRisky = obj.optBoolean("isRisky", false),
            timeoutSec = obj.optLong("timeoutSec", 10L),
            cwd = obj.optString("cwd", ""),
            exitCode = obj.optLong("exitCode", 0L),
            output = obj.optString("output", ""),
            redactions = obj.optLong("redactions", 0L),
            noticeKind = obj.optString("noticeKind", ""),
            sequence = obj.optLong("sequence", 0L),
            title = obj.optString("title", ""),
            reasoningText = obj.optString("reasoningText", ""),
            thinkingDurationSec = obj.optLong("thinkingDurationSec", 0L),
            jobId = obj.optString("jobId", ""),
            createdAt = Timestamp(createdSec, 0),
            updatedAt = Timestamp(updatedSec, 0)
        )
    }

    companion object {
        private const val PREFS_NAME = "zelkai_local_transcript_v1"
    }
}
