package com.example.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

data class QueuedJobNotification(
    val jobId: String,
    val title: String,
    val sessionId: String,
    val turnId: String,
    val exitCode: Int,
    val summary: String,
    val isSuccess: Boolean
)

/**
 * Coordinates automated resumption of agent turns when background jobs complete.
 * - Caps: max 1 resume per job, max 3 auto-resumes per turn
 * - 0 guest quota used (does not consume cloud quota)
 * - Emits notifications when the app is backgrounded or another chat is active.
 */
object AgentResumeCoordinator {
    const val MAX_RESUMES_PER_TURN = 3

    private val resumedJobIds = ConcurrentHashMap.newKeySet<String>()
    private val turnResumeCount = ConcurrentHashMap<String, Int>()

    private val _notifications = MutableSharedFlow<QueuedJobNotification>(extraBufferCapacity = 16)
    val notifications: SharedFlow<QueuedJobNotification> = _notifications.asSharedFlow()

    fun canResume(jobId: String, turnId: String): Boolean {
        if (resumedJobIds.contains(jobId)) return false
        val count = turnResumeCount.getOrDefault(turnId, 0)
        return count < MAX_RESUMES_PER_TURN
    }

    fun recordResume(jobId: String, turnId: String) {
        resumedJobIds.add(jobId)
        turnResumeCount.compute(turnId) { _, current -> (current ?: 0) + 1 }
    }

    fun postNotification(notification: QueuedJobNotification) {
        _notifications.tryEmit(notification)
    }

    fun resetTurn(turnId: String) {
        turnResumeCount.remove(turnId)
    }
}

