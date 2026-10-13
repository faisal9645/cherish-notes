/**
 * Pushes to the partner's phone, so things arrive even with the app closed. Pushes carry data only
 * (no visible notification of their own); the app decides what to show from that phone's settings
 * (off, hidden content, Notes disguise).
 *
 * - notifyPartner: a new message.
 * - heartbeatPartner: a "thinking of you" heartbeat (a vibration, only if delivered right away).
 * - Voice calls and their recordings: see calls.js.
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/** Tokens FCM no longer accepts (app uninstalled, data cleared): removed from the profile. */
const DEAD_TOKEN_ERRORS = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/** Ids an older app version wrote when the partner wasn't known yet. */
const PLACEHOLDER_IDS = new Set(["", "user_partner", "user_me"]);

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
  const members = [
    memberId(couple.partner1Id, couple.partner1Name),
    memberId(couple.partner2Id, couple.partner2Name),
  ].filter((id) => id && id !== senderId);
  return members[0] || null;
}

/** The receiver: the message's receiverId, or the other member of the couple. */
async function receiverOf(db, message, conversationId) {
  const senderId = String(message.senderId || "");
  const receiverId = String(message.receiverId || "").trim();
  if (!PLACEHOLDER_IDS.has(receiverId) && receiverId !== senderId) return receiverId;

  const couple = await db.collection("couples").doc(conversationId).get();
  return couple.exists ? otherMember(couple.data(), senderId) : null;
}

/** Sends [data] to [receiverId]'s phone; a token FCM rejects for good is removed. */
async function pushTo(db, receiverId, data, android) {
  const receiverRef = db.collection("users").doc(receiverId);
  const receiver = await receiverRef.get();
  const token = receiver.exists ? receiver.get("fcmToken") : null;
  if (!token) {
    logger.info("Receiver has no push token yet", { receiverId });
    return;
  }
  try {
    await getMessaging().send({ token, data, android });
  } catch (error) {
    if (DEAD_TOKEN_ERRORS.has(error.code)) {
      logger.info("Removing a dead push token", { receiverId });
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(receiverRef);
        // Only if the phone hasn't already saved a new one meanwhile
        if (fresh.get("fcmToken") === token) tx.update(receiverRef, { fcmToken: FieldValue.delete() });
      });
      return;
    }
    logger.error("Push failed", { receiverId, code: error.code, message: error.message });
  }
}

exports.notifyPartnerOnMessage = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message || message.isDeleted) return;
    // A call recording is added by the server after the call; the app shows it without a push
    if (String(message.type || "").toUpperCase() === "CALL_RECORDING") return;

    const { conversationId, messageId } = event.params;
    const db = getFirestore();
    const receiverId = await receiverOf(db, message, conversationId);
    if (!receiverId) {
      logger.info("No receiver for message", { conversationId, messageId });
      return;
    }

    // Data only: the app builds the notification itself (masked when its settings say so)
    await pushTo(
      db,
      receiverId,
      {
        type: "message",
        messageId: String(message.id || messageId),
        conversationId,
        senderId: String(message.senderId || ""),
        senderName: String(message.senderName || ""),
        messageText: previewText(message),
      },
      {
        // High priority wakes a phone that is dozing or has the app closed
        priority: "high",
        // A phone that's off for longer than a day doesn't get a stale burst later
        ttl: 24 * 60 * 60 * 1000,
      }
    );
  }
);

exports.heartbeatPartnerOnUpdate = onDocumentUpdated("couples/{coupleId}", async (event) => {
  const before = (event.data && event.data.before.data()) || {};
  const after = (event.data && event.data.after.data()) || {};
  const signal = after.signal;
  if (!signal || signal.type !== "heartbeat" || !signal.id) return;
  // Only a new heartbeat (the couple document also changes for other reasons)
  if (before.signal && before.signal.id === signal.id) return;

  const senderId = String(signal.senderId || "");
  const receiverId = otherMember(after, senderId);
  if (!receiverId) return;

  const sentAt = signal.at && typeof signal.at.toMillis === "function" ? signal.at.toMillis() : Date.now();
  await pushTo(
    getFirestore(),
    receiverId,
    {
      type: "heartbeat",
      signalId: String(signal.id),
      senderId,
      sentAt: String(sentAt),
    },
    {
      priority: "high",
      // Fresh delivery: 60s allows FCM to wake closed app / sleeping radio
      ttl: 60 * 1000,
    }
  );
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

      if (p1Id === uid) {
        // Partner 1 logging back in
        finalPartnerId = p2Id || fallbackPartnerId;
      } else if (p2Id === uid) {
        // Partner 2 logging back in
        finalPartnerId = p1Id || fallbackPartnerId;
      } else if (!p2Id && p1Id !== uid) {
        // Partner 2 joining for the first time
        finalPartnerId = p1Id || fallbackPartnerId;
        tx.update(coupleRef, {
          partner2Id: uid,
          partner2Name: displayName,
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
