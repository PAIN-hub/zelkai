package com.example.domain

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Maps raw Termux/npm/git/pip/dpkg/SSL error strings and [TermuxError] instances into
 * user-friendly [TermuxErrorPresentation] cards, enforces safe fallback rules,
 * and implements the 3-strike infrastructure circuit breaker.
 */
object ErrorMapper {

    fun mapCommandOutputOrError(
        command: String,
        exitCode: Int,
        stdout: String,
        stderr: String,
        explicitError: TermuxError? = null,
        freeStorageBytes: Long = -1L,
        workspaceCwd: String = "",
        extraSecrets: List<String> = emptyList()
    ): TermuxErrorPresentation {
        val combinedRaw = buildString {
            if (stderr.isNotBlank()) append(stderr.trim())
            if (stdout.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(stdout.trim())
            }
        }
        val redactedCombined = NexusToolAndRedactor.redactSecrets(combinedRaw, extraSecrets).text
        val stderrTail = redactedCombined.lines().takeLast(12).joinToString("\n").takeLast(800)
        val canReadOnlyFallback = FallbackPolicy.canOfferLimitedShellFallback(command)

        val inferredError: TermuxError = explicitError ?: inferTypedErrorFromOutput(
            exitCode = exitCode,
            redactedOutput = redactedCombined,
            stderrTail = stderrTail,
            freeStorageBytes = freeStorageBytes
        )

        val baseActions = mutableListOf(
            TermuxErrorActionType.RETRY,
            TermuxErrorActionType.OPEN_TERMUX
        )

        val lower = redactedCombined.lowercase()

        val presentation = when {
            inferredError is TermuxError.TermuxNotInstalled -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "termux_not_installed",
                    title = "Termux Not Installed",
                    oneLineCause = "Nexus uses Termux as its local Linux runtime for git, Node.js, and Python.",
                    numberedFixSteps = listOf(
                        "Install Termux from GitHub Releases or F-Droid (do NOT mix sources, and avoid the outdated Play Store build).",
                        "Open Termux once so it finishes extracting its bootstrap packages.",
                        "Return to Nexus Agent and run the Guided Setup Wizard."
                    ),
                    actionTypes = buildActions(
                        base = listOf(TermuxErrorActionType.OPEN_SETTINGS, TermuxErrorActionType.RETRY),
                        canFallback = canReadOnlyFallback
                    )
                )
            }

            inferredError is TermuxError.PermissionDenied -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "permission_denied",
                    title = "RUN_COMMAND Permission Needed",
                    oneLineCause = "Android blocked Nexus from sending commands to Termux.",
                    numberedFixSteps = listOf(
                        "Tap 'Open settings' below and open Permissions.",
                        "Allow 'Run commands in Termux environment' (com.termux.permission.RUN_COMMAND).",
                        "Tap 'Retry' to run the command again."
                    ),
                    actionTypes = buildActions(
                        base = listOf(TermuxErrorActionType.OPEN_SETTINGS, TermuxErrorActionType.RETRY),
                        canFallback = canReadOnlyFallback
                    )
                )
            }

            inferredError is TermuxError.ExternalAppsDisabled ||
                lower.contains("allow-external-apps") ||
                lower.contains("termux.properties") -> {
                val cmd = TermuxBackend.ONE_TIME_SETUP_COMMAND
                TermuxErrorPresentation(
                    error = TermuxError.ExternalAppsDisabled(),
                    categoryId = "external_apps_disabled",
                    title = "External App Commands Disabled in Termux",
                    oneLineCause = "Termux requires allow-external-apps = true in ~/.termux/termux.properties.",
                    numberedFixSteps = listOf(
                        "Tap 'Copy command' below to copy the idempotent setup one-liner.",
                        "Tap 'Open Termux' and paste the command into Termux.",
                        "Run termux-reload-settings (or force-close and reopen Termux), then tap 'Retry'."
                    ),
                    suggestedCommand = cmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = canReadOnlyFallback
                    )
                )
            }

            inferredError is TermuxError.ServiceUnavailable -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "service_unavailable",
                    title = "Termux Background Service Unreachable",
                    oneLineCause = "Termux was stopped by Android or has not been launched yet.",
                    numberedFixSteps = listOf(
                        "Tap 'Open Termux' to wake up the Termux session.",
                        "Exempt Termux from Android Battery Optimization in Settings so Android does not kill it.",
                        "Return here and tap 'Retry'."
                    ),
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.OPEN_SETTINGS,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = canReadOnlyFallback
                    )
                )
            }

            inferredError is TermuxError.Timeout -> {
                val isServerCandidate = ExecutionTimeouts.isLikelyLongRunningServer(command)
                val extraList = mutableListOf(
                    TermuxErrorActionType.RETRY,
                    TermuxErrorActionType.RE_RUN_AS_BACKGROUND_JOB,
                    TermuxErrorActionType.OPEN_TERMUX
                )
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "timeout",
                    title = "Command Timed Out (${inferredError.timeoutSec}s)",
                    oneLineCause = if (isServerCandidate) {
                        "This command looks like a long-running server and blocked past the foreground timeout."
                    } else {
                        "The command did not finish within ${inferredError.timeoutSec} seconds and was terminated."
                    },
                    numberedFixSteps = listOf(
                        "If this is a dev server (npm run dev, python -m http.server), tap 'Re-run as background job'.",
                        "Check network speed in Termux if installing large packages.",
                        "Tap 'Retry' to run the command again."
                    ),
                    actionTypes = buildActions(base = extraList, canFallback = canReadOnlyFallback)
                )
            }

            lower.contains("dpkg was interrupted") || lower.contains("dpkg --configure -a") -> {
                val fixCmd = "dpkg --configure -a"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "dpkg_interrupted",
                    title = "Package Manager Interrupted (dpkg)",
                    oneLineCause = "A previous package installation was interrupted before dpkg finished configuring packages.",
                    numberedFixSteps = listOf(
                        "Copy and run `dpkg --configure -a` to repair the package database.",
                        "Run `pkg update -y` to refresh package metadata.",
                        "Tap 'Retry' to re-run your original command."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            lower.contains("unable to locate package") || lower.contains("failed to fetch") ||
                lower.contains("temporary failure resolving") -> {
                val fixCmd = "pkg update -y"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "pkg_repo_stale",
                    title = "Package Index Outdated or Mirror Unreachable",
                    oneLineCause = "Termux package lists are missing or the current mirror failed to respond.",
                    numberedFixSteps = listOf(
                        "Run `pkg update -y` to download the latest Termux package catalog.",
                        "If a mirror is down, open Termux and run `termux-change-repo` manually to pick a working mirror.",
                        "Tap 'Retry' once package lists are updated."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            lower.contains("cannot link executable") ||
                (lower.contains("library \"lib") && lower.contains("not found")) -> {
                val fixCmd = "pkg upgrade -y"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "cannot_link_executable",
                    title = "Outdated Shared Libraries in Termux",
                    oneLineCause = "An installed binary expects a newer shared library (.so) than currently installed.",
                    numberedFixSteps = listOf(
                        "Run `pkg upgrade -y` so all Termux packages and shared libraries match.",
                        "Never partial-upgrade individual core libraries without upgrading the base system.",
                        "Tap 'Retry' after the upgrade finishes."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            lower.contains("ssl certificate problem") ||
                lower.contains("unable to get local issuer certificate") ||
                lower.contains("certificate_verify_failed") ||
                lower.contains("unacceptable_tls_certificate") -> {
                val fixCmd = "pkg install -y ca-certificates openssl-tool"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "ssl_ca_missing",
                    title = "TLS / SSL Root Certificates Missing",
                    oneLineCause = "HTTPS verification failed because the CA root certificate bundle is missing or outdated.",
                    numberedFixSteps = listOf(
                        "Install or refresh `ca-certificates` inside Termux (`pkg install -y ca-certificates`).",
                        "Verify that your device clock and date are accurate.",
                        "Tap 'Retry' to repeat the request over TLS."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            inferredError is TermuxError.WorkingDirUnreachable -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "working_dir_unreachable",
                    title = "Working Directory Unreachable",
                    oneLineCause = inferredError.detail.ifBlank {
                        "Shared storage workspace (${inferredError.workspacePath}) could not be verified via Termux pre-flight probe."
                    },
                    numberedFixSteps = listOf(
                        "Tap 'Grant All Files Access' so Nexus can read and write ${WorkspacePaths.DEFAULT_SHARED_ROOT}.",
                        "Tap 'termux-setup-storage guide', open Termux, and run `termux-setup-storage` to grant Termux storage access.",
                        "Or tap 'Use Termux home' to switch to ${WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT}."
                    ),
                    suggestedCommand = "termux-setup-storage",
                    actionTypes = listOf(
                        TermuxErrorActionType.GRANT_ALL_FILES_ACCESS,
                        TermuxErrorActionType.TERMUX_STORAGE_GUIDE,
                        TermuxErrorActionType.USE_TERMUX_HOME,
                        TermuxErrorActionType.COPY_DIAGNOSTICS
                    )
                )
            }

            inferredError is TermuxError.SymlinkRequiredOnSharedStorage -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "symlink_move_to_termux_home",
                    title = "This project needs symlinks. Move it to Termux home?",
                    oneLineCause = "Android shared storage (FUSE) blocks symlinks, node_modules/.bin shims, and executable binaries.",
                    numberedFixSteps = listOf(
                        "Tap 'Move to Termux home' to copy project sources (without node_modules) to Termux home and run a clean install with full symlink support.",
                        "Nexus will automatically mirror your source files back to /storage/emulated/0/Nexus/<project> so external editors can still access them.",
                        "Or tap 'Keep here' to stay in shared storage."
                    ),
                    actionTypes = listOf(
                        TermuxErrorActionType.MOVE_PROJECT_TO_TERMUX_HOME,
                        TermuxErrorActionType.KEEP_PROJECT_HERE,
                        TermuxErrorActionType.COPY_DIAGNOSTICS
                    )
                )
            }

            (lower.contains("eacces") || lower.contains("eperm") || lower.contains("enosys") || lower.contains("operation not permitted") || lower.contains("function not implemented")) &&
                (lower.contains("symlink") || lower.contains("ln:") || lower.contains("node_modules/.bin")) -> {
                val fixCmd = "npm install --no-bin-links"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "npm_shared_storage_symlink",
                    title = "npm Symlink Blocked on Shared Storage",
                    oneLineCause = "Android shared storage (FUSE/sdcardfs) does not support POSIX symlinks or chmod.",
                    numberedFixSteps = listOf(
                        "Use `--no-bin-links` (`npm_config_bin_links=false`) when running npm in shared storage.",
                        "Or tap 'Move to Termux home' to move this project to /data/data/com.termux/files/home/nexus_workspace with automatic source mirroring.",
                        "Or open Settings > Execution environment and switch Workspace Mode to 'Termux Home'."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.MOVE_PROJECT_TO_TERMUX_HOME,
                            TermuxErrorActionType.KEEP_PROJECT_HERE,
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_SETTINGS,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            lower.contains("detected dubious ownership in repository") ||
                lower.contains("safe.directory") -> {
                val targetDir = workspaceCwd.ifBlank { "." }
                val fixCmd = "git config --global --add safe.directory \"$targetDir\""
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "git_dubious_ownership",
                    title = "Git Dubious Ownership Protection",
                    oneLineCause = "Git detected that the shared storage directory is owned by a different Android UID.",
                    numberedFixSteps = listOf(
                        "Mark this workspace directory as a trusted Git `safe.directory`.",
                        "Tap 'Copy command' below to copy `git config --global --add safe.directory ...`.",
                        "Run the command and tap 'Retry'."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            lower.contains("externally-managed-environment") ||
                lower.contains("pep 668") -> {
                val fixCmd = "python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt"
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "python_externally_managed",
                    title = "Python Virtual Environment Required (PEP 668)",
                    oneLineCause = "Termux protects global Python packages from being overwritten by raw pip install.",
                    numberedFixSteps = listOf(
                        "Create a project virtual environment: `python -m venv .venv`.",
                        "Install packages inside the venv: `.venv/bin/pip install <package>`.",
                        "Tap 'Copy command' below to copy the venv setup command."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            inferredError is TermuxError.NetworkError ||
                lower.contains("could not resolve host") ||
                lower.contains("network is unreachable") ||
                lower.contains("name or service not known") -> {
                TermuxErrorPresentation(
                    error = TermuxError.NetworkError(stderrTail),
                    categoryId = "network_error",
                    title = "Network or DNS Unreachable",
                    oneLineCause = "The command could not resolve the remote host or reach the internet.",
                    numberedFixSteps = listOf(
                        "Verify Wi-Fi or mobile data connectivity on your device.",
                        "If using a VPN or firewall, ensure Termux is allowed network access.",
                        "Tap 'Retry' once internet connectivity is restored."
                    ),
                    actionTypes = buildActions(
                        base = listOf(TermuxErrorActionType.RETRY, TermuxErrorActionType.OPEN_TERMUX),
                        canFallback = false
                    )
                )
            }

            inferredError is TermuxError.StorageFull ||
                lower.contains("no space left on device") ||
                lower.contains("enospc") -> {
                val freeMb = if (freeStorageBytes >= 0L) "${freeStorageBytes / (1024L * 1024L)} MB free" else "0 MB free"
                val fixCmd = "pkg clean && npm cache clean --force"
                TermuxErrorPresentation(
                    error = TermuxError.StorageFull(freeStorageBytes),
                    categoryId = "storage_full",
                    title = "No Space Left on Device ($freeMb)",
                    oneLineCause = "Device storage is full and new files or packages cannot be written.",
                    numberedFixSteps = listOf(
                        "Clean Termux package caches (`pkg clean`) or unused local GGUF models in Settings > Data & Privacy.",
                        "Remove unused `node_modules` or build artifacts from your workspace.",
                        "Tap 'Retry' once at least 500 MB of storage is free."
                    ),
                    suggestedCommand = fixCmd,
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.COPY_COMMAND,
                            TermuxErrorActionType.OPEN_SETTINGS,
                            TermuxErrorActionType.RETRY
                        ),
                        canFallback = false
                    )
                )
            }

            inferredError is TermuxError.ResultTooLarge -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "result_too_large",
                    title = "Large Output Truncated Safely",
                    oneLineCause = inferredError.summaryMessage,
                    numberedFixSteps = listOf(
                        "Output was capped to prevent an Android Binder TransactionTooLargeException.",
                        "Use `head`, `tail`, or `grep` to inspect specific sections of large files.",
                        "Or redirect output to a workspace file and read it in chunks."
                    ),
                    actionTypes = buildActions(base = baseActions, canFallback = canReadOnlyFallback)
                )
            }

            else -> {
                TermuxErrorPresentation(
                    error = inferredError,
                    categoryId = "non_zero_exit",
                    title = "Command Failed (Exit Code $exitCode)",
                    oneLineCause = stderrTail.lines().lastOrNull { it.isNotBlank() }
                        ?.take(120)
                        ?: "Command returned non-zero exit status $exitCode.",
                    numberedFixSteps = listOf(
                        "Inspect the error output above for missing arguments or syntax issues.",
                        "Tap 'Open settings' > Execution environment to run the Termux Diagnostics check.",
                        "Tap 'Retry' after fixing the command."
                    ),
                    actionTypes = buildActions(
                        base = listOf(
                            TermuxErrorActionType.RETRY,
                            TermuxErrorActionType.OPEN_TERMUX,
                            TermuxErrorActionType.OPEN_SETTINGS
                        ),
                        canFallback = canReadOnlyFallback
                    )
                )
            }
        }

        val redactedDiag = buildRedactedDiagnosticsSummary(
            categoryId = presentation.categoryId,
            title = presentation.title,
            exitCode = exitCode,
            stderrTail = stderrTail,
            extraSecrets = extraSecrets
        )

        return presentation.copy(
            canFallbackToLimitedShell = canReadOnlyFallback,
            redactedDiagnosticsReport = redactedDiag
        )
    }

    private fun inferTypedErrorFromOutput(
        exitCode: Int,
        redactedOutput: String,
        stderrTail: String,
        freeStorageBytes: Long
    ): TermuxError {
        val lower = redactedOutput.lowercase()
        return when {
            lower.contains("com.termux) is not installed") ->
                TermuxError.TermuxNotInstalled()
            lower.contains("com.termux.permission.run_command is not granted") ->
                TermuxError.PermissionDenied()
            lower.contains("allow-external-apps") ->
                TermuxError.ExternalAppsDisabled()
            exitCode == 124 || lower.contains("timed out after") ->
                TermuxError.Timeout(timeoutSec = 120L)
            lower.contains("no space left on device") || lower.contains("enospc") ->
                TermuxError.StorageFull(freeStorageBytes)
            lower.contains("could not resolve host") || lower.contains("network is unreachable") ->
                TermuxError.NetworkError(stderrTail)
            else -> TermuxError.NonZeroExit(code = exitCode, stderrTail = stderrTail)
        }
    }

    private fun buildActions(
        base: List<TermuxErrorActionType>,
        canFallback: Boolean
    ): List<TermuxErrorActionType> {
        val list = base.toMutableList()
        if (canFallback && !list.contains(TermuxErrorActionType.USE_LIMITED_SHELL)) {
            list.add(TermuxErrorActionType.USE_LIMITED_SHELL)
        }
        if (!list.contains(TermuxErrorActionType.COPY_DIAGNOSTICS)) {
            list.add(TermuxErrorActionType.COPY_DIAGNOSTICS)
        }
        return list.distinct()
    }

    /**
     * Generates a strictly redacted diagnostics report containing NO API keys, NO user prompts,
     * and NO workspace file contents.
     */
    fun buildRedactedDiagnosticsSummary(
        categoryId: String,
        title: String,
        exitCode: Int,
        stderrTail: String,
        extraSecrets: List<String> = emptyList()
    ): String {
        val scrubbedTail = NexusToolAndRedactor.redactSecrets(stderrTail.take(500), extraSecrets).text
        return buildString {
            appendLine("=== Nexus Agent v5 Execution Diagnostics (Redacted) ===")
            appendLine("category: $categoryId")
            appendLine("title: $title")
            appendLine("exit_code: $exitCode")
            appendLine("stderr_tail:")
            appendLine(scrubbedTail.ifBlank { "(none)" })
        }.trim()
    }

    fun buildRedactedDiagnosticsSummary(
        backendType: ExecutionBackendType,
        workspaceMode: WorkspaceMode,
        isTermuxInstalled: Boolean,
        isPermissionGranted: Boolean,
        isAllowExternalAppsOk: Boolean,
        detectedTools: List<DetectedToolInfo>,
        lastError: String?,
        extraSecrets: List<String> = emptyList()
    ): String {
        val toolsStr = detectedTools.joinToString(", ") { t ->
            if (t.isAvailable) "${t.name}=${t.version ?: "ok"}" else "${t.name}=missing"
        }
        val scrubbedErr = NexusToolAndRedactor.redactSecrets(lastError.orEmpty().take(400), extraSecrets).text
        return buildString {
            appendLine("=== Nexus Agent v5 Environment Diagnostics (Redacted) ===")
            appendLine("backend: ${backendType.id}")
            appendLine("workspace_mode: ${workspaceMode.id}")
            appendLine("termux_installed: $isTermuxInstalled")
            appendLine("run_command_permission: $isPermissionGranted")
            appendLine("allow_external_apps: $isAllowExternalAppsOk")
            appendLine("tools: $toolsStr")
            if (scrubbedErr.isNotBlank()) {
                appendLine("last_error: $scrubbedErr")
            }
        }.trim()
    }
}

/**
 * Enforces the Fallback Chain rules:
 * - Termux -> AndroidShellBackend (read-only, safe commands only) -> chat-only.
 * - NEVER switch backends silently.
 * - NEVER auto-fallback to RemoteBridge.
 * - NEVER fall back for install, write, or network commands.
 */
object FallbackPolicy {
    private val READ_ONLY_SAFE_COMMAND_REGEX = Regex(
        """^\s*(?:ls|pwd|whoami|id|uname|date|uptime|df|du|free|env|printenv|which|command\s+-v|cat|head|tail|wc|stat|file|find|grep|echo)\b""",
        RegexOption.IGNORE_CASE
    )

    private val WRITE_INSTALL_OR_NETWORK_REGEX = Regex(
        """(?:\b(?:pkg|apt|apt-get|npm|pnpm|yarn|pip3?|git\s+(?:clone|push|pull|fetch|commit|init|add|reset|checkout|merge|rebase)|rm|mv|cp|mkdir|touch|chmod|chown|sed\s+-i|tee|curl|wget|nc|netcat|ssh|scp|python3?|node|npx)\b|[>|]{1,2})""",
        RegexOption.IGNORE_CASE
    )

    private val REQUIRES_TERMUX_TOOLCHAIN_REGEX = Regex(
        """^\s*(?:node|npm|npx|pnpm|yarn|git|python3?|pip3?|pkg|cargo|go|make|gcc|clang)\b""",
        RegexOption.IGNORE_CASE
    )

    fun canOfferLimitedShellFallback(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("&&") || trimmed.contains(";") || trimmed.contains("`") || trimmed.contains("$(")) {
            return false
        }
        if (WRITE_INSTALL_OR_NETWORK_REGEX.containsMatchIn(trimmed)) {
            return false
        }
        return READ_ONLY_SAFE_COMMAND_REGEX.containsMatchIn(trimmed)
    }

    fun requiresTermuxToolchain(command: String): Boolean {
        return REQUIRES_TERMUX_TOOLCHAIN_REGEX.containsMatchIn(command.trim())
    }
}

enum class BackendHealthState {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

class BackendCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 60_000L
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val lastFailureTimeMs = AtomicLong(0L)
    private val lastCheckTimeMs = AtomicLong(0L)

    @Volatile
    var lastUnhealthyReason: String = ""
        private set

    val state: BackendHealthState
        get() {
            val count = consecutiveFailures.get()
            return when {
                count >= failureThreshold -> BackendHealthState.UNHEALTHY
                count > 0 -> BackendHealthState.DEGRADED
                else -> BackendHealthState.HEALTHY
            }
        }

    val isUnhealthy: Boolean
        get() = state == BackendHealthState.UNHEALTHY

    fun recordSuccess(nowMs: Long = System.currentTimeMillis()) {
        consecutiveFailures.set(0)
        lastUnhealthyReason = ""
        lastCheckTimeMs.set(nowMs)
    }

    fun recordInfrastructureFailure(reason: String, nowMs: Long = System.currentTimeMillis()): BackendHealthState {
        val newCount = consecutiveFailures.incrementAndGet()
        lastFailureTimeMs.set(nowMs)
        lastCheckTimeMs.set(nowMs)
        lastUnhealthyReason = reason.ifBlank { "Termux execution backend failed ($newCount/$failureThreshold)" }
        return state
    }

    fun shouldAllowAutoRecheckBeforeAgentRun(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isUnhealthy) return true
        val elapsed = nowMs - lastCheckTimeMs.get()
        return elapsed >= cooldownMs
    }

    fun resetOnUserAction(nowMs: Long = System.currentTimeMillis()) {
        consecutiveFailures.set(0)
        lastUnhealthyReason = ""
        lastCheckTimeMs.set(nowMs)
    }

    fun formatStructuredUnavailableToolResult(): String {
        val reason = lastUnhealthyReason.ifBlank {
            "Termux backend failed $failureThreshold consecutive times."
        }
        return "Execution environment unavailable: $reason"
    }
}
