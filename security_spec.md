# Security Specification — Nexus Agent v5

## 1. Data Invariants
1. **Strict Multi-Tenant Ownership**: Every document in `/users/{userId}`, `/users/{userId}/sessions/{sessionId}`, and `/users/{userId}/sessions/{sessionId}/entries/{entryId}` belongs exclusively to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
2. **Immutable Identity & Creation Metadata**: `userId`, `sessionId` (on entries), and `createdAt` can never be mutated after document creation.
3. **Bounded Payloads**: All string and list fields have strict `.size()` bounds to prevent Denial-of-Wallet and payload injection attacks.
4. **Temporal Integrity**: `createdAt` and `updatedAt` must be server timestamps (`is timestamp && <= request.time`).

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated read/list on `/users/{userId}/sessions`.
2. Cross-user read (`alice` reading `bob`'s session or transcript entry).
3. Identity spoofing on create (`alice` creating a document with `userId: "bob"`).
4. Shadow update (injecting an undeclared field `isAdmin: true`).
5. Value poisoning (setting `maxTokens` to `"4096"` string or `9999999` out of bounds).
6. Mutating immutable `createdAt` or `userId` on update.
7. Oversized command payload (`command.size() > 2000`).
8. Invalid `type` enum on transcript entry (`type: "exec_root"`).
9. Invalid `commandStatus` enum on transcript entry (`commandStatus: "bypassed"`).
10. Unbounded `inputHistory` array (`size() > 100` or non-string first element).
11. Path variable poisoning (document ID containing spaces or >128 chars).
12. Client-side future timestamp injection (`createdAt > request.time`).
