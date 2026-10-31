package com.example.ui.nexus

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.NexusUserSettings
import com.example.domain.ActiveLoadedModelState
import com.example.domain.ApiKeyTestResult
import com.example.domain.DeviceProfile
import com.example.domain.EngineInfo
import com.example.domain.InferencePreset
import com.example.domain.ModelOption
import com.example.domain.NexusFontSize
import com.example.domain.NexusPersonaPreset
import com.example.domain.NexusPersonalizationConfig
import com.example.domain.NexusResponseStyle
import com.example.domain.NexusToolAndRedactor
import com.example.ui.theme.NexusThemeMode
import java.util.Locale

enum class SettingsSection(
    val id: String,
    val title: String,
    val icon: ImageVector
) {
    PROFILE("profile", "Profile & Account", Icons.Default.Person),
    GENERAL("general", "General", Icons.Default.Tune),
    MODEL_CONFIG("model_config", "Model Configuration", Icons.Default.Hub),
    DATA_PRIVACY("data_privacy", "Data & Privacy", Icons.Default.Shield),
    ABOUT("about", "About Nexus Agent", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusSettingsDialog(
    settings: NexusUserSettings,
    personalizationConfig: NexusPersonalizationConfig,
    avatarBitmap: Bitmap?,
    remoteBridgeUrl: String,
    currentWorkingDir: String,
    hasAllFilesAccess: Boolean,
    isGuestMode: Boolean,
    isNetworkOnline: Boolean,
    userEmail: String?,
    userDisplayName: String?,
    remainingGuestPrompts: Int,
    themeMode: NexusThemeMode,
    downloadedLocalModelsCount: Int,
    totalDownloadedBytes: Long,
    savedKeysMap: Map<String, String>,
    activePreset: InferencePreset,
    activeLoadedModel: ActiveLoadedModelState?,
    isBenchmarking: Boolean,
    availableModelOptions: List<ModelOption>,
    isFetchingModels: Map<String, Boolean>,
    keyTestStatuses: Map<String, ApiKeyTestResult>,
    huggingFaceToken: String,
    showAllOpenAiModels: Boolean,
    deviceProfile: DeviceProfile,
    engineInfo: EngineInfo,
    onSelectThemeMode: (NexusThemeMode) -> Unit,
    onSelectFontSize: (NexusFontSize) -> Unit,
    onSelectResponseStyle: (NexusResponseStyle) -> Unit,
    onSelectPersonaPreset: (String) -> Unit,
    onSaveCustomPersona: (name: String, prompt: String, existingId: String?) -> Unit,
    onDeleteCustomPersona: (String) -> Unit,
    onUpdatePersonalization: (aboutUser: String, responsePref: String, enableForNewChats: Boolean) -> Unit,
    onToggleSkill: (skillId: String, enabled: Boolean) -> Unit,
    onSaveCustomSkill: (id: String?, name: String, description: String, instructions: String, enabled: Boolean) -> Unit,
    onDeleteCustomSkill: (String) -> Unit,
    onUpdateSkillsTokenBudget: (Int) -> Unit,
    onImportSkillsContent: (String) -> Result<Unit>,
    onExportSkillsJson: () -> String,
    onOpenDirectoryChooser: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSelectRecentWorkspace: (String) -> Unit,
    onRemoveRecentWorkspace: (String) -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSaveCroppedAvatar: (Bitmap, Float, Float, Float) -> Unit,
    onRemoveAvatar: () -> Unit,
    onSelectProviderAndModel: (provider: String, modelId: String) -> Unit,
    onSaveProviderApiKey: (provider: String, apiKey: String) -> Unit,
    onUpdateInferenceDefaults: (maxTokens: Long, maxSteps: Long, timeoutSec: Long, remoteBridge: String) -> Unit,
    onSelectPreset: (InferencePreset) -> Unit,
    onReRunBenchmark: () -> Unit,
    onUnloadModel: () -> Unit,
    onTestApiKey: (provider: String, keyOverride: String?) -> Unit,
    onRefreshModels: (provider: String) -> Unit,
    onToggleShowAllOpenAi: (Boolean) -> Unit,
    onSaveHuggingFaceToken: (String) -> Unit,
    onOpenLocalLlmHub: () -> Unit,
    onExportAllChatsJson: () -> Unit,
    onExportAllChatsMarkdown: () -> Unit,
    onClearAllChatHistory: () -> Unit,
    onClearAllSavedApiKeys: () -> Unit,
    onDeleteAccountAndCloudData: () -> Unit,
    onResetSettings: () -> Unit,
    onExportFullSettingsJson: () -> String,
    onImportFullSettingsJson: (String) -> Result<String?>,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    executionEnvironmentSummary: String = "Termux / Android Shell",
    onOpenExecutionEnvironment: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var activeSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showPhotoActionSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }
    var statusToastMessage by remember { mutableStateOf<String?>(null) }

    val effectiveDisplayName = personalizationConfig.displayName.ifBlank {
        userDisplayName?.takeIf { it.isNotBlank() }
            ?: userEmail?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: if (isGuestMode) "Guest User" else "Nexus Operator"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ProfilePhotoPickerAndCropperHost(
            showActionSheet = showPhotoActionSheet,
            hasExistingAvatar = avatarBitmap != null,
            onDismissActionSheet = { showPhotoActionSheet = false },
            onSaveCroppedBitmap = { cropped, zoom, panX, panY ->
                onSaveCroppedAvatar(cropped, zoom, panX, panY)
                statusToastMessage = "Profile photo updated"
            },
            onRemovePhoto = {
                onRemoveAvatar()
                statusToastMessage = "Profile photo removed"
            }
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("settings_dialog")
        ) {
            val isTwoPane = maxWidth >= 840.dp
            val currentDetailSection = activeSection ?: SettingsSection.GENERAL

            BackHandler {
                if (!isTwoPane && activeSection != null) {
                    activeSection = null
                } else {
                    onDismiss()
                }
            }

            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (!isTwoPane && activeSection != null) {
                                                activeSection = null
                                            } else {
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier.testTag("close_settings_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (!isTwoPane && activeSection != null) {
                                                activeSection!!.title
                                            } else {
                                                "Settings"
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!isTwoPane && activeSection != null) {
                                            Text(
                                                text = "Changes save automatically",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showOverflowMenu = true },
                                        modifier = Modifier.testTag("settings_overflow_menu_button")
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "More settings options"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Export / Import settings (JSON)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.ImportExport, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                importJsonInput = ""
                                                showExportImportDialog = true
                                            },
                                            modifier = Modifier.testTag("overflow_export_import_button")
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Reset settings",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.RestartAlt,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                showResetConfirmDialog = true
                                            },
                                            modifier = Modifier.testTag("overflow_reset_settings_button")
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!isTwoPane && activeSection == null) {
                        SettingsAccountBottomBar(
                            isGuestMode = isGuestMode,
                            onRequestSignIn = onSignOut,
                            onRequestSignOut = { showSignOutConfirmDialog = true }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    statusToastMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { statusToastMessage = null }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    if (isTwoPane) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Master Pane
                            Surface(
                                modifier = Modifier
                                    .width(340.dp)
                                    .fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                SettingsHomeContent(
                                    effectiveDisplayName = effectiveDisplayName,
                                    userEmail = userEmail,
                                    isGuestMode = isGuestMode,
                                    remainingGuestPrompts = remainingGuestPrompts,
                                    avatarBitmap = avatarBitmap,
                                    personalizationConfig = personalizationConfig,
                                    settings = settings,
                                    themeMode = themeMode,
                                    selectedSection = currentDetailSection,
                                    showInlineBottomAccountButton = true,
                                    onSelectSection = { activeSection = it },
                                    onChangePicture = { showPhotoActionSheet = true },
                                    onRequestSignIn = onSignOut,
                                    onRequestSignOut = { showSignOutConfirmDialog = true }
                                )
                            }

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Right Detail Pane
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 760.dp)
                                        .align(Alignment.TopCenter)
                                ) {
                                    SettingsSectionDetailRouter(
                                        section = currentDetailSection,
                                        settings = settings,
                                        personalizationConfig = personalizationConfig,
                                        avatarBitmap = avatarBitmap,
                                        effectiveDisplayName = effectiveDisplayName,
                                        remoteBridgeUrl = remoteBridgeUrl,
                                        currentWorkingDir = currentWorkingDir,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        isGuestMode = isGuestMode,
                                        isNetworkOnline = isNetworkOnline,
                                        userEmail = userEmail,
                                        themeMode = themeMode,
                                        downloadedLocalModelsCount = downloadedLocalModelsCount,
                                        totalDownloadedBytes = totalDownloadedBytes,
                                        savedKeysMap = savedKeysMap,
                                        activePreset = activePreset,
                                        activeLoadedModel = activeLoadedModel,
                                        availableModelOptions = availableModelOptions,
                                        isFetchingModels = isFetchingModels,
                                        keyTestStatuses = keyTestStatuses,
                                        huggingFaceToken = huggingFaceToken,
                                        showAllOpenAiModels = showAllOpenAiModels,
                                        deviceProfile = deviceProfile,
                                        engineInfo = engineInfo,
                                        onChangePhotoClick = { showPhotoActionSheet = true },
                                        onSelectThemeMode = onSelectThemeMode,
                                        onSelectFontSize = onSelectFontSize,
                                        onSelectResponseStyle = onSelectResponseStyle,
                                        onSelectPersonaPreset = onSelectPersonaPreset,
                                        onSaveCustomPersona = onSaveCustomPersona,
                                        onDeleteCustomPersona = onDeleteCustomPersona,
                                        onUpdatePersonalization = onUpdatePersonalization,
                                        onToggleSkill = onToggleSkill,
                                        onSaveCustomSkill = onSaveCustomSkill,
                                        onDeleteCustomSkill = onDeleteCustomSkill,
                                        onUpdateSkillsTokenBudget = onUpdateSkillsTokenBudget,
                                        onImportSkillsContent = onImportSkillsContent,
                                        onExportSkillsJson = {
                                            val json = onExportSkillsJson()
                                            clipboardManager.setText(AnnotatedString(json))
                                            statusToastMessage = "Skills JSON copied to clipboard"
                                        },
                                        onOpenDirectoryChooser = onOpenDirectoryChooser,
                                        onRequestAllFilesAccess = onRequestAllFilesAccess,
                                        onSelectRecentWorkspace = onSelectRecentWorkspace,
                                        onRemoveRecentWorkspace = onRemoveRecentWorkspace,
                                        onUpdateDisplayName = onUpdateDisplayName,
                                        onSelectProviderAndModel = onSelectProviderAndModel,
                                        onSaveProviderApiKey = onSaveProviderApiKey,
                                        onUpdateInferenceDefaults = onUpdateInferenceDefaults,
                                        onSelectPreset = onSelectPreset,
                                        onUnloadModel = onUnloadModel,
                                        onTestApiKey = onTestApiKey,
                                        onRefreshModels = onRefreshModels,
                                        onToggleShowAllOpenAi = onToggleShowAllOpenAi,
                                        onSaveHuggingFaceToken = onSaveHuggingFaceToken,
                                        onOpenLocalLlmHub = onOpenLocalLlmHub,
                                        onExportAllChatsJson = onExportAllChatsJson,
                                        onExportAllChatsMarkdown = onExportAllChatsMarkdown,
                                        onClearAllChatHistory = onClearAllChatHistory,
                                        onClearAllSavedApiKeys = onClearAllSavedApiKeys,
                                        onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                                        onSignOut = onSignOut,
                                        executionEnvironmentSummary = executionEnvironmentSummary,
                                        onOpenExecutionEnvironment = onOpenExecutionEnvironment
                                    )
                                }
                            }
                        }
                    } else {
                        // Single-pane layout on phones (< 840dp)
                        if (activeSection == null) {
                            SettingsHomeContent(
                                effectiveDisplayName = effectiveDisplayName,
                                userEmail = userEmail,
                                isGuestMode = isGuestMode,
                                remainingGuestPrompts = remainingGuestPrompts,
                                avatarBitmap = avatarBitmap,
                                personalizationConfig = personalizationConfig,
                                settings = settings,
                                themeMode = themeMode,
                                selectedSection = null,
                                showInlineBottomAccountButton = false,
                                onSelectSection = { activeSection = it },
                                onChangePicture = { showPhotoActionSheet = true },
                                onRequestSignIn = onSignOut,
                                onRequestSignOut = { showSignOutConfirmDialog = true }
                            )
                        } else {
                            SettingsSectionDetailRouter(
                                section = activeSection!!,
                                settings = settings,
                                personalizationConfig = personalizationConfig,
                                avatarBitmap = avatarBitmap,
                                effectiveDisplayName = effectiveDisplayName,
                                remoteBridgeUrl = remoteBridgeUrl,
                                currentWorkingDir = currentWorkingDir,
                                hasAllFilesAccess = hasAllFilesAccess,
                                isGuestMode = isGuestMode,
                                isNetworkOnline = isNetworkOnline,
                                userEmail = userEmail,
                                themeMode = themeMode,
                                downloadedLocalModelsCount = downloadedLocalModelsCount,
                                totalDownloadedBytes = totalDownloadedBytes,
                                savedKeysMap = savedKeysMap,
                                activePreset = activePreset,
                                activeLoadedModel = activeLoadedModel,
                                availableModelOptions = availableModelOptions,
                                isFetchingModels = isFetchingModels,
                                keyTestStatuses = keyTestStatuses,
                                huggingFaceToken = huggingFaceToken,
                                showAllOpenAiModels = showAllOpenAiModels,
                                deviceProfile = deviceProfile,
                                engineInfo = engineInfo,
                                onChangePhotoClick = { showPhotoActionSheet = true },
                                onSelectThemeMode = onSelectThemeMode,
                                onSelectFontSize = onSelectFontSize,
                                onSelectResponseStyle = onSelectResponseStyle,
                                onSelectPersonaPreset = onSelectPersonaPreset,
                                onSaveCustomPersona = onSaveCustomPersona,
                                onDeleteCustomPersona = onDeleteCustomPersona,
                                onUpdatePersonalization = onUpdatePersonalization,
                                onToggleSkill = onToggleSkill,
                                onSaveCustomSkill = onSaveCustomSkill,
                                onDeleteCustomSkill = onDeleteCustomSkill,
                                onUpdateSkillsTokenBudget = onUpdateSkillsTokenBudget,
                                onImportSkillsContent = onImportSkillsContent,
                                onExportSkillsJson = {
                                    val json = onExportSkillsJson()
                                    clipboardManager.setText(AnnotatedString(json))
                                    statusToastMessage = "Skills JSON copied to clipboard"
                                },
                                onOpenDirectoryChooser = onOpenDirectoryChooser,
                                onRequestAllFilesAccess = onRequestAllFilesAccess,
                                onSelectRecentWorkspace = onSelectRecentWorkspace,
                                onRemoveRecentWorkspace = onRemoveRecentWorkspace,
                                onUpdateDisplayName = onUpdateDisplayName,
                                onSelectProviderAndModel = onSelectProviderAndModel,
                                onSaveProviderApiKey = onSaveProviderApiKey,
                                onUpdateInferenceDefaults = onUpdateInferenceDefaults,
                                onSelectPreset = onSelectPreset,
                                onUnloadModel = onUnloadModel,
                                onTestApiKey = onTestApiKey,
                                onRefreshModels = onRefreshModels,
                                onToggleShowAllOpenAi = onToggleShowAllOpenAi,
                                onSaveHuggingFaceToken = onSaveHuggingFaceToken,
                                onOpenLocalLlmHub = onOpenLocalLlmHub,
                                onExportAllChatsJson = onExportAllChatsJson,
                                onExportAllChatsMarkdown = onExportAllChatsMarkdown,
                                onClearAllChatHistory = onClearAllChatHistory,
                                onClearAllSavedApiKeys = onClearAllSavedApiKeys,
                                onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                                onSignOut = onSignOut,
                                executionEnvironmentSummary = executionEnvironmentSummary,
                                onOpenExecutionEnvironment = onOpenExecutionEnvironment
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Reset settings to defaults?") },
            text = {
                Text(
                    "This resets your persona, custom instructions, skills, theme, font size, and inference parameters to default values. Your saved API keys and downloaded GGUF models will NOT be removed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetSettings()
                        onSelectThemeMode(NexusThemeMode.SYSTEM)
                        showResetConfirmDialog = false
                        statusToastMessage = "Settings reset to defaults"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_settings_button")
                ) {
                    Text("Reset settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Log out of Nexus Agent?") },
            text = {
                Text("Your cloud sessions remain safely stored in Firestore. Local device API keys stay encrypted on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportImportDialog) {
        AlertDialog(
            onDismissRequest = { showExportImportDialog = false },
            title = { Text("Export / Import Settings (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Settings exports include your persona, custom instructions, skills, and model defaults. API keys are NEVER included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val json = onExportFullSettingsJson()
                            clipboardManager.setText(AnnotatedString(json))
                            statusToastMessage = "Settings JSON copied to clipboard"
                            showExportImportDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("copy_exported_settings_json_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy exported JSON to clipboard")
                    }
                    HorizontalDivider()
                    Text(
                        text = "Import from JSON",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        label = { Text("Paste exported settings JSON") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_settings_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = onImportFullSettingsJson(importJsonInput)
                        res.fold(
                            onSuccess = { importedTheme ->
                                if (!importedTheme.isNullOrBlank()) {
                                    onSelectThemeMode(NexusThemeMode.fromId(importedTheme))
                                }
                                statusToastMessage = "Settings imported"
                                showExportImportDialog = false
                            },
                            onFailure = { err ->
                                statusToastMessage = err.localizedMessage ?: "Failed to import settings JSON"
                            }
                        )
                    },
                    enabled = importJsonInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_import_settings_json_button")
                ) {
                    Text("Import JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportImportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
internal fun SettingsHomeContent(
    effectiveDisplayName: String,
    userEmail: String?,
    isGuestMode: Boolean,
    remainingGuestPrompts: Int,
    avatarBitmap: Bitmap?,
    personalizationConfig: NexusPersonalizationConfig,
    settings: NexusUserSettings,
    themeMode: NexusThemeMode,
    selectedSection: SettingsSection?,
    showInlineBottomAccountButton: Boolean = false,
    onSelectSection: (SettingsSection) -> Unit,
    onChangePicture: () -> Unit,
    onRequestSignIn: () -> Unit,
    onRequestSignOut: () -> Unit
) {
    val activePersona = personalizationConfig.activePersona

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Profile Header Card (Tapping card, avatar, or "Change picture" edits profile)
            item(key = "profile_header_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSection(SettingsSection.PROFILE) }
                        .testTag("settings_home_profile_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NexusUserAvatar(
                            avatarBitmap = avatarBitmap,
                            displayName = effectiveDisplayName,
                            email = userEmail,
                            isGuestMode = isGuestMode,
                            size = 76.dp,
                            modifier = Modifier
                                .clickable(onClick = onChangePicture)
                                .testTag("settings_home_avatar")
                        )
                        TextButton(
                            onClick = onChangePicture,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("settings_home_change_picture_button")
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Change picture",
                                style = MaterialTheme.typography.labelMedium,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = effectiveDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (!isGuestMode) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = if (!isGuestMode) {
                                    userEmail ?: "Google Account Connected"
                                } else {
                                    "Guest · $remainingGuestPrompts cloud prompts remaining"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isGuestMode) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Section Cards (No duplicate Profile card; concise 1-line subtitles)
            item(key = "section_general") {
                SettingsHomeSectionCard(
                    section = SettingsSection.GENERAL,
                    title = "General",
                    subtitle = "Persona: ${activePersona.name}",
                    isSelected = selectedSection == SettingsSection.GENERAL,
                    onClick = { onSelectSection(SettingsSection.GENERAL) },
                    testTag = "settings_nav_general"
                )
            }

            item(key = "section_model_config") {
                SettingsHomeSectionCard(
                    section = SettingsSection.MODEL_CONFIG,
                    title = "Model Configuration",
                    subtitle = "${NexusToolAndRedactor.providerDisplayLabel(settings.provider)} · ${settings.selectedModel}",
                    isSelected = selectedSection == SettingsSection.MODEL_CONFIG,
                    onClick = { onSelectSection(SettingsSection.MODEL_CONFIG) },
                    testTag = "settings_nav_model_config"
                )
            }

            item(key = "section_data_privacy") {
                SettingsHomeSectionCard(
                    section = SettingsSection.DATA_PRIVACY,
                    title = "Data & Privacy",
                    subtitle = "Redactor active",
                    isSelected = selectedSection == SettingsSection.DATA_PRIVACY,
                    onClick = { onSelectSection(SettingsSection.DATA_PRIVACY) },
                    testTag = "settings_nav_data_privacy"
                )
            }

            item(key = "section_about") {
                SettingsHomeSectionCard(
                    section = SettingsSection.ABOUT,
                    title = "About",
                    subtitle = "Nexus Agent v5.0.0",
                    isSelected = selectedSection == SettingsSection.ABOUT,
                    onClick = { onSelectSection(SettingsSection.ABOUT) },
                    testTag = "settings_nav_about"
                )
            }
        }

        if (showInlineBottomAccountButton) {
            SettingsAccountBottomBar(
                isGuestMode = isGuestMode,
                onRequestSignIn = onRequestSignIn,
                onRequestSignOut = onRequestSignOut
            )
        }
    }
}

@Composable
internal fun SettingsAccountBottomBar(
    isGuestMode: Boolean,
    onRequestSignIn: () -> Unit,
    onRequestSignOut: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (!isGuestMode) {
                OutlinedButton(
                    onClick = onRequestSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log out", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Button(
                    onClick = onRequestSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_sign_in_button")
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign up / Sign in", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SettingsHomeSectionCard(
    section: SettingsSection,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSectionDetailRouter(
    section: SettingsSection,
    settings: NexusUserSettings,
    personalizationConfig: NexusPersonalizationConfig,
    avatarBitmap: Bitmap?,
    effectiveDisplayName: String,
    remoteBridgeUrl: String,
    currentWorkingDir: String,
    hasAllFilesAccess: Boolean,
    isGuestMode: Boolean,
    isNetworkOnline: Boolean,
    userEmail: String?,
    themeMode: NexusThemeMode,
    downloadedLocalModelsCount: Int,
    totalDownloadedBytes: Long,
    savedKeysMap: Map<String, String>,
    activePreset: InferencePreset,
    activeLoadedModel: ActiveLoadedModelState?,
    availableModelOptions: List<ModelOption>,
    isFetchingModels: Map<String, Boolean>,
    keyTestStatuses: Map<String, ApiKeyTestResult>,
    huggingFaceToken: String,
    showAllOpenAiModels: Boolean,
    deviceProfile: DeviceProfile,
    engineInfo: EngineInfo,
    onChangePhotoClick: () -> Unit,
    onSelectThemeMode: (NexusThemeMode) -> Unit,
    onSelectFontSize: (NexusFontSize) -> Unit,
    onSelectResponseStyle: (NexusResponseStyle) -> Unit,
    onSelectPersonaPreset: (String) -> Unit,
    onSaveCustomPersona: (name: String, prompt: String, existingId: String?) -> Unit,
    onDeleteCustomPersona: (String) -> Unit,
    onUpdatePersonalization: (aboutUser: String, responsePref: String, enableForNewChats: Boolean) -> Unit,
    onToggleSkill: (skillId: String, enabled: Boolean) -> Unit,
    onSaveCustomSkill: (id: String?, name: String, description: String, instructions: String, enabled: Boolean) -> Unit,
    onDeleteCustomSkill: (String) -> Unit,
    onUpdateSkillsTokenBudget: (Int) -> Unit,
    onImportSkillsContent: (String) -> Result<Unit>,
    onExportSkillsJson: () -> Unit,
    onOpenDirectoryChooser: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSelectRecentWorkspace: (String) -> Unit,
    onRemoveRecentWorkspace: (String) -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSelectProviderAndModel: (provider: String, modelId: String) -> Unit,
    onSaveProviderApiKey: (provider: String, apiKey: String) -> Unit,
    onUpdateInferenceDefaults: (maxTokens: Long, maxSteps: Long, timeoutSec: Long, remoteBridge: String) -> Unit,
    onSelectPreset: (InferencePreset) -> Unit,
    onUnloadModel: () -> Unit,
    onTestApiKey: (provider: String, keyOverride: String?) -> Unit,
    onRefreshModels: (provider: String) -> Unit,
    onToggleShowAllOpenAi: (Boolean) -> Unit,
    onSaveHuggingFaceToken: (String) -> Unit,
    onOpenLocalLlmHub: () -> Unit,
    onExportAllChatsJson: () -> Unit,
    onExportAllChatsMarkdown: () -> Unit,
    onClearAllChatHistory: () -> Unit,
    onClearAllSavedApiKeys: () -> Unit,
    onDeleteAccountAndCloudData: () -> Unit,
    onSignOut: () -> Unit,
    executionEnvironmentSummary: String = "Termux / Android Shell",
    onOpenExecutionEnvironment: () -> Unit = {}
) {
    when (section) {
        SettingsSection.PROFILE -> {
            ProfileSettingsSubpage(
                avatarBitmap = avatarBitmap,
                displayName = effectiveDisplayName,
                userEmail = userEmail,
                isGuestMode = isGuestMode,
                onChangePhotoClick = onChangePhotoClick,
                onUpdateDisplayName = onUpdateDisplayName,
                onSignOutClick = onSignOut,
                onDeleteAccountAndCloudData = onDeleteAccountAndCloudData
            )
        }
        SettingsSection.GENERAL -> {
            GeneralSettingsSubpage(
                themeMode = themeMode,
                personalizationConfig = personalizationConfig,
                currentWorkingDir = currentWorkingDir,
                hasAllFilesAccess = hasAllFilesAccess,
                onSelectThemeMode = onSelectThemeMode,
                onSelectFontSize = onSelectFontSize,
                onSelectResponseStyle = onSelectResponseStyle,
                onSelectPersonaPreset = onSelectPersonaPreset,
                onSaveCustomPersona = onSaveCustomPersona,
                onDeleteCustomPersona = onDeleteCustomPersona,
                onUpdatePersonalization = onUpdatePersonalization,
                onToggleSkill = onToggleSkill,
                onSaveCustomSkill = onSaveCustomSkill,
                onDeleteCustomSkill = onDeleteCustomSkill,
                onUpdateSkillsTokenBudget = onUpdateSkillsTokenBudget,
                onImportSkillsContent = onImportSkillsContent,
                onExportSkillsJson = onExportSkillsJson,
                onOpenDirectoryChooser = onOpenDirectoryChooser,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onSelectRecentWorkspace = onSelectRecentWorkspace,
                onRemoveRecentWorkspace = onRemoveRecentWorkspace,
                executionEnvironmentSummary = executionEnvironmentSummary,
                onOpenExecutionEnvironment = onOpenExecutionEnvironment
            )
        }
        SettingsSection.MODEL_CONFIG -> {
            ModelConfigurationSettingsSubpage(
                settings = settings,
                remoteBridgeUrl = remoteBridgeUrl,
                savedKeysMap = savedKeysMap,
                activePreset = activePreset,
                activeLoadedModel = activeLoadedModel,
                downloadedLocalModelsCount = downloadedLocalModelsCount,
                availableModelOptions = availableModelOptions,
                isFetchingModels = isFetchingModels,
                keyTestStatuses = keyTestStatuses,
                huggingFaceToken = huggingFaceToken,
                showAllOpenAiModels = showAllOpenAiModels,
                engineInfo = engineInfo,
                onSelectProviderAndModel = onSelectProviderAndModel,
                onSaveProviderApiKey = onSaveProviderApiKey,
                onUpdateInferenceDefaults = onUpdateInferenceDefaults,
                onSelectPreset = onSelectPreset,
                onUnloadModel = onUnloadModel,
                onTestApiKey = onTestApiKey,
                onRefreshModels = onRefreshModels,
                onToggleShowAllOpenAi = onToggleShowAllOpenAi,
                onSaveHuggingFaceToken = onSaveHuggingFaceToken,
                onOpenLocalLlmHub = onOpenLocalLlmHub
            )
        }
        SettingsSection.DATA_PRIVACY -> {
            val savedKeysCount = savedKeysMap.count { it.value.isNotBlank() }
            DataAndPrivacySettingsSubpage(
                isGuestMode = isGuestMode,
                isNetworkOnline = isNetworkOnline,
                downloadedLocalModelsCount = downloadedLocalModelsCount,
                totalDownloadedBytes = totalDownloadedBytes,
                currentWorkingDir = currentWorkingDir,
                hasAllFilesAccess = hasAllFilesAccess,
                recentWorkspaces = personalizationConfig.recentWorkspaces,
                savedKeysCount = savedKeysCount,
                onExportAllChatsJson = onExportAllChatsJson,
                onExportAllChatsMarkdown = onExportAllChatsMarkdown,
                onClearAllChatHistory = onClearAllChatHistory,
                onOpenLocalLlmHub = onOpenLocalLlmHub,
                onOpenDirectoryChooser = onOpenDirectoryChooser,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onSelectRecentWorkspace = onSelectRecentWorkspace,
                onRemoveRecentWorkspace = onRemoveRecentWorkspace,
                onClearAllSavedApiKeys = onClearAllSavedApiKeys,
                executionEnvironmentSummary = executionEnvironmentSummary,
                onOpenExecutionEnvironment = onOpenExecutionEnvironment
            )
        }
        SettingsSection.ABOUT -> {
            AboutSettingsSubpage(
                deviceProfile = deviceProfile,
                engineInfo = engineInfo
            )
        }
    }
}

// ============================================================================
// SECTION 3 — MODEL CONFIGURATION SUB-PAGE
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigurationSettingsSubpage(
    settings: NexusUserSettings,
    remoteBridgeUrl: String,
    savedKeysMap: Map<String, String>,
    activePreset: InferencePreset,
    activeLoadedModel: ActiveLoadedModelState?,
    downloadedLocalModelsCount: Int,
    availableModelOptions: List<ModelOption>,
    isFetchingModels: Map<String, Boolean>,
    keyTestStatuses: Map<String, ApiKeyTestResult>,
    huggingFaceToken: String,
    showAllOpenAiModels: Boolean,
    engineInfo: EngineInfo,
    onSelectProviderAndModel: (provider: String, modelId: String) -> Unit,
    onSaveProviderApiKey: (provider: String, apiKey: String) -> Unit,
    onUpdateInferenceDefaults: (maxTokens: Long, maxSteps: Long, timeoutSec: Long, remoteBridge: String) -> Unit,
    onSelectPreset: (InferencePreset) -> Unit,
    onUnloadModel: () -> Unit,
    onTestApiKey: (provider: String, keyOverride: String?) -> Unit,
    onRefreshModels: (provider: String) -> Unit,
    onToggleShowAllOpenAi: (Boolean) -> Unit,
    onSaveHuggingFaceToken: (String) -> Unit,
    onOpenLocalLlmHub: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedProvider by remember(settings.provider) {
        mutableStateOf(NexusToolAndRedactor.normalizeProviderId(settings.provider))
    }
    var selectedModelText by remember(settings.selectedModel) { mutableStateOf(settings.selectedModel) }

    val cloudProviders = remember { listOf("gemini", "anthropic", "openai") }
    val providerKeysState = remember(savedKeysMap) {
        mutableStateMapOf<String, String>().apply {
            cloudProviders.forEach { p ->
                put(p, savedKeysMap["provider_$p"].orEmpty())
            }
        }
    }
    val providerKeyVisible = remember { mutableStateMapOf<String, Boolean>() }
    var expandedProviderCard by remember(selectedProvider) {
        mutableStateOf<String?>(if (selectedProvider in cloudProviders) selectedProvider else "gemini")
    }

    var hfTokenInput by remember(huggingFaceToken) { mutableStateOf(huggingFaceToken) }
    var showHfToken by remember { mutableStateOf(false) }
    var showHfTokenAccordion by remember { mutableStateOf(false) }
    var showAdvancedInferenceAccordion by remember { mutableStateOf(false) }

    var maxOutputTokensText by remember(settings.maxTokens) { mutableStateOf(settings.maxTokens.toString()) }
    var maxStepsText by remember(settings.maxSteps) { mutableStateOf(settings.maxSteps.toString()) }
    var timeoutSecText by remember(settings.timeoutSec) { mutableStateOf(settings.timeoutSec.toString()) }
    var remoteBridgeUrlText by remember(remoteBridgeUrl) { mutableStateOf(remoteBridgeUrl) }

    val currentProviderOptions = remember(selectedProvider, availableModelOptions) {
        if (selectedProvider == "local") {
            com.example.domain.LocalLlmManager.LOCAL_LLM_CATALOG.map { spec ->
                ModelOption(
                    provider = "local",
                    id = spec.id,
                    displayName = spec.id,
                    isUnverified = false
                )
            }
        } else {
            availableModelOptions.filter {
                NexusToolAndRedactor.normalizeProviderId(it.provider) == selectedProvider
            }
        }
    }

    val isCurrentProviderRefreshing = isFetchingModels[selectedProvider] == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("settings_subpage_model_config"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Default Active Model Card
        ModelConfigSectionCard(
            title = "Default Active Model",
            subtitle = "Choose the provider and exact model ID used by new and active turns"
        ) {
            Text(
                text = "1. Select Provider",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexusToolAndRedactor.AI_PROVIDERS.forEach { providerInfo ->
                    val provId = providerInfo.id
                    FilterChip(
                        selected = selectedProvider == provId,
                        onClick = {
                            selectedProvider = provId
                            val options = if (provId == "local") {
                                com.example.domain.LocalLlmManager.LOCAL_LLM_CATALOG.map { it.id }
                            } else {
                                availableModelOptions
                                    .filter { NexusToolAndRedactor.normalizeProviderId(it.provider) == provId }
                                    .map { it.id }
                            }
                            val nextModel = options.firstOrNull()
                                ?: providerInfo.defaultModel.ifBlank { selectedModelText }
                            selectedModelText = nextModel
                            onSelectProviderAndModel(provId, nextModel)
                        },
                        label = { Text(providerInfo.shortBadge) },
                        modifier = Modifier.testTag("provider_chip_$provId")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Select Model (${currentProviderOptions.size} available)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedProvider != "local") {
                    TextButton(
                        onClick = { onRefreshModels(selectedProvider) },
                        enabled = !isCurrentProviderRefreshing,
                        modifier = Modifier.testTag("refresh_models_button")
                    ) {
                        if (isCurrentProviderRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh models", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (currentProviderOptions.isEmpty() && selectedProvider != "local") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter your ${NexusToolAndRedactor.providerDisplayLabel(selectedProvider)} API key below and tap Refresh models to fetch live models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentProviderOptions.forEach { option ->
                        FilterChip(
                            selected = selectedModelText == option.id,
                            onClick = {
                                selectedModelText = option.id
                                onSelectProviderAndModel(selectedProvider, option.id)
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(option.id)
                                    if (option.isUnverified) {
                                        Text(
                                            text = "unverified",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("model_preset_${option.id}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = selectedModelText,
                onValueChange = {
                    selectedModelText = it
                    if (it.isNotBlank()) {
                        onSelectProviderAndModel(selectedProvider, it.trim())
                    }
                },
                label = { Text("Exact Model Request ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_model_input")
            )
        }

        // 2. Cloud Providers (One clean expandable card per cloud provider)
        ModelConfigSectionCard(
            title = "Cloud Provider API Keys",
            subtitle = "Encrypted on-device with AES256-GCM. Never synced to cloud or included in exports."
        ) {
            cloudProviders.forEach { provId ->
                val provLabel = NexusToolAndRedactor.providerDisplayLabel(provId)
                val keyVal = providerKeysState[provId].orEmpty()
                val visible = providerKeyVisible[provId] == true
                val testResult = keyTestStatuses[provId]
                val isExpanded = expandedProviderCard == provId
                val providerModels = availableModelOptions.filter {
                    NexusToolAndRedactor.normalizeProviderId(it.provider) == provId
                }
                val isActiveProvider = selectedProvider == provId
                val isRefreshingProv = isFetchingModels[provId] == true

                val badgeLabel = when (testResult) {
                    is ApiKeyTestResult.Testing -> "Testing…"
                    is ApiKeyTestResult.Valid -> "Valid (${testResult.modelCount} models)"
                    is ApiKeyTestResult.InvalidKey -> "Invalid key: ${testResult.message}"
                    is ApiKeyTestResult.QuotaExceeded -> "Quota exceeded: ${testResult.message}"
                    is ApiKeyTestResult.NetworkError -> "Network error: ${testResult.message}"
                    else -> when {
                        keyVal.isBlank() -> "Not set"
                        providerModels.isNotEmpty() -> "Valid (${providerModels.size} models)"
                        else -> "Key saved"
                    }
                }

                val isValidBadge = testResult is ApiKeyTestResult.Valid || badgeLabel.startsWith("Valid")
                val isErrorBadge = testResult is ApiKeyTestResult.InvalidKey ||
                    testResult is ApiKeyTestResult.QuotaExceeded ||
                    testResult is ApiKeyTestResult.NetworkError

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isActiveProvider) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_provider_card_$provId")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedProviderCard = if (isExpanded) null else provId
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = provLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isActiveProvider) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isValidBadge -> MaterialTheme.colorScheme.primary
                                        isErrorBadge -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = keyVal,
                                    onValueChange = { updated ->
                                        providerKeysState[provId] = updated
                                        onSaveProviderApiKey(provId, updated.trim())
                                    },
                                    label = { Text("$provLabel API Key") },
                                    placeholder = { Text(apiKeyHintForProviderId(provId)) },
                                    singleLine = true,
                                    visualTransformation = if (visible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                                    if (clip.isNotBlank()) {
                                                        providerKeysState[provId] = clip
                                                        onSaveProviderApiKey(provId, clip)
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentPaste,
                                                    contentDescription = "Paste API key",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { providerKeyVisible[provId] = !visible }
                                            ) {
                                                Icon(
                                                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle key visibility",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            if (isActiveProvider) {
                                                "active_model_api_key_input"
                                            } else {
                                                "provider_key_input_$provId"
                                            }
                                        )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onTestApiKey(provId, keyVal) },
                                        modifier = Modifier.testTag(
                                            if (isActiveProvider) {
                                                "test_active_api_key_button"
                                            } else {
                                                "test_key_button_$provId"
                                            }
                                        )
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test key")
                                    }
                                    OutlinedButton(
                                        onClick = { onRefreshModels(provId) },
                                        enabled = !isRefreshingProv
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refresh models")
                                    }
                                    if (keyVal.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                providerKeysState[provId] = ""
                                                onSaveProviderApiKey(provId, "")
                                            }
                                        ) {
                                            Text("Clear", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (provId == "openai") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Show all OpenAI models (unfiltered)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Switch(
                                            checked = showAllOpenAiModels,
                                            onCheckedChange = onToggleShowAllOpenAi,
                                            modifier = Modifier.testTag("show_all_openai_switch")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. On-Device Local Models Summary Card
        val totalLocalCatalog = com.example.domain.LocalLlmManager.LOCAL_LLM_CATALOG.size
        ModelConfigSectionCard(
            title = "On-Device Local Models (GGUF)",
            subtitle = "Zero-cloud local inference via llama.cpp JNI when compiled or installed"
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Runtime Engine: ${engineInfo.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            val tokPerSec = activeLoadedModel?.benchmark?.tokensPerSec
                            Text(
                                text = if (activeLoadedModel != null) {
                                    buildString {
                                        append("Loaded: ${activeLoadedModel.modelName}")
                                        if (tokPerSec != null && tokPerSec > 0.0) {
                                            append(" · ${String.format(Locale.US, "%.1f", tokPerSec)} tok/s")
                                        }
                                    }
                                } else {
                                    "No model loaded in RAM · $downloadedLocalModelsCount/$totalLocalCatalog GGUF models downloaded"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeLoadedModel != null) {
                            OutlinedButton(
                                onClick = onUnloadModel,
                                modifier = Modifier.testTag("settings_unload_local_model_button")
                            ) {
                                Text("Unload")
                            }
                        }
                    }

                    Button(
                        onClick = onOpenLocalLlmHub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_local_llm_hub_button")
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Local LLM Hub")
                    }

                    TextButton(
                        onClick = { showHfTokenAccordion = !showHfTokenAccordion },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (showHfTokenAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Advanced: Hugging Face Access Token (optional, for gated repos)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    AnimatedVisibility(visible = showHfTokenAccordion) {
                        OutlinedTextField(
                            value = hfTokenInput,
                            onValueChange = {
                                hfTokenInput = it
                                onSaveHuggingFaceToken(it.trim())
                            },
                            label = { Text("Hugging Face Token (hf_...)") },
                            singleLine = true,
                            visualTransformation = if (showHfToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showHfToken = !showHfToken }) {
                                    Icon(
                                        if (showHfToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle HF token visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hf_token_input")
                        )
                    }
                }
            }
        }

        // 4. Inference Defaults (Collapsible Advanced Section)
        ModelConfigSectionCard(
            title = "Inference Defaults & Agent Loop",
            subtitle = "Preset: ${activePreset.label} · Max $maxOutputTokensText tokens · $maxStepsText steps"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedInferenceAccordion = !showAdvancedInferenceAccordion },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvancedInferenceAccordion) "Hide advanced parameters" else "Configure advanced parameters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (showAdvancedInferenceAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showAdvancedInferenceAccordion) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Inference Preset",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InferencePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = activePreset == preset,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("preset_chip_${preset.id}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = maxOutputTokensText,
                            onValueChange = { value ->
                                maxOutputTokensText = value.filter { it.isDigit() }
                                val parsed = maxOutputTokensText.toLongOrNull()
                                if (parsed != null) {
                                    onUpdateInferenceDefaults(
                                        parsed,
                                        maxStepsText.toLongOrNull() ?: settings.maxSteps,
                                        timeoutSecText.toLongOrNull() ?: settings.timeoutSec,
                                        remoteBridgeUrlText
                                    )
                                }
                            },
                            label = { Text("Max Output Tokens") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("max_output_tokens_input")
                        )
                        OutlinedTextField(
                            value = maxStepsText,
                            onValueChange = { value ->
                                maxStepsText = value.filter { it.isDigit() }
                                val parsed = maxStepsText.toLongOrNull()
                                if (parsed != null) {
                                    onUpdateInferenceDefaults(
                                        maxOutputTokensText.toLongOrNull() ?: settings.maxTokens,
                                        parsed,
                                        timeoutSecText.toLongOrNull() ?: settings.timeoutSec,
                                        remoteBridgeUrlText
                                    )
                                }
                            },
                            label = { Text("Max Steps (1–12)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("max_steps_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = timeoutSecText,
                            onValueChange = { value ->
                                timeoutSecText = value.filter { it.isDigit() }
                                val parsed = timeoutSecText.toLongOrNull()
                                if (parsed != null) {
                                    onUpdateInferenceDefaults(
                                        maxOutputTokensText.toLongOrNull() ?: settings.maxTokens,
                                        maxStepsText.toLongOrNull() ?: settings.maxSteps,
                                        parsed,
                                        remoteBridgeUrlText
                                    )
                                }
                            },
                            label = { Text("Timeout (sec)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("timeout_sec_input")
                        )
                        OutlinedTextField(
                            value = remoteBridgeUrlText,
                            onValueChange = { value ->
                                remoteBridgeUrlText = value
                                onUpdateInferenceDefaults(
                                    maxOutputTokensText.toLongOrNull() ?: settings.maxTokens,
                                    maxStepsText.toLongOrNull() ?: settings.maxSteps,
                                    timeoutSecText.toLongOrNull() ?: settings.timeoutSec,
                                    remoteBridgeUrlText.trim()
                                )
                            },
                            label = { Text("Remote Bridge URL (optional)") },
                            placeholder = { Text("https://bridge.internal/exec") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remote_bridge_input")
                        )
                    }
                }
            }
        }
    }
}

private fun apiKeyHintForProviderId(providerId: String): String = when (providerId.lowercase()) {
    "gemini" -> "AIzaSy..."
    "anthropic", "fable" -> "sk-ant-api03-..."
    "openai" -> "sk-proj-..."
    else -> "API key"
}

@Composable
private fun ModelConfigSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun SessionsSwitcherDialog(
    sessions: List<com.example.data.model.NexusSession>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCreateNewSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Chat Session") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("New session title…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onCreateNewSession(newTitle)
                            newTitle = ""
                            onDismiss()
                        }
                    ) {
                        Text("Create")
                    }
                }
                HorizontalDivider()
                sessions.forEach { sess ->
                    val isCurrent = sess.id == activeSessionId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSession(sess.id)
                                onDismiss()
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = sess.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${sess.provider} · ${sess.model} · ${sess.cwd}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AiAnalysisModal(
    analysis: AiCommandAnalysis,
    onRunSuggestedCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Command Audit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = analysis.summary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = analysis.rootCauseOrImpact,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = analysis.securityNotes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (analysis.suggestedCommand.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onRunSuggestedCommand(analysis.suggestedCommand)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run: ${analysis.suggestedCommand}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
