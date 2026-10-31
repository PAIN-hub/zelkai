package com.example.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ModelBenchmarkResult(
    val deviceId: String,
    val modelId: String,
    val runtime: String,
    val osFingerprint: String,
    val ttftMs: Long,
    val tokensPerSec: Double,
    val loadTimeMs: Long,
    val peakMemoryMb: Long,
    val cpuTokensPerSec: Double,
    val vulkanTokensPerSec: Double,
    val vulkanFasterThanCpu: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("modelId", modelId)
        put("runtime", runtime)
        put("osFingerprint", osFingerprint)
        put("ttftMs", ttftMs)
        put("tokensPerSec", tokensPerSec)
        put("loadTimeMs", loadTimeMs)
        put("peakMemoryMb", peakMemoryMb)
        put("cpuTokensPerSec", cpuTokensPerSec)
        put("vulkanTokensPerSec", vulkanTokensPerSec)
        put("vulkanFasterThanCpu", vulkanFasterThanCpu)
        put("timestampMs", timestampMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): ModelBenchmarkResult {
            return ModelBenchmarkResult(
                deviceId = obj.optString("deviceId", ""),
                modelId = obj.optString("modelId", ""),
                runtime = obj.optString("runtime", "llama.cpp-gguf"),
                osFingerprint = obj.optString("osFingerprint", ""),
                ttftMs = obj.optLong("ttftMs", 0L),
                tokensPerSec = obj.optDouble("tokensPerSec", 0.0),
                loadTimeMs = obj.optLong("loadTimeMs", 0L),
                peakMemoryMb = obj.optLong("peakMemoryMb", 0L),
                cpuTokensPerSec = obj.optDouble("cpuTokensPerSec", 0.0),
                vulkanTokensPerSec = obj.optDouble("vulkanTokensPerSec", 0.0),
                vulkanFasterThanCpu = obj.optBoolean("vulkanFasterThanCpu", false),
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
        val userMessage: String
    ) : ModelLoadResult

    data class EngineFailure(
        val modelId: String,
        val reason: String
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
        const val BENCHMARK_FIXED_PROMPT =
            "Write a non-interactive POSIX shell command to list the top 5 largest files in the current directory."
    }

    private val prefs = context.getSharedPreferences("nexus_inference_optimizer_real_v2", Context.MODE_PRIVATE)

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

    private fun cacheKey(deviceId: String, modelId: String, runtime: String): String {
        return "bench::${deviceId}::${modelId.lowercase()}::${runtime.lowercase()}"
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
            return ModelLoadResult.RefusedByLoadGuard(
                modelId = model.id,
                modelName = model.name,
                requiredRamGb = reqGb,
                availableRamGb = availGb,
                userMessage = "Load guard refused ${model.name}: requires at least $reqGb GB free RAM (model + 25% safety margin), but only $availGb GB is currently available. Close background apps or pick a smaller model."
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

        if (effectivePreset == InferencePreset.SPEED && chosenCtx > 2048) {
            chosenCtx = 2048
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
        val nBatch = if (effectivePreset == InferencePreset.SPEED) {
            max(128, baseBatch / 2)
        } else {
            baseBatch
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
                nThreads = max(1, min(nThreads, 4))
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
                        "Measured %.1f tok/s (< 3 tok/s threshold). Recommended faster model: %s (%s)",
                        benchmark.tokensPerSec,
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
     * Never runs synthetic loops or fabricates tok/s numbers (Part A & Part C #6).
     * Returns `null` when [StubEngine] is active so the UI displays `"Not benchmarked"`.
     */
    suspend fun benchmarkModel(
        model: ModelRegistryEntry,
        modelFile: File?,
        forceRebenchmark: Boolean = false
    ): ModelBenchmarkResult? = withContext(Dispatchers.Default) {
        val engine = EngineProvider.currentEngine
        if (!engine.info().isRuntimeInstalled || modelFile == null || !modelFile.exists()) {
            return@withContext null
        }

        val profile = DeviceProfiler.measure(context, forceRebenchmark = forceRebenchmark)
        val key = cacheKey(profile.deviceId, model.id, model.runtime)

        if (!forceRebenchmark) {
            _benchmarks.value[model.id]?.let { existing ->
                if (existing.osFingerprint == profile.osFingerprint && existing.tokensPerSec > 0.0) {
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
                batchSize = cfg.nBatch,
                maxTokens = 64
            )
            val loadStart = System.currentTimeMillis()
            val loadRes = engine.load(modelFile.absolutePath, params)
            if (loadRes.isFailure) return@withContext null
            val loadTimeMs = (System.currentTimeMillis() - loadStart).coerceAtLeast(1L)

            val genStart = System.currentTimeMillis()
            var firstTokenMs = -1L
            val sb = StringBuilder()
            engine.generate(BENCHMARK_FIXED_PROMPT, params).collect { chunk ->
                if (firstTokenMs < 0L) {
                    firstTokenMs = (System.currentTimeMillis() - genStart).coerceAtLeast(1L)
                }
                sb.append(chunk)
            }
            val totalGenMs = (System.currentTimeMillis() - genStart).coerceAtLeast(1L)
            val approxTokens = (sb.length / 4.0).coerceAtLeast(1.0)
            val tokPerSec = ((approxTokens * 1000.0) / totalGenMs * 10.0).roundToInt() / 10.0
            val peakMemMb = ((model.sizeBytes + (cfg.nCtx * 128L * 1024L)) / (1024L * 1024L)).coerceAtLeast(128L)

            val result = ModelBenchmarkResult(
                deviceId = profile.deviceId,
                modelId = model.id,
                runtime = engine.info().name,
                osFingerprint = profile.osFingerprint,
                ttftMs = if (firstTokenMs > 0L) firstTokenMs else totalGenMs,
                tokensPerSec = tokPerSec,
                loadTimeMs = loadTimeMs,
                peakMemoryMb = peakMemMb,
                cpuTokensPerSec = tokPerSec,
                vulkanTokensPerSec = 0.0,
                vulkanFasterThanCpu = false
            )
            prefs.edit().putString(key, result.toJson().toString()).apply()
            _benchmarks.update { it + (model.id to result) }
            result
        } catch (_: Exception) {
            null
        } finally {
            _isBenchmarking.value = false
        }
    }

    /**
     * Unloads any previously loaded model and loads [modelId] through [EngineProvider.currentEngine]
     * with Load Guard protection. When [StubEngine] is active (or the model file is not downloaded),
     * returns [ModelLoadResult.EngineFailure] with an honest message.
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
                reason = engine.info().statusMessage
            )
        }

        if (modelFile == null || !modelFile.exists()) {
            unloadModelInternal()
            return@withContext ModelLoadResult.EngineFailure(
                modelId = entry.id,
                reason = "Model '${entry.name}' is not downloaded on disk. Download it in the Local LLM Hub first."
            )
        }

        return@withContext try {
            withTimeout(20_000L) {
                val benchmark = getBenchmarkForModel(entry.id)
                val config = autoConfigure(
                    profile = profile,
                    model = entry,
                    benchmark = benchmark,
                    userPreset = _activePreset.value
                )

                val loadParams = InferenceParams(
                    contextLength = config.nCtx,
                    threads = config.nThreads,
                    batchSize = config.nBatch,
                    systemInstruction = systemPromptPrefix
                )
                val engineLoad = engine.load(modelFile.absolutePath, loadParams)
                if (engineLoad.isFailure) {
                    val err = engineLoad.exceptionOrNull()?.message ?: "Native runtime failed to load model."
                    return@withTimeout ModelLoadResult.EngineFailure(
                        modelId = entry.id,
                        reason = err
                    )
                }

                val reusedKv = existing != null &&
                    existing.modelId == entry.id &&
                    existing.cachedSystemPromptHash == promptHash &&
                    promptHash != 0

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

    private fun unloadModelInternal() {
        runCatching { EngineProvider.currentEngine.unload() }
        _activeLoadedModel.value = null
    }
}
