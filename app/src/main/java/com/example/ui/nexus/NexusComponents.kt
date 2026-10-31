package com.example.ui.nexus

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.PoppinsFontFamily
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TranscriptEntry
import com.example.domain.ModelLoadResult
import com.example.domain.NexusToolAndRedactor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusAccentSoftLight
import com.example.ui.theme.NexusCodeBg
import com.example.ui.theme.NexusCodeHeaderBg
import com.example.ui.theme.NexusCodeLangText
import com.example.ui.theme.NexusCodeText
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusOkSoft
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NexusThemeTokens
import com.example.ui.theme.NexusWarn
import com.example.ui.theme.NexusWarnSoft
import com.example.ui.theme.NexusWarnText
import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos

/**
 * Small status dot for the Top Bar pill (ready / streaming / awaiting approval / executing).
 */
@Composable
fun StatusDotIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        "stream", "streaming" -> NexusAccent
        "wait", "awaiting_approval" -> NexusWarn
        "exec" -> NexusAccent2
        else -> NexusOk
    }
    val isPulsing = status == "stream" || status == "streaming" || status == "exec" || status == "wait" || status == "awaiting_approval"
    val transition = rememberInfiniteTransition(label = "topBarDotPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 0.72f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 0.42f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(7.dp)
            .scale(pulseScale)
            .alpha(pulseAlpha)
            .clip(CircleShape)
            .background(dotColor)
            .testTag("status_indicator_badge")
    )
}

@Composable
fun StatusIndicatorBadge(status: String) {
    val label = when (status) {
        "stream", "streaming" -> "Streaming"
        "wait", "awaiting_approval" -> "Awaiting approval"
        "exec" -> "Executing"
        else -> "Ready"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatusDotIndicator(status = status)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexusThemeTokens.colors.aiSecondaryText
        )
    }
}

/**
 * Bug #1: "Add API key" card shown when a cloud provider is selected without a user API key in UserApiKeyStore.
 * Opens Settings directly or lets the user switch to an On-Device GGUF model (no key required).
 */
@Composable
fun MissingApiKeyCard(
    prompt: MissingApiKeyPrompt,
    onOpenSettings: () -> Unit,
    onOpenLocalHub: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = NexusThemeTokens.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("missing_api_key_card"),
        shape = RoundedCornerShape(20.dp),
        color = tokens.surface,
        border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API Key required",
                        tint = NexusWarn,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add API key for ${prompt.providerDisplayName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss API key prompt",
                        tint = tokens.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = "Nexus Agent v5 never uses built-in developer API keys or silent fallbacks. Enter your personal ${prompt.providerDisplayName} key in Settings to use ${formatShortModelName(prompt.modelId)}, or run an on-device GGUF model with zero API keys.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.aiSecondaryText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("add_api_key_open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Add API key",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add API key",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onOpenLocalHub,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, tokens.outline),
                    modifier = Modifier.testTag("missing_key_open_local_hub_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Use local model",
                        tint = NexusOk,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Use Local Model",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Part C Load Guard card shown when available RAM is below modelSize * 1.25.
 */
@Composable
fun LoadGuardErrorCard(
    failure: ModelLoadResult.RefusedByLoadGuard,
    onOpenLocalHub: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = NexusThemeTokens.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("load_guard_error_card"),
        shape = RoundedCornerShape(20.dp),
        color = tokens.surface,
        border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Memory load guard",
                        tint = NexusDanger,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Memory Load Guard · ${failure.modelName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.onSurface
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss load guard notice",
                        tint = tokens.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = failure.userMessage,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.aiSecondaryText
            )
            OutlinedButton(
                onClick = onOpenLocalHub,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NexusAccent)
            ) {
                Text(
                    text = "Choose Smaller Quantization in Local Hub",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusAccent
                )
            }
        }
    }
}

@Composable
fun NexusCodeBlock(
    lang: String,
    code: String,
    modifier: Modifier = Modifier,
    enableOwnSelectionContainer: Boolean = true
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val codeLines = remember(code) { code.lines() }
    val lineNumbersText = remember(codeLines.size) {
        (1..codeLines.size.coerceAtLeast(1)).joinToString("\n")
    }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500L)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexusCodeBg)
            .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
    ) {
        DisableSelection {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusCodeHeaderBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NexusAccent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = lang.ifBlank { "text" }.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFA5B4FC),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${codeLines.size} ${if (codeLines.size == 1) "line" else "lines"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.5.sp
                        ),
                        color = NexusCodeLangText.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        1.dp,
                        if (copied) Color(0xFF34D399) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) Color(0xFF34D399) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copied) Color(0xFF34D399) else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
        val codeContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (codeLines.size > 1) {
                    DisableSelection {
                        Text(
                            text = lineNumbersText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 19.sp
                            ),
                            color = NexusCodeLangText.copy(alpha = 0.45f),
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = NexusCodeText
                )
            }
        }
        codeContent()
    }
}

/**
 * Rich `.md` (Markdown) AST node representation for rendering detailed AI messages
 * into native Compose document views.
 */
internal sealed interface RichMarkdownNode {
    data class Heading(val level: Int, val text: String) : RichMarkdownNode
    data class CodeBlock(val lang: String, val code: String) : RichMarkdownNode
    data class Blockquote(val lines: List<String>) : RichMarkdownNode
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RichMarkdownNode
    data class TaskItem(val checked: Boolean, val text: String) : RichMarkdownNode
    data class BulletItem(val indentLevel: Int, val text: String) : RichMarkdownNode
    data class OrderedItem(val numberLabel: String, val text: String) : RichMarkdownNode
    data object HorizontalRule : RichMarkdownNode
    data class Paragraph(val text: String) : RichMarkdownNode
}

private val MD_HEADING_REGEX = Regex("""^\s*(#{1,6})\s+(.+?)\s*$""")
private val MD_HR_REGEX = Regex("""^\s*(?:---+|___+|\*\*\*+)\s*$""")
private val MD_TASK_REGEX = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s+(.+)$""")
private val MD_BULLET_REGEX = Regex("""^(\s*)[-*+•]\s+(.+)$""")
private val MD_ORDERED_REGEX = Regex("""^\s*(\d+[.)])\s+(.+)$""")
private val MD_TABLE_SEP_REGEX = Regex("""^\s*\|?(?:\s*:?-{2,}:?\s*\|)+\s*:?-{2,}:?\s*\|?\s*$""")

private fun splitMarkdownTableRow(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    return trimmed.split("|").map { it.trim() }
}

internal fun parseRichMarkdownNodes(markdown: String): List<RichMarkdownNode> {
    val topBlocks = NexusToolAndRedactor.parseBlocks(markdown)
    val nodes = mutableListOf<RichMarkdownNode>()

    for (block in topBlocks) {
        when (block) {
            is NexusToolAndRedactor.MarkdownBlock.Code -> {
                nodes.add(RichMarkdownNode.CodeBlock(lang = block.lang, code = block.code))
            }
            is NexusToolAndRedactor.MarkdownBlock.Text -> {
                val lines = block.text.lines()
                var i = 0
                val paragraphBuffer = mutableListOf<String>()

                fun flushParagraph() {
                    if (paragraphBuffer.isNotEmpty()) {
                        val joined = paragraphBuffer.joinToString("\n").trim()
                        if (joined.isNotEmpty()) {
                            nodes.add(RichMarkdownNode.Paragraph(joined))
                        }
                        paragraphBuffer.clear()
                    }
                }

                while (i < lines.size) {
                    val line = lines[i]
                    val trimmed = line.trim()

                    // 1) Empty line -> flush current paragraph
                    if (trimmed.isEmpty()) {
                        flushParagraph()
                        i++
                        continue
                    }

                    // 2) Horizontal rule (---, ***, ___)
                    if (MD_HR_REGEX.matches(trimmed)) {
                        flushParagraph()
                        nodes.add(RichMarkdownNode.HorizontalRule)
                        i++
                        continue
                    }

                    // 3) Markdown Heading (# H1 .. ###### H6)
                    val headingMatch = MD_HEADING_REGEX.matchEntire(line)
                    if (headingMatch != null) {
                        flushParagraph()
                        val level = headingMatch.groupValues[1].length.coerceIn(1, 6)
                        val title = headingMatch.groupValues[2].trim()
                        nodes.add(RichMarkdownNode.Heading(level = level, text = title))
                        i++
                        continue
                    }

                    // 4) Markdown Table (header row with `|` followed by separator row `| :--- | :--- |`)
                    if (trimmed.contains("|") && i + 1 < lines.size && MD_TABLE_SEP_REGEX.matches(lines[i + 1])) {
                        flushParagraph()
                        val headers = splitMarkdownTableRow(line)
                        i += 2 // skip header and separator line
                        val tableRows = mutableListOf<List<String>>()
                        while (i < lines.size) {
                            val rowLine = lines[i].trim()
                            if (rowLine.isEmpty() || !rowLine.contains("|")) break
                            if (!MD_TABLE_SEP_REGEX.matches(rowLine)) {
                                val cells = splitMarkdownTableRow(rowLine)
                                tableRows.add(cells)
                            }
                            i++
                        }
                        if (headers.isNotEmpty()) {
                            nodes.add(RichMarkdownNode.Table(headers = headers, rows = tableRows))
                        }
                        continue
                    }

                    // 5) Blockquote (> ...)
                    if (trimmed.startsWith(">")) {
                        flushParagraph()
                        val quoteLines = mutableListOf<String>()
                        while (i < lines.size && lines[i].trim().startsWith(">")) {
                            val cleanQuote = lines[i].trim().removePrefix(">").trimStart()
                            if (cleanQuote.isNotEmpty()) {
                                quoteLines.add(cleanQuote)
                            }
                            i++
                        }
                        if (quoteLines.isNotEmpty()) {
                            nodes.add(RichMarkdownNode.Blockquote(quoteLines))
                        }
                        continue
                    }

                    // 6) Task List Item (- [x] ... or - [ ] ...)
                    val taskMatch = MD_TASK_REGEX.matchEntire(line)
                    if (taskMatch != null) {
                        flushParagraph()
                        val isChecked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
                        val itemText = taskMatch.groupValues[3].trim()
                        nodes.add(RichMarkdownNode.TaskItem(checked = isChecked, text = itemText))
                        i++
                        continue
                    }

                    // 7) Bullet List Item (- ..., * ..., • ...)
                    val bulletMatch = MD_BULLET_REGEX.matchEntire(line)
                    if (bulletMatch != null) {
                        flushParagraph()
                        val indentSpaces = bulletMatch.groupValues[1].length
                        val indentLevel = (indentSpaces / 2).coerceIn(0, 3)
                        val itemText = bulletMatch.groupValues[2].trim()
                        nodes.add(RichMarkdownNode.BulletItem(indentLevel = indentLevel, text = itemText))
                        i++
                        continue
                    }

                    // 8) Ordered List Item (1. ..., 2. ...)
                    val orderedMatch = MD_ORDERED_REGEX.matchEntire(line)
                    if (orderedMatch != null) {
                        flushParagraph()
                        val numLabel = orderedMatch.groupValues[1].trim()
                        val itemText = orderedMatch.groupValues[2].trim()
                        nodes.add(RichMarkdownNode.OrderedItem(numberLabel = numLabel, text = itemText))
                        i++
                        continue
                    }

                    // 9) Regular paragraph text line
                    paragraphBuffer.add(line)
                    i++
                }

                flushParagraph()
            }
        }
    }

    return nodes
}

@Composable
fun MarkdownMessageContent(
    markdown: String,
    isStreaming: Boolean = false
) {
    val tokens = NexusThemeTokens.colors
    val nodes = remember(markdown) { parseRichMarkdownNodes(markdown) }

    val contentColumn: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("markdown_message_container"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            nodes.forEachIndexed { idx, node ->
                val isLastNode = idx == nodes.lastIndex
                when (node) {
                    is RichMarkdownNode.Heading -> {
                        MarkdownHeadingView(
                            level = node.level,
                            text = node.text,
                            showStreamingDot = isStreaming && isLastNode
                        )
                    }

                    is RichMarkdownNode.CodeBlock -> {
                        NexusCodeBlock(
                            lang = node.lang,
                            code = node.code,
                            enableOwnSelectionContainer = false
                        )
                    }

                    is RichMarkdownNode.Blockquote -> {
                        MarkdownBlockquoteView(lines = node.lines)
                    }

                    is RichMarkdownNode.Table -> {
                        MarkdownTableView(
                            headers = node.headers,
                            rows = node.rows
                        )
                    }

                    is RichMarkdownNode.TaskItem -> {
                        MarkdownTaskItemView(
                            checked = node.checked,
                            text = node.text,
                            showStreamingDot = isStreaming && isLastNode
                        )
                    }

                    is RichMarkdownNode.BulletItem -> {
                        MarkdownBulletItemView(
                            indentLevel = node.indentLevel,
                            text = node.text,
                            showStreamingDot = isStreaming && isLastNode
                        )
                    }

                    is RichMarkdownNode.OrderedItem -> {
                        MarkdownOrderedItemView(
                            numberLabel = node.numberLabel,
                            text = node.text,
                            showStreamingDot = isStreaming && isLastNode
                        )
                    }

                    is RichMarkdownNode.HorizontalRule -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(1.dp)
                                .background(tokens.outline)
                        )
                    }

                    is RichMarkdownNode.Paragraph -> {
                        val annotated = remember(node.text) {
                            buildInlineMarkdownAnnotatedString(node.text)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp
                                ),
                                color = tokens.aiText
                            )
                            if (isStreaming && isLastNode) {
                                Spacer(modifier = Modifier.width(4.dp))
                                StreamingPulseDot()
                            }
                        }
                    }
                }
            }
        }
    }

    contentColumn()
}

@Composable
private fun MarkdownHeadingView(
    level: Int,
    text: String,
    showStreamingDot: Boolean
) {
    val tokens = NexusThemeTokens.colors
    val annotated = remember(text) { buildInlineMarkdownAnnotatedString(text) }

    val fontSize = when (level) {
        1 -> 19.5.sp
        2 -> 17.sp
        3 -> 15.5.sp
        else -> 14.2.sp
    }
    val lineHeight = when (level) {
        1 -> 26.sp
        2 -> 23.sp
        3 -> 21.sp
        else -> 20.sp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (level <= 2) 4.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (level == 2) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NexusAccent)
                )
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (level <= 3) tokens.onSurface else tokens.aiSecondaryText
            )
            if (showStreamingDot) {
                StreamingPulseDot()
            }
        }
        if (level == 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(tokens.outline)
            )
        }
    }
}

@Composable
private fun MarkdownBlockquoteView(lines: List<String>) {
    val tokens = NexusThemeTokens.colors
    val joined = remember(lines) { lines.joinToString("\n") }
    val annotated = remember(joined) { buildInlineMarkdownAnnotatedString(joined) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
            .background(NexusAccent.copy(alpha = 0.07f))
            .border(
                width = 1.dp,
                color = NexusAccent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .fillMaxHeight()
                .background(NexusAccent)
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 21.sp
            ),
            color = tokens.aiText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MarkdownTableView(
    headers: List<String>,
    rows: List<List<String>>
) {
    val tokens = NexusThemeTokens.colors
    val colCount = remember(headers, rows) {
        maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("markdown_table_view"),
        shape = RoundedCornerShape(12.dp),
        color = tokens.surface,
        border = BorderStroke(1.dp, tokens.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(tokens.surfaceVariant)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (colIdx in 0 until colCount) {
                        val headerText = headers.getOrElse(colIdx) { "" }
                        val annotatedHeader = remember(headerText) {
                            buildInlineMarkdownAnnotatedString(headerText)
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 240.dp)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = annotatedHeader,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                ),
                                color = tokens.onSurface
                            )
                        }
                    }
                }

                // Divider under header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(tokens.outline)
                )

                // Table Body Rows
                rows.forEachIndexed { rowIdx, rowCells ->
                    val rowBg = if (rowIdx % 2 == 1) {
                        tokens.surfaceVariant.copy(alpha = 0.42f)
                    } else {
                        Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        for (colIdx in 0 until colCount) {
                            val cellText = rowCells.getOrElse(colIdx) { "" }
                            val annotatedCell = remember(cellText) {
                                buildInlineMarkdownAnnotatedString(cellText)
                            }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 110.dp, max = 240.dp)
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = annotatedCell,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    ),
                                    color = tokens.aiText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTaskItemView(
    checked: Boolean,
    text: String,
    showStreamingDot: Boolean
) {
    val tokens = NexusThemeTokens.colors
    val annotated = remember(text) { buildInlineMarkdownAnnotatedString(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) NexusOk.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    width = 1.2.dp,
                    color = if (checked) NexusOk else tokens.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed task",
                    tint = NexusOk,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                color = tokens.aiText
            )
            if (showStreamingDot) {
                Spacer(modifier = Modifier.width(4.dp))
                StreamingPulseDot()
            }
        }
    }
}

@Composable
private fun MarkdownBulletItemView(
    indentLevel: Int,
    text: String,
    showStreamingDot: Boolean
) {
    val tokens = NexusThemeTokens.colors
    val annotated = remember(text) { buildInlineMarkdownAnnotatedString(text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 14).dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.5.dp)
                .size(if (indentLevel == 0) 6.dp else 5.dp)
                .clip(CircleShape)
                .background(if (indentLevel == 0) NexusAccent else tokens.onSurfaceVariant)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.8.sp,
                    lineHeight = 22.5.sp
                ),
                color = tokens.aiText
            )
            if (showStreamingDot) {
                Spacer(modifier = Modifier.width(4.dp))
                StreamingPulseDot()
            }
        }
    }
}

@Composable
private fun MarkdownOrderedItemView(
    numberLabel: String,
    text: String,
    showStreamingDot: Boolean
) {
    val tokens = NexusThemeTokens.colors
    val annotated = remember(text) { buildInlineMarkdownAnnotatedString(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = numberLabel,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 22.sp
            ),
            color = NexusAccent,
            modifier = Modifier.widthIn(min = 22.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.8.sp,
                    lineHeight = 22.5.sp
                ),
                color = tokens.aiText
            )
            if (showStreamingDot) {
                Spacer(modifier = Modifier.width(4.dp))
                StreamingPulseDot()
            }
        }
    }
}

@Composable
fun StreamingPulseDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streamingDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(7.dp)
            .graphicsLayer { alpha = dotAlpha }
            .clip(CircleShape)
            .background(NexusAccent)
    )
}

private val INLINE_TOKEN_REGEX = Regex(
    """(`[^`\n]+`|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~|\[[^\]\n]+\]\([^)\n]+\)|(?<!\*)\*[^*\s][^*\n]*?\*(?!\*))"""
)

private fun buildInlineMarkdownAnnotatedString(
    text: String
): AnnotatedString = buildAnnotatedString {
    var lastIdx = 0
    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
        if (match.range.first > lastIdx) {
            append(text.substring(lastIdx, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length > 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        color = NexusAccent,
                        background = NexusAccent.copy(alpha = 0.11f)
                    )
                ) {
                    append(" " + token.substring(1, token.length - 1) + " ")
                }
            }

            (token.startsWith("**") && token.endsWith("**") && token.length > 4) ||
                (token.startsWith("__") && token.endsWith("__") && token.length > 4) -> {
                val inner = token.substring(2, token.length - 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(inner)
                }
            }

            token.startsWith("~~") && token.endsWith("~~") && token.length > 4 -> {
                val inner = token.substring(2, token.length - 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(inner)
                }
            }

            token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
                val closeBracket = token.indexOf("](")
                val label = token.substring(1, closeBracket)
                withStyle(
                    SpanStyle(
                        color = NexusAccent,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(label)
                }
            }

            token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                val inner = token.substring(1, token.length - 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inner)
                }
            }

            else -> append(token)
        }
        lastIdx = match.range.last + 1
    }
    if (lastIdx < text.length) {
        append(text.substring(lastIdx))
    }
}

private fun evaluateRiskBadgeLevel(command: String, cwd: String, isRisky: Boolean): String {
    if (isRisky) return "high"
    return when (com.example.domain.ToolCallParser.classifyRisk(command, cwd)) {
        com.example.domain.CommandRiskLevel.HIGH -> "high"
        com.example.domain.CommandRiskLevel.MEDIUM -> "medium"
        com.example.domain.CommandRiskLevel.LOW -> "low"
    }
}

/**
 * Strips any legacy or inline model stats / runtime telemetry lines from the message body so
 * model stats are never displayed in the chat bubble unless the user expands the "Message info" dropdown.
 */
internal fun stripLegacyModelStatsFromMessage(rawText: String): Pair<String, List<String>> {
    val extractedStats = mutableListOf<String>()
    val keptLines = mutableListOf<String>()

    for (line in rawText.lines()) {
        val trimmed = line.trim()
        val isStatsLine =
            (trimmed.startsWith("*[On-Device GGUF") && trimmed.endsWith("]*")) ||
                (trimmed.startsWith("[On-Device GGUF") && trimmed.endsWith("]")) ||
                trimmed.startsWith("Processed locally on-device with ") ||
                trimmed.startsWith("- **Runtime Config**:") ||
                trimmed.startsWith("- Runtime Config:") ||
                trimmed.startsWith("- **Measured Speed**:") ||
                trimmed.startsWith("- Measured Speed:") ||
                trimmed.startsWith("- **Thermal / Power**:") ||
                trimmed.startsWith("- Thermal / Power:") ||
                trimmed.startsWith("- **Recommendation**:") ||
                trimmed.startsWith("> **Optimizer Tip:**")

        if (isStatsLine) {
            val cleanStat = trimmed
                .removePrefix("*[")
                .removeSuffix("]*")
                .removePrefix("- ")
                .replace("**", "")
                .trim()
            if (cleanStat.isNotEmpty()) {
                extractedStats.add(cleanStat)
            }
        } else {
            keptLines.add(line)
        }
    }

    val cleanedBody = keptLines.joinToString("\n").trim()
    val finalBody = if (cleanedBody.isNotBlank()) {
        cleanedBody
    } else {
        "Hello! How can I help you today? Ask a question, request code (such as an HTML landing page or script), or specify a terminal command."
    }
    return finalBody to extractedStats
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptEntryItem(
    entry: TranscriptEntry,
    activeProviderLabel: String = "Google Gemini",
    activeModelId: String = "gemini-3.8-flash",
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    feedbackState: String? = null,
    laterEntriesCount: Int = 0,
    onApproveCommand: (TranscriptEntry) -> Unit,
    onDenyCommand: (TranscriptEntry) -> Unit,
    onAnalyzeWithGemini: (TranscriptEntry) -> Unit,
    onSwitchToTerminal: () -> Unit = {},
    onRegenerate: ((TranscriptEntry) -> Unit)? = null,
    onEditAndResubmit: ((TranscriptEntry, String) -> Unit)? = null,
    onToggleFeedback: ((TranscriptEntry, String) -> Unit)? = null,
    onRetryAction: (() -> Unit)? = null,
    onGrantFileAccess: (() -> Unit)? = null,
    onRequestSignIn: (() -> Unit)? = null,
    onOpenEnvironmentSetup: (() -> Unit)? = null,
    onFallbackLimitedShell: ((String) -> Unit)? = null,
    onRerunAsBackgroundJob: ((String) -> Unit)? = null,
    onUseTermuxHome: (() -> Unit)? = null,
    onMoveProjectToTermuxHome: ((String) -> Unit)? = null,
    onKeepProjectInSharedStorage: (() -> Unit)? = null,
    onConfirmProjectSlug: ((String, Boolean) -> Unit)? = null,
    onImportMirrorFromShared: (() -> Unit)? = null,
    onSyncMirrorNow: (() -> Unit)? = null
) {
    val tokens = NexusThemeTokens.colors
    val clipboard = LocalClipboardManager.current
    var copiedMsg by remember { mutableStateOf(false) }
    var selectedTextForSheet by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(copiedMsg) {
        if (copiedMsg) {
            kotlinx.coroutines.delay(1500L)
            copiedMsg = false
        }
    }

    when (entry.type) {
        "user" -> {
            var isEditing by remember(entry.id) { mutableStateOf(false) }
            var editDraft by remember(entry.id, entry.text) { mutableStateOf(entry.text) }
            var showContextMenu by remember(entry.id) { mutableStateOf(false) }
            val mutedTint = tokens.aiSecondaryText.copy(alpha = 0.80f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isEditing) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = tokens.surface,
                        border = BorderStroke(1.5.dp, NexusAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .testTag("user_msg_inline_editor_${entry.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DisableSelection {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = NexusWarn.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("user_msg_edit_warning_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "History truncation warning",
                                            tint = NexusWarn,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        val countMsg = if (laterEntriesCount > 0) {
                                            "Submitting removes $laterEntriesCount later message${if (laterEntriesCount == 1) "" else "s"} and restarts from here. Earlier commands' effects on disk remain."
                                        } else {
                                            "Submitting replaces this prompt and restarts execution. Earlier commands' effects on disk remain."
                                        }
                                        Text(
                                            text = countMsg,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = tokens.onSurface
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = editDraft,
                                onValueChange = { editDraft = it },
                                minLines = 2,
                                maxLines = 8,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = tokens.onSurface,
                                    fontSize = 14.5.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NexusAccent,
                                    unfocusedBorderColor = tokens.outline,
                                    focusedContainerColor = tokens.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = tokens.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_msg_edit_input_${entry.id}")
                            )

                            DisableSelection {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editDraft = entry.text
                                            isEditing = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, tokens.outline),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("user_msg_edit_cancel_${entry.id}")
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tokens.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val trimmed = editDraft.trim()
                                            if (trimmed.isNotBlank()) {
                                                isEditing = false
                                                onEditAndResubmit?.invoke(entry, trimmed)
                                            }
                                        },
                                        enabled = editDraft.trim().isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NexusAccent,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("user_msg_edit_submit_${entry.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Resubmit prompt",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Resubmit",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = 20.dp,
                                bottomEnd = 6.dp
                            ),
                            color = tokens.userBubble,
                            border = BorderStroke(1.dp, tokens.outline),
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .testTag("user_message_bubble_${entry.id}")
                                .pointerInput(entry.id) {
                                    detectTapGestures(
                                        onLongPress = { showContextMenu = true }
                                    )
                                }
                        ) {
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 14.5.sp,
                                    lineHeight = 21.sp
                                ),
                                color = tokens.onUserBubble,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false }
                        ) {
                            if (onEditAndResubmit != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit & Resubmit", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    },
                                    onClick = {
                                        showContextMenu = false
                                        editDraft = entry.text
                                        isEditing = true
                                    },
                                    modifier = Modifier.testTag("user_msg_menu_edit_${entry.id}")
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Copy message", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    clipboard.setText(AnnotatedString(entry.text))
                                    copiedMsg = true
                                },
                                modifier = Modifier.testTag("user_msg_menu_copy_${entry.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("Select text", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    selectedTextForSheet = entry.text
                                },
                                modifier = Modifier.testTag("user_msg_menu_select_${entry.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, entry.text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share prompt"))
                                },
                                modifier = Modifier.testTag("user_msg_menu_share_${entry.id}")
                            )
                        }
                    }

                    // Inline action row beneath user prompt bubble wrapped in DisableSelection (Amendment #6)
                    DisableSelection {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (onEditAndResubmit != null) {
                                Surface(
                                    onClick = {
                                        editDraft = entry.text
                                        isEditing = true
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.testTag("user_msg_edit_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit prompt",
                                            tint = mutedTint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Edit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = mutedTint
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(entry.text))
                                    copiedMsg = true
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("user_msg_copy_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = if (copiedMsg) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = if (copiedMsg) "Copied" else "Copy prompt",
                                    tint = if (copiedMsg) NexusOk else mutedTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { showContextMenu = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("user_msg_more_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Prompt options menu",
                                    tint = mutedTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        "agent", "analysis" -> {
            var showMessageInfo by remember(entry.id) { mutableStateOf(false) }
            var showAiActionMenu by remember(entry.id) { mutableStateOf(false) }
            val (cleanMarkdown, extractedTelemetryLines) = remember(entry.text) {
                stripLegacyModelStatsFromMessage(entry.text)
            }

            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("ai_message_block_${entry.id}")
                        .pointerInput(entry.id) {
                            detectTapGestures(
                                onLongPress = { showAiActionMenu = true }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarkdownMessageContent(markdown = cleanMarkdown, isStreaming = false)

                    // Small muted action row under AI message wrapped in DisableSelection (Amendment #6)
                    DisableSelection {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                            val mutedTint = tokens.aiSecondaryText.copy(alpha = 0.80f)

                            // Copy
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(cleanMarkdown))
                                    copiedMsg = true
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("ai_msg_copy_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = if (copiedMsg) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = if (copiedMsg) "Copied" else "Copy message",
                                    tint = if (copiedMsg) NexusOk else mutedTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Regenerate
                            if (onRegenerate != null) {
                                IconButton(
                                    onClick = { onRegenerate(entry) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("ai_msg_regenerate_${entry.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate response",
                                        tint = mutedTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Thumbs Up
                            val isUp = feedbackState == "up"
                            IconButton(
                                onClick = { onToggleFeedback?.invoke(entry, "up") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("ai_msg_thumb_up_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = if (isUp) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Helpful response",
                                    tint = if (isUp) NexusAccent else mutedTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Thumbs Down
                            val isDown = feedbackState == "down"
                            IconButton(
                                onClick = { onToggleFeedback?.invoke(entry, "down") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("ai_msg_thumb_down_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = if (isDown) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Unhelpful response",
                                    tint = if (isDown) NexusDanger else mutedTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Dropdown button to view message info (collapsed by default)
                            Surface(
                                onClick = { showMessageInfo = !showMessageInfo },
                                shape = RoundedCornerShape(999.dp),
                                color = if (showMessageInfo) NexusAccent.copy(alpha = 0.10f) else Color.Transparent,
                                border = if (showMessageInfo) BorderStroke(1.dp, NexusAccent.copy(alpha = 0.30f)) else null,
                                modifier = Modifier.testTag("ai_msg_info_dropdown_${entry.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "View message info",
                                        tint = if (showMessageInfo) NexusAccent else mutedTint,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Message info",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (showMessageInfo) NexusAccent else mutedTint,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = if (showMessageInfo) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (showMessageInfo) "Hide message info" else "Show message info",
                                        tint = if (showMessageInfo) NexusAccent else mutedTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Expandable Message Info Panel — ONLY visible when user triggers the dropdown button
                        AnimatedVisibility(visible = showMessageInfo) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.surfaceVariant,
                                border = BorderStroke(1.dp, tokens.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("ai_msg_info_panel_${entry.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val modelLabel = if (activeModelId.isNotBlank()) {
                                        "$activeProviderLabel · ${formatShortModelName(activeModelId)}"
                                    } else {
                                        activeProviderLabel
                                    }
                                    Text(
                                        text = if (entry.type == "analysis") "Audit Insight · $modelLabel" else modelLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.onSurface,
                                        modifier = Modifier.testTag("ai_msg_provider_badge_${entry.id}")
                                    )
                                    if (activeModelId.isNotBlank()) {
                                        Text(
                                            text = "Model ID: $activeModelId",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = tokens.aiSecondaryText
                                        )
                                    }
                                    if (entry.cwd.isNotBlank()) {
                                        Text(
                                            text = "Working Directory: ${NexusToolAndRedactor.shortenPathMiddle(entry.cwd, 42)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = tokens.aiSecondaryText
                                        )
                                    }
                                    extractedTelemetryLines.forEach { statLine ->
                                        Text(
                                            text = statLine,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.aiSecondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showAiActionMenu,
                    onDismissRequest = { showAiActionMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy message", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        onClick = {
                            showAiActionMenu = false
                            clipboard.setText(AnnotatedString(cleanMarkdown))
                            copiedMsg = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select text", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        onClick = {
                            showAiActionMenu = false
                            selectedTextForSheet = cleanMarkdown
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        onClick = {
                            showAiActionMenu = false
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, cleanMarkdown)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share response"))
                        }
                    )
                }
            }
        }
    }

        "command" -> {
            PermissionGateCard(
                entry = entry,
                onApprove = { onApproveCommand(entry) },
                onDeny = { onDenyCommand(entry) },
                onAuditWithGemini = { onAnalyzeWithGemini(entry) },
                onSwitchToTerminal = onSwitchToTerminal
            )
        }

        "output" -> {
            ToolOutputCard(
                entry = entry,
                onAnalyzeWithGemini = { onAnalyzeWithGemini(entry) },
                onSwitchToTerminal = onSwitchToTerminal
            )
        }

        "notice" -> {
            val kind = entry.noticeKind.lowercase()
            val isError = kind == "error" || kind == "model_unavailable" || kind == "working_dir_unreachable"
            val isPermission = kind == "permission"
            val isStepLimit = kind == "step_limit"
            val isGuestLimit = kind == "guest_limit"
            val isQuickCommand = kind == "quick_command"
            val isSymlinkMoveOffer = kind == "symlink_move_offer"
            val isProjectSlugProposal = kind == "project_slug_proposal"
            val isMirrorConflict = kind == "mirror_conflict"
            val isWorkingDirUnreachable = kind == "working_dir_unreachable"
            val isInfo = kind == "info" || isQuickCommand || isProjectSlugProposal
            val accent = when {
                isError -> NexusDanger
                isQuickCommand -> NexusOk
                isInfo -> NexusAccent
                isGuestLimit -> NexusAccent
                else -> NexusWarn
            }
            val isSetupNeeded = kind == "setup_needed"
            val isFallbackPrompt = kind == "fallback_prompt"
            val isTimeoutBgOffer = kind == "timeout_bg_offer" ||
                (isError && (entry.text.contains("timed out", ignoreCase = true) || entry.text.contains("timeout", ignoreCase = true)))
            val showBgRerunButton = (isTimeoutBgOffer || (entry.command.isNotBlank() && com.example.domain.ServerLogAnalyzer.shouldRunAsBackgroundJob(entry.command))) &&
                onRerunAsBackgroundJob != null && entry.command.isNotBlank()
            val showSetupButton = (isSetupNeeded || isWorkingDirUnreachable) && onOpenEnvironmentSetup != null
            val showFallbackButton = isFallbackPrompt && onFallbackLimitedShell != null
            val showRetryButton = (isError || isStepLimit || isPermission) && onRetryAction != null
            val showGrantAccessButton = (isPermission || isWorkingDirUnreachable) && onGrantFileAccess != null
            val showSignInButton = isGuestLimit && onRequestSignIn != null

            val initialProposedSlug = remember(entry.text) {
                entry.text.substringAfter("/storage/emulated/0/Nexus/", "")
                    .substringBefore("`")
                    .trim()
                    .ifBlank { "project" }
            }
            var slugDraft by remember(entry.id) { mutableStateOf(initialProposedSlug) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notice_card_${entry.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isQuickCommand) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusOk.copy(alpha = 0.16f),
                                    modifier = Modifier.testTag("quick_command_badge_${entry.id}")
                                ) {
                                    Text(
                                        text = "Quick command",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NexusOk,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.onSurface
                            )
                        }
                    }

                    if (isProjectSlugProposal && onConfirmProjectSlug != null) {
                        DisableSelection {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = slugDraft,
                                    onValueChange = { slugDraft = it },
                                    singleLine = true,
                                    label = { Text("Project folder slug", style = MaterialTheme.typography.labelSmall) },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("project_slug_input_${entry.id}")
                                )
                                Button(
                                    onClick = { onConfirmProjectSlug(slugDraft, false) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NexusAccent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("confirm_project_slug_btn_${entry.id}")
                                ) {
                                    Text(
                                        text = "Confirm",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    DisableSelection {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSymlinkMoveOffer) {
                                if (onMoveProjectToTermuxHome != null) {
                                    Surface(
                                        onClick = { onMoveProjectToTermuxHome(entry.command) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = NexusAccent.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.45f)),
                                        modifier = Modifier.testTag("move_to_termux_home_btn_${entry.id}")
                                    ) {
                                        Text(
                                            text = "Move to Termux home",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = NexusAccent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                if (onKeepProjectInSharedStorage != null) {
                                    Surface(
                                        onClick = onKeepProjectInSharedStorage,
                                        shape = RoundedCornerShape(999.dp),
                                        color = tokens.surfaceVariant,
                                        border = BorderStroke(1.dp, tokens.outline),
                                        modifier = Modifier.testTag("keep_in_shared_storage_btn_${entry.id}")
                                    ) {
                                        Text(
                                            text = "Keep here",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                            if (isWorkingDirUnreachable && onUseTermuxHome != null) {
                                Surface(
                                    onClick = onUseTermuxHome,
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("use_termux_home_notice_btn_${entry.id}")
                                ) {
                                    Text(
                                        text = "Use Termux home",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (isMirrorConflict) {
                                if (onImportMirrorFromShared != null) {
                                    Surface(
                                        onClick = onImportMirrorFromShared,
                                        shape = RoundedCornerShape(999.dp),
                                        color = NexusAccent.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("mirror_import_shared_btn_${entry.id}")
                                    ) {
                                        Text(
                                            text = "Import to Termux home (with backup)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = NexusAccent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                if (onSyncMirrorNow != null) {
                                    Surface(
                                        onClick = onSyncMirrorNow,
                                        shape = RoundedCornerShape(999.dp),
                                        color = NexusWarn.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("mirror_overwrite_shared_btn_${entry.id}")
                                    ) {
                                        Text(
                                            text = "Sync / Overwrite shared",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = NexusWarn,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                            if (showBgRerunButton) {
                                Surface(
                                    onClick = { onRerunAsBackgroundJob?.invoke(entry.command) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("rerun_bg_job_notice_button_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Re-run as background job",
                                            tint = NexusAccent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Re-run as background job",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = NexusAccent,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (showSetupButton) {
                                Surface(
                                    onClick = { onOpenEnvironmentSetup?.invoke() },
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("setup_needed_notice_button_${entry.id}")
                                ) {
                                    Text(
                                        text = if (isWorkingDirUnreachable) "termux-setup-storage guide" else "Set up environment",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusAccent,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (showFallbackButton) {
                                Surface(
                                    onClick = { onFallbackLimitedShell?.invoke(entry.command) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusWarn.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("fallback_limited_shell_button_${entry.id}")
                                ) {
                                    Text(
                                        text = "Run in limited shell",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusWarn,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (showGrantAccessButton) {
                                Surface(
                                    onClick = { onGrantFileAccess?.invoke() },
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusWarn.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("grant_file_access_notice_button_${entry.id}")
                                ) {
                                    Text(
                                        text = "Grant All Files Access",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusWarn,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (showSignInButton) {
                                Surface(
                                    onClick = { onRequestSignIn?.invoke() },
                                    shape = RoundedCornerShape(999.dp),
                                    color = NexusAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("sign_in_notice_button_${entry.id}")
                                ) {
                                    Text(
                                        text = "Sign in",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusAccent,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (showRetryButton) {
                                Surface(
                                    onClick = { onRetryAction?.invoke() },
                                    shape = RoundedCornerShape(999.dp),
                                    color = accent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("retry_notice_button_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Retry",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = accent,
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
        }
    }

    if (selectedTextForSheet != null) {
        SelectTextBottomSheet(
            text = selectedTextForSheet!!,
            onDismiss = { selectedTextForSheet = null }
        )
    }
}

/**
 * Isolated bottom sheet for static text selection.
 * Free of recompositions, animations, or streaming updates so Android's TextToolbar
 * never flickers or expands across the full screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SelectTextBottomSheet(
    text: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Text",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                text = "Tap and drag over text below to copy specific selections.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 380.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.5.sp,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermissionGateCard(
    entry: TranscriptEntry,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAuditWithGemini: () -> Unit,
    onSwitchToTerminal: () -> Unit
) {
    val tokens = NexusThemeTokens.colors
    val status = entry.commandStatus.ifBlank { "pending" }
    val isPending = status == "pending"
    val isApproved = status == "approved"
    val isDenied = status == "denied" || status == "cancelled"

    val riskLevel = remember(entry.command, entry.cwd, entry.isRisky) {
        evaluateRiskBadgeLevel(entry.command, entry.cwd, entry.isRisky)
    }

    val safetyAnalysis = remember(entry.command, entry.cwd) {
        com.example.domain.ToolCallParser.analyzeCommandSafety(
            entry.command,
            entry.cwd
        )
    }

    val activityVerb = remember(entry.command) {
        com.example.domain.ToolCallParser.activityVerbForCommand(entry.command)
    }

    val (riskBadgeBg, riskBadgeColor) = when (riskLevel) {
        "high" -> NexusDanger.copy(alpha = 0.12f) to NexusDanger
        "medium" -> NexusWarn.copy(alpha = 0.14f) to NexusWarn
        else -> NexusOk.copy(alpha = 0.12f) to NexusOk
    }

    val statusColor = when {
        isApproved -> NexusOk
        isDenied -> NexusDanger
        entry.isRisky || riskLevel == "high" -> NexusDanger
        else -> NexusWarn
    }

    val headerLabel = when {
        isApproved -> "$activityVerb · ${entry.reason}"
        status == "cancelled" -> "Cancelled · ${entry.reason}"
        isDenied -> "Denied · ${entry.reason}"
        else -> entry.reason.ifBlank { "Shell command requires approval" }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_card_${entry.id}"),
        shape = RoundedCornerShape(20.dp),
        color = tokens.surface,
        border = BorderStroke(
            1.dp,
            if (isPending && (entry.isRisky || riskLevel == "high")) NexusDanger.copy(alpha = 0.45f) else tokens.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (entry.isRisky || riskLevel == "high") Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = "Command approval status",
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = headerLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (safetyAnalysis.runsInBackground || entry.command.startsWith("start_job") || com.example.domain.ServerLogAnalyzer.shouldRunAsBackgroundJob(entry.command)) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NexusAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("bg_job_badge_${entry.id}")
                        ) {
                            Text(
                                text = "Runs in background",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusAccent,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = riskBadgeBg,
                        border = BorderStroke(1.dp, riskBadgeColor.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("risk_badge_${entry.id}")
                    ) {
                        Text(
                            text = "$riskLevel risk",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = riskBadgeColor,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${NexusToolAndRedactor.shortenPathMiddle(entry.cwd.ifBlank { "/" }, 28)} · ${entry.timeoutSec}s timeout",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = tokens.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onAuditWithGemini,
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.surfaceVariant,
                        border = BorderStroke(1.dp, tokens.outline),
                        modifier = Modifier.testTag("audit_command_btn_${entry.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Audit command",
                                tint = NexusAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Audit",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onSurface,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        onClick = onSwitchToTerminal,
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.surfaceVariant,
                        border = BorderStroke(1.dp, tokens.outline),
                        modifier = Modifier.testTag("track_in_terminal_cmd_${entry.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Track in Terminal",
                                tint = NexusAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Track in Terminal",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onSurface,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Package install / network / security metadata inside approval card (Part 2 #5)
            val hasSafetyMetadata = safetyAnalysis.packages.isNotEmpty() ||
                safetyAnalysis.registryHosts.isNotEmpty() ||
                safetyAnalysis.runsInstallScripts ||
                safetyAnalysis.touchesOutsideWorkspace ||
                safetyAnalysis.readsCredentials

            if (hasSafetyMetadata) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, tokens.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_safety_metadata_${entry.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (safetyAnalysis.packages.isNotEmpty()) {
                            Text(
                                text = "Packages: ${safetyAnalysis.packages.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = tokens.onSurface
                            )
                        }
                        if (safetyAnalysis.registryHosts.isNotEmpty()) {
                            Text(
                                text = "Registry / Host: ${safetyAnalysis.registryHosts.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = tokens.onSurfaceVariant
                            )
                        }
                        if (safetyAnalysis.runsInstallScripts) {
                            Text(
                                text = "Warning: Package postinstall/lifecycle scripts may execute during install.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusWarn
                            )
                        }
                        if (safetyAnalysis.touchesOutsideWorkspace) {
                            Text(
                                text = "Warning: Command accesses paths outside the current workspace directory.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusDanger
                            )
                        }
                        if (safetyAnalysis.readsCredentials) {
                            Text(
                                text = "Warning: Command inspects environment or credential files.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusDanger
                            )
                        }
                    }
                }
            }

            NexusCodeBlock(lang = "bash", code = entry.command)

            if (isPending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusOk,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("approve_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Approve and run",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Approve & run",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onDeny,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = NexusDanger
                        ),
                        modifier = Modifier.testTag("deny_command_button")
                    ) {
                        Text(
                            text = "Deny",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusDanger,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolOutputCard(
    entry: TranscriptEntry,
    onAnalyzeWithGemini: () -> Unit,
    onSwitchToTerminal: () -> Unit
) {
    val tokens = NexusThemeTokens.colors
    val isZeroExit = entry.exitCode == 0L
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tokens.surface,
        border = BorderStroke(1.dp, tokens.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetaChip(
                    text = "exit ${entry.exitCode}",
                    bgColor = if (isZeroExit) NexusOkSoft else NexusWarnSoft,
                    textColor = if (isZeroExit) NexusOk else NexusWarnText
                )
                if (entry.cwd.isNotBlank()) {
                    MetaChip(
                        text = NexusToolAndRedactor.shortenPathMiddle(entry.cwd, 34),
                        bgColor = tokens.surfaceVariant,
                        textColor = tokens.onSurfaceVariant
                    )
                }
                if (entry.redactions > 0L) {
                    MetaChip(
                        text = "${entry.redactions} secret${if (entry.redactions == 1L) "" else "s"} masked",
                        bgColor = NexusWarnSoft,
                        textColor = NexusWarnText
                    )
                }
                Surface(
                    onClick = onAnalyzeWithGemini,
                    shape = RoundedCornerShape(999.dp),
                    color = NexusAccentSoftLight.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.testTag("analyze_output_btn_${entry.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Explain output",
                            tint = NexusAccent,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Explain output",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusAccent,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    onClick = onSwitchToTerminal,
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.surfaceVariant,
                    border = BorderStroke(1.dp, tokens.outline),
                    modifier = Modifier.testTag("output_switch_terminal_btn_${entry.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Track in Terminal",
                            tint = tokens.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Track in Terminal",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onSurfaceVariant,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }

            NexusCodeBlock(lang = "output", code = entry.output)
        }
    }
}

@Composable
fun MetaChip(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NexusStatusBar(
    isBridgeOnline: Boolean,
    cwd: String,
    commandsCount: Long,
    timeoutSec: Long,
    redactionsCount: Long,
    onSessionsClick: () -> Unit
) {
    val tokens = NexusThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surfaceVariant)
            .border(1.dp, tokens.outline)
            .clickable { onSessionsClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("nexus_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isBridgeOnline) NexusOk else NexusDanger)
            )
            Text(
                text = if (isBridgeOnline) "online" else "offline",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onSurfaceVariant,
                softWrap = false,
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Working directory",
                tint = tokens.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = NexusToolAndRedactor.shortenPathMiddle(cwd.ifBlank { "/" }, 28),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = tokens.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "$commandsCount cmd${if (commandsCount == 1L) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.onSurfaceVariant,
            softWrap = false,
            maxLines = 1
        )

        if (redactionsCount > 0L) {
            MetaChip(
                text = "$redactionsCount redacted",
                bgColor = NexusWarnSoft,
                textColor = NexusWarnText
            )
        }
    }
}

@Preview(name = "User & AI Messages - Light Mode", showBackground = true, backgroundColor = 0xFFF7F6F3)
@Composable
private fun UserAndAiMessageLightPreview() {
    MyApplicationTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(NexusThemeTokens.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TranscriptEntryItem(
                entry = TranscriptEntry(
                    id = "preview_user_1",
                    type = "user",
                    text = "I need a marketing campaign idea for a new productivity app targeting remote workers."
                ),
                activeProviderLabel = "On-Device (Local GGUF)",
                activeModelId = "qwen2.5-coder-1.5b-q4_k_m",
                isDarkTheme = false,
                onApproveCommand = {},
                onDenyCommand = {},
                onAnalyzeWithGemini = {}
            )
            TranscriptEntryItem(
                entry = TranscriptEntry(
                    id = "preview_ai_1",
                    type = "agent",
                    text = "Great target audience. A campaign centered around **\"Work Smarter, Not Longer\"** could resonate well. The goal would be to help remote professionals reclaim time and reduce burnout through smarter workflows."
                ),
                activeProviderLabel = "On-Device (Local GGUF)",
                activeModelId = "qwen2.5-coder-1.5b-q4_k_m",
                isDarkTheme = false,
                onApproveCommand = {},
                onDenyCommand = {},
                onAnalyzeWithGemini = {}
            )
        }
    }
}

@Preview(name = "User & AI Messages - Dark Mode", showBackground = true, backgroundColor = 0xFF0B0D11)
@Composable
private fun UserAndAiMessageDarkPreview() {
    MyApplicationTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(NexusThemeTokens.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TranscriptEntryItem(
                entry = TranscriptEntry(
                    id = "preview_user_dark_1",
                    type = "user",
                    text = "Can you inspect the workspace logs and summarize any errors?"
                ),
                activeProviderLabel = "Anthropic Claude",
                activeModelId = "claude-sonnet-4-20250514",
                isDarkTheme = true,
                onApproveCommand = {},
                onDenyCommand = {},
                onAnalyzeWithGemini = {}
            )
            TranscriptEntryItem(
                entry = TranscriptEntry(
                    id = "preview_ai_dark_1",
                    type = "agent",
                    text = "I inspected `/nexus_workspace` and verified that all local benchmarks passed at **28.4 tok/s**."
                ),
                activeProviderLabel = "Anthropic Claude",
                activeModelId = "claude-sonnet-4-20250514",
                isDarkTheme = true,
                onApproveCommand = {},
                onDenyCommand = {},
                onAnalyzeWithGemini = {}
            )
        }
    }
}
