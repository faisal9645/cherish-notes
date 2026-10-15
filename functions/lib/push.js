/**
 * Delivering a push to one member of a couple.
 *
 * Over the app's versions a phone has saved its push address in a few places: its profile, the
 * same profile id without or with "user_", and its slot on the couple record. One of them can be
 * out of date, so every distinct address is tried (a stale one never hides the current one), and
 * an address FCM rejects for good is removed from where it was found. The sender's own addresses
 * are never used, so a mixed-up couple slot can't send the push back to the sender's phone.
 */
const { FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");
const logic = require("./callLogic");

/** Errors meaning an address is gone for good (the app was uninstalled or its data cleared). */
const DEAD_TOKEN_ERRORS = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

const cleanId = (id) => String(id || "").trim().replace(/^user_/, "").toLowerCase();
const cleanName = (name) => String(name || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");

/** The same profile id with "user_" added or taken off ("" for no id). */
function alternateId(id) {
  const value = String(id || "").trim();
  if (!value) return "";
  return value.startsWith("user_") ? value.slice("user_".length) : `user_${value}`;
}

/** The couple record's field with [userId]'s push address, when the record says which partner they are. */
function coupleTokenField(couple, userId) {
  const user = cleanId(userId);
  if (!couple || !user) return null;
  const isThem = (id, name) => cleanId(id) === user || cleanName(name) === user;
  if (isThem(couple.partner1Id, couple.partner1Name)) return "partner1FcmToken";
  if (isThem(couple.partner2Id, couple.partner2Name)) return "partner2FcmToken";
  return null;
}

/**
 * Where a push to [receiverId] goes: each distinct address in [profiles] (the receiver's profile
 * snapshots, as { ref, data }) and in their couple slot, except [excludeTokens].
 */
function pickTargets({ profiles, couple, coupleRef, receiverId, excludeTokens }) {
  const excluded = new Set((excludeTokens || []).filter(Boolean));
  const seen = new Set();
  const targets = [];
  const add = (token, ref, field, notificationStatus) => {
    if (typeof token !== "string" || !token || excluded.has(token) || seen.has(token)) return;
    seen.add(token);
    const item = { token, ref, field };
    if (notificationStatus) item.notificationStatus = notificationStatus;
    targets.push(item);
  };
  for (const profile of profiles || []) {
    if (profile && profile.data) add(profile.data.fcmToken, profile.ref, "fcmToken", profile.data.notificationStatus);
  }
  const field = coupleTokenField(couple, receiverId);
  if (field && couple) add(couple[field], coupleRef, field);
  return targets;
}

/** Sends [data] to one address: "sent", "dead" (and removed) or "failed". */
async function sendToTarget(db, target, data, android) {
  try {
    const payload = { token: target.token, data, android };
    if (data && data.type === "message") {
      const isSafe = target.notificationStatus === "SAFE" || target.notificationStatus === "DISCREET";
      payload.notification = {
        title: isSafe ? "Notes" : (data.senderName || "Cherish"),
        body: isSafe ? "1 note updated" : (data.messageText || "New message"),
      };
      payload.android = {
        ...android,
        notification: {
          channelId: "notes_sync_channel_v3",
          icon: "ic_stat_notes",
          color: "#2563FF",
          tag: "cherish_message",
          defaultSound: true,
          defaultVibrateTimings: true,
        },
      };
    }
    await getMessaging().send(payload);
    return "sent";
  } catch (error) {
    if (DEAD_TOKEN_ERRORS.has(error.code)) {
      if (target.ref) {
        await db
          .runTransaction(async (tx) => {
            const fresh = await tx.get(target.ref);
            // Only if that phone hasn't saved a new address there meanwhile
            if (fresh.exists && fresh.get(target.field) === target.token) {
              tx.update(target.ref, { [target.field]: FieldValue.delete() });
            }
          })
          .catch((err) => logger.warn("Couldn't remove a dead push token", { message: String(err && err.message) }));
      }
      return "dead";
    }
    logger.error("Push failed", { code: error.code, message: error.message });
    return "failed";
  }
}

/**
 * Sends [data] (data only: the app builds what's shown) to [receiverId]'s phone, never to
 * [senderId]'s. Returns "sent", "no_push_token", "dead_push_token" or "push_failed".
 */
async function pushToMember(db, { receiverId, senderId, coupleId, data, android }) {
  const receiver = String(receiverId || "").trim();
  if (!receiver) return "no_push_token";
  const users = db.collection("users");
  const idsOf = (id) => [...new Set([String(id || "").trim(), alternateId(id)].filter(Boolean))];
  const receiverRefs = idsOf(receiver).map((id) => users.doc(id));
  const senderRefs = senderId ? idsOf(senderId).map((id) => users.doc(id)) : [];
  const coupleRef = coupleId ? db.collection("couples").doc(coupleId) : null;
  const [receiverSnaps, senderSnaps, coupleSnap] = await Promise.all([
    Promise.all(receiverRefs.map((ref) => ref.get())),
    Promise.all(senderRefs.map((ref) => ref.get())),
    coupleRef ? coupleRef.get() : null,
  ]);
  const profiles = receiverSnaps.filter((snap) => snap.exists).map((snap) => ({ ref: snap.ref, data: snap.data() }));
  const senderTokens = senderSnaps.filter((snap) => snap.exists).map((snap) => snap.get("fcmToken"));
  const couple = coupleSnap && coupleSnap.exists ? coupleSnap.data() : null;
  const targets = pickTargets({ profiles, couple, coupleRef, receiverId: receiver, excludeTokens: senderTokens });
  if (targets.length === 0) {
    logger.info("Receiver has no push token yet", { receiverId: receiver, coupleId });
    return "no_push_token";
  }
  const outcomes = await Promise.all(targets.map((target) => sendToTarget(db, target, data, android)));
  if (outcomes.includes("sent")) return "sent";
  return outcomes.every((outcome) => outcome === "dead") ? "dead_push_token" : "push_failed";
}

/** [userId]'s name as their profile shows it, else as the couple was set up with. */
async function displayNameOf(db, userId, couple) {
  const id = String(userId || "").trim();
  if (!id) return "";
  const users = db.collection("users");
  let snap = await users.doc(id).get();
  if (!snap.exists) snap = await users.doc(alternateId(id)).get();
  const name = snap.exists ? String(snap.get("displayName") || "").trim() : "";
  if (name) return name;
  if (couple) {
    if (logic.isSameUser(logic.memberId(couple.partner1Id, couple.partner1Name), id)) return String(couple.partner1Name || "");
    if (logic.isSameUser(logic.memberId(couple.partner2Id, couple.partner2Name), id)) return String(couple.partner2Name || "");
  }
  return "";
}

/** A call's ringing push (every value a string, as FCM wants). */
function callInviteData({ callId, coupleId, callerId, callerName, ringUntilMs, nowMs }) {
  return {
    type: "call_invite",
    callId: String(callId),
    coupleId: String(coupleId),
    callerId: String(callerId),
    callerName: String(callerName || ""),
    sentAt: String(nowMs),
    ringUntil: String(ringUntilMs),
  };
}

/** Tells the called phone to stop ringing; [reason] "missed" or "cancelled" also says it was missed. */
function callCancelData({ callId, coupleId, reason }) {
  return { type: "call_cancel", callId: String(callId), coupleId: String(coupleId), reason: String(reason) };
}

module.exports = {
  DEAD_TOKEN_ERRORS,
  alternateId,
  coupleTokenField,
  pickTargets,
  pushToMember,
  displayNameOf,
  callInviteData,
  callCancelData,
};
