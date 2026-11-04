package com.example.ui.nexus

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NexusSession
import com.example.domain.NexusToolAndRedactor
import com.example.ui.auth.NexusLogoIcon
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusThemeMode
import com.example.ui.theme.NexusWarn
import java.util.Calendar

private data class SessionDateSection(
    val title: String,
    val sessions: List<NexusSession>
)

private fun groupSessionsByDate(sessions: List<NexusSession>): List<SessionDateSection> {
    val visible = sessions.filter { !it.isTemporary && it.id != "temporary_chat_ephemeral" }
    if (visible.isEmpty()) return emptyList()

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfTodayMs = cal.timeInMillis
    val startOfYesterdayMs = startOfTodayMs - 24L * 3600_000L
    val startOf7DaysMs = startOfTodayMs - 7L * 24L * 3600_000L

    val pinned = visible.filter { it.isPinned }
    val unpinned = visible.filter { !it.isPinned }

    val today = mutableListOf<NexusSession>()
    val yesterday = mutableListOf<NexusSession>()
    val prev7Days = mutableListOf<NexusSession>()
    val older = mutableListOf<NexusSession>()

    for (sess in unpinned) {
        val tsSec = sess.updatedAt?.seconds ?: sess.createdAt?.seconds
        val tsMs = if (tsSec != null && tsSec > 0L) tsSec * 1000L else startOfTodayMs
        when {
            tsMs >= startOfTodayMs -> today.add(sess)
            tsMs >= startOfYesterdayMs -> yesterday.add(sess)
            tsMs >= startOf7DaysMs -> prev7Days.add(sess)
            else -> older.add(sess)
        }
    }

    val sections = mutableListOf<SessionDateSection>()
    if (pinned.isNotEmpty()) sections.add(SessionDateSection("Pinned", pinned))
    if (today.isNotEmpty()) sections.add(SessionDateSection("Today", today))
    if (yesterday.isNotEmpty()) sections.add(SessionDateSection("Yesterday", yesterday))
    if (prev7Days.isNotEmpty()) sections.add(SessionDateSection("Previous 7 days", prev7Days))
    if (older.isNotEmpty()) sections.add(SessionDateSection("Older", older))
    return sections
}

/**
 * Custom vector Incognito / Temporary Chat icon (hat brim + crown + eyeglasses) matching reference UI.
 */
@Composable
fun IncognitoVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.65.dp.toPx()

        // Hat crown (trapezoid dome)
        val crownPath = Path().apply {
            moveTo(w * 0.24f, h * 0.44f)
            lineTo(w * 0.33f, h * 0.17f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.67f, h * 0.17f)
            lineTo(w * 0.76f, h * 0.44f)
        }
        drawPath(
            path = crownPath,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Hat brim line
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.46f),
            end = Offset(w * 0.88f, h * 0.46f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Left & right round glasses lenses
        val lensRadius = w * 0.145f
        val lensY = h * 0.72f
        val leftLensX = w * 0.31f
        val rightLensX = w * 0.69f

        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(leftLensX, lensY),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(rightLensX, lensY),
            style = Stroke(width = stroke)
        )

        // Bridge between lenses
        drawLine(
            color = tint,
            start = Offset(leftLensX + lensRadius, lensY),
            end = Offset(rightLensX - lensRadius, lensY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusSidebar(
    sessions: List<NexusSession>,
    activeSessionId: String,
    isTemporaryChat: Boolean,
    currentViewMode: NexusViewMode,
    currentCwd: String,
    isGuestMode: Boolean,
    userName: String?,
    userEmail: String?,
    avatarBitmap: Bitmap? = null,
    isNetworkOnline: Boolean,
    isSyncingOrRunning: Boolean,
    hasPendingApproval: Boolean,
    activeDownloadCount: Int,
    themeMode: NexusThemeMode,
    onNewChatClick: () -> Unit,
    onTemporaryChatClick: () -> Unit,
    onSelectSession: (String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onTogglePinSession: (sessionId: String) -> Unit,
    onExportSession: (sessionId: String, format: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onClearCurrentSession: () -> Unit,
    onSelectViewMode: (NexusViewMode) -> Unit,
    onOpenWorkingDirectory: () -> Unit,
    onOpenLocalLlmHub: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectThemeMode: (NexusThemeMode) -> Unit,
    onSignInOrUpClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSetupNeeded: Boolean = false,
    activeBackgroundJobsCount: Int = 0,
    onOpenExecutionEnvironment: () -> Unit = {},
    onOpenJobsSheet: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingSessionId by remember { mutableStateOf<String?>(null) }
    var editingTitleDraft by remember { mutableStateOf("") }
    var confirmDeleteSession by remember { mutableStateOf<NexusSession?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showWhatsLockedSheet by remember { mutableStateOf(false) }

    val filteredSessions = remember(sessions, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            sessions.filter { !it.isTemporary }
        } else {
            sessions.filter { !it.isTemporary && it.title.contains(q, ignoreCase = true) }
        }
    }

    val groupedSections = remember(filteredSessions) {
        groupSessionsByDate(filteredSessions)
    }

    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .testTag("nexus_sidebar"),
        color = MaterialTheme.colorScheme.surface
    ) {
        val isLargeFontScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale >= 1.5f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // =================================================================
            // 1. PINNED HEADER (Logo, Sync Status, New chat, Temp)
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        NexusLogoIcon(modifier = Modifier.size(28.dp))
                        Text(
                            text = "ZELKAI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SidebarSyncIndicator(
                        isGuestMode = isGuestMode,
                        isTemporaryChat = isTemporaryChat,
                        isNetworkOnline = isNetworkOnline,
                        isSyncing = isSyncingOrRunning
                    )
                }

                if (isLargeFontScale) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onNewChatClick,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sidebar_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New chat",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "New chat",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onTemporaryChatClick,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isTemporaryChat) {
                                    NexusAccent.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                contentColor = if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sidebar_temp_chat_button")
                        ) {
                            IncognitoVectorIcon(
                                tint = if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Temp",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onNewChatClick,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sidebar_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New chat",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "New chat",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onTemporaryChatClick,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isTemporaryChat) {
                                    NexusAccent.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                contentColor = if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .testTag("sidebar_temp_chat_button")
                        ) {
                            IncognitoVectorIcon(
                                tint = if (isTemporaryChat) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Temp",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // =================================================================
            // 2. SCROLLABLE MIDDLE (Search, Chat List, Workspace Items)
            // =================================================================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("sidebar_scroll_list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search field inside scrollable middle
                item(key = "sidebar_search_item") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search chats",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search chats…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(NexusAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sidebar_search_field")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                }

                // Section: Chats (WCAG AA high-contrast header)
                item(key = "section_chats_header") {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                if (groupedSections.isEmpty()) {
                    item(key = "empty_chats_hint") {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching chats." else "No saved chats yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    groupedSections.forEach { section ->
                        item(key = "group_${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 6.dp,
                                    bottom = 2.dp
                                )
                            )
                        }

                        items(
                            items = section.sessions,
                            key = { "chat_item_${it.id}" }
                        ) { session ->
                            val isActive = !isTemporaryChat &&
                                session.id == activeSessionId &&
                                currentViewMode == NexusViewMode.CHAT
                            val isEditingThis = editingSessionId == session.id

                            SidebarChatSessionRow(
                                session = session,
                                isActive = isActive,
                                isEditing = isEditingThis,
                                editingDraft = editingTitleDraft,
                                onEditingDraftChange = { editingTitleDraft = it },
                                onCommitRename = {
                                    onRenameSession(session.id, editingTitleDraft)
                                    editingSessionId = null
                                },
                                onCancelRename = { editingSessionId = null },
                                onSelect = {
                                    onSelectSession(session.id)
                                    onSelectViewMode(NexusViewMode.CHAT)
                                },
                                onStartRename = {
                                    editingSessionId = session.id
                                    editingTitleDraft = session.title
                                },
                                onTogglePin = { onTogglePinSession(session.id) },
                                onExportMd = { onExportSession(session.id, "md") },
                                onExportJson = { onExportSession(session.id, "json") },
                                onRequestDelete = { confirmDeleteSession = session },
                                onSwipeDelete = { confirmDeleteSession = session }
                            )
                        }
                    }
                }

                // Divider before Workspace section
                item(key = "workspace_divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Section: Workspace (WCAG AA high-contrast header)
                item(key = "section_workspace_header") {
                    Text(
                        text = "Workspace",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                item(key = "nav_chat") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "Chat",
                        isSelected = currentViewMode == NexusViewMode.CHAT,
                        onClick = { onSelectViewMode(NexusViewMode.CHAT) },
                        testTag = "switch_to_chat_button"
                    )
                }

                item(key = "nav_terminal") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Terminal,
                        label = "Terminal CLI",
                        subtitle = if (hasPendingApproval) "Approval required" else null,
                        badgeText = if (hasPendingApproval) "1" else null,
                        badgeColor = NexusWarn,
                        isSelected = currentViewMode == NexusViewMode.TERMINAL,
                        onClick = { onSelectViewMode(NexusViewMode.TERMINAL) },
                        testTag = "view_mode_toggle_chip"
                    )
                }

                item(key = "nav_dashboard") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Analytics,
                        label = "Dashboard",
                        subtitle = "Execution time & success rate",
                        isSelected = currentViewMode == NexusViewMode.DASHBOARD,
                        onClick = { onSelectViewMode(NexusViewMode.DASHBOARD) },
                        testTag = "switch_to_dashboard_button"
                    )
                }

                item(key = "nav_workdir") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.FolderOpen,
                        label = "Working directory",
                        subtitle = NexusToolAndRedactor.shortenPathMiddle(currentCwd, 32),
                        isMonospaceSubtitle = true,
                        isSelected = false,
                        onClick = onOpenWorkingDirectory,
                        testTag = "header_directory_picker_button"
                    )
                }

                item(key = "nav_exec_env") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Terminal,
                        label = "Execution environment",
                        subtitle = if (isSetupNeeded) "Termux setup & diagnostics" else "Termux ready · Toolchain",
                        badgeText = if (isSetupNeeded) "Setup needed" else null,
                        badgeColor = NexusWarn,
                        isSelected = false,
                        onClick = onOpenExecutionEnvironment,
                        testTag = "sidebar_execution_env_button"
                    )
                }

                item(key = "nav_background_jobs") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Memory,
                        label = "Background jobs",
                        subtitle = if (activeBackgroundJobsCount > 0) "$activeBackgroundJobsCount running in Termux" else "Dev servers & daemons",
                        badgeText = if (activeBackgroundJobsCount > 0) activeBackgroundJobsCount.toString() else null,
                        badgeColor = NexusOk,
                        isSelected = false,
                        onClick = onOpenJobsSheet,
                        testTag = "sidebar_background_jobs_button"
                    )
                }

                item(key = "nav_local_llm") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Memory,
                        label = "Local LLM Hub",
                        subtitle = if (activeDownloadCount > 0) "Downloading ($activeDownloadCount)" else "On-device GGUF models",
                        badgeText = if (activeDownloadCount > 0) activeDownloadCount.toString() else null,
                        badgeColor = NexusAccent,
                        isSelected = false,
                        onClick = onOpenLocalLlmHub,
                        testTag = "sidebar_local_llm_hub_button"
                    )
                }

                item(key = "nav_settings") {
                    SidebarWorkspaceItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        subtitle = "API keys, persona & limits",
                        isSelected = false,
                        onClick = onOpenSettings,
                        testTag = "open_settings_button"
                    )
                }
            }

            // =================================================================
            // 3. PINNED COMPACT FOOTER (Icon-only Theme Switch + Guest/User Row)
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Icon-only Theme Segmented Switch (sun / moon / auto) with content descriptions
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sidebar_theme_segmented_control")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val options = listOf(
                            Triple(NexusThemeMode.LIGHT, "Light theme", Icons.Default.LightMode),
                            Triple(NexusThemeMode.DARK, "Dark theme", Icons.Default.DarkMode),
                            Triple(NexusThemeMode.SYSTEM, "Auto system theme", Icons.Default.BrightnessAuto)
                        )
                        options.forEach { (mode, contentDesc, icon) ->
                            val selected = themeMode == mode
                            Surface(
                                onClick = { onSelectThemeMode(mode) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag(if (mode == NexusThemeMode.DARK) "theme_mode_toggle" else "theme_mode_${mode.id}")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = contentDesc,
                                        tint = if (selected) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact Guest Row or Signed-in User Row
                if (isGuestMode) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sidebar_guest_profile_card")
                    ) {
                        if (isLargeFontScale) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    NexusUserAvatar(
                                        avatarBitmap = avatarBitmap,
                                        displayName = userName,
                                        email = userEmail,
                                        isGuestMode = true,
                                        size = 30.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userName?.takeIf { it.isNotBlank() } ?: "Guest",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "What's locked?",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = NexusAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clickable { showWhatsLockedSheet = true }
                                                .testTag("sidebar_whats_locked_link")
                                        )
                                    }
                                }

                                Button(
                                    onClick = onSignInOrUpClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NexusAccent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("guest_sign_in_upgrade_button")
                                ) {
                                    Text(
                                        text = "Sign up / Sign in",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    NexusUserAvatar(
                                        avatarBitmap = avatarBitmap,
                                        displayName = userName,
                                        email = userEmail,
                                        isGuestMode = true,
                                        size = 30.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userName?.takeIf { it.isNotBlank() } ?: "Guest",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "What's locked?",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = NexusAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clickable { showWhatsLockedSheet = true }
                                                .testTag("sidebar_whats_locked_link")
                                        )
                                    }
                                }

                                Button(
                                    onClick = onSignInOrUpClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NexusAccent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .testTag("guest_sign_in_upgrade_button")
                                ) {
                                    Text(
                                        text = "Sign up / Sign in",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box {
                        Surface(
                            onClick = { showProfileMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sidebar_user_profile_row")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    NexusUserAvatar(
                                        avatarBitmap = avatarBitmap,
                                        displayName = userName,
                                        email = userEmail,
                                        isGuestMode = false,
                                        size = 32.dp
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userName?.ifBlank { null }
                                                ?: userEmail?.substringBefore('@')
                                                ?: "Nexus Operator",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = userEmail ?: "Signed in with Google",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Account menu",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings & API keys") },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showProfileMenu = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Sign out",
                                        color = NexusDanger
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = NexusDanger
                                    )
                                },
                                onClick = {
                                    showProfileMenu = false
                                    onSignOutClick()
                                },
                                modifier = Modifier.testTag("sidebar_sign_out_button")
                            )
                        }
                    }
                }
            }
        }
    }

    // "What's locked?" Bottom Sheet for Guests
    if (showWhatsLockedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWhatsLockedSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("whats_locked_bottom_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = NexusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "What's unlocked with an account?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Guest mode keeps your workspace and chats local to this device. Sign in with Google to unlock:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lockedItems = listOf(
                    "Cloud Sync & Backup — sync chat sessions, personas, skills, and profile picture across devices",
                    "Unlimited Multi-Session History — automatic cloud persistence for all project threads",
                    "Full Transcript Exports — export all sessions as Markdown or JSON bundles",
                    "Large Local LLMs (>1 GB) — unlock 1.5B+ GGUF coding models in the Local LLM Hub"
                )
                lockedItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NexusOk,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showWhatsLockedSheet = false }) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showWhatsLockedSheet = false
                            onSignInOrUpClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Sign up / Sign in", softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }

    // Delete Session Confirmation Dialog
    if (confirmDeleteSession != null) {
        val target = confirmDeleteSession!!
        AlertDialog(
            onDismissRequest = { confirmDeleteSession = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Delete chat?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "\"${target.title}\" and its transcript will be deleted. You can undo immediately after deleting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteSession = null
                        onDeleteSession(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusDanger,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_session_button")
                ) {
                    Text("Delete", softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSession = null }) {
                    Text("Cancel", softWrap = false, maxLines = 1)
                }
            }
        )
    }
}

@Composable
private fun SidebarSyncIndicator(
    isGuestMode: Boolean,
    isTemporaryChat: Boolean,
    isNetworkOnline: Boolean,
    isSyncing: Boolean
) {
    val (icon, label, tint) = when {
        isTemporaryChat -> Triple(Icons.Default.CloudOff, "Temp", NexusWarn)
        isGuestMode -> Triple(Icons.Default.CloudOff, "Local", MaterialTheme.colorScheme.onSurfaceVariant)
        !isNetworkOnline -> Triple(Icons.Default.CloudOff, "Offline", NexusDanger)
        isSyncing -> Triple(Icons.Default.CloudSync, "Syncing", NexusAccent)
        else -> Triple(Icons.Default.CloudDone, "Synced", NexusOk)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f)),
        modifier = Modifier.testTag("sidebar_sync_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = tint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarChatSessionRow(
    session: NexusSession,
    isActive: Boolean,
    isEditing: Boolean,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    onCommitRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSelect: () -> Unit,
    onStartRename: () -> Unit,
    onTogglePin: () -> Unit,
    onExportMd: () -> Unit,
    onExportJson: () -> Unit,
    onRequestDelete: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                false
            } else {
                false
            }
        }
    )

    LaunchedEffect(session.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isEditing,
        backgroundContent = {
            val bg by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    NexusDanger.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = NexusDanger,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) {
        Surface(
            onClick = { if (!isEditing) onSelect() },
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) {
                NexusAccent.copy(alpha = 0.10f)
            } else {
                Color.Transparent
            },
            border = if (isActive) {
                BorderStroke(1.dp, NexusAccent.copy(alpha = 0.35f))
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sidebar_session_item_${session.id}")
        ) {
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicTextField(
                        value = editingDraft,
                        onValueChange = onEditingDraftChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(NexusAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommitRename() }),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("inline_rename_field_${session.id}")
                    )
                    IconButton(
                        onClick = onCommitRename,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save title",
                            tint = NexusOk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancelRename,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (session.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = NexusAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = session.title.ifBlank { "New chat" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.5.sp
                            ),
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("session_overflow_btn_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Chat options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onStartRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (session.isPinned) "Unpin" else "Pin") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (session.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Markdown") },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onExportMd()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON") },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onExportJson()
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            DropdownMenuItem(
                                text = { Text("Delete", color = NexusDanger) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = NexusDanger,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRequestDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarWorkspaceItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    isMonospaceSubtitle: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color = NexusAccent,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) NexusAccent.copy(alpha = 0.10f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, NexusAccent.copy(alpha = 0.35f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = if (isMonospaceSubtitle) {
                                    JetBrainsMonoFontFamily
                                } else {
                                    MaterialTheme.typography.labelSmall.fontFamily
                                },
                                fontSize = 10.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeColor)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
