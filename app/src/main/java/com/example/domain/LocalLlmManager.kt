package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.util.NexusNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Thrown when a local model is requested while unloaded or missing on disk.
 * Surfaces as a visible "Model not loaded" error card with Retry in the transcript.
 */
class LocalModelNotLoadedException(
    val modelId: String,
    override val message: String = "Model not loaded ($modelId). Load or select a model in Local LLM Hub."
) : Exception(message)

/**
 * Thrown when local inference fails or when a native GGUF runtime is not installed.
 * Surfaces as a visible "Inference failed" error card with Retry in the transcript.
 */
class LocalModelInferenceException(
    val modelId: String,
    override val message: String = "Inference failed on local model ($modelId)."
) : Exception(message)

class LocalModelLoadGuardException(
    val guardFailure: ModelLoadResult.RefusedByLoadGuard
) : IllegalStateException(guardFailure.userMessage)

data class LocalLlmSpec(
    val id: String,
    val name: String,
    val family: String,
    val parameterCount: String,
    val quantization: String,
    val downloadSizeMb: Long,
    val minRamGb: Double,
    val recommendedRamGb: Double,
    val minFreeStorageGb: Double,
    val requires64Bit: Boolean = true,
    val specialty: String,
    val huggingFaceRepo: String,
    val downloadUrl: String = "",
    val sha256: String = "",
    val sizeBytes: Long = downloadSizeMb * 1024L * 1024L,
    val ramRequiredBytes: Long = (minRamGb * 1024.0 * 1024.0 * 1024.0).toLong(),
    val contextDefault: Int = 4096,
    val runtime: String = "llama.cpp-gguf",
    val licenseUrl: String = "https://huggingface.co",
    val paramsBillions: Double = 1.5,
    val qualityScore: Int = 70,
    val minApiLevel: Int = 24
) {
    fun toRegistryEntry(): ModelRegistryEntry {
        return ModelRegistryEntry(
            id = id,
            name = name,
            family = family,
            params = parameterCount,
            paramsBillions = paramsBillions,
            quantization = quantization,
            format = "GGUF",
            downloadUrl = downloadUrl.ifBlank { "https://huggingface.co/$huggingFaceRepo" },
            sha256 = sha256,
            sizeBytes = sizeBytes,
            ramRequiredBytes = ramRequiredBytes,
            contextDefault = contextDefault,
            runtime = runtime,
            licenseUrl = licenseUrl,
            huggingFaceRepo = huggingFaceRepo,
            specialty = specialty,
            qualityScore = qualityScore,
            minApiLevel = minApiLevel
        )
    }

    private fun ModelRegistryEntry(
        id: String,
        name: String,
        family: String,
        params: String,
        paramsBillions: Double,
        quantization: String,
        format: String,
        downloadUrl: String,
        sha256: String,
        sizeBytes: Long,
        ramRequiredBytes: Long,
        contextDefault: Int,
        runtime: String,
        licenseUrl: String,
        huggingFaceRepo: String,
        specialty: String,
        qualityScore: Int,
        minApiLevel: Int
    ): com.example.domain.ModelRegistryEntry = com.example.domain.ModelRegistryEntry(
        id = id,
        name = name,
        family = family,
        params = params,
        paramsBillions = paramsBillions,
        quant = quantization,
        format = format,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        ramRequiredBytes = ramRequiredBytes,
        contextDefault = contextDefault,
        runtime = runtime,
        licenseUrl = licenseUrl,
        huggingFaceRepo = huggingFaceRepo,
        specialty = specialty,
        qualityScore = qualityScore,
        minApiLevel = minApiLevel
    )
}

enum class CapabilityVerdict {
    CAPABLE,
    MARGINAL,
    NOT_CAPABLE
}

data class DeviceHardwareTelemetry(
    val totalRamGb: Double,
    val availableRamGb: Double,
    val freeStorageGb: Double,
    val cpuCores: Int,
    val primaryAbi: String,
    val is64Bit: Boolean,
    val deviceName: String
)

data class ModelCapabilityCheckResult(
    val model: LocalLlmSpec,
    val telemetry: DeviceHardwareTelemetry,
    val verdict: CapabilityVerdict,
    val ramPassed: Boolean,
    val storagePassed: Boolean,
    val archPassed: Boolean,
    val speedStatusLabel: String,
    val findings: List<String>,
    val summaryRecommendation: String
)

data class LocalModelDownloadState(
    val modelId: String,
    val progressFraction: Float,
    val downloadedMb: Long,
    val totalMb: Long,
    val stageLabel: String,
    val isDownloading: Boolean,
    val isCompleted: Boolean,
    val isPaused: Boolean = false,
    val isWaitingForNetwork: Boolean = false,
    val speedMbps: String = "",
    val errorMessage: String? = null,
    val downloadedBytes: Long = downloadedMb * 1024L * 1024L,
    val totalBytes: Long = totalMb * 1024L * 1024L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val status: DownloadStatus = when {
        isCompleted -> DownloadStatus.READY
        errorMessage != null -> DownloadStatus.FAILED
        isPaused || isWaitingForNetwork -> DownloadStatus.PAUSED
        isDownloading -> DownloadStatus.DOWNLOADING
        else -> DownloadStatus.QUEUED
    },
    val etag: String = "",
    val integrityUnverified: Boolean = false
) {
    val etaLabel: String
        get() = when {
            etaSeconds <= 0L -> ""
            etaSeconds < 60L -> "${etaSeconds}s left"
            else -> "${etaSeconds / 60L}m ${etaSeconds % 60L}s left"
        }
}

data class QuickCommandTranslation(
    val summaryLabel: String,
    val shellCommand: String
)

class LocalLlmManager(private val context: Context) {

    companion object {
        const val MOBILE_DATA_WARN_THRESHOLD_BYTES = 500L * 1024L * 1024L // 500 MB
        const val HEAD_CACHE_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours

        @Volatile
        private var singletonInstance: LocalLlmManager? = null

        fun getInstance(context: Context): LocalLlmManager {
            return singletonInstance ?: synchronized(this) {
                singletonInstance ?: LocalLlmManager(context.applicationContext).also {
                    singletonInstance = it
                }
            }
        }

        val LOCAL_LLM_CATALOG: List<LocalLlmSpec>
            get() = ModelRegistry.loadModels().map { it.toLocalLlmSpec() }

        fun findSpec(modelId: String): LocalLlmSpec? {
            return ModelRegistry.findById(modelId)?.toLocalLlmSpec()
                ?: LOCAL_LLM_CATALOG.find {
                    it.id.equals(modelId, ignoreCase = true) ||
                        it.name.equals(modelId, ignoreCase = true) ||
                        modelId.lowercase().contains(it.id.lowercase())
                }
        }
    }

    private val prefs = context.getSharedPreferences("nexus_local_llms_v2", Context.MODE_PRIVATE)
    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }
    private val legacyModelsDir: File = File(context.filesDir, "local_llms")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationHelper = NexusNotificationHelper(context)
    private val userApiKeyStore = UserApiKeyStore(context)

    private var downloadHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val inferenceOptimizer = InferenceOptimizer(context)

    @Volatile
    var isModelExplicitlyUnloaded: Boolean = false
        private set

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val completionCallbacks = ConcurrentHashMap<String, (LocalLlmSpec) -> Unit>()

    private val _downloadedModels = MutableStateFlow(scanDownloadedModelsFromDisk())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, LocalModelDownloadState>> = _downloadStates.asStateFlow()

    private val _isNetworkOnline = MutableStateFlow(checkCurrentNetworkOnline())
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean("wifi_only_downloads", false))
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    private val _headProbes = MutableStateFlow<Map<String, HeadProbeMetadata>>(loadCachedHeadProbes())
    val headProbes: StateFlow<Map<String, HeadProbeMetadata>> = _headProbes.asStateFlow()

    private val _unavailableCatalogReasons = MutableStateFlow<Map<String, String>>(emptyMap())
    val unavailableCatalogReasons: StateFlow<Map<String, String>> = _unavailableCatalogReasons.asStateFlow()

    private val _deviceProfile = MutableStateFlow(DeviceProfiler.measure(context))
    val deviceProfile: StateFlow<DeviceProfile> = _deviceProfile.asStateFlow()

    private val _recommendationReport = MutableStateFlow(
        RecommendationEngine.evaluateCatalog(
            deviceProfile = _deviceProfile.value,
            catalog = ModelRegistry.loadModels(context),
            preset = inferenceOptimizer.activePreset.value,
            measuredBenchmarks = inferenceOptimizer.benchmarks.value,
            downloadedModelIds = _downloadedModels.value
        )
    )
    val recommendationReport: StateFlow<DeviceRecommendationReport> = _recommendationReport.asStateFlow()

    val benchmarks: StateFlow<Map<String, ModelBenchmarkResult>> = inferenceOptimizer.benchmarks
    val activeLoadedModel: StateFlow<ActiveLoadedModelState?> = inferenceOptimizer.activeLoadedModel
    val activePreset: StateFlow<InferencePreset> = inferenceOptimizer.activePreset
    val isBenchmarking: StateFlow<Boolean> = inferenceOptimizer.isBenchmarking
    val lastLoadGuardError: StateFlow<ModelLoadResult.RefusedByLoadGuard?> = inferenceOptimizer.lastLoadGuardError
    val crashRecoveryNotice: StateFlow<CrashRecoveryNotice?> = inferenceOptimizer.crashRecoveryNotice
    val unstableModelIds: StateFlow<Set<String>> = inferenceOptimizer.unstableModelIds

    private val _showModelsAboveRecommendedSize = MutableStateFlow(
        prefs.getBoolean("show_models_above_recommended_size", false)
    )
    val showModelsAboveRecommendedSize: StateFlow<Boolean> = _showModelsAboveRecommendedSize.asStateFlow()

    private val _engineInfo = MutableStateFlow(EngineProvider.engineInfo())
    val engineInfo: StateFlow<EngineInfo> = _engineInfo.asStateFlow()

    init {
        singletonInstance = this
        ModelDownloadService.managerRef = this
        ModelRegistry.loadModels(context)
        if (EngineProvider.currentEngine is StubEngine) {
            EngineProvider.initialize(context)
        }
        _engineInfo.value = EngineProvider.engineInfo()
        restorePersistedCheckpoints()
        registerConnectivityAutoResume()
        scope.launch {
            EngineProvider.engineFlow.collect { eng ->
                _engineInfo.value = eng.info()
            }
        }
        scope.launch {
            inferenceOptimizer.benchmarks.collect {
                refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
            }
        }
        scope.launch {
            refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
        }
    }

    fun setDownloadHttpClientForTesting(client: OkHttpClient) {
        this.downloadHttpClient = client
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        _wifiOnlyDownloads.value = enabled
        prefs.edit().putBoolean("wifi_only_downloads", enabled).apply()
    }

    fun isCurrentConnectionMeteredOrCellular(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isNotMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            isCellular || !isNotMetered
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clarified user preference: warn and ask whether to wait for Wi-Fi or continue on mobile data
     * when downloading a model over 500 MB on cellular/metered data.
     */
    fun shouldWarnMobileDataForSpec(spec: LocalLlmSpec): Boolean {
        return spec.sizeBytes > MOBILE_DATA_WARN_THRESHOLD_BYTES && isCurrentConnectionMeteredOrCellular()
    }

    fun requiresMobileDataConfirmation(spec: LocalLlmSpec): Boolean = shouldWarnMobileDataForSpec(spec)

    fun hasSufficientStorageForSpec(spec: LocalLlmSpec): Boolean {
        val profile = DeviceProfiler.measure(context)
        _deviceProfile.value = profile
        val requiredBytes = (spec.sizeBytes * 1.10).toLong()
        return profile.freeStorageBytes >= requiredBytes
    }

    fun getModelFileOnDisk(modelId: String): File? {
        val primary = File(modelsDir, "$modelId.gguf")
        if (primary.exists() && primary.length() > 0L) return primary
        val legacy = File(legacyModelsDir, "$modelId.gguf")
        if (legacy.exists() && legacy.length() > 0L) return legacy
        return null
    }

    fun getDownloadedModelSizeBytes(modelId: String): Long {
        return getModelFileOnDisk(modelId)?.length() ?: 0L
    }

    fun getDownloadedFileSizeBytes(modelId: String): Long = getDownloadedModelSizeBytes(modelId)

    fun getTotalModelsDiskUsageBytes(): Long {
        val primarySum = modelsDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        val legacySum = if (legacyModelsDir.exists()) {
            legacyModelsDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } else 0L
        return primarySum + legacySum
    }

    fun getTotalDownloadedBytes(): Long = getTotalModelsDiskUsageBytes()

    /**
     * Amendment #3: Runtime HEAD probe for catalog URLs, cached 24h in preferences.
     */
    suspend fun verifyCatalogUrlsHead(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hfToken = userApiKeyStore.getHuggingFaceToken()
        val currentProbes = _headProbes.value.toMutableMap()
        val unavailable = _unavailableCatalogReasons.value.toMutableMap()

        for (spec in LOCAL_LLM_CATALOG) {
            val cached = currentProbes[spec.id]
            if (!forceRefresh && cached != null && (now - cached.timestampMs) < HEAD_CACHE_TTL_MS) {
                continue
            }
            val url = spec.downloadUrl
            if (url.isBlank()) continue
            runCatching {
                val meta = ResumableGgufDownloader.probeCanonicalHeaders(
                    canonicalUrl = url,
                    hfToken = hfToken,
                    baseClient = downloadHttpClient
                )
                currentProbes[spec.id] = meta
                saveHeadProbeToCache(spec.id, meta)
                if (meta.statusCode in 200..399) {
                    unavailable.remove(spec.id)
                } else if (meta.statusCode == 401 || meta.statusCode == 403) {
                    unavailable[spec.id] = "Gated repository (HTTP ${meta.statusCode}) — add Hugging Face token in Settings"
                } else if (meta.statusCode >= 400) {
                    unavailable[spec.id] = "Unavailable (HTTP ${meta.statusCode})"
                }
            }
        }
        _headProbes.value = currentProbes
        _unavailableCatalogReasons.value = unavailable
    }

    private fun loadCachedHeadProbes(): Map<String, HeadProbeMetadata> {
        val now = System.currentTimeMillis()
        val map = mutableMapOf<String, HeadProbeMetadata>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("head_probe_") && v is String) {
                runCatching {
                    val obj = JSONObject(v)
                    val ts = obj.optLong("timestampMs", 0L)
                    if (now - ts < HEAD_CACHE_TTL_MS) {
                        val id = k.removePrefix("head_probe_")
                        map[id] = HeadProbeMetadata(
                            statusCode = obj.optInt("statusCode", 200),
                            contentLength = obj.optLong("contentLength", -1L),
                            etag = obj.optString("etag", ""),
                            linkedSha256 = obj.optString("linkedSha256", ""),
                            resolvedUrl = obj.optString("resolvedUrl", ""),
                            timestampMs = ts
                        )
                    }
                }
            }
        }
        return map
    }

    private fun saveHeadProbeToCache(modelId: String, meta: HeadProbeMetadata) {
        val obj = JSONObject().apply {
            put("statusCode", meta.statusCode)
            put("contentLength", meta.contentLength)
            put("etag", meta.etag)
            put("linkedSha256", meta.linkedSha256)
            put("resolvedUrl", meta.resolvedUrl)
            put("timestampMs", meta.timestampMs)
        }
        prefs.edit().putString("head_probe_$modelId", obj.toString()).apply()
    }

    fun refreshDeviceProfileAndRecommendations(forceRebenchmark: Boolean = false): DeviceRecommendationReport {
        val measured = DeviceProfiler.measure(context, forceRebenchmark = forceRebenchmark)
        _deviceProfile.value = measured
        _downloadedModels.value = scanDownloadedModelsFromDisk()
        val report = RecommendationEngine.evaluateCatalog(
            deviceProfile = measured,
            catalog = ModelRegistry.loadModels(context),
            preset = inferenceOptimizer.activePreset.value,
            measuredBenchmarks = inferenceOptimizer.benchmarks.value,
            downloadedModelIds = _downloadedModels.value,
            allowAboveRecommendedBudget = _showModelsAboveRecommendedSize.value,
            unstableModelIds = inferenceOptimizer.unstableModelIds.value
        )
        _recommendationReport.value = report
        return report
    }

    fun setShowModelsAboveRecommendedSize(enabled: Boolean) {
        _showModelsAboveRecommendedSize.value = enabled
        prefs.edit().putBoolean("show_models_above_recommended_size", enabled).apply()
        refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
    }

    fun dismissCrashRecoveryNotice() {
        inferenceOptimizer.dismissCrashRecoveryNotice()
    }

    fun setInferencePreset(preset: InferencePreset) {
        inferenceOptimizer.setInferencePreset(preset)
        refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
    }

    suspend fun reRunBenchmarkForModel(modelId: String): ModelBenchmarkResult? {
        val entry = ModelRegistry.findById(modelId, context) ?: return null
        val file = getModelFileOnDisk(entry.id)
        val result = inferenceOptimizer.benchmarkModel(entry, file, forceRebenchmark = true)
        if (_activeLoadedModelMatches(entry.id)) {
            inferenceOptimizer.loadAndPrepareModel(entry.id, modelFile = file)
        }
        refreshDeviceProfileAndRecommendations(forceRebenchmark = true)
        return result
    }

    private fun _activeLoadedModelMatches(modelId: String): Boolean {
        return inferenceOptimizer.activeLoadedModel.value?.modelId.equals(modelId, ignoreCase = true)
    }

    suspend fun switchActiveLocalModel(
        modelId: String,
        systemPromptPrefix: String = "",
        forceLoadDespiteWarning: Boolean = false
    ): ModelLoadResult {
        isModelExplicitlyUnloaded = false
        val file = getModelFileOnDisk(modelId)
        val res = inferenceOptimizer.loadAndPrepareModel(
            modelId = modelId,
            systemPromptPrefix = systemPromptPrefix,
            modelFile = file,
            forceLoadDespiteWarning = forceLoadDespiteWarning
        )
        refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
        return res
    }

    fun unloadActiveLocalModel() {
        isModelExplicitlyUnloaded = true
        inferenceOptimizer.unloadModel()
    }

    fun onAppBackgrounded() {
        inferenceOptimizer.onAppBackgrounded()
    }

    fun onTrimMemory(level: Int) {
        inferenceOptimizer.onTrimMemory(level)
    }

    fun stopLocalGeneration() {
        runCatching { EngineProvider.currentEngine.stop() }
    }

    private fun checkCurrentNetworkOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerConnectivityAutoResume() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkOnline.value = true
                    autoResumeDownloadsWaitingForNetwork()
                }

                override fun onLost(network: Network) {
                    val stillOnline = checkCurrentNetworkOnline()
                    _isNetworkOnline.value = stillOnline
                    if (!stillOnline) {
                        transitionActiveDownloadsToWaitingForNetwork()
                    }
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun transitionActiveDownloadsToWaitingForNetwork() {
        val currentMap = _downloadStates.value
        currentMap.values.filter { it.isDownloading }.forEach { activeState ->
            activeDownloadJobs[activeState.modelId]?.cancel()
            activeDownloadJobs.remove(activeState.modelId)
            val waitingState = activeState.copy(
                isDownloading = false,
                isPaused = false,
                isWaitingForNetwork = true,
                status = DownloadStatus.PAUSED,
                speedMbps = "0.0 MB/s",
                stageLabel = "Connection lost at ${activeState.downloadedMb} MB · Auto-resumes when online…"
            )
            _downloadStates.update { it + (activeState.modelId to waitingState) }
            persistCheckpoint(waitingState)
        }
    }

    private fun autoResumeDownloadsWaitingForNetwork() {
        if (_wifiOnlyDownloads.value && isCurrentConnectionMeteredOrCellular()) return
        val waitingList = _downloadStates.value.values.filter { it.isWaitingForNetwork }
        waitingList.forEach { state ->
            val spec = findSpec(state.modelId) ?: return@forEach
            val cb = completionCallbacks[state.modelId] ?: {}
            resumeDownload(spec, onComplete = cb)
        }
    }

    fun scanDownloadedModelsFromDisk(): Set<String> {
        val found = mutableSetOf<String>()
        LOCAL_LLM_CATALOG.forEach { spec ->
            val primary = File(modelsDir, "${spec.id}.gguf")
            val legacy = File(legacyModelsDir, "${spec.id}.gguf")
            if ((primary.exists() && primary.length() > 0L) || (legacy.exists() && legacy.length() > 0L)) {
                found.add(spec.id)
            }
        }
        return found
    }

    private fun persistCheckpoint(state: LocalModelDownloadState) {
        val key = "checkpoint_${state.modelId}"
        if (state.isCompleted) {
            prefs.edit().remove(key).apply()
            return
        }
        val json = JSONObject().apply {
            put("modelId", state.modelId)
            put("progressFraction", state.progressFraction.toDouble())
            put("downloadedMb", state.downloadedMb)
            put("totalMb", state.totalMb)
            put("downloadedBytes", state.downloadedBytes)
            put("totalBytes", state.totalBytes)
            put("stageLabel", state.stageLabel)
            put("isPaused", state.isPaused)
            put("isWaitingForNetwork", state.isWaitingForNetwork)
            put("etag", state.etag)
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    private fun removeCheckpoint(modelId: String) {
        prefs.edit().remove("checkpoint_$modelId").apply()
    }

    private fun restorePersistedCheckpoints() {
        val restored = mutableMapOf<String, LocalModelDownloadState>()
        val diskDownloaded = scanDownloadedModelsFromDisk()
        _downloadedModels.value = diskDownloaded

        LOCAL_LLM_CATALOG.forEach { spec ->
            val partFile = File(modelsDir, "${spec.id}.gguf.part")
            val raw = prefs.getString("checkpoint_${spec.id}", null)
            if (partFile.exists() && partFile.length() > 0L && !diskDownloaded.contains(spec.id)) {
                val obj = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
                val dlBytes = partFile.length()
                val dlMb = dlBytes / (1024L * 1024L)
                val totalBytes = obj?.optLong("totalBytes", spec.sizeBytes)?.coerceAtLeast(dlBytes + 1L) ?: spec.sizeBytes
                val totalMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                val fraction = (dlBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                val waitingNet = obj?.optBoolean("isWaitingForNetwork", false) ?: false
                val savedEtag = obj?.optString("etag", "").orEmpty()
                restored[spec.id] = LocalModelDownloadState(
                    modelId = spec.id,
                    progressFraction = fraction,
                    downloadedMb = dlMb,
                    totalMb = totalMb,
                    downloadedBytes = dlBytes,
                    totalBytes = totalBytes,
                    status = DownloadStatus.PAUSED,
                    stageLabel = if (waitingNet) {
                        "Waiting for network to auto-resume ($dlMb / $totalMb MB)…"
                    } else {
                        "Paused at $dlMb / $totalMb MB · Tap Resume"
                    },
                    isDownloading = false,
                    isCompleted = false,
                    isPaused = !waitingNet,
                    isWaitingForNetwork = waitingNet,
                    speedMbps = "0.0 MB/s",
                    etag = savedEtag
                )
            }
        }
        if (restored.isNotEmpty()) {
            _downloadStates.value = restored
        }
    }

    fun inspectDeviceHardware(): DeviceHardwareTelemetry {
        val profile = DeviceProfiler.measure(context)
        _deviceProfile.value = profile
        return profile.toLegacyTelemetry()
    }

    fun checkDeviceCapabilityForModel(spec: LocalLlmSpec): ModelCapabilityCheckResult {
        val profile = DeviceProfiler.measure(context)
        _deviceProfile.value = profile
        val telemetry = profile.toLegacyTelemetry()
        val entry = spec.toRegistryEntry()
        val scored = RecommendationEngine.scoreSingleModel(
            deviceProfile = profile,
            model = entry,
            preset = inferenceOptimizer.activePreset.value,
            benchmark = inferenceOptimizer.getBenchmarkForModel(spec.id),
            isAlreadyDownloaded = _downloadedModels.value.contains(spec.id)
        )

        val archPassed = !spec.requires64Bit || profile.is64Bit
        val requiredStorageBytes = (spec.sizeBytes * 1.10).toLong()
        val storagePassed = profile.freeStorageBytes >= requiredStorageBytes
        val apiPassed = profile.apiLevel >= entry.minApiLevel
        val footprintBytes = RamPolicy.estimateModelFootprintBytes(spec.sizeBytes)
        val footprintGb = footprintBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val ramCapable = footprintBytes <= profile.ramBudgetBytes
        val ramMarginal = !ramCapable && footprintBytes <= profile.ramHardCapBytes

        val findings = mutableListOf<String>()

        findings.add(
            String.format(
                Locale.US,
                "SoC & RAM Class: %s · %s · %s",
                profile.socSummary,
                profile.coreSummary,
                profile.effectiveRamClass.label
            )
        )

        if (archPassed && apiPassed) {
            findings.add("CPU & OS: ${profile.primaryAbi} (64-bit GGUF & q8_0 KV cache supported)")
        } else if (!archPassed) {
            findings.add("CPU ABI: ${profile.primaryAbi} — 32-bit mode cannot mmap 64-bit GGUF tensors")
        } else {
            findings.add("Android API ${profile.apiLevel} is below minimum API ${entry.minApiLevel}")
        }

        when {
            ramCapable -> findings.add(
                String.format(
                    Locale.US,
                    "Memory Fit: %.2f GB footprint fits within %.1f GB recommended budget on %.1f GB device",
                    footprintGb,
                    profile.ramBudgetGb,
                    profile.totalRamGb
                )
            )
            ramMarginal -> findings.add(
                String.format(
                    Locale.US,
                    "Above Recommended Budget: %.2f GB footprint (recommended budget %.1f GB, hard cap %.1f GB on %.1f GB device)",
                    footprintGb,
                    profile.ramBudgetGb,
                    profile.ramHardCapGb,
                    profile.totalRamGb
                )
            )
            else -> findings.add(
                String.format(
                    Locale.US,
                    "Exceeds Hard Cap: %.2f GB footprint exceeds %.1f GB hard cap on %.1f GB device",
                    footprintGb,
                    profile.ramHardCapGb,
                    profile.totalRamGb
                )
            )
        }

        if (storagePassed) {
            findings.add(
                String.format(
                    Locale.US,
                    "Storage: %.1f GB free (requires %.2f GB incl. 10%% headroom for %d MB GGUF)",
                    profile.freeStorageGb,
                    spec.minFreeStorageGb,
                    spec.downloadSizeMb
                )
            )
        } else {
            findings.add(
                String.format(
                    Locale.US,
                    "Low Storage: needs %.1f GB free storage (size + 10%%), you have %.1f GB",
                    spec.minFreeStorageGb,
                    profile.freeStorageGb
                )
            )
        }

        val verdict = when {
            !archPassed || !storagePassed || !apiPassed || (!ramCapable && !ramMarginal) -> CapabilityVerdict.NOT_CAPABLE
            ramCapable -> CapabilityVerdict.CAPABLE
            else -> CapabilityVerdict.MARGINAL
        }

        val optConfig = inferenceOptimizer.autoConfigure(profile, entry, inferenceOptimizer.getBenchmarkForModel(spec.id))

        val summary = when (verdict) {
            CapabilityVerdict.CAPABLE ->
                "Verified compatible (Score ${scored.totalScore}/100). Auto-configured: n_ctx=${optConfig.nCtx}, n_threads=${optConfig.nThreads} (big+prime cores), KV=q8_0, mmap=true."
            CapabilityVerdict.MARGINAL ->
                "Marginal RAM headroom. InferenceOptimizer will clamp n_ctx=1024 and n_batch=128 to prevent low-memory kills."
            CapabilityVerdict.NOT_CAPABLE ->
                "Not recommended: ${scored.rejectionReason ?: "Hardware below minimum requirements"}."
        }

        return ModelCapabilityCheckResult(
            model = spec,
            telemetry = telemetry,
            verdict = verdict,
            ramPassed = ramCapable || ramMarginal,
            storagePassed = storagePassed,
            archPassed = archPassed && apiPassed,
            speedStatusLabel = scored.speedDisplayLabel,
            findings = findings,
            summaryRecommendation = summary
        )
    }

    fun startModelDownload(
        spec: LocalLlmSpec,
        allowMobileDataForLargeModel: Boolean = false,
        onComplete: (LocalLlmSpec) -> Unit = {}
    ) {
        if (!hasSufficientStorageForSpec(spec)) {
            val neededGb = String.format(Locale.US, "%.2f", (spec.sizeBytes * 1.10) / (1024.0 * 1024.0 * 1024.0))
            val failedState = LocalModelDownloadState(
                modelId = spec.id,
                progressFraction = 0f,
                downloadedMb = 0L,
                totalMb = spec.downloadSizeMb,
                status = DownloadStatus.FAILED,
                stageLabel = "Insufficient free storage (needs $neededGb GB incl. 10% safety headroom).",
                isDownloading = false,
                isCompleted = false,
                errorMessage = "Insufficient free storage (needs $neededGb GB incl. 10% safety headroom)."
            )
            _downloadStates.update { it + (spec.id to failedState) }
            return
        }

        if (_wifiOnlyDownloads.value && !allowMobileDataForLargeModel && isCurrentConnectionMeteredOrCellular()) {
            val waitingWifiState = LocalModelDownloadState(
                modelId = spec.id,
                progressFraction = _downloadStates.value[spec.id]?.progressFraction ?: 0f,
                downloadedMb = _downloadStates.value[spec.id]?.downloadedMb ?: 0L,
                totalMb = spec.downloadSizeMb,
                status = DownloadStatus.PAUSED,
                stageLabel = "Wi-Fi Only enabled · Waiting for unmetered Wi-Fi connection…",
                isDownloading = false,
                isCompleted = false,
                isPaused = false,
                isWaitingForNetwork = true
            )
            _downloadStates.update { it + (spec.id to waitingWifiState) }
            persistCheckpoint(waitingWifiState)
            ModelDownloadWorker.enqueueResumeWork(context, spec.id, wifiOnly = true)
            return
        }

        completionCallbacks[spec.id] = onComplete
        startForegroundDownloadServiceOrDirect(spec)
    }

    private fun startForegroundDownloadServiceOrDirect(spec: LocalLlmSpec) {
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_OR_RESUME
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, spec.id)
        }
        val startedService = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.getOrDefault(false)

        if (!startedService || isRunningInUnitTest()) {
            if (activeDownloadJobs[spec.id]?.isActive == true) return
            val job = scope.launch {
                executeRealDownloadInternal(spec)
            }
            activeDownloadJobs[spec.id] = job
        }
    }

    private fun isRunningInUnitTest(): Boolean {
        return Build.FINGERPRINT == null ||
            Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    suspend fun executeRealDownloadInternal(spec: LocalLlmSpec): Boolean {
        val existingState = _downloadStates.value[spec.id]
        val savedEtag = existingState?.etag.orEmpty()
        val hfToken = userApiKeyStore.getHuggingFaceToken()

        val queuedState = LocalModelDownloadState(
            modelId = spec.id,
            progressFraction = existingState?.progressFraction ?: 0f,
            downloadedMb = existingState?.downloadedMb ?: 0L,
            totalMb = spec.downloadSizeMb,
            status = DownloadStatus.DOWNLOADING,
            stageLabel = "Connecting to ${spec.huggingFaceRepo}…",
            isDownloading = true,
            isCompleted = false,
            etag = savedEtag
        )
        _downloadStates.update { it + (spec.id to queuedState) }

        val result = ResumableGgufDownloader.downloadModelResumable(
            modelId = spec.id,
            canonicalUrl = spec.downloadUrl,
            modelsDir = modelsDir,
            expectedManifestSha256 = spec.sha256,
            expectedManifestSizeBytes = spec.sizeBytes,
            hfToken = hfToken,
            storedEtag = savedEtag,
            baseClient = downloadHttpClient
        ) { progressState, latestEtag ->
            val updated = progressState.copy(etag = latestEtag)
            _downloadStates.update { it + (spec.id to updated) }
            persistCheckpoint(updated)
            notificationHelper.notifyDownloadProgress(
                modelId = spec.id,
                modelName = spec.name,
                downloadedMb = updated.downloadedMb,
                totalMb = updated.totalMb,
                progressPercent = (updated.progressFraction * 100).toInt(),
                speedMbps = updated.speedMbps,
                isPaused = updated.isPaused,
                isWaitingForNetwork = updated.isWaitingForNetwork
            )
        }

        return result.fold(
            onSuccess = {
                _downloadedModels.value = scanDownloadedModelsFromDisk()
                removeCheckpoint(spec.id)
                refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
                notificationHelper.notifyDownloadFinished(
                    modelId = spec.id,
                    modelName = spec.name,
                    quantization = spec.quantization,
                    totalMb = spec.downloadSizeMb
                )
                completionCallbacks[spec.id]?.invoke(spec)
                true
            },
            onFailure = { err ->
                if (err is CancellationException) throw err
                val online = checkCurrentNetworkOnline()
                _isNetworkOnline.value = online
                if (!online) {
                    onServicePausedForNetworkOrTimeout(
                        spec.id,
                        "Connection lost · Auto-resumes when network returns…"
                    )
                    ModelDownloadWorker.enqueueResumeWork(context, spec.id, _wifiOnlyDownloads.value)
                } else {
                    val current = _downloadStates.value[spec.id]
                    val failedState = LocalModelDownloadState(
                        modelId = spec.id,
                        progressFraction = current?.progressFraction ?: 0f,
                        downloadedMb = current?.downloadedMb ?: 0L,
                        totalMb = spec.downloadSizeMb,
                        status = DownloadStatus.FAILED,
                        stageLabel = err.message ?: "Download failed.",
                        isDownloading = false,
                        isCompleted = false,
                        errorMessage = err.message ?: "Download failed."
                    )
                    _downloadStates.update { it + (spec.id to failedState) }
                }
                false
            }
        )
    }

    fun pauseDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        runCatching {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ModelDownloadService.ACTION_PAUSE
                putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
        onServicePausedDownload(modelId)
    }

    internal fun onServicePausedDownload(modelId: String) {
        val current = _downloadStates.value[modelId] ?: return
        val pausedState = current.copy(
            isDownloading = false,
            isPaused = true,
            isWaitingForNetwork = false,
            status = DownloadStatus.PAUSED,
            speedMbps = "0.0 MB/s",
            stageLabel = "Paused at ${current.downloadedMb} / ${current.totalMb} MB · Tap Resume"
        )
        _downloadStates.update { it + (modelId to pausedState) }
        persistCheckpoint(pausedState)
        val specName = findSpec(modelId)?.name ?: modelId
        notificationHelper.notifyDownloadProgress(
            modelId = modelId,
            modelName = specName,
            downloadedMb = pausedState.downloadedMb,
            totalMb = pausedState.totalMb,
            progressPercent = (pausedState.progressFraction * 100).toInt(),
            speedMbps = pausedState.speedMbps,
            isPaused = true,
            isWaitingForNetwork = false
        )
    }

    internal fun onServicePausedForNetworkOrTimeout(modelId: String, reason: String) {
        val current = _downloadStates.value[modelId] ?: return
        val waitingState = current.copy(
            isDownloading = false,
            isPaused = false,
            isWaitingForNetwork = true,
            status = DownloadStatus.PAUSED,
            speedMbps = "0.0 MB/s",
            stageLabel = reason
        )
        _downloadStates.update { it + (modelId to waitingState) }
        persistCheckpoint(waitingState)
    }

    fun resumeDownload(spec: LocalLlmSpec, onComplete: (LocalLlmSpec) -> Unit = {}) {
        completionCallbacks[spec.id] = onComplete
        startForegroundDownloadServiceOrDirect(spec)
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        runCatching {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ModelDownloadService.ACTION_CANCEL
                putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
        onServiceCancelledDownload(modelId)
    }

    internal fun onServiceCancelledDownload(modelId: String) {
        completionCallbacks.remove(modelId)
        File(modelsDir, "$modelId.gguf.part").delete()
        File(legacyModelsDir, "$modelId.gguf.part").delete()
        removeCheckpoint(modelId)
        _downloadStates.update { it - modelId }
        notificationHelper.cancelDownloadNotification(modelId)
    }

    fun deleteDownloadedModel(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        if (_activeLoadedModelMatches(modelId)) {
            unloadActiveLocalModel()
        }
        File(modelsDir, "$modelId.gguf").delete()
        File(modelsDir, "$modelId.gguf.part").delete()
        File(legacyModelsDir, "$modelId.gguf").delete()
        File(legacyModelsDir, "$modelId.gguf.part").delete()
        removeCheckpoint(modelId)
        _downloadedModels.value = scanDownloadedModelsFromDisk()
        _downloadStates.update { it - modelId }
        refreshDeviceProfileAndRecommendations(forceRebenchmark = false)
    }

    /**
     * Streams tokens from [EngineProvider.currentEngine] on Dispatchers.Default (Part C #4–5):
     * - Never returns static filler text or rule-based shortcuts posing as model output.
     * - When [StubEngine] is active (`!engine.info().isRuntimeInstalled`), throws [LocalModelInferenceException]
     *   with `"Runtime not installed: models can be downloaded but not run yet"`.
     * - When a real native [LocalInferenceEngine] is installed, loads the `.gguf` file (enforcing Load Guard)
     *   and streams tokens directly from `engine.generate(prompt, params)`.
     */
    fun streamLocalInference(
        modelId: String,
        userPrompt: String,
        currentCwd: String,
        systemPromptPrefix: String = "",
        recentCommandOutputs: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val trimmedId = modelId.trim()
        if (trimmedId.isEmpty() || isModelExplicitlyUnloaded) {
            throw LocalModelNotLoadedException(
                modelId = trimmedId.ifEmpty { "none" },
                message = "Model not loaded: ${trimmedId.ifEmpty { "no active local model" }}. Select or load a model in the Local LLM Hub."
            )
        }

        val spec = findSpec(trimmedId)
            ?: throw LocalModelNotLoadedException(
                modelId = trimmedId,
                message = "Model not loaded: '$trimmedId' is not registered in the local GGUF catalog."
            )

        val engine = EngineProvider.currentEngine
        if (!engine.info().isRuntimeInstalled) {
            throw LocalModelInferenceException(
                modelId = spec.id,
                message = engine.info().statusMessage
            )
        }

        val modelFile = getModelFileOnDisk(spec.id)
            ?: throw LocalModelNotLoadedException(
                modelId = spec.id,
                message = "Model not loaded: '${spec.name}' is not downloaded on disk. Open Local LLM Hub to download it."
            )

        val loadRes = inferenceOptimizer.loadAndPrepareModel(
            modelId = spec.id,
            systemPromptPrefix = systemPromptPrefix,
            modelFile = modelFile
        )
        when (loadRes) {
            is ModelLoadResult.RefusedByLoadGuard -> throw LocalModelLoadGuardException(loadRes)
            is ModelLoadResult.EngineFailure -> {
                if (loadRes.errorKind == LocalRuntimeErrorKind.CORRUPT_FILE ||
                    loadRes.errorKind == LocalRuntimeErrorKind.UNSUPPORTED_MODEL ||
                    loadRes.errorKind == LocalRuntimeErrorKind.OUT_OF_MEMORY
                ) {
                    throw TypedLocalInferenceException(
                        kind = loadRes.errorKind,
                        modelId = spec.id,
                        userTitle = loadRes.userTitle,
                        userMessage = loadRes.reason,
                        suggestedActionLabel = loadRes.suggestedActionLabel
                    )
                }
                throw LocalModelNotLoadedException(
                    modelId = spec.id,
                    message = "Model not loaded: ${loadRes.reason}"
                )
            }
            is ModelLoadResult.Loaded -> inferenceOptimizer.touchActiveModel(systemPromptPrefix)
        }

        val loadedInfo = loadRes as ModelLoadResult.Loaded
        val cfg = loadedInfo.config

        val userTurnPrompt = buildString {
            append(userPrompt.trim())
            if (recentCommandOutputs.isNotEmpty()) {
                append("\n\n")
                for ((cmd, out) in recentCommandOutputs) {
                    append("Previous command executed:\n```sh-exec\n").append(cmd).append("\n```\n")
                    append(out.trim()).append("\n")
                }
            }
        }

        val params = InferenceParams(
            contextLength = cfg.nCtx,
            threads = cfg.nThreads,
            threadsBatch = cfg.nThreadsBatch,
            batchSize = cfg.nBatch,
            useMmap = cfg.useMmap,
            flashAttention = cfg.flashAttention,
            kvCacheType = cfg.kvCacheType,
            temperature = if (cfg.presetUsed == InferencePreset.SPEED) 0.2f else 0.4f,
            systemInstruction = systemPromptPrefix,
            stopSequences = ToolCallParser.LOCAL_STOP_SEQUENCES,
            expectedSizeBytes = spec.sizeBytes
        )

        val buffer = StringBuilder()
        try {
            engine.generate(userTurnPrompt, params).collect { chunk ->
                currentCoroutineContext().ensureActive()
                buffer.append(chunk)
                emit(chunk)
            }
        } catch (ce: CancellationException) {
            engine.stop()
            throw ce
        } catch (te: TypedLocalInferenceException) {
            throw te
        } catch (e: Throwable) {
            throw LocalModelInferenceException(
                modelId = spec.id,
                message = "Inference failed on ${spec.name}: ${e.message ?: "runtime error"}"
            )
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Dedicated Quick Command translator (Part A & Clarifications):
     * Triggered ONLY when the user explicitly taps the **Quick Command** action chip or types `/quick <directive>`.
     * Never invoked from `streamLocalInference` and never poses as AI model output.
     */
    fun translateQuickCommandDirective(
        directive: String,
        currentCwd: String
    ): QuickCommandTranslation? {
        val cmd = translateDirectiveToSingleShellCommand(directive, currentCwd) ?: return null
        return QuickCommandTranslation(
            summaryLabel = "Quick command",
            shellCommand = cmd
        )
    }

    fun translateDirectiveToSingleShellCommand(
        directive: String,
        currentCwd: String
    ): String? {
        val trimmed = directive.trim().removePrefix("/quick").trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.US)

        val parsedExisting = ToolCallParser.parse(trimmed)
        if (parsedExisting is ToolCallParseResult.Success) {
            return parsedExisting.toolCall.command
        }

        val directShellPrefixes = listOf(
            "mkdir ", "ls", "pwd", "cat ", "echo ", "touch ", "rm ", "mv ", "cp ",
            "chmod ", "chown ", "find ", "grep ", "sed ", "head ", "tail ", "wc ",
            "df", "du", "uname", "whoami", "id", "ps", "top", "env", "printenv",
            "date", "uptime", "stat ", "file ", "tar ", "gzip ", "gunzip ", "curl ", "wget ", "git "
        )
        if (!trimmed.contains('\n') && directShellPrefixes.any {
                lower == it.trim() || lower.startsWith(it)
            }
        ) {
            return trimmed
        }

        val strippedAction = trimmed.replace(
            Regex("^(?:please\\s+)?(?:run|execute|exec|shell)\\s+", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        if (strippedAction != trimmed && !strippedAction.contains('\n')) {
            val strippedLower = strippedAction.lowercase(Locale.US)
            if (directShellPrefixes.any { strippedLower == it.trim() || strippedLower.startsWith(it) }) {
                return strippedAction
            }
        }

        val createFolderRegex = Regex(
            pattern = "(?:create|make|new|add|mkdir)\\s+(?:a\\s+|an\\s+)?(?:new\\s+)?(?:folder|directory|dir|subfolder|subdir)(?:\\s+(?:named|called|with\\s+name))?\\s+['\"`]?([a-zA-Z0-9_./-]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        createFolderRegex.find(trimmed)?.let { match ->
            val folderName = match.groupValues[1].trim()
            if (folderName.isNotEmpty()) {
                return "mkdir -p $folderName"
            }
        }

        if (lower.contains("create") || lower.contains("make") || lower.contains("mkdir")) {
            val namedDirRegex = Regex(
                pattern = "(?:folder|directory|dir)\\s+(?:named|called)\\s+['\"`]?([a-zA-Z0-9_./-]+)['\"`]?",
                option = RegexOption.IGNORE_CASE
            )
            namedDirRegex.find(trimmed)?.let { match ->
                val folderName = match.groupValues[1].trim()
                if (folderName.isNotEmpty()) {
                    return "mkdir -p $folderName"
                }
            }
        }

        val createFileWithContentRegex = Regex(
            pattern = "(?:create|make|write|touch)\\s+(?:a\\s+|an\\s+)?(?:new\\s+)?file(?:\\s+(?:named|called))?\\s+['\"`]?([a-zA-Z0-9_./-]+)['\"`]?\\s+(?:with\\s+(?:content|text)|containing)\\s+['\"`]?([^\\n'\"`]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        createFileWithContentRegex.find(trimmed)?.let { match ->
            val fileName = match.groupValues[1].trim()
            val content = match.groupValues[2].trim().replace("\"", "\\\"")
            if (fileName.isNotEmpty()) {
                return "printf \"%s\\n\" \"$content\" > $fileName"
            }
        }

        val createFileRegex = Regex(
            pattern = "(?:create|make|touch)\\s+(?:a\\s+|an\\s+)?(?:empty\\s+|new\\s+)?file(?:\\s+(?:named|called))?\\s+['\"`]?([a-zA-Z0-9_./-]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        createFileRegex.find(trimmed)?.let { match ->
            val fileName = match.groupValues[1].trim()
            if (fileName.isNotEmpty()) {
                return "touch $fileName"
            }
        }

        val removeRecursiveRegex = Regex(
            pattern = "(?:delete|remove|rm)\\s+(?:the\\s+)?(?:folder|directory|dir)\\s+(?:named\\s+|called\\s+)?['\"`]?([a-zA-Z0-9_./-]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        removeRecursiveRegex.find(trimmed)?.let { match ->
            val target = match.groupValues[1].trim()
            if (target.isNotEmpty()) {
                return "rm -rf $target"
            }
        }

        val removeFileRegex = Regex(
            pattern = "(?:delete|remove|rm)\\s+(?:the\\s+)?(?:file\\s+)?(?:named\\s+|called\\s+)?['\"`]?([a-zA-Z0-9_./-]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        if (lower.startsWith("delete ") || lower.startsWith("remove ")) {
            removeFileRegex.find(trimmed)?.let { match ->
                val target = match.groupValues[1].trim()
                if (target.isNotEmpty()) {
                    return "rm $target"
                }
            }
        }

        val readFileRegex = Regex(
            pattern = "(?:read|view|show|cat|open|display|print)\\s+(?:the\\s+)?(?:contents?\\s+of\\s+)?(?:file\\s+)?['\"`]?([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        readFileRegex.find(trimmed)?.let { match ->
            val fileName = match.groupValues[1].trim()
            if (fileName.isNotEmpty()) {
                return "cat $fileName"
            }
        }

        if (lower.contains("list files") ||
            lower.contains("show files") ||
            lower.contains("directory structure") ||
            lower.contains("analyze the directory") ||
            lower.contains("inspect workspace") ||
            lower.contains("what files") ||
            lower.contains("list directory")
        ) {
            return "ls -la"
        }

        if (lower.contains("disk space") ||
            lower.contains("free storage") ||
            lower.contains("storage usage") ||
            lower.contains("check disk")
        ) {
            return "df -h ."
        }

        if (lower.contains("working directory") ||
            lower.contains("current directory") ||
            lower.contains("where am i")
        ) {
            return "pwd"
        }

        if (lower.contains("kernel") ||
            lower.contains("system info") ||
            lower.contains("device info") ||
            lower.contains("system telemetry")
        ) {
            return "uname -a"
        }

        val findRegex = Regex(
            pattern = "(?:find|search\\s+for)\\s+files?\\s+(?:named|matching)\\s+['\"`]?([a-zA-Z0-9_*.-]+)['\"`]?",
            option = RegexOption.IGNORE_CASE
        )
        findRegex.find(trimmed)?.let { match ->
            val pattern = match.groupValues[1].trim()
            if (pattern.isNotEmpty()) {
                return "find . -maxdepth 3 -name \"$pattern\""
            }
        }

        return null
    }
}
