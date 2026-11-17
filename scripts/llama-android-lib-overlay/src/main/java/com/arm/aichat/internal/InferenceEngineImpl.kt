package com.arm.aichat.internal

import android.content.Context
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.arm.aichat.NativeLoadConfig
import com.arm.aichat.UnsupportedArchitectureException
import dalvik.annotation.optimization.FastNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JNI wrapper for the llama.cpp library providing Android-friendly access to large language models.
 */
internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        @Volatile
        private var instance: InferenceEngine? = null

        /**
         * Create or obtain [InferenceEngineImpl]'s single instance.
         *
         * @throws IllegalArgumentException if native library path is invalid
         * @throws UnsatisfiedLinkError if library failed to load
         * @throws SecurityException if library loading is denied
         */
        internal fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: run {
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    require(nativeLibDir.isNotBlank()) { "Expected a valid native library path!" }
                    try {
                        Log.i(TAG, "Instantiating InferenceEngineImpl...")
                        InferenceEngineImpl(nativeLibDir).also { instance = it }
                    } catch (e: UnsatisfiedLinkError) {
                        Log.e(TAG, "Failed to load native library from $nativeLibDir", e)
                        throw e
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException loading native library from $nativeLibDir", e)
                        throw e
                    }
                }
            }
    }

    @FastNative
    private external fun init(nativeLibDir: String)

    @FastNative
    private external fun requestAbort()

    @FastNative
    private external fun load(modelPath: String): Int

    @FastNative
    private external fun loadWithParams(modelPath: String, useMmap: Boolean): Int

    @FastNative
    private external fun prepare(): Int

    @FastNative
    private external fun prepareWithConfig(
        nCtx: Int,
        nThreads: Int,
        nThreadsBatch: Int,
        nBatch: Int,
        flashAttn: Boolean,
        kvQ8_0: Boolean,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        grammar: String?
    ): Int

    @FastNative
    private external fun nativeUpdateSampler(
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        grammar: String?
    )

    @FastNative
    private external fun systemInfo(): String

    @FastNative
    private external fun nativeCountTokens(text: String): Int

    @FastNative
    private external fun hasExplicitChatTemplate(): Boolean

    @FastNative
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String

    @FastNative
    private external fun processSystemPrompt(systemPrompt: String): Int

    @FastNative
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int

    @FastNative
    private external fun generateNextToken(): String?

    @FastNative
    private external fun generateNextTokenBytes(): ByteArray?

    @FastNative
    private external fun unload()

    @FastNative
    private external fun shutdown()

    private val _state =
        MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    @Volatile
    private var _cancelGeneration = false

    @Volatile
    private var cachedSysInfo: String = ""

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        _state.value = InferenceEngine.State.Initializing
        System.loadLibrary("ai-chat")
        init(nativeLibDir)
        cachedSysInfo = runCatching { systemInfo() }.getOrDefault("")
        _state.value = InferenceEngine.State.Initialized
    }

    override suspend fun loadModel(pathToModel: String) {
        loadModel(pathToModel, NativeLoadConfig())
    }

    override suspend fun loadModel(pathToModel: String, config: NativeLoadConfig) =
        withContext(llamaDispatcher) {
            if (_state.value !is InferenceEngine.State.Initialized) {
                when (_state.value) {
                    is InferenceEngine.State.Error -> {
                        _state.value = InferenceEngine.State.Initialized
                    }
                    is InferenceEngine.State.ModelReady,
                    is InferenceEngine.State.Generating,
                    is InferenceEngine.State.ProcessingUserPrompt,
                    is InferenceEngine.State.ProcessingSystemPrompt -> {
                        _state.value = InferenceEngine.State.UnloadingModel
                        runCatching { unload() }
                        _state.value = InferenceEngine.State.Initialized
                    }
                    else -> {}
                }
            }

            try {
                File(pathToModel).let {
                    require(it.exists()) { "File not found" }
                    require(it.isFile) { "Not a valid file" }
                    require(it.canRead()) { "Cannot read file" }
                }

                _state.value = InferenceEngine.State.LoadingModel
                loadWithParams(pathToModel, config.useMmap).let {
                    if (it != 0) throw UnsupportedArchitectureException()
                }
                prepareWithConfig(
                    nCtx = config.nCtx,
                    nThreads = config.nThreads,
                    nThreadsBatch = config.nThreadsBatch,
                    nBatch = config.nBatch,
                    flashAttn = config.flashAttention,
                    kvQ8_0 = config.kvCacheQ8_0,
                    temp = config.temperature,
                    topP = config.topP,
                    topK = config.topK,
                    repeatPenalty = config.repeatPenalty,
                    grammar = config.grammar.takeIf { it.isNotBlank() }
                ).let {
                    if (it != 0) {
                        runCatching { unload() }
                        throw IOException("Failed to prepare native llama context")
                    }
                }
                _cancelGeneration = false
                _state.value = InferenceEngine.State.ModelReady
            } catch (e: Exception) {
                _state.value = InferenceEngine.State.Error(e)
                throw e
            }
        }

    override suspend fun updateSampler(
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        grammar: String
    ) = withContext(llamaDispatcher) {
        if (_state.value is InferenceEngine.State.ModelReady) {
            nativeUpdateSampler(
                temp = temperature,
                topP = topP,
                topK = topK,
                repeatPenalty = repeatPenalty,
                grammar = grammar.takeIf { it.isNotBlank() }
            )
        }
    }

    override suspend fun resetConversation() =
        withContext(llamaDispatcher) {
            if (_state.value !is InferenceEngine.State.ModelReady) return@withContext
            // Empty system prompt => native side resets chat_msgs + KV cache (LLAMA_PATCHES.md #7)
            processSystemPrompt("")
            Unit
        }

    override suspend fun setSystemPrompt(systemPrompt: String) =
        withContext(llamaDispatcher) {
            require(systemPrompt.isNotBlank()) { "Cannot process empty system prompt!" }
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Cannot process system prompt in ${_state.value.javaClass.simpleName}!"
            }

            _state.value = InferenceEngine.State.ProcessingSystemPrompt
            processSystemPrompt(systemPrompt).let { result ->
                if (result != 0) {
                    RuntimeException("Failed to process system prompt: $result").also {
                        _state.value = InferenceEngine.State.Error(it)
                        throw it
                    }
                }
            }
            _state.value = InferenceEngine.State.ModelReady
        }

    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
    ): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "User prompt discarded due to: ${_state.value.javaClass.simpleName}"
        }

        try {
            _cancelGeneration = false
            _state.value = InferenceEngine.State.ProcessingUserPrompt

            processUserPrompt(message, predictLength).let { result ->
                if (result != 0) {
                    if (_cancelGeneration) {
                        _state.value = InferenceEngine.State.ModelReady
                        return@flow
                    }
                    throw IOException("Failed to process user prompt (code $result)")
                }
            }

            _state.value = InferenceEngine.State.Generating
            while (!_cancelGeneration) {
                generateNextToken()?.let { utf8token ->
                    if (utf8token.isNotEmpty()) emit(utf8token)
                } ?: break
            }
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            requestAbort()
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    override fun sendUserPromptBytes(
        message: String,
        predictLength: Int
    ): Flow<ByteArray> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "User prompt discarded due to: ${_state.value.javaClass.simpleName}"
        }

        try {
            _cancelGeneration = false
            _state.value = InferenceEngine.State.ProcessingUserPrompt

            processUserPrompt(message, predictLength).let { result ->
                if (result != 0) {
                    if (_cancelGeneration) {
                        _state.value = InferenceEngine.State.ModelReady
                        return@flow
                    }
                    throw IOException("Failed to process user prompt (code $result)")
                }
            }

            _state.value = InferenceEngine.State.Generating
            while (!_cancelGeneration) {
                val bytes = generateNextTokenBytes() ?: break
                if (bytes.isNotEmpty()) {
                    emit(bytes)
                }
            }
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            requestAbort()
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    override fun abortGeneration() {
        _cancelGeneration = true
        runCatching { requestAbort() }
    }

    override fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        if (_state.value !is InferenceEngine.State.ModelReady) return -1
        return runCatching {
            runBlocking(llamaDispatcher) {
                nativeCountTokens(text)
            }
        }.getOrDefault(-1)
    }

    override fun getSystemInfo(): String {
        if (cachedSysInfo.isNotBlank()) return cachedSysInfo
        return runCatching { systemInfo().also { cachedSysInfo = it } }.getOrDefault("")
    }

    override fun hasChatTemplate(): Boolean {
        if (_state.value !is InferenceEngine.State.ModelReady) return false
        return runCatching { hasExplicitChatTemplate() }.getOrDefault(false)
    }

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Benchmark request discarded due to: $state"
            }
            _state.value = InferenceEngine.State.Benchmarking
            benchModel(pp, tg, pl, nr).also {
                _state.value = InferenceEngine.State.ModelReady
            }
        }

    override fun cleanUp() {
        abortGeneration()
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is InferenceEngine.State.ModelReady,
                is InferenceEngine.State.Generating,
                is InferenceEngine.State.ProcessingUserPrompt,
                is InferenceEngine.State.ProcessingSystemPrompt,
                is InferenceEngine.State.Benchmarking -> {
                    _state.value = InferenceEngine.State.UnloadingModel
                    runCatching { unload() }
                    _state.value = InferenceEngine.State.Initialized
                }
                is InferenceEngine.State.Error -> {
                    runCatching { unload() }
                    _state.value = InferenceEngine.State.Initialized
                }
                else -> {
                    // Already Initialized or Uninitialized — no-op
                }
            }
        }
    }

    override fun destroy() {
        abortGeneration()
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is InferenceEngine.State.Uninitialized -> {}
                is InferenceEngine.State.Initialized -> shutdown()
                else -> {
                    runCatching { unload() }
                    runCatching { shutdown() }
                }
            }
        }
        llamaScope.cancel()
    }
}
