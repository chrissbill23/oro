# Load-test run `cmp-2cpu-a`

```
run: cmp-2cpu-a, 2026-10-26T00:38:59Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 639 | 17.6 | 46.4 | 103.6 | 252 | 639 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7092 | 2.9 | 12.6 | 52.0 | 167 | 7092 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7731 | 2.7 | 10.5 | 34.3 | 189 | 7731 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2352 | 6.0 | 21.6 | 65.8 | 154 | 2352 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2270 | 1.7 | 6.1 | 11.2 | 131 | 2270 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 239 | 20.1 | 72.0 | 134.3 | 310 | 239 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 82 | 21.7 | 54.0 | 132.1 | 178 | 82 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 82 | 2.6 | 7.3 | 20.6 | 53 | 82 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1285 | 12.7 | 48.3 | 117.3 | 471 | 1285 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14193 | 2.6 | 34.3 | 107.7 | 742 | 14193 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15478 | 2.5 | 18.9 | 94.9 | 649 | 15478 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4744 | 5.2 | 37.7 | 108.1 | 470 | 4744 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4584 | 1.5 | 10.1 | 58.7 | 300 | 4584 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 453 | 18.9 | 95.5 | 217.8 | 369 | 453 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 160 | 17.2 | 96.6 | 160.0 | 394 | 160 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 160 | 2.3 | 12.1 | 41.0 | 59 | 160 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1907 | 12.9 | 188.3 | 474.9 | 791 | 1907 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21078 | 5.1 | 237.3 | 592.3 | 1,429 | 21078 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 22985 | 4.6 | 199.4 | 588.0 | 1,628 | 22985 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7098 | 8.1 | 207.0 | 511.4 | 1,051 | 7098 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6855 | 2.4 | 113.2 | 394.6 | 1,033 | 6855 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 663 | 28.7 | 263.7 | 591.0 | 861 | 663 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 243 | 24.1 | 312.9 | 798.9 | 1,078 | 243 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 243 | 3.0 | 99.4 | 350.3 | 612 | 243 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2310 | 232.0 | 9,723.9 | 15,498.0 | 21,298 | 2307 | 0 | 0 | 0 | 3 | 0 | 0 |
| 2000 | preview | 14581 | 2,426.2 | 16,348.5 | 20,367.9 | 25,695 | 14466 | 0 | 0 | 0 | 115 | 0 | 0 |
| 2000 | after | 16890 | 1,669.7 | 10,732.4 | 17,946.0 | 24,445 | 16816 | 0 | 0 | 0 | 74 | 0 | 0 |
| 2000 | send | 5098 | 1,405.3 | 9,668.8 | 18,273.2 | 21,883 | 5066 | 0 | 0 | 0 | 32 | 0 | 0 |
| 2000 | read | 4893 | 1,022.3 | 8,771.1 | 18,413.8 | 23,942 | 4877 | 0 | 0 | 0 | 16 | 0 | 0 |
| 2000 | search | 498 | 1,073.5 | 9,482.0 | 18,950.2 | 21,613 | 498 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 176 | 1,673.4 | 9,974.2 | 15,342.2 | 15,455 | 176 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 176 | 978.6 | 8,145.6 | 16,850.7 | 18,156 | 175 | 0 | 0 | 0 | 1 | 0 | 0 |
| 3000 | list | 3301 | 2,384.4 | 9,249.9 | 16,850.3 | 24,184 | 3300 | 0 | 0 | 0 | 1 | 0 | 0 |
| 3000 | preview | 13323 | 8,700.5 | 17,895.0 | 19,641.3 | 23,871 | 13307 | 0 | 0 | 0 | 16 | 0 | 0 |
| 3000 | after | 16624 | 8,312.6 | 16,327.1 | 18,848.3 | 23,877 | 16597 | 0 | 0 | 0 | 27 | 0 | 0 |
| 3000 | send | 4953 | 8,021.2 | 16,383.5 | 18,795.9 | 22,378 | 4945 | 0 | 0 | 0 | 8 | 0 | 0 |
| 3000 | read | 4781 | 7,792.9 | 16,174.5 | 18,400.0 | 20,693 | 4781 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 511 | 8,347.9 | 16,567.2 | 18,649.0 | 21,216 | 510 | 0 | 0 | 0 | 1 | 0 | 0 |
| 3000 | upload | 164 | 8,447.2 | 15,948.9 | 19,260.4 | 19,583 | 164 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 164 | 7,620.7 | 15,269.3 | 17,789.7 | 18,871 | 164 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6416 | 56.1 | 16,973.2 | 59,931.4 | 60,019 | 3525 | 0 | 0 | 0 | 6 | 0 | 2885 |
| 4000 | preview | 15187 | 7,155.8 | 14,727.2 | 17,083.5 | 38,930 | 15172 | 0 | 0 | 0 | 3 | 0 | 12 |
| 4000 | after | 18708 | 6,834.2 | 15,199.9 | 17,653.9 | 60,150 | 18683 | 0 | 0 | 0 | 14 | 0 | 11 |
| 4000 | send | 5623 | 6,644.4 | 14,587.4 | 17,222.7 | 60,029 | 5616 | 0 | 0 | 0 | 1 | 0 | 6 |
| 4000 | read | 5433 | 5,886.2 | 13,586.5 | 15,472.4 | 18,537 | 5433 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 567 | 6,403.4 | 14,350.8 | 17,307.1 | 19,017 | 567 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 184 | 6,757.3 | 14,570.2 | 17,148.3 | 17,923 | 183 | 0 | 0 | 0 | 1 | 0 | 0 |
| 4000 | download | 183 | 6,088.7 | 12,802.2 | 13,556.0 | 15,525 | 183 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 106.8 | 0.00 % (0) | none | 0.36 (1.06) | 0.23 (0.51) | 0.09 (0.26) | 0.18 (0.89) | 0.00 (0.01) | 1,692 | 3 / 0 | 2 / 0 | 22 / 1.1 | 5,000-5,000 | 82 / 0 | 0 |
| 1000 | 214.1 | 0.00 % (0) | none | 0.81 (1.80) | 0.59 (1.35) | 0.24 (0.55) | 0.39 (3.19) | 0.01 (0.03) | 1,713 | 3 / 0 | 4 / 0 | 31 / 1.1 | 5,000-5,000 | 160 / 0 | 0 |
| 1500 | 318.5 | 0.00 % (0) | none | 1.23 (2.58) | 0.91 (3.21) | 0.57 (2.32) | 0.32 (0.69) | 0.01 (0.08) | 1,712 | 10 / 25 | 5 / 27 | 85 / 1.7 | 5,000-5,000 | 243 / 0 | 0 |
| 2000 | 225.8 | 0.54 % (241) | http_req_duration{name:list}, http_req_duration{name:send} | 2.07 (3.60) | 1.24 (2.52) | 1.26 (4.01) | 0.58 (1.51) | 0.01 (0.04) | 1,842 | 10 / 190 | 5 / 10 | 33 / 3.4 | 5,000-5,000 | 176 / 0 | 0 |
| 3000 | 208.4 | 0.12 % (53) | http_req_duration{name:list}, http_req_duration{name:send} | 2.22 (2.68) | 1.46 (2.69) | 1.76 (4.35) | 0.75 (1.97) | 0.02 (0.13) | 1,911 | 10 / 190 | 5 / 19 | 71 / 3.9 | 5,000-5,000 | 164 / 0 | 0 |
| 4000 | 248.9 | 5.62 % (2,939) | http_req_duration{name:send}, http_req_duration{name:list} | 1.98 (2.88) | 1.54 (3.43) | 1.82 (5.23) | 0.71 (3.64) | 0.03 (0.21) | 1,988 | 10 / 189 | 5 / 15 | 117 / 1.4 | 5,000-5,000 | 183 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,020, average 28.1 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 183,715
- Got live: 183,715; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,619, p95 43,015, p99 46,818, max 48,168
  - level 500 (committed in its hold, 11,593): p50 573, p95 1,017, p99 1,086, max 1,118
  - level 1000 (committed in its hold, 24,076): p50 707, p95 1,181, p99 1,530, max 1,701
  - level 1500 (committed in its hold, 36,610): p50 1,086, p95 3,083, p99 4,266, max 4,298
  - level 2000 (committed in its hold, 23,725): p50 11,794, p95 44,775, p99 45,639, max 45,674
  - level 3000 (committed in its hold, 21,771): p50 16,669, p95 33,813, p99 35,172, max 35,301
  - level 4000 (committed in its hold, 27,681): p50 25,724, p95 47,578, p99 48,110, max 48,168
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
