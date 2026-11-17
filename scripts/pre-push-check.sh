#!/usr/bin/env bash
# ==============================================================================
# ZELKAI Local Pre-Push Syntax & Invariant Hook (scripts/pre-push-check.sh)
# Fast pre-push guard to catch syntax errors, conflict markers, and invariant
# violations before pushing to GitHub Actions (.github/workflows/android-cli.yml).
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "[pre-push] Running ZELKAI fast pre-push checks..."

# 1. Verify critical security & architectural invariant files exist
INVARIANT_FILES=(
  "app/src/main/java/com/example/domain/NexusToolAndRedactor.kt"
  "firestore.rules"
  "app/src/main/java/com/example/domain/LocalShellBridge.kt"
  "app/src/main/java/com/example/ui/nexus/TerminalInterface.kt"
  "app/google-services.json"
)

for f in "${INVARIANT_FILES[@]}"; do
  if [ ! -f "$f" ]; then
    echo "[pre-push][ERROR] Required invariant file missing: $f"
    exit 1
  fi
done

# 2. Check for unresolved git merge conflict markers in tracked Kotlin/XML/Gradle files
if git grep -n -E '^(<<<<<<<|=======|>>>>>>>)' -- '*.kt' '*.kts' '*.xml' '*.pro' 2>/dev/null; then
  echo "[pre-push][ERROR] Unresolved git merge conflict markers detected above."
  exit 1
fi

# 3. Verify package_name in google-services.json matches com.zelkai.devagent
if ! grep -q '"package_name": "com.zelkai.devagent"' app/google-services.json; then
  echo "[pre-push][ERROR] app/google-services.json is missing package_name com.zelkai.devagent"
  exit 1
fi

# 4. Optional fast Kotlin compiler check (run when --compile is passed or PREPUSH_COMPILE=1)
if [[ "${1:-}" == "--compile" ]] || [[ "${PREPUSH_COMPILE:-0}" == "1" ]]; then
  echo "[pre-push] Running fast Kotlin compiler check (:app:compileDebugKotlin)..."
  set +e
  COMPILE_OUT=$(./gradlew :app:compileDebugKotlin --console=plain 2>&1)
  STATUS=$?
  set -e
  if [ $STATUS -ne 0 ]; then
    echo "[pre-push][ERROR] Kotlin compilation failed — exact errors:"
    echo "$COMPILE_OUT" | grep -E "^e: " || echo "$COMPILE_OUT" | tail -n 35
    exit $STATUS
  fi
fi

echo "[pre-push] All pre-push checks passed."
