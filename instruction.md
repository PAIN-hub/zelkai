# Nexus Agent v5 — Build, Test & Runtime Instructions

This document covers environment prerequisites, dependency setup, native `llama.cpp` build configuration, unit/instrumented test execution, 16 KB ELF alignment verification, the Agent Timeline UI refactor, and root-cause findings for the 5 resolved blockers in **Nexus Agent v5**.

---

## 1. Prerequisites & Environment Setup

- **JDK**: Java 17+ (OpenJDK 17 or 21 recommended)
- **Android SDK**:
  - `compileSdk = 35` (`android-35`)
  - `build-tools = 36.1.0` (or `36.0.0+`)
- **Android NDK (Side-by-Side)**:
  - `27.2.12479018` (configured in `:llama` and `app`)
- **CMake**:
  - `3.22.1`
- **Local SDK Path Configuration**:
  Ensure `local.properties` exists in the repository root pointing to your Android SDK installation:
  ```properties
  sdk.dir=/home/madara/Android/Sdk
  ```

---

## 2. Vendored `llama.cpp` Runtime Overview

- **Pinned Release Tag**: `b12020` (`0ec4027a2a0183de87c007069adddc06e665f7de`)
- **Location**: `third_party/llama.cpp`
- **Gradle Module**: `:llama` mapped in `settings.gradle.kts` to `third_party/llama.cpp/examples/llama.android/lib`
- **Documentation**:
  - Version details: [`docs/LLAMA_VERSION.md`](docs/LLAMA_VERSION.md)
  - Minimal JNI/Kotlin patches: [`docs/LLAMA_PATCHES.md`](docs/LLAMA_PATCHES.md)
  - Physical device verification & logcat guide: [`docs/RUNTIME_TEST.md`](docs/RUNTIME_TEST.md)

---

## 3. Dependency Installation & Keystore Setup

All Gradle dependencies are resolved automatically via the Gradle Wrapper (`./gradlew`).

If building a clean checkout without keystores:
```bash
# Debug keystore (if not already present)
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# Release upload keystore (if not already present)
keytool -genkeypair -v -keystore my-upload-key.jks \
  -storepass android -alias upload -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Release,O=Android,C=US"
```

---

## 4. Running Tests

### Run Unit Tests (JVM)
Executes all test suites (`TurnTimelineUnitTest`, `GenerationOutcomeUnitTest`, `RamPolicyUnitTest`, `TopBarStatusUnitTest`, `LlamaRuntimeUnitTest`, `ExampleUnitTest`, `ExampleRobolectricTest`):
```bash
./gradlew testDebugUnitTest
```

### Run Instrumented Tests (Physical ARM64 Device or Emulator)
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.LlamaEngineInstrumentedTest
```

---

## 5. Building Debug & Release APKs

Both `debug` and `release` variants compile the native `:llama` (`libai-chat.so`, `libllama.so`, `libggml*.so`) targets in **CMake `Release` (`-O3`)** mode for `arm64-v8a` only, with OpenSSL and CURL disabled and 16 KB page-size alignment enabled (`-Wl,-z,max-page-size=16384`).

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
STORE_PASSWORD=android KEY_PASSWORD=android ./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## 6. Verifying Native ABI Filtering & 16 KB Page Alignment

```bash
# 1. Confirm only arm64-v8a native libraries are packaged in the APK
unzip -l app/build/outputs/apk/release/app-release.apk | grep '\.so'

# 2. Verify 16 KB ELF LOAD segment alignment (Align = 0x4000)
/home/madara/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf -W -l \
  app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/*.so | grep -E "File:|Type|LOAD"

# 3. Verify APK zipalign 16 KB alignment
/home/madara/Android/Sdk/build-tools/36.0.0/zipalign -c -P 16 -v 4 \
  app/build/outputs/apk/release/app-release.apk | grep -E "\.so|Verification"
```

---

## 7. Findings & Root Causes for Blockers A–E

| Area | Symptom | Evidence (`file:line`) | Root Cause |
| :--- | :--- | :--- | :--- |
| **A) Google Sign-In** | Account picker appears, then app returns to sign-in screen with no error and no loader. | `AuthScreen.kt:80-145`, `AuthViewModel.kt:180` | Transient post-login Firestore bootstrap or profile creation failures were caught in the top-level catch block and treated as authentication failures, signing out or navigating back. Fixed by deciding auth success **ONLY** by Firebase Auth, surfacing a non-blocking `"Signed in, but sync failed · Retry"` banner if post-login sync fails, adding 5-step `AuthDiagnostics`, and providing "Copy diagnostics". |
| **B) New Chat** | Tapping "New chat" failed to show empty draft, did not close drawer, or crashed on Firestore listener error. | `NexusViewModel.kt:3225`, `NexusMainScreen.kt:700` | Eager remote Firestore session insertion triggered rules or snapshot listeners on empty chats before any message was sent. Fixed by storing draft sessions lazily in local state until the first message, immediately closing drawer, resetting stream state, and offering multi-chat lock explanation dialog to guests. |
| **C) Empty Model Response** | App reported `"Model returned an empty response"` during tool calls or pure reasoning turns. | `NexusViewModel.kt:3995-4085`, `GenerationOutcome.kt:26-95` | Generation outcome was classified only after aggressively stripping tool syntax and `<think>` blocks; when the model produced tool calls or reasoning only, stripped visible text was empty and misclassified as an error. Fixed with sealed `GenerationOutcome` (`ToolCallsOnly` is valid turn; `ReasoningOnly` triggers 1 auto-follow-up turn; `NoTokens` resets engine and retries once). |
| **D) Long-Running Commands** | Background build or server commands timed out at 120s or blocked UI agent turn indefinitely. | `JobManager.kt:140`, `CommandExecutionPolicy.kt:1-90` | Commands lacked policy-based execution; long-running processes ran synchronously in the foreground thread. Fixed with `CommandExecutionPolicy` (`FOREGROUND` 120s with exit 124 timeout hint, `TRACKED` detached job awaited up to 15m, `SERVICE` server job with ready port probes), tool grammar for `wait_for_job`, and `AgentResumeCoordinator`. |
| **E) Top Status Indicator** | Outdated `"Free plan"` or `"Guest · Sign in"` pill occupied top editorial center bar. | `NexusEditorialTopBar.kt:120`, `NexusComponents.kt:135`, `AgentStatus.kt:12-32` | Top center pill was hardcoded to billing/guest state rather than live agent operational phase. Fixed by replacing with high-visibility center pill driven by sealed `AgentStatus` (`Idle` "Ready", `Working(phase)` animated dot + "Thinking"/"Writing"/"Running command", `Error`), moving guest quota inline near composer when `<= 2` prompts left. |

---

## 8. Total Device RAM Budgeting & Load Guards

- **Budgeting Basis**: All model sizing, recommendation scoring, and load limits derive strictly from **Total Device RAM** (`MemTotal` / `MemoryInfo.totalMem`), adjusted at most 1 step by SoC class (`RamClass`), rather than transient free memory.
- **Formulas**:
  - `RamClass`: `LOW (<=3 GB)`, `MID (4 GB)`, `MID_PLUS (6 GB)`, `HIGH (8 GB)`, `HIGH_PLUS (12 GB)`, `ULTRA (16 GB+)`
  - `hardCap = 0.70 * totalRam`
  - `footprint = modelFileSize + kvCache + 12% overhead`
- **Load Guard**: Hard block loading **ONLY** when `footprint > hardCap`. If system `lowMemory` flag is active, show soft warning card (`LowMemoryWarning`) with `"Load anyway"` override.

---

## 9. Agent Timeline Interaction Pattern

- **Thought Process Row (`ThinkingRow`)**: Muted brain vector icon + `"Thought Process"` + chevron. Shown only when reasoning text exists (`<think>` or cloud thinking). Displays live duration timer during streaming and `"Thought for 8s"` when finished. Excluded from history sent back to the model.
- **Tool Group Row (`ToolGroupRow`)**: 48dp header with Lucide lightning bolt icon + count badge + chevron (`"Running… (2 of 3)"`, `"Waiting for your approval"`, `"Ran 3 commands"`, `"Completed 4 steps"`, `"Ran 3 commands · 1 failed"`).
- **Curved Tree Connector**: Custom Canvas bezier connector (`╰`) linking the header down through each step, with bullet dots `•`, plain-language step titles, duration, and expandable output viewer (capped at 200 lines).
- **Animated Working Indicator (`AgentWorkingIndicator`)**: 4 animated waving dots (`••••`) during active agent turns when no visible text is streaming, displaying `"Loading model…"` during on-device weights loading.
- **Typography & Theme**: Poppins + JetBrains Mono, clean Light/White default palette, and 100% monochrome Lucide vector stroke icons (zero emojis).
