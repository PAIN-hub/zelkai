# On-Device `llama.cpp` Runtime Verification Guide (`RUNTIME_TEST.md`)

This document provides the exact `adb` installation, logcat filtering, instrumented test execution, and manual physical-device verification checklist for the Nexus Agent v5 on-device inference runtime (`:llama`, pinned to `llama.cpp` release tag `b12020`).

---

## 1. Prerequisites & Device Check

Verify that an ARM64 Android phone is connected via USB debugging or Wireless debugging:

```bash
adb devices -l
adb shell getprop ro.product.cpu.abi
# Expected on physical ARM64 phone: arm64-v8a
```

---

## 2. Build & Install Commands

### Build Debug & Release APKs

```bash
# Build Debug APK (uses Release -O3 CMake optimization for :llama)
./gradlew assembleDebug

# Build Release APK
STORE_PASSWORD=android KEY_PASSWORD=android ./gradlew assembleRelease
```

### Verify 16 KB Page-Size Alignment Before Installing

```bash
# 1. Verify ELF LOAD segment alignment (0x4000 = 16384 bytes)
/home/madara/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf -W -l \
  app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/*.so | grep -E "File:|Type|LOAD"

# 2. Verify APK zipalign at 16 KB page boundary
/home/madara/Android/Sdk/build-tools/36.0.0/zipalign -c -P 16 -v 4 \
  app/build/outputs/apk/release/app-release.apk | grep -E "\.so|Verification"
```

### Install APK via `adb`

```bash
# Install Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or install Release APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 3. Filtered Logcat Commands (Zero Prompt/Output Leakage)

Prompts, tokens, and API keys are never written to logcat. Use the following command to observe runtime initialization, GGUF metadata inspection, memory guards, benchmark timings, and lifecycle unload events:

```bash
adb logcat -c
adb logcat -v time -s \
  EngineProvider:I \
  LlamaEngine:I \
  LocalLlmManager:I \
  InferenceOptimizer:I \
  AiChat:I \
  GGUF:I \
  AndroidRuntime:E
```

---

## 4. Physical ARM64 Instrumented Test Execution

To run the automated physical-device test (`LlamaEngineInstrumentedTest`):

```bash
# Optional: pre-push a small GGUF model to app-private filesDir/models for offline test execution
adb shell run-as com.example.nexus mkdir -p files/models
adb push SmolLM2-360M-Instruct-Q8_0.gguf /data/local/tmp/SmolLM2-360M-Instruct-Q8_0.gguf
adb shell run-as com.example.nexus cp /data/local/tmp/SmolLM2-360M-Instruct-Q8_0.gguf files/models/

# Execute the instrumented test suite on the connected physical ARM64 phone
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.LlamaEngineInstrumentedTest
```

---

## 5. Manual Verification Checklist (Physical ARM64 Phone)

- [ ] **1. Engine Status in Local LLM Hub**
  - Open **Model Switcher** -> **Manage Local Models (Local LLM Hub)**.
  - Confirm the `"Runtime not installed"` warning banner is **absent** on a physical ARM64 phone.
  - Confirm the Runtime card displays:
    - Engine tag: `b12020`
    - Backend / CPU features: `CPU (ARM64 NEON / DotProd)` (or detected `/proc/cpuinfo` features)
    - ABI: `arm64-v8a`

- [ ] **2. Download & Integrity Verification**
  - Download **SmolLM2 360M Instruct (Q8_0)** or **Qwen 2.5 Coder 0.5B (Q4_K_M)** from the Hub.
  - Confirm progress bar updates smoothly and the model transitions to `Downloaded` in `filesDir/models/`.
  - Confirm unbenchmarked models initially show `"Not benchmarked"` in both the Hub and Model Switcher.

- [ ] **3. First Load + Real Automatic Benchmark**
  - Select the downloaded model from the **Model Switcher** under **On-Device** (or tap **Load** in the Hub).
  - Confirm the top header chip updates to the selected local model name.
  - Confirm that after the first load, the engine runs 1 warm-up + 3 timed benchmark iterations on the fixed benchmark prompt and updates the Hub and Model Switcher with **measured** `tok/s`, `TTFT (ms)`, `Load (ms)`, and `Peak RAM (MB)`.
  - Tap **Re-run benchmark** in the Hub and verify the measured values refresh and persist across app restarts via Jetpack DataStore.

- [ ] **4. Real Streaming Chat & Agent Tool Call**
  - Ensure **Guest Mode** or local selection is active; verify sending local prompts **never increments** the guest cloud quota counter.
  - Send prompt: `"Create index.html with a landing page"`.
  - Confirm real streamed text arrives and produces either a valid `<sh-exec>` tool execution block (using the compact `<300` token local agent system prompt and GBNF grammar/stop sequences) or a typed error card—never simulated filler text.
  - Test an emoji and CJK prompt (e.g., `"Reply with 3 rocket emojis and 日本語"`), confirming no `\uFFFD` replacement characters appear across token boundaries.
  - If using **DeepSeek R1 Distill 1.5B**, confirm `<think>...</think>` blocks are parsed into a collapsed **Thinking** section, excluded from tool-call execution, and stripped from subsequent conversation turns.

- [ ] **5. Fast Stop Cancellation (`< 500ms`)**
  - Trigger a long generation prompt and tap **Stop** mid-stream.
  - Confirm native generation aborts within `500ms`, the streaming indicator finishes cleanly without crashing, and partial output remains visible.

- [ ] **6. Model Switching**
  - Download a second model (e.g., switch from **SmolLM2 360M** to **Qwen 2.5 Coder 0.5B**).
  - Select the second model in the **Model Switcher**.
  - Confirm the first model unloads cleanly, the new model loads, the header chip updates, and subsequent prompts route exclusively to the newly selected model.

- [ ] **7. Background / Idle Unload & Lazy Reload**
  - Background the app or leave the loaded model idle for > 5 minutes.
  - Confirm the idle watchdog unloads the model (`isLoaded == false`) to reclaim RAM, and sending the next prompt automatically lazy-reloads the selected model before generating.

- [ ] **8. Low-Memory Behavior (`RUNNING_CRITICAL`) & Typed Error Cards**
  - While a model is loaded, simulate critical system memory pressure from a terminal:
    ```bash
    adb shell am send-trim-memory com.example.nexus RUNNING_CRITICAL
    ```
  - Confirm `MainActivity.onTrimMemory` triggers `LocalLlmManager.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` and unloads the native model immediately without crashing.
  - Send a new prompt and verify lazy reload succeeds.
  - Test corrupting a `.gguf` file header in `filesDir/models/` and attempting to load it; confirm a typed **Corrupt Model File** card appears in chat with a working **Delete & Re-download** action button.

- [ ] **9. Screen Rotation & Lifecycle Stability**
  - Rotate the device (`Portrait <-> Landscape`) while tokens are actively streaming.
  - Confirm generation continues smoothly in `NexusViewModel` without duplicating loads or crashing JNI pointers.

- [ ] **10. x86_64 Emulator / Non-ARM64 Fallback Verification**
  - Launch the APK on an `x86_64` Android emulator (if available).
  - Confirm `EngineProvider` catches the missing/unsupported ABI cleanly, selects `StubEngine`, displays `"Needs an ARM64 phone"` in the Local LLM Hub, and never crashes.

