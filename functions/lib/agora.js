/**
 * Agora's server side: call tokens, who is in a call's channel, and the call recorder.
 * Every request uses the project's REST credentials (customer id + secret), which stay on the server.
 */
const { RtcTokenBuilder, RtcRole } = require("agora-token");

const RECORDING_API = "https://api.sd-rtn.com";
const CHANNEL_API = "https://api.agora.io";
const REQUEST_TIMEOUT_MS = 8000;

class AgoraError extends Error {
  constructor(message, status, body) {
    super(message);
    this.name = "AgoraError";
    this.status = status;
    this.body = body;
  }
}

/** A token for [uid] to join and talk in [channel] only, valid for [ttlSeconds]. */
function buildRtcToken({ appId, appCertificate, channel, uid, ttlSeconds }) {
  return RtcTokenBuilder.buildTokenWithUid(appId, appCertificate, channel, uid, RtcRole.PUBLISHER, ttlSeconds, ttlSeconds);
}

function createAgoraClient({ appId, customerId, customerSecret, fetchImpl = fetch }) {
  const auth = "Basic " + Buffer.from(`${customerId}:${customerSecret}`).toString("base64");

  async function request(method, url, body) {
    const response = await fetchImpl(url, {
      method,
      headers: { Authorization: auth, "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch (_) {
      json = null;
    }
    if (!response.ok) {
      throw new AgoraError(`Agora ${method} ${new URL(url).pathname} failed with ${response.status}`, response.status, json || text);
    }
    return json || {};
  }

  const recordingBase = `${RECORDING_API}/v1/apps/${encodeURIComponent(appId)}/cloud_recording`;

  return {
    /** The numeric user ids in [channel] right now (empty when nobody is in it). */
    async channelUsers(channel) {
      const json = await request("GET", `${CHANNEL_API}/dev/v1/channel/user/${encodeURIComponent(appId)}/${encodeURIComponent(channel)}`);
      const data = json && json.data;
      if (!json.success || !data || !data.channel_exist) return [];
      const users = Array.isArray(data.users) ? data.users : [];
      return users.map(Number).filter((uid) => Number.isFinite(uid));
    },

    async acquire(channel, recorderUid) {
      const json = await request("POST", `${recordingBase}/acquire`, {
        cname: channel,
        uid: String(recorderUid),
        clientRequest: { scene: 0, resourceExpiredHour: 24 },
      });
      if (!json.resourceId) throw new AgoraError("Agora acquire returned no resourceId", 200, json);
      return json.resourceId;
    },

    /** Starts mixing every subscribed voice into one recording; returns Agora's session id. */
    async startMixRecording(resourceId, channel, recorderUid, clientRequest) {
      const json = await request(
        "POST",
        `${recordingBase}/resourceid/${encodeURIComponent(resourceId)}/mode/mix/start`,
        { cname: channel, uid: String(recorderUid), clientRequest }
      );
      if (!json.sid) throw new AgoraError("Agora start returned no sid", 200, json);
      return json.sid;
    },

    async query(resourceId, sid) {
      return request("GET", `${recordingBase}/resourceid/${encodeURIComponent(resourceId)}/sid/${encodeURIComponent(sid)}/mode/mix/query`);
    },

    async stop(resourceId, sid, channel, recorderUid) {
      return request(
        "POST",
        `${recordingBase}/resourceid/${encodeURIComponent(resourceId)}/sid/${encodeURIComponent(sid)}/mode/mix/stop`,
        { cname: channel, uid: String(recorderUid), clientRequest: {} }
      );
    },
  };
}

module.exports = { AgoraError, buildRtcToken, createAgoraClient };
