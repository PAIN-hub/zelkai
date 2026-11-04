package com.example.domain

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class DeviceTier(val label: String, val rank: Int) {
    LOW("LOW (<=3GB)", 0),
    MID("MID (4-6GB)", 1),
    HIGH("HIGH (8-12GB)", 2),
    ULTRA("ULTRA (12GB+ Flagship)", 3);

    fun stepUp(): DeviceTier = entries.getOrElse(rank + 1) { ULTRA }
    fun stepDown(): DeviceTier = entries.getOrElse(rank - 1) { LOW }
}

/**
 * Total-RAM class with tolerance for kernel/modem carve-outs (e.g. an 8 GB phone reports ~7.4–7.8 GB).
 * Used for all model recommendations, context sizing, and load budgets — NEVER free/available RAM.
 */
enum class RamClass(
    val label: String,
    val nominalGb: Int,
    val budgetFraction: Double,
    val rank: Int
) {
    LOW("LOW (<=3 GB)", 3, 0.40, 0),
    MID("MID (4 GB)", 4, 0.45, 1),
    MID_PLUS("MID_PLUS (6 GB)", 6, 0.50, 2),
    HIGH("HIGH (8 GB)", 8, 0.55, 3),
    HIGH_PLUS("HIGH_PLUS (12 GB)", 12, 0.60, 4),
    ULTRA("ULTRA (16 GB+)", 16, 0.60, 5);

    fun stepUp(): RamClass = entries.getOrElse(rank + 1) { ULTRA }
    fun stepDown(): RamClass = entries.getOrElse(rank - 1) { LOW }

    fun toDeviceTier(): DeviceTier = when (this) {
        LOW -> DeviceTier.LOW
        MID, MID_PLUS -> DeviceTier.MID
        HIGH, HIGH_PLUS -> DeviceTier.HIGH
        ULTRA -> DeviceTier.ULTRA
    }

    companion object {
        fun fromTotalRamBytes(totalRamBytes: Long): RamClass {
            val totalGb = totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return when {
                totalGb <= 3.5 -> LOW
                totalGb <= 5.0 -> MID
                totalGb <= 7.0 -> MID_PLUS
                totalGb <= 10.0 -> HIGH
                totalGb <= 14.0 -> HIGH_PLUS
                else -> ULTRA
            }
        }
    }
}

/**
 * Deterministic total-RAM budget and model footprint calculator.
 * Android keeps unused RAM full of caches that it reclaims on demand, so model compatibility,
 * context length, and load guards must be based on Total RAM (`MemTotal` / `MemoryInfo.totalMem`),
 * never on fluctuating `availMem`.
 */
object RamPolicy {
    const val HARD_CAP_FRACTION = 0.70
    const val RUNTIME_OVERHEAD_MULTIPLIER = 1.12

    fun budgetBytes(totalRamBytes: Long, ramClass: RamClass = RamClass.fromTotalRamBytes(totalRamBytes)): Long {
        return (totalRamBytes.toDouble() * ramClass.budgetFraction).toLong()
    }

    fun hardCapBytes(totalRamBytes: Long): Long {
        return (totalRamBytes.toDouble() * HARD_CAP_FRACTION).toLong()
    }

    /**
     * Computes KV cache size from GGUF metadata (`n_layer`, `n_head_kv`, `head_dim`, `n_ctx`, `q8_0`/`f16`)
     * or falls back to a proportional estimate from `modelFileSize` and `nCtx`.
     */
    fun estimateKvCacheBytes(
        modelFileSize: Long,
        nCtx: Int = 2048,
        nLayer: Int = 0,
        nHeadKv: Int = 0,
        headDim: Int = 0,
        kvBytesPerElem: Int = 1 // 1 byte for q8_0, 2 bytes for f16
    ): Long {
        val safeCtx = nCtx.coerceAtLeast(512).toLong()
        return if (nLayer > 0 && nHeadKv > 0 && headDim > 0) {
            2L * nLayer.toLong() * nHeadKv.toLong() * headDim.toLong() * safeCtx * kvBytesPerElem.coerceAtLeast(1).toLong()
        } else {
            // Fallback: ~7% of model size at 2048 context with q8_0 KV cache
            val ctxScale = safeCtx.toDouble() / 2048.0
            val kvTypeScale = if (kvBytesPerElem <= 1) 1.0 else 1.8
            (modelFileSize.toDouble() * 0.07 * ctxScale * kvTypeScale).toLong()
        }
    }

    /**
     * `footprint = (modelFileSize + kvCache) + 12% runtime overhead`
     */
    fun estimateModelFootprintBytes(
        modelFileSize: Long,
        nCtx: Int = 2048,
        nLayer: Int = 0,
        nHeadKv: Int = 0,
        headDim: Int = 0,
        kvBytesPerElem: Int = 1
    ): Long {
        val kv = estimateKvCacheBytes(
            modelFileSize = modelFileSize,
            nCtx = nCtx,
            nLayer = nLayer,
            nHeadKv = nHeadKv,
            headDim = headDim,
            kvBytesPerElem = kvBytesPerElem
        )
        return ((modelFileSize + kv).toDouble() * RUNTIME_OVERHEAD_MULTIPLIER).toLong()
    }
}

data class CpuTopologyProfile(
    val totalCores: Int,
    val primeCores: Int,
    val bigCores: Int,
    val littleCores: Int,
    val maxFreqKhz: Long,
    val minClusterFreqKhz: Long,
    val perCoreMaxFreqKhz: List<Long>
) {
    val performanceCores: Int
        get() = (primeCores + bigCores).coerceAtLeast(1)

    val maxFreqGhz: Double
        get() = if (maxFreqKhz > 0) ((maxFreqKhz / 100_000.0).roundToInt() / 10.0) else 0.0
}

data class MicroBenchmarkScore(
    val cpuGops: Double,
    val memBandwidthGbPerSec: Double,
    val compositeScore: Int, // 0..100
    val durationMs: Long
)

data class DeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val socManufacturer: String,
    val socModel: String,
    val isHighEndSoc: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val apiLevel: Int,
    val osFingerprint: String,
    val cpuTopology: CpuTopologyProfile,
    val thermalStatusCode: Int, // 0=NONE, 1=LIGHT, 2=MODERATE, 3=SEVERE, 4=CRITICAL
    val thermalStatusLabel: String,
    val isBatterySaverOn: Boolean,
    val hasVulkanCompute: Boolean,
    val vulkanVersionLabel: String,
    val microBenchmark: MicroBenchmarkScore,
    val baseTier: DeviceTier,
    val effectiveTier: DeviceTier,
    val baseRamClass: RamClass = RamClass.fromTotalRamBytes(totalRamBytes),
    val effectiveRamClass: RamClass = baseRamClass
) {
    val totalRamGb: Double
        get() = ((totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToLong() / 10.0

    /**
     * Informational only ("Free memory now: X GB (changes constantly)").
     * Never used for model recommendation, context sizing, or load rejection.
     */
    val availableRamGb: Double
        get() = ((availableRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToLong() / 10.0

    val ramBudgetBytes: Long
        get() = RamPolicy.budgetBytes(totalRamBytes, effectiveRamClass)

    val ramBudgetGb: Double
        get() = ((ramBudgetBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToLong() / 10.0

    val ramHardCapBytes: Long
        get() = RamPolicy.hardCapBytes(totalRamBytes)

    val ramHardCapGb: Double
        get() = ((ramHardCapBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToLong() / 10.0

    val freeStorageGb: Double
        get() = ((freeStorageBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToLong() / 10.0

    val isThermalThrottling: Boolean
        get() = thermalStatusCode >= 2 // MODERATE or higher

    val socSummary: String
        get() = listOf(socManufacturer, socModel)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            .distinct()
            .joinToString(" ")
            .ifBlank { Build.HARDWARE ?: "ARM64 SoC" }

    val coreSummary: String
        get() = buildString {
            append("${cpuTopology.totalCores} cores (")
            val parts = mutableListOf<String>()
            if (cpuTopology.primeCores > 0) parts.add("${cpuTopology.primeCores}P")
            if (cpuTopology.bigCores > 0) parts.add("${cpuTopology.bigCores}B")
            if (cpuTopology.littleCores > 0) parts.add("${cpuTopology.littleCores}L")
            append(parts.joinToString("+"))
            if (cpuTopology.maxFreqGhz > 0.0) {
                append(" @ ${String.format(Locale.US, "%.2f", cpuTopology.maxFreqKhz / 1_000_000.0)} GHz")
            }
            append(")")
        }

    fun toLegacyTelemetry(): DeviceHardwareTelemetry {
        return DeviceHardwareTelemetry(
            totalRamGb = totalRamGb,
            availableRamGb = availableRamGb,
            freeStorageGb = freeStorageGb,
            cpuCores = cpuTopology.totalCores,
            primaryAbi = primaryAbi,
            is64Bit = is64Bit,
            deviceName = "$deviceName · $socSummary (${effectiveTier.name})"
        )
    }
}

object DeviceProfiler {

    @Volatile
    private var cachedMicroBenchmark: MicroBenchmarkScore? = null

    fun measure(context: Context, forceRebenchmark: Boolean = false): DeviceProfile {
        val (totalRamBytes, availRamBytes) = measureRamBytes(context)
        val freeStorageBytes = measureFreeStorageBytes(context)
        val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val primaryAbi = supportedAbis.firstOrNull() ?: "arm64-v8a"
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true ||
            primaryAbi.contains("64") ||
            primaryAbi.contains("arm64") ||
            primaryAbi.contains("x86_64")

        val cpuTopology = measureCpuTopology()
        val (socManufacturer, socModel) = measureSocIdentity()
        val highEndSoc = detectHighEndSoc(socManufacturer, socModel, cpuTopology)

        val (thermalCode, thermalLabel) = measureThermalState(context)
        val batterySaver = measureBatterySaver(context)
        val (hasVulkan, vulkanLabel) = measureVulkanSupport(context)

        val bench = if (!forceRebenchmark && cachedMicroBenchmark != null) {
            cachedMicroBenchmark!!
        } else {
            runMicroBenchmark().also { cachedMicroBenchmark = it }
        }

        val baseRamClass = RamClass.fromTotalRamBytes(totalRamBytes)
        val effectiveRamClass = adjustRamClassBySoc(
            baseRamClass = baseRamClass,
            isHighEndSoc = highEndSoc,
            topology = cpuTopology,
            benchmark = bench
        )
        val baseTier = computeBaseTier(
            totalRamBytes = totalRamBytes,
            isHighEndSoc = highEndSoc,
            performanceCores = cpuTopology.performanceCores
        )
        val effectiveTier = adjustTierByBenchmarkAndThermals(
            baseTier = baseTier,
            totalRamBytes = totalRamBytes,
            isHighEndSoc = highEndSoc,
            benchmark = bench,
            thermalCode = thermalCode
        )

        val rawDeviceName = listOfNotNull(
            Build.MANUFACTURER?.replaceFirstChar { it.uppercase() },
            Build.MODEL
        ).joinToString(" ").trim().ifBlank { "Android Device" }

        val deviceId = "${Build.BOARD}_${Build.MODEL}_${totalRamBytes / (1024 * 1024)}MB"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")

        return DeviceProfile(
            deviceId = deviceId,
            deviceName = rawDeviceName,
            socManufacturer = socManufacturer,
            socModel = socModel,
            isHighEndSoc = highEndSoc,
            totalRamBytes = totalRamBytes,
            availableRamBytes = availRamBytes,
            freeStorageBytes = freeStorageBytes,
            primaryAbi = primaryAbi,
            supportedAbis = supportedAbis,
            is64Bit = is64Bit,
            apiLevel = Build.VERSION.SDK_INT,
            osFingerprint = Build.FINGERPRINT ?: "api_${Build.VERSION.SDK_INT}",
            cpuTopology = cpuTopology,
            thermalStatusCode = thermalCode,
            thermalStatusLabel = thermalLabel,
            isBatterySaverOn = batterySaver,
            hasVulkanCompute = hasVulkan,
            vulkanVersionLabel = vulkanLabel,
            microBenchmark = bench,
            baseTier = baseTier,
            effectiveTier = effectiveTier,
            baseRamClass = baseRamClass,
            effectiveRamClass = effectiveRamClass
        )
    }

    /**
     * Pure helper for deterministic unit tests and profile construction from measured specs.
     */
    fun buildProfileFromSpecs(
        totalRamGb: Double,
        availableRamGb: Double,
        freeStorageGb: Double = 32.0,
        socManufacturer: String = "Qualcomm",
        socModel: String = "Snapdragon 8 Gen 3",
        primeCores: Int = 1,
        bigCores: Int = 5,
        littleCores: Int = 2,
        maxFreqKhz: Long = 3_300_000L,
        primaryAbi: String = "arm64-v8a",
        apiLevel: Int = 34,
        thermalStatusCode: Int = 0,
        isBatterySaverOn: Boolean = false,
        hasVulkanCompute: Boolean = true,
        benchmarkCompositeScore: Int = 82
    ): DeviceProfile {
        val gb = 1024L * 1024L * 1024L
        val totalBytes = (totalRamGb * gb).toLong()
        val availBytes = (availableRamGb * gb).toLong()
        val storageBytes = (freeStorageGb * gb).toLong()
        val totalCores = (primeCores + bigCores + littleCores).coerceAtLeast(1)
        val topology = CpuTopologyProfile(
            totalCores = totalCores,
            primeCores = primeCores,
            bigCores = bigCores,
            littleCores = littleCores,
            maxFreqKhz = maxFreqKhz,
            minClusterFreqKhz = (maxFreqKhz * 0.55).toLong(),
            perCoreMaxFreqKhz = List(totalCores) { maxFreqKhz }
        )
        val isHighEnd = detectHighEndSoc(socManufacturer, socModel, topology)
        val bench = MicroBenchmarkScore(
            cpuGops = benchmarkCompositeScore * 0.45,
            memBandwidthGbPerSec = benchmarkCompositeScore * 0.35,
            compositeScore = benchmarkCompositeScore,
            durationMs = 8L
        )
        val baseRamClass = RamClass.fromTotalRamBytes(totalBytes)
        val effectiveRamClass = adjustRamClassBySoc(
            baseRamClass = baseRamClass,
            isHighEndSoc = isHighEnd,
            topology = topology,
            benchmark = bench
        )
        val baseTier = computeBaseTier(totalBytes, isHighEnd, topology.performanceCores)
        val effectiveTier = adjustTierByBenchmarkAndThermals(
            baseTier = baseTier,
            totalRamBytes = totalBytes,
            isHighEndSoc = isHighEnd,
            benchmark = bench,
            thermalCode = thermalStatusCode
        )
        return DeviceProfile(
            deviceId = "test_${socModel.replace(" ", "_")}_${totalRamGb.toInt()}GB",
            deviceName = "$socManufacturer $socModel Test Device",
            socManufacturer = socManufacturer,
            socModel = socModel,
            isHighEndSoc = isHighEnd,
            totalRamBytes = totalBytes,
            availableRamBytes = availBytes,
            freeStorageBytes = storageBytes,
            primaryAbi = primaryAbi,
            supportedAbis = listOf(primaryAbi),
            is64Bit = primaryAbi.contains("64"),
            apiLevel = apiLevel,
            osFingerprint = "test_fingerprint_$apiLevel",
            cpuTopology = topology,
            thermalStatusCode = thermalStatusCode,
            thermalStatusLabel = thermalCodeToLabel(thermalStatusCode),
            isBatterySaverOn = isBatterySaverOn,
            hasVulkanCompute = hasVulkanCompute,
            vulkanVersionLabel = if (hasVulkanCompute) "Vulkan 1.3" else "CPU NEON",
            microBenchmark = bench,
            baseTier = baseTier,
            effectiveTier = effectiveTier,
            baseRamClass = baseRamClass,
            effectiveRamClass = effectiveRamClass
        )
    }

    private fun measureRamBytes(context: Context): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val minRealisticTotal = 256L * 1024L * 1024L
            val minRealisticAvail = 64L * 1024L * 1024L
            val total = if (memInfo.totalMem >= minRealisticTotal) memInfo.totalMem else readMemInfoBytes("MemTotal")
            val avail = if (memInfo.availMem >= minRealisticAvail && memInfo.totalMem >= minRealisticTotal) {
                memInfo.availMem
            } else {
                readMemInfoBytes("MemAvailable")
            }
            val safeTotal = if (total >= minRealisticTotal) total else 4L * 1024L * 1024L * 1024L
            val safeAvail = if (avail >= minRealisticAvail) avail else (safeTotal * 0.55).toLong()
            safeTotal to safeAvail
        } catch (_: Exception) {
            val fallbackTotal = 4L * 1024L * 1024L * 1024L
            fallbackTotal to (fallbackTotal * 0.55).toLong()
        }
    }

    private fun readMemInfoBytes(field: String): Long {
        return try {
            val file = File("/proc/meminfo")
            if (!file.canRead()) return 0L
            val line = file.bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(field, ignoreCase = true) }
            } ?: return 0L
            val kb = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return 0L
            kb * 1024L
        } catch (_: Exception) {
            0L
        }
    }

    private fun measureFreeStorageBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes.coerceAtLeast(0L)
        } catch (_: Exception) {
            8L * 1024L * 1024L * 1024L
        }
    }

    /**
     * Reads per-core max frequencies from /sys/devices/system/cpu/cpu[0..N]/cpufreq/cpuinfo_max_freq
     * and categorizes cores into Prime, Big, and Little clusters.
     */
    fun measureCpuTopology(): CpuTopologyProfile {
        val runtimeCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val freqs = mutableListOf<Long>()

        try {
            val cpuRoot = File("/sys/devices/system/cpu")
            val cpuDirs = cpuRoot.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("cpu[0-9]+"))
            }?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }

            if (!cpuDirs.isNullOrEmpty()) {
                for (dir in cpuDirs) {
                    val freqFileCandidates = listOf(
                        File(dir, "cpufreq/cpuinfo_max_freq"),
                        File(dir, "cpufreq/scaling_max_freq")
                    )
                    val khz = freqFileCandidates.firstNotNullOfOrNull { f ->
                        if (f.exists() && f.canRead()) {
                            runCatching { f.readText().trim().toLongOrNull() }.getOrNull()
                        } else null
                    } ?: 0L
                    freqs.add(khz)
                }
            }
        } catch (_: Exception) {
        }

        val totalCores = max(runtimeCores, freqs.size)
        val validFreqs = freqs.filter { it > 0L }

        if (validFreqs.isEmpty()) {
            // Fallback when /sys/devices/system/cpu cpufreq nodes are restricted by SELinux
            val little = (totalCores / 2).coerceAtLeast(1)
            val big = (totalCores - little).coerceAtLeast(1)
            val prime = if (totalCores >= 8) 1 else 0
            val adjustedBig = (big - prime).coerceAtLeast(1)
            return CpuTopologyProfile(
                totalCores = totalCores,
                primeCores = prime,
                bigCores = adjustedBig,
                littleCores = little,
                maxFreqKhz = 2_400_000L,
                minClusterFreqKhz = 1_800_000L,
                perCoreMaxFreqKhz = List(totalCores) { 2_400_000L }
            )
        }

        val maxFreq = validFreqs.maxOrNull() ?: 2_400_000L
        val minFreq = validFreqs.minOrNull() ?: 1_800_000L
        val distinctClusters = validFreqs.map { (it / 50_000L) * 50_000L }.distinct().sorted()

        var primeCount = 0
        var bigCount = 0
        var littleCount = 0

        if (distinctClusters.size <= 1) {
            // Symmetric SMP cores (e.g. emulator or uniform cluster)
            bigCount = (totalCores / 2).coerceAtLeast(1)
            littleCount = (totalCores - bigCount).coerceAtLeast(0)
        } else if (distinctClusters.size == 2) {
            val threshold = (maxFreq + minFreq) / 2L
            validFreqs.forEach { f ->
                if (f >= threshold) bigCount++ else littleCount++
            }
        } else {
            // 3+ clusters: Little + Big + Prime (e.g. 1+3+4, 1+4+3, 2+6)
            val primeThreshold = (maxFreq * 0.93).toLong()
            val littleThreshold = (minFreq * 1.15).toLong()
            validFreqs.forEach { f ->
                when {
                    f >= primeThreshold -> primeCount++
                    f <= littleThreshold -> littleCount++
                    else -> bigCount++
                }
            }
            if (bigCount == 0 && primeCount > 1) {
                bigCount = primeCount - 1
                primeCount = 1
            }
        }

        return CpuTopologyProfile(
            totalCores = totalCores,
            primeCores = primeCount,
            bigCores = bigCount.coerceAtLeast(1),
            littleCores = littleCount,
            maxFreqKhz = maxFreq,
            minClusterFreqKhz = minFreq,
            perCoreMaxFreqKhz = freqs
        )
    }

    private fun measureSocIdentity(): Pair<String, String> {
        var manufacturer = ""
        var model = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manufacturer = Build.SOC_MANUFACTURER ?: ""
            model = Build.SOC_MODEL ?: ""
        }
        if (model.isBlank() || model.equals("unknown", ignoreCase = true)) {
            model = Build.HARDWARE ?: Build.BOARD ?: ""
            try {
                val cpuInfo = File("/proc/cpuinfo")
                if (cpuInfo.canRead()) {
                    cpuInfo.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.startsWith("Hardware", ignoreCase = true) ||
                                line.startsWith("model name", ignoreCase = true)
                            ) {
                                val v = line.substringAfter(":").trim()
                                if (v.isNotBlank()) model = v
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (manufacturer.isBlank() || manufacturer.equals("unknown", ignoreCase = true)) {
            val lower = model.lowercase()
            manufacturer = when {
                lower.contains("sm8") || lower.contains("sm7") || lower.contains("sdm") ||
                    lower.contains("snapdragon") || lower.contains("qcom") || lower.contains("taro") ||
                    lower.contains("kalama") || lower.contains("pineapple") || lower.contains("sun") -> "Qualcomm"
                lower.contains("tensor") || lower.contains("gs101") || lower.contains("gs201") ||
                    lower.contains("zuma") -> "Google"
                lower.contains("mt6") || lower.contains("mt8") || lower.contains("dimensity") ||
                    lower.contains("helio") -> "MediaTek"
                lower.contains("exynos") || lower.contains("s5e") -> "Samsung"
                else -> Build.MANUFACTURER ?: "ARM"
            }
        }
        return manufacturer.ifBlank { "ARM" } to model.ifBlank { "ARM64" }
    }

    fun detectHighEndSoc(
        manufacturer: String,
        model: String,
        topology: CpuTopologyProfile
    ): Boolean {
        val combined = "$manufacturer $model".lowercase()
        val highEndPatterns = listOf(
            "sm84", "sm85", "sm86", "sm87", // Snapdragon 8 Gen 1/2/3/Elite
            "snapdragon 8",
            "8 gen",
            "8 elite",
            "kalama", "pineapple", "taro", "lahaina", "sun",
            "tensor g2", "tensor g3", "tensor g4", "zuma", "gs201",
            "dimensity 9000", "dimensity 9200", "dimensity 9300", "dimensity 9400",
            "mt6983", "mt6985", "mt6989", "mt6991",
            "exynos 2200", "exynos 2400", "s5e9925", "s5e9945"
        )
        val lowEndPatterns = listOf(
            "helio", "mt676", "mt673", "sm6", "sm4", "sdm4", "sdm6", "unisoc"
        )
        if (lowEndPatterns.any { combined.contains(it) }) return false
        if (highEndPatterns.any { combined.contains(it) }) return true
        return topology.maxFreqKhz >= 2_950_000L && topology.performanceCores >= 4
    }

    private fun measureThermalState(context: Context): Pair<Int, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    val status = pm.currentThermalStatus
                    return status to thermalCodeToLabel(status)
                }
            } catch (_: Exception) {
            }
        }
        return 0 to "NOMINAL"
    }

    private fun thermalCodeToLabel(code: Int): String = when (code) {
        0 -> "NOMINAL"
        1 -> "LIGHT"
        2 -> "MODERATE"
        3 -> "SEVERE"
        4 -> "CRITICAL"
        5 -> "EMERGENCY"
        else -> "SHUTDOWN"
    }

    private fun measureBatterySaver(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }

    private fun measureVulkanSupport(context: Context): Pair<Boolean, String> {
        return try {
            val pm = context.packageManager
            val hasLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            } else false
            val hasVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            } else false
            val hasCompute = hasLevel || hasVersion
            val label = if (hasCompute) "Vulkan Compute Ready" else "CPU NEON SIMD"
            hasCompute to label
        } catch (_: Exception) {
            false to "CPU NEON SIMD"
        }
    }

    /**
     * Quick (~5–15ms) on-device CPU FP32 dot-product and memory-bandwidth micro-benchmark.
     * Used to adjust the nominal DeviceTier up or down based on real silicon throughput.
     */
    fun runMicroBenchmark(): MicroBenchmarkScore {
        val startNs = System.nanoTime()

        // 1. FP32 Dot Product throughput (measuring Q4/FP32 matrix-vector inner loop)
        val vecLen = 4096
        val iterations = 120
        val a = FloatArray(vecLen) { (it and 31) * 0.03125f }
        val b = FloatArray(vecLen) { ((it + 7) and 31) * 0.015625f }
        var acc = 0f
        val cpuStartNs = System.nanoTime()
        for (iter in 0 until iterations) {
            var sum0 = 0f
            var sum1 = 0f
            var i = 0
            while (i < vecLen) {
                sum0 += a[i] * b[i]
                sum1 += a[i + 1] * b[i + 1]
                i += 2
            }
            acc += (sum0 + sum1) * 0.0001f
        }
        val cpuElapsedNs = (System.nanoTime() - cpuStartNs).coerceAtLeast(1_000L)
        val totalFlops = vecLen.toDouble() * 2.0 * iterations.toDouble()
        val cpuGops = (totalFlops / cpuElapsedNs.toDouble()) // GFLOP/s

        // 2. Memory streaming read/write bandwidth
        val bufLen = 131_072 // 512 KB IntArray
        val src = IntArray(bufLen) { it }
        val dst = IntArray(bufLen)
        val memReps = 24
        val memStartNs = System.nanoTime()
        for (r in 0 until memReps) {
            System.arraycopy(src, 0, dst, 0, bufLen)
            dst[r and 1023] += acc.toInt()
        }
        val memElapsedNs = (System.nanoTime() - memStartNs).coerceAtLeast(1_000L)
        val bytesTransferred = bufLen.toDouble() * 4.0 * 2.0 * memReps.toDouble()
        val memBwGbPerSec = (bytesTransferred / memElapsedNs.toDouble()) // GB/s

        val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

        // Normalize to 0..100 score
        val cpuNorm = ((cpuGops / 4.5) * 100.0).coerceIn(10.0, 100.0)
        val memNorm = ((memBwGbPerSec / 18.0) * 100.0).coerceIn(10.0, 100.0)
        val composite = (cpuNorm * 0.55 + memNorm * 0.45).roundToInt().coerceIn(10, 100)

        return MicroBenchmarkScore(
            cpuGops = ((cpuGops * 100.0).roundToInt() / 100.0),
            memBandwidthGbPerSec = ((memBwGbPerSec * 100.0).roundToInt() / 100.0),
            compositeScore = composite,
            durationMs = totalMs
        )
    }

    /**
     * Adjusts the base [RamClass] by at most 1 step based on SoC class (prime/big cores, max frequency,
     * high-end SoC identity, and micro-benchmark), while never exceeding physical RAM safety bounds.
     */
    fun adjustRamClassBySoc(
        baseRamClass: RamClass,
        isHighEndSoc: Boolean,
        topology: CpuTopologyProfile,
        benchmark: MicroBenchmarkScore
    ): RamClass {
        if (baseRamClass == RamClass.LOW) return RamClass.LOW
        val isWeakCpu = !isHighEndSoc && topology.maxFreqKhz < 2_100_000L && benchmark.compositeScore <= 28
        if (isWeakCpu) {
            return baseRamClass.stepDown()
        }
        return baseRamClass
    }

    fun computeBaseTier(
        totalRamBytes: Long,
        isHighEndSoc: Boolean,
        performanceCores: Int
    ): DeviceTier {
        val totalGb = totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return when {
            totalGb <= 3.6 -> DeviceTier.LOW
            totalGb <= 6.6 -> DeviceTier.MID
            totalGb >= 11.4 && (isHighEndSoc || performanceCores >= 6) -> DeviceTier.ULTRA
            else -> DeviceTier.HIGH
        }
    }

    fun adjustTierByBenchmarkAndThermals(
        baseTier: DeviceTier,
        totalRamBytes: Long,
        isHighEndSoc: Boolean,
        benchmark: MicroBenchmarkScore,
        thermalCode: Int
    ): DeviceTier {
        val totalGb = totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        var tier = baseTier

        // Never promote a <=3.6GB phone out of LOW or a <=6.6GB phone into ULTRA due to hard RAM limits,
        // but adjust tier up/down when CPU/memory bandwidth warrants it:
        if (benchmark.compositeScore >= 85 && tier == DeviceTier.HIGH && totalGb >= 11.2) {
            tier = DeviceTier.ULTRA
        } else if (benchmark.compositeScore >= 78 && tier == DeviceTier.MID && totalGb >= 5.8 && isHighEndSoc) {
            tier = DeviceTier.HIGH
        } else if (benchmark.compositeScore <= 26 && tier != DeviceTier.LOW) {
            tier = tier.stepDown()
        }

        // Severe thermal throttling steps effective tier down by 1
        if (thermalCode >= 3 && tier != DeviceTier.LOW) {
            tier = tier.stepDown()
        }
        return tier
    }
}
