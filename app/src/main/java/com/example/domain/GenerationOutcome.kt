package com.example.domain

/**
 * Counts-only diagnostics for one local generation. Never contains prompt or output text.
 */
data class LocalGenerationDiagnostics(
    val promptTokens: Int = 0,
    val nCtx: Int = 0,
    val rawPieceCount: Int = 0,
    val finishReason: String = "none", // eos, length, stop_sequence, cancelled, error, none
    val templateFamily: String = "",
    val grammarActive: Boolean = false,
    val visibleCharsBefore: Int = 0,
    val visibleCharsAfter: Int = 0
) {
    fun toRedactedSummary(): String =
        "prompt_tokens=$promptTokens n_ctx=$nCtx pieces=$rawPieceCount finish=$finishReason " +
            "template=$templateFamily grammar=$grammarActive visible_before=$visibleCharsBefore visible_after=$visibleCharsAfter"
}

/**
 * Classification of every generation result, computed from BOTH the raw text (before
 * post-processing) and the visible text (after stripping tool syntax, <think>, fences),
 * because "empty" can be produced by the stripping itself.
 */
sealed interface GenerationOutcome {
    val isError: Boolean get() = false

    data class Text(val visibleText: String) : GenerationOutcome
    /** The model only emitted tool calls. This is NOT an error. */
    data class ToolCallsOnly(val toolCallCount: Int) : GenerationOutcome
    data class ReasoningOnly(val reasoningChars: Int) : GenerationOutcome
    data object NoTokens : GenerationOutcome { override val isError get() = true }
    data class PromptTooLong(val promptTokens: Int, val nCtx: Int) : GenerationOutcome { override val isError get() = true }
    data object CancelledByUser : GenerationOutcome
    data class Blocked(val reason: String) : GenerationOutcome { override val isError get() = true }
    data class BudgetConsumedByReasoning(val reasoningChars: Int) : GenerationOutcome
    data class Error(val message: String) : GenerationOutcome { override val isError get() = true }

    companion object {
        private val THINK_BLOCK_CLOSED = Regex("(?s)<think>.*?</think>")
        private val SH_EXEC_FENCE_CLOSED = Regex("(?s)```sh-exec[^\\n]*\\n.*?\\n```")
        private val NEXUS_FILE_CLOSED = Regex("(?s)<nexus:file\\b[^>]*>.*?</nexus:file>")
        private val NEXUS_TOOL_CLOSED = Regex("(?s)<nexus_tool\\b[^>]*>.*?</nexus_tool>|<nexus_tool\\b[^>]*/>")
        private val THINK_BLOCK_ANY = Regex("(?s)<think>.*?(</think>|$)")

        /**
         * Pure classifier. [finishReason] uses provider-normalised values:
         * `eos|stop`, `length|max_tokens`, `stop_sequence`, `cancelled`, `safety|blocked|content_filter`,
         * `prompt_too_long`, `error`.
         */
        fun classify(
            rawText: String,
            finishReason: String,
            toolCallCount: Int = 0,
            promptTokens: Int = 0,
            nCtx: Int = 0,
            errorMessage: String? = null
        ): GenerationOutcome {
            val reason = finishReason.lowercase()
            if (errorMessage != null) return Error(errorMessage)
            if (reason == "cancelled") return CancelledByUser
            if (reason in setOf("safety", "blocked", "content_filter", "refusal", "recitation")) {
                return Blocked(reason)
            }
            if (reason == "prompt_too_long" || (nCtx > 0 && promptTokens >= nCtx)) {
                return PromptTooLong(promptTokens, nCtx)
            }

            val reasoning = THINK_BLOCK_ANY.findAll(rawText).sumOf {
                it.value.removePrefix("<think>").removeSuffix("</think>").trim().length
            }
            val visible = stripForDisplay(rawText)

            if (visible.isNotBlank()) return Text(visible)
            if (toolCallCount > 0) return ToolCallsOnly(toolCallCount)
            if (reasoning > 0) {
                return if (reason == "length" || reason == "max_tokens") {
                    BudgetConsumedByReasoning(reasoning)
                } else {
                    ReasoningOnly(reasoning)
                }
            }
            return NoTokens
        }

        fun stripForDisplay(raw: String): String {
            return raw
                .replace(THINK_BLOCK_ANY, "")
                .replace(SH_EXEC_FENCE_CLOSED, "")
                .replace(NEXUS_FILE_CLOSED, "")
                .replace(NEXUS_TOOL_CLOSED, "")
                .trim()
        }

        /** Maps cloud provider finish reasons (Gemini finishReason, Anthropic stop_reason, OpenAI finish_reason). */
        fun normalizeCloudFinishReason(raw: String?): String {
            return when (raw?.trim()?.lowercase()) {
                null, "" -> "eos"
                "stop", "end_turn", "stop_sequence", "finish_reason_unspecified" -> "eos"
                "max_tokens", "length", "model_length" -> "length"
                "safety", "prohibited_content", "spii", "blocklist", "content_filter" -> "safety"
                "recitation" -> "recitation"
                "refusal" -> "refusal"
                "tool_use", "tool_calls", "function_call" -> "eos"
                "malformed_function_call" -> "error"
                else -> raw.trim().lowercase()
            }
        }
    }
}

