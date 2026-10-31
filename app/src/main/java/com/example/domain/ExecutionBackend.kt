package com.example.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ExecutionBackendType(val id: String, val label: String) {
    TERMUX("termux", "Termux (Full Linux Toolchain)"),
    ANDROID_SHELL("android_shell", "Android Shell (Toybox)"),
    REMOTE_BRIDGE("remote_bridge", "Remote Bridge (server.mjs)");

    companion object {
        fun fromId(id: String?): ExecutionBackendType {
            return entries.firstOrNull { it.id == id } ?: TERMUX
        }
    }
}

enum class WorkspaceMode(val id: String, val label: String) {
    TERMUX_HOME("termux_home", "Termux Home (/data/data/com.termux/files/home/nexus_workspace)"),
    SHARED_STORAGE("shared_storage", "Shared Storage (/storage/emulated/0/Nexus)");

    companion object {
        fun fromId(id: String?): WorkspaceMode {
            return entries.firstOrNull { it.id == id } ?: SHARED_STORAGE
        }
    }
}

sealed interface ExecEvent {
    data class StdoutChunk(val text: String) : ExecEvent
    data class StderrChunk(val text: String) : ExecEvent
    data class Completed(
        val exitCode: Int,
        val durationMs: Long,
        val timedOut: Boolean = false,
        val errorMessage: String? = null
    ) : ExecEvent
}

data class DetectedToolInfo(
    val name: String,
    val binaryName: String,
    val isAvailable: Boolean,
    val version: String? = null,
    val path: String? = null
)

data class EnvironmentDescription(
    val backendType: ExecutionBackendType,
    val shellName: String,
    val isAvailable: Boolean,
    val summaryLabel: String,
    val cwd: String,
    val workspaceMode: WorkspaceMode,
    val detectedTools: List<DetectedToolInfo> = emptyList(),
    val statusWarning: String? = null,
    val isTermuxInstalled: Boolean = false,
    val isRunCommandPermissionGranted: Boolean = false,
    val isAllowExternalAppsEnabled: Boolean = false,
    val isStorageAccessible: Boolean = false
) {
    val isLimitedShell: Boolean
        get() = backendType == ExecutionBackendType.ANDROID_SHELL || !isAvailable

    val summaryLine: String
        get() = summaryLabel
}

data class BackgroundJobInfo(
    val jobId: String,
    val command: String,
    val cwd: String,
    val backendType: ExecutionBackendType,
    val startedAtMs: Long = System.currentTimeMillis(),
    val isRunning: Boolean = true,
    val pidOrToken: String? = null,
    val logTail: List<String> = emptyList(),
    val exitCode: Int? = null
) {
    val logLines: List<String>
        get() = logTail
}

interface ExecutionBackend {
    val backendType: ExecutionBackendType

    fun exec(
        cmd: String,
        cwd: String,
        env: Map<String, String> = emptyMap(),
        timeoutSec: Long = 0L
    ): Flow<ExecEvent>

    fun cancel()

    suspend fun isAvailable(): Boolean

    suspend fun describeEnvironment(
        cwd: String = "",
        workspaceMode: WorkspaceMode = WorkspaceMode.SHARED_STORAGE
    ): EnvironmentDescription
}

object ExecutionTimeouts {
    const val DEFAULT_COMMAND_TIMEOUT_SEC = 120L
    const val PACKAGE_OR_BUILD_TIMEOUT_SEC = 600L
    const val INSTALL_OR_BUILD_TIMEOUT_SEC = 600L

    private val LONG_RUNNING_INSTALL_OR_BUILD_REGEX = Regex(
        """^\s*(?:pkg\s+(?:install|i|upgrade|update)|apt(?:-get)?\s+(?:install|upgrade|update|dist-upgrade)|npm\s+(?:install|i|ci|update|run\s+build)|pnpm\s+(?:install|i|add|build)|yarn\s+(?:install|add|build)|pip3?\s+install|python3?\s+-m\s+pip\s+install|gradle\b|./gradlew\b|cargo\s+(?:build|install|test)|go\s+(?:build|install|get)|make\b)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    private val LONG_RUNNING_SERVER_REGEX = Regex(
        """\b(?:npm\s+run\s+(?:dev|serve|watch)|npm\s+start|pnpm\s+(?:dev|start)|yarn\s+(?:dev|start)|python3?\s+-m\s+http\.server|uvicorn\b|flask\s+run|vite\b|next\s+dev|nodemon\b)\b""",
        RegexOption.IGNORE_CASE
    )

    fun resolveTimeoutSec(cmd: String, requestedTimeoutSec: Long = 0L): Long {
        if (requestedTimeoutSec > 0L) {
            return requestedTimeoutSec.coerceIn(5L, PACKAGE_OR_BUILD_TIMEOUT_SEC)
        }
        return if (LONG_RUNNING_INSTALL_OR_BUILD_REGEX.containsMatchIn(cmd)) {
            PACKAGE_OR_BUILD_TIMEOUT_SEC
        } else {
            DEFAULT_COMMAND_TIMEOUT_SEC
        }
    }

    fun recommendedTimeoutSec(cmd: String): Long = resolveTimeoutSec(cmd, 0L)

    fun isPackageInstallOrBuild(cmd: String): Boolean =
        LONG_RUNNING_INSTALL_OR_BUILD_REGEX.containsMatchIn(cmd)

    fun isLikelyLongRunningServer(cmd: String): Boolean =
        LONG_RUNNING_SERVER_REGEX.containsMatchIn(cmd)

    fun buildSafeEnvironment(
        userEnv: Map<String, String> = emptyMap(),
        workspaceMode: WorkspaceMode = WorkspaceMode.SHARED_STORAGE,
        forceNoBinLinks: Boolean = false
    ): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        // Non-interactive defaults so commands never hang waiting for tty prompts
        merged["CI"] = "true"
        merged["GIT_TERMINAL_PROMPT"] = "0"
        merged["DEBIAN_FRONTEND"] = "noninteractive"
        merged["PIP_NO_INPUT"] = "1"
        merged["npm_config_yes"] = "true"
        // Keep npm and pip caches inside Termux home so shared storage never slows down or fails cache locks
        merged["npm_config_cache"] = WorkspacePaths.TERMUX_NPM_CACHE
        merged["PIP_CACHE_DIR"] = WorkspacePaths.TERMUX_PIP_CACHE
        // Amendment #5: Only set npm_config_bin_links=false on explicit retry after cleaning partial node_modules
        if (forceNoBinLinks) {
            merged["npm_config_bin_links"] = "false"
        }
        // Filter out any accidental secret/key env vars from userEnv
        for ((k, v) in userEnv) {
            val upper = k.uppercase()
            if (upper.contains("API_KEY") ||
                upper.contains("SECRET") ||
                upper.contains("TOKEN") ||
                upper.contains("PASSWORD") ||
                upper.contains("PRIVATE_KEY")
            ) {
                continue
            }
            merged[k] = v
        }
        return merged
    }
}

/**
 * Wraps the existing LocalShellBridge (Android toybox /system/bin/sh) without modifying LocalShellBridge.
 */
class AndroidShellBackend(
    private val shellBridge: LocalShellBridge
) : ExecutionBackend {
    constructor(context: Context) : this(LocalShellBridge(context.applicationContext))

    override val backendType: ExecutionBackendType = ExecutionBackendType.ANDROID_SHELL
    private val cancelled = AtomicBoolean(false)

    override fun exec(
        cmd: String,
        cwd: String,
        env: Map<String, String>,
        timeoutSec: Long
    ): Flow<ExecEvent> = flow {
        cancelled.set(false)
        val effectiveTimeout = ExecutionTimeouts.resolveTimeoutSec(cmd, timeoutSec)
        val safeEnv = ExecutionTimeouts.buildSafeEnvironment(env, WorkspaceMode.SHARED_STORAGE)
        val envPrefix = safeEnv.entries.joinToString(" ") { (k, v) ->
            "$k='${v.replace("'", "'\\''")}'"
        }
        val wrappedCmd = if (envPrefix.isNotBlank()) "export $envPrefix; $cmd" else cmd
        val res = shellBridge.executeCommand(
            command = wrappedCmd,
            timeoutSec = effectiveTimeout
        )
        if (cancelled.get()) {
            emit(ExecEvent.StderrChunk("[Cancelled by user]\n"))
            emit(ExecEvent.Completed(exitCode = 130, durationMs = res.durationMs, errorMessage = "Cancelled"))
            return@flow
        }
        if (res.stdout.isNotEmpty()) {
            emit(ExecEvent.StdoutChunk(res.stdout))
        }
        if (res.stderr.isNotEmpty()) {
            emit(ExecEvent.StderrChunk(res.stderr))
        }
        emit(
            ExecEvent.Completed(
                exitCode = res.exitCode,
                durationMs = res.durationMs,
                timedOut = res.exitCode == 124
            )
        )
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        cancelled.set(true)
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun describeEnvironment(
        cwd: String,
        workspaceMode: WorkspaceMode
    ): EnvironmentDescription = withContext(Dispatchers.IO) {
        val workingDir = File(cwd).takeIf { it.exists() } ?: shellBridge.rootWorkspaceDir
        val tools = listOf("node", "npm", "git", "python", "pip").map { tool ->
            DetectedToolInfo(
                name = tool,
                binaryName = tool,
                isAvailable = false,
                version = null
            )
        }
        EnvironmentDescription(
            backendType = ExecutionBackendType.ANDROID_SHELL,
            shellName = "/system/bin/sh (Android toybox)",
            isAvailable = true,
            summaryLabel = "Limited shell: Android toybox",
            cwd = workingDir.absolutePath,
            workspaceMode = workspaceMode,
            detectedTools = tools,
            statusWarning = "Limited shell: install Termux for git, node, python",
            isTermuxInstalled = false,
            isRunCommandPermissionGranted = false,
            isAllowExternalAppsEnabled = false,
            isStorageAccessible = workingDir.canRead() && workingDir.canWrite()
        )
    }
}

/**
 * Wraps the existing optional remote bridge (server.mjs) over HTTP.
 */
class RemoteBridgeBackend(
    private val context: Context,
    private val endpointUrlProvider: () -> String
) : ExecutionBackend {
    override val backendType: ExecutionBackendType = ExecutionBackendType.REMOTE_BRIDGE

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    override fun exec(
        cmd: String,
        cwd: String,
        env: Map<String, String>,
        timeoutSec: Long
    ): Flow<ExecEvent> = flow {
        val startMs = System.currentTimeMillis()
        val baseUrl = endpointUrlProvider().trim().removeSuffix("/")
        if (baseUrl.isBlank()) {
            emit(ExecEvent.StderrChunk("Remote Bridge URL is not configured.\n"))
            emit(ExecEvent.Completed(exitCode = 1, durationMs = 0L, errorMessage = "Remote Bridge URL empty"))
            return@flow
        }
        val payload = JSONObject().apply {
            put("command", cmd)
            put("cwd", cwd)
            put("timeoutSec", ExecutionTimeouts.resolveTimeoutSec(cmd, timeoutSec))
        }
        val req = Request.Builder()
            .url("$baseUrl/api/exec")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            val call = client.newCall(req)
            activeCall = call
            call.execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val durationMs = System.currentTimeMillis() - startMs
                if (!resp.isSuccessful) {
                    emit(ExecEvent.StderrChunk("Remote Bridge HTTP ${resp.code}: $raw\n"))
                    emit(ExecEvent.Completed(exitCode = resp.code, durationMs = durationMs))
                    return@flow
                }
                val json = runCatching { JSONObject(raw) }.getOrNull()
                val stdout = json?.optString("stdout").orEmpty()
                val stderr = json?.optString("stderr").orEmpty()
                val exitCode = json?.optInt("exitCode", 0) ?: 0
                if (stdout.isNotEmpty()) emit(ExecEvent.StdoutChunk(stdout))
                if (stderr.isNotEmpty()) emit(ExecEvent.StderrChunk(stderr))
                emit(ExecEvent.Completed(exitCode = exitCode, durationMs = durationMs))
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            emit(ExecEvent.StderrChunk("Remote Bridge error: ${e.message ?: "connection failed"}\n"))
            emit(ExecEvent.Completed(exitCode = 1, durationMs = durationMs, errorMessage = e.message))
        } finally {
            activeCall = null
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        activeCall?.cancel()
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = endpointUrlProvider().trim().removeSuffix("/")
        if (baseUrl.isBlank()) return@withContext false
        runCatching {
            val req = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun describeEnvironment(
        cwd: String,
        workspaceMode: WorkspaceMode
    ): EnvironmentDescription = withContext(Dispatchers.IO) {
        val reachable = isAvailable()
        EnvironmentDescription(
            backendType = ExecutionBackendType.REMOTE_BRIDGE,
            shellName = "Remote Bridge (${endpointUrlProvider().ifBlank { "unconfigured" }})",
            isAvailable = reachable,
            summaryLabel = if (reachable) "Remote Bridge connected" else "Remote Bridge offline",
            cwd = cwd,
            workspaceMode = workspaceMode,
            detectedTools = emptyList(),
            statusWarning = if (!reachable) "Remote Bridge server.mjs is unreachable." else null,
            isTermuxInstalled = false,
            isRunCommandPermissionGranted = false,
            isAllowExternalAppsEnabled = false,
            isStorageAccessible = reachable
        )
    }
}

/**
 * Helper for routing workspace file reads/writes/sed edits through the active ExecutionBackend.
 */
object BackendWorkspaceFileOps {
    suspend fun readFileViaBackend(
        backend: ExecutionBackend,
        cwd: String,
        targetPath: String,
        maxLines: Int = 400
    ): Pair<Int, String> {
        val escapedPath = targetPath.replace("'", "'\\''")
        val cmd = "cat '$escapedPath' | head -n ${maxLines.coerceIn(1, 5000)}"
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exit = 0
        backend.exec(cmd = cmd, cwd = cwd, timeoutSec = 30L).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> stdout.append(ev.text)
                is ExecEvent.StderrChunk -> stderr.append(ev.text)
                is ExecEvent.Completed -> exit = ev.exitCode
            }
        }
        val combined = if (exit == 0) stdout.toString() else (stderr.toString().ifBlank { stdout.toString() })
        return exit to combined
    }

    suspend fun writeFileViaBackend(
        backend: ExecutionBackend,
        cwd: String,
        targetPath: String,
        content: String
    ): Pair<Int, String> {
        val resolvedTarget = if (targetPath.startsWith("/")) {
            targetPath
        } else {
            "${cwd.trimEnd('/')}/$targetPath"
        }
        val parentDir = File(resolvedTarget).parent ?: cwd
        if (WorkspacePathValidator.isNexusRootPath(parentDir) || WorkspacePathValidator.isNexusRootPath(resolvedTarget)) {
            return 1 to "Blocked write to workspace root (${WorkspacePaths.DEFAULT_SHARED_ROOT}). Projects must live in a subfolder (${WorkspacePaths.DEFAULT_SHARED_ROOT}/<project>)."
        }
        val escapedPath = targetPath.replace("'", "'\\''")
        val delimiter = "NEXUS_EOF_${System.currentTimeMillis()}"
        val cmd = buildString {
            append("mkdir -p \"$(dirname '$escapedPath')\" && ")
            append("cat <<'$delimiter' > '$escapedPath'\n")
            append(content)
            if (!content.endsWith("\n")) append("\n")
            append(delimiter)
        }
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exit = 0
        backend.exec(cmd = cmd, cwd = cwd, timeoutSec = 30L).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> stdout.append(ev.text)
                is ExecEvent.StderrChunk -> stderr.append(ev.text)
                is ExecEvent.Completed -> exit = ev.exitCode
            }
        }
        val msg = if (exit == 0) {
            "Wrote ${content.toByteArray().size} bytes to $targetPath"
        } else {
            stderr.toString().ifBlank { "Failed to write $targetPath (exit $exit)" }
        }
        return exit to msg
    }

    suspend fun sedReplaceViaBackend(
        backend: ExecutionBackend,
        cwd: String,
        targetPath: String,
        search: String,
        replace: String
    ): Pair<Int, String> {
        val resolvedTarget = if (targetPath.startsWith("/")) {
            targetPath
        } else {
            "${cwd.trimEnd('/')}/$targetPath"
        }
        val parentDir = File(resolvedTarget).parent ?: cwd
        if (WorkspacePathValidator.isNexusRootPath(parentDir) || WorkspacePathValidator.isNexusRootPath(resolvedTarget)) {
            return 1 to "Blocked file modification in workspace root (${WorkspacePaths.DEFAULT_SHARED_ROOT}). Projects must live in a subfolder (${WorkspacePaths.DEFAULT_SHARED_ROOT}/<project>)."
        }
        val escapedPath = targetPath.replace("'", "'\\''")
        val escSearch = search.replace("\\", "\\\\").replace("/", "\\/").replace("'", "'\\''")
        val escReplace = replace.replace("\\", "\\\\").replace("/", "\\/").replace("&", "\\&").replace("'", "'\\''")
        val cmd = "sed -i 's/$escSearch/$escReplace/g' '$escapedPath'"
        val stderr = StringBuilder()
        var exit = 0
        backend.exec(cmd = cmd, cwd = cwd, timeoutSec = 30L).collect { ev ->
            when (ev) {
                is ExecEvent.StdoutChunk -> Unit
                is ExecEvent.StderrChunk -> stderr.append(ev.text)
                is ExecEvent.Completed -> exit = ev.exitCode
            }
        }
        return exit to if (exit == 0) "Updated $targetPath" else stderr.toString()
    }
}
