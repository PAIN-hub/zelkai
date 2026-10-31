package com.example.ui.nexus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.BackgroundJobInfo
import com.example.domain.DiagnosticCheckResult
import com.example.domain.DiagnosticFixAction
import com.example.domain.DiagnosticStatus
import com.example.domain.EnvironmentDescription
import com.example.domain.ErrorMapper
import com.example.domain.ExecutionBackendType
import com.example.domain.NexusToolAndRedactor
import com.example.domain.ProjectRecipeCard
import com.example.domain.SelfTestReport
import com.example.domain.TermuxBackend
import com.example.domain.TermuxDiagnostics
import com.example.domain.TermuxDiagnosticsReport
import com.example.domain.TermuxWizardPersistedState
import com.example.domain.WorkspaceMode
import com.example.domain.WorkspaceProjectGuide
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import com.example.ui.theme.PoppinsFontFamily

private val NexusMonoFont = JetBrainsMonoFontFamily

object NexusSettingsTokens {
    val CardRadius = 18.dp
}

data class NexusSettingsPalette(
    val bg: Color,
    val topBarBg: Color,
    val cardBg: Color,
    val surfaceMuted: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

@Composable
fun rememberEnvironmentSetupPalette(): NexusSettingsPalette {
    val cs = MaterialTheme.colorScheme
    return NexusSettingsPalette(
        bg = cs.background,
        topBarBg = cs.surface,
        cardBg = cs.surface,
        surfaceMuted = cs.surfaceVariant.copy(alpha = 0.65f),
        cardBorder = cs.outline,
        textPrimary = cs.onSurface,
        textSecondary = cs.onSurfaceVariant,
        textMuted = cs.onSurfaceVariant.copy(alpha = 0.75f),
        accent = NexusAccent,
        accentSoft = NexusAccent.copy(alpha = 0.12f),
        onAccent = Color.White,
        success = NexusOk,
        warning = NexusWarn,
        danger = NexusDanger
    )
}

@Composable
fun EnvironmentSetupScreen(
    activeBackendType: ExecutionBackendType,
    workspaceMode: WorkspaceMode,
    environmentDescription: EnvironmentDescription,
    hasAllFilesAccess: Boolean,
    isProbing: Boolean,
    connectionTestResult: String?,
    backgroundJobs: List<BackgroundJobInfo>,
    onSelectBackendType: (ExecutionBackendType) -> Unit,
    onSelectWorkspaceMode: (WorkspaceMode) -> Unit,
    onRefreshEnvironment: () -> Unit,
    onRunTestConnection: () -> Unit,
    onRequestInstallToolchain: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onStopBackgroundJob: (String) -> Unit,
    onDismiss: () -> Unit,
    remoteBridgeUrl: String = "",
    onUpdateRemoteBridgeUrl: (String) -> Unit = {},
    diagnosticsReport: TermuxDiagnosticsReport? = null,
    selfTestReport: SelfTestReport? = null,
    isRunningSelfTest: Boolean = false,
    projectGuide: WorkspaceProjectGuide? = null,
    wizardState: TermuxWizardPersistedState = TermuxWizardPersistedState(),
    onRunDiagnostics: () -> Unit = onRefreshEnvironment,
    onRunSelfTest: () -> Unit = {},
    onRetryPackagesIndividually: () -> Unit = {},
    onRunRecipeViaAgent: (String) -> Unit = {},
    onVerifyRecipeCommand: (String) -> Unit = {},
    onToggleKeepTermuxAwake: (Boolean) -> Unit = {},
    onMarkWizardComplete: () -> Unit = {},
    onSkipWizard: () -> Unit = {},
    mirrorGitFolder: Boolean = false,
    mirrorBuildOutputs: Boolean = false,
    mirrorDeletions: Boolean = false,
    mirrorSyncStatusLabel: String = "Synced",
    onUpdateMirrorConfig: ((Boolean, Boolean, Boolean) -> Unit)? = null,
    onSyncMirrorNow: (() -> Unit)? = null,
    onImportMirrorFromShared: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        EnvironmentSetupScreen(
            p = rememberEnvironmentSetupPalette(),
            selectedBackendType = activeBackendType,
            selectedWorkspaceMode = workspaceMode,
            remoteBridgeUrl = remoteBridgeUrl,
            environmentDescription = environmentDescription,
            isProbingEnvironment = isProbing,
            connectionTestResult = connectionTestResult,
            backgroundJobs = backgroundJobs,
            onSelectBackendType = onSelectBackendType,
            onSelectWorkspaceMode = onSelectWorkspaceMode,
            onUpdateRemoteBridgeUrl = onUpdateRemoteBridgeUrl,
            onRefreshEnvironment = onRefreshEnvironment,
            onTestConnection = onRunTestConnection,
            onProposeInstallToolchainCommand = { onRequestInstallToolchain() },
            onStopBackgroundJob = onStopBackgroundJob,
            onClose = onDismiss,
            diagnosticsReport = diagnosticsReport,
            selfTestReport = selfTestReport,
            isRunningSelfTest = isRunningSelfTest,
            projectGuide = projectGuide,
            wizardState = wizardState,
            onRunDiagnostics = onRunDiagnostics,
            onRunSelfTest = onRunSelfTest,
            onRetryPackagesIndividually = onRetryPackagesIndividually,
            onRunRecipeViaAgent = onRunRecipeViaAgent,
            onVerifyRecipeCommand = onVerifyRecipeCommand,
            onToggleKeepTermuxAwake = onToggleKeepTermuxAwake,
            onMarkWizardComplete = onMarkWizardComplete,
            onSkipWizard = onSkipWizard,
            mirrorGitFolder = mirrorGitFolder,
            mirrorBuildOutputs = mirrorBuildOutputs,
            mirrorDeletions = mirrorDeletions,
            mirrorSyncStatusLabel = mirrorSyncStatusLabel,
            onUpdateMirrorConfig = onUpdateMirrorConfig,
            onSyncMirrorNow = onSyncMirrorNow,
            onImportMirrorFromShared = onImportMirrorFromShared
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentSetupScreen(
    p: NexusSettingsPalette,
    selectedBackendType: ExecutionBackendType,
    selectedWorkspaceMode: WorkspaceMode,
    remoteBridgeUrl: String,
    environmentDescription: EnvironmentDescription,
    isProbingEnvironment: Boolean,
    connectionTestResult: String?,
    backgroundJobs: List<BackgroundJobInfo>,
    onSelectBackendType: (ExecutionBackendType) -> Unit,
    onSelectWorkspaceMode: (WorkspaceMode) -> Unit,
    onUpdateRemoteBridgeUrl: (String) -> Unit,
    onRefreshEnvironment: () -> Unit,
    onTestConnection: () -> Unit,
    onProposeInstallToolchainCommand: (String) -> Unit,
    onStopBackgroundJob: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    diagnosticsReport: TermuxDiagnosticsReport? = null,
    selfTestReport: SelfTestReport? = null,
    isRunningSelfTest: Boolean = false,
    projectGuide: WorkspaceProjectGuide? = null,
    wizardState: TermuxWizardPersistedState = TermuxWizardPersistedState(),
    onRunDiagnostics: () -> Unit = onRefreshEnvironment,
    onRunSelfTest: () -> Unit = {},
    onRetryPackagesIndividually: () -> Unit = {},
    onRunRecipeViaAgent: (String) -> Unit = {},
    onVerifyRecipeCommand: (String) -> Unit = {},
    onToggleKeepTermuxAwake: (Boolean) -> Unit = {},
    onMarkWizardComplete: () -> Unit = {},
    onSkipWizard: () -> Unit = {},
    mirrorGitFolder: Boolean = false,
    mirrorBuildOutputs: Boolean = false,
    mirrorDeletions: Boolean = false,
    mirrorSyncStatusLabel: String = "Synced",
    onUpdateMirrorConfig: ((Boolean, Boolean, Boolean) -> Unit)? = null,
    onSyncMirrorNow: (() -> Unit)? = null,
    onImportMirrorFromShared: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedBannerText by remember { mutableStateOf<String?>(null) }
    var bridgeUrlDraft by remember(remoteBridgeUrl) { mutableStateOf(remoteBridgeUrl) }

    val runCommandPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRunDiagnostics()
    }

    fun executeFixAction(fix: DiagnosticFixAction) {
        when (fix) {
            DiagnosticFixAction.OPEN_FDROID -> runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TermuxDiagnostics.TERMUX_FDROID_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            DiagnosticFixAction.OPEN_GITHUB_RELEASES -> runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TermuxDiagnostics.TERMUX_GITHUB_RELEASES_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            DiagnosticFixAction.REQUEST_RUN_COMMAND_PERMISSION -> {
                try {
                    runCommandPermissionLauncher.launch(TermuxBackend.PERMISSION_RUN_COMMAND)
                } catch (_: Throwable) {
                    runCatching {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
            }
            DiagnosticFixAction.OPEN_APP_SETTINGS,
            DiagnosticFixAction.OPEN_APP_PERMISSION_SETTINGS -> runCatching {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            DiagnosticFixAction.COPY_ALLOW_EXTERNAL_APPS_CMD -> {
                clipboard.setText(AnnotatedString(TermuxDiagnostics.IDEMPOTENT_ALLOW_EXTERNAL_APPS_CMD))
                copiedBannerText = "Copied allow-external-apps command! Paste inside Termux and run termux-reload-settings."
            }
            DiagnosticFixAction.OPEN_TERMUX_APP,
            DiagnosticFixAction.OPEN_TERMUX -> runCatching {
                val launch = context.packageManager.getLaunchIntentForPackage(TermuxBackend.TERMUX_PACKAGE_NAME)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            }
            DiagnosticFixAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS -> runCatching {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            DiagnosticFixAction.COPY_STORAGE_SETUP_CMD,
            DiagnosticFixAction.COPY_TERMUX_SETUP_STORAGE_CMD -> {
                clipboard.setText(AnnotatedString("termux-setup-storage"))
                copiedBannerText = "Copied `termux-setup-storage` — paste and run inside Termux to grant storage access."
            }
            DiagnosticFixAction.PROPOSE_INSTALL_TOOLCHAIN -> {
                onProposeInstallToolchainCommand(TermuxDiagnostics.INSTALL_TOOLCHAIN_BATCH_CMD)
            }
            DiagnosticFixAction.RETRY_PACKAGES_INDIVIDUALLY -> {
                onRetryPackagesIndividually()
            }
            DiagnosticFixAction.RUN_SELF_TEST -> {
                onRunSelfTest()
            }
            DiagnosticFixAction.REQUEST_ALL_FILES_ACCESS -> runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            DiagnosticFixAction.NONE -> Unit
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("environment_setup_screen"),
        containerColor = p.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = p.topBarBg,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("env_setup_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = p.textPrimary
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Execution Environment & Setup Wizard",
                                color = p.textPrimary,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = environmentDescription.summaryLabel,
                                color = p.textSecondary,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onRunDiagnostics,
                            enabled = !isProbingEnvironment,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("env_setup_refresh_btn")
                        ) {
                            if (isProbingEnvironment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = p.accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Run diagnostics",
                                    tint = p.accent
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = p.cardBorder, thickness = 1.dp)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!copiedBannerText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.success.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, p.success),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = copiedBannerText!!,
                            color = p.success,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 0. Guided Termux Setup Wizard Card (resumable, skippable, persisted in DataStore)
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, if (wizardState.isCompleted) p.success.copy(alpha = 0.5f) else p.accent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_setup_wizard_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (wizardState.isCompleted) Icons.Default.Verified else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (wizardState.isCompleted) p.success else p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (wizardState.isCompleted) {
                                        "Termux Setup Complete"
                                    } else {
                                        "Guided Termux Setup Wizard"
                                    },
                                    color = p.textPrimary,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Install Termux & any Termux add-ons from the same source (F-Droid or GitHub Releases). Mixing sources causes signature conflicts, and the old Play Store build is outdated.",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { executeFixAction(DiagnosticFixAction.OPEN_FDROID) },
                                modifier = Modifier.testTag("wizard_open_fdroid_btn")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("F-Droid Termux", fontSize = 12.sp, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { executeFixAction(DiagnosticFixAction.OPEN_GITHUB_RELEASES) },
                                modifier = Modifier.testTag("wizard_open_github_btn")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("GitHub Releases", fontSize = 12.sp, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    val summary = ErrorMapper.buildRedactedDiagnosticsSummary(
                                        backendType = selectedBackendType,
                                        workspaceMode = selectedWorkspaceMode,
                                        isTermuxInstalled = environmentDescription.isTermuxInstalled,
                                        isPermissionGranted = environmentDescription.isRunCommandPermissionGranted,
                                        isAllowExternalAppsOk = environmentDescription.isAllowExternalAppsEnabled,
                                        detectedTools = environmentDescription.detectedTools,
                                        lastError = null
                                    )
                                    clipboard.setText(AnnotatedString(summary))
                                    copiedBannerText = "Copied redacted diagnostics summary (no API keys, prompts, or file contents)."
                                },
                                modifier = Modifier.testTag("copy_redacted_diagnostics_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy diagnostics", fontSize = 12.sp, softWrap = false)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!wizardState.isCompleted) {
                                Button(
                                    onClick = onMarkWizardComplete,
                                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                                    modifier = Modifier.testTag("wizard_mark_done_btn")
                                ) {
                                    Text("Mark setup complete", fontSize = 12.sp, softWrap = false)
                                }
                                if (!wizardState.isSkipped) {
                                    TextButton(
                                        onClick = onSkipWizard,
                                        modifier = Modifier.testTag("wizard_skip_btn")
                                    ) {
                                        Text("Skip for now", fontSize = 12.sp, color = p.textSecondary, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Sequential Diagnostics Engine Card
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics_engine_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Diagnostics Engine",
                                    color = p.textPrimary,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Sequential non-blocking checks · Cached for 60s",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp
                                )
                            }
                            OutlinedButton(
                                onClick = onRunDiagnostics,
                                enabled = !isProbingEnvironment,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("run_diagnostics_btn")
                            ) {
                                Text("Re-run checks", fontSize = 11.sp, softWrap = false)
                            }
                        }

                        if (diagnosticsReport != null && diagnosticsReport.checks.isNotEmpty()) {
                            diagnosticsReport.checks.forEach { check ->
                                DiagnosticCheckRow(
                                    p = p,
                                    check = check,
                                    onFixAction = { executeFixAction(it) }
                                )
                            }
                        } else {
                            ChecklistRow(
                                p = p,
                                passed = environmentDescription.isTermuxInstalled,
                                title = "1. Termux installed (com.termux)",
                                description = "Install from F-Droid or GitHub Releases. Do not mix sources."
                            )
                            ChecklistRow(
                                p = p,
                                passed = environmentDescription.isRunCommandPermissionGranted,
                                title = "2. RUN_COMMAND permission",
                                description = "Grant com.termux.permission.RUN_COMMAND at runtime.",
                                actionLabel = if (!environmentDescription.isRunCommandPermissionGranted) "Grant" else null,
                                onAction = { executeFixAction(DiagnosticFixAction.REQUEST_RUN_COMMAND_PERMISSION) }
                            )
                            ChecklistRow(
                                p = p,
                                passed = environmentDescription.isAllowExternalAppsEnabled,
                                title = "3. allow-external-apps = true",
                                description = "Set in ~/.termux/termux.properties and run termux-reload-settings.",
                                actionLabel = if (!environmentDescription.isAllowExternalAppsEnabled) "Copy cmd" else null,
                                onAction = { executeFixAction(DiagnosticFixAction.COPY_ALLOW_EXTERNAL_APPS_CMD) }
                            )
                            ChecklistRow(
                                p = p,
                                passed = environmentDescription.isStorageAccessible,
                                title = "4. Workspace storage writable",
                                description = "Active path: ${NexusToolAndRedactor.shortenPathMiddle(environmentDescription.cwd, 38)}"
                            )
                        }

                        HorizontalDivider(color = p.cardBorder)

                        // Idempotent allow-external-apps command box
                        Text(
                            text = "Idempotent allow-external-apps Setup Command",
                            color = p.textPrimary,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Paste inside Termux, then run `termux-reload-settings` or fully close and reopen Termux:",
                            color = p.textSecondary,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = p.surfaceMuted,
                            border = BorderStroke(1.dp, p.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = TermuxDiagnostics.IDEMPOTENT_ALLOW_EXTERNAL_APPS_CMD,
                                color = p.textPrimary,
                                fontFamily = NexusMonoFont,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { executeFixAction(DiagnosticFixAction.COPY_ALLOW_EXTERNAL_APPS_CMD) },
                                modifier = Modifier.testTag("copy_termux_setup_cmd_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy command", softWrap = false, maxLines = 1)
                            }

                            Button(
                                onClick = onTestConnection,
                                enabled = !isProbingEnvironment,
                                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                                modifier = Modifier.testTag("test_termux_connection_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test connection (echo nexus_ok)", softWrap = false, maxLines = 1)
                            }
                        }

                        if (!connectionTestResult.isNullOrBlank()) {
                            val ok = connectionTestResult.startsWith("Connected")
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (ok) p.success.copy(alpha = 0.12f) else p.warning.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (ok) p.success else p.warning),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connection_test_result_banner")
                            ) {
                                Text(
                                    text = connectionTestResult,
                                    color = if (ok) p.success else p.warning,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // 2. End-to-End Self-Test Card with progress list and timings
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_test_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "End-to-End Self-Test",
                                    color = p.textPrimary,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Verifies workspace file write, node -e, git init in temp folder, and cleanup with timings",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = onRunSelfTest,
                                enabled = !isRunningSelfTest,
                                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("run_self_test_btn")
                            ) {
                                if (isRunningSelfTest) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = p.onAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Run self-test", fontSize = 12.sp, softWrap = false)
                            }
                        }

                        if (selfTestReport != null) {
                            selfTestReport.steps.forEach { step ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = p.surfaceMuted,
                                    border = BorderStroke(1.dp, p.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (step.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (step.passed) p.success else p.danger,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = step.title,
                                                color = p.textPrimary,
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                            if (step.detail.isNotBlank()) {
                                                Text(
                                                    text = step.detail,
                                                    color = p.textSecondary,
                                                    fontFamily = NexusMonoFont,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${step.durationMs} ms",
                                            color = p.textSecondary,
                                            fontFamily = NexusMonoFont,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (selfTestReport.allPassed) {
                                    "All 4 self-test steps passed in ${selfTestReport.totalDurationMs} ms."
                                } else {
                                    "Self-test completed with failures (${selfTestReport.totalDurationMs} ms)."
                                },
                                color = if (selfTestReport.allPassed) p.success else p.warning,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 3. Project Setup Guide & Recipe Cards
                if (projectGuide != null) {
                    Surface(
                        shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                        color = p.cardBg,
                        border = BorderStroke(1.dp, p.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_setup_guide_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = p.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Project Setup Guide",
                                        color = p.textPrimary,
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    val detectedLabels = projectGuide.detectedTypes.joinToString(" · ") {
                                        "${it.title} (${it.markerFile})"
                                    }
                                    Text(
                                        text = if (detectedLabels.isNotBlank()) {
                                            "Detected in workspace: $detectedLabels"
                                        } else {
                                            "Quick recipes for Node, Python, static sites, and Git in your workspace"
                                        },
                                        color = p.textSecondary,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            projectGuide.recipes.forEach { recipe ->
                                RecipeStepCard(
                                    p = p,
                                    recipe = recipe,
                                    onCopy = { cmd ->
                                        clipboard.setText(AnnotatedString(cmd))
                                        copiedBannerText = "Copied `${cmd.take(36)}` to clipboard."
                                    },
                                    onRunViaAgent = onRunRecipeViaAgent,
                                    onVerify = onVerifyRecipeCommand
                                )
                            }
                        }
                    }
                }

                // 4. Detected CLI Toolchain Card
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val missingTools = environmentDescription.detectedTools.filter { !it.isAvailable }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Detected CLI Toolchain",
                                    color = p.textPrimary,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Probed via `command -v` and `--version` inside active backend",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp
                                )
                            }
                            if (missingTools.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        onProposeInstallToolchainCommand(TermuxBackend.INSTALL_TOOLCHAIN_COMMAND)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = p.accent,
                                        contentColor = p.onAccent
                                    ),
                                    modifier = Modifier.testTag("install_missing_tools_btn")
                                ) {
                                    Text(
                                        text = "Install",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        environmentDescription.detectedTools.forEach { tool ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = p.surfaceMuted,
                                border = BorderStroke(1.dp, p.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (tool.isAvailable) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (tool.isAvailable) p.success else p.warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tool.name,
                                            color = p.textPrimary,
                                            fontFamily = NexusMonoFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (tool.isAvailable) {
                                                "${tool.version ?: "Installed"}${tool.path?.let { " · $it" }.orEmpty()}"
                                            } else {
                                                "Missing in current environment"
                                            },
                                            color = p.textSecondary,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!tool.isAvailable) {
                                        OutlinedButton(
                                            onClick = {
                                                onProposeInstallToolchainCommand(TermuxBackend.INSTALL_TOOLCHAIN_COMMAND)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("install_tool_${tool.name}_btn")
                                        ) {
                                            Text(
                                                text = "Install",
                                                fontSize = 11.sp,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (missingTools.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tapping \"Install\" proposes `${TermuxBackend.INSTALL_TOOLCHAIN_COMMAND}` through the approval gate.",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onRetryPackagesIndividually,
                                    modifier = Modifier.testTag("retry_packages_individually_btn")
                                ) {
                                    Text("Retry 1-by-1", fontSize = 11.sp, color = p.accent, softWrap = false)
                                }
                            }
                        }
                    }
                }

                // 5. Background Job Wake-Lock & Battery Optimization Settings
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Termux awake while jobs run",
                                    color = p.textPrimary,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Acquires `termux-wake-lock` when starting detached dev servers so Android does not throttle localhost ports.",
                                    color = p.textSecondary,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = wizardState.keepTermuxAwakeDuringJobs,
                                onCheckedChange = onToggleKeepTermuxAwake,
                                modifier = Modifier.testTag("keep_termux_awake_switch")
                            )
                        }
                        OutlinedButton(
                            onClick = { executeFixAction(DiagnosticFixAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS) },
                            modifier = Modifier.testTag("open_battery_exemption_btn")
                        ) {
                            Text("Configure Battery Optimization Exemption", fontSize = 12.sp, softWrap = false)
                        }
                    }
                }

                // 6. Active Backend Selector Card
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Execution Backend",
                                color = p.textPrimary,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Never switches backends silently. Prompts before using the read-only limited shell if Termux is unavailable.",
                            color = p.textSecondary,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp
                        )

                        ExecutionBackendType.entries.forEach { backend ->
                            val selected = selectedBackendType == backend
                            val subtitle = when (backend) {
                                ExecutionBackendType.TERMUX ->
                                    "Full Linux bash, pkg, git, node, npm, python & pip via com.termux.RUN_COMMAND"
                                ExecutionBackendType.ANDROID_SHELL ->
                                    "Built-in /system/bin/sh (read-only fallback; no git, node, or python)"
                                ExecutionBackendType.REMOTE_BRIDGE ->
                                    "Explicit opt-in HTTP bridge to server.mjs on workstation or LAN host"
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) p.accentSoft else p.surfaceMuted,
                                border = BorderStroke(1.dp, if (selected) p.accent else p.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectBackendType(backend) }
                                    .testTag("backend_option_${backend.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onSelectBackendType(backend) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = backend.label,
                                            color = p.textPrimary,
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = subtitle,
                                            color = p.textSecondary,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedBackendType == ExecutionBackendType.REMOTE_BRIDGE) {
                            OutlinedTextField(
                                value = bridgeUrlDraft,
                                onValueChange = {
                                    bridgeUrlDraft = it
                                    onUpdateRemoteBridgeUrl(it)
                                },
                                label = { Text("Remote Bridge URL (server.mjs)") },
                                placeholder = { Text("http://127.0.0.1:8787") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_bridge_url_input")
                            )
                        }
                    }
                }

                // 7. Workspace Mode Card (Termux Home vs Shared Storage)
                Surface(
                    shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                    color = p.cardBg,
                    border = BorderStroke(1.dp, p.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Workspace Filesystem Mode",
                                color = p.textPrimary,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        WorkspaceMode.entries.forEach { mode ->
                            val selected = selectedWorkspaceMode == mode
                            val desc = when (mode) {
                                WorkspaceMode.TERMUX_HOME ->
                                    "Native ext4 filesystem (/data/data/com.termux/files/home). Full POSIX permissions, chmod +x, and npm symlinks work without restrictions."
                                WorkspaceMode.SHARED_STORAGE ->
                                    "Shared storage folder accessible to Nexus and other Android apps. Applies npm_config_bin_links=false and git core.fileMode=false automatically."
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) p.accentSoft else p.surfaceMuted,
                                border = BorderStroke(1.dp, if (selected) p.accent else p.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectWorkspaceMode(mode) }
                                    .testTag("workspace_mode_${mode.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onSelectWorkspaceMode(mode) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label,
                                            color = p.textPrimary,
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = desc,
                                            color = p.textSecondary,
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedWorkspaceMode == WorkspaceMode.TERMUX_HOME) {
                            HorizontalDivider(color = p.cardBorder, thickness = 1.dp)
                            Text(
                                text = "One-Way Source Mirroring (Termux Home → /storage/emulated/0/Nexus/<project>)",
                                color = p.textPrimary,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Mirrors source files so other Android apps can browse your project. Excludes node_modules, .venv, .cache, .nexus-trash, and secret files (.env, *.pem, id_rsa). Status: $mirrorSyncStatusLabel",
                                color = p.textSecondary,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.sp
                            )
                            if (onUpdateMirrorConfig != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Include .git folder in mirror (off by default)",
                                        color = p.textPrimary,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = mirrorGitFolder,
                                        onCheckedChange = { onUpdateMirrorConfig(it, mirrorBuildOutputs, mirrorDeletions) },
                                        modifier = Modifier.testTag("mirror_git_toggle")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Include dist/ and build/ outputs in mirror",
                                        color = p.textPrimary,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = mirrorBuildOutputs,
                                        onCheckedChange = { onUpdateMirrorConfig(mirrorGitFolder, it, mirrorDeletions) },
                                        modifier = Modifier.testTag("mirror_build_outputs_toggle")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Mirror deletions directly (when off, moves deleted files to .nexus-trash)",
                                        color = p.textPrimary,
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = mirrorDeletions,
                                        onCheckedChange = { onUpdateMirrorConfig(mirrorGitFolder, mirrorBuildOutputs, it) },
                                        modifier = Modifier.testTag("mirror_deletions_toggle")
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onSyncMirrorNow != null) {
                                    OutlinedButton(
                                        onClick = onSyncMirrorNow,
                                        modifier = Modifier.testTag("mirror_sync_now_btn")
                                    ) {
                                        Text("Sync now", fontSize = 12.sp, softWrap = false)
                                    }
                                }
                                if (onImportMirrorFromShared != null) {
                                    OutlinedButton(
                                        onClick = onImportMirrorFromShared,
                                        modifier = Modifier.testTag("mirror_import_from_shared_btn")
                                    ) {
                                        Text("Import from shared", fontSize = 12.sp, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Long-Running Background Jobs Summary
                if (backgroundJobs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(NexusSettingsTokens.CardRadius),
                        color = p.cardBg,
                        border = BorderStroke(1.dp, p.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Background Jobs (${backgroundJobs.count { it.isRunning }} active)",
                                color = p.textPrimary,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            backgroundJobs.forEach { job ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = p.surfaceMuted,
                                    border = BorderStroke(1.dp, p.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (job.isRunning) p.success else p.textMuted)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "${job.jobId} · ${job.command}",
                                                color = p.textPrimary,
                                                fontFamily = NexusMonoFont,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (job.isRunning) {
                                                OutlinedButton(
                                                    onClick = { onStopBackgroundJob(job.jobId) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.danger),
                                                    modifier = Modifier.testTag("stop_bg_job_${job.jobId}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Stop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Stop", fontSize = 11.sp, softWrap = false, maxLines = 1)
                                                }
                                            }
                                        }
                                        if (job.logTail.isNotEmpty()) {
                                            Text(
                                                text = job.logTail.takeLast(4).joinToString("\n"),
                                                color = p.textSecondary,
                                                fontFamily = NexusMonoFont,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(
    p: NexusSettingsPalette,
    check: DiagnosticCheckResult,
    onFixAction: (DiagnosticFixAction) -> Unit
) {
    val statusColor = when (check.status) {
        DiagnosticStatus.OK -> p.success
        DiagnosticStatus.MISSING -> p.warning
        DiagnosticStatus.ERROR -> p.danger
        DiagnosticStatus.UNKNOWN -> p.textMuted
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = p.surfaceMuted,
        border = BorderStroke(1.dp, p.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diag_check_${check.id.lowercase()}")
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
                    imageVector = if (check.status == DiagnosticStatus.OK) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = check.status.name,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = check.title,
                    color = p.textPrimary,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${check.status.name} · ${check.durationMs}ms",
                        color = statusColor,
                        fontFamily = NexusMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = check.message,
                color = p.textPrimary,
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp
            )
            if (check.probableCause.isNotBlank() && check.status != DiagnosticStatus.OK) {
                Text(
                    text = "Cause: ${check.probableCause}",
                    color = p.textSecondary,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp
                )
            }
            if (check.fixAction != DiagnosticFixAction.NONE && check.fixLabel.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onFixAction(check.fixAction) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(check.fixLabel, fontSize = 11.sp, softWrap = false, maxLines = 1)
                    }
                    if (check.secondaryFixAction != DiagnosticFixAction.NONE && check.secondaryFixLabel.isNotBlank()) {
                        TextButton(
                            onClick = { onFixAction(check.secondaryFixAction) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(check.secondaryFixLabel, fontSize = 11.sp, color = p.accent, softWrap = false, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeStepCard(
    p: NexusSettingsPalette,
    recipe: ProjectRecipeCard,
    onCopy: (String) -> Unit,
    onRunViaAgent: (String) -> Unit,
    onVerify: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = p.surfaceMuted,
        border = BorderStroke(1.dp, p.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_card_${recipe.id}")
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
                Text(
                    text = recipe.title,
                    color = p.textPrimary,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (recipe.runsAsBackgroundJob) {
                    Surface(
                        color = p.accentSoft,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Runs in background",
                            color = p.accent,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = recipe.description,
                color = p.textSecondary,
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = p.cardBg,
                border = BorderStroke(1.dp, p.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$ ${recipe.command}",
                    color = p.textPrimary,
                    fontFamily = NexusMonoFont,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopy(recipe.command) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp, softWrap = false)
                }
                Button(
                    onClick = { onRunViaAgent(recipe.command) },
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run via agent", fontSize = 11.sp, softWrap = false)
                }
                OutlinedButton(
                    onClick = { onVerify(recipe.verifyCommand) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Verify", fontSize = 11.sp, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    p: NexusSettingsPalette,
    passed: Boolean,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (passed) "Ready" else "Action needed",
            tint = if (passed) p.success else p.warning,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = p.textPrimary,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = p.textSecondary,
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
