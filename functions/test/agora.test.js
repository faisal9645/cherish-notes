const test = require("node:test");
const assert = require("node:assert/strict");
const { buildRtcToken, createAgoraClient, AgoraError } = require("../lib/agora");

/** A fetch that records requests and answers with [responses] in turn. */
function fakeFetch(responses) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    const next = responses.shift() || { status: 200, body: {} };
    return {
      ok: next.status >= 200 && next.status < 300,
      status: next.status,
      text: async () => (typeof next.body === "string" ? next.body : JSON.stringify(next.body)),
    };
  };
  fn.calls = calls;
  return fn;
}

const credentials = { appId: "app123", customerId: "cid", customerSecret: "csecret" };

test("tokens are built for one channel and uid", () => {
  const token = buildRtcToken({
    appId: "970CA35de60c44645bbae8a215061b33",
    appCertificate: "5CFd2fd1755d40ecb72977518be15d3b",
    channel: "AbCdEfGhIjKlMnOpQrSt",
    uid: 1001,
    ttlSeconds: 600,
  });
  assert.equal(typeof token, "string");
  assert.ok(token.startsWith("007"), "AccessToken2 format");
  assert.notEqual(
    token,
    buildRtcToken({
      appId: "970CA35de60c44645bbae8a215061b33",
      appCertificate: "5CFd2fd1755d40ecb72977518be15d3b",
      channel: "AbCdEfGhIjKlMnOpQrSt",
      uid: 1002,
      ttlSeconds: 600,
    })
  );
});

test("requests use Basic auth with the REST credentials", async () => {
  const fetchImpl = fakeFetch([{ status: 200, body: { success: true, data: { channel_exist: true, mode: 1, total: 2, users: [1001, 1002] } } }]);
  const client = createAgoraClient({ ...credentials, fetchImpl });
  await client.channelUsers("AbCdEfGhIjKlMnOpQrSt");
  const { url, init } = fetchImpl.calls[0];
  assert.equal(init.headers.Authorization, "Basic " + Buffer.from("cid:csecret").toString("base64"));
  assert.equal(url, "https://api.agora.io/dev/v1/channel/user/app123/AbCdEfGhIjKlMnOpQrSt");
});

test("channel users: numbers, or nobody when the channel doesn't exist", async () => {
  let client = createAgoraClient({
    ...credentials,
    fetchImpl: fakeFetch([{ status: 200, body: { success: true, data: { channel_exist: true, mode: 1, total: 3, users: [1001, 1002, 900001] } } }]),
  });
  assert.deepEqual(await client.channelUsers("c"), [1001, 1002, 900001]);
  client = createAgoraClient({ ...credentials, fetchImpl: fakeFetch([{ status: 200, body: { success: true, data: { channel_exist: false } } }]) });
  assert.deepEqual(await client.channelUsers("c"), []);
});

test("recording start sends the mix request and returns the session id", async () => {
  const fetchImpl = fakeFetch([
    { status: 200, body: { resourceId: "res-1" } },
    { status: 200, body: { resourceId: "res-1", sid: "sid-1" } },
  ]);
  const client = createAgoraClient({ ...credentials, fetchImpl });
  const resourceId = await client.acquire("AbCdEfGhIjKlMnOpQrSt", 900001);
  const sid = await client.startMixRecording(resourceId, "AbCdEfGhIjKlMnOpQrSt", 900001, { recordingConfig: { streamTypes: 0 } });
  assert.equal(resourceId, "res-1");
  assert.equal(sid, "sid-1");
  assert.equal(fetchImpl.calls[0].url, "https://api.sd-rtn.com/v1/apps/app123/cloud_recording/acquire");
  assert.equal(fetchImpl.calls[1].url, "https://api.sd-rtn.com/v1/apps/app123/cloud_recording/resourceid/res-1/mode/mix/start");
  const startBody = JSON.parse(fetchImpl.calls[1].init.body);
  assert.equal(startBody.uid, "900001");
  assert.equal(startBody.cname, "AbCdEfGhIjKlMnOpQrSt");
  assert.equal(startBody.clientRequest.recordingConfig.streamTypes, 0);
});

test("Agora errors surface with their status", async () => {
  const client = createAgoraClient({ ...credentials, fetchImpl: fakeFetch([{ status: 401, body: { message: "Invalid authentication credentials" } }]) });
  await assert.rejects(client.acquire("c", 900001), (error) => error instanceof AgoraError && error.status === 401);
});

test("a start without a session id is an error, not a silent success", async () => {
  const client = createAgoraClient({ ...credentials, fetchImpl: fakeFetch([{ status: 200, body: { resourceId: "r" } }]) });
  await assert.rejects(client.startMixRecording("r", "c", 900001, {}), AgoraError);
});
