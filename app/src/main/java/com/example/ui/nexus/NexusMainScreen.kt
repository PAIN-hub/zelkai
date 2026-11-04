package com.example.ui.nexus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.TranscriptEntry
import com.example.data.repository.NexusRepository
import com.example.domain.AgentPhase
import com.example.domain.AgentStatus
import com.example.domain.LocalLlmManager
import com.example.domain.LocalModelDownloadState
import com.example.domain.LocalShellBridge
import com.example.domain.NexusPersonalizationStore
import com.example.domain.NexusToolAndRedactor
import com.example.domain.TimelineBlock
import com.example.domain.TurnTimelineBuilder
import com.example.domain.ProviderModelRepository
import com.example.domain.UserApiKeyStore
import com.example.ui.auth.signOutFromNexus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusAccentSoftLight
import com.example.ui.theme.NexusBgDark
import com.example.ui.theme.NexusBgLight
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.domain.ManagedJobRecord
import com.example.domain.ManagedJobState
import com.example.ui.theme.NexusPalette
import com.example.ui.theme.rememberNexusPalette
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusThemeMode
import com.example.ui.theme.NexusWarn
import com.example.util.NexusNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.hypot
import kotlinx.coroutines.launch

enum class NexusViewMode {
    CHAT,
    TERMINAL,
    DASHBOARD
}

private fun checkAllFilesAccessGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun extractFirstName(
    displayName: String?,
    email: String?,
    isGuestMode: Boolean
): String? {
    if (isGuestMode) return null
    val rawName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.substringBefore('@')?.replace('.', ' ')?.replace('_', ' ')?.trim()
    val firstToken = rawName?.split(Regex("\\s+"))?.firstOrNull()?.trim()
    return firstToken?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
}

@Composable
fun NexusMainScreen(
    currentUserId: String,
    isGuestMode: Boolean = false,
    isDarkTheme: Boolean,
    themeMode: NexusThemeMode = if (isDarkTheme) NexusThemeMode.DARK else NexusThemeMode.LIGHT,
    onToggleTheme: () -> Unit,
    onSelectThemeMode: (NexusThemeMode) -> Unit = {},
    onSelectFontScale: (Float) -> Unit = {},
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: NexusViewModel = viewModel(
        key = "${currentUserId}_$isGuestMode",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val notificationHelper = NexusNotificationHelper(app)
                val keyStore = UserApiKeyStore(app)
                NexusViewModel(
                    repository = NexusRepository(db),
                    shellBridge = LocalShellBridge(app),
                    localLlmManager = LocalLlmManager(app),
                    userApiKeyStore = keyStore,
                    notificationHelper = notificationHelper,
                    currentUserId = currentUserId,
                    isGuestMode = isGuestMode,
                    providerModelRepository = ProviderModelRepository(app, keyStore),
                    personalizationStore = NexusPersonalizationStore(app),
                    appContext = app
                )
            }
        }
    )

    val personalizationConfig by viewModel.personalizationConfig.collectAsStateWithLifecycle()
    val avatarBitmap by viewModel.avatarBitmap.collectAsStateWithLifecycle()

    LaunchedEffect(personalizationConfig.fontSize) {
        onSelectFontScale(personalizationConfig.fontSize.scaleFactor)
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Single source of truth for provider and model (Bug #2)
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()

    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val currentWorkingDir by viewModel.currentWorkingDir.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val entriesState by viewModel.entriesState.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val agentStatus by viewModel.agentStatus.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val promptInput by viewModel.promptInput.collectAsStateWithLifecycle()
    val activeAnalysis by viewModel.activeAnalysis.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val uiBannerMessage by viewModel.uiBannerMessage.collectAsStateWithLifecycle()
    val remoteBridgeUrl by viewModel.remoteBridgeUrl.collectAsStateWithLifecycle()
    val downloadedLocalModels by viewModel.downloadedLocalModels.collectAsStateWithLifecycle()
    val localDownloadStates by viewModel.localDownloadStates.collectAsStateWithLifecycle()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val savedApiKeys by viewModel.savedApiKeys.collectAsStateWithLifecycle()
    val runtimeLatenciesMs by viewModel.runtimeLatenciesMs.collectAsStateWithLifecycle()
    val liveNotificationBanner by viewModel.liveNotificationBanner.collectAsStateWithLifecycle()
    val guestDirectivesUsed by viewModel.guestDirectivesUsed.collectAsStateWithLifecycle()
    val isTemporaryChat by viewModel.isTemporaryChat.collectAsStateWithLifecycle()
    val temporarySession by viewModel.temporarySession.collectAsStateWithLifecycle()
    val messageFeedback by viewModel.messageFeedback.collectAsStateWithLifecycle()

    // Part A/B/C/D StateFlows
    val deviceProfile by viewModel.deviceProfile.collectAsStateWithLifecycle()
    val recommendationReport by viewModel.recommendationReport.collectAsStateWithLifecycle()
    val modelBenchmarks by viewModel.modelBenchmarks.collectAsStateWithLifecycle()
    val activeLoadedLocalModel by viewModel.activeLoadedLocalModel.collectAsStateWithLifecycle()
    val inferencePreset by viewModel.inferencePreset.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.isBenchmarking.collectAsStateWithLifecycle()
    val loadGuardError by viewModel.loadGuardError.collectAsStateWithLifecycle()
    val missingApiKeyPrompt by viewModel.missingApiKeyPrompt.collectAsStateWithLifecycle()
    val showGuestChatLimitDialog by viewModel.showGuestChatLimitDialog.collectAsStateWithLifecycle()
    val cloudSyncIssue by viewModel.cloudSyncIssue.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()

    // ExecutionBackend & Background Jobs StateFlows (Part 2)
    val environmentDescription by viewModel.environmentDescription.collectAsStateWithLifecycle()
    val isProbingEnvironment by viewModel.isProbingEnvironment.collectAsStateWithLifecycle()
    val connectionTestResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val backgroundJobs by viewModel.backgroundJobs.collectAsStateWithLifecycle()
    val managedJobs by viewModel.managedJobs.collectAsStateWithLifecycle()
    val reconciledRunningCount by viewModel.reconciledRunningCountOnStart.collectAsStateWithLifecycle()
    val isSetupNeeded by viewModel.isSetupNeeded.collectAsStateWithLifecycle()
    val diagnosticsReport by viewModel.diagnosticsReport.collectAsStateWithLifecycle()
    val selfTestReport by viewModel.selfTestReport.collectAsStateWithLifecycle()
    val isRunningSelfTest by viewModel.isRunningSelfTest.collectAsStateWithLifecycle()
    val projectGuide by viewModel.projectGuide.collectAsStateWithLifecycle()
    val wizardState by viewModel.wizardState.collectAsStateWithLifecycle()

    // Dynamic provider model lists, key test results, Wi-Fi-only toggle, and LocalInferenceEngine info
    val geminiModels by viewModel.geminiModels.collectAsStateWithLifecycle()
    val anthropicModels by viewModel.anthropicModels.collectAsStateWithLifecycle()
    val openAiModels by viewModel.openAiModels.collectAsStateWithLifecycle()
    val showAllOpenAiModels by viewModel.showAllOpenAiModels.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val keyTestStatuses by viewModel.keyTestStatuses.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val engineInfo by viewModel.engineInfo.collectAsStateWithLifecycle()
    val pendingMobileDataDownloadSpec by viewModel.pendingMobileDataDownloadSpec.collectAsStateWithLifecycle()

    // Workspace Project Binding, Pre-Flight, Install Progress & One-Way Mirror StateFlows
    val activeProjectSlug by viewModel.activeProjectSlug.collectAsStateWithLifecycle()
    val isBoundProjectMissing by viewModel.isBoundProjectMissing.collectAsStateWithLifecycle()
    val installProgressBanner by viewModel.installProgressBanner.collectAsStateWithLifecycle()
    val mirrorSyncStatus by viewModel.mirrorSyncStatus.collectAsStateWithLifecycle()

    val allDynamicModelOptions = remember(geminiModels, anthropicModels, openAiModels) {
        geminiModels + anthropicModels + openAiModels
    }

    val nexusPalette = rememberNexusPalette()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLocalLlmHubDialog by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showModelSwitcherSheet by remember { mutableStateOf(false) }
    var showEnvironmentSetupScreen by rememberSaveable { mutableStateOf(false) }
    var showJobsSheet by remember { mutableStateOf(false) }
    var previewPort by remember { mutableStateOf<Int?>(null) }
    var previewTitle by remember { mutableStateOf("") }
    var dismissLimitedShellBanner by rememberSaveable { mutableStateOf(false) }
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(NexusViewMode.CHAT) }
    var isDirectShellInput by rememberSaveable { mutableStateOf(false) }
    var hasAllFilesAccess by remember { mutableStateOf(checkAllFilesAccessGranted()) }
    var guestToastDismissedForCount by rememberSaveable { mutableStateOf(-1) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // BackHandler for drawer, environment setup, jobs sheet, preview, secondary views, or temporary chat
    BackHandler(enabled = drawerState.isOpen || showJobsSheet || previewPort != null || showEnvironmentSetupScreen || viewMode != NexusViewMode.CHAT || isTemporaryChat) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            previewPort != null -> previewPort = null
            showJobsSheet -> showJobsSheet = false
            showEnvironmentSetupScreen -> showEnvironmentSetupScreen = false
            viewMode != NexusViewMode.CHAT -> viewMode = NexusViewMode.CHAT
            isTemporaryChat -> viewModel.exitTemporaryChat()
        }
    }

    // Circular reveal + cross-fade animation state when toggling Light/Dark theme
    var previousDarkTheme by remember { mutableStateOf(isDarkTheme) }
    var previousCanvasOverlayColor by remember { mutableStateOf<Color?>(null) }
    val themeRevealProgress = remember { Animatable(1f) }

    LaunchedEffect(isDarkTheme) {
        if (isDarkTheme != previousDarkTheme) {
            previousCanvasOverlayColor = if (previousDarkTheme) NexusBgDark else NexusBgLight
            previousDarkTheme = isDarkTheme
            themeRevealProgress.snapTo(0f)
            themeRevealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
            previousCanvasOverlayColor = null
        }
    }

    // Android 13+ POST_NOTIFICATIONS runtime permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Handled gracefully by NexusNotificationHelper */ }

    LaunchedEffect(Unit) {
        viewModel.refreshProviderModels(forceNetwork = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Navigate to redesigned AuthScreen to sign in or create an account
    val triggerGuestSignInUpgrade = {
        onSignedOut()
    }

    // All Files Access Launchers
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess = checkAllFilesAccessGranted()
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasAllFilesAccess = grants.values.all { it }
    }

    val requestAllFilesAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                allFilesSettingsLauncher.launch(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                allFilesSettingsLauncher.launch(fallback)
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // System Folder Picker (SAF OpenDocumentTree)
    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val resolvedPath = LocalShellBridge.resolveTreeUriToPath(context, treeUri)
            if (!resolvedPath.isNullOrBlank()) {
                viewModel.changeWorkingDirectory(resolvedPath, createIfMissing = true)
                showDirectoryDialog = false
            }
        }
    }

    // Re-check All Files Access when returning from Settings AND unload local model when backgrounded (Part C)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasAllFilesAccess = checkAllFilesAccessGranted()
                    viewModel.refreshDeviceRecommendations()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackgrounded()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val activeSession = if (isTemporaryChat) temporarySession else sessions.find { it.id == activeSessionId }
    val entries = (entriesState as? UiState.Success)?.data.orEmpty()
    val effectiveCwd = currentWorkingDir.ifBlank { activeSession?.cwd ?: "/nexus_workspace" }

    // Recent shell commands executed in the session for smarter auto-complete
    val recentSessionCommands = remember(entries) {
        entries.mapNotNull { entry ->
            when (entry.type) {
                "command" -> entry.command.takeIf { it.isNotBlank() }
                "user" -> entry.text.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    // Auto-complete suggestions computed dynamically as the user types
    val autocompleteSuggestions = remember(promptInput, isDirectShellInput, settings.inputHistory, recentSessionCommands) {
        NexusToolAndRedactor.computeAutocompleteSuggestions(
            rawQuery = promptInput,
            isDirectShellMode = isDirectShellInput,
            inputHistory = settings.inputHistory,
            recentCommands = recentSessionCommands
        )
    }

    // Active or paused downloads for compact toast
    val activeDownloads = remember(localDownloadStates) {
        localDownloadStates.values.filter {
            it.isDownloading || it.isPaused || it.isWaitingForNetwork
        }
    }

    val hasPendingApproval = remember(entries) {
        entries.any { it.type == "command" && it.commandStatus == "pending" }
    }

    val listState = rememberLazyListState()

    val showJumpToLatest by remember {
        derivedStateOf {
            listState.canScrollForward && entries.size > 2
        }
    }

    LaunchedEffect(entries.size, streamingText, viewMode, missingApiKeyPrompt, loadGuardError) {
        if (viewMode != NexusViewMode.DASHBOARD) {
            val extraItems = (if (streamingText != null) 1 else 0) +
                (if (missingApiKeyPrompt != null) 1 else 0) +
                (if (loadGuardError != null) 1 else 0) +
                (if (viewMode == NexusViewMode.TERMINAL) 1 else 0)
            val total = entries.size + extraItems
            if (total > 0 && !listState.canScrollForward) {
                listState.animateScrollToItem(total - 1)
            } else if (total > 0 && entries.size <= 2) {
                listState.animateScrollToItem(total - 1)
            }
        }
    }

    val remainingGuestPrompts = (NexusViewModel.GUEST_MAX_DIRECTIVES - guestDirectivesUsed).coerceAtLeast(0)
    // Single source of truth provider badge + model ID (Bug #2)
    val activeProviderBadge = NexusToolAndRedactor.providerDisplayLabel(selectedProvider)
    val currentUser = Firebase.auth.currentUser
    val userDisplayName = personalizationConfig.displayName.ifBlank { currentUser?.displayName.orEmpty() }.takeIf { it.isNotBlank() }
    val userEmail = currentUser?.email
    val userFirstName = remember(userDisplayName, userEmail, isGuestMode) {
        if (personalizationConfig.displayName.isNotBlank()) {
            personalizationConfig.displayName.trim().split(Regex("\\s+")).firstOrNull()?.replaceFirstChar { it.uppercase() }
        } else {
            extractFirstName(userDisplayName, userEmail, isGuestMode)
        }
    }

    val effectiveAgentStatus: AgentStatus = when {
        hasPendingApproval -> AgentStatus.Working(AgentPhase.WAITING_FOR_APPROVAL)
        streamingText != null -> AgentStatus.Working(AgentPhase.WRITING)
        else -> agentStatus
    }

    // Determine whether the chat is in its empty state (no user/agent/command entries and no active missing-key/load-guard cards)
    val isChatEmptyState = remember(entries, streamingText, missingApiKeyPrompt, loadGuardError) {
        streamingText == null &&
            missingApiKeyPrompt == null &&
            loadGuardError == null &&
            entries.none {
                it.type in listOf("user", "agent", "command", "output", "analysis")
            }
    }

    val sidebarComposable: @Composable () -> Unit = {
        NexusSidebar(
            sessions = sessions,
            activeSessionId = activeSessionId,
            isTemporaryChat = isTemporaryChat,
            currentViewMode = viewMode,
            currentCwd = effectiveCwd,
            isGuestMode = isGuestMode,
            userName = userDisplayName,
            userEmail = userEmail,
            avatarBitmap = avatarBitmap,
            isNetworkOnline = isNetworkOnline,
            isSyncingOrRunning = isRunning,
            hasPendingApproval = hasPendingApproval,
            activeDownloadCount = activeDownloads.size,
            themeMode = themeMode,
            onNewChatClick = {
                viewModel.createNewSession()
                viewMode = NexusViewMode.CHAT
                scope.launch { drawerState.close() }
            },
            onTemporaryChatClick = {
                viewModel.toggleTemporaryChat()
                viewMode = NexusViewMode.CHAT
                scope.launch { drawerState.close() }
            },
            onSelectSession = { sessionId ->
                viewModel.selectSession(sessionId)
                viewMode = NexusViewMode.CHAT
                scope.launch { drawerState.close() }
            },
            onRenameSession = viewModel::renameSession,
            onTogglePinSession = viewModel::togglePinSession,
            onExportSession = { _, format ->
                scope.launch { drawerState.close() }
                if (isGuestMode) {
                    triggerGuestSignInUpgrade()
                } else {
                    exportTranscript(
                        context = context,
                        clipboard = clipboard,
                        entries = entries,
                        provider = activeProviderBadge,
                        model = selectedModelId,
                        format = format
                    )
                }
            },
            onDeleteSession = viewModel::deleteSessionWithUndo,
            onClearCurrentSession = {
                viewModel.clearActiveSessionImmediate()
                scope.launch { drawerState.close() }
            },
            onSelectViewMode = { targetMode ->
                viewMode = targetMode
                scope.launch { drawerState.close() }
            },
            onOpenWorkingDirectory = {
                scope.launch { drawerState.close() }
                showDirectoryDialog = true
            },
            onOpenLocalLlmHub = {
                scope.launch { drawerState.close() }
                viewModel.refreshDeviceRecommendations()
                showLocalLlmHubDialog = true
            },
            onOpenSettings = {
                scope.launch { drawerState.close() }
                viewModel.refreshProviderModels(forceNetwork = false)
                showSettingsDialog = true
            },
            onSelectThemeMode = { selectedMode ->
                onSelectThemeMode(selectedMode)
            },
            onSignInOrUpClick = {
                scope.launch { drawerState.close() }
                triggerGuestSignInUpgrade()
            },
            onSignOutClick = {
                scope.launch { drawerState.close() }
                if (isGuestMode) {
                    triggerGuestSignInUpgrade()
                } else {
                    signOutFromNexus(
                        context = context,
                        onSignOutComplete = onSignedOut,
                        scope = scope
                    )
                }
            },
            isSetupNeeded = isSetupNeeded,
            activeBackgroundJobsCount = managedJobs.count { it.state == ManagedJobState.RUNNING || it.state == ManagedJobState.STARTING },
            onOpenExecutionEnvironment = {
                scope.launch { drawerState.close() }
                viewModel.refreshExecutionEnvironment()
                viewModel.runDiagnostics()
                showEnvironmentSetupScreen = true
            },
            onOpenJobsSheet = {
                scope.launch { drawerState.close() }
                showJobsSheet = true
            }
        )
    }

    val mainContentComposable: @Composable (Boolean) -> Unit = { isPermanentRail ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .drawWithContent {
                    drawContent()
                    val overlayColor = previousCanvasOverlayColor
                    val progress = themeRevealProgress.value
                    if (overlayColor != null && progress < 1f) {
                        val origin = Offset(size.width * 0.88f, 36.dp.toPx())
                        val maxRadius = hypot(size.width, size.height) * 1.08f
                        val currentRadius = maxRadius * progress
                        val revealPath = Path().apply {
                            addOval(Rect(center = origin, radius = currentRadius))
                        }
                        clipPath(revealPath, clipOp = ClipOp.Difference) {
                            drawRect(color = overlayColor.copy(alpha = (1f - progress * 0.4f).coerceIn(0f, 1f)))
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NexusAccent.copy(alpha = (1f - progress) * 0.32f),
                                    NexusAccent2.copy(alpha = (1f - progress) * 0.16f)
                                ),
                                center = origin,
                                radius = currentRadius.coerceAtLeast(1f)
                            ),
                            radius = currentRadius,
                            center = origin,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
        ) {
            // 1. Single-Row Editorial Top Bar (never wraps)
            NexusEditorialTopBar(
                isPermanentRail = isPermanentRail,
                isGuestMode = isGuestMode,
                isTemporaryChat = isTemporaryChat,
                status = effectiveAgentStatus,
                hasPendingBadge = hasPendingApproval || activeDownloads.isNotEmpty(),
                userFirstName = userFirstName,
                viewMode = viewMode,
                onOpenDrawer = {
                    scope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
                onNewChatClick = {
                    viewModel.createNewSession()
                    viewMode = NexusViewMode.CHAT
                },
                onCenterPillClick = {
                    val currentErr = effectiveAgentStatus as? AgentStatus.Error
                    if (currentErr?.targetEntryId != null) {
                        val targetIdx = entries.indexOfFirst { it.id == currentErr.targetEntryId }
                        if (targetIdx >= 0) {
                            scope.launch { listState.animateScrollToItem(targetIdx) }
                        } else {
                            viewModel.refreshProviderModels(forceNetwork = false)
                            showSettingsDialog = true
                        }
                    } else {
                        viewModel.refreshProviderModels(forceNetwork = false)
                        showSettingsDialog = true
                    }
                },
                onToggleTemporaryChat = {
                    viewModel.toggleTemporaryChat()
                    viewMode = NexusViewMode.CHAT
                },
                onSwitchBackToChat = {
                    viewMode = NexusViewMode.CHAT
                },
                onExportCurrentChatMarkdown = {
                    if (isGuestMode) {
                        triggerGuestSignInUpgrade()
                    } else {
                        exportTranscript(
                            context = context,
                            clipboard = clipboard,
                            entries = entries,
                            provider = activeProviderBadge,
                            model = selectedModelId,
                            format = "md"
                        )
                    }
                },
                onExportCurrentChatJson = {
                    if (isGuestMode) {
                        triggerGuestSignInUpgrade()
                    } else {
                        exportTranscript(
                            context = context,
                            clipboard = clipboard,
                            entries = entries,
                            provider = activeProviderBadge,
                            model = selectedModelId,
                            format = "json"
                        )
                    }
                },
                onRequestClearChat = {
                    showClearChatConfirmDialog = true
                },
                onOpenExecutionEnvironment = {
                    viewModel.refreshExecutionEnvironment()
                    showEnvironmentSetupScreen = true
                }
            )

            // Subtle Temporary Chat Banner when ON
            AnimatedVisibility(
                visible = isTemporaryChat,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 }
            ) {
                Surface(
                    color = NexusAccent.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, NexusAccent.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("temporary_chat_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IncognitoVectorIcon(
                                tint = NexusAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Temporary chat – not saved or synced",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusAccent,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.exitTemporaryChat() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Limited shell banner when Android toybox shell is active instead of Termux
            if (environmentDescription.isLimitedShell && !dismissLimitedShellBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("limited_shell_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = NexusWarn,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Limited shell: install Termux for git, node, python",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Set up",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusAccent,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.refreshExecutionEnvironment()
                                    showEnvironmentSetupScreen = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("limited_shell_setup_button")
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss limited shell notice",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { dismissLimitedShellBanner = true }
                        )
                    }
                }
            }

            // Reconciled jobs on app start banner ("N jobs are still running in Termux: Manage")
            if (reconciledRunningCount > 0) {
                NexusReconciledJobsBanner(
                    runningCount = reconciledRunningCount,
                    palette = nexusPalette,
                    onManageClick = { showJobsSheet = true },
                    onDismiss = { viewModel.dismissReconciledBanner() }
                )
            }

            // Active background jobs indicator bar with Stop button
            val runningBgJobs = backgroundJobs.filter { it.isRunning }
            if (runningBgJobs.isNotEmpty()) {
                val primaryJob = runningBgJobs.first()
                Surface(
                    color = NexusAccent.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, NexusAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_background_jobs_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NexusAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Job ${primaryJob.jobId}: ${primaryJob.command}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val lastLine = primaryJob.logLines.lastOrNull()
                            if (!lastLine.isNullOrBlank()) {
                                Text(
                                    text = lastLine,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            onClick = { viewModel.stopBackgroundJob(primaryJob.jobId) },
                            shape = RoundedCornerShape(6.dp),
                            color = NexusDanger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("stop_bg_job_bar_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopCircle,
                                    contentDescription = "Stop background job",
                                    tint = NexusDanger,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Stop",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusDanger,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (isAnalyzing || isBenchmarking) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = NexusAccent,
                    trackColor = NexusAccentSoftLight
                )
            }

            // Missing bound project folder recovery banner (Amendment #7)
            if (isBoundProjectMissing && activeProjectSlug.isNotBlank()) {
                Surface(
                    color = NexusWarn.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, NexusWarn.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("missing_project_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Project: $activeProjectSlug (Missing)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusWarn,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Recreate",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.recreateMissingProjectFolder() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("recreate_missing_project_btn")
                        )
                        Text(
                            text = "Switch",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showDirectoryDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("switch_missing_project_btn")
                        )
                        Text(
                            text = "Detach",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.detachSessionProject() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("detach_missing_project_btn")
                        )
                    }
                }
            }

            // One-way mirror status chip bar when Termux home mode is active for a project (Amendment #4)
            if (personalizationConfig.workspaceMode == com.example.domain.WorkspaceMode.TERMUX_HOME && activeProjectSlug.isNotBlank() && !isTemporaryChat) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mirror_status_header_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Project: $activeProjectSlug · Mirror: ${mirrorSyncStatus.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sync now",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.syncProjectMirrorNow() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("header_mirror_sync_now_btn")
                        )
                        Text(
                            text = "Import",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.importFromSharedToTermuxHome() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("header_mirror_import_btn")
                        )
                    }
                }
            }

            // Shared storage install progress banner (Task 5)
            if (!installProgressBanner.isNullOrBlank()) {
                Surface(
                    color = NexusAccent.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, NexusAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shared_storage_install_progress_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NexusAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = installProgressBanner!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Error banner if present
            if (!uiBannerMessage.isNullOrBlank()) {
                Surface(
                    color = NexusDanger.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, NexusDanger.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.dismissBanner() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiBannerMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusDanger,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusDanger
                        )
                    }
                }
            }

            FeedbackHost()

            // 2. Main Content Area (Chat / Terminal CLI / Analytics Dashboard) + Floating Overlays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = entriesState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = NexusAccent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexusDanger
                            )
                        }
                    }
                    is UiState.Success -> {
                        Crossfade(
                            targetState = viewMode,
                            animationSpec = tween(durationMillis = 180),
                            label = "viewModeCrossfade"
                        ) { activeViewMode ->
                            when (activeViewMode) {
                                NexusViewMode.DASHBOARD -> {
                                    CommandAnalyticsDashboard(
                                        entries = state.data,
                                        runtimeLatenciesMs = runtimeLatenciesMs,
                                        isGuestMode = isGuestMode,
                                        onRunCommand = { cmd ->
                                            viewModel.executeDirectShellCommand(cmd)
                                        },
                                        onSwitchToTerminal = { viewMode = NexusViewMode.TERMINAL },
                                        onRequestSignIn = triggerGuestSignInUpgrade
                                    )
                                }
                                NexusViewMode.TERMINAL -> {
                                    TerminalInterface(
                                        entries = state.data,
                                        streamingText = streamingText,
                                        agentStatus = activeSession?.status ?: sessionStatus,
                                        isRunning = isRunning,
                                        activeProviderLabel = activeProviderBadge,
                                        activeModelId = selectedModelId,
                                        currentCwd = effectiveCwd,
                                        inputHistory = settings.inputHistory,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        isDarkTheme = isDarkTheme,
                                        listState = listState,
                                        onApproveCommand = { viewModel.resolveCommandPermission(it, true) },
                                        onDenyCommand = { viewModel.resolveCommandPermission(it, false) },
                                        onAnalyzeWithGemini = { viewModel.analyzeEntryWithGemini(it) },
                                        onRecallHistoryItem = { recalled -> viewModel.updatePromptInput(recalled) },
                                        onExecuteImmediateCommand = { rawCmd -> viewModel.executeDirectShellCommand(rawCmd) },
                                        onOpenDirectoryPicker = { showDirectoryDialog = true },
                                        onRequestAllFilesAccess = requestAllFilesAccess,
                                        onToggleTheme = onToggleTheme
                                    )
                                }
                                NexusViewMode.CHAT -> {
                                    if (isChatEmptyState) {
                                        EmptyState(
                                            firstName = userFirstName,
                                            isGuestMode = isGuestMode,
                                            isTemporaryChat = isTemporaryChat,
                                            enabled = !isRunning,
                                            onSelectSuggestion = { directive ->
                                                viewModel.submitDirective(directive)
                                            },
                                            hasApiKeyOrLocalModel = (missingApiKeyPrompt == null),
                                            hasConfiguredEnvironment = hasAllFilesAccess,
                                            onOpenSettings = { showSettingsDialog = true },
                                            onOpenEnvironmentSetup = { showEnvironmentSetupScreen = true },
                                            onSignIn = triggerGuestSignInUpgrade,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            val turns = remember(state.data, isRunning) {
                                                TurnTimelineBuilder.partitionIntoTurns(state.data, isRunning)
                                            }
                                            LazyColumn(
                                                state = listState,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .widthIn(max = 780.dp)
                                                    .testTag("session_log_list"),
                                                contentPadding = PaddingValues(
                                                    start = 18.dp,
                                                    end = 18.dp,
                                                    top = 12.dp,
                                                    bottom = 24.dp
                                                ),
                                                verticalArrangement = Arrangement.spacedBy(18.dp)
                                            ) {
                                                // Bug #1: "Add API key" card that opens Settings when a key is missing
                                                if (missingApiKeyPrompt != null) {
                                                    item(key = "missing_api_key_card_item") {
                                                        MissingApiKeyCard(
                                                            prompt = missingApiKeyPrompt!!,
                                                            onOpenSettings = {
                                                                viewModel.dismissMissingApiKeyPrompt()
                                                                viewModel.refreshProviderModels(forceNetwork = false)
                                                                showSettingsDialog = true
                                                            },
                                                            onOpenLocalHub = {
                                                                viewModel.dismissMissingApiKeyPrompt()
                                                                viewModel.refreshDeviceRecommendations()
                                                                showLocalLlmHubDialog = true
                                                            },
                                                            onDismiss = { viewModel.dismissMissingApiKeyPrompt() }
                                                        )
                                                    }
                                                }

                                                // Part C: Load Guard card when available RAM < modelSize * 1.25
                                                if (loadGuardError != null) {
                                                    item(key = "load_guard_error_card_item") {
                                                        LoadGuardErrorCard(
                                                            failure = loadGuardError!!,
                                                            onOpenLocalHub = {
                                                                viewModel.dismissLoadGuardError()
                                                                viewModel.refreshDeviceRecommendations()
                                                                showLocalLlmHubDialog = true
                                                            },
                                                            onDismiss = { viewModel.dismissLoadGuardError() }
                                                        )
                                                    }
                                                }

                                                // Non-blocking cloud sync notice banner ("Signed in, but sync failed · Retry")
                                                if (cloudSyncIssue != null) {
                                                    item(key = "cloud_sync_issue_banner") {
                                                        Surface(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = NexusWarn.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.35f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = "Signed in, but sync failed",
                                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                TextButton(
                                                                    onClick = { viewModel.retryCloudSync() },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Retry",
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                        color = NexusAccent
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }


                                                items(
                                                    items = turns,
                                                    key = { it.id }
                                                ) { turn ->
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        // 1. User Message (top-right pill bubble)
                                                        if (turn.userEntry != null) {
                                                            TranscriptEntryItem(
                                                                entry = turn.userEntry,
                                                                activeProviderLabel = activeProviderBadge,
                                                                activeModelId = selectedModelId,
                                                                isDarkTheme = isDarkTheme,
                                                                feedbackState = messageFeedback[turn.userEntry.id],
                                                                onApproveCommand = { viewModel.resolveCommandPermission(it, true) },
                                                                onDenyCommand = { viewModel.resolveCommandPermission(it, false) },
                                                                onAnalyzeWithGemini = { viewModel.analyzeEntryWithGemini(it) },
                                                                onSwitchToTerminal = { viewMode = NexusViewMode.TERMINAL },
                                                                onRegenerate = { viewModel.regenerateFromEntry(it) },
                                                                onEditAndResubmit = { targetEntry, editedText ->
                                                                    viewModel.editAndResubmitPrompt(targetEntry, editedText)
                                                                }
                                                            )
                                                        }

                                                        // 2. Assistant Turn Timeline Blocks (Thought Process, Tool Group, Text, Approvals, Notices)
                                                        val timelineBlocks = remember(turn.assistantEntries, turn.isLiveTurn, streamingText) {
                                                            TurnTimelineBuilder.build(
                                                                entries = turn.assistantEntries,
                                                                activeStreamingText = if (turn.isLiveTurn) streamingText.orEmpty() else "",
                                                                isTurnActive = turn.isLiveTurn
                                                            )
                                                        }

                                                        timelineBlocks.forEach { block ->
                                                            when (block) {
                                                                is TimelineBlock.ThinkingBlock -> {
                                                                    ThinkingRow(
                                                                        thinkingBlock = block,
                                                                        visibilityPreference = personalizationConfig.thoughtProcessVisibility
                                                                    )
                                                                }
                                                                is TimelineBlock.TextBlock -> {
                                                                    MarkdownMessageContent(
                                                                        markdown = block.markdownText,
                                                                        isStreaming = turn.isLiveTurn && block.blockId == "live_text"
                                                                    )
                                                                }
                                                                is TimelineBlock.ToolGroup -> {
                                                                    ToolGroupRow(
                                                                        toolGroup = block,
                                                                        expandMode = personalizationConfig.toolGroupExpandMode,
                                                                        onAuditCommand = { cmd ->
                                                                            turn.assistantEntries.firstOrNull { it.command == cmd }?.let {
                                                                                viewModel.analyzeEntryWithGemini(it)
                                                                            }
                                                                        },
                                                                        onExplainOutput = { cmd ->
                                                                            turn.assistantEntries.firstOrNull { it.command == cmd }?.let {
                                                                                viewModel.analyzeEntryWithGemini(it)
                                                                            }
                                                                        },
                                                                        onOpenInTerminal = { viewMode = NexusViewMode.TERMINAL }
                                                                    )
                                                                }
                                                                is TimelineBlock.ApprovalBlock -> {
                                                                    TranscriptEntryItem(
                                                                        entry = block.commandEntry,
                                                                        activeProviderLabel = activeProviderBadge,
                                                                        activeModelId = selectedModelId,
                                                                        isDarkTheme = isDarkTheme,
                                                                        onApproveCommand = { viewModel.resolveCommandPermission(it, true) },
                                                                        onDenyCommand = { viewModel.resolveCommandPermission(it, false) },
                                                                        onAnalyzeWithGemini = { viewModel.analyzeEntryWithGemini(it) },
                                                                        onSwitchToTerminal = { viewMode = NexusViewMode.TERMINAL }
                                                                    )
                                                                }
                                                                is TimelineBlock.NoticeBlock -> {
                                                                    TranscriptEntryItem(
                                                                        entry = block.noticeEntry,
                                                                        activeProviderLabel = activeProviderBadge,
                                                                        activeModelId = selectedModelId,
                                                                        isDarkTheme = isDarkTheme,
                                                                        onApproveCommand = { viewModel.resolveCommandPermission(it, true) },
                                                                        onDenyCommand = { viewModel.resolveCommandPermission(it, false) },
                                                                        onAnalyzeWithGemini = { viewModel.analyzeEntryWithGemini(it) },
                                                                        onSwitchToTerminal = { viewMode = NexusViewMode.TERMINAL },
                                                                        onRetryAction = { viewModel.retryLastFailedAction() },
                                                                        onGrantFileAccess = requestAllFilesAccess,
                                                                        onRequestSignIn = triggerGuestSignInUpgrade,
                                                                        onOpenEnvironmentSetup = {
                                                                            viewModel.refreshExecutionEnvironment()
                                                                            viewModel.runDiagnostics()
                                                                            showEnvironmentSetupScreen = true
                                                                        },
                                                                        onFallbackLimitedShell = { cmd -> viewModel.executeFallbackInLimitedShell(cmd) },
                                                                        onRerunAsBackgroundJob = { cmd -> viewModel.startBackgroundJob(cmd) },
                                                                        onConfirmProjectSlug = { slug, bindExisting -> viewModel.confirmPendingProjectProposal(slug, bindExisting) }
                                                                    )
                                                                }
                                                                is TimelineBlock.ErrorBlock -> {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = NexusDanger.copy(alpha = 0.08f),
                                                                        border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.35f)),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Column(
                                                                            modifier = Modifier.padding(12.dp),
                                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = block.title,
                                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                                                color = NexusDanger
                                                                            )
                                                                            Text(
                                                                                text = block.message,
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = MaterialTheme.colorScheme.onSurface
                                                                            )
                                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                                Button(
                                                                                    onClick = { viewModel.retryLastFailedAction() },
                                                                                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger)
                                                                                ) {
                                                                                    Text("Retry", color = Color.White)
                                                                                }
                                                                                OutlinedButton(
                                                                                    onClick = { viewModel.createNewSession() }
                                                                                ) {
                                                                                    Text("New chat")
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // 3. Animated Working Indicator while live turn has no visible text streaming yet
                                                        if (turn.isLiveTurn && streamingText.isNullOrBlank()) {
                                                            AgentWorkingIndicator(
                                                                isLoadingModel = (effectiveAgentStatus as? AgentStatus.Working)?.phase == AgentPhase.LOADING_MODEL
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Compact Dismissible Snackbars / Toasts (Top-Aligned inside Content Area, never heavy cards)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1) Compact Guest Prompt Counter Toast (dismissible per prompt count)
                    if (isGuestMode && guestToastDismissedForCount != guestDirectivesUsed && guestDirectivesUsed > 0) {
                        CompactGuestLimitToast(
                            remainingPrompts = remainingGuestPrompts,
                            maxPrompts = NexusViewModel.GUEST_MAX_DIRECTIVES,
                            onSignInClick = triggerGuestSignInUpgrade,
                            onDismiss = { guestToastDismissedForCount = guestDirectivesUsed }
                        )
                    }

                    // 2) Live Notification Toast (Approval Needed, Download Complete, Deleted Session Undo, Edit Truncate Undo, Guest Limit)
                    if (liveNotificationBanner != null) {
                        CompactNotificationToast(
                            event = liveNotificationBanner!!,
                            onApprove = { entryId ->
                                entries.find { it.id == entryId }?.let { entry ->
                                    viewModel.resolveCommandPermission(entry, true)
                                }
                                viewModel.dismissLiveNotificationBanner()
                            },
                            onDeny = { entryId ->
                                entries.find { it.id == entryId }?.let { entry ->
                                    viewModel.resolveCommandPermission(entry, false)
                                }
                                viewModel.dismissLiveNotificationBanner()
                            },
                            onUndoDelete = {
                                viewModel.undoDeleteSession()
                            },
                            onUndoEditTruncate = {
                                viewModel.undoEditAndResubmit()
                            },
                            onOpenHub = {
                                viewModel.refreshDeviceRecommendations()
                                showLocalLlmHubDialog = true
                                viewModel.dismissLiveNotificationBanner()
                            },
                            onSignIn = {
                                viewModel.dismissLiveNotificationBanner()
                                triggerGuestSignInUpgrade()
                            },
                            onDismiss = { viewModel.dismissLiveNotificationBanner() }
                        )
                    }

                    // 3) Compact Active Model Download Toast
                    if (activeDownloads.isNotEmpty()) {
                        CompactDownloadProgressToast(
                            downloads = activeDownloads,
                            onOpenLocalHub = {
                                viewModel.refreshDeviceRecommendations()
                                showLocalLlmHubDialog = true
                            }
                        )
                    }
                }

                // Bottom-Right Floating Controls: "Track in Terminal" pill (when agent active) + "Jump to latest" FAB
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewMode == NexusViewMode.CHAT && (isRunning || hasPendingApproval)) {
                        Surface(
                            onClick = { viewMode = NexusViewMode.TERMINAL },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.45f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.testTag("floating_track_in_terminal_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusDotIndicator(agentStatus = effectiveAgentStatus)
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Track in Terminal",
                                    tint = NexusAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Terminal",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showJumpToLatest && viewMode == NexusViewMode.CHAT,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150))
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val total = entries.size + (if (streamingText != null) 1 else 0)
                                    if (total > 0) {
                                        listState.animateScrollToItem(total - 1)
                                    }
                                }
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .testTag("jump_to_latest_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Jump to latest message",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. Floating Bottom Composer (with active background jobs status chip)
            val isOverlayOpen = showSettingsDialog || showLocalLlmHubDialog || showSessionsDialog ||
                showDirectoryDialog || showEnvironmentSetupScreen || showJobsSheet
            if (viewMode != NexusViewMode.DASHBOARD && !isOverlayOpen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NexusActiveJobsStatusChip(
                        jobs = managedJobs,
                        palette = nexusPalette,
                        onOpenJobsSheet = { showJobsSheet = true },
                        onOpenPreviewPort = { port, title ->
                            previewPort = port
                            previewTitle = title
                        },
                        modifier = Modifier.widthIn(max = 780.dp)
                    )
                    ChatComposer(
                        promptText = promptInput,
                        activeModelId = selectedModelId,
                        currentCwd = effectiveCwd,
                        isDirectShellInput = isDirectShellInput,
                        isRunning = isRunning,
                        isAnalyzing = isAnalyzing,
                        autocompleteSuggestions = autocompleteSuggestions,
                        onPromptChange = viewModel::updatePromptInput,
                        onSelectSuggestion = { suggestion ->
                            viewModel.updatePromptInput(suggestion.text)
                        },
                        onToggleInputMode = { isDirectShellInput = !isDirectShellInput },
                        onOpenModelSwitcher = {
                            viewModel.refreshProviderModels(forceNetwork = false)
                            showModelSwitcherSheet = true
                        },
                        onOpenDirectoryPicker = { showDirectoryDialog = true },
                        onHistoryPrev = viewModel::recallPreviousDirective,
                        onHistoryNext = viewModel::recallNextDirective,
                        onEnhancePrompt = viewModel::enhancePromptWithFlashLite,
                        onExecuteQuickCommand = { viewModel.executeQuickCommand() },
                        onSubmit = {
                            if (isDirectShellInput) {
                                viewModel.executeDirectShellCommand(promptInput)
                            } else {
                                viewModel.submitDirective()
                            }
                        },
                        onStop = viewModel::stopRunningAgent,
                        modifier = Modifier.widthIn(max = 780.dp)
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        val isExpandedScreen = maxWidth >= 840.dp

        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                ) {
                    sidebarComposable()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    mainContentComposable(true)
                }
            }
        } else {
            val drawerWidth = minOf(320.dp, maxWidth * 0.85f)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerContentColor = MaterialTheme.colorScheme.onSurface,
                        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.width(drawerWidth)
                    ) {
                        sidebarComposable()
                    }
                }
            ) {
                mainContentComposable(false)
            }
        }
    }

    // Model Switcher Modal Bottom Sheet
    if (showModelSwitcherSheet) {
        ModelSwitcherSheet(
            activeProvider = selectedProvider,
            activeModelId = selectedModelId,
            savedApiKeys = savedApiKeys,
            downloadedLocalModels = downloadedLocalModels,
            modelBenchmarks = modelBenchmarks,
            geminiModels = geminiModels,
            anthropicModels = anthropicModels,
            openAiModels = openAiModels,
            showAllOpenAiModels = showAllOpenAiModels,
            isFetchingModels = isFetchingModels,
            onRefreshProviderModels = { prov -> viewModel.refreshProviderModels(provider = prov, forceNetwork = true) },
            onToggleShowAllOpenAi = viewModel::toggleShowAllOpenAiModels,
            onSelectModel = { provider, modelId ->
                viewModel.selectProviderAndModel(provider, modelId)
                showModelSwitcherSheet = false
            },
            onOpenApiKeySettings = { provider, modelId ->
                if (modelId.isNotBlank()) {
                    viewModel.selectProviderAndModel(provider, modelId)
                }
                showModelSwitcherSheet = false
                showSettingsDialog = true
            },
            onOpenLocalLlmHub = {
                showModelSwitcherSheet = false
                viewModel.refreshDeviceRecommendations()
                showLocalLlmHubDialog = true
            },
            onDismiss = { showModelSwitcherSheet = false }
        )
    }

    if (showDirectoryDialog) {
        DirectoryChooserDialog(
            currentCwd = effectiveCwd,
            presets = viewModel.getDirectoryPresets(),
            hasAllFilesAccess = hasAllFilesAccess,
            listSubdirectories = viewModel::listSubdirectories,
            onSelectDirectory = viewModel::changeWorkingDirectory,
            onLaunchSystemFolderPicker = { documentTreeLauncher.launch(null) },
            onRequestAllFilesAccess = requestAllFilesAccess,
            onDismiss = { showDirectoryDialog = false }
        )
    }

    if (showSettingsDialog) {
        NexusSettingsDialog(
            settings = settings.copy(
                provider = selectedProvider,
                selectedModel = selectedModelId
            ),
            personalizationConfig = personalizationConfig,
            avatarBitmap = avatarBitmap,
            remoteBridgeUrl = remoteBridgeUrl,
            currentWorkingDir = effectiveCwd,
            hasAllFilesAccess = hasAllFilesAccess,
            isGuestMode = isGuestMode,
            isNetworkOnline = isNetworkOnline,
            userEmail = if (isGuestMode) null else userEmail,
            userDisplayName = userDisplayName,
            remainingGuestPrompts = remainingGuestPrompts,
            themeMode = themeMode,
            downloadedLocalModelsCount = downloadedLocalModels.size,
            totalDownloadedBytes = viewModel.getTotalDownloadedBytes(),
            savedKeysMap = savedApiKeys,
            activePreset = inferencePreset,
            activeLoadedModel = activeLoadedLocalModel,
            isBenchmarking = isBenchmarking,
            availableModelOptions = allDynamicModelOptions,
            isFetchingModels = isFetchingModels,
            keyTestStatuses = keyTestStatuses,
            huggingFaceToken = viewModel.getHuggingFaceToken(),
            showAllOpenAiModels = showAllOpenAiModels,
            deviceProfile = deviceProfile,
            engineInfo = engineInfo,
            onSelectThemeMode = onSelectThemeMode,
            onSelectFontSize = viewModel::updateFontSize,
            onSelectResponseStyle = viewModel::updateResponseStyle,
            onSelectPersonaPreset = viewModel::selectPersonaPreset,
            onSaveCustomPersona = viewModel::saveCustomPersona,
            onDeleteCustomPersona = viewModel::deleteCustomPersona,
            onUpdatePersonalization = viewModel::updatePersonalization,
            onToggleSkill = viewModel::toggleSkill,
            onSaveCustomSkill = viewModel::saveCustomSkill,
            onDeleteCustomSkill = viewModel::deleteCustomSkill,
            onUpdateSkillsTokenBudget = viewModel::updateSkillsTokenBudget,
            onImportSkillsContent = viewModel::importSkillFromMarkdownOrJson,
            onExportSkillsJson = viewModel::exportSkillsAsJson,
            onOpenDirectoryChooser = { showDirectoryDialog = true },
            onRequestAllFilesAccess = requestAllFilesAccess,
            onSelectRecentWorkspace = { path -> viewModel.changeWorkingDirectory(path, createIfMissing = true) },
            onRemoveRecentWorkspace = viewModel::removeRecentWorkspace,
            onUpdateDisplayName = viewModel::updateDisplayName,
            onSaveCroppedAvatar = viewModel::saveCroppedAvatarBitmap,
            onRemoveAvatar = viewModel::removeAvatar,
            onSelectProviderAndModel = viewModel::selectProviderAndModel,
            onSaveProviderApiKey = viewModel::saveProviderApiKey,
            onUpdateInferenceDefaults = viewModel::updateInferenceDefaults,
            onSelectPreset = viewModel::setInferencePreset,
            onReRunBenchmark = { viewModel.reRunBenchmark() },
            onUnloadModel = viewModel::unloadLocalModel,
            onTestApiKey = { prov, keyOverride -> viewModel.testProviderApiKey(prov, keyOverride.orEmpty()) },
            onRefreshModels = { prov -> viewModel.refreshProviderModels(provider = prov, forceNetwork = true) },
            onToggleShowAllOpenAi = viewModel::toggleShowAllOpenAiModels,
            onSaveHuggingFaceToken = viewModel::saveHuggingFaceToken,
            onOpenLocalLlmHub = {
                showSettingsDialog = false
                viewModel.refreshDeviceRecommendations()
                showLocalLlmHubDialog = true
            },
            onExportAllChatsJson = {
                if (isGuestMode) {
                    showSettingsDialog = false
                    triggerGuestSignInUpgrade()
                } else {
                    exportTranscript(
                        context = context,
                        clipboard = clipboard,
                        entries = entries,
                        provider = activeProviderBadge,
                        model = selectedModelId,
                        format = "json"
                    )
                }
            },
            onExportAllChatsMarkdown = {
                if (isGuestMode) {
                    showSettingsDialog = false
                    triggerGuestSignInUpgrade()
                } else {
                    exportTranscript(
                        context = context,
                        clipboard = clipboard,
                        entries = entries,
                        provider = activeProviderBadge,
                        model = selectedModelId,
                        format = "md"
                    )
                }
            },
            onClearAllChatHistory = viewModel::clearAllChatHistory,
            onClearAllSavedApiKeys = viewModel::clearAllSavedApiKeys,
            onDeleteAccountAndCloudData = {
                viewModel.deleteAccountAndCloudData {
                    showSettingsDialog = false
                    signOutFromNexus(
                        context = context,
                        onSignOutComplete = onSignedOut,
                        scope = scope
                    )
                }
            },
            onResetSettings = viewModel::resetAllSettingsToDefaults,
            onExportFullSettingsJson = { viewModel.exportFullSettingsJson(themeMode.id) },
            onImportFullSettingsJson = viewModel::importFullSettingsJson,
            executionEnvironmentSummary = environmentDescription.summaryLine,
            onOpenExecutionEnvironment = {
                viewModel.refreshExecutionEnvironment()
                showEnvironmentSetupScreen = true
            },
            onSignOut = {
                showSettingsDialog = false
                if (isGuestMode) {
                    triggerGuestSignInUpgrade()
                } else {
                    signOutFromNexus(
                        context = context,
                        onSignOutComplete = onSignedOut,
                        scope = scope
                    )
                }
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showEnvironmentSetupScreen) {
        EnvironmentSetupScreen(
            activeBackendType = personalizationConfig.executionBackend,
            workspaceMode = personalizationConfig.workspaceMode,
            environmentDescription = environmentDescription,
            hasAllFilesAccess = hasAllFilesAccess,
            isProbing = isProbingEnvironment,
            connectionTestResult = connectionTestResult,
            backgroundJobs = backgroundJobs,
            onSelectBackendType = viewModel::selectExecutionBackend,
            onSelectWorkspaceMode = viewModel::selectWorkspaceMode,
            onRefreshEnvironment = viewModel::refreshExecutionEnvironment,
            onRunTestConnection = viewModel::runExecutionBackendConnectionTest,
            onRequestInstallToolchain = {
                showEnvironmentSetupScreen = false
                showSettingsDialog = false
                viewMode = NexusViewMode.CHAT
                viewModel.proposeInstallToolchain()
            },
            onRequestAllFilesAccess = requestAllFilesAccess,
            onStopBackgroundJob = viewModel::stopBackgroundJob,
            onDismiss = { showEnvironmentSetupScreen = false },
            diagnosticsReport = diagnosticsReport,
            selfTestReport = selfTestReport,
            isRunningSelfTest = isRunningSelfTest,
            projectGuide = projectGuide,
            wizardState = wizardState,
            onRunDiagnostics = { viewModel.runDiagnostics(forceRefresh = true) },
            onRunSelfTest = viewModel::runEndToEndSelfTest,
            onRetryPackagesIndividually = viewModel::retryPackagesIndividually,
            onRunRecipeViaAgent = { recipeCmd ->
                showEnvironmentSetupScreen = false
                viewMode = NexusViewMode.CHAT
                viewModel.executeDirectShellCommand(recipeCmd)
            },
            onVerifyRecipeCommand = { verifyCmd ->
                viewModel.verifyProjectRecipe("", verifyCmd)
            },
            onToggleKeepTermuxAwake = viewModel::toggleKeepTermuxAwake,
            onMarkWizardComplete = viewModel::markWizardComplete,
            onSkipWizard = viewModel::skipWizard,
            mirrorGitFolder = personalizationConfig.mirrorGitFolder,
            mirrorBuildOutputs = personalizationConfig.mirrorBuildOutputs,
            mirrorDeletions = personalizationConfig.mirrorDeletions,
            mirrorSyncStatusLabel = mirrorSyncStatus.label,
            onUpdateMirrorConfig = { git, build, del ->
                viewModel.updateMirrorConfig(git, build, del)
            },
            onSyncMirrorNow = { viewModel.syncProjectMirrorNow() },
            onImportMirrorFromShared = { viewModel.importFromSharedToTermuxHome() }
        )
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Clear current chat?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will clear all messages and tool executions in the current chat session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmDialog = false
                        viewModel.clearActiveSessionImmediate()
                    },
                    modifier = Modifier.testTag("confirm_clear_current_chat_button")
                ) {
                    Text(
                        text = "Clear chat",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusDanger,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        )
    }

    if (showLocalLlmHubDialog) {
        LocalLlmHubDialog(
            telemetry = remember(deviceProfile) { viewModel.inspectDeviceHardware() },
            deviceProfile = deviceProfile,
            recommendationReport = recommendationReport,
            activePreset = inferencePreset,
            benchmarks = modelBenchmarks,
            activeLoadedModel = activeLoadedLocalModel,
            isBenchmarking = isBenchmarking,
            downloadedModels = downloadedLocalModels,
            downloadStates = localDownloadStates,
            isNetworkOnline = isNetworkOnline,
            activeProvider = selectedProvider,
            activeModelId = selectedModelId,
            wifiOnlyDownloads = wifiOnlyDownloads,
            totalDownloadedBytes = viewModel.getTotalDownloadedBytes(),
            engineInfo = engineInfo,
            pendingMobileDataSpec = pendingMobileDataDownloadSpec,
            getDownloadedFileSizeBytes = viewModel::getDownloadedFileSizeBytes,
            onToggleWifiOnly = viewModel::setWifiOnlyDownloads,
            onConfirmMobileDataDownload = viewModel::confirmMobileDataDownload,
            onDismissMobileDataPrompt = viewModel::dismissMobileDataPrompt,
            onSelectPreset = viewModel::setInferencePreset,
            onReRunBenchmark = { modelId -> viewModel.reRunBenchmark(modelId) },
            onUnloadModel = viewModel::unloadLocalModel,
            onCheckCapability = viewModel::checkDeviceCapabilityForModel,
            onStartDownload = { spec -> viewModel.startLocalModelDownload(spec) },
            onPauseDownload = viewModel::pauseLocalModelDownload,
            onResumeDownload = viewModel::resumeLocalModelDownload,
            onCancelDownload = viewModel::cancelLocalModelDownload,
            onDeleteModel = viewModel::deleteLocalModel,
            onActivateLocalModel = { spec ->
                viewModel.activateLocalModel(spec)
                showLocalLlmHubDialog = false
            },
            onDismiss = { showLocalLlmHubDialog = false }
        )
    }

    if (showSessionsDialog) {
        SessionsSwitcherDialog(
            sessions = sessions,
            activeSessionId = activeSessionId,
            onSelectSession = viewModel::selectSession,
            onCreateNewSession = viewModel::createNewSession,
            onDismiss = { showSessionsDialog = false }
        )
    }

    if (activeAnalysis != null) {
        AiAnalysisModal(
            analysis = activeAnalysis!!,
            onRunSuggestedCommand = { cmd ->
                viewModel.submitDirective("Run `$cmd` and explain the result.")
            },
            onDismiss = viewModel::dismissAnalysis
        )
    }

    if (showJobsSheet) {
        NexusJobsBottomSheet(
            jobs = managedJobs,
            palette = nexusPalette,
            onDismiss = { showJobsSheet = false },
            onStopJob = { viewModel.stopBackgroundJob(it) },
            onStopAllJobs = { viewModel.stopAllBackgroundJobs() },
            onRestartJob = { viewModel.restartBackgroundJob(it) },
            onRefreshJobs = { viewModel.pollJobs() },
            onOpenPreview = { port, title ->
                previewPort = port
                previewTitle = title
            }
        )
    }

    if (previewPort != null) {
        LoopbackWebViewPreviewSheet(
            port = previewPort!!,
            commandTitle = previewTitle,
            palette = nexusPalette,
            onDismiss = { previewPort = null }
        )
    }

    if (showGuestChatLimitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissGuestChatLimitDialog() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Guest mode limit",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Guest mode supports 1 active chat session. Sign in with Google to create unlimited chats, sync across devices, and keep your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissGuestChatLimitDialog()
                        triggerGuestSignInUpgrade()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusAccent)
                ) {
                    Text("Sign in with Google", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissGuestChatLimitDialog() }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * 1) SINGLE-ROW TOP BAR (never wraps)
 * - Left: Drawer toggle + New Chat icon
 * - Center: High-visibility AgentStatus center pill (Ready / Thinking / Writing / Running command / Error)
 * - Right: Temporary Chat toggle icon (incognito style)
 */
@Composable
private fun NexusEditorialTopBar(
    isPermanentRail: Boolean,
    isGuestMode: Boolean,
    isTemporaryChat: Boolean,
    status: AgentStatus,
    hasPendingBadge: Boolean,
    userFirstName: String?,
    viewMode: NexusViewMode,
    onOpenDrawer: () -> Unit,
    onNewChatClick: () -> Unit = {},
    onCenterPillClick: () -> Unit,
    onToggleTemporaryChat: () -> Unit,
    onSwitchBackToChat: () -> Unit,
    onExportCurrentChatMarkdown: () -> Unit = {},
    onExportCurrentChatJson: () -> Unit = {},
    onRequestClearChat: () -> Unit = {},
    onOpenExecutionEnvironment: () -> Unit = {}
) {
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    val topBarContainerColor = if (isTemporaryChat) {
        NexusAccent.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(topBarContainerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("nexus_editorial_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger button + New Chat button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isPermanentRail) {
                Surface(
                    onClick = onOpenDrawer,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("open_sidebar_drawer_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open navigation sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        if (hasPendingBadge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 7.dp, end = 7.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(NexusWarn)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onNewChatClick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("top_bar_new_chat_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "New chat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (viewMode != NexusViewMode.CHAT) {
                Surface(
                    onClick = onSwitchBackToChat,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Back to Chat",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Center: High-visibility AgentStatus center pill
        Surface(
            onClick = onCenterPillClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp)
                .height(38.dp)
                .testTag("auth_status_indicator_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusDotIndicator(agentStatus = status)

                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Temporary Chat toggle icon + Chat top-bar overflow menu (Export MD/JSON, Execution Env, Clear chat)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = onToggleTemporaryChat,
                shape = RoundedCornerShape(12.dp),
                color = if (isTemporaryChat) {
                    NexusAccent.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isTemporaryChat) NexusAccent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("temporary_chat_toggle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IncognitoVectorIcon(
                        tint = if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                Surface(
                    onClick = { showTopOverflowMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("chat_top_bar_overflow_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Chat actions menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showTopOverflowMenu,
                    onDismissRequest = { showTopOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export Markdown", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showTopOverflowMenu = false
                            onExportCurrentChatMarkdown()
                        },
                        modifier = Modifier.testTag("top_menu_export_md")
                    )
                    DropdownMenuItem(
                        text = { Text("Export JSON", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showTopOverflowMenu = false
                            onExportCurrentChatJson()
                        },
                        modifier = Modifier.testTag("top_menu_export_json")
                    )
                    DropdownMenuItem(
                        text = { Text("Execution environment", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showTopOverflowMenu = false
                            onOpenExecutionEnvironment()
                        },
                        modifier = Modifier.testTag("top_menu_execution_env")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Clear chat",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = NexusDanger
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = NexusDanger,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showTopOverflowMenu = false
                            onRequestClearChat()
                        },
                        modifier = Modifier.testTag("top_menu_clear_chat")
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGuestLimitToast(
    remainingPrompts: Int,
    maxPrompts: Int,
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = Modifier.testTag("guest_mode_limited_banner")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PersonOutline,
                contentDescription = "Guest prompts remaining",
                tint = NexusWarn,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$remainingPrompts/$maxPrompts prompts left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Sign in",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                ),
                color = NexusAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onSignInClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("guest_banner_google_signin_button")
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss guest notice",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactNotificationToast(
    event: LiveNotificationEvent,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onUndoEditTruncate: () -> Unit = {},
    onOpenHub: () -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = when (event.kind) {
        "approval" -> NexusWarn
        "download_done" -> NexusOk
        "guest_limit" -> NexusDanger
        else -> NexusAccent
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_in_app_notification_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when (event.kind) {
                    "download_done" -> Icons.Default.CheckCircle
                    else -> Icons.Default.NotificationsActive
                },
                contentDescription = event.title,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (event.kind) {
                "approval" -> {
                    val entryId = event.entryId
                    if (entryId != null) {
                        Text(
                            text = "Approve",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusOk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onApprove(entryId) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Deny",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusDanger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onDeny(entryId) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                "deleted_undo" -> {
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        color = NexusAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onUndoDelete)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("undo_delete_session_button")
                    )
                }
                "edit_truncate_undo" -> {
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        color = NexusAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onUndoEditTruncate)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("undo_edit_resubmit_button")
                    )
                }
                "download_done" -> {
                    Text(
                        text = "Open Hub",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusOk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenHub)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                "guest_limit" -> {
                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onSignIn)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notification",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactDownloadProgressToast(
    downloads: List<LocalModelDownloadState>,
    onOpenLocalHub: () -> Unit
) {
    val primary = downloads.first()
    val pct = (primary.progressFraction * 100).toInt().coerceIn(0, 100)
    val statusBadge = when {
        primary.isPaused -> "Paused"
        primary.isWaitingForNetwork -> "Waiting for network"
        else -> "$pct%"
    }

    Surface(
        onClick = onOpenLocalHub,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_download_progress_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Local LLM Download",
                        tint = NexusAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Downloading ${primary.modelId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$statusBadge · ${primary.downloadedMb}/${primary.totalMb} MB",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { primary.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = NexusAccent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun exportTranscript(
    context: Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    entries: List<TranscriptEntry>,
    provider: String,
    model: String,
    format: String
) {
    if (entries.isEmpty()) return
    val content = if (format == "json") {
        NexusToolAndRedactor.exportToJson(entries, provider, model)
    } else {
        NexusToolAndRedactor.exportToMarkdown(entries, provider, model)
    }
    clipboard.setText(AnnotatedString(content))
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = if (format == "json") "application/json" else "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, "Zelkai Session Export ($format)")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export Nexus Session"))
}
