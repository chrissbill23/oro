# Load-test run `cmp-4cpu-a`

```
run: cmp-4cpu-a, 2026-10-26T09:14:36Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: AC Power, low power mode 0
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 500 ended on battery power.**

**NOT COMPARABLE: level 1000 ended on battery power.**

**NOT COMPARABLE: level 1500 ended on battery power.**

**NOT COMPARABLE: level 2000 ended on battery power.**

**NOT COMPARABLE: level 3000 ended on battery power.**

**NOT COMPARABLE: level 4000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 637 | 13.8 | 29.2 | 48.5 | 72 | 637 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7096 | 2.4 | 6.7 | 13.4 | 58 | 7096 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7733 | 2.4 | 6.3 | 12.0 | 58 | 7733 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2386 | 5.1 | 12.4 | 23.9 | 81 | 2386 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2310 | 1.4 | 3.7 | 6.8 | 55 | 2310 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 218 | 19.5 | 48.8 | 91.0 | 138 | 218 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 76 | 14.0 | 26.2 | 42.3 | 43 | 76 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 76 | 2.1 | 4.2 | 6.4 | 7 | 76 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1284 | 11.1 | 45.2 | 130.5 | 233 | 1284 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14201 | 2.1 | 7.4 | 18.4 | 143 | 14201 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15485 | 2.1 | 6.8 | 13.8 | 140 | 15485 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4710 | 4.3 | 12.3 | 32.9 | 126 | 4710 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4569 | 1.3 | 3.8 | 8.3 | 114 | 4569 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 456 | 14.9 | 39.4 | 90.6 | 2,156 | 456 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 141 | 10.3 | 29.8 | 58.3 | 144 | 141 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 141 | 1.9 | 4.2 | 6.2 | 7 | 141 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1983 | 9.6 | 36.4 | 63.5 | 149 | 1983 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21236 | 2.4 | 13.3 | 33.8 | 141 | 21236 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23219 | 2.5 | 11.7 | 27.2 | 122 | 23219 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7158 | 4.7 | 18.5 | 36.0 | 124 | 7158 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6928 | 1.4 | 6.5 | 15.6 | 181 | 6928 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 691 | 15.9 | 49.6 | 77.4 | 154 | 691 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 230 | 12.8 | 44.8 | 93.1 | 125 | 230 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 230 | 2.2 | 8.5 | 25.8 | 40 | 230 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2611 | 10.1 | 51.3 | 111.3 | 286 | 2611 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28325 | 3.1 | 25.3 | 87.1 | 370 | 28325 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30936 | 3.1 | 19.6 | 63.6 | 324 | 30936 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9517 | 5.5 | 27.4 | 67.6 | 281 | 9517 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9201 | 1.7 | 11.2 | 33.6 | 275 | 9201 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 945 | 17.4 | 67.4 | 117.4 | 301 | 945 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 316 | 14.6 | 72.7 | 137.7 | 195 | 316 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 316 | 2.6 | 17.1 | 55.3 | 145 | 316 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3870 | 15.5 | 273.0 | 557.5 | 1,202 | 3870 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 41915 | 13.2 | 397.5 | 714.4 | 1,713 | 41915 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 45785 | 11.8 | 361.3 | 666.0 | 1,502 | 45785 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13899 | 17.1 | 328.6 | 635.0 | 1,615 | 13899 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13440 | 6.3 | 217.1 | 436.0 | 1,166 | 13440 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1371 | 45.8 | 406.6 | 717.9 | 5,447 | 1371 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 459 | 40.1 | 299.2 | 463.0 | 639 | 459 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 459 | 7.5 | 255.6 | 449.7 | 567 | 459 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6946 | 7.4 | 1,102.4 | 59,997.2 | 60,157 | 4035 | 0 | 0 | 0 | 0 | 0 | 2911 |
| 4000 | preview | 40976 | 344.3 | 1,793.0 | 2,484.2 | 3,837 | 40974 | 0 | 0 | 0 | 0 | 0 | 2 |
| 4000 | after | 45010 | 238.0 | 1,731.7 | 2,379.2 | 3,660 | 45007 | 0 | 0 | 0 | 0 | 0 | 3 |
| 4000 | send | 13893 | 236.0 | 1,722.0 | 2,342.7 | 3,652 | 13893 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 13457 | 148.7 | 1,506.8 | 2,101.8 | 2,869 | 13457 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1311 | 241.9 | 1,733.3 | 2,276.3 | 3,123 | 1311 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 436 | 204.6 | 1,545.4 | 1,896.4 | 2,532 | 436 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 436 | 122.2 | 1,454.0 | 2,115.0 | 2,793 | 436 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 106.9 | 0.00 % (0) | none | 0.35 (0.96) | 0.18 (0.28) | 0.07 (0.12) | 0.11 (0.27) | 0.00 (0.01) | 1,763 | 2 / 0 | 2 / 0 | 22 / 0.9 | 5,000-5,000 | 76 / 0 | 0 |
| 1000 | 213.7 | 0.00 % (0) | none | 0.46 (0.85) | 0.33 (0.49) | 0.12 (0.19) | 0.15 (0.27) | 0.00 (0.01) | 1,715 | 5 / 0 | 2 / 0 | 37 / 1.0 | 5,000-5,000 | 141 / 0 | 0 |
| 1500 | 321.9 | 0.00 % (0) | none | 0.93 (1.32) | 0.65 (0.99) | 0.25 (0.39) | 0.32 (1.03) | 0.00 (0.03) | 1,744 | 5 / 0 | 3 / 0 | 49 / 1.1 | 5,000-5,000 | 230 / 0 | 0 |
| 2000 | 428.2 | 0.00 % (0) | none | 1.69 (3.11) | 1.12 (2.08) | 0.79 (2.81) | 0.38 (1.00) | 0.01 (0.04) | 1,763 | 7 / 0 | 4 / 0 | 51 / 0.9 | 5,000-5,000 | 316 / 0 | 0 |
| 3000 | 631.9 | 0.00 % (0) | none | 2.50 (3.85) | 1.89 (3.01) | 1.05 (2.84) | 0.66 (1.43) | 0.02 (0.10) | 1,817 | 10 / 188 | 5 / 48 | 109 / 1.0 | 5,000-5,000 | 459 / 0 | 0 |
| 4000 | 582.8 | 2.38 % (2,916) | http_req_duration{name:list}, http_req_duration{name:send}, http_req_failed | 2.67 (4.01) | 2.07 (3.15) | 1.61 (4.93) | 0.59 (1.45) | 0.02 (0.05) | 1,874 | 10 / 190 | 5 / 27 | 151 / 1.4 | 5,000-5,000 | 436 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,667, average 7.8 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 318,683
- Got live: 318,683; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 901, p95 1,800, p99 2,446, max 3,531
  - level 500 (committed in its hold, 10,079): p50 683, p95 1,021, p99 1,086, max 1,103
  - level 1000 (committed in its hold, 21,893): p50 577, p95 1,045, p99 1,076, max 1,090
  - level 1500 (committed in its hold, 34,727): p50 644, p95 1,171, p99 1,225, max 1,308
  - level 2000 (committed in its hold, 51,462): p50 849, p95 1,285, p99 1,375, max 1,672
  - level 3000 (committed in its hold, 64,947): p50 1,038, p95 1,815, p99 2,299, max 2,719
  - level 4000 (committed in its hold, 64,613): p50 1,249, p95 2,028, p99 2,560, max 3,114
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
