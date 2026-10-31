package com.example.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single point of truth for the active [LocalInferenceEngine] implementation.
 * Swap [StubEngine] for a native JNI `LlamaCppEngine` here in a single line when NDK binaries are added.
 */
object EngineProvider {
    private val _engineFlow = MutableStateFlow<LocalInferenceEngine>(StubEngine())
    val engineFlow: StateFlow<LocalInferenceEngine> = _engineFlow.asStateFlow()

    var currentEngine: LocalInferenceEngine
        get() = _engineFlow.value
        set(value) {
            _engineFlow.value = value
        }

    fun engineInfo(): EngineInfo = currentEngine.info()

    fun resetToDefault() {
        _engineFlow.value = StubEngine()
    }
}
