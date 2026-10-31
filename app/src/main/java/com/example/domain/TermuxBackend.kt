package com.example.domain

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.TransactionTooLargeException
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TermuxPluginResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errCode: Int = 0,
    val errMsg: String? = null,
    val correlationId: String = "",
    val wasTruncatedOrSpooled: Boolean = false
)

object TermuxResultRegistry {
    private val nextExecutionId = AtomicInteger(10_000)
    private val pendingResults = ConcurrentHashMap<Int, CompletableDeferred<TermuxPluginResult>>()
    private val pendingCorrelationIds = ConcurrentHashMap<String, Int>()

    fun registerExecution(correlationId: String = "corr_${UUID.randomUUID().toString().replace("-", "").take(12)}"): Triple<Int, String, CompletableDeferred<TermuxPluginResult>> {
        val id = nextExecutionId.incrementAndGet()
        val deferred = CompletableDeferred<TermuxPluginResult>()
        pendingResults[id] = deferred
        pendingCorrelationIds[correlationId] = id
        return Triple(id, correlationId, deferred)
    }

    fun isKnownExecution(executionId: Int, correlationId: String?): Boolean {
        if (!pendingResults.containsKey(executionId)) return false
        if (!correlationId.isNullOrBlank()) {
            val mappedId = pendingCorrelationIds[correlationId] ?: return false
            if (mappedId != executionId) return false
        }
        return true
    }

    fun completeExecution(executionId: Int, result: TermuxPluginResult, correlationId: String? = null): Boolean {
        if (!correlationId.isNullOrBlank()) {
            val mapped = pendingCorrelationIds[correlationId]
            if (mapped == null || mapped != executionId) {
                // Ignore unknown or mismatched correlation IDs
                return false
            }
            pendingCorrelationIds.remove(correlationId)
        } else {
            pendingCorrelationIds.entries.removeIf { it.value == executionId }
        }
        val deferred = pendingResults.remove(executionId) ?: return false
        return deferred.complete(result)
    }

    fun cancelExecution(executionId: Int, reason: String = "Cancelled by user") {
        pendingCorrelationIds.entries.removeIf { it.value == executionId }
        pendingResults.remove(executionId)?.complete(
            TermuxPluginResult(
                stdout = "",
                stderr = "$reason\n",
                exitCode = 130,
                errCode = -1,
                errMsg = reason
            )
        )
    }

    fun clearStaleExecutions() {
        pendingCorrelationIds.clear()
        for ((_, def) in pendingResults) {
            def.complete(
                TermuxPluginResult(
                    stdout = "",
                    stderr = "Interrupted by process restart.\n",
                    exitCode = 130,
                    errCode = -1,
                    errMsg = "Interrupted by process restart"
                )
            )
        }
        pendingResults.clear()
    }
}

/**
 * BroadcastReceiver that receives Termux RUN_COMMAND PendingIntent results.
 * Wrapped in defensive `try/catch` blocks so malformed Bundles or oversized IPC payloads
 * never crash the application process. Ignores unknown execution or correlation IDs.
 */
class TermuxResultBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (intent == null) return
            val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
            if (executionId == -1) return
            val correlationId = intent.getStringExtra(EXTRA_CORRELATION_ID)

            if (!TermuxResultRegistry.isKnownExecution(executionId, correlationId)) {
                return
            }

            val resultBundle: Bundle? = runCatching { intent.getBundleExtra(TERMUX_RESULT_BUNDLE_KEY) }.getOrNull()
            if (resultBundle != null) {
                val rawStdout = resultBundle.getString(TERMUX_STDOUT_KEY)
                    ?: resultBundle.getString("com.termux.RUN_COMMAND_STDOUT").orEmpty()
                val rawStderr = resultBundle.getString(TERMUX_STDERR_KEY)
                    ?: resultBundle.getString("com.termux.RUN_COMMAND_STDERR").orEmpty()
                val exitCode = if (resultBundle.containsKey(TERMUX_EXIT_CODE_KEY)) {
                    resultBundle.getInt(TERMUX_EXIT_CODE_KEY, 0)
                } else {
                    resultBundle.getInt("com.termux.RUN_COMMAND_EXIT_CODE", 0)
                }
                val errCode = resultBundle.getInt(TERMUX_ERR_KEY, 0)
                val errMsg = resultBundle.getString(TERMUX_ERRMSG_KEY)
                val cappedOut = capOutputForSafety(rawStdout)
                val cappedErr = capOutputForSafety(rawStderr)
                TermuxResultRegistry.completeExecution(
                    executionId = executionId,
                    correlationId = correlationId,
                    result = TermuxPluginResult(
                        stdout = cappedOut.first,
                        stderr = cappedErr.first,
                        exitCode = exitCode,
                        errCode = errCode,
                        errMsg = errMsg,
                        correlationId = correlationId.orEmpty(),
                        wasTruncatedOrSpooled = cappedOut.second || cappedErr.second
                    )
                )
            } else {
                val rawStdout = intent.getStringExtra(TERMUX_STDOUT_KEY).orEmpty()
                val rawStderr = intent.getStringExtra(TERMUX_STDERR_KEY).orEmpty()
                val exitCode = intent.getIntExtra(TERMUX_EXIT_CODE_KEY, 0)
                val cappedOut = capOutputForSafety(rawStdout)
                val cappedErr = capOutputForSafety(rawStderr)
                TermuxResultRegistry.completeExecution(
                    executionId = executionId,
                    correlationId = correlationId,
                    result = TermuxPluginResult(
                        stdout = cappedOut.first,
                        stderr = cappedErr.first,
                        exitCode = exitCode,
                        correlationId = correlationId.orEmpty(),
                        wasTruncatedOrSpooled = cappedOut.second || cappedErr.second
                    )
                )
            }
        } catch (_: Throwable) {
            // Never allow a broadcast receiver failure to crash the app
        }
    }

    companion object {
        const val ACTION_TERMUX_EXEC_RESULT = "com.example.nexus.TERMUX_EXEC_RESULT"
        const val EXTRA_EXECUTION_ID = "nexus_execution_id"
        const val EXTRA_CORRELATION_ID = "nexus_correlation_id"

        const val TERMUX_RESULT_BUNDLE_KEY = "result"
        const val TERMUX_STDOUT_KEY = "stdout"
        const val TERMUX_STDERR_KEY = "stderr"
        const val TERMUX_EXIT_CODE_KEY = "exitCode"
        const val TERMUX_ERR_KEY = "err"
        const val TERMUX_ERRMSG_KEY = "errmsg"

        const val MAX_IPC_SAFE_CHARS = 96 * 1024

        fun capOutputForSafety(raw: String, maxChars: Int = MAX_IPC_SAFE_CHARS): Pair<String, Boolean> {
            if (raw.length <= maxChars) return raw to false
            val headLen = (maxChars * 2) / 3
            val tailLen = maxChars - headLen
            val capped = buildString {
                append(raw.take(headLen))
                append("\n\n...[Output exceeded ${maxChars / 1024} KB; truncated middle ${raw.length - maxChars} chars for memory safety]...\n\n")
                append(raw.takeLast(tailLen))
            }
            return capped to true
        }
    }
}

/**
 * Termux execution backend using Termux's official `com.termux.RUN_COMMAND` Intent API.
 * Executes commands inside Termux's `/data/data/com.termux/files/usr/bin/bash -lc` login shell.
 *
 * Enforces no-crash guarantees:
 * - Wraps every Intent, PendingIntent, and service start in `try/catch` (`ActivityNotFoundException`,
 *   `SecurityException`, `TransactionTooLargeException`, `IllegalStateException`, etc.).
 * - Uses an explicit `PendingIntent` with a unique correlation ID per command and ignores unknown IDs.
 * - Spools command stdout/stderr inside Termux via a temporary file capped with `head`/`tail`
 *   so oversized outputs never trigger `TransactionTooLargeException` across the Binder boundary.
 */
class TermuxBackend(
    private val context: Context,
    private val wizardStore: TermuxWizardStore? = null
) : ExecutionBackend {

    override val backendType: ExecutionBackendType = ExecutionBackendType.TERMUX

    @Volatile
    private var activeExecutionId: Int? = null

    @Volatile
    private var activeCorrelationId: String? = null

    @Volatile
    private var activeTokenFile: String? = null

    @Volatile
    var lastKnownCwd: String = WorkspacePaths.DEFAULT_SHARED_ROOT
        private set

    /**
     * Optional test seam for intercepting Termux RUN_COMMAND service intents in JVM/Robolectric tests.
     */
    var intentDispatcher: ((serviceIntent: Intent, executionId: Int, correlationId: String) -> Unit)? = null

    fun isTermuxPackageInstalled(): Boolean {
        if (intentDispatcher != null) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun isRunCommandPermissionGranted(): Boolean {
        if (intentDispatcher != null) return true
        return try {
            ContextCompat.checkSelfPermission(
                context,
                PERMISSION_RUN_COMMAND
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun isAvailable(): Boolean {
        return isTermuxPackageInstalled() && isRunCommandPermissionGranted()
    }

    fun resetSessionCwd(newCwd: String) {
        if (newCwd.isNotBlank()) {
            lastKnownCwd = newCwd.trim()
        }
    }

    override fun exec(
        cmd: String,
        cwd: String,
        env: Map<String, String>,
        timeoutSec: Long
    ): Flow<ExecEvent> = flow {
        val startMs = System.currentTimeMillis()
        if (!isTermuxPackageInstalled()) {
            val msg = "Termux (com.termux) is not installed. Install Termux from F-Droid or GitHub Releases."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 127, durationMs = 0L, errorMessage = msg))
            return@flow
        }
        if (!isRunCommandPermissionGranted()) {
            val msg = "Permission com.termux.permission.RUN_COMMAND is not granted. Grant it in Execution Environment settings."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 126, durationMs = 0L, errorMessage = msg))
            return@flow
        }

        val candidateCwd = cwd.ifBlank { lastKnownCwd.ifBlank { WorkspacePaths.DEFAULT_SHARED_ROOT } }
        val validation = WorkspacePathValidator.validateForTermux(candidateCwd)
        if (validation is PathValidationOutcome.Rejected) {
            emit(ExecEvent.StderrChunk("${validation.reason}\n"))
            emit(
                ExecEvent.Completed(
                    exitCode = 126,
                    durationMs = System.currentTimeMillis() - startMs,
                    errorMessage = validation.reason
                )
            )
            return@flow
        }
        val allowed = validation as PathValidationOutcome.Allowed
        val effectiveCwd = allowed.normalizedPath
        val workspaceMode = allowed.mode
        val adaptedCmd = if (workspaceMode == WorkspaceMode.SHARED_STORAGE) {
            WorkspaceCommandAdapter.adaptCommandForSharedStorage(
                command = cmd,
                workspaceCwd = effectiveCwd,
                projectSlug = File(effectiveCwd).name
            )
        } else {
            cmd
        }
        val effectiveTimeoutSec = ExecutionTimeouts.resolveTimeoutSec(adaptedCmd, timeoutSec)
        val forceNoBinLinks = env["npm_config_bin_links"] == "false"
        val safeEnv = ExecutionTimeouts.buildSafeEnvironment(
            userEnv = env,
            workspaceMode = workspaceMode,
            forceNoBinLinks = forceNoBinLinks
        )

        val (execId, correlationId, deferred) = TermuxResultRegistry.registerExecution()
        activeExecutionId = execId
        activeCorrelationId = correlationId
        val pidFile = "$TERMUX_TMP_DIR/nexus_job_${execId}.pid"
        val spoolFile = "$TERMUX_TMP_DIR/nexus_spool_${execId}.out"
        activeTokenFile = pidFile

        try {
            wizardStore?.recordPendingCorrelationId(correlationId)
        } catch (_: Throwable) {
        }

        val envExports = safeEnv.entries.joinToString(" ") { (k, v) ->
            "$k='${v.replace("'", "'\\''")}'"
        }

        val sharedStorageGitConfig = if (workspaceMode == WorkspaceMode.SHARED_STORAGE) {
            WorkspaceCommandAdapter.consumeOneTimeGitConfigPrefix(effectiveCwd)
        } else {
            ""
        }

        val escapedCwd = effectiveCwd.replace("'", "'\\''")
        val escapedInnerCmd = adaptedCmd.replace("'", "'\\''")

        // Spool stdout/stderr to a temp file if output is huge (> 80 KB) and emit head + tail chunks
        // so Binder IPC never hits TransactionTooLargeException (1 MB Binder buffer limit).
        val wrappedScript = buildString {
            append("export PATH=\"$TERMUX_PREFIX_BIN:\$PATH\"; ")
            if (envExports.isNotBlank()) {
                append("export $envExports; ")
            }
            append("mkdir -p \"${WorkspacePaths.TERMUX_NPM_CACHE}\" \"${WorkspacePaths.TERMUX_PIP_CACHE}\" 2>/dev/null || true; ")
            append("mkdir -p '$escapedCwd' 2>/dev/null || true; ")
            append("cd '$escapedCwd' 2>/dev/null || cd \"$TERMUX_HOME_DIR\"; ")
            append("echo \$\$ > '$pidFile' 2>/dev/null || true; ")
            append(sharedStorageGitConfig)
            append("bash -c '$escapedInnerCmd; __ec=\$?; printf \"\\n$PWD_MARKER%s\" \"\$PWD\"; exit \$__ec' > '$spoolFile' 2>&1; ")
            append("__nexus_ec=\$?; ")
            append("if [ -f '$spoolFile' ]; then ")
            append("__sz=\$(wc -c < '$spoolFile' 2>/dev/null | tr -d ' '); ")
            append("if [ \"\${__sz:-0}\" -gt $MAX_SPOOL_BYTES ]; then ")
            append("head -c 49152 '$spoolFile'; ")
            append("printf '\\n\\n...[Output spooled (%s bytes); showing head 48 KB + tail 32 KB to prevent IPC overflow]...\\n\\n' \"\$__sz\"; ")
            append("tail -c 32768 '$spoolFile'; ")
            append("else ")
            append("cat '$spoolFile'; ")
            append("fi; ")
            append("rm -f '$spoolFile' 2>/dev/null || true; ")
            append("fi; ")
            append("rm -f '$pidFile' 2>/dev/null || true; exit \$__nexus_ec")
        }

        val pendingIntent = try {
            val resultIntent = Intent(context, TermuxResultBroadcastReceiver::class.java).apply {
                setPackage(context.packageName)
                action = TermuxResultBroadcastReceiver.ACTION_TERMUX_EXEC_RESULT
                putExtra(TermuxResultBroadcastReceiver.EXTRA_EXECUTION_ID, execId)
                putExtra(TermuxResultBroadcastReceiver.EXTRA_CORRELATION_ID, correlationId)
            }
            val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

            PendingIntent.getBroadcast(
                context,
                execId,
                resultIntent,
                pendingFlags
            )
        } catch (e: Throwable) {
            TermuxResultRegistry.cancelExecution(execId, "Failed to create PendingIntent")
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            val msg = "Failed to prepare Termux result PendingIntent: ${e.localizedMessage ?: "unknown error"}."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 1, durationMs = System.currentTimeMillis() - startMs, errorMessage = msg))
            return@flow
        }

        val serviceIntent = Intent().apply {
            component = ComponentName(TERMUX_PACKAGE_NAME, TERMUX_RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", wrappedScript))
            putExtra(EXTRA_WORKDIR, effectiveCwd)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        try {
            val customDispatcher = intentDispatcher
            if (customDispatcher != null) {
                customDispatcher(serviceIntent, execId, correlationId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (se: SecurityException) {
            TermuxResultRegistry.cancelExecution(execId, "SecurityException starting RunCommandService")
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            val msg = "Termux rejected RUN_COMMAND (${se.message}). Ensure allow-external-apps = true in ~/.termux/termux.properties and permission is granted."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 126, durationMs = System.currentTimeMillis() - startMs, errorMessage = msg))
            return@flow
        } catch (anf: ActivityNotFoundException) {
            TermuxResultRegistry.cancelExecution(execId, "ActivityNotFoundException starting RunCommandService")
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            val msg = "Termux RunCommandService was not found (${anf.message}). Reinstall Termux from F-Droid or GitHub."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 127, durationMs = System.currentTimeMillis() - startMs, errorMessage = msg))
            return@flow
        } catch (ttl: TransactionTooLargeException) {
            TermuxResultRegistry.cancelExecution(execId, "TransactionTooLargeException starting RunCommandService")
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            val msg = "Command payload exceeded Android IPC transaction size (${ttl.message}). Shorten the command or write it to a script file."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 1, durationMs = System.currentTimeMillis() - startMs, errorMessage = msg))
            return@flow
        } catch (e: Throwable) {
            TermuxResultRegistry.cancelExecution(execId, "Failed to dispatch RUN_COMMAND")
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            val msg = "Failed to start Termux RunCommandService: ${e.message}. Open Termux once so its daemon is initialized, and verify allow-external-apps = true."
            emit(ExecEvent.StderrChunk("$msg\n"))
            emit(ExecEvent.Completed(exitCode = 1, durationMs = System.currentTimeMillis() - startMs, errorMessage = msg))
            return@flow
        }

        try {
            val result = withTimeout(effectiveTimeoutSec * 1000L) {
                deferred.await()
            }
            val durationMs = System.currentTimeMillis() - startMs
            var cleanedStdout = result.stdout
            val markerIdx = cleanedStdout.lastIndexOf(PWD_MARKER)
            if (markerIdx >= 0) {
                val parsedPwd = cleanedStdout.substring(markerIdx + PWD_MARKER.length).lineSequence().firstOrNull()?.trim().orEmpty()
                cleanedStdout = cleanedStdout.substring(0, markerIdx).trimEnd('\n', '\r')
                if (parsedPwd.startsWith("/")) {
                    lastKnownCwd = parsedPwd
                }
            } else {
                lastKnownCwd = effectiveCwd
            }
            if (cleanedStdout.isNotEmpty()) {
                emit(ExecEvent.StdoutChunk(cleanedStdout))
            }
            if (result.stderr.isNotEmpty()) {
                emit(ExecEvent.StderrChunk(result.stderr))
            }
            if (result.errCode != 0 && !result.errMsg.isNullOrBlank()) {
                emit(ExecEvent.StderrChunk("[Termux error ${result.errCode}]: ${result.errMsg}\n"))
            }
            emit(
                ExecEvent.Completed(
                    exitCode = result.exitCode,
                    durationMs = durationMs,
                    timedOut = false,
                    errorMessage = result.errMsg
                )
            )
        } catch (_: TimeoutCancellationException) {
            val durationMs = System.currentTimeMillis() - startMs
            killPidFileIfPresent(pidFile)
            TermuxResultRegistry.cancelExecution(execId, "Timed out after ${effectiveTimeoutSec}s")
            emit(ExecEvent.StderrChunk("\n[Command timed out after ${effectiveTimeoutSec}s]\n"))
            emit(
                ExecEvent.Completed(
                    exitCode = 124,
                    durationMs = durationMs,
                    timedOut = true,
                    errorMessage = "Timed out after ${effectiveTimeoutSec}s"
                )
            )
        } finally {
            try { wizardStore?.removePendingCorrelationId(correlationId) } catch (_: Throwable) {}
            activeExecutionId = null
            activeCorrelationId = null
            activeTokenFile = null
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        val execId = activeExecutionId
        val pidFile = activeTokenFile
        if (pidFile != null) {
            killPidFileIfPresent(pidFile)
        }
        if (execId != null) {
            TermuxResultRegistry.cancelExecution(execId, "Cancelled by user")
        }
    }

    private fun killPidFileIfPresent(pidFile: String) {
        if (intentDispatcher != null) return
        try {
            val killScript = "if [ -f '$pidFile' ]; then kill -TERM -\$(cat '$pidFile') 2>/dev/null || kill -9 \$(cat '$pidFile') 2>/dev/null; rm -f '$pidFile'; fi"
            val killIntent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE_NAME, TERMUX_RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
                putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", killScript))
                putExtra(EXTRA_BACKGROUND, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(killIntent)
            } else {
                context.startService(killIntent)
            }
        } catch (_: Throwable) {
            // Ignore errors during best-effort cleanup
        }
    }

    suspend fun testConnection(timeoutSec: Long = 10L): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isTermuxPackageInstalled()) {
            return@withContext false to "Termux app (com.termux) is not installed."
        }
        if (!isRunCommandPermissionGranted()) {
            return@withContext false to "RUN_COMMAND permission is not granted."
        }
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1
        var timedOut = false
        exec(cmd = "echo ok", cwd = TERMUX_HOME_DIR, timeoutSec = timeoutSec).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> stdout.append(ev.text)
                is ExecEvent.StderrChunk -> stderr.append(ev.text)
                is ExecEvent.Completed -> {
                    exitCode = ev.exitCode
                    timedOut = ev.timedOut
                }
            }
        }
        val outTrimmed = stdout.toString().trim()
        if (exitCode == 0 && (outTrimmed.contains("ok") || outTrimmed.contains("nexus_ok"))) {
            true to "Connected (echo ok -> \"$outTrimmed\")"
        } else if (timedOut) {
            false to "Timed out waiting for Termux response (${timeoutSec}s). Ensure `allow-external-apps = true` is set in ~/.termux/termux.properties and Termux has been started."
        } else {
            false to (stderr.toString().trim().ifBlank { "Exit code $exitCode" })
        }
    }

    suspend fun probeTools(): List<DetectedToolInfo> = withContext(Dispatchers.IO) {
        val toolSpecs = listOf(
            "node" to "node --version",
            "npm" to "npm --version",
            "git" to "git --version",
            "python" to "python --version",
            "pip" to "pip --version"
        )
        if (!isAvailable()) {
            return@withContext toolSpecs.map { (name, _) ->
                DetectedToolInfo(name = name, binaryName = name, isAvailable = false)
            }
        }
        val probeScript = buildString {
            for ((name, verCmd) in toolSpecs) {
                append("if command -v $name >/dev/null 2>&1; then ")
                append("echo \"TOOL:$name:FOUND:\$(command -v $name):\$($verCmd 2>&1 | head -n 1)\"; ")
                append("else echo \"TOOL:$name:MISSING::\"; fi; ")
            }
        }
        val stdout = StringBuilder()
        var completedOk = false
        exec(cmd = probeScript, cwd = TERMUX_HOME_DIR, timeoutSec = 10L).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> stdout.append(ev.text)
                is ExecEvent.StderrChunk -> Unit
                is ExecEvent.Completed -> completedOk = !ev.timedOut
            }
        }
        if (!completedOk || stdout.isBlank()) {
            return@withContext toolSpecs.map { (name, _) ->
                DetectedToolInfo(name = name, binaryName = name, isAvailable = false)
            }
        }
        val lines = stdout.toString().lines()
        toolSpecs.map { (name, _) ->
            val line = lines.firstOrNull { it.startsWith("TOOL:$name:") }
            if (line != null) {
                val parts = line.split(":", limit = 5)
                val status = parts.getOrNull(2) == "FOUND"
                val path = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                val version = parts.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
                DetectedToolInfo(
                    name = name,
                    binaryName = name,
                    isAvailable = status,
                    version = version,
                    path = path
                )
            } else {
                DetectedToolInfo(name = name, binaryName = name, isAvailable = false)
            }
        }
    }

    /**
     * Task 2 Pre-flight workspace check before the first command in a session:
     * Through Termux runs `test -d` / `test -w` on the workspace, and writes and deletes a probe file.
     */
    suspend fun verifyWorkspacePreFlight(
        workspacePath: String,
        timeoutSec: Long = 8L
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val validation = WorkspacePathValidator.validateForTermux(workspacePath)
        if (validation is PathValidationOutcome.Rejected) {
            return@withContext false to validation.reason
        }
        val target = (validation as PathValidationOutcome.Allowed).normalizedPath
        val esc = target.replace("'", "'\\''")
        val probeCmd = buildString {
            append("mkdir -p '$esc' 2>/dev/null || true; ")
            append("if ! test -d '$esc'; then echo 'NEXUS_PF_ERR:Directory does not exist: $esc'; exit 2; fi; ")
            append("if ! test -w '$esc'; then echo 'NEXUS_PF_ERR:Directory is not writable by Termux (run termux-setup-storage): $esc'; exit 3; fi; ")
            append("__pf='$esc/.nexus_probe_'\$\$; ")
            append("if printf 'probe' > \"\$__pf\" 2>/dev/null && test -f \"\$__pf\" && rm -f \"\$__pf\" 2>/dev/null; then ")
            append("echo 'NEXUS_PF_OK'; exit 0; ")
            append("else ")
            append("rm -f \"\$__pf\" 2>/dev/null || true; ")
            append("echo 'NEXUS_PF_ERR:Probe file write/delete failed in $esc (grant All Files Access & run termux-setup-storage)'; exit 4; ")
            append("fi")
        }
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exit = -1
        exec(cmd = probeCmd, cwd = TERMUX_HOME_DIR, timeoutSec = timeoutSec).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> stdout.append(ev.text)
                is ExecEvent.StderrChunk -> stderr.append(ev.text)
                is ExecEvent.Completed -> exit = ev.exitCode
            }
        }
        val outStr = stdout.toString()
        if (exit == 0 && outStr.contains("NEXUS_PF_OK")) {
            true to "Workspace writable ($target)"
        } else {
            val errLine = outStr.lineSequence().firstOrNull { it.startsWith("NEXUS_PF_ERR:") }
                ?.removePrefix("NEXUS_PF_ERR:")
                ?.trim()
                ?: stderr.toString().trim().ifBlank { "Termux could not write probe file in $target (exit $exit)." }
            false to errLine
        }
    }

    /**
     * Amendment #9: Finds orphan temporary scratch folders in `$HOME/.nexus/scratch` older than [maxAgeMinutes] (default 24h = 1440m).
     */
    suspend fun listOrphanScratchDirs(maxAgeMinutes: Int = 1440): List<String> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptyList()
        val scratchRoot = WorkspacePaths.TERMUX_SCRATCH_ROOT
        val cmd = "if [ -d '$scratchRoot' ]; then find '$scratchRoot' -mindepth 1 -maxdepth 1 -type d -mmin +$maxAgeMinutes -print 2>/dev/null; fi"
        val stdout = StringBuilder()
        exec(cmd = cmd, cwd = TERMUX_HOME_DIR, timeoutSec = 10L).collect { ev ->
            if (ev is ExecEvent.StdoutChunk) stdout.append(ev.text)
        }
        stdout.toString().lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("$scratchRoot/") }
            .toList()
    }

    override suspend fun describeEnvironment(
        cwd: String,
        workspaceMode: WorkspaceMode
    ): EnvironmentDescription = withContext(Dispatchers.IO) {
        val installed = isTermuxPackageInstalled()
        val permGranted = isRunCommandPermissionGranted()
        val effectiveCwd = if (workspaceMode == WorkspaceMode.TERMUX_HOME) {
            if (cwd.startsWith(TERMUX_HOME_DIR)) cwd else WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT
        } else {
            val candidate = cwd.ifBlank { WorkspacePaths.DEFAULT_SHARED_ROOT }
            when (val v = WorkspacePathValidator.validateForTermux(candidate)) {
                is PathValidationOutcome.Allowed -> v.normalizedPath
                is PathValidationOutcome.Rejected -> WorkspacePaths.DEFAULT_SHARED_ROOT
            }
        }
        val storageOk = File(effectiveCwd).let { it.exists() && it.canRead() } || workspaceMode == WorkspaceMode.TERMUX_HOME

        if (!installed || !permGranted) {
            val missingReason = when {
                !installed -> "Termux not installed (install F-Droid / GitHub build)"
                else -> "Grant com.termux.permission.RUN_COMMAND permission"
            }
            return@withContext EnvironmentDescription(
                backendType = ExecutionBackendType.TERMUX,
                shellName = "Termux bash ($TERMUX_BASH_PATH)",
                isAvailable = false,
                summaryLabel = missingReason,
                cwd = effectiveCwd,
                workspaceMode = workspaceMode,
                detectedTools = listOf("node", "npm", "git", "python", "pip").map {
                    DetectedToolInfo(name = it, binaryName = it, isAvailable = false)
                },
                statusWarning = "Limited shell: install Termux for git, node, python",
                isTermuxInstalled = installed,
                isRunCommandPermissionGranted = permGranted,
                isAllowExternalAppsEnabled = false,
                isStorageAccessible = storageOk
            )
        }

        val (pingOk, _) = testConnection()
        val tools = if (pingOk) probeTools() else listOf("node", "npm", "git", "python", "pip").map {
            DetectedToolInfo(name = it, binaryName = it, isAvailable = false)
        }
        val availableCount = tools.count { it.isAvailable }
        EnvironmentDescription(
            backendType = ExecutionBackendType.TERMUX,
            shellName = "Termux bash -lc ($TERMUX_BASH_PATH)",
            isAvailable = pingOk,
            summaryLabel = if (pingOk) {
                "Termux Ready · $availableCount/${tools.size} tools detected"
            } else {
                "Termux installed · Enable allow-external-apps"
            },
            cwd = effectiveCwd,
            workspaceMode = workspaceMode,
            detectedTools = tools,
            statusWarning = if (!pingOk) {
                "Run the one-time setup command inside Termux to enable allow-external-apps = true."
            } else if (workspaceMode == WorkspaceMode.SHARED_STORAGE) {
                "Shared storage mode: symlinks & chmod are disabled (npm_config_bin_links=false and core.fileMode=false applied automatically)."
            } else {
                null
            },
            isTermuxInstalled = true,
            isRunCommandPermissionGranted = true,
            isAllowExternalAppsEnabled = pingOk,
            isStorageAccessible = storageOk
        )
    }

    companion object {
        const val TERMUX_PACKAGE_NAME = "com.termux"
        const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

        const val TERMUX_PREFIX_DIR = "/data/data/com.termux/files/usr"
        const val TERMUX_PREFIX_BIN = "/data/data/com.termux/files/usr/bin"
        const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"
        const val TERMUX_HOME_PATH = "$TERMUX_HOME_DIR/nexus_workspace"
        const val TERMUX_TMP_DIR = "/data/data/com.termux/files/usr/tmp"

        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

        const val MAX_SPOOL_BYTES = 81920L // 80 KB cap before chunked head/tail spooling
        const val PWD_MARKER = "__NEXUS_PWD_MARKER_9f8e7d__"

        const val ONE_TIME_SETUP_COMMAND =
            "mkdir -p ~/.termux && grep -q '^allow-external-apps\\s*=\\s*true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"

        const val INSTALL_TOOLCHAIN_COMMAND =
            "pkg update -y && pkg install -y nodejs git python"
    }
}
