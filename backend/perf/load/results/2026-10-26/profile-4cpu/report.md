# Load-test run `profile-4cpu`

```
run: profile-4cpu, 2026-10-26T10:36:11Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75 -XX:StartFlightRecording=settings=profile,delay=205s,duration=60s,filename=/tmp/oro.jfr, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 3000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: 3000 (60 s)
```

**NOT COMPARABLE: level 3000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | list | 3825 | 22.6 | 1,286.7 | 1,787.3 | 2,551 | 3825 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 40694 | 16.7 | 1,228.7 | 1,838.7 | 3,213 | 40694 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 44519 | 14.7 | 1,199.1 | 1,876.4 | 3,224 | 44519 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13680 | 22.1 | 997.1 | 1,845.7 | 3,189 | 13680 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 13241 | 8.0 | 655.9 | 1,566.1 | 2,202 | 13241 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1365 | 56.4 | 910.4 | 1,776.6 | 7,077 | 1365 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 439 | 50.2 | 1,045.5 | 1,601.3 | 1,772 | 439 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 439 | 10.2 | 664.6 | 1,641.8 | 2,031 | 439 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3000 | 615.7 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 2.70 (4.05) | 1.84 (3.05) | 0.99 (3.06) | 0.51 (1.00) | 0.02 (0.10) | 1,848 | 10 / 185 | 5 / 12 | 126 / 1.7 | 5,000-5,000 | 439 / 0 | 0 |

Scans (Oro's timer, since its last start): 445, average 9.8 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 81,419
- Got live: 81,419; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,147, p95 1,800, p99 2,099, max 2,410
  - level 3000 (committed in its hold, 63,633): p50 1,226, p95 1,815, p99 2,103, max 2,410
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
