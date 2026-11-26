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

function validMedicinePayload(uid, name, now = new Date(Date.now() - 1000)) {
  return {
    brandName: "Napa 500mg",
    normalizedBrand: "napa 500mg",
    genericName: "Paracetamol",
    companyName: "Beximco Pharmaceuticals Ltd.",
    category: "Tablet",
    boxPrice: 120.0,
    piecesPerBox: 100,
    pricePerPiece: 1.2,
    status: "approved",
    createdByUid: uid,
    createdByName: name,
    lastUpdatedByUid: uid,
    lastUpdatedByName: name,
    createdAt: now,
    updatedAt: now,
  };
}

function validUpdatePayload(uid, name, medicineId, actionType = "CREATE", now = new Date(Date.now() - 1000)) {
  return {
    medicineId,
    brandName: "Napa 500mg",
    genericName: "Paracetamol",
    companyName: "Beximco Pharmaceuticals Ltd.",
    category: "Tablet",
    boxPrice: 120.0,
    piecesPerBox: 100,
    pricePerPiece: 1.2,
    actionType,
    changeSummary: actionType === "CREATE" ? "Initial community submission" : "Updated box price",
    status: "approved",
    contributorUid: uid,
    contributorName: name,
    createdAt: now,
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

test("Unauthenticated users cannot read or write medicines or updates", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("medicines").where("status", "==", "approved").get());
  await assertFails(unauthDb.collection("medicine_updates").where("status", "==", "approved").get());
  await assertFails(
    unauthDb.collection("medicines").doc("med_napa").set(validMedicinePayload(ALICE_UID, "Alice"))
  );
});

test("Unfiltered list queries are rejected; status-filtered queries succeed for signed-in users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const medRef = aliceDb.collection("medicines").doc("med_napa");
  const updateRef = aliceDb.collection("medicine_updates").doc("upd_1");

  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      tx.set(medRef, validMedicinePayload(ALICE_UID, "Alice"));
      tx.set(updateRef, validUpdatePayload(ALICE_UID, "Alice", "med_napa", "CREATE"));
    })
  );

  // Filtered queries matching Kotlin repository
  await assertSucceeds(bobDb.collection("medicines").where("status", "==", "approved").get());
  await assertSucceeds(bobDb.collection("medicine_updates").where("status", "==", "approved").get());

  // Unfiltered queries must fail
  await assertFails(bobDb.collection("medicines").get());
  await assertFails(bobDb.collection("medicine_updates").get());
});

test("Community correction updates preserve immutable fields and reject spoofing or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("medicines").doc("med_seclo").set(validMedicinePayload(ALICE_UID, "Alice", created))
  );

  // Bob submits a valid correction
  await assertSucceeds(
    bobDb.runTransaction(async (tx) => {
      const ref = bobDb.collection("medicines").doc("med_seclo");
      const updRef = bobDb.collection("medicine_updates").doc("upd_bob_1");
      tx.update(ref, {
        genericName: "Paracetamol + Caffeine",
        boxPrice: 150.0,
        piecesPerBox: 100,
        pricePerPiece: 1.5,
        lastUpdatedByUid: BOB_UID,
        lastUpdatedByName: "Bob",
        updatedAt: new Date(Date.now() - 1000),
      });
      tx.set(updRef, validUpdatePayload(BOB_UID, "Bob", "med_seclo", "UPDATE"));
    })
  );

  // Reject spoofing lastUpdatedByUid as Alice
  await assertFails(
    bobDb.collection("medicines").doc("med_seclo").update({
      boxPrice: 160.0,
      lastUpdatedByUid: ALICE_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating immutable createdAt or brandName
  await assertFails(
    bobDb.collection("medicines").doc("med_seclo").update({
      brandName: "Hijacked Name",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("medicines").doc("med_seclo").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on create and reject delete
  await assertFails(
    aliceDb.collection("medicines").doc("med_ghost").set({
      ...validMedicinePayload(ALICE_UID, "Alice"),
      id: "med_ghost",
    })
  );
  await assertFails(aliceDb.collection("medicines").doc("med_seclo").delete());
});

test("Reject invalid category, zero/negative prices, future timestamps, and orphaned updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = validMedicinePayload(ALICE_UID, "Alice");

  await assertFails(
    aliceDb.collection("medicines").doc("med_bad_cat").set({ ...payload, category: "InvalidCat" })
  );
  await assertFails(
    aliceDb.collection("medicines").doc("med_bad_price").set({ ...payload, boxPrice: 0 })
  );
  await assertFails(
    aliceDb.collection("medicines").doc("med_bad_pieces").set({ ...payload, piecesPerBox: 0 })
  );
  await assertFails(
    aliceDb.collection("medicines").doc("med_future").set({
      ...payload,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Orphaned medicine_updates entry for nonexistent medicine must fail existsAfter check
  await assertFails(
    aliceDb.collection("medicine_updates").doc("upd_orphan").set(
      validUpdatePayload(ALICE_UID, "Alice", "med_nonexistent", "CREATE")
    )
  );
});
