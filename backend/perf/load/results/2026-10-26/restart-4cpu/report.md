# Load-test run `restart-4cpu`

```
run: restart-4cpu, 2026-10-26T10:18:12Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
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
| 1500 | list | 1927 | 16.4 | 494.1 | 1,803.7 | 6,286 | 1842 | 0 | 0 | 0 | 0 | 0 | 85 |
| 1500 | preview | 20772 | 5.0 | 1,443.6 | 5,439.8 | 6,310 | 17242 | 0 | 0 | 0 | 0 | 0 | 3530 |
| 1500 | after | 22699 | 4.3 | 967.4 | 1,340.1 | 6,044 | 18931 | 0 | 0 | 0 | 0 | 0 | 3768 |
| 1500 | send | 6902 | 8.2 | 912.0 | 1,310.0 | 5,300 | 5806 | 0 | 0 | 1 | 0 | 0 | 1095 |
| 1500 | read | 5637 | 3.8 | 589.8 | 1,245.7 | 5,750 | 5604 | 0 | 0 | 0 | 0 | 0 | 33 |
| 1500 | search | 684 | 23.3 | 893.1 | 1,340.0 | 1,560 | 561 | 0 | 0 | 0 | 0 | 0 | 123 |
| 1500 | upload | 208 | 18.2 | 991.9 | 1,331.7 | 1,444 | 171 | 0 | 0 | 0 | 0 | 0 | 37 |
| 1500 | download | 171 | 4.4 | 606.9 | 1,234.0 | 1,377 | 170 | 0 | 0 | 0 | 0 | 0 | 1 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 300.0 | 14.70 % (8,673) | http_req_duration{name:send}, http_req_failed | 2.14 (4.12) | 0.62 (1.54) | 0.29 (0.56) | 0.69 (2.30) | 0.00 (0.04) | 1,918 | 10 / 186 | 5 / 82 | 89 / 10.9 | 0-5,000 | 171 / 0 | 0 |

Scans (Oro's timer, since its last start): 29, average 10.7 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 35,635
- Got live: 29,439; caught up after a reconnection: 6,192; **missing: 4**
- Duplicates: 0 live events received twice; 670 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 831, p95 3,275, p99 3,839, max 33,154
  - level 1500 (committed in its hold, 18,623): p50 867, p95 3,454, p99 3,908, max 33,154
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 600 (reconnections: 300); failures seen by the clients: {'status-500': 436, 'ConnectException': 765, 'IOException': 5}
- Missing, first ones: [('u1-61', 'c20e75dd-1c73-434b-83ad-47c3280f97f2', 124), ('u1-61', 'c20e75dd-1c73-434b-83ad-47c3280f97f2', 125), ('u1-61', 'c20e75dd-1c73-434b-83ad-47c3280f97f2', 126), ('u1-61', 'c20e75dd-1c73-434b-83ad-47c3280f97f2', 127)]

## Restart under load

- Stopped and started again in 45.6 s; ready 52.9 s after the stop was asked
- Tracked streams ended by the restart: 300; back (a hello): 300; caught up: 300
- Hello after readiness (ms): p50 20,962, p95 30,883, max 33,026; caught up after the stop was asked (ms): p50 73,994, p95 85,520, max 85,996
- Outbox in the minute after readiness: oldest event at most 1.2 s, at most 51 pending; under 3 s 6.7 s after readiness (first sample)
- Requests that failed from 5 s before the stop to 60 s after readiness: {'after 0 network': 3768, 'preview 0 network': 3530, 'send 0 network': 1095, 'search 0 network': 123, 'list 0 network': 85, 'upload 0 network': 37, 'read 0 network': 33, 'send 400 client_error': 1, 'download 0 network': 1}
