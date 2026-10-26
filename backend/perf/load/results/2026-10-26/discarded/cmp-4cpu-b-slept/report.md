# Load-test run `cmp-4cpu-b`

```
run: cmp-4cpu-b, 2026-10-26T01:33:14Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**INVALID: level 500 took 4,391 s (a stall or a sleep of the machine): do not use this run.**

**INVALID: level 1000 took 16,326 s (a stall or a sleep of the machine): do not use this run.**

**INVALID: level 1500 took 1,070 s (a stall or a sleep of the machine): do not use this run.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 635 | 16.4 | 575.3 | 2,068.7 | 10,585 | 635 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 6446 | 2.7 | 4,300.7 | 11,378.3 | 1,406,439 | 6446 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7081 | 2.6 | 1,182.6 | 8,774.5 | 179,276 | 7081 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2221 | 5.7 | 643.9 | 6,696.7 | 11,970 | 2221 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2129 | 1.6 | 263.9 | 2,460.0 | 11,381 | 2129 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 217 | 21.9 | 596.2 | 4,579.0 | 179,324 | 217 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 92 | 17.1 | 903.9 | 1,848.4 | 6,882 | 92 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 92 | 2.4 | 270.8 | 2,144.4 | 6,091 | 92 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1156 | 6,112.5 | 1,173,009.1 | 1,175,116.7 | 2,044,594 | 1156 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 7090 | 13.3 | 807,797.9 | 2,043,186.5 | 2,044,813 | 7090 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 8246 | 10.8 | 807,800.9 | 2,043,160.8 | 2,044,811 | 8246 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 2512 | 22.4 | 2,039,296.0 | 2,044,170.7 | 2,044,830 | 2512 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 2426 | 6.7 | 804,671.8 | 2,043,141.1 | 2,044,803 | 2426 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 271 | 63.7 | 807,411.7 | 2,043,122.3 | 2,044,182 | 271 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 86 | 45.1 | 606,902.4 | 992,955.3 | 2,044,246 | 86 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 86 | 7.0 | 607,371.1 | 2,040,142.1 | 2,040,158 | 86 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1828 | 4,302.7 | 16,926.6 | 18,644.3 | 19,016 | 1828 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 16537 | 10.6 | 7,483.4 | 8,938.5 | 865,595 | 16537 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 18365 | 10.5 | 7,500.3 | 13,762.1 | 16,868 | 18365 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 5503 | 19.7 | 5,370.2 | 8,930.4 | 16,102 | 5503 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 5317 | 6.5 | 2,939.6 | 8,732.6 | 13,414 | 5317 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 532 | 74.4 | 3,243.5 | 8,907.1 | 865,744 | 532 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 186 | 65.8 | 4,548.8 | 8,406.8 | 9,224 | 186 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 186 | 9.8 | 2,784.0 | 8,968.3 | 9,226 | 186 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 5.5 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 1.12 (4.01) | 0.36 (2.55) | 0.11 (0.57) | 0.58 (2.46) | 0.01 (0.05) | 2,048 | 9 / 1 | 4 / 27 | 104 / 1.4 | 960-5,000 | 92 / 0 | 0 |
| 1000 | 3.0 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 3.17 (12.80) | 0.81 (4.51) | 0.22 (0.92) | 1.67 (11.26) | 0.00 (0.03) | 2,048 | 10 / 75 | 5 / 13 | 48 / 1.0 | 771-5,000 | 86 / 0 | 0 |
| 1500 | 45.6 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 2.37 (5.91) | 1.24 (3.92) | 0.69 (2.96) | 0.67 (2.31) | 0.01 (0.09) | 2,047 | 10 / 183 | 5 / 18 | 377 / 14.8 | 3,869-4,999 | 186 / 0 | 0 |

Scans (Oro's timer, since its last start): 402, average 12.1 ms.
