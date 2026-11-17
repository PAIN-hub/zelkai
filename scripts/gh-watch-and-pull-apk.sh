#!/usr/bin/env bash
# ==============================================================================
# ZELKAI GitHub Actions Watcher & Error Tailer (scripts/gh-watch-and-pull-apk.sh)
# Tails the latest GitHub Actions run for the current branch, automatically
# detects failed steps and extracts narrowed e:/CMake/test errors, or downloads
# the compiled app-debug.apk to ./dist/app-debug.apk on success.
# ==============================================================================
set -euo pipefail

REPO_OWNER="PAIN-hub"
REPO_NAME="zelkai"
BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

if [ -z "$TOKEN" ] && command -v gh >/dev/null 2>&1; then
  TOKEN="$(gh auth token 2>/dev/null || true)"
fi

if [ -z "$TOKEN" ]; then
  echo "[gh-watch] Set GITHUB_TOKEN (or authenticate with 'gh auth login') to tail private/public CI logs & download artifacts."
  echo "[gh-watch] Falling back to public GitHub REST API status polling for $REPO_OWNER/$REPO_NAME ($BRANCH)..."
fi

api_get() {
  local url="$1"
  if [ -n "$TOKEN" ]; then
    curl -fsSL -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" "$url"
  else
    curl -fsSL -H "Accept: application/vnd.github+json" "$url"
  fi
}

echo "[gh-watch] Waiting for workflow run on branch '$BRANCH'..."
RUN_ID=""
for _ in $(seq 1 24); do
  RUN_JSON=$(api_get "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs?branch=$BRANCH&per_page=1")
  RUN_ID=$(echo "$RUN_JSON" | python3 -c "import sys,json; runs=json.load(sys.stdin).get('workflow_runs',[]); print(runs[0]['id'] if runs else '')" 2>/dev/null || true)
  if [ -n "$RUN_ID" ]; then
    break
  fi
  sleep 3
done

if [ -z "$RUN_ID" ]; then
  echo "[gh-watch][ERROR] No workflow run found for branch '$BRANCH'."
  exit 1
fi

echo "[gh-watch] Tracking Run ID: $RUN_ID (https://github.com/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID)"

LAST_STEP=""
while true; do
  RUN_INFO=$(api_get "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID")
  STATUS=$(echo "$RUN_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))")
  CONCLUSION=$(echo "$RUN_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin).get('conclusion') or '')")

  JOBS_JSON=$(api_get "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID/jobs")
  CURR_STEP=$(echo "$JOBS_JSON" | python3 -c "
import sys, json
jobs = json.load(sys.stdin).get('jobs', [])
if jobs:
    steps = jobs[0].get('steps', [])
    active = [s['name'] for s in steps if s.get('status') == 'in_progress']
    failed = [s['name'] for s in steps if s.get('conclusion') == 'failure']
    if failed:
        print('FAILED: ' + ', '.join(failed))
    elif active:
        print('Running: ' + active[-1])
    elif steps:
        print('Last: ' + steps[-1]['name'])
" 2>/dev/null || true)

  if [ -n "$CURR_STEP" ] && [ "$CURR_STEP" != "$LAST_STEP" ]; then
    echo "[gh-watch] $CURR_STEP"
    LAST_STEP="$CURR_STEP"
  fi

  if [ "$STATUS" = "completed" ]; then
    break
  fi
  sleep 8
done

if [ "$CONCLUSION" != "success" ]; then
  echo "[gh-watch][FAILURE] Workflow concluded with '$CONCLUSION'."
  JOB_ID=$(echo "$JOBS_JSON" | python3 -c "import sys,json; jobs=json.load(sys.stdin).get('jobs',[]); print(jobs[0]['id'] if jobs else '')" 2>/dev/null || true)
  if [ -n "$JOB_ID" ] && [ -n "$TOKEN" ]; then
    echo "[gh-watch] Fetching narrowed error diagnostics from Job $JOB_ID..."
    LOG_FILE="$(mktemp)"
    curl -fsSL -L -H "Authorization: token $TOKEN" "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/jobs/$JOB_ID/logs" -o "$LOG_FILE" || true
    grep -E "^[0-9T:.-]+Z (e: |.*FAILED|.*AssertionError|.*error:|.*fatal error:|.*CMake Error|.*<failure|.*FAILURE:)" "$LOG_FILE" | tail -n 50 || tail -n 50 "$LOG_FILE"
    rm -f "$LOG_FILE"
  else
    echo "[gh-watch] Inspect full logs at: https://github.com/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID"
  fi
  exit 1
fi

echo "[gh-watch][SUCCESS] Build & tests passed on GitHub!"
if [ -n "$TOKEN" ]; then
  mkdir -p dist
  ARTIFACTS_JSON=$(api_get "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID/artifacts")
  ZIP_URL=$(echo "$ARTIFACTS_JSON" | python3 -c "import sys,json; arts=json.load(sys.stdin).get('artifacts',[]); print(next((a['archive_download_url'] for a in arts if a['name']=='zelkai-debug-apk'), ''))" 2>/dev/null || true)
  if [ -n "$ZIP_URL" ]; then
    echo "[gh-watch] Downloading zelkai-debug-apk to ./dist/..."
    curl -fsSL -L -H "Authorization: token $TOKEN" "$ZIP_URL" -o dist/zelkai-debug-apk.zip
    unzip -o dist/zelkai-debug-apk.zip -d dist/ >/dev/null
    rm -f dist/zelkai-debug-apk.zip
    echo "[gh-watch] Ready: $(ls -lh dist/app-debug.apk)"
  fi
fi
