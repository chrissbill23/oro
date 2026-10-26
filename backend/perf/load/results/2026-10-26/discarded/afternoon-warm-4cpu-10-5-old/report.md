# Load-test run `warm-4cpu-10-5-old`

```
run: warm-4cpu-10-5-old, 2026-10-26T13:33:55Z
git: 5544088
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: AC Power, low power mode 0
workload: levels 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2503 | 221.9 | 2,726.6 | 3,326.1 | 5,581 | 2503 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 25502 | 43.3 | 2,474.4 | 3,259.7 | 5,357 | 25502 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 28005 | 36.7 | 2,492.5 | 3,321.8 | 5,696 | 28005 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 8500 | 44.9 | 2,393.3 | 3,192.1 | 4,923 | 8500 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 8233 | 17.9 | 1,977.3 | 2,694.3 | 3,922 | 8233 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 848 | 98.5 | 2,257.7 | 3,033.5 | 4,051 | 848 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 267 | 81.0 | 2,104.2 | 2,782.3 | 3,533 | 267 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 267 | 22.2 | 1,669.9 | 2,568.6 | 2,925 | 267 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3350 | 9,991.1 | 19,709.0 | 20,663.9 | 23,047 | 3341 | 0 | 0 | 0 | 9 | 0 | 0 |
| 3000 | preview | 14810 | 5,196.9 | 15,435.0 | 19,738.0 | 23,763 | 14772 | 0 | 0 | 0 | 38 | 0 | 0 |
| 3000 | after | 18151 | 4,629.4 | 15,867.5 | 19,687.5 | 23,265 | 18110 | 0 | 0 | 0 | 41 | 0 | 0 |
| 3000 | send | 5531 | 4,086.4 | 16,116.8 | 19,757.7 | 24,008 | 5524 | 0 | 0 | 0 | 7 | 0 | 0 |
| 3000 | read | 5366 | 3,358.3 | 13,789.0 | 18,902.9 | 21,682 | 5359 | 0 | 0 | 0 | 7 | 0 | 0 |
| 3000 | search | 581 | 4,086.9 | 15,477.7 | 19,833.1 | 21,199 | 579 | 0 | 0 | 0 | 2 | 0 | 0 |
| 3000 | upload | 158 | 4,064.6 | 14,226.6 | 19,455.4 | 20,617 | 158 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 158 | 2,702.7 | 13,737.9 | 15,124.2 | 15,423 | 158 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 7041 | 21.0 | 1,701.1 | 59,997.6 | 60,060 | 3971 | 0 | 0 | 0 | 0 | 0 | 3070 |
| 4000 | preview | 38710 | 814.6 | 2,166.8 | 3,002.5 | 4,304 | 38709 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | after | 42681 | 728.2 | 2,048.8 | 2,879.7 | 4,665 | 42679 | 0 | 0 | 0 | 0 | 0 | 2 |
| 4000 | send | 13106 | 697.2 | 2,009.6 | 2,868.8 | 3,682 | 13105 | 0 | 0 | 0 | 0 | 0 | 1 |
| 4000 | read | 12687 | 544.2 | 1,830.7 | 2,595.0 | 3,271 | 12687 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1201 | 756.4 | 2,104.4 | 2,808.0 | 3,380 | 1201 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 418 | 672.4 | 1,826.4 | 2,602.8 | 3,133 | 418 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 418 | 537.0 | 1,731.4 | 2,684.6 | 3,053 | 418 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 378.9 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 2.38 (3.85) | 1.59 (2.33) | 0.70 (1.27) | 0.51 (1.24) | 0.01 (0.03) | 1,792 | 10 / 188 | 5 / 38 | 81 / 2.6 | 4,999-4,999 | 267 / 0 | 0 |
| 3000 | 245.1 | 0.22 % (104) | http_req_duration{name:send}, http_req_duration{name:list} | 2.24 (3.48) | 1.66 (3.20) | 1.31 (2.63) | 0.59 (1.14) | 0.01 (0.03) | 1,920 | 10 / 190 | 5 / 19 | 131 / 3.7 | 4,999-4,999 | 158 / 0 | 0 |
| 4000 | 553.5 | 2.64 % (3,074) | http_req_duration{name:list}, http_req_duration{name:send}, http_req_failed | 2.50 (3.72) | 2.01 (2.92) | 1.26 (2.33) | 0.74 (1.56) | 0.02 (0.05) | 1,920 | 10 / 189 | 5 / 112 | 155 / 1.4 | 4,999-4,999 | 418 / 0 | 0 |

Scans (Oro's timer, since its last start): 853, average 18.4 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 165,171
- Got live: 165,171; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,591, p95 4,715, p99 11,833, max 21,213
  - level 2000 (committed in its hold, 43,866): p50 1,363, p95 3,879, p99 4,378, max 4,748
  - level 3000 (committed in its hold, 29,196): p50 2,720, p95 15,301, p99 20,870, max 21,213
  - level 4000 (committed in its hold, 59,481): p50 1,612, p95 2,845, p99 3,544, max 3,796
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
