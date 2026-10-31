package com.example.ui.nexus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.ActiveLoadedModelState
import com.example.domain.CapabilityVerdict
import com.example.domain.DeviceHardwareTelemetry
import com.example.domain.DeviceProfile
import com.example.domain.DeviceRecommendationReport
import com.example.domain.InferencePreset
import com.example.domain.LocalLlmSpec
import com.example.domain.LocalModelDownloadState
import com.example.domain.ModelBenchmarkResult
import com.example.domain.ModelCapabilityCheckResult
import com.example.domain.EngineInfo
import com.example.domain.EngineProvider
import com.example.domain.RecommendationBucket
import com.example.domain.ScoredModelRecommendation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalLlmHubDialog(
    telemetry: DeviceHardwareTelemetry,
    deviceProfile: DeviceProfile? = null,
    recommendationReport: DeviceRecommendationReport? = null,
    activePreset: InferencePreset = InferencePreset.BALANCED,
    benchmarks: Map<String, ModelBenchmarkResult> = emptyMap(),
    activeLoadedModel: ActiveLoadedModelState? = null,
    isBenchmarking: Boolean = false,
    isNetworkOnline: Boolean,
    downloadedModels: Set<String>,
    downloadStates: Map<String, LocalModelDownloadState>,
    activeProvider: String,
    activeModelId: String,
    engineInfo: EngineInfo = EngineProvider.currentEngine.info(),
    wifiOnlyDownloads: Boolean = false,
    totalDownloadedBytes: Long = 0L,
    pendingMobileDataSpec: LocalLlmSpec? = null,
    getDownloadedFileSizeBytes: (String) -> Long = { 0L },
    onToggleWifiOnly: (Boolean) -> Unit = {},
    onConfirmMobileDataDownload: () -> Unit = {},
    onDismissMobileDataPrompt: () -> Unit = {},
    onSelectPreset: (InferencePreset) -> Unit = {},
    onReRunBenchmark: (String) -> Unit = {},
    onUnloadModel: () -> Unit = {},
    onCheckCapability: (LocalLlmSpec) -> ModelCapabilityCheckResult,
    onStartDownload: (LocalLlmSpec) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (LocalLlmSpec) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onActivateLocalModel: (LocalLlmSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingCapabilityCheck by remember { mutableStateOf<ModelCapabilityCheckResult?>(null) }
    var pendingDeleteSpec by remember { mutableStateOf<LocalLlmSpec?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                .testTag("local_llm_hub_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "On-Device Local LLM Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Strict hardware profiler, multi-quantization ranker & resumable GGUF manager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_local_llm_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Local LLM Hub"
                        )
                    }
                }

                // StubEngine / Runtime Status Banner (Part C #4)
                if (!engineInfo.isRuntimeInstalled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexusWarn.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_runtime_not_installed_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Runtime status",
                                tint = NexusWarn,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = engineInfo.statusBannerMessage,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Device Hardware + SoC + Tier + Network Telemetry Card
                DeviceHardwareBanner(
                    telemetry = telemetry,
                    deviceProfile = deviceProfile,
                    activeLoadedModel = activeLoadedModel,
                    isBenchmarking = isBenchmarking,
                    isNetworkOnline = isNetworkOnline,
                    wifiOnlyDownloads = wifiOnlyDownloads,
                    totalDownloadedBytes = totalDownloadedBytes,
                    onToggleWifiOnly = onToggleWifiOnly,
                    onUnloadModel = onUnloadModel
                )

                // Preference Chips: Speed / Balanced / Quality
                PreferenceSelectorRow(
                    activePreset = activePreset,
                    onSelectPreset = onSelectPreset
                )

                // >500 MB Mobile Data Confirmation Card (Clarifying Answer #3)
                AnimatedVisibility(visible = pendingMobileDataSpec != null) {
                    pendingMobileDataSpec?.let { spec ->
                        MobileDataConfirmationCard(
                            spec = spec,
                            onWaitForWifi = {
                                onToggleWifiOnly(true)
                                onDismissMobileDataPrompt()
                            },
                            onContinueMobileData = onConfirmMobileDataDownload,
                            onCancel = onDismissMobileDataPrompt
                        )
                    }
                }

                // Delete Confirmation Card showing freed MB/GB (Part C #3)
                AnimatedVisibility(visible = pendingDeleteSpec != null) {
                    pendingDeleteSpec?.let { spec ->
                        val actualBytes = getDownloadedFileSizeBytes(spec.id)
                            .takeIf { it > 0L } ?: (spec.downloadSizeMb * 1024L * 1024L)
                        val freedLabel = if (actualBytes >= 1024L * 1024L * 1024L) {
                            String.format(Locale.US, "%.2f GB", actualBytes / (1024.0 * 1024.0 * 1024.0))
                        } else {
                            String.format(Locale.US, "%.0f MB", actualBytes / (1024.0 * 1024.0))
                        }
                        DeleteModelConfirmationCard(
                            spec = spec,
                            freedSpaceLabel = freedLabel,
                            onConfirmDelete = {
                                val idToDelete = spec.id
                                pendingDeleteSpec = null
                                onDeleteModel(idToDelete)
                            },
                            onCancel = { pendingDeleteSpec = null }
                        )
                    }
                }

                // Pre-Download Device Capability Check Modal Card
                AnimatedVisibility(visible = pendingCapabilityCheck != null) {
                    pendingCapabilityCheck?.let { check ->
                        CapabilityPreFlightCard(
                            result = check,
                            onConfirmDownload = {
                                val specToDownload = check.model
                                pendingCapabilityCheck = null
                                onStartDownload(specToDownload)
                            },
                            onDismissCheck = { pendingCapabilityCheck = null }
                        )
                    }
                }

                // Grouped Catalog List:
                // 1) "Recommended for your device" (top pick)
                // 2) "Also compatible"
                // 3) "Not recommended" (disabled with exact reason)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("local_llm_catalog_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    val topPick = recommendationReport?.topPick
                    val alsoCompatible = recommendationReport?.alsoCompatible.orEmpty()
                    val notRecommended = recommendationReport?.notRecommended.orEmpty()

                    if (topPick != null) {
                        item(key = "section_recommended_top") {
                            BucketSectionHeader(
                                title = "Recommended for your device",
                                subtitle = "Top composite fit (S = ${String.format(Locale.US, "%.1f", topPick.totalScore)}/100) for your RAM & CPU topology",
                                badgeColor = NexusOk
                            )
                        }
                        item(key = "rec_top_${topPick.spec.id}") {
                            val spec = topPick.spec
                            LocalLlmCatalogCard(
                                spec = spec,
                                recommendation = topPick,
                                benchmark = benchmarks[spec.id],
                                isDownloaded = downloadedModels.contains(spec.id),
                                isCurrentlyActive = activeProvider.equals("local", ignoreCase = true) &&
                                    activeModelId.equals(spec.id, ignoreCase = true),
                                isRuntimeInstalled = engineInfo.isRuntimeInstalled,
                                downloadState = downloadStates[spec.id],
                                onRequestDownloadWithCheck = {
                                    pendingCapabilityCheck = onCheckCapability(spec)
                                },
                                onPauseDownload = { onPauseDownload(spec.id) },
                                onResumeDownload = { onResumeDownload(spec) },
                                onCancelDownload = { onCancelDownload(spec.id) },
                                onDeleteModel = { pendingDeleteSpec = spec },
                                onActivateModel = { onActivateLocalModel(spec) },
                                onReRunBenchmark = { onReRunBenchmark(spec.id) }
                            )
                        }
                    }

                    if (alsoCompatible.isNotEmpty()) {
                        item(key = "section_also_compatible") {
                            BucketSectionHeader(
                                title = "Also compatible (${alsoCompatible.size})",
                                subtitle = "Fits safely within device RAM & storage headroom",
                                badgeColor = NexusAccent
                            )
                        }
                        items(
                            items = alsoCompatible,
                            key = { "compat_${it.spec.id}" }
                        ) { rec ->
                            val spec = rec.spec
                            LocalLlmCatalogCard(
                                spec = spec,
                                recommendation = rec,
                                benchmark = benchmarks[spec.id],
                                isDownloaded = downloadedModels.contains(spec.id),
                                isCurrentlyActive = activeProvider.equals("local", ignoreCase = true) &&
                                    activeModelId.equals(spec.id, ignoreCase = true),
                                isRuntimeInstalled = engineInfo.isRuntimeInstalled,
                                downloadState = downloadStates[spec.id],
                                onRequestDownloadWithCheck = {
                                    pendingCapabilityCheck = onCheckCapability(spec)
                                },
                                onPauseDownload = { onPauseDownload(spec.id) },
                                onResumeDownload = { onResumeDownload(spec) },
                                onCancelDownload = { onCancelDownload(spec.id) },
                                onDeleteModel = { pendingDeleteSpec = spec },
                                onActivateModel = { onActivateLocalModel(spec) },
                                onReRunBenchmark = { onReRunBenchmark(spec.id) }
                            )
                        }
                    }

                    if (notRecommended.isNotEmpty()) {
                        item(key = "section_not_recommended") {
                            BucketSectionHeader(
                                title = "Not recommended (${notRecommended.size})",
                                subtitle = "Exceeds safe RAM, storage, or ABI thresholds on this device",
                                badgeColor = NexusDanger
                            )
                        }
                        items(
                            items = notRecommended,
                            key = { "notrec_${it.spec.id}" }
                        ) { rec ->
                            val spec = rec.spec
                            LocalLlmCatalogCard(
                                spec = spec,
                                recommendation = rec,
                                benchmark = benchmarks[spec.id],
                                isDownloaded = downloadedModels.contains(spec.id),
                                isCurrentlyActive = activeProvider.equals("local", ignoreCase = true) &&
                                    activeModelId.equals(spec.id, ignoreCase = true),
                                isRuntimeInstalled = engineInfo.isRuntimeInstalled,
                                downloadState = downloadStates[spec.id],
                                onRequestDownloadWithCheck = {
                                    pendingCapabilityCheck = onCheckCapability(spec)
                                },
                                onPauseDownload = { onPauseDownload(spec.id) },
                                onResumeDownload = { onResumeDownload(spec) },
                                onCancelDownload = { onCancelDownload(spec.id) },
                                onDeleteModel = { pendingDeleteSpec = spec },
                                onActivateModel = { onActivateLocalModel(spec) },
                                onReRunBenchmark = { onReRunBenchmark(spec.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketSectionHeader(
    title: String,
    subtitle: String,
    badgeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreferenceSelectorRow(
    activePreset: InferencePreset,
    onSelectPreset: (InferencePreset) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Ranking & Optimizer Preset:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InferencePreset.entries.forEach { preset ->
                val isSelected = preset == activePreset
                Surface(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) NexusAccent else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) NexusAccent else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.testTag("preset_chip_${preset.id}")
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceHardwareBanner(
    telemetry: DeviceHardwareTelemetry,
    deviceProfile: DeviceProfile?,
    activeLoadedModel: ActiveLoadedModelState?,
    isBenchmarking: Boolean,
    isNetworkOnline: Boolean,
    wifiOnlyDownloads: Boolean,
    totalDownloadedBytes: Long,
    onToggleWifiOnly: (Boolean) -> Unit,
    onUnloadModel: () -> Unit
) {
    val totalRamStr = String.format(Locale.US, "%.1f GB", telemetry.totalRamGb)
    val availRamStr = String.format(Locale.US, "%.1f GB", telemetry.availableRamGb)
    val freeStorageStr = String.format(Locale.US, "%.1f GB", telemetry.freeStorageGb)
    val usedStorageStr = if (totalDownloadedBytes >= 1024L * 1024L * 1024L) {
        String.format(Locale.US, "%.2f GB", totalDownloadedBytes / (1024.0 * 1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%.0f MB", totalDownloadedBytes / (1024.0 * 1024.0))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("device_hardware_summary_banner"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Device Hardware",
                    tint = NexusAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${telemetry.deviceName} · ${deviceProfile?.socSummary ?: telemetry.primaryAbi}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (deviceProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = NexusAccent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "TIER ${deviceProfile.effectiveTier.name}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isNetworkOnline) NexusOk.copy(alpha = 0.14f) else NexusWarn.copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isNetworkOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Network status",
                            tint = if (isNetworkOnline) NexusOk else NexusWarn,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isNetworkOnline) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = if (isNetworkOnline) NexusOk else NexusWarn
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "RAM: $availRamStr free / $totalRamStr",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Storage: $freeStorageStr free · Models: $usedStorageStr",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (deviceProfile != null) {
                val maxGhz = String.format(Locale.US, "%.2f GHz", deviceProfile.cpuTopology.maxFreqKhz / 1_000_000.0)
                Text(
                    text = "Cores: ${deviceProfile.cpuTopology.primeCores}P+${deviceProfile.cpuTopology.bigCores}B+${deviceProfile.cpuTopology.littleCores}L @ $maxGhz",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Thermal: ${deviceProfile.thermalStatusLabel}${if (deviceProfile.hasVulkanCompute) " · ${deviceProfile.vulkanVersionLabel}" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "CPU: ${telemetry.primaryAbi} (${telemetry.cpuCores} cores)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = { onToggleWifiOnly(!wifiOnlyDownloads) },
                shape = RoundedCornerShape(999.dp),
                color = if (wifiOnlyDownloads) NexusAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (wifiOnlyDownloads) NexusAccent else MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("wifi_only_downloads_toggle")
            ) {
                Text(
                    text = if (wifiOnlyDownloads) "Wi-Fi Only: ON" else "Wi-Fi Only: OFF (>500MB asks)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (wifiOnlyDownloads) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (activeLoadedModel != null || isBenchmarking) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (activeLoadedModel != null) {
                    val tokSec = activeLoadedModel.benchmark?.tokensPerSec ?: 0.0
                    Text(
                        text = "Loaded: ${activeLoadedModel.modelName} · ctx=${activeLoadedModel.config.nCtx} · threads=${activeLoadedModel.config.nThreads} · ${String.format(Locale.US, "%.1f", tokSec)} tok/s",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = NexusOk,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onUnloadModel,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("hub_unload_model_button")
                    ) {
                        Text("Unload", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        text = "Running 3-pass inference micro-benchmark…",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = NexusAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityPreFlightCard(
    result: ModelCapabilityCheckResult,
    onConfirmDownload: () -> Unit,
    onDismissCheck: () -> Unit
) {
    val accentColor = when (result.verdict) {
        CapabilityVerdict.CAPABLE -> NexusOk
        CapabilityVerdict.MARGINAL -> NexusWarn
        CapabilityVerdict.NOT_CAPABLE -> NexusDanger
    }

    val verdictTitle = when (result.verdict) {
        CapabilityVerdict.CAPABLE -> "DEVICE CAPABLE · READY FOR LOCAL INFERENCE"
        CapabilityVerdict.MARGINAL -> "MARGINAL HARDWARE · MEMORY PRESSURE WARNING"
        CapabilityVerdict.NOT_CAPABLE -> "DEVICE NOT CAPABLE · HARDWARE LIMITATION"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.5.dp, accentColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("capability_preflight_card"),
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
                    imageVector = when (result.verdict) {
                        CapabilityVerdict.CAPABLE -> Icons.Default.Verified
                        CapabilityVerdict.MARGINAL -> Icons.Default.Warning
                        CapabilityVerdict.NOT_CAPABLE -> Icons.Default.ErrorOutline
                    },
                    contentDescription = "Capability check verdict",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = verdictTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentColor
                )
            }
            Text(
                text = result.speedStatusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "Hardware Check for ${result.model.name} (${result.model.downloadSizeMb} MB):",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            result.findings.forEach { finding ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = finding,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = result.summaryRecommendation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDismissCheck,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (result.storagePassed && result.archPassed && result.verdict != CapabilityVerdict.NOT_CAPABLE) {
                Button(
                    onClick = onConfirmDownload,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (result.verdict == CapabilityVerdict.CAPABLE) NexusOk else NexusWarn,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("confirm_capability_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Confirm Download",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download (${result.model.downloadSizeMb} MB)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileDataConfirmationCard(
    spec: LocalLlmSpec,
    onWaitForWifi: () -> Unit,
    onContinueMobileData: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NexusWarn.copy(alpha = 0.12f))
            .border(1.5.dp, NexusWarn, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("mobile_data_warning_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Large Download on Mobile Data (${spec.downloadSizeMb} MB)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = NexusWarn
        )
        Text(
            text = "${spec.name} is over 500 MB. Would you like to wait for Wi-Fi or continue downloading over mobile data?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(
                onClick = onWaitForWifi,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("mobile_data_wait_wifi_button")
            ) {
                Text("Use Wi-Fi", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onContinueMobileData,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexusWarn),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("mobile_data_continue_button")
            ) {
                Text("Continue on Mobile Data", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun DeleteModelConfirmationCard(
    spec: LocalLlmSpec,
    freedSpaceLabel: String,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NexusDanger.copy(alpha = 0.10f))
            .border(1.dp, NexusDanger, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("delete_model_confirm_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Delete ${spec.name}?",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = NexusDanger
        )
        Text(
            text = "Deleting this GGUF model will free $freedSpaceLabel of on-device storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirmDelete,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexusDanger),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("confirm_delete_model_button")
            ) {
                Text("Delete ($freedSpaceLabel)", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalLlmCatalogCard(
    spec: LocalLlmSpec,
    recommendation: ScoredModelRecommendation?,
    benchmark: ModelBenchmarkResult?,
    isDownloaded: Boolean,
    isCurrentlyActive: Boolean,
    isRuntimeInstalled: Boolean = false,
    downloadState: LocalModelDownloadState?,
    onRequestDownloadWithCheck: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onActivateModel: () -> Unit,
    onReRunBenchmark: () -> Unit
) {
    val isDownloading = downloadState?.isDownloading == true
    val isPaused = downloadState?.isPaused == true
    val isWaitingForNetwork = downloadState?.isWaitingForNetwork == true
    val hasActiveOrPausedTransfer = downloadState != null && (isDownloading || isPaused || isWaitingForNetwork)
    val isNotRecommended = recommendation?.bucket == RecommendationBucket.NOT_RECOMMENDED
    val isTopPick = recommendation?.bucket == RecommendationBucket.RECOMMENDED_TOP_PICK

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isCurrentlyActive -> NexusAccent.copy(alpha = 0.10f)
                    isTopPick -> NexusOk.copy(alpha = 0.07f)
                    isNotRecommended -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                }
            )
            .border(
                1.dp,
                when {
                    isCurrentlyActive -> NexusAccent
                    isTopPick -> NexusOk.copy(alpha = 0.65f)
                    isDownloaded -> NexusOk.copy(alpha = 0.55f)
                    isWaitingForNetwork -> NexusWarn
                    isPaused -> NexusAccent.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.outline
                },
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
            .testTag("local_llm_card_${spec.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = spec.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isTopPick) {
                        MetaChip(
                            text = "TOP PICK",
                            bgColor = NexusOk,
                            textColor = Color.White
                        )
                    }
                    if (isCurrentlyActive) {
                        MetaChip(
                            text = "ACTIVE",
                            bgColor = NexusAccent,
                            textColor = Color.White
                        )
                    } else if (isDownloaded) {
                        MetaChip(
                            text = "INSTALLED",
                            bgColor = NexusOk.copy(alpha = 0.16f),
                            textColor = NexusOk
                        )
                    } else if (isWaitingForNetwork) {
                        MetaChip(
                            text = "WAITING FOR NETWORK",
                            bgColor = NexusWarn.copy(alpha = 0.18f),
                            textColor = NexusWarn
                        )
                    } else if (isPaused) {
                        MetaChip(
                            text = "PAUSED",
                            bgColor = NexusAccent.copy(alpha = 0.16f),
                            textColor = NexusAccent
                        )
                    }
                }
                Text(
                    text = "${spec.family} · ${spec.huggingFaceRepo}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "${spec.parameterCount} · ${spec.quantization}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = NexusAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = spec.specialty,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Speed / Score / Hardware Requirements Row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = "Download size",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${spec.downloadSizeMb} MB",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Minimum RAM",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Min ${spec.minRamGb} GB RAM",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Throughput",
                    tint = if (benchmark != null) NexusOk else NexusAccent,
                    modifier = Modifier.size(13.dp)
                )
                val speedLabel = when {
                    benchmark != null -> "${String.format(Locale.US, "%.1f", benchmark.tokensPerSec)} tok/s · TTFT ${benchmark.ttftMs}ms"
                    recommendation != null -> recommendation.speedDisplayLabel
                    else -> "${spec.contextDefault} ctx"
                }
                Text(
                    text = speedLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (benchmark != null) NexusOk else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recommendation != null && !isNotRecommended) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Composite Score",
                        tint = NexusAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Score ${String.format(Locale.US, "%.0f", recommendation.totalScore)}/100",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = NexusAccent
                    )
                }
            }
        }

        if (hasActiveOrPausedTransfer && downloadState != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = downloadState.stageLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = if (isWaitingForNetwork) NexusWarn else NexusAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val etaSuffix = downloadState.etaLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                    Text(
                        text = "${(downloadState.progressFraction * 100).toInt()}% · ${downloadState.speedMbps}$etaSuffix",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { downloadState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = when {
                        isWaitingForNetwork -> NexusWarn
                        isPaused -> NexusAccent.copy(alpha = 0.65f)
                        else -> NexusAccent
                    },
                    trackColor = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Checkpoint (${downloadState.downloadedMb} / ${downloadState.totalMb} MB)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloading) {
                            OutlinedButton(
                                onClick = onPauseDownload,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("pause_download_${spec.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause download",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Button(
                                onClick = onResumeDownload,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NexusOk),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("continue_download_${spec.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Continue download",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Continue", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }

                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("cancel_download_${spec.id}")
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall, color = NexusDanger)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed on device",
                            tint = NexusOk,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (benchmark != null) {
                                "Optimized: ${String.format(Locale.US, "%.1f", benchmark.tokensPerSec)} tok/s · Peak ${benchmark.peakMemoryMb} MB"
                            } else {
                                "Installed · Ready for offline GGUF inference"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusOk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onReRunBenchmark,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("rerun_benchmark_${spec.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-run benchmark",
                                tint = NexusAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteModel,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("delete_local_model_${spec.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete downloaded model",
                                tint = NexusDanger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Button(
                            onClick = onActivateModel,
                            enabled = !isCurrentlyActive && isRuntimeInstalled,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NexusAccent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("activate_local_model_${spec.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Use local model",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isCurrentlyActive -> "In Use"
                                    !isRuntimeInstalled -> "Runtime Required"
                                    else -> "Use Model"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                } else if (isNotRecommended) {
                    Text(
                        text = "Not recommended (${recommendation?.rejectionReason ?: "insufficient RAM"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusDanger,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("disabled_download_${spec.id}")
                    ) {
                        Text(
                            text = "Incompatible",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        text = recommendation?.speedDisplayLabel ?: "Checks RAM, storage & ABI before download",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestDownloadWithCheck,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTopPick) NexusOk else NexusAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("check_and_download_${spec.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Check device capability and download",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isTopPick) "Download Best Fit" else "Check & Download",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
