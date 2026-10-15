/**
 * Pushes to the partner's phone, so things arrive even with the app closed. Pushes carry data only
 * (no visible notification of their own); the app decides what to show from that phone's settings
 * (off, hidden content, Notes disguise).
 *
 * - notifyPartnerOnMessage: a new message.
 * - heartbeatPartnerOnUpdate: a "thinking of you" heartbeat (a vibration, only if delivered right away).
 * - pushPartner: the sending phone asks for those pushes (and a call's) directly, right after
 *   saving what they're about, so they don't depend on the database triggers alone.
 * - Voice calls and their recordings: see calls.js.
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const logic = require("./lib/callLogic");
const { pushToMember, displayNameOf, callInviteData, callCancelData } = require("./lib/push");

initializeApp();

/** A message is pushed when its phone asks only this soon after it was written (a queued one still counts). */
const MESSAGE_PUSH_WINDOW_MS = 24 * 60 * 60 * 1000;
/** A heartbeat older than this isn't felt any more, so it isn't pushed. */
const HEARTBEAT_PUSH_WINDOW_MS = 2 * 60 * 1000;
/** High priority wakes a dozing phone; one that's off for over a day doesn't get a stale burst later. */
const MESSAGE_ANDROID = { priority: "high", ttl: 24 * 60 * 60 * 1000 };
/** A heartbeat only counts if it arrives right away. */
const HEARTBEAT_ANDROID = { priority: "high", ttl: 60 * 1000 };
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

/** The receiver: the message's receiverId, or the other member of the couple. */
async function receiverOf(db, message, conversationId) {
  const senderId = String(message.senderId || "");
  const receiverId = String(message.receiverId || "").trim();
  if (!logic.isPlaceholderId(receiverId) && !logic.isSameUser(receiverId, senderId)) return receiverId;

  const couple = await db.collection("couples").doc(conversationId).get();
  return couple.exists ? otherMember(couple.data(), senderId) : null;
}

/**
 * A call's entry in the chat (its recording, "Missed voice call", "Voice call (2:05)"): the call's
 * own pushes already rang and said it was missed, so these don't push again.
 */
function isCallLogMessage(message) {
  return String(message.type || "").toUpperCase() === "CALL_RECORDING" || Boolean(String(message.callId || "").trim());
}

/** Pushes [message] to whoever it's for. */
async function pushMessage(db, conversationId, messageId, message) {
  const receiverId = await receiverOf(db, message, conversationId);
  if (!receiverId) {
    logger.info("No receiver for message", { conversationId, messageId });
    return "no_receiver";
  }
  // Data only: the app builds the notification itself (masked when its settings say so)
  return pushToMember(db, {
    receiverId,
    senderId: String(message.senderId || ""),
    coupleId: conversationId,
    data: {
      type: "message",
      messageId: String(message.id || messageId),
      conversationId,
      senderId: String(message.senderId || ""),
      senderName: String(message.senderName || ""),
      messageText: previewText(message),
    },
    android: MESSAGE_ANDROID,
  });
}

/** A heartbeat ([type] "heartbeat") or its "felt it" answer ("heartbeat_felt"), to the other member. */
async function pushSignal(db, coupleId, couple, signal, type) {
  const senderId = String(signal.senderId || "");
  const receiverId = otherMember(couple, senderId);
  if (!receiverId) return "no_receiver";
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
    android: HEARTBEAT_ANDROID,
  });
}

exports.notifyPartnerOnMessage = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message || message.isDeleted || isCallLogMessage(message)) return;
    const { conversationId, messageId } = event.params;
    await pushMessage(getFirestore(), conversationId, messageId, message);
  }
);

exports.heartbeatPartnerOnUpdate = onDocumentUpdated("couples/{coupleId}", async (event) => {
  const before = (event.data && event.data.before.data()) || {};
  const after = (event.data && event.data.after.data()) || {};
  const db = getFirestore();
  const { coupleId } = event.params;

  // 1. A heartbeat, to the partner
  const signal = after.signal;
  if (signal && signal.type === "heartbeat" && signal.id && (!before.signal || before.signal.id !== signal.id)) {
    await pushSignal(db, coupleId, after, signal, "heartbeat");
  }

  // 2. "Felt it", back to whoever sent the heartbeat
  const felt = after.signalFelt;
  if (felt && felt.type === "heartbeat_felt" && felt.id && (!before.signalFelt || before.signalFelt.id !== felt.id)) {
    await pushSignal(db, coupleId, after, felt, "heartbeat_felt");
  }
});

// ---------- Pushes the sending phone asks for ----------

async function pushMessageById(db, conversationId, messageId, nowMs) {
  if (!logic.isSafeId(messageId)) throw new HttpsError("invalid-argument", "Bad message id.");
  const snap = await db.collection("conversations").doc(conversationId).collection("messages").doc(messageId).get();
  if (!snap.exists) return "not_found";
  const message = snap.data();
  if (message.isDeleted || isCallLogMessage(message)) return "skipped";
  const writtenAt = Number(message.timestamp || 0);
  if (!writtenAt || Math.abs(nowMs - writtenAt) > MESSAGE_PUSH_WINDOW_MS) return "stale";
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
  const calleeId = logic.calleeFor(control, couple);
  if (!calleeId || logic.isSameUser(calleeId, callerId)) return "no_receiver";
  return pushToMember(db, {
    receiverId: calleeId,
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
  });
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
  const calleeId = logic.calleeFor(call, coupleSnap.data() || {});
  if (!calleeId || logic.isSameUser(calleeId, callerId)) return "no_receiver";
  return pushToMember(db, {
    receiverId: calleeId,
    senderId: callerId,
    coupleId,
    data: callCancelData({ callId, coupleId, reason: call.state }),
    android: { priority: "high", ttl: CALL_CANCEL_TTL_MS },
  });
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

/**
 * The sending phone asks for its partner's push right after saving what it's about: a message,
 * a call ringing or called off, a heartbeat. This direct path doesn't depend on the database
 * triggers (when both push, the receiving phone still shows each thing once). Only what's saved
 * is pushed, to the couple member it's for, while it's still current.
 */
exports.pushPartner = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const data = request.data || {};
  const coupleId = data.coupleId;
  if (!logic.isSafeId(coupleId)) throw new HttpsError("invalid-argument", "Bad couple id.");
  const db = getFirestore();
  const nowMs = Date.now();
  let status;
  switch (data.kind) {
    case "message":
      status = await pushMessageById(db, coupleId, data.messageId, nowMs);
      break;
    case "call_invite":
      status = await pushCallInvite(db, coupleId, data.callId, nowMs);
      break;
    case "call_cancel":
      status = await pushCallCancel(db, coupleId, data.callId);
      break;
    case "heartbeat":
    case "heartbeat_felt":
      status = await pushHeartbeat(db, coupleId, data.signalId, data.kind, nowMs);
      break;
    default:
      throw new HttpsError("invalid-argument", "Unknown push.");
  }
  if (status !== "sent") logger.info("Partner push not sent", { kind: data.kind, coupleId, status });
  return { status };
});

/**
 * Verifies the couple secret code on the server before letting a phone join.
 * Adds the phone's auth UID to the couple's memberUids list.
 * Security rules then enforce that ONLY memberUids can read/write that couple's data.
 */
exports.coupleJoin = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "You must be signed in.");
  }
  const { secretCode, username, partnerName } = request.data || {};
  const cleanCode = String(secretCode || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  const cleanUser = String(username || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
  const cleanPartner = String(partnerName || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");

  if (!cleanCode) throw new HttpsError("invalid-argument", "Secret code is required.");
  if (!cleanUser) throw new HttpsError("invalid-argument", "Username is required.");

  const coupleId = `couple_${cleanCode}`;
  const uid = `user_${cleanUser}`;
  const fallbackPartnerId = cleanPartner ? `user_${cleanPartner}` : "";
  const authUid = request.auth.uid;
  const displayName = String(username || "").trim() || cleanUser;
  const partnerDisplayName = String(partnerName || "").trim() || cleanPartner;

  const db = getFirestore();
  const coupleRef = db.collection("couples").doc(coupleId);
  const userRef = db.collection("users").doc(uid);

  return await db.runTransaction(async (tx) => {
    const coupleSnap = await tx.get(coupleRef);
    let finalPartnerId = fallbackPartnerId;

    if (!coupleSnap.exists) {
      // New couple space: register Partner 1
      const newCouple = {
        id: coupleId,
        secretCode: cleanCode,
        partner1Id: uid,
        partner1Name: displayName,
        partner2Name: partnerDisplayName,
        memberUids: [authUid],
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      };
      tx.set(coupleRef, newCouple);
    } else {
      const existing = coupleSnap.data() || {};
      const p1Id = existing.partner1Id;
      const p2Id = existing.partner2Id;

      const isP1 = p1Id === uid ||
        (p1Id && p1Id.replace(/^user_/, "").toLowerCase() === cleanUser) ||
        (existing.partner1Name && existing.partner1Name.trim().toLowerCase() === cleanUser) ||
        (existing.partner1Name && existing.partner1Name.trim().toLowerCase() === displayName.toLowerCase());

      const isP2 = p2Id === uid ||
        (p2Id && p2Id.replace(/^user_/, "").toLowerCase() === cleanUser) ||
        (existing.partner2Name && existing.partner2Name.trim().toLowerCase() === cleanUser) ||
        (existing.partner2Name && existing.partner2Name.trim().toLowerCase() === displayName.toLowerCase());

      if (isP1) {
        // Partner 1 logging back in
        finalPartnerId = p2Id || fallbackPartnerId;
      } else if (isP2) {
        // Partner 2 logging back in
        finalPartnerId = p1Id || fallbackPartnerId;
      } else if (!p2Id && !isP1) {
        // Partner 2 joining for the first time
        finalPartnerId = p1Id || fallbackPartnerId;
        tx.update(coupleRef, {
          partner2Id: uid,
          partner2Name: displayName,
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else if (!p1Id) {
        finalPartnerId = p2Id || fallbackPartnerId;
        tx.update(coupleRef, {
          partner1Id: uid,
          partner1Name: displayName,
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        // Both slots taken by other users
        throw new HttpsError("permission-denied", "This Couple Secret Code is already in use by two registered partners.");
      }

      // Add auth UID to memberUids
      const currentMembers = Array.isArray(existing.memberUids) ? existing.memberUids : [];
      if (!currentMembers.includes(authUid)) {
        tx.update(coupleRef, {
          memberUids: FieldValue.arrayUnion(authUid),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    }

    // Set/update user document with coupleId and authUid
    tx.set(userRef, {
      id: uid,
      displayName: displayName,
      coupleId: coupleId,
      partnerId: finalPartnerId,
      partnerName: partnerDisplayName,
      authUid: authUid,
      isOnline: true,
      lastSeen: Date.now(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      success: true,
      coupleId,
      uid,
      partnerId: finalPartnerId,
    };
  });
});

// Voice calls and their recordings
Object.assign(exports, require("./calls"));
