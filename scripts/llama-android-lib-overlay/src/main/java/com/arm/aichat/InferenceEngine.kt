package com.arm.aichat

import com.arm.aichat.InferenceEngine.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Configuration parameters for native llama.cpp context and sampler initialization.
 */
data class NativeLoadConfig(
    val nCtx: Int = 2048,
    val nThreads: Int = 4,
    val nThreadsBatch: Int = 4,
    val nBatch: Int = 256,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val kvCacheQ8_0: Boolean = true,
    val temperature: Float = 0.3f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.10f,
    val grammar: String = ""
)

/**
 * Interface defining the core LLM inference operations.
 */
interface InferenceEngine {
    /**
     * Current state of the inference engine
     */
    val state: StateFlow<State>

    /**
     * Load a model from the given path with default settings.
     *
     * @throws UnsupportedArchitectureException if model architecture not supported
     */
    suspend fun loadModel(pathToModel: String)

    /**
     * Load a model from the given path with explicit native context & sampler settings.
     *
     * @throws UnsupportedArchitectureException if model architecture not supported
     */
    suspend fun loadModel(pathToModel: String, config: NativeLoadConfig)

    /**
     * Updates the native sampler chain parameters before generation.
     */
    suspend fun updateSampler(
        temperature: Float,
        topP: Float = 0.95f,
        topK: Int = 40,
        repeatPenalty: Float = 1.10f,
        grammar: String = ""
    )

    /**
     * Sends a system prompt to the loaded model (reusing KV cache prefix when unchanged).
     */
    suspend fun setSystemPrompt(systemPrompt: String)

    /**
     * Sends a user prompt to the loaded model and returns a Flow of generated tokens.
     */
    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>

    /**
     * Sends a user prompt to the loaded model and returns a Flow of raw UTF-8 token byte chunks.
     */
    fun sendUserPromptBytes(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<ByteArray>

    /**
     * Immediately signals the native llama_decode abort callback so generation stops in < 500ms.
     */
    fun abortGeneration()

    /**
     * Tokenizes [text] using the currently loaded model's vocabulary and returns the token count,
     * or -1 if no model is loaded.
     */
    fun countTokens(text: String): Int

    /**
     * Returns llama.cpp system/CPU feature info string.
     */
    fun getSystemInfo(): String

    /**
     * Returns true if the loaded GGUF model has an explicit native chat template.
     */
    fun hasChatTemplate(): Boolean

    /**
     * NEXUS PATCH (LLAMA_PATCHES.md #7): clears chat history and the KV cache so the next
     * pre-templated user prompt starts from position 0. Default no-op for test fakes.
     */
    suspend fun resetConversation() {}

    /**
     * Runs a benchmark with the specified parameters.
     */
    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String

    /**
     * Unloads the currently loaded model.
     */
    fun cleanUp()

    /**
     * Cleans up resources when the engine is no longer needed.
     */
    fun destroy()

    /**
     * States of the inference engine
     */
    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()

        object LoadingModel : State()
        object UnloadingModel : State()
        object ModelReady : State()

        object Benchmarking : State()
        object ProcessingSystemPrompt : State()
        object ProcessingUserPrompt : State()

        object Generating : State()

        data class Error(val exception: Exception) : State()
    }

    companion object {
        const val DEFAULT_PREDICT_LENGTH = 1024
    }
}

val State.isUninterruptible
    get() = this is State.Initializing ||
        this is State.LoadingModel ||
        this is State.UnloadingModel ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt

val State.isModelLoaded: Boolean
    get() = this is State.ModelReady ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt ||
        this is State.Generating

class UnsupportedArchitectureException : Exception()
