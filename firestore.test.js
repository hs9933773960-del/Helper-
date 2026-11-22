const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const EMPLOYER_UID = "employer_101";
const WORKER_UID = "worker_202";
const OTHER_UID = "other_303";
const ADMIN_UID = "admin_999";
const ADMIN_EMAIL = "sktauseef938433@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("jobs").where("status", "==", "OPEN").get());
  await assertFails(unauthDb.collection("users").doc(EMPLOYER_UID).get());
  await assertFails(unauthDb.collection("app_config").doc("payments").get());
  await assertFails(
    unauthDb.collection("jobs").doc("job_1").set({
      employerId: EMPLOYER_UID,
      title: "Hack",
    })
  );
});

test("Users & Public Profiles: PII isolation, self-profile write, and admin moderation", async () => {
  const employerDb = testEnv.authenticatedContext(EMPLOYER_UID).firestore();
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  const ts = pastDate();
  await assertSucceeds(
    employerDb.collection("users").doc(EMPLOYER_UID).set({
      userId: EMPLOYER_UID,
      displayName: "Ramesh Das",
      phoneNumber: "9876543210",
      phoneVerified: true,
      email: "ramesh@example.com",
      role: "EMPLOYER",
      city: "Kolkata",
      area: "Salt Lake",
      skills: "",
      bio: "Shop owner",
      language: "BN",
      blocked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    employerDb.collection("public_profiles").doc(EMPLOYER_UID).set({
      userId: EMPLOYER_UID,
      displayName: "Ramesh Das",
      role: "EMPLOYER",
      city: "Kolkata",
      area: "Salt Lake",
      skills: "",
      bio: "Shop owner",
      phoneVerified: true,
      blocked: false,
      ratingSum: 0,
      ratingCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Worker cannot read Employer's private PII user doc or list /users
  await assertFails(workerDb.collection("users").doc(EMPLOYER_UID).get());
  await assertFails(workerDb.collection("users").get());

  // Worker CAN read public_profiles (filtered by blocked == false)
  await assertSucceeds(workerDb.collection("public_profiles").doc(EMPLOYER_UID).get());
  await assertSucceeds(
    workerDb.collection("public_profiles").where("blocked", "==", false).get()
  );
  // Unfiltered public_profiles list fails
  await assertFails(workerDb.collection("public_profiles").get());

  // User cannot unblock/block themselves or tamper with ratingCount
  await assertFails(
    employerDb.collection("users").doc(EMPLOYER_UID).update({
      blocked: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    employerDb.collection("public_profiles").doc(EMPLOYER_UID).update({
      ratingSum: 50,
      ratingCount: 10,
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list users and block/unblock
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(EMPLOYER_UID).update({
      blocked: true,
      updatedAt: pastDate(1000),
    })
  );

  // Spoofed admin email with email_verified=false MUST FAIL
  const spoofDb = testEnv
    .authenticatedContext("spoof_1", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofDb.collection("users").get());
});

test("App Payment Config: only Admin can write; signed-in users can read", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertFails(
    workerDb.collection("app_config").doc("payments").set({
      adminName: "Fake Admin",
      upiId: "fake@upi",
      accountNumber: "1234",
      ifscCode: "SBIN0001",
      phonePeNumber: "9999999999",
      employerBookingFee: 10,
      workerAcceptFee: 10,
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    adminDb.collection("app_config").doc("payments").set({
      adminName: "Helper Adda Admin",
      upiId: "helperadda@upi",
      accountNumber: "348900112233",
      ifscCode: "SBIN0001234",
      phonePeNumber: "9830012345",
      employerBookingFee: 10,
      workerAcceptFee: 10,
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(workerDb.collection("app_config").doc("payments").get());
});

test("Jobs, Job Contacts, Payments, Chats & Reviews full workflow", async () => {
  const employerDb = testEnv.authenticatedContext(EMPLOYER_UID).firestore();
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  const ts = pastDate(10000);

  // Setup public profiles for review target
  await assertSucceeds(
    employerDb.collection("public_profiles").doc(EMPLOYER_UID).set({
      userId: EMPLOYER_UID,
      displayName: "Ramesh Das",
      role: "EMPLOYER",
      city: "Kolkata",
      area: "Salt Lake",
      skills: "",
      bio: "",
      phoneVerified: true,
      blocked: false,
      ratingSum: 0,
      ratingCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 1. Employer posts job + contact + ₹10 booking payment
  await assertSucceeds(
    employerDb.collection("jobs").doc("job_1").set({
      employerId: EMPLOYER_UID,
      employerName: "Ramesh Das",
      title: "Electrician Needed Today",
      description: "Need wiring check and ceiling fan repair",
      category: "Electrician",
      jobType: "DAILY",
      wageAmount: 700,
      wagePeriod: "PER_DAY",
      city: "Kolkata",
      area: "Salt Lake",
      status: "OPEN",
      bookingFeePaid: true,
      bookingFeeRef: "PP12345678",
      workerFeePaid: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    employerDb.collection("job_contacts").doc("job_1").set({
      jobId: "job_1",
      employerId: EMPLOYER_UID,
      employerPhone: "9876543210",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    employerDb.collection("payments").doc("pay_emp_1").set({
      userId: EMPLOYER_UID,
      userName: "Ramesh Das",
      userRole: "EMPLOYER",
      feeType: "JOB_POST_FEE",
      amount: 10,
      paymentApp: "PhonePe",
      transactionRef: "PP12345678",
      jobId: "job_1",
      jobTitle: "Electrician Needed Today",
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Ghost @DocumentId field 'id' must fail
  await assertFails(
    employerDb.collection("jobs").doc("job_bad").set({
      id: "job_bad",
      employerId: EMPLOYER_UID,
      employerName: "Ramesh Das",
      title: "Bad Job",
      description: "Has id field",
      category: "Other",
      jobType: "DAILY",
      wageAmount: 500,
      wagePeriod: "PER_DAY",
      city: "Kolkata",
      area: "",
      status: "OPEN",
      bookingFeePaid: true,
      bookingFeeRef: "PP123",
      workerFeePaid: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Worker cannot read employer's private job_contact BEFORE accepting
  await assertFails(workerDb.collection("job_contacts").doc("job_1").get());

  // Worker CAN query OPEN jobs, but NOT unfiltered jobs
  await assertSucceeds(workerDb.collection("jobs").where("status", "==", "OPEN").get());
  await assertFails(workerDb.collection("jobs").get());

  // 2. Worker accepts job atomically via batch + creates chat + payment
  const acceptTs = pastDate(2000);
  const batch = workerDb.batch();
  batch.update(workerDb.collection("jobs").doc("job_1"), {
    status: "ACCEPTED",
    acceptedWorkerId: WORKER_UID,
    acceptedWorkerName: "Suman Mondal",
    workerFeePaid: true,
    workerFeeRef: "PTM998877",
    updatedAt: acceptTs,
  });
  batch.update(workerDb.collection("job_contacts").doc("job_1"), {
    workerId: WORKER_UID,
    workerPhone: "9123456789",
    updatedAt: acceptTs,
  });
  batch.set(workerDb.collection("chats").doc("job_1"), {
    jobId: "job_1",
    jobTitle: "Electrician Needed Today",
    employerId: EMPLOYER_UID,
    employerName: "Ramesh Das",
    workerId: WORKER_UID,
    workerName: "Suman Mondal",
    memberUids: [EMPLOYER_UID, WORKER_UID],
    lastMessage: "Accepted job",
    createdAt: acceptTs,
    updatedAt: acceptTs,
  });
  batch.set(workerDb.collection("payments").doc("pay_wrk_1"), {
    userId: WORKER_UID,
    userName: "Suman Mondal",
    userRole: "WORKER",
    feeType: "JOB_ACCEPT_FEE",
    amount: 10,
    paymentApp: "Paytm",
    transactionRef: "PTM998877",
    jobId: "job_1",
    jobTitle: "Electrician Needed Today",
    status: "PENDING",
    createdAt: acceptTs,
    updatedAt: acceptTs,
  });
  await assertSucceeds(batch.commit());

  // Now Worker CAN read job_contacts/job_1; third party CANNOT
  await assertSucceeds(workerDb.collection("job_contacts").doc("job_1").get());
  await assertFails(otherDb.collection("job_contacts").doc("job_1").get());

  // 3. Chat messages work for members with memberUids filter, blocked for third party
  await assertSucceeds(
    workerDb.collection("chats").doc("job_1").collection("messages").doc("msg_1").set({
      chatId: "job_1",
      senderId: WORKER_UID,
      senderName: "Suman Mondal",
      memberUids: [EMPLOYER_UID, WORKER_UID],
      text: "Namaskar, coming in 30 mins",
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    workerDb
      .collection("chats")
      .doc("job_1")
      .collection("messages")
      .where("memberUids", "array-contains", WORKER_UID)
      .get()
  );
  await assertFails(
    otherDb.collection("chats").doc("job_1").collection("messages").get()
  );
  await assertFails(
    otherDb
      .collection("chats")
      .doc("job_1")
      .collection("messages")
      .where("memberUids", "array-contains", WORKER_UID)
      .get()
  );
  await assertFails(
    otherDb.collection("chats").doc("job_1").collection("messages").doc("msg_1").get()
  );

  // 4. Worker cannot verify their own payment; Admin CAN verify payment
  await assertFails(
    workerDb.collection("payments").doc("pay_wrk_1").update({
      status: "VERIFIED",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    adminDb.collection("payments").doc("pay_wrk_1").update({
      status: "VERIFIED",
      updatedAt: pastDate(500),
    })
  );

  // 5. Worker submits rating & review for Employer
  const reviewBatch = workerDb.batch();
  reviewBatch.set(
    workerDb
      .collection("public_profiles")
      .doc(EMPLOYER_UID)
      .collection("reviews")
      .doc("job_1_" + WORKER_UID),
    {
      jobId: "job_1",
      reviewerId: WORKER_UID,
      reviewerName: "Suman Mondal",
      targetUserId: EMPLOYER_UID,
      rating: 5,
      comment: "Paid on time and very respectful",
      createdAt: pastDate(500),
    }
  );
  reviewBatch.update(workerDb.collection("public_profiles").doc(EMPLOYER_UID), {
    ratingSum: 5,
    ratingCount: 1,
    updatedAt: pastDate(500),
  });
  await assertSucceeds(reviewBatch.commit());
});
