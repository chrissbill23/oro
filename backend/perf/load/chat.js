// The load test of Oro (ORO-SCL-007): people using Umoja's messaging, as its client calls Oro.
//
// users: each virtual user is one person (its own token, its organization), every 8 to 12 s: what it heard on its
// stream, what came in one of its conversations (?after=, the catch-up), a message 30 % of the time and its read
// position, a search 3 %, a file UPLOAD_RATE of the time. What it heard (LIST): `events` (Umoja since 2026-10-24) reads
// the list once, then the one message of each event for its preview, and the whole list again 2 % of the time (a
// stream opened again, a conversation changed); `always` (Umoja before) reads the whole list each time. A file sent:
// uploaded, then (DOWNLOAD_CHECK of the time) downloaded at once: 200 when the upload said available, 409 when not
// (not scanned clean yet); a 200 before a clean scan is counted in download_before_clean, which must stay 0. Then sent
// in a message when available. busy: the members of a group of 499 sending into it at a fixed rate.
//
// Every response is counted in `outcomes` by operation (name) and outcome: ok (2xx), rate_limited (429, the limits
// doing their job), not_ready (409 on a download of a file not scanned yet, expected), client_error (other 4xx),
// unavailable (502, 503, 504: a restart, a full pool), server_error (other 5xx), network (no answer). The ones that
// are neither ok nor rate_limited are also printed as `OUTCOME <epoch ms> <name> <status> <outcome>` lines, for the
// time line of a restart. handleSummary writes SUMMARY (JSON): per operation p50, p95, p99, max, count and outcomes.
//
// Env: BASE (http://oro-load:8082/oro), STAGES (vus:seconds,...), BUSY_RATE (messages per second, 0: none),
// LIST (events | always), UPLOAD_RATE (0.01), UPLOAD_KB (20), DOWNLOAD_CHECK (1: every upload downloaded at once),
// SCAN_CHECK (1: before the test, the EICAR test file must be refused with 422, else the test stops: the scanner is not
// on), SUMMARY (summary.json).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

function required(path) {
  try {
    return open(path);
  } catch (e) {
    throw new Error(`${path} is missing: made for each run (backend/perf/README.md, Running it)`);
  }
}

const BASE = __ENV.BASE || 'http://oro-load:8082/oro';
const tokens = new SharedArray('tokens', () => JSON.parse(required('./tokens.json')).filter((t) => t.user !== 'u1-1'));
const orgs = JSON.parse(required('./orgs.json'));
const busy = JSON.parse(required('./busy.json'));
const busyTokens = new SharedArray('busy', () => {
  const members = new Set(busy.members);
  return JSON.parse(required('./tokens.json')).filter((t) => members.has(t.user));
});

function number(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value) || value < 0) throw new Error(`${name}=${raw}: a number at least 0`);
  return value;
}

const listMode = __ENV.LIST || 'events';
if (!['events', 'always'].includes(listMode)) throw new Error(`LIST=${listMode}: events or always`);
const busyRate = number('BUSY_RATE', 0);
const uploadRate = number('UPLOAD_RATE', 0.01);
const uploadKb = number('UPLOAD_KB', 20);
const downloadCheck = number('DOWNLOAD_CHECK', 1);
const line = 'load test: the minutes of the meeting, the budget and the release notes.\n';
const file = line.repeat(Math.ceil((uploadKb * 1024) / line.length)).slice(0, uploadKb * 1024);
const EICAR = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';

const stages = (__ENV.STAGES || '100:60').split(',').map((s) => {
  const [target, seconds] = s.split(':').map(Number);
  if (!Number.isInteger(target) || !Number.isInteger(seconds)) throw new Error(`STAGES=${__ENV.STAGES}: vus:seconds,...`);
  return { target, duration: `${seconds}s` };
});

const OPS = ['list', 'preview', 'after', 'send', 'read', 'search', 'upload', 'download', 'busy-send'];
const OUTCOMES = ['ok', 'rate_limited', 'not_ready', 'client_error', 'unavailable', 'server_error', 'network'];
const outcomes = new Counter('outcomes');
const uploadsAvailable = new Counter('uploads_available');
const uploadsPending = new Counter('uploads_pending');
const downloadBeforeClean = new Counter('download_before_clean');

// a threshold on each operation and outcome: k6 keeps their submetrics in the summary (always true but the first ones)
const thresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:list}': ['p(95)<500'],
  'http_req_duration{name:send}': ['p(95)<500'],
  download_before_clean: ['count==0'],
};
for (const op of OPS) {
  const key = `http_req_duration{name:${op}}`;
  thresholds[key] = (thresholds[key] || []).concat(['max>=0']);
  for (const o of OUTCOMES) thresholds[`outcomes{name:${op},outcome:${o}}`] = ['count>=0'];
}

export const options = {
  scenarios: {
    users: { executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '10s', exec: 'user' },
    ...(busyRate > 0 ? {
      busy: {
        executor: 'constant-arrival-rate', rate: busyRate, timeUnit: '1s', exec: 'busySend',
        duration: `${stages.reduce((sum, s) => sum + parseInt(s.duration, 10), 0)}s`, preAllocatedVUs: 50, maxVUs: 200,
      },
    } : {}),
  },
  thresholds,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
  discardResponseBodies: false,
};

function headers(person) {
  return { Authorization: `Bearer ${person.token}`, 'x-organization-id': orgs[String(person.org)], 'Content-Type': 'application/json' };
}

function tagged(name, expected) {
  return { tags: { name }, responseCallback: http.expectedStatuses(...(expected || [{ min: 200, max: 299 }, 429])) };
}

/** the response counted by its outcome; the ones not ok nor rate limited printed with their time */
function counted(res, name, expected409) {
  const s = res.status;
  let outcome;
  if (s >= 200 && s < 300) outcome = 'ok';
  else if (s === 429) outcome = 'rate_limited';
  else if (s === 409 && expected409) outcome = 'not_ready';
  else if (s === 0) outcome = 'network';
  else if (s === 502 || s === 503 || s === 504) outcome = 'unavailable';
  else if (s >= 500) outcome = 'server_error';
  else outcome = 'client_error';
  outcomes.add(1, { name, outcome });
  if (outcome !== 'ok' && outcome !== 'rate_limited' && outcome !== 'not_ready') {
    console.log(`OUTCOME ${Date.now()} ${name} ${s} ${outcome}`);
  }
  return res;
}

export function setup() {
  if (tokens.length === 0) throw new Error('tokens.json holds no token');
  if (Object.keys(orgs).length === 0) throw new Error('orgs.json holds no organization');
  if (!busy.id || busyTokens.length === 0) throw new Error('busy.json: no busy group, or none of its members in tokens.json');
  if (__ENV.SCAN_CHECK === '1') {
    // the scanner must really scan: the EICAR test file is refused (422)
    const person = busyTokens[0];
    const res = http.post(`${BASE}/conversations/${busy.id}/attachments`, { file: http.file(EICAR, 'eicar.txt', 'text/plain') },
      { headers: { Authorization: `Bearer ${person.token}`, 'x-organization-id': busy.org } });
    if (res.status !== 422) {
      throw new Error(`SCAN_CHECK: the EICAR test file got ${res.status}, not 422: is Oro scanning (ORO_ATTACHMENTS_SCAN=clamav, clamd up)?`);
    }
  }
}

const state = {};

function upload(c, h, vu) {
  const auth = { Authorization: h.headers.Authorization, 'x-organization-id': h.headers['x-organization-id'] };
  const res = counted(http.post(`${BASE}/conversations/${c.id}/attachments`,
    { file: http.file(file, 'notes.txt', 'text/plain') }, { headers: auth, ...tagged('upload') }), 'upload');
  if (res.status !== 201) return;
  const available = res.json('available') === true;
  (available ? uploadsAvailable : uploadsPending).add(1);
  if (Math.random() < downloadCheck) {
    const got = counted(http.get(`${BASE}/attachments/${res.json('id')}/content`,
      { headers: auth, ...tagged('download', [{ min: 200, max: 299 }, 409, 429]) }), 'download', !available);
    if (!available && got.status === 200) downloadBeforeClean.add(1);
    check(got, { 'download as the scan said': (r) => r.status === (available ? 200 : 409) || r.status === 429 });
  }
  if (available) {
    const sent = counted(http.post(`${BASE}/conversations/${c.id}/messages`, JSON.stringify({
      clientMessageId: `load-${vu}-${Date.now()}`, type: 'FILE', body: null, attachmentIds: [res.json('id')],
    }), Object.assign({}, h, tagged('send'))), 'send');
    check(sent, { 'file sent': (r) => r.status === 201 || r.status === 429 });
  }
}

export function user() {
  const vu = exec.vu.idInTest;
  const person = tokens[(vu * 7919) % tokens.length];
  const h = { headers: headers(person) };
  const me = state[vu] || (state[vu] = { conversations: [], listed: false, lastMessageId: null });

  if (listMode === 'always' || !me.listed || !me.lastMessageId || Math.random() < 0.02) {
    const list = counted(http.get(`${BASE}/conversations`, Object.assign({}, h, tagged('list'))), 'list');
    check(list, { 'list 200': (r) => r.status === 200 });
    if (list.status === 200) {
      me.conversations = list.json().map((c) => ({ id: c.id, seq: c.lastMessageSeq }));
      me.listed = true;
    }
  } else {
    // an event heard: the one message, for the preview in the list
    const one = counted(http.get(`${BASE}/messages/${me.lastMessageId}`, Object.assign({}, h, tagged('preview'))), 'preview');
    check(one, { 'preview 200': (r) => r.status === 200 || r.status === 404 });
  }
  if (me.conversations.length) {
    const c = me.conversations[Math.floor(Math.random() * Math.min(me.conversations.length, 10))];
    const after = counted(http.get(`${BASE}/conversations/${c.id}/messages?after=${Math.max(0, c.seq - 3)}`,
      Object.assign({}, h, tagged('after'))), 'after');
    check(after, { 'after 200': (r) => r.status === 200 });
    if (after.status === 200) {
      const got = after.json('messages') || [];
      if (got.length) me.lastMessageId = got[got.length - 1].id;
    }

    if (Math.random() < uploadRate) {
      upload(c, h, vu);
    } else if (Math.random() < 0.3) {
      const sent = counted(http.post(`${BASE}/conversations/${c.id}/messages`, JSON.stringify({
        clientMessageId: `load-${vu}-${Date.now()}`, type: 'TEXT', body: 'load test: the minutes of the meeting',
      }), Object.assign({}, h, tagged('send'))), 'send');
      check(sent, { 'sent': (r) => r.status === 201 || r.status === 429 });
      if (sent.status === 201) {
        c.seq = sent.json('seq');
        counted(http.put(`${BASE}/conversations/${c.id}/read`, JSON.stringify({ seq: c.seq }),
          Object.assign({}, h, tagged('read'))), 'read');
      }
    }
    if (Math.random() < 0.03) {
      const words = ['invoice', 'meeting', 'deploy', 'budget', 'visa contract', 'release notes'];
      counted(http.get(`${BASE}/search/messages?q=${encodeURIComponent(words[Math.floor(Math.random() * words.length)])}`,
        Object.assign({}, h, tagged('search'))), 'search');
    }
  }
  sleep(8 + Math.random() * 4);
}

export function busySend() {
  const person = busyTokens[Math.floor(Math.random() * busyTokens.length)];
  const sent = counted(http.post(`${BASE}/conversations/${busy.id}/messages`, JSON.stringify({
    clientMessageId: `busy-${exec.scenario.iterationInTest}-${Date.now()}`, type: 'TEXT', body: 'load test: in the busy group',
  }), { headers: headers(person), ...tagged('busy-send') }), 'busy-send');
  check(sent, { 'busy sent': (r) => r.status === 201 || r.status === 429 });
}

/** per operation: latency percentiles (ms), requests and outcomes; the run's totals */
export function handleSummary(data) {
  const m = data.metrics;
  const ops = {};
  for (const op of OPS) {
    const d = m[`http_req_duration{name:${op}}`];
    const counts = {};
    for (const o of OUTCOMES) {
      const c = m[`outcomes{name:${op},outcome:${o}}`];
      if (c && c.values.count) counts[o] = c.values.count;
    }
    if (!d || !d.values.count) continue;
    ops[op] = {
      count: d.values.count, p50: d.values['p(50)'], p95: d.values['p(95)'], p99: d.values['p(99)'], max: d.values.max,
      outcomes: counts,
    };
  }
  const value = (name, key) => (m[name] ? m[name].values[key] : 0);
  const summary = {
    stages: __ENV.STAGES, list: listMode, busyRate, uploadRate, uploadKb, downloadCheck,
    durationMs: data.state.testRunDurationMs,
    requests: value('http_reqs', 'count'), requestsPerSecond: value('http_reqs', 'rate'),
    failedRate: m['http_req_failed{expected:no}'] ? m['http_req_failed{expected:no}'].values.rate : null,
    iterations: value('iterations', 'count'), vusMax: value('vus_max', 'max'),
    uploadsAvailable: value('uploads_available', 'count'), uploadsPending: value('uploads_pending', 'count'),
    downloadBeforeClean: value('download_before_clean', 'count'),
    thresholdsFailed: Object.entries(m).filter(([, v]) => v.thresholds && Object.values(v.thresholds).some((t) => !t.ok))
      .map(([k]) => k),
    ops,
  };
  const rows = Object.entries(ops).map(([op, o]) => `${op.padEnd(10)} n=${String(o.count).padStart(7)} p50=${o.p50.toFixed(1)} `
    + `p95=${o.p95.toFixed(1)} p99=${o.p99.toFixed(1)} max=${o.max.toFixed(0)} ${JSON.stringify(o.outcomes)}`);
  return {
    [__ENV.SUMMARY || 'summary.json']: JSON.stringify(summary, null, 2),
    stdout: `${rows.join('\n')}\nrequests ${summary.requests} (${summary.requestsPerSecond.toFixed(1)}/s), `
      + `failed ${(100 * (summary.failedRate || 0)).toFixed(2)} %, thresholds failed: ${summary.thresholdsFailed.join(', ') || 'none'}\n`,
  };
}
