# Load-test run `cmp-4cpu-a`

```
run: cmp-4cpu-a, 2026-10-26T01:06:48Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 650 | 13.6 | 27.2 | 46.7 | 83 | 650 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7114 | 2.4 | 6.0 | 11.9 | 109 | 7114 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7764 | 2.3 | 5.6 | 10.5 | 60 | 7764 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2411 | 4.9 | 10.7 | 17.8 | 50 | 2411 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2332 | 1.4 | 3.2 | 6.2 | 45 | 2332 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 230 | 17.4 | 39.0 | 72.6 | 93 | 230 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 79 | 14.7 | 35.5 | 45.3 | 52 | 79 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 79 | 1.9 | 4.2 | 6.8 | 9 | 79 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1261 | 11.0 | 29.8 | 52.8 | 134 | 1261 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14210 | 2.1 | 8.2 | 22.7 | 75 | 14210 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15471 | 2.1 | 7.5 | 16.4 | 99 | 15471 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4847 | 4.2 | 12.7 | 27.7 | 99 | 4847 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4686 | 1.2 | 4.3 | 10.2 | 60 | 4686 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 488 | 15.0 | 40.7 | 67.5 | 114 | 488 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 161 | 12.2 | 39.4 | 77.8 | 98 | 161 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 161 | 1.9 | 4.9 | 11.0 | 67 | 161 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1956 | 10.5 | 36.5 | 66.6 | 143 | 1956 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21325 | 2.4 | 12.6 | 35.2 | 128 | 21325 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23281 | 2.4 | 10.5 | 21.9 | 141 | 23281 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7133 | 4.5 | 17.7 | 36.4 | 148 | 7133 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6898 | 1.3 | 6.0 | 14.8 | 128 | 6898 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 681 | 16.0 | 48.7 | 82.3 | 1,955 | 681 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 235 | 14.0 | 40.3 | 59.3 | 91 | 235 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 235 | 2.1 | 8.5 | 18.6 | 56 | 235 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2519 | 10.6 | 59.4 | 141.6 | 411 | 2519 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28421 | 3.2 | 29.3 | 99.7 | 367 | 28421 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30940 | 3.2 | 22.7 | 77.3 | 410 | 30940 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9640 | 5.8 | 33.8 | 87.8 | 534 | 9640 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9317 | 1.8 | 13.8 | 54.0 | 332 | 9317 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 929 | 19.9 | 76.1 | 125.6 | 1,897 | 929 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 323 | 18.1 | 82.2 | 152.8 | 235 | 323 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 323 | 2.5 | 16.2 | 55.7 | 121 | 323 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3811 | 15.5 | 348.5 | 1,980.8 | 3,417 | 3811 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 40530 | 26.3 | 1,930.3 | 3,010.7 | 4,707 | 40530 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 44341 | 20.6 | 2,060.0 | 3,046.3 | 4,415 | 44341 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13603 | 27.4 | 1,875.4 | 3,053.3 | 4,274 | 13603 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13163 | 10.9 | 1,261.1 | 2,830.7 | 3,868 | 13163 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1319 | 64.5 | 1,665.5 | 3,120.4 | 3,595 | 1319 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 440 | 58.0 | 1,678.5 | 2,828.8 | 3,754 | 440 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 440 | 15.6 | 1,291.9 | 2,961.4 | 3,152 | 440 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6843 | 10.5 | 3,242.0 | 60,061.9 | 61,476 | 3761 | 0 | 0 | 0 | 0 | 0 | 3082 |
| 4000 | preview | 28226 | 2,727.7 | 4,213.9 | 4,801.2 | 6,745 | 28226 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 31987 | 2,595.1 | 4,034.9 | 4,760.5 | 6,130 | 31987 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 9908 | 2,579.7 | 4,031.2 | 4,770.2 | 6,259 | 9908 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 9556 | 2,449.2 | 3,859.7 | 4,519.3 | 5,985 | 9556 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 958 | 2,702.9 | 4,212.1 | 5,019.1 | 6,169 | 958 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 352 | 2,574.7 | 3,991.9 | 4,463.9 | 4,896 | 352 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 352 | 2,453.9 | 3,807.3 | 4,385.9 | 4,986 | 352 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 107.7 | 0.00 % (0) | none | 0.31 (0.90) | 0.17 (0.24) | 0.07 (0.33) | 0.15 (0.91) | 0.00 (0.03) | 1,795 | 3 / 0 | 2 / 0 | 13 / 0.7 | 5,000-5,000 | 79 / 0 | 0 |
| 1000 | 215.5 | 0.00 % (0) | none | 0.62 (1.24) | 0.37 (0.54) | 0.21 (1.09) | 0.22 (0.37) | 0.01 (0.03) | 1,810 | 4 / 0 | 2 / 0 | 34 / 1.1 | 5,000-5,000 | 161 / 0 | 0 |
| 1500 | 321.7 | 0.00 % (0) | none | 0.94 (1.60) | 0.61 (1.01) | 0.26 (0.43) | 0.28 (0.54) | 0.01 (0.02) | 1,835 | 6 / 0 | 3 / 0 | 37 / 1.0 | 5,000-5,000 | 235 / 0 | 0 |
| 2000 | 430.1 | 0.00 % (0) | none | 1.37 (2.53) | 1.04 (1.94) | 0.46 (1.29) | 0.39 (1.19) | 0.01 (0.03) | 1,876 | 10 / 25 | 5 / 7 | 71 / 1.2 | 5,000-5,000 | 323 / 0 | 0 |
| 3000 | 598.6 | 0.00 % (0) | http_req_duration{name:send} | 2.76 (3.69) | 2.10 (3.13) | 1.12 (1.94) | 0.77 (1.42) | 0.02 (0.10) | 1,857 | 10 / 189 | 5 / 72 | 81 / 1.5 | 5,000-5,000 | 440 / 0 | 0 |
| 4000 | 419.8 | 3.50 % (3,082) | http_req_duration{name:send}, http_req_duration{name:list} | 2.77 (4.08) | 1.93 (3.17) | 1.78 (3.85) | 0.70 (1.95) | 0.03 (0.10) | 1,911 | 10 / 189 | 5 / 4 | 57 / 1.2 | 5,000-5,000 | 352 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,597, average 10.9 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 291,247
- Got live: 291,247; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 961, p95 2,646, p99 3,894, max 4,562
  - level 500 (committed in its hold, 10,915): p50 617, p95 1,016, p99 1,047, max 1,057
  - level 1000 (committed in its hold, 24,160): p50 723, p95 1,107, p99 1,130, max 1,274
  - level 1500 (committed in its hold, 33,533): p50 695, p95 1,124, p99 1,362, max 1,537
  - level 2000 (committed in its hold, 47,465): p50 820, p95 1,309, p99 1,390, max 1,455
  - level 3000 (committed in its hold, 65,531): p50 1,238, p95 2,136, p99 2,596, max 3,050
  - level 4000 (committed in its hold, 44,278): p50 1,968, p95 3,935, p99 4,408, max 4,562
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
