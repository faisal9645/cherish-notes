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
