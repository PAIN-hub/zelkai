package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class NexusNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_APPROVALS = "nexus_agent_approvals"
        const val CHANNEL_DOWNLOADS = "nexus_model_downloads"
        private const val APPROVAL_NOTIFICATION_BASE_ID = 4100
        private const val DOWNLOAD_NOTIFICATION_BASE_ID = 5200
    }

    init {
        ensureChannelsCreated()
    }

    private fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val approvalsChannel = NotificationChannel(
                CHANNEL_APPROVALS,
                "AI Command Approval Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the Zelkai AI Agent proposes a bash command and requires your permission."
                enableVibration(true)
            }

            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Local LLM Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download progress and completion alerts for on-device GGUF models."
            }

            manager.createNotificationChannel(approvalsChannel)
            manager.createNotificationChannel(downloadsChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun buildLaunchPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun notifyCommandApprovalNeeded(
        entryId: String,
        command: String,
        reason: String,
        isRisky: Boolean
    ) {
        if (!hasNotificationPermission()) return
        runCatching {
            val notifId = APPROVAL_NOTIFICATION_BASE_ID + (entryId.hashCode() and 0x7FF)
            val title = if (isRisky) {
                "High-Risk Command Needs Approval"
            } else {
                "AI Agent Needs Command Approval"
            }
            val body = "$ $command — $reason"

            val builder = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
                .setSmallIcon(R.drawable.nexus_app_icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Proposed bash command:\n$ $command\n\nReason: $reason\nTap to review and Approve or Deny."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(buildLaunchPendingIntent(notifId))

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }

    fun dismissApprovalNotification(entryId: String) {
        runCatching {
            val notifId = APPROVAL_NOTIFICATION_BASE_ID + (entryId.hashCode() and 0x7FF)
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun buildDownloadForegroundNotification(
        modelId: String,
        modelName: String,
        downloadedMb: Long,
        totalMb: Long,
        progressPercent: Int,
        speedMbps: String,
        isPaused: Boolean = false,
        isWaitingForNetwork: Boolean = false
    ): android.app.Notification {
        val notifId = DOWNLOAD_NOTIFICATION_BASE_ID + (modelId.hashCode() and 0x7FF)
        val title = when {
            isWaitingForNetwork -> "Paused (Offline): $modelName"
            isPaused -> "Download Paused: $modelName"
            else -> "Downloading $modelName ($progressPercent%)"
        }
        val statusText = when {
            isWaitingForNetwork -> "$downloadedMb / $totalMb MB · Auto-resumes on reconnect"
            isPaused -> "$downloadedMb / $totalMb MB · Tap to resume in Local LLM Hub"
            speedMbps.isNotBlank() -> "$downloadedMb / $totalMb MB · $speedMbps"
            else -> "$downloadedMb / $totalMb MB"
        }

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.nexus_app_icon)
            .setContentTitle(title)
            .setContentText(statusText)
            .setOngoing(!isPaused && !isWaitingForNetwork)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildLaunchPendingIntent(notifId))
            .build()
    }

    fun notifyDownloadProgress(
        modelId: String,
        modelName: String,
        downloadedMb: Long,
        totalMb: Long,
        progressPercent: Int,
        speedMbps: String,
        isPaused: Boolean = false,
        isWaitingForNetwork: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        runCatching {
            val notifId = DOWNLOAD_NOTIFICATION_BASE_ID + (modelId.hashCode() and 0x7FF)
            val notification = buildDownloadForegroundNotification(
                modelId = modelId,
                modelName = modelName,
                downloadedMb = downloadedMb,
                totalMb = totalMb,
                progressPercent = progressPercent,
                speedMbps = speedMbps,
                isPaused = isPaused,
                isWaitingForNetwork = isWaitingForNetwork
            )
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    fun notifyDownloadFinished(
        modelId: String,
        modelName: String,
        quantization: String,
        totalMb: Long
    ) {
        if (!hasNotificationPermission()) return
        runCatching {
            val notifId = DOWNLOAD_NOTIFICATION_BASE_ID + (modelId.hashCode() and 0x7FF)
            val builder = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
                .setSmallIcon(R.drawable.nexus_app_icon)
                .setContentTitle("Local LLM Download Complete")
                .setContentText("$modelName ($quantization · $totalMb MB) is ready for offline inference.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$modelName ($quantization · $totalMb MB) has been verified and installed on-device. Open Local LLM Hub to activate it."
                    )
                )
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(buildLaunchPendingIntent(notifId))

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }

    fun cancelDownloadNotification(modelId: String) {
        runCatching {
            val notifId = DOWNLOAD_NOTIFICATION_BASE_ID + (modelId.hashCode() and 0x7FF)
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun notifyUnexpectedJobExit(
        jobId: String,
        command: String,
        exitCode: Int,
        redactedLogLines: List<String>
    ) {
        if (!hasNotificationPermission()) return
        runCatching {
            val notifId = 6300 + (jobId.hashCode() and 0x7FF)
            val tailSnippet = redactedLogLines.takeLast(5).joinToString("\n").ifBlank { "(no log output)" }
            val builder = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
                .setSmallIcon(R.drawable.nexus_app_icon)
                .setContentTitle("Background Job Exited (code $exitCode)")
                .setContentText("$jobId ($command) stopped unexpectedly.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Job $jobId ($command) exited with code $exitCode.\n\nLast redacted log lines:\n$tailSnippet"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(buildLaunchPendingIntent(notifId))

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }
}
