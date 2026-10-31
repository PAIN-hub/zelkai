package com.example.domain

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Canonical workspace paths, validators, project binding helpers, root-write path guard,
 * shared-storage command adapter, symlink fallback detector, and one-way source mirroring engine.
 */
object WorkspacePaths {
    const val DEFAULT_SHARED_ROOT = "/storage/emulated/0/Nexus"
    const val SHARED_STORAGE_BASE = "/storage/emulated/0"
    const val TERMUX_HOME_DIR = TermuxBackend.TERMUX_HOME_DIR
    const val TERMUX_HOME_WORKSPACE_ROOT = "$TERMUX_HOME_DIR/nexus_workspace"
    const val TERMUX_JOBS_ROOT = "$TERMUX_HOME_DIR/.nexus/jobs"
    const val TERMUX_SCRATCH_ROOT = "$TERMUX_HOME_DIR/.nexus/scratch"
    const val TERMUX_MIRRORS_ROOT = "$TERMUX_HOME_DIR/.nexus/mirrors"
    const val TERMUX_VENVS_ROOT = "$TERMUX_HOME_DIR/.venvs"
    const val TERMUX_NPM_CACHE = "$TERMUX_HOME_DIR/.cache/npm"
    const val TERMUX_PIP_CACHE = "$TERMUX_HOME_DIR/.cache/pip"

    val RESERVED_SLUGS = setOf(
        ".",
        "..",
        ".nexus",
        ".nexus-trash",
        "node_modules",
        ".git",
        ".venvs",
        ".venv",
        ".cache",
        "con",
        "prn",
        "aux",
        "nul"
    )

    fun resolveProjectPath(slug: String, mode: WorkspaceMode, sharedRootOverride: String = DEFAULT_SHARED_ROOT): String {
        val cleanSlug = sanitizeSlug(slug)
        if (cleanSlug.isEmpty()) {
            return if (mode == WorkspaceMode.TERMUX_HOME) {
                TERMUX_HOME_WORKSPACE_ROOT
            } else {
                sharedRootOverride
            }
        }
        return if (mode == WorkspaceMode.TERMUX_HOME) {
            "$TERMUX_HOME_WORKSPACE_ROOT/$cleanSlug"
        } else {
            "${sharedRootOverride.trimEnd('/')}/$cleanSlug"
        }
    }

    fun sharedPathForSlug(slug: String, sharedRootOverride: String = DEFAULT_SHARED_ROOT): String {
        val cleanSlug = sanitizeSlug(slug)
        return if (cleanSlug.isEmpty()) sharedRootOverride else "${sharedRootOverride.trimEnd('/')}/$cleanSlug"
    }

    fun sharedProjectPath(slug: String, sharedRootOverride: String = DEFAULT_SHARED_ROOT): String =
        sharedPathForSlug(slug, sharedRootOverride)

    fun termuxHomePathForSlug(slug: String): String {
        val cleanSlug = sanitizeSlug(slug)
        return if (cleanSlug.isEmpty()) TERMUX_HOME_WORKSPACE_ROOT else "$TERMUX_HOME_WORKSPACE_ROOT/$cleanSlug"
    }

    fun termuxHomeProjectPath(slug: String): String = termuxHomePathForSlug(slug)

    fun jobDirectory(jobId: String): String {
        val clean = jobId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "$TERMUX_JOBS_ROOT/$clean"
    }

    fun tempScratchPath(tempSessionId: String): String {
        val cleanId = tempSessionId.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "temp_session" }
        return "$TERMUX_SCRATCH_ROOT/$cleanId"
    }

    fun scratchDirectory(tempSessionId: String): String = tempScratchPath(tempSessionId)

    fun venvPathForProject(slugOrName: String): String {
        val clean = sanitizeSlug(slugOrName).ifBlank { "default" }
        return "$TERMUX_VENVS_ROOT/$clean"
    }

    /**
     * Sanitizes any user or prompt-derived project name into a safe lowercase slug (`[a-z0-9-]`, max 40 chars).
     */
    fun sanitizeSlug(raw: String): String {
        val lower = raw.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return ""
        val replaced = lower
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
        if (replaced.isEmpty() || replaced in RESERVED_SLUGS) return ""
        return replaced
    }

    fun proposeSlugFromPrompt(prompt: String, command: String = ""): String =
        proposeSlugFromPromptOrCommand(prompt, command)

    /**
     * Proposes a concise, meaningful slug from a user prompt or shell command.
     */
    fun proposeSlugFromPromptOrCommand(prompt: String, command: String = ""): String {
        val cloneMatch = Regex("""git\s+clone\s+\S+/([a-zA-Z0-9_.-]+?)(?:\.git)?(?:\s|$)""", RegexOption.IGNORE_CASE)
            .find(command.ifBlank { prompt })
        if (cloneMatch != null) {
            val repoSlug = sanitizeSlug(cloneMatch.groupValues[1])
            if (repoSlug.length >= 2) return repoSlug
        }

        val stopWords = setOf(
            "a", "an", "the", "please", "can", "you", "help", "me", "create", "folder", "and",
            "write", "run", "start", "setup", "set", "up", "in", "on", "for", "with", "to",
            "of", "my", "new", "simple", "basic", "app", "application", "project", "using", "use", "let", "lets"
        )
        val sourceText = prompt.ifBlank { command }
        val words = sourceText.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('-') }
            .filter { it.isNotEmpty() && it !in stopWords && it !in RESERVED_SLUGS }
            .take(4)

        val candidate = sanitizeSlug(words.joinToString("-"))
        if (candidate.length >= 2) return candidate

        return when {
            command.contains("npm", ignoreCase = true) || command.contains("node", ignoreCase = true) -> "node-app"
            command.contains("flask", ignoreCase = true) -> "flask-app"
            command.contains("python", ignoreCase = true) || command.contains("pip", ignoreCase = true) -> "python-app"
            command.contains("git", ignoreCase = true) -> "git-project"
            else -> "nexus-project"
        }
    }

    /**
     * Checks if a candidate slug collides with an existing folder and computes the next available `<slug>-2`, `<slug>-3`, etc.
     */
    fun resolveSlugCollision(
        desiredSlug: String,
        existingSlugs: Set<String>
    ): SlugResolution {
        val base = sanitizeSlug(desiredSlug).ifBlank { "nexus-project" }
        if (base !in existingSlugs) {
            return SlugResolution(
                sanitizedSlug = base,
                hasCollision = false,
                nextAvailableSlug = base
            )
        }
        var suffix = 2
        while ("$base-$suffix" in existingSlugs && suffix < 999) {
            suffix++
        }
        return SlugResolution(
            sanitizedSlug = base,
            hasCollision = true,
            nextAvailableSlug = "$base-$suffix"
        )
    }
}

class SlugResolution(
    val sanitizedSlug: String,
    val hasCollision: Boolean,
    val nextAvailableSlug: String
) {
    val second: String
        get() = nextAvailableSlug

    operator fun component1(): Boolean = hasCollision
    operator fun component2(): String = nextAvailableSlug
}

data class ProjectFolderSummary(
    val slug: String,
    val sharedPath: String,
    val termuxHomePath: String,
    val existsInShared: Boolean = true,
    val existsInTermuxHome: Boolean = false,
    val detectedTypes: List<WorkspaceProjectType> = emptyList(),
    val lastModifiedMs: Long = 0L
) {
    val primaryBadge: String
        get() = detectedTypes.firstOrNull()?.badge ?: "Project"
}

/**
 * Proposals surfaced in the chat when an Unassigned session first attempts to write a file or run a mutating command.
 */
data class PendingProjectCreationProposal(
    val sessionId: String = "",
    val proposedSlug: String,
    val hasCollision: Boolean = false,
    val collisionExists: Boolean = hasCollision,
    val nextAvailableSlug: String = proposedSlug,
    val pendingCommand: String = "",
    val triggerCommand: String = pendingCommand,
    val pendingUserPrompt: String = "",
    val triggerPrompt: String = pendingUserPrompt,
    val isDirectShell: Boolean = false
)

/**
 * Result of validating a workspace path for Termux / AndroidShell execution.
 */
sealed interface PathValidationOutcome {
    data class Allowed(val normalizedPath: String, val mode: WorkspaceMode) : PathValidationOutcome
    data class Rejected(val path: String, val reason: String) : PathValidationOutcome
}

typealias WorkspacePathValidation = PathValidationOutcome

/**
 * Result of evaluating whether a command is allowed in the current project jail / root.
 */
sealed interface CommandJailDecision {
    data object Allowed : CommandJailDecision

    data class BlockedRootWrite(val rootPath: String, val reason: String) : CommandJailDecision {
        val message: String
            get() = reason
    }

    data class BlockedTraversalOrSymlinkEscape(val command: String, val reason: String) : CommandJailDecision {
        val message: String
            get() = reason
    }

    data class RequiresOutsideProjectApproval(val command: String, val targetDescription: String) : CommandJailDecision {
        val reason: String
            get() = "Requires approval to access path outside bound project ($targetDescription)"
        val message: String
            get() = reason
    }
}

typealias WorkspaceJailDecision = CommandJailDecision

object WorkspacePathValidator {

    /**
     * Validates a workspace directory path:
     * - Allows `/storage/emulated/0/...` (and `/sdcard/...` mapped to `/storage/emulated/0/...`).
     * - Allows Termux home `/data/data/com.termux/files/home/...` when in TERMUX_HOME mode or scratch.
     * - Rejects all other `/data/data/<pkg>` and `/data/user/<id>` paths (e.g. Nexus app-private directory)
     *   because Termux runs under a different Android UID and cannot access another app's private data dir.
     */
    fun validateForTermux(rawPath: String): PathValidationOutcome {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) {
            return PathValidationOutcome.Rejected(rawPath, "Workspace path cannot be empty.")
        }
        if (trimmed.contains("..")) {
            return PathValidationOutcome.Rejected(rawPath, "Path traversal ('..') is not allowed in workspace paths.")
        }
        val normalized = normalizeSdcardPrefix(trimmed).trimEnd('/').ifBlank { "/" }

        // Allow Termux's own home directory and subdirectories
        if (normalized == WorkspacePaths.TERMUX_HOME_DIR ||
            normalized.startsWith("${WorkspacePaths.TERMUX_HOME_DIR}/")
        ) {
            return PathValidationOutcome.Allowed(normalized, WorkspaceMode.TERMUX_HOME)
        }

        // Reject any /data/data/<pkg> or /data/user/<id> path outside Termux home
        if (normalized.startsWith("/data/data/") ||
            normalized.startsWith("/data/user/") ||
            normalized.startsWith("/data/user_de/")
        ) {
            return PathValidationOutcome.Rejected(
                path = rawPath,
                reason = "Termux cannot access app-private paths under /data/data/ or /data/user/. Use /storage/emulated/0/Nexus/<project> or Termux home."
            )
        }

        // Allow shared storage (/storage/emulated/0/...)
        if (normalized == WorkspacePaths.SHARED_STORAGE_BASE ||
            normalized.startsWith("${WorkspacePaths.SHARED_STORAGE_BASE}/")
        ) {
            return PathValidationOutcome.Allowed(normalized, WorkspaceMode.SHARED_STORAGE)
        }

        return PathValidationOutcome.Rejected(
            path = rawPath,
            reason = "Path '$rawPath' is outside supported workspaces (/storage/emulated/0/ or Termux home)."
        )
    }

    fun normalizeSdcardPrefix(path: String): String {
        val trimmed = path.trim()
        return when {
            trimmed == "/sdcard" -> WorkspacePaths.SHARED_STORAGE_BASE
            trimmed.startsWith("/sdcard/") -> WorkspacePaths.SHARED_STORAGE_BASE + trimmed.removePrefix("/sdcard")
            else -> trimmed
        }
    }

    fun isNexusRootPath(path: String, sharedRootOverride: String = WorkspacePaths.DEFAULT_SHARED_ROOT): Boolean {
        val norm = normalizeSdcardPrefix(path).trimEnd('/')
        val rootNorm = normalizeSdcardPrefix(sharedRootOverride).trimEnd('/')
        return norm.equals(rootNorm, ignoreCase = false)
    }

    fun evaluateCommandJail(
        command: String,
        activeCwd: String,
        boundProjectDir: String?
    ): CommandJailDecision {
        val slug = boundProjectDir?.trimEnd('/')?.substringAfterLast('/').orEmpty()
        return evaluateCommandInWorkspace(
            command = command,
            activeCwd = activeCwd,
            boundProjectSlug = slug,
            isTemporaryChat = false
        )
    }

    /**
     * Evaluates Amendment #1 (Block ALL writes in `/storage/emulated/0/Nexus` root by path; allow reads)
     * and Amendment #2 (Best-effort jail: reject `..` traversal / symlink escapes; require approval for `cd` or writes outside bound project).
     */
    fun evaluateCommandInWorkspace(
        command: String,
        activeCwd: String = "",
        currentCwd: String = activeCwd,
        boundProjectSlug: String = "",
        isTemporaryChat: Boolean = false,
        sharedRootOverride: String = WorkspacePaths.DEFAULT_SHARED_ROOT
    ): CommandJailDecision {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return CommandJailDecision.Allowed

        // 1. Reject explicit path traversal escaping upwards (`../`)
        if (containsUpwardTraversalEscape(trimmed)) {
            return CommandJailDecision.BlockedTraversalOrSymlinkEscape(
                command = trimmed,
                reason = "Best-effort jail rejected path traversal ('..') or symlink escape outside the project directory."
            )
        }

        val effectiveCwd = currentCwd.ifBlank { activeCwd }
        val normCwd = normalizeSdcardPrefix(effectiveCwd).trimEnd('/')
        val normRoot = normalizeSdcardPrefix(sharedRootOverride).trimEnd('/')
        val isAtNexusRoot = normCwd == normRoot || (!isTemporaryChat && boundProjectSlug.isBlank() && normCwd == normRoot)

        // 2. Amendment #1: Block ALL writes targeting `/storage/emulated/0/Nexus` root by path (not by command name).
        // Allow read-only inspection (`ls`, `find`, `cat`, `stat`, `pwd`, etc.) at the root.
        if (targetsNexusRootForWrite(trimmed, normCwd, normRoot)) {
            return CommandJailDecision.BlockedRootWrite(
                rootPath = normRoot,
                reason = "Direct writes to the workspace root ($normRoot) are blocked. Projects must live in a subfolder ($normRoot/<project>) created via the project slug flow."
            )
        }

        if (isAtNexusRoot && isMutatingOrWriteCommand(trimmed)) {
            return CommandJailDecision.BlockedRootWrite(
                rootPath = normRoot,
                reason = "Cannot write files or run mutating commands directly inside $normRoot. Confirm or choose a project subfolder ($normRoot/<project>) first."
            )
        }

        // 3. Best-effort jail check when bound to a project
        if (boundProjectSlug.isNotBlank() && !isTemporaryChat) {
            val sharedProj = WorkspacePaths.sharedPathForSlug(boundProjectSlug, sharedRootOverride).trimEnd('/')
            val termuxProj = WorkspacePaths.termuxHomePathForSlug(boundProjectSlug).trimEnd('/')
            val outsideTarget = findOutsideProjectTarget(trimmed, normCwd, sharedProj, termuxProj)
            if (outsideTarget != null) {
                return CommandJailDecision.RequiresOutsideProjectApproval(
                    command = trimmed,
                    targetDescription = outsideTarget
                )
            }
        }

        return CommandJailDecision.Allowed
    }

    fun isReadOnlyCommand(command: String): Boolean {
        return !isMutatingOrWriteCommand(command)
    }

    /**
     * Detects any command or redirection that writes, creates, modifies, installs, or deletes files.
     */
    fun isMutatingOrWriteCommand(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false

        val withoutDevNull = trimmed.replace(Regex("""[12]?>+\s*/dev/null"""), "")
        if (Regex("""(?:^|[^0-9<>])>+\s*[^\s&|;]+""").containsMatchIn(withoutDevNull)) {
            return true
        }

        val mutatingRegex = Regex(
            """(?:^|[;&|]\s*|\b)(?:mkdir|touch|rm|rmdir|mv|cp|ln|chmod|chown|chgrp|truncate|dd|tee|patch|sed\s+-i|perl\s+-i|awk\s+-i|tar\s+-[a-zA-Z]*x|unzip|gunzip|git\s+(?:init|clone|add|commit|push|pull|merge|rebase|reset|clean|checkout|stash|mv|rm)|npm\s+(?:init|install|i|ci|add|remove|uninstall|update|link|rebuild|run\s+build)|pnpm\s+(?:init|install|i|add|remove|build)|yarn\s+(?:init|install|add|remove|build)|npx\b|pip3?\s+(?:install|uninstall)|python3?\s+-m\s+(?:venv|pip\s+install)|pkg\s+(?:install|i|remove|uninstall|upgrade|update)|apt(?:-get)?\s+(?:install|remove|upgrade|update)|cargo\s+(?:init|new|build|install)|go\s+(?:mod\s+init|get|build|install)|curl\s+[^\n]*-(?:o|O)\b|wget\b|start_job\b)""",
            RegexOption.IGNORE_CASE
        )
        if (mutatingRegex.containsMatchIn(withoutDevNull)) {
            return true
        }

        if (Regex("""^\s*(?:node|python3?|bash|sh)\b(?!\s+(?:--version|-v|-V)\s*$)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(withoutDevNull)
        ) {
            return true
        }

        return false
    }

    private fun targetsNexusRootForWrite(command: String, normCwd: String, normRoot: String): Boolean {
        if (!isMutatingOrWriteCommand(command)) return false
        val normalizedCmd = normalizeSdcardPrefix(command)

        val rootPathPattern = Regex.escape(normRoot)
        val refsRootDirectly = Regex("""$rootPathPattern(?:\s*$|[\s;|&'"]|/[^/\s;|&'"]+(?:\s*$|[\s;|&'"]))""")
            .containsMatchIn(normalizedCmd)
        if (refsRootDirectly) {
            val subPathMatches = Regex("""$rootPathPattern/([^\s;|&'"]+)""").findAll(normalizedCmd).toList()
            if (subPathMatches.isEmpty()) return true
            if (subPathMatches.any { !it.groupValues[1].contains("/") }) {
                return true
            }
        }
        return normCwd == normRoot
    }

    private fun containsUpwardTraversalEscape(command: String): Boolean {
        val tokens = command.split(Regex("""[\s;|&'"]+""")).filter { it.isNotBlank() }
        for (tok in tokens) {
            if (tok == ".." || tok.startsWith("../") || tok.endsWith("/..") || tok.contains("/../")) {
                return true
            }
        }
        if (Regex("""\bln\s+-[a-zA-Z]*s\b[^\n]*(?:\s/(?:data|system|proc|etc|sdcard|storage)|\s~)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(command)
        ) {
            return true
        }
        return false
    }

    private fun findOutsideProjectTarget(
        command: String,
        normCwd: String,
        sharedProjectPath: String,
        termuxProjectPath: String
    ): String? {
        val normalizedCmd = normalizeSdcardPrefix(command)
        val cdMatch = Regex("""(?:^|[;&|]\s*)cd\s+([^\s;&|'"]+)""").find(normalizedCmd)
        if (cdMatch != null) {
            val target = cdMatch.groupValues[1].trim()
            if (target == "~" || target.startsWith("~/") || target == "/") {
                return target
            }
            if (target.startsWith("/")) {
                val cleanTarget = target.trimEnd('/')
                val inShared = cleanTarget == sharedProjectPath || cleanTarget.startsWith("$sharedProjectPath/")
                val inTermux = cleanTarget == termuxProjectPath || cleanTarget.startsWith("$termuxProjectPath/")
                if (!inShared && !inTermux) {
                    return cleanTarget
                }
            }
        }

        if (isMutatingOrWriteCommand(normalizedCmd)) {
            val absMatches = Regex("""(?:^|[\s><=|'"])(/(?:storage/emulated/0|data/data/com\.termux/files/home|system|etc|sdcard)[^\s;|&'"]*)""")
                .findAll(normalizedCmd)
                .map { normalizeSdcardPrefix(it.groupValues[1]).trimEnd('/') }
                .toList()
            for (abs in absMatches) {
                if (abs.startsWith(WorkspacePaths.TERMUX_VENVS_ROOT) ||
                    abs.startsWith(WorkspacePaths.TERMUX_NPM_CACHE) ||
                    abs.startsWith(WorkspacePaths.TERMUX_PIP_CACHE) ||
                    abs.startsWith(WorkspacePaths.TERMUX_JOBS_ROOT) ||
                    abs.startsWith(TermuxBackend.TERMUX_TMP_DIR)
                ) {
                    continue
                }
                val inShared = abs == sharedProjectPath || abs.startsWith("$sharedProjectPath/")
                val inTermux = abs == termuxProjectPath || abs.startsWith("$termuxProjectPath/")
                if (!inShared && !inTermux) {
                    return abs
                }
            }
        }
        return null
    }
}

/**
 * Shared-storage command & environment adapter (Task 3 + Amendment #5):
 * - Rewrites `./script.sh` to `bash script.sh` on shared storage (`noexec` mount).
 * - Routes npm cache to `$HOME/.cache/npm`, pip cache to `$HOME/.cache/pip`, and Python venvs to `$HOME/.venvs/<project>`.
 * - Tracks per-workspace idempotent `git config --global core.fileMode false` and `safe.directory`.
 * - Detects symlink-specific errors for the clean `bin-links` single retry and the smart `"Move to Termux home"` card.
 */
object WorkspaceCommandAdapter {
    private val configuredGitWorkspaces = ConcurrentHashMap.newKeySet<String>()

    private val DOT_SLASH_SCRIPT_REGEX = Regex("""(^|[;&|]\s*)\./([a-zA-Z0-9_./-]+\.(?:sh|bash))(?!\S)""")
    private val DOT_SLASH_GRADLEW_REGEX = Regex("""(^|[;&|]\s*)\./gradlew\b""")
    private val PYTHON_VENV_CREATE_REGEX = Regex("""\b(python3?\s+-m\s+venv)\s+(\.?venv|env)\b""")
    private val PYTHON_VENV_ACTIVATE_REGEX = Regex("""(\b(?:source|\.)\s+)(?:\./)?(?:\.?venv|env)/bin/activate\b""")
    private val PYTHON_VENV_BIN_REGEX = Regex("""(?:\./)?(?:\.?venv|env)/bin/(python3?|pip3?|flask|uvicorn|gunicorn|pytest)\b""")

    fun adaptCommandForSharedStorage(
        command: String,
        projectSlug: String
    ): String = adaptCommandForSharedStorage(
        command = command,
        workspaceCwd = WorkspacePaths.sharedPathForSlug(projectSlug),
        projectSlug = projectSlug
    )

    fun adaptCommandForSharedStorage(
        command: String,
        workspaceCwd: String,
        projectSlug: String
    ): String {
        var adapted = command.trim()
        adapted = DOT_SLASH_SCRIPT_REGEX.replace(adapted) { m ->
            "${m.groupValues[1]}bash ./${m.groupValues[2]}"
        }
        adapted = DOT_SLASH_GRADLEW_REGEX.replace(adapted) { m ->
            "${m.groupValues[1]}bash ./gradlew"
        }

        val venvDir = WorkspacePaths.venvPathForProject(projectSlug.ifBlank { File(workspaceCwd).name })
        if (PYTHON_VENV_CREATE_REGEX.containsMatchIn(adapted)) {
            adapted = PYTHON_VENV_CREATE_REGEX.replace(adapted) { m ->
                "mkdir -p \"${WorkspacePaths.TERMUX_VENVS_ROOT}\" && ${m.groupValues[1]} \"$venvDir\""
            }
        }
        if (PYTHON_VENV_ACTIVATE_REGEX.containsMatchIn(adapted)) {
            adapted = PYTHON_VENV_ACTIVATE_REGEX.replace(adapted) { m ->
                "${m.groupValues[1]}\"$venvDir/bin/activate\""
            }
        }
        if (PYTHON_VENV_BIN_REGEX.containsMatchIn(adapted)) {
            adapted = PYTHON_VENV_BIN_REGEX.replace(adapted) { m ->
                "\"$venvDir/bin/${m.groupValues[1]}\""
            }
        }
        return adapted
    }

    fun consumeOneTimeGitConfigPrefix(workspaceCwd: String): String {
        val clean = workspaceCwd.trim().trimEnd('/')
        if (clean.isEmpty()) return ""
        if (!configuredGitWorkspaces.add(clean)) {
            return ""
        }
        val escaped = clean.replace("'", "'\\''")
        return "git config --global core.fileMode false 2>/dev/null || true; " +
            "git config --global --get-all safe.directory 2>/dev/null | grep -Fxq '$escaped' || " +
            "git config --global --add safe.directory '$escaped' 2>/dev/null || true; "
    }

    fun markGitWorkspaceConfigured(workspaceCwd: String) {
        configuredGitWorkspaces.add(workspaceCwd.trim().trimEnd('/'))
    }

    fun isNpmOrPackageInstallCommand(command: String): Boolean {
        return Regex("""\b(?:npm|pnpm|yarn)\s+(?:install|i|ci|add)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(command)
    }

    fun isLongPackageInstallCommand(command: String): Boolean {
        return Regex(
            """\b(?:npm\s+(?:install|i|ci|add)|pnpm\s+(?:install|i|add)|yarn\s+(?:install|add)|pip3?\s+install|python3?\s+-m\s+pip\s+install|pkg\s+(?:install|i|upgrade)|apt(?:-get)?\s+(?:install|upgrade))\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(command)
    }

    fun isNpmSymlinkFailure(
        command: String,
        stdout: String,
        stderr: String,
        exitCode: Int = 1
    ): Boolean = isNpmSymlinkInstallFailure(command, "$stdout\n$stderr", exitCode)

    fun isNpmSymlinkInstallFailure(
        command: String,
        combinedOutput: String,
        exitCode: Int = 1
    ): Boolean {
        if (exitCode == 0 || !isNpmOrPackageInstallCommand(command)) return false
        val lower = combinedOutput.lowercase(Locale.US)
        val hasSymlinkKeyword = lower.contains("symlink") || lower.contains("bin-links") || lower.contains("enosys")
        val hasPermCode = lower.contains("eperm") || lower.contains("enosys") || lower.contains("operation not permitted") || lower.contains("function not implemented")
        return hasSymlinkKeyword && hasPermCode
    }

    /**
     * Amendment #5: Trigger `"This project needs symlinks. Move it to Termux home?"` ONLY on:
     * 1. Symlink-specific errors (`symlink`, `EPERM`/`ENOSYS` on symlink, `ln -s` failure),
     * 2. `noexec` binary/script execution failure on shared storage (`Permission denied` executing `./...` or `node_modules/...`),
     * 3. Missing `node_modules/.bin/<tool>` shim after `--no-bin-links`.
     * Never trigger on bare unrelated `EACCES`.
     */
    fun shouldOfferMoveToTermuxHomeForSymlinkOrNoexec(
        cmd: String = "",
        command: String = cmd,
        combinedOutput: String = "",
        stdout: String = "",
        stderr: String = "",
        exitCode: Int = 1,
        cwd: String = WorkspacePaths.DEFAULT_SHARED_ROOT,
        workspaceMode: WorkspaceMode = if (cwd.startsWith("/storage/emulated/0")) WorkspaceMode.SHARED_STORAGE else WorkspaceMode.TERMUX_HOME
    ): Boolean {
        val effectiveCmd = command.ifBlank { cmd }
        val effectiveOut = combinedOutput.ifBlank { "$stdout\n$stderr" }
        if (exitCode == 0 || workspaceMode != WorkspaceMode.SHARED_STORAGE) return false
        val lower = effectiveOut.lowercase(Locale.US)

        // 1. Explicit symlink failure
        val isSymlinkError = (lower.contains("symlink") && (
            lower.contains("eperm") ||
                lower.contains("enosys") ||
                lower.contains("operation not permitted") ||
                lower.contains("function not implemented") ||
                lower.contains("failed to create symbolic link")
            )) || lower.contains("failed to create symbolic link")

        // 2. Missing .bin shim (e.g. `node_modules/.bin/vite: not found`)
        val isMissingBinShim = lower.contains("node_modules/.bin") &&
            (lower.contains("not found") || lower.contains("no such file") || lower.contains("permission denied"))

        // 3. `noexec` execution failure on shared storage (never on bare `cat /root/protected.txt: Permission denied (EACCES)`)
        val isNoExecFailure = (lower.contains("permission denied") || exitCode == 126) &&
            !lower.contains("eacces") &&
            (effectiveCmd.contains("node_modules/") ||
                effectiveCmd.contains("./") ||
                (lower.contains("/storage/emulated/0/") && lower.contains("node_modules")))

        return isSymlinkError || isMissingBinShim || isNoExecFailure
    }
}

/**
 * Shared-storage secret file detector (Task 5):
 * Warns once per project if `.env`, `*.pem`, `id_rsa`, `*.key`, etc. exist in `/storage/emulated/0/Nexus/<project>`
 * and excludes them from automatic workspace context.
 */
object WorkspaceSecretScanner {
    private val warnedProjects = ConcurrentHashMap.newKeySet<String>()

    private val SECRET_FILE_NAME_REGEX = Regex(
        """^(?:\.env(?:\..+)?|id_rsa(?:\..+)?|id_ed25519(?:\..+)?|.*\.pem|.*\.key|.*\.p12|.*\.pfx|credentials\.json|service-account.*\.json|\.npmrc|\.pypirc|\.netrc)$""",
        RegexOption.IGNORE_CASE
    )

    fun isSecretFileName(fileName: String): Boolean {
        val name = fileName.trim().substringAfterLast('/')
        if (name.equals(".env.example", ignoreCase = true) || name.equals(".env.sample", ignoreCase = true)) {
            return false
        }
        return SECRET_FILE_NAME_REGEX.matches(name)
    }

    fun scanForNewSecretFilesWarning(projectPath: String): List<String> {
        val cleanPath = projectPath.trim().trimEnd('/')
        if (cleanPath.isEmpty() || !cleanPath.startsWith(WorkspacePaths.SHARED_STORAGE_BASE)) {
            return emptyList()
        }
        if (warnedProjects.contains(cleanPath)) return emptyList()

        val found = findSecretFilesInDirectory(File(cleanPath))
        if (found.isNotEmpty()) {
            warnedProjects.add(cleanPath)
        }
        return found
    }

    fun scanForNewSecretFilesWarning(projectDir: File): String? {
        val found = scanForNewSecretFilesWarning(projectDir.absolutePath)
        if (found.isEmpty()) return null
        return "Warning: Secret or key file(s) (${found.joinToString(", ")}) detected in shared storage (${projectDir.absolutePath}). Other apps with storage access can read this folder, so Nexus excludes these files from automatic AI context."
    }

    fun findSecretFilesInDirectory(rootDir: File): List<String> {
        return try {
            if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
            val results = mutableListOf<String>()
            val children = rootDir.listFiles().orEmpty()
            for (child in children) {
                if (child.isFile && isSecretFileName(child.name)) {
                    results.add(child.name)
                } else if (child.isDirectory && child.name !in setOf("node_modules", ".git", ".nexus", ".nexus-trash", ".venv")) {
                    child.listFiles().orEmpty().forEach { sub ->
                        if (sub.isFile && isSecretFileName(sub.name)) {
                            results.add("${child.name}/${sub.name}")
                        }
                    }
                }
            }
            results.sorted()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/**
 * Mirror sync status for header chip and Settings > Execution environment.
 */
enum class MirrorSyncStatus(val id: String, val label: String) {
    IDLE("idle", "Idle"),
    SYNCED("synced", "Synced"),
    SYNCING("syncing", "Syncing…"),
    OUT_OF_SYNC("out_of_sync", "Out of sync"),
    CONFLICT("conflict", "Conflict"),
    ERROR("error", "Sync error")
}

sealed class MirrorTypedError(open val title: String, open val message: String) {
    data class StorageFull(
        override val title: String = "Storage Full",
        override val message: String = "Device storage is full; could not mirror project to shared storage."
    ) : MirrorTypedError(title, message)

    data class PermissionLost(
        override val title: String = "Permission Lost",
        override val message: String = "Shared storage permission lost; grant All Files Access or run termux-setup-storage."
    ) : MirrorTypedError(title, message)

    data class PathUnreachable(
        val path: String,
        override val message: String = "Shared mirror path is unreachable: $path",
        override val title: String = "Path Unreachable"
    ) : MirrorTypedError(title, message)
}

data class MirrorManifestEntry(
    val relPath: String,
    val size: Long,
    val mtimeSec: Long,
    val sha256: String
)

data class MirrorConflictItem(
    val projectSlug: String,
    val relPath: String,
    val termuxFile: String,
    val sharedFile: String,
    val termuxSha256: String,
    val sharedSha256: String
) {
    val relativePath: String
        get() = relPath
}

enum class MirrorConflictResolution {
    IMPORT_TO_TERMUX_HOME,
    OVERWRITE_SHARED_COPY,
    KEEP_BOTH,
    SKIP
}

typealias MirrorConflictResolutionChoice = MirrorConflictResolution

data class MirrorSettingsConfig(
    val includeGitFolder: Boolean = false,
    val includeBuildOutputs: Boolean = false,
    val mirrorDeletions: Boolean = false,
    val customExcludes: List<String> = emptyList(),
    val mirrorGitFolder: Boolean = includeGitFolder,
    val mirrorBuildOutputs: Boolean = includeBuildOutputs
) {
    fun buildEffectiveExcludes(): List<String> {
        val list = mutableListOf(
            ".nexus-trash",
            ".nexus",
            "node_modules",
            ".venv",
            "venvs",
            "__pycache__",
            ".cache",
            ".env",
            ".env.*",
            "*.pem",
            "id_rsa",
            "id_ed25519",
            "*.key",
            "credentials.json"
        )
        if (!mirrorGitFolder && !includeGitFolder) {
            list.add(".git")
        }
        if (!mirrorBuildOutputs && !includeBuildOutputs) {
            list.add("dist")
            list.add("build")
        }
        customExcludes.map { it.trim() }.filter { it.isNotEmpty() }.forEach { custom ->
            if (custom !in list) list.add(custom)
        }
        return list
    }
}

typealias MirrorExcludeConfig = MirrorSettingsConfig
typealias MirrorConfig = MirrorSettingsConfig

sealed class MirrorSyncResult {
    data class Success(
        val projectSlug: String,
        val filesCopied: Int,
        val trashedCount: Int = 0
    ) : MirrorSyncResult()

    data class ConflictsDetected(
        val projectSlug: String,
        val conflicts: List<MirrorConflictItem>
    ) : MirrorSyncResult()

    data class Failed(
        val projectSlug: String,
        val typedError: MirrorTypedError
    ) : MirrorSyncResult()
}

data class LocalMirrorDirectoryResult(
    val success: Boolean,
    val filesCopied: Int = 0,
    val trashedCount: Int = 0,
    val conflicts: List<MirrorConflictItem> = emptyList(),
    val error: MirrorTypedError? = null
)

data class MirrorSyncOutcome(
    val status: MirrorSyncStatus,
    val syncedFilesCount: Int = 0,
    val trashedFilesCount: Int = 0,
    val conflicts: List<MirrorConflictItem> = emptyList(),
    val error: MirrorTypedError? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val usedRsync: Boolean = false
)

/**
 * Amendment #4: One-Way Source Mirroring Engine (`$HOME/nexus_workspace/<slug>` -> `/storage/emulated/0/Nexus/<slug>`).
 * - Excludes `.git` by default (with toggle), `.nexus-trash`, `.nexus`, `node_modules`, `.venv`, `__pycache__`, `.cache`, `dist`/`build`, and secrets.
 * - Uses `rsync -rt --no-perms --no-owner --no-group --omit-dir-times` with `cp -ru` fallback.
 * - Fast two-phase diff: checks `mtime + size` before computing `sha256`.
 * - Rebuilds a missing/corrupt manifest by comparing both trees (`size + sha256`).
 * - Stages deleted files into `/storage/emulated/0/Nexus/<slug>/.nexus-trash/<timestamp>/` unless `mirrorDeletions` is enabled.
 * - Queues a trailing debounced run if triggered while busy.
 * - Backs up the Termux-home copy to `$HOME/.nexus/mirrors/<slug>/backups/<timestamp>/` before importing external shared edits.
 */
class ProjectMirrorManager(
    @Suppress("UNUSED_PARAMETER") private val appContext: Context? = null,
    private val backendProvider: suspend () -> ExecutionBackend = {
        throw IllegalStateException("ExecutionBackend provider not configured")
    },
    private val excludeConfigProvider: () -> MirrorSettingsConfig = { MirrorSettingsConfig() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    private val _syncStatus = MutableStateFlow(MirrorSyncStatus.SYNCED)
    val syncStatus: StateFlow<MirrorSyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestampMs = MutableStateFlow(0L)
    val lastSyncTimestampMs: StateFlow<Long> = _lastSyncTimestampMs.asStateFlow()

    private val _activeConflicts = MutableStateFlow<List<MirrorConflictItem>>(emptyList())
    val activeConflicts: StateFlow<List<MirrorConflictItem>> = _activeConflicts.asStateFlow()

    private val _lastTypedError = MutableStateFlow<MirrorTypedError?>(null)
    val lastTypedError: StateFlow<MirrorTypedError?> = _lastTypedError.asStateFlow()

    @Volatile
    var isSyncing: Boolean = false
        private set

    @Volatile
    var hasPendingTrailingSync: Boolean = false
        private set

    fun scheduleDebouncedSync(projectSlug: String, debounceMs: Long = 3_000L) {
        val clean = WorkspacePaths.sanitizeSlug(projectSlug)
        if (clean.isEmpty()) return
        if (isSyncing) {
            hasPendingTrailingSync = true
            return
        }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            syncProjectToShared(clean, excludeConfigProvider())
            if (consumePendingTrailingSync()) {
                delay(debounceMs)
                syncProjectToShared(clean, excludeConfigProvider())
            }
        }
    }

    fun consumePendingTrailingSync(): Boolean {
        val pending = hasPendingTrailingSync
        hasPendingTrailingSync = false
        return pending
    }

    suspend fun syncProjectToShared(
        projectSlug: String,
        config: MirrorSettingsConfig = excludeConfigProvider()
    ): MirrorSyncResult {
        val cleanSlug = WorkspacePaths.sanitizeSlug(projectSlug)
        val outcome = syncProjectOneWay(cleanSlug, config)
        return when {
            outcome.error != null -> {
                _syncStatus.value = MirrorSyncStatus.ERROR
                _lastTypedError.value = outcome.error
                MirrorSyncResult.Failed(cleanSlug, outcome.error)
            }
            outcome.conflicts.isNotEmpty() -> {
                _syncStatus.value = MirrorSyncStatus.CONFLICT
                _activeConflicts.value = outcome.conflicts
                MirrorSyncResult.ConflictsDetected(cleanSlug, outcome.conflicts)
            }
            else -> {
                _syncStatus.value = MirrorSyncStatus.SYNCED
                _activeConflicts.value = emptyList()
                _lastTypedError.value = null
                _lastSyncTimestampMs.value = outcome.timestampMs
                MirrorSyncResult.Success(
                    projectSlug = cleanSlug,
                    filesCopied = outcome.syncedFilesCount,
                    trashedCount = outcome.trashedFilesCount
                )
            }
        }
    }

    suspend fun importFromSharedToTermuxHome(projectSlug: String): Result<String> {
        val cleanSlug = WorkspacePaths.sanitizeSlug(projectSlug)
        if (cleanSlug.isEmpty()) {
            return Result.failure(IllegalArgumentException("Project slug cannot be blank"))
        }
        return try {
            val backend = backendProvider()
            val termuxDir = WorkspacePaths.termuxHomePathForSlug(cleanSlug)
            val sharedDir = WorkspacePaths.sharedPathForSlug(cleanSlug)
            val timestampSec = clock() / 1000L
            val backupDir = "${WorkspacePaths.TERMUX_MIRRORS_ROOT}/$cleanSlug/backups/$timestampSec"
            val excludes = excludeConfigProvider().buildEffectiveExcludes()
            val copyCmd = buildString {
                append("mkdir -p '$backupDir' '$termuxDir' && ")
                append("if [ -d '$termuxDir' ]; then ")
                append(buildRsyncOrCpCommand(termuxDir, backupDir, excludes))
                append("; fi && ")
                append(buildRsyncOrCpCommand(sharedDir, termuxDir, excludes))
            }
            var exit = 0
            val err = StringBuilder()
            backend.exec(cmd = copyCmd, cwd = WorkspacePaths.TERMUX_HOME_DIR, timeoutSec = 60L).collect { ev ->
                when (ev) {
                    is ExecEvent.StderrChunk -> err.append(ev.text)
                    is ExecEvent.Completed -> exit = ev.exitCode
                    else -> Unit
                }
            }
            if (exit == 0) {
                _activeConflicts.value = emptyList()
                _syncStatus.value = MirrorSyncStatus.SYNCED
                _lastSyncTimestampMs.value = clock()
                Result.success(backupDir)
            } else {
                Result.failure(IllegalStateException(err.toString().trim().ifBlank { "Import command exited with $exit" }))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Local JVM File-based one-way sync used in unit tests and host-side directory mirroring:
     * Enforces `.git`, `.nexus-trash`, `node_modules`, and secret file exclusions, two-phase `mtime + size` before `sha256`,
     * safe deletion staging into `.nexus-trash/<timestamp>/`, and missing manifest rebuild.
     */
    fun syncDirectoryPair(
        projectSlug: String,
        sourceDir: File,
        targetDir: File,
        manifestFile: File,
        config: MirrorSettingsConfig = MirrorSettingsConfig()
    ): LocalMirrorDirectoryResult {
        return try {
            sourceDir.mkdirs()
            targetDir.mkdirs()
            val excludes = config.buildEffectiveExcludes()
            val sourceMap = scanLocalTree(sourceDir, excludes)
            val targetMap = scanLocalTree(targetDir, excludes)

            val existingManifest: Map<String, MirrorManifestEntry>? = if (manifestFile.exists()) {
                parseManifestJson(manifestFile.readText())
            } else if (targetMap.isEmpty()) {
                emptyMap()
            } else {
                null
            }

            val (conflicts, deletedRelPaths, updatedManifest) = evaluateTreeDiffAndConflicts(
                projectSlug = projectSlug,
                termuxRoot = sourceDir.absolutePath,
                sharedRoot = targetDir.absolutePath,
                termuxFiles = sourceMap,
                sharedFiles = targetMap,
                manifest = existingManifest,
                hashLookup = { isShared, rel ->
                    val f = File(if (isShared) targetDir else sourceDir, rel)
                    computeFileSha256(f)
                }
            )

            val timestampSec = clock() / 1000L
            for (rel in deletedRelPaths) {
                val targetFile = File(targetDir, rel)
                if (targetFile.exists()) {
                    if (config.mirrorDeletions) {
                        targetFile.delete()
                    } else {
                        val trashFile = File(targetDir, ".nexus-trash/$timestampSec/$rel")
                        trashFile.parentFile?.mkdirs()
                        targetFile.copyTo(trashFile, overwrite = true)
                        targetFile.delete()
                    }
                }
            }

            val conflictRelSet = conflicts.map { it.relPath }.toSet()
            var copiedCount = 0
            for ((rel, _) in sourceMap) {
                if (rel in conflictRelSet) continue
                val srcFile = File(sourceDir, rel)
                val dstFile = File(targetDir, rel)
                dstFile.parentFile?.mkdirs()
                srcFile.copyTo(dstFile, overwrite = true)
                dstFile.setLastModified(srcFile.lastModified())
                copiedCount++
            }

            // Record synced target file mtime+size in manifest so subsequent fast checks know target wasn't modified externally
            val persistedManifest = LinkedHashMap<String, MirrorManifestEntry>()
            for ((rel, entry) in updatedManifest) {
                val dstFile = File(targetDir, rel)
                if (dstFile.exists() && rel !in conflictRelSet) {
                    persistedManifest[rel] = entry.copy(
                        size = dstFile.length(),
                        mtimeSec = dstFile.lastModified() / 1000L,
                        sha256 = entry.sha256.ifBlank { computeFileSha256(dstFile) }
                    )
                } else {
                    persistedManifest[rel] = entry
                }
            }

            manifestFile.parentFile?.mkdirs()
            manifestFile.writeText(serializeManifestJson(persistedManifest))

            LocalMirrorDirectoryResult(
                success = true,
                filesCopied = copiedCount,
                trashedCount = deletedRelPaths.size,
                conflicts = conflicts
            )
        } catch (t: Throwable) {
            LocalMirrorDirectoryResult(
                success = false,
                error = MirrorTypedError.PathUnreachable(targetDir.absolutePath, t.message ?: "Mirror failed")
            )
        }
    }

    /**
     * Local JVM File-based import from shared to Termux home with automatic pre-import backup
     * of the Termux-home copy into `backupRootDir/<timestamp>/`.
     */
    fun importDirectoryPairWithBackup(
        projectSlug: String,
        sharedDir: File,
        termuxDir: File,
        backupRootDir: File,
        manifestFile: File,
        config: MirrorSettingsConfig = MirrorSettingsConfig()
    ): LocalMirrorDirectoryResult {
        return try {
            val excludes = config.buildEffectiveExcludes()
            val timestampMs = clock()
            val backupDir = File(backupRootDir, timestampMs.toString())
            backupDir.mkdirs()

            // 1. Back up existing Termux-home files before importing
            val existingTermuxFiles = scanLocalTree(termuxDir, excludes)
            for ((rel, _) in existingTermuxFiles) {
                val src = File(termuxDir, rel)
                val dst = File(backupDir, rel)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
            }

            // 2. Copy shared files into Termux home and update manifest
            val sharedFiles = scanLocalTree(sharedDir, excludes)
            val newManifest = LinkedHashMap<String, MirrorManifestEntry>()
            var importedCount = 0
            for ((rel, _) in sharedFiles) {
                val src = File(sharedDir, rel)
                val dst = File(termuxDir, rel)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                dst.setLastModified(src.lastModified())
                val sha = computeFileSha256(src)
                newManifest[rel] = MirrorManifestEntry(
                    relPath = rel,
                    size = src.length(),
                    mtimeSec = src.lastModified() / 1000L,
                    sha256 = sha
                )
                importedCount++
            }

            manifestFile.parentFile?.mkdirs()
            manifestFile.writeText(serializeManifestJson(newManifest))

            LocalMirrorDirectoryResult(
                success = true,
                filesCopied = importedCount
            )
        } catch (t: Throwable) {
            LocalMirrorDirectoryResult(
                success = false,
                error = MirrorTypedError.PathUnreachable(sharedDir.absolutePath, t.message ?: "Import failed")
            )
        }
    }

    private fun scanLocalTree(rootDir: File, excludes: List<String>): Map<String, MirrorManifestEntry> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyMap()
        val result = LinkedHashMap<String, MirrorManifestEntry>()
        val rootPath = rootDir.canonicalFile.toPath()
        rootDir.canonicalFile.walkTopDown()
            .onEnter { dir ->
                if (dir == rootDir.canonicalFile) true
                else !isExcludedName(dir.name, excludes)
            }
            .filter { it.isFile && !isExcludedName(it.name, excludes) && !WorkspaceSecretScanner.isSecretFileName(it.name) }
            .forEach { file ->
                val rel = rootPath.relativize(file.toPath()).toString().replace('\\', '/')
                result[rel] = MirrorManifestEntry(
                    relPath = rel,
                    size = file.length(),
                    mtimeSec = file.lastModified() / 1000L,
                    sha256 = ""
                )
            }
        return result
    }

    private fun isExcludedName(name: String, excludes: List<String>): Boolean {
        for (pattern in excludes) {
            if (pattern.startsWith("*.")) {
                val ext = pattern.removePrefix("*")
                if (name.endsWith(ext, ignoreCase = true)) return true
            } else if (pattern.endsWith(".*")) {
                val prefix = pattern.removeSuffix("*")
                if (name.startsWith(prefix, ignoreCase = true)) return true
            } else if (name.equals(pattern, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun computeFileSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } > 0) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun buildRsyncOrCpCommand(
            sourceDir: String,
            destDir: String,
            excludes: List<String>,
            skipConflictRelPaths: Set<String> = emptySet()
        ): String {
            val escSrc = sourceDir.trimEnd('/').replace("'", "'\\''")
            val escDst = destDir.trimEnd('/').replace("'", "'\\''")
            val allExcludes = (excludes + skipConflictRelPaths).distinct()
            val rsyncExcludes = allExcludes.joinToString(" ") { pat ->
                "--exclude='${pat.replace("'", "'\\''")}'"
            }

            val findPrunes = excludes.joinToString(" -o ") { pat ->
                "-name '${pat.replace("'", "'\\''")}'"
            }
            return buildString {
                append("mkdir -p '$escDst' && ")
                append("if command -v rsync >/dev/null 2>&1; then ")
                append("rsync -rt --no-perms --no-owner --no-group --omit-dir-times $rsyncExcludes '$escSrc/' '$escDst/' && echo 'NEXUS_MIRROR_ENGINE:rsync'; ")
                append("else ")
                append("(cd '$escSrc' && find . \\( $findPrunes \\) -prune -o -type f -print | while IFS= read -r __f; do ")
                append("__rel=\"\${__f#./}\"; ")
                if (skipConflictRelPaths.isNotEmpty()) {
                    val skipCheck = skipConflictRelPaths.joinToString(" || ") {
                        "[ \"\$__rel\" = '${it.replace("'", "'\\''")}' ]"
                    }
                    append("if $skipCheck; then continue; fi; ")
                }
                append("mkdir -p \"$escDst/\$(dirname \"\$__rel\")\" && cp -ru \"\$__f\" \"$escDst/\$__rel\" 2>/dev/null || cp -f \"\$__f\" \"$escDst/\$__rel\"; ")
                append("done) && echo 'NEXUS_MIRROR_ENGINE:cp_fallback'; ")
                append("fi")
            }
        }

        fun serializeManifestJson(entries: Map<String, MirrorManifestEntry>): String {
            val root = JSONObject()
            root.put("version", 1)
            val arr = JSONArray()
            entries.values.sortedBy { it.relPath }.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("relPath", e.relPath)
                        put("size", e.size)
                        put("mtimeSec", e.mtimeSec)
                        put("sha256", e.sha256)
                    }
                )
            }
            root.put("files", arr)
            return root.toString()
        }

        fun parseManifestJson(raw: String): Map<String, MirrorManifestEntry>? {
            if (raw.isBlank()) return null
            return runCatching {
                val obj = JSONObject(raw.trim())
                val arr = obj.getJSONArray("files")
                val map = LinkedHashMap<String, MirrorManifestEntry>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val rel = item.optString("relPath", "").trim()
                    if (rel.isEmpty()) continue
                    map[rel] = MirrorManifestEntry(
                        relPath = rel,
                        size = item.optLong("size", 0L),
                        mtimeSec = item.optLong("mtimeSec", 0L),
                        sha256 = item.optString("sha256", "")
                    )
                }
                map
            }.getOrNull()
        }

        /**
         * Two-phase conflict & diff evaluator:
         * Checks `mtime + size` first against [manifest]; only requires `sha256` comparison when `mtime` or `size` changed.
         * Also supports rebuilding a missing manifest (`manifest == null`) by comparing `size + sha256` across both trees.
         */
        fun evaluateTreeDiffAndConflicts(
            projectSlug: String,
            termuxRoot: String,
            sharedRoot: String,
            termuxFiles: Map<String, MirrorManifestEntry>,
            sharedFiles: Map<String, MirrorManifestEntry>,
            manifest: Map<String, MirrorManifestEntry>?,
            hashLookup: (isShared: Boolean, relPath: String) -> String
        ): Triple<List<MirrorConflictItem>, List<String>, Map<String, MirrorManifestEntry>> {
            val conflicts = mutableListOf<MirrorConflictItem>()
            val deletedInTermux = mutableListOf<String>()
            val rebuiltOrUpdatedManifest = LinkedHashMap<String, MirrorManifestEntry>()

            if (manifest == null) {
                val allPaths = (termuxFiles.keys + sharedFiles.keys).sorted()
                for (rel in allPaths) {
                    if (rel.startsWith(".nexus-trash/") || rel == ".nexus-trash") continue
                    val tEntry = termuxFiles[rel]
                    val sEntry = sharedFiles[rel]
                    when {
                        tEntry != null && sEntry != null -> {
                            val tHash = tEntry.sha256.ifBlank { hashLookup(false, rel) }
                            val sHash = if (tEntry.size == sEntry.size) {
                                sEntry.sha256.ifBlank { hashLookup(true, rel) }
                            } else {
                                sEntry.sha256.ifBlank { "size_mismatch_${sEntry.size}" }
                            }
                            if (tEntry.size == sEntry.size && tHash.isNotBlank() && tHash == sHash) {
                                rebuiltOrUpdatedManifest[rel] = tEntry.copy(sha256 = tHash)
                            } else {
                                conflicts.add(
                                    MirrorConflictItem(
                                        projectSlug = projectSlug,
                                        relPath = rel,
                                        termuxFile = "$termuxRoot/$rel",
                                        sharedFile = "$sharedRoot/$rel",
                                        termuxSha256 = tHash,
                                        sharedSha256 = sHash
                                    )
                                )
                            }
                        }
                        tEntry != null -> {
                            val tHash = tEntry.sha256.ifBlank { hashLookup(false, rel) }
                            rebuiltOrUpdatedManifest[rel] = tEntry.copy(sha256 = tHash)
                        }
                        sEntry != null -> {
                            val sHash = sEntry.sha256.ifBlank { hashLookup(true, rel) }
                            conflicts.add(
                                MirrorConflictItem(
                                    projectSlug = projectSlug,
                                    relPath = rel,
                                    termuxFile = "$termuxRoot/$rel",
                                    sharedFile = "$sharedRoot/$rel",
                                    termuxSha256 = "",
                                    sharedSha256 = sHash
                                )
                            )
                        }
                    }
                }
                return Triple(conflicts, deletedInTermux, rebuiltOrUpdatedManifest)
            }

            // 1) Detect files deleted in Termux home that were previously mirrored
            for ((rel, prev) in manifest) {
                if (rel.startsWith(".nexus-trash/") || rel == ".nexus-trash") continue
                if (!termuxFiles.containsKey(rel) && sharedFiles.containsKey(rel)) {
                    val sEntry = sharedFiles[rel]!!
                    val sharedModified = if (sEntry.size == prev.size && sEntry.mtimeSec == prev.mtimeSec) {
                        false
                    } else {
                        val sHash = sEntry.sha256.ifBlank { hashLookup(true, rel) }
                        sHash != prev.sha256
                    }
                    if (sharedModified) {
                        val sHash = sEntry.sha256.ifBlank { hashLookup(true, rel) }
                        conflicts.add(
                            MirrorConflictItem(
                                projectSlug = projectSlug,
                                relPath = rel,
                                termuxFile = "$termuxRoot/$rel",
                                sharedFile = "$sharedRoot/$rel",
                                termuxSha256 = "",
                                sharedSha256 = sHash
                            )
                        )
                    } else {
                        deletedInTermux.add(rel)
                    }
                }
            }

            // 2) Check existing/modified files in Termux home against shared copy
            for ((rel, tEntry) in termuxFiles) {
                if (rel.startsWith(".nexus-trash/") || rel == ".nexus-trash") continue
                val prev = manifest[rel]
                val sEntry = sharedFiles[rel]
                if (sEntry != null) {
                    if (prev != null) {
                        val sharedUnchangedFast = (sEntry.size == prev.size && sEntry.mtimeSec == prev.mtimeSec)
                        if (!sharedUnchangedFast) {
                            val sHash = sEntry.sha256.ifBlank { hashLookup(true, rel) }
                            if (sHash != prev.sha256) {
                                val tHash = if (tEntry.size == prev.size && tEntry.mtimeSec == prev.mtimeSec && prev.sha256.isNotBlank()) {
                                    prev.sha256
                                } else {
                                    tEntry.sha256.ifBlank { hashLookup(false, rel) }
                                }
                                if (sHash != tHash) {
                                    conflicts.add(
                                        MirrorConflictItem(
                                            projectSlug = projectSlug,
                                            relPath = rel,
                                            termuxFile = "$termuxRoot/$rel",
                                            sharedFile = "$sharedRoot/$rel",
                                            termuxSha256 = tHash,
                                            sharedSha256 = sHash
                                        )
                                    )
                                    rebuiltOrUpdatedManifest[rel] = prev
                                    continue
                                }
                            }
                        }
                    } else {
                        val tHash = tEntry.sha256.ifBlank { hashLookup(false, rel) }
                        val sHash = sEntry.sha256.ifBlank { hashLookup(true, rel) }
                        if (tHash != sHash) {
                            conflicts.add(
                                MirrorConflictItem(
                                    projectSlug = projectSlug,
                                    relPath = rel,
                                    termuxFile = "$termuxRoot/$rel",
                                    sharedFile = "$sharedRoot/$rel",
                                    termuxSha256 = tHash,
                                    sharedSha256 = sHash
                                )
                            )
                            continue
                        }
                    }
                }

                val finalHash = if (prev != null && tEntry.size == prev.size && tEntry.mtimeSec == prev.mtimeSec && prev.sha256.isNotBlank()) {
                    prev.sha256
                } else {
                    tEntry.sha256.ifBlank { hashLookup(false, rel) }
                }
                rebuiltOrUpdatedManifest[rel] = tEntry.copy(sha256 = finalHash)
            }

            return Triple(conflicts, deletedInTermux, rebuiltOrUpdatedManifest)
        }

        fun buildConflictResolutionScript(
            item: MirrorConflictItem,
            resolution: MirrorConflictResolution,
            timestampSec: Long
        ): String {
            val escRel = item.relPath.replace("'", "'\\''")
            val escTermux = item.termuxFile.replace("'", "'\\''")
            val escShared = item.sharedFile.replace("'", "'\\''")
            val backupFile = "${WorkspacePaths.TERMUX_MIRRORS_ROOT}/${item.projectSlug}/backups/$timestampSec/$escRel"
            val sharedRoot = item.sharedFile.removeSuffix("/${item.relPath}").replace("'", "'\\''")
            val trashFile = "$sharedRoot/.nexus-trash/$timestampSec/$escRel"

            return when (resolution) {
                MirrorConflictResolution.IMPORT_TO_TERMUX_HOME -> buildString {
                    append("mkdir -p \"\$(dirname '$backupFile')\" && ")
                    append("if [ -f '$escTermux' ]; then cp -f '$escTermux' '$backupFile'; fi && ")
                    append("mkdir -p \"\$(dirname '$escTermux')\" && ")
                    append("cp -f '$escShared' '$escTermux'")
                }
                MirrorConflictResolution.OVERWRITE_SHARED_COPY -> buildString {
                    append("mkdir -p \"\$(dirname '$trashFile')\" && ")
                    append("if [ -f '$escShared' ]; then cp -f '$escShared' '$trashFile'; fi && ")
                    append("mkdir -p \"\$(dirname '$escShared')\" && ")
                    append("cp -f '$escTermux' '$escShared'")
                }
                MirrorConflictResolution.KEEP_BOTH -> {
                    val sharedCopyInTermux = "${escTermux}.shared-$timestampSec"
                    val sharedCopyInShared = "${escShared}.shared-$timestampSec"
                    buildString {
                        append("if [ -f '$escShared' ]; then cp -f '$escShared' '$sharedCopyInTermux' && cp -f '$escShared' '$sharedCopyInShared'; fi && ")
                        append("if [ -f '$escTermux' ]; then cp -f '$escTermux' '$escShared'; fi")
                    }
                }
                MirrorConflictResolution.SKIP -> "true"
            }
        }
    }

    suspend fun syncProjectOneWay(
        projectSlug: String,
        config: MirrorSettingsConfig = MirrorSettingsConfig(),
        sharedRootOverride: String = WorkspacePaths.DEFAULT_SHARED_ROOT
    ): MirrorSyncOutcome {
        val cleanSlug = WorkspacePaths.sanitizeSlug(projectSlug)
        if (cleanSlug.isEmpty()) {
            return MirrorSyncOutcome(status = MirrorSyncStatus.IDLE)
        }
        if (isSyncing) {
            hasPendingTrailingSync = true
            return MirrorSyncOutcome(status = MirrorSyncStatus.SYNCING)
        }
        isSyncing = true
        _syncStatus.value = MirrorSyncStatus.SYNCING
        try {
            val backend = backendProvider()
            val termuxDir = WorkspacePaths.termuxHomePathForSlug(cleanSlug)
            val sharedDir = WorkspacePaths.sharedPathForSlug(cleanSlug, sharedRootOverride)
            val mirrorMetaDir = "${WorkspacePaths.TERMUX_MIRRORS_ROOT}/$cleanSlug"
            val manifestPath = "$mirrorMetaDir/manifest.json"
            val excludes = config.buildEffectiveExcludes()

            val statScript = buildTreeStatScript(termuxDir, sharedDir, manifestPath, excludes)
            val out = StringBuilder()
            val err = StringBuilder()
            var exit = 0
            backend.exec(cmd = statScript, cwd = WorkspacePaths.TERMUX_HOME_DIR, timeoutSec = 30L).collect { ev ->
                when (ev) {
                    is ExecEvent.StdoutChunk -> out.append(ev.text)
                    is ExecEvent.StderrChunk -> err.append(ev.text)
                    is ExecEvent.Completed -> exit = ev.exitCode
                }
            }

            val combinedLower = (err.toString() + "\n" + out.toString()).lowercase(Locale.US)
            if (combinedLower.contains("no space left on device") || combinedLower.contains("enospc")) {
                return MirrorSyncOutcome(status = MirrorSyncStatus.ERROR, error = MirrorTypedError.StorageFull())
            }
            if (combinedLower.contains("permission denied") && combinedLower.contains(sharedDir.lowercase(Locale.US))) {
                return MirrorSyncOutcome(status = MirrorSyncStatus.ERROR, error = MirrorTypedError.PermissionLost())
            }
            if (exit != 0 && !out.toString().contains("NEXUS_STAT_DONE")) {
                return MirrorSyncOutcome(
                    status = MirrorSyncStatus.ERROR,
                    error = MirrorTypedError.PathUnreachable(sharedDir)
                )
            }

            val parsedScan = parseTreeStatOutput(out.toString())
            val existingManifest = if (parsedScan.manifestRaw.isNotBlank()) {
                parseManifestJson(parsedScan.manifestRaw)
            } else if (parsedScan.sharedFiles.isEmpty()) {
                emptyMap()
            } else {
                null
            }

            val (conflicts, deletedRelPaths, updatedManifest) = evaluateTreeDiffAndConflicts(
                projectSlug = cleanSlug,
                termuxRoot = termuxDir,
                sharedRoot = sharedDir,
                termuxFiles = parsedScan.termuxFiles,
                sharedFiles = parsedScan.sharedFiles,
                manifest = existingManifest,
                hashLookup = { isShared, rel ->
                    if (isShared) parsedScan.sharedFiles[rel]?.sha256.orEmpty()
                    else parsedScan.termuxFiles[rel]?.sha256.orEmpty()
                }
            )

            val nowSec = clock() / 1000L
            var trashedCount = 0
            if (deletedRelPaths.isNotEmpty()) {
                val delScript = buildDeletionStagingScript(
                    sharedDir = sharedDir,
                    deletedRelPaths = deletedRelPaths,
                    mirrorDeletions = config.mirrorDeletions,
                    timestampSec = nowSec
                )
                backend.exec(cmd = delScript, cwd = WorkspacePaths.TERMUX_HOME_DIR, timeoutSec = 25L).collect { }
                trashedCount = deletedRelPaths.size
            }

            val conflictPathsSet = conflicts.map { it.relPath }.toSet()
            val syncCmd = buildRsyncOrCpCommand(
                sourceDir = termuxDir,
                destDir = sharedDir,
                excludes = excludes,
                skipConflictRelPaths = conflictPathsSet
            )
            val syncOut = StringBuilder()
            val syncErr = StringBuilder()
            var syncExit = 0
            backend.exec(cmd = syncCmd, cwd = WorkspacePaths.TERMUX_HOME_DIR, timeoutSec = 60L).collect { ev ->
                when (ev) {
                    is ExecEvent.StdoutChunk -> syncOut.append(ev.text)
                    is ExecEvent.StderrChunk -> syncErr.append(ev.text)
                    is ExecEvent.Completed -> syncExit = ev.exitCode
                }
            }

            val syncLower = (syncErr.toString() + "\n" + syncOut.toString()).lowercase(Locale.US)
            if (syncExit != 0) {
                val typedErr = when {
                    syncLower.contains("no space left") || syncLower.contains("enospc") -> MirrorTypedError.StorageFull()
                    syncLower.contains("permission denied") -> MirrorTypedError.PermissionLost()
                    else -> MirrorTypedError.PathUnreachable(sharedDir, syncErr.toString().trim().ifBlank { "Mirror command failed (exit $syncExit)" })
                }
                return MirrorSyncOutcome(status = MirrorSyncStatus.ERROR, error = typedErr)
            }

            val manifestJson = serializeManifestJson(updatedManifest)
            BackendWorkspaceFileOps.writeFileViaBackend(
                backend = backend,
                cwd = WorkspacePaths.TERMUX_HOME_DIR,
                targetPath = manifestPath,
                content = manifestJson
            )

            val usedRsync = syncOut.toString().contains("NEXUS_MIRROR_ENGINE:rsync")
            val finalStatus = if (conflicts.isNotEmpty()) MirrorSyncStatus.CONFLICT else MirrorSyncStatus.SYNCED
            return MirrorSyncOutcome(
                status = finalStatus,
                syncedFilesCount = updatedManifest.size,
                trashedFilesCount = trashedCount,
                conflicts = conflicts,
                timestampMs = clock(),
                usedRsync = usedRsync
            )
        } finally {
            isSyncing = false
        }
    }

    suspend fun resolveConflict(
        item: MirrorConflictItem,
        resolution: MirrorConflictResolution
    ): Boolean {
        val backend = backendProvider()
        val nowSec = clock() / 1000L
        val script = buildConflictResolutionScript(item, resolution, nowSec)
        var exit = 0
        backend.exec(cmd = script, cwd = WorkspacePaths.TERMUX_HOME_DIR, timeoutSec = 30L).collect { ev ->
            if (ev is ExecEvent.Completed) exit = ev.exitCode
        }
        if (exit == 0) {
            _activeConflicts.value = _activeConflicts.value.filterNot {
                it.projectSlug == item.projectSlug && it.relPath == item.relPath
            }
            if (_activeConflicts.value.isEmpty()) {
                _syncStatus.value = MirrorSyncStatus.SYNCED
            }
        }
        return exit == 0
    }

    private data class ParsedTreeStat(
        val manifestRaw: String,
        val termuxFiles: Map<String, MirrorManifestEntry>,
        val sharedFiles: Map<String, MirrorManifestEntry>
    )

    private fun buildTreeStatScript(
        termuxDir: String,
        sharedDir: String,
        manifestPath: String,
        excludes: List<String>
    ): String {
        val escTermux = termuxDir.trimEnd('/').replace("'", "'\\''")
        val escShared = sharedDir.trimEnd('/').replace("'", "'\\''")
        val escManifest = manifestPath.replace("'", "'\\''")
        val findPrunes = excludes.joinToString(" -o ") { pat ->
            "-name '${pat.replace("'", "'\\''")}'"
        }
        return buildString {
            append("mkdir -p '$escTermux' '$escShared' \"\$(dirname '$escManifest')\" && ")
            append("printf 'NEXUS_MANIFEST_START\\n'; ")
            append("if [ -f '$escManifest' ]; then cat '$escManifest'; fi; ")
            append("printf '\\nNEXUS_MANIFEST_END\\n'; ")
            append("(cd '$escTermux' && find . \\( $findPrunes \\) -prune -o -type f -print | while IFS= read -r __f; do ")
            append("__rel=\"\${__f#./}\"; ")
            append("__st=\$(stat -c '%s:%Y' \"\$__f\" 2>/dev/null || wc -c < \"\$__f\" | tr -d ' '); ")
            append("__sh=\$(sha256sum \"\$__f\" 2>/dev/null | awk '{print \$1}'); ")
            append("printf 'NEXUS_T_FILE:%s|%s|%s\\n' \"\$__rel\" \"\$__st\" \"\$__sh\"; ")
            append("done); ")
            append("(cd '$escShared' && find . \\( $findPrunes \\) -prune -o -type f -print | while IFS= read -r __f; do ")
            append("__rel=\"\${__f#./}\"; ")
            append("__st=\$(stat -c '%s:%Y' \"\$__f\" 2>/dev/null || wc -c < \"\$__f\" | tr -d ' '); ")
            append("__sh=\$(sha256sum \"\$__f\" 2>/dev/null | awk '{print \$1}'); ")
            append("printf 'NEXUS_S_FILE:%s|%s|%s\\n' \"\$__rel\" \"\$__st\" \"\$__sh\"; ")
            append("done); ")
            append("printf 'NEXUS_STAT_DONE\\n'")
        }
    }

    private fun parseTreeStatOutput(raw: String): ParsedTreeStat {
        val manifestStart = raw.indexOf("NEXUS_MANIFEST_START")
        val manifestEnd = raw.indexOf("NEXUS_MANIFEST_END")
        val manifestRaw = if (manifestStart >= 0 && manifestEnd > manifestStart) {
            raw.substring(manifestStart + "NEXUS_MANIFEST_START".length, manifestEnd).trim()
        } else ""

        val termuxMap = LinkedHashMap<String, MirrorManifestEntry>()
        val sharedMap = LinkedHashMap<String, MirrorManifestEntry>()

        for (line in raw.lineSequence()) {
            when {
                line.startsWith("NEXUS_T_FILE:") -> {
                    parseStatLine(line.removePrefix("NEXUS_T_FILE:"))?.let { termuxMap[it.relPath] = it }
                }
                line.startsWith("NEXUS_S_FILE:") -> {
                    parseStatLine(line.removePrefix("NEXUS_S_FILE:"))?.let { sharedMap[it.relPath] = it }
                }
            }
        }
        return ParsedTreeStat(manifestRaw, termuxMap, sharedMap)
    }

    private fun parseStatLine(payload: String): MirrorManifestEntry? {
        val parts = payload.split("|")
        if (parts.size < 2) return null
        val rel = parts[0].trim()
        if (rel.isEmpty()) return null
        val statParts = parts[1].split(":")
        val size = statParts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
        val mtime = statParts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        val sha = parts.getOrNull(2)?.trim().orEmpty()
        return MirrorManifestEntry(relPath = rel, size = size, mtimeSec = mtime, sha256 = sha)
    }

    private fun buildDeletionStagingScript(
        sharedDir: String,
        deletedRelPaths: List<String>,
        mirrorDeletions: Boolean,
        timestampSec: Long
    ): String {
        val escShared = sharedDir.trimEnd('/').replace("'", "'\\''")
        return deletedRelPaths.joinToString("; ") { rel ->
            val escRel = rel.replace("'", "'\\''")
            val target = "$escShared/$escRel"
            if (mirrorDeletions) {
                "rm -f '$target' 2>/dev/null || true"
            } else {
                val trashTarget = "$escShared/.nexus-trash/$timestampSec/$escRel"
                "if [ -f '$target' ]; then mkdir -p \"\$(dirname '$trashTarget')\" && mv '$target' '$trashTarget' 2>/dev/null || true; fi"
            }
        }
    }
}
