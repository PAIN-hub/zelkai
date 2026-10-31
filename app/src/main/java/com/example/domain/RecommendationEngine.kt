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
    val memoryHeadroomScore: Double, // 0..100
    val compatibilityScore: Double,  // 0..100
    val measuredTokPerSec: Double,
    val isSpeedMeasured: Boolean,
    val measuredTtftMs: Long?,
    val speedDisplayLabel: String,
    val rejectionReason: String? = null
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
     * Per Part C #6: Never fabricates tok/s numbers. Shows `"Not benchmarked"` unless a real
     * inference runtime has recorded a benchmark in [measuredBenchmarks].
     */
    fun evaluateCatalog(
        deviceProfile: DeviceProfile,
        catalog: List<ModelRegistryEntry>,
        preset: InferencePreset = InferencePreset.BALANCED,
        measuredBenchmarks: Map<String, ModelBenchmarkResult> = emptyMap(),
        downloadedModelIds: Set<String> = emptySet()
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
                isAlreadyDownloaded = model.id in downloadedModelIds
            )
        }

        val compatibleSorted = evaluated
            .filter { it.isCompatible }
            .sortedByDescending { it.totalScore }

        val topPick = compatibleSorted.firstOrNull()?.copy(
            bucket = RecommendationBucket.RECOMMENDED_TOP_PICK
        )
        val alsoCompatible = compatibleSorted.drop(1).map {
            it.copy(bucket = RecommendationBucket.ALSO_COMPATIBLE)
        }
        val incompatible = evaluated
            .filter { !it.isCompatible }
            .sortedBy { it.entry.ramRequiredBytes }
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
        isAlreadyDownloaded: Boolean = false
    ): ScoredModelRecommendation {
        val gb = 1024.0 * 1024.0 * 1024.0
        val availRamGb = deviceProfile.availableRamBytes / gb
        val totalRamGb = deviceProfile.totalRamBytes / gb
        val freeStorageGb = deviceProfile.freeStorageBytes / gb
        val requiredRamGb = model.ramRequiredBytes / gb
        val requiredStorageBytes = (model.sizeBytes * 1.10).toLong()
        val requiredStorageGb = requiredStorageBytes / gb

        val rejectionReasons = mutableListOf<String>()

        if (!deviceProfile.is64Bit) {
            rejectionReasons.add("requires 64-bit ARM64/x86_64 ABI, device is ${deviceProfile.primaryAbi}")
        }
        if (deviceProfile.apiLevel < model.minApiLevel) {
            rejectionReasons.add("requires Android API ${model.minApiLevel}+, device is API ${deviceProfile.apiLevel}")
        }

        val maxSafeTotalRamBytes = (deviceProfile.totalRamBytes * 0.82).toLong()
        if (model.ramRequiredBytes > deviceProfile.availableRamBytes) {
            rejectionReasons.add(
                String.format(
                    Locale.US,
                    "needs %.1f GB free RAM, you have %.1f GB",
                    requiredRamGb,
                    availRamGb
                )
            )
        } else if (model.ramRequiredBytes > maxSafeTotalRamBytes) {
            rejectionReasons.add(
                String.format(
                    Locale.US,
                    "needs %.1f GB RAM (exceeds safe budget on %.1f GB device)",
                    requiredRamGb,
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

        val speedLabel = if (realMeasuredTokSec != null) {
            val ttftPart = benchmark.ttftMs.takeIf { it > 0L }?.let { " · TTFT ${it}ms" } ?: ""
            String.format(Locale.US, "%.1f tok/s%s (measured)", realMeasuredTokSec, ttftPart)
        } else {
            "Not benchmarked"
        }

        val isCompatible = rejectionReasons.isEmpty()

        val quality = model.qualityScore.toDouble().coerceIn(0.0, 100.0)

        // Hardware efficiency score derived from real RAM ratio & measured benchmark when present
        val hardwareEfficiencyScore = if (realMeasuredTokSec != null) {
            ((realMeasuredTokSec / 28.0) * 100.0).coerceIn(5.0, 100.0)
        } else {
            val sizeRatio = (model.sizeBytes.toDouble() / deviceProfile.availableRamBytes.coerceAtLeast(1L).toDouble())
                .coerceIn(0.05, 1.0)
            ((1.0 - sizeRatio * 0.75) * 100.0).coerceIn(15.0, 100.0)
        }

        val remainingRamBytes = (deviceProfile.availableRamBytes - model.ramRequiredBytes).coerceAtLeast(0L)
        val headroomFraction = if (deviceProfile.availableRamBytes > 0L) {
            remainingRamBytes.toDouble() / deviceProfile.availableRamBytes.toDouble()
        } else 0.0
        val memoryHeadroom = (headroomFraction * 135.0).coerceIn(0.0, 100.0)

        val tierOptimalParams = when (deviceProfile.effectiveTier) {
            DeviceTier.LOW -> 0.5
            DeviceTier.MID -> 1.5
            DeviceTier.HIGH -> 3.8
            DeviceTier.ULTRA -> 8.0
        }
        val paramDistancePenalty = (abs(model.paramsBillions - tierOptimalParams) * 9.5).coerceAtMost(55.0)
        val vulkanBonus = if (deviceProfile.hasVulkanCompute) 8.0 else 0.0
        val compatibility = (92.0 + vulkanBonus - paramDistancePenalty).coerceIn(15.0, 100.0)

        val baseFormulaScore =
            0.35 * quality +
                0.30 * hardwareEfficiencyScore +
                0.20 * memoryHeadroom +
                0.15 * compatibility

        val adjustedScore = when (preset) {
            InferencePreset.BALANCED -> baseFormulaScore
            InferencePreset.SPEED -> {
                0.15 * quality +
                    0.50 * hardwareEfficiencyScore +
                    0.25 * memoryHeadroom +
                    0.10 * compatibility
            }
            InferencePreset.QUALITY -> {
                0.65 * quality +
                    0.10 * hardwareEfficiencyScore +
                    0.05 * memoryHeadroom +
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
            memoryHeadroomScore = (memoryHeadroom * 10.0).roundToInt() / 10.0,
            compatibilityScore = (compatibility * 10.0).roundToInt() / 10.0,
            measuredTokPerSec = realMeasuredTokSec ?: 0.0,
            isSpeedMeasured = isMeasured,
            measuredTtftMs = benchmark?.ttftMs,
            speedDisplayLabel = speedLabel,
            rejectionReason = rejectionReasons.firstOrNull()
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
                    it.paramsBillions > current.paramsBillions &&
                        it.ramRequiredBytes <= (deviceProfile.availableRamBytes * 0.80).toLong()
                }
                .minByOrNull { it.paramsBillions }
        }
        return null
    }
}
