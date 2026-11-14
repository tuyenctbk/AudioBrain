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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validNotePayload(uid) {
  return {
    userId: uid,
    title: "EV Tax Credit Research",
    summary: "Quick voice memo from morning commute about EV tax credit rules.",
    transcript: "Check what the latest rules on EV tax credits are for 2026.",
    category: "Commute",
    durationSeconds: 42,
    actionItems: ["Verify $7,500 tax credit eligibility", "Save dealer invoice"],
    completedActionIndices: [],
    decisions: ["Use 2026 IRS clean vehicle rules for evaluation"],
    claimsToVerify: ["Check what the latest rules on EV tax credits are"],
    claimVerdicts: [],
    factCheckSummary: "",
    factCheckStatus: "none",
    searchQueries: [],
    sources: [],
    flashcards: [],
    pinned: false,
    tags: ["work", "ev-credits"],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

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

test("Unauthenticated user: cannot list or read notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").get());
});

test("Unauthenticated user: cannot create note", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set(validNotePayload(ALICE_UID))
  );
});

test("Authenticated owner: can create, list, get, update, and delete own note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1");

  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertSucceeds(noteRef.get());

  // Action 1: Toggle pin & action item completion
  await assertSucceeds(
    noteRef.update({
      completedActionIndices: [0],
      pinned: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Action 3: Update fact-check results and structured claim verdicts
  await assertSucceeds(
    noteRef.update({
      factCheckSummary: "Verified via IRS guidelines.",
      factCheckStatus: "verified",
      searchQueries: ["latest EV tax credit rules"],
      sources: [{ title: "IRS Clean Vehicle Credits", url: "https://irs.gov/credits" }],
      claimsToVerify: ["Check what the latest rules on EV tax credits are"],
      claimVerdicts: [
        {
          claim: "Federal EV credit offers up to $7,500",
          status: "supported",
          explanation: "Confirmed for qualifying clean vehicles meeting income and MSRP limits.",
        },
      ],
      updatedAt: serverTimestamp(),
    })
  );

  // Action 4: Update tags and query by tag
  await assertSucceeds(
    noteRef.update({
      tags: ["work", "ideas", "meeting"],
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes")
      .where("tags", "array-contains", "ideas")
      .get()
  );

  // Action 5: Save AI study flashcards
  await assertSucceeds(
    noteRef.update({
      flashcards: [
        {
          question: "What is the max federal new EV tax credit?",
          answer: "Up to $7,500 subject to battery sourcing, MSRP, and MAGI limits.",
        },
      ],
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's notes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceNoteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_alice");
  await assertSucceeds(aliceNoteRef.set(validNotePayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const targetRef = bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_alice");

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(targetRef.get());
  await assertFails(targetRef.update({ pinned: true, updatedAt: serverTimestamp() }));
  await assertFails(targetRef.delete());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_spoof").set(validNotePayload(BOB_UID))
  );
});

test("Dirty Dozen: rejects ghost fields, spoofed userId, invalid category, invalid verdicts, and immutable createdAt change", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_guard");

  // Ghost field
  await assertFails(
    noteRef.set({ ...validNotePayload(ALICE_UID), isAdmin: true })
  );

  // Spoofed userId
  await assertFails(
    noteRef.set(validNotePayload(BOB_UID))
  );

  // Invalid category
  await assertFails(
    noteRef.set({ ...validNotePayload(ALICE_UID), category: "InvalidCategory" })
  );

  // Create valid note, then try changing createdAt
  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID)));
  await assertFails(
    noteRef.update({
      title: "Updated Title",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Rejects > 10 tags or invalid tag types/empty tag
  await assertFails(
    noteRef.update({
      tags: ["t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10", "t11"],
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    noteRef.update({
      tags: [""],
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    noteRef.update({
      tags: [123],
      updatedAt: serverTimestamp(),
    })
  );

  // Rejects invalid verdict status or ghost key inside verdict
  await assertFails(
    noteRef.update({
      factCheckSummary: "Bad verdict status",
      factCheckStatus: "verified",
      searchQueries: [],
      sources: [],
      claimsToVerify: [],
      claimVerdicts: [{ claim: "Test", status: "bogus_status", explanation: "nope" }],
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    noteRef.update({
      flashcards: [{ question: "", answer: "Missing question" }],
      updatedAt: serverTimestamp(),
    })
  );
});

test("Messages subcollection: owner can create/list/delete under existing note; updates forbidden; cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_chat");
  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID)));

  const msgRef = noteRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "model",
      text: "Here are the latest EV tax credit rules.",
      searchQueries: ["EV tax credit 2026"],
      sources: [{ title: "IRS", url: "https://irs.gov" }],
      createdAt: serverTimestamp(),
    })
  );

  await assertSucceeds(noteRef.collection("messages").get());
  await assertFails(msgRef.update({ text: "Tampered" }));

  // Bob cannot read or write Alice's messages
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_chat").collection("messages").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_chat").collection("messages").doc("msg_2").set({
      userId: BOB_UID,
      role: "user",
      text: "Intrusion",
      createdAt: serverTimestamp(),
    })
  );

  // Orphaned message without parent note fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("nonexistent").collection("messages").doc("msg_x").set({
      userId: ALICE_UID,
      role: "user",
      text: "Orphan",
      createdAt: serverTimestamp(),
    })
  );

  await assertSucceeds(msgRef.delete());
});

test("User settings preferences: owner can save Brief/Detailed structure and Contextual Summarization toggle; invalid modes and cross-user access blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");

  // Create Brief preference with contextualSummarization enabled
  await assertSucceeds(
    prefsRef.set({
      userId: ALICE_UID,
      noteStructure: "brief",
      contextualSummarization: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Switch to Detailed preference with contextualSummarization disabled
  await assertSucceeds(
    prefsRef.set({
      userId: ALICE_UID,
      noteStructure: "detailed",
      contextualSummarization: false,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(prefsRef.get());

  // Reject invalid noteStructure value, non-bool contextualSummarization, or ghost fields
  await assertFails(
    prefsRef.set({
      userId: ALICE_UID,
      noteStructure: "invalid_mode",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    prefsRef.set({
      userId: ALICE_UID,
      noteStructure: "brief",
      contextualSummarization: "yes",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    prefsRef.set({
      userId: ALICE_UID,
      noteStructure: "brief",
      extraGhost: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Also test detectedTone validation on notes
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_tone");
  await assertSucceeds(
    noteRef.set({
      ...validNotePayload(ALICE_UID),
      detectedTone: "technical",
    })
  );
  await assertFails(
    noteRef.set({
      ...validNotePayload(ALICE_UID),
      detectedTone: "invalid_tone",
    })
  );

  // Cross-user isolation & unauthenticated access blocked
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set({
      userId: BOB_UID,
      noteStructure: "brief",
      updatedAt: serverTimestamp(),
    })
  );

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get()
  );
});

test("Fact-check search history: owner can create, update count, and list; cross-user and ghost fields blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const historyRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("fact_check_history")
    .doc("topic_ev_tax_credit");

  // Valid create
  await assertSucceeds(
    historyRef.set({
      userId: ALICE_UID,
      query: "IRS Clean Vehicle Tax Credit income limits",
      topic: "Clean Vehicle Tax Credit",
      verificationCount: 1,
      lastStatus: "supported",
      lastExplanation: "Income cap is $150k single / $300k married joint.",
      sourceUrl: "https://irs.gov/clean-vehicle",
      lastNoteId: "note_123",
      lastNoteTitle: "EV Credit Research",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Valid update (incrementing count and updating last status)
  await assertSucceeds(
    historyRef.update({
      verificationCount: 2,
      lastStatus: "updated",
      updatedAt: serverTimestamp(),
    })
  );

  // Owner can read and list
  await assertSucceeds(historyRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fact_check_history").get()
  );

  // Rejected: invalid lastStatus
  await assertFails(
    historyRef.set({
      userId: ALICE_UID,
      query: "IRS Clean Vehicle Tax Credit income limits",
      verificationCount: 1,
      lastStatus: "invalid_status",
      updatedAt: serverTimestamp(),
    })
  );

  // Rejected: negative verificationCount
  await assertFails(
    historyRef.set({
      userId: ALICE_UID,
      query: "IRS Clean Vehicle Tax Credit income limits",
      verificationCount: -1,
      lastStatus: "supported",
      updatedAt: serverTimestamp(),
    })
  );

  // Rejected: ghost fields
  await assertFails(
    historyRef.set({
      userId: ALICE_UID,
      query: "IRS Clean Vehicle Tax Credit income limits",
      verificationCount: 1,
      lastStatus: "supported",
      ghostField: "disallowed",
      updatedAt: serverTimestamp(),
    })
  );

  // Cross-user isolation: Bob cannot read or write Alice's search history
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("fact_check_history")
      .doc("topic_ev_tax_credit")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("fact_check_history")
      .doc("topic_ev_tax_credit")
      .set({
        userId: BOB_UID,
        query: "Bob query",
        verificationCount: 1,
        lastStatus: "supported",
        updatedAt: serverTimestamp(),
      })
  );

  // Unauthenticated blocked
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("fact_check_history")
      .doc("topic_ev_tax_credit")
      .get()
  );

  // Owner can delete
  await assertSucceeds(historyRef.delete());
});

