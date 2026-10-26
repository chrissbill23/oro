# Load-test run `warm-4cpu-20-10`

```
run: warm-4cpu-20-10, 2026-10-26T13:00:48Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=20 ORO_WORKER_DB_POOL_SIZE=10
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 2000 ended on battery power.**

**NOT COMPARABLE: level 3000 ended on battery power.**

**NOT COMPARABLE: level 4000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2474 | 99.4 | 2,460.7 | 3,687.8 | 5,019 | 2474 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 23386 | 195.4 | 3,528.7 | 4,401.7 | 6,111 | 23386 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 25860 | 153.7 | 3,211.1 | 4,152.1 | 5,936 | 25860 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 7953 | 160.6 | 2,962.1 | 4,077.5 | 6,028 | 7953 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 7684 | 72.9 | 2,372.2 | 3,567.5 | 4,668 | 7684 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 790 | 313.6 | 3,247.4 | 4,503.0 | 6,957 | 790 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 269 | 278.9 | 2,880.3 | 3,838.4 | 4,145 | 269 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 269 | 111.2 | 2,783.1 | 3,639.7 | 4,086 | 269 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3641 | 32.1 | 4,042.4 | 6,704.1 | 8,004 | 3641 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 31223 | 1,159.8 | 4,045.1 | 6,097.9 | 7,904 | 31223 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 34864 | 853.6 | 4,386.3 | 5,513.5 | 8,012 | 34864 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 10703 | 831.0 | 3,677.8 | 5,041.3 | 7,602 | 10703 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 10358 | 616.7 | 3,146.4 | 4,715.5 | 7,376 | 10358 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1035 | 810.7 | 3,388.0 | 4,934.7 | 7,110 | 1035 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 345 | 982.6 | 3,855.8 | 4,899.8 | 6,530 | 345 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 345 | 696.4 | 3,075.5 | 4,630.9 | 6,476 | 345 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6274 | 11.0 | 5,749.7 | 60,214.0 | 62,215 | 3693 | 0 | 0 | 0 | 0 | 0 | 2581 |
| 4000 | preview | 23341 | 2,682.9 | 7,320.8 | 12,696.8 | 16,649 | 23323 | 0 | 0 | 0 | 10 | 0 | 8 |
| 4000 | after | 27026 | 2,686.3 | 6,443.5 | 11,930.4 | 16,137 | 27003 | 0 | 0 | 0 | 7 | 0 | 16 |
| 4000 | send | 8345 | 2,756.0 | 8,471.7 | 12,315.3 | 15,864 | 8340 | 0 | 0 | 0 | 2 | 0 | 3 |
| 4000 | read | 8060 | 2,170.1 | 6,687.4 | 12,142.9 | 15,245 | 8057 | 0 | 0 | 0 | 3 | 0 | 0 |
| 4000 | search | 846 | 2,289.2 | 8,080.9 | 13,031.0 | 15,811 | 845 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | upload | 280 | 2,942.2 | 8,312.2 | 9,764.0 | 12,914 | 280 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 280 | 2,293.3 | 6,791.0 | 12,892.3 | 13,450 | 280 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 357.6 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 2.93 (4.54) | 2.08 (3.27) | 1.45 (3.91) | 0.71 (1.47) | 0.03 (0.12) | 1,802 | 20 / 178 | 10 / 157 | 132 / 4.2 | 5,000-5,000 | 269 / 0 | 0 |
| 3000 | 480.9 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 3.02 (4.22) | 2.20 (3.09) | 1.70 (3.56) | 0.69 (1.16) | 0.02 (0.05) | 1,863 | 20 / 178 | 10 / 32 | 142 / 3.3 | 5,000-5,000 | 345 / 0 | 0 |
| 4000 | 354.3 | 3.53 % (2,631) | http_req_duration{name:send}, http_req_duration{name:list}, http_req_failed | 2.93 (4.82) | 2.21 (3.63) | 2.57 (5.16) | 0.88 (2.38) | 0.02 (0.10) | 1,990 | 20 / 179 | 10 / 47 | 35 / 1.6 | 5,000-5,000 | 280 / 0 | 0 |

Scans (Oro's timer, since its last start): 902, average 22.2 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 160,744
- Got live: 160,744; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 7,428, p95 34,186, p99 67,101, max 68,403
  - level 2000 (committed in its hold, 36,251): p50 4,850, p95 24,007, p99 25,890, max 25,934
  - level 3000 (committed in its hold, 46,346): p50 13,456, p95 27,717, p99 30,031, max 30,203
  - level 4000 (committed in its hold, 35,668): p50 20,078, p95 66,706, p99 68,135, max 68,403
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
