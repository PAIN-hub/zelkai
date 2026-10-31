# UX Copy Audit — Nexus Agent v5

This audit catalogs all user-visible strings across Nexus Agent v5, identifies outdated or unverified claims, and provides accurate, verified replacement copy aligned with the unified system glossary (*Chat*, *Project*, *Background job*, *Execution environment*, *Cloud prompts*, *On-device model*).

| Location | Current Text | Problem | New Text (Verified) |
|---|---|---|---|
| `AuthScreen.kt:329` | "Powered by Gemini 3.8 Flash, Gemini 4 Argon, and Gemini 3.5 Flash-Lite with role personas & output analysis." | Mentions unreleased or outdated internal model names (Gemini 3.8, Gemini 4 Argon). | "Multi-model reasoning powered by Google Gemini, Anthropic Claude, OpenAI, and local on-device models." |
| `LocalShellBridge.kt:383` | `"providers": ["gemini-3.8-flash", "gemini-4-argon", "gemini-3.5-flash-lite"]` | Outdated model IDs in fallback provider list. | `"providers": ["gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite"]` |
| `AuthScreen.kt:432` | "Guest mode enables local AI terminal & telemetry preview (up to 5 prompts, no cloud sync)." | Hardcoded "5 prompts" contradicts the real active constant `GUEST_DIRECTIVES_LIMIT = 3`. | "Guest mode includes 3 cloud AI prompts. On-device models and direct shell commands don't count. No cloud sync." |
| `AuthScreen.kt:403` | "Sign in with Google" (on generic purple button) | Lacks official Google "G" branding and button guidelines. | Official Google button: "Continue with Google" with standard Google icon. |
| `AuthScreen.kt:3` | No Email/Password sign-up or sign-in | Claims "Sign up / Sign in" in copy but provides no email sign-up/in fields. | "Continue with email" expanding inline sign-in / account creation form with validation. |
| `AuthScreen.kt:173` | `e.localizedMessage ?: "Sign in failed"` | Shows raw exceptions to users on auth errors. | Friendly error cards mapping to actionable steps (e.g. "Check your internet connection", "Incorrect password", "Google Sign-In configuration error"). |
| `NexusJobsAndPreviewSheet.kt:837` | `(No log output yet)` | Low contrast on dark terminal surface in light theme; missing job state context. | "No output yet · Job is {state}" on high-contrast CodeSurface. |
| `NexusSettingsDialog.kt:205` | Direct string messages in `statusToastMessage` | Inconsistent ephemeral toasts scattered across settings. | Unified `FeedbackHost` and `UiMessage` snackbars with standardized strings from `strings.xml`. |
| `NexusMainScreen.kt:1396` | "Guest directive limit: X/3 used" | Uses internal engineering term "directive". | "Cloud prompts: {used}/{total} used · On-device models & direct shell commands are unlimited." |
| `strings.xml:3` | "Multi-model AI shell agent and command workspace with real-time cloud synchronization..." | Wordy marketing subtitle with jargon. | "Your AI agent for Android. Chat with Gemini, Claude, OpenAI, or on-device models. You approve every risky command." |
| `NexusSettingsDialog.kt` | Various instances of "Bridge" / "JNI" / "toybox" | Internal implementation jargon shown to end users without definitions. | Unified glossary: "Execution environment (Termux or Built-in shell)". |
