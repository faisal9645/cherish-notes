const test = require("node:test");
const assert = require("node:assert/strict");

const push = require("../lib/push");
const logic = require("../lib/callLogic");

const couple = {
  partner1Id: "user_faisal",
  partner1Name: "Faisal",
  partner2Id: "user_ayesha",
  partner2Name: "Ayesha",
  partner1FcmToken: "token-faisal-slot",
  partner2FcmToken: "token-ayesha-slot",
};

test("alternateId adds or takes off user_", () => {
  assert.equal(push.alternateId("user_ayesha"), "ayesha");
  assert.equal(push.alternateId("ayesha"), "user_ayesha");
  assert.equal(push.alternateId(""), "");
  assert.equal(push.alternateId(null), "");
});

test("coupleTokenField finds the receiver's slot by id or by name, and nothing for strangers", () => {
  assert.equal(push.coupleTokenField(couple, "user_ayesha"), "partner2FcmToken");
  assert.equal(push.coupleTokenField(couple, "faisal"), "partner1FcmToken");
  assert.equal(push.coupleTokenField({ partner1Name: "Faisal Khan" }, "user_faisalkhan"), "partner1FcmToken");
  assert.equal(push.coupleTokenField(couple, "user_someone"), null);
  assert.equal(push.coupleTokenField(couple, ""), null);
  assert.equal(push.coupleTokenField(null, "user_ayesha"), null);
});

test("pickTargets tries every distinct address of the receiver, never the sender's", () => {
  const profiles = [
    { ref: "users/user_ayesha", data: { fcmToken: "token-ayesha-new" } },
    { ref: "users/ayesha", data: { fcmToken: "token-ayesha-old" } },
  ];
  const targets = push.pickTargets({
    profiles,
    couple: { ...couple, partner2FcmToken: "token-ayesha-new" },
    coupleRef: "couples/c1",
    receiverId: "user_ayesha",
    excludeTokens: ["token-faisal"],
  });
  assert.deepEqual(targets.map((t) => t.token), ["token-ayesha-new", "token-ayesha-old"]);
  assert.deepEqual(targets.map((t) => t.field), ["fcmToken", "fcmToken"]);
});

test("pickTargets falls back to the couple slot, and skips a slot holding the sender's address", () => {
  const fromSlot = push.pickTargets({ profiles: [], couple, coupleRef: "couples/c1", receiverId: "user_ayesha", excludeTokens: [] });
  assert.deepEqual(fromSlot, [{ token: "token-ayesha-slot", ref: "couples/c1", field: "partner2FcmToken" }]);

  const mixedUp = { ...couple, partner2FcmToken: "token-faisal" };
  const none = push.pickTargets({ profiles: [], couple: mixedUp, coupleRef: "couples/c1", receiverId: "user_ayesha", excludeTokens: ["token-faisal"] });
  assert.deepEqual(none, []);
});

test("pickTargets ignores empty and non-string tokens", () => {
  const targets = push.pickTargets({
    profiles: [{ ref: "a", data: { fcmToken: "" } }, { ref: "b", data: { fcmToken: 42 } }, { ref: "c", data: {} }, null],
    couple: null,
    receiverId: "user_ayesha",
  });
  assert.deepEqual(targets, []);
});

test("call push payloads carry only strings", () => {
  const invite = push.callInviteData({ callId: "A1", coupleId: "c1", callerId: "user_faisal", callerName: null, ringUntilMs: 5, nowMs: 1 });
  assert.deepEqual(invite, { type: "call_invite", callId: "A1", coupleId: "c1", callerId: "user_faisal", callerName: "", sentAt: "1", ringUntil: "5" });
  const cancel = push.callCancelData({ callId: "A1", coupleId: "c1", reason: "missed" });
  assert.deepEqual(cancel, { type: "call_cancel", callId: "A1", coupleId: "c1", reason: "missed" });
  for (const value of [...Object.values(invite), ...Object.values(cancel)]) assert.equal(typeof value, "string");
});

test("controlPushChange rings once when a call starts ringing", () => {
  const ringing = { callId: "callA", state: "ringing" };
  assert.deepEqual(logic.controlPushChange(null, ringing), { kind: "invite" });
  assert.deepEqual(logic.controlPushChange({ callId: "callOld", state: "ended" }, ringing), { kind: "invite" });
  // A later write to the same ringing call doesn't ring again
  assert.equal(logic.controlPushChange(ringing, { ...ringing, updatedAt: 2 }), null);
});

test("controlPushChange stops the ringing when a ringing call ends unanswered", () => {
  const ringing = { callId: "callA", state: "ringing" };
  assert.deepEqual(logic.controlPushChange(ringing, { callId: "callA", state: "missed" }), { kind: "cancel", reason: "missed" });
  assert.deepEqual(logic.controlPushChange(ringing, { callId: "callA", state: "cancelled" }), { kind: "cancel", reason: "cancelled" });
  // Older app versions wrote "ended" when the caller hung up while it rang
  assert.deepEqual(logic.controlPushChange(ringing, { callId: "callA", state: "ended" }), { kind: "cancel", reason: "cancelled" });
});

test("controlPushChange says nothing for an answered, declined or finished call", () => {
  const ringing = { callId: "callA", state: "ringing" };
  assert.equal(logic.controlPushChange(ringing, { callId: "callA", state: "connecting" }), null);
  assert.equal(logic.controlPushChange(ringing, { callId: "callA", state: "declined" }), null);
  assert.equal(logic.controlPushChange({ callId: "callA", state: "connected" }, { callId: "callA", state: "ended" }), null);
  assert.equal(logic.controlPushChange({ callId: "callA", state: "ringing" }, null), null);
  assert.equal(logic.controlPushChange(null, { state: "ringing" }), null);
});

test("calleeFor uses the callee, or the caller's partner when the app wrote a placeholder", () => {
  assert.equal(logic.calleeFor({ callerId: "user_faisal", calleeId: "user_ayesha" }, couple), "user_ayesha");
  assert.equal(logic.calleeFor({ callerId: "user_faisal", calleeId: "partner" }, couple), "user_ayesha");
  assert.equal(logic.calleeFor({ callerId: "user_ayesha", calleeId: "" }, couple), "user_faisal");
  assert.equal(logic.calleeFor(null, couple), null);
});

test("ringUntilMs reads either way the app writes the ring's end", () => {
  assert.equal(logic.ringUntilMs({ ringExpiresAtMs: 5000 }), 5000);
  assert.equal(logic.ringUntilMs({ ringExpiresAt: { toMillis: () => 7000 } }), 7000);
  assert.equal(logic.ringUntilMs({ createdAt: 1000 }), 1000 + logic.RING_TIMEOUT_MS);
  assert.equal(logic.ringUntilMs({}), 0);
  assert.equal(logic.ringUntilMs(null), 0);
});

test("cancelReasonFor tells the called phone only about calls it missed", () => {
  assert.equal(logic.cancelReasonFor("missed"), "missed");
  assert.equal(logic.cancelReasonFor("cancelled"), "cancelled");
  assert.equal(logic.cancelReasonFor("busy"), "cancelled");
  assert.equal(logic.cancelReasonFor("declined"), null);
  assert.equal(logic.cancelReasonFor("connected"), null);
});

test("calleesFor rings the callee and the caller's partner on the couple record, never the caller", () => {
  assert.deepEqual(logic.calleesFor({ callerId: "user_faisal", calleeId: "user_ayesha" }, couple), ["user_ayesha"]);
  // The calling phone had an old id for the partner: the couple record's partner gets it too
  assert.deepEqual(logic.calleesFor({ callerId: "user_faisal", calleeId: "user_ayesha_old" }, couple), ["user_ayesha_old", "user_ayesha"]);
  assert.deepEqual(logic.calleesFor({ callerId: "user_faisal", calleeId: "partner" }, couple), ["user_ayesha"]);
  assert.deepEqual(logic.calleesFor({ callerId: "user_faisal", calleeId: "faisal" }, couple), ["user_ayesha"]);
  assert.deepEqual(logic.calleesFor({ callerId: "user_faisal", calleeId: "" }, null), []);
  assert.deepEqual(logic.calleesFor(null, couple), []);
});

test("pickTargets with several receiver ids tries each one's couple slot", () => {
  const targets = push.pickTargets({
    profiles: [{ ref: "users/user_ayesha_old", data: { fcmToken: "token-old" } }],
    couple,
    coupleRef: "couples/c1",
    receiverId: ["user_ayesha_old", "user_ayesha"],
    excludeTokens: ["token-faisal-slot"],
  });
  assert.deepEqual(targets.map((t) => t.token), ["token-old", "token-ayesha-slot"]);
});

test("notificationsOff only when every profile that says so says CLOSED", () => {
  assert.equal(push.notificationsOff([{ data: { notificationStatus: "CLOSED" } }]), true);
  assert.equal(push.notificationsOff([{ data: { notificationStatus: "CLOSED" } }, { data: {} }]), true);
  assert.equal(push.notificationsOff([{ data: { notificationStatus: "CLOSED" } }, { data: { notificationStatus: "OPEN" } }]), false);
  assert.equal(push.notificationsOff([{ data: { notificationStatus: "SAFE" } }]), false);
  assert.equal(push.notificationsOff([{ data: {} }]), false);
  assert.equal(push.notificationsOff([]), false);
});

/** Just enough Firestore for pushOnce: documents by path that can be created once and deleted. */
function fakeDb({ unavailable = false } = {}) {
  const store = new Map();
  const docRef = (path) => ({
    path,
    collection: (name) => ({ doc: (id) => docRef(`${path}/${name}/${id}`) }),
    create: async (data) => {
      if (unavailable) throw Object.assign(new Error("14 UNAVAILABLE"), { code: 14 });
      if (store.has(path)) throw Object.assign(new Error("6 ALREADY_EXISTS"), { code: 6 });
      store.set(path, data);
    },
    delete: async () => {
      store.delete(path);
    },
  });
  return { store, collection: (name) => ({ doc: (id) => docRef(`${name}/${id}`) }) };
}

test("pushOnce sends each push once, whichever path asks first", async () => {
  const db = fakeDb();
  let sends = 0;
  const send = async () => {
    sends += 1;
    return "sent";
  };
  assert.equal(await push.pushOnce(db, "c1", "message:m1", send), "sent");
  assert.equal(await push.pushOnce(db, "c1", "message:m1", send), "already_sent");
  assert.equal(await push.pushOnce(db, "c1", "call_invite:k1", send), "sent");
  assert.equal(await push.pushOnce(db, "c2", "message:m1", send), "sent");
  assert.equal(sends, 3);
  assert.deepEqual([...db.store.keys()], [
    "couples/c1/pushes/message:m1",
    "couples/c1/pushes/call_invite:k1",
    "couples/c2/pushes/message:m1",
  ]);
  assert.ok(db.store.get("couples/c1/pushes/message:m1").expireAt);
});

test("pushOnce lets the other path try when nothing reached the phone, not when it was skipped", async () => {
  const db = fakeDb();
  assert.equal(await push.pushOnce(db, "c1", "message:m1", async () => "no_push_token"), "no_push_token");
  assert.equal(await push.pushOnce(db, "c1", "message:m1", async () => "sent"), "sent");

  assert.equal(await push.pushOnce(db, "c1", "message:m2", async () => "seen_in_app"), "seen_in_app");
  assert.equal(await push.pushOnce(db, "c1", "message:m2", async () => "sent"), "already_sent");
});

test("pushOnce still sends when the record can't be checked, or there's no couple", async () => {
  const send = async () => "sent";
  assert.equal(await push.pushOnce(fakeDb({ unavailable: true }), "c1", "message:m1", send), "sent");
  const db = fakeDb();
  assert.equal(await push.pushOnce(db, "", "message:m1", send), "sent");
  assert.equal(await push.pushOnce(db, "", "message:m1", send), "sent");
});
