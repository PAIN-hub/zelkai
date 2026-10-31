package com.example.domain

import com.example.data.model.NexusUserSettings
import com.example.data.model.TranscriptEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object NexusToolAndRedactor {
    const val TOOL_NAME = "execute_shell_command"
    const val MIN_TIMEOUT_SEC = 1L
    const val MAX_TIMEOUT_SEC = 600L
    const val DEFAULT_TIMEOUT_SEC = 10L
    const val MAX_OUTPUT_CHARS = 4000

    val SYSTEM_PROMPT = NexusUserSettings.DEFAULT_SYSTEM_PROMPT

    data class PersonaPreset(
        val id: String,
        val label: String,
        val badge: String,
        val recommendedModel: String,
        val prompt: String
    )

    val PERSONA_PRESETS = listOf(
        PersonaPreset(
            id = "linux_agent",
            label = "Linux Shell Agent (Gen-v5)",
            badge = "Default v5",
            recommendedModel = "claude-sonnet-5-5",
            prompt = NexusUserSettings.DEFAULT_SYSTEM_PROMPT
        ),
        PersonaPreset(
            id = "security_auditor",
            label = "DevSecOps & Security Auditor",
            badge = "Deep Audit",
            recommendedModel = "claude-opus-5-5",
            prompt = "You are a Senior DevSecOps and Linux Security Auditor. Use execute_shell_command to inspect permissions, environment configuration, open ports, and file integrity. Always explain security implications before and after commands. Keep commands non-interactive and concise."
        ),
        PersonaPreset(
            id = "code_architect",
            label = "Systems & Code Architect",
            badge = "Reasoning",
            recommendedModel = "claude-opus-5-5",
            prompt = "You are a Staff Systems & Software Architect. Analyze project structure, diagnose complex build/runtime errors, and propose clean architectural improvements. Use execute_shell_command when inspecting files or testing scripts. Format answers in structured Markdown."
        ),
        PersonaPreset(
            id = "rapid_ops",
            label = "Rapid Diagnostics Assistant",
            badge = "Fast Lite",
            recommendedModel = "claude-haiku-5-5",
            prompt = "You are a fast, ultra-concise command-line assistant. Provide immediate shell one-liners, quick diagnostics, and brief explanations. Use execute_shell_command for rapid checks."
        )
    )

    data class PlatformProviderInfo(
        val id: String,
        val displayName: String,
        val shortBadge: String,
        val description: String,
        val defaultModel: String
    )

    /**
     * Supported AI providers (Part B #4):
     * - "Fable Intelligence API" is removed as a separate provider; Fable models appear under Anthropic.
     * - All cloud providers use the user's personal API key from Settings.
     */
    val AI_PROVIDERS = listOf(
        PlatformProviderInfo(
            id = "gemini",
            displayName = "Google Gemini",
            shortBadge = "Gemini",
            description = "Cloud · Live models fetched with your Gemini API key",
            defaultModel = ""
        ),
        PlatformProviderInfo(
            id = "anthropic",
            displayName = "Anthropic Claude",
            shortBadge = "Claude",
            description = "Cloud · Live models fetched with your Anthropic key (includes Fable seeds when offline)",
            defaultModel = "claude-sonnet-5-5"
        ),
        PlatformProviderInfo(
            id = "openai",
            displayName = "OpenAI Platform",
            shortBadge = "OpenAI",
            description = "Cloud · Live chat models fetched with your OpenAI API key",
            defaultModel = ""
        ),
        PlatformProviderInfo(
            id = "local",
            displayName = "On-Device Local LLM (GGUF)",
            shortBadge = "Local LLM",
            description = "On-Device · Download open-weights GGUF models from Hugging Face",
            defaultModel = "qwen2.5-coder-1.5b-instruct"
        )
    )

    fun normalizeProviderId(provider: String?): String {
        return when (val raw = provider?.trim()?.lowercase().orEmpty()) {
            "fable" -> "anthropic"
            "gemini", "anthropic", "openai", "local" -> raw
            else -> "gemini"
        }
    }

    /**
     * Returns `(id, displayName)` pairs where `id` and `displayName` are the exact same string
     * for cloud models (Part B #1). Gemini and OpenAI have no hardcoded seeds.
     */
    fun modelsForProvider(
        providerId: String,
        dynamicCloudModels: List<ModelOption> = emptyList()
    ): List<Pair<String, String>> {
        val norm = providerId.lowercase().trim()
        return when (norm) {
            "gemini", "openai" -> dynamicCloudModels.map { it.id to it.displayName }
            "anthropic" -> {
                val list = dynamicCloudModels.ifEmpty { ProviderModelRepository.ANTHROPIC_UNVERIFIED_SEEDS }
                list.map { opt ->
                    val label = if (opt.isUnverified) "${opt.id} (unverified)" else opt.id
                    opt.id to label
                }
            }
            "local" -> LocalLlmManager.LOCAL_LLM_CATALOG.map { spec ->
                spec.id to spec.id
            }
            else -> emptyList()
        }
    }

    fun providerDisplayLabel(providerId: String): String {
        val norm = if (providerId.equals("fable", ignoreCase = true)) "anthropic" else providerId
        return AI_PROVIDERS.find { it.id.equals(norm, ignoreCase = true) }?.shortBadge
            ?: norm.replaceFirstChar { it.uppercase() }
    }

    data class AutocompleteSuggestion(
        val text: String,
        val description: String,
        val kind: String,
        val badge: String
    )

    val COMMON_SHELL_SUGGESTIONS = listOf(
        AutocompleteSuggestion("sh ./system-check.sh", "Run workspace telemetry & system health check", "shell", "CMD"),
        AutocompleteSuggestion("ls -la", "List all files, permissions, and hidden items", "shell", "CMD"),
        AutocompleteSuggestion("cat logs/agent-audit.log", "Inspect agent audit log with automatic secret redaction", "shell", "CMD"),
        AutocompleteSuggestion("pwd && df -h", "Print working directory and storage disk usage", "shell", "CMD"),
        AutocompleteSuggestion("uname -a && id", "Show kernel architecture and active Linux user UID/GID", "shell", "CMD"),
        AutocompleteSuggestion("find . -maxdepth 2 -type f", "Find files up to 2 levels deep in working directory", "shell", "CMD"),
        AutocompleteSuggestion("ps -A | head -n 20", "List active processes in container", "shell", "CMD"),
        AutocompleteSuggestion("grep -rn \"ERROR\\|WARN\" logs/", "Search logs directory for warnings or errors", "shell", "CMD"),
        AutocompleteSuggestion("cat README.md", "Read workspace README documentation", "shell", "CMD"),
        AutocompleteSuggestion("env | sort", "Display sorted environment variables (secrets auto-masked)", "shell", "CMD"),
        AutocompleteSuggestion("du -sh * 2>/dev/null", "Summarize directory sizes in current workspace", "shell", "CMD"),
        AutocompleteSuggestion("stat .", "Inspect filesystem metadata of current directory", "shell", "CMD")
    )

    val COMMON_AGENT_DIRECTIVES = listOf(
        AutocompleteSuggestion("/quick create a folder named web", "Quick Command: mkdir -p web in current workspace", "directive", "QUICK"),
        AutocompleteSuggestion("/quick list files in workspace", "Quick Command: ls -la in current workspace", "directive", "QUICK"),
        AutocompleteSuggestion("Run sh ./system-check.sh and summarize the system health.", "Telemetry & health report", "directive", "AI"),
        AutocompleteSuggestion("Inspect logs/agent-audit.log and summarize any warnings or masked tokens.", "Log & secret redaction audit", "directive", "AI"),
        AutocompleteSuggestion("List all files in the current working directory and check disk space.", "Workspace inventory", "directive", "AI"),
        AutocompleteSuggestion("Analyze the directory structure and create a summary report.", "Architecture overview", "directive", "AI")
    )

    fun computeAutocompleteSuggestions(
        rawQuery: String,
        isDirectShellMode: Boolean,
        inputHistory: List<String>,
        recentCommands: List<String>,
        maxResults: Int = 6
    ): List<AutocompleteSuggestion> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        val results = mutableListOf<AutocompleteSuggestion>()
        val seen = mutableSetOf<String>()

        for (item in (inputHistory.asReversed() + recentCommands.asReversed())) {
            val cleanItem = item.trim()
            if (cleanItem.isBlank() || cleanItem.equals(query, ignoreCase = true)) continue
            if (cleanItem.startsWith(query, ignoreCase = true) || cleanItem.contains(query, ignoreCase = true)) {
                if (seen.add(cleanItem.lowercase())) {
                    results.add(
                        AutocompleteSuggestion(
                            text = cleanItem,
                            description = "Previously entered in session history",
                            kind = "history",
                            badge = "HISTORY"
                        )
                    )
                }
            }
            if (results.size >= maxResults) return results
        }

        val primaryPool = if (isDirectShellMode) {
            COMMON_SHELL_SUGGESTIONS + COMMON_AGENT_DIRECTIVES
        } else {
            COMMON_AGENT_DIRECTIVES + COMMON_SHELL_SUGGESTIONS
        }

        val prefixMatches = primaryPool.filter {
            !it.text.equals(query, ignoreCase = true) && it.text.startsWith(query, ignoreCase = true)
        }
        val containsMatches = primaryPool.filter {
            !it.text.equals(query, ignoreCase = true) &&
                !it.text.startsWith(query, ignoreCase = true) &&
                (it.text.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true))
        }

        for (suggestion in (prefixMatches + containsMatches)) {
            if (seen.add(suggestion.text.lowercase())) {
                results.add(suggestion)
                if (results.size >= maxResults) break
            }
        }

        return results
    }

    private val RISKY_REGEX = Regex(
        """\b(rm\s+-\w*[rf]|sudo|mkfs|dd\s+if=|chmod\s+-R|chown\s+-R|shutdown|reboot|kill\s+-9)\b|\|\s*(ba)?sh\b|>\s*\/dev\/""",
        RegexOption.IGNORE_CASE
    )

    fun isRiskyCommand(command: String): Boolean = RISKY_REGEX.containsMatchIn(command)

    fun clampTimeoutSec(value: Number?, fallback: Long = DEFAULT_TIMEOUT_SEC): Long {
        if (value == null) return fallback
        val d = value.toDouble()
        if (d.isNaN() || d.isInfinite()) return fallback
        return min(MAX_TIMEOUT_SEC, max(MIN_TIMEOUT_SEC, d.roundToLong()))
    }

    fun clipOutput(s: String): String =
        if (s.length > MAX_OUTPUT_CHARS) "${s.take(MAX_OUTPUT_CHARS)}\n…[truncated]" else s

    fun shortenPath(path: String, maxLen: Int = 44): String =
        if (path.length <= maxLen) path else "…${path.takeLast(maxLen - 1)}"

    fun shortenPathMiddle(path: String, maxLen: Int = 38): String {
        val clean = path.trim()
        if (clean.length <= maxLen || maxLen < 10) return clean
        val headLen = (maxLen - 1) / 2
        val tailLen = maxLen - 1 - headLen
        return "${clean.take(headLen)}…${clean.takeLast(tailLen)}"
    }

    // Secret Redaction Engine — DO NOT MODIFY
    private const val REDACTED = "[REDACTED]"
    private val SKIP_VALUE = Regex("""^\[REDACTED|^(?:Bearer|Basic)$|^\d{1,6}$""", RegexOption.IGNORE_CASE)

    private val PEM_REGEX = Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)""")
    private val URL_CRED_REGEX = Regex("""\b([a-z][a-z0-9+.-]{0,20}:\/\/[^/\s:@]{1,100}:)[^/\s@]{1,200}@""", RegexOption.IGNORE_CASE)
    private val SK_KEY_REGEX = Regex("""\b(?:sk|rk)[-_](?:ant-|proj-|live_|test_)?[A-Za-z0-9_-]{16,}""")
    private val AIZA_REGEX = Regex("""\bAIza[0-9A-Za-z_-]{30,}""")
    private val GH_PAT_REGEX = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})""")
    private val SLACK_REGEX = Regex("""\bxox[abprs]-[A-Za-z0-9-]{10,}""")
    private val AWS_REGEX = Regex("""\b(?:AKIA|ASIA)[0-9A-Z]{16}\b""")
    private val NPM_REGEX = Regex("""\bnpm_[A-Za-z0-9]{30,}""")
    private val JWT_REGEX = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")
    private val BEARER_REGEX = Regex("""\bBearer\s+[A-Za-z0-9._~+/=-]{12,}""", RegexOption.IGNORE_CASE)
    private val BASIC_REGEX = Regex("""\bAuthorization:\s*Basic\s+[A-Za-z0-9+/=]{8,}""", RegexOption.IGNORE_CASE)
    private val KV_SECRET_REGEX = Regex(
        """\b([\w.-]{0,40}?(?:password|passwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|credentials?|authorization)[\w.-]{0,40})(["']?\s*[=:]\s*)(["']?)([^\s"'&;,]{4,})""",
        RegexOption.IGNORE_CASE
    )

    data class RedactionResult(val text: String, val count: Int)

    fun redactSecrets(input: String?, extraSecrets: List<String> = emptyList()): RedactionResult {
        var text = input.orEmpty()
        var count = 0

        for (secret in extraSecrets) {
            if (secret.length < 8 || !text.contains(secret)) continue
            val parts = text.split(secret)
            count += parts.size - 1
            text = parts.joinToString(REDACTED)
        }

        text = PEM_REGEX.replace(text) { count++; "[REDACTED PRIVATE KEY]" }
        text = URL_CRED_REGEX.replace(text) { m -> count++; "${m.groupValues[1]}$REDACTED@" }
        text = SK_KEY_REGEX.replace(text) { count++; REDACTED }
        text = AIZA_REGEX.replace(text) { count++; REDACTED }
        text = GH_PAT_REGEX.replace(text) { count++; REDACTED }
        text = SLACK_REGEX.replace(text) { count++; REDACTED }
        text = AWS_REGEX.replace(text) { count++; REDACTED }
        text = NPM_REGEX.replace(text) { count++; REDACTED }
        text = JWT_REGEX.replace(text) { count++; REDACTED }
        text = BEARER_REGEX.replace(text) { count++; "Bearer $REDACTED" }
        text = BASIC_REGEX.replace(text) { count++; "Authorization: Basic $REDACTED" }
        text = KV_SECRET_REGEX.replace(text) { m ->
            val key = m.groupValues[1]
            val sep = m.groupValues[2]
            val quote = m.groupValues[3]
            val value = m.groupValues[4]
            if (SKIP_VALUE.containsMatchIn(value)) {
                m.value
            } else {
                count++
                "$key$sep$quote$REDACTED"
            }
        }

        return RedactionResult(text, count)
    }

    sealed interface MarkdownBlock {
        data class Text(val text: String) : MarkdownBlock
        data class Code(val lang: String, val code: String) : MarkdownBlock
    }

    private val FENCE_REGEX = Regex("""^\s*```([\w+#.-]*)\s*$""")

    fun parseBlocks(src: String): List<MarkdownBlock> {
        val rawBlocks = mutableListOf<Triple<String, String, MutableList<String>>>()
        var current: Triple<String, String, MutableList<String>>? = null

        for (line in src.split('\n')) {
            val match = FENCE_REGEX.matchEntire(line)
            if (match != null) {
                if (current?.first == "code") {
                    current = null
                } else {
                    val nextCode = Triple("code", match.groupValues[1], mutableListOf<String>())
                    rawBlocks.add(nextCode)
                    current = nextCode
                }
                continue
            }
            if (current == null) {
                val nextText = Triple("text", "", mutableListOf<String>())
                rawBlocks.add(nextText)
                current = nextText
            }
            current.third.add(line)
        }

        return rawBlocks.mapNotNull { (type, lang, lines) ->
            val joined = lines.joinToString("\n")
            if (type == "code") {
                MarkdownBlock.Code(lang.ifBlank { "text" }, joined)
            } else {
                val trimmed = joined.trimStart('\n').trimEnd()
                if (trimmed.isBlank()) null else MarkdownBlock.Text(trimmed)
            }
        }
    }

    fun fenceFor(text: String): String {
        val longest = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat(max(3, longest + 1))
    }

    private fun clean(s: String): String = redactSecrets(s).text

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun exportToMarkdown(entries: List<TranscriptEntry>, provider: String, model: String): String {
        val out = mutableListOf(
            "# Nexus session",
            "",
            "- Exported: ${isoNow()}",
            "- Model: $provider · $model",
            ""
        )
        for (item in entries) {
            when (item.type) {
                "user" -> {
                    out.add("## You")
                    out.add("")
                    out.add(clean(item.text))
                    out.add("")
                }
                "agent" -> {
                    out.add("## Agent")
                    out.add("")
                    out.add(clean(item.text))
                    out.add("")
                }
                "analysis" -> {
                    out.add("## Intelligence Analysis")
                    out.add("")
                    out.add(clean(item.text))
                    out.add("")
                }
                "command" -> {
                    val cmd = clean(item.command)
                    val f = fenceFor(cmd)
                    out.add("### Command — ${item.commandStatus.ifBlank { "proposed" }}")
                    out.add("")
                    if (item.reason.isNotBlank()) {
                        out.add("_${clean(item.reason).replace('\n', ' ')}_")
                    }
                    out.add("${f}bash")
                    out.add(cmd)
                    out.add(f)
                    out.add("")
                    val sub = listOfNotNull(
                        item.cwd.takeIf { it.isNotBlank() }?.let { "cwd: ${clean(it)}" },
                        item.timeoutSec.takeIf { it > 0 }?.let { "timeout: ${it}s" }
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) {
                        out.add("<sub>$sub</sub>")
                        out.add("")
                    }
                }
                "output" -> {
                    val body = clean(item.output)
                    val f = fenceFor(body)
                    val cwdSuffix = if (item.cwd.isNotBlank()) " · ${clean(item.cwd)}" else ""
                    out.add("### Output — exit ${item.exitCode}$cwdSuffix")
                    out.add("")
                    out.add("${f}text")
                    out.add(body)
                    out.add(f)
                    out.add("")
                }
                "notice" -> {
                    out.add("> ${clean(item.text).replace("\n", "\n> ")}")
                    out.add("")
                }
            }
        }
        return out.joinToString("\n").trimEnd() + "\n"
    }

    fun exportToJson(entries: List<TranscriptEntry>, provider: String, model: String): String {
        val itemsArray = JSONArray()
        for (item in entries) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                if (item.text.isNotBlank()) put("text", clean(item.text))
                if (item.command.isNotBlank()) put("command", clean(item.command))
                if (item.reason.isNotBlank()) put("reason", clean(item.reason))
                if (item.commandStatus.isNotBlank()) put("status", item.commandStatus)
                if (item.cwd.isNotBlank()) put("cwd", clean(item.cwd))
                if (item.type == "output") {
                    put("exitCode", item.exitCode)
                    put("output", clean(item.output))
                    put("redactions", item.redactions)
                }
            }
            itemsArray.put(obj)
        }
        val root = JSONObject().apply {
            put("app", "nexus-shell-agent")
            put("exportedAt", isoNow())
            put("provider", provider)
            put("model", model)
            put("items", itemsArray)
        }
        return root.toString(2)
    }

    data class CommandTelemetryItem(
        val commandKey: String,
        val displayLabel: String,
        val fullCommandExample: String,
        val totalRuns: Int,
        val successCount: Int,
        val failureCount: Int,
        val avgExecutionMs: Long,
        val minExecutionMs: Long,
        val maxExecutionMs: Long,
        val totalRedactions: Long,
        val isRisky: Boolean
    ) {
        val successRatePercent: Float
            get() = if (totalRuns <= 0) 0f else (successCount.toFloat() * 100f) / totalRuns.toFloat()
    }

    data class CommandRunSample(
        val index: Int,
        val commandLabel: String,
        val executionMs: Long,
        val isSuccess: Boolean,
        val exitCode: Long
    )

    data class CommandAnalyticsSummary(
        val frequentCommands: List<CommandTelemetryItem>,
        val recentTimeline: List<CommandRunSample>,
        val totalExecutedCommands: Int,
        val overallSuccessRatePercent: Float,
        val averageExecutionMs: Long,
        val p95ExecutionMs: Long,
        val totalSecretsRedacted: Long,
        val pendingApprovalsCount: Int,
        val deniedCommandsCount: Int
    )

    private fun shortenCommandLabel(cmd: String): String {
        val trimmed = cmd.trim()
        if (trimmed.length <= 18) return trimmed
        return trimmed.take(16) + "…"
    }

    /**
     * Computes command telemetry strictly from real executed commands in the session transcript.
     * Contains zero fabricated baseline runs or synthetic durations.
     */
    fun computeCommandAnalytics(
        entries: List<TranscriptEntry>,
        runtimeLatenciesMs: Map<String, Long> = emptyMap()
    ): CommandAnalyticsSummary {
        val outputEntries = entries.filter { it.type == "output" && it.command.isNotBlank() }
        val commandEntries = entries.filter { it.type == "command" }
        val pendingCount = commandEntries.count { it.commandStatus == "pending" }
        val deniedCount = commandEntries.count { it.commandStatus == "denied" || it.commandStatus == "cancelled" }

        data class MutableStats(
            val fullCommand: String,
            var runs: Int = 0,
            var successes: Int = 0,
            var failures: Int = 0,
            val durations: MutableList<Long> = mutableListOf(),
            var redactions: Long = 0L
        )

        val statsMap = linkedMapOf<String, MutableStats>()
        val timeline = mutableListOf<CommandRunSample>()

        outputEntries.forEachIndexed { idx, entry ->
            val rawCmd = entry.command.trim()
            val normKey = rawCmd.lowercase()
            val measuredMs = runtimeLatenciesMs[entry.id] ?: 0L
            val isOk = entry.exitCode == 0L

            val group = statsMap.getOrPut(normKey) {
                MutableStats(fullCommand = rawCmd)
            }
            group.runs += 1
            if (isOk) group.successes += 1 else group.failures += 1
            if (measuredMs > 0L) {
                group.durations.add(measuredMs)
            }
            group.redactions += entry.redactions

            timeline.add(
                CommandRunSample(
                    index = idx + 1,
                    commandLabel = shortenCommandLabel(rawCmd),
                    executionMs = measuredMs,
                    isSuccess = isOk,
                    exitCode = entry.exitCode
                )
            )
        }

        val frequentList = statsMap.entries.map { (_, st) ->
            val avg = if (st.durations.isEmpty()) 0L else st.durations.average().roundToLong()
            val minMs = st.durations.minOrNull() ?: 0L
            val maxMs = st.durations.maxOrNull() ?: 0L
            CommandTelemetryItem(
                commandKey = st.fullCommand,
                displayLabel = shortenCommandLabel(st.fullCommand),
                fullCommandExample = st.fullCommand,
                totalRuns = st.runs,
                successCount = st.successes,
                failureCount = st.failures,
                avgExecutionMs = avg,
                minExecutionMs = minMs,
                maxExecutionMs = maxMs,
                totalRedactions = st.redactions,
                isRisky = isRiskyCommand(st.fullCommand)
            )
        }.sortedWith(
            compareByDescending<CommandTelemetryItem> { it.totalRuns }
                .thenByDescending { it.avgExecutionMs }
        ).take(8)

        val allDurations = statsMap.values.flatMap { it.durations }.sorted()
        val totalRuns = outputEntries.size
        val totalSuccess = outputEntries.count { it.exitCode == 0L }
        val overallSuccessRate = if (totalRuns <= 0) 100f else (totalSuccess.toFloat() * 100f) / totalRuns.toFloat()
        val overallAvgMs = if (allDurations.isEmpty()) 0L else allDurations.average().roundToLong()
        val p95Ms = if (allDurations.isEmpty()) 0L else {
            val p95Idx = ((allDurations.size - 1) * 0.95).toInt().coerceIn(0, allDurations.lastIndex)
            allDurations[p95Idx]
        }
        val totalRedactions = statsMap.values.sumOf { it.redactions }

        return CommandAnalyticsSummary(
            frequentCommands = frequentList,
            recentTimeline = timeline.takeLast(12),
            totalExecutedCommands = totalRuns,
            overallSuccessRatePercent = overallSuccessRate,
            averageExecutionMs = overallAvgMs,
            p95ExecutionMs = p95Ms,
            totalSecretsRedacted = totalRedactions,
            pendingApprovalsCount = pendingCount,
            deniedCommandsCount = deniedCount
        )
    }
}
