package com.example.domain

/**
 * Typed Termux & ExecutionBackend errors for Nexus Agent v5.
 * Covers both foreground command execution and detached background jobs.
 */
sealed class TermuxError(open val summaryMessage: String) {
    data class TermuxNotInstalled(
        override val summaryMessage: String = "Termux (com.termux) is not installed on this device."
    ) : TermuxError(summaryMessage)

    data class PermissionDenied(
        val permanentlyDenied: Boolean = false,
        override val summaryMessage: String = "Termux RUN_COMMAND permission is not granted."
    ) : TermuxError(summaryMessage)

    data class ExternalAppsDisabled(
        override val summaryMessage: String = "allow-external-apps is not enabled in ~/.termux/termux.properties."
    ) : TermuxError(summaryMessage)

    data class ServiceUnavailable(
        val detail: String = "Termux RunCommandService did not respond within 10s.",
        override val summaryMessage: String = "Termux app is stopped, killed by battery saver, or unreachable."
    ) : TermuxError(summaryMessage)

    data class Timeout(
        val timeoutSec: Long,
        val command: String = "",
        override val summaryMessage: String = "Command timed out after ${timeoutSec}s."
    ) : TermuxError(summaryMessage)

    data class ResultTooLarge(
        val totalBytes: Long,
        val truncatedPreview: String,
        override val summaryMessage: String = "Command output exceeded Binder IPC limit (${totalBytes / 1024} KB); spooled via chunked file reader."
    ) : TermuxError(summaryMessage)

    data class ResultParseError(
        val detail: String,
        override val summaryMessage: String = "Could not parse result bundle returned from Termux."
    ) : TermuxError(summaryMessage)

    data class NonZeroExit(
        val code: Int,
        val stderrTail: String,
        override val summaryMessage: String = "Command exited with code $code."
    ) : TermuxError(summaryMessage)

    data class NetworkError(
        val detail: String = "",
        override val summaryMessage: String = "Network unreachable or DNS lookup failed inside Termux."
    ) : TermuxError(summaryMessage)

    data class StorageFull(
        val freeBytes: Long = -1L,
        override val summaryMessage: String = "No space left on device storage."
    ) : TermuxError(summaryMessage)

    data class JobStartFailed(
        val command: String,
        val exitCode: Int = -1,
        val logTail: String = "",
        override val summaryMessage: String = "Background job failed to start: $command"
    ) : TermuxError(summaryMessage)

    data class JobLost(
        val jobId: String,
        val command: String,
        override val summaryMessage: String = "Background job $jobId is no longer running (Termux session ended)."
    ) : TermuxError(summaryMessage)

    data class PortNotDetected(
        val jobId: String,
        override val summaryMessage: String = "No listening localhost port was detected in the server logs."
    ) : TermuxError(summaryMessage)

    data class WorkingDirUnreachable(
        val workspacePath: String,
        val detail: String = "",
        val missingAllFilesAccess: Boolean = false,
        val missingTermuxStorage: Boolean = false,
        override val summaryMessage: String = "Workspace '$workspacePath' is not accessible or writable."
    ) : TermuxError(summaryMessage)

    data class SymlinkRequiredOnSharedStorage(
        val command: String,
        val detail: String = "",
        override val summaryMessage: String = "This project needs symlinks. Move it to Termux home?"
    ) : TermuxError(summaryMessage)

    data class Unknown(
        val rawCause: String,
        override val summaryMessage: String = "Unexpected execution error."
    ) : TermuxError(summaryMessage)
}

enum class TermuxErrorActionType(val label: String) {
    RETRY("Retry"),
    OPEN_TERMUX("Open Termux"),
    COPY_COMMAND("Copy command"),
    OPEN_SETTINGS("Open settings"),
    USE_LIMITED_SHELL("Use limited shell"),
    COPY_DIAGNOSTICS("Copy diagnostics"),
    RE_RUN_AS_BACKGROUND_JOB("Re-run as background job"),
    RERUN_AS_BACKGROUND_JOB("Re-run as background job"),
    RESTART_JOB("Restart"),
    GRANT_ALL_FILES_ACCESS("Grant All Files Access"),
    TERMUX_STORAGE_GUIDE("termux-setup-storage guide"),
    USE_TERMUX_HOME("Use Termux home"),
    MOVE_PROJECT_TO_TERMUX_HOME("Move to Termux home"),
    KEEP_PROJECT_HERE("Keep here")
}

data class TermuxErrorAction(
    val type: TermuxErrorActionType,
    val label: String = type.label,
    val payload: String = ""
)

data class TermuxErrorPresentation(
    val error: TermuxError,
    val categoryId: String,
    val title: String,
    val oneLineCause: String,
    val numberedFixSteps: List<String>,
    val suggestedCommand: String? = null,
    val canFallbackToLimitedShell: Boolean = false,
    val actionTypes: List<TermuxErrorActionType> = listOf(
        TermuxErrorActionType.RETRY,
        TermuxErrorActionType.OPEN_TERMUX,
        TermuxErrorActionType.COPY_DIAGNOSTICS
    ),
    val redactedDiagnosticsReport: String = ""
) {
    val probableCause: String get() = oneLineCause
    val numberedSteps: List<String> get() = numberedFixSteps
    val actions: List<TermuxErrorAction>
        get() = actionTypes.map { t ->
            val payload = when (t) {
                TermuxErrorActionType.COPY_COMMAND -> suggestedCommand.orEmpty()
                TermuxErrorActionType.COPY_DIAGNOSTICS -> redactedDiagnosticsReport
                else -> ""
            }
            TermuxErrorAction(type = t, label = t.label, payload = payload)
        }
}
