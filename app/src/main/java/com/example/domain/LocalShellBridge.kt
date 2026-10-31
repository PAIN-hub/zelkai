package com.example.domain

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ShellExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val cwd: String,
    val timeoutSec: Long,
    val redactions: Int,
    val durationMs: Long = 45L
) {
    val combinedOutput: String
        get() {
            val parts = mutableListOf<String>()
            if (stdout.isNotBlank()) parts.add(stdout)
            if (stderr.isNotBlank()) {
                parts.add(if (stdout.isNotBlank()) "[stderr]\n$stderr" else stderr)
            }
            return parts.joinToString("\n").ifBlank { "(no output)" }
        }
}

data class DirectoryPreset(
    val label: String,
    val path: String,
    val description: String
)

/**
 * Result of pre-execution working directory & permission validation (Fix #6).
 */
sealed interface CwdValidationResult {
    data class Valid(val resolvedPath: String) : CwdValidationResult
    data class MissingAllFilesAccess(val targetPath: String, val message: String) : CwdValidationResult
    data class InvalidOrNotWritable(val targetPath: String, val message: String) : CwdValidationResult
}

class LocalShellBridge(private val appContext: Context) {
    val sharedNexusRootDir: File = File(WorkspacePaths.DEFAULT_SHARED_ROOT).apply {
        runCatching {
            if (File("/storage/emulated/0").exists() && !exists()) {
                mkdirs()
            }
        }
    }

    val rootWorkspaceDir: File = run {
        val sharedParent = File("/storage/emulated/0")
        if (sharedParent.exists() && (sharedNexusRootDir.exists() || runCatching { sharedNexusRootDir.mkdirs() }.getOrDefault(false))) {
            seedWorkspaceFiles(sharedNexusRootDir)
            sharedNexusRootDir
        } else {
            File(appContext.filesDir, "nexus_workspace").apply {
                if (!exists()) mkdirs()
                seedWorkspaceFiles(this)
            }
        }
    }

    @Volatile
    var currentCwd: String = rootWorkspaceDir.absolutePath
        private set

    @Volatile
    var isOnline: Boolean = true
        private set

    /**
     * Injectable checker for Android 11+ All Files Access (`Environment.isExternalStorageManager()`).
     * Can be overridden in unit tests to verify missing or granted external storage permission.
     */
    var hasAllFilesAccessChecker: () -> Boolean = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(125, TimeUnit.SECONDS)
        .build()

    private var remoteSessionToken: String? = null

    fun resetSessionCwd(): String {
        if (!rootWorkspaceDir.exists()) rootWorkspaceDir.mkdirs()
        currentCwd = rootWorkspaceDir.absolutePath
        isOnline = true
        return currentCwd
    }

    /**
     * Lists existing project subfolders inside `/storage/emulated/0/Nexus` (or [rootPath])
     * and auto-detects project markers (`package.json`, `requirements.txt`, `index.html`, `.git`).
     */
    fun listProjectFolders(rootPath: String = rootWorkspaceDir.absolutePath): List<ProjectFolderSummary> {
        val targetRoot = File(rootPath).takeIf { it.exists() && it.isDirectory } ?: rootWorkspaceDir
        val children = runCatching { targetRoot.listFiles().orEmpty().toList() }.getOrDefault(emptyList())
        return children
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
            .map { dir ->
                val detected = mutableListOf<WorkspaceProjectType>()
                if (File(dir, "package.json").exists()) detected.add(WorkspaceProjectType.NODE_JS)
                if (File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists()) {
                    detected.add(WorkspaceProjectType.PYTHON)
                }
                if (File(dir, "index.html").exists()) detected.add(WorkspaceProjectType.STATIC_WEB)
                if (File(dir, ".git").exists()) detected.add(WorkspaceProjectType.GIT_REPO)
                if (detected.isEmpty()) detected.add(WorkspaceProjectType.GENERAL)
                ProjectFolderSummary(
                    slug = dir.name,
                    sharedPath = WorkspacePaths.sharedProjectPath(dir.name),
                    termuxHomePath = WorkspacePaths.termuxHomeProjectPath(dir.name),
                    detectedTypes = detected,
                    existsInShared = true,
                    existsInTermuxHome = File(WorkspacePaths.termuxHomeProjectPath(dir.name)).exists(),
                    lastModifiedMs = dir.lastModified()
                )
            }
    }

    /**
     * Checks whether [path] is inside app-private internal or external storage directories
     * that never require `MANAGE_EXTERNAL_STORAGE`.
     */
    fun isAppOwnedStoragePath(path: String): Boolean {
        val normalizedTarget = try {
            File(path).canonicalPath
        } catch (_: Exception) {
            File(path).absolutePath
        }
        val appDirs = buildList {
            add(appContext.filesDir)
            add(appContext.cacheDir)
            appContext.dataDir?.let { add(it) }
            appContext.getExternalFilesDir(null)?.let { add(it) }
            appContext.externalCacheDir?.let { add(it) }
        }
        return appDirs.any { appDir ->
            val base = try {
                appDir.canonicalPath
            } catch (_: Exception) {
                appDir.absolutePath
            }
            normalizedTarget == base || normalizedTarget.startsWith("$base/")
        }
    }

    /**
     * Verifies before command execution that `cwd` exists and is writable,
     * and checks All Files Access when `cwd` is outside app storage (Fix #6).
     */
    fun validateWorkingDirectory(targetPath: String = currentCwd): CwdValidationResult {
        val candidatePath = targetPath.trim().ifBlank { rootWorkspaceDir.absolutePath }
        val dir = File(candidatePath)

        // If path is outside app-owned storage and inside shared external storage, verify All Files Access first
        if (!isAppOwnedStoragePath(candidatePath) && !hasAllFilesAccessChecker()) {
            return CwdValidationResult.MissingAllFilesAccess(
                targetPath = candidatePath,
                message = "Grant file access: Working directory '$candidatePath' is outside app storage and requires All Files Access permission."
            )
        }

        if (!dir.exists()) {
            // If it's the default workspace dir, auto-create it
            if (candidatePath == rootWorkspaceDir.absolutePath) {
                dir.mkdirs()
            } else {
                return CwdValidationResult.InvalidOrNotWritable(
                    targetPath = candidatePath,
                    message = "Working directory does not exist: $candidatePath"
                )
            }
        }

        if (!dir.isDirectory) {
            return CwdValidationResult.InvalidOrNotWritable(
                targetPath = candidatePath,
                message = "Working directory path is not a directory: $candidatePath"
            )
        }

        if (!dir.canWrite()) {
            return if (!isAppOwnedStoragePath(candidatePath) && !hasAllFilesAccessChecker()) {
                CwdValidationResult.MissingAllFilesAccess(
                    targetPath = candidatePath,
                    message = "Grant file access: Working directory '$candidatePath' is not writable without All Files Access."
                )
            } else {
                CwdValidationResult.InvalidOrNotWritable(
                    targetPath = candidatePath,
                    message = "Working directory is not writable: $candidatePath"
                )
            }
        }

        val resolved = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        currentCwd = resolved
        return CwdValidationResult.Valid(resolved)
    }

    fun setWorkingDirectory(targetPath: String, createIfMissing: Boolean = false): Result<String> {
        return try {
            val trimmed = targetPath.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Directory path cannot be empty."))
            }
            val dir = File(trimmed)
            if (!dir.exists() && createIfMissing) {
                dir.mkdirs()
            }
            if (!dir.exists() || !dir.isDirectory) {
                return Result.failure(IllegalArgumentException("Directory does not exist or is not accessible: $trimmed"))
            }
            val resolved = try {
                dir.canonicalPath
            } catch (_: Exception) {
                dir.absolutePath
            }
            currentCwd = resolved
            isOnline = true
            Result.success(resolved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listSubdirectories(path: String): List<File> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listFilesInDirectory(path: String): List<File> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getDirectoryPresets(): List<DirectoryPreset> {
        val list = mutableListOf(
            DirectoryPreset(
                label = "Nexus Shared Root (/storage/emulated/0/Nexus)",
                path = WorkspacePaths.DEFAULT_SHARED_ROOT,
                description = "Default shared storage root for Nexus projects (both Termux & Android Shell)"
            ),
            DirectoryPreset(
                label = "Termux Home Workspace",
                path = WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT,
                description = "Fast ext4 Termux home workspace with full symlink & chmod support"
            )
        )
        val extRoot = Environment.getExternalStorageDirectory()
        if (extRoot != null) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                list.add(
                    DirectoryPreset(
                        label = "Downloads",
                        path = downloadDir.absolutePath,
                        description = "Shared Downloads folder"
                    )
                )
            }
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                list.add(
                    DirectoryPreset(
                        label = "Documents",
                        path = docsDir.absolutePath,
                        description = "Shared Documents folder"
                    )
                )
            }
        }
        return list
    }

    companion object {
        fun resolveTreeUriToPath(context: Context, treeUri: Uri): String? {
            return try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                // Format is typically "primary:Download/Subfolder" or "XXXX-XXXX:Folder"
                val split = docId.split(":")
                val type = split.firstOrNull().orEmpty()
                val relative = if (split.size > 1) split[1] else ""
                if (type.equals("primary", ignoreCase = true)) {
                    val base = Environment.getExternalStorageDirectory().absolutePath
                    if (relative.isBlank()) base else "$base/$relative"
                } else if (docId.startsWith("/")) {
                    docId
                } else {
                    val fallback = "/storage/$type" + (if (relative.isBlank()) "" else "/$relative")
                    if (File(fallback).exists()) fallback else {
                        context.getExternalFilesDir(null)?.absolutePath
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun seedWorkspaceFiles(dir: File) {
        try {
            val readme = File(dir, "README.md")
            if (!readme.exists()) {
                readme.writeText(
                    """
                    # Nexus // Shell Agent v5 Workspace
                    Welcome to the live Android Linux shell workspace with real-time Firestore synchronization.
                    
                    ## Available Directories & Files
                    - `README.md` — Workspace overview
                    - `system-check.sh` — Quick system telemetry & storage check script
                    - `config/services.json` — Sample microservice topology configuration
                    - `logs/agent-audit.log` — Sample system log with test tokens (for verifying secret redaction)
                    """.trimIndent() + "\n"
                )
            }
            val script = File(dir, "system-check.sh")
            if (!script.exists()) {
                script.writeText(
                    """
                    #!/system/bin/sh
                    echo "=== NEXUS v5 SYSTEM TELEMETRY ==="
                    echo "Kernel : $(uname -s -r -m)"
                    echo "Uptime : $(uptime 2>/dev/null || echo 'up')"
                    echo "Workdir: $(pwd)"
                    df -h . 2>/dev/null || df .
                    """.trimIndent() + "\n"
                )
                script.setExecutable(true)
            }
            val configDir = File(dir, "config").apply { mkdirs() }
            val servicesJson = File(configDir, "services.json")
            if (!servicesJson.exists()) {
                servicesJson.writeText(
                    """
                    {
                      "service": "nexus-bridge-v5",
                      "port": 8080,
                      "realtimeSync": true,
                      "providers": ["gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"]
                    }
                    """.trimIndent() + "\n"
                )
            }
            val logsDir = File(dir, "logs").apply { mkdirs() }
            val sampleLog = File(logsDir, "agent-audit.log")
            if (!sampleLog.exists()) {
                sampleLog.writeText(
                    """
                    [2026-10-28T09:50:00Z] INFO  Nexus Bridge initialized in ${dir.absolutePath}
                    [2026-10-28T09:51:12Z] WARN  High latency on worker node-2 (245ms)
                    [2026-10-28T09:52:04Z] DEBUG Auth header Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJuZXh1cyJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
                    [2026-10-28T09:53:19Z] INFO  Health check passed (exitCode=0)
                    """.trimIndent() + "\n"
                )
            }
        } catch (_: Exception) {
        }
    }

    suspend fun executeCommand(
        command: String,
        timeoutSec: Long,
        extraSecrets: List<String> = emptyList(),
        remoteBridgeUrl: String? = null
    ): ShellExecResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val effectiveTimeoutSec = NexusToolAndRedactor.clampTimeoutSec(timeoutSec)
        val trimmedBridge = remoteBridgeUrl?.trim().orEmpty()
        if (trimmedBridge.startsWith("http://") || trimmedBridge.startsWith("https://")) {
            return@withContext executeAgainstRemoteBridge(trimmedBridge.trimEnd('/'), command, effectiveTimeoutSec, extraSecrets)
        }

        val workingDir = File(currentCwd).takeIf { it.exists() && it.isDirectory } ?: rootWorkspaceDir
        val marker = "__NEXUS_PWD_MARKER_9f8e7d__"
        val wrappedScript = """
            $command
            __ec=$?
            printf "\n$marker%s" "${'$'}PWD"
            exit ${'$'}__ec
        """.trimIndent()

        var proc: Process? = null
        var timedOut = false

        val rawPair = withTimeoutOrNull(effectiveTimeoutSec * 1000L) {
            suspendCancellableCoroutine<Triple<String, String, Int>> { cont ->
                try {
                    val shellBin = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
                    val process = ProcessBuilder(shellBin, "-c", wrappedScript)
                        .directory(workingDir)
                        .start()
                    proc = process

                    cont.invokeOnCancellation {
                        try {
                            process.destroyForcibly()
                        } catch (_: Exception) {
                        }
                    }

                    val stdout = process.inputStream.bufferedReader().readText()
                    val stderr = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (cont.isActive) {
                        cont.resume(Triple(stdout, stderr, exitCode))
                    }
                } catch (e: Exception) {
                    if (cont.isActive) {
                        cont.resume(Triple("", e.localizedMessage ?: "Failed to execute shell command", 1))
                    }
                }
            }
        }

        val (rawOut, rawErr, exitCode) = if (rawPair == null) {
            timedOut = true
            try {
                proc?.destroyForcibly()
            } catch (_: Exception) {
            }
            Triple("", "[timed out after ${effectiveTimeoutSec}s]", 124)
        } else {
            rawPair
        }

        var cleanedStdout = rawOut
        val markerIdx = rawOut.lastIndexOf(marker)
        if (markerIdx >= 0) {
            val newPwd = rawOut.substring(markerIdx + marker.length).trim()
            cleanedStdout = rawOut.substring(0, markerIdx).trimEnd()
            if (newPwd.isNotBlank() && File(newPwd).isDirectory) {
                currentCwd = newPwd
            }
        }

        val finalStderr = if (timedOut && !rawErr.contains("[timed out")) {
            (rawErr + "\n[timed out after ${effectiveTimeoutSec}s]").trim()
        } else {
            rawErr.trim()
        }

        val redactedOut = NexusToolAndRedactor.redactSecrets(cleanedStdout.trim(), extraSecrets)
        val redactedErr = NexusToolAndRedactor.redactSecrets(finalStderr, extraSecrets)

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(8L)
        isOnline = true
        ShellExecResult(
            stdout = redactedOut.text,
            stderr = redactedErr.text,
            exitCode = exitCode,
            cwd = currentCwd,
            timeoutSec = effectiveTimeoutSec,
            redactions = redactedOut.count + redactedErr.count,
            durationMs = elapsedMs
        )
    }

    private fun executeAgainstRemoteBridge(
        baseUrl: String,
        command: String,
        timeoutSec: Long,
        extraSecrets: List<String>
    ): ShellExecResult {
        val startMs = System.currentTimeMillis()
        return try {
            if (remoteSessionToken == null) {
                val sessReq = Request.Builder().url("$baseUrl/api/session").get().build()
                httpClient.newCall(sessReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("Bridge HTTP ${resp.code}")
                    val json = JSONObject(resp.body?.string().orEmpty())
                    remoteSessionToken = json.optString("token")
                    val cwd = json.optString("cwd")
                    if (cwd.isNotBlank()) currentCwd = cwd
                }
            }
            val bodyJson = JSONObject().apply {
                put("command", command)
                put("timeoutMs", timeoutSec * 1000L)
            }.toString()
            val execReq = Request.Builder()
                .url("$baseUrl/api/exec")
                .header("X-Nexus-Token", remoteSessionToken.orEmpty())
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(execReq).execute().use { resp ->
                if (resp.code == 401) {
                    remoteSessionToken = null
                    error("Bridge session token expired; retry command.")
                }
                if (!resp.isSuccessful) error("Bridge error: HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                val newCwd = json.optString("cwd", currentCwd)
                if (newCwd.isNotBlank()) currentCwd = newCwd
                val so = NexusToolAndRedactor.redactSecrets(json.optString("stdout", ""), extraSecrets)
                val se = NexusToolAndRedactor.redactSecrets(json.optString("stderr", ""), extraSecrets)
                val serverRedactions = json.optInt("redactions", 0)
                val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(12L)
                isOnline = true
                ShellExecResult(
                    stdout = so.text,
                    stderr = se.text,
                    exitCode = json.optInt("exitCode", 0),
                    cwd = currentCwd,
                    timeoutSec = timeoutSec,
                    redactions = serverRedactions + so.count + se.count,
                    durationMs = elapsedMs
                )
            }
        } catch (e: Exception) {
            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(15L)
            isOnline = false
            ShellExecResult(
                stdout = "",
                stderr = "Bridge unreachable (${e.localizedMessage ?: "error"})",
                exitCode = 1,
                cwd = currentCwd,
                timeoutSec = timeoutSec,
                redactions = 0,
                durationMs = elapsedMs
            )
        }
    }
}
