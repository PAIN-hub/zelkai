package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TranscriptEntry
import com.example.domain.CommandRiskLevel
import com.example.domain.CwdValidationResult
import com.example.domain.DeviceProfiler
import com.example.domain.DeviceTier
import com.example.domain.EngineProvider
import com.example.domain.InferencePreset
import com.example.domain.LocalInferenceEngine
import com.example.domain.LocalLlmManager
import com.example.domain.LocalModelNotLoadedException
import com.example.domain.LocalShellBridge
import com.example.domain.MissingApiKeyException
import com.example.domain.ModelRegistry
import com.example.domain.MultiPlatformAiEngine
import com.example.domain.ProviderModelRepository
import com.example.domain.ProviderTransport
import com.example.domain.RecommendationBucket
import com.example.domain.RecommendationEngine
import com.example.domain.StubEngine
import com.example.domain.ToolCall
import com.example.domain.ToolCallParseResult
import com.example.domain.ToolCallParser
import com.example.domain.JobManager
import com.example.domain.JobStore
import com.example.domain.ManagedJobRecord
import com.example.domain.ManagedJobState
import com.example.domain.ServerLogAnalyzer
import com.example.domain.StartJobOutcome
import com.example.domain.ToolActionKind
import com.example.domain.BackendCircuitBreaker
import com.example.domain.BackendHealthState
import com.example.domain.DiagnosticFixAction
import com.example.domain.DiagnosticStatus
import com.example.domain.EnvironmentDescription
import com.example.domain.ErrorMapper
import com.example.domain.ExecEvent
import com.example.domain.ExecutionBackend
import com.example.domain.ExecutionBackendType
import com.example.domain.FallbackPolicy
import com.example.domain.DetectedToolInfo
import com.example.domain.TermuxDiagnostics
import com.example.domain.TermuxEnvironmentProbe
import com.example.domain.TermuxError
import com.example.domain.TermuxErrorActionType
import com.example.domain.TermuxPluginResult
import com.example.domain.UserApiKeyStore
import com.example.domain.WorkspaceMode
import com.example.domain.WorkspaceProjectType
import com.example.ui.nexus.NexusViewModel
import com.example.ui.nexus.UiState
import com.example.ui.theme.DarkNexusSemanticColors
import com.example.ui.theme.LightNexusSemanticColors
import com.example.ui.theme.NexusLogStderr
import com.example.ui.theme.NexusLogSurfaceDark
import com.example.ui.theme.NexusLogSurfaceLight
import com.example.ui.theme.NexusLogWarning
import com.example.ui.theme.NexusOnLogSurfaceDark
import com.example.ui.theme.NexusOnLogSurfaceLight
import com.example.ui.theme.wcagContrastRatio
import com.example.util.NexusNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    private fun pumpMainLooper(ms: Long = 50L) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private suspend fun awaitViewModelIdle(viewModel: NexusViewModel, timeoutMs: Long = 8000L) {
        val start = System.currentTimeMillis()
        pumpMainLooper(50L)
        delay(20L)
        pumpMainLooper(50L)
        while (viewModel.isRunning.value && System.currentTimeMillis() - start < timeoutMs) {
            delay(20L)
            pumpMainLooper(60L)
        }
        pumpMainLooper(50L)
    }

    private suspend fun currentEntries(viewModel: NexusViewModel): List<TranscriptEntry> {
        pumpMainLooper(50L)
        val state = viewModel.entriesState.first { it is UiState.Success }
        pumpMainLooper(50L)
        return (state as UiState.Success).data
    }

    @Test
    fun toolCallParser_parsesFenceVariants_jsonFallback_andRejectsMalformedOrMultiLine() {
        // 1) Canonical ```sh-exec fence
        val shExecRes = ToolCallParser.parse(
            """
            Creating the web folder now:
            ```sh-exec
            mkdir -p web
            ```
            """.trimIndent()
        )
        assertTrue(shExecRes is ToolCallParseResult.Success)
        val shExecCall = (shExecRes as ToolCallParseResult.Success).toolCall
        assertEquals("mkdir -p web", shExecCall.command)
        assertEquals("sh-exec", shExecCall.source)
        assertEquals(CommandRiskLevel.LOW, shExecCall.riskLevel)

        // 2) Tolerated fence variants: ```bash, ```sh, ```shell
        for (fence in listOf("bash", "sh", "shell")) {
            val res = ToolCallParser.parse("```$fence\nls -la\n```")
            assertTrue("Fence $fence must parse as Success", res is ToolCallParseResult.Success)
            assertEquals("ls -la", (res as ToolCallParseResult.Success).toolCall.command)
        }

        // 3) JSON fallback {"tool":"shell","command":"..."}
        val jsonFallbackRes = ToolCallParser.parse(
            """{"tool":"shell","command":"mkdir -p api_dir","reason":"Create api dir","timeoutSec":12}"""
        )
        assertTrue(jsonFallbackRes is ToolCallParseResult.Success)
        val jsonCall = (jsonFallbackRes as ToolCallParseResult.Success).toolCall
        assertEquals("mkdir -p api_dir", jsonCall.command)
        assertEquals(12L, jsonCall.timeoutSec)

        // 4) Normalized native cloud function call block
        val nativeNormalized = ToolCallParser.formatNormalizedToolCallBlock(
            ToolCall(command = "pwd", reason = "Check cwd", source = "gemini-native"),
            explanation = "Checking directory."
        )
        val nativeParsed = ToolCallParser.parse(nativeNormalized)
        assertTrue(nativeParsed is ToolCallParseResult.Success)
        assertEquals("pwd", (nativeParsed as ToolCallParseResult.Success).toolCall.command)

        // 5) Reject empty command inside fence
        val emptyFenceRes = ToolCallParser.parse("```sh-exec\n   \n```")
        assertTrue("Empty command fence must return ParseError", emptyFenceRes is ToolCallParseResult.ParseError)

        // 6) Reject multi-line chained commands with unescaped newlines unless approved
        val multiLineUnescaped = """
            ```sh-exec
            mkdir -p web
            cd web
            ```
        """.trimIndent()
        val multiLineRejected = ToolCallParser.parse(multiLineUnescaped, allowMultiLineIfApproved = false)
        assertTrue("Unescaped multi-line command must be rejected by default", multiLineRejected is ToolCallParseResult.ParseError)

        val multiLineApproved = ToolCallParser.parse(multiLineUnescaped, allowMultiLineIfApproved = true)
        assertTrue("Approved multi-line command should parse when allowMultiLineIfApproved=true", multiLineApproved is ToolCallParseResult.Success)

        // Escaped line continuation (`\\\n`) is allowed as a single logical command
        val escapedContinuation = "```sh-exec\nmkdir -p \\\n  web/assets\n```"
        val escapedRes = ToolCallParser.parse(escapedContinuation)
        assertTrue(escapedRes is ToolCallParseResult.Success)
        assertEquals("mkdir -p web/assets", (escapedRes as ToolCallParseResult.Success).toolCall.command)

        // 7) Reject multiple command blocks in one step
        val multipleBlocks = """
            ```sh-exec
            mkdir a
            ```
            ```sh-exec
            mkdir b
            ```
        """.trimIndent()
        assertTrue(ToolCallParser.parse(multipleBlocks) is ToolCallParseResult.ParseError)

        // 8) Reject unclosed fence or malformed JSON
        assertTrue(ToolCallParser.parse("```sh-exec\nmkdir broken") is ToolCallParseResult.ParseError)
        assertTrue(ToolCallParser.parse("{\"tool\":\"shell\",\"command\":") is ToolCallParseResult.ParseError)

        // 9) Verify risk classification levels (LOW, MEDIUM, HIGH)
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("mkdir -p web"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("rm temp.txt"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("rm -rf build_output"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("sudo ls"))

        // 10) Verify output feedback redaction and 2000-char truncation
        val longSecretOutput = "sk-proj-1234567890abcdefghijklmnop " + "x".repeat(2500)
        val formattedFeedback = ToolCallParser.formatToolOutputForModelFeedback(
            command = "cat .env",
            cwd = "/workspace",
            exitCode = 0,
            rawOutput = longSecretOutput,
            maxChars = 2000
        )
        assertTrue("Feedback must be truncated to <= 2000 chars", formattedFeedback.length <= 2000)
        assertFalse("Feedback must redact secrets", formattedFeedback.contains("sk-proj-1234567890"))
    }

    @Test
    fun executionPipeline_quickCommand_stubEngineNotice_andCloudToolCallApprovalGate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shellBridge = LocalShellBridge(context)
        val cwdDir = File(shellBridge.resetSessionCwd())
        val webDir = File(cwdDir, "web")
        if (webDir.exists()) webDir.deleteRecursively()

        val localLlmManager = LocalLlmManager(context)
        val userApiKeyStore = UserApiKeyStore(context)
        val notificationHelper = NexusNotificationHelper(context)

        try {
            // 1) Test explicit Quick Command (`/quick create a folder named web`) -> labeled "Quick command", never poses as model output
            val viewModel = NexusViewModel(
                repository = null,
                shellBridge = shellBridge,
                localLlmManager = localLlmManager,
                userApiKeyStore = userApiKeyStore,
                notificationHelper = notificationHelper,
                currentUserId = "guest_local_session",
                isGuestMode = true
            )

            viewModel.submitDirective("/quick create a folder named web")
            awaitViewModelIdle(viewModel)

            assertTrue("Directory 'web' must exist in cwd after Quick Command", webDir.exists() && webDir.isDirectory)
            val entriesAfterQuick = currentEntries(viewModel)
            assertTrue(
                "Quick command must emit a Quick Command notice card, never an AI message",
                entriesAfterQuick.any { it.type == "notice" && it.noticeKind == "quick_command" && it.text.contains("Quick command", ignoreCase = true) }
            )
            assertTrue("Transcript must contain approved command card", entriesAfterQuick.any { it.type == "command" && it.command == "mkdir -p web" && it.commandStatus == "approved" })
            assertTrue("Transcript must contain output card with exitCode 0", entriesAfterQuick.any { it.type == "output" && it.command == "mkdir -p web" && it.exitCode == 0L })

            // 2) Test Unloaded Local Model: sending prompt when local model is unloaded shows "Model not loaded" error card
            userApiKeyStore.persistSelectedProviderAndModel("local", LocalLlmManager.LOCAL_LLM_CATALOG.first().id)
            pumpMainLooper(50L)
            viewModel.unloadLocalModel()
            viewModel.submitDirective("list files")
            awaitViewModelIdle(viewModel)
            val entriesAfterUnload = currentEntries(viewModel)
            val unloadedErrorNotice = entriesAfterUnload.lastOrNull { it.type == "notice" && it.noticeKind == "error" }
            assertNotNull("Unloaded local model must produce an error notice card", unloadedErrorNotice)
            assertTrue(
                "Error card must state 'Model not loaded'",
                unloadedErrorNotice!!.text.contains("Model not loaded", ignoreCase = true)
            )

            // 3) Test StubEngine when no native JNI engine is linked: returns EngineInfo(isRuntimeInstalled = false)
            EngineProvider.resetToDefault()
            assertFalse("StubEngine.isRuntimeInstalled must be false", EngineProvider.currentEngine.info().isRuntimeInstalled)

            // 4) Test Cloud Provider pipeline: medium-risk command -> pending approval -> denied -> denial turn fed back -> agent continues
            val feedbackTurnsSeenByCloud = mutableListOf<List<Pair<String, String>>>()
            val testCloudTransport = object : ProviderTransport {
                override fun streamGeminiRest(
                    modelId: String,
                    apiKey: String,
                    maxTokens: Int,
                    systemInstruction: String,
                    conversationTurns: List<Pair<String, String>>
                ): Flow<String> {
                    feedbackTurnsSeenByCloud.add(conversationTurns)
                    val lastTurnText = conversationTurns.lastOrNull()?.second.orEmpty()
                    return when {
                        lastTurnText.contains("[COMMAND_DENIED_BY_USER]") -> {
                            flowOf("Understood, skipping deletion of `obsolete.txt` as requested.")
                        }
                        lastTurnText.contains("[TOOL_EXECUTION_RESULT]") -> {
                            flowOf("Verified `cloud_web` folder creation with exit code 0.")
                        }
                        lastTurnText.contains("delete obsolete.txt") -> {
                            flowOf("```sh-exec\nrm obsolete.txt\n```")
                        }
                        else -> {
                            flowOf("```sh-exec\nmkdir -p cloud_web\n```")
                        }
                    }
                }

                override fun streamAnthropicRest(
                    modelId: String,
                    apiKey: String,
                    maxTokens: Int,
                    systemInstruction: String,
                    conversationTurns: List<Pair<String, String>>
                ): Flow<String> = flowOf()

                override fun streamOpenAiCompatibleRest(
                    provider: String,
                    endpointUrl: String,
                    modelId: String,
                    apiKey: String,
                    maxTokens: Int,
                    systemInstruction: String,
                    conversationTurns: List<Pair<String, String>>
                ): Flow<String> = flowOf()

                override fun streamLocalOnDevice(
                    modelId: String,
                    userPrompt: String,
                    currentCwd: String,
                    compactSystemPrompt: String,
                    recentCommandOutputs: List<Pair<String, String>>
                ): Flow<String> = flowOf()
            }

            userApiKeyStore.setApiKey("gemini", null, "user-test-gemini-key")
            userApiKeyStore.persistSelectedProviderAndModel("gemini", "gemini-2.5-flash")

            val cloudVm = NexusViewModel(
                repository = null,
                shellBridge = shellBridge,
                localLlmManager = localLlmManager,
                userApiKeyStore = userApiKeyStore,
                notificationHelper = notificationHelper,
                currentUserId = "test_user_cloud",
                isGuestMode = true,
                customTransport = testCloudTransport
            )

            val cloudWebDir = File(cwdDir, "cloud_web")
            if (cloudWebDir.exists()) cloudWebDir.deleteRecursively()

            cloudVm.submitDirective("create a folder named cloud_web")
            awaitViewModelIdle(cloudVm)

            assertTrue("Cloud provider tool call must create cloud_web in cwd", cloudWebDir.exists() && cloudWebDir.isDirectory)
            assertTrue(
                "Cloud provider must receive [TOOL_EXECUTION_RESULT] in step 2",
                feedbackTurnsSeenByCloud.any { turns -> turns.any { it.second.contains("[TOOL_EXECUTION_RESULT]") } }
            )

            // Test MEDIUM-risk command ("rm obsolete.txt") -> requires approval -> deny it -> verify denial turn & agent continuation
            cloudVm.submitDirective("delete obsolete.txt")
            val waitStart = System.currentTimeMillis()
            var pendingEntry: TranscriptEntry? = null
            while (System.currentTimeMillis() - waitStart < 4000L) {
                shadowOf(Looper.getMainLooper()).idle()
                pendingEntry = currentEntries(cloudVm).find { it.type == "command" && it.commandStatus == "pending" }
                if (pendingEntry != null) break
                delay(25L)
            }
            assertNotNull("Medium-risk command 'rm obsolete.txt' must pause at approval gate", pendingEntry)

            cloudVm.resolveCommandPermission(pendingEntry!!, approved = false)
            awaitViewModelIdle(cloudVm)

            val postDenyEntries = currentEntries(cloudVm)
            assertTrue(
                "Transcript must show denial notice",
                postDenyEntries.any { it.type == "notice" && it.text.contains("Command denied by user", ignoreCase = true) }
            )
            assertTrue(
                "Agent must continue after denial and acknowledge it",
                postDenyEntries.any { it.type == "agent" && it.text.contains("skipping deletion", ignoreCase = true) }
            )
        } finally {
            EngineProvider.resetToDefault()
        }
    }

    @Test
    fun guestQuota_countsOnlyCloudPrompts_andAllowsLocalModelsAndDirectShellAtZeroQuota() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shellBridge = LocalShellBridge(context)
        val cwdDir = File(shellBridge.resetSessionCwd())
        val localLlmManager = LocalLlmManager(context)
        val userApiKeyStore = UserApiKeyStore(context)
        val notificationHelper = NexusNotificationHelper(context)

        // Register a test LocalInferenceEngine via EngineProvider seam to verify local execution at 0/5 guest quota
        val localSpec = LocalLlmManager.LOCAL_LLM_CATALOG.first()
        val ggufFile = File(localLlmManager.modelsDir, "${localSpec.id}.gguf")
        ggufFile.parentFile?.mkdirs()
        // Write valid GGUF magic header + padding so loadModel succeeds
        ggufFile.writeBytes("GGUF".toByteArray() + ByteArray(1024))

        val testNativeEngine = object : LocalInferenceEngine {
            private val _loaded = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val isLoaded: kotlinx.coroutines.flow.StateFlow<Boolean> = _loaded

            override suspend fun load(path: String, params: com.example.domain.InferenceParams): Result<Unit> {
                _loaded.value = true
                return Result.success(Unit)
            }

            override fun generate(prompt: String, params: com.example.domain.InferenceParams): Flow<String> = flow {
                if (prompt.contains("[TOOL_EXECUTION_RESULT]\nCommand:")) {
                    emit("Created folder guest_local_ok.")
                } else {
                    emit("```sh-exec\nmkdir -p guest_local_ok\n```")
                }
            }

            override fun stop() {}

            override fun unload() {
                _loaded.value = false
            }

            override fun info(): com.example.domain.EngineInfo = com.example.domain.EngineInfo(
                name = "TestNativeLlamaEngine",
                version = "1.0-test",
                isRuntimeInstalled = true,
                statusMessage = "Ready"
            )
        }

        try {
            EngineProvider.currentEngine = testNativeEngine
            localLlmManager.switchActiveLocalModel(localSpec.id)
            userApiKeyStore.setApiKey("gemini", null, "user-gemini-key")

            val guestVm = NexusViewModel(
                repository = null,
                shellBridge = shellBridge,
                localLlmManager = localLlmManager,
                userApiKeyStore = userApiKeyStore,
                notificationHelper = notificationHelper,
                currentUserId = "guest_quota_test",
                isGuestMode = true
            )

            // Set guest quota to exhausted (5/5 used -> 0 remaining)
            guestVm.setGuestDirectivesUsedForTest(NexusViewModel.GUEST_MAX_DIRECTIVES)
            assertEquals(5, guestVm.guestDirectivesUsed.value)

            // 1) Cloud provider prompt at 0/5 quota must be blocked and show inline "Limit reached, sign in" card
            userApiKeyStore.persistSelectedProviderAndModel("gemini", "gemini-2.5-flash")
            shadowOf(Looper.getMainLooper()).idle()
            guestVm.submitDirective("create a folder named blocked_cloud")
            delay(40L)
            shadowOf(Looper.getMainLooper()).idle()
            val entriesAfterCloudBlock = currentEntries(guestVm)
            assertTrue(
                "Exhausted guest quota on cloud provider must emit 'Limit reached, sign in' notice card",
                entriesAfterCloudBlock.any {
                    it.type == "notice" &&
                        it.noticeKind == "guest_limit" &&
                        it.text.contains("Limit reached, sign in", ignoreCase = true)
                }
            )
            assertFalse(File(cwdDir, "blocked_cloud").exists())

            // 2) Local model prompt at 0/5 quota must NOT consume quota and MUST still work!
            val guestLocalDir = File(cwdDir, "guest_local_ok")
            if (guestLocalDir.exists()) guestLocalDir.deleteRecursively()
            userApiKeyStore.persistSelectedProviderAndModel("local", localSpec.id)
            shadowOf(Looper.getMainLooper()).idle()

            guestVm.submitDirective("create a folder named guest_local_ok")
            awaitViewModelIdle(guestVm)

            assertEquals("Local model must not increment guestDirectivesUsed", 5, guestVm.guestDirectivesUsed.value)
            assertTrue("Local model must still execute commands when guest quota is 0/5", guestLocalDir.exists() && guestLocalDir.isDirectory)

            // 3) Direct shell mode (`sh >`) at 0/5 quota must bypass LLM and execute
            val directShDir = File(cwdDir, "direct_sh_ok")
            if (directShDir.exists()) directShDir.deleteRecursively()
            guestVm.executeDirectShellCommand("mkdir -p direct_sh_ok")
            awaitViewModelIdle(guestVm)

            assertTrue("Direct shell command (sh >) must succeed at 0/5 guest quota", directShDir.exists() && directShDir.isDirectory)

            // 4) Verify cwd permission check surfaces "Grant file access" when outside app storage and All Files Access is missing
            shellBridge.hasAllFilesAccessChecker = { false }
            val validation = shellBridge.validateWorkingDirectory("/storage/emulated/0/Download")
            assertTrue("External storage path without All Files Access must return MissingAllFilesAccess", validation is CwdValidationResult.MissingAllFilesAccess)
        } finally {
            EngineProvider.resetToDefault()
            ggufFile.delete()
        }
    }

    @Test
    fun providerRouting_hitsSelectedProviderOnly_andRequiresUserKeyForCloudProviders() = runBlocking {
        val hitLog = mutableListOf<Pair<String, String>>()
        val testTransport = object : ProviderTransport {
            override fun streamGeminiRest(
                modelId: String,
                apiKey: String,
                maxTokens: Int,
                systemInstruction: String,
                conversationTurns: List<Pair<String, String>>
            ): Flow<String> {
                hitLog.add("gemini" to modelId)
                return flowOf("ok:gemini:$modelId")
            }

            override fun streamAnthropicRest(
                modelId: String,
                apiKey: String,
                maxTokens: Int,
                systemInstruction: String,
                conversationTurns: List<Pair<String, String>>
            ): Flow<String> {
                hitLog.add("anthropic" to modelId)
                return flowOf("ok:anthropic:$modelId")
            }

            override fun streamOpenAiCompatibleRest(
                provider: String,
                endpointUrl: String,
                modelId: String,
                apiKey: String,
                maxTokens: Int,
                systemInstruction: String,
                conversationTurns: List<Pair<String, String>>
            ): Flow<String> {
                hitLog.add(provider to modelId)
                return flowOf("ok:$provider:$modelId")
            }

            override fun streamLocalOnDevice(
                modelId: String,
                userPrompt: String,
                currentCwd: String,
                compactSystemPrompt: String,
                recentCommandOutputs: List<Pair<String, String>>
            ): Flow<String> {
                hitLog.add("local" to modelId)
                return flowOf("ok:local:$modelId")
            }
        }

        val providersToTest = listOf(
            Triple("gemini", "gemini-2.5-flash", "user-gemini-key"),
            Triple("anthropic", "claude-sonnet-5-5", "user-claude-key"),
            Triple("openai", "o3", "user-openai-key"),
            Triple("local", "qwen2.5-coder-1.5b-instruct", "")
        )

        val entries = listOf(
            TranscriptEntry(
                id = "u1",
                type = "user",
                text = "Hello",
                cwd = "/nexus_workspace",
                sequence = 1L
            )
        )

        for ((provider, modelId, key) in providersToTest) {
            hitLog.clear()
            val chunks = MultiPlatformAiEngine.routeWithTransport(
                provider = provider,
                modelId = modelId,
                personalApiKey = key,
                maxTokens = 512L,
                entries = entries,
                personaPrompt = "System",
                currentCwd = "/nexus_workspace",
                fallbackUserPrompt = "Hello",
                transport = testTransport
            ).toList()

            val response = chunks.joinToString("")
            assertEquals("Each request must hit exactly one provider", 1, hitLog.size)
            assertEquals("Request for $provider must hit $provider only", provider, hitLog.first().first)
            assertEquals("Request for $modelId must use exact modelId", modelId, hitLog.first().second)
            assertEquals("ok:$provider:$modelId", response)
        }

        // Legacy "fable" provider routes under "anthropic"
        hitLog.clear()
        MultiPlatformAiEngine.routeWithTransport(
            provider = "fable",
            modelId = "claude-fable-5-1",
            personalApiKey = "user-anthropic-key",
            maxTokens = 512L,
            entries = entries,
            personaPrompt = "System",
            currentCwd = "/nexus_workspace",
            fallbackUserPrompt = "Hello",
            transport = testTransport
        ).toList()
        assertEquals("anthropic", hitLog.single().first)
        assertEquals("claude-fable-5-1", hitLog.single().second)

        // Verify that cloud providers throw MissingApiKeyException when key is blank
        for (cloudProvider in listOf("gemini", "anthropic", "openai")) {
            hitLog.clear()
            try {
                MultiPlatformAiEngine.routeWithTransport(
                    provider = cloudProvider,
                    modelId = "test-model",
                    personalApiKey = "",
                    maxTokens = 512L,
                    entries = entries,
                    personaPrompt = "System",
                    currentCwd = "/nexus_workspace",
                    fallbackUserPrompt = "Hello",
                    transport = testTransport
                ).toList()
                fail("Expected MissingApiKeyException for $cloudProvider when key is blank")
            } catch (expected: MissingApiKeyException) {
                assertEquals(cloudProvider, expected.provider)
                assertTrue("Transport must not be called when API key is missing", hitLog.isEmpty())
            }
        }
    }

    @Test
    fun mockWebServer_providerModelDiscovery_errorMapping_and404AutoSwapPolicy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keyStore = UserApiKeyStore(context)
        keyStore.setProviderKey("gemini", "AIzaSyTestKey123")
        val server = MockWebServer()
        server.start()

        try {
            val baseUrl = server.url("/").toString().trimEnd('/')
            val repo = ProviderModelRepository(
                context = context,
                userApiKeyStore = keyStore,
                httpClient = OkHttpClient()
            ).apply {
                geminiBaseUrl = baseUrl
                anthropicBaseUrl = baseUrl
                openAiBaseUrl = baseUrl
            }

            // 1) Gemini GET /v1beta/models: strips "models/" prefix, filters by generateContent, sends x-goog-api-key header
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "models": [
                            {
                              "name": "models/gemini-2.5-flash",
                              "displayName": "models/gemini-2.5-flash",
                              "supportedGenerationMethods": ["generateContent", "countTokens"]
                            },
                            {
                              "name": "models/text-embedding-004",
                              "displayName": "Text Embedding 004",
                              "supportedGenerationMethods": ["embedContent"]
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            val geminiRes = repo.fetchModelsForProvider("gemini", forceRefresh = true, overrideApiKey = "AIzaSyTestKey123")
            assertTrue(geminiRes.isSuccess)
            val geminiModels = geminiRes.getOrThrow()
            assertEquals("Only generateContent models must be kept", 1, geminiModels.size)
            assertEquals("models/ prefix must be stripped from ModelOption.id", "gemini-2.5-flash", geminiModels.single().id)
            assertEquals("models/ prefix must be stripped from displayName", "gemini-2.5-flash", geminiModels.single().displayName)
            assertFalse("supportsToolCalls must default to false", geminiModels.single().supportsToolCalls)

            val recordedGeminiReq = server.takeRequest()
            assertEquals("AIzaSyTestKey123", recordedGeminiReq.getHeader("x-goog-api-key"))

            // 2) Anthropic fallback seed list when offline/error: falls back to 4 unverified seeds including claude-fable-5-1
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"message":"Overloaded"}}"""))
            val anthropicFallback = repo.fetchModelsForProvider("anthropic", forceRefresh = true, overrideApiKey = "sk-ant-test")
            assertTrue(anthropicFallback.isFailure)
            server.takeRequest()
            val seeds = repo.anthropicModels.value
            assertEquals(4, seeds.size)
            assertTrue(seeds.all { it.isUnverified })
            assertTrue(seeds.any { it.id == "claude-fable-5-1" })

            // 3) OpenAI heuristic filter vs Show All toggle
            val openAiJson = """
                {
                  "data": [
                    {"id": "gpt-4o-mini"},
                    {"id": "o3-mini"},
                    {"id": "text-embedding-3-small"},
                    {"id": "whisper-1"}
                  ]
                }
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(openAiJson))
            repo.setShowAllOpenAiModels(false)
            val filteredOpenAi = repo.fetchModelsForProvider("openai", forceRefresh = true, overrideApiKey = "sk-proj-test")
            assertTrue(filteredOpenAi.isSuccess)
            val filteredIds = filteredOpenAi.getOrThrow().map { it.id }
            assertEquals(listOf("gpt-4o-mini", "o3-mini"), filteredIds)
            server.takeRequest()

            repo.setShowAllOpenAiModels(true)
            assertEquals(4, repo.openAiModels.value.size)

            // 4) Amendment #2: 404 Replacement Policy — auto-swap ONLY when error message names a replacement in the refreshed list
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "models": [
                            {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]}
                          ]
                        }
                        """.trimIndent()
                    )
            )
            val namedResolution = repo.handleModelNotFound(
                provider = "gemini",
                failedModelId = "gemini-1.5-flash",
                errorBody = "Model gemini-1.5-flash is deprecated. Please use gemini-2.5-flash instead."
            )
            assertEquals("gemini-2.5-flash", namedResolution.autoSwappedModel?.id)
            server.takeRequest()

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "models": [
                            {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]}
                          ]
                        }
                        """.trimIndent()
                    )
            )
            val unnamedResolution = repo.handleModelNotFound(
                provider = "gemini",
                failedModelId = "gemini-1.5-flash",
                errorBody = "models/gemini-1.5-flash is not found for API version v1beta."
            )
            assertNull("Must NOT pick first verified model when error does not name a replacement", unnamedResolution.autoSwappedModel)
            server.takeRequest()

            // 5) Error mapping: 401/403 -> INVALID_API_KEY, 404 -> MODEL_NOT_FOUND, 429 -> RATE_LIMITED with Retry-After
            val err401 = MultiPlatformAiEngine.classifyHttpError("gemini", "gemini-2.5-flash", 401, """{"error":{"message":"API key not valid"}}""", null)
            assertEquals(com.example.domain.ProviderErrorKind.INVALID_API_KEY, err401.kind)

            val err404 = MultiPlatformAiEngine.classifyHttpError("gemini", "gemini-old", 404, """{"error":{"message":"Model gemini-old is no longer available"}}""", null)
            assertEquals(com.example.domain.ProviderErrorKind.MODEL_NOT_FOUND, err404.kind)

            val err429 = MultiPlatformAiEngine.classifyHttpError("openai", "gpt-4o", 429, """{"error":{"message":"Rate limit reached"}}""", "7")
            assertEquals(com.example.domain.ProviderErrorKind.RATE_LIMITED, err429.kind)
            assertEquals(7L, err429.retryAfterSeconds)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mockWebServer_ggufDownloader_resumesWithRangeAndIfRange_verifiesSha256_andRejectsMismatch() = runBlocking {
        val server = MockWebServer()
        server.start()

        val tempDir = File(System.getProperty("java.io.tmpdir"), "nexus_gguf_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Build a valid GGUF payload starting with 'G','G','U','F' (0x47, 0x47, 0x55, 0x46)
            val fullPayload = "GGUF".toByteArray() + ByteArray(2048) { idx -> (idx % 251).toByte() }
            val digest = MessageDigest.getInstance("SHA-256")
            val expectedSha256 = digest.digest(fullPayload).joinToString("") { "%02x".format(it) }

            val part1 = fullPayload.copyOfRange(0, 512)
            val part2 = fullPayload.copyOfRange(512, fullPayload.size)

            val targetFile = File(tempDir, "test-model.gguf")
            val partFile = File(tempDir, "test-model.gguf.part")

            // Write initial 512 bytes to .part to test resuming from a partial file
            partFile.writeBytes(part1)

            // 1) Pre-redirect HEAD response returns x-linked-etag & x-linked-size, followed by 206 Partial Content GET
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("x-linked-etag", "\"etag-gguf-v1\"")
                    .setHeader("x-linked-size", fullPayload.size.toString())
            )
            val buffer2 = Buffer().write(part2)
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("ETag", "\"etag-gguf-v1\"")
                    .setHeader("Content-Length", part2.size.toString())
                    .setHeader("Content-Range", "bytes 512-${fullPayload.size - 1}/${fullPayload.size}")
                    .setBody(buffer2)
            )

            val downloadUrl = server.url("/resolve/main/test-model.gguf").toString()
            val result = com.example.domain.ResumableGgufDownloader.downloadModelResumable(
                modelId = "test-model",
                canonicalUrl = downloadUrl,
                modelsDir = tempDir,
                expectedManifestSha256 = expectedSha256,
                expectedManifestSizeBytes = fullPayload.size.toLong(),
                hfToken = "hf_test_token_123",
                storedEtag = "",
                baseClient = OkHttpClient(),
                onProgress = { _, _ -> }
            )

            assertTrue("Resumable GGUF download should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertTrue("Final .gguf file must exist after atomic rename", targetFile.exists())
            assertFalse(".part file must be renamed/removed after completion", partFile.exists())
            assertEquals(fullPayload.size.toLong(), targetFile.length())

            val headReq = server.takeRequest()
            assertEquals("HEAD", headReq.method)
            assertEquals("Bearer hf_test_token_123", headReq.getHeader("Authorization"))

            val rangeReq = server.takeRequest()
            assertEquals("GET", rangeReq.method)
            assertEquals("bytes=512-", rangeReq.getHeader("Range"))
            assertEquals("\"etag-gguf-v1\"", rangeReq.getHeader("If-Range"))
            assertEquals("Bearer hf_test_token_123", rangeReq.getHeader("Authorization"))

            // 2) Test SHA-256 mismatch: must delete corrupt file and return failure
            val badTargetFile = File(tempDir, "bad-model.gguf")
            val badPartFile = File(tempDir, "bad-model.gguf.part")
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("x-linked-etag", "\"etag-bad\"")
                    .setHeader("x-linked-size", fullPayload.size.toString())
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", fullPayload.size.toString())
                    .setBody(Buffer().write(fullPayload))
            )

            val badResult = com.example.domain.ResumableGgufDownloader.downloadModelResumable(
                modelId = "bad-model",
                canonicalUrl = server.url("/resolve/main/bad-model.gguf").toString(),
                modelsDir = tempDir,
                expectedManifestSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                expectedManifestSizeBytes = fullPayload.size.toLong(),
                hfToken = "",
                storedEtag = "",
                baseClient = OkHttpClient(),
                onProgress = { _, _ -> }
            )
            assertTrue("SHA-256 mismatch must fail", badResult.isFailure)
            assertFalse("Corrupt .part file must be deleted on SHA-256 mismatch", badPartFile.exists())
            assertFalse("Final file must not be created on SHA-256 mismatch", badTargetFile.exists())
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun recommendationEngine_scalesFrom4GbPhoneTo16GbSnapdragon8Flagship() {
        val catalog = ModelRegistry.loadModels()
        assertTrue("Catalog should include at least 15 models across 0.36B to 14B", catalog.size >= 15)

        val lowMidPhone = DeviceProfiler.buildProfileFromSpecs(
            totalRamGb = 4.0,
            availableRamGb = 1.9,
            freeStorageGb = 12.0,
            socManufacturer = "MediaTek",
            socModel = "Helio G99",
            primeCores = 0,
            bigCores = 2,
            littleCores = 6,
            maxFreqKhz = 2_200_000L,
            benchmarkCompositeScore = 42
        )

        val lowReport = RecommendationEngine.evaluateCatalog(
            deviceProfile = lowMidPhone,
            catalog = catalog,
            preset = InferencePreset.BALANCED
        )
        assertNotNull("4GB phone must still receive a compatible top pick", lowReport.topPick)
        assertTrue(
            "Top pick on 4GB phone with 1.9GB free RAM must be <= 1.7B",
            lowReport.topPick!!.entry.paramsBillions <= 1.7
        )
        val qwen14bOnLow = lowReport.allEvaluated.first { it.entry.id == "qwen2.5-coder-14b-instruct" }
        assertEquals(RecommendationBucket.NOT_RECOMMENDED, qwen14bOnLow.bucket)
        assertTrue(
            "Rejection reason must explain RAM shortfall",
            qwen14bOnLow.rejectionReason.orEmpty().contains("RAM", ignoreCase = true)
        )

        val flagship16Gb = DeviceProfiler.buildProfileFromSpecs(
            totalRamGb = 16.0,
            availableRamGb = 10.8,
            freeStorageGb = 64.0,
            socManufacturer = "Qualcomm",
            socModel = "Snapdragon 8 Gen 3",
            primeCores = 1,
            bigCores = 5,
            littleCores = 2,
            maxFreqKhz = 3_400_000L,
            benchmarkCompositeScore = 92
        )
        assertEquals(DeviceTier.ULTRA, flagship16Gb.effectiveTier)

        val ultraReport = RecommendationEngine.evaluateCatalog(
            deviceProfile = flagship16Gb,
            catalog = catalog,
            preset = InferencePreset.QUALITY
        )
        assertNotNull(ultraReport.topPick)
        assertTrue(
            "16GB ULTRA device in Quality mode should recommend a 7B–14B model",
            ultraReport.topPick!!.entry.paramsBillions >= 7.0
        )
    }

    @Test
    fun userAndAiMessageColors_satisfyWcagAaContrastInLightAndDark() {
        val lightUserContrast = wcagContrastRatio(
            LightNexusSemanticColors.onUserBubble,
            LightNexusSemanticColors.userBubble
        )
        val darkUserContrast = wcagContrastRatio(
            DarkNexusSemanticColors.onUserBubble,
            DarkNexusSemanticColors.userBubble
        )
        val lightAiContrast = wcagContrastRatio(
            LightNexusSemanticColors.aiText,
            LightNexusSemanticColors.background
        )
        val darkAiContrast = wcagContrastRatio(
            DarkNexusSemanticColors.aiText,
            DarkNexusSemanticColors.background
        )

        assertTrue("Light user bubble contrast ($lightUserContrast) must exceed WCAG AA 4.5", lightUserContrast >= 4.5)
        assertTrue("Dark user bubble contrast ($darkUserContrast) must exceed WCAG AA 4.5", darkUserContrast >= 4.5)
        assertTrue("Light AI text contrast ($lightAiContrast) must exceed WCAG AA 4.5", lightAiContrast >= 4.5)
        assertTrue("Dark AI text contrast ($darkAiContrast) must exceed WCAG AA 4.5", darkAiContrast >= 4.5)
    }

    @Test
    fun logSurfaceAndTextColors_satisfyWcagAaContrastInLightAndDark() {
        val lightLogContrast = wcagContrastRatio(
            NexusOnLogSurfaceLight,
            NexusLogSurfaceLight
        )
        val darkLogContrast = wcagContrastRatio(
            NexusOnLogSurfaceDark,
            NexusLogSurfaceDark
        )
        val lightStderrContrast = wcagContrastRatio(
            NexusLogStderr,
            NexusLogSurfaceLight
        )
        val darkStderrContrast = wcagContrastRatio(
            NexusLogStderr,
            NexusLogSurfaceDark
        )
        val lightWarningContrast = wcagContrastRatio(
            NexusLogWarning,
            NexusLogSurfaceLight
        )
        val darkWarningContrast = wcagContrastRatio(
            NexusLogWarning,
            NexusLogSurfaceDark
        )

        assertTrue("Light log text contrast ($lightLogContrast) must exceed WCAG AA 4.5", lightLogContrast >= 4.5)
        assertTrue("Dark log text contrast ($darkLogContrast) must exceed WCAG AA 4.5", darkLogContrast >= 4.5)
        assertTrue("Light log stderr contrast ($lightStderrContrast) must exceed WCAG AA 4.5", lightStderrContrast >= 4.5)
        assertTrue("Dark log stderr contrast ($darkStderrContrast) must exceed WCAG AA 4.5", darkStderrContrast >= 4.5)
        assertTrue("Light log warning contrast ($lightWarningContrast) must exceed WCAG AA 4.5", lightWarningContrast >= 4.5)
        assertTrue("Dark log warning contrast ($darkWarningContrast) must exceed WCAG AA 4.5", darkWarningContrast >= 4.5)
    }

    @Test
    fun localSystemPrompt_isUnder300Tokens_andIncludesFewShotShExecExamplesAndCwd() {
        val cwd = "/data/user/0/com.aistudio.nexusagent.v5sync/files/nexus_workspace"
        val prompt = MultiPlatformAiEngine.buildCompactLocalSystemInstruction(cwd)
        val approxTokens = prompt.length / 4

        assertTrue("Local system prompt ($approxTokens tokens) must be < 300 tokens", approxTokens < 300)
        assertTrue("Local system prompt must inject real cwd", prompt.contains(cwd))
        assertTrue("Local system prompt must mention Android constraints (no sudo, no apt, toybox/mksh)", prompt.contains("no sudo") && prompt.contains("toybox/mksh"))
        assertEquals(
            "Local system prompt must include 2 few-shot sh-exec examples",
            2,
            Regex("```sh-exec").findAll(prompt).count()
        )
    }

    @Test
    fun personalizationStore_systemPromptComposition_safetyPolicyPriority_andExportImportExcludesKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.domain.NexusPersonalizationStore(context)
        store.resetToDefaults()

        store.selectPersonaPreset("senior_devops")
        store.updateResponseStyle(com.example.domain.NexusResponseStyle.CONCISE)
        store.updatePersonalization(
            aboutUser = "I am a site reliability engineer working on Android Linux shells.",
            responsePreference = "Use POSIX one-liners.",
            enableForNewChats = true
        )
        store.saveCustomSkill(
            name = "Kubernetes Helper",
            description = "K8s manifest tips",
            instructions = "Prefer declarative YAML.",
            enabled = true
        )

        val composed = store.buildEffectiveSystemPrompt("Base system prompt.")
        assertTrue(composed.contains("Senior DevOps"))
        assertTrue(composed.contains("site reliability engineer"))
        assertTrue(composed.contains("Kubernetes Helper"))
        assertTrue(
            "Immutable safety policy must be appended after user instructions/personas/skills",
            composed.endsWith(com.example.domain.NexusPersonalizationStore.IMMUTABLE_SAFETY_POLICY)
        )

        val exportedJson = store.exportFullSettingsJson(
            provider = "anthropic",
            selectedModel = "claude-sonnet-5-5",
            maxTokens = 4096L,
            maxSteps = 6L,
            timeoutSec = 10L,
            themeModeId = "dark"
        )
        assertFalse("Exported settings JSON must never contain API keys", exportedJson.contains("apiKey", ignoreCase = true))
        assertTrue(exportedJson.contains("senior_devops"))

        store.resetToDefaults()
        assertEquals("default", store.config.value.personaPresetId)

        val importResult = store.importFullSettingsJson(exportedJson)
        assertTrue(importResult.isSuccess)
        assertEquals("senior_devops", store.config.value.personaPresetId)
    }

    @Test
    fun termuxExecution_riskClassifier_safetyAnalysis_activityVerbs_andTimeouts() {
        // 1) HIGH risk commands: package installs (pkg, apt, npm i -g, non-venv pip install),
        //    curl|sh, wget|sh, rm -rf, git push, chmod -R, outside workspace, reading env/credentials
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("pkg install -y nodejs git python"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("apt install -y git"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("npm install -g typescript"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("pip install requests"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("python3 -m pip install flask"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("curl -fsSL https://example.com/install.sh | sh"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("wget -qO- https://example.com/setup.sh | bash"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("rm -rf build_output"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("git push origin main"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("git push --force origin main"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("chmod -R 755 ./scripts"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("cat ~/.ssh/id_rsa"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("printenv"))
        assertEquals(
            CommandRiskLevel.HIGH,
            ToolCallParser.classifyRisk("cat /etc/hosts", workspaceCwd = "/data/data/com.termux/files/home/project")
        )

        // 2) MEDIUM risk commands: npm install/ci, pip install into a venv, git clone/pull/commit, node/python script execution
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("npm install express zod"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("npm ci"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk(".venv/bin/pip install requests"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("venv/bin/pip3 install fastapi"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("source .venv/bin/activate && pip install requests"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("git clone https://github.com/example/repo.git"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("git pull origin main"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("git commit -m 'init'"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("python main.py"))
        assertEquals(CommandRiskLevel.MEDIUM, ToolCallParser.classifyRisk("node server.js"))

        // 3) LOW risk read-only commands: ls, cat, git status/diff/log, node/python --version, npm ls
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("ls -la"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("cat README.md"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("git status"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("git diff"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("git log -n 5"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("node -v"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("node --version"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("npm ls"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("python --version"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("pip list"))
        assertEquals(CommandRiskLevel.LOW, ToolCallParser.classifyRisk("which git"))

        // 4) Safety analysis metadata for approval card (package names, registry hosts, install scripts)
        val npmSafety = ToolCallParser.analyzeCommandSafety("npm install express axios")
        assertTrue(npmSafety.packageNames.containsAll(listOf("express", "axios")))
        assertTrue(npmSafety.registryHosts.contains("registry.npmjs.org"))
        assertTrue(npmSafety.runsInstallScripts)

        val npmIgnoreScripts = ToolCallParser.analyzeCommandSafety("npm install --ignore-scripts express")
        assertFalse(npmIgnoreScripts.runsInstallScripts)

        val pkgSafety = ToolCallParser.analyzeCommandSafety("pkg update -y && pkg install -y nodejs git python")
        assertTrue(pkgSafety.packageNames.containsAll(listOf("nodejs", "git", "python")))
        assertTrue(pkgSafety.registryHosts.contains("packages.termux.dev"))

        // 5) Activity-row verbs
        assertEquals("Installing…", ToolCallParser.activityVerbForCommand("npm install express"))
        assertEquals("Running git…", ToolCallParser.activityVerbForCommand("git status"))
        assertEquals("Executing Python…", ToolCallParser.activityVerbForCommand("python3 script.py"))
        assertEquals("Running Node…", ToolCallParser.activityVerbForCommand("node index.js"))

        // 6) Execution timeouts (600s for installs/builds, 120s default)
        assertEquals(600L, com.example.domain.ExecutionTimeouts.recommendedTimeoutSec("pkg install -y nodejs git"))
        assertEquals(600L, com.example.domain.ExecutionTimeouts.recommendedTimeoutSec("npm install express"))
        assertEquals(120L, com.example.domain.ExecutionTimeouts.recommendedTimeoutSec("git status"))

        // 7) Middle-ellipsis path formatter
        val longPath = "/data/user/0/com.aistudio.nexusagent.v5sync/files/nexus_workspace"
        val shortened = com.example.domain.NexusToolAndRedactor.shortenPathMiddle(longPath, 32)
        assertTrue(shortened.contains("…"))
        assertTrue(shortened.endsWith("nexus_workspace"))
    }

    @Test
    fun termuxBackend_intentResultParsing_pwdPersistence_backendSelection_andCancelFlow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatchedIntents = mutableListOf<android.content.Intent>()

        val termuxBackend = com.example.domain.TermuxBackend(context = context).apply {
            intentDispatcher = { serviceIntent, executionId, correlationId ->
                dispatchedIntents.add(serviceIntent)
                val args = serviceIntent.getStringArrayExtra(com.example.domain.TermuxBackend.EXTRA_ARGUMENTS).orEmpty()
                val script = args.getOrNull(1).orEmpty()
                if (executionId > 0) {
                    val simulatedOut = when {
                        script.contains("echo ok") -> "ok\n__NEXUS_PWD_MARKER_9f8e7d__/data/data/com.termux/files/home\n"
                        script.contains("cd /data/data/com.termux/files/home/subproject") ->
                            "v22.4.0\ngit version 2.45.2\n__NEXUS_PWD_MARKER_9f8e7d__/data/data/com.termux/files/home/subproject\n"
                        else -> "done\n__NEXUS_PWD_MARKER_9f8e7d__/data/data/com.termux/files/home/subproject\n"
                    }
                    val callbackIntent = android.content.Intent(context, com.example.domain.TermuxResultBroadcastReceiver::class.java).apply {
                        action = com.example.domain.TermuxResultBroadcastReceiver.ACTION_TERMUX_EXEC_RESULT
                        putExtra(com.example.domain.TermuxResultBroadcastReceiver.EXTRA_EXECUTION_ID, executionId)
                        putExtra(com.example.domain.TermuxResultBroadcastReceiver.EXTRA_CORRELATION_ID, correlationId)
                        val resultBundle = android.os.Bundle().apply {
                            putString(com.example.domain.TermuxResultBroadcastReceiver.TERMUX_STDOUT_KEY, simulatedOut)
                            putString(com.example.domain.TermuxResultBroadcastReceiver.TERMUX_STDERR_KEY, "")
                            putInt(com.example.domain.TermuxResultBroadcastReceiver.TERMUX_EXIT_CODE_KEY, 0)
                        }
                        putExtra(com.example.domain.TermuxResultBroadcastReceiver.TERMUX_RESULT_BUNDLE_KEY, resultBundle)
                    }
                    com.example.domain.TermuxResultBroadcastReceiver().onReceive(context, callbackIntent)
                }
            }
        }

        // 1) Verify testConnection() runs `echo ok` via bash -lc with non-interactive env defaults
        val (ok, detail) = termuxBackend.testConnection()
        assertTrue("testConnection() must succeed when Termux returns exit 0 and 'ok'", ok)
        assertTrue("testConnection() detail must include 'ok'", detail.contains("ok"))
        val firstIntent = dispatchedIntents.first()
        assertEquals(com.example.domain.TermuxBackend.ACTION_RUN_COMMAND, firstIntent.action)
        assertEquals(com.example.domain.TermuxBackend.TERMUX_BASH_PATH, firstIntent.getStringExtra(com.example.domain.TermuxBackend.EXTRA_COMMAND_PATH))
        assertTrue(firstIntent.getBooleanExtra(com.example.domain.TermuxBackend.EXTRA_BACKGROUND, false))
        val scriptSent = firstIntent.getStringArrayExtra(com.example.domain.TermuxBackend.EXTRA_ARGUMENTS)!![1]
        assertTrue("Script must export CI=true and GIT_TERMINAL_PROMPT=0", scriptSent.contains("CI='true'") && scriptSent.contains("GIT_TERMINAL_PROMPT='0'"))

        // 2) Verify secrets/API keys are never passed in env and working directory persists across commands
        val events = termuxBackend.exec(
            cmd = "cd /data/data/com.termux/files/home/subproject && node -v && git --version",
            cwd = "/data/data/com.termux/files/home",
            env = mapOf("OPENAI_API_KEY" to "sk-secret-do-not-leak", "CUSTOM_FLAG" to "1"),
            timeoutSec = 30L
        ).toList()
        val lastIntentScript = dispatchedIntents.last().getStringArrayExtra(com.example.domain.TermuxBackend.EXTRA_ARGUMENTS)!![1]
        assertFalse("API keys/secrets must never be passed into Termux environment", lastIntentScript.contains("OPENAI_API_KEY") || lastIntentScript.contains("sk-secret-do-not-leak"))
        assertTrue("Safe non-secret env vars may be passed", lastIntentScript.contains("CUSTOM_FLAG='1'"))
        val stdoutCombined = events.filterIsInstance<com.example.domain.ExecEvent.StdoutChunk>().joinToString("") { it.text }
        assertTrue("Stdout must contain node and git versions without internal PWD marker", stdoutCombined.contains("v22.4.0") && !stdoutCombined.contains("__NEXUS_PWD_MARKER"))
        assertEquals(
            "TermuxBackend.lastKnownCwd must update and persist after `cd`",
            "/data/data/com.termux/files/home/subproject",
            termuxBackend.lastKnownCwd
        )

        // 3) Verify fallback to AndroidShellBackend with warning banner when Termux is unavailable,
        //    and verify "init a git repo and install express" approval gate + cancel() stopping a running npm install
        val shellBridge = LocalShellBridge(context)
        val localLlmManager = LocalLlmManager(context)
        val userApiKeyStore = UserApiKeyStore(context)
        val notificationHelper = NexusNotificationHelper(context)
        userApiKeyStore.setApiKey("gemini", null, "user-test-gemini-key")
        userApiKeyStore.persistSelectedProviderAndModel("gemini", "gemini-2.5-flash")

        var cancelCalled = false
        val executedCommands = mutableListOf<String>()
        val controllableBackend = object : com.example.domain.ExecutionBackend {
            override val backendType = com.example.domain.ExecutionBackendType.TERMUX
            override suspend fun isAvailable(): Boolean = true
            override fun exec(
                cmd: String,
                cwd: String,
                env: Map<String, String>,
                timeoutSec: Long
            ): Flow<com.example.domain.ExecEvent> = flow {
                executedCommands.add(cmd)
                if (cmd.contains("npm install slow-pkg")) {
                    emit(com.example.domain.ExecEvent.StdoutChunk("Installing slow-pkg...\n"))
                    delay(10_000L)
                } else {
                    emit(com.example.domain.ExecEvent.StdoutChunk("Success: $cmd\n"))
                    emit(com.example.domain.ExecEvent.Completed(exitCode = 0, durationMs = 15L))
                }
            }
            override fun cancel() {
                cancelCalled = true
            }
            override suspend fun describeEnvironment(
                cwd: String,
                workspaceMode: com.example.domain.WorkspaceMode
            ) = com.example.domain.EnvironmentDescription(
                backendType = com.example.domain.ExecutionBackendType.TERMUX,
                shellName = "Termux bash -lc",
                isAvailable = true,
                summaryLabel = "Termux (git, node, python)",
                cwd = cwd,
                workspaceMode = workspaceMode
            )
        }

        val transport = object : ProviderTransport {
            override fun streamGeminiRest(
                modelId: String,
                apiKey: String,
                maxTokens: Int,
                systemInstruction: String,
                conversationTurns: List<Pair<String, String>>
            ): Flow<String> {
                val lastTurn = conversationTurns.lastOrNull()?.second.orEmpty()
                return when {
                    lastTurn.contains("command: git init", ignoreCase = true) ->
                        flowOf("```sh-exec\nnpm install express\n```")
                    lastTurn.contains("command: npm install express", ignoreCase = true) ->
                        flowOf("Initialized git repository and installed express.")
                    else ->
                        flowOf("```sh-exec\ngit init\n```")
                }
            }
            override fun streamAnthropicRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf<String>()
            override fun streamOpenAiCompatibleRest(provider: String, endpointUrl: String, modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf<String>()
            override fun streamLocalOnDevice(modelId: String, userPrompt: String, currentCwd: String, compactSystemPrompt: String, recentCommandOutputs: List<Pair<String, String>>) = flowOf<String>()
        }

        val vm = NexusViewModel(
            repository = null,
            shellBridge = shellBridge,
            localLlmManager = localLlmManager,
            userApiKeyStore = userApiKeyStore,
            notificationHelper = notificationHelper,
            currentUserId = "test_termux_user",
            isGuestMode = true,
            customTransport = transport,
            customExecutionBackend = controllableBackend
        )

        // When customExecutionBackend is removed and Termux isn't installed in Robolectric, it falls back to AndroidShellBackend
        vm.customExecutionBackend = null
        val fallbackBackend = vm.resolveActiveExecutionBackend()
        assertEquals(com.example.domain.ExecutionBackendType.ANDROID_SHELL, fallbackBackend.backendType)
        val fallbackDesc = fallbackBackend.describeEnvironment(shellBridge.currentCwd, com.example.domain.WorkspaceMode.SHARED_STORAGE)
        assertEquals("Limited shell: install Termux for git, node, python", fallbackDesc.statusWarning)

        // Restore controllable Termux backend and run "init a git repo and install express" through approvals
        vm.customExecutionBackend = controllableBackend
        vm.submitDirective("init a git repo and install express")

        // Approve Step 1: `git init` (MEDIUM risk)
        var pending1: TranscriptEntry? = null
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 4000L) {
            pumpMainLooper(40L)
            pending1 = currentEntries(vm).find { it.type == "command" && it.command == "git init" && it.commandStatus == "pending" }
            if (pending1 != null) break
            delay(25L)
        }
        assertNotNull("`git init` must pause at approval gate", pending1)
        vm.resolveCommandPermission(pending1!!, approved = true)

        // Approve Step 2: `npm install express` (MEDIUM risk)
        var pending2: TranscriptEntry? = null
        val t1 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t1 < 4000L) {
            pumpMainLooper(40L)
            pending2 = currentEntries(vm).find { it.type == "command" && it.command == "npm install express" && it.commandStatus == "pending" }
            if (pending2 != null) break
            delay(25L)
        }
        assertNotNull("`npm install express` must pause at approval gate", pending2)
        vm.resolveCommandPermission(pending2!!, approved = true)
        awaitViewModelIdle(vm)

        assertEquals(listOf("git init", "npm install express"), executedCommands)

        // Verify cancelling a running `npm install` calls backend.cancel()
        vm.executeDirectShellCommand("npm install slow-pkg")
        var pendingSlow: TranscriptEntry? = null
        val t2 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t2 < 4000L) {
            pumpMainLooper(40L)
            pendingSlow = currentEntries(vm).find { it.type == "command" && it.command == "npm install slow-pkg" && it.commandStatus == "pending" }
            if (pendingSlow != null) break
            delay(25L)
        }
        assertNotNull(pendingSlow)
        vm.resolveCommandPermission(pendingSlow!!, approved = true)
        delay(80L)
        pumpMainLooper(50L)
        vm.stopRunningAgent()
        awaitViewModelIdle(vm)
        assertTrue("Stopping a running npm install must invoke ExecutionBackend.cancel()", cancelCalled)
    }

    class FakeTermux(
        override val backendType: ExecutionBackendType = ExecutionBackendType.TERMUX,
        var isAvailableResponse: Boolean = true,
        var currentCwd: String = "/data/data/com.termux/files/home"
    ) : ExecutionBackend {
        val executedCommands = mutableListOf<String>()
        var cancelCalled = false
        var execHandler: ((cmd: String, cwd: String, timeoutSec: Long) -> Flow<ExecEvent>)? = null

        override suspend fun isAvailable(): Boolean = isAvailableResponse

        override fun exec(
            cmd: String,
            cwd: String,
            env: Map<String, String>,
            timeoutSec: Long
        ): Flow<ExecEvent> {
            executedCommands.add(cmd)
            execHandler?.let { handler ->
                return handler(cmd, cwd, timeoutSec)
            }
            return flow {
                emit(ExecEvent.StdoutChunk("fake stdout for: $cmd\n"))
                emit(ExecEvent.Completed(exitCode = 0, durationMs = 10L))
            }
        }

        override fun cancel() {
            cancelCalled = true
        }

        override suspend fun describeEnvironment(
            cwd: String,
            workspaceMode: WorkspaceMode
        ): EnvironmentDescription = EnvironmentDescription(
            backendType = backendType,
            shellName = "FakeTermux bash",
            isAvailable = isAvailableResponse,
            summaryLabel = "Fake Termux (Test Double)",
            cwd = cwd.ifBlank { currentCwd },
            workspaceMode = workspaceMode
        )
    }

    @Test
    fun errorMapper_identifiesAllKnownFailurePatterns_andCreatesFriendlyActionCards() {
        // 1) dpkg was interrupted -> suggests dpkg --configure -a
        val dpkgCard = ErrorMapper.mapCommandOutputOrError(
            command = "pkg install -y git",
            exitCode = 1,
            stdout = "",
            stderr = "E: dpkg was interrupted, you must manually run 'dpkg --configure -a' to correct the problem."
        )
        assertEquals("dpkg_interrupted", dpkgCard.categoryId)
        assertTrue(dpkgCard.title.contains("dpkg", ignoreCase = true))
        assertEquals("dpkg --configure -a", dpkgCard.suggestedCommand)
        assertTrue(dpkgCard.actionTypes.contains(TermuxErrorActionType.COPY_COMMAND))

        // 2) Package repo / mirror failure -> suggests pkg update -y or termux-change-repo
        val repoCard = ErrorMapper.mapCommandOutputOrError(
            command = "pkg install nodejs",
            exitCode = 100,
            stdout = "",
            stderr = "E: Unable to locate package nodejs\nE: Failed to fetch https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs"
        )
        assertEquals("pkg_repo_stale", repoCard.categoryId)
        assertTrue(repoCard.title.contains("Outdated") || repoCard.title.contains("Mirror"))
        assertEquals("pkg update -y", repoCard.suggestedCommand)
        assertTrue(repoCard.numberedFixSteps.any { it.contains("termux-change-repo", ignoreCase = true) })

        // 3) CANNOT LINK EXECUTABLE or missing symbol -> suggests pkg upgrade -y
        val linkerCard = ErrorMapper.mapCommandOutputOrError(
            command = "node -v",
            exitCode = 1,
            stdout = "",
            stderr = "CANNOT LINK EXECUTABLE \"node\": cannot locate symbol \"__libc_init\" in libc.so"
        )
        assertEquals("cannot_link_executable", linkerCard.categoryId)
        assertTrue(linkerCard.title.contains("Shared Libraries") || linkerCard.title.contains("Termux"))
        assertEquals("pkg upgrade -y", linkerCard.suggestedCommand)

        // 4) Certificate verification / SSL failure -> suggests ca-certificates
        val sslCard = ErrorMapper.mapCommandOutputOrError(
            command = "git clone https://github.com/example/repo.git",
            exitCode = 128,
            stdout = "",
            stderr = "fatal: unable to access 'https://github.com/example/repo.git/': SSL certificate problem: unable to get local issuer certificate"
        )
        assertEquals("ssl_ca_missing", sslCard.categoryId)
        assertTrue(sslCard.title.contains("Certificates") || sslCard.title.contains("TLS"))
        assertEquals("pkg install -y ca-certificates openssl-tool", sslCard.suggestedCommand)

        // 5) npm EACCES / EPERM symlink on shared storage -> suggests bin-links=false or Termux home
        val npmSymlinkCard = ErrorMapper.mapCommandOutputOrError(
            command = "npm install express",
            exitCode = 1,
            stdout = "",
            stderr = "npm ERR! code EACCES\nnpm ERR! syscall symlink\nnpm ERR! path ../express/bin/express"
        )
        assertEquals("npm_shared_storage_symlink", npmSymlinkCard.categoryId)
        assertTrue(npmSymlinkCard.title.contains("npm Symlink", ignoreCase = true))
        assertTrue(npmSymlinkCard.numberedFixSteps.any { it.contains("bin-links") || it.contains("Termux Home") })

        // 6) git dubious ownership -> suggests git config --global --add safe.directory
        val gitSafeCard = ErrorMapper.mapCommandOutputOrError(
            command = "git status",
            exitCode = 128,
            stdout = "",
            stderr = "fatal: detected dubious ownership in repository at '/sdcard/Download/my-project'"
        )
        assertEquals("git_dubious_ownership", gitSafeCard.categoryId)
        assertTrue(gitSafeCard.title.contains("Dubious Ownership", ignoreCase = true))
        assertTrue(gitSafeCard.suggestedCommand.orEmpty().contains("safe.directory"))

        // 7) python externally-managed-environment -> suggests venv
        val pythonVenvCard = ErrorMapper.mapCommandOutputOrError(
            command = "pip install requests",
            exitCode = 1,
            stdout = "",
            stderr = "error: externally-managed-environment\n\n× This environment is externally managed"
        )
        assertEquals("python_externally_managed", pythonVenvCard.categoryId)
        assertTrue(pythonVenvCard.title.contains("Python", ignoreCase = true))
        assertTrue(pythonVenvCard.suggestedCommand.orEmpty().contains("venv"))

        // 8) Storage full
        val storageCard = ErrorMapper.mapCommandOutputOrError(
            command = "pkg install python",
            exitCode = 1,
            stdout = "",
            stderr = "E: You don't have enough free space in /data/data/com.termux/files/usr: No space left on device"
        )
        assertEquals("storage_full", storageCard.categoryId)
        assertTrue(storageCard.title.contains("No Space Left", ignoreCase = true))

        // 9) Network error
        val netCard = ErrorMapper.mapCommandOutputOrError(
            command = "curl https://example.com",
            exitCode = 6,
            stdout = "",
            stderr = "curl: (6) Could not resolve host: example.com"
        )
        assertEquals("network_error", netCard.categoryId)
        assertTrue(netCard.title.contains("Network", ignoreCase = true))

        // 10) Explicit typed error instances
        val notInstalledCard = ErrorMapper.mapCommandOutputOrError(
            command = "git status",
            exitCode = -1,
            stdout = "",
            stderr = "",
            explicitError = TermuxError.TermuxNotInstalled()
        )
        assertEquals("termux_not_installed", notInstalledCard.categoryId)

        val permDeniedCard = ErrorMapper.mapCommandOutputOrError(
            command = "git status",
            exitCode = -1,
            stdout = "",
            stderr = "",
            explicitError = TermuxError.PermissionDenied()
        )
        assertEquals("permission_denied", permDeniedCard.categoryId)

        val timeoutCard = ErrorMapper.mapCommandOutputOrError(
            command = "npm run dev",
            exitCode = -1,
            stdout = "",
            stderr = "",
            explicitError = TermuxError.Timeout(timeoutSec = 120L, command = "npm run dev")
        )
        assertEquals("timeout", timeoutCard.categoryId)
        assertTrue(timeoutCard.actionTypes.contains(TermuxErrorActionType.RE_RUN_AS_BACKGROUND_JOB))
    }

    @Test
    fun circuitBreaker_threeStrikesTransition_cooldownRecheck_andUserReset() {
        val breaker = BackendCircuitBreaker(failureThreshold = 3, cooldownMs = 60_000L)

        // Initial state: HEALTHY
        assertEquals(BackendHealthState.HEALTHY, breaker.state)
        assertFalse(breaker.isUnhealthy)

        // Strike 1 -> DEGRADED
        val s1 = breaker.recordInfrastructureFailure("Termux timeout 1")
        assertEquals(BackendHealthState.DEGRADED, s1)
        assertEquals(BackendHealthState.DEGRADED, breaker.state)
        assertFalse(breaker.isUnhealthy)

        // Strike 2 -> DEGRADED
        val s2 = breaker.recordInfrastructureFailure("Termux timeout 2")
        assertEquals(BackendHealthState.DEGRADED, s2)
        assertFalse(breaker.isUnhealthy)

        // Strike 3 -> UNHEALTHY (Circuit tripped!)
        val s3 = breaker.recordInfrastructureFailure("Termux RunCommandService unreachable")
        assertEquals(BackendHealthState.UNHEALTHY, s3)
        assertTrue(breaker.isUnhealthy)
        assertEquals("Termux RunCommandService unreachable", breaker.lastUnhealthyReason)

        // Structured result formatting
        val structuredResult = breaker.formatStructuredUnavailableToolResult()
        assertTrue(structuredResult.startsWith("Execution environment unavailable:"))
        assertTrue(structuredResult.contains("Termux RunCommandService unreachable"))

        // Auto-recheck allowed test:
        // Immediately after failure (< 60s cooldown): auto-recheck should NOT be allowed
        val now = System.currentTimeMillis()
        assertFalse(breaker.shouldAllowAutoRecheckBeforeAgentRun(now + 10_000L))

        // After cooldown elapses (>= 60s): auto-recheck IS allowed
        assertTrue(breaker.shouldAllowAutoRecheckBeforeAgentRun(now + 65_000L))

        // User taps "Reset" / "Retry" -> resets to HEALTHY
        breaker.resetOnUserAction()
        assertEquals(BackendHealthState.HEALTHY, breaker.state)
        assertFalse(breaker.isUnhealthy)
        assertEquals("", breaker.lastUnhealthyReason)

        // Success records keep it HEALTHY
        breaker.recordSuccess()
        assertEquals(BackendHealthState.HEALTHY, breaker.state)
    }

    @Test
    fun fallbackPolicy_strictlyEnforcesReadOnlySafeCommands() {
        // Safe read-only commands allowed on limited Android shell fallback
        val safeCommands = listOf(
            "ls",
            "ls -la",
            "cat README.md",
            "pwd",
            "df -h",
            "whoami",
            "uname -a",
            "echo nexus_ok",
            "which git",
            "head -n 10 file.txt"
        )
        for (cmd in safeCommands) {
            assertTrue("Safe command '$cmd' must be permitted on limited shell fallback", FallbackPolicy.canOfferLimitedShellFallback(cmd))
        }

        // Dangerous, write, install, network, and pipe/chained commands must be STRICTLY REJECTED
        val rejectedCommands = listOf(
            "rm -rf build",
            "mkdir -p new_folder",
            "touch index.js",
            "cp a b",
            "mv a b",
            "npm install express",
            "git clone https://example.com/repo.git",
            "git push origin main",
            "python script.py",
            "python3 app.py",
            "node index.js",
            "curl https://example.com",
            "wget https://example.com/file",
            "pkg install nodejs",
            "ls && rm -rf /",
            "echo 'hello' > out.txt",
            "cat file.txt | grep test",
            "ls; echo hi"
        )
        for (cmd in rejectedCommands) {
            assertFalse("Command '$cmd' must NOT be allowed on limited shell fallback", FallbackPolicy.canOfferLimitedShellFallback(cmd))
        }

        // Toolchain requirements detection
        assertTrue(FallbackPolicy.requiresTermuxToolchain("node -v"))
        assertTrue(FallbackPolicy.requiresTermuxToolchain("npm install"))
        assertTrue(FallbackPolicy.requiresTermuxToolchain("git status"))
        assertTrue(FallbackPolicy.requiresTermuxToolchain("python3 script.py"))
        assertTrue(FallbackPolicy.requiresTermuxToolchain("pip install requests"))
        assertTrue(FallbackPolicy.requiresTermuxToolchain("pkg update"))
        assertFalse(FallbackPolicy.requiresTermuxToolchain("ls -la"))
        assertFalse(FallbackPolicy.requiresTermuxToolchain("cat file.txt"))
    }

    class FakeTermuxEnvironmentProbe(
        var installed: Boolean = true,
        var permissionGranted: Boolean = true,
        var batteryExempt: Boolean = true,
        var storageWritable: Boolean = true,
        var pingResult: Pair<Boolean, String> = true to "ok",
        var toolsList: List<DetectedToolInfo> = listOf(
            DetectedToolInfo("node", "node", true, "v22.4.0"),
            DetectedToolInfo("npm", "npm", true, "10.8.1"),
            DetectedToolInfo("git", "git", true, "2.45.2"),
            DetectedToolInfo("python", "python", true, "3.11.9"),
            DetectedToolInfo("pip", "pip", true, "24.1.2")
        ),
        var rawCommandHandler: ((cmd: String, cwd: String) -> TermuxPluginResult)? = null
    ) : TermuxEnvironmentProbe {
        override fun isTermuxPackageInstalled(): Boolean = installed
        override fun isRunCommandPermissionGranted(): Boolean = permissionGranted
        override fun isTermuxBatteryOptimizationExempt(): Boolean = batteryExempt
        override fun isSharedStorageWritable(workspacePath: String): Boolean = storageWritable
        override suspend fun pingTermuxReachability(timeoutSec: Long): Pair<Boolean, String> = pingResult
        override suspend fun probeInstalledTools(): List<DetectedToolInfo> = toolsList
        override suspend fun runRawCommand(cmd: String, cwd: String, timeoutSec: Long): TermuxPluginResult {
            rawCommandHandler?.let { return it(cmd, cwd) }
            return TermuxPluginResult(stdout = "nexus_selftest_ok\n", stderr = "", exitCode = 0, errCode = 0)
        }
    }

    @Test
    fun termuxDiagnostics_caching_projectDetection_andEndToEndSelfTest() {
        runBlocking<Unit> {
            val fakeProbe = FakeTermuxEnvironmentProbe()

            // 1) Test project detection across marker files
            val tempProjectDir = File(System.getProperty("java.io.tmpdir"), "nexus_proj_test_${System.currentTimeMillis()}")
            tempProjectDir.mkdirs()
            try {
                File(tempProjectDir, "package.json").writeText("""{"name": "demo-app", "scripts": {"dev": "vite", "build": "vite build"}}""")
                File(tempProjectDir, "requirements.txt").writeText("fastapi\nuvicorn\n")
                File(tempProjectDir, "index.html").writeText("<!DOCTYPE html><html><body>Hello</body></html>")
                val gitDir = File(tempProjectDir, ".git")
                gitDir.mkdirs()

                val diagnostics = TermuxDiagnostics(probe = fakeProbe)

                val guideReport = diagnostics.inspectWorkspaceProjectGuide(
                    workspaceCwd = tempProjectDir.absolutePath,
                    detectedTools = fakeProbe.toolsList
                )
                val detectedProjects = guideReport.detectedTypes
                assertTrue("Must detect Node.js project", detectedProjects.contains(WorkspaceProjectType.NODE_JS))
                assertTrue("Must detect Python project", detectedProjects.contains(WorkspaceProjectType.PYTHON))
                assertTrue("Must detect Static HTML project", detectedProjects.contains(WorkspaceProjectType.STATIC_WEB))
                assertTrue("Must detect Git Repo", detectedProjects.contains(WorkspaceProjectType.GIT_REPO))
                assertTrue("Guide must offer recipes", guideReport.recipes.isNotEmpty())

                // 2) Test End-to-end self-test progress reporting
                fakeProbe.rawCommandHandler = { cmd, _ ->
                    when {
                        cmd.contains("node -e") -> TermuxPluginResult("node-ok-v22.4.0\n", "", 0, 0)
                        cmd.contains("git init") -> TermuxPluginResult("Initialized empty Git repository\n", "", 0, 0)
                        else -> TermuxPluginResult("nexus-selftest-ok\n", "", 0, 0)
                    }
                }

                val selfTestReport = diagnostics.runEndToEndSelfTest(workspaceCwd = tempProjectDir.absolutePath)

                assertTrue("Self test should pass with fakeProbe returning exit 0", selfTestReport.allPassed)
                assertEquals("Self test must execute all 4 steps", 4, selfTestReport.steps.size)
                assertTrue("All steps must report passed=true", selfTestReport.steps.all { it.passed })
                assertTrue("All steps must report non-negative durationMs", selfTestReport.steps.all { it.durationMs >= 0L })
                assertTrue("Total duration must be non-negative", selfTestReport.totalDurationMs >= 0L)
                assertEquals(
                    listOf("write_workspace_file", "node_eval", "git_init_temp", "cleanup_temp"),
                    selfTestReport.steps.map { it.stepId }
                )

                // 3) Diagnostics report caching: runs within 60s return cached instance unless forceRefresh = true
                val rep1 = diagnostics.runDiagnostics(
                    workspaceCwd = tempProjectDir.absolutePath,
                    workspaceMode = WorkspaceMode.SHARED_STORAGE,
                    forceRefresh = true
                )
                val rep2 = diagnostics.runDiagnostics(
                    workspaceCwd = tempProjectDir.absolutePath,
                    workspaceMode = WorkspaceMode.SHARED_STORAGE,
                    forceRefresh = false
                )
                assertEquals("Second call within 60s should return cached report timestamp", rep1.timestampMs, rep2.timestampMs)
            } finally {
                tempProjectDir.deleteRecursively()
            }
        }
    }

    @Test
    fun corruptedAndLargeResult_gracefullyHandledWithoutAppCrash() {
        // 1) Test corrupted or unexpected output strings into ErrorMapper
        val corruptOutput = "\u0000\u0001\u0002GarbageBinaryDump\u00FF\u00FE\u007F" + "random text".repeat(50)
        val presentation = ErrorMapper.mapCommandOutputOrError(
            command = "unknown_corrupt_command",
            exitCode = 139, // Segfault
            stdout = corruptOutput,
            stderr = "Segmentation fault (core dumped)"
        )
        assertNotNull(presentation)
        assertFalse(presentation.title.isBlank())
        assertFalse(presentation.oneLineCause.isBlank())
        assertTrue(presentation.numberedFixSteps.isNotEmpty())

        // 2) Test ResultTooLarge typed error
        val largeError = TermuxError.ResultTooLarge(
            totalBytes = 1_500_000L,
            truncatedPreview = "preview tail..."
        )
        val largeCard = ErrorMapper.mapCommandOutputOrError(
            command = "cat giant_file.log",
            exitCode = 0,
            stdout = "",
            stderr = "",
            explicitError = largeError
        )
        assertEquals("result_too_large", largeCard.categoryId)
        assertTrue(largeCard.title.contains("Binder IPC") || largeCard.title.contains("Too Large") || largeCard.title.contains("Large Output"))
    }

    @Test
    fun jobManager_portDetectionAndReadySignals_andLoopbackPreviewValidation() {
        // 1) Port extraction from logs
        assertEquals(3000, ServerLogAnalyzer.extractPortFromLogs("Server listening on port 3000"))
        assertEquals(3000, ServerLogAnalyzer.extractPortFromLogs("http://localhost:3000"))
        assertEquals(8080, ServerLogAnalyzer.extractPortFromLogs("Serving HTTP on 127.0.0.1 port 8080 ..."))
        assertEquals(5173, ServerLogAnalyzer.extractPortFromLogs("  ➜  Local:   http://localhost:5173/"))
        assertNull(ServerLogAnalyzer.extractPortFromLogs("Build finished in 4s with no errors"))

        // 2) Port extraction from command flags
        assertEquals(8000, ServerLogAnalyzer.extractPortFromCommand("python -m http.server 8000"))
        assertEquals(3001, ServerLogAnalyzer.extractPortFromCommand("vite --port 3001"))
        assertEquals(4000, ServerLogAnalyzer.extractPortFromCommand("PORT=4000 node server.js"))
        assertNull(ServerLogAnalyzer.extractPortFromCommand("git status"))

        // 3) Ready pattern detection
        assertTrue(ServerLogAnalyzer.hasReadySignal("Ready in 320ms"))
        assertTrue(ServerLogAnalyzer.hasReadySignal("listening on http://localhost:3000"))
        assertTrue(ServerLogAnalyzer.hasReadySignal("serving http on 0.0.0.0:8080"))
        assertFalse(ServerLogAnalyzer.hasReadySignal("transpiling typescript files 35%"))

        // 4) Loopback URL security validation (only 127.0.0.1 and localhost allowed)
        assertTrue(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("http://127.0.0.1:3000"))
        assertTrue(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("http://localhost:8080/app"))
        assertTrue(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("https://127.0.0.1:8443"))
        assertFalse(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("http://192.168.1.10:3000"))
        assertFalse(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("https://external-phishing.com"))
        assertFalse(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("file:///data/data/com.termux/files/home"))
        assertFalse(ServerLogAnalyzer.isLoopbackPreviewUrlAllowed("javascript:alert(1)"))
    }

    @Test
    fun jobManager_stopEscalationScript_andJobStorePersistence() = runBlocking {
        // 1) Stop escalation script includes SIGTERM, loop check, and SIGKILL
        val script = JobManager.buildStopEscalationScript(9999)
        assertTrue(script.contains("kill -TERM -9999"))
        assertTrue(script.contains("kill -TERM 9999"))
        assertTrue(script.contains("kill -KILL -9999"))
        assertTrue(script.contains("STOPPED"))

        // 2) JobStore serialization & deserialization round-trip
        val testJobs = listOf(
            ManagedJobRecord(
                id = "job_test1",
                command = "python -m http.server 8000",
                cwd = "/workspace",
                startedAtMs = 1700000000000L,
                pid = 1234,
                port = 8000,
                state = ManagedJobState.RUNNING,
                logLines = listOf("Serving HTTP on 0.0.0.0 port 8000")
            ),
            ManagedJobRecord(
                id = "job_test2",
                command = "npm run dev",
                cwd = "/workspace/web",
                startedAtMs = 1700000005000L,
                pid = 5678,
                port = 3000,
                state = ManagedJobState.STOPPED,
                exitCode = 0,
                logLines = listOf("Vite dev server stopped")
            )
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JobStore(context)
        store.saveJobs(testJobs)
        val loaded = store.loadJobs()
        assertEquals(2, loaded.size)
        assertEquals("job_test1", loaded[0].id)
        assertEquals(8000, loaded[0].port)
        assertEquals(ManagedJobState.RUNNING, loaded[0].state)
        assertEquals(1234, loaded[0].pid)
        assertEquals("job_test2", loaded[1].id)
        assertEquals(ManagedJobState.STOPPED, loaded[1].state)
    }

    @Test
    fun toolCallParser_backgroundJobGrammarAndAutoDetection() {
        // 1) Explicit start_job tool call
        val startParsed = ToolCallParser.parse("```sh-exec\nstart_job(\"npm run dev\")\n```")
        assertTrue(startParsed is ToolCallParseResult.Success)
        val startCall = (startParsed as ToolCallParseResult.Success).toolCall
        assertEquals(ToolActionKind.START_JOB, startCall.actionKind)
        assertEquals("npm run dev", startCall.command)
        assertTrue(startCall.runsInBackground)

        // 2) Explicit job_status tool call
        val statusParsed = ToolCallParser.parse("```sh-exec\njob_status(\"job_xyz\")\n```")
        assertTrue(statusParsed is ToolCallParseResult.Success)
        val statusCall = (statusParsed as ToolCallParseResult.Success).toolCall
        assertEquals(ToolActionKind.JOB_STATUS, statusCall.actionKind)
        assertEquals("job_xyz", statusCall.jobId)
        assertEquals(CommandRiskLevel.LOW, statusCall.riskLevel)

        // 3) Explicit job_logs tool call
        val logsParsed = ToolCallParser.parse("```sh-exec\njob_logs(\"job_xyz\", 80)\n```")
        assertTrue(logsParsed is ToolCallParseResult.Success)
        val logsCall = (logsParsed as ToolCallParseResult.Success).toolCall
        assertEquals(ToolActionKind.JOB_LOGS, logsCall.actionKind)
        assertEquals("job_xyz", logsCall.jobId)
        assertEquals(80, logsCall.logLinesCount)

        // 4) Explicit stop_job tool call
        val stopParsed = ToolCallParser.parse("```sh-exec\nstop_job(\"job_xyz\")\n```")
        assertTrue(stopParsed is ToolCallParseResult.Success)
        val stopCall = (stopParsed as ToolCallParseResult.Success).toolCall
        assertEquals(ToolActionKind.STOP_JOB, stopCall.actionKind)
        assertEquals("job_xyz", stopCall.jobId)
        assertEquals(CommandRiskLevel.LOW, stopCall.riskLevel)

        // 5) Auto-detection of server command into background job
        val serverParsed = ToolCallParser.parse("```sh-exec\npython -m http.server 8000\n```")
        assertTrue(serverParsed is ToolCallParseResult.Success)
        val serverCall = (serverParsed as ToolCallParseResult.Success).toolCall
        assertTrue(serverCall.runsInBackground)
        assertEquals(ToolActionKind.START_JOB, serverCall.actionKind)
    }

    @Test
    fun jobManager_startJob_andReconciliationWithFakeBackend() = runBlocking {
        val executedCommands = mutableListOf<String>()
        val fakeBackend = object : ExecutionBackend {
            override val backendType: ExecutionBackendType = ExecutionBackendType.TERMUX
            override suspend fun isAvailable(): Boolean = true
            override fun exec(
                cmd: String,
                cwd: String,
                env: Map<String, String>,
                timeoutSec: Long
            ): Flow<ExecEvent> = flow {
                executedCommands.add(cmd)
                if (cmd.contains("NEXUS_POLL_META")) {
                    emit(ExecEvent.StdoutChunk("NEXUS_POLL_META:1:100:\nServer listening on http://localhost:3000\n"))
                    emit(ExecEvent.Completed(0, 10L))
                } else if (cmd.contains("mkdir -p")) {
                    emit(ExecEvent.StdoutChunk("NEXUS_JOB_PID:7777\n"))
                    emit(ExecEvent.Completed(0, 10L))
                } else if (cmd.contains("kill -0")) {
                    emit(ExecEvent.Completed(0, 10L))
                } else {
                    emit(ExecEvent.Completed(0, 10L))
                }
            }
            override fun cancel() {}
            override suspend fun describeEnvironment(
                cwd: String,
                workspaceMode: WorkspaceMode
            ): EnvironmentDescription = EnvironmentDescription(
                backendType = ExecutionBackendType.TERMUX,
                shellName = "Fake Termux",
                isAvailable = true,
                summaryLabel = "Ready",
                cwd = cwd,
                workspaceMode = workspaceMode
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JobStore(context)
        val manager = JobManager(
            backendProvider = { fakeBackend },
            jobStore = store,
            portVerifier = { true }
        )

        // Start job
        val outcome = manager.startJob(
            command = "npm run dev",
            cwd = "/test_workspace",
            readyWaitMs = 1000L,
            pollIntervalMs = 100L
        )
        assertTrue("Job launch should succeed", outcome.success)
        assertNotNull(outcome.job)
        assertEquals(7777, outcome.job?.pid)
        assertEquals(ManagedJobState.RUNNING, outcome.job?.state)
        assertEquals(1, manager.jobs.value.size)

        // Test reconciliation on start
        val reconciled = manager.reconcilePersistedJobsOnStart()
        assertEquals(1, reconciled.size)
        assertEquals(ManagedJobState.RUNNING, reconciled[0].state)
    }

    @Test
    fun runA_editAndResubmitPrompt_truncatesLaterMessages_supports10sUndo_andRespectsGuestQuota() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shellBridge = LocalShellBridge(context)
        val localLlmManager = LocalLlmManager(context)
        val userApiKeyStore = UserApiKeyStore(context)
        val notificationHelper = NexusNotificationHelper(context)
        userApiKeyStore.setApiKey("gemini", null, "user-test-gemini-key")
        userApiKeyStore.persistSelectedProviderAndModel("gemini", "gemini-2.5-flash")

        val fakeTransport = object : ProviderTransport {
            override fun streamGeminiRest(
                modelId: String,
                apiKey: String,
                maxTokens: Int,
                systemInstruction: String,
                conversationTurns: List<Pair<String, String>>
            ): Flow<String> {
                val lastUserText = conversationTurns.lastOrNull { it.first == "user" }?.second.orEmpty()
                return flowOf("Agent reply to: $lastUserText")
            }
            override fun streamAnthropicRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf<String>()
            override fun streamOpenAiCompatibleRest(provider: String, endpointUrl: String, modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>) = flowOf<String>()
            override fun streamLocalOnDevice(modelId: String, userPrompt: String, currentCwd: String, compactSystemPrompt: String, recentCommandOutputs: List<Pair<String, String>>) = flowOf<String>()
        }

        val vm = NexusViewModel(
            repository = null,
            shellBridge = shellBridge,
            localLlmManager = localLlmManager,
            userApiKeyStore = userApiKeyStore,
            notificationHelper = notificationHelper,
            currentUserId = "test_edit_resubmit_user",
            isGuestMode = true,
            customTransport = fakeTransport,
            customExecutionBackend = FakeTermux()
        )

        // Submit Turn 1
        vm.submitDirective("First question")
        awaitViewModelIdle(vm)

        // Submit Turn 2
        vm.submitDirective("Second question")
        awaitViewModelIdle(vm)

        val beforeEditEntries = currentEntries(vm)
        val userEntriesBefore = beforeEditEntries.filter { it.type == "user" }
        assertEquals("Should have 2 user prompts before editing", 2, userEntriesBefore.size)
        assertEquals("Guest quota should be 2 after 2 prompts", 2, vm.guestDirectivesUsed.value)

        // Edit the FIRST user prompt -> should truncate first prompt + its reply + second prompt + its reply, and resubmit
        val firstUserEntry = userEntriesBefore.first()
        vm.editAndResubmitPrompt(firstUserEntry, "First question (edited)")
        awaitViewModelIdle(vm)

        val afterEditEntries = currentEntries(vm)
        val userEntriesAfter = afterEditEntries.filter { it.type == "user" }
        assertEquals("Later messages must be truncated so only the edited prompt remains", 1, userEntriesAfter.size)
        assertEquals("First question (edited)", userEntriesAfter.single().text)
        assertTrue(
            "Agent reply must correspond to the edited prompt",
            afterEditEntries.any { it.type == "agent" && it.text.contains("First question (edited)") }
        )
        assertEquals("Resubmitting consumes 1 guest turn (2 + 1 = 3)", 3, vm.guestDirectivesUsed.value)
        assertEquals(
            "Live notification banner must offer 10s Undo snackbar",
            "edit_truncate_undo",
            vm.liveNotificationBanner.value?.kind
        )

        // Trigger Undo within 10s -> restores original 2 turns and refunds the 1 guest turn consumed by the cancelled resubmit
        vm.undoEditAndResubmit()
        awaitViewModelIdle(vm)

        val restoredEntries = currentEntries(vm)
        val restoredUserPrompts = restoredEntries.filter { it.type == "user" }.map { it.text }
        assertEquals(listOf("First question", "Second question"), restoredUserPrompts)
        assertEquals("Undo refunds the 1 turn from the cancelled resubmit back to 2", 2, vm.guestDirectivesUsed.value)
    }

    @Test
    fun runB_workspacePathValidator_rootWriteBlocking_highRiskConfigPaths_andSlugCollisionResolution() {
        // 1) Slug sanitization & collision resolution (Amendments #7 & #8)
        assertEquals("express-api-server", com.example.domain.WorkspacePaths.sanitizeSlug("  Express API_Server!! "))
        assertEquals(
            "build-fastapi-todo-service",
            com.example.domain.WorkspacePaths.proposeSlugFromPrompt("Please create a folder and build a FastAPI todo service")
        )
        val collision = com.example.domain.WorkspacePaths.resolveSlugCollision(
            desiredSlug = "express-app",
            existingSlugs = setOf("express-app", "express-app-2")
        )
        assertTrue(collision.hasCollision)
        assertEquals("express-app", collision.sanitizedSlug)
        assertEquals("express-app-3", collision.nextAvailableSlug)

        // 2) Path validator for Termux: allows /storage/emulated/0/** and Termux home; rejects /data/data/* and /data/user/*
        val validShared = com.example.domain.WorkspacePathValidator.validateForTermux("/storage/emulated/0/Nexus/my-app")
        assertTrue(validShared is com.example.domain.PathValidationOutcome.Allowed)
        assertEquals(
            WorkspaceMode.SHARED_STORAGE,
            (validShared as com.example.domain.PathValidationOutcome.Allowed).mode
        )

        val validTermuxHome = com.example.domain.WorkspacePathValidator.validateForTermux("/data/data/com.termux/files/home/nexus_workspace/my-app")
        assertTrue(validTermuxHome is com.example.domain.PathValidationOutcome.Allowed)
        assertEquals(
            WorkspaceMode.TERMUX_HOME,
            (validTermuxHome as com.example.domain.PathValidationOutcome.Allowed).mode
        )

        val rejectedAppPrivate = com.example.domain.WorkspacePathValidator.validateForTermux("/data/user/0/com.aistudio.nexusagent/files/nexus_workspace")
        assertTrue(rejectedAppPrivate is com.example.domain.PathValidationOutcome.Rejected)

        val rejectedOtherData = com.example.domain.WorkspacePathValidator.validateForTermux("/data/data/com.other.app/files")
        assertTrue(rejectedOtherData is com.example.domain.PathValidationOutcome.Rejected)

        // 3) Root write blocking by resolved path (Amendment #1):
        //    Blocks ALL writes in /storage/emulated/0/Nexus root, allows reads in root, allows writes in /storage/emulated/0/Nexus/<slug>
        val rootReadDecision = com.example.domain.WorkspacePathValidator.evaluateCommandJail(
            command = "ls -la",
            activeCwd = "/storage/emulated/0/Nexus",
            boundProjectDir = null
        )
        assertEquals(com.example.domain.CommandJailDecision.Allowed, rootReadDecision)

        val rootWriteTouchDecision = com.example.domain.WorkspacePathValidator.evaluateCommandJail(
            command = "touch foo.txt",
            activeCwd = "/storage/emulated/0/Nexus",
            boundProjectDir = null
        )
        assertTrue(
            "touch in /storage/emulated/0/Nexus root must be blocked by path",
            rootWriteTouchDecision is com.example.domain.CommandJailDecision.BlockedRootWrite
        )

        val rootWriteRedirectDecision = com.example.domain.WorkspacePathValidator.evaluateCommandJail(
            command = "echo hi > /storage/emulated/0/Nexus/test.txt",
            activeCwd = "/storage/emulated/0/Nexus/my-project",
            boundProjectDir = "/storage/emulated/0/Nexus/my-project"
        )
        assertTrue(
            "Redirect directly into /storage/emulated/0/Nexus/test.txt root must be blocked by path",
            rootWriteRedirectDecision is com.example.domain.CommandJailDecision.BlockedRootWrite
        )

        val projectWriteDecision = com.example.domain.WorkspacePathValidator.evaluateCommandJail(
            command = "npm init -y",
            activeCwd = "/storage/emulated/0/Nexus/my-project",
            boundProjectDir = "/storage/emulated/0/Nexus/my-project"
        )
        assertEquals(com.example.domain.CommandJailDecision.Allowed, projectWriteDecision)

        // 4) Best-effort jail & HIGH-risk Termux config paths (Amendment #2):
        //    Writes to ~/.bashrc, ~/.profile, ~/.termux/, and $PREFIX are HIGH risk
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("echo 'alias ll=ls' >> ~/.bashrc"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("sed -i 's/foo/bar/' ~/.profile"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("cp custom.prop ~/.termux/termux.properties"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("rm -f \$PREFIX/bin/node"))
        assertEquals(CommandRiskLevel.HIGH, ToolCallParser.classifyRisk("touch /data/data/com.termux/files/usr/bin/malicious"))

        // Upward traversal '..' is rejected by evaluateCommandJail
        val traversalDecision = com.example.domain.WorkspacePathValidator.evaluateCommandJail(
            command = "cat ../secret.txt",
            activeCwd = "/storage/emulated/0/Nexus/my-project",
            boundProjectDir = "/storage/emulated/0/Nexus/my-project"
        )
        assertTrue(traversalDecision is com.example.domain.CommandJailDecision.BlockedTraversalOrSymlinkEscape)
    }

    @Test
    fun runB_cleanBinLinksSingleRetry_symlinkMoveOffer_andJobManagerTermuxHomeStorage() {
        // 1) Amendment #5: Clean bin-links single retry & symlink/noexec detection
        //    Normal try first (forceNoBinLinks = false by default)
        val defaultEnv = com.example.domain.ExecutionTimeouts.buildSafeEnvironment(
            userEnv = emptyMap(),
            workspaceMode = WorkspaceMode.SHARED_STORAGE
        )
        assertFalse(
            "First npm install attempt must NOT set npm_config_bin_links=false upfront",
            defaultEnv.containsKey("npm_config_bin_links")
        )
        assertEquals("/data/data/com.termux/files/home/.cache/npm", defaultEnv["npm_config_cache"])
        assertEquals("/data/data/com.termux/files/home/.cache/pip", defaultEnv["PIP_CACHE_DIR"])

        val retryEnv = com.example.domain.ExecutionTimeouts.buildSafeEnvironment(
            userEnv = emptyMap(),
            workspaceMode = WorkspaceMode.SHARED_STORAGE,
            forceNoBinLinks = true
        )
        assertEquals("false", retryEnv["npm_config_bin_links"])

        // Symlink error triggers retry & move offer; bare EACCES without symlink/noexec does NOT trigger symlink move offer
        assertTrue(
            com.example.domain.WorkspaceCommandAdapter.isNpmSymlinkFailure(
                command = "npm install vite",
                stdout = "",
                stderr = "npm ERR! code EPERM\nnpm ERR! syscall symlink\nnpm ERR! path ../vite/bin/vite.js"
            )
        )
        assertTrue(
            com.example.domain.WorkspaceCommandAdapter.shouldOfferMoveToTermuxHomeForSymlinkOrNoexec(
                command = "npm run build",
                stdout = "",
                stderr = "sh: 1: vite: Permission denied\n/storage/emulated/0/Nexus/app/node_modules/.bin/vite: not found",
                exitCode = 127
            )
        )
        assertFalse(
            "Bare unrelated EACCES must NOT trigger the symlink Move-to-Termux-Home card",
            com.example.domain.WorkspaceCommandAdapter.shouldOfferMoveToTermuxHomeForSymlinkOrNoexec(
                command = "cat /root/protected.txt",
                stdout = "",
                stderr = "cat: /root/protected.txt: Permission denied (EACCES)",
                exitCode = 1
            )
        )

        // Shared storage command adaptation: ./script.sh -> bash script.sh, python venv in $HOME/.venvs/<slug>
        assertEquals(
            "bash ./setup.sh --fast",
            com.example.domain.WorkspaceCommandAdapter.adaptCommandForSharedStorage("./setup.sh --fast", "demo-app")
        )
        val adaptedVenv = com.example.domain.WorkspaceCommandAdapter.adaptCommandForSharedStorage(
            "python3 -m venv .venv",
            "demo-app"
        )
        assertTrue(
            "Python venv on shared storage must redirect to Termux home .venvs/<project>",
            adaptedVenv.contains("/data/data/com.termux/files/home/.venvs/demo-app")
        )

        // 2) Task 3: JobManager stores job logs & pid files in Termux home (/data/data/com.termux/files/home/.nexus/jobs/<id>), never in shared workspace
        assertEquals(
            "/data/data/com.termux/files/home/.nexus/jobs/job_123",
            JobManager.jobDirPath("job_123")
        )
        assertFalse(
            "Job directory must never be inside /storage/emulated/0/Nexus",
            JobManager.jobDirPath("job_123").startsWith("/storage/emulated/0")
        )
    }

    @Test
    fun runC_oneWayProjectMirrorManager_excludesGitAndTrash_mtimeSizeCheck_conflictDetection_andMissingManifestRebuild() = runBlocking {
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "nexus_mirror_test_${System.currentTimeMillis()}")
        val termuxProjectDir = File(tempRoot, "termux_home/nexus_workspace/demo-proj")
        val sharedProjectDir = File(tempRoot, "shared/Nexus/demo-proj")
        val manifestFile = File(tempRoot, "termux_home/.nexus/mirrors/demo-proj/manifest.json")
        val backupRoot = File(tempRoot, "termux_home/.nexus/mirrors/demo-proj/backups")
        termuxProjectDir.mkdirs()
        sharedProjectDir.mkdirs()

        try {
            // Create source files, excluded folders (.git, node_modules, .nexus-trash), and secret file (.env)
            File(termuxProjectDir, "index.js").writeText("console.log('v1');\n")
            File(termuxProjectDir, "README.md").writeText("# Demo Project\n")
            File(termuxProjectDir, ".env").writeText("SECRET_KEY=do_not_mirror\n")
            File(termuxProjectDir, ".git").apply { mkdirs(); File(this, "HEAD").writeText("ref: refs/heads/main\n") }
            File(termuxProjectDir, "node_modules/pkg").apply { mkdirs(); File(this, "index.js").writeText("module.exports = {};") }

            val mirrorManager = com.example.domain.ProjectMirrorManager()
            val defaultConfig = com.example.domain.MirrorConfig(
                includeGitFolder = false,
                includeBuildOutputs = false,
                mirrorDeletions = false
            )

            // 1) Initial sync copies index.js & README.md, excludes .git, node_modules, and .env
            val res1 = mirrorManager.syncDirectoryPair(
                projectSlug = "demo-proj",
                sourceDir = termuxProjectDir,
                targetDir = sharedProjectDir,
                manifestFile = manifestFile,
                config = defaultConfig
            )
            assertTrue("Initial mirror sync should succeed", res1.success)
            assertTrue(File(sharedProjectDir, "index.js").exists())
            assertTrue(File(sharedProjectDir, "README.md").exists())
            assertFalse(".git must be excluded by default", File(sharedProjectDir, ".git").exists())
            assertFalse("node_modules must never be mirrored", File(sharedProjectDir, "node_modules").exists())
            assertFalse(".env secret file must never be mirrored", File(sharedProjectDir, ".env").exists())
            assertTrue("Manifest file must be written", manifestFile.exists())

            // 2) Delete README.md in Termux home with mirrorDeletions = false -> moves to .nexus-trash/<timestamp>/README.md
            File(termuxProjectDir, "README.md").delete()
            val res2 = mirrorManager.syncDirectoryPair(
                projectSlug = "demo-proj",
                sourceDir = termuxProjectDir,
                targetDir = sharedProjectDir,
                manifestFile = manifestFile,
                config = defaultConfig
            )
            assertTrue(res2.success)
            assertFalse("Deleted file must be removed from active shared path", File(sharedProjectDir, "README.md").exists())
            val trashDir = File(sharedProjectDir, ".nexus-trash")
            assertTrue("Deleted file must be staged inside .nexus-trash when mirrorDeletions=false", trashDir.exists() && trashDir.walkTopDown().any { it.name == "README.md" })

            // 3) External edit in shared folder -> triggers Conflict (and ignores .nexus-trash)
            File(sharedProjectDir, "index.js").writeText("console.log('edited externally in shared storage');\n")
            val res3 = mirrorManager.syncDirectoryPair(
                projectSlug = "demo-proj",
                sourceDir = termuxProjectDir,
                targetDir = sharedProjectDir,
                manifestFile = manifestFile,
                config = defaultConfig
            )
            assertEquals("External modification in shared folder must report 1 conflict", 1, res3.conflicts.size)
            assertEquals("index.js", res3.conflicts.single().relativePath)
            assertEquals("console.log('v1');\n", File(termuxProjectDir, "index.js").readText())

            // 4) Rebuild missing manifest: delete manifestFile when index.js differs -> detects conflict on index.js
            manifestFile.delete()
            val resRebuild = mirrorManager.syncDirectoryPair(
                projectSlug = "demo-proj",
                sourceDir = termuxProjectDir,
                targetDir = sharedProjectDir,
                manifestFile = manifestFile,
                config = defaultConfig
            )
            assertEquals("Missing manifest rebuild must flag differing file as conflict", 1, resRebuild.conflicts.size)
            assertEquals("index.js", resRebuild.conflicts.single().relativePath)

            // 5) Import from shared to Termux home backs up Termux-home copy first
            val importRes = mirrorManager.importDirectoryPairWithBackup(
                projectSlug = "demo-proj",
                sharedDir = sharedProjectDir,
                termuxDir = termuxProjectDir,
                backupRootDir = backupRoot,
                manifestFile = manifestFile,
                config = defaultConfig
            )
            assertTrue("Import from shared should succeed", importRes.success)
            assertEquals(
                "Termux home copy must be updated with shared file content after import",
                "console.log('edited externally in shared storage');\n",
                File(termuxProjectDir, "index.js").readText()
            )
            assertTrue(
                "Termux-home copy must be backed up before import",
                backupRoot.exists() && backupRoot.walkTopDown().any { it.name == "index.js" && it.readText().contains("v1") }
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}


