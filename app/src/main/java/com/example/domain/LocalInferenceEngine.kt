package com.example.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LocalRuntimeErrorKind {
    OUT_OF_MEMORY,
    CORRUPT_FILE,
    UNSUPPORTED_MODEL,
    MODEL_NOT_LOADED,
    GENERATION_FAILED,
    RUNTIME_UNAVAILABLE
}

class TypedLocalInferenceException(
    val kind: LocalRuntimeErrorKind,
    val modelId: String = "",
    val userTitle: String,
    val userMessage: String,
    val suggestedActionLabel: String = "",
    val suggestedModelId: String? = null,
    cause: Throwable? = null
) : IllegalStateException(userMessage, cause)

data class InferenceParams(
    val contextLength: Int = 2048,
    val threads: Int = 4,
    val threadsBatch: Int = threads,
    val batchSize: Int = 128,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val kvCacheType: String = "q8_0",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.10f,
    val maxTokens: Int = 1024,
    val systemInstruction: String = "",
    val stopSequences: List<String> = listOf("```sh-exec\n"),
    val grammar: String = "",
    val expectedSizeBytes: Long = 0L,
    val availableRamBytes: Long = 0L
)

data class EngineInfo(
    val name: String,
    val version: String,
    val isRuntimeInstalled: Boolean,
    val statusMessage: String,
    val llamaCppTag: String = "b12020",
    val backendInfo: String = "CPU",
    val cpuFeatures: String = "",
    val abi: String = "arm64-v8a"
) {
    val isAvailable: Boolean get() = isRuntimeInstalled
    val statusBannerMessage: String get() = statusMessage
}

/**
 * Clean abstraction for on-device GGUF inference engines.
 * No synthetic text or latency generators are permitted in any implementation.
 */
interface LocalInferenceEngine {
    suspend fun load(path: String, params: InferenceParams = InferenceParams()): Result<Unit>
    fun generate(prompt: String, params: InferenceParams = InferenceParams()): Flow<String>
    fun stop()
    fun unload()
    val isLoaded: StateFlow<Boolean>
    fun info(): EngineInfo
    fun countTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)
    fun onTrimMemory(level: Int) {}
}
