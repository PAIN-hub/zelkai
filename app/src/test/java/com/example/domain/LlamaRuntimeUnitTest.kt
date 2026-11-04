package com.example.domain

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.arm.aichat.InferenceEngine
import com.arm.aichat.NativeLoadConfig
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.example.data.model.TranscriptEntry
import com.example.ui.nexus.NexusViewModel
import com.example.ui.nexus.UiState
import com.example.util.NexusNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LlamaRuntimeUnitTest {

    /**
     * Reusable [FakeEngine] implementing [LocalInferenceEngine] for deterministic unit testing.
     */
    class FakeEngine(
        private val runtimeInstalled: Boolean = true,
        var simulatedErrorOnLoad: TypedLocalInferenceException? = null,
        var simulatedErrorOnGenerate: TypedLocalInferenceException? = null,
        var tokensToEmit: List<String> = listOf("Hello", " from", " FakeEngine", "!")
    ) : LocalInferenceEngine {
        private val _isLoaded = MutableStateFlow(false)
        override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

        var loadedPath: String? = null
        var lastLoadParams: InferenceParams? = null
        var lastGenerateParams: InferenceParams? = null
        val stopRequested = AtomicBoolean(false)
        var unloadCount = 0
        var trimMemoryLevels = mutableListOf<Int>()

        override suspend fun load(path: String, params: InferenceParams): Result<Unit> {
            simulatedErrorOnLoad?.let { return Result.failure(it) }
            loadedPath = path
            lastLoadParams = params
            _isLoaded.value = true
            return Result.success(Unit)
        }

        override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
            simulatedErrorOnGenerate?.let { throw it }
            lastGenerateParams = params
            stopRequested.set(false)
            for (token in tokensToEmit) {
                if (stopRequested.get()) break
                emit(token)
            }
        }

        override fun stop() {
            stopRequested.set(true)
        }

        override fun unload() {
            unloadCount++
            _isLoaded.value = false
        }

        override fun info(): EngineInfo = EngineInfo(
            name = if (runtimeInstalled) "FakeLlamaEngine (llama.cpp)" else "StubEngine (No Native Runtime)",
            version = if (runtimeInstalled) "b12020" else "0.0-stub",
            isRuntimeInstalled = runtimeInstalled,
            statusMessage = if (runtimeInstalled) {
                "llama.cpp b12020 · CPU (NEON, DotProd, FP16) · arm64-v8a"
            } else {
                "On-device GGUF inference needs an ARM64 (arm64-v8a) phone."
            },
            llamaCppTag = "b12020",
            backendInfo = "CPU (NEON, DotProd, FP16)",
            cpuFeatures = "NEON, DotProd, FP16",
            abi = if (runtimeInstalled) "arm64-v8a" else "x86_64"
        )

        override fun countTokens(text: String): Int {
            if (text.isBlank()) return 0
            return text.trim().split(Regex("\\s+")).size
        }

        override fun onTrimMemory(level: Int) {
            trimMemoryLevels.add(level)
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                _isLoaded.value = false
            }
        }
    }

    /**
     * Fake JNI [InferenceEngine] for testing [LlamaEngine] load guards, chat templates, and cancellation
     * without requiring the ARM64 `.so` on host JVM/x86_64.
     */
    private class FakeNativeBinding(
        var hasChatTemplateFlag: Boolean = true,
        var tokenBytesSequence: List<ByteArray> = listOf("Hello".toByteArray(Charsets.UTF_8))
    ) : InferenceEngine {
        private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
        override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

        val abortFlag = AtomicBoolean(false)
        var loadCalledCount = 0
        var cleanUpCalledCount = 0
        var lastUseMmap: Boolean = true
        var lastFlashAttn: Boolean = false
        var lastKvQ8: Boolean = true

        override suspend fun loadModel(pathToModel: String) {
            loadModel(pathToModel, NativeLoadConfig())
        }

        override suspend fun loadModel(pathToModel: String, config: NativeLoadConfig) {
            loadCalledCount++
            lastUseMmap = config.useMmap
            lastFlashAttn = config.flashAttention
            lastKvQ8 = config.kvCacheQ8_0
            _state.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {}

        override suspend fun updateSampler(
            temperature: Float,
            topP: Float,
            topK: Int,
            repeatPenalty: Float,
            grammar: String
        ) {}

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
            abortFlag.set(false)
            for (bytes in tokenBytesSequence) {
                if (abortFlag.get()) break
                emit(String(bytes, Charsets.UTF_8))
            }
        }

        override fun sendUserPromptBytes(message: String, predictLength: Int): Flow<ByteArray> = flow {
            abortFlag.set(false)
            for (bytes in tokenBytesSequence) {
                if (abortFlag.get()) break
                emit(bytes)
                if (tokenBytesSequence.size > 10) {
                    delay(30L)
                }
            }
        }

        override fun abortGeneration() {
            abortFlag.set(true)
        }

        override fun countTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)

        override fun getSystemInfo(): String = "CPU : NEON = 1 | ARM_FMA = 1 | FP16_VA = 1 | DOTPROD = 1"

        override fun hasChatTemplate(): Boolean = hasChatTemplateFlag

        override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = "bench-ok"

        override fun cleanUp() {
            cleanUpCalledCount++
            _state.value = InferenceEngine.State.Initialized
        }

        override fun destroy() {
            _state.value = InferenceEngine.State.Uninitialized
        }
    }

    private fun buildMetadataReader(
        arch: String = "qwen2",
        contextLen: Int = 4096,
        chatTemplate: String? = "<|im_start|>system\n{{system}}<|im_end|>"
    ): GgufMetadataReader = object : GgufMetadataReader {
        override suspend fun ensureSourceFileFormat(file: File): Boolean = true
        override suspend fun ensureSourceFileFormat(context: Context, uri: Uri): Boolean = true
        override suspend fun readStructuredMetadata(input: InputStream): GgufMetadata {
            return GgufMetadata(
                version = GgufMetadata.GgufVersion.VALIDATED_V3,
                tensorCount = 128L,
                kvCount = 24L,
                basic = GgufMetadata.BasicInfo(
                    uuid = "test-uuid",
                    name = "TestModel"
                ),
                architecture = GgufMetadata.ArchitectureInfo(
                    architecture = arch,
                    fileType = 1
                ),
                dimensions = GgufMetadata.DimensionsInfo(
                    contextLength = contextLen
                ),
                tokenizer = GgufMetadata.TokenizerInfo(
                    model = "gpt2",
                    chatTemplate = chatTemplate
                )
            )
        }
    }

    @Test
    fun engineProvider_selectsNativeOnArm64_andFallsBackToStubOnUnsatisfiedLinkSecurityNonArm64OrEmulator() {
        val fakeNative = FakeEngine(runtimeInstalled = true)

        // 1) Physical ARM64 device -> selects native engine
        val selectedArm64 = EngineProvider.createEngineWithFallback(
            supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a"),
            isEmulator = false,
            nativeLoader = { fakeNative }
        )
        assertTrue("Physical ARM64 must select real engine", selectedArm64.info().isRuntimeInstalled)
        assertEquals("b12020", selectedArm64.info().llamaCppTag)
        assertEquals("arm64-v8a", selectedArm64.info().abi)

        // 2) UnsatisfiedLinkError -> falls back to StubEngine without crashing
        val fallbackUnsatisfied = EngineProvider.createEngineWithFallback(
            supportedAbis = arrayOf("arm64-v8a"),
            isEmulator = false,
            nativeLoader = { throw UnsatisfiedLinkError("dlopen failed: libai-chat.so not found") }
        )
        assertTrue(fallbackUnsatisfied is StubEngine)
        assertFalse(fallbackUnsatisfied.info().isRuntimeInstalled)
        assertTrue(fallbackUnsatisfied.info().statusMessage.contains("ARM64", ignoreCase = true))

        // 3) SecurityException -> falls back to StubEngine without crashing
        val fallbackSecurity = EngineProvider.createEngineWithFallback(
            supportedAbis = arrayOf("arm64-v8a"),
            isEmulator = false,
            nativeLoader = { throw SecurityException("SELinux execmem denied") }
        )
        assertTrue(fallbackSecurity is StubEngine)
        assertFalse(fallbackSecurity.info().isRuntimeInstalled)

        // 4) Non-ARM64 device (x86_64) -> falls back to StubEngine and shows "needs an ARM64" state
        var loaderCalledOnX86 = false
        val fallbackX86 = EngineProvider.createEngineWithFallback(
            supportedAbis = arrayOf("x86_64", "x86"),
            isEmulator = false,
            nativeLoader = {
                loaderCalledOnX86 = true
                fakeNative
            }
        )
        assertFalse("Native loader must not be invoked on x86_64", loaderCalledOnX86)
        assertFalse(fallbackX86.info().isRuntimeInstalled)
        assertTrue(
            "x86_64 fallback message must state ARM64 requirement",
            fallbackX86.info().statusMessage.contains("needs an ARM64", ignoreCase = true)
        )

        // 5) Emulator -> falls back to StubEngine
        val fallbackEmulator = EngineProvider.createEngineWithFallback(
            supportedAbis = arrayOf("arm64-v8a"),
            isEmulator = true,
            nativeLoader = { fakeNative }
        )
        assertFalse(fallbackEmulator.info().isRuntimeInstalled)
        assertTrue(fallbackEmulator.info().statusMessage.contains("ARM64 phone", ignoreCase = true))
    }

    @Test
    fun llamaEngine_enforcesLoadGuards_stopWithin500ms_andTrimMemoryLazyReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "llama_guard_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val validFile = File(tempDir, "valid_qwen.gguf")
            val validPayload = "GGUF".toByteArray(Charsets.US_ASCII) + ByteArray(4096)
            validFile.writeBytes(validPayload)

            val corruptMagicFile = File(tempDir, "corrupt_magic.gguf")
            corruptMagicFile.writeBytes("BAD!".toByteArray(Charsets.US_ASCII) + ByteArray(4096))

            val binding = FakeNativeBinding(
                tokenBytesSequence = List(40) { " token".toByteArray(Charsets.UTF_8) }
            )

            // 1) Missing file -> CORRUPT_FILE typed error
            val engine = LlamaEngine(
                context = context,
                nativeEngineOverride = binding,
                ggufReader = buildMetadataReader(arch = "qwen2")
            )
            val missingRes = engine.load(File(tempDir, "nonexistent.gguf").absolutePath)
            assertTrue(missingRes.isFailure)
            val missingEx = missingRes.exceptionOrNull() as TypedLocalInferenceException
            assertEquals(LocalRuntimeErrorKind.CORRUPT_FILE, missingEx.kind)

            // 2) Invalid GGUF magic -> CORRUPT_FILE typed error
            val badMagicRes = engine.load(corruptMagicFile.absolutePath)
            assertTrue(badMagicRes.isFailure)
            val badMagicEx = badMagicRes.exceptionOrNull() as TypedLocalInferenceException
            assertEquals(LocalRuntimeErrorKind.CORRUPT_FILE, badMagicEx.kind)

            // 3) File size mismatch vs manifest -> CORRUPT_FILE typed error
            val sizeMismatchRes = engine.load(
                validFile.absolutePath,
                InferenceParams(expectedSizeBytes = 500_000_000L, availableRamBytes = 4_000_000_000L)
            )
            assertTrue(sizeMismatchRes.isFailure)
            val sizeEx = sizeMismatchRes.exceptionOrNull() as TypedLocalInferenceException
            assertEquals(LocalRuntimeErrorKind.CORRUPT_FILE, sizeEx.kind)

            // 4) Unsupported model architecture -> UNSUPPORTED_MODEL typed error ("This model type isn't supported by the bundled runtime")
            val unsupportedEngine = LlamaEngine(
                context = context,
                nativeEngineOverride = binding,
                ggufReader = buildMetadataReader(arch = "rwkv99_experimental")
            )
            val unsupportedRes = unsupportedEngine.load(
                validFile.absolutePath,
                InferenceParams(expectedSizeBytes = validFile.length(), availableRamBytes = 4_000_000_000L)
            )
            assertTrue(unsupportedRes.isFailure)
            val unsupportedEx = unsupportedRes.exceptionOrNull() as TypedLocalInferenceException
            assertEquals(LocalRuntimeErrorKind.UNSUPPORTED_MODEL, unsupportedEx.kind)
            assertEquals("This model type isn't supported by the bundled runtime", unsupportedEx.userTitle)

            // 5) Memory guard (available RAM < model size + 25%) -> OUT_OF_MEMORY typed error
            val largeDummyFile = File(tempDir, "large_header.gguf")
            largeDummyFile.writeBytes(validPayload)
            val oomRes = engine.load(
                largeDummyFile.absolutePath,
                InferenceParams(
                    expectedSizeBytes = largeDummyFile.length(),
                    availableRamBytes = (largeDummyFile.length() * 1.10).toLong() // Only 10% headroom (< 25%)
                )
            )
            assertTrue("Must refuse to load when available RAM < model size + 25%", oomRes.isFailure)
            val oomEx = oomRes.exceptionOrNull() as TypedLocalInferenceException
            assertEquals(LocalRuntimeErrorKind.OUT_OF_MEMORY, oomEx.kind)

            // 6) Valid load succeeds
            val okRes = engine.load(
                validFile.absolutePath,
                InferenceParams(
                    expectedSizeBytes = validFile.length(),
                    availableRamBytes = validFile.length() * 10L
                )
            )
            assertTrue("Valid GGUF with sufficient RAM must load", okRes.isSuccess)
            assertTrue(engine.isLoaded.value)

            // 7) stop() aborts generation in < 500ms and completes Flow cleanly
            val firstTokenArrived = CompletableDeferred<Unit>()
            val collectedTokens = mutableListOf<String>()
            val genJob = launch(Dispatchers.Default) {
                engine.generate("Write a long story", InferenceParams(maxTokens = 256)).collect { chunk ->
                    collectedTokens.add(chunk)
                    if (!firstTokenArrived.isCompleted) {
                        firstTokenArrived.complete(Unit)
                    }
                }
            }
            firstTokenArrived.await()
            val stopStartMs = System.currentTimeMillis()
            engine.stop()
            genJob.join()
            val stopDurationMs = System.currentTimeMillis() - stopStartMs
            assertTrue("stop() must abort generation in < 500ms (took ${stopDurationMs}ms)", stopDurationMs < 500L)
            assertTrue("Should have emitted at least 1 token before stop()", collectedTokens.isNotEmpty())
            assertTrue("Should have stopped before all 40 tokens were emitted", collectedTokens.size < 40)

            // 8) onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL) unloads immediately, and next generate() lazy-loads
            engine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            val waitUnloadStart = System.currentTimeMillis()
            while (engine.isLoaded.value && System.currentTimeMillis() - waitUnloadStart < 1000L) {
                delay(15L)
            }
            assertFalse("Model must be unloaded on TRIM_MEMORY_RUNNING_CRITICAL", engine.isLoaded.value)

            binding.tokenBytesSequence = listOf("Lazy".toByteArray(), " reloaded".toByteArray())
            val lazyOutput = engine.generate("Ping", InferenceParams(maxTokens = 16)).toList().joinToString("")
            assertEquals("Lazy reloaded", lazyOutput)
            assertTrue("Model must be lazy-reloaded on next prompt", engine.isLoaded.value)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun thinkingTagParser_andUtf8StreamDecoder_handleMultiByteEmojiCjkAndToolExclusion() {
        // 1) Complete <think>...</think> parsing
        val sampleWithThink = "<think>\nFirst I should check the directory.\n```sh-exec\nrm -rf /\n```\n</think>\nI will create the landing page:\n```sh-exec\nprintf '<h1>Hello</h1>' > index.html\n```"
        val parsed = ThinkingTagParser.parse(sampleWithThink)
        assertTrue(parsed.thinkingText.contains("First I should check the directory."))
        assertFalse(parsed.isThinkingInProgress)
        assertFalse("Visible text must not contain <think> contents", parsed.visibleText.contains("rm -rf"))
        assertTrue(parsed.visibleText.contains("index.html"))

        // 2) Streaming unclosed <think> tag
        val streamingThink = "<think>Analyzing user requirements for index.html..."
        val parsedStreaming = ThinkingTagParser.parse(streamingThink)
        assertEquals("Analyzing user requirements for index.html...", parsedStreaming.thinkingText)
        assertEquals("", parsedStreaming.visibleText)
        assertTrue(parsedStreaming.isThinkingInProgress)

        // 3) ToolCallParser excludes <think>...</think> from tool-call parsing
        val toolParseRes = ToolCallParser.parse(sampleWithThink)
        assertTrue(toolParseRes is ToolCallParseResult.Success)
        val extractedCall = (toolParseRes as ToolCallParseResult.Success).toolCall
        assertEquals(
            "ToolCallParser must ignore sh-exec inside <think> and parse only the visible sh-exec",
            "printf '<h1>Hello</h1>' > index.html",
            extractedCall.command
        )

        // 4) History sent back to the model excludes <think>...</think>
        val historyEntries = listOf(
            TranscriptEntry(id = "1", type = "user", text = "Create index.html", sequence = 1L),
            TranscriptEntry(id = "2", type = "agent", text = "<think>Secret chain of thought</think>\nCreated index.html.", sequence = 2L),
            TranscriptEntry(id = "3", type = "user", text = "Now check it", sequence = 3L)
        )
        val turns = MultiPlatformAiEngine.buildConversationTurnsForRouting(historyEntries, "Now check it")
        assertTrue("History turns must exclude <think> content", turns.none { it.second.contains("Secret chain of thought") })
        assertTrue(turns.any { it.second.contains("Created index.html.") })

        // 5) Partial UTF-8 across token boundaries (4-byte emoji 🚀🎉 and 3-byte CJK 你好世界)
        val expectedText = "Launch 🚀🎉 你好世界 — Café!"
        val fullUtf8Bytes = expectedText.toByteArray(Charsets.UTF_8)
        val decoder = Utf8StreamDecoder()
        val reconstructed = StringBuilder()

        // Feed 1 byte at a time to split every multi-byte sequence across token boundaries
        for (b in fullUtf8Bytes) {
            reconstructed.append(decoder.decodeChunk(byteArrayOf(b)))
        }
        reconstructed.append(decoder.flushRemaining())

        val finalString = reconstructed.toString()
        assertEquals(expectedText, finalString)
        assertFalse("No UTF-8 replacement character (U+FFFD) may appear", finalString.contains('\uFFFD'))
    }

    @Test
    fun historyTrimming_andTypedErrorMappingInViewModel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1) History trimming to context budget using tokenizer
        val entries = (1..20).flatMap { i ->
            listOf(
                TranscriptEntry(
                    id = "u_$i",
                    type = "user",
                    text = "User request number $i with extra details about the project workspace.",
                    sequence = (i * 2 - 1).toLong()
                ),
                TranscriptEntry(
                    id = "a_$i",
                    type = "agent",
                    text = "<think>Thinking about step $i</think>Agent response number $i.",
                    sequence = (i * 2).toLong()
                )
            )
        }

        val customTokenizer: (String) -> Int = { text -> text.trim().split(Regex("\\s+")).size }
        val trimmed = MultiPlatformAiEngine.trimEntriesForLocalBudget(
            entries = entries,
            tokenBudget = 210,
            tokenCounter = customTokenizer
        )
        assertTrue("History must be trimmed to fit token budget", trimmed.size < entries.size)
        assertEquals("Latest entry must be preserved", "a_20", trimmed.last().id)
        assertTrue(
            "Trimmed agent entries must have <think> blocks stripped",
            trimmed.filter { it.type == "agent" }.none { it.text.contains("<think>") }
        )

        // 2) ViewModel error mapping for TypedLocalInferenceException (CORRUPT_FILE, UNSUPPORTED_MODEL, OUT_OF_MEMORY, GENERATION_FAILED)
        val shellBridge = LocalShellBridge(context)
        val localLlmManager = LocalLlmManager(context)
        val userApiKeyStore = UserApiKeyStore(context)
        val notificationHelper = NexusNotificationHelper(context)

        val spec = LocalLlmManager.LOCAL_LLM_CATALOG.first()
        userApiKeyStore.persistSelectedProviderAndModel("local", spec.id)

        val errorKindsToVerify = listOf(
            Triple(
                LocalRuntimeErrorKind.CORRUPT_FILE,
                "local_corrupt_file",
                "Corrupt GGUF file"
            ),
            Triple(
                LocalRuntimeErrorKind.UNSUPPORTED_MODEL,
                "local_unsupported_model",
                "This model type isn't supported by the bundled runtime"
            ),
            Triple(
                LocalRuntimeErrorKind.OUT_OF_MEMORY,
                "local_oom",
                "Not enough available memory"
            ),
            Triple(
                LocalRuntimeErrorKind.GENERATION_FAILED,
                "local_generation_failed",
                "Local generation failed"
            )
        )

        for ((errorKind, expectedNoticeKind, title) in errorKindsToVerify) {
            val failingTransport = object : ProviderTransport {
                override fun streamGeminiRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>): Flow<String> = flow {}
                override fun streamAnthropicRest(modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>): Flow<String> = flow {}
                override fun streamOpenAiCompatibleRest(provider: String, endpointUrl: String, modelId: String, apiKey: String, maxTokens: Int, systemInstruction: String, conversationTurns: List<Pair<String, String>>): Flow<String> = flow {}
                override fun streamLocalOnDevice(
                    modelId: String,
                    userPrompt: String,
                    currentCwd: String,
                    compactSystemPrompt: String,
                    recentCommandOutputs: List<Pair<String, String>>
                ): Flow<String> = flow {
                    throw TypedLocalInferenceException(
                        kind = errorKind,
                        modelId = spec.id,
                        userTitle = title,
                        userMessage = "$title: simulated test failure for $errorKind."
                    )
                }
            }

            val vm = NexusViewModel(
                repository = null,
                shellBridge = shellBridge,
                localLlmManager = localLlmManager,
                userApiKeyStore = userApiKeyStore,
                notificationHelper = notificationHelper,
                currentUserId = "test_error_mapping_${errorKind.name}",
                isGuestMode = true,
                customTransport = failingTransport
            )

            vm.submitDirective("Create index.html with a landing page")
            val start = System.currentTimeMillis()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
            while (vm.isRunning.value && System.currentTimeMillis() - start < 4000L) {
                delay(20L)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))

            val currentList = (vm.entriesState.first { it is UiState.Success } as UiState.Success).data
            val noticeEntry = currentList.lastOrNull { it.type == "notice" }
            assertNotNull("Expected a notice entry for $errorKind", noticeEntry)
            assertEquals("Notice kind must map to $expectedNoticeKind", expectedNoticeKind, noticeEntry!!.noticeKind)
            assertTrue(noticeEntry.text.contains(title))
        }
    }
}

