# Bundled `llama.cpp` Version

Nexus Agent v5 bundles `llama.cpp` for on-device GGUF inference on `arm64-v8a` Android devices.

## Pinned Release Metadata

| Field | Value |
| :--- | :--- |
| **Upstream Repository** | `https://github.com/ggml-org/llama.cpp` |
| **Pinned Release Tag** | `b12020` |
| **Commit SHA** | `0ec4027a2a0183de87c007069adddc06e665f7de` |
| **Release Commit Date** | `2026-10-30 22:13:48 -0700` (`2026-10-31T05:13:48Z`) |
| **Integration Date** | `2026-10-31` |
| **Vendored Location** | `third_party/llama.cpp` |
| **Android Library Module** | `:llama` (`third_party/llama.cpp/examples/llama.android/lib`) |
| **Target ABI** | `arm64-v8a` |
| **ELF Page Size Alignment** | `16 KB` (`-Wl,-z,max-page-size=16384`) |
| **License** | MIT License (`third_party/llama.cpp/LICENSE`) |

## Build & Upgrade Policy

1. **Never track `master`**: Always pin to an explicit `bXXXX` release tag and record the tag, commit SHA, and date in this file.
2. **Release-Optimized Native Builds**: Both `debug` and `release` Android build variants compile `llama.cpp` with `-DCMAKE_BUILD_TYPE=Release` (`-O3 -DNDEBUG`) so on-device token generation and benchmarks reflect real hardware capability.
3. **Minimal Patch Footprint**: Any modification to `third_party/llama.cpp/examples/llama.android/lib` required by `LlamaEngine` is documented in [`docs/LLAMA_PATCHES.md`](LLAMA_PATCHES.md).
4. **Zero Network in Native Layer**: `LLAMA_OPENSSL=OFF` and `LLAMA_CURL=OFF` are enforced at CMake configuration time; all GGUF downloads and SHA-256 checks are managed in Kotlin by `ModelDownloadService`.

