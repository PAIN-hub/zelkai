package com.example.domain

import com.example.data.model.TranscriptEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Thrown when a cloud provider (Gemini, Anthropic Claude, OpenAI) is selected
 * but the user has not entered their personal API key in Settings.
 * Never falls back to any developer key or alternative provider.
 */
class MissingApiKeyException(
    val provider: String,
    val modelId: String,
    val providerDisplayName: String = NexusToolAndRedactor.providerDisplayLabel(provider)
) : IllegalStateException(
    "API key required for $providerDisplayName ($modelId). Add your personal API key in Settings to use this model."
)

enum class ProviderErrorKind {
    INVALID_API_KEY,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    OFFLINE,
    OTHER
}

/**
 * Structured provider error mapped from HTTP status codes and network exceptions (Part B #6).
 * Never exposes raw server JSON, stack traces, or API keys in [userMessage].
 */
class ProviderApiException(
    val provider: String,
    val modelId: String,
    val statusCode: Int,
    val kind: ProviderErrorKind,
    val userMessage: String,
    val rawErrorBody: String = "",
    val retryAfterSeconds: Long? = null
) : IOException(userMessage) {
    val httpStatus: Int get() = statusCode
}

/**
 * Pluggable transport interface used by [MultiPlatformAiEngine] so unit tests can verify
 * that each provider routes strictly to its own backend and never falls back to another provider.
 */
interface ProviderTransport {
    fun streamGeminiRest(
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String>

    fun streamAnthropicRest(
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String>

    fun streamOpenAiCompatibleRest(
        provider: String,
        endpointUrl: String,
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String>

    fun streamLocalOnDevice(
        modelId: String,
        userPrompt: String,
        currentCwd: String,
        compactSystemPrompt: String,
        recentCommandOutputs: List<Pair<String, String>>
    ): Flow<String>
}

class MultiPlatformAiEngine(
    private val localLlmManager: LocalLlmManager,
    private val userApiKeyStore: UserApiKeyStore,
    private val customTransport: ProviderTransport? = null
) {

    companion object {
        const val LOCAL_MAX_AGENT_STEPS = 4
        const val LOCAL_SYSTEM_PROMPT_MAX_TOKENS = 280
        const val LOCAL_HISTORY_TOKEN_BUDGET = 1200

        fun buildCompactLocalSystemInstruction(
            currentCwd: String
        ): String {
            return """
You are Nexus Agent on Android Linux.
Real working directory (cwd): $currentCwd
Android constraints: no sudo, no apt/apt-get, Termux bash or toybox/mksh commands.
Protocol: To run a shell command in cwd, output ONLY one `sh-exec` block (1 command per block, max 1 per step, no unescaped multi-line chains). For long-running servers (`npm run dev`, `python -m http.server`, `node server.js`, `flask run`), use `start_job <command>`, `job_status <id>`, `job_logs <id> 30`, or `stop_job <id>`. After `[TOOL_EXECUTION_RESULT]`, summarize the result in plain text.

Example 1:
User: create a folder named web
Assistant:
```sh-exec
mkdir -p web
```

Example 2:
User: start a static HTTP server on port 8000
Assistant:
```sh-exec
start_job python -m http.server 8000
```
            """.trimIndent()
        }

        fun buildCloudSystemInstruction(
            personaPrompt: String,
            currentCwd: String
        ): String {
            val basePersona = personaPrompt.ifBlank {
                "You are Nexus Agent, an AI software engineer and Android Linux shell assistant."
            }
            return """
$basePersona

Current Working Directory (cwd): $currentCwd
Android Environment Constraints: no sudo, no apt/apt-get (use `pkg install -y` in Termux). All relative paths resolve against `$currentCwd`.

### TOOL-CALL & SHELL EXECUTION PROTOCOL
1. When the user asks to perform a file/directory operation, inspect the system, or run a command, call `execute_shell_command` OR emit a single fenced block:
```sh-exec
<command>
```
2. For long-running dev servers (`npm start`, `npm run dev`, `node server.js`, `python -m http.server`, `flask run`, `vite`, `next dev`), NEVER block the foreground shell. Instead call `start_job(command)` (or emit `start_job <command>` in `sh-exec`), and manage jobs with `job_status(id)`, `job_logs(id, lines)`, and `stop_job(id)`.
3. Emit at most ONE command per step, with no unescaped multi-line command chains.
4. After receiving `[TOOL_EXECUTION_RESULT]`, inspect the exit code and output, then either run the next step or summarize the final result clearly in Markdown.
5. For pure conceptual questions that do not touch the filesystem or shell, answer directly in clean Markdown without emitting a command block.
            """.trimIndent()
        }

        fun trimEntriesForLocalBudget(
            entries: List<TranscriptEntry>,
            tokenBudget: Int = LOCAL_HISTORY_TOKEN_BUDGET
        ): List<TranscriptEntry> {
            val maxChars = (tokenBudget * 4).coerceAtLeast(800)
            var usedChars = 0
            val result = ArrayList<TranscriptEntry>()

            for (entry in entries.asReversed()) {
                val compactEntry = when (entry.type) {
                    "output" -> entry.copy(output = entry.output.take(2000))
                    "agent" -> entry.copy(text = entry.text.take(600))
                    "user" -> entry.copy(text = entry.text.take(600))
                    else -> entry
                }
                val entryChars = compactEntry.text.length + compactEntry.output.length + compactEntry.command.length + 32
                if (usedChars + entryChars > maxChars && result.isNotEmpty()) {
                    break
                }
                usedChars += entryChars
                result.add(compactEntry)
            }
            return result.reversed()
        }

        fun buildConversationTurnsForRouting(
            entries: List<TranscriptEntry>,
            fallbackUserPrompt: String
        ): List<Pair<String, String>> {
            val rawTurns = mutableListOf<Pair<String, String>>()

            entries.takeLast(36).forEach { entry ->
                when (entry.type) {
                    "user" -> {
                        val t = entry.text.trim()
                        if (t.isNotEmpty()) rawTurns.add("user" to t)
                    }
                    "agent" -> {
                        val t = entry.text.trim()
                        if (t.isNotEmpty()) {
                            rawTurns.add("model" to t)
                        }
                    }
                    "command" -> {
                        when (entry.commandStatus) {
                            "denied", "cancelled" -> {
                                rawTurns.add(
                                    "user" to "[COMMAND_DENIED_BY_USER] Command `${entry.command}` in `${entry.cwd}` was ${entry.commandStatus} by the user. Do not re-run it unless asked; acknowledge the denial and continue."
                                )
                            }
                            "approved" -> {
                                rawTurns.add(
                                    "model" to "```sh-exec\n${entry.command}\n```"
                                )
                            }
                        }
                    }
                    "output" -> {
                        val formattedFeedback = ToolCallParser.formatToolOutputForModelFeedback(
                            command = entry.command,
                            cwd = entry.cwd,
                            exitCode = entry.exitCode.toInt(),
                            rawOutput = entry.output,
                            maxChars = 2000
                        )
                        rawTurns.add("user" to formattedFeedback)
                    }
                }
            }

            val mergedTurns = mutableListOf<Pair<String, String>>()
            for ((role, text) in rawTurns) {
                val last = mergedTurns.lastOrNull()
                if (last != null && last.first == role) {
                    mergedTurns[mergedTurns.lastIndex] = role to "${last.second}\n\n$text"
                } else {
                    mergedTurns.add(role to text)
                }
            }

            while (mergedTurns.isNotEmpty() && mergedTurns.first().first != "user") {
                mergedTurns.removeAt(0)
            }

            if (mergedTurns.isEmpty()) {
                mergedTurns.add("user" to fallbackUserPrompt.ifBlank { "Hello" })
            } else if (mergedTurns.last().first == "model") {
                val promptSuffix = fallbackUserPrompt.takeIf { it.isNotBlank() }
                    ?: "[System: Continue based on the latest state.]"
                mergedTurns.add("user" to promptSuffix)
            }

            return mergedTurns
        }

        fun routeWithTransport(
            provider: String,
            modelId: String,
            personalApiKey: String,
            maxTokens: Long,
            entries: List<TranscriptEntry>,
            personaPrompt: String,
            currentCwd: String,
            fallbackUserPrompt: String,
            transport: ProviderTransport,
            openAiEndpointUrl: String = "https://api.openai.com/v1/chat/completions"
        ): Flow<String> {
            val normProvider = when (val raw = provider.lowercase().trim()) {
                "fable" -> "anthropic"
                else -> raw
            }
            val cleanModelId = modelId.trim().removePrefix("models/")
            val tokenLimit = maxTokens.coerceIn(256L, 64000L).toInt()

            val latestUserPrompt = entries.lastOrNull { it.type == "user" }?.text?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackUserPrompt.trim().ifBlank { "Hello" }

            if (normProvider == "local") {
                val trimmedEntries = trimEntriesForLocalBudget(entries)
                val resolvedPrompt = trimmedEntries.lastOrNull { it.type == "user" }?.text?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: latestUserPrompt

                val compactSystemPrompt = buildCompactLocalSystemInstruction(currentCwd = currentCwd)

                val entriesAfterLatestUser = trimmedEntries.takeLastWhile { it.type != "user" }
                val recentOutputs = entriesAfterLatestUser.mapNotNull { postEntry ->
                    when {
                        postEntry.type == "output" -> {
                            val formatted = ToolCallParser.formatToolOutputForModelFeedback(
                                command = postEntry.command,
                                cwd = postEntry.cwd.ifBlank { currentCwd },
                                exitCode = postEntry.exitCode.toInt(),
                                rawOutput = postEntry.output,
                                maxChars = 2000
                            )
                            postEntry.command to formatted
                        }
                        postEntry.type == "command" && (postEntry.commandStatus == "denied" || postEntry.commandStatus == "cancelled") -> {
                            postEntry.command to "[COMMAND_DENIED_BY_USER] Command `${postEntry.command}` was ${postEntry.commandStatus} by the user."
                        }
                        else -> null
                    }
                }

                return transport.streamLocalOnDevice(
                    modelId = cleanModelId,
                    userPrompt = resolvedPrompt,
                    currentCwd = currentCwd,
                    compactSystemPrompt = compactSystemPrompt,
                    recentCommandOutputs = recentOutputs
                )
            }

            val cleanKey = personalApiKey.trim()
            if (cleanKey.isBlank()) {
                return flow {
                    throw MissingApiKeyException(
                        provider = normProvider,
                        modelId = cleanModelId
                    )
                }
            }

            val systemInstruction = buildCloudSystemInstruction(
                personaPrompt = personaPrompt,
                currentCwd = currentCwd
            )
            val conversationTurns = buildConversationTurnsForRouting(
                entries = entries,
                fallbackUserPrompt = latestUserPrompt
            )

            return when (normProvider) {
                "gemini" -> transport.streamGeminiRest(
                    modelId = cleanModelId,
                    apiKey = cleanKey,
                    maxTokens = tokenLimit,
                    systemInstruction = systemInstruction,
                    conversationTurns = conversationTurns
                )
                "anthropic" -> transport.streamAnthropicRest(
                    modelId = cleanModelId,
                    apiKey = cleanKey,
                    maxTokens = tokenLimit,
                    systemInstruction = systemInstruction,
                    conversationTurns = conversationTurns
                )
                "openai" -> transport.streamOpenAiCompatibleRest(
                    provider = "openai",
                    endpointUrl = openAiEndpointUrl,
                    modelId = cleanModelId,
                    apiKey = cleanKey,
                    maxTokens = tokenLimit,
                    systemInstruction = systemInstruction,
                    conversationTurns = conversationTurns
                )
                else -> flow {
                    throw IllegalArgumentException("Unsupported AI provider '$provider'.")
                }
            }
        }

        fun classifyHttpError(
            provider: String,
            modelId: String,
            statusCode: Int,
            rawBody: String,
            retryAfterHeader: String?
        ): ProviderApiException {
            val label = NexusToolAndRedactor.providerDisplayLabel(provider)
            val cleanId = modelId.removePrefix("models/")
            val retrySec = retryAfterHeader?.trim()?.toLongOrNull()

            return when (statusCode) {
                401, 403 -> ProviderApiException(
                    provider = provider,
                    modelId = cleanId,
                    statusCode = statusCode,
                    kind = ProviderErrorKind.INVALID_API_KEY,
                    userMessage = "$label rejected your API key (HTTP $statusCode). Open Settings to update or test your key.",
                    rawErrorBody = rawBody
                )
                404 -> ProviderApiException(
                    provider = provider,
                    modelId = cleanId,
                    statusCode = 404,
                    kind = ProviderErrorKind.MODEL_NOT_FOUND,
                    userMessage = "Model '$cleanId' is no longer available on $label (HTTP 404). Select an active model.",
                    rawErrorBody = rawBody
                )
                429 -> {
                    val waitHint = if (retrySec != null && retrySec > 0) " Retry after ${retrySec}s." else ""
                    ProviderApiException(
                        provider = provider,
                        modelId = cleanId,
                        statusCode = 429,
                        kind = ProviderErrorKind.RATE_LIMITED,
                        userMessage = "$label rate limit or quota exceeded (HTTP 429).$waitHint",
                        rawErrorBody = rawBody,
                        retryAfterSeconds = retrySec
                    )
                }
                in 500..599 -> ProviderApiException(
                    provider = provider,
                    modelId = cleanId,
                    statusCode = statusCode,
                    kind = ProviderErrorKind.SERVER_ERROR,
                    userMessage = "$label server error (HTTP $statusCode). Please retry shortly.",
                    rawErrorBody = rawBody
                )
                else -> ProviderApiException(
                    provider = provider,
                    modelId = cleanId,
                    statusCode = statusCode,
                    kind = ProviderErrorKind.OTHER,
                    userMessage = "$label request failed (HTTP $statusCode).",
                    rawErrorBody = rawBody
                )
            }
        }
    }

    var geminiBaseUrl: String = "https://generativelanguage.googleapis.com"
    var anthropicBaseUrl: String = "https://api.anthropic.com"
    var openAiBaseUrl: String = "https://api.openai.com"

    private var streamingHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun setHttpClientForTesting(client: OkHttpClient) {
        this.streamingHttpClient = client
    }

    private val defaultTransport = object : ProviderTransport {
        override fun streamGeminiRest(
            modelId: String,
            apiKey: String,
            maxTokens: Int,
            systemInstruction: String,
            conversationTurns: List<Pair<String, String>>
        ): Flow<String> = executeGeminiRest(
            modelId = modelId,
            apiKey = apiKey,
            maxTokens = maxTokens,
            systemInstruction = systemInstruction,
            conversationTurns = conversationTurns
        )

        override fun streamAnthropicRest(
            modelId: String,
            apiKey: String,
            maxTokens: Int,
            systemInstruction: String,
            conversationTurns: List<Pair<String, String>>
        ): Flow<String> = executeAnthropicRest(
            modelId = modelId,
            apiKey = apiKey,
            maxTokens = maxTokens,
            systemInstruction = systemInstruction,
            conversationTurns = conversationTurns
        )

        override fun streamOpenAiCompatibleRest(
            provider: String,
            endpointUrl: String,
            modelId: String,
            apiKey: String,
            maxTokens: Int,
            systemInstruction: String,
            conversationTurns: List<Pair<String, String>>
        ): Flow<String> = executeOpenAiCompatibleRest(
            endpointUrl = endpointUrl,
            modelId = modelId,
            apiKey = apiKey,
            maxTokens = maxTokens,
            systemInstruction = systemInstruction,
            conversationTurns = conversationTurns
        )

        override fun streamLocalOnDevice(
            modelId: String,
            userPrompt: String,
            currentCwd: String,
            compactSystemPrompt: String,
            recentCommandOutputs: List<Pair<String, String>>
        ): Flow<String> = localLlmManager.streamLocalInference(
            modelId = modelId,
            userPrompt = userPrompt,
            currentCwd = currentCwd,
            systemPromptPrefix = compactSystemPrompt,
            recentCommandOutputs = recentCommandOutputs
        )
    }

    private val transport: ProviderTransport
        get() = customTransport ?: defaultTransport

    fun streamCompletion(
        provider: String,
        modelId: String,
        maxTokens: Long,
        entries: List<TranscriptEntry>,
        personaPrompt: String,
        currentCwd: String,
        fallbackUserPrompt: String
    ): Flow<String> {
        val normProvider = when (val raw = provider.lowercase().trim()) {
            "fable" -> "anthropic"
            else -> raw
        }
        val cleanModelId = modelId.trim().removePrefix("models/")

        val latestUserPrompt = entries.lastOrNull { it.type == "user" }?.text?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackUserPrompt.trim().ifBlank { "Hello" }

        if (normProvider == "local") {
            return routeWithTransport(
                provider = normProvider,
                modelId = cleanModelId,
                personalApiKey = "",
                maxTokens = maxTokens,
                entries = entries,
                personaPrompt = personaPrompt,
                currentCwd = currentCwd,
                fallbackUserPrompt = latestUserPrompt,
                transport = transport
            )
        }

        val personalKey = userApiKeyStore.getApiKey(normProvider, cleanModelId).trim()
        val openAiUrl = "${openAiBaseUrl.trimEnd('/')}/v1/chat/completions"

        return routeWithTransport(
            provider = normProvider,
            modelId = cleanModelId,
            personalApiKey = personalKey,
            maxTokens = maxTokens,
            entries = entries,
            personaPrompt = personaPrompt,
            currentCwd = currentCwd,
            fallbackUserPrompt = latestUserPrompt,
            transport = transport,
            openAiEndpointUrl = openAiUrl
        )
    }

    suspend fun generateSingleTurnText(
        provider: String,
        modelId: String,
        prompt: String,
        currentCwd: String
    ): String {
        val normProvider = provider.lowercase().trim()
        val sb = StringBuilder()
        val singleEntry = listOf(
            TranscriptEntry(
                id = "single_turn",
                type = "user",
                text = prompt,
                cwd = currentCwd,
                sequence = 1L
            )
        )
        streamCompletion(
            provider = normProvider,
            modelId = modelId,
            maxTokens = 1024L,
            entries = singleEntry,
            personaPrompt = "You are a concise Linux CLI security and DevOps assistant. Format answers in clean Markdown.",
            currentCwd = currentCwd,
            fallbackUserPrompt = prompt
        ).collect { chunk ->
            sb.append(chunk)
        }
        return sb.toString().trim()
    }

    private fun mapHttpFailure(
        provider: String,
        modelId: String,
        statusCode: Int,
        rawBody: String,
        retryAfterHeader: String?
    ): ProviderApiException = classifyHttpError(provider, modelId, statusCode, rawBody, retryAfterHeader)

    private fun mapNetworkException(provider: String, modelId: String, error: Throwable): ProviderApiException {
        if (error is ProviderApiException) return error
        val label = NexusToolAndRedactor.providerDisplayLabel(provider)
        val cleanId = modelId.removePrefix("models/")
        return when (error) {
            is SocketTimeoutException -> ProviderApiException(
                provider = provider,
                modelId = cleanId,
                statusCode = 408,
                kind = ProviderErrorKind.TIMEOUT,
                userMessage = "$label request timed out after 30s. Check your connection and retry."
            )
            is UnknownHostException -> ProviderApiException(
                provider = provider,
                modelId = cleanId,
                statusCode = 0,
                kind = ProviderErrorKind.OFFLINE,
                userMessage = "Device is offline or cannot reach $label."
            )
            else -> ProviderApiException(
                provider = provider,
                modelId = cleanId,
                statusCode = 0,
                kind = ProviderErrorKind.OTHER,
                userMessage = "Connection to $label failed. Check your network and retry."
            )
        }
    }

    private fun buildGeminiToolsJson(): JSONArray {
        val execFn = JSONObject().apply {
            put("name", "execute_shell_command")
            put("description", "Execute a foreground shell command in the current working directory.")
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("command", JSONObject().put("type", "STRING").put("description", "Single non-interactive shell command to run in cwd."))
                            put("reason", JSONObject().put("type", "STRING").put("description", "Short explanation of why this command is needed."))
                            put("timeoutSec", JSONObject().put("type", "INTEGER").put("description", "Execution timeout in seconds (2..300)."))
                        }
                    )
                    put("required", JSONArray().put("command"))
                }
            )
        }
        val startJobFn = JSONObject().apply {
            put("name", "start_job")
            put("description", "Start a detached background server/daemon job (e.g. npm run dev, python -m http.server, node server.js, flask run).")
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("command", JSONObject().put("type", "STRING").put("description", "Long-running server or background command to launch detached."))
                            put("reason", JSONObject().put("type", "STRING").put("description", "Short explanation of the background server."))
                        }
                    )
                    put("required", JSONArray().put("command"))
                }
            )
        }
        val jobStatusFn = JSONObject().apply {
            put("name", "job_status")
            put("description", "Check the status, PID, and detected port of a background job.")
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().put("id", JSONObject().put("type", "STRING").put("description", "Background job ID (e.g. job_a1b2c3d4).")))
                    put("required", JSONArray().put("id"))
                }
            )
        }
        val jobLogsFn = JSONObject().apply {
            put("name", "job_logs")
            put("description", "Fetch the latest redacted output log lines from a background job.")
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("id", JSONObject().put("type", "STRING").put("description", "Background job ID."))
                            put("lines", JSONObject().put("type", "INTEGER").put("description", "Number of recent log lines to return (1..500)."))
                        }
                    )
                    put("required", JSONArray().put("id"))
                }
            )
        }
        val stopJobFn = JSONObject().apply {
            put("name", "stop_job")
            put("description", "Stop a running background job process group (SIGTERM -> SIGKILL).")
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().put("id", JSONObject().put("type", "STRING").put("description", "Background job ID to terminate.")))
                    put("required", JSONArray().put("id"))
                }
            )
        }
        val decls = JSONArray()
            .put(execFn)
            .put(startJobFn)
            .put(jobStatusFn)
            .put(jobLogsFn)
            .put(stopJobFn)
        return JSONArray().put(
            JSONObject().put("functionDeclarations", decls)
        )
    }

    private fun parseNativeFunctionCall(
        fnName: String,
        args: JSONObject,
        source: String
    ): ToolCall? {
        return when (fnName.trim().lowercase()) {
            "start_job" -> {
                val cmd = args.optString("command", "").trim()
                if (cmd.isEmpty()) null else ToolCall(
                    command = cmd,
                    reason = args.optString("reason", "Runs in background"),
                    timeoutSec = 10L,
                    source = source,
                    riskLevel = ToolCallParser.classifyRisk(cmd),
                    actionKind = ToolActionKind.START_JOB,
                    runsInBackground = true
                )
            }
            "job_status" -> {
                val id = args.optString("id", args.optString("job_id", "")).trim()
                if (id.isEmpty()) null else ToolCall(
                    command = "job_status $id",
                    reason = "Check status of background job $id",
                    timeoutSec = 10L,
                    source = source,
                    riskLevel = CommandRiskLevel.LOW,
                    actionKind = ToolActionKind.JOB_STATUS,
                    jobId = id,
                    runsInBackground = false
                )
            }
            "job_logs" -> {
                val id = args.optString("id", args.optString("job_id", "")).trim()
                val lines = args.optInt("lines", 50).coerceIn(1, 500)
                if (id.isEmpty()) null else ToolCall(
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
            }
            "stop_job" -> {
                val id = args.optString("id", args.optString("job_id", "")).trim()
                if (id.isEmpty()) null else ToolCall(
                    command = "stop_job $id",
                    reason = "Stop background job $id",
                    timeoutSec = 10L,
                    source = source,
                    riskLevel = CommandRiskLevel.LOW,
                    actionKind = ToolActionKind.STOP_JOB,
                    jobId = id,
                    runsInBackground = false
                )
            }
            else -> {
                val cmd = args.optString("command", "").trim()
                if (cmd.isEmpty()) null else ToolCallParser.buildToolCallFromCommandString(
                    rawCommand = cmd,
                    source = source,
                    defaultTimeoutSec = args.optLong("timeoutSec", 10L).coerceIn(2L, 300L),
                    reasonOverride = args.optString("reason", "Execute shell command")
                )
            }
        }
    }

    /**
     * Amendment #1 & Part B #5:
     * Calls Gemini `POST {base}/v1beta/models/{cleanId}:streamGenerateContent?alt=sse`
     * with the user's key in the `x-goog-api-key` HTTP header.
     */
    private fun executeGeminiRest(
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String> = flow {
        val cleanModelId = modelId.trim().removePrefix("models/")
        val contentsArr = JSONArray()
        conversationTurns.forEach { (role, text) ->
            val geminiRole = if (role == "model") "model" else "user"
            contentsArr.put(
                JSONObject().apply {
                    put("role", geminiRole)
                    put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", text))
                    )
                }
            )
        }

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            put("contents", contentsArr)
            put("tools", buildGeminiToolsJson())
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", maxTokens)
                    put("temperature", 0.2)
                }
            )
        }

        val base = geminiBaseUrl.trimEnd('/')
        val url = "$base/v1beta/models/$cleanModelId:streamGenerateContent?alt=sse"
        val req = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequestWithRetryAndStream(
            provider = "gemini",
            modelId = cleanModelId,
            request = req
        ) { chunkJson ->
            parseGeminiCandidateChunk(chunkJson)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseGeminiCandidateChunk(json: JSONObject): Pair<String, ToolCall?> {
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        var nativeToolCall: ToolCall? = null
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val partObj = parts.optJSONObject(i) ?: continue
                val partText = partObj.optString("text", "")
                if (partText.isNotEmpty()) {
                    sb.append(partText)
                }
                val fnCall = partObj.optJSONObject("functionCall")
                if (fnCall != null && nativeToolCall == null) {
                    val fnName = fnCall.optString("name", "execute_shell_command")
                    val args = fnCall.optJSONObject("args") ?: JSONObject()
                    nativeToolCall = parseNativeFunctionCall(fnName, args, "gemini-native")
                }
            }
        }
        return sb.toString() to nativeToolCall
    }

    private fun buildAnthropicToolsJson(): JSONArray {
        val execSchema = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Single non-interactive shell command to run in cwd."))
                    put("reason", JSONObject().put("type", "string").put("description", "Short explanation of why this command is needed."))
                    put("timeoutSec", JSONObject().put("type", "integer").put("description", "Execution timeout in seconds (2..300)."))
                }
            )
            put("required", JSONArray().put("command"))
        }
        val startJobSchema = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Long-running server command to start detached in background."))
                    put("reason", JSONObject().put("type", "string").put("description", "Short explanation."))
                }
            )
            put("required", JSONArray().put("command"))
        }
        val jobIdSchema = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().put("id", JSONObject().put("type", "string").put("description", "Background job ID.")))
            put("required", JSONArray().put("id"))
        }
        val jobLogsSchema = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("id", JSONObject().put("type", "string").put("description", "Background job ID."))
                    put("lines", JSONObject().put("type", "integer").put("description", "Number of log lines (1..500)."))
                }
            )
            put("required", JSONArray().put("id"))
        }
        return JSONArray()
            .put(JSONObject().put("name", "execute_shell_command").put("description", "Execute a shell command in the current working directory.").put("input_schema", execSchema))
            .put(JSONObject().put("name", "start_job").put("description", "Start a detached background server job.").put("input_schema", startJobSchema))
            .put(JSONObject().put("name", "job_status").put("description", "Check status of a background job.").put("input_schema", jobIdSchema))
            .put(JSONObject().put("name", "job_logs").put("description", "Fetch recent logs of a background job.").put("input_schema", jobLogsSchema))
            .put(JSONObject().put("name", "stop_job").put("description", "Stop a running background job.").put("input_schema", jobIdSchema))
    }

    private fun executeAnthropicRest(
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String> = flow {
        val messagesArr = JSONArray()
        conversationTurns.forEach { (role, text) ->
            val anthRole = if (role == "model") "assistant" else "user"
            messagesArr.put(
                JSONObject().apply {
                    put("role", anthRole)
                    put("content", text)
                }
            )
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", maxTokens.coerceAtMost(8192))
            put("system", systemInstruction)
            put("messages", messagesArr)
            put("tools", buildAnthropicToolsJson())
            put("stream", true)
        }

        val base = anthropicBaseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$base/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .header("accept", "text/event-stream, application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequestWithRetryAndStream(
            provider = "anthropic",
            modelId = modelId,
            request = req
        ) { chunkJson ->
            parseAnthropicChunk(chunkJson)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseAnthropicChunk(json: JSONObject): Pair<String, ToolCall?> {
        val eventType = json.optString("type", "")
        if (eventType == "content_block_delta") {
            val delta = json.optJSONObject("delta")
            if (delta?.optString("type") == "text_delta") {
                return delta.optString("text", "") to null
            }
        }
        val contentArr = json.optJSONArray("content")
        val sb = StringBuilder()
        var nativeToolCall: ToolCall? = null
        if (contentArr != null) {
            for (i in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> sb.append(block.optString("text"))
                    "tool_use" -> {
                        if (nativeToolCall == null) {
                            val fnName = block.optString("name", "execute_shell_command")
                            val input = block.optJSONObject("input") ?: JSONObject()
                            nativeToolCall = parseNativeFunctionCall(fnName, input, "anthropic-native")
                        }
                    }
                }
            }
        }
        return sb.toString() to nativeToolCall
    }

    private fun buildOpenAiToolsJson(): JSONArray {
        fun wrapFn(name: String, desc: String, params: JSONObject): JSONObject =
            JSONObject().put("type", "function").put(
                "function",
                JSONObject().put("name", name).put("description", desc).put("parameters", params)
            )

        val execParams = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Single non-interactive shell command to run in cwd."))
                    put("reason", JSONObject().put("type", "string").put("description", "Short explanation of why this command is needed."))
                    put("timeoutSec", JSONObject().put("type", "integer").put("description", "Execution timeout in seconds (2..300)."))
                }
            )
            put("required", JSONArray().put("command"))
        }
        val startJobParams = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Long-running server command to start detached in background."))
                    put("reason", JSONObject().put("type", "string").put("description", "Short explanation."))
                }
            )
            put("required", JSONArray().put("command"))
        }
        val jobIdParams = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().put("id", JSONObject().put("type", "string").put("description", "Background job ID.")))
            put("required", JSONArray().put("id"))
        }
        val jobLogsParams = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("id", JSONObject().put("type", "string").put("description", "Background job ID."))
                    put("lines", JSONObject().put("type", "integer").put("description", "Number of log lines (1..500)."))
                }
            )
            put("required", JSONArray().put("id"))
        }

        return JSONArray()
            .put(wrapFn("execute_shell_command", "Execute a shell command in the current working directory.", execParams))
            .put(wrapFn("start_job", "Start a detached background server job.", startJobParams))
            .put(wrapFn("job_status", "Check status of a background job.", jobIdParams))
            .put(wrapFn("job_logs", "Fetch recent logs of a background job.", jobLogsParams))
            .put(wrapFn("stop_job", "Stop a running background job.", jobIdParams))
    }

    private fun executeOpenAiCompatibleRest(
        endpointUrl: String,
        modelId: String,
        apiKey: String,
        maxTokens: Int,
        systemInstruction: String,
        conversationTurns: List<Pair<String, String>>
    ): Flow<String> = flow {
        val messagesArr = JSONArray()
        val isReasoningModel = modelId.startsWith("o3") || modelId.startsWith("o1") || modelId.startsWith("o4")
        messagesArr.put(
            JSONObject().apply {
                put("role", if (isReasoningModel) "developer" else "system")
                put("content", systemInstruction)
            }
        )
        conversationTurns.forEach { (role, text) ->
            val openAiRole = if (role == "model") "assistant" else "user"
            messagesArr.put(
                JSONObject().apply {
                    put("role", openAiRole)
                    put("content", text)
                }
            )
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArr)
            put("tools", buildOpenAiToolsJson())
            put("stream", true)
            if (isReasoningModel) {
                put("max_completion_tokens", maxTokens)
            } else {
                put("max_tokens", maxTokens)
            }
        }

        val req = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequestWithRetryAndStream(
            provider = "openai",
            modelId = modelId,
            request = req
        ) { chunkJson ->
            parseOpenAiChunk(chunkJson)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseOpenAiChunk(json: JSONObject): Pair<String, ToolCall?> {
        val choices = json.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0) ?: return "" to null

        val delta = firstChoice.optJSONObject("delta")
        if (delta != null) {
            val deltaContent = delta.optString("content", "")
            val toolCalls = delta.optJSONArray("tool_calls")
            var nativeToolCall: ToolCall? = null
            if (toolCalls != null && toolCalls.length() > 0) {
                val fn = toolCalls.optJSONObject(0)?.optJSONObject("function")
                val fnName = fn?.optString("name", "execute_shell_command").orEmpty()
                val argsRaw = fn?.optString("arguments", "").orEmpty()
                if (argsRaw.isNotEmpty()) {
                    val argsJson = runCatching { JSONObject(argsRaw) }.getOrNull()
                    if (argsJson != null) {
                        nativeToolCall = parseNativeFunctionCall(fnName, argsJson, "openai-native")
                    }
                }
            }
            return deltaContent to nativeToolCall
        }

        val firstMsg = firstChoice.optJSONObject("message") ?: return "" to null
        val contentText = firstMsg.optString("content", "")
        val toolCalls = firstMsg.optJSONArray("tool_calls")
        var nativeToolCall: ToolCall? = null
        if (toolCalls != null && toolCalls.length() > 0) {
            val fn = toolCalls.optJSONObject(0)?.optJSONObject("function")
            val fnName = fn?.optString("name", "execute_shell_command").orEmpty()
            val argsRaw = fn?.optString("arguments", "{}").orEmpty()
            val argsJson = runCatching { JSONObject(argsRaw) }.getOrNull() ?: JSONObject()
            nativeToolCall = parseNativeFunctionCall(fnName, argsJson, "openai-native")
        }
        return contentText to nativeToolCall
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeRequestWithRetryAndStream(
        provider: String,
        modelId: String,
        request: Request,
        chunkParser: (JSONObject) -> Pair<String, ToolCall?>
    ) {
        var attempt = 0
        val maxAttempts = 3
        var lastError: ProviderApiException? = null

        while (attempt < maxAttempts) {
            attempt++
            currentCoroutineContext().ensureActive()
            val call = streamingHttpClient.newCall(request)
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val rawErr = resp.body?.string().orEmpty()
                        val retryHeader = resp.header("Retry-After")
                        val mapped = mapHttpFailure(
                            provider = provider,
                            modelId = modelId,
                            statusCode = resp.code,
                            rawBody = rawErr,
                            retryAfterHeader = retryHeader
                        )
                        if (mapped.kind == ProviderErrorKind.SERVER_ERROR && attempt < maxAttempts) {
                            lastError = mapped
                            Thread.sleep(150L * attempt)
                            return@use
                        }
                        throw mapped
                    }

                    val reader = resp.body?.charStream()?.buffered()
                        ?: throw mapHttpFailure(provider, modelId, 502, "", null)

                    var emittedAnyText = false
                    var capturedToolCall: ToolCall? = null
                    val nonSseLines = StringBuilder()
                    var sawSseDataLine = false

                    reader.useLines { lines ->
                        for (line in lines) {
                            currentCoroutineContext().ensureActive()
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                sawSseDataLine = true
                                val dataPayload = trimmed.removePrefix("data:").trim()
                                if (dataPayload.isEmpty() || dataPayload == "[DONE]") continue
                                val json = runCatching { JSONObject(dataPayload) }.getOrNull() ?: continue
                                val (textPart, toolCall) = chunkParser(json)
                                if (textPart.isNotEmpty()) {
                                    emittedAnyText = true
                                    emit(textPart)
                                }
                                if (toolCall != null && capturedToolCall == null) {
                                    capturedToolCall = toolCall
                                }
                            } else {
                                nonSseLines.appendLine(line)
                            }
                        }
                    }

                    if (!sawSseDataLine) {
                        val fullBody = nonSseLines.toString().trim()
                        if (fullBody.startsWith("[")) {
                            val arr = runCatching { JSONArray(fullBody) }.getOrNull()
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optJSONObject(i) ?: continue
                                    val (textPart, toolCall) = chunkParser(item)
                                    if (textPart.isNotEmpty()) {
                                        emittedAnyText = true
                                        emit(textPart)
                                    }
                                    if (toolCall != null && capturedToolCall == null) {
                                        capturedToolCall = toolCall
                                    }
                                }
                            }
                        } else if (fullBody.startsWith("{")) {
                            val json = runCatching { JSONObject(fullBody) }.getOrNull()
                            if (json != null) {
                                val (textPart, toolCall) = chunkParser(json)
                                if (textPart.isNotEmpty()) {
                                    emittedAnyText = true
                                    emit(textPart)
                                }
                                if (toolCall != null && capturedToolCall == null) {
                                    capturedToolCall = toolCall
                                }
                            }
                        }
                    }

                    if (capturedToolCall != null) {
                        val toolBlock = ToolCallParser.formatNormalizedToolCallBlock(capturedToolCall!!, "")
                        val prefix = if (emittedAnyText) "\n\n" else ""
                        emit("$prefix$toolBlock")
                        emittedAnyText = true
                    }

                    if (!emittedAnyText) {
                        emit("(empty response from $modelId)")
                    }
                    return
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                val mapped = mapNetworkException(provider, modelId, e)
                if (mapped.kind == ProviderErrorKind.SERVER_ERROR && attempt < maxAttempts) {
                    lastError = mapped
                    Thread.sleep(150L * attempt)
                    continue
                }
                throw mapped
            }
        }

        throw (lastError ?: ProviderApiException(
            provider = provider,
            modelId = modelId,
            statusCode = 503,
            kind = ProviderErrorKind.SERVER_ERROR,
            userMessage = "Server unavailable after $maxAttempts attempts."
        ))
    }
}
