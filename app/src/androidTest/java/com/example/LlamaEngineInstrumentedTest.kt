package com.example

import android.os.Build
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.EngineProvider
import com.example.domain.InferenceParams
import com.example.domain.LlamaEngine
import com.example.domain.StubEngine
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Physical ARM64 device instrumented test for [LlamaEngine] and [EngineProvider].
 *
 * Verifies:
 * 1. On x86/emulator devices, [EngineProvider] gracefully falls back to [StubEngine] without crashing.
 * 2. On physical ARM64 devices, [EngineProvider] initializes [LlamaEngine] with native `libai-chat.so`.
 * 3. When a tiny GGUF model (`SmolLM2 360M` or `Qwen2.5-Coder 0.5B Q4_K_M`) is present in `filesDir/models`,
 *    loads it, verifies real token streaming, verifies `stop()` cancels generation in < 500ms,
 *    and verifies `unload()` frees native memory.
 */
@RunWith(AndroidJUnit4::class)
class LlamaEngineInstrumentedTest {

    @Test
    fun engineProviderSelectsAppropriateRuntimeWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EngineProvider.initialize(context)
        val engine = EngineProvider.getEngine(context)
        val abis = Build.SUPPORTED_ABIS.toList()
        val isEmulator = EngineProvider.isLikelyEmulator()

        if (abis.any { it.equals("arm64-v8a", ignoreCase = true) } && !isEmulator) {
            assertTrue(
                "Expected LlamaEngine on physical ARM64 device, got ${engine.info()}",
                engine is LlamaEngine && EngineProvider.isRealRuntimeActive()
            )
        } else {
            assertTrue(
                "Expected StubEngine fallback on emulator or non-ARM64 ABI, got ${engine.info()}",
                engine is StubEngine && !EngineProvider.isRealRuntimeActive()
            )
        }
    }

    @Test
    fun physicalArm64LoadStreamStopUnder500msAndUnloadFreesMemory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EngineProvider.initialize(context)
        val engine = EngineProvider.getEngine(context)

        assumeTrue(
            "Requires physical ARM64 device with active LlamaEngine",
            engine is LlamaEngine && EngineProvider.isRealRuntimeActive()
        )

        val modelsDir = File(context.filesDir, "models")
        val targetModels = listOf(
            "SmolLM2-360M-Instruct-Q8_0.gguf" to 386_404_992L,
            "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf" to 397_807_936L
        )

        val availableModel = targetModels.firstNotNullOfOrNull { (fileName, expectedBytes) ->
            val file = File(modelsDir, fileName)
            if (file.exists() && file.length() > 1024L) file to expectedBytes else null
        } ?: modelsDir.listFiles()?.firstOrNull { it.extension.equals("gguf", ignoreCase = true) }?.let {
            it to it.length()
        }

        assumeTrue(
            "Requires SmolLM2-360M or Qwen2.5-Coder-0.5B GGUF in ${modelsDir.absolutePath}",
            availableModel != null
        )

        val (modelFile, expectedBytes) = availableModel!!
        val loadParams = InferenceParams(
            contextLength = 1024,
            threads = 4,
            threadsBatch = 4,
            batchSize = 256,
            useMmap = true,
            flashAttention = true,
            kvCacheType = "q8_0",
            expectedSizeBytes = expectedBytes
        )

        val loadResult = engine.load(modelFile.absolutePath, loadParams)
        assertTrue("Expected loadResult.isSuccess but got $loadResult", loadResult.isSuccess)
        assertTrue("Expected engine.isLoaded to be true after load", engine.isLoaded.value)

        val heapLoaded = Debug.getNativeHeapAllocatedSize()

        // 1. Stream tokens and verify real non-empty output
        val streamedTokens = StringBuilder()
        engine.generate(
            prompt = "Write a single HTML heading tag saying Hello Nexus:",
            params = InferenceParams(maxTokens = 24, temperature = 0.2f)
        ).collect { token ->
            streamedTokens.append(token)
        }
        assertTrue("Expected non-empty streamed tokens from GGUF model", streamedTokens.isNotEmpty())

        // 2. Verify stop() aborts generation within 500ms
        var firstTokenObserved = false
        val genJob = launch {
            engine.generate(
                prompt = "Write a detailed 500-word tutorial about HTML5 and CSS3 layout architecture:",
                params = InferenceParams(maxTokens = 256, temperature = 0.7f)
            ).collect {
                firstTokenObserved = true
            }
        }
        var waitedMs = 0
        while (!firstTokenObserved && waitedMs < 5000) {
            delay(20)
            waitedMs += 20
        }
        val stopElapsedMs = measureTimeMillis {
            engine.stop()
            genJob.cancelAndJoin()
        }
        assertTrue("Expected stop() to complete in < 500ms, took ${stopElapsedMs}ms", stopElapsedMs < 500L)

        // 3. Verify unload() transitions state and frees native heap
        engine.unload()
        assertFalse("Expected engine.isLoaded to be false after unload()", engine.isLoaded.value)
        val heapAfterUnload = Debug.getNativeHeapAllocatedSize()
        assertTrue(
            "Expected native heap after unload ($heapAfterUnload) <= loaded heap ($heapLoaded)",
            heapAfterUnload <= heapLoaded
        )
    }
}
