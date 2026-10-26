# Load-test run `cmp-2cpu-a`

```
run: cmp-2cpu-a, 2026-10-26T07:45:56Z
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
| 500 | list | 637 | 13.3 | 25.7 | 47.5 | 72 | 637 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7099 | 2.4 | 6.7 | 17.8 | 69 | 7099 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7736 | 2.3 | 6.0 | 10.8 | 73 | 7736 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2347 | 5.0 | 11.1 | 23.8 | 74 | 2347 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2263 | 1.4 | 3.5 | 6.8 | 64 | 2263 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 236 | 18.2 | 43.4 | 91.8 | 155 | 236 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 84 | 12.9 | 30.0 | 46.1 | 84 | 84 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 84 | 1.9 | 3.9 | 4.5 | 6 | 84 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1276 | 11.2 | 44.4 | 139.9 | 584 | 1276 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14168 | 2.2 | 44.2 | 253.2 | 1,002 | 14168 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15444 | 2.3 | 20.2 | 202.7 | 817 | 15444 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4772 | 4.5 | 43.3 | 217.7 | 688 | 4772 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4635 | 1.3 | 11.7 | 149.9 | 518 | 4635 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 498 | 16.9 | 101.5 | 484.6 | 644 | 498 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 137 | 15.5 | 164.0 | 493.2 | 682 | 137 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 137 | 2.0 | 37.1 | 161.7 | 201 | 137 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1915 | 10.6 | 58.7 | 95.1 | 154 | 1915 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21297 | 2.5 | 37.9 | 67.4 | 190 | 21297 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23212 | 2.5 | 18.1 | 68.3 | 204 | 23212 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7093 | 4.7 | 43.6 | 85.4 | 257 | 7093 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6864 | 1.4 | 11.2 | 56.1 | 155 | 6864 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 694 | 17.0 | 76.5 | 97.4 | 193 | 694 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 229 | 15.1 | 105.7 | 186.2 | 472 | 229 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 229 | 2.3 | 11.9 | 55.4 | 90 | 229 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2571 | 11.1 | 132.3 | 356.7 | 1,061 | 2571 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28183 | 10.2 | 330.5 | 825.8 | 2,437 | 28183 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30754 | 6.8 | 303.4 | 815.4 | 2,285 | 30754 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9388 | 11.0 | 301.4 | 790.7 | 1,787 | 9388 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9092 | 3.4 | 197.6 | 575.4 | 1,619 | 9092 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 900 | 32.6 | 211.2 | 589.3 | 1,300 | 900 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 296 | 32.4 | 397.6 | 711.8 | 1,061 | 296 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 296 | 4.8 | 176.9 | 579.9 | 619 | 296 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3610 | 106.0 | 5,253.8 | 5,885.2 | 6,675 | 3610 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 27274 | 2,316.0 | 4,499.8 | 5,855.3 | 7,675 | 27274 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 30884 | 2,003.9 | 4,108.6 | 5,581.4 | 7,381 | 30884 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 9355 | 2,000.7 | 3,803.3 | 5,261.0 | 7,041 | 9355 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 9069 | 1,853.9 | 3,430.2 | 4,763.3 | 6,658 | 9069 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 913 | 2,071.7 | 3,748.6 | 5,071.0 | 6,355 | 913 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 286 | 2,175.8 | 3,872.8 | 5,409.1 | 5,805 | 286 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 286 | 1,983.7 | 3,542.2 | 5,013.0 | 5,563 | 286 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6729 | 8.5 | 4,056.3 | 60,003.2 | 60,019 | 3661 | 0 | 0 | 0 | 0 | 0 | 3068 |
| 4000 | preview | 25783 | 3,688.6 | 5,052.6 | 5,623.5 | 7,439 | 25783 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 29444 | 3,410.0 | 4,978.3 | 5,535.4 | 7,257 | 29444 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 8917 | 3,388.2 | 4,945.5 | 5,536.5 | 7,595 | 8917 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 8652 | 3,182.8 | 4,630.0 | 5,116.8 | 6,702 | 8652 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 975 | 3,378.5 | 4,984.3 | 5,842.7 | 8,247 | 975 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 265 | 3,275.5 | 4,801.3 | 5,264.1 | 5,358 | 265 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 265 | 3,215.3 | 4,614.8 | 5,121.8 | 5,695 | 265 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 106.9 | 0.00 % (0) | none | 0.36 (0.76) | 0.19 (0.30) | 0.11 (0.51) | 0.22 (0.77) | 0.00 (0.01) | 1,647 | 2 / 0 | 1 / 0 | 20 / 0.9 | 5,000-5,000 | 84 / 0 | 0 |
| 1000 | 214.4 | 0.00 % (0) | none | 0.71 (1.72) | 0.49 (1.13) | 0.17 (0.60) | 0.32 (1.48) | 0.01 (0.08) | 1,674 | 8 / 49 | 2 / 0 | 40 / 1.3 | 5,000-5,000 | 137 / 0 | 0 |
| 1500 | 320.7 | 0.00 % (0) | none | 0.89 (1.70) | 0.65 (0.99) | 0.41 (1.94) | 0.37 (1.51) | 0.01 (0.03) | 1,696 | 8 / 0 | 2 / 0 | 50 / 1.1 | 5,000-5,000 | 229 / 0 | 0 |
| 2000 | 422.7 | 0.00 % (0) | none | 1.34 (2.05) | 1.07 (1.73) | 0.44 (0.67) | 0.35 (0.74) | 0.01 (0.02) | 1,725 | 10 / 176 | 5 / 9 | 128 / 1.8 | 5,000-5,000 | 296 / 0 | 0 |
| 3000 | 415.9 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 1.97 (2.11) | 1.50 (2.14) | 1.15 (3.81) | 0.51 (1.17) | 0.01 (0.03) | 1,784 | 10 / 188 | 5 / 9 | 41 / 3.1 | 5,000-5,000 | 286 / 0 | 0 |
| 4000 | 385.7 | 3.79 % (3,068) | http_req_duration{name:list}, http_req_failed, http_req_duration{name:send} | 1.78 (2.26) | 1.35 (2.31) | 1.15 (3.02) | 0.61 (2.54) | 0.01 (0.08) | 1,828 | 10 / 190 | 5 / 32 | 54 / 1.4 | 5,000-5,000 | 265 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,308, average 16.1 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 254,382
- Got live: 254,382; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,229, p95 33,050, p99 37,626, max 38,737
  - level 500 (committed in its hold, 12,199): p50 568, p95 998, p99 1,016, max 1,039
  - level 1000 (committed in its hold, 24,162): p50 666, p95 1,206, p99 2,012, max 2,441
  - level 1500 (committed in its hold, 33,013): p50 765, p95 1,237, p99 1,400, max 1,479
  - level 2000 (committed in its hold, 44,718): p50 1,261, p95 4,009, p99 5,321, max 5,486
  - level 3000 (committed in its hold, 38,832): p50 18,198, p95 36,920, p99 37,879, max 38,609
  - level 4000 (committed in its hold, 41,184): p50 19,994, p95 37,456, p99 38,487, max 38,737
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
