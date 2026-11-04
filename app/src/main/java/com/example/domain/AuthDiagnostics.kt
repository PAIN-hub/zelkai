package com.example.domain

enum class AuthStep(val label: String) {
    CREDENTIAL_REQUEST("1. Credential Manager Request"),
    ID_TOKEN_RECEIVED("2. Google ID Token Received"),
    FIREBASE_SIGN_IN("3. Firebase Authentication"),
    POST_LOGIN_BOOTSTRAP("4. Profile & Firestore Bootstrap"),
    NAVIGATION("5. State & Navigation")
}

data class AuthStepRecord(
    val step: AuthStep,
    val isSuccess: Boolean,
    val exceptionClass: String? = null,
    val exceptionCode: String? = null,
    val details: String? = null
)

/**
 * Redacted diagnostics recorder for Google Sign-in.
 * Strictly guarantees ZERO user tokens, passwords, or emails are ever recorded or copied.
 */
object AuthDiagnostics {
    private val records = mutableMapOf<AuthStep, AuthStepRecord>()

    fun recordSuccess(step: AuthStep, detail: String? = null) {
        records[step] = AuthStepRecord(
            step = step,
            isSuccess = true,
            details = detail
        )
    }

    fun recordFailure(
        step: AuthStep,
        exception: Throwable?,
        code: String? = null,
        detail: String? = null
    ) {
        records[step] = AuthStepRecord(
            step = step,
            isSuccess = false,
            exceptionClass = exception?.javaClass?.simpleName,
            exceptionCode = code,
            details = detail
        )
    }

    fun clear() {
        records.clear()
    }

    fun getRecords(): List<AuthStepRecord> = records.values.sortedBy { it.step.ordinal }

    fun exportRedactedSummary(): String = buildString {
        appendLine("=== Nexus Auth Diagnostics (Redacted) ===")
        AuthStep.entries.forEach { step ->
            val rec = records[step]
            if (rec == null) {
                appendLine("${step.label}: NOT_REACHED")
            } else if (rec.isSuccess) {
                appendLine("${step.label}: PASS ${rec.details.orEmpty()}")
            } else {
                appendLine("${step.label}: FAIL [${rec.exceptionClass.orEmpty()}] code=${rec.exceptionCode.orEmpty()} ${rec.details.orEmpty()}")
            }
        }
    }
}

