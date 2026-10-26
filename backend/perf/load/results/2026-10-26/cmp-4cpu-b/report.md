# Load-test run `cmp-4cpu-b`

```
run: cmp-4cpu-b, 2026-10-26T08:12:40Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: AC Power, low power mode 0
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 653 | 14.7 | 30.0 | 56.2 | 101 | 653 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7081 | 2.4 | 7.1 | 19.9 | 87 | 7081 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7734 | 2.4 | 6.4 | 14.7 | 157 | 7734 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2413 | 5.2 | 13.2 | 23.5 | 80 | 2413 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2332 | 1.4 | 3.6 | 7.8 | 31 | 2332 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 237 | 21.4 | 69.6 | 154.3 | 3,278 | 237 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 81 | 16.2 | 32.5 | 62.7 | 71 | 81 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 81 | 2.4 | 4.4 | 7.1 | 15 | 81 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1286 | 12.1 | 36.2 | 73.2 | 160 | 1286 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14194 | 2.3 | 11.1 | 34.3 | 178 | 14194 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15480 | 2.3 | 9.7 | 24.4 | 174 | 15480 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4751 | 4.5 | 16.1 | 41.7 | 100 | 4751 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4586 | 1.3 | 5.6 | 13.0 | 80 | 4586 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 470 | 17.3 | 56.3 | 128.3 | 265 | 470 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 165 | 13.7 | 42.5 | 74.8 | 134 | 165 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 165 | 2.1 | 6.8 | 14.0 | 15 | 165 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1922 | 9.7 | 33.2 | 68.0 | 118 | 1922 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21306 | 2.5 | 12.9 | 30.5 | 109 | 21306 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23228 | 2.5 | 11.6 | 24.5 | 82 | 23228 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7139 | 4.7 | 18.1 | 36.0 | 121 | 7139 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6912 | 1.4 | 6.9 | 16.8 | 76 | 6912 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 680 | 16.4 | 48.9 | 77.6 | 2,614 | 680 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 227 | 12.8 | 46.7 | 91.0 | 139 | 227 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 227 | 2.1 | 9.6 | 15.3 | 43 | 227 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2616 | 11.1 | 63.0 | 136.3 | 247 | 2616 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28341 | 3.3 | 29.2 | 74.2 | 229 | 28341 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30957 | 3.3 | 23.6 | 63.4 | 229 | 30957 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9608 | 5.8 | 34.2 | 75.6 | 253 | 9608 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9293 | 1.8 | 14.4 | 42.8 | 160 | 9293 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 945 | 18.7 | 73.4 | 129.8 | 228 | 945 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 315 | 18.1 | 75.4 | 129.1 | 169 | 315 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 315 | 2.8 | 16.8 | 41.4 | 92 | 315 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3820 | 15.1 | 232.0 | 492.1 | 998 | 3820 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 41831 | 16.5 | 394.7 | 751.6 | 1,590 | 41831 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 45651 | 14.1 | 361.9 | 674.4 | 1,582 | 45651 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13807 | 19.7 | 329.4 | 632.5 | 1,360 | 13807 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13347 | 7.3 | 226.4 | 457.5 | 1,148 | 13347 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1357 | 52.7 | 433.9 | 699.4 | 3,582 | 1357 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 460 | 48.3 | 307.1 | 486.5 | 695 | 460 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 460 | 10.2 | 216.1 | 372.0 | 565 | 460 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6986 | 8.0 | 797.2 | 59,998.8 | 60,016 | 4080 | 0 | 0 | 0 | 0 | 0 | 2906 |
| 4000 | preview | 43062 | 193.2 | 1,433.5 | 2,002.3 | 3,356 | 43062 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 47141 | 140.7 | 1,352.2 | 1,857.9 | 2,947 | 47141 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 14570 | 138.2 | 1,294.6 | 1,734.4 | 3,004 | 14570 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 14103 | 78.4 | 1,107.7 | 1,672.9 | 2,603 | 14103 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1411 | 186.5 | 1,337.7 | 1,737.2 | 2,664 | 1411 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 467 | 155.2 | 1,197.8 | 1,845.2 | 2,078 | 467 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 467 | 64.7 | 1,134.9 | 2,098.6 | 2,174 | 467 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 107.5 | 0.00 % (0) | none | 0.36 (0.92) | 0.20 (0.41) | 0.11 (0.66) | 0.14 (0.41) | 0.01 (0.04) | 1,701 | 1 / 0 | 1 / 0 | 18 / 0.9 | 5,000-5,000 | 81 / 0 | 0 |
| 1000 | 214.2 | 0.00 % (0) | none | 0.70 (1.43) | 0.45 (1.28) | 0.26 (1.82) | 0.27 (0.74) | 0.00 (0.02) | 1,719 | 5 / 0 | 5 / 1 | 40 / 1.0 | 5,000-5,000 | 165 / 0 | 0 |
| 1500 | 321.5 | 0.00 % (0) | none | 0.98 (1.74) | 0.70 (1.59) | 0.46 (2.31) | 0.32 (0.84) | 0.01 (0.03) | 1,745 | 4 / 0 | 3 / 0 | 64 / 1.2 | 5,000-5,000 | 227 / 0 | 0 |
| 2000 | 429.5 | 0.00 % (0) | none | 1.63 (3.12) | 1.10 (2.37) | 0.68 (2.95) | 0.48 (1.07) | 0.02 (0.09) | 1,777 | 10 / 1 | 4 / 0 | 100 / 1.3 | 5,000-5,000 | 315 / 0 | 0 |
| 3000 | 628.8 | 0.00 % (0) | none | 2.44 (3.89) | 1.77 (2.94) | 1.35 (4.16) | 0.60 (1.34) | 0.02 (0.06) | 1,826 | 10 / 187 | 5 / 43 | 127 / 1.4 | 5,000-5,000 | 460 / 0 | 0 |
| 4000 | 610.2 | 2.27 % (2,906) | http_req_duration{name:list}, http_req_duration{name:send}, http_req_failed | 2.46 (3.92) | 2.06 (3.92) | 1.46 (3.86) | 0.57 (1.52) | 0.02 (0.09) | 1,872 | 10 / 189 | 5 / 13 | 120 / 2.1 | 5,000-5,000 | 467 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,724, average 8.3 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 318,326
- Got live: 318,326; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 931, p95 1,892, p99 2,664, max 3,991
  - level 500 (committed in its hold, 10,551): p50 594, p95 1,034, p99 1,063, max 1,067
  - level 1000 (committed in its hold, 21,490): p50 636, p95 1,077, p99 1,205, max 1,244
  - level 1500 (committed in its hold, 34,365): p50 720, p95 1,109, p99 1,200, max 1,270
  - level 2000 (committed in its hold, 50,281): p50 825, p95 1,350, p99 1,720, max 1,892
  - level 3000 (committed in its hold, 65,791): p50 1,075, p95 1,787, p99 2,176, max 3,135
  - level 4000 (committed in its hold, 64,582): p50 1,308, p95 2,631, p99 3,724, max 3,991
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
