# Load-test run `uploads-none`

```
run: uploads-none, 2026-10-26T11:05:17Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan none
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 1500 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.1 x 200 KB (download check 1)
restart: none; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | list | 1918 | 16.4 | 136.1 | 213.1 | 345 | 1918 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21173 | 5.5 | 95.2 | 193.1 | 525 | 21173 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23091 | 5.0 | 93.2 | 184.6 | 516 | 23091 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 8572 | 8.4 | 101.3 | 189.8 | 478 | 8572 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6180 | 2.6 | 73.3 | 110.3 | 359 | 6180 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 722 | 27.7 | 160.9 | 194.8 | 2,174 | 722 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 2392 | 13.5 | 174.3 | 269.1 | 545 | 2392 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 2392 | 3.9 | 82.7 | 119.0 | 282 | 2392 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 346.3 | 0.00 % (0) | none | - | - | - | - | - | - | 10 / 12 | 5 / 13 | 105 / 1.8 | 5,000-5,000 | 2,392 / 0 | 0 |

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 58,132
- Got live: 58,132; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,061, p95 2,103, p99 2,720, max 3,183
  - level 1500 (committed in its hold, 44,242): p50 1,109, p95 2,173, p99 3,028, max 3,183
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
