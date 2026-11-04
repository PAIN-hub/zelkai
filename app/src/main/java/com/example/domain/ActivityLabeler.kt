package com.example.domain

import java.io.File

/**
 * Deterministically generates plain-language, presentable step titles from shell commands
 * or file operations when an explicit sanitized title is absent.
 */
object ActivityLabeler {

    fun label(command: String, explicitTitle: String? = null, filePath: String? = null): String {
        // 1. If explicit title is present and passes sanitization, use it
        val sanitized = TitleSanitizer.sanitize(explicitTitle)
        if (sanitized != null) return sanitized

        // 2. If this is a dedicated file creation/update action
        if (!filePath.isNullOrBlank()) {
            val fileName = File(filePath.trim()).name.ifBlank { filePath.trim() }
            return "Create $fileName"
        }

        val raw = command.trim()
        if (raw.isBlank()) return "Run command"

        // Strip leading environment variables (e.g. `PORT=3000 npm start`) or sudo
        var clean = raw
        if (clean.startsWith("sudo ")) clean = clean.removePrefix("sudo ").trim()
        while (clean.matches(Regex("^[A-Z0-9_]+=[^\\s]+\\s+.*"))) {
            clean = clean.substringAfter(" ").trim()
        }

        val tokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = tokens.firstOrNull()?.lowercase() ?: return "Run command"
        val second = tokens.getOrNull(1)?.lowercase().orEmpty()
        val third = tokens.getOrNull(2).orEmpty()

        // 1. Check if tool/package is installed
        if (clean.contains(" --version") || clean.contains(" -v") || first == "which" || (first == "command" && second == "-v")) {
            val tool = if (first == "which") {
                tokens.getOrNull(1)?.substringAfterLast('/')?.take(24) ?: "tool"
            } else if (first == "command" && second == "-v") {
                tokens.getOrNull(2)?.substringAfterLast('/')?.take(24) ?: "tool"
            } else {
                first.take(24)
            }
            return "Check $tool is installed"
        }

        // 2. Install packages
        if ((first == "npm" || first == "pnpm" || first == "yarn") && (second in setOf("i", "install", "add"))) {
            val pkg = tokens.drop(2).firstOrNull { !it.startsWith("-") }?.take(24)
            return if (pkg != null) "Install $pkg" else "Install dependencies"
        }
        if (first == "pip" || first == "pip3" && second == "install") {
            val pkg = tokens.drop(2).firstOrNull { !it.startsWith("-") }?.take(24)
            return if (pkg != null) "Install $pkg" else "Install Python packages"
        }
        if ((first == "pkg" || first == "apt" || first == "apt-get") && (second == "install")) {
            val pkg = tokens.drop(2).firstOrNull { !it.startsWith("-") }?.take(24)
            return if (pkg != null) "Install $pkg" else "Install packages"
        }
        if (first == "cargo" && second == "add") {
            val pkg = tokens.drop(2).firstOrNull { !it.startsWith("-") }?.take(24)
            return if (pkg != null) "Install $pkg" else "Install crate"
        }

        // 3. Git operations
        if (first == "git") {
            when (second) {
                "clone" -> {
                    val repo = tokens.getOrNull(2)?.trimEnd('/')?.substringAfterLast('/')?.removeSuffix(".git")?.take(28)
                    return if (!repo.isNullOrBlank()) "Clone $repo" else "Clone repository"
                }
                "checkout", "switch" -> {
                    val branch = tokens.drop(2).firstOrNull { !it.startsWith("-") }?.take(24)
                    return if (branch != null) "Switch to branch $branch" else "Switch branch"
                }
                "pull" -> return "Pull latest changes"
                "push" -> return "Push commits"
                "commit" -> return "Commit changes"
                "status" -> return "Check git status"
                "diff" -> return "View git diff"
                "add" -> return "Stage changes"
                "init" -> return "Initialize repository"
            }
        }

        // 4. Directory creation
        if (first == "mkdir") {
            val dir = tokens.drop(1).lastOrNull { !it.startsWith("-") }?.let { File(it).name.ifBlank { it } }?.take(24)
            return if (dir != null) "Create folder $dir" else "Create directory"
        }

        // 5. File creation / update via shell
        if (clean.contains("> ") || clean.contains(">> ") || first == "touch") {
            val targetPath = if (first == "touch") {
                tokens.drop(1).lastOrNull { !it.startsWith("-") }
            } else {
                clean.substringAfterLast(">").trim().split(Regex("\\s+")).firstOrNull()
            }
            if (!targetPath.isNullOrBlank()) {
                val fname = File(targetPath).name.ifBlank { targetPath }.take(28)
                return "Create $fname"
            }
        }

        // 6. Running a file
        if (first in setOf("python", "python3", "node", "bash", "sh") && second.isNotBlank() && !second.startsWith("-")) {
            val fname = File(second).name.ifBlank { second }.take(28)
            return "Run $fname"
        }

        // 7. Server start / stop
        if (clean.contains("run dev") || clean.contains("run start") || clean.contains("python -m http.server") ||
            clean.contains("uvicorn") || clean.contains("gunicorn") || clean.contains("flask run") || clean.contains("serve ")
        ) {
            return "Start server"
        }
        if (first == "kill" || first == "pkill" || first == "killall") {
            return "Stop server"
        }

        // 8. List files and location
        if (first == "pwd") return "Print working directory"
        if (first in setOf("ls", "find", "tree")) {
            val target = tokens.drop(1).firstOrNull { !it.startsWith("-") }
            return if (!target.isNullOrBlank()) "List files in ${File(target).name.ifBlank { target }.take(20)}" else "List files"
        }

        // 9. Read file / search
        if (first in setOf("cat", "head", "tail", "less", "more")) {
            val target = tokens.drop(1).firstOrNull { !it.startsWith("-") }
            return if (!target.isNullOrBlank()) "Read ${File(target).name.ifBlank { target }.take(24)}" else "Read file"
        }
        if (first in setOf("grep", "rg", "ag")) {
            return "Search files"
        }

        // 10. File removal
        if (first == "rm") {
            val target = tokens.drop(1).lastOrNull { !it.startsWith("-") }
            return if (!target.isNullOrBlank()) "Delete ${File(target).name.ifBlank { target }.take(24)}" else "Delete files"
        }

        // 11. Generic fallback
        return "Run command"
    }
}

