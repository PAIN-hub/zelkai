package com.example.domain

import com.example.data.model.TranscriptEntry

sealed interface TimelineBlock {
    val blockId: String

    data class ThinkingBlock(
        override val blockId: String,
        val text: String,
        val durationSec: Long,
        val isLive: Boolean = false
    ) : TimelineBlock

    data class TextBlock(
        override val blockId: String,
        val markdownText: String
    ) : TimelineBlock

    data class ToolStepItem(
        val id: String,
        val title: String,
        val command: String,
        val cwd: String,
        val status: ToolStepStatus,
        val exitCode: Long,
        val output: String,
        val redactions: Long,
        val isRisky: Boolean,
        val jobId: String = "",
        val filePath: String? = null,
        val fileContent: String? = null
    )

    enum class ToolStepStatus {
        PENDING_APPROVAL,
        DENIED,
        RUNNING,
        SUCCESS,
        FAILED,
        TIMED_OUT
    }

    enum class ToolGroupOverallState {
        RUNNING,
        WAITING_APPROVAL,
        DENIED_SKIPPED,
        COMPLETED_SUCCESS,
        COMPLETED_WITH_FAILURE
    }

    data class ToolGroup(
        override val blockId: String,
        val steps: List<ToolStepItem>,
        val overallState: ToolGroupOverallState,
        val activeStepIndex: Int = 0,
        val activeStepTitle: String = ""
    ) : TimelineBlock {
        val totalCount: Int get() = steps.size
        val failedCount: Int get() = steps.count { it.status == ToolStepStatus.FAILED || it.status == ToolStepStatus.TIMED_OUT }
        val hasPendingApproval: Boolean get() = steps.any { it.status == ToolStepStatus.PENDING_APPROVAL }
        val hasDenial: Boolean get() = steps.any { it.status == ToolStepStatus.DENIED }

        fun headerLabel(): String {
            val count = totalCount
            val commandsCount = steps.count { it.filePath.isNullOrBlank() }
            val filesCount = steps.count { !it.filePath.isNullOrBlank() }

            return when (overallState) {
                ToolGroupOverallState.WAITING_APPROVAL -> "Waiting for your approval"
                ToolGroupOverallState.DENIED_SKIPPED -> "Skipped · you denied a command"
                ToolGroupOverallState.RUNNING -> "Running… (${(activeStepIndex + 1).coerceAtMost(count)} of $count)"
                ToolGroupOverallState.COMPLETED_WITH_FAILURE -> {
                    if (filesCount > 0 && commandsCount == 0) {
                        "Edited $filesCount file${if (filesCount > 1) "s" else ""} · $failedCount failed"
                    } else if (commandsCount > 0 && filesCount == 0) {
                        "Ran $commandsCount command${if (commandsCount > 1) "s" else ""} · $failedCount failed"
                    } else {
                        "Completed $count steps · $failedCount failed"
                    }
                }
                ToolGroupOverallState.COMPLETED_SUCCESS -> {
                    if (filesCount > 0 && commandsCount == 0) {
                        "Edited $filesCount file${if (filesCount > 1) "s" else ""}"
                    } else if (commandsCount > 0 && filesCount == 0) {
                        "Ran $commandsCount command${if (commandsCount > 1) "s" else ""}"
                    } else {
                        "Completed $count steps"
                    }
                }
            }
        }
    }

    data class ApprovalBlock(
        override val blockId: String,
        val commandEntry: TranscriptEntry,
        val isPending: Boolean
    ) : TimelineBlock

    data class ErrorBlock(
        override val blockId: String,
        val title: String,
        val message: String,
        val suggestedAction: String? = null
    ) : TimelineBlock

    data class NoticeBlock(
        override val blockId: String,
        val noticeEntry: TranscriptEntry
    ) : TimelineBlock
}

/**
 * Pure function that transforms a sequence of [TranscriptEntry] items for an assistant turn
 * into structured [TimelineBlock] instances with stripped tool syntax and clean step titles.
 */
object TurnTimelineBuilder {

    fun build(
        entries: List<TranscriptEntry>,
        activeStreamingText: String = "",
        isTurnActive: Boolean = false,
        streamingThinkingSec: Long = 0L
    ): List<TimelineBlock> {
        val blocks = mutableListOf<TimelineBlock>()

        var pendingToolSteps = mutableListOf<TimelineBlock.ToolStepItem>()
        var toolGroupGroupId: String? = null

        fun flushToolGroup() {
            if (pendingToolSteps.isNotEmpty()) {
                val gid = toolGroupGroupId ?: "tool_group_${pendingToolSteps.first().id}"
                val hasPending = pendingToolSteps.any { it.status == TimelineBlock.ToolStepStatus.PENDING_APPROVAL }
                val hasDenial = pendingToolSteps.any { it.status == TimelineBlock.ToolStepStatus.DENIED }
                val hasRunning = pendingToolSteps.any { it.status == TimelineBlock.ToolStepStatus.RUNNING }
                val hasFailure = pendingToolSteps.any {
                    it.status == TimelineBlock.ToolStepStatus.FAILED || it.status == TimelineBlock.ToolStepStatus.TIMED_OUT
                }

                val overall = when {
                    hasPending -> TimelineBlock.ToolGroupOverallState.WAITING_APPROVAL
                    hasDenial -> TimelineBlock.ToolGroupOverallState.DENIED_SKIPPED
                    hasRunning -> TimelineBlock.ToolGroupOverallState.RUNNING
                    hasFailure -> TimelineBlock.ToolGroupOverallState.COMPLETED_WITH_FAILURE
                    else -> TimelineBlock.ToolGroupOverallState.COMPLETED_SUCCESS
                }

                val runningIdx = pendingToolSteps.indexOfFirst { it.status == TimelineBlock.ToolStepStatus.RUNNING }
                    .takeIf { it >= 0 } ?: (pendingToolSteps.size - 1).coerceAtLeast(0)
                val activeTitle = pendingToolSteps.getOrNull(runningIdx)?.title.orEmpty()

                blocks.add(
                    TimelineBlock.ToolGroup(
                        blockId = gid,
                        steps = pendingToolSteps.toList(),
                        overallState = overall,
                        activeStepIndex = runningIdx,
                        activeStepTitle = activeTitle
                    )
                )
                pendingToolSteps = mutableListOf()
                toolGroupGroupId = null
            }
        }

        // Process stored entries
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            when (entry.type) {
                "agent" -> {
                    flushToolGroup()
                    val raw = entry.text
                    val parsed = ThinkingTagParser.parse(raw)
                    val effectiveReasoning = if (entry.reasoningText.isNotBlank()) entry.reasoningText else parsed.thinkingText
                    if (effectiveReasoning.isNotBlank()) {
                        blocks.add(
                            TimelineBlock.ThinkingBlock(
                                blockId = "thinking_${entry.id.ifBlank { i.toString() }}",
                                text = effectiveReasoning,
                                durationSec = entry.thinkingDurationSec,
                                isLive = false
                            )
                        )
                    }

                    val cleanVisible = GenerationOutcome.stripForDisplay(
                        if (parsed.thinkingText.isNotBlank()) parsed.visibleText else raw
                    )
                    if (cleanVisible.isNotBlank()) {
                        blocks.add(
                            TimelineBlock.TextBlock(
                                blockId = "text_${entry.id.ifBlank { i.toString() }}",
                                markdownText = cleanVisible
                            )
                        )
                    }
                }
                "command" -> {
                    // Peek next entry for output
                    val nextEntry = entries.getOrNull(i + 1)
                    val outputEntry = if (nextEntry?.type == "output") {
                        i++ // consume output together with command
                        nextEntry
                    } else null

                    val status = when {
                        entry.commandStatus == "pending" -> TimelineBlock.ToolStepStatus.PENDING_APPROVAL
                        entry.commandStatus == "denied" || entry.commandStatus == "cancelled" -> TimelineBlock.ToolStepStatus.DENIED
                        outputEntry == null && isTurnActive && i >= entries.size - 1 -> TimelineBlock.ToolStepStatus.RUNNING
                        outputEntry != null && outputEntry.exitCode == 124L -> TimelineBlock.ToolStepStatus.TIMED_OUT
                        outputEntry != null && outputEntry.exitCode != 0L -> TimelineBlock.ToolStepStatus.FAILED
                        else -> TimelineBlock.ToolStepStatus.SUCCESS
                    }

                    val title = ActivityLabeler.label(
                        command = entry.command,
                        explicitTitle = entry.title
                    )

                    if (toolGroupGroupId == null) {
                        toolGroupGroupId = "tg_${entry.id.ifBlank { i.toString() }}"
                    }

                    pendingToolSteps.add(
                        TimelineBlock.ToolStepItem(
                            id = entry.id.ifBlank { "cmd_$i" },
                            title = title,
                            command = entry.command,
                            cwd = entry.cwd,
                            status = status,
                            exitCode = outputEntry?.exitCode ?: entry.exitCode,
                            output = outputEntry?.output ?: entry.output,
                            redactions = (outputEntry?.redactions ?: 0L) + entry.redactions,
                            isRisky = entry.isRisky,
                            jobId = entry.jobId
                        )
                    )

                    // If waiting for approval, also emit an ApprovalBlock so user can act immediately
                    if (status == TimelineBlock.ToolStepStatus.PENDING_APPROVAL) {
                        blocks.add(
                            TimelineBlock.ApprovalBlock(
                                blockId = "approval_${entry.id.ifBlank { i.toString() }}",
                                commandEntry = entry,
                                isPending = true
                            )
                        )
                    }
                }
                "output" -> {
                    // Standalone output without preceding command (rare fallback)
                    val title = ActivityLabeler.label(entry.command, entry.title)
                    pendingToolSteps.add(
                        TimelineBlock.ToolStepItem(
                            id = entry.id.ifBlank { "out_$i" },
                            title = title,
                            command = entry.command,
                            cwd = entry.cwd,
                            status = if (entry.exitCode == 124L) TimelineBlock.ToolStepStatus.TIMED_OUT
                            else if (entry.exitCode != 0L) TimelineBlock.ToolStepStatus.FAILED
                            else TimelineBlock.ToolStepStatus.SUCCESS,
                            exitCode = entry.exitCode,
                            output = entry.output,
                            redactions = entry.redactions,
                            isRisky = entry.isRisky
                        )
                    )
                }
                "notice" -> {
                    flushToolGroup()
                    if (entry.noticeKind in listOf("project_slug_proposal", "setup_needed", "timeout_bg_offer", "fallback_prompt", "permission")) {
                        blocks.add(
                            TimelineBlock.NoticeBlock(
                                blockId = "notice_${entry.id.ifBlank { i.toString() }}",
                                noticeEntry = entry
                            )
                        )
                    } else if (entry.noticeKind == "error" || entry.text.startsWith("Inference failed:") || entry.text.startsWith("Error:")) {
                        blocks.add(
                            TimelineBlock.ErrorBlock(
                                blockId = "err_${entry.id.ifBlank { i.toString() }}",
                                title = "Generation notice",
                                message = entry.text
                            )
                        )
                    } else {
                        blocks.add(
                            TimelineBlock.TextBlock(
                                blockId = "notice_${entry.id.ifBlank { i.toString() }}",
                                markdownText = entry.text
                            )
                        )
                    }
                }
                else -> {
                    flushToolGroup()
                    if (entry.text.isNotBlank()) {
                        blocks.add(
                            TimelineBlock.TextBlock(
                                blockId = "block_${entry.id.ifBlank { i.toString() }}",
                                markdownText = entry.text
                            )
                        )
                    }
                }
            }
            i++
        }

        // Live streaming addition
        if (activeStreamingText.isNotBlank()) {
            val parsedLive = ThinkingTagParser.parse(activeStreamingText)
            if (parsedLive.thinkingText.isNotBlank()) {
                blocks.add(
                    TimelineBlock.ThinkingBlock(
                        blockId = "live_thinking",
                        text = parsedLive.thinkingText,
                        durationSec = streamingThinkingSec,
                        isLive = isTurnActive
                    )
                )
            }
            val cleanLive = GenerationOutcome.stripForDisplay(
                if (parsedLive.thinkingText.isNotBlank()) parsedLive.visibleText else activeStreamingText
            )
            if (cleanLive.isNotBlank()) {
                blocks.add(
                    TimelineBlock.TextBlock(
                        blockId = "live_text",
                        markdownText = cleanLive
                    )
                )
            }
        }

        flushToolGroup()
        return blocks
    }

    data class ChatTurn(
        val id: String,
        val userEntry: TranscriptEntry?,
        val assistantEntries: List<TranscriptEntry>,
        val isLiveTurn: Boolean = false
    )

    /**
     * Partitions a flat list of transcript entries into conversation turns.
     * Each turn starts with a user entry (if present) followed by all generated
     * assistant, command, output, and notice entries.
     */
    fun partitionIntoTurns(
        entries: List<TranscriptEntry>,
        isAgentRunning: Boolean = false
    ): List<ChatTurn> {
        val turns = mutableListOf<ChatTurn>()
        var currentUserEntry: TranscriptEntry? = null
        var currentAssistantEntries = mutableListOf<TranscriptEntry>()

        fun flushTurn(isLast: Boolean) {
            if (currentUserEntry != null || currentAssistantEntries.isNotEmpty() || (isLast && isAgentRunning)) {
                val turnId = currentUserEntry?.id?.ifBlank { null }
                    ?: currentAssistantEntries.firstOrNull()?.id?.ifBlank { null }
                    ?: "turn_${turns.size}"
                turns.add(
                    ChatTurn(
                        id = turnId,
                        userEntry = currentUserEntry,
                        assistantEntries = currentAssistantEntries.toList(),
                        isLiveTurn = isLast && isAgentRunning
                    )
                )
                currentUserEntry = null
                currentAssistantEntries = mutableListOf()
            }
        }

        for (entry in entries) {
            if (entry.type == "user") {
                flushTurn(isLast = false)
                currentUserEntry = entry
            } else {
                currentAssistantEntries.add(entry)
            }
        }
        flushTurn(isLast = true)
        return turns
    }
}
