package com.example.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.providerModelsCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_provider_models_cache_v1"
)

/**
 * Single representation of a cloud or local model option.
 * Per Part B #1 & Amendment #1 & #7:
 * - [id] and [displayName] are the exact same string (with `"models/"` stripped for Gemini).
 * - [supportsToolCalls] defaults to `false`.
 * - [isUnverified] is true only for the Anthropic offline/failure fallback seed list.
 */
data class ModelOption(
    val provider: String,
    val id: String,
    val displayName: String = id,
    val isUnverified: Boolean = false,
    val supportsToolCalls: Boolean = false
)

sealed class ApiKeyTestResult {
    data object Idle : ApiKeyTestResult()
    data object Testing : ApiKeyTestResult()
    data class Valid(val modelCount: Int) : ApiKeyTestResult()
    data class InvalidKey(val message: String) : ApiKeyTestResult()
    data class QuotaExceeded(val message: String) : ApiKeyTestResult()
    data class NetworkError(val message: String) : ApiKeyTestResult()
}

typealias KeyTestResult = ApiKeyTestResult

data class ModelReplacementResolution(
    val refreshedModels: List<ModelOption>,
    val autoSwappedModel: ModelOption?,
    val userNotice: String
) {
    val userNoticeMessage: String get() = userNotice
}

/**
 * Dynamic provider model discovery with 24-hour DataStore caching, automatic cache invalidation
 * whenever a provider's API key changes, OpenAI heuristic filtering with "Show all" toggle,
 * and strict 404 replacement resolution.
 */
class ProviderModelRepository(
    private val context: Context? = null,
    private val userApiKeyStore: UserApiKeyStore,
    private var httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours

        val ANTHROPIC_UNVERIFIED_SEEDS: List<ModelOption> = listOf(
            ModelOption(
                provider = "anthropic",
                id = "claude-fable-5-1",
                displayName = "claude-fable-5-1",
                isUnverified = true,
                supportsToolCalls = false
            ),
            ModelOption(
                provider = "anthropic",
                id = "claude-opus-5-5",
                displayName = "claude-opus-5-5",
                isUnverified = true,
                supportsToolCalls = false
            ),
            ModelOption(
                provider = "anthropic",
                id = "claude-sonnet-5-5",
                displayName = "claude-sonnet-5-5",
                isUnverified = true,
                supportsToolCalls = false
            ),
            ModelOption(
                provider = "anthropic",
                id = "claude-haiku-5-5",
                displayName = "claude-haiku-5-5",
                isUnverified = true,
                supportsToolCalls = false
            )
        )

        private val KEY_SHOW_ALL_OPENAI = booleanPreferencesKey("show_all_openai_models")

        private fun modelsCacheKey(provider: String) =
            stringPreferencesKey("models_json_${provider.lowercase().trim()}")

        private fun modelsTimestampKey(provider: String) =
            longPreferencesKey("models_ts_${provider.lowercase().trim()}")

        /**
         * Heuristic filter for OpenAI `/v1/models` chat-capable models (Amendment #7).
         */
        fun isOpenAiChatCapableModelId(modelId: String): Boolean {
            val lower = modelId.lowercase().trim()
            val excludedSubstrings = listOf(
                "embedding", "tts", "whisper", "dall-e", "moderation",
                "babbage", "davinci", "audio", "realtime", "transcribe", "search", "instruct"
            )
            if (excludedSubstrings.any { lower.contains(it) }) return false
            return lower.startsWith("gpt-") ||
                lower.startsWith("o1") ||
                lower.startsWith("o3") ||
                lower.startsWith("o4") ||
                lower.startsWith("chatgpt-")
        }
    }

    var geminiBaseUrl: String = "https://generativelanguage.googleapis.com"
    var anthropicBaseUrl: String = "https://api.anthropic.com"
    var openAiBaseUrl: String = "https://api.openai.com"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mirrorPrefs = context?.getSharedPreferences("nexus_provider_models_mirror", Context.MODE_PRIVATE)
    private val inMemoryCacheTs = mutableMapOf<String, Long>()
    private val inMemoryCacheJson = mutableMapOf<String, String>()

    private val _geminiModels = MutableStateFlow<List<ModelOption>>(emptyList())
    val geminiModels: StateFlow<List<ModelOption>> = _geminiModels.asStateFlow()

    private val _anthropicModels = MutableStateFlow<List<ModelOption>>(ANTHROPIC_UNVERIFIED_SEEDS)
    val anthropicModels: StateFlow<List<ModelOption>> = _anthropicModels.asStateFlow()

    private val _rawOpenAiModels = MutableStateFlow<List<ModelOption>>(emptyList())
    private val _openAiModels = MutableStateFlow<List<ModelOption>>(emptyList())
    val openAiModels: StateFlow<List<ModelOption>> = _openAiModels.asStateFlow()

    private val _showAllOpenAiModels = MutableStateFlow(
        mirrorPrefs?.getBoolean("show_all_openai_models", false) ?: false
    )
    val showAllOpenAiModels: StateFlow<Boolean> = _showAllOpenAiModels.asStateFlow()

    private val _isFetchingByProvider = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isFetchingByProvider: StateFlow<Map<String, Boolean>> = _isFetchingByProvider.asStateFlow()
    val isFetchingModels: StateFlow<Map<String, Boolean>> get() = isFetchingByProvider

    private val _lastFetchErrorByProvider = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastFetchErrorByProvider: StateFlow<Map<String, String>> = _lastFetchErrorByProvider.asStateFlow()

    init {
        loadCachedFromMirror()
        userApiKeyStore.registerProviderKeyChangeListener { changedProvider ->
            invalidateProviderCache(changedProvider)
            val newKey = userApiKeyStore.getProviderKey(changedProvider)
            if (newKey.isNotBlank()) {
                ioScope.launch {
                    fetchModelsForProvider(changedProvider, forceRefresh = true)
                }
            }
        }
    }

    fun setHttpClientForTesting(client: OkHttpClient) {
        this.httpClient = client
    }

    fun setShowAllOpenAiModels(showAll: Boolean) {
        _showAllOpenAiModels.value = showAll
        mirrorPrefs?.edit()?.putBoolean("show_all_openai_models", showAll)?.apply()
        recomputeOpenAiFilteredList()
        val ctx = context ?: return
        ioScope.launch {
            runCatching {
                ctx.providerModelsCacheDataStore.edit { prefs ->
                    prefs[KEY_SHOW_ALL_OPENAI] = showAll
                }
            }
        }
    }

    private fun recomputeOpenAiFilteredList() {
        val raw = _rawOpenAiModels.value
        _openAiModels.value = if (_showAllOpenAiModels.value) {
            raw
        } else {
            raw.filter { isOpenAiChatCapableModelId(it.id) }
        }
    }

    private fun loadCachedFromMirror() {
        val now = System.currentTimeMillis()
        listOf("gemini", "anthropic", "openai").forEach { provider ->
            val ts = mirrorPrefs?.getLong("ts_$provider", 0L) ?: inMemoryCacheTs[provider] ?: 0L
            val json = mirrorPrefs?.getString("json_$provider", null) ?: inMemoryCacheJson[provider]
            if (!json.isNullOrBlank() && (now - ts) < CACHE_TTL_MS) {
                val parsed = deserializeModels(json)
                applyProviderModels(provider, parsed)
            } else if (provider == "anthropic") {
                _anthropicModels.value = ANTHROPIC_UNVERIFIED_SEEDS
            }
        }
    }

    private fun applyProviderModels(provider: String, models: List<ModelOption>) {
        when (provider.lowercase().trim()) {
            "gemini" -> _geminiModels.value = models
            "anthropic" -> _anthropicModels.value = models.ifEmpty { ANTHROPIC_UNVERIFIED_SEEDS }
            "openai" -> {
                _rawOpenAiModels.value = models
                recomputeOpenAiFilteredList()
            }
        }
    }

    fun invalidateProviderCache(provider: String) {
        val norm = provider.lowercase().trim()
        inMemoryCacheTs.remove(norm)
        inMemoryCacheJson.remove(norm)
        mirrorPrefs?.edit()
            ?.remove("ts_$norm")
            ?.remove("json_$norm")
            ?.apply()
        when (norm) {
            "gemini" -> _geminiModels.value = emptyList()
            "anthropic" -> _anthropicModels.value = ANTHROPIC_UNVERIFIED_SEEDS
            "openai" -> {
                _rawOpenAiModels.value = emptyList()
                _openAiModels.value = emptyList()
            }
        }
        val ctx = context ?: return
        ioScope.launch {
            runCatching {
                ctx.providerModelsCacheDataStore.edit { prefs ->
                    prefs.remove(modelsCacheKey(norm))
                    prefs.remove(modelsTimestampKey(norm))
                }
            }
        }
    }

    fun getModelsForProvider(provider: String): List<ModelOption> {
        return when (provider.lowercase().trim()) {
            "gemini" -> _geminiModels.value
            "anthropic" -> _anthropicModels.value
            "openai" -> _openAiModels.value
            else -> emptyList()
        }
    }

    suspend fun fetchModels(
        provider: String,
        forceRefresh: Boolean = false,
        overrideApiKey: String? = null
    ): Result<List<ModelOption>> = fetchModelsForProvider(provider, forceRefresh, overrideApiKey)

    suspend fun fetchModelsForProvider(
        provider: String,
        forceRefresh: Boolean = false,
        overrideApiKey: String? = null
    ): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
        val normProvider = provider.lowercase().trim()
        val now = System.currentTimeMillis()

        if (!forceRefresh && overrideApiKey == null) {
            val cachedTs = mirrorPrefs?.getLong("ts_$normProvider", 0L) ?: inMemoryCacheTs[normProvider] ?: 0L
            val cachedJson = mirrorPrefs?.getString("json_$normProvider", null) ?: inMemoryCacheJson[normProvider]
            if (!cachedJson.isNullOrBlank() && (now - cachedTs) < CACHE_TTL_MS) {
                val cachedList = deserializeModels(cachedJson)
                if (cachedList.isNotEmpty()) {
                    applyProviderModels(normProvider, cachedList)
                    return@withContext Result.success(getModelsForProvider(normProvider))
                }
            }
        }

        val apiKey = (overrideApiKey ?: userApiKeyStore.getProviderKey(normProvider)).trim()
        if (apiKey.isEmpty()) {
            if (normProvider == "anthropic") {
                _anthropicModels.value = ANTHROPIC_UNVERIFIED_SEEDS
                return@withContext Result.success(ANTHROPIC_UNVERIFIED_SEEDS)
            }
            applyProviderModels(normProvider, emptyList())
            return@withContext Result.failure(IllegalStateException("Missing API key for $normProvider"))
        }

        _isFetchingByProvider.value = _isFetchingByProvider.value + (normProvider to true)
        try {
            val fetched = when (normProvider) {
                "gemini" -> fetchGeminiModels(apiKey)
                "anthropic" -> fetchAnthropicModels(apiKey)
                "openai" -> fetchOpenAiModels(apiKey)
                else -> emptyList()
            }
            saveModelsToCache(normProvider, fetched, now)
            applyProviderModels(normProvider, fetched)
            _lastFetchErrorByProvider.value = _lastFetchErrorByProvider.value - normProvider
            Result.success(getModelsForProvider(normProvider))
        } catch (e: Exception) {
            val safeMessage = NexusToolAndRedactor.redactSecrets(
                e.message ?: "Failed to fetch models",
                userApiKeyStore.allSavedSecretsForRedaction() + listOf(apiKey)
            ).text
            _lastFetchErrorByProvider.value = _lastFetchErrorByProvider.value + (normProvider to safeMessage)
            if (normProvider == "anthropic") {
                _anthropicModels.value = ANTHROPIC_UNVERIFIED_SEEDS
            }
            Result.failure(e)
        } finally {
            _isFetchingByProvider.value = _isFetchingByProvider.value + (normProvider to false)
        }
    }

    /**
     * Tests a user's API key against the provider's `/models` endpoint without leaking secrets.
     */
    suspend fun testApiKey(provider: String, candidateKey: String): ApiKeyTestResult = withContext(Dispatchers.IO) {
        val normProvider = provider.lowercase().trim()
        val cleanKey = candidateKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext ApiKeyTestResult.InvalidKey("Enter an API key first.")
        }
        try {
            val models = when (normProvider) {
                "gemini" -> fetchGeminiModels(cleanKey)
                "anthropic" -> fetchAnthropicModels(cleanKey)
                "openai" -> fetchOpenAiModels(cleanKey)
                else -> return@withContext ApiKeyTestResult.InvalidKey("Unsupported provider: $normProvider")
            }
            val now = System.currentTimeMillis()
            saveModelsToCache(normProvider, models, now)
            applyProviderModels(normProvider, models)
            val visibleCount = getModelsForProvider(normProvider).size
            ApiKeyTestResult.Valid(visibleCount)
        } catch (e: ProviderHttpException) {
            when (e.statusCode) {
                401, 403 -> ApiKeyTestResult.InvalidKey("Invalid or unauthorized API key (HTTP ${e.statusCode}).")
                429 -> ApiKeyTestResult.QuotaExceeded("Quota exceeded or rate limited (HTTP 429).")
                else -> ApiKeyTestResult.NetworkError("Server returned HTTP ${e.statusCode}.")
            }
        } catch (e: IOException) {
            ApiKeyTestResult.NetworkError("Network error — check your connection.")
        } catch (e: Exception) {
            ApiKeyTestResult.NetworkError("Could not verify key.")
        }
    }

    /**
     * Amendment #2: Strict 404 Replacement Policy.
     * Refreshes the provider's model list and auto-swaps ONLY when [errorBody] explicitly names
     * a replacement model that exists in the refreshed list. Never picks "first verified model".
     */
    suspend fun handleModelNotFound(
        provider: String,
        failedModelId: String,
        errorBody: String
    ): ModelReplacementResolution = withContext(Dispatchers.IO) {
        val normProvider = provider.lowercase().trim()
        fetchModelsForProvider(normProvider, forceRefresh = true)
        val available = getModelsForProvider(normProvider).filter { !it.isUnverified }

        val cleanFailed = failedModelId.removePrefix("models/").trim()
        val namedReplacement = available.firstOrNull { candidate ->
            candidate.id !in listOf(cleanFailed, failedModelId) &&
                (errorBody.contains(candidate.id, ignoreCase = true) ||
                    errorBody.contains("models/${candidate.id}", ignoreCase = true))
        }

        if (namedReplacement != null) {
            userApiKeyStore.persistSelectedProviderAndModel(normProvider, namedReplacement.id)
            ModelReplacementResolution(
                refreshedModels = available,
                autoSwappedModel = namedReplacement,
                userNotice = "Model updated: '$cleanFailed' was replaced by '${namedReplacement.id}'."
            )
        } else {
            ModelReplacementResolution(
                refreshedModels = available,
                autoSwappedModel = null,
                userNotice = "Model '$cleanFailed' is no longer available — select a model from the refreshed list."
            )
        }
    }

    /**
     * Amendment #1: Gemini `GET /v1beta/models` with `x-goog-api-key` header.
     * Keep models supporting `generateContent` / `streamGenerateContent` and strip `"models/"` prefix.
     */
    private fun fetchGeminiModels(apiKey: String): List<ModelOption> {
        val base = geminiBaseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/v1beta/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, rawBody)
            }
            return parseGeminiModelsJson(rawBody)
        }
    }

    fun parseGeminiModelsJson(rawBody: String): List<ModelOption> {
        val root = JSONObject(rawBody)
        val modelsArr = root.optJSONArray("models") ?: JSONArray()
        val list = mutableListOf<ModelOption>()
        for (i in 0 until modelsArr.length()) {
            val item = modelsArr.optJSONObject(i) ?: continue
            val rawName = item.optString("name", "").trim()
            if (rawName.isEmpty()) continue
            val methodsArr = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            var supportsGen = false
            for (m in 0 until methodsArr.length()) {
                val method = methodsArr.optString(m, "")
                if (method == "generateContent" || method == "streamGenerateContent") {
                    supportsGen = true
                    break
                }
            }
            if (!supportsGen) continue
            val cleanId = rawName.removePrefix("models/").trim()
            if (cleanId.isNotEmpty()) {
                list.add(
                    ModelOption(
                        provider = "gemini",
                        id = cleanId,
                        displayName = cleanId,
                        isUnverified = false,
                        supportsToolCalls = false
                    )
                )
            }
        }
        return list.distinctBy { it.id }
    }

    private fun fetchAnthropicModels(apiKey: String): List<ModelOption> {
        val base = anthropicBaseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/v1/models")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, rawBody)
            }
            return parseAnthropicModelsJson(rawBody)
        }
    }

    fun parseAnthropicModelsJson(rawBody: String): List<ModelOption> {
        val root = JSONObject(rawBody)
        val dataArr = root.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<ModelOption>()
        for (i in 0 until dataArr.length()) {
            val item = dataArr.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isNotEmpty()) {
                list.add(
                    ModelOption(
                        provider = "anthropic",
                        id = id,
                        displayName = id,
                        isUnverified = false,
                        supportsToolCalls = false
                    )
                )
            }
        }
        return list.distinctBy { it.id }
    }

    private fun fetchOpenAiModels(apiKey: String): List<ModelOption> {
        val base = openAiBaseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, rawBody)
            }
            return parseOpenAiModelsJson(rawBody)
        }
    }

    fun parseOpenAiModelsJson(rawBody: String): List<ModelOption> {
        val root = JSONObject(rawBody)
        val dataArr = root.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<ModelOption>()
        for (i in 0 until dataArr.length()) {
            val item = dataArr.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isNotEmpty()) {
                list.add(
                    ModelOption(
                        provider = "openai",
                        id = id,
                        displayName = id,
                        isUnverified = false,
                        supportsToolCalls = false
                    )
                )
            }
        }
        return list.sortedBy { it.id }.distinctBy { it.id }
    }

    private suspend fun saveModelsToCache(provider: String, models: List<ModelOption>, timestampMs: Long) {
        val norm = provider.lowercase().trim()
        val serialized = serializeModels(models)
        inMemoryCacheTs[norm] = timestampMs
        inMemoryCacheJson[norm] = serialized
        mirrorPrefs?.edit()
            ?.putLong("ts_$norm", timestampMs)
            ?.putString("json_$norm", serialized)
            ?.apply()
        val ctx = context ?: return
        runCatching {
            ctx.providerModelsCacheDataStore.edit { prefs ->
                prefs[modelsCacheKey(norm)] = serialized
                prefs[modelsTimestampKey(norm)] = timestampMs
            }
        }
    }

    private fun serializeModels(models: List<ModelOption>): String {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("provider", m.provider)
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("isUnverified", m.isUnverified)
                    put("supportsToolCalls", m.supportsToolCalls)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeModels(json: String): List<ModelOption> {
        return runCatching {
            val arr = JSONArray(json)
            val result = mutableListOf<ModelOption>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val provider = obj.optString("provider", "").trim()
                if (id.isNotEmpty() && provider.isNotEmpty()) {
                    result.add(
                        ModelOption(
                            provider = provider,
                            id = id,
                            displayName = id,
                            isUnverified = obj.optBoolean("isUnverified", false),
                            supportsToolCalls = obj.optBoolean("supportsToolCalls", false)
                        )
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }
}

class ProviderHttpException(
    val statusCode: Int,
    val rawErrorBody: String
) : IOException("HTTP $statusCode")
