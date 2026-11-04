package com.example.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Fallback implementation of [LocalInferenceEngine] when the native JNI llama.cpp runtime
 * cannot be loaded (e.g. non-ARM64 ABI, x86/x86_64 emulator, or missing `.so`).
 * Never returns generated filler text or synthetic speeds.
 */
class StubEngine(
    private val customStatusMessage: String = "Runtime not installed: models can be downloaded but not run yet",
    private val detectedAbi: String = "unknown"
) : LocalInferenceEngine {
    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    override suspend fun load(path: String, params: InferenceParams): Result<Unit> {
        _isLoaded.value = false
        return Result.failure(
            TypedLocalInferenceException(
                kind = LocalRuntimeErrorKind.RUNTIME_UNAVAILABLE,
                userTitle = "On-device runtime unavailable",
                userMessage = customStatusMessage
            )
        )
    }

    override fun generate(prompt: String, params: InferenceParams): Flow<String> = flow {
        throw TypedLocalInferenceException(
            kind = LocalRuntimeErrorKind.RUNTIME_UNAVAILABLE,
            userTitle = "On-device runtime unavailable",
            userMessage = customStatusMessage
        )
    }

    override fun stop() {
        // Nothing active to stop when native runtime is not active.
    }

    override fun unload() {
        _isLoaded.value = false
    }

    override fun info(): EngineInfo = EngineInfo(
        name = "StubEngine",
        version = "1.0",
        isRuntimeInstalled = false,
        statusMessage = customStatusMessage,
        llamaCppTag = LlamaEngine.LLAMA_CPP_TAG,
        backendInfo = "Stub (Unavailable)",
        cpuFeatures = "None",
        abi = detectedAbi
    )
}
