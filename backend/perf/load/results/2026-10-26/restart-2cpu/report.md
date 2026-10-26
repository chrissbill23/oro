# Load-test run `restart-2cpu`

```
run: restart-2cpu, 2026-10-26T10:08:54Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 1500 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: 1500 at 100 s; profile: none
```

**NOT COMPARABLE: level 1500 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | list | 1825 | 13.2 | 1,312.9 | 16,201.1 | 25,273 | 1807 | 0 | 0 | 0 | 0 | 0 | 18 |
| 1500 | preview | 16520 | 15.6 | 17,187.5 | 24,744.4 | 31,298 | 15818 | 0 | 0 | 0 | 0 | 0 | 702 |
| 1500 | after | 18345 | 6.5 | 14,780.3 | 23,905.9 | 26,326 | 17421 | 0 | 0 | 0 | 0 | 0 | 924 |
| 1500 | send | 5662 | 11.8 | 7,067.7 | 23,205.0 | 26,589 | 5315 | 0 | 0 | 0 | 0 | 0 | 347 |
| 1500 | read | 5151 | 4.0 | 3,182.4 | 20,738.8 | 25,578 | 5082 | 0 | 0 | 0 | 0 | 0 | 69 |
| 1500 | search | 554 | 32.8 | 3,730.2 | 22,037.0 | 24,896 | 522 | 0 | 0 | 0 | 0 | 0 | 32 |
| 1500 | upload | 183 | 99.9 | 6,722.8 | 23,037.6 | 24,283 | 173 | 0 | 0 | 0 | 0 | 0 | 10 |
| 1500 | download | 173 | 11.7 | 2,699.6 | 6,921.1 | 10,677 | 166 | 0 | 0 | 0 | 0 | 0 | 7 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 230.4 | 4.36 % (2,109) | http_req_failed, http_req_duration{name:list}, http_req_duration{name:send} | 1.79 (2.08) | 0.60 (1.21) | 0.29 (0.45) | 0.51 (1.18) | 0.01 (0.02) | 1,725 | 10 / 180 | 5 / 70 | 48 / 33.9 | 23-5,000 | 173 / 0 | 0 |

Scans (Oro's timer, since its last start): 20, average 26.5 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 30,998
- Got live: 27,809; caught up after a reconnection: 3,186; **missing: 3**
- Duplicates: 0 live events received twice; 2,718 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 983, p95 19,189, p99 37,818, max 82,042
  - level 1500 (committed in its hold, 17,984): p50 1,190, p95 21,413, p99 43,683, max 82,042
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 600 (reconnections: 300); failures seen by the clients: {'status-500': 195, 'IOException': 233, 'ConnectException': 508}
- Missing, first ones: [('u1-155', '11afc129-4911-44a6-b2d2-3517025b9847', 195), ('u1-155', '11afc129-4911-44a6-b2d2-3517025b9847', 196), ('u1-155', '11afc129-4911-44a6-b2d2-3517025b9847', 197)]

## Restart under load

- Stopped and started again in 46.0 s; ready 57.4 s after the stop was asked
- Tracked streams ended by the restart: 300; back (a hello): 300; caught up: 300
- Hello after readiness (ms): p50 21,364, p95 55,675, max 57,075; caught up after the stop was asked (ms): p50 111,324, p95 113,349, max 114,527
- Outbox in the minute after readiness: oldest event at most 33.9 s, at most 1 pending; under 3 s 22.9 s after readiness (first sample)
- Requests that failed from 5 s before the stop to 60 s after readiness: {'after 0 network': 924, 'preview 0 network': 702, 'send 0 network': 347, 'read 0 network': 69, 'search 0 network': 32, 'list 0 network': 18, 'upload 0 network': 10, 'download 0 network': 7}
