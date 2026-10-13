/**
 * The rules of a call, with no Firebase or network in them, so they can be tested on their own.
 *
 * A call: ringing -> connecting (answered) -> connected (both phones confirmed in the channel by
 * Agora) -> ended. Or it stops early: declined, cancelled, missed, busy, unavailable, failed.
 * Its recording: waiting -> starting -> recording -> uploading -> processing -> ready, or failed,
 * deleted, or none (the call never connected, so there's nothing to record).
 */
const crypto = require("crypto");

const CALL_STATE = Object.freeze({
  RINGING: "ringing",
  CONNECTING: "connecting",
  CONNECTED: "connected",
  ENDED: "ended",
  DECLINED: "declined",
  CANCELLED: "cancelled",
  MISSED: "missed",
  BUSY: "busy",
  UNAVAILABLE: "unavailable",
  FAILED: "failed",
});

const ACTIVE_STATES = Object.freeze([CALL_STATE.RINGING, CALL_STATE.CONNECTING, CALL_STATE.CONNECTED]);

const RECORDING = Object.freeze({
  WAITING: "waiting",
  STARTING: "starting",
  RECORDING: "recording",
  UPLOADING: "uploading",
  PROCESSING: "processing",
  READY: "ready",
  FAILED: "failed",
  DELETED: "deleted",
  NONE: "none",
});

/** Recording states that are settled: nothing more will happen to them on their own. */
const SETTLED_RECORDING = Object.freeze([RECORDING.READY, RECORDING.FAILED, RECORDING.DELETED, RECORDING.NONE]);

/** How long a phone rings before the call counts as missed. */
const RING_TIMEOUT_MS = 75 * 1000;
/** An answered call that hasn't connected by now has failed. */
const CONNECT_TIMEOUT_MS = 60 * 1000;
/** How long a token works (and its join/publish rights last); the app renews it before then. */
const TOKEN_TTL_SECONDS = 20 * 60;
/** Fixed Agora user ids inside a call's own channel (the channel is the call id). */
const CALLER_UID = 1001;
const CALLEE_UID = 1002;
const RECORDER_UID = 900001;
/** A recording file smaller than this isn't a real recording. */
const MIN_RECORDING_BYTES = 1024;
/** Where Agora uploads (its path prefix only allows letters and digits), before the move. */
const STAGING_ROOT = "callrecstaging";

/** Ids an older app version wrote when the partner wasn't known yet. */
const PLACEHOLDER_IDS = new Set(["", "user_partner", "user_me"]);

/** Like the app: a member's id, or "user_<name>" from the name the couple was set up with. */
function memberId(id, name) {
  const clean = String(id || "").trim();
  if (clean) return clean;
  const fromName = String(name || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "");
  return fromName ? `user_${fromName}` : "";
}

/** Whether two user IDs represent the same user (handles optional 'user_' prefix and case). */
function isSameUser(a, b) {
  if (!a || !b) return false;
  if (a === b) return true;
  const cleanA = String(a).trim().replace(/^user_/, "").toLowerCase();
  const cleanB = String(b).trim().replace(/^user_/, "").toLowerCase();
  return cleanA.length > 0 && cleanA === cleanB;
}

/** The couple's two members (app user ids), without blanks or placeholders. */
function coupleMembers(couple) {
  if (!couple) return [];
  const ids = [memberId(couple.partner1Id, couple.partner1Name), memberId(couple.partner2Id, couple.partner2Name)];
  return [...new Set(ids.filter((id) => id && !PLACEHOLDER_IDS.has(id)))];
}

/** Checks whether [userId] is a member of [couple], comparing IDs and names flexibly. */
function isMemberOfCouple(couple, userId) {
  if (!couple || !userId) return false;
  const members = coupleMembers(couple);
  if (members.some((m) => isSameUser(m, userId))) return true;
  const cleanUser = String(userId).trim().replace(/^user_/, "").toLowerCase();
  const p1 = String(couple.partner1Id || "").trim().replace(/^user_/, "").toLowerCase();
  const p2 = String(couple.partner2Id || "").trim().replace(/^user_/, "").toLowerCase();
  const n1 = String(couple.partner1Name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  const n2 = String(couple.partner2Name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  return (p1 && p1 === cleanUser) ||
         (p2 && p2 === cleanUser) ||
         (n1 && n1 === cleanUser) ||
         (n2 && n2 === cleanUser);
}

/** Finds the partner of [userId] in [couple]. Never returns the user themselves. */
function partnerOf(couple, userId) {
  if (!couple || !userId) return null;
  const cleanUser = String(userId).trim().replace(/^user_/, "").toLowerCase();
  const p1 = memberId(couple.partner1Id, couple.partner1Name);
  const p2 = memberId(couple.partner2Id, couple.partner2Name);
  const p1Clean = String(p1 || "").replace(/^user_/, "").toLowerCase();
  const p2Clean = String(p2 || "").replace(/^user_/, "").toLowerCase();
  const n1Clean = String(couple.partner1Name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  const n2Clean = String(couple.partner2Name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");

  if (cleanUser === p1Clean || (n1Clean && cleanUser === n1Clean)) {
    return p2 && !isSameUser(p2, userId) ? p2 : (couple.partner2Id || (n2Clean ? `user_${n2Clean}` : null));
  }
  if (cleanUser === p2Clean || (n2Clean && cleanUser === n2Clean)) {
    return p1 && !isSameUser(p1, userId) ? p1 : (couple.partner1Id || (n1Clean ? `user_${n1Clean}` : null));
  }
  const members = coupleMembers(couple);
  return members.find((id) => !isSameUser(id, userId)) || null;
}

/** A Firestore document id we accept from a phone: no slashes, not empty, not huge. */
function isSafeId(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 128 && !/[/\s]/.test(value) &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value);
}

/** A call id: letters and digits only (it's also the Agora channel and part of a storage path). */
function isValidCallId(value) {
  return typeof value === "string" && /^[A-Za-z0-9]{16,40}$/.test(value);
}

function isActive(call) {
  return Boolean(call) && ACTIVE_STATES.includes(call.state);
}

function toMillis(value) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return 0;
}

/** A ringing call nobody answered in time. */
function isRingExpired(call, nowMs) {
  if (!call || call.state !== CALL_STATE.RINGING) return false;
  const until = toMillis(call.ringExpiresAt) || toMillis(call.createdAt) + RING_TIMEOUT_MS;
  return nowMs > until;
}

/** An answered call that never got both phones into the channel. */
function isConnectTimedOut(call, nowMs) {
  if (!call || call.state !== CALL_STATE.CONNECTING) return false;
  const answeredAt = toMillis(call.answeredAt);
  return answeredAt > 0 && nowMs - answeredAt > CONNECT_TIMEOUT_MS;
}

/** The Agora uid a member uses in this call, or null for someone who isn't in it. */
function agoraUidFor(call, userId) {
  if (!call || !userId) return null;
  if (isSameUser(userId, call.callerId)) return CALLER_UID;
  if (isSameUser(userId, call.calleeId)) return CALLEE_UID;
  return null;
}

const END_REASONS = new Set(["hangup", "peer_lost", "network", "error", "permission", "no_answer", "cancelled", "declined"]);

/**
 * What hanging up does to [call] when [userId] does it, for [reason] (as the phone says it).
 * Null when nothing changes (the call is already over).
 */
function endTransition(call, userId, reason) {
  if (!isActive(call)) return null;
  const why = END_REASONS.has(reason) ? reason : "hangup";
  if (call.state === CALL_STATE.RINGING) {
    if (isSameUser(userId, call.calleeId)) return { state: CALL_STATE.DECLINED, endReason: "declined" };
    if (why === "no_answer") return { state: CALL_STATE.MISSED, endReason: "no_answer" };
    return { state: CALL_STATE.CANCELLED, endReason: "cancelled" };
  }
  if (call.state === CALL_STATE.CONNECTING && !call.connectedAt) {
    return { state: CALL_STATE.FAILED, endReason: why === "hangup" ? "cancelled" : why };
  }
  return { state: CALL_STATE.ENDED, endReason: why };
}

/** Seconds the call was connected (0 if it never was). */
function connectedSeconds(call, endedAtMs) {
  const connectedAt = toMillis(call && call.connectedAt);
  if (!connectedAt) return 0;
  return Math.max(0, Math.round((endedAtMs - connectedAt) / 1000));
}

/** Checks Agora's webhook signature: hex HMAC-SHA256 of the raw body with the webhook secret. */
function verifyAgoraSignature(rawBody, signature, secret) {
  if (!rawBody || !signature || !secret) return false;
  const expected = crypto.createHmac("sha256", secret).update(rawBody).digest("hex");
  const given = String(signature).trim().toLowerCase();
  if (given.length !== expected.length) return false;
  return crypto.timingSafeEqual(Buffer.from(given, "utf8"), Buffer.from(expected, "utf8"));
}

/** Agora's upload folder for a call (the call id is letters and digits, as Agora requires). */
function stagingPrefix(callId) {
  return `${STAGING_ROOT}/${callId}/`;
}

function stagingPathSegments(callId) {
  return [STAGING_ROOT, callId];
}

/** Where a call's recording lives for good. A recording over ~3 hours comes in more parts. */
function finalRecordingPath(coupleId, callId, index) {
  const name = index > 0 ? `recording_${index}.mp4` : "recording.mp4";
  return `${finalRecordingPrefix(coupleId, callId)}${name}`;
}

function finalRecordingPrefix(coupleId, callId) {
  return `couples/${coupleId}/call-recordings/${callId}/`;
}

/**
 * The MP4 parts of a recording, in order. Agora names them <sid>_<cname>_<index>.mp4; anything
 * else in the folder (the HLS playlist and its pieces) is ignored.
 */
function pickRecordingSlices(objectNames) {
  return (objectNames || [])
    .filter((name) => typeof name === "string" && name.toLowerCase().endsWith(".mp4"))
    .map((name) => {
      const match = name.match(/_(\d+)\.mp4$/i);
      return { name, index: match ? Number(match[1]) : 0 };
    })
    .sort((a, b) => a.index - b.index || a.name.localeCompare(b.name))
    .map((slice) => slice.name);
}

/** The chat message id for a call's recording: one per call, so retries can't add a second. */
function recordingMessageId(callId) {
  return `callrec_${callId}`;
}

/** The chat message for a ready recording, shaped like the app's own messages. */
function buildRecordingMessage({ call, coupleId, callerName, storagePaths, sizeBytes, durationSec, timestampMs }) {
  const id = recordingMessageId(call.callId);
  return {
    id,
    conversationId: coupleId,
    senderId: call.callerId,
    senderName: callerName || "",
    receiverId: call.calleeId,
    text: "Call recording",
    timestamp: timestampMs,
    type: "CALL_RECORDING",
    mediaUrl: null,
    mediaName: "recording.mp4",
    mediaSize: sizeBytes,
    durationSeconds: durationSec,
    waveform: [],
    status: "SENT",
    readTimestamp: null,
    // The field names the app's Firestore mapping uses for these flags
    edited: false,
    deleted: false,
    starred: false,
    pinned: false,
    audioPlayed: false,
    isVideoNote: false,
    replyToMessageId: null,
    replyToText: null,
    replyToSenderName: null,
    reactions: {},
    mediaUrls: [],
    hasLink: false,
    thumbnailUrls: [],
    effect: null,
    callId: call.callId,
    recordingPath: storagePaths[0],
    recordingPaths: storagePaths,
  };
}

/**
 * Whether Agora's recording query says it is recording right now: status 5 ("In progress"). Status
 * 4 only means its parts are ready, so it doesn't count yet.
 */
function isAgoraRecordingActive(queryResponse) {
  const status = queryResponse && queryResponse.serverResponse && queryResponse.serverResponse.status;
  return status === 5;
}

module.exports = {
  CALL_STATE,
  ACTIVE_STATES,
  RECORDING,
  SETTLED_RECORDING,
  RING_TIMEOUT_MS,
  CONNECT_TIMEOUT_MS,
  TOKEN_TTL_SECONDS,
  CALLER_UID,
  CALLEE_UID,
  RECORDER_UID,
  MIN_RECORDING_BYTES,
  STAGING_ROOT,
  memberId,
  isSameUser,
  coupleMembers,
  isMemberOfCouple,
  partnerOf,
  isSafeId,
  isValidCallId,
  isActive,
  toMillis,
  isRingExpired,
  isConnectTimedOut,
  agoraUidFor,
  endTransition,
  connectedSeconds,
  verifyAgoraSignature,
  stagingPrefix,
  stagingPathSegments,
  finalRecordingPath,
  finalRecordingPrefix,
  pickRecordingSlices,
  recordingMessageId,
  buildRecordingMessage,
  isAgoraRecordingActive,
};
