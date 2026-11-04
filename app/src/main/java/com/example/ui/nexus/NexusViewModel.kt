package com.example.ui.nexus

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.NexusSession
import com.example.data.model.NexusUserSettings
import com.example.data.model.TranscriptEntry
import com.example.data.repository.NexusRepository
import com.example.domain.ActiveLoadedModelState
import com.example.domain.AndroidShellBackend
import com.example.domain.BackgroundJobInfo
import com.example.domain.BackendWorkspaceFileOps
import com.example.domain.CommandRiskLevel
import com.example.domain.CwdValidationResult
import com.example.domain.DeviceHardwareTelemetry
import com.example.domain.DeviceProfile
import com.example.domain.DeviceRecommendationReport
import com.example.domain.DirectoryPreset
import com.example.domain.EnvironmentDescription
import com.example.domain.ExecEvent
import com.example.domain.ExecutionBackend
import com.example.domain.ExecutionBackendType
import com.example.domain.ExecutionTimeouts
import com.example.domain.InferencePreset
import com.example.domain.LocalLlmManager
import com.example.domain.LocalLlmSpec
import com.example.domain.LocalModelDownloadState
import com.example.domain.LocalModelInferenceException
import com.example.domain.LocalModelLoadGuardException
import com.example.domain.LocalModelNotLoadedException
import com.example.domain.LocalRuntimeErrorKind
import com.example.domain.TypedLocalInferenceException
import com.example.domain.ThinkingTagParser
import com.example.domain.LocalShellBridge
import com.example.domain.MissingApiKeyException
import com.example.domain.ModelBenchmarkResult
import com.example.domain.ModelCapabilityCheckResult
import com.example.domain.ModelLoadResult
import com.example.domain.MultiPlatformAiEngine
import com.example.domain.NexusFontSize
import com.example.domain.NexusPersonalizationConfig
import com.example.domain.NexusPersonalizationStore
import com.example.domain.NexusResponseStyle
import com.example.domain.NexusToolAndRedactor
import com.example.domain.ProviderTransport
import com.example.domain.RemoteBridgeBackend
import com.example.domain.ShellExecResult
import com.example.domain.TermuxBackend
import com.example.domain.JobManager
import com.example.domain.JobStore
import com.example.domain.ManagedJobRecord
import com.example.domain.ManagedJobState
import com.example.domain.ServerLogAnalyzer
import com.example.domain.StartJobOutcome
import com.example.domain.ToolActionKind
import com.example.domain.ToolCall
import com.example.domain.ToolCallParseResult
import com.example.domain.ToolCallParser
import com.example.domain.UserApiKeyStore
import com.example.domain.WorkspaceMode
import com.example.domain.BackendCircuitBreaker
import com.example.domain.BackendHealthState
import com.example.domain.ErrorMapper
import com.example.domain.FallbackPolicy
import com.example.domain.SelfTestReport
import com.example.domain.SelfTestStepResult
import com.example.domain.TermuxDiagnostics
import com.example.domain.TermuxDiagnosticsReport
import com.example.domain.TermuxError
import com.example.domain.TermuxWizardState
import com.example.domain.TermuxWizardStore
import com.example.domain.WorkspaceProjectGuide
import com.example.domain.WorkspaceProjectType
import com.example.domain.CommandJailDecision
import com.example.domain.MirrorConflictItem
import com.example.domain.MirrorConflictResolutionChoice
import com.example.domain.MirrorExcludeConfig
import com.example.domain.MirrorSyncResult
import com.example.domain.MirrorSyncStatus
import com.example.domain.MirrorTypedError
import com.example.domain.PendingProjectCreationProposal
import com.example.domain.ProjectFolderSummary
import com.example.domain.ProjectMirrorManager
import com.example.domain.WorkspaceCommandAdapter
import com.example.domain.WorkspacePathValidation
import com.example.domain.WorkspacePathValidator
import com.example.domain.WorkspacePaths
import com.example.domain.WorkspaceSecretScanner
import com.example.util.NexusNotificationHelper
import com.google.firebase.Timestamp
import com.example.domain.ApiKeyTestResult
import com.example.domain.EngineInfo
import com.example.domain.ProviderApiException
import com.example.domain.ProviderErrorKind
import com.example.domain.ProviderModelRepository
import com.example.domain.ModelOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "NexusViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ProposedToolCall(
    val command: String,
    val reason: String,
    val timeoutSec: Long
)

data class AiCommandAnalysis(
    val entryId: String,
    val summary: String,
    val rootCauseOrImpact: String,
    val securityNotes: String,
    val suggestedCommand: String
)

data class LiveNotificationEvent(
    val id: String = UUID.randomUUID().toString(),
    val kind: String, // "approval", "download_done", "guest_limit", "deleted_undo"
    val title: String,
    val message: String,
    val entryId: String? = null
)

data class MissingApiKeyPrompt(
    val provider: String,
    val modelId: String,
    val providerDisplayName: String,
    val message: String
)

private data class DeletedSessionBackup(
    val session: NexusSession,
    val entries: List<TranscriptEntry>
)

private data class TruncatedEditBackup(
    val sessionId: String,
    val keptBaselineIds: Set<String>,
    val minRemovedSequence: Long,
    val removedEntries: List<TranscriptEntry>,
    val guestQuotaIncremented: Boolean
)

class NexusViewModel(
    private val repository: NexusRepository? = null,
    private val shellBridge: LocalShellBridge,
    private val localLlmManager: LocalLlmManager,
    private val userApiKeyStore: UserApiKeyStore,
    private val notificationHelper: NexusNotificationHelper,
    val currentUserId: String,
    val isGuestMode: Boolean = false,
    customTransport: ProviderTransport? = null,
    val providerModelRepository: ProviderModelRepository = ProviderModelRepository(
        context = null,
        userApiKeyStore = userApiKeyStore
    ),
    val personalizationStore: NexusPersonalizationStore = NexusPersonalizationStore(context = null),
    private val appContext: Context? = null,
    var customExecutionBackend: ExecutionBackend? = null
) : ViewModel() {

    companion object {
        const val GUEST_MAX_DIRECTIVES = 5
        const val GUEST_DIRECTIVES_LIMIT = GUEST_MAX_DIRECTIVES
        const val GUEST_MAX_STEPS = 3L
        private const val TEMP_SESSION_ID = "temporary_chat_ephemeral"
    }

    private val multiPlatformEngine = MultiPlatformAiEngine(
        localLlmManager = localLlmManager,
        userApiKeyStore = userApiKeyStore,
        customTransport = customTransport
    )

    val personalizationConfig: StateFlow<NexusPersonalizationConfig> = personalizationStore.config
    val avatarBitmap: StateFlow<Bitmap?> = personalizationStore.avatarBitmap

    // Execution Backends (Part 2: Termux, AndroidShell, RemoteBridge)
    private val androidShellBackend = AndroidShellBackend(shellBridge)
    private val termuxBackend: TermuxBackend? = appContext?.let { ctx ->
        TermuxBackend(context = ctx.applicationContext)
    }
    private val remoteBridgeBackend: RemoteBridgeBackend? = appContext?.let { ctx ->
        RemoteBridgeBackend(
            context = ctx.applicationContext,
            endpointUrlProvider = {
                if (isGuestMode) "" else _remoteBridgeUrl.value.ifBlank { personalizationStore.config.value.remoteBridgeUrl }
            }
        )
    }

    private val _environmentDescription = MutableStateFlow(
        EnvironmentDescription(
            backendType = ExecutionBackendType.ANDROID_SHELL,
            shellName = "/system/bin/sh (Android toybox)",
            isAvailable = true,
            summaryLabel = "Limited shell: Android toybox",
            cwd = shellBridge.currentCwd,
            workspaceMode = WorkspaceMode.SHARED_STORAGE,
            statusWarning = "Limited shell: install Termux for git, node, python"
        )
    )
    val environmentDescription: StateFlow<EnvironmentDescription> = _environmentDescription.asStateFlow()

    private val _isProbingEnvironment = MutableStateFlow(false)
    val isProbingEnvironment: StateFlow<Boolean> = _isProbingEnvironment.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    // Diagnostics, Circuit Breaker, Setup Wizard, and Project Guide
    val circuitBreaker = BackendCircuitBreaker(failureThreshold = 3, cooldownMs = 60_000L)
    val wizardStore: TermuxWizardStore? = appContext?.let { TermuxWizardStore(it) }
    val termuxDiagnostics: TermuxDiagnostics? = appContext?.let { ctx ->
        termuxBackend?.let { tb -> TermuxDiagnostics(ctx, tb) }
    }

    private val _diagnosticsReport = MutableStateFlow<TermuxDiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<TermuxDiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _selfTestReport = MutableStateFlow<SelfTestReport?>(null)
    val selfTestReport: StateFlow<SelfTestReport?> = _selfTestReport.asStateFlow()

    private val _isRunningSelfTest = MutableStateFlow(false)
    val isRunningSelfTest: StateFlow<Boolean> = _isRunningSelfTest.asStateFlow()

    private val _projectGuide = MutableStateFlow<WorkspaceProjectGuide?>(null)
    val projectGuide: StateFlow<WorkspaceProjectGuide?> = _projectGuide.asStateFlow()

    private val _verifiedRecipeIds = MutableStateFlow<Set<String>>(emptySet())

    private val _wizardState = MutableStateFlow(TermuxWizardState())
    val wizardState: StateFlow<TermuxWizardState> = _wizardState.asStateFlow()

    // Workspace Project Binding, Lazy Slug Flow & One-Way Source Mirroring (Run B & Run C)
    var enforceUnassignedProjectSlugFlow: Boolean = (customTransport == null && customExecutionBackend == null)

    val projectMirrorManager = ProjectMirrorManager(
        appContext = appContext,
        backendProvider = { resolveActiveExecutionBackend() },
        excludeConfigProvider = { personalizationStore.config.value.mirrorExcludeConfig }
    )
    val mirrorSyncStatus: StateFlow<MirrorSyncStatus> = projectMirrorManager.syncStatus
    val lastMirrorSyncTimeMs: StateFlow<Long> = projectMirrorManager.lastSyncTimestampMs
    val mirrorConflicts: StateFlow<List<MirrorConflictItem>> = projectMirrorManager.activeConflicts
    val mirrorTypedError: StateFlow<MirrorTypedError?> = projectMirrorManager.lastTypedError

    private val _activeProjectSlug = MutableStateFlow("")
    val activeProjectSlug: StateFlow<String> = _activeProjectSlug.asStateFlow()

    private val _activeProjectStorageLocation = MutableStateFlow("shared")
    val activeProjectStorageLocation: StateFlow<String> = _activeProjectStorageLocation.asStateFlow()

    private val _isBoundProjectMissing = MutableStateFlow(false)
    val isBoundProjectMissing: StateFlow<Boolean> = _isBoundProjectMissing.asStateFlow()

    private val _availableProjects = MutableStateFlow<List<ProjectFolderSummary>>(emptyList())
    val availableProjects: StateFlow<List<ProjectFolderSummary>> = _availableProjects.asStateFlow()

    private val _pendingProjectProposal = MutableStateFlow<PendingProjectCreationProposal?>(null)
    val pendingProjectProposal: StateFlow<PendingProjectCreationProposal?> = _pendingProjectProposal.asStateFlow()

    private val _installProgressBanner = MutableStateFlow<String?>(null)
    val installProgressBanner: StateFlow<String?> = _installProgressBanner.asStateFlow()

    private val preflightVerifiedPaths = ConcurrentHashMap.newKeySet<String>()
    private var activeTempScratchPath: String? = null

    val jobStore: JobStore? = appContext?.let { JobStore(it) }
    val jobManager: JobManager = JobManager(
        backendProvider = { resolveActiveExecutionBackend() },
        jobStore = jobStore,
        secretExtractor = { userApiKeyStore.allSavedSecretsForRedaction() },
        onUnexpectedJobExit = { job, tail ->
            val redactedTail = tail.map {
                NexusToolAndRedactor.redactSecrets(it, userApiKeyStore.allSavedSecretsForRedaction()).text
            }
            notificationHelper.notifyUnexpectedJobExit(job.id, job.command, job.exitCode ?: 1, redactedTail)
            viewModelScope.launch {
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Job ${job.id} (`${job.command}`) exited unexpectedly with code ${job.exitCode ?: "unknown"}:\n" +
                        redactedTail.takeLast(4).joinToString("\n"),
                    noticeKind = "error",
                    command = job.command,
                    cwd = job.cwd
                )
            }
        },
        keepWakeLockProvider = { _wizardState.value.keepTermuxAwakeWhileJobsRun }
    )

    val managedJobs: StateFlow<List<ManagedJobRecord>> = jobManager.jobs
    val reconciledRunningCountOnStart: StateFlow<Int> = jobManager.reconciledRunningCountOnStart

    val backgroundJobs: StateFlow<List<BackgroundJobInfo>> = jobManager.jobs.map { list ->
        list.map { j ->
            BackgroundJobInfo(
                jobId = j.id,
                command = j.command,
                cwd = j.cwd,
                backendType = ExecutionBackendType.TERMUX,
                startedAtMs = j.startedAtMs,
                isRunning = j.state == ManagedJobState.RUNNING || j.state == ManagedJobState.STARTING,
                exitCode = j.exitCode,
                logTail = j.logLines.takeLast(8)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isSetupNeeded: StateFlow<Boolean> = combine(
        _environmentDescription,
        _diagnosticsReport,
        _wizardState
    ) { env, diag, wiz ->
        if (wiz.completed || wiz.skipped) {
            false
        } else {
            val termuxAvailable = env.isTermuxInstalled && env.isAvailable
            val diagNeedsSetup = diag?.needsSetupBadge ?: (!termuxAvailable || env.statusWarning != null)
            diagNeedsSetup
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Single source of truth for model routing, persisted in Jetpack DataStore via UserApiKeyStore
    val selectedProvider: StateFlow<String> = userApiKeyStore.selectedProvider
    val selectedModelId: StateFlow<String> = userApiKeyStore.selectedModelId

    // Dynamic provider model lists (Part B #1-3 & Amendments #1, #2, #7)
    val geminiModels: StateFlow<List<ModelOption>> = providerModelRepository.geminiModels
    val anthropicModels: StateFlow<List<ModelOption>> = providerModelRepository.anthropicModels
    val openAiModels: StateFlow<List<ModelOption>> = providerModelRepository.openAiModels
    val showAllOpenAiModels: StateFlow<Boolean> = providerModelRepository.showAllOpenAiModels
    val isFetchingModels: StateFlow<Map<String, Boolean>> = providerModelRepository.isFetchingModels

    private val _keyTestStatuses = MutableStateFlow<Map<String, ApiKeyTestResult>>(emptyMap())
    val keyTestStatuses: StateFlow<Map<String, ApiKeyTestResult>> = _keyTestStatuses.asStateFlow()

    val downloadedLocalModels: StateFlow<Set<String>> = localLlmManager.downloadedModels
    val localDownloadStates: StateFlow<Map<String, LocalModelDownloadState>> = localLlmManager.downloadStates
    val isNetworkOnline: StateFlow<Boolean> = localLlmManager.isNetworkOnline
    val wifiOnlyDownloads: StateFlow<Boolean> = localLlmManager.wifiOnlyDownloads
    val engineInfo: StateFlow<EngineInfo> = localLlmManager.engineInfo
    val savedApiKeys: StateFlow<Map<String, String>> = userApiKeyStore.keysMap

    private val _pendingMobileDataDownloadSpec = MutableStateFlow<LocalLlmSpec?>(null)
    val pendingMobileDataDownloadSpec: StateFlow<LocalLlmSpec?> = _pendingMobileDataDownloadSpec.asStateFlow()

    // Strict Device Measurement, Recommendations & Inference Optimizer StateFlows
    val deviceProfile: StateFlow<DeviceProfile> = localLlmManager.deviceProfile
    val recommendationReport: StateFlow<DeviceRecommendationReport> = localLlmManager.recommendationReport
    val modelBenchmarks: StateFlow<Map<String, ModelBenchmarkResult>> = localLlmManager.benchmarks
    val activeLoadedLocalModel: StateFlow<ActiveLoadedModelState?> = localLlmManager.activeLoadedModel
    val inferencePreset: StateFlow<InferencePreset> = localLlmManager.activePreset
    val isBenchmarking: StateFlow<Boolean> = localLlmManager.isBenchmarking
    val loadGuardError: StateFlow<ModelLoadResult.RefusedByLoadGuard?> = localLlmManager.lastLoadGuardError

    private val _missingApiKeyPrompt = MutableStateFlow<MissingApiKeyPrompt?>(null)
    val missingApiKeyPrompt: StateFlow<MissingApiKeyPrompt?> = _missingApiKeyPrompt.asStateFlow()

    private val _runtimeLatenciesMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val runtimeLatenciesMs: StateFlow<Map<String, Long>> = _runtimeLatenciesMs.asStateFlow()

    private val _liveNotificationBanner = MutableStateFlow<LiveNotificationEvent?>(null)
    val liveNotificationBanner: StateFlow<LiveNotificationEvent?> = _liveNotificationBanner.asStateFlow()

    private val _guestDirectivesUsed = MutableStateFlow(0)
    val guestDirectivesUsed: StateFlow<Int> = _guestDirectivesUsed.asStateFlow()

    // Temporary Chat Mode (in-memory only, never saved or synced, never shown in sidebar Chats)
    private val _isTemporaryChat = MutableStateFlow(false)
    val isTemporaryChat: StateFlow<Boolean> = _isTemporaryChat.asStateFlow()

    private val _temporaryEntries = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    private val _temporarySession = MutableStateFlow(
        NexusSession(
            id = TEMP_SESSION_ID,
            userId = currentUserId,
            title = "Temporary chat",
            status = "idle",
            cwd = shellBridge.currentCwd,
            commandsCount = 0L,
            redactionsCount = 0L,
            provider = NexusToolAndRedactor.providerDisplayLabel(userApiKeyStore.selectedProvider.value),
            model = userApiKeyStore.selectedModelId.value,
            isTemporary = true
        )
    )
    val temporarySession: StateFlow<NexusSession> = _temporarySession.asStateFlow()

    // Pinned Session IDs & Message Thumbs Feedback
    private val _pinnedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedSessionIds: StateFlow<Set<String>> = _pinnedSessionIds.asStateFlow()

    private val _messageFeedback = MutableStateFlow<Map<String, String>>(emptyMap()) // entryId -> "up" | "down"
    val messageFeedback: StateFlow<Map<String, String>> = _messageFeedback.asStateFlow()

    private var lastDeletedBackup: DeletedSessionBackup? = null
    private var lastTruncatedEditBackup: TruncatedEditBackup? = null
    private var editUndoExpiryJob: Job? = null
    private val _optimisticallyTruncatedIds = MutableStateFlow<Set<String>>(emptySet())
    private var lastSubmittedPrompt: String? = null
    private var lastFailedDirectCommand: String? = null

    // Local ephemeral state for Guest Mode (never touches Firestore)
    private val _guestSessions = MutableStateFlow(
        listOf(
            NexusSession(
                id = "guest_session_1",
                userId = "guest_local_session",
                title = "New chat",
                status = "idle",
                cwd = shellBridge.currentCwd,
                commandsCount = 0L,
                redactionsCount = 0L,
                provider = NexusToolAndRedactor.providerDisplayLabel(userApiKeyStore.selectedProvider.value),
                model = userApiKeyStore.selectedModelId.value,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        )
    )
    private val _guestEntries = MutableStateFlow<List<TranscriptEntry>>(emptyList())

    /**
     * Redacted debug-only pipeline stage logger (DIAGNOSE requirement).
     * Never logs in release builds and always redacts user API keys and secrets before printing.
     */
    private fun logPipelineStage(stage: String, detail: String) {
        if (BuildConfig.DEBUG) {
            val redactedDetail = NexusToolAndRedactor.redactSecrets(
                detail,
                userApiKeyStore.allSavedSecretsForRedaction()
            ).text
            Log.d(TAG, "[PIPELINE][$stage] $redactedDetail")
        }
    }

    fun inspectDeviceHardware(): DeviceHardwareTelemetry = localLlmManager.inspectDeviceHardware()

    fun refreshDeviceRecommendations(forceRebenchmark: Boolean = false): DeviceRecommendationReport =
        localLlmManager.refreshDeviceProfileAndRecommendations(forceRebenchmark)

    fun setInferencePreset(preset: InferencePreset) {
        localLlmManager.setInferencePreset(preset)
    }

    fun reRunBenchmark(modelId: String? = null) {
        val targetId = modelId
            ?: selectedModelId.value.takeIf { selectedProvider.value == "local" }
            ?: downloadedLocalModels.value.firstOrNull()
            ?: LocalLlmManager.LOCAL_LLM_CATALOG.first().id
        viewModelScope.launch {
            val result = localLlmManager.reRunBenchmarkForModel(targetId)
            if (result != null) {
                val specName = LocalLlmManager.findSpec(targetId)?.name ?: targetId
                _liveNotificationBanner.value = LiveNotificationEvent(
                    kind = "download_done",
                    title = "Benchmark Complete · $specName",
                    message = String.format(
                        Locale.US,
                        "Measured %.1f tok/s · TTFT %dms · Load %dms",
                        result.tokensPerSec,
                        result.ttftMs,
                        result.loadTimeMs
                    )
                )
            }
        }
    }

    fun unloadLocalModel() {
        localLlmManager.unloadActiveLocalModel()
        _uiBannerMessage.value = "Unloaded on-device model from RAM."
    }

    fun onAppBackgrounded() {
        localLlmManager.onAppBackgrounded()
    }

    fun dismissMissingApiKeyPrompt() {
        _missingApiKeyPrompt.value = null
    }

    fun dismissLoadGuardError() {
        localLlmManager.inferenceOptimizer.dismissLoadGuardError()
    }

    fun checkDeviceCapabilityForModel(spec: LocalLlmSpec): ModelCapabilityCheckResult =
        localLlmManager.checkDeviceCapabilityForModel(spec)

    fun refreshProviderModels(provider: String? = null, forceNetwork: Boolean = true) {
        viewModelScope.launch {
            val targets = if (provider != null) listOf(provider) else listOf("gemini", "anthropic", "openai")
            targets.forEach { prov ->
                if (prov == "anthropic" || userApiKeyStore.hasApiKeyFor(prov, null)) {
                    providerModelRepository.fetchModels(prov, forceRefresh = forceNetwork)
                }
            }
        }
    }

    fun setShowAllOpenAiModels(showAll: Boolean) {
        viewModelScope.launch {
            providerModelRepository.setShowAllOpenAiModels(showAll)
        }
    }

    fun toggleShowAllOpenAiModels(showAll: Boolean = !showAllOpenAiModels.value) {
        setShowAllOpenAiModels(showAll)
    }

    fun testProviderApiKey(provider: String, candidateKey: String) {
        val cleanProv = NexusToolAndRedactor.normalizeProviderId(provider)
        viewModelScope.launch {
            val res = providerModelRepository.testApiKey(cleanProv, candidateKey)
            _keyTestStatuses.update { it + (cleanProv to res) }
            if (res is ApiKeyTestResult.Valid && candidateKey.isNotBlank()) {
                userApiKeyStore.setProviderKey(cleanProv, candidateKey.trim())
                providerModelRepository.fetchModels(cleanProv, forceRefresh = true)
            }
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        localLlmManager.setWifiOnlyDownloads(enabled)
    }

    fun getHuggingFaceToken(): String = userApiKeyStore.getHuggingFaceToken()

    fun setHuggingFaceToken(token: String) {
        userApiKeyStore.setHuggingFaceToken(token)
    }

    fun saveHuggingFaceToken(token: String) {
        setHuggingFaceToken(token)
    }

    fun getTotalDownloadedBytes(): Long = localLlmManager.getTotalDownloadedBytes()

    fun getDownloadedFileSizeBytes(modelId: String): Long = localLlmManager.getDownloadedFileSizeBytes(modelId)

    fun confirmMobileDataDownload() {
        val spec = _pendingMobileDataDownloadSpec.value ?: return
        _pendingMobileDataDownloadSpec.value = null
        startLocalModelDownload(spec, confirmedMobileData = true)
    }

    fun waitForWifiForPendingDownload() {
        setWifiOnlyDownloads(true)
        val spec = _pendingMobileDataDownloadSpec.value
        _pendingMobileDataDownloadSpec.value = null
        if (spec != null) {
            startLocalModelDownload(spec, confirmedMobileData = false)
        }
    }

    fun dismissMobileDataPrompt() {
        _pendingMobileDataDownloadSpec.value = null
    }

    fun startLocalModelDownload(spec: LocalLlmSpec, confirmedMobileData: Boolean = false) {
        // Capability check runs before every download
        val capCheck = localLlmManager.checkDeviceCapabilityForModel(spec)
        if (!capCheck.storagePassed || !capCheck.archPassed) {
            _uiBannerMessage.value = capCheck.summaryRecommendation
            return
        }
        if (isGuestMode && spec.downloadSizeMb > 1100L) {
            _liveNotificationBanner.value = LiveNotificationEvent(
                kind = "guest_limit",
                title = "Sign In Required for Large Local Models (>1 GB)",
                message = "Guest Mode supports compact models up to 1 GB (like Qwen2.5-Coder 1.5B or SmolLM2 360M). Sign in with Google to download ${spec.name}."
            )
            return
        }
        if (!confirmedMobileData && localLlmManager.requiresMobileDataConfirmation(spec)) {
            _pendingMobileDataDownloadSpec.value = spec
            return
        }
        localLlmManager.startModelDownload(
            spec = spec,
            allowMobileDataForLargeModel = confirmedMobileData
        ) { completedSpec ->
            viewModelScope.launch {
                val bench = localLlmManager.benchmarks.value[completedSpec.id]
                val speedSuffix = bench?.let {
                    String.format(Locale.US, " · %.1f tok/s (TTFT %dms)", it.tokensPerSec, it.ttftMs)
                } ?: ""
                _liveNotificationBanner.value = LiveNotificationEvent(
                    kind = "download_done",
                    title = "Local LLM Ready · ${completedSpec.name}",
                    message = "${completedSpec.quantization} · ${completedSpec.downloadSizeMb} MB$speedSuffix"
                )
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Local LLM downloaded & GGUF verified: ${completedSpec.name} (${completedSpec.quantization} · ${completedSpec.downloadSizeMb} MB$speedSuffix).",
                    noticeKind = "info",
                    cwd = shellBridge.currentCwd
                )
            }
        }
    }

    fun pauseLocalModelDownload(modelId: String) {
        localLlmManager.pauseDownload(modelId)
    }

    fun resumeLocalModelDownload(spec: LocalLlmSpec) {
        localLlmManager.resumeDownload(spec) { completedSpec ->
            viewModelScope.launch {
                val bench = localLlmManager.benchmarks.value[completedSpec.id]
                val speedSuffix = bench?.let {
                    String.format(Locale.US, " · %.1f tok/s (TTFT %dms)", it.tokensPerSec, it.ttftMs)
                } ?: ""
                _liveNotificationBanner.value = LiveNotificationEvent(
                    kind = "download_done",
                    title = "Local LLM Download Complete",
                    message = "${completedSpec.name} (${completedSpec.quantization} · ${completedSpec.downloadSizeMb} MB$speedSuffix) resumed and finished."
                )
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Local LLM resumed, verified & benchmarked: ${completedSpec.name} (${completedSpec.quantization} · ${completedSpec.downloadSizeMb} MB$speedSuffix).",
                    noticeKind = "info",
                    cwd = shellBridge.currentCwd
                )
            }
        }
    }

    fun cancelLocalModelDownload(modelId: String) {
        localLlmManager.cancelDownload(modelId)
    }

    fun deleteLocalModel(modelId: String) {
        localLlmManager.deleteDownloadedModel(modelId)
    }

    fun deleteAndRedownloadCorruptLocalModel(modelId: String) {
        val targetId = modelId.ifBlank { selectedModelId.value }
        localLlmManager.deleteDownloadedModel(targetId)
        val spec = LocalLlmManager.findSpec(targetId)
        if (spec != null) {
            startLocalModelDownload(spec, confirmedMobileData = false)
            _uiBannerMessage.value = "Deleted corrupt ${spec.name} file and started fresh download."
        } else {
            _uiBannerMessage.value = "Deleted corrupt model file."
        }
    }

    fun activateLocalModel(spec: LocalLlmSpec) {
        selectProviderAndModel("local", spec.id)
    }

    fun selectProviderAndModel(provider: String, modelId: String) {
        saveSettings(
            provider = provider,
            selectedModel = modelId,
            maxTokens = _settings.value.maxTokens,
            maxSteps = _settings.value.maxSteps,
            timeoutSec = _settings.value.timeoutSec,
            agentPersona = _settings.value.agentPersona,
            remoteBridge = _remoteBridgeUrl.value
        )
    }

    private val _settings = MutableStateFlow(
        NexusUserSettings(
            userId = currentUserId,
            provider = userApiKeyStore.selectedProvider.value,
            selectedModel = userApiKeyStore.selectedModelId.value,
            maxSteps = if (isGuestMode) GUEST_MAX_STEPS else 6L,
            activeSessionId = if (isGuestMode) "guest_session_1" else "default_session",
            inputHistory = listOf("sh ./system-check.sh", "ls -la", "cat logs/agent-audit.log")
        )
    )
    val settings: StateFlow<NexusUserSettings> = _settings.asStateFlow()

    private val _activeSessionId = MutableStateFlow(if (isGuestMode) "guest_session_1" else "default_session")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _currentWorkingDir = MutableStateFlow(shellBridge.currentCwd)
    val currentWorkingDir: StateFlow<String> = _currentWorkingDir.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _agentStatus = MutableStateFlow("idle")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _promptInput = MutableStateFlow("")
    val promptInput: StateFlow<String> = _promptInput.asStateFlow()
    private var historyCursor: Int? = null
    private var historyDraft: String = ""

    private val _clearConfirmArmed = MutableStateFlow(false)
    val clearConfirmArmed: StateFlow<Boolean> = _clearConfirmArmed.asStateFlow()
    private var clearConfirmJob: Job? = null

    private val _activeAnalysis = MutableStateFlow<AiCommandAnalysis?>(null)
    val activeAnalysis: StateFlow<AiCommandAnalysis?> = _activeAnalysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _uiBannerMessage = MutableStateFlow<String?>(null)
    val uiBannerMessage: StateFlow<String?> = _uiBannerMessage.asStateFlow()

    private val _remoteBridgeUrl = MutableStateFlow("")
    val remoteBridgeUrl: StateFlow<String> = _remoteBridgeUrl.asStateFlow()

    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var activeAgentJob: Job? = null
    @Volatile
    private var userRequestedStop: Boolean = false

    private fun activeEffectiveSessionId(): String {
        return if (_isTemporaryChat.value) TEMP_SESSION_ID else _activeSessionId.value
    }

    val sessionsState: StateFlow<UiState<List<NexusSession>>> = combine(
        if (isGuestMode || repository == null) {
            _guestSessions.map<List<NexusSession>, UiState<List<NexusSession>>> { UiState.Success(it) }
        } else {
            repository.observeSessions()
                .map<List<NexusSession>, UiState<List<NexusSession>>> { list -> UiState.Success(list) }
                .catch { e ->
                    Log.e(TAG, "Error observing sessions", e)
                    emit(UiState.Error(e.localizedMessage ?: "Failed to load sessions"))
                }
        },
        _pinnedSessionIds
    ) { state, pinnedIds ->
        when (state) {
            is UiState.Success -> {
                val enriched = state.data
                    .filter { !it.isTemporary && it.id != TEMP_SESSION_ID }
                    .map { it.copy(isPinned = it.id in pinnedIds) }
                UiState.Success(enriched)
            }
            else -> state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = if (isGuestMode || repository == null) UiState.Success(_guestSessions.value) else UiState.Loading
    )

    private fun resolveApprovalsFromEntries(entries: List<TranscriptEntry>) {
        entries.forEach { entry ->
            if (entry.type == "command") {
                val deferred = pendingApprovals[entry.id]
                if (deferred != null && !deferred.isCompleted) {
                    when (entry.commandStatus) {
                        "approved" -> deferred.complete(true)
                        "denied", "cancelled" -> deferred.complete(false)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesState: StateFlow<UiState<List<TranscriptEntry>>> = combine(
        _isTemporaryChat,
        _activeSessionId
    ) { isTemp, sessionId -> isTemp to sessionId }
        .flatMapLatest { (isTemp, sessionId) ->
            when {
                isTemp -> {
                    _temporaryEntries.map<List<TranscriptEntry>, UiState<List<TranscriptEntry>>> { entries ->
                        resolveApprovalsFromEntries(entries)
                        UiState.Success(entries)
                    }
                }
                isGuestMode || repository == null -> {
                    _guestEntries.map<List<TranscriptEntry>, UiState<List<TranscriptEntry>>> { entries ->
                        resolveApprovalsFromEntries(entries)
                        UiState.Success(entries)
                    }
                }
                else -> {
                    combine(
                        repository.observeEntries(sessionId),
                        _optimisticallyTruncatedIds
                    ) { entries, truncatedIds ->
                        val filtered = if (truncatedIds.isEmpty()) entries else entries.filter { it.id !in truncatedIds }
                        resolveApprovalsFromEntries(filtered)
                        UiState.Success(filtered) as UiState<List<TranscriptEntry>>
                    }
                        .catch { e ->
                            Log.e(TAG, "Error observing entries for $sessionId", e)
                            emit(UiState.Error(e.localizedMessage ?: "Failed to observe session transcript"))
                        }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = if (isGuestMode || repository == null) UiState.Success(_guestEntries.value) else UiState.Loading
        )

    init {
        // Keep _settings synchronized with DataStore selectedProvider & selectedModelId
        viewModelScope.launch {
            combine(selectedProvider, selectedModelId) { prov, mod -> prov to mod }
                .collect { (prov, mod) ->
                    if (_settings.value.provider != prov || _settings.value.selectedModel != mod) {
                        _settings.update { it.copy(provider = prov, selectedModel = mod) }
                    }
                    // Clear missing API key card if the newly selected provider/model has a key or is local
                    if (userApiKeyStore.hasApiKeyFor(prov, mod)) {
                        _missingApiKeyPrompt.value = null
                    }
                }
        }

        // Clear missing API key card as soon as the user saves a key for the active provider
        viewModelScope.launch {
            savedApiKeys.collect {
                val prov = selectedProvider.value
                val mod = selectedModelId.value
                if (userApiKeyStore.hasApiKeyFor(prov, mod)) {
                    _missingApiKeyPrompt.value = null
                }
            }
        }

        if (!isGuestMode) {
            observeSettingsAndBootstrapSession()
        }

        wizardStore?.let { store ->
            viewModelScope.launch {
                store.stateFlow.collect { st ->
                    _wizardState.value = st
                }
            }
            viewModelScope.launch {
                val stale = store.consumeStaleCorrelationIdsOnRestart()
                if (stale.isNotEmpty()) {
                    Log.i(TAG, "Consumed ${stale.size} stale correlation IDs on restart: $stale")
                }
            }
        }

        refreshExecutionEnvironment()
        runDiagnostics(forceRefresh = false)
        refreshAvailableProjects()
        viewModelScope.launch {
            jobManager.reconcilePersistedJobsOnStart()
            sweepOrphanScratchFoldersOnStart()
        }
    }

    /**
     * Sweeps `$HOME/.nexus/scratch/` on app start for orphan scratch folders `>24h` old,
     * stopping any background jobs running in them before deleting them (Amendment #9).
     */
    private suspend fun sweepOrphanScratchFoldersOnStart() {
        val tb = termuxBackend ?: return
        if (!tb.isAvailable()) return
        runCatching {
            val orphans = tb.listOrphanScratchDirs(maxAgeMinutes = 24 * 60)
            for (orphanDir in orphans) {
                if (orphanDir.startsWith(WorkspacePaths.TERMUX_SCRATCH_ROOT)) {
                    jobManager.stopJobsInDirectory(orphanDir)
                    val escaped = orphanDir.replace("'", "'\\''")
                    tb.exec("rm -rf '$escaped'", WorkspacePaths.TERMUX_HOME_DIR, emptyMap(), 10L).collect {}
                }
            }
        }
    }

    fun refreshAvailableProjects() {
        val sharedRoot = if (shellBridge.currentCwd.startsWith(WorkspacePaths.DEFAULT_SHARED_ROOT)) {
            WorkspacePaths.DEFAULT_SHARED_ROOT
        } else {
            File(shellBridge.currentCwd).let { f ->
                if (f.name == "Nexus" || _activeProjectSlug.value.isBlank()) f.absolutePath
                else f.parentFile?.absolutePath ?: f.absolutePath
            }
        }
        _availableProjects.value = shellBridge.listProjectFolders(sharedRoot)
    }

    /**
     * Binds the active session to a project slug (`[a-z0-9-]`), resolves the device path,
     * updates the active CWD for both backends, and appends a transcript notice when switching.
     * Blocks switching while a command or job start is running (Amendment #8).
     */
    fun bindSessionToProject(
        rawSlug: String,
        storageLocation: String = _activeProjectStorageLocation.value,
        createFolder: Boolean = true,
        emitSwitchNotice: Boolean = true
    ): Result<String> {
        if (_isRunning.value) {
            _uiBannerMessage.value = "Cannot switch projects while a command is running."
            return Result.failure(IllegalStateException("Cannot switch projects while a command is running."))
        }
        val cleanSlug = WorkspacePaths.sanitizeSlug(rawSlug)
        val previousSlug = _activeProjectSlug.value
        val resolvedStorage = if (storageLocation == "termux_home") "termux_home" else "shared"
        val resolvedPath = if (resolvedStorage == "termux_home") {
            WorkspacePaths.termuxHomeProjectPath(cleanSlug)
        } else {
            val baseRoot = if (shellBridge.currentCwd.startsWith(WorkspacePaths.DEFAULT_SHARED_ROOT)) {
                WorkspacePaths.DEFAULT_SHARED_ROOT
            } else {
                File(shellBridge.currentCwd).let { f ->
                    if (previousSlug.isNotBlank() && f.name == previousSlug) {
                        f.parentFile?.absolutePath ?: WorkspacePaths.DEFAULT_SHARED_ROOT
                    } else {
                        f.absolutePath
                    }
                }
            }
            "$baseRoot/$cleanSlug"
        }

        if (resolvedStorage == "shared") {
            val res = shellBridge.setWorkingDirectory(resolvedPath, createIfMissing = createFolder)
            if (res.isFailure && createFolder) {
                return res
            }
        } else if (createFolder) {
            viewModelScope.launch {
                val escaped = resolvedPath.replace("'", "'\\''")
                resolveActiveExecutionBackend().exec("mkdir -p '$escaped'", WorkspacePaths.TERMUX_HOME_DIR, emptyMap(), 10L).collect {}
            }
        }

        _activeProjectSlug.value = cleanSlug
        _activeProjectStorageLocation.value = resolvedStorage
        _isBoundProjectMissing.value = resolvedStorage == "shared" && !File(resolvedPath).exists()
        _currentWorkingDir.value = resolvedPath
        termuxBackend?.resetSessionCwd(resolvedPath)
        personalizationStore.updateModeCwd(
            mode = if (resolvedStorage == "termux_home") WorkspaceMode.TERMUX_HOME else WorkspaceMode.SHARED_STORAGE,
            cwd = resolvedPath
        )
        personalizationStore.addRecentWorkspace(resolvedPath)
        refreshAvailableProjects()

        viewModelScope.launch {
            syncCurrentSessionStats(status = _agentStatus.value)
            if (emitSwitchNotice && previousSlug != cleanSlug) {
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Switched project to $cleanSlug (cwd: $resolvedPath)",
                    noticeKind = "info",
                    cwd = resolvedPath
                )
            }
        }
        return Result.success(resolvedPath)
    }

    fun detachSessionProject() {
        if (_isRunning.value) {
            _uiBannerMessage.value = "Cannot detach project while a command is running."
            return
        }
        _activeProjectSlug.value = ""
        _activeProjectStorageLocation.value = "shared"
        _isBoundProjectMissing.value = false
        val rootPath = WorkspacePaths.DEFAULT_SHARED_ROOT
        shellBridge.setWorkingDirectory(rootPath, createIfMissing = true)
        _currentWorkingDir.value = shellBridge.currentCwd
        termuxBackend?.resetSessionCwd(_currentWorkingDir.value)
        viewModelScope.launch {
            syncCurrentSessionStats(status = _agentStatus.value)
            appendEntryOrLocal(
                sessionId = activeEffectiveSessionId(),
                type = "notice",
                sequence = nextSequence(),
                text = "Detached project binding. Session is now Unassigned (read-only root: ${_currentWorkingDir.value}).",
                noticeKind = "info",
                cwd = _currentWorkingDir.value
            )
        }
    }

    fun recreateMissingProjectFolder() {
        val slug = _activeProjectSlug.value
        if (slug.isBlank()) return
        bindSessionToProject(slug, _activeProjectStorageLocation.value, createFolder = true, emitSwitchNotice = false)
        _isBoundProjectMissing.value = false
    }

    fun confirmPendingProjectProposal(chosenSlug: String, bindExistingOnCollision: Boolean = false) {
        val proposal = _pendingProjectProposal.value ?: return
        val cleanSlug = WorkspacePaths.sanitizeSlug(chosenSlug.ifBlank { proposal.proposedSlug })
        val existingSet = _availableProjects.value.map { it.slug }.toSet()
        val finalSlug = if (!bindExistingOnCollision && cleanSlug in existingSet) {
            WorkspacePaths.resolveSlugCollision(cleanSlug, existingSet).second
        } else {
            cleanSlug
        }
        _pendingProjectProposal.value = null
        val bindRes = bindSessionToProject(
            rawSlug = finalSlug,
            storageLocation = "shared",
            createFolder = true,
            emitSwitchNotice = true
        )
        if (bindRes.isSuccess) {
            when {
                proposal.triggerCommand.isNotBlank() -> executeDirectShellCommand(proposal.triggerCommand)
                proposal.triggerPrompt.isNotBlank() -> submitDirective(overridePrompt = proposal.triggerPrompt)
            }
        }
    }

    fun dismissPendingProjectProposal() {
        _pendingProjectProposal.value = null
    }

    /**
     * Separate confirmed action to delete a project's files on disk (Amendment #8).
     * Deleting a chat session never deletes its project folder automatically.
     */
    fun deleteProjectFilesConfirmed(slug: String) {
        val cleanSlug = WorkspacePaths.sanitizeSlug(slug)
        if (cleanSlug.isBlank()) return
        viewModelScope.launch {
            val sharedDir = File(WorkspacePaths.sharedProjectPath(cleanSlug))
            if (sharedDir.exists() && sharedDir.parentFile?.absolutePath == WorkspacePaths.DEFAULT_SHARED_ROOT) {
                runCatching { sharedDir.deleteRecursively() }
            }
            if (_activeProjectSlug.value == cleanSlug) {
                detachSessionProject()
            }
            refreshAvailableProjects()
            _uiBannerMessage.value = "Deleted project files for '$cleanSlug'."
        }
    }

    /**
     * Resolves the currently active [ExecutionBackend] based on user settings and Termux readiness.
     * Defaults to Termux if ready, else Android shell with a visible banner.
     */
    suspend fun resolveActiveExecutionBackend(): ExecutionBackend {
        customExecutionBackend?.let { custom ->
            if (custom.isAvailable()) return custom
        }
        val preferred = personalizationStore.config.value.executionBackendType
        return when (preferred) {
            ExecutionBackendType.TERMUX -> {
                val tb = termuxBackend
                if (tb != null && tb.isAvailable()) {
                    tb
                } else {
                    androidShellBackend
                }
            }
            ExecutionBackendType.REMOTE_BRIDGE -> {
                val rb = remoteBridgeBackend
                if (rb != null && rb.isAvailable()) {
                    rb
                } else {
                    androidShellBackend
                }
            }
            ExecutionBackendType.ANDROID_SHELL -> androidShellBackend
        }
    }

    /**
     * Refreshes the live [EnvironmentDescription] (Termux checklist, detected tool versions, active CWD).
     */
    fun refreshExecutionEnvironment() {
        if (_isProbingEnvironment.value) return
        viewModelScope.launch {
            _isProbingEnvironment.value = true
            try {
                val cfg = personalizationStore.config.value
                val preferred = cfg.executionBackendType
                val mode = cfg.workspaceMode
                val cwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
                val custom = customExecutionBackend
                val desc = if (custom != null) {
                    custom.describeEnvironment(cwd = cwd, workspaceMode = mode)
                } else {
                    when (preferred) {
                        ExecutionBackendType.TERMUX -> {
                            val tb = termuxBackend
                            if (tb != null) {
                                tb.describeEnvironment(cwd = cwd, workspaceMode = mode)
                            } else {
                                androidShellBackend.describeEnvironment(cwd = cwd, workspaceMode = mode)
                            }
                        }
                        ExecutionBackendType.REMOTE_BRIDGE -> {
                            val rb = remoteBridgeBackend
                            if (rb != null) {
                                rb.describeEnvironment(cwd = cwd, workspaceMode = mode)
                            } else {
                                androidShellBackend.describeEnvironment(cwd = cwd, workspaceMode = mode)
                            }
                        }
                        ExecutionBackendType.ANDROID_SHELL ->
                            androidShellBackend.describeEnvironment(cwd = cwd, workspaceMode = mode)
                    }
                }
                _environmentDescription.value = desc
            } catch (e: Exception) {
                Log.w(TAG, "Environment probe error: ${e.message}")
            } finally {
                _isProbingEnvironment.value = false
            }
        }
    }

    fun selectExecutionBackend(backendType: ExecutionBackendType) {
        personalizationStore.updateExecutionBackend(backendType)
        if (backendType == ExecutionBackendType.TERMUX &&
            personalizationStore.config.value.workspaceMode == WorkspaceMode.TERMUX_HOME
        ) {
            _currentWorkingDir.value = personalizationStore.config.value.termuxHomeModeCwd.ifBlank {
                TermuxBackend.TERMUX_HOME_PATH
            }
        }
        refreshExecutionEnvironment()
    }

    fun selectWorkspaceMode(mode: WorkspaceMode) {
        val currentCfg = personalizationStore.config.value
        val previousMode = currentCfg.workspaceMode
        if (_currentWorkingDir.value.isNotBlank()) {
            personalizationStore.updateModeCwd(previousMode, _currentWorkingDir.value)
        }
        personalizationStore.updateWorkspaceMode(mode)
        val slug = _activeProjectSlug.value
        if (mode == WorkspaceMode.TERMUX_HOME) {
            _activeProjectStorageLocation.value = "termux_home"
            _currentWorkingDir.value = if (slug.isNotBlank()) {
                WorkspacePaths.termuxHomeProjectPath(slug)
            } else {
                personalizationStore.config.value.termuxHomeModeCwd.ifBlank { TermuxBackend.TERMUX_HOME_PATH }
            }
        } else {
            _activeProjectStorageLocation.value = "shared"
            _currentWorkingDir.value = if (slug.isNotBlank()) {
                WorkspacePaths.sharedProjectPath(slug)
            } else {
                personalizationStore.config.value.sharedModeCwd.ifBlank { shellBridge.currentCwd }
            }
        }
        refreshExecutionEnvironment()
    }

    fun updateMirrorConfig(
        mirrorGitFolder: Boolean = personalizationStore.config.value.mirrorGitFolder,
        mirrorBuildOutputs: Boolean = personalizationStore.config.value.mirrorBuildOutputs,
        mirrorDeletions: Boolean = personalizationStore.config.value.mirrorDeletions,
        customExcludes: List<String> = personalizationStore.config.value.mirrorCustomExcludes
    ) {
        personalizationStore.updateMirrorConfig(
            mirrorGitFolder = mirrorGitFolder,
            mirrorBuildOutputs = mirrorBuildOutputs,
            mirrorDeletions = mirrorDeletions,
            customExcludes = customExcludes
        )
    }

    fun syncProjectMirrorNow() {
        val slug = _activeProjectSlug.value.ifBlank {
            File(_currentWorkingDir.value).name.takeIf {
                it.isNotBlank() && it != "Nexus" && it != "nexus_workspace"
            }.orEmpty()
        }
        if (slug.isBlank()) {
            _uiBannerMessage.value = "Bind a project first to mirror files between Termux home and shared storage."
            return
        }
        viewModelScope.launch {
            val res = projectMirrorManager.syncProjectToShared(slug)
            when (res) {
                is MirrorSyncResult.Success -> {
                    _uiBannerMessage.value = "Mirrored '${res.projectSlug}' to shared storage (${res.filesCopied} synced)."
                }
                is MirrorSyncResult.ConflictsDetected -> {
                    appendEntryOrLocal(
                        sessionId = activeEffectiveSessionId(),
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Changes found in the shared folder (${res.conflicts.size} file(s): ${res.conflicts.joinToString { it.relativePath }}). Choose how to resolve before mirroring.",
                        noticeKind = "mirror_conflict",
                        cwd = _currentWorkingDir.value
                    )
                }
                is MirrorSyncResult.Failed -> {
                    appendEntryOrLocal(
                        sessionId = activeEffectiveSessionId(),
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Mirror failed (${res.typedError.title}): ${res.typedError.message}",
                        noticeKind = "error",
                        cwd = _currentWorkingDir.value
                    )
                }
            }
        }
    }

    fun importFromSharedToTermuxHome() {
        val slug = _activeProjectSlug.value.ifBlank {
            File(_currentWorkingDir.value).name.takeIf {
                it.isNotBlank() && it != "Nexus" && it != "nexus_workspace"
            }.orEmpty()
        }
        if (slug.isBlank()) return
        viewModelScope.launch {
            val res = projectMirrorManager.importFromSharedToTermuxHome(slug)
            res.onSuccess { backupDir ->
                _uiBannerMessage.value = "Imported shared changes into Termux home (backup saved to $backupDir)."
            }.onFailure { err ->
                _uiBannerMessage.value = "Import failed: ${err.localizedMessage ?: "error"}"
            }
        }
    }

    fun resolveMirrorConflict(conflict: MirrorConflictItem, choice: MirrorConflictResolutionChoice) {
        viewModelScope.launch {
            projectMirrorManager.resolveConflict(conflict, choice)
        }
    }

    /**
     * Moves the active project from shared storage (`/storage/emulated/0/Nexus/<slug>`) to
     * Termux home (`$HOME/nexus_workspace/<slug>`) via the standard approval gate (Amendment #5):
     * - Copies project sources **without `node_modules` or `.venv`**
     * - Cleans any partial `node_modules` in the shared copy
     * - Switches the session to Termux home and runs a fresh install
     */
    fun proposeMoveProjectToTermuxHome(
        failedCommand: String = "",
        cleanSharedNodeModules: Boolean = true
    ) {
        val slug = _activeProjectSlug.value.ifBlank {
            File(_currentWorkingDir.value).name.takeIf { it.isNotBlank() && it != "Nexus" } ?: "project"
        }.let { WorkspacePaths.sanitizeSlug(it) }
        val sharedDir = WorkspacePaths.sharedProjectPath(slug)
        val termuxDir = WorkspacePaths.termuxHomeProjectPath(slug)
        val cleanSharedStep = if (cleanSharedNodeModules) " && rm -rf '$sharedDir/node_modules'" else ""
        val retryCmd = failedCommand.trim().ifBlank { "npm install" }
        val moveScript = "mkdir -p '$termuxDir' && " +
            "(if command -v rsync >/dev/null 2>&1; then " +
            "rsync -rt --no-perms --no-owner --no-group --omit-dir-times --exclude='node_modules' --exclude='.venv' '$sharedDir/' '$termuxDir/'; " +
            "else cp -r '$sharedDir/.' '$termuxDir/' && rm -rf '$termuxDir/node_modules' '$termuxDir/.venv'; fi)" +
            "$cleanSharedStep && cd '$termuxDir' && $retryCmd"
        _activeProjectSlug.value = slug
        _activeProjectStorageLocation.value = "termux_home"
        personalizationStore.updateWorkspaceMode(WorkspaceMode.TERMUX_HOME)
        personalizationStore.updateModeCwd(WorkspaceMode.TERMUX_HOME, termuxDir)
        _currentWorkingDir.value = termuxDir
        executeDirectShellCommand(moveScript)
    }

    fun keepProjectInSharedStorage() {
        val slug = _activeProjectSlug.value.ifBlank {
            File(_currentWorkingDir.value).name
        }
        if (slug.isNotBlank()) {
            personalizationStore.markProjectKeepInSharedStorage(slug)
            _uiBannerMessage.value = "Kept '$slug' in shared storage. Symlink move prompt suppressed for this project."
        }
    }

    fun runExecutionBackendConnectionTest() {
        val tb = termuxBackend
        viewModelScope.launch {
            _isProbingEnvironment.value = true
            _connectionTestResult.value = "Running `echo ok` handshake…"
            try {
                val msg = if (tb != null && personalizationStore.config.value.executionBackendType == ExecutionBackendType.TERMUX) {
                    val (ok, detail) = tb.testConnection()
                    if (ok) "Connected to Termux: $detail" else "Termux test failed: $detail"
                } else {
                    val active = resolveActiveExecutionBackend()
                    val stdoutBuf = StringBuilder()
                    var code = -1
                    active.exec("echo ok", _currentWorkingDir.value, emptyMap(), 10L).collect { ev ->
                        when (ev) {
                            is ExecEvent.StdoutChunk -> stdoutBuf.append(ev.text)
                            is ExecEvent.StderrChunk -> stdoutBuf.append(ev.text)
                            is ExecEvent.Completed -> code = ev.exitCode
                        }
                    }
                    if (code == 0) {
                        "Connected to ${active.backendType.label} (`echo ok` -> ${stdoutBuf.toString().trim()})"
                    } else {
                        "Test failed (exit $code): ${stdoutBuf.toString().trim()}"
                    }
                }
                _connectionTestResult.value = msg
                refreshExecutionEnvironment()
            } finally {
                _isProbingEnvironment.value = false
            }
        }
    }

    /**
     * One-click "Install" action in EnvironmentSetupScreen:
     * Proposes `pkg update -y && pkg install -y nodejs git python` through the standard approval gate.
     */
    fun proposeInstallToolchain() {
        executeDirectShellCommand(TermuxBackend.INSTALL_TOOLCHAIN_COMMAND)
    }

    fun runDiagnostics(forceRefresh: Boolean = false) {
        val diag = termuxDiagnostics ?: return
        viewModelScope.launch {
            _isProbingEnvironment.value = true
            try {
                val rep = diag.runDiagnostics(
                    workspaceCwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd },
                    workspaceMode = personalizationStore.config.value.workspaceMode,
                    forceRefresh = forceRefresh
                )
                _diagnosticsReport.value = rep
                refreshExecutionEnvironment()
                refreshProjectGuide()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to run diagnostics", e)
            } finally {
                _isProbingEnvironment.value = false
            }
        }
    }

    fun runEndToEndSelfTest() {
        val diag = termuxDiagnostics ?: return
        if (_isRunningSelfTest.value) return
        viewModelScope.launch {
            _isRunningSelfTest.value = true
            try {
                val report = diag.runEndToEndSelfTest(
                    workspaceCwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
                ) { intermediateSteps ->
                    _selfTestReport.value = SelfTestReport(
                        passed = intermediateSteps.all { it.passed },
                        totalDurationMs = intermediateSteps.sumOf { it.durationMs },
                        steps = intermediateSteps
                    )
                }
                _selfTestReport.value = report
            } catch (e: Exception) {
                Log.w(TAG, "Self test fault", e)
            } finally {
                _isRunningSelfTest.value = false
            }
        }
    }

    fun retryPackagesIndividually() {
        executeDirectShellCommand("pkg update -y && pkg install -y nodejs && pkg install -y git && pkg install -y python")
    }

    fun refreshProjectGuide() {
        val diag = termuxDiagnostics ?: return
        val tools = _diagnosticsReport.value?.detectedTools.orEmpty()
        val guide = diag.inspectWorkspaceProjectGuide(
            workspaceCwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd },
            detectedTools = tools,
            verifiedRecipeIds = _verifiedRecipeIds.value
        )
        _projectGuide.value = guide
    }

    fun verifyProjectRecipe(recipeId: String, verifyCommand: String) {
        viewModelScope.launch {
            val cwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
            val backend = resolveActiveExecutionBackend()
            var exit = 1
            backend.exec(cmd = verifyCommand, cwd = cwd, timeoutSec = 20L).collect { ev ->
                if (ev is ExecEvent.Completed) exit = ev.exitCode
            }
            if (exit == 0) {
                if (recipeId.isNotBlank()) {
                    _verifiedRecipeIds.update { it + recipeId }
                }
                refreshProjectGuide()
                _uiBannerMessage.value = "Recipe verified successfully."
            } else {
                _uiBannerMessage.value = "Verification failed (exit $exit)."
            }
        }
    }

    fun toggleKeepTermuxAwake(enabled: Boolean) {
        viewModelScope.launch {
            wizardStore?.setKeepTermuxAwake(enabled)
            _wizardState.update { it.copy(keepTermuxAwakeWhileJobsRun = enabled) }
        }
    }

    fun markWizardComplete() {
        viewModelScope.launch {
            wizardStore?.markCompleted(true)
            _wizardState.update { it.copy(completed = true, skipped = false) }
        }
    }

    fun skipWizard() {
        viewModelScope.launch {
            wizardStore?.markSkipped(true)
            _wizardState.update { it.copy(skipped = true) }
        }
    }

    fun executeFallbackInLimitedShell(command: String) {
        if (!FallbackPolicy.canOfferLimitedShellFallback(command)) {
            viewModelScope.launch {
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Fallback rejected: `$command` is not a safe read-only command.",
                    noticeKind = "error",
                    cwd = _currentWorkingDir.value
                )
            }
            return
        }
        executeDirectShellCommand(command)
    }

    /**
     * Starts a long-running background job (e.g. `npm run dev`, `python -m http.server`)
     * using [JobManager] detached under `setsid bash -lc`.
     */
    fun startBackgroundJob(command: String) {
        val cleanCmd = command.trim()
        if (cleanCmd.isBlank()) return
        val cwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
        viewModelScope.launch {
            val outcome = jobManager.startJob(cleanCmd, cwd)
            if (!outcome.success) {
                val errText = outcome.error?.summaryMessage ?: outcome.message
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Failed to start background job: $errText",
                    noticeKind = "error",
                    cwd = cwd
                )
            }
        }
    }

    fun stopBackgroundJob(jobId: String) {
        viewModelScope.launch {
            jobManager.stopJob(jobId)
        }
    }

    fun stopAllBackgroundJobs() {
        viewModelScope.launch {
            jobManager.stopAllJobs()
        }
    }

    fun stopAllBackgroundJobsOnClose() {
        viewModelScope.launch {
            jobManager.stopAllJobs()
        }
    }

    fun restartBackgroundJob(jobId: String) {
        viewModelScope.launch {
            jobManager.restartJob(jobId)
        }
    }

    fun pollJobs() {
        viewModelScope.launch {
            jobManager.pollAllRunningJobs()
        }
    }

    fun dismissReconciledBanner() {
        jobManager.dismissReconciledBanner()
    }

    override fun onCleared() {
        stopAllBackgroundJobsOnClose()
        super.onCleared()
    }

    /**
     * Executes a shell command on the active [ExecutionBackend] (Termux, RemoteBridge, or AndroidShell),
     * collecting streaming stdout/stderr chunks, redacting secrets, and updating the working directory.
     */
    private suspend fun executeOnActiveBackend(
        command: String,
        requestedTimeoutSec: Long
    ): ShellExecResult {
        val backend = resolveActiveExecutionBackend()
        val effectiveTimeoutSec = ExecutionTimeouts.resolveTimeoutSec(
            cmd = command,
            requestedTimeoutSec = requestedTimeoutSec
        )
        val initialCwd = if (
            backend.backendType == ExecutionBackendType.TERMUX &&
            personalizationStore.config.value.workspaceMode == WorkspaceMode.TERMUX_HOME &&
            !_currentWorkingDir.value.startsWith("/data/data/com.termux")
        ) {
            TermuxBackend.TERMUX_HOME_PATH
        } else {
            _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
        }

        // Fast path when using AndroidShellBackend directly
        if (backend.backendType == ExecutionBackendType.ANDROID_SHELL) {
            val bridgeUrl = if (isGuestMode) "" else _remoteBridgeUrl.value.ifBlank { personalizationStore.config.value.remoteBridgeUrl }
            return shellBridge.executeCommand(
                command = command,
                timeoutSec = effectiveTimeoutSec,
                extraSecrets = userApiKeyStore.allSavedSecretsForRedaction(),
                remoteBridgeUrl = bridgeUrl
            )
        }

        if (backend is TermuxBackend && circuitBreaker.isUnhealthy) {
            if (circuitBreaker.shouldAllowAutoRecheckBeforeAgentRun()) {
                circuitBreaker.resetOnUserAction()
            } else {
                val unavailableMsg = circuitBreaker.formatStructuredUnavailableToolResult()
                _uiBannerMessage.value = "Termux environment unavailable: ${circuitBreaker.lastUnhealthyReason}"
                return ShellExecResult(
                    stdout = "",
                    stderr = unavailableMsg,
                    exitCode = 1,
                    cwd = initialCwd,
                    timeoutSec = effectiveTimeoutSec,
                    redactions = 0,
                    durationMs = 0L
                )
            }
        }

        if (backend is TermuxBackend) {
            val preflightKey = "${backend.backendType.id}:$initialCwd"
            if (preflightKey !in preflightVerifiedPaths) {
                val (probeOk, probeErr) = backend.verifyWorkspacePreFlight(initialCwd)
                if (probeOk) {
                    preflightVerifiedPaths.add(preflightKey)
                } else {
                    val unreachableMsg = probeErr ?: "Workspace '$initialCwd' is not writable from Termux."
                    appendEntryOrLocal(
                        sessionId = activeEffectiveSessionId(),
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Working directory unreachable (`$initialCwd`): $unreachableMsg",
                        noticeKind = "working_dir_unreachable",
                        command = command,
                        cwd = initialCwd
                    )
                    return ShellExecResult(
                        stdout = "",
                        stderr = unreachableMsg,
                        exitCode = 1,
                        cwd = initialCwd,
                        timeoutSec = effectiveTimeoutSec,
                        redactions = 0,
                        durationMs = 0L
                    )
                }
            }
        }

        val isInstallOrBuild = ExecutionTimeouts.isPackageInstallOrBuild(command)
        val isOnSharedStorage = initialCwd.startsWith("/storage/emulated/0")
        var progressJob: Job? = null
        if (isInstallOrBuild) {
            val startMs = System.currentTimeMillis()
            val storageNote = if (isOnSharedStorage) " · Shared storage is slower than Termux home" else ""
            _installProgressBanner.value = "Installing packages (0s elapsed)$storageNote…"
            progressJob = viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1_000L)
                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(1L)
                    _installProgressBanner.value = "Installing packages (${elapsedSec}s elapsed)$storageNote…"
                }
            }
        }

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()
        var exitCode = 0
        var durationMs = 0L

        try {
            backend.exec(
                cmd = command,
                cwd = initialCwd,
                env = emptyMap(),
                timeoutSec = effectiveTimeoutSec
            ).collect { event ->
                when (event) {
                    is ExecEvent.StdoutChunk -> stdoutBuilder.append(event.text)
                    is ExecEvent.StderrChunk -> stderrBuilder.append(event.text)
                    is ExecEvent.Completed -> {
                        exitCode = event.exitCode
                        durationMs = event.durationMs
                    }
                }
            }

            // Amendment #5: Clean bin-links single retry on shared storage
            val firstCombined = "${stdoutBuilder}\n${stderrBuilder}"
            if (exitCode != 0 && isOnSharedStorage &&
                WorkspaceCommandAdapter.isNpmSymlinkInstallFailure(command, firstCombined)
            ) {
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Shared storage does not support symlinks (`EPERM`/`ENOSYS`). Cleaning partial `node_modules` and retrying once with `npm_config_bin_links=false`…",
                    noticeKind = "info",
                    command = command,
                    cwd = initialCwd
                )
                // Clean partial node_modules before the bin-links retry
                backend.exec(
                    cmd = "rm -rf node_modules",
                    cwd = initialCwd,
                    env = emptyMap(),
                    timeoutSec = 60L
                ).collect {}

                stdoutBuilder.clear()
                stderrBuilder.clear()
                backend.exec(
                    cmd = command,
                    cwd = initialCwd,
                    env = mapOf("npm_config_bin_links" to "false"),
                    timeoutSec = effectiveTimeoutSec
                ).collect { event ->
                    when (event) {
                        is ExecEvent.StdoutChunk -> stdoutBuilder.append(event.text)
                        is ExecEvent.StderrChunk -> stderrBuilder.append(event.text)
                        is ExecEvent.Completed -> {
                            exitCode = event.exitCode
                            durationMs += event.durationMs
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            backend.cancel()
            throw e
        } finally {
            progressJob?.cancel()
            _installProgressBanner.value = null
        }

        val finalCwd = if (backend is TermuxBackend) {
            backend.lastKnownCwd.ifBlank { initialCwd }
        } else {
            initialCwd
        }
        if (finalCwd != initialCwd) {
            _currentWorkingDir.value = finalCwd
            personalizationStore.addRecentWorkspace(finalCwd)
        }

        val extraSecrets = userApiKeyStore.allSavedSecretsForRedaction()
        val redactedOut = NexusToolAndRedactor.redactSecrets(stdoutBuilder.toString(), extraSecrets)
        val redactedErr = NexusToolAndRedactor.redactSecrets(stderrBuilder.toString(), extraSecrets)
        val totalRedactions = redactedOut.count + redactedErr.count
        val finalCombinedOutput = "${redactedOut.text}\n${redactedErr.text}"

        // Amendment #5: Trigger "Move to Termux home?" card ONLY on symlink-specific or noexec failures
        val activeSlug = _activeProjectSlug.value.ifBlank { File(finalCwd).name }
        if (exitCode != 0 && isOnSharedStorage &&
            activeSlug !in personalizationStore.config.value.keepInSharedStorageProjects &&
            WorkspaceCommandAdapter.shouldOfferMoveToTermuxHomeForSymlinkOrNoexec(
                cmd = command,
                combinedOutput = finalCombinedOutput,
                exitCode = exitCode,
                cwd = finalCwd
            )
        ) {
            appendEntryOrLocal(
                sessionId = activeEffectiveSessionId(),
                type = "notice",
                sequence = nextSequence(),
                text = "This project needs symlinks. Move it to Termux home?",
                noticeKind = "symlink_move_offer",
                command = command,
                cwd = finalCwd
            )
        }

        // Task 5: Secret file scanner warning once per shared project
        if (finalCwd.startsWith("/storage/emulated/0")) {
            val secretWarn = WorkspaceSecretScanner.scanForNewSecretFilesWarning(File(finalCwd))
            if (!secretWarn.isNullOrBlank()) {
                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "notice",
                    sequence = nextSequence(),
                    text = secretWarn,
                    noticeKind = "warn",
                    cwd = finalCwd
                )
            }
        }

        // Task 4 & Amendment #4: Trigger trailing debounced one-way mirror when working in Termux home
        if (finalCwd.startsWith(WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT) && activeSlug.isNotBlank()) {
            projectMirrorManager.scheduleDebouncedSync(activeSlug, debounceMs = 3_000L)
        }

        if (backend is TermuxBackend) {
            if (exitCode == 0) {
                circuitBreaker.recordSuccess()
            } else {
                val errText = redactedErr.text.lowercase()
                val isInfra = exitCode == 124 ||
                    errText.contains("com.termux") ||
                    errText.contains("not installed") ||
                    errText.contains("not granted") ||
                    errText.contains("unreachable") ||
                    errText.contains("service unavailable")
                if (isInfra) {
                    val reason = redactedErr.text.lines().lastOrNull { it.isNotBlank() }
                        ?: "Termux command failed with exit $exitCode"
                    val healthState = circuitBreaker.recordInfrastructureFailure(reason)
                    if (healthState == BackendHealthState.UNHEALTHY) {
                        _uiBannerMessage.value = "Termux backend marked UNHEALTHY (3 consecutive failures). Check Termux or run Diagnostics."
                    }
                }
            }
        }

        return ShellExecResult(
            stdout = redactedOut.text,
            stderr = redactedErr.text,
            exitCode = exitCode,
            cwd = finalCwd,
            timeoutSec = effectiveTimeoutSec,
            redactions = totalRedactions,
            durationMs = durationMs
        )
    }

    private fun observeSettingsAndBootstrapSession() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.observeUserSettings()
                .catch { e ->
                    Log.e(TAG, "Failed to observe user settings", e)
                }
                .collect { remoteSettings ->
                    if (remoteSettings != null) {
                        if (!userApiKeyStore.hasPersistedModelSelection()) {
                            userApiKeyStore.persistSelectedProviderAndModel(
                                provider = remoteSettings.provider,
                                modelId = remoteSettings.selectedModel
                            )
                        }
                        personalizationStore.applyRemoteSyncedSettings(
                            displayName = remoteSettings.displayName.takeIf { it.isNotBlank() },
                            avatarDataUri = remoteSettings.avatarDataUri.takeIf { it.isNotBlank() },
                            fontSize = remoteSettings.fontSize.takeIf { it.isNotBlank() },
                            responseStyle = remoteSettings.responseStyle.takeIf { it.isNotBlank() },
                            personaPresetId = remoteSettings.personaPreset.takeIf { it.isNotBlank() },
                            customPersonasJson = remoteSettings.customPersonasJson.takeIf { it.isNotBlank() },
                            aboutUser = remoteSettings.aboutUser.takeIf { it.isNotBlank() },
                            responsePreference = remoteSettings.responsePreference.takeIf { it.isNotBlank() },
                            enableForNewChats = remoteSettings.enableForNewChats,
                            skillsJson = remoteSettings.skillsJson.takeIf { it.isNotBlank() },
                            skillsTokenBudget = remoteSettings.skillsTokenBudget,
                            recentWorkspaces = remoteSettings.recentWorkspaces
                        )
                        val authoritativeProvider = selectedProvider.value
                        val authoritativeModel = selectedModelId.value
                        _settings.value = remoteSettings.copy(
                            provider = authoritativeProvider,
                            selectedModel = authoritativeModel
                        )
                        if (remoteSettings.activeSessionId.isNotBlank() && !_isRunning.value && !_isTemporaryChat.value) {
                            _activeSessionId.value = remoteSettings.activeSessionId
                        }
                    } else {
                        val pCfg = personalizationStore.config.value
                        val initial = NexusUserSettings(
                            userId = currentUserId,
                            provider = selectedProvider.value,
                            selectedModel = selectedModelId.value,
                            activeSessionId = "default_session",
                            inputHistory = listOf("sh ./system-check.sh", "ls -la", "cat logs/agent-audit.log"),
                            displayName = pCfg.displayName,
                            avatarDataUri = pCfg.avatarDataUri,
                            fontSize = pCfg.fontSize.id,
                            responseStyle = pCfg.responseStyle.id,
                            personaPreset = pCfg.personaPresetId,
                            customPersonasJson = personalizationStore.serializeCustomPersonasForCloud(),
                            aboutUser = pCfg.aboutUser,
                            responsePreference = pCfg.responsePreference,
                            enableForNewChats = pCfg.enableForNewChats,
                            skillsJson = personalizationStore.serializeSkillsForCloud(),
                            skillsTokenBudget = pCfg.skillsTokenBudget.toLong(),
                            recentWorkspaces = pCfg.recentWorkspaces
                        )
                        _settings.value = initial
                        repo.ensureOrSaveUserSettings(initial)
                    }
                }
        }

        viewModelScope.launch {
            sessionsState.collect { state ->
                if (state is UiState.Success && state.data.isEmpty()) {
                    val createRes = repo.createSession(
                        title = "New chat",
                        cwd = shellBridge.currentCwd,
                        provider = NexusToolAndRedactor.providerDisplayLabel(selectedProvider.value),
                        model = selectedModelId.value,
                        sessionId = "default_session"
                    )
                    if (createRes.isSuccess) {
                        _activeSessionId.value = "default_session"
                        _currentWorkingDir.value = shellBridge.currentCwd
                    }
                } else if (state is UiState.Success && state.data.isNotEmpty() && !_isTemporaryChat.value) {
                    val currentId = _activeSessionId.value
                    val activeSess = state.data.find { it.id == currentId } ?: state.data.first().also {
                        _activeSessionId.value = it.id
                    }
                    // Amendment #7: Migrate existing sessions without a projectSlug to Unassigned;
                    // resolve project path per device from projectSlug + storageLocation.
                    val sessSlug = activeSess.projectSlug.trim()
                    val sessStorage = activeSess.storageLocation.ifBlank { "shared" }
                    _activeProjectSlug.value = sessSlug
                    _activeProjectStorageLocation.value = sessStorage
                    if (!_isRunning.value) {
                        if (sessSlug.isNotBlank()) {
                            val resolvedDevicePath = activeSess.resolvedProjectPath()
                            if (sessStorage == "shared") {
                                val folderExists = File(resolvedDevicePath).exists()
                                _isBoundProjectMissing.value = !folderExists
                                if (folderExists) {
                                    shellBridge.setWorkingDirectory(resolvedDevicePath, createIfMissing = false)
                                        .onSuccess { resolved -> _currentWorkingDir.value = resolved }
                                } else {
                                    _currentWorkingDir.value = resolvedDevicePath
                                }
                            } else {
                                _isBoundProjectMissing.value = false
                                _currentWorkingDir.value = resolvedDevicePath
                            }
                        } else {
                            _isBoundProjectMissing.value = false
                            if (activeSess.cwd.isNotBlank() && activeSess.cwd != "/") {
                                shellBridge.setWorkingDirectory(activeSess.cwd, createIfMissing = false)
                                    .onSuccess { resolved -> _currentWorkingDir.value = resolved }
                            }
                        }
                    }
                }
            }
        }
    }

    // Temporary Chat Mode controls (Amendment #9: Scratch folder in Termux home deleted on exit)
    fun toggleTemporaryChat() {
        if (_isRunning.value) stopRunningAgent()
        val next = !_isTemporaryChat.value
        if (next) {
            val scratchId = "temp_${System.currentTimeMillis()}"
            val scratchPath = WorkspacePaths.scratchDirectory(scratchId)
            activeTempScratchPath = scratchPath
            _isTemporaryChat.value = true
            _temporaryEntries.value = emptyList()
            _temporarySession.value = NexusSession(
                id = TEMP_SESSION_ID,
                userId = currentUserId,
                title = "Temporary chat",
                status = "idle",
                cwd = scratchPath,
                commandsCount = 0L,
                redactionsCount = 0L,
                provider = NexusToolAndRedactor.providerDisplayLabel(userApiKeyStore.selectedProvider.value),
                model = userApiKeyStore.selectedModelId.value,
                isTemporary = true
            )
            _currentWorkingDir.value = scratchPath
            viewModelScope.launch {
                val escaped = scratchPath.replace("'", "'\\''")
                resolveActiveExecutionBackend().exec("mkdir -p '$escaped'", WorkspacePaths.TERMUX_HOME_DIR, emptyMap(), 10L).collect {}
            }
        } else {
            exitTemporaryChat()
        }
    }

    fun startTemporaryChat() {
        if (!_isTemporaryChat.value) {
            toggleTemporaryChat()
        } else {
            if (_isRunning.value) stopRunningAgent()
            _temporaryEntries.value = emptyList()
        }
    }

    fun exitTemporaryChat() {
        if (_isTemporaryChat.value) {
            if (_isRunning.value) stopRunningAgent()
            val scratchToClean = activeTempScratchPath
            activeTempScratchPath = null
            _isTemporaryChat.value = false
            _temporaryEntries.value = emptyList()
            // Restore the active session's bound project path or shared root
            val curSess = currentSessionModel()
            _currentWorkingDir.value = if (curSess != null && !curSess.isProjectUnassigned) {
                curSess.resolvedProjectPath()
            } else {
                shellBridge.currentCwd
            }
            if (!scratchToClean.isNullOrBlank() && scratchToClean.startsWith(WorkspacePaths.TERMUX_SCRATCH_ROOT)) {
                viewModelScope.launch {
                    jobManager.stopJobsInDirectory(scratchToClean)
                    val escaped = scratchToClean.replace("'", "'\\''")
                    resolveActiveExecutionBackend().exec("rm -rf '$escaped'", WorkspacePaths.TERMUX_HOME_DIR, emptyMap(), 10L).collect {}
                    runCatching { File(scratchToClean).deleteRecursively() }
                }
            }
        }
    }

    // Session Pin / Rename / Delete with Undo
    fun togglePinSession(sessionId: String) {
        _pinnedSessionIds.update { current ->
            if (sessionId in current) current - sessionId else current + sessionId
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val clean = newTitle.trim().ifBlank { "Untitled chat" }.take(120)
        if (isGuestMode) {
            _guestSessions.update { list ->
                list.map { if (it.id == sessionId) it.copy(title = clean) else it }
            }
            return
        }
        viewModelScope.launch {
            repository?.renameSession(sessionId, clean)
        }
    }

    fun deleteSessionWithUndo(sessionId: String) {
        if (_isRunning.value && _activeSessionId.value == sessionId) {
            stopRunningAgent()
        }
        if (isGuestMode || repository == null) {
            val currentSessions = _guestSessions.value
            val targetSession = currentSessions.find { it.id == sessionId } ?: currentSessions.firstOrNull()
            val currentEntries = _guestEntries.value
            if (targetSession != null) {
                lastDeletedBackup = DeletedSessionBackup(targetSession, currentEntries)
                _liveNotificationBanner.value = LiveNotificationEvent(
                    kind = "deleted_undo",
                    title = "Deleted \"${targetSession.title.take(28)}\"",
                    message = "Chat removed. Tap Undo to restore."
                )
            }
            clearActiveSession()
            return
        }
        viewModelScope.launch {
            val allSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
            val targetSession = allSessions.find { it.id == sessionId }
            val result = repository.deleteSessionWithBackup(sessionId)
            result.onSuccess { (deletedSess, deletedEntries) ->
                val backupSess = deletedSess ?: targetSession
                if (backupSess != null) {
                    lastDeletedBackup = DeletedSessionBackup(backupSess, deletedEntries)
                    _liveNotificationBanner.value = LiveNotificationEvent(
                        kind = "deleted_undo",
                        title = "Deleted \"${backupSess.title.take(28)}\"",
                        message = "Chat removed from cloud history."
                    )
                }
                if (_activeSessionId.value == sessionId) {
                    val remaining = allSessions.filter { it.id != sessionId }
                    if (remaining.isNotEmpty()) {
                        selectSession(remaining.first().id)
                    } else {
                        createNewSession("New chat")
                    }
                }
            }
        }
    }

    fun undoDeleteSession() {
        val backup = lastDeletedBackup ?: return
        lastDeletedBackup = null
        if (_liveNotificationBanner.value?.kind == "deleted_undo") {
            _liveNotificationBanner.value = null
        }
        if (isGuestMode || repository == null) {
            _guestSessions.value = listOf(backup.session)
            _guestEntries.value = backup.entries
            _activeSessionId.value = backup.session.id
            return
        }
        viewModelScope.launch {
            val res = repository.restoreDeletedSession(backup.session, backup.entries)
            if (res.isSuccess) {
                _activeSessionId.value = backup.session.id
            }
        }
    }

    fun toggleMessageFeedback(entryId: String, feedbackKind: String) {
        _messageFeedback.update { current ->
            if (current[entryId] == feedbackKind) {
                current - entryId
            } else {
                current + (entryId to feedbackKind)
            }
        }
    }

    fun regenerateFromEntry(agentEntry: TranscriptEntry) {
        if (_isRunning.value) return
        val currentEntries = (entriesState.value as? UiState.Success)?.data.orEmpty()
        val idx = currentEntries.indexOfFirst { it.id == agentEntry.id }
        val precedingUserEntry = if (idx > 0) {
            currentEntries.subList(0, idx).lastOrNull { it.type == "user" }
        } else {
            currentEntries.lastOrNull { it.type == "user" }
        }
        if (precedingUserEntry != null && precedingUserEntry.text.isNotBlank()) {
            editAndResubmitPrompt(precedingUserEntry, precedingUserEntry.text)
        }
    }

    /**
     * Edits a submitted user prompt, cancels any active generation/steps/approvals first,
     * truncates all messages from [targetEntry] onwards, offers a 10-second Undo window,
     * deletes truncated Firestore documents in chunks of <= 450, and restarts execution
     * from [newPromptText].
     */
    fun editAndResubmitPrompt(targetEntry: TranscriptEntry, newPromptText: String) {
        val cleanNewPrompt = newPromptText.trim()
        if (cleanNewPrompt.isBlank()) return

        val activeProv = selectedProvider.value.lowercase().trim()
        val activeMod = selectedModelId.value.trim()
        val sessionId = activeEffectiveSessionId()

        // Guest-quota behavior (Amendment #3):
        // Editing and resubmitting consumes 1 guest cloud prompt turn; truncating earlier messages
        // does not refund used guest turns. Block before truncating if guest quota is exhausted.
        if (isGuestMode && activeProv != "local" && _guestDirectivesUsed.value >= GUEST_MAX_DIRECTIVES) {
            logPipelineStage("DROP_EDIT_AT_GUEST_QUOTA", "Edit & resubmit blocked at guest quota (${_guestDirectivesUsed.value}/$GUEST_MAX_DIRECTIVES)")
            _liveNotificationBanner.value = LiveNotificationEvent(
                kind = "guest_limit",
                title = "Limit reached, sign in ($GUEST_MAX_DIRECTIVES/$GUEST_MAX_DIRECTIVES cloud prompts)",
                message = "Sign in with Google to edit & resubmit with cloud models, or switch to a Local LLM."
            )
            return
        }

        // Verify API key before truncating so user history is never lost if key is missing
        if (activeProv != "local" && !userApiKeyStore.hasApiKeyFor(activeProv, activeMod)) {
            val provLabel = NexusToolAndRedactor.providerDisplayLabel(activeProv)
            _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                provider = activeProv,
                modelId = activeMod,
                providerDisplayName = provLabel,
                message = "Missing personal API key for $provLabel ($activeMod). Enter your key in Settings before resubmitting."
            )
            return
        }

        viewModelScope.launch {
            // 1. Cancel any running steps, active generation, or pending approvals first
            if (_isRunning.value || pendingApprovals.isNotEmpty() || activeAgentJob?.isActive == true) {
                val prevJob = activeAgentJob
                stopRunningAgent()
                runCatching { prevJob?.join() }
                _isRunning.value = false
                _streamingText.value = null
            }

            // 2. Identify entries to keep vs. truncate (targetEntry and all subsequent messages)
            val currentEntries = when {
                _isTemporaryChat.value || sessionId == TEMP_SESSION_ID -> _temporaryEntries.value
                isGuestMode || repository == null -> _guestEntries.value
                else -> (entriesState.value as? UiState.Success)?.data.orEmpty()
            }
            val targetIdx = currentEntries.indexOfFirst { it.id == targetEntry.id }
                .takeIf { it >= 0 }
                ?: currentEntries.indexOfFirst { it.sequence == targetEntry.sequence && it.type == "user" }

            if (targetIdx < 0) {
                submitDirective(overridePrompt = cleanNewPrompt)
                return@launch
            }

            val entriesToKeep = currentEntries.subList(0, targetIdx).toList()
            val entriesToRemove = currentEntries.subList(targetIdx, currentEntries.size).toList()
            val removedIds = entriesToRemove.map { it.id }.filter { it.isNotBlank() }

            // 3. Save 10-second Undo snapshot
            editUndoExpiryJob?.cancel()
            lastTruncatedEditBackup = TruncatedEditBackup(
                sessionId = sessionId,
                keptBaselineIds = entriesToKeep.map { it.id }.toSet(),
                minRemovedSequence = entriesToRemove.minOfOrNull { it.sequence } ?: targetEntry.sequence,
                removedEntries = entriesToRemove,
                guestQuotaIncremented = isGuestMode && activeProv != "local"
            )

            // 4. Truncate locally and in Firestore (chunked WriteBatch <= 450 ops)
            when {
                _isTemporaryChat.value || sessionId == TEMP_SESSION_ID -> {
                    _temporaryEntries.value = entriesToKeep
                }
                isGuestMode || repository == null -> {
                    _guestEntries.value = entriesToKeep
                }
                else -> {
                    _optimisticallyTruncatedIds.update { it + removedIds }
                    repository.truncateEntriesBatch(sessionId, removedIds)
                }
            }

            // 5. Show 10-second Undo snackbar warning that earlier disk side-effects remain
            val laterCount = (entriesToRemove.size - 1).coerceAtLeast(0)
            val summaryTitle = if (laterCount > 0) {
                "Resubmitted · $laterCount later message${if (laterCount == 1) "" else "s"} removed"
            } else {
                "Prompt edited & resubmitted"
            }
            _liveNotificationBanner.value = LiveNotificationEvent(
                kind = "edit_truncate_undo",
                title = summaryTitle,
                message = "Earlier command effects on disk remain. Tap Undo within 10s to restore."
            )
            editUndoExpiryJob = viewModelScope.launch {
                kotlinx.coroutines.delay(10_000L)
                lastTruncatedEditBackup = null
                _optimisticallyTruncatedIds.value = emptySet()
                if (_liveNotificationBanner.value?.kind == "edit_truncate_undo") {
                    _liveNotificationBanner.value = null
                }
            }

            // 6. Restart execution from the edited prompt
            submitDirective(overridePrompt = cleanNewPrompt)
        }
    }

    /**
     * Restores messages truncated by [editAndResubmitPrompt] if invoked within the 10-second Undo window.
     */
    fun undoEditAndResubmit() {
        val backup = lastTruncatedEditBackup ?: return
        lastTruncatedEditBackup = null
        editUndoExpiryJob?.cancel()
        editUndoExpiryJob = null
        if (_liveNotificationBanner.value?.kind == "edit_truncate_undo") {
            _liveNotificationBanner.value = null
        }

        viewModelScope.launch {
            if (_isRunning.value || pendingApprovals.isNotEmpty() || activeAgentJob?.isActive == true) {
                val prevJob = activeAgentJob
                stopRunningAgent()
                runCatching { prevJob?.join() }
                _isRunning.value = false
                _streamingText.value = null
            }

            if (backup.guestQuotaIncremented) {
                _guestDirectivesUsed.update { (it - 1).coerceAtLeast(0) }
            }

            val removedIdsSet = backup.removedEntries.map { it.id }.toSet()
            _optimisticallyTruncatedIds.update { it - removedIdsSet }

            when {
                _isTemporaryChat.value || backup.sessionId == TEMP_SESSION_ID -> {
                    _temporaryEntries.update { current ->
                        (current.filter { it.id in backup.keptBaselineIds } + backup.removedEntries)
                            .sortedBy { it.sequence }
                    }
                }
                isGuestMode || repository == null -> {
                    _guestEntries.update { current ->
                        (current.filter { it.id in backup.keptBaselineIds } + backup.removedEntries)
                            .sortedBy { it.sequence }
                    }
                }
                else -> {
                    val currentSnapshot = (entriesState.value as? UiState.Success)?.data.orEmpty()
                    val newlyAddedIds = currentSnapshot
                        .map { it.id }
                        .filter { it.isNotBlank() && it !in backup.keptBaselineIds && it !in removedIdsSet }
                    if (newlyAddedIds.isNotEmpty()) {
                        _optimisticallyTruncatedIds.update { it + newlyAddedIds }
                        repository.truncateEntriesBatch(backup.sessionId, newlyAddedIds)
                    }
                    repository.restoreEntriesBatch(backup.sessionId, backup.removedEntries)
                    _optimisticallyTruncatedIds.update { it - newlyAddedIds }
                }
            }
        }
    }

    /**
     * Retries the last failed directive or direct shell command when the user taps the Retry button
     * on an error / timeout / step-limit / unloaded-model notice card (Fix #8).
     */
    fun retryLastFailedAction() {
        if (_isRunning.value) return
        viewModelScope.launch {
            if (selectedProvider.value == "local" && localLlmManager.isModelExplicitlyUnloaded) {
                val prefix = MultiPlatformAiEngine.buildCompactLocalSystemInstruction(shellBridge.currentCwd)
                localLlmManager.switchActiveLocalModel(selectedModelId.value, prefix)
            }
            val directCmd = lastFailedDirectCommand
            if (!directCmd.isNullOrBlank()) {
                lastFailedDirectCommand = null
                executeDirectShellCommand(directCmd)
                return@launch
            }
            val lastPrompt = lastSubmittedPrompt
                ?: (entriesState.value as? UiState.Success)?.data?.lastOrNull { it.type == "user" }?.text
            if (!lastPrompt.isNullOrBlank()) {
                submitDirective(overridePrompt = lastPrompt)
            }
        }
    }

    // Unified Storage Helpers: Route to Temporary Memory, Guest Memory, or Firestore
    private suspend fun appendEntryOrLocal(
        sessionId: String,
        type: String,
        sequence: Long,
        text: String = "",
        command: String = "",
        reason: String = "",
        commandStatus: String = "",
        isRisky: Boolean = false,
        timeoutSec: Long = 10L,
        cwd: String = "",
        exitCode: Long = 0L,
        output: String = "",
        redactions: Long = 0L,
        noticeKind: String = ""
    ): Result<String> {
        if (_isTemporaryChat.value || sessionId == TEMP_SESSION_ID) {
            val newId = "temp_entry_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val newEntry = TranscriptEntry(
                id = newId,
                userId = currentUserId,
                sessionId = TEMP_SESSION_ID,
                type = type,
                text = text,
                command = command,
                reason = reason,
                commandStatus = commandStatus,
                isRisky = isRisky,
                timeoutSec = timeoutSec,
                cwd = cwd,
                exitCode = exitCode,
                output = output,
                redactions = redactions,
                noticeKind = noticeKind,
                sequence = sequence
            )
            _temporaryEntries.update { it + newEntry }
            return Result.success(newId)
        }
        if (isGuestMode || repository == null) {
            val newId = "guest_entry_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val newEntry = TranscriptEntry(
                id = newId,
                userId = currentUserId,
                sessionId = sessionId,
                type = type,
                text = text,
                command = command,
                reason = reason,
                commandStatus = commandStatus,
                isRisky = isRisky,
                timeoutSec = timeoutSec,
                cwd = cwd,
                exitCode = exitCode,
                output = output,
                redactions = redactions,
                noticeKind = noticeKind,
                sequence = sequence
            )
            _guestEntries.update { it + newEntry }
            return Result.success(newId)
        }
        return repository.appendEntry(
            sessionId = sessionId,
            type = type,
            sequence = sequence,
            text = text,
            command = command,
            reason = reason,
            commandStatus = commandStatus,
            isRisky = isRisky,
            timeoutSec = timeoutSec,
            cwd = cwd,
            exitCode = exitCode,
            output = output,
            redactions = redactions,
            noticeKind = noticeKind
        )
    }

    private suspend fun updateEntryStatusOrLocal(
        sessionId: String,
        entryId: String,
        commandStatus: String
    ) {
        if (_isTemporaryChat.value || sessionId == TEMP_SESSION_ID) {
            _temporaryEntries.update { list ->
                list.map { if (it.id == entryId) it.copy(commandStatus = commandStatus) else it }
            }
            return
        }
        if (isGuestMode || repository == null) {
            _guestEntries.update { list ->
                list.map { if (it.id == entryId) it.copy(commandStatus = commandStatus) else it }
            }
            return
        }
        repository.updateEntryTextOrStatus(
            sessionId = sessionId,
            entryId = entryId,
            commandStatus = commandStatus
        )
    }

    private suspend fun saveUserSettingsOrLocal(updatedSettings: NexusUserSettings) {
        if (isGuestMode || _isTemporaryChat.value || repository == null) return
        repository.ensureOrSaveUserSettings(updatedSettings)
    }

    fun dismissLiveNotificationBanner() {
        _liveNotificationBanner.value = null
    }

    fun getDirectoryPresets(): List<DirectoryPreset> = shellBridge.getDirectoryPresets()

    fun listSubdirectories(path: String): List<File> = shellBridge.listSubdirectories(path)

    fun changeWorkingDirectory(targetPath: String, createIfMissing: Boolean = false): Result<String> {
        val result = shellBridge.setWorkingDirectory(targetPath, createIfMissing)
        result.onSuccess { resolvedPath ->
            _currentWorkingDir.value = resolvedPath
            personalizationStore.addRecentWorkspace(resolvedPath)
            syncPersonalizationToCloudSettings()
            viewModelScope.launch {
                val sessionId = activeEffectiveSessionId()
                syncCurrentSessionStats(status = _agentStatus.value)
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Working directory changed to: $resolvedPath",
                    noticeKind = "info",
                    cwd = resolvedPath
                )
            }
        }
        return result
    }

    /**
     * Direct shell mode (`sh >`) (Fix #5 & #6 & #8):
     * Bypasses the LLM and runs directly through:
     * 1. Working directory & permission validation (`shellBridge.validateWorkingDirectory`)
     * 2. Risk classifier (`ToolCallParser.classifyRisk`: LOW -> auto-run, MEDIUM/HIGH -> approval gate)
     * 3. Active `ExecutionBackend` (`TermuxBackend`, `RemoteBridgeBackend`, or `AndroidShellBackend`) with timeout
     * 4. Output card with exit code (and error card with Retry on timeout / bridge failure)
     */
    fun executeDirectShellCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isBlank() || _isRunning.value) return
        if (!_isTemporaryChat.value) {
            pushInputHistory(cmd)
        }
        _promptInput.value = ""
        lastFailedDirectCommand = cmd

        val sessionId = activeEffectiveSessionId()
        val cfg = _settings.value

        activeAgentJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                logPipelineStage("DIRECT_SHELL_START", "cmd='$cmd', cwd='${shellBridge.currentCwd}'")

                val activeBackend = resolveActiveExecutionBackend()
                if (activeBackend.backendType != ExecutionBackendType.TERMUX) {
                    // Validate cwd & permissions before running on AndroidShell
                    when (val cwdCheck = shellBridge.validateWorkingDirectory(shellBridge.currentCwd)) {
                        is CwdValidationResult.MissingAllFilesAccess -> {
                            logPipelineStage("DROP_AT_CWD_PERMISSION", cwdCheck.message)
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = cwdCheck.message,
                                noticeKind = "permission",
                                cwd = shellBridge.currentCwd
                            )
                            return@launch
                        }
                        is CwdValidationResult.InvalidOrNotWritable -> {
                            logPipelineStage("DROP_AT_CWD_VALIDATION", cwdCheck.message)
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = cwdCheck.message,
                                noticeKind = "error",
                                cwd = shellBridge.currentCwd
                            )
                            return@launch
                        }
                        is CwdValidationResult.Valid -> {
                            _currentWorkingDir.value = cwdCheck.resolvedPath
                        }
                    }
                }

                // Evaluate workspace root write protection & project jail (Amendments #1, #2, #8)
                val shouldCheckJail = enforceUnassignedProjectSlugFlow ||
                    _currentWorkingDir.value == WorkspacePaths.DEFAULT_SHARED_ROOT ||
                    _activeProjectSlug.value.isNotBlank()
                val jailDecision = if (shouldCheckJail) {
                    WorkspacePathValidator.evaluateCommandInWorkspace(
                        command = cmd,
                        currentCwd = _currentWorkingDir.value,
                        boundProjectSlug = _activeProjectSlug.value,
                        isTemporaryChat = _isTemporaryChat.value
                    )
                } else {
                    CommandJailDecision.Allowed
                }

                when (jailDecision) {
                    is CommandJailDecision.BlockedRootWrite -> {
                        if (_activeProjectSlug.value.isBlank() && !_isTemporaryChat.value) {
                            val proposedSlug = WorkspacePaths.proposeSlugFromPromptOrCommand(
                                prompt = lastSubmittedPrompt.orEmpty(),
                                command = cmd
                            )
                            val existingSlugs = _availableProjects.value.map { it.slug }.toSet()
                            val (collision, altSlug) = WorkspacePaths.resolveSlugCollision(proposedSlug, existingSlugs)
                            _pendingProjectProposal.value = PendingProjectCreationProposal(
                                proposedSlug = proposedSlug,
                                collisionExists = collision,
                                nextAvailableSlug = altSlug,
                                triggerCommand = cmd,
                                triggerPrompt = lastSubmittedPrompt.orEmpty()
                            )
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = "${jailDecision.message}\nProposing project folder: `/storage/emulated/0/Nexus/$proposedSlug`",
                                noticeKind = "project_slug_proposal",
                                command = cmd,
                                cwd = _currentWorkingDir.value
                            )
                        } else {
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = jailDecision.message,
                                noticeKind = "warn",
                                command = cmd,
                                cwd = _currentWorkingDir.value
                            )
                        }
                        return@launch
                    }
                    is CommandJailDecision.BlockedTraversalOrSymlinkEscape -> {
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = jailDecision.message,
                            noticeKind = "error",
                            command = cmd,
                            cwd = _currentWorkingDir.value
                        )
                        return@launch
                    }
                    else -> Unit
                }

                val riskLevel = ToolCallParser.classifyRisk(cmd, _currentWorkingDir.value)
                val isOutsideProjectApproval = jailDecision is CommandJailDecision.RequiresOutsideProjectApproval
                val isHighRisk = riskLevel == CommandRiskLevel.HIGH
                val requiresApproval = isOutsideProjectApproval ||
                    riskLevel == CommandRiskLevel.MEDIUM ||
                    riskLevel == CommandRiskLevel.HIGH
                val effectiveTimeout = ExecutionTimeouts.resolveTimeoutSec(cmd, cfg.timeoutSec)
                val approvalReason = if (jailDecision is CommandJailDecision.RequiresOutsideProjectApproval) {
                    jailDecision.reason
                } else {
                    "Direct shell command (sh >)"
                }
                logPipelineStage("DIRECT_SHELL_RISK_CHECK", "cmd='$cmd', risk=$riskLevel, requiresApproval=$requiresApproval")

                val cmdSeq = nextSequence()
                val createRes = appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "command",
                    sequence = cmdSeq,
                    command = cmd,
                    reason = approvalReason,
                    commandStatus = if (requiresApproval) "pending" else "approved",
                    isRisky = isHighRisk,
                    timeoutSec = effectiveTimeout,
                    cwd = _currentWorkingDir.value
                )
                val entryId = createRes.getOrNull() ?: return@launch

                if (requiresApproval) {
                    syncCurrentSessionStats(status = "wait")
                    notificationHelper.notifyCommandApprovalNeeded(
                        entryId = entryId,
                        command = cmd,
                        reason = "Direct shell command ($riskLevel risk)",
                        isRisky = isHighRisk
                    )
                    _liveNotificationBanner.value = LiveNotificationEvent(
                        kind = "approval",
                        title = if (isHighRisk) "High-risk command needs approval" else "Medium-risk command needs approval",
                        message = "$ $cmd",
                        entryId = entryId
                    )

                    val deferred = CompletableDeferred<Boolean>()
                    pendingApprovals[entryId] = deferred
                    val approved = try {
                        deferred.await()
                    } finally {
                        pendingApprovals.remove(entryId)
                        notificationHelper.dismissApprovalNotification(entryId)
                    }
                    if (!approved) {
                        logPipelineStage("DROP_AT_APPROVAL_GATE", "Direct command denied by user: '$cmd'")
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = "Command denied by user: `$cmd`",
                            noticeKind = "warn",
                            cwd = _currentWorkingDir.value
                        )
                        return@launch
                    }
                }

                if (ExecutionTimeouts.isLikelyLongRunningServer(cmd)) {
                    startBackgroundJob(cmd)
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Started background process: `$cmd`. Use the Background Jobs bar or Environment Setup screen to view logs or stop it.",
                        noticeKind = "info",
                        cwd = _currentWorkingDir.value
                    )
                    return@launch
                }

                syncCurrentSessionStats(status = "exec")
                val execResult = executeOnActiveBackend(
                    command = cmd,
                    requestedTimeoutSec = effectiveTimeout
                )
                logPipelineStage("DIRECT_SHELL_EXEC_DONE", "cmd='$cmd', exitCode=${execResult.exitCode}, cwd='${execResult.cwd}'")
                _currentWorkingDir.value = execResult.cwd
                val clipped = NexusToolAndRedactor.clipOutput(execResult.combinedOutput)
                val outRes = appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "output",
                    sequence = nextSequence(),
                    command = cmd,
                    cwd = execResult.cwd,
                    exitCode = execResult.exitCode.toLong(),
                    output = clipped,
                    redactions = execResult.redactions.toLong()
                )
                outRes.getOrNull()?.let { outId ->
                    _runtimeLatenciesMs.update { it + (outId to execResult.durationMs) }
                }

                if (execResult.exitCode == 124) {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Command timed out after ${execResult.timeoutSec}s: `$cmd`. You can re-run this command as a detached background job.",
                        noticeKind = "timeout_bg_offer",
                        command = cmd,
                        cwd = execResult.cwd
                    )
                } else if ((activeBackend.backendType == ExecutionBackendType.ANDROID_SHELL && !shellBridge.isOnline) || execResult.stderr.startsWith("Bridge unreachable")) {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Shell bridge error: ${execResult.stderr}",
                        noticeKind = "error",
                        cwd = execResult.cwd
                    )
                } else {
                    lastFailedDirectCommand = null
                }

                syncCurrentSessionStats(
                    status = "idle",
                    addCommands = 1L,
                    addRedactions = execResult.redactions.toLong()
                )
            } catch (e: Exception) {
                logPipelineStage("DIRECT_SHELL_ERROR", e.localizedMessage ?: "error")
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Shell execution failed: ${e.localizedMessage ?: "unexpected error"}",
                    noticeKind = "error",
                    cwd = _currentWorkingDir.value
                )
            } finally {
                _isRunning.value = false
                _agentStatus.value = "idle"
            }
        }
    }

    fun updatePromptInput(text: String) {
        _promptInput.value = text
    }

    fun recallPreviousDirective() {
        val entries = _settings.value.inputHistory
        if (entries.isEmpty()) return
        val cursor = historyCursor ?: run {
            historyDraft = _promptInput.value
            entries.size
        }
        val nextCursor = (cursor - 1).coerceAtLeast(0)
        historyCursor = nextCursor
        _promptInput.value = entries[nextCursor]
    }

    fun recallNextDirective() {
        val cursor = historyCursor ?: return
        val entries = _settings.value.inputHistory
        if (cursor < entries.size - 1) {
            val nextCursor = cursor + 1
            historyCursor = nextCursor
            _promptInput.value = entries[nextCursor]
        } else {
            historyCursor = null
            _promptInput.value = historyDraft
        }
    }

    private fun pushInputHistory(prompt: String) {
        if (_isTemporaryChat.value) return
        val trimmed = prompt.trim().take(2000)
        historyCursor = null
        historyDraft = ""
        if (trimmed.isBlank()) return
        val existing = _settings.value.inputHistory
        if (existing.lastOrNull() == trimmed) return
        val updatedList = (existing + trimmed).takeLast(100)
        val updatedSettings = _settings.value.copy(inputHistory = updatedList)
        _settings.value = updatedSettings
        viewModelScope.launch {
            saveUserSettingsOrLocal(updatedSettings)
        }
    }

    fun saveSettings(
        provider: String,
        selectedModel: String,
        maxTokens: Long,
        maxSteps: Long,
        timeoutSec: Long,
        agentPersona: String,
        remoteBridge: String,
        geminiApiKey: String? = null,
        anthropicApiKey: String? = null,
        openaiApiKey: String? = null,
        fableApiKey: String? = null,
        activeModelSpecificKey: String? = null,
        huggingFaceToken: String? = null
    ) {
        _remoteBridgeUrl.value = if (isGuestMode) "" else remoteBridge.trim()
        val resolvedProvider = NexusToolAndRedactor.normalizeProviderId(provider)
        val defaultModelForProv = NexusToolAndRedactor.AI_PROVIDERS
            .firstOrNull { it.id == resolvedProvider }?.defaultModel ?: "gemini-2.5-flash"
        val resolvedModel = selectedModel.trim().removePrefix("models/").ifBlank { defaultModelForProv }.take(120)

        val previousProvider = selectedProvider.value
        val previousModel = selectedModelId.value

        if (huggingFaceToken != null) {
            userApiKeyStore.setHuggingFaceToken(huggingFaceToken)
        }

        if (geminiApiKey != null || anthropicApiKey != null || openaiApiKey != null || fableApiKey != null || activeModelSpecificKey != null) {
            userApiKeyStore.saveAllProviderKeys(
                geminiKey = geminiApiKey ?: userApiKeyStore.getProviderKey("gemini"),
                anthropicKey = anthropicApiKey ?: userApiKeyStore.getProviderKey("anthropic"),
                openaiKey = openaiApiKey ?: userApiKeyStore.getProviderKey("openai"),
                fableKey = fableApiKey ?: "",
                activeModelId = if (resolvedProvider != "local") resolvedModel else null,
                activeModelKey = activeModelSpecificKey
            )
        }

        // Persist single source of truth in DataStore
        userApiKeyStore.persistSelectedProviderAndModel(resolvedProvider, resolvedModel)

        // Refresh dynamic models when a cloud provider has a key
        if (resolvedProvider != "local") {
            refreshProviderModels(resolvedProvider, forceNetwork = false)
        }

        // When switching to a local model: unload previous model, load new one
        // When switching away from local model: unload local model
        viewModelScope.launch {
            if (resolvedProvider == "local") {
                val compactPrefix = MultiPlatformAiEngine.buildCompactLocalSystemInstruction(shellBridge.currentCwd)
                val loadRes = localLlmManager.switchActiveLocalModel(resolvedModel, compactPrefix)
                if (loadRes is ModelLoadResult.RefusedByLoadGuard) {
                    _uiBannerMessage.value = loadRes.userMessage
                }
            } else if (previousProvider == "local" && previousModel.isNotBlank()) {
                localLlmManager.unloadActiveLocalModel()
            }
        }

        // Update missing API key prompt state immediately
        if (resolvedProvider != "local" && !userApiKeyStore.hasApiKeyFor(resolvedProvider, resolvedModel)) {
            val provLabel = NexusToolAndRedactor.providerDisplayLabel(resolvedProvider)
            _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                provider = resolvedProvider,
                modelId = resolvedModel,
                providerDisplayName = provLabel,
                message = "No personal API key configured for $provLabel ($resolvedModel). Add your API key in Settings to use this model."
            )
        } else {
            _missingApiKeyPrompt.value = null
        }

        val effectiveMaxSteps = if (isGuestMode) {
            maxSteps.coerceIn(1L, GUEST_MAX_STEPS)
        } else {
            maxSteps.coerceIn(1L, 12L)
        }

        val pCfg = personalizationStore.config.value
        val sanitized = _settings.value.copy(
            provider = resolvedProvider,
            selectedModel = resolvedModel,
            maxTokens = maxTokens.coerceIn(256L, 64000L),
            maxSteps = effectiveMaxSteps,
            timeoutSec = NexusToolAndRedactor.clampTimeoutSec(timeoutSec),
            agentPersona = agentPersona.trim().ifBlank { NexusUserSettings.DEFAULT_SYSTEM_PROMPT }.take(2000),
            displayName = pCfg.displayName,
            avatarDataUri = pCfg.avatarDataUri,
            fontSize = pCfg.fontSize.id,
            responseStyle = pCfg.responseStyle.id,
            personaPreset = pCfg.personaPresetId,
            customPersonasJson = personalizationStore.serializeCustomPersonasForCloud(),
            aboutUser = pCfg.aboutUser,
            responsePreference = pCfg.responsePreference,
            enableForNewChats = pCfg.enableForNewChats,
            skillsJson = personalizationStore.serializeSkillsForCloud(),
            skillsTokenBudget = pCfg.skillsTokenBudget.toLong(),
            recentWorkspaces = pCfg.recentWorkspaces
        )
        _settings.value = sanitized
        viewModelScope.launch {
            saveUserSettingsOrLocal(sanitized)
            syncCurrentSessionStats(status = _agentStatus.value)
        }
    }

    // --- Auto-Save Profile, General, Model Configuration, and Data & Privacy Actions ---

    private fun syncPersonalizationToCloudSettings() {
        val pCfg = personalizationStore.config.value
        val updated = _settings.value.copy(
            displayName = pCfg.displayName,
            avatarDataUri = pCfg.avatarDataUri,
            fontSize = pCfg.fontSize.id,
            responseStyle = pCfg.responseStyle.id,
            personaPreset = pCfg.personaPresetId,
            customPersonasJson = personalizationStore.serializeCustomPersonasForCloud(),
            aboutUser = pCfg.aboutUser,
            responsePreference = pCfg.responsePreference,
            enableForNewChats = pCfg.enableForNewChats,
            skillsJson = personalizationStore.serializeSkillsForCloud(),
            skillsTokenBudget = pCfg.skillsTokenBudget.toLong(),
            recentWorkspaces = pCfg.recentWorkspaces
        )
        _settings.value = updated
        viewModelScope.launch {
            saveUserSettingsOrLocal(updated)
        }
    }

    fun updateDisplayName(name: String) {
        personalizationStore.updateDisplayName(name)
        syncPersonalizationToCloudSettings()
    }

    fun saveCroppedAvatarBitmap(
        bitmap: Bitmap,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f
    ) {
        personalizationStore.saveAvatarBitmap(
            source = bitmap,
            cropScale = zoom,
            offsetXNormalized = panX,
            offsetYNormalized = panY
        )
        syncPersonalizationToCloudSettings()
    }

    fun removeAvatar() {
        personalizationStore.removeAvatar()
        syncPersonalizationToCloudSettings()
    }

    fun updateFontSize(fontSize: NexusFontSize) {
        personalizationStore.updateFontSize(fontSize)
        syncPersonalizationToCloudSettings()
    }

    fun updateResponseStyle(style: NexusResponseStyle) {
        personalizationStore.updateResponseStyle(style)
        syncPersonalizationToCloudSettings()
    }

    fun selectPersonaPreset(personaId: String) {
        personalizationStore.selectPersonaPreset(personaId)
        syncPersonalizationToCloudSettings()
    }

    fun saveCustomPersona(name: String, prompt: String, existingId: String? = null) {
        personalizationStore.saveCustomPersona(name, prompt, existingId)
        syncPersonalizationToCloudSettings()
    }

    fun deleteCustomPersona(personaId: String) {
        personalizationStore.deleteCustomPersona(personaId)
        syncPersonalizationToCloudSettings()
    }

    fun updatePersonalization(
        aboutUser: String = personalizationConfig.value.aboutUser,
        responsePreference: String = personalizationConfig.value.responsePreference,
        enableForNewChats: Boolean = personalizationConfig.value.enableForNewChats
    ) {
        personalizationStore.updatePersonalization(
            aboutUser = aboutUser,
            responsePreference = responsePreference,
            enableForNewChats = enableForNewChats
        )
        syncPersonalizationToCloudSettings()
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        personalizationStore.toggleSkill(skillId, enabled)
        syncPersonalizationToCloudSettings()
    }

    fun saveCustomSkill(
        id: String? = null,
        name: String,
        description: String,
        instructions: String,
        enabled: Boolean = true
    ) {
        personalizationStore.saveCustomSkill(
            id = id,
            name = name,
            description = description,
            instructions = instructions,
            enabled = enabled
        )
        syncPersonalizationToCloudSettings()
    }

    fun deleteCustomSkill(skillId: String) {
        personalizationStore.deleteCustomSkill(skillId)
        syncPersonalizationToCloudSettings()
    }

    fun updateSkillsTokenBudget(budget: Int) {
        personalizationStore.updateSkillsTokenBudget(budget)
        syncPersonalizationToCloudSettings()
    }

    fun importSkillFromMarkdownOrJson(rawContent: String): Result<Unit> {
        return personalizationStore.importSkillFromMarkdownOrJson(rawContent).map {
            syncPersonalizationToCloudSettings()
        }
    }

    fun exportSkillsAsJson(): String = personalizationStore.exportSkillsAsJson()

    fun removeRecentWorkspace(path: String) {
        personalizationStore.removeRecentWorkspace(path)
        syncPersonalizationToCloudSettings()
    }

    fun saveProviderApiKey(provider: String, apiKey: String) {
        val cleanProv = NexusToolAndRedactor.normalizeProviderId(provider)
        userApiKeyStore.setProviderKey(cleanProv, apiKey.trim())
        if (apiKey.isNotBlank() && cleanProv != "local") {
            refreshProviderModels(cleanProv, forceNetwork = false)
        }
    }

    fun updateInferenceDefaults(
        maxTokens: Long = _settings.value.maxTokens,
        maxSteps: Long = _settings.value.maxSteps,
        timeoutSec: Long = _settings.value.timeoutSec,
        remoteBridge: String = _remoteBridgeUrl.value
    ) {
        saveSettings(
            provider = selectedProvider.value,
            selectedModel = selectedModelId.value,
            maxTokens = maxTokens,
            maxSteps = maxSteps,
            timeoutSec = timeoutSec,
            agentPersona = _settings.value.agentPersona,
            remoteBridge = remoteBridge
        )
    }

    fun clearAllSavedApiKeys() {
        userApiKeyStore.clearAllApiKeys()
        _keyTestStatuses.value = emptyMap()
        _uiBannerMessage.value = "Cleared all saved API keys from device Keystore."
    }

    fun clearAllChatHistory() {
        if (_isRunning.value) stopRunningAgent()
        viewModelScope.launch {
            if (isGuestMode || repository == null) {
                _guestEntries.value = emptyList()
                _guestSessions.update { list ->
                    list.take(1).map {
                        it.copy(
                            title = "New chat",
                            status = "idle",
                            commandsCount = 0L,
                            redactionsCount = 0L
                        )
                    }
                }
            } else {
                repository.deleteAllUserSessions()
                createNewSession("New chat")
            }
        }
    }

    fun deleteAccountAndCloudData(onCompleted: () -> Unit) {
        if (_isRunning.value) stopRunningAgent()
        viewModelScope.launch {
            if (!isGuestMode && repository != null) {
                repository.deleteUserAccountAndCloudData()
            }
            personalizationStore.removeAvatar()
            personalizationStore.resetToDefaults()
            onCompleted()
        }
    }

    fun resetAllSettingsToDefaults() {
        personalizationStore.resetToDefaults()
        localLlmManager.setInferencePreset(InferencePreset.BALANCED)
        _remoteBridgeUrl.value = ""
        saveSettings(
            provider = selectedProvider.value,
            selectedModel = selectedModelId.value,
            maxTokens = 4096L,
            maxSteps = if (isGuestMode) GUEST_MAX_STEPS else 6L,
            timeoutSec = 10L,
            agentPersona = NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
            remoteBridge = ""
        )
    }

    fun exportFullSettingsJson(themeModeId: String): String {
        val s = _settings.value
        return personalizationStore.exportFullSettingsJson(
            provider = selectedProvider.value,
            selectedModel = selectedModelId.value,
            maxTokens = s.maxTokens,
            maxSteps = s.maxSteps,
            timeoutSec = s.timeoutSec,
            themeModeId = themeModeId
        )
    }

    fun importFullSettingsJson(rawJson: String): Result<String?> {
        return personalizationStore.importFullSettingsJson(rawJson).map {
            runCatching {
                val obj = org.json.JSONObject(rawJson.trim())
                val modelDefaults = obj.optJSONObject("modelDefaults")
                if (modelDefaults != null) {
                    saveSettings(
                        provider = modelDefaults.optString("provider", selectedProvider.value),
                        selectedModel = modelDefaults.optString("selectedModel", selectedModelId.value),
                        maxTokens = modelDefaults.optLong("maxTokens", _settings.value.maxTokens),
                        maxSteps = modelDefaults.optLong("maxSteps", _settings.value.maxSteps),
                        timeoutSec = modelDefaults.optLong("timeoutSec", _settings.value.timeoutSec),
                        agentPersona = _settings.value.agentPersona,
                        remoteBridge = _remoteBridgeUrl.value
                    )
                } else {
                    syncPersonalizationToCloudSettings()
                }
                obj.optString("themeMode", "").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    fun selectSession(sessionId: String) {
        if (isGuestMode) {
            _isTemporaryChat.value = false
            return
        }
        if (_isRunning.value) stopRunningAgent()
        _isTemporaryChat.value = false
        _activeSessionId.value = sessionId
        val updated = _settings.value.copy(activeSessionId = sessionId)
        _settings.value = updated
        viewModelScope.launch {
            saveUserSettingsOrLocal(updated)
        }
    }

    fun createNewSession(customTitle: String = "") {
        if (_isRunning.value) stopRunningAgent()
        _isTemporaryChat.value = false
        if (isGuestMode) {
            _guestEntries.value = emptyList()
            _guestSessions.update { list ->
                list.map {
                    it.copy(
                        title = customTitle.trim().ifBlank { "New chat" },
                        status = "idle",
                        commandsCount = 0L,
                        redactionsCount = 0L,
                        provider = NexusToolAndRedactor.providerDisplayLabel(selectedProvider.value),
                        model = selectedModelId.value,
                        updatedAt = Timestamp.now()
                    )
                }
            }
            return
        }
        val repo = repository ?: return
        viewModelScope.launch {
            val title = customTitle.trim().ifBlank { "New chat" }
            val freshCwd = shellBridge.currentCwd
            _currentWorkingDir.value = freshCwd
            val cfg = _settings.value
            val res = repo.createSession(
                title = title,
                cwd = freshCwd,
                provider = NexusToolAndRedactor.providerDisplayLabel(selectedProvider.value),
                model = selectedModelId.value
            )
            res.onSuccess { newId ->
                _activeSessionId.value = newId
                val updated = cfg.copy(
                    provider = selectedProvider.value,
                    selectedModel = selectedModelId.value,
                    activeSessionId = newId
                )
                _settings.value = updated
                repo.ensureOrSaveUserSettings(updated)
            }
        }
    }

    fun onClearClicked() {
        if (_isRunning.value) return
        if (!_clearConfirmArmed.value) {
            _clearConfirmArmed.value = true
            return
        }
        clearConfirmJob?.cancel()
        _clearConfirmArmed.value = false
        clearActiveSession()
    }

    fun clearActiveSessionImmediate() {
        if (_isRunning.value) stopRunningAgent()
        clearConfirmJob?.cancel()
        _clearConfirmArmed.value = false
        clearActiveSession()
    }

    private fun clearActiveSession() {
        val sessionId = activeEffectiveSessionId()
        viewModelScope.launch {
            val cwd = shellBridge.currentCwd
            _currentWorkingDir.value = cwd
            val activeProvLabel = NexusToolAndRedactor.providerDisplayLabel(selectedProvider.value)
            val activeModId = selectedModelId.value
            when {
                _isTemporaryChat.value -> {
                    _temporaryEntries.value = emptyList()
                    _temporarySession.update {
                        it.copy(
                            status = "idle",
                            cwd = cwd,
                            commandsCount = 0L,
                            redactionsCount = 0L,
                            provider = activeProvLabel,
                            model = activeModId
                        )
                    }
                }
                isGuestMode || repository == null -> {
                    _guestEntries.value = emptyList()
                    _guestSessions.update { list ->
                        list.map {
                            it.copy(
                                title = "New chat",
                                status = "idle",
                                cwd = cwd,
                                commandsCount = 0L,
                                redactionsCount = 0L,
                                provider = activeProvLabel,
                                model = activeModId
                            )
                        }
                    }
                }
                else -> {
                    repository.clearSessionEntries(sessionId)
                    repository.updateSessionStatusAndStats(
                        sessionId = sessionId,
                        status = "idle",
                        cwd = cwd,
                        commandsCount = 0L,
                        redactionsCount = 0L,
                        provider = activeProvLabel,
                        model = activeModId
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        _uiBannerMessage.value = null
    }

    fun dismissAnalysis() {
        _activeAnalysis.value = null
    }

    private fun nextSequence(): Long {
        val currentList = when {
            _isTemporaryChat.value -> _temporaryEntries.value
            isGuestMode || repository == null -> _guestEntries.value
            else -> (entriesState.value as? UiState.Success)?.data.orEmpty()
        }
        return (currentList.maxOfOrNull { it.sequence } ?: 0L) + 1L
    }

    private fun currentSessionModel(): NexusSession? {
        if (_isTemporaryChat.value) return _temporarySession.value
        val list = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        return list.find { it.id == _activeSessionId.value }
    }

    private suspend fun syncCurrentSessionStats(
        status: String,
        addCommands: Long = 0L,
        addRedactions: Long = 0L,
        updatedTitle: String? = null
    ) {
        val sessionId = activeEffectiveSessionId()
        val cur = currentSessionModel()
        val activeProvLabel = NexusToolAndRedactor.providerDisplayLabel(selectedProvider.value)
        val activeModId = selectedModelId.value
        val effectiveCwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd }
        _agentStatus.value = status
        _currentWorkingDir.value = effectiveCwd

        if (_isTemporaryChat.value || sessionId == TEMP_SESSION_ID) {
            _temporarySession.update { sess ->
                sess.copy(
                    status = status,
                    cwd = effectiveCwd,
                    commandsCount = sess.commandsCount + addCommands,
                    redactionsCount = sess.redactionsCount + addRedactions,
                    provider = activeProvLabel,
                    model = activeModId
                )
            }
            return
        }

        if (isGuestMode || repository == null) {
            _guestSessions.update { list ->
                list.map { sess ->
                    if (sess.id == sessionId) {
                        sess.copy(
                            status = status,
                            cwd = effectiveCwd,
                            projectSlug = _activeProjectSlug.value,
                            storageLocation = _activeProjectStorageLocation.value,
                            commandsCount = sess.commandsCount + addCommands,
                            redactionsCount = sess.redactionsCount + addRedactions,
                            provider = activeProvLabel,
                            model = activeModId,
                            title = updatedTitle ?: sess.title,
                            updatedAt = Timestamp.now()
                        )
                    } else sess
                }
            }
            return
        }

        repository.updateSessionStatusAndStats(
            sessionId = sessionId,
            status = status,
            cwd = effectiveCwd,
            commandsCount = (cur?.commandsCount ?: 0L) + addCommands,
            redactionsCount = (cur?.redactionsCount ?: 0L) + addRedactions,
            provider = activeProvLabel,
            model = activeModId,
            title = updatedTitle,
            projectSlug = _activeProjectSlug.value,
            storageLocation = _activeProjectStorageLocation.value
        )
    }

    /**
     * Helper for tests to set the current guest directive count directly.
     */
    internal fun setGuestDirectivesUsedForTest(usedCount: Int) {
        _guestDirectivesUsed.value = usedCount.coerceAtLeast(0)
    }

    /**
     * Explicit Quick Command execution (Part A & Clarifying Answer #1):
     * Triggered via the dedicated Quick Command action chip or `/quick ` slash prefix.
     * Labeled `"Quick command"` in the UI (`noticeKind = "quick_command"`) and never poses as model output.
     */
    fun executeQuickCommand(rawInput: String? = null) {
        val raw = (rawInput ?: _promptInput.value).trim()
        if (raw.isBlank() || _isRunning.value) return
        val sessionId = activeEffectiveSessionId()
        pushInputHistory(raw)
        if (rawInput == null) {
            _promptInput.value = ""
        }

        val translated = localLlmManager.translateQuickCommandDirective(raw, shellBridge.currentCwd)
        if (translated == null) {
            viewModelScope.launch {
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "user",
                    sequence = nextSequence(),
                    text = raw,
                    cwd = shellBridge.currentCwd
                )
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = "No matching Quick command shortcut for '$raw'. Try '/quick create a folder named demo', '/quick list files', or '/quick check disk space'.",
                    noticeKind = "quick_command",
                    cwd = shellBridge.currentCwd
                )
            }
            return
        }

        viewModelScope.launch {
            appendEntryOrLocal(
                sessionId = sessionId,
                type = "user",
                sequence = nextSequence(),
                text = raw,
                cwd = shellBridge.currentCwd
            )
            appendEntryOrLocal(
                sessionId = sessionId,
                type = "notice",
                sequence = nextSequence(),
                text = "${translated.summaryLabel} → `${translated.shellCommand}`",
                noticeKind = "quick_command",
                cwd = shellBridge.currentCwd
            )
            executeDirectShellCommand(translated.shellCommand)
        }
    }

    fun submitDirective(overridePrompt: String? = null) {
        val prompt = (overridePrompt ?: _promptInput.value).trim()
        if (prompt.isBlank() || _isRunning.value) return

        // Route explicit `/quick` slash commands to Quick Command handler (never poses as AI model output)
        if (prompt.startsWith("/quick", ignoreCase = true)) {
            executeQuickCommand(prompt)
            return
        }

        val activeProv = selectedProvider.value.lowercase().trim()
        val activeMod = selectedModelId.value.trim()
        val sessionId = activeEffectiveSessionId()
        lastSubmittedPrompt = prompt
        lastFailedDirectCommand = null

        // Fix #4: Guest Limit — count ONLY cloud-provider prompts against guest quota.
        // Local-model prompts never consume quota and still work when quota is 0.
        if (isGuestMode && activeProv != "local") {
            if (_guestDirectivesUsed.value >= GUEST_MAX_DIRECTIVES) {
                logPipelineStage("DROP_AT_GUEST_QUOTA", "Cloud prompt blocked at guest quota (${_guestDirectivesUsed.value}/$GUEST_MAX_DIRECTIVES)")
                _liveNotificationBanner.value = LiveNotificationEvent(
                    kind = "guest_limit",
                    title = "Limit reached, sign in ($GUEST_MAX_DIRECTIVES/$GUEST_MAX_DIRECTIVES cloud prompts)",
                    message = "Sign in with Google for unlimited cloud prompts, or use a Local LLM (unlimited in Guest Mode)."
                )
                viewModelScope.launch {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Limit reached, sign in: You have used all $GUEST_MAX_DIRECTIVES/$GUEST_MAX_DIRECTIVES guest cloud prompts. Sign in with Google to continue using cloud models, or switch to an on-device Local LLM (unlimited).",
                        noticeKind = "guest_limit",
                        cwd = shellBridge.currentCwd
                    )
                }
                return
            }
        }

        // Enforce Bug #1: Cloud providers require the user's personal API key in UserApiKeyStore.
        if (activeProv != "local" && !userApiKeyStore.hasApiKeyFor(activeProv, activeMod)) {
            val provLabel = NexusToolAndRedactor.providerDisplayLabel(activeProv)
            _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                provider = activeProv,
                modelId = activeMod,
                providerDisplayName = provLabel,
                message = "Missing personal API key for $provLabel ($activeMod). Enter your key in Settings or switch to an On-Device Local GGUF model."
            )
            return
        }
        _missingApiKeyPrompt.value = null

        // Increment guest quota ONLY for cloud-provider prompts (Fix #4)
        if (isGuestMode && activeProv != "local") {
            _guestDirectivesUsed.update { it + 1 }
        }

        pushInputHistory(prompt)
        if (overridePrompt == null) {
            _promptInput.value = ""
        }

        val cfg = _settings.value.copy(
            provider = activeProv,
            selectedModel = activeMod
        )

        userRequestedStop = false
        activeAgentJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val curSession = currentSessionModel()
                val isDefaultTitle = curSession != null && (
                    curSession.title.equals("New chat", ignoreCase = true) ||
                        curSession.title.startsWith("Nexus Shell") ||
                        curSession.title.startsWith("Guest Shell")
                    )
                val hasNoUserPrompts = ((entriesState.value as? UiState.Success)?.data?.none { it.type == "user" } == true)
                val newTitle = if (!_isTemporaryChat.value && isDefaultTitle && hasNoUserPrompts) {
                    prompt.lines().firstOrNull()?.trim()?.take(46) ?: prompt.take(46)
                } else null

                val userSeq = nextSequence()
                val userEntryRes = appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "user",
                    sequence = userSeq,
                    text = prompt,
                    cwd = shellBridge.currentCwd
                )
                val initialUserEntry = TranscriptEntry(
                    id = userEntryRes.getOrNull() ?: "local_user_$userSeq",
                    userId = currentUserId,
                    sessionId = sessionId,
                    type = "user",
                    text = prompt,
                    cwd = shellBridge.currentCwd,
                    sequence = userSeq
                )

                runAgentLoop(
                    sessionId = sessionId,
                    cfg = cfg,
                    initialTitle = newTitle,
                    initialUserEntry = initialUserEntry,
                    initialUserPrompt = prompt
                )
            } catch (e: TimeoutCancellationException) {
                logPipelineStage("DROP_AT_TIMEOUT", "Generation timed out after 60s")
                val partial = _streamingText.value?.trim().orEmpty()
                _streamingText.value = null
                if (partial.isNotBlank()) {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "agent",
                        sequence = nextSequence(),
                        text = partial,
                        cwd = shellBridge.currentCwd
                    )
                }
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Inference timed out after 60 seconds. Tap Retry to try again.",
                    noticeKind = "error",
                    cwd = shellBridge.currentCwd
                )
            } catch (e: CancellationException) {
                logPipelineStage("DROP_AT_CANCELLATION", "userRequestedStop=$userRequestedStop")
                val partial = _streamingText.value?.trim().orEmpty()
                _streamingText.value = null
                if (partial.isNotBlank()) {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "agent",
                        sequence = nextSequence(),
                        text = partial,
                        cwd = shellBridge.currentCwd
                    )
                }
                if (userRequestedStop) {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Stopped.",
                        noticeKind = "warn",
                        cwd = shellBridge.currentCwd
                    )
                }
            } catch (e: MissingApiKeyException) {
                logPipelineStage("DROP_AT_MISSING_API_KEY", "provider=${e.provider}, model=${e.modelId}")
                _streamingText.value = null
                _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                    provider = e.provider,
                    modelId = e.modelId,
                    providerDisplayName = e.providerDisplayName,
                    message = e.message ?: "Missing API key for ${e.providerDisplayName}."
                )
            } catch (e: TypedLocalInferenceException) {
                logPipelineStage("DROP_AT_TYPED_LOCAL_ERROR", "kind=${e.kind}, model=${e.modelId}, msg=${e.userMessage}")
                _streamingText.value = null
                val mappedNoticeKind = when (e.kind) {
                    LocalRuntimeErrorKind.OUT_OF_MEMORY -> "local_oom"
                    LocalRuntimeErrorKind.CORRUPT_FILE -> "local_corrupt_file"
                    LocalRuntimeErrorKind.UNSUPPORTED_MODEL -> "local_unsupported_model"
                    LocalRuntimeErrorKind.GENERATION_FAILED -> "local_generation_failed"
                    LocalRuntimeErrorKind.MODEL_NOT_LOADED,
                    LocalRuntimeErrorKind.RUNTIME_UNAVAILABLE -> "error"
                }
                val formattedText = if (e.userTitle.isNotBlank() && !e.userMessage.startsWith(e.userTitle, ignoreCase = true)) {
                    "${e.userTitle}: ${e.userMessage}"
                } else {
                    e.userMessage
                }
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = formattedText,
                    command = e.modelId.ifBlank { activeMod },
                    noticeKind = mappedNoticeKind,
                    cwd = shellBridge.currentCwd
                )
            } catch (e: LocalModelNotLoadedException) {
                logPipelineStage("DROP_AT_MODEL_NOT_LOADED", e.message)
                _streamingText.value = null
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = e.message,
                    command = e.modelId,
                    noticeKind = "error",
                    cwd = shellBridge.currentCwd
                )
            } catch (e: LocalModelInferenceException) {
                logPipelineStage("DROP_AT_INFERENCE_FAILED", e.message)
                _streamingText.value = null
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = e.message,
                    command = e.modelId,
                    noticeKind = "local_generation_failed",
                    cwd = shellBridge.currentCwd
                )
            } catch (e: ProviderApiException) {
                logPipelineStage("DROP_AT_PROVIDER_API_ERROR", "provider=${e.provider}, status=${e.httpStatus}, kind=${e.kind}")
                _streamingText.value = null
                if (e.kind == ProviderErrorKind.MODEL_NOT_FOUND) {
                    val recovery = providerModelRepository.handleModelNotFound(
                        provider = e.provider,
                        failedModelId = e.modelId,
                        errorBody = e.rawErrorBody
                    )
                    _uiBannerMessage.value = recovery.userNoticeMessage
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = recovery.userNoticeMessage,
                        noticeKind = if (recovery.autoSwappedModel != null) "info" else "model_unavailable",
                        cwd = shellBridge.currentCwd
                    )
                } else {
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = e.userMessage,
                        noticeKind = "error",
                        cwd = shellBridge.currentCwd
                    )
                }
            } catch (e: LocalModelLoadGuardException) {
                logPipelineStage("DROP_AT_LOAD_GUARD", e.guardFailure.userMessage)
                _streamingText.value = null
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = e.guardFailure.userMessage,
                    command = e.guardFailure.modelId,
                    noticeKind = "local_oom",
                    cwd = shellBridge.currentCwd
                )
            } catch (e: Exception) {
                logPipelineStage("DROP_AT_EXCEPTION", e.localizedMessage ?: "Generation failed")
                Log.e(TAG, "Agent loop fault", e)
                _streamingText.value = null
                appendEntryOrLocal(
                    sessionId = sessionId,
                    type = "notice",
                    sequence = nextSequence(),
                    text = "Inference failed: ${e.localizedMessage ?: "Generation failed"}",
                    noticeKind = "error",
                    cwd = shellBridge.currentCwd
                )
            } finally {
                _streamingText.value = null
                _isRunning.value = false
                userRequestedStop = false
                syncCurrentSessionStats(status = "idle")
            }
        }
    }

    fun stopRunningAgent() {
        userRequestedStop = true
        localLlmManager.stopLocalGeneration()
        customExecutionBackend?.cancel()
        termuxBackend?.cancel()
        androidShellBackend.cancel()
        remoteBridgeBackend?.cancel()
        pendingApprovals.forEach { (entryId, deferred) ->
            notificationHelper.dismissApprovalNotification(entryId)
            if (!deferred.isCompleted) {
                deferred.complete(false)
                viewModelScope.launch {
                    updateEntryStatusOrLocal(
                        sessionId = activeEffectiveSessionId(),
                        entryId = entryId,
                        commandStatus = "cancelled"
                    )
                }
            }
        }
        pendingApprovals.clear()
        activeAgentJob?.cancel()
        activeAgentJob = null
    }

    fun resolveCommandPermission(entry: TranscriptEntry, approved: Boolean) {
        val sessionId = activeEffectiveSessionId()
        val deferred = pendingApprovals[entry.id]
        notificationHelper.dismissApprovalNotification(entry.id)
        if (_liveNotificationBanner.value?.entryId == entry.id) {
            _liveNotificationBanner.value = null
        }
        viewModelScope.launch {
            updateEntryStatusOrLocal(
                sessionId = sessionId,
                entryId = entry.id,
                commandStatus = if (approved) "approved" else "denied"
            )
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(approved)
            } else if (approved && !_isRunning.value) {
                executeStandaloneApprovedCommand(entry)
            }
        }
    }

    private suspend fun executeStandaloneApprovedCommand(entry: TranscriptEntry) {
        val sessionId = activeEffectiveSessionId()
        _isRunning.value = true
        try {
            val activeBackend = resolveActiveExecutionBackend()
            if (activeBackend.backendType != ExecutionBackendType.TERMUX) {
                when (val cwdCheck = shellBridge.validateWorkingDirectory(shellBridge.currentCwd)) {
                    is CwdValidationResult.MissingAllFilesAccess -> {
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = cwdCheck.message,
                            noticeKind = "permission",
                            cwd = shellBridge.currentCwd
                        )
                        return
                    }
                    is CwdValidationResult.InvalidOrNotWritable -> {
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = cwdCheck.message,
                            noticeKind = "error",
                            cwd = shellBridge.currentCwd
                        )
                        return
                    }
                    is CwdValidationResult.Valid -> {
                        _currentWorkingDir.value = cwdCheck.resolvedPath
                    }
                }
            }

            syncCurrentSessionStats(status = "exec")
            val result = executeOnActiveBackend(
                command = entry.command,
                requestedTimeoutSec = entry.timeoutSec
            )
            _currentWorkingDir.value = result.cwd
            val clipped = NexusToolAndRedactor.clipOutput(result.combinedOutput)
            val outRes = appendEntryOrLocal(
                sessionId = sessionId,
                type = "output",
                sequence = nextSequence(),
                command = entry.command,
                cwd = result.cwd,
                exitCode = result.exitCode.toLong(),
                output = clipped,
                redactions = result.redactions.toLong()
            )
            outRes.getOrNull()?.let { outId ->
                _runtimeLatenciesMs.update { it + (outId to result.durationMs) }
            }
            syncCurrentSessionStats(
                status = "idle",
                addCommands = 1L,
                addRedactions = result.redactions.toLong()
            )
        } finally {
            _isRunning.value = false
        }
    }

    /**
     * Full provider-independent execution pipeline (Fix #2, #3, #6, #8):
     * Model output -> ToolCallParser -> Risk classifier (LOW auto-run, MEDIUM/HIGH approval gate)
     * -> Working directory & permission check -> Active ExecutionBackend with timeout
     * -> Output card with exit code -> Output fed back as next user turn (redacted, truncated to 2000 chars).
     */
    private suspend fun runAgentLoop(
        sessionId: String,
        cfg: NexusUserSettings,
        initialTitle: String?,
        initialUserEntry: TranscriptEntry,
        initialUserPrompt: String
    ) {
        val activeProv = selectedProvider.value.lowercase().trim()
        val activeMod = selectedModelId.value.trim()

        val maxStepsCap = when {
            activeProv == "local" -> MultiPlatformAiEngine.LOCAL_MAX_AGENT_STEPS.toLong()
            isGuestMode -> GUEST_MAX_STEPS
            else -> 12L
        }
        val maxSteps = cfg.maxSteps.coerceIn(1L, maxStepsCap).toInt()

        val truncatedIds = _optimisticallyTruncatedIds.value +
            lastTruncatedEditBackup?.takeIf { it.sessionId == sessionId }?.removedEntries?.map { it.id }?.toSet().orEmpty()
        val baseEntries = when {
            _isTemporaryChat.value -> _temporaryEntries.value
            isGuestMode -> _guestEntries.value
            else -> (entriesState.value as? UiState.Success)?.data.orEmpty()
        }.filter { it.id !in truncatedIds }
        val workingEntries = baseEntries.toMutableList()
        workingEntries.removeAll { it.id == initialUserEntry.id }
        workingEntries.add(initialUserEntry)

        for (step in 0 until maxSteps) {
            syncCurrentSessionStats(
                status = "stream",
                updatedTitle = if (step == 0) initialTitle else null
            )

            val accumulated = StringBuilder()
            _streamingText.value = ""

            val effectiveSystemPrompt = personalizationStore.buildEffectiveSystemPrompt(
                baseAgentPersona = cfg.agentPersona,
                envDescription = _environmentDescription.value
            )

            withTimeout(60_000L) {
                multiPlatformEngine.streamCompletion(
                    provider = activeProv,
                    modelId = activeMod,
                    maxTokens = cfg.maxTokens,
                    entries = workingEntries.toList(),
                    personaPrompt = effectiveSystemPrompt,
                    currentCwd = _currentWorkingDir.value.ifBlank { shellBridge.currentCwd },
                    fallbackUserPrompt = initialUserPrompt
                ).collect { delta ->
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        _streamingText.value = stripToolTagsForDisplay(accumulated.toString())
                    }
                }
            }

            val fullRawText = accumulated.toString()
            _streamingText.value = null
            logPipelineStage(
                stage = "1_MODEL_OUTPUT",
                detail = "step=${step + 1}/$maxSteps, provider=$activeProv, model=$activeMod, chars=${fullRawText.length}, preview='${fullRawText.take(160)}'"
            )

            val parseResult = ToolCallParser.parse(
                rawText = fullRawText,
                defaultTimeoutSec = cfg.timeoutSec,
                allowMultiLineIfApproved = false
            )

            when (parseResult) {
                is ToolCallParseResult.ParseError -> {
                    logPipelineStage("2_DROP_AT_TOOL_CALL_PARSER", parseResult.message)
                    appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "notice",
                        sequence = nextSequence(),
                        text = "Tool-call parse failed: ${parseResult.message}",
                        noticeKind = "error",
                        cwd = _currentWorkingDir.value
                    )
                    return
                }

                is ToolCallParseResult.NoToolCall -> {
                    logPipelineStage("2_TOOL_CALL_PARSER", "Plain-text turn (no command block emitted); completing turn.")
                    val cleanMarkdown = stripToolTagsForDisplay(fullRawText)
                    if (cleanMarkdown.isNotBlank()) {
                        val agentSeq = nextSequence()
                        val agentRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "agent",
                            sequence = agentSeq,
                            text = cleanMarkdown,
                            cwd = _currentWorkingDir.value
                        )
                        workingEntries.add(
                            TranscriptEntry(
                                id = agentRes.getOrNull() ?: "local_agent_${step}_$agentSeq",
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "agent",
                                text = cleanMarkdown,
                                cwd = _currentWorkingDir.value,
                                sequence = agentSeq
                            )
                        )
                    } else {
                        logPipelineStage("DROP_AT_EMPTY_MODEL_OUTPUT", "Model returned blank text")
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = "Inference failed: Model returned an empty response.",
                            noticeKind = "error",
                            cwd = _currentWorkingDir.value
                        )
                    }
                    return
                }

                is ToolCallParseResult.Success -> {
                    val toolCall: ToolCall = parseResult.toolCall
                    val parsedThinking = ThinkingTagParser.parse(fullRawText)
                    val thinkingBlockPrefix = if (parsedThinking.thinkingText.isNotEmpty()) {
                        "<think>\n${parsedThinking.thinkingText}\n</think>\n\n"
                    } else {
                        ""
                    }
                    val assistantText = (thinkingBlockPrefix + parseResult.cleanedAssistantText).trim()
                    logPipelineStage(
                        stage = "2_TOOL_CALL_PARSER",
                        detail = "Parsed command='${toolCall.command}', source=${toolCall.source}, risk=${toolCall.riskLevel}"
                    )

                    if (assistantText.isNotBlank()) {
                        val agentSeq = nextSequence()
                        val agentRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "agent",
                            sequence = agentSeq,
                            text = assistantText,
                            cwd = _currentWorkingDir.value
                        )
                        workingEntries.add(
                            TranscriptEntry(
                                id = agentRes.getOrNull() ?: "local_agent_${step}_$agentSeq",
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "agent",
                                text = parseResult.cleanedAssistantText,
                                cwd = _currentWorkingDir.value,
                                sequence = agentSeq
                            )
                        )
                    }

                    // Stage 2.5: Toolchain prerequisite check
                    if (FallbackPolicy.requiresTermuxToolchain(toolCall.command) && isSetupNeeded.value) {
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = "Set up environment: `${toolCall.command}` requires the Termux developer toolchain. Tap 'Set up environment' to configure git, Node.js, and Python.",
                            noticeKind = "setup_needed",
                            cwd = _currentWorkingDir.value
                        )
                    }

                    // Stage 2.8: Workspace root write protection & best-effort project jail (Amendments #1, #2, #8)
                    val shouldCheckJail = enforceUnassignedProjectSlugFlow ||
                        _currentWorkingDir.value == WorkspacePaths.DEFAULT_SHARED_ROOT ||
                        _activeProjectSlug.value.isNotBlank()
                    val jailDecision = if (shouldCheckJail) {
                        WorkspacePathValidator.evaluateCommandInWorkspace(
                            command = toolCall.command,
                            currentCwd = _currentWorkingDir.value,
                            boundProjectSlug = _activeProjectSlug.value,
                            isTemporaryChat = _isTemporaryChat.value
                        )
                    } else {
                        CommandJailDecision.Allowed
                    }

                    when (jailDecision) {
                        is CommandJailDecision.BlockedRootWrite -> {
                            if (_activeProjectSlug.value.isBlank() && !_isTemporaryChat.value) {
                                val proposedSlug = WorkspacePaths.proposeSlugFromPromptOrCommand(
                                    prompt = initialUserPrompt,
                                    command = toolCall.command
                                )
                                val existingSlugs = _availableProjects.value.map { it.slug }.toSet()
                                val (collision, altSlug) = WorkspacePaths.resolveSlugCollision(proposedSlug, existingSlugs)
                                _pendingProjectProposal.value = PendingProjectCreationProposal(
                                    proposedSlug = proposedSlug,
                                    collisionExists = collision,
                                    nextAvailableSlug = altSlug,
                                    triggerCommand = toolCall.command,
                                    triggerPrompt = initialUserPrompt
                                )
                                appendEntryOrLocal(
                                    sessionId = sessionId,
                                    type = "notice",
                                    sequence = nextSequence(),
                                    text = "${jailDecision.message}\nProposing project folder: `/storage/emulated/0/Nexus/$proposedSlug`",
                                    noticeKind = "project_slug_proposal",
                                    command = toolCall.command,
                                    cwd = _currentWorkingDir.value
                                )
                            } else {
                                appendEntryOrLocal(
                                    sessionId = sessionId,
                                    type = "notice",
                                    sequence = nextSequence(),
                                    text = jailDecision.message,
                                    noticeKind = "warn",
                                    command = toolCall.command,
                                    cwd = _currentWorkingDir.value
                                )
                            }
                            return
                        }
                        is CommandJailDecision.BlockedTraversalOrSymlinkEscape -> {
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = jailDecision.message,
                                noticeKind = "error",
                                command = toolCall.command,
                                cwd = _currentWorkingDir.value
                            )
                            return
                        }
                        else -> Unit
                    }

                    // Stage 3: Risk classifier (LOW -> auto-run, MEDIUM/HIGH -> approval card)
                    val riskLevel = ToolCallParser.classifyRisk(toolCall.command, _currentWorkingDir.value)
                    val isOutsideProjectApproval = jailDecision is CommandJailDecision.RequiresOutsideProjectApproval
                    val isHighRisk = riskLevel == CommandRiskLevel.HIGH
                    val requiresApproval = isOutsideProjectApproval ||
                        riskLevel == CommandRiskLevel.MEDIUM ||
                        riskLevel == CommandRiskLevel.HIGH
                    val effectiveTimeout = ExecutionTimeouts.resolveTimeoutSec(toolCall.command, toolCall.timeoutSec)
                    val effectiveReason = if (jailDecision is CommandJailDecision.RequiresOutsideProjectApproval) {
                        "${toolCall.reason} (${jailDecision.reason})"
                    } else {
                        toolCall.reason
                    }
                    logPipelineStage(
                        stage = "3_RISK_CHECK",
                        detail = "cmd='${toolCall.command}', risk=$riskLevel, requiresApproval=$requiresApproval"
                    )

                    val cmdSeq = nextSequence()
                    val createRes = appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "command",
                        sequence = cmdSeq,
                        command = toolCall.command,
                        reason = effectiveReason,
                        commandStatus = if (requiresApproval) "pending" else "approved",
                        isRisky = isHighRisk,
                        timeoutSec = effectiveTimeout,
                        cwd = _currentWorkingDir.value
                    )
                    val entryId = createRes.getOrNull() ?: "cmd_${step}_$cmdSeq"

                    if (requiresApproval) {
                        logPipelineStage("4_APPROVAL_GATE", "Awaiting user approval for $riskLevel risk command '${toolCall.command}'")
                        syncCurrentSessionStats(status = "wait")

                        notificationHelper.notifyCommandApprovalNeeded(
                            entryId = entryId,
                            command = toolCall.command,
                            reason = toolCall.reason,
                            isRisky = isHighRisk
                        )
                        _liveNotificationBanner.value = LiveNotificationEvent(
                            kind = "approval",
                            title = if (isHighRisk) "High-risk command needs approval" else "Medium-risk command needs approval",
                            message = "$ ${toolCall.command}",
                            entryId = entryId
                        )

                        val deferred = CompletableDeferred<Boolean>()
                        pendingApprovals[entryId] = deferred

                        val approved = try {
                            deferred.await()
                        } finally {
                            pendingApprovals.remove(entryId)
                            notificationHelper.dismissApprovalNotification(entryId)
                        }

                        if (!approved) {
                            logPipelineStage("4_DROP_AT_APPROVAL_GATE", "User denied command '${toolCall.command}'")
                            val deniedText = "Command denied by user: `${toolCall.command}`"
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = deniedText,
                                noticeKind = "warn",
                                cwd = _currentWorkingDir.value
                            )
                            workingEntries.add(
                                TranscriptEntry(
                                    id = entryId,
                                    userId = currentUserId,
                                    sessionId = sessionId,
                                    type = "command",
                                    command = toolCall.command,
                                    reason = toolCall.reason,
                                    commandStatus = "denied",
                                    isRisky = isHighRisk,
                                    timeoutSec = effectiveTimeout,
                                    cwd = _currentWorkingDir.value,
                                    sequence = cmdSeq
                                )
                            )
                            // Continue to next step so the model sees the denial turn and responds accordingly
                            continue
                        }
                        logPipelineStage("4_APPROVAL_GATE", "User approved command '${toolCall.command}'")
                        workingEntries.add(
                            TranscriptEntry(
                                id = entryId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "command",
                                command = toolCall.command,
                                reason = toolCall.reason,
                                commandStatus = "approved",
                                isRisky = isHighRisk,
                                timeoutSec = effectiveTimeout,
                                cwd = _currentWorkingDir.value,
                                sequence = cmdSeq
                            )
                        )
                    } else {
                        logPipelineStage("4_APPROVAL_GATE", "Auto-approved LOW-risk command '${toolCall.command}'")
                        workingEntries.add(
                            TranscriptEntry(
                                id = entryId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "command",
                                command = toolCall.command,
                                reason = toolCall.reason,
                                commandStatus = "approved",
                                isRisky = false,
                                timeoutSec = effectiveTimeout,
                                cwd = _currentWorkingDir.value,
                                sequence = cmdSeq
                            )
                        )
                    }

                    // Pre-execution Working Directory & Permission check (Fix #6)
                    val activeBackend = resolveActiveExecutionBackend()
                    if (activeBackend.backendType == ExecutionBackendType.TERMUX && circuitBreaker.isUnhealthy && !circuitBreaker.shouldAllowAutoRecheckBeforeAgentRun()) {
                        val structuredError = circuitBreaker.formatStructuredUnavailableToolResult()
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = structuredError,
                            noticeKind = "error",
                            cwd = _currentWorkingDir.value
                        )
                        val outSeq = nextSequence()
                        val outRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "output",
                            sequence = outSeq,
                            command = toolCall.command,
                            cwd = _currentWorkingDir.value,
                            exitCode = 1L,
                            output = structuredError,
                            redactions = 0L
                        )
                        val feedbackOutput = ToolCallParser.formatToolOutputForModelFeedback(
                            command = toolCall.command,
                            cwd = _currentWorkingDir.value,
                            exitCode = 1,
                            rawOutput = structuredError,
                            extraSecrets = userApiKeyStore.allSavedSecretsForRedaction(),
                            maxChars = 2000
                        )
                        workingEntries.add(
                            TranscriptEntry(
                                id = outRes.getOrNull() ?: "cb_out_$outSeq",
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "output",
                                command = toolCall.command,
                                cwd = _currentWorkingDir.value,
                                exitCode = 1L,
                                output = feedbackOutput,
                                sequence = outSeq
                            )
                        )
                        continue
                    }
                    if (activeBackend.backendType != ExecutionBackendType.TERMUX) {
                        when (val cwdCheck = shellBridge.validateWorkingDirectory(shellBridge.currentCwd)) {
                            is CwdValidationResult.MissingAllFilesAccess -> {
                                logPipelineStage("5_DROP_AT_CWD_PERMISSION", cwdCheck.message)
                                appendEntryOrLocal(
                                    sessionId = sessionId,
                                    type = "notice",
                                    sequence = nextSequence(),
                                    text = cwdCheck.message,
                                    noticeKind = "permission",
                                    cwd = shellBridge.currentCwd
                                )
                                return
                            }
                            is CwdValidationResult.InvalidOrNotWritable -> {
                                logPipelineStage("5_DROP_AT_CWD_NOT_WRITABLE", cwdCheck.message)
                                appendEntryOrLocal(
                                    sessionId = sessionId,
                                    type = "notice",
                                    sequence = nextSequence(),
                                    text = cwdCheck.message,
                                    noticeKind = "error",
                                    cwd = shellBridge.currentCwd
                                )
                                return
                            }
                            is CwdValidationResult.Valid -> {
                                _currentWorkingDir.value = cwdCheck.resolvedPath
                            }
                        }
                    }

                    // Stage 5: Active ExecutionBackend execution with timeout or JobManager dispatch
                    if (toolCall.actionKind == ToolActionKind.START_JOB) {
                        syncCurrentSessionStats(status = "exec")
                        val targetCmd = toolCall.command
                        val outcome = jobManager.startJob(
                            command = targetCmd,
                            cwd = _currentWorkingDir.value
                        )
                        val outSeq = nextSequence()
                        val (jobExitCode, jobOutputText) = if (outcome.success && outcome.job != null) {
                            val j = outcome.job
                            val portDesc = j.port?.let { ", port=$it" } ?: ""
                            val body = "Job ${j.id} started (state=${j.state.id}, pid=${j.pid}$portDesc).\n" +
                                "First ${outcome.initialRedactedLines.size} log lines:\n" +
                                outcome.initialRedactedLines.joinToString("\n")
                            0L to body
                        } else {
                            val code = outcome.job?.exitCode?.toLong() ?: 1L
                            val body = "Job failed or exited immediately (exit $code):\n" +
                                (outcome.initialRedactedLines.ifEmpty { listOf(outcome.message) }).joinToString("\n")
                            code to body
                        }
                        val outRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "output",
                            sequence = outSeq,
                            command = "start_job $targetCmd",
                            cwd = _currentWorkingDir.value,
                            exitCode = jobExitCode,
                            output = jobOutputText,
                            redactions = 0L
                        )
                        val outId = outRes.getOrNull() ?: "local_output_${step}_$outSeq"
                        val feedbackOutput = ToolCallParser.formatToolOutputForModelFeedback(
                            command = "start_job $targetCmd",
                            cwd = _currentWorkingDir.value,
                            exitCode = jobExitCode.toInt(),
                            rawOutput = jobOutputText,
                            extraSecrets = userApiKeyStore.allSavedSecretsForRedaction(),
                            maxChars = 2000
                        )
                        workingEntries.add(
                            TranscriptEntry(
                                id = outId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "output",
                                command = "start_job $targetCmd",
                                cwd = _currentWorkingDir.value,
                                exitCode = jobExitCode,
                                output = feedbackOutput,
                                sequence = outSeq
                            )
                        )
                        continue
                    } else if (toolCall.actionKind == ToolActionKind.JOB_STATUS) {
                        syncCurrentSessionStats(status = "exec")
                        val targetId = toolCall.jobId
                        val job = jobManager.jobs.value.firstOrNull { it.id == targetId }
                        val statusText = if (job != null) {
                            "Job ${job.id}: state=${job.state.id}, pid=${job.pid}, uptime=${job.uptimeSummary()}${job.port?.let { ", port=$it" } ?: ""}${job.exitCode?.let { ", exitCode=$it" } ?: ""}"
                        } else {
                            "Job '$targetId' not found."
                        }
                        val outSeq = nextSequence()
                        val outRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "output",
                            sequence = outSeq,
                            command = "job_status $targetId",
                            cwd = _currentWorkingDir.value,
                            exitCode = if (job != null) 0L else 1L,
                            output = statusText,
                            redactions = 0L
                        )
                        val outId = outRes.getOrNull() ?: "local_output_${step}_$outSeq"
                        workingEntries.add(
                            TranscriptEntry(
                                id = outId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "output",
                                command = "job_status $targetId",
                                cwd = _currentWorkingDir.value,
                                exitCode = if (job != null) 0L else 1L,
                                output = statusText,
                                sequence = outSeq
                            )
                        )
                        continue
                    } else if (toolCall.actionKind == ToolActionKind.JOB_LOGS) {
                        syncCurrentSessionStats(status = "exec")
                        val targetId = toolCall.jobId
                        val maxLines = toolCall.logLinesCount
                        val logs = jobManager.getJobLogs(targetId, maxLines)
                        val logsJoined = logs.joinToString("\n").ifBlank { "(no new logs)" }
                        val outSeq = nextSequence()
                        val outRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "output",
                            sequence = outSeq,
                            command = "job_logs $targetId $maxLines",
                            cwd = _currentWorkingDir.value,
                            exitCode = 0L,
                            output = logsJoined,
                            redactions = 0L
                        )
                        val outId = outRes.getOrNull() ?: "local_output_${step}_$outSeq"
                        val feedbackOutput = ToolCallParser.formatToolOutputForModelFeedback(
                            command = "job_logs $targetId",
                            cwd = _currentWorkingDir.value,
                            exitCode = 0,
                            rawOutput = logsJoined,
                            extraSecrets = userApiKeyStore.allSavedSecretsForRedaction(),
                            maxChars = 2000
                        )
                        workingEntries.add(
                            TranscriptEntry(
                                id = outId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "output",
                                command = "job_logs $targetId",
                                cwd = _currentWorkingDir.value,
                                exitCode = 0L,
                                output = feedbackOutput,
                                sequence = outSeq
                            )
                        )
                        continue
                    } else if (toolCall.actionKind == ToolActionKind.STOP_JOB) {
                        syncCurrentSessionStats(status = "exec")
                        val targetId = toolCall.jobId
                        val stopped = jobManager.stopJob(targetId) != null
                        val stopText = if (stopped) "Job $targetId stopped." else "Job $targetId not running or not found."
                        val outSeq = nextSequence()
                        val outRes = appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "output",
                            sequence = outSeq,
                            command = "stop_job $targetId",
                            cwd = _currentWorkingDir.value,
                            exitCode = if (stopped) 0L else 1L,
                            output = stopText,
                            redactions = 0L
                        )
                        val outId = outRes.getOrNull() ?: "local_output_${step}_$outSeq"
                        workingEntries.add(
                            TranscriptEntry(
                                id = outId,
                                userId = currentUserId,
                                sessionId = sessionId,
                                type = "output",
                                command = "stop_job $targetId",
                                cwd = _currentWorkingDir.value,
                                exitCode = if (stopped) 0L else 1L,
                                output = stopText,
                                sequence = outSeq
                            )
                        )
                        continue
                    }

                    syncCurrentSessionStats(status = "exec")
                    val execResult = executeOnActiveBackend(
                        command = toolCall.command,
                        requestedTimeoutSec = effectiveTimeout
                    )
                    _currentWorkingDir.value = execResult.cwd
                    logPipelineStage(
                        stage = "5_LOCAL_SHELL_BRIDGE",
                        detail = "Executed '${toolCall.command}' in '${execResult.cwd}', exitCode=${execResult.exitCode}, durationMs=${execResult.durationMs}"
                    )

                    val clippedOutput = NexusToolAndRedactor.clipOutput(execResult.combinedOutput)

                    // Stage 6: Output card with exit code
                    val outSeq = nextSequence()
                    val outRes = appendEntryOrLocal(
                        sessionId = sessionId,
                        type = "output",
                        sequence = outSeq,
                        command = toolCall.command,
                        cwd = execResult.cwd,
                        exitCode = execResult.exitCode.toLong(),
                        output = clippedOutput,
                        redactions = execResult.redactions.toLong()
                    )
                    val outId = outRes.getOrNull() ?: "local_output_${step}_$outSeq"
                    _runtimeLatenciesMs.update { it + (outId to execResult.durationMs) }
                    logPipelineStage("6_OUTPUT_CARD", "Appended output card id=$outId, exitCode=${execResult.exitCode}")

                    // Surface explicit error card with Retry on timeout or bridge error (Fix #8)
                    if (execResult.exitCode == 124) {
                        logPipelineStage("DROP_AT_BRIDGE_TIMEOUT", "Command '${toolCall.command}' timed out after ${execResult.timeoutSec}s")
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = "Command timed out after ${execResult.timeoutSec}s: `${toolCall.command}`. This long-running command can run as a background job.",
                            noticeKind = "timeout_bg_offer",
                            command = toolCall.command,
                            cwd = execResult.cwd
                        )
                        return
                    } else if ((activeBackend.backendType == ExecutionBackendType.ANDROID_SHELL && !shellBridge.isOnline) || execResult.stderr.startsWith("Bridge unreachable")) {
                        logPipelineStage("DROP_AT_BRIDGE_ERROR", execResult.stderr)
                        appendEntryOrLocal(
                            sessionId = sessionId,
                            type = "notice",
                            sequence = nextSequence(),
                            text = "Shell bridge error: ${execResult.stderr}",
                            noticeKind = "error",
                            cwd = execResult.cwd
                        )
                        return
                    }

                    if (activeBackend.backendType == ExecutionBackendType.TERMUX && execResult.exitCode != 0) {
                        if (FallbackPolicy.canOfferLimitedShellFallback(toolCall.command)) {
                            appendEntryOrLocal(
                                sessionId = sessionId,
                                type = "notice",
                                sequence = nextSequence(),
                                text = "Termux unavailable. Run this read-only command in the limited shell?",
                                noticeKind = "fallback_prompt",
                                command = toolCall.command,
                                cwd = execResult.cwd
                            )
                        }
                    }

                    // Stage 7: Feed redacted, 2000-char truncated output back as the next user turn
                    val feedbackOutput = ToolCallParser.formatToolOutputForModelFeedback(
                        command = toolCall.command,
                        cwd = execResult.cwd,
                        exitCode = execResult.exitCode,
                        rawOutput = clippedOutput,
                        extraSecrets = userApiKeyStore.allSavedSecretsForRedaction(),
                        maxChars = 2000
                    )
                    workingEntries.add(
                        TranscriptEntry(
                            id = outId,
                            userId = currentUserId,
                            sessionId = sessionId,
                            type = "output",
                            command = toolCall.command,
                            cwd = execResult.cwd,
                            exitCode = execResult.exitCode.toLong(),
                            output = feedbackOutput,
                            redactions = execResult.redactions.toLong(),
                            sequence = outSeq
                        )
                    )
                    logPipelineStage("7_FEEDBACK_TO_MODEL", "Fed output (${feedbackOutput.length} chars) back for step ${step + 2}")

                    syncCurrentSessionStats(
                        status = "stream",
                        addCommands = 1L,
                        addRedactions = execResult.redactions.toLong()
                    )
                }
            }
        }

        // Step cap reached while commands were still being executed (Fix #3 & #8)
        logPipelineStage("STEP_LIMIT_REACHED", "Reached maxSteps=$maxSteps for provider=$activeProv")
        appendEntryOrLocal(
            sessionId = sessionId,
            type = "notice",
            sequence = nextSequence(),
            text = when {
                activeProv == "local" ->
                    "Step limit reached (${MultiPlatformAiEngine.LOCAL_MAX_AGENT_STEPS} steps/turn for on-device model). Tap Retry or send another prompt to continue."
                isGuestMode ->
                    "Step limit reached ($GUEST_MAX_STEPS steps in Guest Mode). Sign in with Google for up to 12 autonomous steps per turn."
                else ->
                    "Step limit reached ($maxSteps steps). Tap Retry or send another directive to continue."
            },
            noticeKind = "step_limit",
            cwd = shellBridge.currentCwd
        )
    }

    fun analyzeEntryWithGemini(entry: TranscriptEntry) {
        if (_isAnalyzing.value) return
        val activeProv = selectedProvider.value
        val activeMod = selectedModelId.value
        if (activeProv != "local" && !userApiKeyStore.hasApiKeyFor(activeProv, activeMod)) {
            val provLabel = NexusToolAndRedactor.providerDisplayLabel(activeProv)
            _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                provider = activeProv,
                modelId = activeMod,
                providerDisplayName = provLabel,
                message = "Add your $provLabel API key in Settings to run AI command audit."
            )
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val prompt = when (entry.type) {
                    "output" -> """
                        Analyze this Linux shell command execution output from Nexus Agent v5:
                        Command: ${entry.command.ifBlank { "(shell command)" }}
                        Working Directory: ${entry.cwd}
                        Exit Code: ${entry.exitCode}
                        Redacted Secrets Count: ${entry.redactions}
                        Output:
                        ${entry.output}
                    """.trimIndent()
                    "command" -> """
                        Audit this proposed Linux shell command before execution:
                        Command: ${entry.command}
                        Reason: ${entry.reason}
                        Working Directory: ${entry.cwd}
                        Flagged Risky: ${entry.isRisky}
                    """.trimIndent()
                    else -> """
                        Analyze this Nexus session entry and provide technical insights:
                        ${entry.text}
                    """.trimIndent()
                }

                val markdownReply = withTimeout(60_000L) {
                    multiPlatformEngine.generateSingleTurnText(
                        provider = activeProv,
                        modelId = activeMod,
                        prompt = prompt,
                        currentCwd = shellBridge.currentCwd
                    )
                }

                val analysis = AiCommandAnalysis(
                    entryId = entry.id,
                    summary = markdownReply.lines().firstOrNull { it.isNotBlank() } ?: "Analysis complete.",
                    rootCauseOrImpact = markdownReply,
                    securityNotes = if (entry.isRisky) "High-risk command flagged by Nexus security gate." else "No critical anomalies detected.",
                    suggestedCommand = ""
                )
                _activeAnalysis.value = analysis

                appendEntryOrLocal(
                    sessionId = activeEffectiveSessionId(),
                    type = "analysis",
                    sequence = nextSequence(),
                    text = "**AI Audit (${NexusToolAndRedactor.providerDisplayLabel(activeProv)} · $activeMod)**\n\n$markdownReply",
                    cwd = shellBridge.currentCwd
                )
            } catch (e: MissingApiKeyException) {
                _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                    provider = e.provider,
                    modelId = e.modelId,
                    providerDisplayName = e.providerDisplayName,
                    message = e.message ?: "Missing API key."
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Analysis timed out", e)
                _uiBannerMessage.value = "Analysis timed out after 60s."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                _uiBannerMessage.value = e.localizedMessage ?: "Analysis failed."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun enhancePromptWithFlashLite() {
        val currentDraft = _promptInput.value.trim()
        if (currentDraft.isBlank() || _isAnalyzing.value) return
        val activeProv = selectedProvider.value
        val activeMod = selectedModelId.value
        if (activeProv != "local" && !userApiKeyStore.hasApiKeyFor(activeProv, activeMod)) {
            val provLabel = NexusToolAndRedactor.providerDisplayLabel(activeProv)
            _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                provider = activeProv,
                modelId = activeMod,
                providerDisplayName = provLabel,
                message = "Add your $provLabel API key in Settings to enhance prompts."
            )
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val prompt = """
                    Rewrite the following user request into a clear, precise 1-sentence directive for a Linux Shell Agent (working directory: ${shellBridge.currentCwd}).
                    Return ONLY the improved directive text (no quotes or preamble).
                    User draft: $currentDraft
                """.trimIndent()
                val improved = withTimeout(30_000L) {
                    multiPlatformEngine.generateSingleTurnText(
                        provider = activeProv,
                        modelId = activeMod,
                        prompt = prompt,
                        currentCwd = shellBridge.currentCwd
                    )
                }.let { stripToolTagsForDisplay(it) }
                if (improved.isNotBlank()) {
                    _promptInput.value = improved
                }
            } catch (e: MissingApiKeyException) {
                _missingApiKeyPrompt.value = MissingApiKeyPrompt(
                    provider = e.provider,
                    modelId = e.modelId,
                    providerDisplayName = e.providerDisplayName,
                    message = e.message ?: "Missing API key."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prompt enhancement failed", e)
                _uiBannerMessage.value = e.localizedMessage ?: "Could not enhance prompt."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private val TOOL_BLOCK_REGEX = Regex("""<nexus_tool>\s*(\{[\s\S]*?\})\s*</nexus_tool>""", RegexOption.IGNORE_CASE)

    private fun stripToolTagsForDisplay(raw: String): String {
        val withoutComplete = TOOL_BLOCK_REGEX.replace(raw, "")
        val partialIdx = withoutComplete.indexOf("<nexus_tool", ignoreCase = true)
        return (if (partialIdx >= 0) withoutComplete.substring(0, partialIdx) else withoutComplete).trim()
    }
}
