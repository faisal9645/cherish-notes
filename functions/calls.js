/**
 * Voice calls between the two phones of a couple. The app itself starts, answers and ends calls
 * (it writes couples/{coupleId}/callControl/active and calls/{callId}) and records them; this
 * only does what a phone can't do for the other one:
 *
 * - callPushOnControlChange: rings the called phone when a call starts ringing, and stops it when
 *   the call is called off or rings out (also with the app closed there).
 * - onCallRecordingMessageDeleted: deleting the recording's chat message deletes the audio too.
 *
 * The audio is in Storage at couples/{coupleId}/call-recordings/{callId}/.
 */
const { onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

const logic = require("./lib/callLogic");
const push = require("./lib/push");

const { RECORDING, RING_TIMEOUT_MS } = logic;

/** Long enough for a phone that's briefly offline to still stop ringing and show the missed call. */
const STOP_RINGING_TTL_MS = 5 * 60 * 1000;

const db = () => getFirestore();
const coupleRef = (coupleId) => db().collection("couples").doc(coupleId);
const callRef = (coupleId, callId) => coupleRef(coupleId).collection("calls").doc(callId);

/** Sends a call push to [receiverId]'s phone (never to [senderId]'s); says whether it went out. */
async function sendCallPush(receiverId, data, ttlMs, coupleId, senderId) {
  return push.pushToMember(db(), { receiverId, senderId, coupleId, data, android: { priority: "high", ttl: ttlMs } });
}

// ---------- Ringing the other phone ----------

/**
 * The app writes the couple's current call itself (couples/{coupleId}/callControl/active), so the
 * called phone rings from here: a push when a call starts ringing (also with the app closed
 * there), and one when it's called off or rings out, so that phone stops and shows it was missed.
 * The app asks for the same pushes directly (pushPartner); the called phone acts on each once.
 */
exports.callPushOnControlChange = onDocumentWritten("couples/{coupleId}/callControl/{docId}", async (event) => {
  const before = event.data && event.data.before && event.data.before.exists ? event.data.before.data() : null;
  const after = event.data && event.data.after && event.data.after.exists ? event.data.after.data() : null;
  const change = logic.controlPushChange(before, after);
  if (!change || !logic.isValidCallId(after.callId)) return;
  const { coupleId } = event.params;
  const nowMs = Date.now();
  const couple = (await coupleRef(coupleId).get()).data() || {};
  const callerId = String(after.callerId || "");
  const calleeId = logic.calleeFor(after, couple);
  if (!calleeId || logic.isSameUser(calleeId, callerId)) return;

  if (change.kind === "invite") {
    const ringUntil = logic.ringUntilMs(after) || nowMs + RING_TIMEOUT_MS;
    if (ringUntil <= nowMs) return;
    const data = push.callInviteData({
      callId: after.callId,
      coupleId,
      callerId,
      callerName: await push.displayNameOf(db(), callerId, couple),
      ringUntilMs: ringUntil,
      nowMs,
    });
    const outcome = await sendCallPush(calleeId, data, ringUntil - nowMs, coupleId, callerId);
    logger.info("Call ringing push", { callId: after.callId, outcome });
    return;
  }
  const data = push.callCancelData({ callId: after.callId, coupleId, reason: change.reason });
  await sendCallPush(calleeId, data, STOP_RINGING_TTL_MS, coupleId, callerId);
});

// ---------- Deleting a recording ----------

exports.onCallRecordingMessageDeleted = onDocumentDeleted(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message || String(message.type || "").toUpperCase() !== "CALL_RECORDING") return;
    const { conversationId, messageId } = event.params;
    const callId = String(message.callId || "");
    // Only the real recording message of a call of this couple
    if (!logic.isValidCallId(callId) || messageId !== logic.recordingMessageId(callId)) return;
    const ref = callRef(conversationId, callId);
    if (!(await ref.get()).exists) return;
    await getStorage().bucket().deleteFiles({ prefix: logic.finalRecordingPrefix(conversationId, callId), force: true });
    await ref.update({
      "recording.status": RECORDING.DELETED,
      "recording.deletedAt": FieldValue.serverTimestamp(),
      "recording.storagePath": FieldValue.delete(),
      "recording.storagePaths": FieldValue.delete(),
      "recording.updatedAt": FieldValue.serverTimestamp(),
    });
    logger.info("Call recording deleted with its message", { callId });
  }
);
