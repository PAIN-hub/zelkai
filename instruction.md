# Nexus Agent v5 — Build, Test & Runtime Instructions

This document covers environment prerequisites, dependency setup, native `llama.cpp` build configuration, unit/instrumented test execution, and 16 KB ELF alignment verification for **Nexus Agent v5**.

---

## 1. Prerequisites & Environment Setup

- **JDK**: Java 17+ (OpenJDK 17 or 21 recommended)
- **Android SDK**:
  - `compileSdk = 35` (`android-35`)
  - `build-tools = 36.1.0` (or `35.0.0+`)
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
Executes `LlamaRuntimeUnitTest` covering `FakeEngine`, `EngineProvider` selection & fallback, `LlamaEngine` load guards (missing file, corrupt GGUF magic, size mismatch, unsupported architecture, OOM guard, `<500ms` stop, `onTrimMemory`, idle timeout), `<think>...</think>` streaming parser, multi-byte UTF-8 boundary decoding, history token trimming, and `NexusViewModel` typed error card mapping:

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

