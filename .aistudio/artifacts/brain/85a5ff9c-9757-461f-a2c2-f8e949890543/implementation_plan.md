# Shared Storage Workspace, Termux Sync & Chat UX Upgrade (Revised Plan)

## User Review & Incorporated Amendments

All 10 user amendments have been incorporated into the design and execution plan:

1. **Path-Based Root Write Blocking (`/storage/emulated/0/Nexus`)**
   - Block **all writes** targeting the `/storage/emulated/0/Nexus` root directory by resolved path (not by command name), covering file-write tools, shell redirections, and file creation calls.
   - Allow **reads** (`ls`, `find`, `cat`, `read_file`) in `/storage/emulated/0/Nexus`. Project subfolders (`/storage/emulated/0/Nexus/<slug>`) are created exclusively through the lazy slug confirmation or project picker flow.
2. **Best-Effort Jail, Full Command Display & HIGH-Risk Config Paths**
   - Frame the project directory jail as **best-effort** static and runtime path enforcement.
   - Every approval card displays the **full, untruncated command string** and resolved working directory.
   - Any write or modification targeting `~/.bashrc`, `~/.profile`, `~/.termux/`, or `$PREFIX` (`/data/data/com.termux/files/usr`) is classified as **HIGH risk** and requires explicit user approval.
3. **Edit & Resubmit Hardening (Cancel First, Side-Effect Warning, 10s Undo, Chunked Deletes, Firestore Rules & Guest Quota)**
   - **Cancel Active Work First**: Before truncating, cancel any in-flight LLM generation, running foreground command/step, or pending approval card in that session.
   - **Disk Side-Effect Warning**: The inline editor displays a clear warning: *"Resubmitting removes later chat messages, but files written or commands already executed on disk are not undone."*
   - **10-Second Undo**: Hold truncated entries in a rollback buffer and display a 10-second `Undo` snackbar (`"Removed N messages — Undo"`). Tapping `Undo` cancels the new run and restores the previous transcript.
   - **Chunked Firestore Batch Deletes & Rules**: Delete truncated Firestore message documents in chunks of `<= 450` operations per `WriteBatch`. Update `firestore.rules` (and rule tests) to explicitly validate owner `delete` permissions on `users/{userId}/chats/{chatId}/messages/{messageId}`.
   - **Guest Quota Behavior**: Resubmitting an edited prompt consumes **1 guest prompt turn** (truncating prior messages does not refund used guest turns; if guest quota is exhausted, prompt for sign-in before truncating).
4. **One-Way Mirror Refinements (`.git` Exclude, FUSE-Safe `rsync`, `mtime+size` Pre-Check, Trailing Sync, Manifest Rebuild & Pre-Import Backup)**
   - Exclude the **entire `.git` directory** by default (with an optional Settings toggle `"Mirror .git folder"`, default `false`), and always exclude `.nexus-trash` and `.nexus`.
   - Use FUSE-safe flags: `rsync -rt --no-perms --no-owner --no-group --omit-dir-times` (avoiding `EPERM` on Android shared storage permissions/ownership/directory times), with a `cp -ru` script fallback honoring the same exclude patterns.
   - Check **`mtime + size` first** against the manifest before computing `sha256`, hashing only files whose timestamp or byte size changed.
   - Use a **trailing run queue**: if a file change occurs while a command or mirror pass is running, coalesce it into a single trailing 3s-debounced mirror pass after completion instead of dropping it.
   - **Rebuild Missing Manifest**: If `manifest.json` is missing or corrupt, reconstruct it by scanning both `$HOME/nexus_workspace/<slug>` and `/storage/emulated/0/Nexus/<slug>` (matching `size + sha256` records as `Synced`; divergent files surface as conflicts rather than being overwritten).
   - **Back Up Before Import**: Before `"Import to Termux home"` overwrites any file in `$HOME/nexus_workspace/<slug>`, save a backup copy under `$HOME/.nexus/mirrors/<slug>/backups/<timestamp>/<relativePath>`.
5. **Move to Termux Home & Clean `bin-links` Retry**
   - **Clean Before `bin-links` Retry**: When an initial npm install on shared storage fails with a symlink error (`EPERM`, `ENOSYS`, `symlink`), delete the partial `node_modules` directory (`rm -rf node_modules`) before running the single retry with `npm_config_bin_links=false`.
   - **Symlink-Specific Card Trigger**: Trigger `"This project needs symlinks. Move it to Termux home?"` **only** on symlink-specific errors (`EPERM`/`ENOSYS` on `symlink`, `ln` failure), `noexec` script/binary execution failure on shared storage (`Permission denied` executing `./` or `.bin/*`), or missing `node_modules/.bin/*`—**never** on a bare unrelated `EACCES`.
   - **No `node_modules` Copy on Move**: When moving a project to `$HOME/nexus_workspace/<slug>`, do **not** copy `node_modules` (or `.venv`); copy only source files and run a clean fresh install in Termux home. Offer a checkbox/button to delete the leftover partial `node_modules` in `/storage/emulated/0/Nexus/<slug>/node_modules`.
6. **Finished-Only Text Selection & `DisableSelection` on Action Rows**
   - Wrap message text in `SelectionContainer` **only when the message is finished (`!entry.isStreaming`)**; render streaming messages as plain `Text` to eliminate selection handle resets during token streaming.
   - Wrap all message action rows (`Edit`, `Copy`, metadata badges, code block header bars) in `DisableSelection { ... }`.
7. **Slug-Only Cross-Device Binding, `termuxHomePath`, Legacy Migration & Missing Project Recovery**
   - Sync only `projectSlug: String?` and `storageLocation` across devices; resolve `sharedPath` (`/storage/emulated/0/Nexus/<slug>`) and `termuxHomePath` (`$HOME/nexus_workspace/<slug>`) locally per device.
   - Migrate existing sessions without a binding to `Unassigned` (`projectSlug = null`).
   - If a bound project's folder is missing on disk, show `Project: <slug> (Missing)` in the header and prompt on the next write/command to `Recreate project folder`, `Choose another project`, or `Switch to Unassigned`.
8. **Slug Sanitization, Collision Handling, Read-Only `Unassigned` CWD & Project Switch Guard**
   - Sanitize slugs to `[a-z0-9-]` (lowercase, max 40 chars, collapse non-alphanumerics to `-`, trim `-`, block `.` and `..`). Handle folder collisions by offering `Bind to existing <slug>` or creating `<slug>-2` (`-3`, etc.).
   - While a session is `Unassigned`, read-only commands execute with `cwd = /storage/emulated/0/Nexus`.
   - Block switching projects while a command or background job start is actively running, and post a transcript notice (`"Switched project to <slug> (cwd: <path>)"`) whenever the bound project changes.
   - Exclude `.nexus-trash` from all mirror and conflict scans.
9. **Temporary Scratch Cleanup: Stop Jobs First & 24h Orphan Sweep**
   - When ending or deleting a temporary chat, first stop all `JobManager` jobs running under `$HOME/.nexus/scratch/<tempSessionId>`, then delete the scratch folder.
   - On app startup, sweep `$HOME/.nexus/scratch/` for orphan scratch folders older than 24 hours (`mtime > 24h`), stopping any associated jobs and deleting the folders.
10. **Three-Run Sequential Implementation, Acceptance Criteria & Manual Device Test Checklist**
    - Execute implementation in 3 sequential, build-and-test-verified runs:
      - **Run A**: Selection Fix + Inline Edit & Resubmit
      - **Run B**: Workspace Root, Session Binding, Best-Effort Jail, Lazy Slug Flow, Pre-Flight Probe & Temp Scratch Lifecycle
      - **Run C**: Clean `bin-links` Retry, Symlink Fallback Card & One-Way Source Mirroring Engine

---

## 1. Overview & Core Concept

Nexus Agent v5 establishes `/storage/emulated/0/Nexus` as the shared-storage workspace root for both `AndroidShellBackend` and `TermuxBackend`, binding each chat session to an isolated `/storage/emulated/0/Nexus/<slug>` project directory created lazily on first write. When projects encounter Android FUSE `noexec` or symlink constraints, Nexus cleanly retries once with `npm_config_bin_links=false` (after cleaning partial `node_modules`) and, if symlink/execution errors persist, offers an approval-gated move to `$HOME/nexus_workspace/<slug>` backed by a trailing-debounced, conflict-aware one-way source mirror back to `/storage/emulated/0/Nexus/<slug>`. In parallel, the chat transcript is upgraded with flicker-free text selection on finished messages and an inline prompt editor that cancels active steps, truncates later history with a 10-second Undo window, and resubmits.

---

## 2. User Experience & Visual Design

### A. Flicker-Free Text Selection & Inline Prompt Reprompting (Run A)
- **Finished-Only `SelectionContainer`**:
  - Completed user and assistant messages wrap their prose/markdown body in a single `SelectionContainer`, while streaming messages render without `SelectionContainer` until completion.
  - Action bars (`Edit`, `Copy`), timestamps, and code-block toolbars are wrapped in `DisableSelection` so selection handles never snag on UI chrome.
- **Edit & Resubmit Flow**:
  - Accessible via both an inline `Edit` button beneath each user bubble and a long-press menu (`Edit & Resubmit`, `Copy text`).
  - Opens an inline editor showing: *"Resubmitting removes N later messages from chat history. Files written or commands already run on disk are not reverted."*
  - On submit: cancels any active streaming/tool step or pending approval, truncates later messages, shows a **10-second `Undo` snackbar**, and restarts generation from the edited prompt.

### B. Project Binding Header, Lazy Slug Bar & Pre-Flight Cards (Run B)
- **Header Chips**:
  - Shows `Project: Unassigned`, `Project: <slug>`, or `Project: <slug> (Missing)` in the chat top bar.
  - Project switching is disabled while a command is running; when switched, a system notice is appended to the chat transcript.
- **Lazy Slug Confirmation Card**:
  - Triggered on the first write or project command in an `Unassigned` chat. Proposes a sanitized slug (`[a-z0-9-]`), warns on name collisions (`Use existing` vs `Create <slug>-2`), and lets the user confirm or rename in one tap.
- **Pre-Flight `WorkingDirUnreachable` Card**:
  - Displayed if `test -d` / `test -w` or probe file creation fails before the first session command, offering `Grant All Files Access`, `termux-setup-storage guide`, `Use Termux home`, and `Copy diagnostics`.

### C. Symlink Fallback & Conflict Resolution Cards (Run C)
- **Smart Symlink Fallback Card**:
  - Title: **"This project needs symlinks. Move it to Termux home?"**
  - Triggered only on symlink-specific (`EPERM`/`ENOSYS` on symlink) or shared-storage `noexec`/`.bin` failures after a clean `--no-bin-links` retry.
  - Copies project sources (excluding `node_modules` and `.venv`) to `$HOME/nexus_workspace/<slug>`, optionally deletes the broken shared `node_modules`, and runs a fresh install in Termux home.
- **Mirror Status Chip & Conflict Card**:
  - Header chip displays `Synced • <time ago>`, `Syncing…`, `Out of sync`, or `Conflict`.
  - If a shared file was modified externally (`mtime+size` -> `sha256` mismatch against manifest), shows **"Changes found in the shared folder"** with `Import to Termux home` (backing up Termux copy first), `Overwrite shared copy`, `Keep both (file.shared-<timestamp>)`, and `Skip`.

---

## 3. Technical Architecture & Phased Execution Plan

```
┌───────────────────────────────────────────────────────────────────────────┐
│                   Run A: Chat Selection & Edit/Resubmit                   │
│  - Finished-only SelectionContainer + DisableSelection on action bars     │
│  - Cancel active step/approval -> 10s Undo buffer -> Chunked batch delete │
│  - firestore.rules message delete permission + guest quota accounting     │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│            Run B: Workspace Binding, Best-Effort Jail & Lifecycle         │
│  - ProjectWorkspaceBinding (sync slug only; resolve shared/termuxHomePath)│
│  - Unassigned read-only cwd = /storage/emulated/0/Nexus; block root write │
│  - Sanitize slug [a-z0-9-] + collision handling + missing folder recovery │
│  - HIGH-risk classification for ~/.bashrc, ~/.profile, ~/.termux, $PREFIX │
│  - Temp scratch ~/.nexus/scratch/<id>: stop jobs before delete + 24h sweep│
│  - JobManager logs in ~/.nexus/jobs/<id>; pre-flight probe; secret warning│
└────────────────────────────────────┬──────────────────────────────────────┘
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│          Run C: Clean Bin-Links Retry, Fallback Card & Mirror Engine      │
│  - Clean partial node_modules before single npm_config_bin_links=false    │
│  - Move to Termux home without node_modules + fresh install + cleanup opt │
│  - rsync -rt --no-perms --no-owner --no-group --omit-dir-times / cp -ru   │
│  - Exclude .git (toggle), .nexus-trash, node_modules, .venv, secrets      │
│  - mtime+size pre-check -> sha256 -> trailing 3s run -> pre-import backup │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Models & Security Rules
- **`ProjectWorkspaceBinding`**:
  - Stored fields (synced): `sessionId: String`, `projectSlug: String?` (`null` = `Unassigned`), `storageLocation: SHARED_STORAGE | TERMUX_HOME_MIRRORED | TEMP_SCRATCH`, `fallbackDecision: NONE | MOVED_TO_TERMUX | KEEP_IN_SHARED`, `secretWarningShown: Boolean`.
  - Computed per device: `sharedPath = "/storage/emulated/0/Nexus/$projectSlug"`, `termuxHomePath = "/data/data/com.termux/files/home/nexus_workspace/$projectSlug"`, `activeCwd` (returns `/storage/emulated/0/Nexus` for read-only commands when `projectSlug == null`).
- **`Firestore Rules` (`firestore.rules`)**:
  - Verify `match /users/{userId}/chats/{chatId}/messages/{messageId}` allows `read, create, update, delete: if isOwner(userId)` so chunked truncation batch deletes succeed under zero-trust security rules.

---

## 4. Acceptance Criteria

1. **Selection & Copy**: Long-pressing any finished user or assistant message opens the native Android text selection handles without vanishing; action buttons and code headers are excluded via `DisableSelection`; streaming messages do not flicker.
2. **Edit & Resubmit**: Editing a prompt cancels any running generation/step/approval, warns that disk changes persist, truncates all subsequent messages, offers a 10-second `Undo` snackbar, deletes Firestore documents in chunks of `<= 450`, enforces guest quota, and restarts execution from the edited prompt.
3. **Root Protection & Best-Effort Jail**: All write operations directly in `/storage/emulated/0/Nexus` are blocked by path resolution while reads succeed; `..` traversal and `/data/data/*` (outside Termux home) are rejected; writes to `~/.bashrc`, `~/.profile`, `~/.termux/`, or `$PREFIX` trigger HIGH-risk approval cards showing the full command.
4. **Session Binding & Lazy Slug**: Existing sessions migrate to `Unassigned`; plain Q&A chats create no folder; first write/command proposes a sanitized `[a-z0-9-]` slug with collision handling; missing project folders show `Project: <slug> (Missing)` with recovery options; project switching is blocked during running commands and logs a transcript notice.
5. **Temp Scratch & Jobs**: Temporary chats use `$HOME/.nexus/scratch/<id>`, stop associated `JobManager` jobs before deleting the folder on exit, and sweep orphans `>24h` old on app start. All `JobManager` logs/PIDs live in `$HOME/.nexus/jobs/<id>/`.
6. **Clean Retry & Move to Termux Home**: Failed shared-storage npm installs clean partial `node_modules` and retry once with `npm_config_bin_links=false`. Persistent symlink/`noexec`/`.bin` errors surface the Smart Fallback Card, which moves source files (excluding `node_modules`), offers shared `node_modules` cleanup, and runs a fresh install in Termux home.
7. **One-Way Source Mirror**: Uses `rsync -rt --no-perms --no-owner --no-group --omit-dir-times` (or `cp -ru` fallback), excludes whole `.git` (with toggle), `.nexus-trash`, `node_modules`, `.venv`, and secret files, runs a trailing 3s-debounced sync on changes, checks `mtime+size` before `sha256`, rebuilds missing manifests safely, stages deletions in `.nexus-trash/<timestamp>/`, and backs up Termux-home files before importing shared conflicts.
8. **Automated Verification**: `compile_applet` and `gradle :app:testDebugUnitTest` pass with unit tests covering all path rules, truncation/batching, symlink retry/fallback, job paths, and mirror conflict/trash logic.

---

## 5. Manual Device Test Checklist

1. **Long-Press Copy Test**: Send a prompt, wait for the assistant reply to finish streaming, and long-press a word in both the user bubble and assistant prose/code block. Confirm the selection handles stay visible, dragging adjusts selection, and tapping `Copy` copies only the selected text (excluding `Edit`/`Copy` button labels).
2. **Edit, Resubmit & 10s Undo Test**: In a 4-turn conversation, tap `Edit` on Turn 1, verify the disk side-effect warning, submit a modified prompt, and tap `Undo` within 10 seconds—confirm Turns 2–4 reappear. Edit Turn 1 again, let the 10s window expire, and confirm Turns 2–4 are permanently removed and the new response streams in.
3. **Plain Q&A vs. Lazy Slug Creation**: Start a new chat (`Project: Unassigned`), ask a general coding question, and verify no folder is created in `/storage/emulated/0/Nexus`. Next, ask the agent to create `index.js`, confirm the proposed slug card appears, tap `Confirm & Run`, and verify `/storage/emulated/0/Nexus/<slug>/index.js` is created.
4. **Root Write Block & HIGH-Risk Approval**: Attempt to write a file directly to `/storage/emulated/0/Nexus/test.txt` (verify blocked by path) and attempt a command modifying `~/.bashrc` (verify HIGH-risk approval card shows the full command).
5. **Pre-Flight & Symlink Fallback -> Mirror Test**: Run an npm package installation requiring bin symlinks in `/storage/emulated/0/Nexus/<slug>`. Confirm the single clean retry with `npm_config_bin_links=false`, approve `"Move to Termux home"` on the fallback card, verify `node_modules` is excluded from the copy and freshly installed in `$HOME/nexus_workspace/<slug>`, and confirm source edits mirror back to `/storage/emulated/0/Nexus/<slug>` with the header chip updating to `Synced`.
6. **External Conflict & Backup Test**: Edit a mirrored source file directly inside `/storage/emulated/0/Nexus/<slug>` using an external editor, then run a command in Termux home that touches the project. Verify the `"Changes found in the shared folder"` card appears, select `"Import to Termux home"`, and verify a backup copy was saved under `$HOME/.nexus/mirrors/<slug>/backups/`.
