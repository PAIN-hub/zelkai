package com.example.domain

/**
 * Execution policy for commands:
 * - FOREGROUND: normal synchronous execution up to 120s timeout.
 * - TRACKED: long build or compilation job detached and awaited up to 15m without calling model.
 * - SERVICE: background daemon or server job awaited up to 10s for readiness/listening port.
 */
enum class CommandExecutionPolicy(val id: String, val label: String, val maxWaitSeconds: Long) {
    FOREGROUND("foreground", "Foreground (up to 120s)", 120L),
    TRACKED("tracked", "Tracked background job (up to 15m)", 900L),
    SERVICE("service", "Background service (ready check up to 10s)", 10L);

    companion object {
        fun fromId(raw: String?): CommandExecutionPolicy {
            return entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: FOREGROUND
        }

        fun detectPolicy(command: String, explicitMode: String? = null): CommandExecutionPolicy {
            if (explicitMode?.equals("async", ignoreCase = true) == true ||
                explicitMode?.equals("background", ignoreCase = true) == true
            ) {
                return TRACKED
            }
            val lower = command.lowercase().trim()
            if (lower.contains("npm run dev") ||
                lower.contains("npm start") ||
                lower.contains("python -m http.server") ||
                lower.contains("uvicorn") ||
                lower.contains("gunicorn") ||
                lower.contains("flask run") ||
                lower.endsWith(" &")
            ) {
                return SERVICE
            }
            if (lower.contains("docker build") ||
                lower.contains("./gradlew") ||
                lower.contains("cargo build --release") ||
                lower.contains("make -j") ||
                lower.contains("git clone ")
            ) {
                return TRACKED
            }
            return FOREGROUND
        }

        fun timeoutHint(command: String, timeoutSec: Long): String {
            return "Command '$command' TIMED OUT after ${timeoutSec}s and was stopped. If it is a server or long build, start it with start_job and check it with wait_for_job or job_logs."
        }
    }
}

