package com.example.domain

/**
 * Sanitizes optional user- or model-provided step titles:
 * - Single line only
 * - Max 60 characters
 * - Strips markdown formatting, control characters, and URLs
 * - Redacts secrets via [NexusToolAndRedactor]
 * - Rejects strings containing shell metacharacters (`; | & > < $ ` ( )`) or strings
 *   that look like shell command syntax -> returns null so callers fall back to [ActivityLabeler].
 */
object TitleSanitizer {
    private const val MAX_TITLE_LENGTH = 60
    private val SHELL_META_CHARS = setOf(';', '|', '&', '>', '<', '$', '`', '(', ')')
    private val URL_REGEX = Regex("https?://\\S+|ftp://\\S+", RegexOption.IGNORE_CASE)
    private val CONTROL_CHARS = Regex("[\\x00-\\x1F\\x7F]")
    private val MARKDOWN_FORMATTING = Regex("[*_~#\\[\\]`]")

    fun sanitize(rawTitle: String?): String? {
        if (rawTitle.isNullOrBlank()) return null

        // 1. Secret redaction first
        val redacted = NexusToolAndRedactor.redactSecrets(rawTitle).text

        // 2. Reject shell metacharacters
        if (redacted.any { it in SHELL_META_CHARS }) return null

        // 3. Strip URLs, control characters, and markdown syntax
        var cleaned = redacted
            .replace(URL_REGEX, "")
            .replace(CONTROL_CHARS, " ")
            .replace(MARKDOWN_FORMATTING, "")
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        // Collapse multiple whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return null

        // 4. Reject if it still looks like an unparsed raw command (e.g. starts with flag or command executable)
        if (looksLikeRawCommand(cleaned)) return null

        return cleaned.take(MAX_TITLE_LENGTH).trim()
    }

    private fun looksLikeRawCommand(title: String): Boolean {
        val lower = title.lowercase()
        if (lower.startsWith("-") || lower.startsWith("./") || lower.startsWith("/")) return true
        val firstToken = lower.substringBefore(" ")
        val shellVerbs = setOf(
            "sh", "bash", "zsh", "sudo", "apt", "pkg", "npm", "pip", "python", "python3",
            "node", "git", "cat", "ls", "grep", "sed", "awk", "curl", "wget", "kill", "pkill"
        )
        // If the entire title is just a shell binary name or starts with a binary followed by CLI flags (-f, --all)
        if (firstToken in shellVerbs) {
            val rest = lower.substringAfter(" ", "").trim()
            if (rest.startsWith("-") || rest.contains(" -") || rest.isBlank()) return true
        }
        return false
    }
}

