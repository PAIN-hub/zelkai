package com.example.domain

enum class AgentPhase(val label: String) {
    THINKING("Thinking"),
    WRITING("Writing"),
    RUNNING_COMMAND("Running command"),
    WAITING_FOR_APPROVAL("Waiting for approval"),
    WAITING_FOR_JOB("Waiting for job"),
    LOADING_MODEL("Loading model")
}

/**
 * High-visibility top status driving the center pill of NexusEditorialTopBar.
 * Replaces the obsolete "Free plan" and "Guest · Sign in" pills.
 */
sealed interface AgentStatus {
    val label: String

    data object Idle : AgentStatus {
        override val label: String get() = "Ready"
    }

    data class Working(val phase: AgentPhase) : AgentStatus {
        override val label: String get() = phase.label
    }

    data class Error(
        val summary: String,
        val targetEntryId: String? = null
    ) : AgentStatus {
        override val label: String get() = "Error"
    }
}

