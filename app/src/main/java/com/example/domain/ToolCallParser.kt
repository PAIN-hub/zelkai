package com.example.domain

import org.json.JSONObject

/**
 * Risk classification for proposed shell commands.
 * - LOW: Safe workspace operations (e.g., mkdir, ls, pwd, cat, stat, echo, df, uname, touch, find, grep) -> auto-run.
 * - MEDIUM: File modifications/deletions, permission changes, network requests, or package installs -> requires approval.
 * - HIGH: Recursive deletions, privilege escalation, raw block device operations, or piped shell execution -> requires approval.
 */
enum class CommandRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Action kind for tool calls (foreground shell vs detached background jobs).
 */
enum class ToolActionKind {
    SHELL,
    START_JOB,
    WAIT_FOR_JOB,
    JOB_STATUS,
    JOB_LOGS,
    STOP_JOB,
    WRITE_FILE
}

/**
 * Normalized provider-independent tool call model.
 * Both fenced blocks (`sh-exec` / `bash` / `sh` / `shell` / JSON fallback), `<nexus:file>` blocks,
 * and cloud native function calls normalize into this single [ToolCall] model.
 */
data class ToolCall(
    val command: String,
    val reason: String = "Execute shell command",
    val timeoutSec: Long = 10L,
    val source: String = "sh-exec",
    val riskLevel: CommandRiskLevel = ToolCallParser.classifyRisk(command),
    val approvedMultiLine: Boolean = false,
    val actionKind: ToolActionKind = if (ServerLogAnalyzer.shouldRunAsBackgroundJob(command)) {
        ToolActionKind.START_JOB
    } else {
        ToolActionKind.SHELL
    },
    val jobId: String = "",
    val logLinesCount: Int = 50,
    val runsInBackground: Boolean = actionKind == ToolActionKind.START_JOB || ServerLogAnalyzer.shouldRunAsBackgroundJob(command),
    val explicitTitle: String? = null,
    val targetFilePath: String? = null,
    val targetFileContent: String? = null,
    val executionPolicy: CommandExecutionPolicy = CommandExecutionPolicy.detectPolicy(command),
    val waitMaxSeconds: Long = 120L
)

sealed interface ToolCallParseResult {
    data object NoToolCall : ToolCallParseResult

    data class Success(
        val toolCall: ToolCall,
        val cleanedAssistantText: String
    ) : ToolCallParseResult

    data class ParseError(
        val message: String,
        val rawSnippet: String = ""
    ) : ToolCallParseResult
}

/**
 * Provider-independent parser and risk classifier for Nexus Agent v5 shell execution and background jobs.
 *
 * Protocol:
 * - Commands are emitted as a fenced block:
 *   ```sh-exec
 *   <command>
 *   ```
 *   one command per block, max 1 per step.
 * - Supports background job tool calls:
 *   `start_job(command)`, `job_status(id)`, `job_logs(id, lines)`, `stop_job(id)`
 *   both in fenced `sh-exec` syntax and JSON / cloud function-calling schemas.
 * - Auto-detects long-running server commands (`npm start`, `npm run dev`, `node server.js`,
 *   `python -m http.server`, `flask run`, `vite`, `next dev`) and marks `runsInBackground = true`.
 */
object ToolCallParser {

    /**
     * GBNF grammar for local GGUF inference so output is constrained to either plain text
     * or a single `sh-exec` fenced command block (including `start_job`, `job_status`, `job_logs`, `stop_job`).
     */
    const val LOCAL_SH_EXEC_GBNF_GRAMMAR: String = """
root ::= sh-exec-block | plain-text
sh-exec-block ::= "```sh-exec\n" (job-action | command-line) "\n```"
job-action ::= ("start_job " command-line) | ("job_status " job-id) | ("job_logs " job-id (" " [0-9]+)?) | ("stop_job " job-id)
job-id ::= [a-zA-Z0-9_-]+
command-line ::= [^\n\r`]+ ("\\\n" [^\n\r`]+)*
plain-text ::= ([^`] | "`" [^`] | "``" [^`])+
"""

    val LOCAL_STOP_SEQUENCES: List<String> = listOf(
        "\n```\n\n",
        "</nexus_tool>",
        "<|im_end|>",
        "<|eot_id|>"
    )

    private val FENCED_TOOL_REGEX = Regex(
        pattern = "```(sh-exec|bash|sh|shell)(?:\\s+title=\"([^\"]*)\")?\\s*\\r?\\n([\\s\\S]*?)```",
        option = RegexOption.IGNORE_CASE
    )

    private val NEXUS_FILE_TAG_REGEX = Regex(
        pattern = "<nexus:file\\s+path=\"([^\"]+)\"(?:\\s+title=\"([^\"]*)\")?>([\\s\\S]*?)</nexus:file>",
        option = RegexOption.IGNORE_CASE
    )

    private val UNCLOSED_SH_EXEC_REGEX = Regex(
        pattern = "```(sh-exec|bash|sh|shell)\\b(?![\\s\\S]*?```)",
        option = RegexOption.IGNORE_CASE
    )

    private val NEXUS_TOOL_TAG_REGEX = Regex(
        pattern = "<nexus_tool>([\\s\\S]*?)</nexus_tool>",
        option = RegexOption.IGNORE_CASE
    )

    private val INLINE_JSON_TOOL_REGEX = Regex(
        pattern = "\\{[\\s\\S]*?\"(?:tool|command|job_id|id)\"\\s*:[\\s\\S]*?\\}"
    )

    // Function-like or CLI-style job syntax inside sh-exec blocks:
    // start_job("npm run dev") or start_job npm run dev
    // wait_for_job("job_123", 30) or wait_for_job job_123 30
    // job_status("job_123") or job_status job_123
    // job_logs("job_123", 50) or job_logs job_123 50
    // stop_job("job_123") or stop_job job_123
    private val START_JOB_CALL_REGEX = Regex(
        """^\s*start_job\s*(?:\(\s*["']?(.+?)["']?\s*\)|\s+(.+))\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val WAIT_FOR_JOB_CALL_REGEX = Regex(
        """^\s*wait_for_job\s*(?:\(\s*["']?([a-zA-Z0-9_.-]+)["']?(?:\s*,\s*(\d+))?\s*\)|\s+([a-zA-Z0-9_.-]+)(?:\s+(\d+))?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val JOB_STATUS_CALL_REGEX = Regex(
        """^\s*job_status\s*(?:\(\s*["']?([a-zA-Z0-9_.-]+)["']?\s*\)|\s+([a-zA-Z0-9_.-]+))\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val JOB_LOGS_CALL_REGEX = Regex(
        """^\s*job_logs\s*(?:\(\s*["']?([a-zA-Z0-9_.-]+)["']?(?:\s*,\s*(\d+))?\s*\)|\s+([a-zA-Z0-9_.-]+)(?:\s+(\d+))?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STOP_JOB_CALL_REGEX = Regex(
        """^\s*stop_job\s*(?:\(\s*["']?([a-zA-Z0-9_.-]+)["']?\s*\)|\s+([a-zA-Z0-9_.-]+))\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val LOW_RISK_EXACT_OR_PREFIX_PATTERNS = listOf(
        Regex("^\\s*git\\s+(status|diff|log|show|branch(\\s+--list|\\s+-a|\\s+-r)?)\\b", RegexOption.IGNORE_CASE),
        Regex("^\\s*node\\s+(--version|-v)\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*python3?\\s+(--version|-V)\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*npm\\s+(ls|list|--version|-v)\\b", RegexOption.IGNORE_CASE),
        Regex("^\\s*pip3?\\s+(--version|-V|list|show)\\b", RegexOption.IGNORE_CASE),
        Regex("^\\s*(command\\s+-v|which)\\s+[a-zA-Z0-9_.-]+\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*(job_status|job_logs|stop_job)\\b", RegexOption.IGNORE_CASE)
    )

    private val CREDENTIAL_OR_ENV_READ_REGEX = Regex(
        """(\b(cat|less|more|head|tail|grep|awk|sed|cp|scp|curl|wget)\b[^\n]*(\.env\b|\.npmrc\b|\.pypirc\b|\.netrc\b|\.git-credentials\b|id_rsa\b|id_ed25519\b|credentials\.json\b|google-services\.json\b|/data/data/[^/\s]+/shared_prefs))|(^\s*(printenv|env|set|export\s+-p)\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val HIGH_RISK_TERMUX_CONFIG_OR_PREFIX_REGEX = Regex(
        """(?:~/|\${'$'}HOME/|/data/data/com\.termux/files/home/)(?:\.bashrc\b|\.profile\b|\.bash_profile\b|\.zshrc\b|\.termux\b)|(?:\${'$'}\bPREFIX\b|/data/data/com\.termux/files/usr\b)""",
        RegexOption.IGNORE_CASE
    )

    private val HIGH_RISK_PATTERNS = listOf(
        Regex("\\brm\\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r|--recursive)\\b", RegexOption.IGNORE_CASE),
        Regex("\\brm\\s+-rf\\s+/", RegexOption.IGNORE_CASE),
        Regex("\\bsudo\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsu\\s+-?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdd\\s+if=", RegexOption.IGNORE_CASE),
        Regex("\\bmkfs\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchmod\\s+(-R|--recursive)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchmod\\s+(-R\\s+)?777\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchmod\\s+\\+x\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchown\\s+-R\\b", RegexOption.IGNORE_CASE),
        Regex("\\bkill\\s+-9\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(reboot|shutdown|poweroff)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(curl|wget)\\b[^\\n]*\\|\\s*(sh|bash|mksh|zsh|python3?|node)\\b", RegexOption.IGNORE_CASE),
        Regex(">\\s*/dev/(sd|block|null|zero|mem)", RegexOption.IGNORE_CASE),
        Regex(":\\(\\)\\s*\\{\\s*:\\|:\\s*&\\s*\\}\\s*;\\s*:"),
        Regex("\\b(pkg|apt|apt-get)\\s+(install|i|update|upgrade|dist-upgrade|remove|uninstall|purge)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnpm\\s+(?:install|i|add)\\b[^\\n]*\\s(-g|--global)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnpm\\s+(-g|--global)\\s+(?:install|i|add)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+push\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+reset\\s+--hard\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+clean\\s+-[a-zA-Z]*f", RegexOption.IGNORE_CASE),
        CREDENTIAL_OR_ENV_READ_REGEX,
        HIGH_RISK_TERMUX_CONFIG_OR_PREFIX_REGEX
    )

    private val VENV_PIP_INSTALL_REGEX = Regex(
        """(\b(?:\.?venv|env)/bin/(?:pip3?|python3?\s+-m\s+pip)\s+install\b)|(\b(?:source|\.)\s+[^\n]*activate\b[^\n]*&&[^\n]*\b(?:pip3?|python3?\s+-m\s+pip)\s+install\b)|(\bVIRTUAL_ENV=[^\s]+\s+(?:pip3?|python3?\s+-m\s+pip)\s+install\b)""",
        RegexOption.IGNORE_CASE
    )

    private val RAW_PIP_INSTALL_REGEX = Regex(
        """\b(?:pip3?|python3?\s+-m\s+pip)\s+install\b""",
        RegexOption.IGNORE_CASE
    )

    private val MEDIUM_RISK_PATTERNS = listOf(
        Regex("\\brm\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmv\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchmod\\b", RegexOption.IGNORE_CASE),
        Regex("\\bchown\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(curl|wget|nc|netcat|ssh|scp)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(npm|pnpm|yarn)\\s+(install|i|ci|add|remove|uninstall|update|run|start|build|test|init)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpython3?\\s+-m\\s+venv\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(pm|am)\\s+(install|uninstall|remove|clear)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgit\\s+(init|add|clone|pull|commit|fetch|merge|rebase|reset|clean|checkout\\s+--|branch\\s+-D|stash)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(node|python3?|npx|tsx|flask|vite|next|uvicorn|gunicorn)\\b(?!\\s+(--version|-v|-V)\\s*$)", RegexOption.IGNORE_CASE),
        Regex("\\bsed\\s+-i\\b", RegexOption.IGNORE_CASE),
        Regex("\\bkill(all)?\\b", RegexOption.IGNORE_CASE)
    )

    data class CommandSafetyAnalysis(
        val riskLevel: CommandRiskLevel = CommandRiskLevel.LOW,
        val packageNames: List<String> = emptyList(),
        val registryHosts: List<String> = emptyList(),
        val hasInstallScriptsWarning: Boolean = false,
        val touchesOutsideWorkspace: Boolean = false,
        val readsCredentialOrEnvFiles: Boolean = false,
        val warningNotes: List<String> = emptyList(),
        val runsInBackground: Boolean = false
    ) {
        val packages: List<String> get() = packageNames
        val runsInstallScripts: Boolean get() = hasInstallScriptsWarning
        val readsCredentials: Boolean get() = readsCredentialOrEnvFiles
    }

    fun analyzeCommandSafety(command: String, workspaceCwd: String = ""): CommandSafetyAnalysis {
        val trimmed = command.trim()
        val unwrapped = unwrapStartJobCommandIfPresent(trimmed)
        val packages = mutableListOf<String>()
        val registries = mutableSetOf<String>()
        var hasInstallScriptWarn = false
        val warnings = mutableListOf<String>()

        // Detect pkg / apt packages
        val pkgMatch = Regex("\\b(?:pkg|apt|apt-get)\\s+(?:install|i)\\s+([^;&|\\n]+)", RegexOption.IGNORE_CASE).findAll(unwrapped)
        for (m in pkgMatch) {
            val tokens = m.groupValues[1].trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() && !it.startsWith("-") }
            packages.addAll(tokens)
            registries.add("packages.termux.dev")
        }

        // Detect npm packages
        val npmMatch = Regex("\\b(?:npm|pnpm|yarn)\\s+(?:install|i|add|ci)\\b([^;&|\\n]*)", RegexOption.IGNORE_CASE).findAll(unwrapped)
        for (m in npmMatch) {
            val args = m.groupValues[1].trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() && !it.startsWith("-") }
            if (args.isEmpty()) {
                packages.add("package.json dependencies")
            } else {
                packages.addAll(args)
            }
            registries.add("registry.npmjs.org")
            if (!unwrapped.contains("--ignore-scripts")) {
                hasInstallScriptWarn = true
                warnings.add("Runs package preinstall/postinstall scripts (add --ignore-scripts to block)")
            }
        }

        // Detect pip packages
        val pipMatch = Regex("\\bpip3?\\s+install\\s+([^;&|\\n]+)", RegexOption.IGNORE_CASE).findAll(unwrapped)
        for (m in pipMatch) {
            val args = m.groupValues[1].trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() && !it.startsWith("-") }
            packages.addAll(args)
            registries.add("pypi.org")
            hasInstallScriptWarn = true
            warnings.add("May execute setup.py / build hooks from PyPI distributions")
        }

        // Extract explicit registry or URL hosts
        val urlHosts = Regex("https?://([a-zA-Z0-9.-]+)").findAll(unwrapped)
        for (u in urlHosts) {
            registries.add(u.groupValues[1])
        }

        val readsCreds = CREDENTIAL_OR_ENV_READ_REGEX.containsMatchIn(unwrapped)
        if (readsCreds) {
            warnings.add("Reads environment variables or sensitive credential/config files")
        }

        if (HIGH_RISK_TERMUX_CONFIG_OR_PREFIX_REGEX.containsMatchIn(unwrapped)) {
            warnings.add("Targets protected Termux config (~/.bashrc, ~/.profile, ~/.termux/) or \$PREFIX (HIGH risk)")
        }

        val touchesOutside = touchesOutsideWorkspace(unwrapped, workspaceCwd)
        if (touchesOutside) {
            warnings.add("Touches filesystem paths outside the active project workspace (best-effort jail)")
        }

        if (Regex("\\b(curl|wget)\\b[^\\n]*\\|\\s*(sh|bash|mksh|zsh|python3?|node)\\b", RegexOption.IGNORE_CASE).containsMatchIn(unwrapped)) {
            hasInstallScriptWarn = true
            warnings.add("Pipes remote network content directly into an interpreter")
        }

        val isBg = START_JOB_CALL_REGEX.matches(trimmed) || ServerLogAnalyzer.shouldRunAsBackgroundJob(unwrapped)
        val baseRisk = classifyRisk(trimmed, workspaceCwd)
        return CommandSafetyAnalysis(
            riskLevel = baseRisk,
            packageNames = packages.distinct(),
            registryHosts = registries.toList(),
            hasInstallScriptsWarning = hasInstallScriptWarn,
            touchesOutsideWorkspace = touchesOutside,
            readsCredentialOrEnvFiles = readsCreds,
            warningNotes = warnings.distinct(),
            runsInBackground = isBg
        )
    }

    private fun unwrapStartJobCommandIfPresent(command: String): String {
        val m = START_JOB_CALL_REGEX.find(command.trim()) ?: return command.trim()
        return (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
    }

    private val UPWARD_PATH_TRAVERSAL_REGEX = Regex("""(?:^|[\s'"=/])\.\.(?:[/\s'"]|$)""")

    private fun touchesOutsideWorkspace(command: String, workspaceCwd: String): Boolean {
        val trimmed = command.trim()
        if (UPWARD_PATH_TRAVERSAL_REGEX.containsMatchIn(trimmed)) return true
        val cleanCwd = workspaceCwd.trim().removeSuffix("/")
        val cdMatches = Regex("""\bcd\s+([^\s;|&'"]+)""").findAll(trimmed).map { it.groupValues[1].trim() }.toList()
        for (cdTarget in cdMatches) {
            if (cdTarget == "~" || cdTarget.startsWith("~/") || cdTarget.startsWith("\$HOME")) {
                if (cleanCwd.isEmpty() || !cleanCwd.startsWith(WorkspacePaths.TERMUX_HOME_DIR)) {
                    return true
                }
            }
            if (cdTarget.startsWith("/") && cleanCwd.isNotEmpty() && !cdTarget.startsWith(cleanCwd)) {
                return true
            }
        }
        val absolutePaths = Regex("""(?:^|\s)(/(?:sdcard|storage/emulated/0|system|data/local|etc|proc/sys|root)[^\s;|&'"]*)""")
            .findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .toList()
        if (absolutePaths.isEmpty()) return false
        return absolutePaths.any { path ->
            cleanCwd.isEmpty() || !path.startsWith(cleanCwd)
        }
    }

    /**
     * Classifies a shell command or job action into LOW, MEDIUM, or HIGH risk.
     * - `stop_job`, `job_status`, and `job_logs` are always LOW risk.
     * - `start_job(cmd)` follows the risk classification of `cmd`.
     */
    fun classifyRisk(command: String, workspaceCwd: String = ""): CommandRiskLevel {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return CommandRiskLevel.LOW

        if (JOB_STATUS_CALL_REGEX.matches(trimmed) ||
            JOB_LOGS_CALL_REGEX.matches(trimmed) ||
            STOP_JOB_CALL_REGEX.matches(trimmed)
        ) {
            return CommandRiskLevel.LOW
        }

        val effectiveCmd = unwrapStartJobCommandIfPresent(trimmed)

        for (pattern in HIGH_RISK_PATTERNS) {
            if (pattern.containsMatchIn(effectiveCmd)) {
                return CommandRiskLevel.HIGH
            }
        }

        if (RAW_PIP_INSTALL_REGEX.containsMatchIn(effectiveCmd)) {
            return if (VENV_PIP_INSTALL_REGEX.containsMatchIn(effectiveCmd)) {
                CommandRiskLevel.MEDIUM
            } else {
                CommandRiskLevel.HIGH
            }
        }

        if (touchesOutsideWorkspace(effectiveCmd, workspaceCwd)) {
            return CommandRiskLevel.HIGH
        }

        if (!effectiveCmd.contains("&&") && !effectiveCmd.contains(";") && !effectiveCmd.contains("|")) {
            for (lowPattern in LOW_RISK_EXACT_OR_PREFIX_PATTERNS) {
                if (lowPattern.containsMatchIn(effectiveCmd)) {
                    return CommandRiskLevel.LOW
                }
            }
        }

        if (VENV_PIP_INSTALL_REGEX.containsMatchIn(effectiveCmd)) {
            return CommandRiskLevel.MEDIUM
        }

        for (pattern in MEDIUM_RISK_PATTERNS) {
            if (pattern.containsMatchIn(effectiveCmd)) {
                return CommandRiskLevel.MEDIUM
            }
        }

        if (hasUnescapedNewlines(effectiveCmd)) {
            return CommandRiskLevel.MEDIUM
        }

        return CommandRiskLevel.LOW
    }

    /**
     * Activity-row verb labeler for git, npm, pip, pkg, python, node, background jobs, and shell commands.
     */
    fun activityVerbForCommand(command: String): String {
        val trimmed = command.trim()
        return when {
            START_JOB_CALL_REGEX.matches(trimmed) || ServerLogAnalyzer.shouldRunAsBackgroundJob(trimmed) ->
                "Starting background job…"
            JOB_STATUS_CALL_REGEX.matches(trimmed) ->
                "Checking job status…"
            JOB_LOGS_CALL_REGEX.matches(trimmed) ->
                "Tailing job logs…"
            STOP_JOB_CALL_REGEX.matches(trimmed) ->
                "Stopping job…"
            Regex("(\\b(pkg|apt|apt-get)\\s+(install|i|update|upgrade)\\b)|(\\b(npm|pnpm|yarn)\\s+(install|i|ci|add)\\b)|(\\bpip3?\\s+install\\b)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ->
                "Installing…"
            Regex("^\\s*git\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ->
                "Running git…"
            Regex("^\\s*python3?\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ->
                "Executing Python…"
            Regex("^\\s*(node|npx|npm\\s+run|npm\\s+start|npm\\s+test)\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ->
                "Running Node…"
            else -> "Executing shell…"
        }
    }

    /**
     * Checks whether a command contains unescaped newlines (more than 1 logical line without trailing `\`).
     */
    fun hasUnescapedNewlines(command: String): Boolean {
        val normalized = command.trim().replace("\r\n", "\n")
        if (!normalized.contains('\n')) return false

        val lines = normalized.split('\n')
        for (i in 0 until lines.size - 1) {
            val line = lines[i].trimEnd()
            if (!line.endsWith("\\")) {
                return true
            }
        }
        return false
    }

    private fun normalizeEscapedLineContinuations(command: String): String {
        return command.trim()
            .replace("\r\n", "\n")
            .replace(Regex("\\s*\\\\\\n\\s*"), " ")
            .trim()
    }

    /**
     * Formats a normalized [ToolCall] into a canonical `sh-exec` representation
     * so cloud providers' native function calls and text outputs share one unified pipeline.
     */
    fun formatNormalizedToolCallBlock(toolCall: ToolCall, explanation: String = ""): String {
        val prefix = explanation.trim().let { if (it.isNotEmpty()) "$it\n\n" else "" }
        val blockBody = when (toolCall.actionKind) {
            ToolActionKind.START_JOB -> "```sh-exec\nstart_job ${toolCall.command.trim()}\n```"
            ToolActionKind.WAIT_FOR_JOB -> "```sh-exec\nwait_for_job ${toolCall.jobId.trim()} ${toolCall.waitMaxSeconds}\n```"
            ToolActionKind.JOB_STATUS -> "```sh-exec\njob_status ${toolCall.jobId.trim()}\n```"
            ToolActionKind.JOB_LOGS -> "```sh-exec\njob_logs ${toolCall.jobId.trim()} ${toolCall.logLinesCount}\n```"
            ToolActionKind.STOP_JOB -> "```sh-exec\nstop_job ${toolCall.jobId.trim()}\n```"
            ToolActionKind.WRITE_FILE -> "<nexus:file path=\"${toolCall.targetFilePath.orEmpty()}\">\n${toolCall.targetFileContent.orEmpty()}\n</nexus:file>"
            ToolActionKind.SHELL -> "```sh-exec\n${toolCall.command.trim()}\n```"
        }
        return buildString {
            append(prefix)
            append(blockBody)
        }
    }

    /**
     * Parses a single command line or job expression into a [ToolCall].
     */
    fun buildToolCallFromCommandString(
        rawCommand: String,
        source: String,
        defaultTimeoutSec: Long = 10L,
        reasonOverride: String? = null,
        approvedMultiLine: Boolean = false,
        explicitTitle: String? = null
    ): ToolCall {
        val trimmed = rawCommand.trim()

        START_JOB_CALL_REGEX.find(trimmed)?.let { m ->
            val innerCmd = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
            val risk = classifyRisk(innerCmd)
            return ToolCall(
                command = innerCmd,
                reason = reasonOverride ?: "Start background job ($source)",
                timeoutSec = defaultTimeoutSec,
                source = source,
                riskLevel = risk,
                approvedMultiLine = approvedMultiLine,
                actionKind = ToolActionKind.START_JOB,
                runsInBackground = true
            )
        }

        JOB_STATUS_CALL_REGEX.find(trimmed)?.let { m ->
            val jobId = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
            return ToolCall(
                command = "job_status $jobId",
                reason = reasonOverride ?: "Check status of background job $jobId",
                timeoutSec = 10L,
                source = source,
                riskLevel = CommandRiskLevel.LOW,
                actionKind = ToolActionKind.JOB_STATUS,
                jobId = jobId,
                runsInBackground = false
            )
        }

        WAIT_FOR_JOB_CALL_REGEX.find(trimmed)?.let { m ->
            val jobId = (m.groupValues[1].ifBlank { m.groupValues[3] }).trim()
            val secStr = m.groupValues[2].ifBlank { m.groupValues[4] }
            val waitSec = secStr.toLongOrNull()?.coerceIn(1L, 120L) ?: 30L
            return ToolCall(
                command = "wait_for_job $jobId $waitSec",
                reason = reasonOverride ?: "Wait up to ${waitSec}s for background job $jobId",
                timeoutSec = waitSec + 5L,
                source = source,
                riskLevel = CommandRiskLevel.LOW,
                actionKind = ToolActionKind.WAIT_FOR_JOB,
                jobId = jobId,
                waitMaxSeconds = waitSec,
                runsInBackground = false
            )
        }

        JOB_LOGS_CALL_REGEX.find(trimmed)?.let { m ->
            val jobId = (m.groupValues[1].ifBlank { m.groupValues[3] }).trim()
            val linesStr = m.groupValues[2].ifBlank { m.groupValues[4] }
            val linesCount = linesStr.toIntOrNull()?.coerceIn(1, 500) ?: 50
            return ToolCall(
                command = "job_logs $jobId $linesCount",
                reason = reasonOverride ?: "Fetch last $linesCount log lines for job $jobId",
                timeoutSec = 10L,
                source = source,
                riskLevel = CommandRiskLevel.LOW,
                actionKind = ToolActionKind.JOB_LOGS,
                jobId = jobId,
                logLinesCount = linesCount,
                runsInBackground = false
            )
        }

        STOP_JOB_CALL_REGEX.find(trimmed)?.let { m ->
            val jobId = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
            return ToolCall(
                command = "stop_job $jobId",
                reason = reasonOverride ?: "Stop background job $jobId",
                timeoutSec = 10L,
                source = source,
                riskLevel = CommandRiskLevel.LOW,
                actionKind = ToolActionKind.STOP_JOB,
                jobId = jobId,
                runsInBackground = false
            )
        }

        val autoBackground = ServerLogAnalyzer.shouldRunAsBackgroundJob(trimmed)
        val risk = classifyRisk(trimmed)
        return ToolCall(
            command = trimmed,
            reason = reasonOverride ?: if (autoBackground) {
                "Runs in background ($source)"
            } else {
                "Execute shell command ($source)"
            },
            timeoutSec = defaultTimeoutSec,
            source = source,
            riskLevel = risk,
            approvedMultiLine = approvedMultiLine,
            actionKind = if (autoBackground) ToolActionKind.START_JOB else ToolActionKind.SHELL,
            runsInBackground = autoBackground,
            explicitTitle = explicitTitle
        )
    }

    /**
     * Parses raw model text for a tool call.
     */
    fun parse(
        rawText: String,
        defaultTimeoutSec: Long = 10L,
        allowMultiLineIfApproved: Boolean = false
    ): ToolCallParseResult {
        val trimmedText = ThinkingTagParser.stripThinkingForHistoryAndTools(rawText).trim()
        if (trimmedText.isEmpty()) {
            return ToolCallParseResult.NoToolCall
        }

        // 0. Check `<nexus:file path="..." title="...">...</nexus:file>` tags
        val fileTagMatches = NEXUS_FILE_TAG_REGEX.findAll(trimmedText).toList()
        if (fileTagMatches.isNotEmpty()) {
            if (fileTagMatches.size > 1) {
                return ToolCallParseResult.ParseError(
                    message = "Multiple file creation blocks emitted in a single step; max 1 file operation per step is permitted.",
                    rawSnippet = fileTagMatches.first().value.take(160)
                )
            }
            val match = fileTagMatches.first()
            val path = match.groupValues[1].trim()
            val titleAttr = match.groupValues[2].takeIf { it.isNotBlank() }
            val fileContent = match.groupValues[3]
            val cleaned = trimmedText.replace(match.value, "").trim()
            val safePath = path.replace("'", "'\\''")
            val catCommand = "cat << 'EOF' > '$safePath'\n$fileContent\nEOF"
            val tc = ToolCall(
                command = catCommand,
                reason = "Create or update file $path",
                timeoutSec = defaultTimeoutSec,
                source = "nexus:file",
                riskLevel = classifyRisk(catCommand),
                approvedMultiLine = true,
                actionKind = ToolActionKind.WRITE_FILE,
                explicitTitle = titleAttr ?: "Create ${java.io.File(path).name.ifBlank { path }}",
                targetFilePath = path,
                targetFileContent = fileContent
            )
            return ToolCallParseResult.Success(tc, cleaned)
        }

        // 1. Check `<nexus_tool>...</nexus_tool>` tags first
        val nexusTagMatches = NEXUS_TOOL_TAG_REGEX.findAll(trimmedText).toList()
        if (nexusTagMatches.isNotEmpty()) {
            if (nexusTagMatches.size > 1) {
                return ToolCallParseResult.ParseError(
                    message = "Multiple tool calls emitted in a single step (${nexusTagMatches.size}); max 1 command per step is allowed.",
                    rawSnippet = nexusTagMatches.first().value.take(160)
                )
            }
            val match = nexusTagMatches.first()
            val body = match.groupValues[1].trim()
            val jsonResult = parseJsonToolPayload(
                jsonText = body,
                source = "nexus_tool",
                defaultTimeoutSec = defaultTimeoutSec,
                allowMultiLineIfApproved = allowMultiLineIfApproved
            )
            return when (jsonResult) {
                is ToolCallParseResult.Success -> {
                    val cleaned = trimmedText.replace(match.value, "").trim()
                    jsonResult.copy(
                        cleanedAssistantText = cleaned.ifBlank {
                            formatNormalizedToolCallBlock(jsonResult.toolCall)
                        }
                    )
                }
                else -> jsonResult
            }
        }

        // 2. Check for unclosed ```sh-exec / ```bash / ```sh / ```shell fence
        if (UNCLOSED_SH_EXEC_REGEX.containsMatchIn(trimmedText) && !FENCED_TOOL_REGEX.containsMatchIn(trimmedText)) {
            return ToolCallParseResult.ParseError(
                message = "Malformed tool call: unclosed code fence in model output.",
                rawSnippet = trimmedText.take(160)
            )
        }

        // 3. Check fenced code blocks: ```sh-exec, ```bash, ```sh, ```shell
        val fenceMatches = FENCED_TOOL_REGEX.findAll(trimmedText).toList()
        if (fenceMatches.isNotEmpty()) {
            val shExecMatches = fenceMatches.filter {
                it.groupValues[1].equals("sh-exec", ignoreCase = true)
            }
            if (shExecMatches.size > 1 || (shExecMatches.isEmpty() && fenceMatches.size > 1)) {
                return ToolCallParseResult.ParseError(
                    message = "Multiple shell command blocks emitted in one step; only 1 command block per step is permitted.",
                    rawSnippet = fenceMatches.first().value.take(160)
                )
            }

            val chosenMatch = shExecMatches.firstOrNull() ?: fenceMatches.first()
            val fenceLang = chosenMatch.groupValues[1].lowercase()
            val explicitTitle = chosenMatch.groupValues[2].takeIf { it.isNotBlank() }
            val rawCommandBody = chosenMatch.groupValues[3]

            val strippedBody = rawCommandBody
                .trim('\r', '\n', ' ', '\t')
                .lines()
                .dropWhile { it.trim().startsWith("#") }
                .joinToString("\n")
                .trim()

            if (strippedBody.isEmpty()) {
                return ToolCallParseResult.ParseError(
                    message = "Empty shell command inside ```$fenceLang block.",
                    rawSnippet = chosenMatch.value.take(160)
                )
            }

            if (strippedBody.startsWith("{") && strippedBody.endsWith("}")) {
                return parseJsonToolPayload(
                    jsonText = strippedBody,
                    source = fenceLang,
                    defaultTimeoutSec = defaultTimeoutSec,
                    allowMultiLineIfApproved = allowMultiLineIfApproved,
                    fullAssistantText = trimmedText
                )
            }

            if (hasUnescapedNewlines(strippedBody) && !allowMultiLineIfApproved) {
                return ToolCallParseResult.ParseError(
                    message = "Rejected multi-line chained command containing unescaped newlines.",
                    rawSnippet = strippedBody.take(160)
                )
            }

            val finalCommand = if (hasUnescapedNewlines(strippedBody)) {
                strippedBody
            } else {
                normalizeEscapedLineContinuations(strippedBody)
            }

            val toolCall = buildToolCallFromCommandString(
                rawCommand = finalCommand,
                source = fenceLang,
                defaultTimeoutSec = defaultTimeoutSec,
                approvedMultiLine = allowMultiLineIfApproved && hasUnescapedNewlines(strippedBody),
                explicitTitle = explicitTitle
            )
            val cleaned = trimmedText.replace(chosenMatch.value, "").trim()
            return ToolCallParseResult.Success(
                toolCall = toolCall,
                cleanedAssistantText = cleaned
            )
        }

        // 4. Fallback: JSON {"tool":"..."} or {"command":"..."} in plain text
        val jsonCandidate = INLINE_JSON_TOOL_REGEX.find(trimmedText)
        if (jsonCandidate != null) {
            val candidateStr = jsonCandidate.value.trim()
            return parseJsonToolPayload(
                jsonText = candidateStr,
                source = "json-fallback",
                defaultTimeoutSec = defaultTimeoutSec,
                allowMultiLineIfApproved = allowMultiLineIfApproved,
                fullAssistantText = trimmedText
            )
        }

        if (trimmedText.contains("\"tool\"") &&
            (trimmedText.contains("\"shell\"") || trimmedText.contains("\"start_job\"") || trimmedText.contains("\"job_status\"") || trimmedText.contains("\"job_logs\"") || trimmedText.contains("\"stop_job\"")) &&
            !trimmedText.endsWith("}")
        ) {
            return ToolCallParseResult.ParseError(
                message = "Malformed JSON tool call in model response.",
                rawSnippet = trimmedText.take(160)
            )
        }

        return ToolCallParseResult.NoToolCall
    }

    fun parseJsonToolPayload(
        jsonText: String,
        source: String,
        defaultTimeoutSec: Long,
        allowMultiLineIfApproved: Boolean,
        fullAssistantText: String = ""
    ): ToolCallParseResult {
        return try {
            val json = JSONObject(jsonText)
            val toolName = json.optString("tool", json.optString("name", "shell")).trim().lowercase()

            when (toolName) {
                "job_status" -> {
                    val id = json.optString("id", json.optString("job_id", "")).trim()
                    if (id.isEmpty()) {
                        return ToolCallParseResult.ParseError(
                            message = "job_status tool call is missing required 'id' field.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    val tc = ToolCall(
                        command = "job_status $id",
                        reason = "Check status of background job $id",
                        timeoutSec = 10L,
                        source = source,
                        riskLevel = CommandRiskLevel.LOW,
                        actionKind = ToolActionKind.JOB_STATUS,
                        jobId = id,
                        runsInBackground = false
                    )
                    val cleanedText = fullAssistantText.replace(jsonText, "").trim().ifBlank {
                        formatNormalizedToolCallBlock(tc)
                    }
                    return ToolCallParseResult.Success(tc, cleanedText)
                }

                "job_logs" -> {
                    val id = json.optString("id", json.optString("job_id", "")).trim()
                    if (id.isEmpty()) {
                        return ToolCallParseResult.ParseError(
                            message = "job_logs tool call is missing required 'id' field.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    val lines = json.optInt("lines", 50).coerceIn(1, 500)
                    val tc = ToolCall(
                        command = "job_logs $id $lines",
                        reason = "Fetch last $lines log lines for job $id",
                        timeoutSec = 10L,
                        source = source,
                        riskLevel = CommandRiskLevel.LOW,
                        actionKind = ToolActionKind.JOB_LOGS,
                        jobId = id,
                        logLinesCount = lines,
                        runsInBackground = false
                    )
                    val cleanedText = fullAssistantText.replace(jsonText, "").trim().ifBlank {
                        formatNormalizedToolCallBlock(tc)
                    }
                    return ToolCallParseResult.Success(tc, cleanedText)
                }

                "stop_job" -> {
                    val id = json.optString("id", json.optString("job_id", "")).trim()
                    if (id.isEmpty()) {
                        return ToolCallParseResult.ParseError(
                            message = "stop_job tool call is missing required 'id' field.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    val tc = ToolCall(
                        command = "stop_job $id",
                        reason = "Stop background job $id",
                        timeoutSec = 10L,
                        source = source,
                        riskLevel = CommandRiskLevel.LOW,
                        actionKind = ToolActionKind.STOP_JOB,
                        jobId = id,
                        runsInBackground = false
                    )
                    val cleanedText = fullAssistantText.replace(jsonText, "").trim().ifBlank {
                        formatNormalizedToolCallBlock(tc)
                    }
                    return ToolCallParseResult.Success(tc, cleanedText)
                }

                "start_job" -> {
                    val rawCommand = json.optString("command", "").trim()
                    if (rawCommand.isEmpty()) {
                        return ToolCallParseResult.ParseError(
                            message = "start_job tool call is missing required 'command' field.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    if (hasUnescapedNewlines(rawCommand) && !allowMultiLineIfApproved) {
                        return ToolCallParseResult.ParseError(
                            message = "Rejected multi-line chained command containing unescaped newlines in start_job call.",
                            rawSnippet = rawCommand.take(160)
                        )
                    }
                    val finalCommand = if (hasUnescapedNewlines(rawCommand)) {
                        rawCommand
                    } else {
                        normalizeEscapedLineContinuations(rawCommand)
                    }
                    val reason = json.optString("reason", "Runs in background").trim().ifEmpty { "Runs in background" }
                    val risk = classifyRisk(finalCommand)
                    val tc = ToolCall(
                        command = finalCommand,
                        reason = reason,
                        timeoutSec = 10L,
                        source = source,
                        riskLevel = risk,
                        approvedMultiLine = allowMultiLineIfApproved && hasUnescapedNewlines(rawCommand),
                        actionKind = ToolActionKind.START_JOB,
                        runsInBackground = true
                    )
                    val cleanedText = fullAssistantText.replace(jsonText, "").trim().ifBlank {
                        formatNormalizedToolCallBlock(tc)
                    }
                    return ToolCallParseResult.Success(tc, cleanedText)
                }

                "", "shell", "sh-exec", "bash", "execute_shell_command" -> {
                    if (!json.has("command")) {
                        return ToolCallParseResult.ParseError(
                            message = "Tool call JSON is missing required 'command' field.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    val rawCommand = json.optString("command", "").trim()
                    if (rawCommand.isEmpty()) {
                        return ToolCallParseResult.ParseError(
                            message = "Tool call JSON contains an empty 'command'.",
                            rawSnippet = jsonText.take(160)
                        )
                    }
                    if (hasUnescapedNewlines(rawCommand) && !allowMultiLineIfApproved) {
                        return ToolCallParseResult.ParseError(
                            message = "Rejected multi-line chained command containing unescaped newlines in JSON tool call.",
                            rawSnippet = rawCommand.take(160)
                        )
                    }
                    val finalCommand = if (hasUnescapedNewlines(rawCommand)) {
                        rawCommand
                    } else {
                        normalizeEscapedLineContinuations(rawCommand)
                    }
                    val reason = json.optString("reason", "Execute shell command").trim().ifEmpty { "Execute shell command" }
                    val timeoutSec = json.optLong("timeoutSec", defaultTimeoutSec).coerceIn(2L, 600L)
                    val tc = buildToolCallFromCommandString(
                        rawCommand = finalCommand,
                        source = source,
                        defaultTimeoutSec = timeoutSec,
                        reasonOverride = reason,
                        approvedMultiLine = allowMultiLineIfApproved && hasUnescapedNewlines(rawCommand)
                    )
                    val cleanedText = fullAssistantText.replace(jsonText, "").trim().ifBlank {
                        formatNormalizedToolCallBlock(tc)
                    }
                    return ToolCallParseResult.Success(tc, cleanedText)
                }

                else -> {
                    return ToolCallParseResult.ParseError(
                        message = "Unsupported tool '$toolName'. Supported tools: 'shell', 'start_job', 'job_status', 'job_logs', 'stop_job'.",
                        rawSnippet = jsonText.take(160)
                    )
                }
            }
        } catch (e: Exception) {
            ToolCallParseResult.ParseError(
                message = "Invalid tool call JSON: ${e.message ?: "parse error"}",
                rawSnippet = jsonText.take(160)
            )
        }
    }

    /**
     * Formats shell execution output to be fed back to the model as the next user turn.
     * Always redacts secrets via [NexusToolAndRedactor.redactSecrets] and truncates to [maxChars] (default 2000).
     */
    fun formatToolOutputForModelFeedback(
        command: String,
        cwd: String,
        exitCode: Int,
        rawOutput: String,
        extraSecrets: List<String> = emptyList(),
        maxChars: Int = 2000
    ): String {
        val redactedCmd = NexusToolAndRedactor.redactSecrets(command, extraSecrets).text
        val redactedOut = NexusToolAndRedactor.redactSecrets(rawOutput, extraSecrets).text.trim()
        val body = buildString {
            append("[TOOL_EXECUTION_RESULT]\n")
            append("cwd: $cwd\n")
            append("command: $redactedCmd\n")
            append("exit_code: $exitCode\n")
            append("output:\n")
            append(if (redactedOut.isEmpty()) "(no stdout/stderr output)" else redactedOut)
        }
        return if (body.length > maxChars) {
            val suffix = "\n...[truncated]"
            body.take((maxChars - suffix.length).coerceAtLeast(0)) + suffix
        } else {
            body
        }
    }
}
