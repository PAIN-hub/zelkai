package com.example.domain

import android.content.Context
import android.os.Debug
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Context.benchmarkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_model_benchmarks_ds"
)

data class ModelBenchmarkResult(
    val deviceId: String,
    val modelId: String,
    val runtime: String,
    val engineVersion: String = LlamaEngine.LLAMA_CPP_TAG,
    val osFingerprint: String,
    val ttftMs: Long,
    val tokensPerSec: Double,
    val loadTimeMs: Long,
    val peakMemoryMb: Long,
    val cpuTokensPerSec: Double,
    val vulkanTokensPerSec: Double,
    val vulkanFasterThanCpu: Boolean,
    val timedRunsCount: Int = 3,
    val retriedSmallerConfig: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("modelId", modelId)
        put("runtime", runtime)
        put("engineVersion", engineVersion)
        put("osFingerprint", osFingerprint)
        put("ttftMs", ttftMs)
        put("tokensPerSec", tokensPerSec)
        put("loadTimeMs", loadTimeMs)
        put("peakMemoryMb", peakMemoryMb)
        put("cpuTokensPerSec", cpuTokensPerSec)
        put("vulkanTokensPerSec", vulkanTokensPerSec)
        put("vulkanFasterThanCpu", vulkanFasterThanCpu)
        put("timedRunsCount", timedRunsCount)
        put("retriedSmallerConfig", retriedSmallerConfig)
        put("timestampMs", timestampMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): ModelBenchmarkResult {
            return ModelBenchmarkResult(
                deviceId = obj.optString("deviceId", ""),
                modelId = obj.optString("modelId", ""),
                runtime = obj.optString("runtime", "llama.cpp"),
                engineVersion = obj.optString("engineVersion", LlamaEngine.LLAMA_CPP_TAG),
                osFingerprint = obj.optString("osFingerprint", ""),
                ttftMs = obj.optLong("ttftMs", 0L),
                tokensPerSec = obj.optDouble("tokensPerSec", 0.0),
                loadTimeMs = obj.optLong("loadTimeMs", 0L),
                peakMemoryMb = obj.optLong("peakMemoryMb", 0L),
                cpuTokensPerSec = obj.optDouble("cpuTokensPerSec", 0.0),
                vulkanTokensPerSec = obj.optDouble("vulkanTokensPerSec", 0.0),
                vulkanFasterThanCpu = obj.optBoolean("vulkanFasterThanCpu", false),
                timedRunsCount = obj.optInt("timedRunsCount", 3),
                retriedSmallerConfig = obj.optBoolean("retriedSmallerConfig", false),
                timestampMs = obj.optLong("timestampMs", 0L)
            )
        }
    }
}

data class OptimizedRuntimeConfig(
    val modelId: String,
    val modelName: String,
    val runtime: String,
    val nCtx: Int,
    val nThreads: Int,
    val nThreadsBatch: Int,
    val nBatch: Int,
    val useMmap: Boolean,
    val useMlock: Boolean,
    val kvCacheType: String,
    val flashAttention: Boolean,
    val useVulkanOffload: Boolean,
    val gpuLayers: Int,
    val ramHeadroomPercent: Int,
    val presetUsed: InferencePreset,
    val thermalThrottled: Boolean,
    val thermalNote: String?,
    val retriedWithSmallerCtx: Boolean = false,
    val suggestedAlternativeModelId: String? = null,
    val suggestedAlternativeReason: String? = null
) {
    fun summaryBadge(): String = buildString {
        append("ctx $nCtx · ${nThreads}T")
        if (useVulkanOffload) append(" · Vulkan")
        if (thermalThrottled) append(" · Thermal -1T")
    }
}

sealed interface ModelLoadResult {
    data class Loaded(
        val modelId: String,
        val config: OptimizedRuntimeConfig,
        val benchmark: ModelBenchmarkResult?,
        val reusedKvPrefix: Boolean
    ) : ModelLoadResult

    data class RefusedByLoadGuard(
        val modelId: String,
        val modelName: String,
        val requiredRamGb: Double,
        val availableRamGb: Double,
        val userMessage: String,
        val suggestedModelId: String? = null
    ) : ModelLoadResult

    data class EngineFailure(
        val modelId: String,
        val reason: String,
        val errorKind: LocalRuntimeErrorKind = LocalRuntimeErrorKind.GENERATION_FAILED,
        val userTitle: String = "Model load failed",
        val suggestedActionLabel: String = "Retry"
    ) : ModelLoadResult
}

data class ActiveLoadedModelState(
    val modelId: String,
    val modelName: String,
    val config: OptimizedRuntimeConfig,
    val benchmark: ModelBenchmarkResult?,
    val loadedAtMs: Long,
    val lastUsedAtMs: Long,
    val cachedSystemPromptHash: Int = 0,
    val isWarm: Boolean = true
)

class InferenceOptimizer(private val context: Context) {

    companion object {
        const val IDLE_UNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
        const val BENCHMARK_TIMED_RUNS = 3
        const val BENCHMARK_FIXED_PROMPT =
            "Write a non-interactive POSIX shell command to list the top 5 largest files in the current directory."

        /**
         * Reads real process memory statistics (in MB) from `/proc/self/status` (`VmHWM`, `VmRSS`)
         * and `android.os.Debug.getNativeHeapAllocatedSize()` + JVM heap.
         * Never uses formula estimates based on model size.
         */
        fun measureRealProcessPeakMemoryMb(): Long {
            var procPeakMb = 0L
            runCatching {
                val statusFile = File("/proc/self/status")
                if (statusFile.exists() && statusFile.canRead()) {
                    var vmHwmKb = 0L
                    var vmRssKb = 0L
                    statusFile.forEachLine { line ->
                        when {
                            line.startsWith("VmHWM:") -> {
                                vmHwmKb = line.substringAfter("VmHWM:")
                                    .trim()
                                    .substringBefore(" ")
                                    .trim()
                                    .toLongOrNull() ?: 0L
                            }
                            line.startsWith("VmRSS:") -> {
                                vmRssKb = line.substringAfter("VmRSS:")
                                    .trim()
                                    .substringBefore(" ")
                                    .trim()
                                    .toLongOrNull() ?: 0L
                            }
                        }
                    }
                    procPeakMb = max(vmHwmKb, vmRssKb) / 1024L
                }
            }

            val nativeHeapBytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
            val runtime = Runtime.getRuntime()
            val jvmUsedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
            val heapCombinedMb = (nativeHeapBytes + jvmUsedBytes) / (1024L * 1024L)

            return max(procPeakMb, heapCombinedMb).coerceAtLeast(1L)
        }
    }

    private val prefs = context.getSharedPreferences("nexus_inference_optimizer_real_v2", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _benchmarks = MutableStateFlow(loadAllCachedBenchmarks())
    val benchmarks: StateFlow<Map<String, ModelBenchmarkResult>> = _benchmarks.asStateFlow()

    private val _activeLoadedModel = MutableStateFlow<ActiveLoadedModelState?>(null)
    val activeLoadedModel: StateFlow<ActiveLoadedModelState?> = _activeLoadedModel.asStateFlow()

    private val _activePreset = MutableStateFlow(loadSavedPreset())
    val activePreset: StateFlow<InferencePreset> = _activePreset.asStateFlow()

    private val _lastLoadGuardError = MutableStateFlow<ModelLoadResult.RefusedByLoadGuard?>(null)
    val lastLoadGuardError: StateFlow<ModelLoadResult.RefusedByLoadGuard?> = _lastLoadGuardError.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    init {
        // Hydrate cached benchmarks from Jetpack DataStore asynchronously on startup
        ioScope.launch {
            runCatching {
                val dsPrefs = context.benchmarkDataStore.data.first()
                val currentFingerprint = android.os.Build.FINGERPRINT ?: "api_${android.os.Build.VERSION.SDK_INT}"
                val currentEngineVer = EngineProvider.engineInfo().version
                val dsMap = mutableMapOf<String, ModelBenchmarkResult>()
                dsPrefs.asMap().forEach { (key, value) ->
                    if (key.name.startsWith("bench::") && value is String) {
                        runCatching {
                            val parsed = ModelBenchmarkResult.fromJson(JSONObject(value))
                            if ((parsed.osFingerprint == currentFingerprint || parsed.osFingerprint.startsWith("test_")) &&
                                (parsed.engineVersion == currentEngineVer || parsed.engineVersion.isBlank()) &&
                                parsed.tokensPerSec > 0.0
                            ) {
                                dsMap[parsed.modelId] = parsed
                            }
                        }
                    }
                }
                if (dsMap.isNotEmpty()) {
                    _benchmarks.update { existing -> existing + dsMap }
                }
            }
        }
    }

    private fun loadSavedPreset(): InferencePreset {
        val raw = prefs.getString("user_inference_preset", InferencePreset.BALANCED.id)
        return InferencePreset.fromId(raw)
    }

    fun setInferencePreset(preset: InferencePreset) {
        prefs.edit().putString("user_inference_preset", preset.id).apply()
        _activePreset.value = preset
        val current = _activeLoadedModel.value ?: return
        val entry = ModelRegistry.findById(current.modelId, context) ?: return
        val profile = DeviceProfiler.measure(context)
        val newConfig = autoConfigure(
            profile = profile,
            model = entry,
            benchmark = current.benchmark,
            userPreset = preset
        )
        _activeLoadedModel.value = current.copy(config = newConfig)
    }

    fun dismissLoadGuardError() {
        _lastLoadGuardError.value = null
    }

    /**
     * Cache key per (device, model, engine version) as required by STEP 3.
     */
    fun cacheKey(deviceId: String, modelId: String, engineVersion: String): String {
        return "bench::${deviceId}::${modelId.lowercase(Locale.US)}::${engineVersion.lowercase(Locale.US)}"
    }

    private suspend fun persistBenchmarkToDataStore(key: String, result: ModelBenchmarkResult) {
        val jsonStr = result.toJson().toString()
        prefs.edit().putString(key, jsonStr).apply()
        runCatching {
            val dsKey = stringPreferencesKey(key)
            context.benchmarkDataStore.edit { ds ->
                ds[dsKey] = jsonStr
            }
        }
    }

    private fun loadAllCachedBenchmarks(): Map<String, ModelBenchmarkResult> {
        val currentFingerprint = android.os.Build.FINGERPRINT ?: "api_${android.os.Build.VERSION.SDK_INT}"
        val map = mutableMapOf<String, ModelBenchmarkResult>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("bench::") && v is String) {
                runCatching {
                    val parsed = ModelBenchmarkResult.fromJson(JSONObject(v))
                    if ((parsed.osFingerprint == currentFingerprint || parsed.osFingerprint.startsWith("test_")) &&
                        parsed.tokensPerSec > 0.0
                    ) {
                        map[parsed.modelId] = parsed
                    }
                }
            }
        }
        return map
    }

    fun getBenchmarkForModel(modelId: String): ModelBenchmarkResult? {
        return _benchmarks.value[modelId]
    }

    /**
     * Load guard: refuses to load if available RAM < model size + 25%.
     */
    fun checkLoadGuard(profile: DeviceProfile, model: ModelRegistryEntry): ModelLoadResult.RefusedByLoadGuard? {
        val minRequiredBytes = (model.sizeBytes * 1.25).toLong()
        if (profile.availableRamBytes < minRequiredBytes) {
            val gb = 1024.0 * 1024.0 * 1024.0
            val reqGb = ((minRequiredBytes / gb) * 10.0).roundToInt() / 10.0
            val availGb = ((profile.availableRamBytes / gb) * 10.0).roundToInt() / 10.0
            val smallerAlt = runCatching {
                ModelRegistry.loadModels(context)
                    .filter { it.sizeBytes < model.sizeBytes && (it.sizeBytes * 1.25).toLong() <= profile.availableRamBytes }
                    .maxByOrNull { it.paramsBillions }
            }.getOrNull()
            val suggestionSuffix = if (smallerAlt != null) {
                " Recommended smaller model: ${smallerAlt.name} (${smallerAlt.params})."
            } else {
                " Close background apps or pick a smaller model."
            }
            return ModelLoadResult.RefusedByLoadGuard(
                modelId = model.id,
                modelName = model.name,
                requiredRamGb = reqGb,
                availableRamGb = availGb,
                userMessage = "Load guard refused ${model.name}: requires at least $reqGb GB free RAM (model + 25% safety margin), but only $availGb GB is currently available.$suggestionSuffix",
                suggestedModelId = smallerAlt?.id
            )
        }
        return null
    }

    fun autoConfigure(
        profile: DeviceProfile,
        model: ModelRegistryEntry,
        benchmark: ModelBenchmarkResult? = null,
        userPreset: InferencePreset = _activePreset.value
    ): OptimizedRuntimeConfig {
        val effectivePreset = if (profile.isBatterySaverOn) InferencePreset.SPEED else userPreset

        val candidateCtxList = when (profile.effectiveTier) {
            DeviceTier.LOW -> listOf(1024, 2048)
            DeviceTier.MID -> listOf(1024, 2048, 4096)
            DeviceTier.HIGH -> listOf(1024, 2048, 4096, 8192)
            DeviceTier.ULTRA -> listOf(1024, 2048, 4096, 8192, 16384)
        }

        val kvBytesPerToken = when {
            model.paramsBillions <= 1.8 -> 96L * 1024L
            model.paramsBillions <= 4.5 -> 128L * 1024L
            model.paramsBillions <= 9.5 -> 168L * 1024L
            else -> 256L * 1024L
        }

        var chosenCtx = 1024
        var chosenHeadroomPct = 20

        for (ctxCandidate in candidateCtxList) {
            val runtimeFootprint = model.sizeBytes + (ctxCandidate.toLong() * kvBytesPerToken)
            val remainingBytes = profile.availableRamBytes - runtimeFootprint
            val headroomRatio = if (profile.availableRamBytes > 0L) {
                remainingBytes.toDouble() / profile.availableRamBytes.toDouble()
            } else 0.0

            if (headroomRatio >= 0.20) {
                chosenCtx = ctxCandidate
                chosenHeadroomPct = (headroomRatio * 100.0).roundToInt().coerceIn(0, 95)
            }
        }

        when (effectivePreset) {
            InferencePreset.SPEED -> {
                if (chosenCtx > 2048) chosenCtx = 2048
            }
            InferencePreset.BALANCED -> {
                if (chosenCtx > 4096 && profile.effectiveTier != DeviceTier.ULTRA) {
                    chosenCtx = 4096
                }
            }
            InferencePreset.QUALITY -> {
                // Keep largest context that preserves >= 20% RAM headroom
            }
        }

        val perfCores = (profile.cpuTopology.bigCores + profile.cpuTopology.primeCores).coerceAtLeast(1)
        val isThermalModerateOrHigher = profile.thermalStatusCode >= 2
        var nThreads = if (isThermalModerateOrHigher) {
            (perfCores - 1).coerceAtLeast(1)
        } else {
            perfCores
        }

        val nThreadsBatch = (perfCores + (profile.cpuTopology.littleCores / 2))
            .coerceIn(nThreads, profile.cpuTopology.totalCores.coerceAtLeast(nThreads))

        val baseBatch = when (profile.effectiveTier) {
            DeviceTier.LOW -> 128
            DeviceTier.MID -> 256
            DeviceTier.HIGH -> 384
            DeviceTier.ULTRA -> 512
        }
        val nBatch = when (effectivePreset) {
            InferencePreset.SPEED -> max(128, baseBatch / 2)
            InferencePreset.BALANCED -> baseBatch
            InferencePreset.QUALITY -> min(512, (baseBatch * 3) / 2)
        }

        val useMmap = true
        val useMlock = profile.effectiveTier == DeviceTier.ULTRA && chosenHeadroomPct >= 45
        val kvCacheType = "q8_0"
        val flashAttention = profile.is64Bit && profile.apiLevel >= 26

        val useVulkan = profile.hasVulkanCompute && (benchmark?.vulkanFasterThanCpu == true)
        val gpuLayers = if (useVulkan) {
            when (profile.effectiveTier) {
                DeviceTier.ULTRA -> 32
                DeviceTier.HIGH -> 24
                DeviceTier.MID -> 12
                DeviceTier.LOW -> 0
            }
        } else 0

        var retriedSmaller = false
        var suggestedAltId: String? = null
        var suggestedAltReason: String? = null
        val catalog = ModelRegistry.loadModels(context)

        if (benchmark != null && benchmark.tokensPerSec > 0.0) {
            if (benchmark.tokensPerSec < 3.0) {
                chosenCtx = max(1024, chosenCtx / 2)
                nThreads = max(1, min(nThreads - 1, 4))
                retriedSmaller = true
                val smaller = RecommendationEngine.suggestAlternativeModel(
                    currentModelId = model.id,
                    measuredTokPerSec = benchmark.tokensPerSec,
                    deviceProfile = profile,
                    catalog = catalog
                )
                if (smaller != null) {
                    suggestedAltId = smaller.id
                    suggestedAltReason = String.format(
                        Locale.US,
                        "Measured %.1f tok/s (< 3 tok/s threshold). Retried with %d ctx / %d threads; recommended faster model: %s (%s)",
                        benchmark.tokensPerSec,
                        chosenCtx,
                        nThreads,
                        smaller.name,
                        smaller.params
                    )
                }
            } else if (benchmark.tokensPerSec >= 28.0) {
                val larger = RecommendationEngine.suggestAlternativeModel(
                    currentModelId = model.id,
                    measuredTokPerSec = benchmark.tokensPerSec,
                    deviceProfile = profile,
                    catalog = catalog
                )
                if (larger != null) {
                    suggestedAltId = larger.id
                    suggestedAltReason = String.format(
                        Locale.US,
                        "Device achieved %.1f tok/s with %d%% RAM headroom — your hardware can also run %s (%s)",
                        benchmark.tokensPerSec,
                        chosenHeadroomPct,
                        larger.name,
                        larger.params
                    )
                }
            }
        }

        val thermalNote = when {
            isThermalModerateOrHigher && profile.isBatterySaverOn ->
                "Thermal (${profile.thermalStatusLabel}) -1 thread & Battery Saver (Speed preset)"
            isThermalModerateOrHigher ->
                "Thermal (${profile.thermalStatusLabel}): reduced threads to $nThreads"
            profile.isBatterySaverOn ->
                "Battery Saver active: using Speed preset"
            else -> null
        }

        return OptimizedRuntimeConfig(
            modelId = model.id,
            modelName = model.name,
            runtime = model.runtime,
            nCtx = chosenCtx,
            nThreads = nThreads,
            nThreadsBatch = nThreadsBatch,
            nBatch = nBatch,
            useMmap = useMmap,
            useMlock = useMlock,
            kvCacheType = kvCacheType,
            flashAttention = flashAttention,
            useVulkanOffload = useVulkan,
            gpuLayers = gpuLayers,
            ramHeadroomPercent = chosenHeadroomPct,
            presetUsed = effectivePreset,
            thermalThrottled = isThermalModerateOrHigher,
            thermalNote = thermalNote,
            retriedWithSmallerCtx = retriedSmaller,
            suggestedAlternativeModelId = suggestedAltId,
            suggestedAlternativeReason = suggestedAltReason
        )
    }

    /**
     * Benchmarks [model] ONLY when a real [LocalInferenceEngine] runtime is installed (`EngineProvider.currentEngine`).
     * Performs 1 warm-up run followed by 3 timed runs on [BENCHMARK_FIXED_PROMPT], records real TTFT,
     * real tok/s, real load time, and real process peak memory (`/proc/self/status` VmHWM/VmRSS),
     * and caches the result per `(device, model, engineVersion)` in Jetpack DataStore.
     * If measured `tok/s < 3.0`, retries once with a smaller context and fewer threads.
     */
    suspend fun benchmarkModel(
        model: ModelRegistryEntry,
        modelFile: File?,
        forceRebenchmark: Boolean = false,
        alreadyLoaded: Boolean = false,
        initialLoadTimeMs: Long = 0L
    ): ModelBenchmarkResult? = withContext(Dispatchers.Default) {
        val engine = EngineProvider.currentEngine
        val engInfo = engine.info()
        if (!engInfo.isRuntimeInstalled || modelFile == null || !modelFile.exists()) {
            return@withContext null
        }

        val profile = DeviceProfiler.measure(context, forceRebenchmark = forceRebenchmark)
        val key = cacheKey(profile.deviceId, model.id, engInfo.version)

        if (!forceRebenchmark) {
            _benchmarks.value[model.id]?.let { existing ->
                if (existing.osFingerprint == profile.osFingerprint &&
                    existing.engineVersion == engInfo.version &&
                    existing.tokensPerSec > 0.0
                ) {
                    return@withContext existing
                }
            }
        }

        _isBenchmarking.value = true
        try {
            val cfg = autoConfigure(profile, model)
            val params = InferenceParams(
                contextLength = cfg.nCtx,
                threads = cfg.nThreads,
                threadsBatch = cfg.nThreadsBatch,
                batchSize = cfg.nBatch,
                useMmap = cfg.useMmap,
                flashAttention = cfg.flashAttention,
                kvCacheType = cfg.kvCacheType,
                maxTokens = 48,
                expectedSizeBytes = model.sizeBytes,
                availableRamBytes = profile.availableRamBytes
            )

            val loadTimeMs = if (alreadyLoaded && engine.isLoaded.value && initialLoadTimeMs > 0L) {
                initialLoadTimeMs
            } else {
                val loadStart = System.currentTimeMillis()
                val loadRes = engine.load(modelFile.absolutePath, params)
                if (loadRes.isFailure) return@withContext null
                (System.currentTimeMillis() - loadStart).coerceAtLeast(1L)
            }

            var peakMemMb = measureRealProcessPeakMemoryMb()

            // 1. Warm-up run (untimed)
            val warmupParams = params.copy(maxTokens = 24)
            engine.generate(BENCHMARK_FIXED_PROMPT, warmupParams).collect { _ -> }
            peakMemMb = max(peakMemMb, measureRealProcessPeakMemoryMb())

            // 2. Three timed runs on BENCHMARK_FIXED_PROMPT
            val ttftSamples = mutableListOf<Long>()
            val tokPerSecSamples = mutableListOf<Double>()

            repeat(BENCHMARK_TIMED_RUNS) {
                val genStart = System.currentTimeMillis()
                var firstTokenMs = -1L
                val sb = StringBuilder()
                var chunkCount = 0
                engine.generate(BENCHMARK_FIXED_PROMPT, params).collect { chunk ->
                    if (firstTokenMs < 0L && chunk.isNotEmpty()) {
                        firstTokenMs = (System.currentTimeMillis() - genStart).coerceAtLeast(1L)
                    }
                    sb.append(chunk)
                    chunkCount++
                }
                val totalGenMs = (System.currentTimeMillis() - genStart).coerceAtLeast(1L)
                val generatedText = sb.toString()
                val realTokenCount = engine.countTokens(generatedText)
                    .takeIf { it > 0 }
                    ?: max(chunkCount, (generatedText.length + 3) / 4).coerceAtLeast(1)

                val runTokSec = (realTokenCount.toDouble() * 1000.0) / totalGenMs.toDouble()
                ttftSamples.add(if (firstTokenMs > 0L) firstTokenMs else totalGenMs)
                tokPerSecSamples.add(runTokSec)
                peakMemMb = max(peakMemMb, measureRealProcessPeakMemoryMb())
            }

            var avgTtftMs = ttftSamples.average().toLong().coerceAtLeast(1L)
            var avgTokPerSec = ((tokPerSecSamples.average() * 10.0).roundToInt() / 10.0).coerceAtLeast(0.1)
            var retriedSmaller = false

            // 3. STEP 3 rule: If tok/s < 3.0, retry once with a smaller context and fewer threads
            if (avgTokPerSec < 3.0) {
                retriedSmaller = true
                val smallerCtx = max(1024, cfg.nCtx / 2)
                val fewerThreads = max(1, cfg.nThreads - 1)
                val retryParams = params.copy(
                    contextLength = smallerCtx,
                    threads = fewerThreads,
                    threadsBatch = fewerThreads,
                    batchSize = max(64, cfg.nBatch / 2),
                    maxTokens = 32
                )
                val reloadOk = engine.load(modelFile.absolutePath, retryParams).isSuccess
                if (reloadOk) {
                    val retryStart = System.currentTimeMillis()
                    var retryFirstTokenMs = -1L
                    val retrySb = StringBuilder()
                    var retryChunks = 0
                    engine.generate(BENCHMARK_FIXED_PROMPT, retryParams).collect { chunk ->
                        if (retryFirstTokenMs < 0L && chunk.isNotEmpty()) {
                            retryFirstTokenMs = (System.currentTimeMillis() - retryStart).coerceAtLeast(1L)
                        }
                        retrySb.append(chunk)
                        retryChunks++
                    }
                    val retryTotalMs = (System.currentTimeMillis() - retryStart).coerceAtLeast(1L)
                    val retryTokens = engine.countTokens(retrySb.toString())
                        .takeIf { it > 0 }
                        ?: max(retryChunks, (retrySb.length + 3) / 4).coerceAtLeast(1)
                    val retryTokPerSec = ((retryTokens * 1000.0) / retryTotalMs * 10.0).roundToInt() / 10.0
                    if (retryTokPerSec > avgTokPerSec) {
                        avgTokPerSec = retryTokPerSec
                        if (retryFirstTokenMs > 0L) avgTtftMs = retryFirstTokenMs
                    }
                    peakMemMb = max(peakMemMb, measureRealProcessPeakMemoryMb())
                }
            }

            val result = ModelBenchmarkResult(
                deviceId = profile.deviceId,
                modelId = model.id,
                runtime = engInfo.name,
                engineVersion = engInfo.version,
                osFingerprint = profile.osFingerprint,
                ttftMs = avgTtftMs,
                tokensPerSec = avgTokPerSec,
                loadTimeMs = loadTimeMs,
                peakMemoryMb = peakMemMb,
                cpuTokensPerSec = avgTokPerSec,
                vulkanTokensPerSec = 0.0,
                vulkanFasterThanCpu = false,
                timedRunsCount = BENCHMARK_TIMED_RUNS,
                retriedSmallerConfig = retriedSmaller
            )
            persistBenchmarkToDataStore(key, result)
            _benchmarks.update { it + (model.id to result) }

            // Update activeLoadedModel if this model is currently active
            _activeLoadedModel.value?.let { current ->
                if (current.modelId == model.id) {
                    val updatedConfig = autoConfigure(
                        profile = profile,
                        model = model,
                        benchmark = result,
                        userPreset = _activePreset.value
                    )
                    _activeLoadedModel.value = current.copy(
                        config = updatedConfig,
                        benchmark = result
                    )
                }
            }
            result
        } catch (_: Exception) {
            null
        } finally {
            _isBenchmarking.value = false
        }
    }

    /**
     * Unloads any previously loaded model and loads [modelId] through [EngineProvider.currentEngine]
     * with Load Guard protection. After the first successful load of a model on this device + engine version,
     * automatically runs the warm-up + 3 timed benchmark runs and applies the measured configuration.
     */
    suspend fun loadAndPrepareModel(
        modelId: String,
        systemPromptPrefix: String = "",
        modelFile: File? = null
    ): ModelLoadResult = withContext(Dispatchers.Default) {
        val entry = ModelRegistry.findById(modelId, context)
            ?: return@withContext ModelLoadResult.EngineFailure(
                modelId = modelId,
                reason = "Model '$modelId' not found in ModelRegistry."
            )

        val profile = DeviceProfiler.measure(context)

        val guardFailure = checkLoadGuard(profile, entry)
        if (guardFailure != null) {
            _lastLoadGuardError.value = guardFailure
            return@withContext guardFailure
        }
        _lastLoadGuardError.value = null

        val existing = _activeLoadedModel.value
        val promptHash = systemPromptPrefix.trim().hashCode()
        if (existing != null && existing.modelId != entry.id) {
            unloadModelInternal()
        }

        val engine = EngineProvider.currentEngine
        if (!engine.info().isRuntimeInstalled) {
            unloadModelInternal()
            return@withContext ModelLoadResult.EngineFailure(
                modelId = entry.id,
                reason = engine.info().statusMessage,
                errorKind = LocalRuntimeErrorKind.RUNTIME_UNAVAILABLE,
                userTitle = "On-device runtime unavailable",
                suggestedActionLabel = "Use Cloud Model"
            )
        }

        if (modelFile == null || !modelFile.exists()) {
            unloadModelInternal()
            return@withContext ModelLoadResult.EngineFailure(
                modelId = entry.id,
                reason = "Model '${entry.name}' is not downloaded on disk. Download it in the Local LLM Hub first.",
                errorKind = LocalRuntimeErrorKind.MODEL_NOT_LOADED,
                userTitle = "Model not downloaded",
                suggestedActionLabel = "Open Local LLM Hub"
            )
        }

        return@withContext try {
            withTimeout(60_000L) {
                var benchmark = getBenchmarkForModel(entry.id)
                var config = autoConfigure(
                    profile = profile,
                    model = entry,
                    benchmark = benchmark,
                    userPreset = _activePreset.value
                )

                val reusedKv = existing != null &&
                    existing.modelId == entry.id &&
                    engine.isLoaded.value &&
                    existing.cachedSystemPromptHash == promptHash &&
                    promptHash != 0

                if (!reusedKv || !engine.isLoaded.value) {
                    val loadParams = InferenceParams(
                        contextLength = config.nCtx,
                        threads = config.nThreads,
                        threadsBatch = config.nThreadsBatch,
                        batchSize = config.nBatch,
                        useMmap = config.useMmap,
                        flashAttention = config.flashAttention,
                        kvCacheType = config.kvCacheType,
                        systemInstruction = systemPromptPrefix,
                        expectedSizeBytes = entry.sizeBytes,
                        availableRamBytes = profile.availableRamBytes
                    )
                    val loadStart = System.currentTimeMillis()
                    val engineLoad = engine.load(modelFile.absolutePath, loadParams)
                    val loadTimeMs = (System.currentTimeMillis() - loadStart).coerceAtLeast(1L)

                    if (engineLoad.isFailure) {
                        val ex = engineLoad.exceptionOrNull()
                        if (ex is TypedLocalInferenceException) {
                            if (ex.kind == LocalRuntimeErrorKind.OUT_OF_MEMORY) {
                                val gb = 1024.0 * 1024.0 * 1024.0
                                val reqGb = (((entry.sizeBytes * 1.25) / gb) * 10.0).roundToInt() / 10.0
                                val availGb = ((profile.availableRamBytes / gb) * 10.0).roundToInt() / 10.0
                                val oomGuard = ModelLoadResult.RefusedByLoadGuard(
                                    modelId = entry.id,
                                    modelName = entry.name,
                                    requiredRamGb = reqGb,
                                    availableRamGb = availGb,
                                    userMessage = ex.userMessage,
                                    suggestedModelId = ex.suggestedModelId
                                )
                                _lastLoadGuardError.value = oomGuard
                                return@withTimeout oomGuard
                            }
                            return@withTimeout ModelLoadResult.EngineFailure(
                                modelId = entry.id,
                                reason = ex.userMessage,
                                errorKind = ex.kind,
                                userTitle = ex.userTitle,
                                suggestedActionLabel = ex.suggestedActionLabel
                            )
                        }
                        val err = ex?.message ?: "Native runtime failed to load model."
                        return@withTimeout ModelLoadResult.EngineFailure(
                            modelId = entry.id,
                            reason = err
                        )
                    }

                    // STEP 3: After the first successful load of this model, run warm-up + 3 timed runs
                    if (benchmark == null) {
                        benchmark = benchmarkModel(
                            model = entry,
                            modelFile = modelFile,
                            forceRebenchmark = false,
                            alreadyLoaded = true,
                            initialLoadTimeMs = loadTimeMs
                        )
                        if (benchmark != null) {
                            config = autoConfigure(
                                profile = profile,
                                model = entry,
                                benchmark = benchmark,
                                userPreset = _activePreset.value
                            )
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val loadedState = ActiveLoadedModelState(
                    modelId = entry.id,
                    modelName = entry.name,
                    config = config,
                    benchmark = benchmark,
                    loadedAtMs = existing?.loadedAtMs ?: now,
                    lastUsedAtMs = now,
                    cachedSystemPromptHash = promptHash,
                    isWarm = true
                )
                _activeLoadedModel.value = loadedState

                ModelLoadResult.Loaded(
                    modelId = entry.id,
                    config = config,
                    benchmark = benchmark,
                    reusedKvPrefix = reusedKv
                )
            }
        } catch (e: Exception) {
            unloadModelInternal()
            ModelLoadResult.EngineFailure(
                modelId = entry.id,
                reason = e.localizedMessage ?: "Native runtime initialization failed."
            )
        }
    }

    fun touchActiveModel(systemPromptPrefix: String = "") {
        val current = _activeLoadedModel.value ?: return
        val now = System.currentTimeMillis()
        if (now - current.lastUsedAtMs >= IDLE_UNLOAD_TIMEOUT_MS) {
            unloadModelInternal()
            return
        }
        val hash = if (systemPromptPrefix.isNotBlank()) systemPromptPrefix.trim().hashCode() else current.cachedSystemPromptHash
        _activeLoadedModel.value = current.copy(
            lastUsedAtMs = now,
            cachedSystemPromptHash = hash,
            isWarm = true
        )
    }

    fun unloadModel() {
        unloadModelInternal()
    }

    fun onAppBackgrounded() {
        unloadModelInternal()
    }

    fun onTrimMemory(level: Int) {
        EngineProvider.currentEngine.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            _activeLoadedModel.value = _activeLoadedModel.value?.copy(isWarm = false)
        }
    }

    private fun unloadModelInternal() {
        runCatching { EngineProvider.currentEngine.unload() }
        _activeLoadedModel.value = null
    }
}
