package com.example.ui.nexus

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.model.NexusSession
import com.example.data.model.TranscriptEntry
import com.example.domain.DetectedToolInfo
import com.example.domain.EnvironmentDescription
import com.example.domain.ExecutionBackendType
import com.example.domain.WorkspaceMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NexusThemeMode

/**
 * Multi-width & multi-font-scale responsive previews (320dp, 360dp, 411dp, and 900dp tablet,
 * each at font scales 1.0, 1.3, and 2.0 in Light and Dark mode).
 */
@Preview(name = "320dp • 1.0x • Light", widthDp = 320, heightDp = 720, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "320dp • 1.3x • Light", widthDp = 320, heightDp = 720, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "320dp • 2.0x • Light", widthDp = 320, heightDp = 720, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "320dp • 1.0x • Dark", widthDp = 320, heightDp = 720, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "320dp • 1.3x • Dark", widthDp = 320, heightDp = 720, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "320dp • 2.0x • Dark", widthDp = 320, heightDp = 720, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "360dp • 1.0x • Light", widthDp = 360, heightDp = 760, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "360dp • 1.3x • Light", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "360dp • 2.0x • Light", widthDp = 360, heightDp = 760, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "360dp • 1.0x • Dark", widthDp = 360, heightDp = 760, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "360dp • 1.3x • Dark", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "360dp • 2.0x • Dark", widthDp = 360, heightDp = 760, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "411dp • 1.0x • Light", widthDp = 411, heightDp = 840, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "411dp • 1.3x • Light", widthDp = 411, heightDp = 840, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "411dp • 2.0x • Light", widthDp = 411, heightDp = 840, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "411dp • 1.0x • Dark", widthDp = 411, heightDp = 840, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "411dp • 1.3x • Dark", widthDp = 411, heightDp = 840, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "411dp • 2.0x • Dark", widthDp = 411, heightDp = 840, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet 900dp • 1.0x • Light", widthDp = 900, heightDp = 720, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tablet 900dp • 1.3x • Light", widthDp = 900, heightDp = 720, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tablet 900dp • 2.0x • Light", widthDp = 900, heightDp = 720, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tablet 900dp • 1.0x • Dark", widthDp = 900, heightDp = 720, fontScale = 1.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet 900dp • 1.3x • Dark", widthDp = 900, heightDp = 720, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet 900dp • 2.0x • Dark", widthDp = 900, heightDp = 720, fontScale = 2.0f, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class NexusResponsiveMatrixPreviews

@NexusResponsiveMatrixPreviews
@Composable
fun NexusSidebarResponsivePreview() {
    MyApplicationTheme {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxHeight()
        ) {
            NexusSidebar(
                sessions = listOf(
                    NexusSession(
                        id = "s1",
                        title = "New chat",
                        isPinned = false
                    ),
                    NexusSession(
                        id = "s2",
                        title = "NodeJS Express API setup & tests",
                        isPinned = true
                    )
                ),
                activeSessionId = "s1",
                isTemporaryChat = false,
                currentViewMode = NexusViewMode.CHAT,
                currentCwd = "/data/user/0/com.aistudio.nexusagent.v5sync/files/nexus_workspace",
                isGuestMode = true,
                userName = "Guest User",
                userEmail = null,
                avatarBitmap = null,
                isNetworkOnline = true,
                isSyncingOrRunning = false,
                hasPendingApproval = false,
                activeDownloadCount = 0,
                themeMode = NexusThemeMode.LIGHT,
                onNewChatClick = {},
                onTemporaryChatClick = {},
                onSelectSession = {},
                onRenameSession = { _, _ -> },
                onTogglePinSession = {},
                onExportSession = { _, _ -> },
                onDeleteSession = {},
                onClearCurrentSession = {},
                onSelectViewMode = {},
                onOpenWorkingDirectory = {},
                onOpenLocalLlmHub = {},
                onOpenSettings = {},
                onSelectThemeMode = {},
                onSignInOrUpClick = {},
                onSignOutClick = {}
            )
        }
    }
}

@NexusResponsiveMatrixPreviews
@Composable
fun NexusDataPrivacyResponsivePreview() {
    MyApplicationTheme {
        DataAndPrivacySettingsSubpage(
            isGuestMode = true,
            isNetworkOnline = true,
            downloadedLocalModelsCount = 2,
            totalDownloadedBytes = 1_450_000_000L,
            currentWorkingDir = "/data/user/0/com.aistudio.nexusagent.v5sync/files/nexus_workspace",
            hasAllFilesAccess = false,
            recentWorkspaces = listOf("/data/data/com.termux/files/home"),
            savedKeysCount = 3,
            onExportAllChatsJson = {},
            onExportAllChatsMarkdown = {},
            onClearAllChatHistory = {},
            onOpenLocalLlmHub = {},
            onOpenDirectoryChooser = {},
            onRequestAllFilesAccess = {},
            onSelectRecentWorkspace = {},
            onRemoveRecentWorkspace = {},
            onClearAllSavedApiKeys = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@NexusResponsiveMatrixPreviews
@Composable
fun NexusEnvironmentSetupResponsivePreview() {
    MyApplicationTheme {
        EnvironmentSetupScreen(
            p = rememberEnvironmentSetupPalette(),
            selectedBackendType = ExecutionBackendType.TERMUX,
            selectedWorkspaceMode = WorkspaceMode.TERMUX_HOME,
            remoteBridgeUrl = "",
            environmentDescription = EnvironmentDescription(
                backendType = ExecutionBackendType.TERMUX,
                shellName = "Termux bash -lc",
                isAvailable = true,
                summaryLabel = "Termux (git, node, python)",
                cwd = "/data/data/com.termux/files/home",
                workspaceMode = WorkspaceMode.TERMUX_HOME,
                detectedTools = listOf(
                    DetectedToolInfo("Node.js", "node", true, "v22.4.0"),
                    DetectedToolInfo("npm", "npm", true, "10.8.1"),
                    DetectedToolInfo("Git", "git", true, "2.45.2"),
                    DetectedToolInfo("Python", "python", false),
                    DetectedToolInfo("pip", "pip", false)
                ),
                isTermuxInstalled = true,
                isRunCommandPermissionGranted = true,
                isAllowExternalAppsEnabled = true,
                isStorageAccessible = true
            ),
            isProbingEnvironment = false,
            connectionTestResult = "Connected to Termux (`echo ok` -> ok)",
            backgroundJobs = emptyList(),
            onSelectBackendType = {},
            onSelectWorkspaceMode = {},
            onUpdateRemoteBridgeUrl = {},
            onRefreshEnvironment = {},
            onTestConnection = {},
            onProposeInstallToolchainCommand = {},
            onStopBackgroundJob = {},
            onClose = {}
        )
    }
}

@NexusResponsiveMatrixPreviews
@Composable
fun NexusApprovalGateResponsivePreview() {
    MyApplicationTheme {
        PermissionGateCard(
            entry = TranscriptEntry(
                id = "req-1",
                type = "command",
                command = "npm install express && git status",
                cwd = "/data/data/com.termux/files/home/project",
                reason = "Package installation or dependency modification",
                commandStatus = "pending",
                isRisky = false,
                timeoutSec = 600L
            ),
            onApprove = {},
            onDeny = {},
            onAuditWithGemini = {},
            onSwitchToTerminal = {}
        )
    }
}
