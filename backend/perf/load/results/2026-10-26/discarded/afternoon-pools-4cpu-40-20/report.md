# Load-test run `pools-4cpu-40-20`

```
run: pools-4cpu-40-20, 2026-10-26T12:12:24Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=40 ORO_WORKER_DB_POOL_SIZE=20
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
| 3000 | list | 3638 | 870.3 | 9,297.4 | 9,970.7 | 10,988 | 3636 | 0 | 0 | 0 | 2 | 0 | 0 |
| 3000 | preview | 29716 | 491.5 | 5,668.1 | 7,887.3 | 11,949 | 29713 | 0 | 0 | 0 | 3 | 0 | 0 |
| 3000 | after | 33352 | 501.4 | 6,961.4 | 9,159.0 | 10,900 | 33348 | 0 | 0 | 0 | 4 | 0 | 0 |
| 3000 | send | 10246 | 540.5 | 6,619.0 | 8,283.6 | 10,864 | 10246 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 9923 | 332.9 | 3,531.1 | 7,360.4 | 10,128 | 9922 | 0 | 0 | 0 | 1 | 0 | 0 |
| 3000 | search | 972 | 724.7 | 7,221.9 | 8,808.5 | 10,742 | 972 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 323 | 598.7 | 2,868.9 | 7,655.5 | 9,645 | 323 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 323 | 305.3 | 2,332.7 | 6,978.0 | 8,449 | 323 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6854 | 28.9 | 2,517.3 | 60,011.2 | 60,214 | 3949 | 0 | 0 | 0 | 0 | 0 | 2905 |
| 4000 | preview | 36915 | 861.4 | 3,161.7 | 3,793.5 | 54,874 | 36909 | 0 | 0 | 0 | 0 | 0 | 6 |
| 4000 | after | 40860 | 725.6 | 3,007.8 | 3,758.6 | 5,148 | 40855 | 0 | 0 | 0 | 0 | 0 | 5 |
| 4000 | send | 12427 | 762.2 | 2,857.4 | 3,786.2 | 13,627 | 12423 | 0 | 0 | 0 | 0 | 0 | 4 |
| 4000 | read | 12004 | 553.7 | 2,369.1 | 3,409.5 | 4,641 | 12004 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1216 | 1,018.0 | 3,112.8 | 4,207.4 | 4,975 | 1215 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | upload | 419 | 603.8 | 2,731.7 | 3,878.3 | 4,286 | 419 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 419 | 467.4 | 2,111.9 | 3,092.6 | 3,825 | 419 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | 460.2 | 0.01 % (10) | http_req_duration{name:send}, http_req_duration{name:list} | 3.00 (4.02) | 2.45 (3.70) | 1.71 (4.46) | 0.77 (1.63) | 0.02 (0.12) | 1,872 | 40 / 158 | 20 / 137 | 84 / 2.1 | 5,000-5,000 | 323 / 0 | 0 |
| 4000 | 528.8 | 2.63 % (2,921) | http_req_duration{name:send}, http_req_duration{name:list}, http_req_failed | 2.92 (4.28) | 2.32 (3.50) | 1.94 (4.04) | 0.50 (1.59) | 0.02 (0.05) | 1,900 | 40 / 157 | 20 / 36 | 77 / 3.2 | 5,000-5,000 | 419 / 0 | 0 |

Scans (Oro's timer, since its last start): 751, average 18.5 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 140,258
- Got live: 140,258; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 17,192, p95 63,642, p99 73,343, max 77,576
  - level 3000 (committed in its hold, 57,610): p50 11,582, p95 43,299, p99 44,467, max 44,824
  - level 4000 (committed in its hold, 51,446): p50 39,973, p95 70,346, p99 76,357, max 77,576
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
