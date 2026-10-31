package com.example.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Honest default implementation of [LocalInferenceEngine] when no native JNI llama.cpp runtime is bundled.
 * Never returns generated filler text or synthetic speeds.
 */
class StubEngine : LocalInferenceEngine {
    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    override suspend fun load(path: String, params: InferenceParams): Result<Unit> {
        _isLoaded.value = false
        return Result.failure(IllegalStateException("Native runtime not installed"))
    }

    override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
        throw IllegalStateException("Native runtime not installed")
    }

    override fun stop() {
        // Nothing active to stop when native runtime is not installed.
    }

    override fun unload() {
        _isLoaded.value = false
    }

    override fun info(): EngineInfo = EngineInfo(
        name = "StubEngine",
        version = "1.0",
        isRuntimeInstalled = false,
        statusMessage = "Runtime not installed: models can be downloaded but not run yet"
    )
}
