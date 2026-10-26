# Load-test run `uploads-clamav`

```
run: uploads-clamav, 2026-10-26T10:57:09Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 1500 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.1 x 200 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 1500 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | list | 1938 | 13.3 | 108.0 | 234.7 | 508 | 1938 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21187 | 3.9 | 77.1 | 201.6 | 968 | 21187 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23125 | 3.8 | 80.1 | 189.9 | 1,024 | 23125 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 8596 | 6.8 | 93.1 | 197.3 | 894 | 8596 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6252 | 2.0 | 63.3 | 109.6 | 523 | 6252 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 751 | 22.5 | 133.0 | 319.7 | 1,583 | 751 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 2344 | 13.1 | 173.7 | 306.6 | 1,069 | 2344 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 2344 | 3.1 | 73.0 | 186.1 | 675 | 2344 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 347.0 | 0.00 % (0) | none | 1.09 (2.04) | 0.83 (1.43) | 0.41 (1.56) | 0.44 (0.98) | 0.04 (0.09) | 1,779 | 10 / 13 | 5 / 11 | 139 / 2.5 | 5,000-5,000 | 2,344 / 0 | 0 |

Scans (Oro's timer, since its last start): 2,426, average 5.7 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 45,892
- Got live: 45,892; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 923, p95 1,782, p99 2,809, max 3,439
  - level 1500 (committed in its hold, 35,175): p50 972, p95 1,711, p99 2,882, max 3,284
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
