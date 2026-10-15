/**
 * Delivering a push to one member of a couple.
 *
 * Over the app's versions a phone has saved its push address in a few places: its profile, the
 * same profile id without or with "user_", and its slot on the couple record. One of them can be
 * out of date, so every distinct address is tried (a stale one never hides the current one), and
 * an address FCM rejects for good is removed from where it was found. The sender's own addresses
 * are never used, so a mixed-up couple slot can't send the push back to the sender's phone.
 *
 * Pushes carry data only: the receiving app shows what its own settings say (hidden content, one
 * alert until it's opened). None go to a phone whose notifications are off, a message alerts once
 * until the receiver has been back in Cherish (claimMessageAlert), and a call's push goes out once
 * even when the calling phone asks again after a timeout (pushOnce). FCM holds back an
 * app's high-priority pushes until the phone wakes up when many of them show nothing.
 */
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
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

/** [receiverId] (one id or a list) as a list of distinct ids, without blanks. */
function receiverList(receiverId) {
  return [...new Set([].concat(receiverId || []).map((id) => String(id || "").trim()).filter(Boolean))];
}

/**
 * Where a push to [receiverId] (one id, or a list when it's not sure which is current) goes: each
 * distinct address in [profiles] (the receiver's profile snapshots, as { ref, data }) and in their
 * couple slot, except [excludeTokens].
 */
function pickTargets({ profiles, couple, coupleRef, receiverId, excludeTokens }) {
  const excluded = new Set((excludeTokens || []).filter(Boolean));
  const seen = new Set();
  const targets = [];
  const add = (token, ref, field) => {
    if (typeof token !== "string" || !token || excluded.has(token) || seen.has(token)) return;
    seen.add(token);
    targets.push({ token, ref, field });
  };
  for (const profile of profiles || []) {
    if (profile && profile.data) add(profile.data.fcmToken, profile.ref, "fcmToken");
  }
  for (const receiver of receiverList(receiverId)) {
    const field = coupleTokenField(couple, receiver);
    if (field && couple) add(couple[field], coupleRef, field);
  }
  return targets;
}

/**
 * The receiver switched notifications off, in the app or for it in Android: their phone saves
 * "CLOSED" as its notificationStatus. Only when no profile of theirs says otherwise, so an old
 * copy can't silence a phone that shows notifications.
 */
function notificationsOff(profiles) {
  const statuses = (profiles || [])
    .map((profile) => String((profile && profile.data && profile.data.notificationStatus) || "").trim().toUpperCase())
    .filter(Boolean);
  return statuses.length > 0 && statuses.every((status) => status === "CLOSED");
}

/** Sends [data] to one address: "sent", "dead" (and removed) or "failed". */
async function sendToTarget(db, target, data, android) {
  try {
    // Data only: the app shows it as that phone's settings say (see the top of this file)
    await getMessaging().send({ token: target.token, data, android });
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
 * Sends [data] (data only: the app builds what's shown) to [receiverId]'s phone (one id, or a list
 * when it's not sure which is current), never to [senderId]'s. With [skipWhenOff], nothing goes to
 * a receiver who switched notifications off ("notifications_off"): their phone would show nothing.
 * Returns "sent", "no_push_token", "dead_push_token" or "push_failed".
 */
async function pushToMember(db, { receiverId, senderId, coupleId, data, android, skipWhenOff = false }) {
  const receivers = receiverList(receiverId).filter((id) => !logic.isSameUser(id, senderId));
  if (receivers.length === 0) return "no_push_token";
  const users = db.collection("users");
  const idsOf = (id) => [...new Set([String(id || "").trim(), alternateId(id)].filter(Boolean))];
  const receiverRefs = [...new Set(receivers.flatMap(idsOf))].map((id) => users.doc(id));
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
  const targets = pickTargets({ profiles, couple, coupleRef, receiverId: receivers, excludeTokens: senderTokens });
  if (targets.length === 0) {
    logger.info("Receiver has no push token yet", { receiverIds: receivers, coupleId });
    return "no_push_token";
  }
  if (skipWhenOff && notificationsOff(profiles)) return "notifications_off";
  const outcomes = await Promise.all(targets.map((target) => sendToTarget(db, target, data, android)));
  if (outcomes.includes("sent")) return "sent";
  return outcomes.every((outcome) => outcome === "dead") ? "dead_push_token" : "push_failed";
}

// ---------- One message alert until the receiver is back in Cherish ----------

/**
 * "alert" when a message may alert the receiver's phone, "already_notified" when one did and they
 * haven't been in Cherish since. [seenMs]: when their phone was last in Cherish (it saves
 * chatSeenAt while it's open there and when it leaves; 0 from an app before this, which then
 * alerts for every message as it used to); [notifiedMs]: the last alert.
 */
function alertDecision(seenMs, notifiedMs) {
  if (!seenMs || !notifiedMs) return "alert";
  return notifiedMs > seenMs ? "already_notified" : "alert";
}

/** The last message alert to [receiverId]'s phone (only these functions use it). */
function alertRef(db, coupleId, receiverId) {
  return db.collection("couples").doc(coupleId).collection("alerts").doc(cleanId(receiverId) || "receiver");
}

/**
 * Claims the message alert for whichever of [receiverIds] is the receiver: { status: "claimed",
 * token, profileRef, ... } with the push address on their own profile (the one their phone keeps
 * current), or "already_notified" (with notifiedAt), "notifications_off", "no_profile", and
 * "unchecked" when the record couldn't be read (then it alerts, as a push twice beats none).
 */
async function claimMessageAlert(db, coupleId, receiverIds) {
  const users = db.collection("users");
  const ids = [...new Set(receiverList(receiverIds).flatMap((id) => [id, alternateId(id)]).filter(Boolean))];
  try {
    return await db.runTransaction(async (tx) => {
      let own = null;
      let other = null;
      for (const id of ids) {
        const snap = await tx.get(users.doc(id));
        if (!snap.exists) continue;
        if (snap.get("chatSeenAt")) {
          own = snap;
          break;
        }
        if (!other) other = snap;
      }
      own = own || other;
      if (!own) return { status: "no_profile" };
      const profile = own.data();
      if (notificationsOff([{ data: profile }])) return { status: "notifications_off" };
      const ref = alertRef(db, coupleId, own.id);
      const record = await tx.get(ref);
      const previous = record.exists ? record.get("notifiedAt") : null;
      if (alertDecision(logic.toMillis(profile.chatSeenAt), logic.toMillis(previous)) === "already_notified") {
        return { status: "already_notified", notifiedAt: logic.toMillis(previous) };
      }
      const at = Timestamp.now();
      tx.set(ref, { notifiedAt: at, receiverId: own.id });
      return {
        status: "claimed",
        ref,
        at,
        previous,
        profileRef: own.ref,
        token: typeof profile.fcmToken === "string" ? profile.fcmToken : "",
        notifiedAt: at.toMillis(),
      };
    });
  } catch (error) {
    logger.warn("Couldn't check the receiver's last alert", { coupleId, message: String(error && error.message) });
    return { status: "unchecked" };
  }
}

/** Undoes [claim] when its push reached no phone, so the next message alerts. */
async function releaseMessageAlert(db, claim) {
  if (!claim || claim.status !== "claimed") return;
  try {
    await db.runTransaction(async (tx) => {
      const record = await tx.get(claim.ref);
      const current = record.exists ? record.get("notifiedAt") : null;
      // Unless a later alert went out meanwhile
      if (!current || typeof current.isEqual !== "function" || !current.isEqual(claim.at)) return;
      if (claim.previous) tx.set(claim.ref, { notifiedAt: claim.previous }, { merge: true });
      else tx.delete(claim.ref);
    });
  } catch (error) {
    logger.warn("Couldn't undo a message alert", { message: String(error && error.message) });
  }
}

// ---------- Each push once ----------

/** Outcomes where nothing reached the phone, so the other path asking for the same push may try. */
const NOT_DELIVERED = new Set(["no_push_token", "dead_push_token", "push_failed"]);

/** Firestore's "already exists" (what creating a document that's there already fails with). */
const ALREADY_EXISTS = 6;

/** How long a push's record is kept: a TTL policy on pushes.expireAt (in the console) may delete it after. */
const PUSH_RECORD_KEEP_MS = 7 * 24 * 60 * 60 * 1000;

/** The record that [key] (e.g. "message:<id>") was pushed for [coupleId]; only these functions use it. */
function pushRef(db, coupleId, key) {
  return db.collection("couples").doc(coupleId).collection("pushes").doc(key);
}

/** True for the first to ask to push [key]; true too when that can't be checked. */
async function claimPush(db, coupleId, key) {
  try {
    await pushRef(db, coupleId, key).create({
      at: FieldValue.serverTimestamp(),
      expireAt: Timestamp.fromMillis(Date.now() + PUSH_RECORD_KEEP_MS),
    });
    return true;
  } catch (error) {
    if (error && error.code === ALREADY_EXISTS) return false;
    // A push twice is better than none
    logger.warn("Couldn't check whether a push went out already", { key, message: String(error && error.message) });
    return true;
  }
}

/** Gives [key] back, so the other path can still push it. */
async function releasePush(db, coupleId, key) {
  try {
    await pushRef(db, coupleId, key).delete();
  } catch (error) {
    logger.warn("Couldn't give a push back", { key, message: String(error && error.message) });
  }
}

/**
 * Runs [send] (it pushes and returns the outcome) only for the first to ask for [key]: a phone that
 * asks again (its first ask timed out but went through) must not ring the other phone twice.
 * "already_sent" for the second; when [send] reached no phone, the key is given back so a later
 * ask may still try.
 */
async function pushOnce(db, coupleId, key, send) {
  if (coupleId && !(await claimPush(db, coupleId, key))) return "already_sent";
  const outcome = await send();
  if (coupleId && NOT_DELIVERED.has(outcome)) await releasePush(db, coupleId, key);
  return outcome;
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
  notificationsOff,
  sendToTarget,
  pushToMember,
  alertDecision,
  claimMessageAlert,
  releaseMessageAlert,
  pushOnce,
  displayNameOf,
  callInviteData,
  callCancelData,
};
