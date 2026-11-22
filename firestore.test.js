const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

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

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any SplitFlow collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacker",
      defaultCurrency: "INR",
      darkMode: false,
      seededDemo: false,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("UserProfile: owner can create and update profile; rejects ghost fields and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Arjun Mehta",
      email: "arjun@example.com",
      upiVpa: "arjun@okaxis",
      defaultCurrency: "INR",
      darkMode: false,
      seededDemo: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update mutable settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkMode: true,
      upiVpa: "arjun.mehta@okhdfcbank",
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject cross-user read and write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
});

test("LedgerMember: create friend/guest, claim guest profile, reject cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Empty list query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("members").get()
  );

  // Create shadow guest profile
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("members")
      .doc("mem_kabir")
      .set({
        userId: ALICE_UID,
        name: "Kabir Sharma",
        avatarColorHex: "#4F46E5",
        selfProfile: false,
        guestProfile: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Claim guest profile -> verified member
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("members")
      .doc("mem_kabir")
      .update({
        name: "Kabir Sharma",
        email: "kabir@example.com",
        upiVpa: "kabir@ybl",
        guestProfile: false,
        claimedAtLabel: "2026-11-21",
        updatedAt: pastDate(1000),
      })
  );

  // Cross-user list denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("members").get()
  );
});

test("ExpenseGroup, ExpenseRecord & SettlementRecord: Kotlin-aligned payloads, validation bounds, and future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // Create group
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_goa")
      .set({
        userId: ALICE_UID,
        name: "Goa Weekend Trip",
        category: "TRIP",
        currency: "INR",
        memberIds: ["mem_self", "mem_kabir", "mem_ananya"],
        accentColorHex: "#0D9488",
        archived: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create itemized expense with minor units (no `id` field!)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp_thalassa")
      .set({
        userId: ALICE_UID,
        groupId: "grp_goa",
        title: "Thalassa Coastal Kitchen",
        notes: "Sunset dinner in Vagator",
        category: "FOOD",
        currency: "INR",
        totalMinorUnits: 286000,
        splitType: "ITEMIZED",
        dateLabel: "2026-11-18",
        shareCode: "goa-thalassa-2860",
        aiExtracted: true,
        aiConfidenceNote: "Extracted 5 items + 5% GST + 5% service charge",
        settled: false,
        payers: [{ memberId: "mem_self", amountMinorUnits: 286000 }],
        allocations: [
          { memberId: "mem_self", amountMinorUnits: 143000, percentageBps: 5000, shares: 1 },
          { memberId: "mem_kabir", amountMinorUnits: 143000, percentageBps: 5000, shares: 1 },
        ],
        lineItems: [
          {
            itemId: "li_1",
            name: "Goan Prawn Curry",
            quantity: 1,
            unitPriceMinor: 68000,
            totalPriceMinor: 68000,
            assignedMemberIds: ["mem_self", "mem_kabir"],
          },
        ],
        taxMinorUnits: 13000,
        serviceChargeMinorUnits: 13000,
        discountMinorUnits: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject extra @DocumentId `id` field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp_bad_id")
      .set({
        id: "exp_bad_id",
        userId: ALICE_UID,
        title: "Invalid Extra ID",
        category: "FOOD",
        currency: "INR",
        totalMinorUnits: 10000,
        splitType: "EQUAL",
        dateLabel: "2026-11-18",
        shareCode: "bad-id-test",
        aiExtracted: false,
        settled: false,
        payers: [{ memberId: "mem_self", amountMinorUnits: 10000 }],
        allocations: [{ memberId: "mem_self", amountMinorUnits: 10000, percentageBps: 10000, shares: 1 }],
        lineItems: [],
        taxMinorUnits: 0,
        serviceChargeMinorUnits: 0,
        discountMinorUnits: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settlements")
      .doc("set_future")
      .set({
        userId: ALICE_UID,
        groupId: "grp_goa",
        fromMemberId: "mem_kabir",
        toMemberId: "mem_self",
        amountMinorUnits: 143000,
        currency: "INR",
        paymentMethod: "UPI",
        note: "Future tampered",
        dateLabel: "2026-11-21",
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Valid settlement succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settlements")
      .doc("set_valid")
      .set({
        userId: ALICE_UID,
        groupId: "grp_goa",
        fromMemberId: "mem_kabir",
        toMemberId: "mem_self",
        amountMinorUnits: 143000,
        currency: "INR",
        paymentMethod: "UPI",
        note: "Paid via GPay UPI",
        dateLabel: "2026-11-21",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject self-to-self settlement
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settlements")
      .doc("set_self")
      .set({
        userId: ALICE_UID,
        fromMemberId: "mem_self",
        toMemberId: "mem_self",
        amountMinorUnits: 5000,
        currency: "INR",
        paymentMethod: "CASH",
        dateLabel: "2026-11-21",
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("SplitFlow v2 fields: group budgets, recurring bills, FX rates, and receipt paths validate cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_budget")
      .set({
        userId: ALICE_UID,
        name: "Tokyo Autumn Trip",
        category: "TRIP",
        currency: "USD",
        memberIds: ["mem_self", "mem_ananya"],
        accentColorHex: "#14B8A6",
        archived: false,
        budgetMinorUnits: 250000,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_budget")
      .update({
        budgetMinorUnits: 300000,
        updatedAt: pastDate(1000),
      })
  );

  // Reject negative budget cap
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_budget")
      .update({
        budgetMinorUnits: -500,
        updatedAt: pastDate(500),
      })
  );

  // Valid recurring + FX + receipt photo expense
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp_v2")
      .set({
        userId: ALICE_UID,
        groupId: "grp_budget",
        title: "Spotify Duo & Cloud Storage",
        category: "UTILITIES",
        currency: "USD",
        totalMinorUnits: 1999,
        splitType: "EQUAL",
        dateLabel: "2026-11-21",
        shareCode: "rec-spotify-1999",
        aiExtracted: false,
        settled: false,
        payers: [{ memberId: "mem_self", amountMinorUnits: 1999 }],
        allocations: [
          { memberId: "mem_self", amountMinorUnits: 1000, percentageBps: 5000, shares: 1 },
          { memberId: "mem_ananya", amountMinorUnits: 999, percentageBps: 5000, shares: 1 },
        ],
        lineItems: [],
        taxMinorUnits: 0,
        serviceChargeMinorUnits: 0,
        discountMinorUnits: 0,
        recurringFrequency: "MONTHLY",
        nextDueDateLabel: "2026-12-21",
        receiptPhotoPath: "receipts/rcpt_demo.jpg",
        fxRateToBaseMicros: 84000000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject invalid recurring frequency enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp_v2_bad")
      .set({
        userId: ALICE_UID,
        title: "Bad Recurrence",
        category: "UTILITIES",
        currency: "USD",
        totalMinorUnits: 1000,
        splitType: "EQUAL",
        dateLabel: "2026-11-21",
        shareCode: "bad-rec-1000",
        aiExtracted: false,
        settled: false,
        payers: [{ memberId: "mem_self", amountMinorUnits: 1000 }],
        allocations: [{ memberId: "mem_self", amountMinorUnits: 1000, percentageBps: 10000, shares: 1 }],
        lineItems: [],
        taxMinorUnits: 0,
        serviceChargeMinorUnits: 0,
        discountMinorUnits: 0,
        recurringFrequency: "HOURLY",
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("SplitFlow v3 fields: THB currency, group splitMemoryNotes, and settlement paymentProofPath validate cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_phuket")
      .set({
        userId: ALICE_UID,
        name: "Phuket Island Hopping",
        category: "TRIP",
        currency: "THB",
        memberIds: ["mem_self", "mem_kabir"],
        accentColorHex: "#14B8A6",
        archived: false,
        budgetMinorUnits: 4500000,
        splitMemoryNotes: "Kabir is vegetarian — skip seafood; boat charters split 50/50",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_phuket")
      .update({
        splitMemoryNotes: "Kabir vegetarian; scooters charged to riders only",
        updatedAt: pastDate(1000),
      })
  );

  // Reject oversized splitMemoryNotes (>400 chars)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("groups")
      .doc("grp_phuket")
      .update({
        splitMemoryNotes: "x".repeat(401),
        updatedAt: pastDate(500),
      })
  );

  // Valid THB partial settlement with paymentProofPath
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settlements")
      .doc("set_v3_proof")
      .set({
        userId: ALICE_UID,
        groupId: "grp_phuket",
        fromMemberId: "mem_kabir",
        toMemberId: "mem_self",
        amountMinorUnits: 120000,
        currency: "THB",
        paymentMethod: "UPI",
        note: "50% milestone transfer",
        paymentProofPath: "receipts/proof_upi_01.jpg",
        dateLabel: "2026-11-21",
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Dining & Drinks preferences: LedgerMember dietPreference and drinksAlcohol validate cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("members")
      .doc("mem_veg_guest")
      .set({
        userId: ALICE_UID,
        name: "Neha Kulkarni",
        avatarColorHex: "#10B981",
        selfProfile: false,
        guestProfile: true,
        dietPreference: "VEG",
        drinksAlcohol: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Update diet & alcohol preference
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("members")
      .doc("mem_veg_guest")
      .update({
        dietPreference: "EGGETARIAN",
        drinksAlcohol: true,
        updatedAt: pastDate(1000),
      })
  );

  // Reject invalid diet enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("members")
      .doc("mem_veg_guest")
      .update({
        dietPreference: "KETO_INVALID",
        updatedAt: pastDate(500),
      })
  );
});



