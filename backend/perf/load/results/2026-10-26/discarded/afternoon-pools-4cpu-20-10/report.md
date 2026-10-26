# Load-test run `pools-4cpu-20-10`

```
run: pools-4cpu-20-10, 2026-10-26T11:47:06Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=20 ORO_WORKER_DB_POOL_SIZE=10
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
| 3000 | list | 3855 | 27.1 | 836.5 | 1,412.2 | 2,444 | 3855 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 40102 | 33.6 | 1,332.9 | 1,822.0 | 3,206 | 40102 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 43957 | 28.9 | 1,300.9 | 1,831.0 | 3,264 | 43957 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13546 | 43.8 | 1,323.5 | 1,868.0 | 2,970 | 13546 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13077 | 15.6 | 1,150.1 | 1,665.0 | 2,476 | 13077 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1349 | 88.9 | 1,400.1 | 2,032.8 | 9,224 | 1349 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 469 | 78.2 | 1,301.4 | 1,637.4 | 1,932 | 469 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 469 | 18.5 | 1,185.2 | 1,849.4 | 2,731 | 469 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 7061 | 7.7 | 1,560.4 | 59,994.7 | 60,022 | 3997 | 0 | 0 | 0 | 0 | 0 | 3064 |
| 4000 | preview | 40168 | 624.0 | 2,127.3 | 2,915.5 | 4,222 | 40167 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | after | 44165 | 499.6 | 2,123.9 | 2,983.9 | 4,018 | 44163 | 0 | 0 | 0 | 0 | 0 | 2 |
| 4000 | send | 13581 | 509.3 | 2,006.0 | 2,995.1 | 3,921 | 13580 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | read | 13145 | 347.6 | 1,714.7 | 2,677.9 | 3,676 | 13145 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1329 | 592.0 | 2,079.7 | 3,042.6 | 3,915 | 1329 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 435 | 352.8 | 1,756.6 | 3,197.3 | 3,318 | 435 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 435 | 252.6 | 1,638.9 | 2,436.8 | 2,785 | 435 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | 608.4 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 3.13 (4.04) | 2.45 (3.37) | 1.40 (3.24) | 0.70 (1.31) | 0.02 (0.03) | 1,888 | 20 / 177 | 10 / 24 | 226 / 2.4 | 5,000-5,000 | 469 / 0 | 0 |
| 4000 | 572.7 | 2.55 % (3,068) | http_req_duration{name:send}, http_req_duration{name:list}, http_req_failed | 3.04 (4.12) | 2.09 (3.28) | 1.48 (3.38) | 0.62 (1.28) | 0.02 (0.06) | 1,933 | 20 / 177 | 10 / 68 | 126 / 1.3 | 5,000-5,000 | 435 / 0 | 0 |

Scans (Oro's timer, since its last start): 907, average 12.8 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 167,227
- Got live: 167,227; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 2,589, p95 7,888, p99 14,356, max 14,720
  - level 3000 (committed in its hold, 66,514): p50 1,808, p95 4,705, p99 6,695, max 7,234
  - level 4000 (committed in its hold, 61,808): p50 5,150, p95 10,902, p99 14,443, max 14,720
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
