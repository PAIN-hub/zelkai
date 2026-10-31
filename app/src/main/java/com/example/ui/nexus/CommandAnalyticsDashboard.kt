package com.example.ui.nexus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TranscriptEntry
import com.example.domain.NexusToolAndRedactor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandAnalyticsDashboard(
    entries: List<TranscriptEntry>,
    runtimeLatenciesMs: Map<String, Long>,
    isGuestMode: Boolean,
    onRunCommand: (String) -> Unit,
    onSwitchToTerminal: () -> Unit,
    onRequestSignIn: () -> Unit
) {
    var includeBenchmarks by remember { mutableStateOf(true) }
    val summary = remember(entries, runtimeLatenciesMs) {
        NexusToolAndRedactor.computeCommandAnalytics(
            entries = entries,
            runtimeLatenciesMs = runtimeLatenciesMs
        )
    }

    var selectedIndex by remember(summary.frequentCommands.size) {
        mutableIntStateOf(0)
    }
    val safeSelectedIdx = selectedIndex.coerceIn(0, (summary.frequentCommands.size - 1).coerceAtLeast(0))
    val selectedCommand = summary.frequentCommands.getOrNull(safeSelectedIdx)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "chartReveal"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("command_analytics_dashboard"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Dashboard Header & Live/Benchmark Toggle
        item(key = "dashboard_header") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        Brush.linearGradient(listOf(NexusAccent, NexusAccent2))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = "Command Telemetry Dashboard",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI Command Telemetry & Analytics",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Recharts / D3 Dual-Axis Execution Time (ms) & Success Rate (%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = { includeBenchmarks = !includeBenchmarks },
                            shape = RoundedCornerShape(999.dp),
                            color = if (includeBenchmarks) NexusAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("toggle_benchmark_data_chip")
                        ) {
                            Text(
                                text = if (includeBenchmarks) "Live + Benchmarks" else "Session Only",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = NexusAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (summary.pendingApprovalsCount > 0) {
                        Surface(
                            onClick = onSwitchToTerminal,
                            shape = RoundedCornerShape(10.dp),
                            color = NexusWarn.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Pending Approval",
                                        tint = NexusWarn,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${summary.pendingApprovalsCount} AI command(s) awaiting your approval",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NexusWarn
                                    )
                                }
                                Text(
                                    text = "Review in CLI →",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = NexusWarn
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Top KPI Metric Cards
        item(key = "kpi_cards_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "SUCCESS RATE",
                    value = String.format(Locale.US, "%.1f%%", summary.overallSuccessRatePercent),
                    subtitle = "${summary.totalExecutedCommands} total runs",
                    accentColor = NexusOk,
                    icon = Icons.Default.CheckCircle
                )
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "AVG LATENCY",
                    value = "${summary.averageExecutionMs} ms",
                    subtitle = "P95: ${summary.p95ExecutionMs} ms",
                    accentColor = NexusAccent,
                    icon = Icons.Default.Speed
                )
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "REDACTED",
                    value = "${summary.totalSecretsRedacted}",
                    subtitle = "${summary.deniedCommandsCount} denied",
                    accentColor = NexusAccent2,
                    icon = Icons.Default.Security
                )
            }
        }

        // 3. Recharts / D3 Composed Dual-Axis Bar + Line Chart (Execution Time ms & Success Rate %)
        item(key = "recharts_dual_axis_chart") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_composed_chart_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Execution Time (ms) vs. Success Rate (%)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap any bar to inspect command telemetry & re-execute",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Chart Legend (Recharts style)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LegendItem(color = NexusAccent, label = "Avg ms (Bar)")
                            LegendItem(color = NexusOk, label = "Success % (Line)")
                        }
                    }

                    if (summary.frequentCommands.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No shell commands executed in this session yet. Enable Benchmarks or run a command.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        RechartsComposedBarLineCanvas(
                            items = summary.frequentCommands,
                            selectedIndex = safeSelectedIdx,
                            animFraction = animProgress,
                            onSelectBar = { idx -> selectedIndex = idx }
                        )

                        // X-Axis Labels Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            summary.frequentCommands.forEachIndexed { idx, item ->
                                val isSelected = idx == safeSelectedIdx
                                Text(
                                    text = item.displayLabel.take(10),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { selectedIndex = idx }
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }

                        // Recharts Interactive Tooltip / Selected Command Inspector
                        if (selectedCommand != null) {
                            SelectedCommandTooltipCard(
                                item = selectedCommand,
                                onRunCommand = { onRunCommand(selectedCommand.fullCommandExample) }
                            )
                        }
                    }
                }
            }
        }

        // 4. D3 Radial Success Gauge + Chronological Execution Latency Area Chart
        item(key = "d3_radial_and_area_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // D3 Radial Donut Gauge
                Surface(
                    modifier = Modifier.weight(0.42f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "D3 Success Ratio",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier.size(112.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            D3RadialSuccessDonut(
                                successRatePercent = summary.overallSuccessRatePercent * animProgress
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", summary.overallSuccessRatePercent),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusOk
                                )
                                Text(
                                    text = "exit 0",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendItem(color = NexusOk, label = "Pass")
                            LegendItem(color = NexusDanger, label = "Fail")
                        }
                    }
                }

                // Chronological Latency Area Spline Chart
                Surface(
                    modifier = Modifier.weight(0.58f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = "Latency Trend",
                                    tint = NexusAccent2,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Latency Timeline (ms)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Last ${summary.recentTimeline.size} runs",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        D3LatencyAreaChart(
                            samples = summary.recentTimeline,
                            animFraction = animProgress
                        )

                        Text(
                            text = "Peak: ${summary.recentTimeline.maxOfOrNull { it.executionMs } ?: 0L} ms · Min: ${summary.recentTimeline.minOfOrNull { it.executionMs } ?: 0L} ms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 5. Guest Mode Cloud Telemetry Upsell (if in Guest Mode)
        if (isGuestMode) {
            item(key = "guest_analytics_notice") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NexusAccent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guest Mode Telemetry (Local Ephemeral Session)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = NexusAccent
                            )
                            Text(
                                text = "Sign in or sign up with Google to persist command analytics across devices and unlock multi-session history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onRequestSignIn,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // 6. Frequently Run AI Agent Commands Leaderboard
        item(key = "leaderboard_title") {
            Text(
                text = "FREQUENTLY RUN AI AGENT COMMANDS (${summary.frequentCommands.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        itemsIndexed(
            items = summary.frequentCommands,
            key = { _, item -> item.commandKey }
        ) { idx, item ->
            FrequentCommandLeaderboardRow(
                rank = idx + 1,
                item = item,
                isSelected = idx == safeSelectedIdx,
                onSelect = { selectedIndex = idx },
                onRunCommand = { onRunCommand(item.fullCommandExample) }
            )
        }
    }
}

@Composable
private fun KpiMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RechartsComposedBarLineCanvas(
    items: List<NexusToolAndRedactor.CommandTelemetryItem>,
    selectedIndex: Int,
    animFraction: Float,
    onSelectBar: (Int) -> Unit
) {
    val maxMs = remember(items) {
        max(250L, (items.maxOfOrNull { it.maxExecutionMs } ?: 250L) + 30L).toFloat()
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(195.dp)
            .pointerInput(items.size) {
                detectTapGestures { offset ->
                    if (items.isNotEmpty() && size.width > 0) {
                        val slotWidth = size.width.toFloat() / items.size.toFloat()
                        val tappedIdx = (offset.x / slotWidth).toInt().coerceIn(0, items.lastIndex)
                        onSelectBar(tappedIdx)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val topPad = 16.dp.toPx()
        val bottomPad = 12.dp.toPx()
        val chartH = (h - topPad - bottomPad).coerceAtLeast(1f)

        // Horizontal dashed D3/Recharts cartesian grid lines (25%, 50%, 75%, 100%)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        for (i in 0..4) {
            val y = topPad + chartH * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (i < 4) dashEffect else null
            )
        }

        if (items.isEmpty()) return@Canvas

        val slotW = w / items.size.toFloat()
        val barW = (slotW * 0.46f).coerceAtMost(38.dp.toPx())

        val linePoints = mutableListOf<Offset>()

        items.forEachIndexed { idx, item ->
            val centerX = slotW * idx + slotW / 2f
            val isSelected = idx == selectedIndex

            // Highlight column band for selected command (Recharts active cursor)
            if (isSelected) {
                drawRoundRect(
                    color = NexusAccent.copy(alpha = 0.09f),
                    topLeft = Offset(slotW * idx + 2.dp.toPx(), topPad / 2f),
                    size = Size(slotW - 4.dp.toPx(), chartH + topPad / 2f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }

            // Execution Time Bar (ms)
            val normMs = (item.avgExecutionMs.toFloat() / maxMs).coerceIn(0.05f, 1f) * animFraction
            val barH = chartH * normMs
            val barTop = topPad + (chartH - barH)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(NexusAccent, NexusAccent2)
                    } else {
                        listOf(NexusAccent.copy(alpha = 0.78f), NexusAccent2.copy(alpha = 0.60f))
                    },
                    startY = barTop,
                    endY = topPad + chartH
                ),
                topLeft = Offset(centerX - barW / 2f, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Min-Max Latency Whisker Line (D3 confidence interval)
            val minRatio = (item.minExecutionMs.toFloat() / maxMs).coerceIn(0f, 1f) * animFraction
            val maxRatio = (item.maxExecutionMs.toFloat() / maxMs).coerceIn(0f, 1f) * animFraction
            val yMax = topPad + chartH * (1f - maxRatio)
            val yMin = topPad + chartH * (1f - minRatio)
            drawLine(
                color = Color(0xFF94A3B8).copy(alpha = 0.7f),
                start = Offset(centerX, yMax),
                end = Offset(centerX, yMin),
                strokeWidth = 1.5.dp.toPx()
            )

            // Success Rate Point (%)
            val successRatio = (item.successRatePercent / 100f).coerceIn(0f, 1f) * animFraction
            val lineY = topPad + chartH * (1f - successRatio)
            linePoints.add(Offset(centerX, lineY))
        }

        // Draw smooth D3/Recharts line connecting success rates
        if (linePoints.size >= 2) {
            val linePath = Path().apply {
                moveTo(linePoints.first().x, linePoints.first().y)
                for (i in 1 until linePoints.size) {
                    val prev = linePoints[i - 1]
                    val cur = linePoints[i]
                    val midX = (prev.x + cur.x) / 2f
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }
            drawPath(
                path = linePath,
                color = NexusOk,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw nodes on the Success Rate line
        linePoints.forEachIndexed { idx, pt ->
            val isSelected = idx == selectedIndex
            drawCircle(
                color = Color.White,
                radius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = if (items[idx].failureCount > 0) NexusWarn else NexusOk,
                radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
private fun SelectedCommandTooltipCard(
    item: NexusToolAndRedactor.CommandTelemetryItem,
    onRunCommand: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Selected Command",
                        tint = NexusAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$ ${item.fullCommandExample}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "Avg: %d ms (Min %d · Max %d ms) · Success: %.0f%% (%d/%d runs)",
                        item.avgExecutionMs,
                        item.minExecutionMs,
                        item.maxExecutionMs,
                        item.successRatePercent,
                        item.successCount,
                        item.totalRuns
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRunCommand,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexusAccent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("tooltip_rerun_command_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run command",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Re-Run",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun D3RadialSuccessDonut(
    successRatePercent: Float
) {
    val sweepSuccess = (successRatePercent.coerceIn(0f, 100f) / 100f) * 360f
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeW = 12.dp.toPx()
        val inset = strokeW / 2f
        val arcSize = Size(size.width - strokeW, size.height - strokeW)

        // Failure / Remainder Track
        drawArc(
            color = NexusDanger.copy(alpha = 0.28f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Success Arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(NexusOk, Color(0xFF34D399), NexusOk)
            ),
            startAngle = -90f,
            sweepAngle = sweepSuccess,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun D3LatencyAreaChart(
    samples: List<NexusToolAndRedactor.CommandRunSample>,
    animFraction: Float
) {
    val maxLat = remember(samples) {
        max(200L, (samples.maxOfOrNull { it.executionMs } ?: 200L) + 25L).toFloat()
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
    ) {
        val w = size.width
        val h = size.height
        val topPad = 8.dp.toPx()
        val botPad = 8.dp.toPx()
        val chartH = (h - topPad - botPad).coerceAtLeast(1f)

        if (samples.isEmpty()) return@Canvas

        val pts = samples.mapIndexed { idx, s ->
            val x = if (samples.size == 1) w / 2f else (w * idx.toFloat() / (samples.size - 1).toFloat())
            val ratio = (s.executionMs.toFloat() / maxLat).coerceIn(0.05f, 1f) * animFraction
            val y = topPad + chartH * (1f - ratio)
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val cur = pts[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }

        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, topPad + chartH)
            lineTo(pts.first().x, topPad + chartH)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(NexusAccent2.copy(alpha = 0.36f), Color.Transparent),
                startY = topPad,
                endY = topPad + chartH
            )
        )

        drawPath(
            path = linePath,
            color = NexusAccent2,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        pts.forEachIndexed { i, pt ->
            val ok = samples[i].isSuccess
            drawCircle(
                color = if (ok) NexusAccent2 else NexusDanger,
                radius = 3.2.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
private fun FrequentCommandLeaderboardRow(
    rank: Int,
    item: NexusToolAndRedactor.CommandTelemetryItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRunCommand: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            if (isSelected) NexusAccent else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NexusAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusAccent
                        )
                    }
                    Text(
                        text = item.fullCommandExample,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetaChip(
                        text = "${item.totalRuns}x runs",
                        bgColor = NexusAccent.copy(alpha = 0.14f),
                        textColor = NexusAccent
                    )
                    MetaChip(
                        text = "${item.avgExecutionMs} ms",
                        bgColor = NexusAccent2.copy(alpha = 0.14f),
                        textColor = NexusAccent2
                    )
                    Surface(
                        onClick = onRunCommand,
                        shape = RoundedCornerShape(7.dp),
                        color = NexusOk.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run command",
                                tint = NexusOk,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Run",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = NexusOk
                            )
                        }
                    }
                }
            }

            // Success Rate Bar + Min/Max Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(NexusDanger.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((item.successRatePercent / 100f).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(999.dp))
                            .background(NexusOk)
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "%.0f%% success · min %dms / max %dms",
                        item.successRatePercent,
                        item.minExecutionMs,
                        item.maxExecutionMs
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
