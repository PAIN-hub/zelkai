package com.example.domain

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

private val Context.nexusJobStoreDataStore by preferencesDataStore(name = "nexus_detached_jobs_store_v5")

enum class ManagedJobState(val id: String, val label: String) {
    STARTING("starting", "Starting"),
    RUNNING("running", "Running"),
    EXITED("exited", "Exited"),
    STOPPED("stopped", "Stopped"),
    LOST("lost", "Lost");

    companion object {
        fun fromId(raw: String?): ManagedJobState {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: EXITED
        }
    }
}

data class ManagedJobRecord(
    val id: String,
    val command: String,
    val cwd: String,
    val startedAtMs: Long,
    val pid: Int,
    val port: Int? = null,
    val state: ManagedJobState = ManagedJobState.RUNNING,
    val exitCode: Int? = null,
    val lastReadByteOffset: Long = 0L,
    val logLines: List<String> = emptyList(),
    val title: String = "",
    val originSessionId: String = "",
    val originTurnId: String = "",
    val resultPending: Boolean = false,
    val resumePolicy: String = "foreground"
) {
    fun uptimeSummary(nowMs: Long = System.currentTimeMillis()): String {
        val elapsedSec = ((nowMs - startedAtMs) / 1000L).coerceAtLeast(0L)
        return when {
            elapsedSec < 60L -> "${elapsedSec}s"
            elapsedSec < 3600L -> "${elapsedSec / 60L}m ${elapsedSec % 60L}s"
            else -> "${elapsedSec / 3600L}h ${(elapsedSec % 3600L) / 60L}m"
        }
    }
}

data class StartJobOutcome(
    val success: Boolean,
    val job: ManagedJobRecord? = null,
    val error: TermuxError? = null,
    val initialRedactedLines: List<String> = emptyList(),
    val message: String = ""
)

class JobStore(private val context: Context) {
    private val keyJobsJson = stringPreferencesKey("managed_jobs_json_v5")

    val persistedJobsFlow: Flow<List<ManagedJobRecord>> = context.nexusJobStoreDataStore.data.map { prefs ->
        parseJobsJson(prefs[keyJobsJson].orEmpty())
    }

    suspend fun loadJobs(): List<ManagedJobRecord> = persistedJobsFlow.first()

    suspend fun saveJobs(jobs: List<ManagedJobRecord>) {
        val arr = JSONArray()
        for (job in jobs) {
            arr.put(
                JSONObject().apply {
                    put("id", job.id)
                    put("command", job.command)
                    put("cwd", job.cwd)
                    put("startedAtMs", job.startedAtMs)
                    put("pid", job.pid)
                    if (job.port != null) put("port", job.port)
                    put("state", job.state.id)
                    if (job.exitCode != null) put("exitCode", job.exitCode)
                    put("lastReadByteOffset", job.lastReadByteOffset)
                    val linesArr = JSONArray()
                    job.logLines.takeLast(120).forEach { linesArr.put(it) }
                    put("logLines", linesArr)
                }
            )
        }
        context.nexusJobStoreDataStore.edit { prefs ->
            prefs[keyJobsJson] = arr.toString()
        }
    }

    companion object {
        fun parseJobsJson(raw: String): List<ManagedJobRecord> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<ManagedJobRecord>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    if (id.isEmpty()) continue
                    val linesArr = obj.optJSONArray("logLines")
                    val lines = mutableListOf<String>()
                    if (linesArr != null) {
                        for (j in 0 until linesArr.length()) {
                            lines.add(linesArr.optString(j, ""))
                        }
                    }
                    list.add(
                        ManagedJobRecord(
                            id = id,
                            command = obj.optString("command", ""),
                            cwd = obj.optString("cwd", ""),
                            startedAtMs = obj.optLong("startedAtMs", System.currentTimeMillis()),
                            pid = obj.optInt("pid", -1),
                            port = if (obj.has("port") && !obj.isNull("port")) obj.optInt("port") else null,
                            state = ManagedJobState.fromId(obj.optString("state", "exited")),
                            exitCode = if (obj.has("exitCode") && !obj.isNull("exitCode")) obj.optInt("exitCode") else null,
                            lastReadByteOffset = obj.optLong("lastReadByteOffset", 0L),
                            logLines = lines
                        )
                    )
                }
                list
            }.getOrDefault(emptyList())
        }
    }
}

object ServerLogAnalyzer {
    private val READY_REGEX = Regex(
        """(?:\blistening\b|\bready\s+in\b|\blocal:\s*https?://|\bserving\s+http\b|\brunning\s+on\s+https?://|\bstarted\s+server\s+on\b|https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):\d{2,5})""",
        RegexOption.IGNORE_CASE
    )

    private val PORT_PATTERNS = listOf(
        Regex("""(?:https?://)?(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1?\]):(\d{2,5})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bport\s+(?:is\s+)?(\d{2,5})\b""", RegexOption.IGNORE_CASE),
        Regex("""\blistening\s+on\s+(?:port\s+)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)
    )

    fun shouldRunAsBackgroundJob(command: String): Boolean {
        return ExecutionTimeouts.isLikelyLongRunningServer(command)
    }

    fun isLoopbackPreviewUrlAllowed(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.startsWith("http://127.0.0.1") ||
            clean.startsWith("http://localhost") ||
            clean.startsWith("https://127.0.0.1") ||
            clean.startsWith("https://localhost")
    }

    fun hasReadySignal(logText: String): Boolean {
        return READY_REGEX.containsMatchIn(logText)
    }

    fun extractPortFromLogs(logText: String): Int? {
        for (regex in PORT_PATTERNS) {
            val matches = regex.findAll(logText).toList()
            if (matches.isNotEmpty()) {
                val portStr = matches.last().groupValues[1]
                val port = portStr.toIntOrNull()
                if (port != null && port in 80..65535) {
                    return port
                }
            }
        }
        return null
    }

    fun extractPortFromCommand(command: String): Int? {
        val httpServerMatch = Regex("""http\.server\s+(\d{2,5})\b""").find(command)
        if (httpServerMatch != null) return httpServerMatch.groupValues[1].toIntOrNull()
        val portFlagMatch = Regex("""(?:--port|-p|PORT=)\s*(\d{2,5})\b""").find(command)
        if (portFlagMatch != null) return portFlagMatch.groupValues[1].toIntOrNull()
        return null
    }

    suspend fun verifyLocalhostPortReachable(port: Int, timeoutMs: Int = 450): Boolean = withContext(Dispatchers.IO) {
        if (port !in 1..65535) return@withContext false
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }
}

class JobManager(
    private val backendProvider: suspend () -> ExecutionBackend,
    private val jobStore: JobStore? = null,
    private val secretExtractor: () -> List<String> = { emptyList() },
    private val onUnexpectedJobExit: (ManagedJobRecord, List<String>) -> Unit = { _, _ -> },
    private val portVerifier: suspend (Int) -> Boolean = { port -> ServerLogAnalyzer.verifyLocalhostPortReachable(port) },
    private val keepWakeLockProvider: suspend () -> Boolean = { false }
) {
    companion object {
        const val MAX_CONCURRENT_JOBS = 3
        const val MAX_LOG_FILE_BYTES = 5L * 1024L * 1024L
        const val MAX_UI_LOG_LINES = 500
        const val INITIAL_AGENT_LOG_LINES = 30

        fun buildStopEscalationScript(pid: Int): String {
            return buildString {
                append("kill -TERM -$pid 2>/dev/null || kill -TERM $pid 2>/dev/null || true; ")
                append("for i in 1 2 3 4 5 6; do ")
                append("if ! kill -0 $pid 2>/dev/null; then break; fi; ")
                append("sleep 0.5; ")
                append("done; ")
                append("if kill -0 $pid 2>/dev/null; then ")
                append("kill -KILL -$pid 2>/dev/null || kill -KILL $pid 2>/dev/null || true; ")
                append("fi; ")
                append("echo 'STOPPED'")
            }
        }

        fun jobDirPath(jobId: String): String = WorkspacePaths.jobDirectory(jobId)
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _jobs = MutableStateFlow<List<ManagedJobRecord>>(emptyList())
    val jobs: StateFlow<List<ManagedJobRecord>> = _jobs.asStateFlow()

    private val _reconciledRunningCountOnStart = MutableStateFlow(0)
    val reconciledRunningCountOnStart: StateFlow<Int> = _reconciledRunningCountOnStart.asStateFlow()

    fun dismissReconciledBanner() {
        _reconciledRunningCountOnStart.value = 0
    }

    suspend fun reconcilePersistedJobsOnStart(): List<ManagedJobRecord> = withContext(Dispatchers.IO) {
        val loaded = mutex.withLock {
            val stored = jobStore?.loadJobs().orEmpty()
            if (stored.isEmpty()) return@withLock emptyList()
            val backend = backendProvider()
            val backendReady = runCatching { backend.isAvailable() }.getOrDefault(false)
            val updated = stored.map { job ->
                if (job.state != ManagedJobState.RUNNING && job.state != ManagedJobState.STARTING) {
                    job
                } else if (!backendReady) {
                    job.copy(state = ManagedJobState.LOST)
                } else {
                    pollSingleJobInternal(backend, job, notifyOnExit = false)
                }
            }
            _jobs.value = updated
            jobStore?.saveJobs(updated)
            updated
        }
        val stillRunning = loaded.count { it.state == ManagedJobState.RUNNING }
        _reconciledRunningCountOnStart.value = stillRunning
        loaded
    }

    suspend fun startJob(
        command: String,
        cwd: String,
        readyWaitMs: Long = 5_000L,
        pollIntervalMs: Long = 450L
    ): StartJobOutcome = withContext(Dispatchers.IO) {
        val cleanCmd = command.trim()
        if (cleanCmd.isEmpty()) {
            return@withContext StartJobOutcome(
                success = false,
                error = TermuxError.JobStartFailed(command, -1, "Empty command"),
                message = "Cannot start an empty background job command."
            )
        }

        val activeRunning = _jobs.value.count { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }
        if (activeRunning >= MAX_CONCURRENT_JOBS) {
            return@withContext StartJobOutcome(
                success = false,
                error = TermuxError.JobStartFailed(
                    command = cleanCmd,
                    exitCode = -1,
                    logTail = "Maximum of $MAX_CONCURRENT_JOBS concurrent background jobs reached."
                ),
                message = "Maximum of $MAX_CONCURRENT_JOBS concurrent background jobs are already running. Stop an existing job first."
            )
        }

        val backend = backendProvider()
        if (!runCatching { backend.isAvailable() }.getOrDefault(false)) {
            return@withContext StartJobOutcome(
                success = false,
                error = TermuxError.ServiceUnavailable("Termux backend is unavailable for background job."),
                message = "Execution environment unavailable: Termux is not ready to launch background jobs."
            )
        }

        val jobId = "job_" + UUID.randomUUID().toString().replace("-", "").take(8)
        val effectiveCwd = cwd.ifBlank { WorkspacePaths.DEFAULT_SHARED_ROOT }
        val jobDir = WorkspacePaths.jobDirectory(jobId)
        val escapedJobDir = jobDir.replace("'", "'\\''")
        val escapedCwd = effectiveCwd.replace("'", "'\\''")
        val escapedInnerCmd = cleanCmd.replace("'", "'\\''")
        val useWakeLock = runCatching { keepWakeLockProvider() }.getOrDefault(false)
        val wakeLockPrefix = if (useWakeLock) "command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true; " else ""

        val launchScript = buildString {
            append("mkdir -p '$escapedJobDir' && ")
            append("cd '$escapedCwd' && ")
            append(": > '$escapedJobDir/out.log' && ")
            append("rm -f '$escapedJobDir/exit.code' && ")
            append(wakeLockPrefix)
            append("(if command -v setsid >/dev/null 2>&1; then ")
            append("setsid bash -lc '$escapedInnerCmd; echo \$? > \"$escapedJobDir/exit.code\"' > '$escapedJobDir/out.log' 2>&1 < /dev/null & ")
            append("else ")
            append("nohup bash -lc '$escapedInnerCmd; echo \$? > \"$escapedJobDir/exit.code\"' > '$escapedJobDir/out.log' 2>&1 < /dev/null & ")
            append("fi; ")
            append("__pid=\$!; echo \$__pid > '$escapedJobDir/job.pid'; printf 'NEXUS_JOB_PID:%s\\n' \"\$__pid\")")
        }

        val launchOut = StringBuilder()
        val launchErr = StringBuilder()
        var launchExit = -1
        runCatching {
            backend.exec(cmd = launchScript, cwd = effectiveCwd, timeoutSec = 20L).collect { ev ->
                when (ev) {
                    is ExecEvent.StdoutChunk -> launchOut.append(ev.text)
                    is ExecEvent.StderrChunk -> launchErr.append(ev.text)
                    is ExecEvent.Completed -> launchExit = ev.exitCode
                }
            }
        }.onFailure { e ->
            return@withContext StartJobOutcome(
                success = false,
                error = TermuxError.JobStartFailed(cleanCmd, -1, e.localizedMessage ?: "Launch error"),
                message = "Failed to start background job: ${e.localizedMessage}"
            )
        }

        val pidLine = launchOut.toString().lines().firstOrNull { it.startsWith("NEXUS_JOB_PID:") }
        val pid = pidLine?.substringAfter("NEXUS_JOB_PID:")?.trim()?.toIntOrNull() ?: -1
        if (launchExit != 0 || pid <= 0) {
            val errTail = redactLines((launchErr.toString() + "\n" + launchOut.toString()).lines())
                .joinToString("\n")
            return@withContext StartJobOutcome(
                success = false,
                error = TermuxError.JobStartFailed(cleanCmd, launchExit, errTail),
                initialRedactedLines = errTail.lines().filter { it.isNotBlank() },
                message = "Background job failed to spawn (exit $launchExit)."
            )
        }

        val initialRecord = ManagedJobRecord(
            id = jobId,
            command = cleanCmd,
            cwd = effectiveCwd,
            startedAtMs = System.currentTimeMillis(),
            pid = pid,
            port = ServerLogAnalyzer.extractPortFromCommand(cleanCmd),
            state = ManagedJobState.STARTING,
            lastReadByteOffset = 0L,
            logLines = emptyList()
        )

        mutex.withLock {
            val next = listOf(initialRecord) + _jobs.value.filterNot { it.id == jobId }
            _jobs.value = next
            jobStore?.saveJobs(next)
        }

        val deadline = System.currentTimeMillis() + readyWaitMs
        var currentJob = initialRecord
        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            currentJob = pollJobStatus(jobId, notifyOnExit = false) ?: currentJob
            val combinedLogs = currentJob.logLines.joinToString("\n")
            if (currentJob.state == ManagedJobState.EXITED || currentJob.state == ManagedJobState.LOST) {
                break
            }
            if (ServerLogAnalyzer.hasReadySignal(combinedLogs) || currentJob.port != null) {
                val candidatePort = currentJob.port ?: ServerLogAnalyzer.extractPortFromLogs(combinedLogs)
                if (candidatePort != null && portVerifier(candidatePort)) {
                    currentJob = updateJobRecord(currentJob.copy(port = candidatePort, state = ManagedJobState.RUNNING))
                    break
                } else if (ServerLogAnalyzer.hasReadySignal(combinedLogs)) {
                    currentJob = updateJobRecord(currentJob.copy(state = ManagedJobState.RUNNING))
                    break
                }
            }
        }

        if (currentJob.state == ManagedJobState.STARTING) {
            currentJob = updateJobRecord(currentJob.copy(state = ManagedJobState.RUNNING))
        }

        val first30 = currentJob.logLines.take(INITIAL_AGENT_LOG_LINES)
        if (currentJob.state == ManagedJobState.EXITED) {
            val code = currentJob.exitCode ?: 1
            return@withContext StartJobOutcome(
                success = false,
                job = currentJob,
                error = TermuxError.JobStartFailed(
                    command = cleanCmd,
                    exitCode = code,
                    logTail = currentJob.logLines.takeLast(20).joinToString("\n")
                ),
                initialRedactedLines = first30,
                message = "Job $jobId exited immediately with code $code."
            )
        }

        StartJobOutcome(
            success = true,
            job = currentJob,
            initialRedactedLines = first30,
            message = buildString {
                append("Job ${currentJob.id} is ${currentJob.state.label.uppercase()} (PID ${currentJob.pid})")
                if (currentJob.port != null) {
                    append(" · listening on http://127.0.0.1:${currentJob.port}")
                }
            }
        )
    }

    suspend fun pollJobStatus(jobId: String, notifyOnExit: Boolean = true): ManagedJobRecord? = withContext(Dispatchers.IO) {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return@withContext null
        val backend = backendProvider()
        if (!runCatching { backend.isAvailable() }.getOrDefault(false)) {
            val lost = current.copy(state = ManagedJobState.LOST)
            return@withContext updateJobRecord(lost)
        }
        val updated = pollSingleJobInternal(backend, current, notifyOnExit = notifyOnExit)
        updateJobRecord(updated)
    }

    suspend fun pollAllRunningJobs(): List<ManagedJobRecord> = withContext(Dispatchers.IO) {
        val activeIds = _jobs.value
            .filter { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }
            .map { it.id }
        for (id in activeIds) {
            pollJobStatus(id, notifyOnExit = true)
        }
        _jobs.value
    }

    suspend fun getJobLogs(jobId: String, maxLines: Int = 100): List<String> {
        val updated = pollJobStatus(jobId, notifyOnExit = false) ?: _jobs.value.firstOrNull { it.id == jobId }
        return updated?.logLines?.takeLast(maxLines.coerceIn(1, MAX_UI_LOG_LINES)).orEmpty()
    }

    suspend fun stopJob(jobId: String): ManagedJobRecord? = withContext(Dispatchers.IO) {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return@withContext null
        val backend = backendProvider()
        val pid = current.pid
        if (pid > 0 && runCatching { backend.isAvailable() }.getOrDefault(false)) {
            val stopScript = buildStopEscalationScript(pid)
            runCatching {
                backend.exec(cmd = stopScript, cwd = current.cwd, timeoutSec = 12L).collect { }
            }
        }
        val stopped = current.copy(
            state = ManagedJobState.STOPPED,
            exitCode = current.exitCode ?: 130,
            logLines = (current.logLines + "[Job stopped by user (SIGTERM -> SIGKILL)]").takeLast(MAX_UI_LOG_LINES)
        )
        updateJobRecord(stopped)
    }

    suspend fun waitForJob(jobId: String, maxSeconds: Long = 30L): ManagedJobRecord? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + (maxSeconds.coerceIn(1L, 120L) * 1000L)
        while (System.currentTimeMillis() < deadline) {
            val job = pollJobStatus(jobId, notifyOnExit = true) ?: _jobs.value.firstOrNull { it.id == jobId }
            if (job == null || job.state == ManagedJobState.EXITED || job.state == ManagedJobState.STOPPED || job.state == ManagedJobState.LOST) {
                return@withContext job
            }
            kotlinx.coroutines.delay(1000L)
        }
        pollJobStatus(jobId, notifyOnExit = false) ?: _jobs.value.firstOrNull { it.id == jobId }
    }

    suspend fun stopAllJobs() {
        val runningIds = _jobs.value
            .filter { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }
            .map { it.id }
        for (id in runningIds) {
            stopJob(id)
        }
    }

    /**
     * Stops any active background jobs running in [directoryPath] or any of its subdirectories.
     * Used before deleting temporary chat scratch folders (Amendment #9).
     */
    suspend fun stopJobsInDirectory(directoryPath: String) {
        val cleanTarget = directoryPath.trim().removeSuffix("/")
        if (cleanTarget.isEmpty()) return
        val matchingIds = _jobs.value
            .filter { job ->
                val cleanJobCwd = job.cwd.trim().removeSuffix("/")
                (job.state == ManagedJobState.RUNNING || job.state == ManagedJobState.STARTING) &&
                    (cleanJobCwd == cleanTarget || cleanJobCwd.startsWith("$cleanTarget/"))
            }
            .map { it.id }
        for (id in matchingIds) {
            stopJob(id)
        }
    }

    suspend fun restartJob(jobId: String): StartJobOutcome {
        val existing = _jobs.value.firstOrNull { it.id == jobId }
            ?: return StartJobOutcome(success = false, message = "Job $jobId not found.")
        if (existing.state == ManagedJobState.RUNNING || existing.state == ManagedJobState.STARTING) {
            stopJob(jobId)
        }
        mutex.withLock {
            val filtered = _jobs.value.filterNot { it.id == jobId }
            _jobs.value = filtered
            jobStore?.saveJobs(filtered)
        }
        return startJob(command = existing.command, cwd = existing.cwd)
    }

    private suspend fun pollSingleJobInternal(
        backend: ExecutionBackend,
        job: ManagedJobRecord,
        notifyOnExit: Boolean
    ): ManagedJobRecord {
        val jobDir = WorkspacePaths.jobDirectory(job.id)
        val escapedJobDir = jobDir.replace("'", "'\\''")
        val startByteOneBased = (job.lastReadByteOffset + 1L).coerceAtLeast(1L)

        val pollScript = buildString {
            append("__alive=0; ")
            append("if kill -0 ${job.pid} 2>/dev/null; then __alive=1; fi; ")
            append("__ec=''; ")
            append("if [ -f '$escapedJobDir/exit.code' ]; then __ec=\$(cat '$escapedJobDir/exit.code' 2>/dev/null | head -n 1); fi; ")
            append("__sz=0; ")
            append("if [ -f '$escapedJobDir/out.log' ]; then ")
            append("__sz=\$(wc -c < '$escapedJobDir/out.log' 2>/dev/null | tr -d ' '); ")
            append("if [ \"\${__sz:-0}\" -gt $MAX_LOG_FILE_BYTES ]; then ")
            append("tail -c 1048576 '$escapedJobDir/out.log' > '$escapedJobDir/out.log.tmp' && mv '$escapedJobDir/out.log.tmp' '$escapedJobDir/out.log'; ")
            append("__sz=\$(wc -c < '$escapedJobDir/out.log' 2>/dev/null | tr -d ' '); ")
            append("fi; ")
            append("fi; ")
            append("printf 'NEXUS_POLL_META:%s:%s:%s\\n' \"\$__alive\" \"\${__sz:-0}\" \"\$__ec\"; ")
            append("if [ -f '$escapedJobDir/out.log' ] && [ \"\${__sz:-0}\" -ge $startByteOneBased ]; then ")
            append("tail -c +$startByteOneBased '$escapedJobDir/out.log' | head -c 65536; ")
            append("fi")
        }

        val outBuf = StringBuilder()
        var completedOk = false
        runCatching {
            backend.exec(cmd = pollScript, cwd = job.cwd, timeoutSec = 10L).collect { ev ->
                when (ev) {
                    is ExecEvent.StdoutChunk -> outBuf.append(ev.text)
                    is ExecEvent.StderrChunk -> Unit
                    is ExecEvent.Completed -> completedOk = !ev.timedOut
                }
            }
        }

        if (!completedOk || outBuf.isBlank()) {
            return if (job.state == ManagedJobState.RUNNING || job.state == ManagedJobState.STARTING) {
                job.copy(state = ManagedJobState.LOST)
            } else {
                job
            }
        }

        val rawOut = outBuf.toString()
        val firstNewline = rawOut.indexOf('\n')
        val metaLine = if (firstNewline >= 0) rawOut.substring(0, firstNewline) else rawOut
        val deltaLogChunk = if (firstNewline >= 0) rawOut.substring(firstNewline + 1) else ""

        if (!metaLine.startsWith("NEXUS_POLL_META:")) {
            return job
        }

        val parts = metaLine.removePrefix("NEXUS_POLL_META:").split(":")
        val isAlive = parts.getOrNull(0)?.trim() == "1"
        val fileSize = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: job.lastReadByteOffset
        val recordedExitCode = parts.getOrNull(2)?.trim()?.toIntOrNull()

        val newRedactedLines = if (deltaLogChunk.isNotEmpty()) {
            redactLines(deltaLogChunk.lines())
        } else {
            emptyList()
        }

        val mergedLines = (job.logLines + newRedactedLines).takeLast(MAX_UI_LOG_LINES)
        val combinedLogText = mergedLines.joinToString("\n")
        val detectedPort = job.port
            ?: ServerLogAnalyzer.extractPortFromLogs(combinedLogText)
            ?: ServerLogAnalyzer.extractPortFromCommand(job.command)
        val verifiedPort = if (detectedPort != null && isAlive) {
            if (portVerifier(detectedPort)) detectedPort else detectedPort
        } else {
            detectedPort
        }

        val nextState = when {
            isAlive -> ManagedJobState.RUNNING
            job.state == ManagedJobState.STOPPED -> ManagedJobState.STOPPED
            else -> ManagedJobState.EXITED
        }
        val finalExitCode = if (!isAlive) (recordedExitCode ?: job.exitCode ?: 0) else null

        val updated = job.copy(
            port = verifiedPort,
            state = nextState,
            exitCode = finalExitCode,
            lastReadByteOffset = fileSize.coerceAtLeast(0L),
            logLines = mergedLines
        )

        if (notifyOnExit &&
            (job.state == ManagedJobState.RUNNING || job.state == ManagedJobState.STARTING) &&
            nextState == ManagedJobState.EXITED &&
            (finalExitCode ?: 0) != 0
        ) {
            onUnexpectedJobExit(updated, mergedLines.takeLast(6))
        }

        return updated
    }

    private suspend fun updateJobRecord(updated: ManagedJobRecord): ManagedJobRecord {
        mutex.withLock {
            val list = _jobs.value.map { if (it.id == updated.id) updated else it }
            _jobs.value = list
            jobStore?.saveJobs(list)
        }
        return updated
    }

    private fun redactLines(lines: List<String>): List<String> {
        val secrets = secretExtractor()
        return lines
            .filter { it.isNotEmpty() }
            .map { line -> NexusToolAndRedactor.redactSecrets(line, secrets).text }
    }
}
