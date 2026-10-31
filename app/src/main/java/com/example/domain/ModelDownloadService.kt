package com.example.domain

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.util.NexusNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY,
    FAILED
}

data class HeadProbeMetadata(
    val statusCode: Int,
    val contentLength: Long,
    val etag: String,
    val linkedSha256: String,
    val resolvedUrl: String,
    val timestampMs: Long
)

/**
 * Core HTTP Range + If-Range resumable GGUF downloader engine shared by [ModelDownloadService],
 * [ModelDownloadWorker], and local HTTP test servers (Part C #2–3 & Amendments #3–5).
 *
 * - Captures `x-linked-etag` / `ETag` and `Content-Length` from the first pre-redirect response
 *   at the canonical `/resolve/main/` URL and re-resolves redirects on every resume so expired
 *   signed CDN URLs never break resumed downloads.
 * - Sends `Range: bytes=<existing>-` and `If-Range: <etag>` when resuming `.part` files.
 * - Handles `206 Partial Content` (append to `.part`), `200 OK` (truncate `.part` and restart from 0),
 *   and `416 Range Not Satisfiable` (if `.part` length == `Content-Length`, proceed to integrity check;
 *   otherwise delete `.part` and restart).
 * - Computes real rolling-average `speedBytesPerSec` and `etaSeconds` from actual network bytes read.
 * - Zero artificial sleeps or synthetic progress steps.
 */
object ResumableGgufDownloader {

    private const val BUFFER_SIZE = 64 * 1024

    fun createNoRedirectClient(baseClient: OkHttpClient): OkHttpClient {
        return baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createStreamingClient(baseClient: OkHttpClient): OkHttpClient {
        return baseClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Probes the canonical URL (without following redirects on the first hop) so Hugging Face's
     * pre-redirect `x-linked-etag` (LFS SHA-256) and `x-linked-size` headers are captured before
     * following `Location` to the signed CDN URL.
     */
    fun probeCanonicalHeaders(
        canonicalUrl: String,
        hfToken: String = "",
        baseClient: OkHttpClient = OkHttpClient()
    ): HeadProbeMetadata {
        val noRedirect = createNoRedirectClient(baseClient)
        val reqBuilder = Request.Builder()
            .url(canonicalUrl)
            .head()
        if (hfToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${hfToken.trim()}")
        }

        var linkedEtag = ""
        var linkedSize = -1L
        var redirectLocation: String? = null
        var firstCode = 0

        noRedirect.newCall(reqBuilder.build()).execute().use { resp ->
            firstCode = resp.code
            val rawLinkedEtag = resp.header("x-linked-etag")
                ?: resp.header("X-Linked-ETag")
                ?: resp.header("ETag")
                ?: ""
            linkedEtag = rawLinkedEtag.trim()
            val rawLinkedSize = resp.header("x-linked-size")
                ?: resp.header("X-Linked-Size")
                ?: resp.header("Content-Length")
            linkedSize = rawLinkedSize?.trim()?.toLongOrNull() ?: -1L
            if (resp.code in 301..308) {
                redirectLocation = resp.header("Location")
            }
        }

        var finalUrl = canonicalUrl
        var finalCode = firstCode
        var finalLength = linkedSize
        var finalEtag = linkedEtag

        if (!redirectLocation.isNullOrBlank()) {
            val resolvedTarget = if (redirectLocation!!.startsWith("http")) {
                redirectLocation!!
            } else {
                val origin = canonicalUrl.substringBefore("/resolve/")
                    .substringBeforeLast("/")
                "$origin/${redirectLocation!!.trimStart('/')}"
            }
            finalUrl = resolvedTarget
            val followClient = createStreamingClient(baseClient)
            val followReq = Request.Builder().url(resolvedTarget).head().build()
            runCatching {
                followClient.newCall(followReq).execute().use { followResp ->
                    finalCode = followResp.code
                    if (finalLength <= 0L) {
                        finalLength = followResp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                    if (finalEtag.isBlank()) {
                        finalEtag = followResp.header("ETag")?.trim().orEmpty()
                    }
                }
            }
        }

        val cleanSha = linkedEtag.trim('"')
        val validSha = if (ModelRegistry.isValidHexSha256(cleanSha)) cleanSha.lowercase() else ""

        return HeadProbeMetadata(
            statusCode = finalCode,
            contentLength = finalLength,
            etag = finalEtag,
            linkedSha256 = validSha,
            resolvedUrl = finalUrl,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Executes a resumable download into `modelsDir/<modelId>.gguf.part`, verifies integrity,
     * and atomically renames to `modelsDir/<modelId>.gguf`.
     */
    suspend fun downloadModelResumable(
        modelId: String,
        canonicalUrl: String,
        modelsDir: File,
        expectedManifestSha256: String = "",
        expectedManifestSizeBytes: Long = -1L,
        hfToken: String = "",
        storedEtag: String = "",
        baseClient: OkHttpClient = OkHttpClient(),
        onProgress: (LocalModelDownloadState, String) -> Unit
    ): Result<Sha256VerificationResult> = withContext(Dispatchers.IO) {
        modelsDir.mkdirs()
        val partFile = File(modelsDir, "$modelId.gguf.part")
        val finalFile = File(modelsDir, "$modelId.gguf")

        try {
            val probe = runCatching {
                probeCanonicalHeaders(canonicalUrl, hfToken, baseClient)
            }.getOrNull()

            if (probe != null && (probe.statusCode == 401 || probe.statusCode == 403 || probe.statusCode == 404)) {
                val msg = if (probe.statusCode == 401 || probe.statusCode == 403) {
                    "HTTP ${probe.statusCode}: Access denied. Add a Hugging Face token in Settings if this repository is gated."
                } else {
                    "HTTP 404: Model file not found at remote repository."
                }
                return@withContext Result.failure(IOException(msg))
            }

            val activeEtag = probe?.etag?.ifBlank { storedEtag } ?: storedEtag
            val resolvedSha256 = expectedManifestSha256.takeIf { ModelRegistry.isValidHexSha256(it) }
                ?: probe?.linkedSha256.orEmpty()
            var totalBytes = when {
                probe != null && probe.contentLength > 0L -> probe.contentLength
                expectedManifestSizeBytes > 0L -> expectedManifestSizeBytes
                else -> -1L
            }

            var restartAttemptedAfter416 = false

            while (true) {
                currentCoroutineContext().ensureActive()
                val existingBytes = if (partFile.exists()) partFile.length() else 0L

                // Always request against canonicalUrl (or fresh redirect) so expired CDN signatures are re-resolved
                val streamClient = createStreamingClient(baseClient)
                val reqBuilder = Request.Builder()
                    .url(canonicalUrl)
                    .get()

                if (hfToken.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${hfToken.trim()}")
                }
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                    if (activeEtag.isNotBlank()) {
                        reqBuilder.header("If-Range", activeEtag)
                    }
                }

                var shouldRetryFromZero = false

                streamClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val code = resp.code

                    if (code == 416) {
                        if (totalBytes > 0L && existingBytes == totalBytes) {
                            // Already fully downloaded in .part; proceed to integrity verification
                            return@use
                        } else if (!restartAttemptedAfter416) {
                            restartAttemptedAfter416 = true
                            runCatching { partFile.delete() }
                            shouldRetryFromZero = true
                            return@use
                        } else {
                            throw IOException("HTTP 416 Range Not Satisfiable for $modelId")
                        }
                    }

                    if (code != 200 && code != 206) {
                        throw IOException("Download failed with HTTP $code")
                    }

                    val isPartialAppend = (code == 206 && existingBytes > 0L)
                    if (code == 200 && existingBytes > 0L) {
                        // Server ignored Range or If-Range mismatched -> truncate .part and start at 0
                        FileOutputStream(partFile, false).use { /* truncate */ }
                    }

                    val body = resp.body ?: throw IOException("Empty response body")
                    val responseContentLen = body.contentLength()
                    val startOffset = if (isPartialAppend) existingBytes else 0L
                    if (responseContentLen > 0L) {
                        totalBytes = startOffset + responseContentLen
                    }

                    val responseEtag = resp.header("x-linked-etag")
                        ?: resp.header("ETag")
                        ?: activeEtag

                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesWrittenTotal = startOffset
                    var windowStartMs = System.currentTimeMillis()
                    var windowBytes = 0L
                    var rollingSpeedBps = 0L
                    var lastEmitMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, isPartialAppend).use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                currentCoroutineContext().ensureActive()
                                output.write(buffer, 0, read)
                                bytesWrittenTotal += read
                                windowBytes += read

                                val now = System.currentTimeMillis()
                                val elapsedWindowMs = (now - windowStartMs).coerceAtLeast(1L)
                                if (elapsedWindowMs >= 250L) {
                                    val instantBps = (windowBytes * 1000L) / elapsedWindowMs
                                    rollingSpeedBps = if (rollingSpeedBps == 0L) {
                                        instantBps
                                    } else {
                                        ((rollingSpeedBps * 7L) + (instantBps * 3L)) / 10L
                                    }
                                    windowStartMs = now
                                    windowBytes = 0L
                                }

                                if (now - lastEmitMs >= 120L || (totalBytes > 0L && bytesWrittenTotal >= totalBytes)) {
                                    lastEmitMs = now
                                    val dlMb = bytesWrittenTotal / (1024L * 1024L)
                                    val totMb = if (totalBytes > 0L) {
                                        (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                                    } else {
                                        (dlMb + 1L).coerceAtLeast(1L)
                                    }
                                    val frac = if (totalBytes > 0L) {
                                        (bytesWrittenTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                    } else {
                                        0.5f
                                    }
                                    val remainingBytes = (totalBytes - bytesWrittenTotal).coerceAtLeast(0L)
                                    val etaSec = if (rollingSpeedBps > 0L && totalBytes > 0L) {
                                        remainingBytes / rollingSpeedBps
                                    } else {
                                        -1L
                                    }
                                    val speedLabel = formatSpeedAndEta(rollingSpeedBps, etaSec)
                                    val state = LocalModelDownloadState(
                                        modelId = modelId,
                                        progressFraction = frac,
                                        downloadedMb = dlMb,
                                        totalMb = totMb,
                                        downloadedBytes = bytesWrittenTotal,
                                        totalBytes = totalBytes.coerceAtLeast(bytesWrittenTotal),
                                        speedBytesPerSec = rollingSpeedBps,
                                        etaSeconds = etaSec,
                                        status = DownloadStatus.DOWNLOADING,
                                        stageLabel = "Downloading GGUF ($dlMb / $totMb MB)…",
                                        isDownloading = true,
                                        isCompleted = false,
                                        isPaused = false,
                                        isWaitingForNetwork = false,
                                        speedMbps = speedLabel,
                                        etag = responseEtag
                                    )
                                    onProgress(state, responseEtag)
                                }

                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }
                }

                if (shouldRetryFromZero) {
                    continue
                }
                break
            }

            currentCoroutineContext().ensureActive()

            val finalBytes = partFile.length()
            val finalMb = (finalBytes / (1024L * 1024L)).coerceAtLeast(1L)
            onProgress(
                LocalModelDownloadState(
                    modelId = modelId,
                    progressFraction = 0.99f,
                    downloadedMb = finalMb,
                    totalMb = finalMb,
                    downloadedBytes = finalBytes,
                    totalBytes = finalBytes,
                    status = DownloadStatus.VERIFYING,
                    stageLabel = "Verifying GGUF header & SHA-256 integrity…",
                    isDownloading = true,
                    isCompleted = false
                ),
                activeEtag
            )

            val expectedLen = if (totalBytes > 0L) totalBytes else -1L
            val verification = ModelRegistry.verifyDownloadedFileSha256(
                file = partFile,
                expectedSha256 = resolvedSha256,
                expectedContentLength = expectedLen
            )
            if (!verification.verified) {
                runCatching { partFile.delete() }
                return@withContext Result.failure(IOException(verification.reason))
            }

            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = partFile.renameTo(finalFile)
            if (!renamed) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            val badgeText = if (verification.integrityUnverified) {
                "Installed · Integrity unverified (GGUF header verified)"
            } else {
                "Installed · SHA-256 verified"
            }

            onProgress(
                LocalModelDownloadState(
                    modelId = modelId,
                    progressFraction = 1f,
                    downloadedMb = finalMb,
                    totalMb = finalMb,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length(),
                    status = DownloadStatus.READY,
                    stageLabel = badgeText,
                    isDownloading = false,
                    isCompleted = true,
                    integrityUnverified = verification.integrityUnverified
                ),
                activeEtag
            )

            Result.success(verification)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun formatSpeedAndEta(speedBps: Long, etaSec: Long): String {
        if (speedBps <= 0L) return ""
        val mbps = speedBps.toDouble() / (1024.0 * 1024.0)
        val speedPart = if (mbps >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbps)
        } else {
            val kbps = (speedBps / 1024L).coerceAtLeast(1L)
            "${kbps} KB/s"
        }
        return if (etaSec in 1..86400) {
            val mins = etaSec / 60
            val secs = etaSec % 60
            if (mins > 0) "$speedPart · ${mins}m ${secs}s left" else "$speedPart · ${secs}s left"
        } else {
            speedPart
        }
    }
}

/**
 * Dedicated Android Foreground Service (`foregroundServiceType="dataSync"`) for resumable GGUF model
 * downloads (Amendment #5).
 * - Implements `onTimeout(startId: Int, fgsType: Int)` for Android 15+ (`API 35+`).
 * - Stays alive while `PAUSED` waiting for network so `ConnectivityManager.NetworkCallback`
 *   auto-resumes immediately when connectivity returns, and schedules [ModelDownloadWorker]
 *   via WorkManager as a process-death fallback.
 */
class ModelDownloadService : Service() {

    companion object {
        const val ACTION_START_OR_RESUME = "com.example.action.START_OR_RESUME_DOWNLOAD"
        const val ACTION_PAUSE = "com.example.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.action.CANCEL_DOWNLOAD"

        const val EXTRA_MODEL_ID = "extra_model_id"
        private const val FOREGROUND_NOTIFICATION_ID = 4100

        @Volatile
        var managerRef: LocalLlmManager? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private lateinit var notificationHelper: NexusNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NexusNotificationHelper(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_OR_RESUME
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)?.trim().orEmpty()
        if (modelId.isEmpty()) {
            stopSelfIfIdle()
            return START_NOT_STICKY
        }

        val manager = managerRef ?: LocalLlmManager.getInstance(applicationContext)

        when (action) {
            ACTION_START_OR_RESUME -> {
                val spec = LocalLlmManager.findSpec(modelId)
                if (spec != null) {
                    promoteToForeground(spec)
                    launchServiceDownload(manager, spec)
                }
            }
            ACTION_PAUSE -> {
                activeJobs[modelId]?.cancel()
                activeJobs.remove(modelId)
                manager.onServicePausedDownload(modelId)
            }
            ACTION_CANCEL -> {
                activeJobs[modelId]?.cancel()
                activeJobs.remove(modelId)
                manager.onServiceCancelledDownload(modelId)
                stopSelfIfIdle()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(spec: LocalLlmSpec) {
        runCatching {
            val notification = notificationHelper.buildDownloadForegroundNotification(
                modelId = spec.id,
                modelName = spec.name,
                downloadedMb = 0L,
                totalMb = spec.downloadSizeMb,
                progressPercent = 0,
                speedMbps = "Starting…"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun launchServiceDownload(manager: LocalLlmManager, spec: LocalLlmSpec) {
        if (activeJobs[spec.id]?.isActive == true) return
        val job = serviceScope.launch {
            try {
                manager.executeRealDownloadInternal(spec)
            } finally {
                activeJobs.remove(spec.id)
                stopSelfIfIdle()
            }
        }
        activeJobs[spec.id] = job
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 (API 35+) dataSync foreground service timeout handler:
        // Persist current checkpoints, enqueue WorkManager continuation, and stop gracefully.
        val manager = managerRef ?: LocalLlmManager.getInstance(applicationContext)
        activeJobs.keys().toList().forEach { modelId ->
            activeJobs[modelId]?.cancel()
            manager.onServicePausedForNetworkOrTimeout(modelId, "Paused by OS background limit · Auto-resumes via WorkManager")
            ModelDownloadWorker.enqueueResumeWork(applicationContext, modelId, manager.wifiOnlyDownloads.value)
        }
        activeJobs.clear()
        stopSelf(startId)
    }

    private fun stopSelfIfIdle() {
        val manager = managerRef
        val hasWaiting = manager?.downloadStates?.value?.values?.any { it.isWaitingForNetwork || it.isDownloading } == true
        if (activeJobs.isEmpty() && !hasWaiting) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

/**
 * WorkManager CoroutineWorker with `NetworkType.CONNECTED` / `NetworkType.UNMETERED` constraint
 * that automatically resumes interrupted GGUF downloads if the Foreground Service is stopped.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val KEY_MODEL_ID = "model_id"

        fun enqueueResumeWork(context: Context, modelId: String, wifiOnly: Boolean) {
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_MODEL_ID to modelId))
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "gguf_download_$modelId",
                    ExistingWorkPolicy.REPLACE,
                    req
                )
            }
        }
    }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)?.trim().orEmpty()
        if (modelId.isEmpty()) return Result.failure()
        val spec = LocalLlmManager.findSpec(modelId) ?: return Result.failure()
        val manager = ModelDownloadService.managerRef ?: LocalLlmManager.getInstance(applicationContext)
        val ok = manager.executeRealDownloadInternal(spec)
        return if (ok) Result.success() else Result.retry()
    }
}
