# Vendored `llama.cpp` Patches (`examples/llama.android/lib`)

This document records every minimal patch made to the vendored `:llama` Android library module (`third_party/llama.cpp/examples/llama.android/lib`) at pinned release tag **`b12020`** (`0ec4027a2a0183de87c007069adddc06e665f7de`).

All core `llama.cpp` and `ggml` C/C++ source files outside `examples/llama.android/lib` are **unmodified**.

---

## 1. `third_party/llama.cpp/examples/llama.android/lib/build.gradle.kts`
- **Plugin & SDK Alignment**: Configured `id("com.android.library")`, `compileSdk { version = release(36) { minorApiLevel = 1 } }`, `minSdk = 24`, `ndkVersion = "27.2.12479018"`, and CMake `version = "3.22.1"`.
- **ABI Filter**: Restricted `ndk.abiFilters` to `arm64-v8a` only.
- **Release-Optimized CMake for Both Variants**: Forced `-DCMAKE_BUILD_TYPE=Release`, `-O3 -DNDEBUG` in both `debug` and `release` buildTypes so native inference benchmarks are never skewed by unoptimized Debug codegen.
- **Network & External Libs Disabled**: Set `-DLLAMA_OPENSSL=OFF`, `-DLLAMA_CURL=OFF`, `-DGGML_OPENMP=OFF`, `-DGGML_BACKEND_DL=OFF`, `-DGGML_CPU_ALL_VARIANTS=OFF`, `-DGGML_CPU_KLEIDIAI=OFF`, and `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`.
- **16 KB Page-Size Alignment**: Added `-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384` and `-DCMAKE_MODULE_LINKER_FLAGS=-Wl,-z,max-page-size=16384`.

---

## 2. `third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/CMakeLists.txt`
- Lowered `cmake_minimum_required` from `3.31.6` to `3.22.1` to match the installed Android SDK CMake.
- Disabled `GGML_CPU_KLEIDIAI` and `GGML_OPENMP` for `arm64-v8a` so the build requires zero network `FetchContent` calls and does not bundle unaligned prebuilt `libomp.so`.
- Added `add_link_options("-Wl,-z,max-page-size=16384")` and `target_link_options(ai-chat PRIVATE "-Wl,-z,max-page-size=16384")` to guarantee 16 KB ELF segment alignment on `libai-chat.so`, `libllama.so`, `libggml.so`, `libggml-base.so`, and `libggml-cpu.so`.

---

## 3. `third_party/llama.cpp/examples/llama.android/lib/src/main/cpp/ai_chat.cpp`
- **Strict Privacy (Zero Prompt/Output Logging)**: Removed all `LOGd`, `LOGi`, and `LOGv` statements that logged user prompts, system prompts, formatted chat messages, or generated token strings.
- **Configurable Native Context & Model Loading (`loadWithParams`, `prepareWithConfig`, `nativeUpdateSampler`)**:
  - Exposed `use_mmap` (`LLAMA_LOAD_MODE_MMAP` vs `LLAMA_LOAD_MODE_NONE`), `n_ctx`, `n_threads`, `n_threads_batch`, `n_batch`, `flash_attn` (`LLAMA_FLASH_ATTN_TYPE_ENABLED`), and `kv_q8_0` (`GGML_TYPE_Q8_0` for `type_k`/`type_v` with automatic fallback to `GGML_TYPE_F16` if an architecture does not support quantized KV).
  - Exposed sampler settings (`temperature`, `top_p`, `top_k`, `repeat_penalty`, and `grammar` via `COMMON_GRAMMAR_TYPE_USER` with safe fallback if grammar parsing fails).
- **Sub-500ms Native Abort (`requestAbort` + `aichat_abort_callback`)**:
  - Added `std::atomic<bool> g_abort_requested` wired to `ctx_params.abort_callback` so calling `stop()` aborts `llama_process` mid-prefill or mid-token immediately (< 50ms).
- **System Prompt KV Cache Prefix Reuse**:
  - Cached `g_last_system_prompt` and `system_prompt_position` in `processSystemPrompt` so subsequent turns with the same system prompt retain `[0, system_prompt_position)` in the KV cache and only clear `[system_prompt_position, -1)`.
- **UTF-8 Byte Streaming (`generateNextTokenBytes`)**:
  - Added `generateNextTokenBytes(): jbyteArray` returning raw `common_token_to_piece` bytes to avoid Android JNI `NewStringUTF` Modified UTF-8 (CESU-8) corruption on 4-byte emojis (`U+10000..U+10FFFF`) and multi-token CJK sequences.
- **Native Token Counter & Chat Template Inspection (`nativeCountTokens`, `hasExplicitChatTemplate`)**:
  - Exposed `common_tokenize` count for history trimming and `common_chat_templates_was_explicit` inspection.
- **`logging.h` API 24 Compatibility Guard**:
  - Guarded `__android_log_is_loggable(prio, LOG_TAG, LOG_MIN_LEVEL)` behind `#if __ANDROID_API__ >= 30` with fallback `return prio >= LOG_MIN_LEVEL;` when compiling against `minSdk = 24`.

---

## 4. `InferenceEngine.kt`, `InferenceEngineImpl.kt`, `GgufMetadataReader.kt`, `GgufMetadataReaderImpl.kt`, `consumer-rules.pro`
- **`InferenceEngine.kt` & `InferenceEngineImpl.kt`**:
  - Added `NativeLoadConfig` and extended `InferenceEngine` with `loadModel(path, config)`, `updateSampler(...)`, `sendUserPromptBytes(...)`, `abortGeneration()`, `countTokens(text)`, `getSystemInfo()`, and `hasChatTemplate()`.
  - Changed `System.loadLibrary("ai-chat")` to run synchronously in `InferenceEngineImpl.init` so `EngineProvider` can catch `UnsatisfiedLinkError` / `SecurityException` deterministically.
  - Allowed `cleanUp()` from any state without throwing `IllegalStateException`.
- **`GgufMetadataReader.kt` & `GgufMetadataReaderImpl.kt`**:
  - Removed `"tokenizer.chat_template"` from `DEFAULT_SKIP_KEYS` so `metadata.tokenizer?.chatTemplate` is read from GGUF files by default.
  - Updated `u32()` helper in `GgufMetadataReaderImpl.kt` to accept `UInt32`, `Int32`, `UInt64`, or `Int64` integer metadata types, and preserved `architectureInfo` even when optional fields (`fileType`, `vocabSize`) are absent.
- **`consumer-rules.pro`**:
  - Broadened `-keep class com.arm.aichat.** { *; }` to protect internal JNI classes under R8 full mode.

