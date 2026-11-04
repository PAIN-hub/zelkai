package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationOutcomeUnitTest {

    @Test
    fun classify_textOutput_classifiedAsText() {
        val outcome = GenerationOutcome.classify(
            rawText = "Here is the snake game in python",
            finishReason = "eos"
        )
        assertTrue(outcome is GenerationOutcome.Text)
        assertEquals("Here is the snake game in python", (outcome as GenerationOutcome.Text).visibleText)
        assertFalse(outcome.isError)
    }

    @Test
    fun classify_toolCallsOnly_isNotAnError() {
        val toolCallFence = "```sh-exec title=\"Check python\"\npython --version\n```"
        val outcome = GenerationOutcome.classify(
            rawText = toolCallFence,
            finishReason = "eos",
            toolCallCount = 1
        )
        assertTrue("ToolCallsOnly must be classified when model only emitted tools", outcome is GenerationOutcome.ToolCallsOnly)
        assertFalse("ToolCallsOnly is NOT an error", outcome.isError)
    }

    @Test
    fun classify_reasoningOnly_classifiedAsReasoningOnly() {
        val thinkingText = "<think>\nLet me think about how to solve this.\nFirst install pygame.\n</think>"
        val outcome = GenerationOutcome.classify(
            rawText = thinkingText,
            finishReason = "eos"
        )
        assertTrue(outcome is GenerationOutcome.ReasoningOnly)
        assertFalse(outcome.isError)
        val chars = (outcome as GenerationOutcome.ReasoningOnly).reasoningChars
        assertTrue(chars > 0)
    }

    @Test
    fun classify_reasoningOnly_withMaxTokens_classifiedAsBudgetConsumed() {
        val thinkingText = "<think>\nLong complex thought process that ran out of tokens...\n"
        val outcome = GenerationOutcome.classify(
            rawText = thinkingText,
            finishReason = "length"
        )
        assertTrue(outcome is GenerationOutcome.BudgetConsumedByReasoning)
        assertFalse(outcome.isError)
    }

    @Test
    fun classify_noTokens_classifiedAsNoTokensError() {
        val outcome = GenerationOutcome.classify(
            rawText = "",
            finishReason = "eos"
        )
        assertTrue(outcome is GenerationOutcome.NoTokens)
        assertTrue("NoTokens is classified as an error", outcome.isError)
    }

    @Test
    fun classify_cancelled_classifiedAsCancelledByUser() {
        val outcome = GenerationOutcome.classify(
            rawText = "Partial response",
            finishReason = "cancelled"
        )
        assertTrue(outcome is GenerationOutcome.CancelledByUser)
        assertFalse(outcome.isError)
    }

    @Test
    fun classify_safetyBlock_classifiedAsBlocked() {
        val outcome = GenerationOutcome.classify(
            rawText = "",
            finishReason = "safety"
        )
        assertTrue(outcome is GenerationOutcome.Blocked)
        assertTrue(outcome.isError)
    }

    @Test
    fun stripForDisplay_removesThinkTags_andToolBlocks() {
        val raw = "<think>Secret thoughts</think>\nHere is your answer.\n```sh-exec title=\"Run\"\npython test.py\n```"
        val stripped = GenerationOutcome.stripForDisplay(raw)
        assertEquals("Here is your answer.", stripped)
    }

    @Test
    fun thinkingTagParser_separatesThinkingFromVisibleText() {
        val raw = "<think>\nAnalyzing the user request.\n</think>\nHello! How can I help you?"
        val parsed = ThinkingTagParser.parse(raw)
        assertEquals("Analyzing the user request.", parsed.thinkingText.trim())
        assertEquals("Hello! How can I help you?", parsed.visibleText.trim())
    }
}

