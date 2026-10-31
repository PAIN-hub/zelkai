package com.example.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.modelSelectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_model_selection_datastore"
)

/**
 * Stores user-supplied personal API keys per platform provider, per model, and optional Hugging Face token
 * encrypted at rest via Android Keystore (`AES/GCM/NoPadding`), strictly in private on-device storage
 * (excluded from backup/transfer, never synced to cloud or packaged in APK, never logged),
 * and persists the single source of truth (selectedProvider + selectedModelId) in Jetpack DataStore.
 */
class UserApiKeyStore(private val context: Context) {

    companion object {
        private val KEY_SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        private val KEY_SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_user_api_keys_master_aes_v1"
        private const val ENCRYPTED_PREFIX = "enc_v1:"
        private const val GCM_TAG_BITS = 128
    }

    private val encryptedPrefs = context.getSharedPreferences("nexus_encrypted_api_keys", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("nexus_personal_api_keys", Context.MODE_PRIVATE)
    private val selectionSyncPrefs = context.getSharedPreferences("nexus_model_selection_mirror", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _keysMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val keysMap: StateFlow<Map<String, String>> = _keysMap.asStateFlow()

    private val _providerKeyChangedEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val providerKeyChangedEvents: SharedFlow<String> = _providerKeyChangedEvents.asSharedFlow()

    private val providerChangeListeners = mutableListOf<(String) -> Unit>()

    private val _selectedProvider = MutableStateFlow(
        normalizeProvider(selectionSyncPrefs.getString("selected_provider", "gemini"))
    )
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModelId = MutableStateFlow(
        selectionSyncPrefs.getString("selected_model_id", "")?.trim().orEmpty()
    )
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    init {
        migrateLegacyPlaintextKeysIfNeeded()
        _keysMap.value = loadAllKeys()
        ioScope.launch {
            runCatching {
                context.modelSelectionDataStore.data
                    .catch { /* keep mirror values on IO error */ }
                    .collect { dataPrefs ->
                        val dsProvider = dataPrefs[KEY_SELECTED_PROVIDER]?.trim()?.lowercase()
                        val dsModel = dataPrefs[KEY_SELECTED_MODEL_ID]?.trim()
                        if (!dsProvider.isNullOrBlank()) {
                            _selectedProvider.value = normalizeProvider(dsProvider)
                        }
                        if (dsModel != null) {
                            _selectedModelId.value = dsModel
                        }
                    }
            }
        }
    }

    private fun normalizeProvider(provider: String?): String {
        val clean = provider?.trim()?.lowercase().orEmpty()
        return when (clean) {
            "fable" -> "anthropic"
            "gemini", "anthropic", "openai", "local" -> clean
            else -> "gemini"
        }
    }

    fun registerProviderKeyChangeListener(listener: (String) -> Unit) {
        synchronized(providerChangeListeners) {
            providerChangeListeners.add(listener)
        }
    }

    private fun notifyProviderKeyChanged(provider: String) {
        val norm = normalizeProvider(provider)
        _providerKeyChangedEvents.tryEmit(norm)
        val snapshot = synchronized(providerChangeListeners) { providerChangeListeners.toList() }
        snapshot.forEach { runCatching { it(norm) } }
    }

    fun persistSelectedProviderAndModel(provider: String, modelId: String) {
        val cleanProvider = normalizeProvider(provider)
        val cleanModel = modelId.trim()
        _selectedProvider.value = cleanProvider
        _selectedModelId.value = cleanModel
        selectionSyncPrefs.edit()
            .putString("selected_provider", cleanProvider)
            .putString("selected_model_id", cleanModel)
            .apply()
        ioScope.launch {
            runCatching {
                context.modelSelectionDataStore.edit { mutablePrefs ->
                    mutablePrefs[KEY_SELECTED_PROVIDER] = cleanProvider
                    mutablePrefs[KEY_SELECTED_MODEL_ID] = cleanModel
                }
            }
        }
    }

    fun hasPersistedModelSelection(): Boolean {
        return selectionSyncPrefs.contains("selected_provider") &&
            selectionSyncPrefs.contains("selected_model_id")
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return@runCatching existing.secretKey

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }

    private fun encryptValue(plainText: String): String {
        val secretKey = getOrCreateSecretKey() ?: return plainText
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$ENCRYPTED_PREFIX$ivB64:$dataB64"
        }.getOrDefault(plainText)
    }

    private fun decryptValue(stored: String): String {
        if (!stored.startsWith(ENCRYPTED_PREFIX)) return stored
        val secretKey = getOrCreateSecretKey() ?: return ""
        return runCatching {
            val payload = stored.removePrefix(ENCRYPTED_PREFIX)
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return@runCatching ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun migrateLegacyPlaintextKeysIfNeeded() {
        val legacyAll = legacyPrefs.all
        if (legacyAll.isEmpty()) return
        val editor = encryptedPrefs.edit()
        legacyAll.forEach { (k, v) ->
            if (v is String && v.isNotBlank()) {
                val targetKey = if (k == "provider:fable") "provider:anthropic" else k
                editor.putString(targetKey, encryptValue(v.trim()))
            }
        }
        editor.apply()
        legacyPrefs.edit().clear().apply()
    }

    private fun loadAllKeys(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        encryptedPrefs.all.forEach { (k, v) ->
            if (v is String && v.isNotBlank()) {
                val decrypted = decryptValue(v).trim()
                if (decrypted.isNotBlank()) {
                    result[k] = decrypted
                }
            }
        }
        return result
    }

    /**
     * Resolves the user's personal API key for a given [provider] and [modelId].
     * Checks model-specific key first (`model:<modelId>`), then provider key (`provider:<provider>`).
     * Never returns any built-in or developer key.
     */
    fun getApiKey(provider: String, modelId: String? = null): String {
        val normProvider = normalizeProvider(provider)
        if (normProvider == "local") return ""
        val normModel = modelId?.lowercase()?.trim().orEmpty()
        val current = _keysMap.value
        if (normModel.isNotEmpty()) {
            current["model:$normModel"]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return current["provider:$normProvider"]?.takeIf { it.isNotBlank() }
            ?: ""
    }

    fun hasApiKeyFor(provider: String, modelId: String? = null): Boolean {
        val normProvider = normalizeProvider(provider)
        if (normProvider == "local") return true
        return getApiKey(normProvider, modelId).isNotBlank()
    }

    fun getProviderKey(provider: String): String {
        val normProvider = normalizeProvider(provider)
        return _keysMap.value["provider:$normProvider"].orEmpty()
    }

    fun setProviderKey(provider: String, apiKey: String) {
        setApiKey(provider = provider, modelId = null, apiKey = apiKey, applyToSpecificModelOnly = false)
    }

    fun getModelSpecificKey(modelId: String): String {
        return _keysMap.value["model:${modelId.lowercase().trim()}"].orEmpty()
    }

    fun getHuggingFaceToken(): String {
        return _keysMap.value["hf:access_token"].orEmpty()
    }

    fun setHuggingFaceToken(token: String) {
        val trimmed = token.trim()
        val editor = encryptedPrefs.edit()
        if (trimmed.isEmpty()) {
            editor.remove("hf:access_token")
        } else {
            editor.putString("hf:access_token", encryptValue(trimmed))
        }
        editor.apply()
        _keysMap.value = loadAllKeys()
    }

    fun setApiKey(
        provider: String,
        modelId: String?,
        apiKey: String,
        applyToSpecificModelOnly: Boolean = false
    ) {
        val trimmed = apiKey.trim()
        val editor = encryptedPrefs.edit()
        val normProvider = normalizeProvider(provider)
        val prevKey = getProviderKey(normProvider)
        if (applyToSpecificModelOnly && !modelId.isNullOrBlank()) {
            val keyName = "model:${modelId.lowercase().trim()}"
            if (trimmed.isEmpty()) editor.remove(keyName) else editor.putString(keyName, encryptValue(trimmed))
        } else {
            val keyName = "provider:$normProvider"
            if (trimmed.isEmpty()) editor.remove(keyName) else editor.putString(keyName, encryptValue(trimmed))
            if (!modelId.isNullOrBlank()) {
                val modelKeyName = "model:${modelId.lowercase().trim()}"
                if (trimmed.isEmpty()) editor.remove(modelKeyName) else editor.putString(modelKeyName, encryptValue(trimmed))
            }
        }
        editor.apply()
        _keysMap.value = loadAllKeys()
        if (prevKey != getProviderKey(normProvider) || !applyToSpecificModelOnly) {
            notifyProviderKeyChanged(normProvider)
        }
    }

    fun saveAllProviderKeys(
        geminiKey: String,
        anthropicKey: String,
        openaiKey: String,
        fableKey: String = "",
        activeModelId: String? = null,
        activeModelKey: String? = null,
        huggingFaceToken: String? = null
    ) {
        val prevGemini = getProviderKey("gemini")
        val prevAnthropic = getProviderKey("anthropic")
        val prevOpenai = getProviderKey("openai")

        val editor = encryptedPrefs.edit()
        fun putOrRemove(k: String, v: String) {
            val t = v.trim()
            if (t.isEmpty()) editor.remove(k) else editor.putString(k, encryptValue(t))
        }
        putOrRemove("provider:gemini", geminiKey)
        val resolvedAnthropic = anthropicKey.ifBlank { fableKey }
        putOrRemove("provider:anthropic", resolvedAnthropic)
        putOrRemove("provider:openai", openaiKey)
        editor.remove("provider:fable")
        if (huggingFaceToken != null) {
            putOrRemove("hf:access_token", huggingFaceToken)
        }
        if (!activeModelId.isNullOrBlank() && activeModelKey != null) {
            putOrRemove("model:${activeModelId.lowercase().trim()}", activeModelKey)
        }
        editor.apply()
        _keysMap.value = loadAllKeys()

        if (prevGemini != getProviderKey("gemini")) notifyProviderKeyChanged("gemini")
        if (prevAnthropic != getProviderKey("anthropic")) notifyProviderKeyChanged("anthropic")
        if (prevOpenai != getProviderKey("openai")) notifyProviderKeyChanged("openai")
    }

    fun clearAllApiKeys() {
        val hadGemini = getProviderKey("gemini").isNotBlank()
        val hadAnthropic = getProviderKey("anthropic").isNotBlank()
        val hadOpenai = getProviderKey("openai").isNotBlank()
        encryptedPrefs.edit().clear().apply()
        legacyPrefs.edit().clear().apply()
        _keysMap.value = emptyMap()
        if (hadGemini) notifyProviderKeyChanged("gemini")
        if (hadAnthropic) notifyProviderKeyChanged("anthropic")
        if (hadOpenai) notifyProviderKeyChanged("openai")
    }

    fun allSavedSecretsForRedaction(): List<String> {
        return _keysMap.value.values.filter { it.length >= 8 }.distinct()
    }
}
