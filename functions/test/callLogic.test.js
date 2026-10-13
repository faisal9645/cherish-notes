const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");
const logic = require("../lib/callLogic");

const { CALL_STATE } = logic;

function call(overrides = {}) {
  return {
    callId: "AbCdEfGhIjKlMnOpQrSt",
    coupleId: "couple_test",
    callerId: "user_a",
    calleeId: "user_b",
    state: CALL_STATE.RINGING,
    ...overrides,
  };
}

test("couple members skip blanks and placeholders, and fall back to names", () => {
  assert.deepEqual(logic.coupleMembers({ partner1Id: "user_a", partner2Id: "user_b" }), ["user_a", "user_b"]);
  assert.deepEqual(logic.coupleMembers({ partner1Id: "user_a", partner2Name: "Bee" }), ["user_a", "user_bee"]);
  assert.deepEqual(logic.coupleMembers({ partner1Id: "user_a", partner2Id: "user_partner" }), ["user_a"]);
  assert.deepEqual(logic.coupleMembers(null), []);
});

test("call ids are letters and digits of a sane length", () => {
  assert.equal(logic.isValidCallId("AbCdEfGhIjKlMnOpQrSt"), true);
  assert.equal(logic.isValidCallId("short"), false);
  assert.equal(logic.isValidCallId("has/slash/AbCdEfGhIjKl"), false);
  assert.equal(logic.isValidCallId("has_underscore_AbCdEfGh"), false);
  assert.equal(logic.isValidCallId(42), false);
});

test("safe ids reject paths and reserved names", () => {
  assert.equal(logic.isSafeId("couple_abc"), true);
  assert.equal(logic.isSafeId("a/b"), false);
  assert.equal(logic.isSafeId(""), false);
  assert.equal(logic.isSafeId("__name__"), false);
  assert.equal(logic.isSafeId("x".repeat(200)), false);
});

test("hanging up a ringing call: caller cancels, timeout misses, callee declines", () => {
  assert.deepEqual(logic.endTransition(call(), "user_a", "hangup"), { state: "cancelled", endReason: "cancelled" });
  assert.deepEqual(logic.endTransition(call(), "user_a", "no_answer"), { state: "missed", endReason: "no_answer" });
  assert.deepEqual(logic.endTransition(call(), "user_b", "hangup"), { state: "declined", endReason: "declined" });
});

test("hanging up a connected call ends it with the reason given (unknown reasons count as hangup)", () => {
  const connected = call({ state: CALL_STATE.CONNECTED, connectedAt: 1000 });
  assert.deepEqual(logic.endTransition(connected, "user_b", "peer_lost"), { state: "ended", endReason: "peer_lost" });
  assert.deepEqual(logic.endTransition(connected, "user_a", "whatever"), { state: "ended", endReason: "hangup" });
});

test("an answered call that never connected fails", () => {
  const connecting = call({ state: CALL_STATE.CONNECTING, connectedAt: null });
  assert.deepEqual(logic.endTransition(connecting, "user_a", "hangup"), { state: "failed", endReason: "cancelled" });
  assert.deepEqual(logic.endTransition(connecting, "user_a", "network"), { state: "failed", endReason: "network" });
});

test("a call that's already over doesn't change again", () => {
  for (const state of ["ended", "declined", "cancelled", "missed", "busy", "unavailable", "failed"]) {
    assert.equal(logic.endTransition(call({ state }), "user_a", "hangup"), null, state);
  }
});

test("ringing expires after its time; Firestore timestamps work too", () => {
  const ringExpiresAt = { toMillis: () => 10_000 };
  assert.equal(logic.isRingExpired(call({ ringExpiresAt }), 9_999), false);
  assert.equal(logic.isRingExpired(call({ ringExpiresAt }), 10_001), true);
  assert.equal(logic.isRingExpired(call({ state: CALL_STATE.CONNECTED, ringExpiresAt }), 99_999), false);
});

test("connect timeout only applies to answered calls", () => {
  const answered = call({ state: CALL_STATE.CONNECTING, answeredAt: 1000 });
  assert.equal(logic.isConnectTimedOut(answered, 1000 + logic.CONNECT_TIMEOUT_MS + 1), true);
  assert.equal(logic.isConnectTimedOut(answered, 2000), false);
  assert.equal(logic.isConnectTimedOut(call(), 10 ** 12), false);
});

test("connected seconds are counted from the connection", () => {
  assert.equal(logic.connectedSeconds(call({ connectedAt: 1_000 }), 62_400), 61);
  assert.equal(logic.connectedSeconds(call({ connectedAt: null }), 62_400), 0);
});

test("each participant has their own Agora uid; others have none", () => {
  const c = call();
  assert.equal(logic.agoraUidFor(c, "user_a"), logic.CALLER_UID);
  assert.equal(logic.agoraUidFor(c, "user_b"), logic.CALLEE_UID);
  assert.equal(logic.agoraUidFor(c, "user_x"), null);
  assert.notEqual(logic.RECORDER_UID, logic.CALLER_UID);
  assert.notEqual(logic.RECORDER_UID, logic.CALLEE_UID);
});

test("webhook signatures: only the exact HMAC-SHA256 of the raw body passes", () => {
  const secret = "s3cret";
  const body = Buffer.from('{"noticeId":"n1","eventType":40}');
  const good = crypto.createHmac("sha256", secret).update(body).digest("hex");
  assert.equal(logic.verifyAgoraSignature(body, good, secret), true);
  assert.equal(logic.verifyAgoraSignature(body, good.toUpperCase(), secret), true);
  assert.equal(logic.verifyAgoraSignature(body, good.slice(0, -1) + "0", secret), good.endsWith("0"));
  assert.equal(logic.verifyAgoraSignature(Buffer.from('{"noticeId":"n2"}'), good, secret), false);
  assert.equal(logic.verifyAgoraSignature(body, "short", secret), false);
  assert.equal(logic.verifyAgoraSignature(body, good, ""), false);
  assert.equal(logic.verifyAgoraSignature(body, undefined, secret), false);
});

test("recording paths: Agora's staging folder is letters and digits; the final one is the couple's", () => {
  const id = "AbCdEfGhIjKlMnOpQrSt";
  assert.deepEqual(logic.stagingPathSegments(id), ["callrecstaging", id]);
  for (const segment of logic.stagingPathSegments(id)) assert.match(segment, /^[A-Za-z0-9]+$/);
  assert.equal(logic.stagingPrefix(id), `callrecstaging/${id}/`);
  assert.equal(logic.finalRecordingPath("couple_x", id, 0), `couples/couple_x/call-recordings/${id}/recording.mp4`);
  assert.equal(logic.finalRecordingPath("couple_x", id, 2), `couples/couple_x/call-recordings/${id}/recording_2.mp4`);
});

test("only the MP4 parts are picked, in order", () => {
  const names = [
    "callrecstaging/X/sid_X_1.mp4",
    "callrecstaging/X/sid_X.m3u8",
    "callrecstaging/X/sid_X_20261013.ts",
    "callrecstaging/X/sid_X_0.mp4",
    "callrecstaging/X/sid_X_10.mp4",
  ];
  assert.deepEqual(logic.pickRecordingSlices(names), [
    "callrecstaging/X/sid_X_0.mp4",
    "callrecstaging/X/sid_X_1.mp4",
    "callrecstaging/X/sid_X_10.mp4",
  ]);
  assert.deepEqual(logic.pickRecordingSlices([]), []);
});

test("the recording message has a stable id, no download link and the private path", () => {
  const c = call({ state: CALL_STATE.ENDED });
  const message = logic.buildRecordingMessage({
    call: c,
    coupleId: "couple_test",
    callerName: "A",
    storagePaths: ["couples/couple_test/call-recordings/AbCdEfGhIjKlMnOpQrSt/recording.mp4"],
    sizeBytes: 12345,
    durationSec: 61,
    timestampMs: 1_700_000_000_000,
  });
  assert.equal(message.id, "callrec_AbCdEfGhIjKlMnOpQrSt");
  assert.equal(message.id, logic.recordingMessageId(c.callId));
  assert.equal(message.type, "CALL_RECORDING");
  assert.equal(message.mediaUrl, null);
  assert.deepEqual(message.mediaUrls, []);
  assert.equal(message.senderId, "user_a");
  assert.equal(message.receiverId, "user_b");
  assert.equal(message.durationSeconds, 61);
  assert.equal(message.timestamp, 1_700_000_000_000);
  assert.equal(message.recordingPath, "couples/couple_test/call-recordings/AbCdEfGhIjKlMnOpQrSt/recording.mp4");
  assert.equal(JSON.stringify(message).includes("https://"), false);
});

test("Agora query: only status 5 counts as recording", () => {
  assert.equal(logic.isAgoraRecordingActive({ serverResponse: { status: 5 } }), true);
  assert.equal(logic.isAgoraRecordingActive({ serverResponse: { status: 4 } }), false);
  assert.equal(logic.isAgoraRecordingActive({ serverResponse: { status: 20 } }), false);
  assert.equal(logic.isAgoraRecordingActive(null), false);
});
