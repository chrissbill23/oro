# Load-test run `warm-4cpu-30-15`

```
run: warm-4cpu-30-15, 2026-10-26T13:17:33Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=30 ORO_WORKER_DB_POOL_SIZE=15
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 2000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2603 | 16.3 | 128.9 | 514.5 | 1,220 | 2603 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28307 | 3.9 | 36.9 | 125.5 | 872 | 28307 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30910 | 4.1 | 30.0 | 106.0 | 1,060 | 30910 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9371 | 7.6 | 54.3 | 147.5 | 844 | 9371 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9077 | 2.3 | 19.7 | 62.4 | 437 | 9077 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 940 | 24.8 | 121.9 | 267.6 | 975 | 940 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 294 | 19.9 | 108.7 | 246.5 | 449 | 294 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 294 | 3.6 | 22.2 | 71.7 | 104 | 294 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3716 | 26.3 | 2,597.3 | 3,013.2 | 4,197 | 3716 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 36201 | 206.8 | 2,673.4 | 3,682.2 | 4,724 | 36201 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 39917 | 161.5 | 2,514.7 | 3,503.7 | 5,069 | 39917 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 12295 | 189.7 | 2,398.2 | 3,230.0 | 5,087 | 12295 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 11861 | 95.4 | 2,094.9 | 2,718.8 | 4,699 | 11861 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1167 | 291.3 | 2,572.1 | 3,844.1 | 5,436 | 1167 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 434 | 254.7 | 2,446.8 | 2,963.1 | 3,936 | 434 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 434 | 92.5 | 1,981.9 | 2,830.6 | 3,787 | 434 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6765 | 8.2 | 2,284.1 | 60,002.8 | 60,132 | 3872 | 0 | 0 | 0 | 0 | 0 | 2893 |
| 4000 | preview | 33239 | 1,218.6 | 4,570.8 | 7,800.6 | 11,677 | 33239 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 37111 | 1,090.7 | 5,164.6 | 7,343.5 | 9,370 | 37111 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 11521 | 1,056.5 | 4,289.2 | 6,821.9 | 8,295 | 11521 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 11145 | 850.7 | 3,445.3 | 6,096.2 | 8,702 | 11145 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1128 | 1,084.7 | 4,158.6 | 6,624.2 | 8,499 | 1128 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 376 | 1,135.0 | 4,253.5 | 6,459.3 | 7,780 | 376 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 376 | 840.8 | 4,007.5 | 6,881.6 | 7,927 | 376 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 426.3 | 0.00 % (0) | none | 1.46 (2.40) | 1.16 (2.65) | 0.67 (3.72) | 0.45 (1.15) | 0.01 (0.04) | 1,855 | 30 / 10 | 14 / 0 | 75 / 1.2 | 5,000-5,000 | 294 / 0 | 0 |
| 3000 | 551.8 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 3.31 (4.19) | 2.78 (4.73) | 1.55 (2.41) | 0.70 (1.19) | 0.02 (0.08) | 1,938 | 30 / 165 | 15 / 67 | 96 / 1.1 | 5,000-5,000 | 434 / 0 | 0 |
| 4000 | 483.7 | 2.85 % (2,893) | http_req_failed, http_req_duration{name:send}, http_req_duration{name:list} | 2.66 (3.94) | 2.34 (3.71) | 1.97 (4.06) | 0.66 (1.69) | 0.03 (0.19) | 1,951 | 30 / 167 | 15 / 33 | 91 / 1.7 | 5,000-5,000 | 376 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,118, average 16.0 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 206,782
- Got live: 206,782; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 5,684, p95 52,207, p99 64,225, max 66,458
  - level 2000 (committed in its hold, 50,680): p50 883, p95 1,453, p99 2,238, max 2,746
  - level 3000 (committed in its hold, 60,222): p50 12,078, p95 21,262, p99 22,398, max 22,520
  - level 4000 (committed in its hold, 50,524): p50 36,699, p95 63,257, p99 66,107, max 66,458
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
