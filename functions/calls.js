/**
 * Voice calls between the two phones of a couple. The app itself starts, answers and ends calls
 * (it writes couples/{coupleId}/callControl/active and calls/{callId}) and records them, and the
 * calling phone asks pushPartner to ring the other phone and to stop it (see index.js). This
 * only does what a phone can't do for the other one:
 *
 * - onCallRecordingMessageDeleted: deleting the recording's chat message deletes the audio too.
 *
 * (callPushOnControlChange, a backup that rang the other phone from every write to callControl,
 * about six runs a call, was removed to save function runs: pushPartner rings it, with retries.)
 *
 * The audio is in Storage at couples/{coupleId}/call-recordings/{callId}/.
 */
const { onDocumentDeleted } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

const logic = require("./lib/callLogic");

const { RECORDING } = logic;

const db = () => getFirestore();
const coupleRef = (coupleId) => db().collection("couples").doc(coupleId);
const callRef = (coupleId, callId) => coupleRef(coupleId).collection("calls").doc(callId);

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
