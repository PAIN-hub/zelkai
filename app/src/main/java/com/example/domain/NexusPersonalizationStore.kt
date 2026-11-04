package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

enum class NexusFontSize(val id: String, val label: String, val scaleFactor: Float) {
    SMALL("small", "Small", 0.90f),
    DEFAULT("default", "Default", 1.0f),
    LARGE("large", "Large", 1.12f);

    companion object {
        fun fromId(raw: String?): NexusFontSize {
            return entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class ThoughtProcessVisibility(val id: String, val label: String) {
    COLLAPSED("collapsed", "Collapsed (default)"),
    HIDDEN("hidden", "Hidden");

    companion object {
        fun fromId(raw: String?): ThoughtProcessVisibility {
            return entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: COLLAPSED
        }
    }
}

enum class ToolGroupExpandMode(val id: String, val label: String) {
    COLLAPSED("collapsed", "Collapsed (default)"),
    EXPANDED("expanded", "Expanded"),
    EXPANDED_WHILE_RUNNING("expanded_while_running", "Expanded while running");

    companion object {
        fun fromId(raw: String?): ToolGroupExpandMode {
            return entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: COLLAPSED
        }
    }
}

enum class NexusResponseStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    val promptInstruction: String
) {
    CONCISE(
        id = "concise",
        label = "Concise",
        subtitle = "Short, direct answers with minimal prose",
        promptInstruction = "Response Style: CONCISE. Keep explanations brief and direct (1-3 sentences). Prefer exact commands and code blocks over long prose."
    ),
    BALANCED(
        id = "balanced",
        label = "Balanced",
        subtitle = "Clear explanations with practical examples",
        promptInstruction = "Response Style: BALANCED. Provide clear, well-structured explanations paired with practical examples or exact shell commands."
    ),
    DETAILED(
        id = "detailed",
        label = "Detailed",
        subtitle = "Thorough walkthroughs, edge cases, and context",
        promptInstruction = "Response Style: DETAILED. Provide comprehensive step-by-step explanations, cover edge cases, and explain why each command or code block works."
    ),
    TECHNICAL(
        id = "technical",
        label = "Technical",
        subtitle = "Deep systems, kernel, POSIX & architecture focus",
        promptInstruction = "Response Style: TECHNICAL. Use precise systems engineering terminology, POSIX/Linux internals, exit codes, and performance trade-offs."
    );

    companion object {
        fun fromId(raw: String?): NexusResponseStyle {
            return entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: BALANCED
        }
    }
}

data class NexusPersonaPreset(
    val id: String,
    val name: String,
    val summary: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = true
)

data class NexusSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean = false
) {
    val estimatedTokens: Int
        get() = estimateTokens(name) + estimateTokens(description) + estimateTokens(instructions)

    companion object {
        fun estimateTokens(text: String): Int {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0
            return ceil(trimmed.length / 4.0).toInt().coerceAtLeast(1)
        }
    }
}

data class NexusPersonalizationConfig(
    val displayName: String = "",
    val avatarDataUri: String = "",
    val fontSize: NexusFontSize = NexusFontSize.DEFAULT,
    val responseStyle: NexusResponseStyle = NexusResponseStyle.BALANCED,
    val personaPresetId: String = "default",
    val customPersonas: List<NexusPersonaPreset> = emptyList(),
    val aboutUser: String = "",
    val responsePreference: String = "",
    val enableForNewChats: Boolean = true,
    val skills: List<NexusSkill> = NexusPersonalizationStore.BUILT_IN_SKILLS,
    val skillsTokenBudget: Int = NexusPersonalizationStore.DEFAULT_SKILLS_TOKEN_BUDGET,
    val recentWorkspaces: List<String> = emptyList(),
    val executionBackendId: String = ExecutionBackendType.TERMUX.id,
    val workspaceModeId: String = WorkspaceMode.SHARED_STORAGE.id,
    val remoteBridgeUrl: String = "",
    val sharedModeCwd: String = WorkspacePaths.DEFAULT_SHARED_ROOT,
    val termuxHomeModeCwd: String = WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT,
    val mirrorGitFolder: Boolean = false,
    val mirrorBuildOutputs: Boolean = false,
    val mirrorDeletions: Boolean = false,
    val mirrorCustomExcludes: List<String> = emptyList(),
    val keepInSharedStorageProjects: Set<String> = emptySet(),
    val thoughtProcessVisibility: ThoughtProcessVisibility = ThoughtProcessVisibility.COLLAPSED,
    val toolGroupExpandMode: ToolGroupExpandMode = ToolGroupExpandMode.COLLAPSED
) {
    val executionBackendType: ExecutionBackendType
        get() = ExecutionBackendType.fromId(executionBackendId)

    val executionBackend: ExecutionBackendType
        get() = executionBackendType

    val workspaceMode: WorkspaceMode
        get() = WorkspaceMode.fromId(workspaceModeId)

    val mirrorExcludeConfig: MirrorExcludeConfig
        get() = MirrorExcludeConfig(
            includeGitFolder = mirrorGitFolder,
            includeBuildOutputs = mirrorBuildOutputs,
            mirrorDeletions = mirrorDeletions,
            customExcludes = mirrorCustomExcludes
        )

    fun activeCwdForMode(mode: WorkspaceMode = workspaceMode): String {
        return when (mode) {
            WorkspaceMode.SHARED_STORAGE -> sharedModeCwd.ifBlank { WorkspacePaths.DEFAULT_SHARED_ROOT }
            WorkspaceMode.TERMUX_HOME -> termuxHomeModeCwd.ifBlank { WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT }
        }
    }

    val enabledSkills: List<NexusSkill>
        get() = skills.filter { it.enabled }

    val enabledSkillsTokens: Int
        get() = enabledSkills.sumOf { it.estimatedTokens }

    val isOverSkillsTokenBudget: Boolean
        get() = enabledSkillsTokens > skillsTokenBudget

    val allPersonas: List<NexusPersonaPreset>
        get() = NexusPersonalizationStore.BUILT_IN_PERSONAS + customPersonas

    val activePersona: NexusPersonaPreset
        get() = allPersonas.find { it.id == personaPresetId }
            ?: NexusPersonalizationStore.BUILT_IN_PERSONAS.first()
}

/**
 * Manages Profile (display name, 512x512 WebP avatar), Appearance (font size),
 * Response Style, Personalization ("About you"), Personas, Reusable Skills with token budget,
 * and Recent Workspaces.
 */
class NexusPersonalizationStore(context: Context?) {

    private val appContext = context?.applicationContext
    private val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val avatarFile: File? = appContext?.let {
        File(File(it.filesDir, "profile").apply { mkdirs() }, "avatar.webp")
    }

    private val _config = MutableStateFlow(loadFromPrefs())
    val config: StateFlow<NexusPersonalizationConfig> = _config.asStateFlow()

    private val _avatarBitmap = MutableStateFlow<Bitmap?>(loadAvatarBitmapFromDiskOrUri(_config.value.avatarDataUri))
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    private fun loadFromPrefs(): NexusPersonalizationConfig {
        val p = prefs ?: return NexusPersonalizationConfig()
        val displayName = p.getString(KEY_DISPLAY_NAME, "")?.trim().orEmpty().take(80)
        val avatarDataUri = p.getString(KEY_AVATAR_DATA_URI, "")?.trim().orEmpty()
        val fontSize = NexusFontSize.fromId(p.getString(KEY_FONT_SIZE, NexusFontSize.DEFAULT.id))
        val responseStyle = NexusResponseStyle.fromId(p.getString(KEY_RESPONSE_STYLE, NexusResponseStyle.BALANCED.id))
        val personaPresetId = p.getString(KEY_PERSONA_PRESET_ID, "default")?.trim().orEmpty().ifBlank { "default" }
        val customPersonasJson = p.getString(KEY_CUSTOM_PERSONAS_JSON, "")?.trim().orEmpty()
        val aboutUser = p.getString(KEY_ABOUT_USER, "")?.take(MAX_ABOUT_USER_CHARS).orEmpty()
        val responsePref = p.getString(KEY_RESPONSE_PREF, "")?.take(MAX_RESPONSE_PREF_CHARS).orEmpty()
        val enableForNewChats = p.getBoolean(KEY_ENABLE_FOR_NEW_CHATS, true)
        val skillsJson = p.getString(KEY_SKILLS_JSON, "")?.trim().orEmpty()
        val tokenBudget = p.getInt(KEY_SKILLS_TOKEN_BUDGET, DEFAULT_SKILLS_TOKEN_BUDGET)
            .coerceIn(MIN_SKILLS_TOKEN_BUDGET, MAX_SKILLS_TOKEN_BUDGET)
        val recentWorkspacesJson = p.getString(KEY_RECENT_WORKSPACES_JSON, "")?.trim().orEmpty()
        val executionBackendId = p.getString(KEY_EXECUTION_BACKEND_ID, ExecutionBackendType.TERMUX.id)?.trim().orEmpty()
            .ifBlank { ExecutionBackendType.TERMUX.id }
        val workspaceModeId = p.getString(KEY_WORKSPACE_MODE_ID, WorkspaceMode.SHARED_STORAGE.id)?.trim().orEmpty()
            .ifBlank { WorkspaceMode.SHARED_STORAGE.id }
        val remoteBridgeUrl = p.getString(KEY_REMOTE_BRIDGE_URL, "")?.trim().orEmpty()
        val sharedModeCwd = p.getString(KEY_SHARED_MODE_CWD, WorkspacePaths.DEFAULT_SHARED_ROOT)?.trim().orEmpty()
            .ifBlank { WorkspacePaths.DEFAULT_SHARED_ROOT }
        val termuxHomeModeCwd = p.getString(KEY_TERMUX_HOME_MODE_CWD, WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT)?.trim().orEmpty()
            .ifBlank { WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT }
        val mirrorGitFolder = p.getBoolean(KEY_MIRROR_GIT_FOLDER, false)
        val mirrorBuildOutputs = p.getBoolean(KEY_MIRROR_BUILD_OUTPUTS, false)
        val mirrorDeletions = p.getBoolean(KEY_MIRROR_DELETIONS, false)
        val mirrorCustomExcludes = parseStringListJson(p.getString(KEY_MIRROR_CUSTOM_EXCLUDES_JSON, "").orEmpty())
        val keepSharedProjects = parseStringListJson(p.getString(KEY_KEEP_IN_SHARED_PROJECTS_JSON, "").orEmpty()).toSet()
        val thoughtVis = ThoughtProcessVisibility.fromId(p.getString(KEY_THOUGHT_PROCESS_VISIBILITY, ThoughtProcessVisibility.COLLAPSED.id))
        val toolExpand = ToolGroupExpandMode.fromId(p.getString(KEY_TOOL_GROUP_EXPAND_MODE, ToolGroupExpandMode.COLLAPSED.id))

        return NexusPersonalizationConfig(
            displayName = displayName,
            avatarDataUri = avatarDataUri,
            fontSize = fontSize,
            responseStyle = responseStyle,
            personaPresetId = personaPresetId,
            customPersonas = parseCustomPersonasJson(customPersonasJson),
            aboutUser = aboutUser,
            responsePreference = responsePref,
            enableForNewChats = enableForNewChats,
            skills = mergeBuiltInAndStoredSkills(skillsJson),
            skillsTokenBudget = tokenBudget,
            recentWorkspaces = parseStringListJson(recentWorkspacesJson).take(5),
            executionBackendId = executionBackendId,
            workspaceModeId = workspaceModeId,
            remoteBridgeUrl = remoteBridgeUrl,
            sharedModeCwd = sharedModeCwd,
            termuxHomeModeCwd = termuxHomeModeCwd,
            mirrorGitFolder = mirrorGitFolder,
            mirrorBuildOutputs = mirrorBuildOutputs,
            mirrorDeletions = mirrorDeletions,
            mirrorCustomExcludes = mirrorCustomExcludes,
            keepInSharedStorageProjects = keepSharedProjects,
            thoughtProcessVisibility = thoughtVis,
            toolGroupExpandMode = toolExpand
        )
    }

    private fun persistConfig(next: NexusPersonalizationConfig) {
        _config.value = next
        val p = prefs ?: return
        p.edit()
            .putString(KEY_DISPLAY_NAME, next.displayName.take(80))
            .putString(KEY_AVATAR_DATA_URI, next.avatarDataUri)
            .putString(KEY_FONT_SIZE, next.fontSize.id)
            .putString(KEY_RESPONSE_STYLE, next.responseStyle.id)
            .putString(KEY_PERSONA_PRESET_ID, next.personaPresetId)
            .putString(KEY_CUSTOM_PERSONAS_JSON, serializeCustomPersonasJson(next.customPersonas))
            .putString(KEY_ABOUT_USER, next.aboutUser.take(MAX_ABOUT_USER_CHARS))
            .putString(KEY_RESPONSE_PREF, next.responsePreference.take(MAX_RESPONSE_PREF_CHARS))
            .putBoolean(KEY_ENABLE_FOR_NEW_CHATS, next.enableForNewChats)
            .putString(KEY_SKILLS_JSON, serializeSkillsJson(next.skills))
            .putInt(KEY_SKILLS_TOKEN_BUDGET, next.skillsTokenBudget.coerceIn(MIN_SKILLS_TOKEN_BUDGET, MAX_SKILLS_TOKEN_BUDGET))
            .putString(KEY_RECENT_WORKSPACES_JSON, serializeStringListJson(next.recentWorkspaces.take(5)))
            .putString(KEY_EXECUTION_BACKEND_ID, next.executionBackendId)
            .putString(KEY_WORKSPACE_MODE_ID, next.workspaceModeId)
            .putString(KEY_REMOTE_BRIDGE_URL, next.remoteBridgeUrl)
            .putString(KEY_SHARED_MODE_CWD, next.sharedModeCwd)
            .putString(KEY_TERMUX_HOME_MODE_CWD, next.termuxHomeModeCwd)
            .putBoolean(KEY_MIRROR_GIT_FOLDER, next.mirrorGitFolder)
            .putBoolean(KEY_MIRROR_BUILD_OUTPUTS, next.mirrorBuildOutputs)
            .putBoolean(KEY_MIRROR_DELETIONS, next.mirrorDeletions)
            .putString(KEY_MIRROR_CUSTOM_EXCLUDES_JSON, serializeStringListJson(next.mirrorCustomExcludes))
            .putString(KEY_KEEP_IN_SHARED_PROJECTS_JSON, serializeStringListJson(next.keepInSharedStorageProjects.toList()))
            .putString(KEY_THOUGHT_PROCESS_VISIBILITY, next.thoughtProcessVisibility.id)
            .putString(KEY_TOOL_GROUP_EXPAND_MODE, next.toolGroupExpandMode.id)
            .apply()
    }

    fun applyRemoteSyncedSettings(
        displayName: String?,
        avatarDataUri: String?,
        fontSize: String?,
        responseStyle: String?,
        personaPresetId: String?,
        customPersonasJson: String?,
        aboutUser: String?,
        responsePreference: String?,
        enableForNewChats: Boolean?,
        skillsJson: String?,
        skillsTokenBudget: Long?,
        recentWorkspaces: List<String>?
    ) {
        val current = _config.value
        val updatedAvatarUri = avatarDataUri ?: current.avatarDataUri
        val updated = current.copy(
            displayName = displayName?.take(80) ?: current.displayName,
            avatarDataUri = updatedAvatarUri,
            fontSize = if (!fontSize.isNullOrBlank()) NexusFontSize.fromId(fontSize) else current.fontSize,
            responseStyle = if (!responseStyle.isNullOrBlank()) NexusResponseStyle.fromId(responseStyle) else current.responseStyle,
            personaPresetId = if (!personaPresetId.isNullOrBlank()) personaPresetId else current.personaPresetId,
            customPersonas = if (!customPersonasJson.isNullOrBlank()) parseCustomPersonasJson(customPersonasJson) else current.customPersonas,
            aboutUser = aboutUser?.take(MAX_ABOUT_USER_CHARS) ?: current.aboutUser,
            responsePreference = responsePreference?.take(MAX_RESPONSE_PREF_CHARS) ?: current.responsePreference,
            enableForNewChats = enableForNewChats ?: current.enableForNewChats,
            skills = if (!skillsJson.isNullOrBlank()) mergeBuiltInAndStoredSkills(skillsJson) else current.skills,
            skillsTokenBudget = skillsTokenBudget?.toInt()?.coerceIn(MIN_SKILLS_TOKEN_BUDGET, MAX_SKILLS_TOKEN_BUDGET)
                ?: current.skillsTokenBudget,
            recentWorkspaces = if (!recentWorkspaces.isNullOrEmpty()) recentWorkspaces.take(5) else current.recentWorkspaces
        )
        persistConfig(updated)
        if (updatedAvatarUri.isNotBlank() && _avatarBitmap.value == null) {
            _avatarBitmap.value = decodeDataUriToBitmap(updatedAvatarUri)
        }
    }

    fun updateDisplayName(name: String): NexusPersonalizationConfig {
        val next = _config.value.copy(displayName = name.trim().take(80))
        persistConfig(next)
        return next
    }

    fun updateFontSize(fontSize: NexusFontSize): NexusPersonalizationConfig {
        val next = _config.value.copy(fontSize = fontSize)
        persistConfig(next)
        return next
    }

    fun updateResponseStyle(style: NexusResponseStyle): NexusPersonalizationConfig {
        val next = _config.value.copy(responseStyle = style)
        persistConfig(next)
        return next
    }

    fun selectPersonaPreset(personaId: String): NexusPersonalizationConfig {
        val next = _config.value.copy(personaPresetId = personaId)
        persistConfig(next)
        return next
    }

    fun saveCustomPersona(name: String, prompt: String, existingId: String? = null): NexusPersonalizationConfig {
        val cleanName = name.trim().take(48).ifBlank { "Custom Persona" }
        val cleanPrompt = prompt.trim().take(1800).ifBlank { "You are a helpful specialist assistant." }
        val targetId = existingId ?: "custom_persona_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val newPersona = NexusPersonaPreset(
            id = targetId,
            name = cleanName,
            summary = cleanPrompt.lines().firstOrNull()?.take(70) ?: cleanPrompt.take(70),
            systemPrompt = cleanPrompt,
            isBuiltIn = false
        )
        val currentList = _config.value.customPersonas.filterNot { it.id == targetId }
        val next = _config.value.copy(
            customPersonas = currentList + newPersona,
            personaPresetId = targetId
        )
        persistConfig(next)
        return next
    }

    fun deleteCustomPersona(personaId: String): NexusPersonalizationConfig {
        val filtered = _config.value.customPersonas.filterNot { it.id == personaId }
        val nextActive = if (_config.value.personaPresetId == personaId) "default" else _config.value.personaPresetId
        val next = _config.value.copy(
            customPersonas = filtered,
            personaPresetId = nextActive
        )
        persistConfig(next)
        return next
    }

    fun updatePersonalization(
        aboutUser: String = _config.value.aboutUser,
        responsePreference: String = _config.value.responsePreference,
        enableForNewChats: Boolean = _config.value.enableForNewChats
    ): NexusPersonalizationConfig {
        val next = _config.value.copy(
            aboutUser = aboutUser.take(MAX_ABOUT_USER_CHARS),
            responsePreference = responsePreference.take(MAX_RESPONSE_PREF_CHARS),
            enableForNewChats = enableForNewChats
        )
        persistConfig(next)
        return next
    }

    fun toggleSkill(skillId: String, enabled: Boolean): NexusPersonalizationConfig {
        val updatedSkills = _config.value.skills.map { skill ->
            if (skill.id == skillId) skill.copy(enabled = enabled) else skill
        }
        val next = _config.value.copy(skills = updatedSkills)
        persistConfig(next)
        return next
    }

    fun saveCustomSkill(
        id: String? = null,
        name: String,
        description: String,
        instructions: String,
        enabled: Boolean = true
    ): NexusPersonalizationConfig {
        val cleanName = name.trim().take(MAX_SKILL_NAME_CHARS).ifBlank { "Untitled Skill" }
        val cleanDesc = description.trim().take(MAX_SKILL_DESC_CHARS)
        val cleanInstr = instructions.trim().take(MAX_SKILL_INSTRUCTIONS_CHARS)
        val targetId = id?.takeIf { it.isNotBlank() }
            ?: "skill_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val existingList = _config.value.skills.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == targetId && !it.isBuiltIn }
        val newSkill = NexusSkill(
            id = targetId,
            name = cleanName,
            description = cleanDesc,
            instructions = cleanInstr,
            enabled = enabled,
            isBuiltIn = false
        )
        if (existingIdx >= 0) {
            existingList[existingIdx] = newSkill
        } else {
            existingList.add(newSkill)
        }
        val next = _config.value.copy(skills = existingList)
        persistConfig(next)
        return next
    }

    fun deleteCustomSkill(skillId: String): NexusPersonalizationConfig {
        val updatedSkills = _config.value.skills.filterNot { it.id == skillId && !it.isBuiltIn }
        val next = _config.value.copy(skills = updatedSkills)
        persistConfig(next)
        return next
    }

    fun updateSkillsTokenBudget(budget: Int): NexusPersonalizationConfig {
        val clamped = budget.coerceIn(MIN_SKILLS_TOKEN_BUDGET, MAX_SKILLS_TOKEN_BUDGET)
        val next = _config.value.copy(skillsTokenBudget = clamped)
        persistConfig(next)
        return next
    }

    fun addRecentWorkspace(path: String): NexusPersonalizationConfig {
        val clean = path.trim()
        if (clean.isEmpty()) return _config.value
        val updated = (listOf(clean) + _config.value.recentWorkspaces.filterNot { it == clean }).take(5)
        val next = _config.value.copy(recentWorkspaces = updated)
        persistConfig(next)
        return next
    }

    fun removeRecentWorkspace(path: String): NexusPersonalizationConfig {
        val updated = _config.value.recentWorkspaces.filterNot { it == path }
        val next = _config.value.copy(recentWorkspaces = updated)
        persistConfig(next)
        return next
    }

    fun updateExecutionBackend(backendType: ExecutionBackendType): NexusPersonalizationConfig {
        val next = _config.value.copy(executionBackendId = backendType.id)
        persistConfig(next)
        return next
    }

    fun updateWorkspaceMode(mode: WorkspaceMode): NexusPersonalizationConfig {
        val next = _config.value.copy(workspaceModeId = mode.id)
        persistConfig(next)
        return next
    }

    fun updateModeCwd(mode: WorkspaceMode, cwd: String): NexusPersonalizationConfig {
        val clean = cwd.trim().ifBlank {
            if (mode == WorkspaceMode.TERMUX_HOME) WorkspacePaths.TERMUX_HOME_WORKSPACE_ROOT else WorkspacePaths.DEFAULT_SHARED_ROOT
        }
        val next = when (mode) {
            WorkspaceMode.SHARED_STORAGE -> _config.value.copy(sharedModeCwd = clean)
            WorkspaceMode.TERMUX_HOME -> _config.value.copy(termuxHomeModeCwd = clean)
        }
        persistConfig(next)
        return next
    }

    fun updateMirrorConfig(
        mirrorGitFolder: Boolean = _config.value.mirrorGitFolder,
        mirrorBuildOutputs: Boolean = _config.value.mirrorBuildOutputs,
        includeGitFolder: Boolean = mirrorGitFolder,
        includeBuildOutputs: Boolean = mirrorBuildOutputs,
        mirrorDeletions: Boolean = _config.value.mirrorDeletions,
        customExcludes: List<String> = _config.value.mirrorCustomExcludes
    ): NexusPersonalizationConfig {
        val next = _config.value.copy(
            mirrorGitFolder = includeGitFolder,
            mirrorBuildOutputs = includeBuildOutputs,
            mirrorDeletions = mirrorDeletions,
            mirrorCustomExcludes = customExcludes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        )
        persistConfig(next)
        return next
    }

    fun markProjectKeepInSharedStorage(projectSlug: String, keep: Boolean = true): NexusPersonalizationConfig {
        val slug = projectSlug.trim()
        if (slug.isEmpty()) return _config.value
        val updatedSet = if (keep) {
            _config.value.keepInSharedStorageProjects + slug
        } else {
            _config.value.keepInSharedStorageProjects - slug
        }
        val next = _config.value.copy(keepInSharedStorageProjects = updatedSet)
        persistConfig(next)
        return next
    }

    fun updateRemoteBridgeUrl(url: String): NexusPersonalizationConfig {
        val next = _config.value.copy(remoteBridgeUrl = url.trim())
        persistConfig(next)
        return next
    }

    fun updateThoughtProcessVisibility(visibility: ThoughtProcessVisibility): NexusPersonalizationConfig {
        val next = _config.value.copy(thoughtProcessVisibility = visibility)
        persistConfig(next)
        return next
    }

    fun updateToolGroupExpandMode(mode: ToolGroupExpandMode): NexusPersonalizationConfig {
        val next = _config.value.copy(toolGroupExpandMode = mode)
        persistConfig(next)
        return next
    }

    /**
     * Crops and downscales the provided [source] bitmap to 512x512 px, encodes as compressed WebP,
     * saves to `context.filesDir/profile/avatar.webp`, and returns the updated config with `avatarDataUri`.
     */
    fun saveAvatarBitmap(
        source: Bitmap,
        cropScale: Float = 1f,
        offsetXNormalized: Float = 0f,
        offsetYNormalized: Float = 0f
    ): NexusPersonalizationConfig {
        val cropped512 = cropAndScaleToSquare512(
            source = source,
            zoom = cropScale.coerceIn(1f, 4f),
            panX = offsetXNormalized.coerceIn(-1f, 1f),
            panY = offsetYNormalized.coerceIn(-1f, 1f)
        )
        _avatarBitmap.value = cropped512

        val webpBytes = encodeBitmapToWebp(cropped512, quality = 82)
        runCatching {
            avatarFile?.writeBytes(webpBytes)
        }
        val b64 = Base64.encodeToString(webpBytes, Base64.NO_WRAP)
        val dataUri = "data:image/webp;base64,$b64"
        val next = _config.value.copy(avatarDataUri = dataUri)
        persistConfig(next)
        return next
    }

    fun removeAvatar(): NexusPersonalizationConfig {
        _avatarBitmap.value = null
        runCatching { avatarFile?.delete() }
        val next = _config.value.copy(avatarDataUri = "")
        persistConfig(next)
        return next
    }

    fun resetToDefaults(): NexusPersonalizationConfig {
        val next = NexusPersonalizationConfig(
            displayName = _config.value.displayName,
            avatarDataUri = _config.value.avatarDataUri
        )
        persistConfig(next)
        return next
    }

    fun importSkillFromMarkdownOrJson(rawContent: String): Result<NexusPersonalizationConfig> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty skill file"))
        return runCatching {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val importedSkills = mutableListOf<NexusSkill>()
                val array = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("skills") ?: JSONArray().put(obj)
                }
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim().take(MAX_SKILL_NAME_CHARS)
                    val desc = item.optString("description").trim().take(MAX_SKILL_DESC_CHARS)
                    val instr = item.optString("instructions", item.optString("content")).trim()
                        .take(MAX_SKILL_INSTRUCTIONS_CHARS)
                    if (name.isNotBlank() && instr.isNotBlank()) {
                        importedSkills.add(
                            NexusSkill(
                                id = "skill_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                                name = name,
                                description = desc.ifBlank { "Imported skill pack" },
                                instructions = instr,
                                enabled = item.optBoolean("enabled", true),
                                isBuiltIn = false
                            )
                        )
                    }
                }
                require(importedSkills.isNotEmpty()) { "No valid skills found in JSON." }
                val next = _config.value.copy(skills = _config.value.skills + importedSkills)
                persistConfig(next)
                next
            } else {
                // Parse Markdown (# Title, optional > description, rest is instructions)
                val lines = trimmed.lines()
                val headingLine = lines.firstOrNull { it.trim().startsWith("#") }
                val name = headingLine?.trim()?.trimStart('#')?.trim()?.take(MAX_SKILL_NAME_CHARS)
                    ?.ifBlank { "Imported Markdown Skill" } ?: "Imported Markdown Skill"
                val descLine = lines.firstOrNull { it.trim().startsWith(">") }
                val desc = descLine?.trim()?.trimStart('>')?.trim()?.take(MAX_SKILL_DESC_CHARS)
                    ?: "Imported from Markdown"
                val body = lines
                    .filterNot { it == headingLine || it == descLine }
                    .joinToString("\n")
                    .trim()
                    .ifBlank { trimmed }
                    .take(MAX_SKILL_INSTRUCTIONS_CHARS)
                saveCustomSkill(
                    name = name,
                    description = desc,
                    instructions = body,
                    enabled = true
                )
            }
        }
    }

    fun exportSkillsAsJson(): String {
        val arr = JSONArray()
        _config.value.skills.forEach { skill ->
            arr.put(
                JSONObject().apply {
                    put("id", skill.id)
                    put("name", skill.name)
                    put("description", skill.description)
                    put("instructions", skill.instructions)
                    put("enabled", skill.enabled)
                    put("isBuiltIn", skill.isBuiltIn)
                }
            )
        }
        return JSONObject().apply {
            put("version", 5)
            put("type", "nexus_skills_bundle")
            put("skillsTokenBudget", _config.value.skillsTokenBudget)
            put("skills", arr)
        }.toString(2)
    }

    /**
     * Exports all non-secret settings (General, Personalization, Personas, Skills, Model Config defaults).
     * Strictly excludes API keys and tokens.
     */
    fun exportFullSettingsJson(
        provider: String,
        selectedModel: String,
        maxTokens: Long,
        maxSteps: Long,
        timeoutSec: Long,
        themeModeId: String
    ): String {
        val cfg = _config.value
        return JSONObject().apply {
            put("schema", "nexus_agent_v5_settings")
            put("exportedAtMs", System.currentTimeMillis())
            put("secretsIncluded", false)
            put("displayName", cfg.displayName)
            put("themeMode", themeModeId)
            put("fontSize", cfg.fontSize.id)
            put("responseStyle", cfg.responseStyle.id)
            put("personaPresetId", cfg.personaPresetId)
            put("aboutUser", cfg.aboutUser)
            put("responsePreference", cfg.responsePreference)
            put("enableForNewChats", cfg.enableForNewChats)
            put("skillsTokenBudget", cfg.skillsTokenBudget)
            put("skills", JSONArray(serializeSkillsJson(cfg.skills)))
            put("customPersonas", JSONArray(serializeCustomPersonasJson(cfg.customPersonas)))
            put("recentWorkspaces", JSONArray(cfg.recentWorkspaces))
            put("executionBackendId", cfg.executionBackendId)
            put("workspaceModeId", cfg.workspaceModeId)
            put("remoteBridgeUrl", cfg.remoteBridgeUrl)
            put("sharedModeCwd", cfg.sharedModeCwd)
            put("termuxHomeModeCwd", cfg.termuxHomeModeCwd)
            put("mirrorGitFolder", cfg.mirrorGitFolder)
            put("mirrorBuildOutputs", cfg.mirrorBuildOutputs)
            put("mirrorDeletions", cfg.mirrorDeletions)
            put("mirrorCustomExcludes", JSONArray(cfg.mirrorCustomExcludes))
            put("modelDefaults", JSONObject().apply {
                put("provider", provider)
                put("selectedModel", selectedModel)
                put("maxTokens", maxTokens)
                put("maxSteps", maxSteps)
                put("timeoutSec", timeoutSec)
            })
        }.toString(2)
    }

    fun importFullSettingsJson(rawJson: String): Result<NexusPersonalizationConfig> {
        return runCatching {
            val obj = JSONObject(rawJson.trim())
            val cur = _config.value
            val skillsArr = obj.optJSONArray("skills")?.toString().orEmpty()
            val personasArr = obj.optJSONArray("customPersonas")?.toString().orEmpty()
            val recentArr = obj.optJSONArray("recentWorkspaces")?.toString().orEmpty()
            val mirrorExcludesArr = obj.optJSONArray("mirrorCustomExcludes")?.toString().orEmpty()
            val next = cur.copy(
                displayName = obj.optString("displayName", cur.displayName).take(80),
                fontSize = NexusFontSize.fromId(obj.optString("fontSize", cur.fontSize.id)),
                responseStyle = NexusResponseStyle.fromId(obj.optString("responseStyle", cur.responseStyle.id)),
                personaPresetId = obj.optString("personaPresetId", cur.personaPresetId).ifBlank { "default" },
                aboutUser = obj.optString("aboutUser", cur.aboutUser).take(MAX_ABOUT_USER_CHARS),
                responsePreference = obj.optString("responsePreference", cur.responsePreference).take(MAX_RESPONSE_PREF_CHARS),
                enableForNewChats = obj.optBoolean("enableForNewChats", cur.enableForNewChats),
                skillsTokenBudget = obj.optInt("skillsTokenBudget", cur.skillsTokenBudget)
                    .coerceIn(MIN_SKILLS_TOKEN_BUDGET, MAX_SKILLS_TOKEN_BUDGET),
                skills = if (skillsArr.isNotBlank()) mergeBuiltInAndStoredSkills(skillsArr) else cur.skills,
                customPersonas = if (personasArr.isNotBlank()) parseCustomPersonasJson(personasArr) else cur.customPersonas,
                recentWorkspaces = if (recentArr.isNotBlank()) parseStringListJson(recentArr).take(5) else cur.recentWorkspaces,
                executionBackendId = obj.optString("executionBackendId", cur.executionBackendId).ifBlank { cur.executionBackendId },
                workspaceModeId = obj.optString("workspaceModeId", cur.workspaceModeId).ifBlank { cur.workspaceModeId },
                remoteBridgeUrl = obj.optString("remoteBridgeUrl", cur.remoteBridgeUrl),
                sharedModeCwd = obj.optString("sharedModeCwd", cur.sharedModeCwd).ifBlank { cur.sharedModeCwd },
                termuxHomeModeCwd = obj.optString("termuxHomeModeCwd", cur.termuxHomeModeCwd).ifBlank { cur.termuxHomeModeCwd },
                mirrorGitFolder = obj.optBoolean("mirrorGitFolder", cur.mirrorGitFolder),
                mirrorBuildOutputs = obj.optBoolean("mirrorBuildOutputs", cur.mirrorBuildOutputs),
                mirrorDeletions = obj.optBoolean("mirrorDeletions", cur.mirrorDeletions),
                mirrorCustomExcludes = if (mirrorExcludesArr.isNotBlank()) parseStringListJson(mirrorExcludesArr) else cur.mirrorCustomExcludes
            )
            persistConfig(next)
            next
        }
    }

    /**
     * Composes the effective system prompt for cloud/local inference:
     * 1. Base system instruction & active Persona preset
     * 2. Real Execution Environment block (Termux vs Android toybox, bash, pkg not apt/sudo, cwd, detected tools)
     * 3. Response Style preset (`Concise`, `Balanced`, `Detailed`, `Technical`)
     * 4. Personalization ("What should Nexus know about you?" + "How should Nexus respond?") when `enableForNewChats` is enabled
     * 5. Enabled Skills within `skillsTokenBudget`
     * 6. Immutable Safety Policy appended last so custom instructions/skills can NEVER bypass the approval gate, risk classifier, or secret redactor.
     */
    fun buildEffectiveSystemPrompt(
        baseAgentPersona: String,
        envDescription: EnvironmentDescription? = null
    ): String {
        val cfg = _config.value
        val sb = StringBuilder()

        val activePersona = cfg.activePersona
        if (activePersona.id != "default") {
            sb.append("[Active Persona: ").append(activePersona.name).append("]\n")
                .append(activePersona.systemPrompt.trim()).append("\n\n")
        } else {
            sb.append(baseAgentPersona.trim()).append("\n\n")
        }

        if (envDescription != null) {
            sb.append("[Execution Environment]\n")
            when (envDescription.backendType) {
                ExecutionBackendType.TERMUX -> {
                    if (envDescription.isAvailable) {
                        sb.append("- Backend: Termux (Android Linux environment via com.termux.RUN_COMMAND)\n")
                        sb.append("- Shell: bash -lc (/data/data/com.termux/files/usr/bin/bash)\n")
                        sb.append("- Package manager: Use `pkg install -y <pkg>` (do NOT use `sudo` or `apt` directly)\n")
                        sb.append("- Active project workspace path (cwd): ").append(envDescription.cwd).append("\n")
                        sb.append("- Workspace mode: ").append(envDescription.workspaceMode.label).append("\n")
                        sb.append("- Project jail (best-effort): Stay inside the active project workspace path. Root /storage/emulated/0/Nexus is read-only; projects live in /storage/emulated/0/Nexus/<project>.\n")
                        if (envDescription.workspaceMode == WorkspaceMode.SHARED_STORAGE) {
                            sb.append("- Shared storage note: FUSE/sdcardfs does not support symlinks or chmod; run scripts with `bash <file>`, Python venvs live in \$HOME/.venvs/<project>, and git core.fileMode=false + safe.directory are applied.\n")
                        } else {
                            sb.append("- Termux home note: Running in native ext4 Termux home with one-way source mirroring back to /storage/emulated/0/Nexus/<project>.\n")
                        }
                        val foundTools = envDescription.detectedTools.filter { it.isAvailable }
                        val missingTools = envDescription.detectedTools.filter { !it.isAvailable }
                        if (foundTools.isNotEmpty()) {
                            sb.append("- Detected tools: ")
                                .append(foundTools.joinToString(", ") { "${it.name} (${it.version ?: "installed"})" })
                                .append("\n")
                        }
                        if (missingTools.isNotEmpty()) {
                            sb.append("- Missing tools: ")
                                .append(missingTools.joinToString(", ") { it.name })
                                .append(" (propose `pkg update -y && pkg install -y nodejs git python` if needed)\n")
                        }
                    } else {
                        sb.append("- Backend: Android Shell fallback (/system/bin/sh toybox) because Termux is not ready (${envDescription.summaryLabel})\n")
                        sb.append("- Shell: /system/bin/sh (Android toybox only; no git/node/python/pkg available until Termux is configured)\n")
                        sb.append("- Active project workspace path (cwd): ").append(envDescription.cwd).append("\n")
                    }
                }
                ExecutionBackendType.ANDROID_SHELL -> {
                    sb.append("- Backend: Android Shell (/system/bin/sh toybox)\n")
                    sb.append("- Shell: /system/bin/sh (POSIX/toybox utilities only; do NOT use sudo, apt, or GNU-only flags)\n")
                    sb.append("- Active project workspace path (cwd): ").append(envDescription.cwd).append("\n")
                }
                ExecutionBackendType.REMOTE_BRIDGE -> {
                    sb.append("- Backend: Remote Bridge (server.mjs)\n")
                    sb.append("- Shell: ").append(envDescription.shellName).append("\n")
                    sb.append("- Active project workspace path (cwd): ").append(envDescription.cwd).append("\n")
                }
            }
            sb.append("\n")
        }

        sb.append(cfg.responseStyle.promptInstruction).append("\n")

        if (cfg.enableForNewChats) {
            if (cfg.displayName.isNotBlank()) {
                sb.append("User Name: ").append(cfg.displayName.trim()).append("\n")
            }
            if (cfg.aboutUser.isNotBlank()) {
                sb.append("\n[About the User]\n").append(cfg.aboutUser.trim()).append("\n")
            }
            if (cfg.responsePreference.isNotBlank()) {
                sb.append("\n[User Response Preferences]\n").append(cfg.responsePreference.trim()).append("\n")
            }
        }

        val activeSkills = cfg.enabledSkills
        if (activeSkills.isNotEmpty()) {
            var remainingBudget = cfg.skillsTokenBudget
            val includedSkills = mutableListOf<NexusSkill>()
            for (skill in activeSkills) {
                val cost = skill.estimatedTokens
                if (cost <= remainingBudget) {
                    includedSkills.add(skill)
                    remainingBudget -= cost
                }
            }
            if (includedSkills.isNotEmpty()) {
                sb.append("\n[Active Skills]\n")
                includedSkills.forEach { skill ->
                    sb.append("- ").append(skill.name).append(": ")
                        .append(skill.instructions.trim()).append("\n")
                }
            }
        }

        sb.append("\n").append(IMMUTABLE_SAFETY_POLICY)
        return sb.toString().trim()
    }

    fun serializeSkillsForCloud(): String = serializeSkillsJson(_config.value.skills)

    fun serializeCustomPersonasForCloud(): String = serializeCustomPersonasJson(_config.value.customPersonas)

    private fun loadAvatarBitmapFromDiskOrUri(dataUri: String): Bitmap? {
        runCatching {
            val file = avatarFile
            if (file != null && file.exists() && file.length() > 0L) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
            }
        }
        if (dataUri.isNotBlank()) {
            return decodeDataUriToBitmap(dataUri)
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "nexus_personalization_and_skills_v5"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_DATA_URI = "avatar_data_uri"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_RESPONSE_STYLE = "response_style"
        private const val KEY_PERSONA_PRESET_ID = "persona_preset_id"
        private const val KEY_CUSTOM_PERSONAS_JSON = "custom_personas_json"
        private const val KEY_ABOUT_USER = "about_user"
        private const val KEY_RESPONSE_PREF = "response_preference"
        private const val KEY_ENABLE_FOR_NEW_CHATS = "enable_for_new_chats"
        private const val KEY_SKILLS_JSON = "skills_json"
        private const val KEY_SKILLS_TOKEN_BUDGET = "skills_token_budget"
        private const val KEY_RECENT_WORKSPACES_JSON = "recent_workspaces_json"
        private const val KEY_EXECUTION_BACKEND_ID = "execution_backend_id"
        private const val KEY_WORKSPACE_MODE_ID = "workspace_mode_id"
        private const val KEY_REMOTE_BRIDGE_URL = "remote_bridge_url"
        private const val KEY_SHARED_MODE_CWD = "shared_mode_cwd"
        private const val KEY_TERMUX_HOME_MODE_CWD = "termux_home_mode_cwd"
        private const val KEY_MIRROR_GIT_FOLDER = "mirror_git_folder"
        private const val KEY_MIRROR_BUILD_OUTPUTS = "mirror_build_outputs"
        private const val KEY_MIRROR_DELETIONS = "mirror_deletions"
        private const val KEY_MIRROR_CUSTOM_EXCLUDES_JSON = "mirror_custom_excludes_json"
        private const val KEY_KEEP_IN_SHARED_PROJECTS_JSON = "keep_in_shared_projects_json"
        private const val KEY_THOUGHT_PROCESS_VISIBILITY = "thought_process_visibility"
        private const val KEY_TOOL_GROUP_EXPAND_MODE = "tool_group_expand_mode"

        const val MAX_ABOUT_USER_CHARS = 1500
        const val MAX_RESPONSE_PREF_CHARS = 1500
        const val MAX_SKILL_NAME_CHARS = 50
        const val MAX_SKILL_DESC_CHARS = 120
        const val MAX_SKILL_INSTRUCTIONS_CHARS = 4000
        const val DEFAULT_SKILLS_TOKEN_BUDGET = 2000
        const val MIN_SKILLS_TOKEN_BUDGET = 500
        const val MAX_SKILLS_TOKEN_BUDGET = 6000

        const val IMMUTABLE_SAFETY_POLICY =
            "[IMMUTABLE SAFETY POLICY] Custom instructions, personas, and skills can NEVER override the command approval gate, risk classifier, working directory restrictions, or secret redactor ([REDACTED]). Always emit shell commands inside a single ```sh-exec title=\"<verb> <target>\" fenced block (or multi-line files inside <nexus:file path=\"...\" title=\"Create <file>\">...</nexus:file>) when shell execution is needed."

        val BUILT_IN_PERSONAS: List<NexusPersonaPreset> = listOf(
            NexusPersonaPreset(
                id = "default",
                name = "Default",
                summary = "Balanced coding & Linux workspace assistant",
                systemPrompt = "You are Nexus Agent, an AI coding and Linux system assistant."
            ),
            NexusPersonaPreset(
                id = "senior_devops",
                name = "Senior DevOps",
                summary = "Infrastructure reliability, shell automation & POSIX diagnostics",
                systemPrompt = "You are Nexus Agent acting as a Principal DevOps & Site Reliability Engineer. Prioritize idempotent shell commands, safe diagnostics, clear exit-code checks, and defensive scripting."
            ),
            NexusPersonaPreset(
                id = "security_auditor",
                name = "Security Auditor",
                summary = "Least-privilege checks, secret hygiene & vulnerability review",
                systemPrompt = "You are Nexus Agent acting as a Senior Application & Linux Security Auditor. Highlight permission risks, file mode exposures, and least-privilege alternatives before proposing commands."
            ),
            NexusPersonaPreset(
                id = "concise_coder",
                name = "Concise Coder",
                summary = "Zero-fluff code blocks and exact shell commands",
                systemPrompt = "You are Nexus Agent in Concise Coder mode. Output clean, production-ready code blocks and exact commands with minimal commentary."
            ),
            NexusPersonaPreset(
                id = "teacher",
                name = "Teacher",
                summary = "Patient explanations of flags, pipelines, and concepts",
                systemPrompt = "You are Nexus Agent in Teacher mode. Explain command flags, Linux concepts, and code patterns clearly so the user learns why each step works."
            )
        )

        val BUILT_IN_SKILLS: List<NexusSkill> = listOf(
            NexusSkill(
                id = "builtin_shell_safety",
                name = "Shell Safety Reviewer",
                description = "Explains flags and potential side-effects before running mutating commands",
                instructions = "Before proposing any command that modifies files, permissions, or processes, briefly state what each flag does and confirm the target path is inside the current workspace.",
                enabled = true,
                isBuiltIn = true
            ),
            NexusSkill(
                id = "builtin_concise_codegen",
                name = "Concise Code Generator",
                description = "Produces self-contained, idiomatic scripts and code without placeholders",
                instructions = "When generating code or shell scripts, always write complete, runnable files with proper error handling (`set -euo pipefail` where supported) and never leave TODO placeholders.",
                enabled = true,
                isBuiltIn = true
            ),
            NexusSkill(
                id = "builtin_step_debugger",
                name = "Step-by-Step Debugger",
                description = "Isolates root causes systematically from stderr and exit codes",
                instructions = "When a command returns a non-zero exit code or error, state the exact root cause from stderr, verify assumptions with a read-only check, and propose a targeted fix.",
                enabled = false,
                isBuiltIn = true
            ),
            NexusSkill(
                id = "builtin_linux_admin",
                name = "Linux Admin Helper",
                description = "Optimizes commands for Android toybox/mksh compatibility",
                instructions = "Target Android's on-device toybox and mksh environment: avoid GNU-only long flags, sudo, apt, or systemd commands, and prefer portable POSIX pipelines.",
                enabled = true,
                isBuiltIn = true
            )
        )

        fun cropAndScaleToSquare512(
            source: Bitmap,
            zoom: Float = 1f,
            panX: Float = 0f,
            panY: Float = 0f
        ): Bitmap {
            val targetSize = 512
            val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val srcW = source.width.toFloat().coerceAtLeast(1f)
            val srcH = source.height.toFloat().coerceAtLeast(1f)
            val baseScale = targetSize.toFloat() / min(srcW, srcH)
            val effectiveScale = baseScale * zoom.coerceIn(1f, 4f)

            val scaledW = srcW * effectiveScale
            val scaledH = srcH * effectiveScale
            val maxPanX = ((scaledW - targetSize) / 2f).coerceAtLeast(0f)
            val maxPanY = ((scaledH - targetSize) / 2f).coerceAtLeast(0f)

            val dx = (targetSize - scaledW) / 2f + (panX.coerceIn(-1f, 1f) * maxPanX)
            val dy = (targetSize - scaledH) / 2f + (panY.coerceIn(-1f, 1f) * maxPanY)

            val matrix = Matrix().apply {
                postScale(effectiveScale, effectiveScale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(source, matrix, paint)
            return out
        }

        fun encodeBitmapToWebp(bitmap: Bitmap, quality: Int = 82): ByteArray {
            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, quality.coerceIn(50, 95), out)
            return out.toByteArray()
        }

        fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
            return runCatching {
                val base64Part = dataUri.substringAfter("base64,", "")
                if (base64Part.isBlank()) return null
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        private fun mergeBuiltInAndStoredSkills(rawJson: String): List<NexusSkill> {
            if (rawJson.isBlank()) return BUILT_IN_SKILLS
            return runCatching {
                val arr = JSONArray(rawJson)
                val enabledById = mutableMapOf<String, Boolean>()
                val customList = mutableListOf<NexusSkill>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val isBuiltIn = obj.optBoolean("isBuiltIn", id.startsWith("builtin_"))
                    val enabled = obj.optBoolean("enabled", false)
                    if (isBuiltIn) {
                        enabledById[id] = enabled
                    } else {
                        val name = obj.optString("name").trim().take(MAX_SKILL_NAME_CHARS)
                        val desc = obj.optString("description").trim().take(MAX_SKILL_DESC_CHARS)
                        val instr = obj.optString("instructions").trim().take(MAX_SKILL_INSTRUCTIONS_CHARS)
                        if (id.isNotBlank() && name.isNotBlank() && instr.isNotBlank()) {
                            customList.add(
                                NexusSkill(
                                    id = id,
                                    name = name,
                                    description = desc,
                                    instructions = instr,
                                    enabled = enabled,
                                    isBuiltIn = false
                                )
                            )
                        }
                    }
                }
                val mergedBuiltIns = BUILT_IN_SKILLS.map { builtIn ->
                    builtIn.copy(enabled = enabledById[builtIn.id] ?: builtIn.enabled)
                }
                mergedBuiltIns + customList
            }.getOrDefault(BUILT_IN_SKILLS)
        }

        private fun serializeSkillsJson(skills: List<NexusSkill>): String {
            val arr = JSONArray()
            skills.forEach { skill ->
                arr.put(
                    JSONObject().apply {
                        put("id", skill.id)
                        put("name", skill.name)
                        put("description", skill.description)
                        put("instructions", skill.instructions)
                        put("enabled", skill.enabled)
                        put("isBuiltIn", skill.isBuiltIn)
                    }
                )
            }
            return arr.toString()
        }

        private fun parseCustomPersonasJson(rawJson: String): List<NexusPersonaPreset> {
            if (rawJson.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(rawJson)
                val list = mutableListOf<NexusPersonaPreset>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim().take(48)
                    val prompt = obj.optString("systemPrompt").trim().take(1800)
                    if (id.isNotBlank() && name.isNotBlank() && prompt.isNotBlank()) {
                        list.add(
                            NexusPersonaPreset(
                                id = id,
                                name = name,
                                summary = prompt.lines().firstOrNull()?.take(70) ?: prompt.take(70),
                                systemPrompt = prompt,
                                isBuiltIn = false
                            )
                        )
                    }
                }
                list
            }.getOrDefault(emptyList())
        }

        private fun serializeCustomPersonasJson(personas: List<NexusPersonaPreset>): String {
            val arr = JSONArray()
            personas.filter { !it.isBuiltIn }.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("systemPrompt", p.systemPrompt)
                    }
                )
            }
            return arr.toString()
        }

        private fun parseStringListJson(rawJson: String): List<String> {
            if (rawJson.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(rawJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }.getOrDefault(emptyList())
        }

        private fun serializeStringListJson(items: List<String>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
