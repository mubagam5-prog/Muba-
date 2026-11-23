const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write profiles or creations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredLanguage: "en",
      darkMode: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("creations").get()
  );
});

test("User profile: owner create, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Valid create (photoUrl omitted because Kotlin filters null values)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      email: "alice@example.com",
      preferredLanguage: "en",
      darkMode: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update of preferences
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "fr",
      darkMode: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write denied (PII isolation)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Top-level users list denied
  await assertFails(aliceDb.collection("users").get());
});

test("Creations subcollection: owner create, list, rename, delete, and security rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Empty collection list succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").get()
  );

  // Valid video creation (mirrors CreationRepository.saveCreation)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_vid_1")
      .set({
        userId: ALICE_UID,
        type: "VIDEO",
        title: "Golden sunset breeze",
        prompt: "Golden sunset breeze with glowing particles",
        motionStyle: "Cinematic Zoom",
        durationSeconds: 5,
        quality: "HD",
        localFileName: "muba_vid_1.mp4",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Valid image creation (mirrors CreationRepository.saveCreation)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_img_1")
      .set({
        userId: ALICE_UID,
        type: "IMAGE",
        title: "Minimalist purple nebula",
        prompt: "Minimalist purple nebula over obsidian peaks",
        durationSeconds: 0,
        quality: "1K",
        localFileName: "muba_img_1.jpg",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject redundant @DocumentId `id` field inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_bad_id")
      .set({
        id: "creation_bad_id",
        userId: ALICE_UID,
        type: "IMAGE",
        title: "Bad ID",
        prompt: "Test",
        durationSeconds: 0,
        quality: "1K",
        localFileName: "bad.jpg",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_future")
      .set({
        userId: ALICE_UID,
        type: "IMAGE",
        title: "Future",
        prompt: "Test",
        durationSeconds: 0,
        quality: "1K",
        localFileName: "future.jpg",
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Valid title update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_vid_1")
      .update({
        title: "Sunset Gold",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Cross-user list, get, update, delete denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("creations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_vid_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_vid_1")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("creations")
      .doc("creation_img_1")
      .delete()
  );
});
