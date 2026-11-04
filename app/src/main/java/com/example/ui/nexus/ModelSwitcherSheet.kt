package com.example.ui.nexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.EngineInfo
import com.example.domain.EngineProvider
import com.example.domain.LocalLlmManager
import com.example.domain.ModelBenchmarkResult
import com.example.domain.ModelOption
import com.example.domain.ProviderModelRepository
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import java.util.Locale

data class ModelSwitcherItem(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val shortDescription: String,
    val requiresApiKey: Boolean,
    val hasApiKey: Boolean,
    val isLocalDownloaded: Boolean = true
)

/**
 * Part B #1 & Amendment #1:
 * No hardcoded model-ID map. For cloud models, the UI label and the request ID are the exact same string
 * (with any leading "models/" prefix stripped).
 */
fun formatShortModelName(modelId: String): String {
    val clean = modelId.trim().removePrefix("models/")
    LocalLlmManager.findSpec(clean)?.let { spec ->
        return spec.name.replace("-Instruct", "").replace(" Instruct", "")
    }
    return clean
}

private fun providerIconFor(providerId: String): ImageVector {
    return when (providerId.lowercase()) {
        "gemini" -> Icons.Default.AutoAwesome
        "anthropic" -> Icons.Default.Psychology
        "openai" -> Icons.Default.Hub
        "local" -> Icons.Default.Memory
        else -> Icons.Default.AutoAwesome
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSwitcherSheet(
    activeProvider: String,
    activeModelId: String,
    savedApiKeys: Map<String, String>,
    downloadedLocalModels: Set<String>,
    modelBenchmarks: Map<String, ModelBenchmarkResult> = emptyMap(),
    geminiModels: List<ModelOption> = emptyList(),
    anthropicModels: List<ModelOption> = ProviderModelRepository.ANTHROPIC_UNVERIFIED_SEEDS,
    openAiModels: List<ModelOption> = emptyList(),
    engineInfo: EngineInfo = EngineProvider.currentEngine.info(),
    isFetchingModels: Map<String, Boolean> = emptyMap(),
    showAllOpenAiModels: Boolean = false,
    onToggleShowAllOpenAi: (Boolean) -> Unit = {},
    onRefreshProviderModels: (String) -> Unit = {},
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onOpenApiKeySettings: (providerId: String, modelId: String) -> Unit,
    onOpenLocalLlmHub: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun hasKeyFor(providerId: String, modelId: String): Boolean {
        val prov = providerId.lowercase().trim()
        if (prov == "local") return true
        val cleanModel = modelId.lowercase().trim().removePrefix("models/")
        val modelKey = savedApiKeys["model:$cleanModel"].orEmpty()
        val providerKey = savedApiKeys["provider:$prov"].orEmpty()
        return modelKey.isNotBlank() || providerKey.isNotBlank()
    }

    // Part B #4: Fable is NOT a separate provider; Fable models appear under Anthropic.
    val cloudGroups = listOf(
        Triple("gemini", "Google Gemini", geminiModels),
        Triple("anthropic", "Anthropic Claude", anthropicModels.ifEmpty { ProviderModelRepository.ANTHROPIC_UNVERIFIED_SEEDS }),
        Triple("openai", "OpenAI", openAiModels)
    )

    val downloadedSpecs = LocalLlmManager.LOCAL_LLM_CATALOG.filter { it.id in downloadedLocalModels }
    val notDownloadedSpecs = LocalLlmManager.LOCAL_LLM_CATALOG.filter { it.id !in downloadedLocalModels }.take(4)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        modifier = Modifier.testTag("model_switcher_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Select model",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Model IDs match live provider endpoints · Cloud models use your personal key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_model_switcher_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close model switcher",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // On-Device Local GGUF Models Section
                item(key = "header_local") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "On-Device Local Models",
                                tint = NexusOk,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "On-Device (Local GGUF · No API Key)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            onClick = {
                                onDismiss()
                                onOpenLocalLlmHub()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = NexusAccent.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.30f)),
                            modifier = Modifier.testTag("manage_local_models_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Manage local models",
                                    tint = NexusAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Manage models",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusAccent
                                )
                            }
                        }
                    }
                }

                if (!engineInfo.isRuntimeInstalled) {
                    item(key = "local_stub_runtime_notice") {
                        Surface(
                            onClick = {
                                onDismiss()
                                onOpenLocalLlmHub()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = NexusWarn.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("switcher_runtime_not_installed_notice")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Runtime not installed",
                                    tint = NexusWarn,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = engineInfo.statusBannerMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (downloadedSpecs.isNotEmpty()) {
                    items(
                        items = downloadedSpecs,
                        key = { "local_dl_${it.id}" }
                    ) { spec ->
                        val isSelected = activeProvider.equals("local", ignoreCase = true) &&
                            activeModelId.equals(spec.id, ignoreCase = true)
                        val bench = modelBenchmarks[spec.id]
                        val tokSecBadge = when {
                            !engineInfo.isRuntimeInstalled -> "Runtime needed"
                            bench != null && bench.tokensPerSec > 0.0 -> "${String.format(Locale.US, "%.1f", bench.tokensPerSec)} tok/s"
                            else -> "Not benchmarked"
                        }
                        val detailPerf = if (bench != null && bench.tokensPerSec > 0.0) {
                            "${String.format(Locale.US, "%.1f", bench.tokensPerSec)} tok/s · TTFT ${bench.ttftMs}ms"
                        } else {
                            tokSecBadge
                        }
                        ModelSwitcherRow(
                            providerId = "local",
                            modelId = spec.id,
                            title = spec.name,
                            description = "Downloaded · ${spec.quantization} · ${spec.downloadSizeMb} MB · $detailPerf",
                            isSelected = isSelected,
                            requiresKey = false,
                            hasKey = true,
                            trailingBadgeText = tokSecBadge,
                            onRowClick = {
                                if (engineInfo.isRuntimeInstalled) {
                                    onSelectModel("local", spec.id)
                                    onDismiss()
                                } else {
                                    onDismiss()
                                    onOpenLocalLlmHub()
                                }
                            },
                            onKeyIconClick = {
                                onDismiss()
                                onOpenLocalLlmHub()
                            }
                        )
                    }
                } else {
                    item(key = "local_empty_hint") {
                        Surface(
                            onClick = {
                                onDismiss()
                                onOpenLocalLlmHub()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No local GGUF models downloaded yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Open the Local LLM Hub to profile your device and download recommended models.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Open Hub",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusAccent
                                )
                            }
                        }
                    }
                }

                items(
                    items = notDownloadedSpecs,
                    key = { "local_avail_${it.id}" }
                ) { spec ->
                    val isSelected = activeProvider.equals("local", ignoreCase = true) &&
                        activeModelId.equals(spec.id, ignoreCase = true)
                    ModelSwitcherRow(
                        providerId = "local",
                        modelId = spec.id,
                        title = spec.name,
                        description = "${spec.quantization} · ${spec.downloadSizeMb} MB · Min ${spec.minRamGb} GB RAM",
                        isSelected = isSelected,
                        requiresKey = false,
                        hasKey = true,
                        trailingBadgeText = "Download",
                        onRowClick = {
                            onDismiss()
                            onOpenLocalLlmHub()
                        },
                        onKeyIconClick = {
                            onDismiss()
                            onOpenLocalLlmHub()
                        }
                    )
                }

                item(key = "spacer_local") {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                cloudGroups.forEach { (providerId, groupTitle, modelsList) ->
                    val hasProviderKey = hasKeyFor(providerId, "")
                    val isFetching = isFetchingModels[providerId] == true

                    item(key = "header_$providerId") {
                        ProviderGroupHeader(
                            providerId = providerId,
                            title = groupTitle,
                            actionLabel = if (!hasProviderKey) "Set API key" else if (isFetching) "Refreshing…" else "Refresh",
                            showOpenAiAllToggle = providerId == "openai" && hasProviderKey,
                            showAllOpenAiModels = showAllOpenAiModels,
                            onToggleOpenAiAll = { onToggleShowAllOpenAi(!showAllOpenAiModels) },
                            onActionClick = {
                                if (!hasProviderKey) {
                                    val defaultId = modelsList.firstOrNull()?.id ?: ""
                                    onOpenApiKeySettings(providerId, defaultId)
                                } else {
                                    onRefreshProviderModels(providerId)
                                }
                            }
                        )
                    }

                    if (modelsList.isEmpty()) {
                        item(key = "empty_models_$providerId") {
                            Surface(
                                onClick = {
                                    if (!hasProviderKey) {
                                        onOpenApiKeySettings(providerId, "")
                                    } else {
                                        onRefreshProviderModels(providerId)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("empty_provider_models_$providerId")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (!hasProviderKey) {
                                            "Add your $groupTitle API key to fetch live verified models"
                                        } else if (isFetching) {
                                            "Fetching live $groupTitle models…"
                                        } else {
                                            "Tap Refresh to fetch live models from $groupTitle"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!hasProviderKey) "Add Key" else "Refresh",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusAccent
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = modelsList,
                            key = { option -> "${providerId}_${option.id}" }
                        ) { option ->
                            val isSelected = activeProvider.equals(providerId, ignoreCase = true) &&
                                activeModelId.equals(option.id, ignoreCase = true)
                            val hasKey = hasKeyFor(providerId, option.id)
                            val desc = buildString {
                                append("ID: ${option.id}")
                                if (option.isUnverified) {
                                    append(" · unverified offline seed")
                                } else {
                                    append(" · verified live model")
                                }
                                if (option.supportsToolCalls) {
                                    append(" · tools")
                                }
                            }

                            ModelSwitcherRow(
                                providerId = providerId,
                                modelId = option.id,
                                title = option.displayName,
                                description = desc,
                                isSelected = isSelected,
                                requiresKey = true,
                                hasKey = hasKey,
                                trailingBadgeText = if (option.isUnverified) "unverified" else null,
                                onRowClick = {
                                    onSelectModel(providerId, option.id)
                                    onDismiss()
                                },
                                onKeyIconClick = {
                                    onSelectModel(providerId, option.id)
                                    onOpenApiKeySettings(providerId, option.id)
                                }
                            )
                        }
                    }

                    item(key = "spacer_$providerId") {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                item(key = "bottom_pad") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderGroupHeader(
    providerId: String,
    title: String,
    actionLabel: String?,
    showOpenAiAllToggle: Boolean = false,
    showAllOpenAiModels: Boolean = false,
    onToggleOpenAiAll: () -> Unit = {},
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = providerIconFor(providerId),
                contentDescription = title,
                tint = NexusAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showOpenAiAllToggle) {
                Surface(
                    onClick = onToggleOpenAiAll,
                    shape = RoundedCornerShape(999.dp),
                    color = if (showAllOpenAiModels) NexusAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (showAllOpenAiModels) NexusAccent else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("openai_show_all_models_toggle")
                ) {
                    Text(
                        text = if (showAllOpenAiModels) "All models" else "Chat filter",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showAllOpenAiModels) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (actionLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (actionLabel == "Set API key") Icons.Default.Key else Icons.Default.Refresh,
                        contentDescription = actionLabel,
                        tint = if (actionLabel == "Set API key") NexusWarn else NexusAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (actionLabel == "Set API key") NexusWarn else NexusAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSwitcherRow(
    providerId: String,
    modelId: String,
    title: String,
    description: String,
    isSelected: Boolean,
    requiresKey: Boolean,
    hasKey: Boolean,
    trailingBadgeText: String? = null,
    onRowClick: () -> Unit,
    onKeyIconClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        NexusAccent.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val containerColor = if (isSelected) {
        NexusAccent.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onRowClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_row_${providerId}_$modelId")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = if (providerId != "local") JetBrainsMonoFontFamily else MaterialTheme.typography.titleSmall.fontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (trailingBadgeText != null) {
                    Surface(
                        onClick = onKeyIconClick,
                        shape = RoundedCornerShape(999.dp),
                        color = if (trailingBadgeText == "unverified") NexusWarn.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (trailingBadgeText == "unverified") NexusWarn.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = trailingBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trailingBadgeText == "unverified") NexusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (requiresKey && !hasKey) {
                    Surface(
                        onClick = onKeyIconClick,
                        shape = RoundedCornerShape(999.dp),
                        color = NexusWarn.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NexusWarn.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("model_key_needed_${providerId}_$modelId")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "API Key required — tap to configure",
                                tint = NexusWarn,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Add key",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = NexusWarn
                            )
                        }
                    }
                } else if (requiresKey && hasKey) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API Key configured",
                        tint = NexusOk,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onKeyIconClick)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(NexusAccent, NexusAccent2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active model",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
