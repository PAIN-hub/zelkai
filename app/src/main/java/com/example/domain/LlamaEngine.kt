package com.example.domain

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.NativeLoadConfig
import com.arm.aichat.UnsupportedArchitectureException
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.arm.aichat.gguf.InvalidFileFormatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes streamed UTF-8 byte chunks across arbitrary token boundaries without emitting
 * `U+FFFD` replacement characters when multi-byte sequences (e.g. 3-byte CJK or 4-byte Emojis)
 * are split across two or more tokens.
 */
class Utf8StreamDecoder {
    private val pending = ByteArrayOutputStream(8)

    fun decodeChunk(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        pending.write(bytes, 0, bytes.size)
        val buf = pending.toByteArray()
        val completeLen = findCompleteUtf8PrefixLength(buf)
        if (completeLen <= 0) {
            return ""
        }
        val decoded = String(buf, 0, completeLen, Charsets.UTF_8)
        pending.reset()
        if (completeLen < buf.size) {
            pending.write(buf, completeLen, buf.size - completeLen)
        }
        return decoded
    }

    fun flushRemaining(): String {
        if (pending.size() == 0) return ""
        val buf = pending.toByteArray()
        pending.reset()
        return String(buf, Charsets.UTF_8)
    }

    fun reset() {
        pending.reset()
    }

    companion object {
        fun findCompleteUtf8PrefixLength(buf: ByteArray): Int {
            val len = buf.size
            if (len == 0) return 0

            // Inspect up to the last 3 bytes to see if an incomplete multi-byte UTF-8 sequence is at the tail
            val maxLookback = min(len, 4)
            for (offsetFromEnd in 1..maxLookback) {
                val idx = len - offsetFromEnd
                val b = buf[idx].toInt() and 0xFF
                if ((b and 0x80) == 0x00) {
                    // Single-byte ASCII at idx: everything up to idx+1 is complete
                    return len
                } else if ((b and 0xC0) == 0xC0) {
                    // Leading byte of a multi-byte sequence
                    val expectedSeqLen = when {
                        (b and 0xE0) == 0xC0 -> 2
                        (b and 0xF0) == 0xE0 -> 3
                        (b and 0xF8) == 0xF0 -> 4
                        else -> 1
                    }
                    return if (offsetFromEnd < expectedSeqLen) {
                        // Incomplete sequence at the tail — wait for next token bytes
                        idx
                    } else {
                        len
                    }
                }
                // Continuation byte (10xxxxxx): keep walking backwards to its leading byte
            }
            return len
        }
    }
}

/**
 * Result of parsing DeepSeek-R1 / QwQ style `<think>...</think>` blocks.
 * - [thinkingText] contains the extracted reasoning trace (collapsed by default in UI).
 * - [visibleText] contains the user-facing response with `<think>...</think>` removed,
 *   used for tool-call parsing and conversation history.
 * - [isThinkingInProgress] is true when an opening `<think>` tag has not yet closed.
 */
data class ParsedThinkingResponse(
    val thinkingText: String,
    val visibleText: String,
    val isThinkingInProgress: Boolean
)

object ThinkingTagParser {
    private val COMPLETE_THINK_BLOCK_REGEX = Regex(
        pattern = "<think>([\\s\\S]*?)</think>",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): ParsedThinkingResponse {
        if (rawText.isEmpty()) {
            return ParsedThinkingResponse(
                thinkingText = "",
                visibleText = "",
                isThinkingInProgress = false
            )
        }

        val thinkingParts = mutableListOf<String>()
        var remaining = rawText

        COMPLETE_THINK_BLOCK_REGEX.findAll(rawText).forEach { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) {
                thinkingParts.add(inner)
            }
        }
        remaining = remaining.replace(COMPLETE_THINK_BLOCK_REGEX, "")

        val openIdx = remaining.indexOf("<think>", ignoreCase = true)
        var inProgress = false
        if (openIdx >= 0) {
            inProgress = true
            val unclosedThink = remaining.substring(openIdx + "<think>".length).trim()
            if (unclosedThink.isNotEmpty()) {
                thinkingParts.add(unclosedThink)
            }
            remaining = remaining.substring(0, openIdx)
        }

        return ParsedThinkingResponse(
            thinkingText = thinkingParts.joinToString("\n\n").trim(),
            visibleText = remaining.trimStart(),
            isThinkingInProgress = inProgress
        )
    }

    /**
     * Strips `<think>...</think>` from a message before it is parsed for tool calls
     * or sent back into model conversation history.
     */
    fun stripThinkingForHistoryAndTools(rawText: String): String {
        return parse(rawText).visibleText.trim()
    }
}

/**
 * Production on-device `llama.cpp` implementation of [LocalInferenceEngine].
 *
 * Enforces:
 * - Mutex serialization around `load`, `unload`, and `generate`.
 * - Pre-load file existence, manifest size check, GGUF magic check, and `GgufMetadataReader` architecture check.
 * - Memory guard refusing load when `availableRam < modelSize * 1.25`.
 * - Automatic unload on `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` and after 5 minutes idle,
 *   with transparent lazy-reload on the next `generate()` call.
 * - Chat template formatting from GGUF metadata.
 * - Multi-byte UTF-8 boundary decoding (`Utf8StreamDecoder`).
 * - Sub-500ms native abort on `stop()`.
 */
class LlamaEngine(
    private val context: Context?,
    private val nativeEngineOverride: InferenceEngine? = null,
    private val ggufReader: GgufMetadataReader = GgufMetadataReader.create(),
    private val availableRamProvider: (() -> Long)? = null,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS
) : LocalInferenceEngine {

    companion object {
        const val LLAMA_CPP_TAG = "b12020"
        const val DEFAULT_IDLE_TIMEOUT_MS = 5 * 60 * 1000L

        /**
         * Architectures supported by llama.cpp at pinned release tag `b12020`.
         */
        val SUPPORTED_ARCHITECTURES: Set<String> = setOf(
            "llama", "llama4", "deci", "falcon", "falcon-h1", "grok", "gpt2", "gptj", "gptneox",
            "mpt", "baichuan", "starcoder", "starcoder2", "refact", "bert", "modern-bert",
            "nomic-bert", "nomic-bert-moe", "neo-bert", "jina-bert-v2", "jina-bert-v3", "eurobert",
            "bloom", "stablelm", "qwen", "qwen2", "qwen2moe", "qwen2vl", "qwen3", "qwen3moe",
            "qwen3next", "qwen3vl", "qwen3vlmoe", "qwen35", "qwen35moe", "phi2", "phi3", "phimoe",
            "plamo", "plamo2", "plamo3", "codeshell", "orion", "internlm2", "minicpm", "minicpm3",
            "gemma", "gemma2", "gemma3", "gemma3n", "gemma4", "mamba", "mamba2", "jamba", "xverse",
            "command-r", "cohere2", "cohere2moe", "dbrx", "olmo", "olmo2", "olmoe", "openelm",
            "arctic", "deepseek", "deepseek2", "deepseek32", "deepseek4", "deepseek41", "chatglm",
            "glm4", "glm4moe", "bitnet", "t5", "t5encoder", "jais", "jais2", "nemotron",
            "nemotron_h", "nemotron_h_moe", "exaone", "exaone4", "exaone-moe", "rwkv6",
            "rwkv6qwen2", "rwkv7", "arwkv7", "granite", "granitemoe", "granitehybrid",
            "chameleon", "bailingmoe", "bailingmoe2", "bailingmoe3", "dots1", "arcee", "afmoe",
            "ernie4_5", "ernie4_5-moe", "hunyuan-moe", "hunyuan-dense", "smollm3", "gpt-oss",
            "lfm2", "lfm2moe", "lfm3", "dream", "smallthinker", "llada", "llada-moe", "seed_oss",
            "grovemoe", "apertus", "minimax-01", "minimax-m2", "minimax-m3", "cogvlm", "rnd1",
            "pangu-embedded", "mistral3", "mistral4", "mistral-large4", "mimo2", "step35",
            "maincoder", "kimi-linear", "kimi-k3", "glm5-next", "mellum", "nanbeige"
        )

        /**
         * Optional GBNF grammar constraining local agent output to either plain text or a single `sh-exec` block.
         */
        const val AGENT_SH_EXEC_GBNF = """
root ::= sh-block | plain-text
sh-block ::= "```sh-exec\n" [^\n`]+ "\n```"
plain-text ::= ([^`] | "`" [^`] | "``" [^`])+
"""

        fun formatWithGgufChatTemplate(
            chatTemplate: String?,
            architecture: String,
            systemPrompt: String,
            userPrompt: String
        ): String {
            // If prompt already contains explicit chat control tokens, do not double-wrap
            if (userPrompt.contains("<|im_start|>") ||
                userPrompt.contains("<|start_header_id|>") ||
                userPrompt.contains("<start_of_turn>") ||
                userPrompt.contains("<|user|>")
            ) {
                return userPrompt
            }

            val tpl = (chatTemplate ?: "").lowercase(Locale.US)
            val arch = architecture.lowercase(Locale.US)
            val sys = systemPrompt.trim()
            val usr = userPrompt.trim()

            return when {
                tpl.contains("<|im_start|>") || arch.startsWith("qwen") || arch.startsWith("smollm") -> buildString {
                    if (sys.isNotEmpty()) {
                        append("<|im_start|>system\n").append(sys).append("<|im_end|>\n")
                    }
                    append("<|im_start|>user\n").append(usr).append("<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }
                tpl.contains("<|start_header_id|>") || arch == "llama" || arch == "llama4" -> buildString {
                    if (sys.isNotEmpty()) {
                        append("<|start_header_id|>system<|end_header_id|>\n\n").append(sys).append("<|eot_id|>")
                    }
                    append("<|start_header_id|>user<|end_header_id|>\n\n").append(usr).append("<|eot_id|>")
                    append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                }
                tpl.contains("<start_of_turn>") || arch.startsWith("gemma") -> buildString {
                    val combinedUser = if (sys.isNotEmpty()) "$sys\n\n$usr" else usr
                    append("<start_of_turn>user\n").append(combinedUser).append("<end_of_turn>\n")
                    append("<start_of_turn>model\n")
                }
                tpl.contains("<|user|>") || arch.startsWith("phi") -> buildString {
                    if (sys.isNotEmpty()) {
                        append("<|system|>\n").append(sys).append("<|end|>\n")
                    }
                    append("<|user|>\n").append(usr).append("<|end|>\n")
                    append("<|assistant|>\n")
                }
                tpl.contains("[inst]") || arch.startsWith("mistral") -> buildString {
                    val combinedUser = if (sys.isNotEmpty()) "$sys\n\n$usr" else usr
                    append("[INST] ").append(combinedUser).append(" [/INST]")
                }
                else -> buildString {
                    if (sys.isNotEmpty()) {
                        append("<|im_start|>system\n").append(sys).append("<|im_end|>\n")
                    }
                    append("<|im_start|>user\n").append(usr).append("<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }
            }
        }
    }

    private val nativeEngine: InferenceEngine by lazy {
        nativeEngineOverride ?: AiChat.getInferenceEngine(
            requireNotNull(context) { "Context is required to initialize native InferenceEngine" }
        )
    }

    private val engineMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleUnloadJob: Job? = null

    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    @Volatile
    private var loadedModelPath: String? = null

    @Volatile
    private var lastLoadPathForLazyReload: String? = null

    @Volatile
    private var lastLoadParamsForLazyReload: InferenceParams? = null

    @Volatile
    private var loadedMetadata: GgufMetadata? = null

    @Volatile
    private var lastSystemPromptSent: String = ""

    @Volatile
    private var stopRequested: Boolean = false

    fun getLoadedMetadata(): GgufMetadata? = loadedMetadata

    private fun resolveAvailableRamBytes(params: InferenceParams): Long {
        if (params.availableRamBytes > 0L) return params.availableRamBytes
        availableRamProvider?.let { return it() }
        val ctx = context ?: return Long.MAX_VALUE / 2
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                if (memInfo.availMem > 0L) memInfo.availMem else Long.MAX_VALUE / 2
            } else {
                Long.MAX_VALUE / 2
            }
        } catch (_: Exception) {
            Long.MAX_VALUE / 2
        }
    }

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        if (idleTimeoutMs <= 0L) return
        idleUnloadJob = scope.launch {
            delay(idleTimeoutMs)
            engineMutex.withLock {
                if (_isLoaded.value) {
                    runCatching { nativeEngine.cleanUp() }
                    _isLoaded.value = false
                    loadedModelPath = null
                    lastSystemPromptSent = ""
                }
            }
        }
    }

    override suspend fun load(path: String, params: InferenceParams): Result<Unit> {
        return engineMutex.withLock {
            loadInternalUnlocked(path, params)
        }
    }

    private suspend fun loadInternalUnlocked(path: String, params: InferenceParams): Result<Unit> {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            _isLoaded.value = false
            return Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.CORRUPT_FILE,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Corrupt or missing model file",
                    userMessage = "Model file does not exist or is empty (${file.name}). Delete and re-download the model.",
                    suggestedActionLabel = "Delete & Re-download"
                )
            )
        }

        val actualSize = file.length()
        if (params.expectedSizeBytes > 0L) {
            val diff = kotlin.math.abs(actualSize - params.expectedSizeBytes)
            // If file size differs from manifest expected size by more than 5% (e.g. truncated download)
            if (actualSize < (params.expectedSizeBytes * 0.95).toLong() && diff > 1024L * 1024L) {
                _isLoaded.value = false
                return Result.failure(
                    TypedLocalInferenceException(
                        kind = LocalRuntimeErrorKind.CORRUPT_FILE,
                        modelId = file.nameWithoutExtension,
                        userTitle = "Corrupt model file (size mismatch)",
                        userMessage = "File size (${actualSize / (1024 * 1024)} MB) does not match the manifest (${params.expectedSizeBytes / (1024 * 1024)} MB). Delete and re-download.",
                        suggestedActionLabel = "Delete & Re-download"
                    )
                )
            }
        }

        // Verify GGUF magic and read structured GGUF metadata before native load
        val metadata = try {
            val hasGgufMagic = file.length() >= 4L && file.inputStream().use { fis ->
                val magic = ByteArray(4)
                fis.read(magic) == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
            val isValidMagic = hasGgufMagic && ggufReader.ensureSourceFileFormat(file)
            if (!isValidMagic) {
                _isLoaded.value = false
                return Result.failure(
                    TypedLocalInferenceException(
                        kind = LocalRuntimeErrorKind.CORRUPT_FILE,
                        modelId = file.nameWithoutExtension,
                        userTitle = "Corrupt model file (invalid GGUF magic)",
                        userMessage = "File '${file.name}' does not have a valid GGUF header. Delete and re-download the model.",
                        suggestedActionLabel = "Delete & Re-download"
                    )
                )
            }
            file.inputStream().buffered().use { input ->
                ggufReader.readStructuredMetadata(input)
            }
        } catch (e: InvalidFileFormatException) {
            _isLoaded.value = false
            return Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.CORRUPT_FILE,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Corrupt model file",
                    userMessage = "File '${file.name}' is not a valid GGUF v2/v3 model file. Delete and re-download.",
                    suggestedActionLabel = "Delete & Re-download",
                    cause = e
                )
            )
        } catch (e: Exception) {
            _isLoaded.value = false
            return Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.CORRUPT_FILE,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Corrupt model file",
                    userMessage = "Failed to read GGUF metadata from '${file.name}': ${e.message ?: "truncated file"}. Delete and re-download.",
                    suggestedActionLabel = "Delete & Re-download",
                    cause = e
                )
            )
        }

        val arch = metadata.architecture?.architecture?.lowercase(Locale.US)?.trim().orEmpty()
        if (arch.isNotEmpty() && arch !in SUPPORTED_ARCHITECTURES) {
            _isLoaded.value = false
            return Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.UNSUPPORTED_MODEL,
                    modelId = file.nameWithoutExtension,
                    userTitle = "This model type isn't supported by the bundled runtime",
                    userMessage = "This model type isn't supported by the bundled runtime ($arch).",
                    suggestedActionLabel = "Select Another Model"
                )
            )
        }

        // Memory Guard: refuse to load if available RAM < model size + 25%
        val availRam = resolveAvailableRamBytes(params)
        val requiredRam = (actualSize * 1.25).toLong()
        if (availRam < requiredRam) {
            _isLoaded.value = false
            val gb = 1024.0 * 1024.0 * 1024.0
            val reqGb = ((requiredRam / gb) * 10.0).roundToInt() / 10.0
            val availGb = ((availRam / gb) * 10.0).roundToInt() / 10.0
            return Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.OUT_OF_MEMORY,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Out of memory",
                    userMessage = "Loading ${file.nameWithoutExtension} requires at least $reqGb GB available RAM (model size + 25% safety margin), but only $availGb GB is free. Switch to a smaller model or lower context size.",
                    suggestedActionLabel = "Use Smaller Model"
                )
            )
        }

        val metadataMaxCtx = metadata.dimensions?.contextLength ?: 0
        val effectiveCtx = if (metadataMaxCtx > 0) {
            min(params.contextLength, metadataMaxCtx)
        } else {
            params.contextLength
        }.coerceIn(512, 32768)

        return try {
            if (_isLoaded.value && loadedModelPath != path) {
                runCatching { nativeEngine.cleanUp() }
                _isLoaded.value = false
                loadedModelPath = null
                lastSystemPromptSent = ""
            }

            val nativeConfig = NativeLoadConfig(
                nCtx = effectiveCtx,
                nThreads = params.threads.coerceIn(1, 16),
                nThreadsBatch = params.threadsBatch.coerceIn(1, 16),
                nBatch = params.batchSize.coerceIn(32, 2048),
                useMmap = params.useMmap,
                flashAttention = params.flashAttention,
                kvCacheQ8_0 = params.kvCacheType.equals("q8_0", ignoreCase = true),
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                repeatPenalty = params.repeatPenalty,
                grammar = params.grammar
            )

            nativeEngine.loadModel(path, nativeConfig)
            loadedMetadata = metadata
            loadedModelPath = path
            lastLoadPathForLazyReload = path
            lastLoadParamsForLazyReload = params
            _isLoaded.value = true

            if (params.systemInstruction.isNotBlank()) {
                runCatching {
                    nativeEngine.setSystemPrompt(params.systemInstruction.trim())
                    lastSystemPromptSent = params.systemInstruction.trim()
                }
            }

            scheduleIdleUnload()
            Result.success(Unit)
        } catch (e: UnsupportedArchitectureException) {
            _isLoaded.value = false
            Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.UNSUPPORTED_MODEL,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Unsupported model architecture",
                    userMessage = "This model type isn't supported by the bundled runtime.",
                    suggestedActionLabel = "Select Another Model",
                    cause = e
                )
            )
        } catch (e: OutOfMemoryError) {
            _isLoaded.value = false
            Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.OUT_OF_MEMORY,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Out of memory",
                    userMessage = "Native allocator ran out of memory while loading ${file.name}. Try a smaller model or lower context size.",
                    suggestedActionLabel = "Use Smaller Model",
                    cause = e
                )
            )
        } catch (e: Exception) {
            _isLoaded.value = false
            Result.failure(
                TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.GENERATION_FAILED,
                    modelId = file.nameWithoutExtension,
                    userTitle = "Failed to load model",
                    userMessage = e.message ?: "Native runtime failed to initialize model context.",
                    suggestedActionLabel = "Retry",
                    cause = e
                )
            )
        }
    }

    override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
        stopRequested = false
        idleUnloadJob?.cancel()

        engineMutex.withLock {
            // Lazy-reload if unloaded by idle timeout or onTrimMemory(RUNNING_CRITICAL)
            if (!_isLoaded.value) {
                val reloadPath = lastLoadPathForLazyReload
                val reloadParams = lastLoadParamsForLazyReload ?: params
                if (!reloadPath.isNullOrBlank()) {
                    val reloadRes = loadInternalUnlocked(reloadPath, reloadParams)
                    if (reloadRes.isFailure) {
                        throw reloadRes.exceptionOrNull()
                            ?: TypedLocalInferenceException(
                                kind = LocalRuntimeErrorKind.MODEL_NOT_LOADED,
                                userTitle = "Model not loaded",
                                userMessage = "Failed to lazy-reload model after memory trim."
                            )
                    }
                } else {
                    throw TypedLocalInferenceException(
                        kind = LocalRuntimeErrorKind.MODEL_NOT_LOADED,
                        userTitle = "Model not loaded",
                        userMessage = "No local model is loaded in the runtime.",
                        suggestedActionLabel = "Open Local LLM Hub"
                    )
                }
            }

            // Update sampler settings if needed
            runCatching {
                nativeEngine.updateSampler(
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    repeatPenalty = params.repeatPenalty,
                    grammar = params.grammar
                )
            }

            // Reuse KV cache for shared system prompt if supported by native binding
            val sys = params.systemInstruction.trim()
            val nativeHasTemplate = nativeEngine.hasChatTemplate()
            var systemPromptAppliedNatively = false
            if (sys.isNotBlank() && nativeHasTemplate) {
                systemPromptAppliedNatively = runCatching {
                    nativeEngine.setSystemPrompt(sys)
                    lastSystemPromptSent = sys
                    true
                }.getOrDefault(false)
            }

            // Format prompt with GGUF metadata chat_template when native template wasn't explicitly applied
            val meta = loadedMetadata
            val effectiveUserMessage = if (systemPromptAppliedNatively) {
                prompt
            } else {
                formatWithGgufChatTemplate(
                    chatTemplate = meta?.tokenizer?.chatTemplate,
                    architecture = meta?.architecture?.architecture ?: "llama",
                    systemPrompt = sys,
                    userPrompt = prompt
                )
            }

            val decoder = Utf8StreamDecoder()
            val accumulated = StringBuilder()
            val maxPredict = params.maxTokens.coerceIn(16, 4096)

            val eosStopTokens = listOf(
                "<|im_end|>",
                "<|eot_id|>",
                "<end_of_turn>",
                "<|end|>",
                "</s>"
            )

            try {
                nativeEngine.sendUserPromptBytes(effectiveUserMessage, maxPredict).collect { rawBytes ->
                    if (stopRequested) {
                        nativeEngine.abortGeneration()
                        return@collect
                    }
                    val decoded = decoder.decodeChunk(rawBytes)
                    if (decoded.isEmpty()) return@collect

                    // Strip trailing EOS stop markers if emitted as text pieces
                    var cleanChunk = decoded
                    var hitStopMarker = false
                    for (eos in eosStopTokens) {
                        val idx = cleanChunk.indexOf(eos)
                        if (idx >= 0) {
                            cleanChunk = cleanChunk.substring(0, idx)
                            hitStopMarker = true
                            break
                        }
                    }

                    if (cleanChunk.isNotEmpty()) {
                        accumulated.append(cleanChunk)
                        emit(cleanChunk)
                    }

                    // Check custom stop sequences (e.g. closing fence of a single sh-exec block)
                    val fullText = accumulated.toString()
                    val visibleSoFar = ThinkingTagParser.parse(fullText).visibleText
                    val shBlockIdx = visibleSoFar.indexOf("```sh-exec\n")
                    if (shBlockIdx >= 0) {
                        val afterFence = visibleSoFar.substring(shBlockIdx + "```sh-exec\n".length)
                        val closingFenceIdx = afterFence.indexOf("\n```")
                        if (closingFenceIdx >= 0) {
                            hitStopMarker = true
                        }
                    }

                    for (stopSeq in params.stopSequences) {
                        if (stopSeq.isNotEmpty() && stopSeq != "```sh-exec\n" && visibleSoFar.endsWith(stopSeq)) {
                            hitStopMarker = true
                            break
                        }
                    }

                    if (hitStopMarker) {
                        stopRequested = true
                        nativeEngine.abortGeneration()
                    }
                }

                val tail = decoder.flushRemaining()
                if (tail.isNotEmpty()) {
                    emit(tail)
                }
            } catch (ce: CancellationException) {
                nativeEngine.abortGeneration()
                throw ce
            } catch (e: TypedLocalInferenceException) {
                throw e
            } catch (e: Exception) {
                throw TypedLocalInferenceException(
                    kind = LocalRuntimeErrorKind.GENERATION_FAILED,
                    modelId = loadedModelPath?.let { File(it).nameWithoutExtension }.orEmpty(),
                    userTitle = "Generation failed",
                    userMessage = e.message ?: "Native inference failed during token generation.",
                    suggestedActionLabel = "Retry",
                    cause = e
                )
            } finally {
                scheduleIdleUnload()
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        stopRequested = true
        runCatching { nativeEngine.abortGeneration() }
    }

    override fun unload() {
        stopRequested = true
        idleUnloadJob?.cancel()
        runCatching { nativeEngine.abortGeneration() }
        runBlocking(Dispatchers.IO) {
            engineMutex.withLock {
                runCatching { nativeEngine.cleanUp() }
                _isLoaded.value = false
                loadedModelPath = null
                loadedMetadata = null
                lastSystemPromptSent = ""
            }
        }
    }

    /**
     * Unloads the active model on critical memory pressure (`TRIM_MEMORY_RUNNING_CRITICAL` or higher)
     * while preserving [lastLoadPathForLazyReload] so the next prompt lazy-loads it transparently.
     */
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            stopRequested = true
            runCatching { nativeEngine.abortGeneration() }
            scope.launch {
                engineMutex.withLock {
                    if (_isLoaded.value) {
                        runCatching { nativeEngine.cleanUp() }
                        _isLoaded.value = false
                        loadedModelPath = null
                        lastSystemPromptSent = ""
                    }
                }
            }
        }
    }

    override fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        if (_isLoaded.value) {
            val nativeCount = runCatching { nativeEngine.countTokens(text) }.getOrDefault(-1)
            if (nativeCount >= 0) return nativeCount
        }
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    override fun info(): EngineInfo {
        val sysInfo = runCatching { nativeEngine.getSystemInfo() }.getOrDefault("")
        val parsedCpuFeatures = parseCpuFeaturesFromSystemInfo(sysInfo)
        return EngineInfo(
            name = "llama.cpp",
            version = LLAMA_CPP_TAG,
            isRuntimeInstalled = true,
            statusMessage = "llama.cpp ($LLAMA_CPP_TAG) native ARM64 runtime active",
            llamaCppTag = LLAMA_CPP_TAG,
            backendInfo = "CPU (ARM64 NEON / DOTPROD / FP16)",
            cpuFeatures = parsedCpuFeatures,
            abi = "arm64-v8a"
        )
    }

    private fun parseCpuFeaturesFromSystemInfo(raw: String): String {
        if (raw.isBlank()) return "ARM_FMA, FP16_VA, DOTPROD"
        // Extract feature flags before the first '|' section in llama_print_system_info()
        val firstSection = raw.substringBefore("|").trim()
        val activeFlags = firstSection.split(" ")
            .filter { it.endsWith("= 1") || it == "NEON" || it == "ARM_FMA" || it == "FP16_VA" || it == "DOTPROD" }
        return if (activeFlags.isNotEmpty()) {
            firstSection.replace(" = 1", "").replace(" = 0", "(off)").take(120)
        } else {
            raw.take(120)
        }
    }
}

