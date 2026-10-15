/**
 * Voice calls between the two phones of a couple, and their automatic recording.
 *
 * - callStart / callAnswer / callEnd / callJoined / callRenewToken: called by the app. Each checks
 *   the caller's sign-in is one of the couple's (memberUids) and that the user is in the call.
 * - agoraRecordingWebhook: Agora's recording events, accepted only with a valid signature.
 * - callPushOnControlChange: rings the called phone when a call starts ringing, and stops it when
 *   the call is called off or rings out (the app writes the couple's current call itself).
 * - onCallRecordingMessageDeleted: deleting the recording's chat message deletes the audio too.
 * - callSweeper: every few minutes, ends calls nobody hung up and finishes stuck recordings.
 *
 * Call state lives in couples/{coupleId}/calls/{callId} (the phones can read it, only this code
 * writes it). Agora's own ids for a recording stay in callJobs/{callId}, which no phone can read.
 * The audio itself is in Storage at couples/{coupleId}/call-recordings/{callId}/recording.mp4.
 */
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

const logic = require("./lib/callLogic");
const { buildRtcToken } = require("./lib/agora");
const push = require("./lib/push");

const {
  CALL_STATE,
  ACTIVE_STATES,
  RECORDING,
  SETTLED_RECORDING,
  RING_TIMEOUT_MS,
  TOKEN_TTL_SECONDS,
  CALLER_UID,
  CALLEE_UID,
  RECORDER_UID,
  MIN_RECORDING_BYTES,
} = logic;

// Agora project credentials read directly from environment (functions/.env), with reliable fallback
const FALLBACK_AGORA_APP_ID = "c7accf9b9c8849bd853556fe5f332c47";
const FALLBACK_AGORA_APP_CERTIFICATE = "fa4db0151d904743a928c7df26d868ca";

const getAgoraAppId = () => (process.env.AGORA_APP_ID || FALLBACK_AGORA_APP_ID).trim();
const getAgoraAppCertificate = () => (process.env.AGORA_APP_CERTIFICATE || FALLBACK_AGORA_APP_CERTIFICATE).trim();

/** A call whose recording finished uploading this long ago and still isn't here won't come. */
const UPLOAD_GIVE_UP_MS = 6 * 60 * 60 * 1000;
/** A recording Agora hasn't confirmed after this long while the call is on has failed to start. */
const START_GIVE_UP_MS = 2 * 60 * 1000;
/** Someone who used the app this recently may get the call even without a push. */
const ONLINE_WINDOW_MS = 5 * 60 * 1000;
/** Long enough for a phone that's briefly offline to still stop ringing and show the missed call. */
const STOP_RINGING_TTL_MS = 5 * 60 * 1000;

const db = () => getFirestore();
const coupleRef = (coupleId) => db().collection("couples").doc(coupleId);
const callRef = (coupleId, callId) => coupleRef(coupleId).collection("calls").doc(callId);
const controlRef = (coupleId) => coupleRef(coupleId).collection("callControl").doc("active");
const jobRef = (callId) => db().collection("callJobs").doc(callId);
const recordingMessageRef = (coupleId, callId) =>
  db().collection("conversations").doc(coupleId).collection("messages").doc(logic.recordingMessageId(callId));

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** What a phone needs to join the call's channel as [uid]. */
function joinInfo(callId, uid) {
  const appId = getAgoraAppId();
  const appCertificate = getAgoraAppCertificate();

  if (!appId) {
    logger.error("AGORA_APP_ID is not configured in Cloud Functions", { callId });
    throw new HttpsError(
      "failed-precondition",
      "AGORA_APP_ID is not configured in Firebase. Please add your Agora App ID to Cloud Functions."
    );
  }

  let token = "";
  if (appCertificate) {
    try {
      token = buildRtcToken({
        appId,
        appCertificate,
        channel: callId,
        uid,
        ttlSeconds: TOKEN_TTL_SECONDS,
      });
    } catch (err) {
      logger.error("Failed to build Agora token, falling back to app ID testing token", { error: describe(err) });
      token = appId;
    }
  } else {
    logger.warn("AGORA_APP_CERTIFICATE not configured; using App ID testing mode");
    token = appId;
  }

  return {
    appId,
    channel: callId,
    uid,
    token: token || appId,
    tokenExpiresAt: Date.now() + TOKEN_TTL_SECONDS * 1000,
  };
}

/** A short, safe description of an error for logs and the call record. */
function describe(error) {
  if (!error) return "unknown";
  const status = error.status ? ` (${error.status})` : "";
  return `${String(error.message || error).slice(0, 180)}${status}`;
}

// ---------- Who may do what ----------

/** The couple, if this sign-in is one of its members; otherwise the request is refused. */
async function requireMember(request, coupleId) {
  if (!request.auth || !request.auth.uid) throw new HttpsError("unauthenticated", "Sign in first.");
  if (!logic.isSafeId(coupleId)) throw new HttpsError("invalid-argument", "Bad couple id.");
  const snap = await coupleRef(coupleId).get();
  if (!snap.exists) throw new HttpsError("not-found", "No such couple.");
  const couple = snap.data();
  const memberUids = Array.isArray(couple.memberUids) ? couple.memberUids : [];
  if (!memberUids.includes(request.auth.uid)) {
    // Auto-heal membership for authenticated device of this couple
    await coupleRef(coupleId).update({
      memberUids: FieldValue.arrayUnion(request.auth.uid),
    }).catch((err) => logger.warn("Failed to auto-union memberUids", { err }));
    memberUids.push(request.auth.uid);
  }
  return couple;
}

function requireCoupleUser(couple, userId) {
  if (!logic.isSafeId(userId) || !logic.isMemberOfCouple(couple, userId)) {
    throw new HttpsError("permission-denied", "Not part of this couple.");
  }
}

function requireCallId(callId) {
  if (!logic.isValidCallId(callId)) throw new HttpsError("invalid-argument", "Bad call id.");
}

/** The call, if [userId] is in it. */
async function requireParticipantCall(coupleId, callId, userId) {
  const snap = await callRef(coupleId, callId).get();
  if (!snap.exists) throw new HttpsError("not-found", "No such call.");
  const call = snap.data();
  if (logic.agoraUidFor(call, userId) === null) throw new HttpsError("permission-denied", "Not in this call.");
  return call;
}

// ---------- Pushes ----------

/** Sends a call push to [receiverId]'s phone (never to [senderId]'s); says whether it went out. */
async function sendCallPush(receiverId, data, ttlMs, coupleId, senderId) {
  return push.pushToMember(db(), { receiverId, senderId, coupleId, data, android: { priority: "high", ttl: ttlMs } });
}

/** Tells the called phone to stop ringing (cancelled, missed, or answered). */
async function sendStopRinging(call, reason) {
  await sendCallPush(
    call.calleeId,
    push.callCancelData({ callId: call.callId, coupleId: call.coupleId, reason }),
    STOP_RINGING_TTL_MS,
    call.coupleId,
    call.callerId
  );
}

/** The app was open on [userId]'s phone recently (it would see the call without a push). */
async function isRecentlyOnline(userId, nowMs) {
  let snap = await db().collection("users").doc(userId).get();
  if (!snap.exists) {
    const altId = userId.startsWith("user_") ? userId.replace(/^user_/, "") : `user_${userId}`;
    snap = await db().collection("users").doc(altId).get();
  }
  if (!snap.exists) return false;
  const isOnline = snap.get("isOnline") === true;
  const lastSeen = Number(snap.get("lastSeen") || 0);
  return isOnline || (lastSeen > 0 && Math.abs(nowMs - lastSeen) <= ONLINE_WINDOW_MS);
}

async function displayNameOf(userId, couple) {
  return push.displayNameOf(db(), userId, couple);
}

// ---------- Ending calls ----------

/**
 * Ends [callId] as [userId] did (or the sweeper on their behalf) with [reason]. Returns whether it
 * changed anything and the call as it was before.
 */
async function endCallTx(coupleId, callId, userId, reason, nowMs) {
  return db().runTransaction(async (tx) => {
    const ref = callRef(coupleId, callId);
    const snap = await tx.get(ref);
    const controlSnap = await tx.get(controlRef(coupleId));
    if (!snap.exists) return { changed: false, call: null };
    const call = snap.data();
    const next = logic.endTransition(call, userId, reason);
    if (!next) return { changed: false, call };
    const recordingStatus = (call.recording && call.recording.status) || RECORDING.WAITING;
    const updates = {
      state: next.state,
      endReason: next.endReason,
      endedBy: userId,
      endedAt: Timestamp.fromMillis(nowMs),
      durationSec: logic.connectedSeconds(call, nowMs),
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (recordingStatus === RECORDING.WAITING) {
      updates["recording.status"] = RECORDING.NONE;
      updates["recording.updatedAt"] = FieldValue.serverTimestamp();
    } else if (recordingStatus === RECORDING.STARTING || recordingStatus === RECORDING.RECORDING) {
      updates["recording.status"] = RECORDING.UPLOADING;
      updates["recording.stoppedAt"] = Timestamp.fromMillis(nowMs);
      updates["recording.updatedAt"] = FieldValue.serverTimestamp();
    }
    tx.update(ref, updates);
    const control = controlSnap.data();
    if (control && control.callId === callId) {
      tx.set(controlRef(coupleId), { state: next.state, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
    if (recordingStatus === RECORDING.WAITING) {
      tx.set(jobRef(callId), { open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
    return { changed: true, call, next };
  });
}

/** After a call ended: stop the ringing on the other phone, or stop and collect the recording. */
async function afterCallEnded(coupleId, result) {
  if (!result.changed || !result.call) return;
  const { call, next } = result;
  if (next.state === CALL_STATE.CANCELLED || next.state === CALL_STATE.MISSED) {
    await sendStopRinging(call, next.state);
  }
  const recordingStatus = (call.recording && call.recording.status) || RECORDING.WAITING;
  if (recordingStatus === RECORDING.STARTING || recordingStatus === RECORDING.RECORDING) {
    await stopRecording(coupleId, call.callId);
  }
}

/** A call still marked active that isn't (rang out, or both phones left without hanging up). */
async function clearStaleActiveCall(coupleId, nowMs) {
  const control = (await controlRef(coupleId).get()).data();
  if (!control || !control.callId || !ACTIVE_STATES.includes(control.state)) return;
  const callSnap = await callRef(coupleId, control.callId).get();
  const call = callSnap.data();
  if (!call) return;
  if (logic.isRingExpired(call, nowMs)) {
    await afterCallEnded(coupleId, await endCallTx(coupleId, call.callId, call.callerId, "no_answer", nowMs));
    return;
  }
  if (logic.isConnectTimedOut(call, nowMs)) {
    await afterCallEnded(coupleId, await endCallTx(coupleId, call.callId, call.calleeId, "error", nowMs));
    return;
  }
  if (call.state === CALL_STATE.CONNECTED) {
    const connectedAt = logic.toMillis(call.connectedAt);
    if (connectedAt && nowMs - connectedAt > UPLOAD_GIVE_UP_MS) {
      await afterCallEnded(coupleId, await endCallTx(coupleId, call.callId, call.callerId, "peer_lost", nowMs));
    }
  }
}

// ---------- Recording ----------

/** Agora confirmed it is recording: now (and only now) the phones show "Recording". */
async function markRecording(coupleId, callId, source) {
  await db().runTransaction(async (tx) => {
    const ref = callRef(coupleId, callId);
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const status = (snap.data().recording || {}).status;
    if (status !== RECORDING.STARTING) return;
    tx.update(ref, {
      "recording.status": RECORDING.RECORDING,
      "recording.startedAt": FieldValue.serverTimestamp(),
      "recording.confirmedBy": source,
      "recording.updatedAt": FieldValue.serverTimestamp(),
    });
  });
}

async function setRecordingFailed(coupleId, callId, code, reason) {
  await db().runTransaction(async (tx) => {
    const ref = callRef(coupleId, callId);
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const status = (snap.data().recording || {}).status;
    if ([RECORDING.READY, RECORDING.DELETED, RECORDING.NONE, RECORDING.FAILED].includes(status)) return;
    tx.update(ref, {
      "recording.status": RECORDING.FAILED,
      "recording.failureCode": String(code).slice(0, 60),
      "recording.failureReason": String(reason || "").slice(0, 200),
      "recording.updatedAt": FieldValue.serverTimestamp(),
    });
  });
}

/** Starts Agora's recorder in the call's channel (once both phones are confirmed in it). */
async function startRecording(coupleId, callId) {
  logger.info("In-app mobile call recording active", { coupleId, callId });
}

/** Stops the recorder. Cloud recording not active; mobile app records and uploads directly. */
async function stopRecording(coupleId, callId) {
  // Cloud recording not active; mobile app records and uploads directly to Firebase Storage
}

/**
 * Moves a finished recording from Agora's upload folder to the couple's private folder, checks
 * it, marks it ready and adds its one chat message. Returns "ready", or why it isn't yet.
 */
async function finalizeRecording(coupleId, callId) {
  const ref = callRef(coupleId, callId);
  const snap = await ref.get();
  if (!snap.exists) return "missing";
  const call = snap.data();
  const status = (call.recording && call.recording.status) || RECORDING.WAITING;
  if ([RECORDING.READY, RECORDING.DELETED, RECORDING.NONE].includes(status)) return status;
  // The recorder can upload a part of a long call while it's still going; wait for the end
  if (logic.isActive(call)) return "call_active";

  const bucket = getStorage().bucket();
  const [stagingFiles] = await bucket.getFiles({ prefix: logic.stagingPrefix(callId) });
  const slices = logic.pickRecordingSlices(stagingFiles.map((file) => file.name));
  const paths = [];
  let totalBytes = 0;

  if (slices.length > 0) {
    await ref.update({ "recording.status": RECORDING.PROCESSING, "recording.updatedAt": FieldValue.serverTimestamp() });
    const sizes = [];
    for (const name of slices) {
      const [meta] = await bucket.file(name).getMetadata();
      sizes.push(Number(meta.size || 0));
    }
    if (sizes.every((size) => size < MIN_RECORDING_BYTES)) {
      // Nothing was heard (the call ended as the recorder started): no recording to keep
      await setRecordingFailed(coupleId, callId, "empty_recording", "The recording had no audio");
      await bucket.deleteFiles({ prefix: logic.stagingPrefix(callId), force: true }).catch(() => {});
      await jobRef(callId).set({ open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
      return "empty";
    }
    for (let index = 0; index < slices.length; index++) {
      const size = sizes[index];
      if (size < MIN_RECORDING_BYTES) continue;
      // Kept parts are numbered from 0, so the first one is always recording.mp4
      const destinationPath = logic.finalRecordingPath(coupleId, callId, paths.length);
      const destination = bucket.file(destinationPath);
      await bucket.file(slices[index]).copy(destination, {
        contentType: "audio/mp4",
        cacheControl: "private, max-age=0",
        metadata: { callId, coupleId },
      });
      const [copied] = await destination.getMetadata();
      if (Number(copied.size || 0) !== size) throw new Error(`Recording part ${index} didn't copy fully`);
      paths.push(destinationPath);
      totalBytes += size;
    }
    await bucket.deleteFiles({ prefix: logic.stagingPrefix(callId), force: true }).catch((error) => {
      logger.warn("Couldn't clear the upload folder", { callId, error: describe(error) });
    });
  } else {
    // Moved already (a retry after the move): check what's there
    const [finalFiles] = await bucket.getFiles({ prefix: logic.finalRecordingPrefix(coupleId, callId) });
    const finalNames = logic.pickRecordingSlices(finalFiles.map((file) => file.name).filter((name) => /recording(_\d+)?\.mp4$/.test(name)));
    if (finalNames.length === 0) return "not_yet";
    for (const name of finalNames) {
      const [meta] = await bucket.file(name).getMetadata();
      const size = Number(meta.size || 0);
      if (size < MIN_RECORDING_BYTES) return "not_yet";
      paths.push(name);
      totalBytes += size;
    }
  }

  const startedAt = logic.toMillis(call.recording && call.recording.startedAt) || logic.toMillis(call.connectedAt);
  const endedAt = logic.toMillis(call.endedAt) || Date.now();
  const durationSec = startedAt ? Math.max(1, Math.round((endedAt - startedAt) / 1000)) : Number(call.durationSec || 0);
  const couple = (await coupleRef(coupleId).get()).data();
  const callerName = await displayNameOf(call.callerId, couple);

  const outcome = await db().runTransaction(async (tx) => {
    const fresh = await tx.get(ref);
    const messageSnap = await tx.get(recordingMessageRef(coupleId, callId));
    if (!fresh.exists) return "missing";
    if ((fresh.data().recording || {}).status === RECORDING.DELETED) return RECORDING.DELETED;
    tx.update(ref, {
      "recording.status": RECORDING.READY,
      "recording.readyAt": FieldValue.serverTimestamp(),
      "recording.storagePath": paths[0],
      "recording.storagePaths": paths,
      "recording.sizeBytes": totalBytes,
      "recording.contentType": "audio/mp4",
      "recording.durationSec": durationSec,
      "recording.failureCode": FieldValue.delete(),
      "recording.failureReason": FieldValue.delete(),
      "recording.updatedAt": FieldValue.serverTimestamp(),
    });
    // One message per call: a second finish (retry, repeated webhook) finds it already there
    if (!messageSnap.exists) {
      tx.create(
        recordingMessageRef(coupleId, callId),
        logic.buildRecordingMessage({ call, coupleId, callerName, storagePaths: paths, sizeBytes: totalBytes, durationSec, timestampMs: endedAt })
      );
    }
    return RECORDING.READY;
  });

  if (outcome === RECORDING.DELETED) {
    // Deleted while it was being moved: don't leave the audio behind
    await bucket.deleteFiles({ prefix: logic.finalRecordingPrefix(coupleId, callId), force: true }).catch(() => {});
    return outcome;
  }
  if (outcome === RECORDING.READY) {
    await jobRef(callId).set({ open: false, finalizedAt: FieldValue.serverTimestamp() }, { merge: true });
    await db().collection("conversations").doc(coupleId).set(
      {
        id: coupleId,
        lastMessageText: "[call_recording]",
        lastMessageSenderId: call.callerId,
        lastMessageTimestamp: endedAt,
      },
      { merge: true }
    );
  }
  return outcome;
}

// ---------- Callable functions ----------

exports.callStart = onCall(async (request) => {
  const { coupleId, userId, callId } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  const calleeId = logic.partnerOf(couple, userId);
  if (!calleeId) throw new HttpsError("failed-precondition", "Your partner hasn't joined yet.");

  const nowMs = Date.now();
  await clearStaleActiveCall(coupleId, nowMs);

  const outcome = await db().runTransaction(async (tx) => {
    const existingSnap = await tx.get(callRef(coupleId, callId));
    const jobSnap = await tx.get(jobRef(callId));
    const controlSnap = await tx.get(controlRef(coupleId));
    if (existingSnap.exists) {
      // The app asked twice (a retry after a timeout): the same call
      const existing = existingSnap.data();
      if (!logic.isSameUser(existing.callerId, userId)) throw new HttpsError("already-exists", "Call id in use.");
      return { status: existing.state, isNew: false };
    }
    if (jobSnap.exists) throw new HttpsError("already-exists", "Call id in use.");
    const control = controlSnap.data();
    if (control && control.callId && ACTIVE_STATES.includes(control.state)) {
      const activeSnap = await tx.get(callRef(coupleId, control.callId));
      const active = activeSnap.data();
      if (logic.isActive(active) && !logic.isRingExpired(active, nowMs)) {
        // Both called each other at once: answer theirs instead
        if (active.state === CALL_STATE.RINGING && logic.isSameUser(active.calleeId, userId)) {
          return { status: "incoming", isNew: false, incomingCallId: active.callId, callerId: active.callerId };
        }
        return { status: CALL_STATE.BUSY, isNew: false };
      }
    }
    tx.set(callRef(coupleId, callId), {
      callId,
      coupleId,
      channel: callId,
      callerId: userId,
      calleeId,
      participants: [userId, calleeId],
      state: CALL_STATE.RINGING,
      endReason: null,
      endedBy: null,
      createdAt: FieldValue.serverTimestamp(),
      ringExpiresAt: Timestamp.fromMillis(nowMs + RING_TIMEOUT_MS),
      answeredAt: null,
      connectedAt: null,
      endedAt: null,
      durationSec: 0,
      joined: {},
      recording: { status: RECORDING.WAITING, updatedAt: FieldValue.serverTimestamp() },
      messageId: logic.recordingMessageId(callId),
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.set(controlRef(coupleId), {
      callId,
      state: CALL_STATE.RINGING,
      callerId: userId,
      calleeId,
      ringExpiresAt: Timestamp.fromMillis(nowMs + RING_TIMEOUT_MS),
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.set(jobRef(callId), { callId, coupleId, open: true, createdAt: FieldValue.serverTimestamp() });
    return { status: CALL_STATE.RINGING, isNew: true };
  });

  if (outcome.status === "incoming") {
    return { status: "incoming", callId: outcome.incomingCallId, callerId: outcome.callerId };
  }
  if (outcome.status === CALL_STATE.BUSY) return { status: CALL_STATE.BUSY };
  if (!ACTIVE_STATES.includes(outcome.status)) return { status: outcome.status, callId };

  const ringExpiresAt = nowMs + RING_TIMEOUT_MS;
  if (outcome.isNew) {
    const pushed = await sendCallPush(
      calleeId,
      push.callInviteData({
        callId,
        coupleId,
        callerId: userId,
        callerName: await displayNameOf(userId, couple),
        ringUntilMs: ringExpiresAt,
        nowMs,
      }),
      RING_TIMEOUT_MS,
      coupleId,
      userId
    );
    // No way to reach the other phone, and the app isn't open there either
    if (pushed !== "sent" && !(await isRecentlyOnline(calleeId, nowMs))) {
      await db().runTransaction(async (tx) => {
        const snap = await tx.get(callRef(coupleId, callId));
        const controlSnap = await tx.get(controlRef(coupleId));
        if (!snap.exists || snap.data().state !== CALL_STATE.RINGING) return;
        tx.update(callRef(coupleId, callId), {
          state: CALL_STATE.UNAVAILABLE,
          endReason: pushed,
          endedAt: Timestamp.fromMillis(nowMs),
          "recording.status": RECORDING.NONE,
          updatedAt: FieldValue.serverTimestamp(),
        });
        if ((controlSnap.data() || {}).callId === callId) {
          tx.set(controlRef(coupleId), { state: CALL_STATE.UNAVAILABLE, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        }
        tx.set(jobRef(callId), { open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
      });
      return { status: CALL_STATE.UNAVAILABLE, callId };
    }
  }
  return { status: outcome.status, callId, ringExpiresAt, ...joinInfo(callId, CALLER_UID) };
});

exports.callAnswer = onCall(async (request) => {
  const { coupleId, callId, userId } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  const nowMs = Date.now();

  const status = await db().runTransaction(async (tx) => {
    const ref = callRef(coupleId, callId);
    const snap = await tx.get(ref);
    const controlSnap = await tx.get(controlRef(coupleId));
    if (!snap.exists) throw new HttpsError("not-found", "No such call.");
    const call = snap.data();
    if (!logic.isSameUser(call.calleeId, userId)) throw new HttpsError("permission-denied", "This call isn't for you.");
    const isCurrent = (controlSnap.data() || {}).callId === callId;
    if (call.state !== CALL_STATE.RINGING) return call.state;
    if (logic.isRingExpired(call, nowMs)) {
      tx.update(ref, {
        state: CALL_STATE.MISSED,
        endReason: "no_answer",
        endedAt: Timestamp.fromMillis(nowMs),
        "recording.status": RECORDING.NONE,
        updatedAt: FieldValue.serverTimestamp(),
      });
      if (isCurrent) tx.set(controlRef(coupleId), { state: CALL_STATE.MISSED, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(jobRef(callId), { open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
      return CALL_STATE.MISSED;
    }
    tx.update(ref, { state: CALL_STATE.CONNECTING, answeredAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
    if (isCurrent) tx.set(controlRef(coupleId), { state: CALL_STATE.CONNECTING, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return CALL_STATE.CONNECTING;
  });

  if (status === CALL_STATE.CONNECTING || status === CALL_STATE.CONNECTED) {
    return { status, callId, ...joinInfo(callId, CALLEE_UID) };
  }
  return { status, callId };
});

exports.callEnd = onCall(async (request) => {
  const { coupleId, callId, userId, reason } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  await requireParticipantCall(coupleId, callId, userId);
  const result = await endCallTx(coupleId, callId, userId, String(reason || "hangup"), Date.now());
  await afterCallEnded(coupleId, result);
  const after = (await callRef(coupleId, callId).get()).data() || {};
  return { status: after.state || "ended", callId };
});

/**
 * The app joined the call's channel (or saw the other phone join). Once Agora itself shows both
 * phones in the channel, the call is connected and its recording starts.
 */
exports.callJoined = onCall(async (request) => {
  const { coupleId, callId, userId } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  const call = await requireParticipantCall(coupleId, callId, userId);
  if (logic.isActive(call)) {
    await callRef(coupleId, callId).update({ [`joined.${userId}`]: FieldValue.serverTimestamp() });
  }
  if (call.state !== CALL_STATE.CONNECTING && call.state !== CALL_STATE.CONNECTED) {
    return { status: call.state, callId };
  }

  const callerJoined = Boolean(
    (call.joined && (call.joined[call.callerId] || call.joined[userId])) ||
    logic.isSameUser(userId, call.callerId)
  );
  const calleeJoined = Boolean(
    (call.joined && (call.joined[call.calleeId] || call.joined[userId])) ||
    logic.isSameUser(userId, call.calleeId)
  );
  if (!callerJoined || !calleeJoined) {
    return { status: call.state, callId, bothInChannel: false };
  }

  const claim = await db().runTransaction(async (tx) => {
    const ref = callRef(coupleId, callId);
    const snap = await tx.get(ref);
    const controlSnap = await tx.get(controlRef(coupleId));
    const current = snap.data();
    if (!current || (current.state !== CALL_STATE.CONNECTING && current.state !== CALL_STATE.CONNECTED)) {
      return { start: false, state: current ? current.state : "missing" };
    }
    const updates = { updatedAt: FieldValue.serverTimestamp() };
    if (current.state === CALL_STATE.CONNECTING) {
      updates.state = CALL_STATE.CONNECTED;
      updates.connectedAt = FieldValue.serverTimestamp();
      if ((controlSnap.data() || {}).callId === callId) {
        tx.set(controlRef(coupleId), { state: CALL_STATE.CONNECTED, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      }
    }
    // Only the first confirmation starts the recorder
    const start = (current.recording || {}).status === RECORDING.WAITING;
    if (start) {
      updates["recording.status"] = RECORDING.STARTING;
      updates["recording.updatedAt"] = FieldValue.serverTimestamp();
    }
    tx.update(ref, updates);
    return { start, state: CALL_STATE.CONNECTED };
  });

  if (claim.start) await startRecording(coupleId, callId);
  const after = (await callRef(coupleId, callId).get()).data() || {};
  return { status: after.state || claim.state, callId, bothInChannel: true, recordingStatus: (after.recording || {}).status || null };
});

exports.callRenewToken = onCall(async (request) => {
  const { coupleId, callId, userId } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  const call = await requireParticipantCall(coupleId, callId, userId);
  if (!logic.isActive(call)) throw new HttpsError("failed-precondition", "The call is over.");
  return { status: call.state, callId, ...joinInfo(callId, logic.agoraUidFor(call, userId)) };
});

/**
 * Finalizes an in-app call recording uploaded by the phone to Storage.
 */
exports.callFinalizeClientRecording = onCall(async (request) => {
  const { coupleId, callId, userId } = request.data || {};
  requireCallId(callId);
  const couple = await requireMember(request, coupleId);
  requireCoupleUser(couple, userId);
  await requireParticipantCall(coupleId, callId, userId);
  const status = await finalizeRecording(coupleId, callId);
  return { status, callId };
});

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
      callerName: await displayNameOf(callerId, couple),
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

// ---------- Sweeper ----------

async function sweepCall(job, nowMs) {
  const { callId, coupleId } = job;
  if (!logic.isValidCallId(callId) || !logic.isSafeId(coupleId)) return;
  const call = (await callRef(coupleId, callId).get()).data();
  if (!call) {
    await jobRef(callId).set({ open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
    return;
  }

  if (logic.isRingExpired(call, nowMs)) {
    await afterCallEnded(coupleId, await endCallTx(coupleId, callId, call.callerId, "no_answer", nowMs));
    return;
  }
  if (logic.isConnectTimedOut(call, nowMs)) {
    await afterCallEnded(coupleId, await endCallTx(coupleId, callId, call.calleeId, "error", nowMs));
    return;
  }

  if (call.state === CALL_STATE.CONNECTED) {
    const connectedAt = logic.toMillis(call.connectedAt);
    if (connectedAt && nowMs - connectedAt > UPLOAD_GIVE_UP_MS) {
      await afterCallEnded(coupleId, await endCallTx(coupleId, callId, call.callerId, "peer_lost", nowMs));
    }
    return;
  }
  if (logic.isActive(call)) return;

  // The call is over
  const recordingStatus = (call.recording && call.recording.status) || RECORDING.WAITING;
  if (recordingStatus === RECORDING.STARTING || recordingStatus === RECORDING.RECORDING) {
    await stopRecording(coupleId, callId);
  }
  if ([RECORDING.STARTING, RECORDING.RECORDING, RECORDING.UPLOADING, RECORDING.PROCESSING, RECORDING.FAILED].includes(recordingStatus)) {
    const outcome = await finalizeRecording(coupleId, callId);
    if (outcome === "not_yet") {
      const endedAt = logic.toMillis(call.endedAt);
      // A recorder that left mid-call still uploads what it had; any other failure has no files
      const mayStillArrive = recordingStatus !== RECORDING.FAILED || call.recording.failureCode === "recorder_left";
      if (!mayStillArrive || (endedAt && nowMs - endedAt > UPLOAD_GIVE_UP_MS)) {
        await setRecordingFailed(coupleId, callId, "upload_timeout", "The recording never arrived");
        await jobRef(callId).set({ open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
      }
    }
    return;
  }
  if (SETTLED_RECORDING.includes(recordingStatus) || recordingStatus === RECORDING.WAITING) {
    await jobRef(callId).set({ open: false, closedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
}

exports.callSweeper = onSchedule("every 5 minutes", async () => {
  const nowMs = Date.now();
  const open = await db().collection("callJobs").where("open", "==", true).limit(50).get();
  for (const doc of open.docs) {
    try {
      await sweepCall(doc.data(), nowMs);
    } catch (error) {
      logger.warn("Sweeping a call failed", { callId: doc.id, error: describe(error) });
    }
  }
});
