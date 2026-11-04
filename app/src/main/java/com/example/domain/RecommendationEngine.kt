package com.example.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InferencePreset(val id: String, val label: String, val description: String) {
    SPEED("speed", "Speed", "Prioritizes fastest response & minimal RAM pressure"),
    BALANCED("balanced", "Balanced", "Optimal balance of coding quality, responsiveness & RAM headroom"),
    QUALITY("quality", "Quality", "Maximizes reasoning & coding depth within safe RAM limits");

    companion object {
        fun fromId(raw: String?): InferencePreset {
            return entries.find { it.id.equals(raw, ignoreCase = true) } ?: BALANCED
        }
    }
}

enum class RecommendationBucket {
    RECOMMENDED_TOP_PICK,
    ALSO_COMPATIBLE,
    NOT_RECOMMENDED
}

data class ScoredModelRecommendation(
    val entry: ModelRegistryEntry,
    val spec: LocalLlmSpec,
    val isCompatible: Boolean,
    val bucket: RecommendationBucket,
    val totalScore: Double,          // 0..100
    val qualityScore: Double,        // 0..100
    val speedScore: Double,          // 0..100
    val memoryHeadroomScore: Double, // 0..100 (memoryFit = clamp((budget - footprint) / budget, 0, 1) * 100)
    val compatibilityScore: Double,  // 0..100
    val measuredTokPerSec: Double,
    val isSpeedMeasured: Boolean,
    val measuredTtftMs: Long?,
    val speedDisplayLabel: String,
    val rejectionReason: String? = null,
    val estimatedFootprintBytes: Long = 0L,
    val ramBudgetBytes: Long = 0L,
    val ramHardCapBytes: Long = 0L,
    val isAboveRecommendedBudget: Boolean = false,
    val isSlowMeasured: Boolean = false,
    val isMarkedUnstable: Boolean = false
)

data class DeviceRecommendationReport(
    val deviceProfile: DeviceProfile,
    val activePreset: InferencePreset,
    val topPick: ScoredModelRecommendation?,
    val alsoCompatible: List<ScoredModelRecommendation>,
    val notRecommended: List<ScoredModelRecommendation>,
    val allEvaluated: List<ScoredModelRecommendation>
)

object RecommendationEngine {

    /**
     * Evaluates and ranks all models in [catalog] for [deviceProfile] under [preset].
     * Uses Total Device RAM (`RamPolicy.budgetBytes` and `RamPolicy.hardCapBytes`) and measured
     * performance — NEVER fluctuating `availableRamBytes`.
     */
    fun evaluateCatalog(
        deviceProfile: DeviceProfile,
        catalog: List<ModelRegistryEntry>,
        preset: InferencePreset = InferencePreset.BALANCED,
        measuredBenchmarks: Map<String, ModelBenchmarkResult> = emptyMap(),
        downloadedModelIds: Set<String> = emptySet(),
        allowAboveRecommendedBudget: Boolean = false,
        unstableModelIds: Set<String> = emptySet(),
        unsupportedArchReasons: Map<String, String> = emptyMap()
    ): DeviceRecommendationReport {
        val effectivePreset = if (deviceProfile.isBatterySaverOn && preset == InferencePreset.BALANCED) {
            InferencePreset.SPEED
        } else {
            preset
        }

        val evaluated = catalog.map { model ->
            scoreSingleModel(
                deviceProfile = deviceProfile,
                model = model,
                preset = effectivePreset,
                benchmark = measuredBenchmarks[model.id],
                isAlreadyDownloaded = model.id in downloadedModelIds,
                allowAboveRecommendedBudget = allowAboveRecommendedBudget,
                isMarkedUnstable = model.id in unstableModelIds,
                unsupportedArchReason = unsupportedArchReasons[model.id]
            )
        }

        val compatibleSorted = evaluated
            .filter { it.isCompatible }
            .sortedWith(
                compareBy<ScoredModelRecommendation> { it.isSlowMeasured || it.isMarkedUnstable || it.isAboveRecommendedBudget }
                    .thenByDescending { it.totalScore }
            )

        val topPickCandidate = compatibleSorted.firstOrNull { !it.isSlowMeasured && !it.isMarkedUnstable && !it.isAboveRecommendedBudget }
            ?: compatibleSorted.firstOrNull()

        val topPick = topPickCandidate?.copy(
            bucket = RecommendationBucket.RECOMMENDED_TOP_PICK
        )
        val alsoCompatible = compatibleSorted
            .filter { it.entry.id != topPick?.entry?.id }
            .map { it.copy(bucket = RecommendationBucket.ALSO_COMPATIBLE) }

        val incompatible = evaluated
            .filter { !it.isCompatible }
            .sortedBy { it.estimatedFootprintBytes }
            .map { it.copy(bucket = RecommendationBucket.NOT_RECOMMENDED) }

        val finalAll = buildList {
            if (topPick != null) add(topPick)
            addAll(alsoCompatible)
            addAll(incompatible)
        }

        return DeviceRecommendationReport(
            deviceProfile = deviceProfile,
            activePreset = effectivePreset,
            topPick = topPick,
            alsoCompatible = alsoCompatible,
            notRecommended = incompatible,
            allEvaluated = finalAll
        )
    }

    fun scoreSingleModel(
        deviceProfile: DeviceProfile,
        model: ModelRegistryEntry,
        preset: InferencePreset = InferencePreset.BALANCED,
        benchmark: ModelBenchmarkResult? = null,
        isAlreadyDownloaded: Boolean = false,
        allowAboveRecommendedBudget: Boolean = false,
        isMarkedUnstable: Boolean = false,
        unsupportedArchReason: String? = null,
        nCtxForEstimate: Int = 2048,
        ggufLayerCount: Int = 0,
        ggufHeadKvCount: Int = 0,
        ggufHeadDim: Int = 0
    ): ScoredModelRecommendation {
        val gb = 1024.0 * 1024.0 * 1024.0
        val totalRamGb = deviceProfile.totalRamBytes / gb
        val freeStorageGb = deviceProfile.freeStorageBytes / gb
        val budgetBytes = deviceProfile.ramBudgetBytes
        val hardCapBytes = deviceProfile.ramHardCapBytes
        val budgetGb = budgetBytes / gb
        val hardCapGb = hardCapBytes / gb

        val footprintBytes = RamPolicy.estimateModelFootprintBytes(
            modelFileSize = model.sizeBytes,
            nCtx = nCtxForEstimate,
            nLayer = ggufLayerCount,
            nHeadKv = ggufHeadKvCount,
            headDim = ggufHeadDim,
            kvBytesPerElem = 1 // q8_0 KV cache
        )
        val footprintGb = footprintBytes / gb
        val requiredStorageBytes = (model.sizeBytes * 1.10).toLong()
        val requiredStorageGb = requiredStorageBytes / gb

        val rejectionReasons = mutableListOf<String>()

        if (!deviceProfile.is64Bit) {
            rejectionReasons.add("requires 64-bit ARM64/x86_64 ABI, device is ${deviceProfile.primaryAbi}")
        }
        if (deviceProfile.apiLevel < model.minApiLevel) {
            rejectionReasons.add("requires Android API ${model.minApiLevel}+, device is API ${deviceProfile.apiLevel}")
        }
        if (!unsupportedArchReason.isNullOrBlank()) {
            rejectionReasons.add(unsupportedArchReason)
        }

        val isAboveRecommended = footprintBytes > budgetBytes
        if (footprintBytes > hardCapBytes) {
            rejectionReasons.add(
                String.format(
                    Locale.US,
                    "estimated footprint %.2f GB exceeds RAM hard cap %.1f GB on %.1f GB device",
                    footprintGb,
                    hardCapGb,
                    totalRamGb
                )
            )
        } else if (isAboveRecommended && !allowAboveRecommendedBudget) {
            rejectionReasons.add(
                String.format(
                    Locale.US,
                    "estimated footprint %.2f GB exceeds %.1f GB recommended RAM budget on %.1f GB device",
                    footprintGb,
                    budgetGb,
                    totalRamGb
                )
            )
        }

        if (!isAlreadyDownloaded && deviceProfile.freeStorageBytes < requiredStorageBytes) {
            rejectionReasons.add(
                String.format(
                    Locale.US,
                    "needs %.1f GB free storage, you have %.1f GB",
                    requiredStorageGb,
                    freeStorageGb
                )
            )
        }

        val realMeasuredTokSec = benchmark?.tokensPerSec?.takeIf { it > 0.0 }
        val isMeasured = realMeasuredTokSec != null
        val isSlowMeasured = realMeasuredTokSec != null && realMeasuredTokSec < 3.0

        val speedLabel = when {
            realMeasuredTokSec != null && isSlowMeasured -> {
                val ttftPart = benchmark.ttftMs.takeIf { it > 0L }?.let { " · TTFT ${it}ms" } ?: ""
                String.format(Locale.US, "%.1f tok/s%s · Also compatible (slow)", realMeasuredTokSec, ttftPart)
            }
            realMeasuredTokSec != null -> {
                val ttftPart = benchmark.ttftMs.takeIf { it > 0L }?.let { " · TTFT ${it}ms" } ?: ""
                String.format(Locale.US, "%.1f tok/s%s (measured)", realMeasuredTokSec, ttftPart)
            }
            else -> "Not benchmarked"
        }

        val isCompatible = rejectionReasons.isEmpty()

        val quality = model.qualityScore.toDouble().coerceIn(0.0, 100.0)

        // Speed score: uses measured tok/s if benchmarked, else static score from model size and SoC class
        val socMultiplier = if (deviceProfile.isHighEndSoc) 1.12 else 0.95
        val hardwareEfficiencyScore = if (realMeasuredTokSec != null) {
            ((realMeasuredTokSec / 28.0) * 100.0).coerceIn(5.0, 100.0)
        } else {
            val sizeRatio = (footprintBytes.toDouble() / budgetBytes.coerceAtLeast(1L).toDouble())
                .coerceIn(0.05, 1.25)
            (((1.0 - sizeRatio * 0.65) * 100.0) * socMultiplier).coerceIn(15.0, 100.0)
        }

        // memoryFit = clamp((budget - footprint) / budget, 0, 1) * 100
        val memoryFitFraction = if (budgetBytes > 0L) {
            ((budgetBytes - footprintBytes).toDouble() / budgetBytes.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        val memoryFitScore = (memoryFitFraction * 100.0).coerceIn(0.0, 100.0)

        val tierOptimalParams = when (deviceProfile.effectiveRamClass) {
            RamClass.LOW -> 0.5
            RamClass.MID -> 1.1
            RamClass.MID_PLUS -> 1.7
            RamClass.HIGH -> 3.0
            RamClass.HIGH_PLUS -> 4.0
            RamClass.ULTRA -> 7.0
        }
        val paramDistancePenalty = (abs(model.paramsBillions - tierOptimalParams) * 9.5).coerceAtMost(55.0)
        val vulkanBonus = if (deviceProfile.hasVulkanCompute) 8.0 else 0.0
        val unstablePenalty = if (isMarkedUnstable) 35.0 else 0.0
        val compatibility = (92.0 + vulkanBonus - paramDistancePenalty - unstablePenalty).coerceIn(10.0, 100.0)

        // S = 0.35*quality + 0.30*speed + 0.20*memoryFit + 0.15*compatibility
        val baseFormulaScore =
            0.35 * quality +
                0.30 * hardwareEfficiencyScore +
                0.20 * memoryFitScore +
                0.15 * compatibility

        val adjustedScore = when (preset) {
            InferencePreset.BALANCED -> baseFormulaScore
            InferencePreset.SPEED -> {
                0.15 * quality +
                    0.50 * hardwareEfficiencyScore +
                    0.25 * memoryFitScore +
                    0.10 * compatibility
            }
            InferencePreset.QUALITY -> {
                0.65 * quality +
                    0.10 * hardwareEfficiencyScore +
                    0.05 * memoryFitScore +
                    0.20 * compatibility
            }
        }

        val finalScore = if (isCompatible) {
            ((adjustedScore * 10.0).roundToInt() / 10.0)
        } else {
            0.0
        }

        return ScoredModelRecommendation(
            entry = model,
            spec = model.toLocalLlmSpec(),
            isCompatible = isCompatible,
            bucket = if (isCompatible) RecommendationBucket.ALSO_COMPATIBLE else RecommendationBucket.NOT_RECOMMENDED,
            totalScore = finalScore,
            qualityScore = quality,
            speedScore = (hardwareEfficiencyScore * 10.0).roundToInt() / 10.0,
            memoryHeadroomScore = (memoryFitScore * 10.0).roundToInt() / 10.0,
            compatibilityScore = (compatibility * 10.0).roundToInt() / 10.0,
            measuredTokPerSec = realMeasuredTokSec ?: 0.0,
            isSpeedMeasured = isMeasured,
            measuredTtftMs = benchmark?.ttftMs,
            speedDisplayLabel = speedLabel,
            rejectionReason = rejectionReasons.firstOrNull(),
            estimatedFootprintBytes = footprintBytes,
            ramBudgetBytes = budgetBytes,
            ramHardCapBytes = hardCapBytes,
            isAboveRecommendedBudget = isAboveRecommended,
            isSlowMeasured = isSlowMeasured,
            isMarkedUnstable = isMarkedUnstable
        )
    }

    fun suggestAlternativeModel(
        currentModelId: String,
        measuredTokPerSec: Double,
        deviceProfile: DeviceProfile,
        catalog: List<ModelRegistryEntry>
    ): ModelRegistryEntry? {
        val current = catalog.find { it.id.equals(currentModelId, ignoreCase = true) } ?: return null
        val report = evaluateCatalog(deviceProfile, catalog)
        val compatible = report.allEvaluated.filter { it.isCompatible }.map { it.entry }

        if (measuredTokPerSec in 0.1..2.99) {
            return compatible
                .filter { it.paramsBillions < current.paramsBillions }
                .maxByOrNull { it.paramsBillions }
        }
        if (measuredTokPerSec >= 28.0) {
            return compatible
                .filter {
                    val fp = RamPolicy.estimateModelFootprintBytes(it.sizeBytes)
                    it.paramsBillions > current.paramsBillions &&
                        fp <= deviceProfile.ramBudgetBytes
                }
                .minByOrNull { it.paramsBillions }
        }
        return null
    }
}
