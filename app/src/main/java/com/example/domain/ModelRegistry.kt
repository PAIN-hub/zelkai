package com.example.domain

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.roundToLong

data class ModelRegistryEntry(
    val id: String,
    val name: String,
    val family: String,
    val params: String,
    val paramsBillions: Double,
    val quant: String,
    val format: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val ramRequiredBytes: Long,
    val contextDefault: Int,
    val runtime: String,
    val licenseUrl: String,
    val huggingFaceRepo: String = "",
    val specialty: String = "",
    val qualityScore: Int = 70,
    val minApiLevel: Int = 24
) {
    val sizeMb: Long
        get() = (sizeBytes / (1024L * 1024L)).coerceAtLeast(1L)

    val ramRequiredGb: Double
        get() = ramRequiredBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    val minRamGb: Double
        get() = (ramRequiredGb * 10.0).roundToLong() / 10.0

    val recommendedRamGb: Double
        get() = ((ramRequiredGb * 1.22) * 10.0).roundToLong() / 10.0

    fun toLocalLlmSpec(): LocalLlmSpec {
        return LocalLlmSpec(
            id = id,
            name = name,
            family = family,
            parameterCount = params,
            quantization = quant,
            downloadSizeMb = sizeMb,
            minRamGb = minRamGb,
            recommendedRamGb = recommendedRamGb,
            minFreeStorageGb = ((sizeBytes * 1.10) / (1024.0 * 1024.0 * 1024.0) * 10.0).roundToLong() / 10.0,
            requires64Bit = true,
            specialty = specialty.ifBlank { "$family ($params $quant) on-device GGUF model" },
            huggingFaceRepo = huggingFaceRepo.ifBlank {
                downloadUrl.substringAfter("huggingface.co/").substringBefore("/resolve/")
            },
            downloadUrl = downloadUrl,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            ramRequiredBytes = ramRequiredBytes,
            contextDefault = contextDefault,
            runtime = runtime,
            licenseUrl = licenseUrl,
            paramsBillions = paramsBillions,
            qualityScore = qualityScore,
            minApiLevel = minApiLevel
        )
    }
}

object ModelRegistry {

    private val HEX_SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

    @Volatile
    private var cachedEntries: List<ModelRegistryEntry>? = null

    fun loadModels(context: Context? = null): List<ModelRegistryEntry> {
        cachedEntries?.let { if (it.isNotEmpty()) return it }

        val jsonText = try {
            context?.assets?.open("models.json")?.bufferedReader()?.use { it.readText() }
                ?: readFromFilesystemFallback()
        } catch (_: Exception) {
            readFromFilesystemFallback()
        }

        val parsed = parseModelsJson(jsonText)
        if (parsed.isNotEmpty()) {
            cachedEntries = parsed
        }
        return parsed
    }

    private fun readFromFilesystemFallback(): String {
        val candidatePaths = listOf(
            "app/src/main/assets/models.json",
            "src/main/assets/models.json"
        )
        for (path in candidatePaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.readText()
            }
        }
        return FALLBACK_MODELS_JSON
    }

    fun parseModelsJson(jsonString: String): List<ModelRegistryEntry> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonString)
            val list = ArrayList<ModelRegistryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sizeBytes = obj.optLong("sizeBytes", 1_000_000_000L)
                val defaultCtx = obj.optInt("contextDefault", 4096)
                val kvCacheBytes = defaultCtx.toLong() * 128L * 1024L
                val computedRamReq = ((sizeBytes + kvCacheBytes) * 1.25).toLong()
                val ramRequiredBytes = obj.optLong("ramRequiredBytes", computedRamReq)
                val paramsStr = obj.optString("params", "1.5B")
                val paramsBillions = obj.optDouble(
                    "paramsBillions",
                    paramsStr.uppercase().removeSuffix("B").toDoubleOrNull() ?: 1.5
                )

                list.add(
                    ModelRegistryEntry(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        family = obj.optString("family", "GGUF Local"),
                        params = paramsStr,
                        paramsBillions = paramsBillions,
                        quant = obj.optString("quant", "Q4_K_M"),
                        format = obj.optString("format", "GGUF"),
                        downloadUrl = obj.getString("downloadUrl"),
                        sha256 = obj.optString("sha256", ""),
                        sizeBytes = sizeBytes,
                        ramRequiredBytes = ramRequiredBytes,
                        contextDefault = defaultCtx,
                        runtime = obj.optString("runtime", "llama.cpp-gguf"),
                        licenseUrl = obj.optString("licenseUrl", "https://huggingface.co"),
                        huggingFaceRepo = obj.optString("huggingFaceRepo", ""),
                        specialty = obj.optString("specialty", ""),
                        qualityScore = obj.optInt("qualityScore", 70),
                        minApiLevel = obj.optInt("minApiLevel", 24)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findById(id: String, context: Context? = null): ModelRegistryEntry? {
        val norm = id.trim().lowercase()
        val entries = loadModels(context)
        return entries.find {
            it.id.lowercase() == norm ||
                it.name.lowercase() == norm ||
                norm.contains(it.id.lowercase())
        }
    }

    fun isValidHexSha256(candidate: String?): Boolean {
        val cleaned = candidate?.trim()?.trim('"')?.lowercase().orEmpty()
        return HEX_SHA256_REGEX.matches(cleaned)
    }

    fun hasGgufMagicHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.readByte().toInt() and 0xFF
                val b1 = raf.readByte().toInt() and 0xFF
                val b2 = raf.readByte().toInt() and 0xFF
                val b3 = raf.readByte().toInt() and 0xFF
                b0 == 0x47 && b1 == 0x47 && b2 == 0x55 && b3 == 0x46 // "GGUF"
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies downloaded GGUF file integrity per Part C #3 & Amendment #4:
     * 1. Checks non-empty file and `"GGUF"` magic header (`0x47 0x47 0x55 0x46`).
     * 2. If [expectedContentLength] > 0, checks `file.length() == expectedContentLength`.
     * 3. If [expectedSha256] (from manifest or `x-linked-etag` / LFS OID) is a 64-char hex SHA-256,
     *    computes full streaming SHA-256 of [file] and compares. On mismatch, deletes [file].
     * 4. If no 64-char hex SHA-256 is available, marks `integrityUnverified = true` while still
     *    enforcing `"GGUF"` magic header and `Content-Length`.
     */
    fun verifyDownloadedFileSha256(
        file: File,
        expectedSha256: String,
        expectedContentLength: Long = -1L
    ): Sha256VerificationResult {
        if (!file.exists() || file.length() < 4L) {
            runCatching { file.delete() }
            return Sha256VerificationResult(
                verified = false,
                integrityUnverified = false,
                computedSha256 = "",
                reason = "Downloaded file is missing or truncated."
            )
        }

        if (!hasGgufMagicHeader(file)) {
            runCatching { file.delete() }
            return Sha256VerificationResult(
                verified = false,
                integrityUnverified = false,
                computedSha256 = "",
                reason = "Invalid GGUF header (missing 'GGUF' magic bytes). Corrupted file deleted."
            )
        }

        if (expectedContentLength > 0L && file.length() != expectedContentLength) {
            val actualLen = file.length()
            runCatching { file.delete() }
            return Sha256VerificationResult(
                verified = false,
                integrityUnverified = false,
                computedSha256 = "",
                reason = "File size mismatch (expected $expectedContentLength bytes, got $actualLen bytes). Corrupted file deleted."
            )
        }

        val cleanExpected = expectedSha256.trim().trim('"').lowercase()
        val computed = computeFileSha256(file)

        if (!isValidHexSha256(cleanExpected)) {
            return Sha256VerificationResult(
                verified = true,
                integrityUnverified = true,
                computedSha256 = computed,
                reason = "Integrity unverified (GGUF magic & Content-Length verified; SHA-256 $computed)."
            )
        }

        return if (computed.equals(cleanExpected, ignoreCase = true)) {
            Sha256VerificationResult(
                verified = true,
                integrityUnverified = false,
                computedSha256 = computed,
                reason = "SHA-256 verified ($computed)."
            )
        } else {
            runCatching { file.delete() }
            Sha256VerificationResult(
                verified = false,
                integrityUnverified = false,
                computedSha256 = computed,
                reason = "SHA-256 mismatch (expected ${cleanExpected.take(12)}…, got ${computed.take(12)}…). Corrupted file deleted."
            )
        }
    }

    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val FALLBACK_MODELS_JSON = """
    [
      {
        "id": "smollm2-360m-instruct",
        "name": "SmolLM2 360M Instruct",
        "family": "HuggingFace SmolLM2",
        "params": "0.36B",
        "paramsBillions": 0.36,
        "quant": "Q8_0",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q8_0.gguf",
        "sha256": "",
        "sizeBytes": 386000000,
        "ramRequiredBytes": 900000000,
        "contextDefault": 2048,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct/blob/main/LICENSE",
        "qualityScore": 44,
        "minApiLevel": 24
      },
      {
        "id": "qwen2.5-coder-0.5b-instruct",
        "name": "Qwen2.5-Coder 0.5B Instruct",
        "family": "Alibaba Qwen2.5-Coder",
        "params": "0.5B",
        "paramsBillions": 0.5,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        "sha256": "",
        "sizeBytes": 415000000,
        "ramRequiredBytes": 1050000000,
        "contextDefault": 4096,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct/blob/main/LICENSE",
        "qualityScore": 54,
        "minApiLevel": 24
      },
      {
        "id": "qwen2.5-coder-1.5b-instruct",
        "name": "Qwen2.5-Coder 1.5B Instruct",
        "family": "Alibaba Qwen2.5-Coder",
        "params": "1.5B",
        "paramsBillions": 1.5,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        "sha256": "",
        "sizeBytes": 1030000000,
        "ramRequiredBytes": 2100000000,
        "contextDefault": 4096,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct/blob/main/LICENSE",
        "qualityScore": 71,
        "minApiLevel": 24
      },
      {
        "id": "deepseek-r1-distill-qwen-1.5b",
        "name": "DeepSeek-R1-Distill 1.5B",
        "family": "DeepSeek R1 Distill",
        "params": "1.5B",
        "paramsBillions": 1.5,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        "sha256": "",
        "sizeBytes": 1117000000,
        "ramRequiredBytes": 2250000000,
        "contextDefault": 4096,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B/blob/main/LICENSE",
        "qualityScore": 73,
        "minApiLevel": 24
      },
      {
        "id": "qwen2.5-coder-3b-instruct",
        "name": "Qwen2.5-Coder 3B Instruct",
        "family": "Alibaba Qwen2.5-Coder",
        "params": "3B",
        "paramsBillions": 3.0,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        "sha256": "",
        "sizeBytes": 2020000000,
        "ramRequiredBytes": 3550000000,
        "contextDefault": 4096,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct/blob/main/LICENSE",
        "qualityScore": 81,
        "minApiLevel": 24
      },
      {
        "id": "qwen2.5-coder-7b-instruct",
        "name": "Qwen2.5-Coder 7B Instruct",
        "family": "Alibaba Qwen2.5-Coder",
        "params": "7B",
        "paramsBillions": 7.6,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
        "sha256": "",
        "sizeBytes": 4680000000,
        "ramRequiredBytes": 7400000000,
        "contextDefault": 8192,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct/blob/main/LICENSE",
        "qualityScore": 91,
        "minApiLevel": 26
      },
      {
        "id": "deepseek-r1-distill-llama-8b",
        "name": "DeepSeek-R1-Distill Llama 8B",
        "family": "DeepSeek R1 Distill",
        "params": "8B",
        "paramsBillions": 8.0,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Llama-8B-GGUF/resolve/main/DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf",
        "sha256": "",
        "sizeBytes": 4920000000,
        "ramRequiredBytes": 7800000000,
        "contextDefault": 8192,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Llama-8B/blob/main/LICENSE",
        "qualityScore": 92,
        "minApiLevel": 26
      },
      {
        "id": "qwen2.5-coder-14b-instruct",
        "name": "Qwen2.5-Coder 14B Instruct",
        "family": "Alibaba Qwen2.5-Coder",
        "params": "14B",
        "paramsBillions": 14.7,
        "quant": "Q4_K_M",
        "format": "GGUF",
        "downloadUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-14B-Instruct-GGUF/resolve/main/qwen2.5-coder-14b-instruct-q4_k_m.gguf",
        "sha256": "",
        "sizeBytes": 8990000000,
        "ramRequiredBytes": 12800000000,
        "contextDefault": 8192,
        "runtime": "llama.cpp-gguf",
        "licenseUrl": "https://huggingface.co/Qwen/Qwen2.5-Coder-14B-Instruct/blob/main/LICENSE",
        "qualityScore": 97,
        "minApiLevel": 29
      }
    ]
    """
}

data class Sha256VerificationResult(
    val verified: Boolean,
    val integrityUnverified: Boolean = false,
    val computedSha256: String,
    val reason: String
)
