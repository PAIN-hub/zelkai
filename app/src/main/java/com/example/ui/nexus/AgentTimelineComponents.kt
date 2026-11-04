package com.example.ui.nexus

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ThoughtProcessVisibility
import com.example.domain.TimelineBlock
import com.example.domain.ToolGroupExpandMode
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusBgDark
import com.example.ui.theme.NexusBgLight
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.sin

/**
 * Clean monochrome Lucide-style Lightning Bolt vector icon.
 * Stroke width 1.75dp, 24x24 viewport, matching the app's dark neutral hierarchy.
 */
@Composable
fun LucideBoltIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.55f, h * 0.05f)
            lineTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.48f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.95f)
            lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.52f, h * 0.45f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.75f * density)
        )
    }
}

/**
 * 1) THOUGHT PROCESS ROW (ThinkingRow)
 *
 * Muted brain icon + "Thought Process" + chevron.
 * Shown ONLY when real reasoning exists (<think> or cloud thinking).
 * Live streaming: "Thinking… Xs" with shimmer.
 * Finished: "Thought for Xs". Redacted, capped at ~8 KB, excluded from model history.
 */
@Composable
fun ThinkingRow(
    thinkingBlock: TimelineBlock.ThinkingBlock,
    visibilityPreference: ThoughtProcessVisibility = ThoughtProcessVisibility.COLLAPSED,
    modifier: Modifier = Modifier
) {
    if (visibilityPreference == ThoughtProcessVisibility.HIDDEN && !thinkingBlock.isLive) {
        return
    }

    var isExpanded by rememberSaveable(thinkingBlock.blockId) {
        mutableStateOf(false)
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "thinking_chevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("timeline_thinking_row")
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Thought Process",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )

            val labelText = when {
                thinkingBlock.isLive -> "Thinking… (${thinkingBlock.durationSec}s)"
                thinkingBlock.durationSec > 0 -> "Thought for ${thinkingBlock.durationSec}s"
                else -> "Thought Process"
            }

            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse thought process" else "Expand thought process",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotationAngle)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                SelectionContainer {
                    val safeText = remember(thinkingBlock.text) {
                        thinkingBlock.text.take(8192)
                    }
                    Text(
                        text = safeText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2) TOOL GROUP ROW (ToolGroupRow)
 *
 * 48dp header with:
 * - Lightning bolt icon + count badge + dynamic label
 * - Status indicator dot / spinner
 * - Chevron: down when expanded, right when collapsed
 *
 * Expanded view:
 * - Curved branch connector tree line (Canvas)
 * - Bullet dot + plain-language titles (from TitleSanitizer / ActivityLabeler)
 * - Duration + status dot
 * - Tappable nested details: exact command/path, exit code, redacted output capped at 200 lines,
 *   Copy / Explain / Open in Terminal / Read-only file viewer.
 */
@Composable
fun ToolGroupRow(
    toolGroup: TimelineBlock.ToolGroup,
    expandMode: ToolGroupExpandMode = ToolGroupExpandMode.COLLAPSED,
    onAuditCommand: (String) -> Unit = {},
    onExplainOutput: (String) -> Unit = {},
    onOpenInTerminal: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val forceExpanded = toolGroup.hasPendingApproval || toolGroup.steps.any { it.isRisky }
    val defaultExpanded = when (expandMode) {
        ToolGroupExpandMode.EXPANDED -> true
        ToolGroupExpandMode.EXPANDED_WHILE_RUNNING ->
            toolGroup.overallState == TimelineBlock.ToolGroupOverallState.RUNNING || forceExpanded
        ToolGroupExpandMode.COLLAPSED -> forceExpanded
    }

    var isGroupExpanded by rememberSaveable(toolGroup.blockId) {
        mutableStateOf(defaultExpanded)
    }

    val expandedStepIds = rememberSaveable { mutableStateMapOf<String, Boolean>() }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isGroupExpanded) 90f else 0f,
        label = "tool_group_chevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("timeline_tool_group_row")
    ) {
        // Header
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { isGroupExpanded = !isGroupExpanded }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LucideBoltIcon(
                tint = when (toolGroup.overallState) {
                    TimelineBlock.ToolGroupOverallState.RUNNING -> NexusAccent
                    TimelineBlock.ToolGroupOverallState.WAITING_APPROVAL -> NexusWarn
                    TimelineBlock.ToolGroupOverallState.COMPLETED_WITH_FAILURE -> NexusDanger
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = toolGroup.headerLabel(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (toolGroup.overallState) {
                TimelineBlock.ToolGroupOverallState.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = NexusAccent
                    )
                }
                TimelineBlock.ToolGroupOverallState.WAITING_APPROVAL -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NexusWarn)
                    )
                }
                TimelineBlock.ToolGroupOverallState.COMPLETED_WITH_FAILURE -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NexusDanger)
                    )
                }
                else -> Unit
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isGroupExpanded) "Collapse tool group" else "Expand tool group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotationAngle)
            )
        }

        // Expanded Step Tree
        AnimatedVisibility(
            visible = isGroupExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 2.dp, end = 4.dp)
            ) {
                toolGroup.steps.forEachIndexed { index, step ->
                    val isStepExpanded = expandedStepIds[step.id] ?: false
                    val isLast = index == toolGroup.steps.lastIndex

                    ToolStepTreeItem(
                        step = step,
                        isLast = isLast,
                        isExpanded = isStepExpanded,
                        onToggleExpand = {
                            expandedStepIds[step.id] = !isStepExpanded
                        },
                        onAuditCommand = onAuditCommand,
                        onExplainOutput = onExplainOutput,
                        onOpenInTerminal = onOpenInTerminal
                    )
                }
            }
        }
    }
}

/**
 * Single tree node with curved connector branch and tappable details.
 */
@Composable
private fun ToolStepTreeItem(
    step: TimelineBlock.ToolStepItem,
    isLast: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAuditCommand: (String) -> Unit,
    onExplainOutput: (String) -> Unit,
    onOpenInTerminal: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val treeLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Curved branch connector Canvas
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .height(if (isExpanded) 120.dp else 36.dp)
        ) {
            val strokeW = 1.5f * density
            val branchY = 18f * density
            val spineX = 6f * density
            val endX = size.width

            // Vertical spine
            drawLine(
                color = treeLineColor,
                start = Offset(spineX, 0f),
                end = Offset(spineX, if (isLast) branchY else size.height),
                strokeWidth = strokeW
            )

            // Curved branch to bullet dot (╰)
            val path = Path().apply {
                moveTo(spineX, branchY - 8f * density)
                quadraticBezierTo(
                    spineX, branchY,
                    spineX + 8f * density, branchY
                )
                lineTo(endX - 4f * density, branchY)
            }
            drawPath(
                path = path,
                color = treeLineColor,
                style = Stroke(width = strokeW)
            )

            // Bullet dot
            val dotColor = when (step.status) {
                TimelineBlock.ToolStepStatus.SUCCESS -> Color(0xFF6B7280)
                TimelineBlock.ToolStepStatus.RUNNING -> NexusAccent
                TimelineBlock.ToolStepStatus.PENDING_APPROVAL -> NexusWarn
                TimelineBlock.ToolStepStatus.FAILED, TimelineBlock.ToolStepStatus.TIMED_OUT -> NexusDanger
                TimelineBlock.ToolStepStatus.DENIED -> Color(0xFF9CA3AF)
            }
            drawCircle(
                color = dotColor,
                radius = 3f * density,
                center = Offset(endX - 2f * density, branchY)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            // Tappable step title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (step.status == TimelineBlock.ToolStepStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = NexusAccent
                    )
                } else if (step.exitCode != 0L && step.status != TimelineBlock.ToolStepStatus.PENDING_APPROVAL) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NexusDanger.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, NexusDanger.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "exit ${step.exitCode}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = NexusDanger,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Step details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Expanded nested details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Raw command or file path
                        if (!step.filePath.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Path:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SelectionContainer {
                                    Text(
                                        text = step.filePath,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SelectionContainer {
                                    Text(
                                        text = step.command,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Redacted Output (capped at 200 lines)
                        val rawOutput = if (!step.filePath.isNullOrBlank()) {
                            step.fileContent.orEmpty().ifBlank { step.output }
                        } else {
                            step.output
                        }

                        if (rawOutput.isNotBlank()) {
                            var showFullOutput by remember { mutableStateOf(false) }
                            val lines = remember(rawOutput) { rawOutput.lines() }
                            val displayLines = remember(lines, showFullOutput) {
                                if (!showFullOutput && lines.size > 200) lines.take(200) else lines
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(6.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = displayLines.joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (lines.size > 200) {
                                    Text(
                                        text = if (showFullOutput) "Show fewer lines" else "Show ${lines.size - 200} more lines",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = NexusAccent
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showFullOutput = !showFullOutput }
                                            .padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }

                        // Action Bar: Copy / Audit / Explain / Terminal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var copied by remember { mutableStateOf(false) }

                            Surface(
                                onClick = {
                                    val toCopy = if (rawOutput.isNotBlank()) rawOutput else step.command
                                    clipboardManager.setText(AnnotatedString(toCopy))
                                    copied = true
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (copied) NexusOk else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (copied) "Copied" else "Copy",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (step.command.isNotBlank()) {
                                Surface(
                                    onClick = { onAuditCommand(step.command) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Audit",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Surface(
                                    onClick = { onExplainOutput(step.command) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Explain",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Surface(
                                    onClick = { onOpenInTerminal(step.command) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = "Terminal",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Terminal",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 3) ANIMATED WORKING INDICATOR (AgentWorkingIndicator)
 *
 * 4 tilted dots waving indicator matching the user reference screenshot (••••).
 * Drawn in draw phase using Canvas, zero jank, respecting system reduce-motion settings.
 * Displays "Loading model…" if an on-device model is loading.
 */
@Composable
fun AgentWorkingIndicator(
    isLoadingModel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val transition = rememberInfiniteTransition(label = "agent_working_dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("agent_working_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)

        if (reduceMotion) {
            Text(
                text = if (isLoadingModel) "Loading model…" else "••••",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = dotColor
            )
        } else {
            Canvas(
                modifier = Modifier
                    .width(42.dp)
                    .height(16.dp)
            ) {
                val dotCount = 4
                val dotRadius = 2.2f * density
                val spacing = size.width / (dotCount + 1)
                val centerY = size.height / 2f
                val amplitude = 2.5f * density

                for (i in 0 until dotCount) {
                    val cx = spacing * (i + 1)
                    val waveOffset = (sin(phase + i * 0.8f)).toFloat() * amplitude
                    val cy = centerY + waveOffset
                    val alpha = (0.45f + 0.55f * ((sin(phase + i * 0.8f) + 1f) / 2f)).coerceIn(0.2f, 1f)

                    drawCircle(
                        color = dotColor.copy(alpha = alpha),
                        radius = dotRadius,
                        center = Offset(cx, cy)
                    )
                }
            }

            if (isLoadingModel) {
                Text(
                    text = "Loading model…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =======================================================================
// PREVIEWS
// =======================================================================

private val sampleThinkingBlock = TimelineBlock.ThinkingBlock(
    blockId = "think_preview_1",
    text = "The user asked to write a classic Snake game in Python.\n" +
        "Plan:\n" +
        "1. Check if pygame is installed with `python3 -m pip show pygame`.\n" +
        "2. If missing, install it.\n" +
        "3. Create snake.py with score tracking, pause, and increasing difficulty.",
    durationSec = 8,
    isLive = false
)

private val sampleToolGroup = TimelineBlock.ToolGroup(
    blockId = "tool_group_preview_1",
    steps = listOf(
        TimelineBlock.ToolStepItem(
            id = "step_1",
            title = "Verify pygame installed",
            command = "python3 -m pip show pygame",
            cwd = "/storage/emulated/0/Nexus/snake_game",
            status = TimelineBlock.ToolStepStatus.SUCCESS,
            exitCode = 0,
            output = "Name: pygame\nVersion: 2.6.1\nSummary: Python Game Development\nLocation: /data/data/com.termux/files/usr/lib/python3.11/site-packages",
            redactions = 0,
            isRisky = false
        ),
        TimelineBlock.ToolStepItem(
            id = "step_2",
            title = "Create snake.py",
            command = "",
            cwd = "/storage/emulated/0/Nexus/snake_game",
            status = TimelineBlock.ToolStepStatus.SUCCESS,
            exitCode = 0,
            output = "Wrote 184 lines to snake.py",
            redactions = 0,
            isRisky = false,
            filePath = "snake.py",
            fileContent = "import pygame\nimport sys\n# snake game implementation..."
        ),
        TimelineBlock.ToolStepItem(
            id = "step_3",
            title = "Run snake game test",
            command = "python3 -c 'import pygame; print(pygame.ver)'",
            cwd = "/storage/emulated/0/Nexus/snake_game",
            status = TimelineBlock.ToolStepStatus.SUCCESS,
            exitCode = 0,
            output = "2.6.1",
            redactions = 0,
            isRisky = false
        )
    ),
    overallState = TimelineBlock.ToolGroupOverallState.COMPLETED_SUCCESS
)

@Preview(name = "Light Mode", showBackground = true, backgroundColor = 0xFFF7F6F3)
@Composable
private fun AgentTimelineComponentsLightPreview() {
    MyApplicationTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThinkingRow(thinkingBlock = sampleThinkingBlock)
            Text(
                text = "I'll write a polished, complete Python game for you. Let me create a classic Snake game with modern polish — score tracking, high scores, pause, increasing difficulty, and clean visuals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            ToolGroupRow(toolGroup = sampleToolGroup, expandMode = ToolGroupExpandMode.EXPANDED)
            AgentWorkingIndicator()
        }
    }
}

@Preview(name = "Dark Mode", uiMode = UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF0B0D11)
@Composable
private fun AgentTimelineComponentsDarkPreview() {
    MyApplicationTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThinkingRow(thinkingBlock = sampleThinkingBlock)
            ToolGroupRow(toolGroup = sampleToolGroup, expandMode = ToolGroupExpandMode.EXPANDED)
            AgentWorkingIndicator()
        }
    }
}

@Preview(name = "Compact 320dp", widthDp = 320, showBackground = true, backgroundColor = 0xFFF7F6F3)
@Composable
private fun AgentTimelineComponentsCompactPreview() {
    MyApplicationTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(8.dp)) {
            ThinkingRow(thinkingBlock = sampleThinkingBlock)
            ToolGroupRow(toolGroup = sampleToolGroup, expandMode = ToolGroupExpandMode.EXPANDED)
        }
    }
}

@Preview(name = "Font Scale 2.0", fontScale = 2.0f, showBackground = true, backgroundColor = 0xFFF7F6F3)
@Composable
private fun AgentTimelineComponentsFontScalePreview() {
    MyApplicationTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(8.dp)) {
            ThinkingRow(thinkingBlock = sampleThinkingBlock)
            ToolGroupRow(toolGroup = sampleToolGroup, expandMode = ToolGroupExpandMode.COLLAPSED)
        }
    }
}

