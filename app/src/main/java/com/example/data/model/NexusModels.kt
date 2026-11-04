package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class NexusUserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val provider: String = "gemini",
    val selectedModel: String = "gemini-3.8-flash",
    val maxTokens: Long = 4096L,
    val maxSteps: Long = 6L,
    val timeoutSec: Long = 10L,
    val agentPersona: String = DEFAULT_SYSTEM_PROMPT,
    val activeSessionId: String = "default_session",
    val inputHistory: List<String> = emptyList(),
    val displayName: String = "",
    val avatarDataUri: String = "",
    val fontSize: String = "default",
    val responseStyle: String = "balanced",
    val personaPreset: String = "default",
    val customPersonasJson: String = "",
    val aboutUser: String = "",
    val responsePreference: String = "",
    val enableForNewChats: Boolean = true,
    val skillsJson: String = "",
    val skillsTokenBudget: Long = 2000L,
    val recentWorkspaces: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Nexus Agent, an AI coding and Linux system assistant. " +
            "For greetings, general questions, or code/web generation requests (such as creating a landing page, scripts, or explanations), reply directly in clean Markdown with language-tagged fenced code blocks—do NOT run shell commands unless needed. " +
            "When the user asks to inspect the filesystem, run a command, or modify workspace files, propose the exact non-interactive shell command required. " +
            "Commands share a persistent working directory, so `cd` carries over to the next command; shell variables do not. " +
            "Secrets in command output are redacted as [REDACTED] — never try to work around that."
    }
}

@IgnoreExtraProperties
data class NexusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New chat",
    val status: String = "idle",
    val cwd: String = "/",
    val commandsCount: Long = 0L,
    val redactionsCount: Long = 0L,
    val provider: String = "Gemini",
    val model: String = "gemini-3.8-flash",
    val projectSlug: String = "",
    val storageLocation: String = "shared_storage",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val isPinned: Boolean = false,
    @get:Exclude val isTemporary: Boolean = false
) {
    @get:Exclude
    val isProjectUnassigned: Boolean
        get() = projectSlug.isBlank()

    fun resolvedProjectPath(): String {
        val clean = projectSlug.trim()
        if (clean.isEmpty()) return "/storage/emulated/0/Nexus"
        return if (storageLocation == "termux_home") {
            "/data/data/com.termux/files/home/nexus_workspace/$clean"
        } else {
            "/storage/emulated/0/Nexus/$clean"
        }
    }
}

@IgnoreExtraProperties
data class TranscriptEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val type: String = "user", // user, agent, command, output, notice, analysis
    val text: String = "",
    val command: String = "",
    val reason: String = "",
    val commandStatus: String = "", // "", pending, approved, denied, cancelled
    val isRisky: Boolean = false,
    val timeoutSec: Long = 10L,
    val cwd: String = "",
    val exitCode: Long = 0L,
    val output: String = "",
    val redactions: Long = 0L,
    val noticeKind: String = "", // warn, error, info
    val sequence: Long = 0L,
    val title: String = "",
    val reasoningText: String = "",
    val thinkingDurationSec: Long = 0L,
    val jobId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
