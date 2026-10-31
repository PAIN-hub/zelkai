package com.example.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class InferenceParams(
    val contextLength: Int = 2048,
    val threads: Int = 4,
    val batchSize: Int = 128,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val systemInstruction: String = "",
    val stopSequences: List<String> = listOf("```sh-exec\n")
)

data class EngineInfo(
    val name: String,
    val version: String,
    val isRuntimeInstalled: Boolean,
    val statusMessage: String
) {
    val isAvailable: Boolean get() = isRuntimeInstalled
    val statusBannerMessage: String get() = statusMessage
}

/**
 * Clean abstraction for on-device GGUF inference engines.
 * No synthetic text or latency generators are permitted in any implementation.
 */
interface LocalInferenceEngine {
    suspend fun load(path: String, params: InferenceParams): Result<Unit>
    fun generate(prompt: String, params: InferenceParams): Flow<String>
    fun stop()
    fun unload()
    val isLoaded: StateFlow<Boolean>
    fun info(): EngineInfo
}
