// The load test of Oro (ORO-SCL-007): people using Umoja's messaging, as its client calls Oro.
//
// users: each virtual user is one person (its own token, its organization), every 8 to 12 s: the list again (the
// client reads it after every event it hears), what came in one of its conversations (?after=), a message 30 % of the
// time and its read position, a search 3 %, a small file 1 %. busy: the members of a group of 499 sending into it at a
// fixed rate (the lock of one conversation).
//
// Env: BASE (http://oro-load:8082/oro), STAGES (vus:seconds,...), BUSY_RATE (messages per second, 0: none).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE = __ENV.BASE || 'http://oro-load:8082/oro';
const tokens = new SharedArray('tokens', () => JSON.parse(open('./tokens.json')).filter((t) => t.user !== 'u1-1'));
const orgs = JSON.parse(open('./orgs.json'));
const busy = JSON.parse(open('./busy.json'));
const busyTokens = new SharedArray('busy', () => {
  const members = new Set(busy.members);
  return JSON.parse(open('./tokens.json')).filter((t) => members.has(t.user));
});
const file = 'x'.repeat(20 * 1024);
const refused = new Counter('refused_429');

const stages = (__ENV.STAGES || '100:60').split(',').map((s) => {
  const [target, seconds] = s.split(':');
  return { target: Number(target), duration: `${seconds}s` };
});
const busyRate = Number(__ENV.BUSY_RATE || 0);

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
  thresholds: {
    'http_req_failed{expected:no}': ['rate<0.01'],
    'http_req_duration{name:list}': ['p(95)<500'],
    'http_req_duration{name:send}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

function headers(person) {
  return { Authorization: `Bearer ${person.token}`, 'x-organization-id': orgs[String(person.org)], 'Content-Type': 'application/json' };
}

function tagged(name) {
  // a 429 is the rate limit doing its job, not a failure of Oro
  return { tags: { name }, responseCallback: http.expectedStatuses({ min: 200, max: 299 }, 429) };
}

function counted(res) {
  if (res.status === 429) refused.add(1);
  return res;
}

const state = {};

export function user() {
  const vu = exec.vu.idInTest;
  const person = tokens[(vu * 7919) % tokens.length];
  const h = { headers: headers(person) };
  const me = state[vu] || (state[vu] = { conversations: [] });

  const list = counted(http.get(`${BASE}/conversations`, Object.assign({}, h, tagged('list'))));
  check(list, { 'list 200': (r) => r.status === 200 });
  if (list.status === 200) {
    me.conversations = list.json().map((c) => ({ id: c.id, seq: c.lastMessageSeq }));
  }
  if (me.conversations.length) {
    const c = me.conversations[Math.floor(Math.random() * Math.min(me.conversations.length, 10))];
    const after = counted(http.get(`${BASE}/conversations/${c.id}/messages?after=${Math.max(0, c.seq - 3)}`, Object.assign({}, h, tagged('after'))));
    check(after, { 'after 200': (r) => r.status === 200 });

    if (Math.random() < 0.01) {
      const upload = counted(http.post(`${BASE}/conversations/${c.id}/attachments`,
        { file: http.file(file, 'notes.txt', 'text/plain') },
        { headers: { Authorization: h.headers.Authorization, 'x-organization-id': h.headers['x-organization-id'] }, ...tagged('upload') }));
      if (upload.status === 201) {
        const sent = counted(http.post(`${BASE}/conversations/${c.id}/messages`, JSON.stringify({
          clientMessageId: `load-${vu}-${Date.now()}`, type: 'FILE', body: null, attachmentIds: [upload.json('id')],
        }), Object.assign({}, h, tagged('send'))));
        check(sent, { 'file sent': (r) => r.status === 201 || r.status === 429 });
      }
    } else if (Math.random() < 0.3) {
      const sent = counted(http.post(`${BASE}/conversations/${c.id}/messages`, JSON.stringify({
        clientMessageId: `load-${vu}-${Date.now()}`, type: 'TEXT', body: 'load test: the minutes of the meeting',
      }), Object.assign({}, h, tagged('send'))));
      check(sent, { 'sent': (r) => r.status === 201 || r.status === 429 });
      if (sent.status === 201) {
        c.seq = sent.json('seq');
        counted(http.put(`${BASE}/conversations/${c.id}/read`, JSON.stringify({ seq: c.seq }), Object.assign({}, h, tagged('read'))));
      }
    }
    if (Math.random() < 0.03) {
      const words = ['invoice', 'meeting', 'deploy', 'budget', 'visa contract', 'release notes'];
      counted(http.get(`${BASE}/search/messages?q=${encodeURIComponent(words[Math.floor(Math.random() * words.length)])}`,
        Object.assign({}, h, tagged('search'))));
    }
  }
  sleep(8 + Math.random() * 4);
}

export function busySend() {
  const person = busyTokens[Math.floor(Math.random() * busyTokens.length)];
  const sent = counted(http.post(`${BASE}/conversations/${busy.id}/messages`, JSON.stringify({
    clientMessageId: `busy-${exec.scenario.iterationInTest}-${Date.now()}`, type: 'TEXT', body: 'load test: in the busy group',
  }), { headers: headers(person), ...tagged('busy-send') }));
  check(sent, { 'busy sent': (r) => r.status === 201 || r.status === 429 });
}
