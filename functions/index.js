/**
 * Pushes to the partner's phone, so things arrive even with the app closed. Pushes carry data only
 * (no visible notification of their own); the app decides what to show from that phone's settings
 * (off, hidden content, Notes disguise).
 *
 * - notifyPartner: a new message.
 * - heartbeatPartner: a "thinking of you" heartbeat (a vibration, only if delivered right away).
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
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

exports.notifyPartner = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message || message.isDeleted) return;

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

exports.heartbeatPartner = onDocumentUpdated("couples/{coupleId}", async (event) => {
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
      // Now or never: a phone that's off or offline doesn't get it later
      ttl: 0,
    }
  );
});
