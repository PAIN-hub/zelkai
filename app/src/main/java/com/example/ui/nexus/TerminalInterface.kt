package com.example.ui.nexus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TranscriptEntry
import com.example.domain.NexusToolAndRedactor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusDangerBorder
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import java.util.Locale
import kotlin.math.hypot

/**
 * `TerminalInterface.kt` — Interactive Command-Line Interface (CLI) component for Nexus Agent v5.
 * Includes a Circular Reveal + Cross-Fade visual transition when toggling between Light and Dark modes,
 * subtle loading animations, inline command approval gates, stdout/stderr execution logs, collapsible
 * command history, working directory selector, and All Files Access permission status.
 */
@Composable
fun TerminalInterface(
    entries: List<TranscriptEntry>,
    streamingText: String?,
    agentStatus: String,
    isRunning: Boolean,
    activeProviderLabel: String,
    activeModelId: String,
    currentCwd: String,
    inputHistory: List<String>,
    hasAllFilesAccess: Boolean,
    isDarkTheme: Boolean,
    listState: LazyListState,
    onApproveCommand: (TranscriptEntry) -> Unit,
    onDenyCommand: (TranscriptEntry) -> Unit,
    onAnalyzeWithGemini: (TranscriptEntry) -> Unit,
    onRecallHistoryItem: (String) -> Unit,
    onExecuteImmediateCommand: (String) -> Unit,
    onOpenDirectoryPicker: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showHistoryPanel by remember { mutableStateOf(false) }

    // Track circular reveal origin (defaults to top-right terminal header toggle button)
    var rootOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var toggleBtnCenterInWindow by remember { mutableStateOf<Offset?>(null) }

    // Previous theme state for circular reveal + cross-fade transition
    var previousThemeState by remember { mutableStateOf(isDarkTheme) }
    var isTransitioningTheme by remember { mutableStateOf(false) }
    val revealProgress = remember { Animatable(1f) }

    LaunchedEffect(isDarkTheme) {
        if (isDarkTheme != previousThemeState) {
            isTransitioningTheme = true
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
            previousThemeState = isDarkTheme
            isTransitioningTheme = false
        }
    }

    // Combine directive history + executed shell commands from transcript
    val executedCommands = remember(entries, inputHistory) {
        val fromTranscript = entries.mapNotNull { entry ->
            when (entry.type) {
                "command" -> entry.command.takeIf { it.isNotBlank() }?.let { "sh: $it" to it }
                "user" -> entry.text.takeIf { it.isNotBlank() }?.let { "ai: $it" to it }
                else -> null
            }
        }
        val fromHistory = inputHistory.map { "hist: $it" to it }
        (fromTranscript + fromHistory).distinctBy { it.second }.reversed()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOriginInWindow = coords.positionInRoot()
            }
            .drawWithContent {
                drawContent()
                // Circular reveal wave overlay + previous theme backdrop during Light/Dark transition
                val progress = revealProgress.value
                if (isTransitioningTheme && progress < 1f) {
                    val localCenter = toggleBtnCenterInWindow?.let { btnPos ->
                        Offset(
                            x = (btnPos.x - rootOriginInWindow.x).coerceIn(0f, size.width),
                            y = (btnPos.y - rootOriginInWindow.y).coerceIn(0f, size.height)
                        )
                    } ?: Offset(size.width * 0.88f, 28.dp.toPx())

                    val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                    val currentRadius = maxRadius * progress

                    val revealPath = Path().apply {
                        addOval(
                            Rect(
                                center = localCenter,
                                radius = currentRadius
                            )
                        )
                    }
                    // Mask outside the expanding circle with the outgoing theme's surface color
                    val outgoingBg = if (previousThemeState) Color(0xFF0B0D11) else Color(0xFFF7F6F3)
                    clipPath(revealPath, clipOp = ClipOp.Difference) {
                        drawRect(
                            color = outgoingBg.copy(alpha = (1f - progress).coerceIn(0f, 0.92f))
                        )
                    }
                    // Subtle glowing ring along the circular reveal edge
                    drawCircle(
                        color = NexusAccent.copy(alpha = (1f - progress) * 0.55f),
                        radius = currentRadius,
                        center = localCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .testTag("terminal_interface_root")
    ) {
        Crossfade(
            targetState = isDarkTheme,
            animationSpec = tween(durationMillis = 220),
            label = "terminalThemeCrossfade"
        ) { dark ->
            val termBg by animateColorAsState(
                targetValue = if (dark) Color(0xFF0B0D11) else Color(0xFFFDFCFB),
                animationSpec = tween(220),
                label = "termBg"
            )
            val termHeaderBg by animateColorAsState(
                targetValue = if (dark) Color(0xFF12151C) else Color(0xFFF3F1EC),
                animationSpec = tween(220),
                label = "termHeaderBg"
            )
            val termBorder by animateColorAsState(
                targetValue = if (dark) Color(0xFF222733) else Color(0xFFE4E2DA),
                animationSpec = tween(220),
                label = "termBorder"
            )
            val termPrimaryText by animateColorAsState(
                targetValue = if (dark) Color(0xFFE5E7EB) else Color(0xFF141519),
                animationSpec = tween(220),
                label = "termPrimaryText"
            )
            val termMutedText by animateColorAsState(
                targetValue = if (dark) Color(0xFF9CA3AF) else Color(0xFF636672),
                animationSpec = tween(220),
                label = "termMutedText"
            )
            val termPromptUser = if (dark) Color(0xFF34D399) else Color(0xFF0D9488)
            val termPromptPath = if (dark) Color(0xFF818CF8) else Color(0xFF4F46E5)
            val termOutputSurface by animateColorAsState(
                targetValue = if (dark) Color(0xFF141821) else Color(0xFFF7F6F3),
                animationSpec = tween(220),
                label = "termOutputSurface"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(termBg)
            ) {
                // 1. Terminal Window Chrome & Working Directory / File Access Toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(termHeaderBg)
                        .border(1.dp, termBorder)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Terminal window dots + active shell title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal CLI",
                                tint = termPromptPath,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "nexus@android:${NexusToolAndRedactor.shortenPath(currentCwd, 28)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = termPrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TerminalHeaderActivityBadge(
                                agentStatus = agentStatus,
                                isRunning = isRunning,
                                termMutedText = termMutedText
                            )

                            // Terminal Light/Dark Circular Reveal Toggle Button
                            if (onToggleTheme != null) {
                                Surface(
                                    onClick = onToggleTheme,
                                    shape = RoundedCornerShape(8.dp),
                                    color = termOutputSurface,
                                    border = BorderStroke(1.dp, termBorder),
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            toggleBtnCenterInWindow = Offset(
                                                pos.x + coords.size.width / 2f,
                                                pos.y + coords.size.height / 2f
                                            )
                                        }
                                        .testTag("terminal_theme_toggle_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                            contentDescription = "Toggle terminal theme",
                                            tint = if (dark) Color(0xFFFBBF24) else termPromptPath,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (dark) "Light" else "Dark",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = termPrimaryText
                                        )
                                    }
                                }
                            }

                            // Command History Drawer Toggle Button
                            Surface(
                                onClick = { showHistoryPanel = !showHistoryPanel },
                                shape = RoundedCornerShape(8.dp),
                                color = if (showHistoryPanel) NexusAccent.copy(alpha = 0.16f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (showHistoryPanel) NexusAccent else termBorder),
                                modifier = Modifier.testTag("terminal_history_toggle_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Command History",
                                        tint = if (showHistoryPanel) NexusAccent else termMutedText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "History (${executedCommands.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (showHistoryPanel) NexusAccent else termPrimaryText
                                    )
                                }
                            }
                        }
                    }

                    // Working Directory Chooser & All Files Access Permission Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onOpenDirectoryPicker,
                            shape = RoundedCornerShape(8.dp),
                            color = termPromptPath.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, termPromptPath.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("terminal_choose_directory_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Choose Working Directory",
                                    tint = termPromptPath,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "cwd: ${NexusToolAndRedactor.shortenPath(currentCwd, 32)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = termPromptPath
                                )
                            }
                        }

                        Surface(
                            onClick = onRequestAllFilesAccess,
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasAllFilesAccess) NexusOk.copy(alpha = 0.10f) else NexusWarn.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (hasAllFilesAccess) NexusOk.copy(alpha = 0.35f) else NexusWarn.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.testTag("terminal_all_files_access_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasAllFilesAccess) Icons.Default.Shield else Icons.Default.Warning,
                                    contentDescription = "All Files Access Status",
                                    tint = if (hasAllFilesAccess) NexusOk else NexusWarn,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (hasAllFilesAccess) "All Files Access: Granted" else "Grant All Files Access",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasAllFilesAccess) NexusOk else NexusWarn
                                )
                            }
                        }
                    }

                    if (isRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .testTag("terminal_header_progress_bar"),
                            color = when (agentStatus) {
                                "exec" -> NexusOk
                                "wait" -> NexusWarn
                                else -> termPromptPath
                            },
                            trackColor = termBorder
                        )
                    }
                }

                // 2. Collapsible Command & Directive History Panel
                AnimatedVisibility(visible = showHistoryPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .background(termOutputSurface)
                            .border(1.dp, termBorder)
                            .padding(10.dp)
                            .testTag("terminal_history_panel"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMMAND & DIRECTIVE HISTORY (TAP TO RECALL)",
                                style = MaterialTheme.typography.labelSmall,
                                color = termMutedText
                            )
                            Text(
                                text = "Close",
                                style = MaterialTheme.typography.labelSmall,
                                color = termPromptPath,
                                modifier = Modifier.clickable { showHistoryPanel = false }
                            )
                        }
                        if (executedCommands.isEmpty()) {
                            Text(
                                text = "No commands recorded in this session yet.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = termMutedText
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(executedCommands) { (taggedLabel, rawCmd) ->
                                    val isShellCmd = taggedLabel.startsWith("sh:")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(termHeaderBg)
                                            .clickable {
                                                onRecallHistoryItem(rawCmd)
                                                showHistoryPanel = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$ $rawCmd",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = termPrimaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isShellCmd) {
                                            Surface(
                                                onClick = {
                                                    onExecuteImmediateCommand(rawCmd)
                                                    showHistoryPanel = false
                                                },
                                                shape = RoundedCornerShape(4.dp),
                                                color = NexusOk.copy(alpha = 0.15f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Run command",
                                                        tint = NexusOk,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Run",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = NexusOk
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

                // 3. Main Terminal Scrollable Output Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("terminal_output_stream"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "terminal_motd_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(termOutputSurface)
                                .border(1.dp, termBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Nexus // Shell Agent v5.0 — Live Terminal Session",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = termPromptPath
                            )
                            Text(
                                text = "cwd: $currentCwd · model: $activeProviderLabel/$activeModelId · secret redaction: active",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = termMutedText
                            )
                        }
                    }

                    items(
                        items = entries,
                        key = { it.id.ifBlank { "${it.sequence}_${it.type}" } }
                    ) { entry ->
                        TerminalStreamEntryBlock(
                            entry = entry,
                            currentCwd = currentCwd,
                            isDarkTheme = dark,
                            termPrimaryText = termPrimaryText,
                            termMutedText = termMutedText,
                            termPromptUser = termPromptUser,
                            termPromptPath = termPromptPath,
                            termOutputSurface = termOutputSurface,
                            termBorder = termBorder,
                            onApproveCommand = { onApproveCommand(entry) },
                            onDenyCommand = { onDenyCommand(entry) },
                            onAnalyzeWithGemini = { onAnalyzeWithGemini(entry) }
                        )
                    }

                    if (streamingText != null) {
                        item(key = "terminal_streaming_line") {
                            TerminalStreamingBlock(
                                streamingText = streamingText,
                                activeProviderLabel = activeProviderLabel,
                                activeModelId = activeModelId,
                                termPrimaryText = termPrimaryText,
                                termMutedText = termMutedText,
                                termPromptPath = termPromptPath,
                                termOutputSurface = termOutputSurface
                            )
                        }
                    } else if (isRunning || agentStatus != "idle") {
                        item(key = "terminal_processing_status_line") {
                            TerminalProcessingStatusBlock(
                                agentStatus = agentStatus,
                                activeProviderLabel = activeProviderLabel,
                                activeModelId = activeModelId,
                                currentCwd = currentCwd,
                                termPrimaryText = termPrimaryText,
                                termMutedText = termMutedText,
                                termPromptPath = termPromptPath,
                                termOutputSurface = termOutputSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerminalStreamEntryBlock(
    entry: TranscriptEntry,
    currentCwd: String,
    isDarkTheme: Boolean,
    termPrimaryText: Color,
    termMutedText: Color,
    termPromptUser: Color,
    termPromptPath: Color,
    termOutputSurface: Color,
    termBorder: Color,
    onApproveCommand: () -> Unit,
    onDenyCommand: () -> Unit,
    onAnalyzeWithGemini: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            val startMs = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startMs < 1500L) {
                withFrameNanos { }
            }
            copied = false
        }
    }

    when (entry.type) {
        "user" -> {
            SelectionContainer {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = termPromptUser
                            )
                        ) {
                            append("nexus@v5")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = termMutedText
                            )
                        ) {
                            append(":")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = termPromptPath
                            )
                        ) {
                            append(NexusToolAndRedactor.shortenPath(entry.cwd.ifBlank { currentCwd }, 24))
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = termPrimaryText
                            )
                        ) {
                            append("$ ")
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = termPrimaryText
                            )
                        ) {
                            append(entry.text)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        lineHeight = 20.sp
                    )
                )
            }
        }

        "agent", "analysis" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(termOutputSurface)
                    .border(
                        1.dp,
                        if (entry.type == "analysis") NexusAccent.copy(alpha = 0.5f) else termBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (entry.type == "analysis") "[gemini-intelligence]" else "[agent]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (entry.type == "analysis") NexusAccent2 else termPromptPath
                    )
                }
                MarkdownMessageContent(markdown = entry.text)
            }
        }

        "command" -> {
            val status = entry.commandStatus.ifBlank { "pending" }
            val isPending = status == "pending"
            val isApproved = status == "approved"
            val isDenied = status == "denied" || status == "cancelled"

            val accentColor = when {
                isApproved -> NexusOk
                isDenied -> NexusDanger
                entry.isRisky -> NexusDanger
                else -> NexusWarn
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = if (isDarkTheme) 0.12f else 0.08f))
                    .border(1.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .testTag("terminal_perm_card_${entry.id}"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (entry.isRisky) Icons.Default.Warning else Icons.Default.Security,
                            contentDescription = "Command approval status",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when {
                                isApproved -> "[APPROVED] ${entry.reason}"
                                status == "cancelled" -> "[CANCELLED] ${entry.reason}"
                                isDenied -> "[DENIED] ${entry.reason}"
                                entry.isRisky -> "[HIGH RISK · APPROVAL REQUIRED] ${entry.reason}"
                                else -> "[APPROVAL REQUIRED] ${entry.reason}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = accentColor
                        )
                    }

                    Surface(
                        onClick = onAnalyzeWithGemini,
                        shape = RoundedCornerShape(6.dp),
                        color = termOutputSurface,
                        border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Audit",
                                tint = NexusAccent,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "AI Audit",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusAccent
                            )
                        }
                    }
                }

                Text(
                    text = "cwd: ${entry.cwd.ifBlank { currentCwd }} · timeout: ${entry.timeoutSec}s",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = termMutedText
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E1117))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$ ${entry.command}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color(0xFF38BDF8)
                            )
                        )
                    }
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy command",
                        tint = if (copied) NexusOk else Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(15.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString(entry.command))
                                copied = true
                            }
                    )
                }

                if (isPending) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onApproveCommand,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusOk,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("approve_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Approve and run",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Approve & run",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onDenyCommand,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NexusDangerBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NexusDanger
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("deny_command_button")
                        ) {
                            Text(
                                text = "Deny",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusDanger
                            )
                        }
                    }
                }
            }
        }

        "output" -> {
            val isOk = entry.exitCode == 0L
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(termOutputSurface)
                    .border(1.dp, termBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "[exit ${entry.exitCode}]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isOk) NexusOk else NexusWarn
                    )
                    if (entry.cwd.isNotBlank()) {
                        Text(
                            text = NexusToolAndRedactor.shortenPath(entry.cwd, 34),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = termMutedText
                        )
                    }
                    if (entry.redactions > 0L) {
                        Text(
                            text = "[${entry.redactions} secret${if (entry.redactions == 1L) "" else "s"} masked]",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = NexusWarn
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onAnalyzeWithGemini() }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Explain output",
                            tint = termPromptPath,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Explain",
                            style = MaterialTheme.typography.labelSmall,
                            color = termPromptPath
                        )
                    }
                }

                HorizontalDivider(color = termBorder)

                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = entry.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = termPrimaryText
                        )
                    }
                }
            }
        }

        "notice" -> {
            val noticeColor = when (entry.noticeKind) {
                "error" -> NexusDanger
                "info" -> termPromptPath
                else -> NexusWarn
            }
            Text(
                text = "# [${entry.noticeKind.ifBlank { "sys" }.uppercase()}] ${entry.text}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    lineHeight = 17.sp
                ),
                color = noticeColor
            )
        }
    }
}

@Composable
private fun TerminalHeaderActivityBadge(
    agentStatus: String,
    isRunning: Boolean,
    termMutedText: Color
) {
    val active = isRunning || agentStatus != "idle"

    val pulseTransition = rememberInfiniteTransition(label = "headerPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val (badgeColor, label) = when (agentStatus) {
        "stream" -> NexusAccent to "STREAMING"
        "exec" -> NexusOk to "EXEC SHELL"
        "wait" -> NexusWarn to "AWAITING GATE"
        else -> if (isRunning) {
            NexusAccent to "WORKING"
        } else {
            termMutedText to "IDLE"
        }
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = badgeColor.copy(alpha = if (active) 0.14f else 0.08f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = if (active) 0.45f else 0.25f)),
        modifier = Modifier.testTag("terminal_activity_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(if (active) dotScale else 1f)
                    .alpha(if (active) dotAlpha else 0.7f)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = badgeColor
            )
        }
    }
}

@Composable
private fun TerminalProcessingStatusBlock(
    agentStatus: String,
    activeProviderLabel: String,
    activeModelId: String,
    currentCwd: String,
    termPrimaryText: Color,
    termMutedText: Color,
    termPromptPath: Color,
    termOutputSurface: Color
) {
    var elapsedTenths by remember { mutableIntStateOf(0) }

    LaunchedEffect(agentStatus) {
        val startMs = SystemClock.elapsedRealtime()
        elapsedTenths = 0
        while (true) {
            withFrameNanos { }
            elapsedTenths = ((SystemClock.elapsedRealtime() - startMs) / 100L).toInt()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "procPulse")
    val borderAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val accentColor = when (agentStatus) {
        "exec" -> NexusOk
        "wait" -> NexusWarn
        else -> termPromptPath
    }

    val statusTitle = when (agentStatus) {
        "exec" -> "[shell · executing subprocess]"
        "wait" -> "[permission gate · awaiting approval]"
        else -> "[$activeProviderLabel · $activeModelId · thinking]"
    }

    val statusDetail = when (agentStatus) {
        "exec" -> "Running command in ${NexusToolAndRedactor.shortenPath(currentCwd, 32)} · masking sensitive tokens…"
        "wait" -> "Execution paused until operator approves or denies the proposed shell command."
        else -> "Synthesizing reasoning steps & shell tool directives…"
    }

    val elapsedFormatted = String.format(Locale.US, "%.1fs", elapsedTenths / 10.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(termOutputSurface)
            .border(1.dp, accentColor.copy(alpha = borderAlpha), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("terminal_processing_indicator"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = accentColor
                )
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentColor
                )
            }
            Text(
                text = elapsedFormatted,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = termMutedText
            )
        }
        Text(
            text = statusDetail,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                lineHeight = 17.sp
            ),
            color = termPrimaryText
        )
    }
}

@Composable
private fun TerminalStreamingBlock(
    streamingText: String,
    activeProviderLabel: String,
    activeModelId: String,
    termPrimaryText: Color,
    termMutedText: Color,
    termPromptPath: Color,
    termOutputSurface: Color
) {
    var elapsedTenths by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val startMs = SystemClock.elapsedRealtime()
        while (true) {
            withFrameNanos { }
            elapsedTenths = ((SystemClock.elapsedRealtime() - startMs) / 100L).toInt()
        }
    }

    val transition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(480),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    val pulseBorderAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorderAlpha"
    )

    val elapsedFormatted = String.format(Locale.US, "%.1fs", elapsedTenths / 10.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(termOutputSurface)
            .border(1.dp, termPromptPath.copy(alpha = pulseBorderAlpha), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("terminal_streaming_block"),
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
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = termPromptPath
                )
                Text(
                    text = "[$activeProviderLabel · $activeModelId · streaming]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = termPromptPath
                )
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(13.dp)
                        .alpha(cursorAlpha)
                        .background(termPromptPath)
                )
            }
            Text(
                text = "${streamingText.length} chars · $elapsedFormatted",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = termMutedText
            )
        }
        Text(
            text = streamingText.ifBlank { "Synthesizing response & shell execution plan…" },
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                lineHeight = 18.sp
            ),
            color = termPrimaryText
        )
    }
}
