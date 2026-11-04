package com.example.domain

import android.content.Context
import android.os.Build
import com.arm.aichat.AiChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single point of truth for the active [LocalInferenceEngine] implementation.
 * Safely initializes [LlamaEngine] on physical ARM64 devices and falls back to [StubEngine]
 * on `UnsatisfiedLinkError`, `SecurityException`, non-ARM64 ABIs, or emulators without crashing.
 */
object EngineProvider {
    private val _engineFlow = MutableStateFlow<LocalInferenceEngine>(StubEngine())
    val engineFlow: StateFlow<LocalInferenceEngine> = _engineFlow.asStateFlow()

    @Volatile
    private var initializedWithContext = false

    var currentEngine: LocalInferenceEngine
        get() = _engineFlow.value
        set(value) {
            _engineFlow.value = value
        }

    fun engineInfo(): EngineInfo = currentEngine.info()

    /**
     * Initializes the native [LlamaEngine] if not already initialized.
     * Safe to call multiple times; never crashes the app if the native library fails to load.
     */
    @Synchronized
    fun initialize(
        context: Context,
        allowEmulator: Boolean = false
    ): LocalInferenceEngine {
        if (initializedWithContext && _engineFlow.value is LlamaEngine) {
            return _engineFlow.value
        }
        val abis = runCatching { Build.SUPPORTED_ABIS ?: emptyArray() }.getOrDefault(emptyArray())
        val isEmu = if (allowEmulator) false else isRunningOnEmulator()
        val resolved = createEngineWithFallback(
            supportedAbis = abis,
            isEmulator = isEmu,
            nativeLoader = {
                val nativeBinding = AiChat.getInferenceEngine(context.applicationContext)
                LlamaEngine(
                    context = context.applicationContext,
                    nativeEngineOverride = nativeBinding
                )
            }
        )
        _engineFlow.value = resolved
        initializedWithContext = true
        return resolved
    }

    /**
     * Pure, unit-testable factory that selects [LlamaEngine] or falls back to [StubEngine]
     * on non-ARM64 devices, emulators, `UnsatisfiedLinkError`, or `SecurityException`.
     */
    fun createEngineWithFallback(
        supportedAbis: Array<String>,
        isEmulator: Boolean,
        nativeLoader: () -> LocalInferenceEngine
    ): LocalInferenceEngine {
        val primaryAbi = supportedAbis.firstOrNull() ?: "unknown"
        val supportsArm64 = supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }

        if (!supportsArm64) {
            return StubEngine(
                customStatusMessage = "On-device GGUF inference needs an ARM64 (arm64-v8a) phone (detected: $primaryAbi).",
                detectedAbi = primaryAbi
            )
        }

        if (isEmulator) {
            return StubEngine(
                customStatusMessage = "On-device GGUF inference needs a physical ARM64 phone (emulator detected: $primaryAbi).",
                detectedAbi = primaryAbi
            )
        }

        return try {
            nativeLoader()
        } catch (e: UnsatisfiedLinkError) {
            StubEngine(
                customStatusMessage = "On-device runtime library could not be linked (${e.message ?: "UnsatisfiedLinkError"}). Needs an ARM64 phone.",
                detectedAbi = primaryAbi
            )
        } catch (e: SecurityException) {
            StubEngine(
                customStatusMessage = "On-device runtime blocked by device security policy (${e.message ?: "SecurityException"}).",
                detectedAbi = primaryAbi
            )
        } catch (e: Throwable) {
            StubEngine(
                customStatusMessage = "On-device runtime unavailable (${e.javaClass.simpleName}).",
                detectedAbi = primaryAbi
            )
        }
    }

    fun isRunningOnEmulator(): Boolean {
        return try {
            val fingerprint = (Build.FINGERPRINT ?: "").lowercase()
            val model = (Build.MODEL ?: "").lowercase()
            val product = (Build.PRODUCT ?: "").lowercase()
            val hardware = (Build.HARDWARE ?: "").lowercase()
            val abis = (Build.SUPPORTED_ABIS ?: emptyArray()).map { it.lowercase() }
            // Treat x86 / x86_64 or generic SDK emulator images without arm64 hardware as emulator
            val isX86Primary = abis.firstOrNull()?.let { it.contains("x86") } == true
            isX86Primary ||
                fingerprint.startsWith("generic") ||
                model.contains("sdk_gphone") ||
                model.contains("emulator") ||
                product.contains("sdk_gphone") ||
                hardware.contains("ranchu") ||
                hardware.contains("goldfish")
        } catch (_: Throwable) {
            false
        }
    }

    fun resetToDefault() {
        initializedWithContext = false
        _engineFlow.value = StubEngine()
    }
}
