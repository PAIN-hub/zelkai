package com.example.ui.nexus

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.BackendHealthState
import com.example.domain.ManagedJobRecord
import com.example.domain.ManagedJobState
import com.example.domain.ServerLogAnalyzer
import com.example.domain.TermuxErrorAction
import com.example.domain.TermuxErrorActionType
import com.example.domain.TermuxErrorPresentation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusPalette
import com.example.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.delay

/**
 * Compact status chip displayed above the chat composer when background jobs are running
 * (e.g., `"Server running · :3000"`). Designed to never wrap or clip on narrow 320dp screens.
 */
@Composable
fun NexusActiveJobsStatusChip(
    jobs: List<ManagedJobRecord>,
    palette: NexusPalette,
    onOpenJobsSheet: () -> Unit,
    onOpenPreviewPort: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val runningJobs = remember(jobs) {
        jobs.filter { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }
    }
    if (runningJobs.isEmpty()) return

    val primaryJob = runningJobs.firstOrNull { it.port != null } ?: runningJobs.first()
    val chipLabel = remember(runningJobs, primaryJob) {
        when {
            runningJobs.size == 1 && primaryJob.port != null ->
                "Server running · :${primaryJob.port}"
            runningJobs.size == 1 ->
                "Job running · ${primaryJob.command.take(18)}"
            primaryJob.port != null ->
                "${runningJobs.size} jobs · :${primaryJob.port}"
            else ->
                "${runningJobs.size} jobs running"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = palette.safeGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, palette.safeGreen.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onOpenJobsSheet() }
                .testTag("active_jobs_status_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.safeGreen)
                )
                Text(
                    text = chipLabel,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = palette.safeGreen,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (primaryJob.port != null) {
            Surface(
                color = palette.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onOpenPreviewPort(primaryJob.port, primaryJob.command) }
                    .testTag("chip_open_preview_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Open preview",
                        tint = palette.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Open preview",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = palette.accent,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * App-start banner shown when reconciled jobs are still alive in Termux:
 * `"N jobs are still running in Termux: Manage"`.
 */
@Composable
fun NexusReconciledJobsBanner(
    runningCount: Int,
    palette: NexusPalette,
    onManageClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (runningCount <= 0) return
    val label = if (runningCount == 1) {
        "1 job is still running in Termux"
    } else {
        "$runningCount jobs are still running in Termux"
    }

    Surface(
        color = palette.accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("reconciled_jobs_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onManageClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("reconciled_jobs_manage_button")
            ) {
                Text(
                    text = "Manage",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.accent,
                    softWrap = false,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss banner",
                    tint = palette.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Circuit breaker banner shown when Termux fails 3 consecutive infrastructure checks.
 */
@Composable
fun NexusCircuitBreakerBanner(
    healthState: BackendHealthState,
    reason: String?,
    palette: NexusPalette,
    onRecheck: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (healthState != BackendHealthState.UNHEALTHY) return

    Surface(
        color = palette.WarnAmber.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.WarnAmber.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("circuit_breaker_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = palette.WarnAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Termux backend unhealthy (circuit breaker open)",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = reason ?: "3 consecutive Termux infrastructure failures occurred. Automatic retries are paused.",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRecheck,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("circuit_breaker_recheck_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-check now", fontFamily = PoppinsFontFamily, fontSize = 11.sp, softWrap = false)
                }
                TextButton(
                    onClick = onOpenSetup,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("circuit_breaker_setup_button")
                ) {
                    Text("Open Setup Wizard", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = palette.accent, softWrap = false)
                }
            }
        }
    }
}

/**
 * Inline chat card shown when the user asks for `npm`, `git`, `node`, or `python`
 * while the Termux toolchain setup is incomplete.
 */
@Composable
fun NexusInlineSetupRequiredCard(
    missingToolsSummary: String,
    palette: NexusPalette,
    onOpenSetupWizard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = palette.surfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("inline_setup_environment_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Set up execution environment",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = palette.textMuted, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = missingToolsSummary.ifBlank {
                    "This task uses developer tools (node, npm, git, or python) that require Termux setup."
                },
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = palette.textSecondary
            )
            Button(
                onClick = onOpenSetupWizard,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("inline_open_setup_wizard_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Set up environment", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Confirmation dialog before falling back to AndroidShellBackend for a read-only command.
 * Never switches backends silently.
 */
@Composable
fun NexusLimitedShellFallbackDialog(
    command: String,
    reason: String,
    palette: NexusPalette,
    onConfirmLimitedShell: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = palette.surfaceModal,
        title = {
            Text(
                text = "Termux unavailable",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = palette.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Termux unavailable. Run this read-only command in the limited shell?",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = palette.textPrimary
                )
                if (reason.isNotBlank()) {
                    Text(
                        text = reason,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                Surface(
                    color = palette.terminalBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$ $command",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmLimitedShell,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_limited_shell_fallback_button")
            ) {
                Text("Use limited shell", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onOpenSetupWizard) {
                    Text("Set up Termux", fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = palette.accent)
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel", fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = palette.textSecondary)
                }
            }
        }
    )
}

/**
 * Typed Error Card displayed in chat or terminal when a command hits a [TermuxError].
 * Shows title, one-line probable cause, numbered fix steps, and action buttons:
 * Retry, Open Termux, Copy command, Open settings, "Use limited shell", "Copy diagnostics",
 * or "Re-run as background job".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermuxErrorCard(
    presentation: TermuxErrorPresentation,
    palette: NexusPalette,
    onActionClick: (TermuxErrorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    Surface(
        color = palette.surfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.WarnAmber.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("termux_error_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = palette.WarnAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = presentation.title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = presentation.probableCause,
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = palette.textSecondary
            )

            if (presentation.numberedSteps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceInput.copy(alpha = 0.65f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presentation.numberedSteps.forEachIndexed { idx, step ->
                        Text(
                            text = "${idx + 1}. $step",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            color = palette.textPrimary
                        )
                    }
                }
            }

            if (copiedLabel != null) {
                Text(
                    text = copiedLabel!!,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = palette.safeGreen
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presentation.actions.forEach { action ->
                    val isPrimary = action.type == TermuxErrorActionType.RETRY ||
                        action.type == TermuxErrorActionType.RERUN_AS_BACKGROUND_JOB ||
                        action.type == TermuxErrorActionType.COPY_COMMAND
                    if (isPrimary) {
                        Button(
                            onClick = {
                                if (action.type == TermuxErrorActionType.COPY_COMMAND && action.payload.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(action.payload))
                                    copiedLabel = "Copied command to clipboard"
                                }
                                onActionClick(action)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Text(
                                text = action.label,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (action.type == TermuxErrorActionType.COPY_COMMAND && action.payload.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(action.payload))
                                    copiedLabel = "Copied command to clipboard"
                                } else if (action.type == TermuxErrorActionType.COPY_DIAGNOSTICS) {
                                    copiedLabel = "Copied redacted diagnostics to clipboard"
                                }
                                onActionClick(action)
                            },
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Text(
                                text = action.label,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = palette.textPrimary,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full Background Jobs Sheet reachable from the chat status chip or Sidebar Workspace item.
 * Shows "Stop all" at the top, and for each job: command, state, uptime, port,
 * and buttons for Logs (live tail, auto-scroll, Copy, max 500 lines), Preview, Restart, and Stop.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NexusJobsBottomSheet(
    jobs: List<ManagedJobRecord>,
    palette: NexusPalette,
    onDismiss: () -> Unit,
    onStopJob: (String) -> Unit,
    onStopAllJobs: () -> Unit,
    onRestartJob: (String) -> Unit,
    onRefreshJobs: () -> Unit,
    onOpenPreview: (Int, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedLogJobId by remember { mutableStateOf<String?>(null) }
    var autoScrollLogs by remember { mutableStateOf(true) }
    var wrapLogs by remember { mutableStateOf(false) }
    var clearedLogJobId by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val clipboard = LocalClipboardManager.current

    // Tick every second while sheet is visible to update job uptime and poll logs
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            onRefreshJobs()
            delay(1500L)
        }
    }

    val selectedLogJob = jobs.firstOrNull { it.id == selectedLogJobId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceModal,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with "Stop all" at the top
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Jobs (${jobs.count { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }}/${com.example.domain.JobManager.MAX_CONCURRENT_JOBS} active)",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Detached Termux process groups · Max 3 concurrent",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val hasRunning = jobs.any { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING }
                if (hasRunning) {
                    OutlinedButton(
                        onClick = onStopAllJobs,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.dangerRed),
                        border = BorderStroke(1.dp, palette.dangerRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("jobs_sheet_stop_all_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop all", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, softWrap = false)
                    }
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            if (selectedLogJob != null) {
                // Live Log Tail View (max 500 lines, ANSI stripping, auto-scroll with pause, wrap toggle, Clear & Copy)
                val isCleared = clearedLogJobId == selectedLogJob.id
                val rawLogLines = if (isCleared) emptyList() else selectedLogJob.logLines.takeLast(500)
                val logLines = remember(rawLogLines) {
                    rawLogLines.map { stripAnsiCodes(it) }
                }
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()

                // Detect if user has scrolled up from the bottom to pause auto-scrolling
                val isAtBottom by remember(logLines.size) {
                    derivedStateOf {
                        if (logLines.isEmpty()) true
                        else {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= logLines.size - 2
                        }
                    }
                }

                LaunchedEffect(logLines.size, autoScrollLogs, isAtBottom) {
                    if (autoScrollLogs && isAtBottom && logLines.isNotEmpty()) {
                        listState.animateScrollToItem(logLines.lastIndex)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(onClick = { selectedLogJobId = null }) {
                        Text("← Back", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.accent)
                    }
                    Text(
                        text = "${selectedLogJob.id} · ${logLines.size} lines",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { wrapLogs = !wrapLogs },
                        modifier = Modifier.testTag("job_logs_wrap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WrapText,
                            contentDescription = "Toggle line wrapping",
                            tint = if (wrapLogs) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { clearedLogJobId = selectedLogJob.id },
                        modifier = Modifier.testTag("job_logs_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear log view",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(logLines.joinToString("\n")))
                        },
                        modifier = Modifier.testTag("job_logs_copy_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    CodeSurface(
                        backgroundColor = palette.logSurface,
                        contentColor = palette.onLogSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp)
                    ) {
                        if (logLines.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No output yet · Job is ${selectedLogJob.state.name.lowercase()}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = palette.onLogSurface.copy(alpha = 0.65f)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(logLines) { line ->
                                    val isStderr = line.contains("ERR", ignoreCase = true) ||
                                        line.contains("Error", ignoreCase = true) ||
                                        line.contains("Exception", ignoreCase = true) ||
                                        line.contains("FATAL", ignoreCase = true)
                                    val isWarn = !isStderr && (line.contains("WARN", ignoreCase = true) || line.contains("warning", ignoreCase = true))

                                    val lineContentColor = when {
                                        isStderr -> palette.logStderr
                                        isWarn -> palette.logWarning
                                        else -> palette.onLogSurface
                                    }

                                    Text(
                                        text = line,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp,
                                        color = lineContentColor,
                                        softWrap = wrapLogs
                                    )
                                }
                            }
                        }
                    }

                    // Floating "Jump to latest" button when user is scrolled up
                    if (!isAtBottom && logLines.isNotEmpty()) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(logLines.lastIndex)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .testTag("jump_to_latest_log_button")
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Jump to latest", fontFamily = PoppinsFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No background jobs running",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Dev servers (npm run dev, python -m http.server, flask run, node server.js) run here detached without blocking the agent.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        val isRunning = job.state == ManagedJobState.RUNNING || job.state == ManagedJobState.STARTING
                        val stateColor = when (job.state) {
                            ManagedJobState.RUNNING, ManagedJobState.STARTING -> palette.safeGreen
                            ManagedJobState.STOPPED -> palette.textMuted
                            ManagedJobState.EXITED -> if ((job.exitCode ?: 0) == 0) palette.accent else palette.WarnAmber
                            ManagedJobState.LOST -> palette.dangerRed
                        }

                        Surface(
                            color = palette.surfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("job_item_${job.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(stateColor)
                                    )
                                    Text(
                                        text = job.command,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = stateColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = job.state.label,
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp,
                                            color = stateColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "ID: ${job.id} · PID ${job.pid}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary
                                    )
                                    Text(
                                        text = "Uptime: ${job.uptimeSummary(nowMs)}",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary
                                    )
                                    if (job.port != null) {
                                        Surface(
                                            color = palette.accent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = ":${job.port}",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = palette.accent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { selectedLogJobId = job.id },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("job_logs_button_${job.id}")
                                    ) {
                                        Text("Logs (${job.logLines.size})", fontFamily = PoppinsFontFamily, fontSize = 11.sp, softWrap = false)
                                    }

                                    if (job.port != null && isRunning) {
                                        Button(
                                            onClick = { onOpenPreview(job.port, job.command) },
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("job_preview_button_${job.id}")
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Preview", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, softWrap = false)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { onRestartJob(job.id) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("job_restart_button_${job.id}")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restart", fontFamily = PoppinsFontFamily, fontSize = 11.sp, softWrap = false)
                                    }

                                    if (isRunning) {
                                        OutlinedButton(
                                            onClick = { onStopJob(job.id) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.dangerRed),
                                            border = BorderStroke(1.dp, palette.dangerRed.copy(alpha = 0.45f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("job_stop_button_${job.id}")
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Stop", fontFamily = PoppinsFontFamily, fontSize = 11.sp, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * In-app Loopback WebView Preview sheet with "Open in browser" option.
 * Security hardening:
 * - Allows loopback origins ONLY (`127.0.0.1` and `localhost`).
 * - Disables file access (`allowFileAccess = false`, `allowContentAccess = false`).
 * - Registers zero JavaScript bridges (`addJavascriptInterface` is never called).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoopbackWebViewPreviewSheet(
    port: Int,
    commandTitle: String,
    palette: NexusPalette,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val loopbackUrl = remember(port) { "http://127.0.0.1:$port" }
    var blockedExternalUrlWarning by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceModal,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loopbackUrl,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Loopback sandbox · $commandTitle",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { reloadTrigger++ },
                    modifier = Modifier.testTag("preview_reload_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload preview", tint = palette.textPrimary)
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(loopbackUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("preview_open_in_browser_button")
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open in browser", fontFamily = PoppinsFontFamily, fontSize = 11.sp, softWrap = false)
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview", tint = palette.textPrimary)
                }
            }

            if (blockedExternalUrlWarning != null) {
                Surface(
                    color = palette.WarnAmber.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = blockedExternalUrlWarning!!,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = palette.WarnAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("loopback_preview_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                return if (ServerLogAnalyzer.isLoopbackPreviewUrlAllowed(target)) {
                                    blockedExternalUrlWarning = null
                                    false
                                } else {
                                    blockedExternalUrlWarning = "Blocked non-loopback navigation: ${target.take(64)}"
                                    true
                                }
                            }
                        }
                        loadUrl(loopbackUrl)
                    }
                },
                update = { webView ->
                    if (reloadTrigger > 0 && webView.url != loopbackUrl) {
                        webView.loadUrl(loopbackUrl)
                    }
                }
            )
        }
    }
}

/**
 * Strips ANSI escape sequences (colors, cursor movements, terminal controls) from log lines.
 */
fun stripAnsiCodes(text: String): String =
    text.replace(Regex("""\u001B\[[;?0-9]*[a-zA-Z]"""), "")

/**
 * Shared CodeSurface composable with explicit paired foreground and background tokens
 * guaranteeing WCAG AA (>= 4.5:1) contrast. Never relies on defaults or LocalContentColor.
 */
@Composable
fun CodeSurface(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        contentColor = contentColor,
        shape = shape,
        border = border
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            content = content
        )
    }
}

@Preview(name = "Job Log Viewer - Dark", showBackground = true)
@Composable
fun JobLogViewerPreviewDark() {
    com.example.ui.theme.MyApplicationTheme(darkTheme = true) {
        val palette = com.example.ui.theme.rememberNexusPalette()
        val sampleJob = ManagedJobRecord(
            id = "job-42",
            command = "npm run dev",
            cwd = "/storage/emulated/0/Nexus/react-app",
            startedAtMs = System.currentTimeMillis() - 120_000L,
            pid = 1234,
            port = 3000,
            state = ManagedJobState.RUNNING,
            logLines = listOf(
                "> vite dev",
                "  VITE v5.4.0  ready in 240 ms",
                "  ➜  Local:   http://localhost:3000/",
                "  ➜  Network: use --host to expose"
            )
        )
        NexusJobsBottomSheet(
            jobs = listOf(sampleJob),
            palette = palette,
            onDismiss = {},
            onStopJob = {},
            onStopAllJobs = {},
            onRestartJob = {},
            onRefreshJobs = {},
            onOpenPreview = { _, _ -> }
        )
    }
}

@Preview(name = "Job Log Viewer - Light", showBackground = true)
@Composable
fun JobLogViewerPreviewLight() {
    com.example.ui.theme.MyApplicationTheme(darkTheme = false) {
        val palette = com.example.ui.theme.rememberNexusPalette()
        val sampleJob = ManagedJobRecord(
            id = "job-42",
            command = "npm run dev",
            cwd = "/storage/emulated/0/Nexus/react-app",
            startedAtMs = System.currentTimeMillis() - 120_000L,
            pid = 1234,
            port = 3000,
            state = ManagedJobState.RUNNING,
            logLines = listOf(
                "> vite dev",
                "  VITE v5.4.0  ready in 240 ms",
                "  ➜  Local:   http://localhost:3000/",
                "  ➜  Network: use --host to expose"
            )
        )
        NexusJobsBottomSheet(
            jobs = listOf(sampleJob),
            palette = palette,
            onDismiss = {},
            onStopJob = {},
            onStopAllJobs = {},
            onRestartJob = {},
            onRefreshJobs = {},
            onOpenPreview = { _, _ -> }
        )
    }
}
