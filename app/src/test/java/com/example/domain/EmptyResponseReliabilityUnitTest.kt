package com.example.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TranscriptEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmptyResponseReliabilityUnitTest {

    @Test
    fun stripForDisplay_preservesVisibleText_whenBreakdownHasUnclosedTagOrFence() {
        val truncatedFile = "Here is the breakdown:\n1. Step one\n<nexus:file path=\"a.kt\">fun a() {"
        val strippedFile = GenerationOutcome.stripForDisplay(truncatedFile)
        assertTrue(strippedFile.contains("Here is the breakdown:"))
        assertTrue(strippedFile.contains("1. Step one"))

        val truncatedExec = "Checking directory first:\n```sh-exec\nls -la"
        val strippedExec = GenerationOutcome.stripForDisplay(truncatedExec)
        assertTrue(strippedExec.contains("Checking directory first:"))
    }

    @Test
    fun generationOutcomeClassify_handlesReasoningBudgetBlockedAndPromptTooLong() {
        val budget = GenerationOutcome.classify(
            rawText = "<think>long chain of thought</think>",
            finishReason = "length"
        )
        assertTrue(budget is GenerationOutcome.BudgetConsumedByReasoning)

        val blocked = GenerationOutcome.classify(
            rawText = "",
            finishReason = "safety"
        )
        assertTrue(blocked is GenerationOutcome.Blocked)

        val tooLong = GenerationOutcome.classify(
            rawText = "",
            finishReason = "eos",
            promptTokens = 4096,
            nCtx = 4096
        )
        assertTrue(tooLong is GenerationOutcome.PromptTooLong)

        val toolOnly = GenerationOutcome.classify(
            rawText = "```sh-exec\nls -la\n```",
            finishReason = "eos",
            toolCallCount = 1
        )
        assertTrue(toolOnly is GenerationOutcome.ToolCallsOnly)
    }

    @Test
    fun openAiSse_accumulatesChunkedToolCalls_ignoresNullContent_andTracksFinishReason() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val engine = MultiPlatformAiEngine(LocalLlmManager(ctx), UserApiKeyStore(ctx))
        val server = MockWebServer()
        server.start()
        try {
            engine.openAiBaseUrl = server.url("/").toString().trimEnd('/')
            val sseBody = listOf(
                """data: {"choices":[{"delta":{"content":null,"reasoning_content":"Step 1... "},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"content":"Here is the breakdown."},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"function":{"name":"execute_shell_command","arguments":"{\"com"}}]},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"function":{"arguments":"mand\":\"ls -la\"}"}}]},"finish_reason":"stop"}]}""",
                "data: [DONE]"
            ).joinToString("\n\n") + "\n\n"
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            )

            val chunks = MultiPlatformAiEngine.routeWithTransport(
                provider = "openai",
                modelId = "gpt-4o",
                personalApiKey = "sk-test-key",
                maxTokens = 2048L,
                entries = listOf(TranscriptEntry(id = "u1", type = "user", text = "Explain and list files", sequence = 1L)),
                personaPrompt = "You are Zelkai.",
                currentCwd = "/tmp",
                fallbackUserPrompt = "Explain and list files",
                transport = engine.defaultTransportPublicForTest(),
                openAiEndpointUrl = server.url("/v1/chat/completions").toString()
            ).toList()

            val full = chunks.joinToString("")
            assertFalse("Must never emit literal 'null' for OpenAI null delta content", full.contains("nullHere"))
            assertTrue(full.contains("<think>Step 1... </think>"))
            assertTrue(full.contains("Here is the breakdown."))
            assertTrue(full.contains("ls -la"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anthropicSse_accumulatesThinkingAndToolUse_andRetriesRateLimit() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val engine = MultiPlatformAiEngine(LocalLlmManager(ctx), UserApiKeyStore(ctx))
        val server = MockWebServer()
        server.start()
        try {
            engine.anthropicBaseUrl = server.url("/").toString().trimEnd('/')
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("""{"error":{"message":"rate limit"}}"""))
            val sseBody = listOf(
                """data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"Analyzing..."}}""",
                """data: {"type":"content_block_stop"}""",
                """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Breakdown complete."}}""",
                """data: {"type":"content_block_start","content_block":{"type":"tool_use","name":"execute_shell_command"}}""",
                """data: {"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{\"command\":\"pwd\"}"}}""",
                """data: {"type":"content_block_stop"}""",
                """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""
            ).joinToString("\n\n") + "\n\n"
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
            )

            val chunks = MultiPlatformAiEngine.routeWithTransport(
                provider = "anthropic",
                modelId = "claude-sonnet-4-20250514",
                personalApiKey = "sk-ant-test",
                maxTokens = 2048L,
                entries = listOf(TranscriptEntry(id = "u1", type = "user", text = "Show pwd", sequence = 1L)),
                personaPrompt = "You are Zelkai.",
                currentCwd = "/tmp",
                fallbackUserPrompt = "Show pwd",
                transport = engine.defaultTransportPublicForTest()
            ).toList()

            val full = chunks.joinToString("")
            assertTrue(full.contains("<think>Analyzing...</think>"))
            assertTrue(full.contains("Breakdown complete."))
            assertTrue(full.contains("pwd"))
            assertEquals("eos", engine.lastFinishReason)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun localProvider_preservesMultiTurnHistoryAndZelkaiIdentity() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        var capturedPrompt = ""
        var capturedSystem = ""
        val fakeTransport = object : ProviderTransport {
            override fun streamGeminiRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf("")
            override fun streamAnthropicRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf("")
            override fun streamOpenAiCompatibleRest(provider: String, endpointUrl: String, modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf("")
            override fun streamLocalOnDevice(
                modelId: String,
                userPrompt: String,
                currentCwd: String,
                compactSystemPrompt: String,
                recentCommandOutputs: List<Pair<String, String>>
            ): Flow<String> {
                capturedPrompt = userPrompt
                capturedSystem = compactSystemPrompt
                return flowOf("Local breakdown ready.")
            }
        }

        val out = MultiPlatformAiEngine.routeWithTransport(
            provider = "local",
            modelId = "qwen2.5-1.5b",
            personalApiKey = "",
            maxTokens = 1024L,
            entries = listOf(
                TranscriptEntry(id = "u1", type = "user", text = "First question", sequence = 1L),
                TranscriptEntry(id = "a1", type = "agent", text = "First answer", sequence = 2L),
                TranscriptEntry(id = "u2", type = "user", text = "Break down step 2", sequence = 3L)
            ),
            personaPrompt = "",
            currentCwd = "/tmp",
            fallbackUserPrompt = "Break down step 2",
            transport = fakeTransport
        ).toList()

        assertEquals("Local breakdown ready.", out.joinToString(""))
        assertTrue(capturedSystem.contains("Zelkai"))
        assertTrue(capturedPrompt.contains("First question"))
        assertTrue(capturedPrompt.contains("First answer"))
        assertTrue(capturedPrompt.contains("Break down step 2"))
    }
}
