# Load-test run `pools-4cpu-30-15`

```
run: pools-4cpu-30-15, 2026-10-26T11:59:47Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=30 ORO_WORKER_DB_POOL_SIZE=15
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 3000 ended on battery power.**

**NOT COMPARABLE: level 4000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | list | 3803 | 31.1 | 362.9 | 650.0 | 1,217 | 3803 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 42080 | 17.8 | 295.7 | 571.8 | 1,514 | 42080 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 45883 | 16.3 | 283.5 | 569.4 | 1,525 | 45883 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 14186 | 27.3 | 315.8 | 601.5 | 1,548 | 14186 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13765 | 9.0 | 194.1 | 400.6 | 1,067 | 13765 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1397 | 66.2 | 498.9 | 854.6 | 2,952 | 1397 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 421 | 66.1 | 339.9 | 588.6 | 1,137 | 421 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 421 | 12.9 | 243.4 | 342.9 | 521 | 421 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6606 | 578.4 | 5,501.1 | 59,998.9 | 60,045 | 4056 | 0 | 0 | 0 | 4 | 0 | 2546 |
| 4000 | preview | 33192 | 1,424.1 | 3,809.3 | 4,557.6 | 5,664 | 33189 | 0 | 0 | 0 | 0 | 0 | 3 |
| 4000 | after | 37248 | 1,397.2 | 3,872.2 | 4,748.6 | 57,721 | 37242 | 0 | 0 | 0 | 2 | 0 | 4 |
| 4000 | send | 11369 | 1,374.9 | 3,775.1 | 4,712.3 | 5,975 | 11369 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 10981 | 1,023.9 | 3,332.9 | 4,239.5 | 5,012 | 10981 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1089 | 1,385.3 | 3,890.5 | 4,836.0 | 5,357 | 1089 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 388 | 1,372.3 | 3,644.6 | 4,233.0 | 4,996 | 388 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 388 | 933.1 | 3,123.0 | 3,910.3 | 4,383 | 388 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | 629.3 | 0.00 % (0) | none | 2.87 (3.92) | 2.21 (3.00) | 1.18 (3.64) | 0.64 (1.39) | 0.02 (0.07) | 1,846 | 30 / 162 | 15 / 30 | 126 / 1.8 | 5,000-5,000 | 421 / 0 | 0 |
| 4000 | 482.0 | 2.53 % (2,559) | http_req_duration{name:send}, http_req_failed, http_req_duration{name:list} | 2.73 (4.04) | 2.35 (3.94) | 1.92 (4.56) | 0.77 (2.17) | 0.03 (0.12) | 1,963 | 30 / 167 | 15 / 20 | 180 / 3.3 | 4,730-5,000 | 388 / 0 | 0 |

Scans (Oro's timer, since its last start): 815, average 17.3 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 161,309
- Got live: 123,167; caught up after a reconnection: 38,142; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,578, p95 43,327, p99 69,815, max 73,799
  - level 3000 (committed in its hold, 67,098): p50 1,203, p95 4,513, p99 7,304, max 7,872
  - level 4000 (committed in its hold, 22,224): p50 34,193, p95 70,173, p99 73,295, max 73,799
- Gaps seen live (a message past the next seq, the missing ones read then): 293, 293 messages
- Hellos: 660 (reconnections: 360); failures seen by the clients: {'HttpConnectTimeoutException': 1045, 'HttpTimeoutException': 1}
