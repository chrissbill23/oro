# Load-test run `cmp-2cpu-b`

```
run: cmp-2cpu-b, 2026-10-26T08:39:07Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
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
| 500 | list | 650 | 13.5 | 28.5 | 48.4 | 102 | 650 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7090 | 2.3 | 6.4 | 20.8 | 78 | 7090 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7740 | 2.3 | 5.8 | 10.5 | 75 | 7740 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2345 | 4.8 | 11.0 | 16.3 | 70 | 2345 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2263 | 1.4 | 3.5 | 6.1 | 66 | 2263 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 233 | 17.8 | 40.7 | 79.2 | 103 | 233 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 82 | 13.9 | 29.0 | 63.6 | 98 | 82 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 82 | 2.0 | 3.6 | 5.5 | 6 | 82 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1271 | 10.8 | 27.9 | 54.0 | 243 | 1271 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14191 | 2.1 | 9.3 | 42.6 | 225 | 14191 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15462 | 2.1 | 7.7 | 31.0 | 177 | 15462 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4825 | 4.1 | 12.7 | 43.9 | 181 | 4825 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4678 | 1.2 | 4.2 | 11.0 | 115 | 4678 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 421 | 14.9 | 42.3 | 97.9 | 222 | 421 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 147 | 11.6 | 35.0 | 72.0 | 98 | 147 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 147 | 1.9 | 4.8 | 8.7 | 16 | 147 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1901 | 9.4 | 44.7 | 82.1 | 158 | 1901 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21303 | 2.4 | 35.1 | 65.5 | 189 | 21303 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23204 | 2.4 | 15.8 | 63.7 | 179 | 23204 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7092 | 4.6 | 35.7 | 83.6 | 138 | 7092 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6878 | 1.4 | 10.1 | 57.3 | 102 | 6878 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 685 | 16.1 | 72.2 | 98.8 | 179 | 685 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 214 | 14.1 | 79.6 | 149.2 | 198 | 214 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 214 | 2.1 | 19.6 | 54.7 | 56 | 214 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2582 | 9.7 | 102.4 | 203.7 | 605 | 2582 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28178 | 6.3 | 152.9 | 327.7 | 983 | 28178 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30760 | 5.1 | 117.3 | 299.3 | 1,097 | 30760 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9460 | 8.7 | 165.6 | 305.8 | 957 | 9460 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9166 | 2.6 | 93.0 | 199.7 | 754 | 9166 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 946 | 26.7 | 208.4 | 395.2 | 4,141 | 946 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 294 | 21.7 | 261.3 | 391.6 | 458 | 294 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 294 | 3.2 | 95.7 | 187.2 | 218 | 294 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3532 | 139.1 | 3,888.0 | 4,471.5 | 5,479 | 3532 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 25109 | 3,151.8 | 4,820.1 | 5,431.0 | 7,330 | 25109 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 28641 | 2,797.7 | 4,756.2 | 5,371.6 | 7,887 | 28641 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 8661 | 2,797.5 | 4,688.0 | 5,301.9 | 6,336 | 8661 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 8389 | 2,668.3 | 4,410.7 | 4,900.9 | 5,904 | 8389 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 833 | 2,793.3 | 4,792.7 | 5,452.5 | 7,893 | 833 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 272 | 2,950.1 | 4,603.0 | 4,996.6 | 5,283 | 272 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 272 | 2,649.0 | 4,327.2 | 4,731.3 | 5,005 | 272 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6821 | 7.4 | 3,273.8 | 59,999.9 | 60,023 | 3742 | 0 | 0 | 0 | 0 | 0 | 3079 |
| 4000 | preview | 25564 | 3,246.2 | 7,271.2 | 8,284.0 | 10,692 | 25564 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 29306 | 2,863.6 | 7,150.0 | 8,288.5 | 11,190 | 29306 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 9066 | 2,836.8 | 7,272.5 | 8,387.7 | 11,140 | 9066 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 8781 | 2,715.3 | 6,795.1 | 7,882.5 | 11,068 | 8781 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 861 | 2,880.6 | 7,362.1 | 8,398.1 | 10,087 | 861 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 285 | 2,873.2 | 7,483.1 | 8,345.9 | 9,269 | 285 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 285 | 2,644.4 | 6,772.9 | 7,574.5 | 8,270 | 285 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 107.0 | 0.00 % (0) | none | 0.32 (0.87) | 0.17 (0.27) | 0.06 (0.08) | 0.13 (0.49) | 0.00 (0.02) | 1,698 | 2 / 0 | 1 / 0 | 20 / 0.9 | 4,998-4,998 | 82 / 0 | 0 |
| 1000 | 214.6 | 0.00 % (0) | none | 0.64 (1.42) | 0.41 (1.31) | 0.17 (0.79) | 0.33 (2.62) | 0.00 (0.02) | 1,662 | 3 / 0 | 2 / 0 | 21 / 0.7 | 4,998-4,998 | 147 / 0 | 0 |
| 1500 | 320.6 | 0.00 % (0) | none | 0.90 (1.52) | 0.63 (0.98) | 0.25 (0.39) | 0.33 (0.82) | 0.01 (0.10) | 1,688 | 10 / 2 | 5 / 2 | 36 / 0.9 | 4,998-4,998 | 214 / 0 | 0 |
| 2000 | 425.8 | 0.00 % (0) | none | 1.41 (2.19) | 1.05 (1.65) | 0.53 (1.98) | 0.40 (1.30) | 0.01 (0.11) | 1,723 | 10 / 52 | 5 / 14 | 111 / 1.5 | 4,998-4,998 | 294 / 0 | 0 |
| 3000 | 377.8 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 1.96 (2.55) | 1.31 (1.66) | 1.16 (5.06) | 0.65 (1.90) | 0.02 (0.06) | 1,782 | 10 / 189 | 5 / 26 | 54 / 1.3 | 4,998-4,998 | 272 / 0 | 0 |
| 4000 | 385.3 | 3.80 % (3,079) | http_req_failed, http_req_duration{name:send}, http_req_duration{name:list} | 1.75 (2.13) | 1.28 (2.61) | 1.30 (4.63) | 0.50 (2.37) | 0.02 (0.10) | 1,812 | 10 / 190 | 5 / 13 | 57 / 1.2 | 4,998-4,998 | 285 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,302, average 17.0 ms.

## Delivery to the tracked clients

- Tracked clients: 298; messages committed in their conversations during the run (after what they had at their first hello): 250,700
- Got live: 250,700; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,228, p95 36,007, p99 41,199, max 43,504
  - level 500 (committed in its hold, 8,851): p50 559, p95 1,018, p99 1,079, max 1,081
  - level 1000 (committed in its hold, 20,805): p50 564, p95 1,111, p99 1,605, max 1,642
  - level 1500 (committed in its hold, 35,584): p50 852, p95 1,416, p99 2,044, max 2,240
  - level 2000 (committed in its hold, 42,683): p50 1,242, p95 2,434, p99 3,682, max 3,994
  - level 3000 (committed in its hold, 41,574): p50 18,289, p95 39,164, p99 41,314, max 42,017
  - level 4000 (committed in its hold, 37,682): p50 27,813, p95 41,301, p99 43,416, max 43,504
- Hellos: 298 (reconnections: 0); failures seen by the clients: none
