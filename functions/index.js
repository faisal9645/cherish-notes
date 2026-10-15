/**
 * Pushes to the partner's phone, so things arrive even with the app closed. Pushes carry data only
 * (no visible notification of their own); the app decides what to show from that phone's settings
 * (hidden content, Notes disguise).
 *
 * - pushPartner: the sending phone asks for a push right after saving what it's about (a message,
 *   a heartbeat or its "felt it", a call ringing or called off), and only when the partner needs
 *   one: not while they're in Cherish (it shows there by itself), not with their notifications
 *   off, and for messages one alert until they're back in Cherish (checked here again). Nothing
 *   runs on its own for messages or heartbeats, so a function only runs when a push is due.
 * - Voice calls: see calls.js (a trigger rings the called phone too, as a backup).
 */
const logger = require("firebase-functions/logger");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const logic = require("./lib/callLogic");
const {
  sendToTarget,
  pushToMember,
  claimMessageAlert,
  releaseMessageAlert,
  pushOnce,
  displayNameOf,
  callInviteData,
  callCancelData,
} = require("./lib/push");

initializeApp();

/** A message is pushed when its phone asks only this soon after it was written (a queued one still counts). */
const MESSAGE_PUSH_WINDOW_MS = 24 * 60 * 60 * 1000;
/** A message with one of these statuses is on the other phone's screen already. */
const SEEN_STATUSES = new Set(["DELIVERED", "READ"]);
/** A heartbeat older than this isn't felt any more, so it isn't pushed. */
const HEARTBEAT_PUSH_WINDOW_MS = 2 * 60 * 1000;
/** High priority wakes a dozing phone; one that's off for over a day doesn't get a stale burst later. */
const MESSAGE_ANDROID = { priority: "high", ttl: 24 * 60 * 60 * 1000 };
/** A heartbeat only counts if it arrives right away. */
const HEARTBEAT_ANDROID = { priority: "high", ttl: 60 * 1000 };
/**
 * "Felt it" mostly reaches a phone that's still open on the heartbeat it just sent (where the app
 * shows it without a notification), so it doesn't wake a sleeping phone.
 */
const HEARTBEAT_FELT_ANDROID = { priority: "normal", ttl: 60 * 1000 };
/** Long enough for a phone that's briefly offline to still stop ringing and show the missed call. */
const CALL_CANCEL_TTL_MS = 5 * 60 * 1000;

/** Same wording as the app's own notifications. */
function previewText(message) {
  switch (String(message.type || "TEXT").toUpperCase()) {
    case "TEXT":
      return String(message.text || "").slice(0, 300) || "New message";
    case "IMAGE":
      return "Photo";
    case "AUDIO":
      return "Voice message";
    case "VIDEO":
      return message.isVideoNote ? "Video note" : "Video";
    case "DOCUMENT":
      return "Document";
    default:
      return "New message";
  }
}

/** Like the app: a member's id, or "user_<name>" from the name the couple was set up with. */
function memberId(id, name) {
  const clean = String(id || "").trim();
  if (clean) return clean;
  const fromName = String(name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  return fromName ? `user_${fromName}` : "";
}

/** The couple member who isn't [senderId]. */
function otherMember(couple, senderId) {
  const cleanSender = String(senderId || "").replace(/^user_/, "").toLowerCase();
  const p1 = memberId(couple.partner1Id, couple.partner1Name);
  const p2 = memberId(couple.partner2Id, couple.partner2Name);
  const isP1 = p1 && p1.replace(/^user_/, "").toLowerCase() === cleanSender;
  const isP2 = p2 && p2.replace(/^user_/, "").toLowerCase() === cleanSender;
  if (isP1) return p2 || null;
  if (isP2) return p1 || null;
  const members = [p1, p2].filter((id) => id && id.replace(/^user_/, "").toLowerCase() !== cleanSender);
  return members[0] || null;
}

/**
 * Who a message is for: its receiverId and the other member of the couple. Usually the same person;
 * when the sending phone had an old id for the partner, the couple record still reaches them.
 */
async function receiversOf(db, message, conversationId) {
  const senderId = String(message.senderId || "");
  const receivers = [];
  const receiverId = String(message.receiverId || "").trim();
  if (!logic.isPlaceholderId(receiverId) && !logic.isSameUser(receiverId, senderId)) receivers.push(receiverId);

  const couple = await db.collection("couples").doc(conversationId).get();
  const other = couple.exists ? otherMember(couple.data(), senderId) : null;
  if (other && !logic.isSameUser(other, senderId)) receivers.push(other);
  return receivers;
}

/** A message the phones mark deleted: Firestore names the field "deleted" when a whole message is written. */
function isDeletedMessage(message) {
  return message.isDeleted === true || message.deleted === true;
}

/**
 * A call's entry in the chat (its recording, "Missed voice call", "Voice call (2:05)"): the call's
 * own pushes already rang and said it was missed, so these don't push again.
 */
function isCallLogMessage(message) {
  return String(message.type || "").toUpperCase() === "CALL_RECORDING" || Boolean(String(message.callId || "").trim());
}

/**
 * Pushes [message] to whoever it's for: their phone alerts once, then not again until they've been
 * back in Cherish (claimMessageAlert); nothing goes with their notifications off. Answers
 * { status, notifiedAt }: notifiedAt is when the alert that counts went out (server time), so
 * the sending phone knows not to ask again until the partner has been in Cherish since.
 */
async function pushMessage(db, conversationId, messageId, message) {
  const senderId = String(message.senderId || "");
  const receiverIds = await receiversOf(db, message, conversationId);
  if (receiverIds.length === 0) {
    logger.info("No receiver for message", { conversationId, messageId });
    return { status: "no_receiver" };
  }
  const alert = await claimMessageAlert(db, conversationId, receiverIds);
  if (alert.status === "already_notified" || alert.status === "notifications_off") return alert;

  // Data only: the app builds the notification itself, as that phone's settings say (masked,
  // one alert until it's opened)
  const data = {
    type: "message",
    messageId: String(message.id || messageId),
    conversationId,
    senderId,
    senderName: String(message.senderName || ""),
    messageText: previewText(message),
  };
  // Their own profile's address first; every address the couple knows for them only if that fails
  let status = "no_push_token";
  if (alert.token) {
    const target = { token: alert.token, ref: alert.profileRef, field: "fcmToken" };
    if ((await sendToTarget(db, target, data, MESSAGE_ANDROID)) === "sent") status = "sent";
  }
  if (status !== "sent") {
    status = await pushToMember(db, {
      receiverId: receiverIds,
      senderId,
      coupleId: conversationId,
      data,
      android: MESSAGE_ANDROID,
      skipWhenOff: true,
    });
  }
  if (status !== "sent") await releaseMessageAlert(db, alert);
  return { status, notifiedAt: status === "sent" ? alert.notifiedAt || 0 : 0 };
}

/** A heartbeat ([type] "heartbeat") or its "felt it" answer ("heartbeat_felt"), to the other member. */
async function pushSignal(db, coupleId, couple, signal, type) {
  const senderId = String(signal.senderId || "");
  const receiverId = otherMember(couple, senderId);
  if (!receiverId) return "no_receiver";
  const felt = type === "heartbeat_felt";
  return pushToMember(db, {
    receiverId,
    senderId,
    coupleId,
    data: {
      type,
      signalId: String(signal.id),
      senderId,
      sentAt: String(logic.toMillis(signal.at) || Date.now()),
    },
    android: felt ? HEARTBEAT_FELT_ANDROID : HEARTBEAT_ANDROID,
    // With notifications off, a closed app doesn't play heartbeats ("felt it" still vibrates)
    skipWhenOff: !felt,
  });
}

// ---------- Pushes the sending phone asks for ----------

async function pushMessageById(db, conversationId, messageId, nowMs) {
  if (!logic.isSafeId(messageId)) throw new HttpsError("invalid-argument", "Bad message id.");
  const snap = await db.collection("conversations").doc(conversationId).collection("messages").doc(messageId).get();
  if (!snap.exists) return { status: "not_found" };
  const message = snap.data();
  if (isDeletedMessage(message) || isCallLogMessage(message)) return { status: "skipped" };
  // The partner's open chat has it already
  if (SEEN_STATUSES.has(String(message.status || "").toUpperCase())) return { status: "seen_in_app" };
  const writtenAt = Number(message.timestamp || 0);
  if (!writtenAt || Math.abs(nowMs - writtenAt) > MESSAGE_PUSH_WINDOW_MS) return { status: "stale" };
  return pushMessage(db, conversationId, messageId, message);
}

async function pushCallInvite(db, coupleId, callId, nowMs) {
  if (!logic.isValidCallId(callId)) throw new HttpsError("invalid-argument", "Bad call id.");
  const coupleRef = db.collection("couples").doc(coupleId);
  const [controlSnap, coupleSnap] = await Promise.all([coupleRef.collection("callControl").doc("active").get(), coupleRef.get()]);
  const control = controlSnap.data();
  if (!control || control.callId !== callId || control.state !== logic.CALL_STATE.RINGING) return "not_ringing";
  const ringUntil = logic.ringUntilMs(control) || nowMs + logic.RING_TIMEOUT_MS;
  if (ringUntil <= nowMs) return "expired";
  const couple = coupleSnap.data() || {};
  const callerId = String(control.callerId || "");
  const calleeIds = logic.calleesFor(control, couple);
  if (calleeIds.length === 0) return "no_receiver";
  return pushOnce(db, coupleId, `call_invite:${callId}`, async () =>
    pushToMember(db, {
      receiverId: calleeIds,
      senderId: callerId,
      coupleId,
      data: callInviteData({
        callId,
        coupleId,
        callerId,
        callerName: await displayNameOf(db, callerId, couple),
        ringUntilMs: ringUntil,
        nowMs,
      }),
      android: { priority: "high", ttl: ringUntil - nowMs },
    })
  );
}

async function pushCallCancel(db, coupleId, callId) {
  if (!logic.isValidCallId(callId)) throw new HttpsError("invalid-argument", "Bad call id.");
  const coupleRef = db.collection("couples").doc(coupleId);
  const [callSnap, coupleSnap] = await Promise.all([coupleRef.collection("calls").doc(callId).get(), coupleRef.get()]);
  const call = callSnap.data();
  if (!call) return "not_found";
  // Only a call that rang out or was called off: one that was answered and ended wasn't missed
  if (call.state !== logic.CALL_STATE.MISSED && call.state !== logic.CALL_STATE.CANCELLED) return "skipped";
  const callerId = String(call.callerId || "");
  const calleeIds = logic.calleesFor(call, coupleSnap.data() || {});
  if (calleeIds.length === 0) return "no_receiver";
  return pushOnce(db, coupleId, `call_cancel:${callId}`, () =>
    pushToMember(db, {
      receiverId: calleeIds,
      senderId: callerId,
      coupleId,
      data: callCancelData({ callId, coupleId, reason: call.state }),
      android: { priority: "high", ttl: CALL_CANCEL_TTL_MS },
    })
  );
}

async function pushHeartbeat(db, coupleId, signalId, kind, nowMs) {
  if (!logic.isSafeId(signalId)) throw new HttpsError("invalid-argument", "Bad heartbeat id.");
  const couple = (await db.collection("couples").doc(coupleId).get()).data();
  const signal = couple && (kind === "heartbeat" ? couple.signal : couple.signalFelt);
  if (!signal || signal.id !== signalId || signal.type !== kind) return "not_found";
  const sentAt = logic.toMillis(signal.at);
  if (sentAt && nowMs - sentAt > HEARTBEAT_PUSH_WINDOW_MS) return "stale";
  return pushSignal(db, coupleId, couple, signal, kind);
}

/** Answers that mean nothing went wrong although no push went out. */
const FINE_WITHOUT_PUSH = new Set(["already_sent", "already_notified", "seen_in_app", "notifications_off"]);

/**
 * The sending phone asks for its partner's push right after saving what it's about: a message,
 * a call ringing or called off, a heartbeat or its "felt it" (for a call, the trigger in calls.js
 * asks too; whichever is first sends it, see pushOnce). Only what's saved is pushed, to the couple
 * member it's for, while it's still current.
 */
exports.pushPartner = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const data = request.data || {};
  const coupleId = data.coupleId;
  if (!logic.isSafeId(coupleId)) throw new HttpsError("invalid-argument", "Bad couple id.");
  const db = getFirestore();
  const nowMs = Date.now();
  let result;
  switch (data.kind) {
    case "message":
      result = await pushMessageById(db, coupleId, data.messageId, nowMs);
      break;
    case "call_invite":
      result = { status: await pushCallInvite(db, coupleId, data.callId, nowMs) };
      break;
    case "call_cancel":
      result = { status: await pushCallCancel(db, coupleId, data.callId) };
      break;
    case "heartbeat":
    case "heartbeat_felt":
      result = { status: await pushHeartbeat(db, coupleId, data.signalId, data.kind, nowMs) };
      break;
    default:
      throw new HttpsError("invalid-argument", "Unknown push.");
  }
  if (result.status !== "sent" && !FINE_WITHOUT_PUSH.has(result.status)) {
    logger.info("Partner push not sent", { kind: data.kind, coupleId, status: result.status });
  }
  return { status: result.status, notifiedAt: result.notifiedAt || 0 };
});

// Voice calls and their recordings
Object.assign(exports, require("./calls"));
