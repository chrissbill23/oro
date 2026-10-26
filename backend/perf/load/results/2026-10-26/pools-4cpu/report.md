# Load-test run `pools-4cpu`

```
run: pools-4cpu, 2026-10-26T10:44:58Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav, ORO_DB_POOL_SIZE=20 ORO_WORKER_DB_POOL_SIZE=10
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 2000 3000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 2000 ended on battery power.**

**NOT COMPARABLE: level 3000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2571 | 12.9 | 51.7 | 84.1 | 165 | 2571 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28382 | 2.9 | 16.2 | 44.7 | 160 | 28382 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30953 | 2.9 | 14.5 | 35.1 | 149 | 30953 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9431 | 5.6 | 24.0 | 48.2 | 155 | 9431 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9114 | 1.7 | 8.8 | 22.5 | 131 | 9114 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 930 | 19.6 | 71.7 | 130.5 | 398 | 930 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 317 | 14.2 | 56.7 | 84.4 | 128 | 317 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 317 | 2.4 | 9.4 | 20.3 | 28 | 317 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3849 | 11.9 | 140.7 | 450.1 | 703 | 3849 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 42320 | 7.3 | 166.2 | 404.8 | 1,078 | 42320 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 46169 | 7.1 | 149.8 | 390.9 | 1,078 | 46169 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 14317 | 11.7 | 152.8 | 353.3 | 949 | 14317 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13849 | 3.8 | 80.5 | 196.0 | 679 | 13849 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1386 | 34.0 | 257.5 | 520.7 | 6,205 | 1386 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 468 | 30.0 | 201.0 | 369.9 | 636 | 468 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 468 | 5.5 | 80.4 | 189.8 | 512 | 468 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 427.2 | 0.00 % (0) | none | 1.36 (2.11) | 0.91 (1.52) | 0.34 (0.64) | 0.34 (0.85) | 0.01 (0.03) | 1,753 | 5 / 0 | 2 / 0 | 69 / 1.1 | 5,000-5,000 | 317 / 0 | 0 |
| 3000 | 639.6 | 0.00 % (0) | none | 2.45 (4.04) | 1.86 (3.01) | 1.33 (4.00) | 0.58 (1.07) | 0.02 (0.05) | 1,822 | 20 / 156 | 10 / 6 | 38 / 1.6 | 5,000-5,000 | 468 / 0 | 0 |

Scans (Oro's timer, since its last start): 796, average 6.5 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 150,375
- Got live: 150,375; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 868, p95 1,500, p99 2,214, max 2,498
  - level 2000 (committed in its hold, 46,091): p50 830, p95 1,226, p99 1,345, max 1,500
  - level 3000 (committed in its hold, 68,580): p50 960, p95 1,938, p99 2,275, max 2,465
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
