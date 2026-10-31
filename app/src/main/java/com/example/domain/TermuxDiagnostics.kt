package com.example.domain

import android.content.Context
import android.os.PowerManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val Context.termuxSetupWizardDataStore by preferencesDataStore(name = "nexus_termux_wizard_prefs_v5")

enum class DiagnosticStatus {
    OK,
    MISSING,
    ERROR,
    UNKNOWN
}

enum class DiagnosticFixAction {
    NONE,
    OPEN_GITHUB_RELEASES,
    OPEN_FDROID,
    REQUEST_RUN_COMMAND_PERMISSION,
    REQUEST_ALL_FILES_ACCESS,
    OPEN_APP_SETTINGS,
    OPEN_APP_PERMISSION_SETTINGS,
    COPY_ALLOW_EXTERNAL_APPS_CMD,
    OPEN_TERMUX,
    OPEN_TERMUX_APP,
    OPEN_BATTERY_OPTIMIZATION_SETTINGS,
    COPY_STORAGE_SETUP_CMD,
    COPY_TERMUX_SETUP_STORAGE_CMD,
    PROPOSE_INSTALL_TOOLCHAIN,
    RETRY_PACKAGES_INDIVIDUALLY,
    RUN_SELF_TEST
}

data class DiagnosticCheckItem(
    val id: String,
    val title: String,
    val status: DiagnosticStatus,
    val plainMessage: String,
    val probableCause: String = "",
    val fixAction: DiagnosticFixAction = DiagnosticFixAction.NONE,
    val fixActionLabel: String = "",
    val secondaryAction: DiagnosticFixAction = DiagnosticFixAction.NONE,
    val secondaryActionLabel: String = "",
    val commandSnippet: String? = null,
    val durationMs: Long = 10L
) {
    val message: String get() = plainMessage
    val fixLabel: String get() = fixActionLabel
    val secondaryFixAction: DiagnosticFixAction get() = secondaryAction
    val secondaryFixLabel: String get() = secondaryActionLabel
}

typealias DiagnosticCheckResult = DiagnosticCheckItem

data class SelfTestStepResult(
    val stepId: String,
    val title: String,
    val passed: Boolean,
    val durationMs: Long,
    val detail: String
)

data class SelfTestReport(
    val passed: Boolean,
    val totalDurationMs: Long,
    val steps: List<SelfTestStepResult>,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val allPassed: Boolean get() = passed
}

data class TermuxDiagnosticsReport(
    val checks: List<DiagnosticCheckItem>,
    val detectedTools: List<DetectedToolInfo>,
    val isFullyReady: Boolean,
    val needsSetupBadge: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class WorkspaceProjectType(
    val id: String,
    val label: String,
    val badge: String,
    val markerFile: String = ""
) {
    NODE_JS("nodejs", "Node.js Project", "Node.js", "package.json"),
    PYTHON("python", "Python Project", "Python", "requirements.txt"),
    STATIC_WEB("static_web", "Static HTML Site", "HTML/Web", "index.html"),
    GIT_REPO("git_repo", "Git Repository", "Git", ".git"),
    GENERAL("general", "General Workspace", "Shell", "workspace");

    val title: String get() = label
}

data class ProjectRecipeStep(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
    val verifyCommand: String,
    val isVerified: Boolean = false,
    val runsAsBackgroundJob: Boolean = ServerLogAnalyzer.shouldRunAsBackgroundJob(command)
)

typealias ProjectRecipeCard = ProjectRecipeStep

data class ProjectSetupGuideReport(
    val detectedTypes: List<WorkspaceProjectType>,
    val prerequisites: List<Pair<String, Boolean>>,
    val recipes: List<ProjectRecipeStep>
)

typealias WorkspaceProjectGuide = ProjectSetupGuideReport

data class TermuxWizardState(
    val currentStepIndex: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val keepTermuxAwakeWhileJobsRun: Boolean = true,
    val pendingCorrelationIds: Set<String> = emptySet()
) {
    val isCompleted: Boolean get() = completed
    val isSkipped: Boolean get() = skipped
    val keepTermuxAwakeDuringJobs: Boolean get() = keepTermuxAwakeWhileJobsRun
}

typealias TermuxWizardPersistedState = TermuxWizardState

class TermuxWizardStore(private val context: Context) {
    private val keyStepIndex = intPreferencesKey("wizard_step_index")
    private val keyCompleted = booleanPreferencesKey("wizard_completed")
    private val keySkipped = booleanPreferencesKey("wizard_skipped")
    private val keyKeepAwake = booleanPreferencesKey("keep_termux_awake_while_jobs_run")
    private val keyPendingCorrelations = stringPreferencesKey("pending_correlation_ids")

    val stateFlow: Flow<TermuxWizardState> = context.termuxSetupWizardDataStore.data.map { prefs ->
        val rawIds = prefs[keyPendingCorrelations].orEmpty()
        val parsedIds = rawIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        TermuxWizardState(
            currentStepIndex = prefs[keyStepIndex] ?: 0,
            completed = prefs[keyCompleted] ?: false,
            skipped = prefs[keySkipped] ?: false,
            keepTermuxAwakeWhileJobsRun = prefs[keyKeepAwake] ?: true,
            pendingCorrelationIds = parsedIds
        )
    }

    suspend fun getState(): TermuxWizardState = stateFlow.first()

    suspend fun updateStep(stepIndex: Int) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            prefs[keyStepIndex] = stepIndex.coerceAtLeast(0)
        }
    }

    suspend fun markCompleted(completed: Boolean = true) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            prefs[keyCompleted] = completed
            if (completed) prefs[keySkipped] = false
        }
    }

    suspend fun markSkipped(skipped: Boolean = true) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            prefs[keySkipped] = skipped
        }
    }

    suspend fun setKeepTermuxAwake(enabled: Boolean) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            prefs[keyKeepAwake] = enabled
        }
    }

    suspend fun registerPendingCorrelationId(correlationId: String) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            val current = prefs[keyPendingCorrelations].orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            current.add(correlationId)
            prefs[keyPendingCorrelations] = current.joinToString(",")
        }
    }

    suspend fun recordPendingCorrelationId(correlationId: String) {
        registerPendingCorrelationId(correlationId)
    }

    suspend fun removePendingCorrelationId(correlationId: String) {
        context.termuxSetupWizardDataStore.edit { prefs ->
            val current = prefs[keyPendingCorrelations].orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != correlationId }
            prefs[keyPendingCorrelations] = current.joinToString(",")
        }
    }

    suspend fun consumeStaleCorrelationIdsOnRestart(): Set<String> {
        var stale = emptySet<String>()
        context.termuxSetupWizardDataStore.edit { prefs ->
            stale = prefs[keyPendingCorrelations].orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            prefs[keyPendingCorrelations] = ""
        }
        return stale
    }
}

interface TermuxEnvironmentProbe {
    fun isTermuxPackageInstalled(): Boolean
    fun isRunCommandPermissionGranted(): Boolean
    fun isTermuxBatteryOptimizationExempt(): Boolean
    fun isNexusAllFilesAccessGranted(): Boolean = true
    fun isSharedStorageWritable(workspacePath: String): Boolean
    suspend fun pingTermuxReachability(timeoutSec: Long = 10L): Pair<Boolean, String>
    suspend fun probeInstalledTools(): List<DetectedToolInfo>
    suspend fun runRawCommand(cmd: String, cwd: String, timeoutSec: Long = 30L): TermuxPluginResult
}

class TermuxDiagnostics(
    private val probe: TermuxEnvironmentProbe,
    private val cacheTtlMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val TERMUX_FDROID_URL = "https://f-droid.org/en/packages/com.termux/"
        const val TERMUX_GITHUB_RELEASES_URL = "https://github.com/termux/termux-app/releases"
        const val IDEMPOTENT_ALLOW_EXTERNAL_APPS_CMD = TermuxBackend.ONE_TIME_SETUP_COMMAND
        const val INSTALL_TOOLCHAIN_BATCH_CMD = TermuxBackend.INSTALL_TOOLCHAIN_COMMAND
    }

    private val mutex = Mutex()
    private var cachedReport: TermuxDiagnosticsReport? = null

    constructor(context: Context, termuxBackend: TermuxBackend) : this(
        probe = AndroidTermuxEnvironmentProbe(context.applicationContext, termuxBackend)
    )

    fun invalidateCache() {
        cachedReport = null
    }

    suspend fun runDiagnostics(
        workspaceCwd: String,
        workspaceMode: WorkspaceMode,
        forceRefresh: Boolean = false
    ): TermuxDiagnosticsReport = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()
            val existing = cachedReport
            if (!forceRefresh && existing != null && (now - existing.timestampMs) < cacheTtlMs) {
                return@withLock existing
            }

            val checks = mutableListOf<DiagnosticCheckItem>()

            // 1. Termux Installed check
            val t0 = clock()
            val installed = runCatching { probe.isTermuxPackageInstalled() }.getOrDefault(false)
            val d0 = (clock() - t0).coerceAtLeast(1L)
            checks.add(
                if (installed) {
                    DiagnosticCheckItem(
                        id = "termux_installed",
                        title = "Termux Installed",
                        status = DiagnosticStatus.OK,
                        plainMessage = "Termux package (com.termux) is installed.",
                        durationMs = d0
                    )
                } else {
                    DiagnosticCheckItem(
                        id = "termux_installed",
                        title = "Termux Installed",
                        status = DiagnosticStatus.MISSING,
                        plainMessage = "Termux is not installed on this device.",
                        probableCause = "Install Termux and any add-ons from the SAME source (GitHub Releases or F-Droid) to avoid signature conflicts. Do NOT use the outdated Google Play Store build.",
                        fixAction = DiagnosticFixAction.OPEN_GITHUB_RELEASES,
                        fixActionLabel = "GitHub Releases",
                        secondaryAction = DiagnosticFixAction.OPEN_FDROID,
                        secondaryActionLabel = "F-Droid",
                        durationMs = d0
                    )
                }
            )

            // 2. RUN_COMMAND Permission check
            val t1 = clock()
            val permGranted = if (installed) {
                runCatching { probe.isRunCommandPermissionGranted() }.getOrDefault(false)
            } else {
                false
            }
            val d1 = (clock() - t1).coerceAtLeast(1L)
            checks.add(
                when {
                    !installed -> DiagnosticCheckItem(
                        id = "run_command_permission",
                        title = "RUN_COMMAND Permission",
                        status = DiagnosticStatus.UNKNOWN,
                        plainMessage = "Waiting for Termux installation before requesting permission.",
                        durationMs = d1
                    )
                    permGranted -> DiagnosticCheckItem(
                        id = "run_command_permission",
                        title = "RUN_COMMAND Permission",
                        status = DiagnosticStatus.OK,
                        plainMessage = "com.termux.permission.RUN_COMMAND is granted.",
                        durationMs = d1
                    )
                    else -> DiagnosticCheckItem(
                        id = "run_command_permission",
                        title = "RUN_COMMAND Permission",
                        status = DiagnosticStatus.MISSING,
                        plainMessage = "Nexus needs permission to send commands to Termux.",
                        probableCause = "Android requires explicit runtime approval for com.termux.permission.RUN_COMMAND. If permanently denied, grant it in App Settings > Permissions.",
                        fixAction = DiagnosticFixAction.REQUEST_RUN_COMMAND_PERMISSION,
                        fixActionLabel = "Grant permission",
                        secondaryAction = DiagnosticFixAction.OPEN_APP_PERMISSION_SETTINGS,
                        secondaryActionLabel = "App settings",
                        durationMs = d1
                    )
                }
            )

            // 3 & 4. allow-external-apps & Termux Reachability (`echo nexus_ok` with 10s timeout)
            val t2 = clock()
            var reachableOk = false
            var externalAppsOk = false
            var pingDetail = ""

            if (installed && permGranted) {
                val (ok, detail) = runCatching { probe.pingTermuxReachability(10L) }
                    .getOrDefault(false to "Termux ping failed")
                pingDetail = detail
                if (ok) {
                    reachableOk = true
                    externalAppsOk = true
                }
            }
            val d2 = (clock() - t2).coerceAtLeast(1L)

            checks.add(
                when {
                    !installed || !permGranted -> DiagnosticCheckItem(
                        id = "allow_external_apps",
                        title = "allow-external-apps Enabled",
                        status = DiagnosticStatus.UNKNOWN,
                        plainMessage = "Inferred via test command once Termux is installed and permission is granted.",
                        commandSnippet = TermuxBackend.ONE_TIME_SETUP_COMMAND,
                        fixAction = DiagnosticFixAction.COPY_ALLOW_EXTERNAL_APPS_CMD,
                        fixActionLabel = "Copy command",
                        durationMs = d2
                    )
                    externalAppsOk -> DiagnosticCheckItem(
                        id = "allow_external_apps",
                        title = "allow-external-apps Enabled",
                        status = DiagnosticStatus.OK,
                        plainMessage = "Termux accepted external command execution (`echo nexus_ok`).",
                        durationMs = d2
                    )
                    else -> DiagnosticCheckItem(
                        id = "allow_external_apps",
                        title = "allow-external-apps Enabled",
                        status = DiagnosticStatus.MISSING,
                        plainMessage = "Termux did not accept external commands ($pingDetail).",
                        probableCause = "Set allow-external-apps = true in ~/.termux/termux.properties, then run termux-reload-settings or fully close and reopen Termux.",
                        fixAction = DiagnosticFixAction.COPY_ALLOW_EXTERNAL_APPS_CMD,
                        fixActionLabel = "Copy command",
                        secondaryAction = DiagnosticFixAction.OPEN_TERMUX,
                        secondaryActionLabel = "Open Termux",
                        commandSnippet = TermuxBackend.ONE_TIME_SETUP_COMMAND,
                        durationMs = d2
                    )
                }
            )

            checks.add(
                when {
                    !installed || !permGranted -> DiagnosticCheckItem(
                        id = "termux_reachable",
                        title = "Termux Service Reachable",
                        status = DiagnosticStatus.UNKNOWN,
                        plainMessage = "Waiting for Termux installation and RUN_COMMAND permission.",
                        durationMs = d2
                    )
                    reachableOk -> DiagnosticCheckItem(
                        id = "termux_reachable",
                        title = "Termux Service Reachable",
                        status = DiagnosticStatus.OK,
                        plainMessage = "Termux responded to `echo nexus_ok` ($pingDetail).",
                        durationMs = d2
                    )
                    else -> DiagnosticCheckItem(
                        id = "termux_reachable",
                        title = "Termux Service Reachable",
                        status = DiagnosticStatus.ERROR,
                        plainMessage = "No reply to `echo nexus_ok` within 10s.",
                        probableCause = "Termux was killed by Android or has not been started yet, or allow-external-apps is off.",
                        fixAction = DiagnosticFixAction.OPEN_TERMUX,
                        fixActionLabel = "Open Termux",
                        durationMs = d2
                    )
                }
            )

            // 5. Battery optimization check
            val t3 = clock()
            val batteryExempt = if (installed) {
                runCatching { probe.isTermuxBatteryOptimizationExempt() }.getOrDefault(false)
            } else {
                false
            }
            val d3 = (clock() - t3).coerceAtLeast(1L)
            checks.add(
                when {
                    !installed -> DiagnosticCheckItem(
                        id = "battery_optimization",
                        title = "Battery Optimization Exemption",
                        status = DiagnosticStatus.UNKNOWN,
                        plainMessage = "Install Termux first to check battery optimization status.",
                        durationMs = d3
                    )
                    batteryExempt -> DiagnosticCheckItem(
                        id = "battery_optimization",
                        title = "Battery Optimization Exemption",
                        status = DiagnosticStatus.OK,
                        plainMessage = "Termux is exempt from Android battery restrictions.",
                        durationMs = d3
                    )
                    else -> DiagnosticCheckItem(
                        id = "battery_optimization",
                        title = "Battery Optimization Exemption",
                        status = DiagnosticStatus.MISSING,
                        plainMessage = "Termux is subject to background battery restrictions.",
                        probableCause = "Android phantom/background killers may terminate long-running npm/python builds or servers unless Termux is set to Unrestricted battery usage.",
                        fixAction = DiagnosticFixAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS,
                        fixActionLabel = "Battery settings",
                        durationMs = d3
                    )
                }
            )

            // 6. Nexus All Files Access + Termux Shared Storage check
            val t4 = clock()
            if (workspaceMode == WorkspaceMode.SHARED_STORAGE) {
                val nexusAllFilesOk = runCatching { probe.isNexusAllFilesAccessGranted() }.getOrDefault(true)
                val writable = runCatching { probe.isSharedStorageWritable(workspaceCwd) }.getOrDefault(false)
                val d4 = (clock() - t4).coerceAtLeast(1L)
                if (!nexusAllFilesOk) {
                    checks.add(
                        DiagnosticCheckItem(
                            id = "nexus_all_files_access",
                            title = "Nexus All Files Access",
                            status = DiagnosticStatus.MISSING,
                            plainMessage = "Nexus needs All Files Access to manage ${WorkspacePaths.DEFAULT_SHARED_ROOT}.",
                            probableCause = "Grant 'All files access' to Nexus in Android Settings so both Nexus and Termux share ${WorkspacePaths.DEFAULT_SHARED_ROOT}.",
                            fixAction = DiagnosticFixAction.REQUEST_ALL_FILES_ACCESS,
                            fixActionLabel = "Grant All Files Access",
                            durationMs = d4
                        )
                    )
                } else {
                    checks.add(
                        DiagnosticCheckItem(
                            id = "nexus_all_files_access",
                            title = "Nexus All Files Access",
                            status = DiagnosticStatus.OK,
                            plainMessage = "Nexus has All Files Access to ${WorkspacePaths.DEFAULT_SHARED_ROOT}.",
                            durationMs = d4
                        )
                    )
                }

                checks.add(
                    if (writable) {
                        DiagnosticCheckItem(
                            id = "shared_storage",
                            title = "Termux Shared Storage (termux-setup-storage)",
                            status = DiagnosticStatus.OK,
                            plainMessage = "Shared workspace is writable (${NexusToolAndRedactor.shortenPathMiddle(workspaceCwd, 34)}).",
                            durationMs = d4
                        )
                    } else {
                        DiagnosticCheckItem(
                            id = "shared_storage",
                            title = "Termux Shared Storage (termux-setup-storage)",
                            status = DiagnosticStatus.MISSING,
                            plainMessage = "Shared storage workspace is not writable by Termux.",
                            probableCause = "1. Copy `termux-setup-storage`. 2. Open Termux, paste and run it, then tap 'Allow' on the interactive Android storage dialog. 3. Return here to verify.",
                            fixAction = DiagnosticFixAction.COPY_TERMUX_SETUP_STORAGE_CMD,
                            fixActionLabel = "Copy command",
                            secondaryAction = DiagnosticFixAction.OPEN_TERMUX,
                            secondaryActionLabel = "Open Termux",
                            commandSnippet = "termux-setup-storage",
                            durationMs = d4
                        )
                    }
                )
            } else {
                val d4 = (clock() - t4).coerceAtLeast(1L)
                checks.add(
                    DiagnosticCheckItem(
                        id = "shared_storage",
                        title = "Termux Home Workspace",
                        status = DiagnosticStatus.OK,
                        plainMessage = "Using native Termux home (${TermuxBackend.TERMUX_HOME_PATH}) with full symlink & chmod support and source mirroring to ${WorkspacePaths.DEFAULT_SHARED_ROOT}.",
                        durationMs = d4
                    )
                )
            }

            // 7. Tools check (node, npm, git, python, pip via `command -v` and `--version`)
            val t5 = clock()
            val tools = if (reachableOk) {
                runCatching { probe.probeInstalledTools() }.getOrElse {
                    defaultMissingTools()
                }
            } else {
                defaultMissingTools()
            }
            val d5 = (clock() - t5).coerceAtLeast(1L)

            val missingTools = tools.filter { !it.isAvailable }
            val installedTools = tools.filter { it.isAvailable }

            checks.add(
                when {
                    !reachableOk -> DiagnosticCheckItem(
                        id = "developer_tools",
                        title = "Developer Toolchain (node, npm, git, python, pip)",
                        status = DiagnosticStatus.UNKNOWN,
                        plainMessage = "Connect Termux first to probe installed developer tools.",
                        durationMs = d5
                    )
                    missingTools.isEmpty() -> DiagnosticCheckItem(
                        id = "developer_tools",
                        title = "Developer Toolchain (node, npm, git, python, pip)",
                        status = DiagnosticStatus.OK,
                        plainMessage = "All 5 tools ready: " + installedTools.joinToString(", ") {
                            "${it.name} (${it.version ?: "ok"})"
                        },
                        durationMs = d5
                    )
                    else -> DiagnosticCheckItem(
                        id = "developer_tools",
                        title = "Developer Toolchain (${installedTools.size}/${tools.size} ready)",
                        status = DiagnosticStatus.MISSING,
                        plainMessage = "Missing: ${missingTools.joinToString(", ") { it.name }}.",
                        probableCause = "Tap 'Install' to propose `${TermuxBackend.INSTALL_TOOLCHAIN_COMMAND}` through the command approval gate.",
                        fixAction = DiagnosticFixAction.PROPOSE_INSTALL_TOOLCHAIN,
                        fixActionLabel = "Install",
                        commandSnippet = TermuxBackend.INSTALL_TOOLCHAIN_COMMAND,
                        durationMs = d5
                    )
                }
            )

            val fullyReady = installed && permGranted && externalAppsOk && reachableOk && missingTools.isEmpty()
            val report = TermuxDiagnosticsReport(
                checks = checks,
                detectedTools = tools,
                isFullyReady = fullyReady,
                needsSetupBadge = !fullyReady,
                timestampMs = now
            )
            cachedReport = report
            report
        }
    }

    suspend fun runEndToEndSelfTest(
        workspaceCwd: String,
        onStepProgress: (List<SelfTestStepResult>) -> Unit = {}
    ): SelfTestReport = withContext(Dispatchers.IO) {
        val overallStart = clock()
        val results = mutableListOf<SelfTestStepResult>()
        val targetCwd = workspaceCwd.ifBlank { TermuxBackend.TERMUX_HOME_DIR }
        val tempDir = "$targetCwd/.nexus_selftest_${clock() % 100_000}"
        val escapedTempDir = tempDir.replace("'", "'\\''")

        suspend fun runStep(id: String, title: String, cmd: String): Boolean {
            val stepStart = clock()
            val res = runCatching { probe.runRawCommand(cmd, targetCwd, 25L) }.getOrElse { e ->
                TermuxPluginResult("", e.localizedMessage ?: "error", 1, -1, e.localizedMessage)
            }
            val elapsed = (clock() - stepStart).coerceAtLeast(1L)
            val passed = res.exitCode == 0 && res.errCode == 0
            val detail = if (passed) {
                res.stdout.trim().lines().firstOrNull()?.take(80).orEmpty().ifBlank { "OK (${elapsed}ms)" }
            } else {
                (res.stderr.ifBlank { res.errMsg.orEmpty() }).trim().lines().lastOrNull()?.take(100)
                    ?: "Failed with exit ${res.exitCode}"
            }
            results.add(
                SelfTestStepResult(
                    stepId = id,
                    title = title,
                    passed = passed,
                    durationMs = elapsed,
                    detail = detail
                )
            )
            onStepProgress(results.toList())
            return passed
        }

        runStep(
            id = "write_workspace_file",
            title = "1. Write file in workspace",
            cmd = "mkdir -p '$escapedTempDir' && printf 'nexus-selftest-ok\\n' > '$escapedTempDir/hello.txt' && cat '$escapedTempDir/hello.txt'"
        )
        runStep(
            id = "node_eval",
            title = "2. Execute Node.js (`node -e`)",
            cmd = "node -e \"console.log('node-ok-' + process.version)\""
        )
        runStep(
            id = "git_init_temp",
            title = "3. Initialize Git repo (`git init`)",
            cmd = "git init '$escapedTempDir/repo' && git -C '$escapedTempDir/repo' status --short"
        )
        runStep(
            id = "cleanup_temp",
            title = "4. Clean up temporary self-test artifacts",
            cmd = "rm -rf '$escapedTempDir' && echo 'cleaned'"
        )

        val totalMs = (clock() - overallStart).coerceAtLeast(1L)
        SelfTestReport(
            passed = results.all { it.passed },
            totalDurationMs = totalMs,
            steps = results
        )
    }

    suspend fun retryToolchainPackagesIndividually(
        workspaceCwd: String,
        packages: List<String> = listOf("nodejs", "git", "python")
    ): Map<String, Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        val outcomes = LinkedHashMap<String, Pair<Boolean, String>>()
        for (pkg in packages) {
            val res = runCatching {
                probe.runRawCommand("pkg install -y $pkg", workspaceCwd, 300L)
            }.getOrElse { e ->
                TermuxPluginResult("", e.localizedMessage ?: "error", 1, -1, e.localizedMessage)
            }
            val ok = res.exitCode == 0 && res.errCode == 0
            val detail = if (ok) {
                "Installed $pkg"
            } else {
                (res.stderr.ifBlank { res.stdout }).lines().lastOrNull { it.isNotBlank() }?.take(100)
                    ?: "Exit code ${res.exitCode}"
            }
            outcomes[pkg] = ok to detail
        }
        invalidateCache()
        outcomes
    }

    fun inspectWorkspaceProjectGuide(
        workspaceCwd: String,
        detectedTools: List<DetectedToolInfo>,
        verifiedRecipeIds: Set<String> = emptySet()
    ): ProjectSetupGuideReport {
        val dir = File(workspaceCwd)
        val types = mutableListOf<WorkspaceProjectType>()
        if (File(dir, "package.json").exists()) types.add(WorkspaceProjectType.NODE_JS)
        if (File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists()) {
            types.add(WorkspaceProjectType.PYTHON)
        }
        if (File(dir, "index.html").exists()) types.add(WorkspaceProjectType.STATIC_WEB)
        if (File(dir, ".git").exists()) types.add(WorkspaceProjectType.GIT_REPO)
        if (types.isEmpty()) types.add(WorkspaceProjectType.GENERAL)

        val toolMap = detectedTools.associateBy({ it.name.lowercase() }, { it.isAvailable })
        val prereqs = mutableListOf<Pair<String, Boolean>>()
        prereqs.add("Workspace directory exists (${NexusToolAndRedactor.shortenPathMiddle(workspaceCwd, 28)})" to dir.exists())
        if (types.contains(WorkspaceProjectType.NODE_JS)) {
            prereqs.add("Node.js & npm installed" to (toolMap["node"] == true && toolMap["npm"] == true))
            prereqs.add("node_modules installed" to File(dir, "node_modules").exists())
        }
        if (types.contains(WorkspaceProjectType.PYTHON)) {
            prereqs.add("Python & pip installed" to (toolMap["python"] == true && toolMap["pip"] == true))
            prereqs.add("Virtual environment (.venv) created" to File(dir, ".venv").exists())
        }
        if (types.contains(WorkspaceProjectType.GIT_REPO)) {
            prereqs.add("Git installed" to (toolMap["git"] == true))
        }

        val recipes = listOf(
            ProjectRecipeStep(
                id = "recipe_node_express",
                title = "Node / Express Server",
                description = "Initialize package.json, install Express, and start an HTTP server on port 3000.",
                command = "npm init -y && npm install express && node -e \"const express=require('express');const app=express();app.get('/',(req,res)=>res.send('Nexus Express Ready'));app.listen(3000,()=>console.log('Listening on http://127.0.0.1:3000'));\"",
                verifyCommand = "test -f package.json && node -v",
                isVerified = verifiedRecipeIds.contains("recipe_node_express"),
                runsAsBackgroundJob = true
            ),
            ProjectRecipeStep(
                id = "recipe_static_site",
                title = "Static Site Server (python -m http.server)",
                description = "Serve index.html and static files in the current workspace over localhost:8000.",
                command = "python3 -m http.server 8000 --bind 127.0.0.1",
                verifyCommand = "python3 --version",
                isVerified = verifiedRecipeIds.contains("recipe_static_site"),
                runsAsBackgroundJob = true
            ),
            ProjectRecipeStep(
                id = "recipe_flask_server",
                title = "Python Flask Server",
                description = "Create a virtual environment, install Flask, and start a local development server on port 5000.",
                command = "python3 -m venv .venv && . .venv/bin/activate && pip install flask && python3 -c \"from flask import Flask; app=Flask(__name__); app.route('/')(lambda: 'Nexus Flask Ready'); app.run(host='127.0.0.1', port=5000)\"",
                verifyCommand = "test -d .venv && .venv/bin/python -c 'import flask'",
                isVerified = verifiedRecipeIds.contains("recipe_flask_server"),
                runsAsBackgroundJob = true
            ),
            ProjectRecipeStep(
                id = "recipe_git_clone_install",
                title = "Git Clone & Install Dependencies",
                description = "Initialize or verify Git repository configuration and install project dependencies.",
                command = "git init && git status",
                verifyCommand = "test -d .git && git status --short",
                isVerified = verifiedRecipeIds.contains("recipe_git_clone_install"),
                runsAsBackgroundJob = false
            ),
            ProjectRecipeStep(
                id = "recipe_python_venv",
                title = "Python Virtual Environment (.venv) Setup",
                description = "Create an isolated PEP 668 compliant virtual environment in .venv and upgrade pip.",
                command = "python3 -m venv .venv && .venv/bin/pip install --upgrade pip",
                verifyCommand = "test -x .venv/bin/python && .venv/bin/python --version",
                isVerified = verifiedRecipeIds.contains("recipe_python_venv"),
                runsAsBackgroundJob = false
            )
        )

        return ProjectSetupGuideReport(
            detectedTypes = types,
            prerequisites = prereqs,
            recipes = recipes
        )
    }

    private fun defaultMissingTools(): List<DetectedToolInfo> {
        return listOf("node", "npm", "git", "python", "pip").map {
            DetectedToolInfo(name = it, binaryName = it, isAvailable = false)
        }
    }
}

class AndroidTermuxEnvironmentProbe(
    private val context: Context,
    private val termuxBackend: TermuxBackend
) : TermuxEnvironmentProbe {
    override fun isTermuxPackageInstalled(): Boolean = termuxBackend.isTermuxPackageInstalled()

    override fun isRunCommandPermissionGranted(): Boolean = termuxBackend.isRunCommandPermissionGranted()

    override fun isTermuxBatteryOptimizationExempt(): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(TermuxBackend.TERMUX_PACKAGE_NAME) ?: false
        }.getOrDefault(false)
    }

    override fun isNexusAllFilesAccessGranted(): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        }.getOrDefault(true)
    }

    override fun isSharedStorageWritable(workspacePath: String): Boolean {
        return runCatching {
            val dir = File(workspacePath)
            if (!dir.exists()) dir.mkdirs()
            dir.exists() && dir.canRead() && dir.canWrite()
        }.getOrDefault(false)
    }

    override suspend fun pingTermuxReachability(timeoutSec: Long): Pair<Boolean, String> {
        return termuxBackend.testConnection(timeoutSec = timeoutSec)
    }

    override suspend fun probeInstalledTools(): List<DetectedToolInfo> {
        return termuxBackend.probeTools()
    }

    override suspend fun runRawCommand(cmd: String, cwd: String, timeoutSec: Long): TermuxPluginResult {
        val out = StringBuilder()
        val err = StringBuilder()
        var exit = 1
        var errMsg: String? = null
        termuxBackend.exec(cmd = cmd, cwd = cwd, timeoutSec = timeoutSec).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> out.append(ev.text)
                is ExecEvent.StderrChunk -> err.append(ev.text)
                is ExecEvent.Completed -> {
                    exit = ev.exitCode
                    errMsg = ev.errorMessage
                }
            }
        }
        return TermuxPluginResult(
            stdout = out.toString(),
            stderr = err.toString(),
            exitCode = exit,
            errCode = if (errMsg != null && exit != 0) 1 else 0,
            errMsg = errMsg
        )
    }
}
