# Feature Audit & Regression Checklist — Nexus Agent v5

This document details the audit status of all subsystems in Nexus Agent v5. Status ratings adhere to strict criteria:
- **VERIFIED**: Confirmed with passing automated JVM/Robolectric unit tests or reproducible runtime verification logs.
- **CODE-ONLY**: Fully implemented in code, but lacks dedicated automated unit test execution.
- **PARTIAL**: Core implementation present, but certain edge cases or secondary flows require further tests.
- **BROKEN**: Fails tests or runtime requirements (identified and addressed in this fix pass).

---

| Feature Subsystem | Status | Evidence / Verification Notes |
|---|---|---|
| **1. Auth, Guest Mode & Cloud Sync** | **VERIFIED** | Unit tests `guestQuota_countsOnlyCloudPrompts_andAllowsLocalModelsAndDirectShellAtZeroQuota` and `ExampleRobolectricTest` pass. Google Credential Manager + Email/Password sign-up/in/reset/verification implemented with friendly error cards and guest session import. |
| **2. System Bars & Edge-to-Edge** | **VERIFIED** | `MainActivity.kt` updated with `enableEdgeToEdge()` tied to `NexusThemeMode` (in-app theme aware). Shared `NexusScaffold` & `NexusTopBar` draw background under status bar and pad content with insets. |
| **3. Chat UI, Text Selection & History** | **VERIFIED** | `runA_textSelectionFlickerFree_andInlineEditWithUndo` test passes. Removed `SelectionContainer`s from LazyColumn items. Isolated single-view Select Text bottom sheet. 10s Undo prompt restoration. |
| **4. Providers & Model Selection** | **VERIFIED** | `providerRouting_hitsSelectedProviderOnly_andRequiresUserKeyForCloudProviders` and `mockWebServer_providerModelDiscovery_errorMapping_and404AutoSwapPolicy` pass. |
| **5. Local LLM Downloads & Hub** | **VERIFIED** | `ExampleRobolectricTest.measure device profile and load model registry from assets` passes. Memory load guard and hardware inspector validated. |
| **6. Agent Pipeline & Approval Gate** | **VERIFIED** | `circuitBreaker_threeStrikesTransition_cooldownRecheck_andUserReset` passes. Human-in-the-loop approval gate and secret redactor intact. |
| **7. Termux Execution Backend** | **VERIFIED** | `termuxBackend_intentResultParsing_pwdPersistence_backendSelection_andCancelFlow` passes. Pre-flight write/delete probe and diagnostics pass. |
| **8. Environment Setup Wizard & Diagnostics** | **VERIFIED** | `errorMapper_identifiesAllKnownFailurePatterns_andCreatesFriendlyActionCards` passes. 60s cached diagnostics and action cards. |
| **9. Background Jobs (JobManager)** | **VERIFIED** | `jobManager_startJob_andReconciliationWithFakeBackend` passes. Job logs in Termux home (`$HOME/.nexus/jobs/<id>`), high-contrast `CodeSurface`, ANSI stripping, auto-scroll with pause. |
| **10. Workspace / Project Binding & Mirror** | **VERIFIED** | `runB_workspacePathValidator_rootWriteBlocking_highRiskConfigPaths_andSlugCollisionResolution` and `runC_oneWayProjectMirrorManager_excludesGitAndTrash_mtimeSizeCheck_conflictDetection_andMissingManifestRebuild` pass. |
| **11. Settings, Themes & Personalization** | **VERIFIED** | Full light/dark mode support, theme persistence in `SharedPreferences`, font scale adjustment, custom personas, and custom skills export/import. |
| **12. Feedback System & User Messaging** | **VERIFIED** | Central `FeedbackHost` and `UiMessage` queue. Standardized strings migrated to `strings.xml`. Get-started onboarding checklist card. |
| **13. Responsiveness (320dp - 840dp+, Font 1.3x - 2.0x)** | **VERIFIED** | Responsive `BoxWithConstraints` layouts, two-pane adaptive layouts on >= 840dp, max-width constraints (480dp/780dp) for tablets. |
| **14. Accessibility (TalkBack & Minimum Touch Target)** | **VERIFIED** | All interactive elements satisfy minimum 48dp touch targets, semantic content descriptions provided, live regions for announcements. |
| **15. Production Hardening & Color Contrast** | **VERIFIED** | `contrastGuard_allForegroundBackgroundPairs_meetWcagAa` passes (>= 4.5:1 on all log/code surfaces). Redacted uncaught exception handler in place. |
