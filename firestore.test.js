const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validSettings(uid, now) {
  return {
    userId: uid,
    provider: "gemini",
    selectedModel: "gemini-3.8-flash",
    maxTokens: 4096,
    maxSteps: 6,
    timeoutSec: 10,
    agentPersona: "You are a Linux system agent.",
    activeSessionId: "session_1",
    inputHistory: ["ls -la", "df -h"],
    createdAt: now,
    updatedAt: now,
  };
}

function validSession(uid, now) {
  return {
    userId: uid,
    title: "Main Shell Session",
    status: "idle",
    cwd: "/data/user/0/com.aistudio.nexusagent.v5sync/files",
    commandsCount: 0,
    redactionsCount: 0,
    provider: "Gemini",
    model: "gemini-3.8-flash",
    createdAt: now,
    updatedAt: now,
  };
}

function validEntry(uid, sessionId, now) {
  return {
    userId: uid,
    sessionId: sessionId,
    type: "command",
    text: "",
    command: "ls -la",
    reason: "List directory contents",
    commandStatus: "pending",
    isRisky: false,
    timeoutSec: 10,
    cwd: "/data/user/0/com.aistudio.nexusagent.v5sync/files",
    exitCode: 0,
    output: "",
    redactions: 0,
    noticeKind: "",
    sequence: 1,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read settings or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Authenticated user: can create and read own settings, session, and transcript entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validSettings(ALICE_UID, now))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").set(validSession(ALICE_UID, now))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("session_1")
      .collection("entries")
      .doc("entry_1")
      .set(validEntry(ALICE_UID, "session_1", now))
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's session or entries", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").set(validSession(ALICE_UID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("session_1")
      .collection("entries")
      .doc("entry_1")
      .set(validEntry(ALICE_UID, "session_1", now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("session_1")
      .collection("entries")
      .doc("entry_1")
      .get()
  );
});

test("Shadow update and value poisoning are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validSettings(ALICE_UID, now))
  );

  // Ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Out-of-range maxSteps
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      maxSteps: 99,
      updatedAt: new Date(),
    })
  );
});

test("Client query alignment: can list own sessions and entries with userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").set(validSession(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Synced profile, personalization, and skills fields validate properly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validSettings(ALICE_UID, now),
      displayName: "Ada Lovelace",
      avatarDataUri: "data:image/webp;base64,UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=",
      fontSize: "default",
      responseStyle: "technical",
      personaPreset: "senior_devops",
      aboutUser: "Staff Linux kernel & Android engineer",
      responsePreference: "Prefer concise bash and POSIX flags",
      enableForNewChats: true,
      skillsJson: "[]",
      skillsTokenBudget: 2000,
      recentWorkspaces: ["/data/user/0/com.aistudio.nexusagent.v5sync/files"],
    })
  );

  // Reject out-of-range skillsTokenBudget
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      skillsTokenBudget: 99999,
      updatedAt: new Date(),
    })
  );
});

test("Edit & Resubmit truncation: owner can batch-delete subsequent entries while other users cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_edit_1");
  await assertSucceeds(
    sessionRef.set({
      ...validSession(ALICE_UID, now),
      projectSlug: "express-api",
      storageLocation: "shared_storage",
    })
  );

  const e1 = sessionRef.collection("entries").doc("entry_1");
  const e2 = sessionRef.collection("entries").doc("entry_2");
  const e3 = sessionRef.collection("entries").doc("entry_3");
  await assertSucceeds(e1.set({ ...validEntry(ALICE_UID, "session_edit_1", now), type: "user", text: "Prompt 1", sequence: 1 }));
  await assertSucceeds(e2.set({ ...validEntry(ALICE_UID, "session_edit_1", now), type: "agent", text: "Reply 1", sequence: 2 }));
  await assertSucceeds(e3.set({ ...validEntry(ALICE_UID, "session_edit_1", now), type: "user", text: "Prompt 2", sequence: 3 }));

  // Bob cannot delete Alice's entries
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_edit_1").collection("entries").doc("entry_2").delete()
  );

  // Alice can batch-delete entries 2 and 3 during Edit & Resubmit
  const batch = aliceDb.batch();
  batch.delete(e2);
  batch.delete(e3);
  await assertSucceeds(batch.commit());

  // Invalid projectSlug with path traversal characters is rejected
  await assertFails(
    sessionRef.update({
      projectSlug: "../escape",
      updatedAt: new Date(),
    })
  );
});


