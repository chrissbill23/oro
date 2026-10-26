# Load-test run `profile-2cpu`

```
run: profile-2cpu, 2026-10-26T10:27:24Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75 -XX:StartFlightRecording=settings=profile,delay=205s,duration=60s,filename=/tmp/oro.jfr, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 2000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: 2000 (60 s)
```

**NOT COMPARABLE: level 2000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2556 | 18.1 | 717.7 | 1,485.0 | 3,019 | 2556 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 25815 | 113.9 | 1,639.8 | 2,240.8 | 3,787 | 25815 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 28371 | 89.2 | 1,507.4 | 2,162.4 | 3,614 | 28371 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 8797 | 99.9 | 1,521.7 | 2,163.4 | 3,816 | 8797 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 8513 | 67.9 | 1,310.6 | 1,867.3 | 2,961 | 8513 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 789 | 164.5 | 1,733.8 | 2,417.5 | 3,082 | 789 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 284 | 182.8 | 1,480.6 | 1,974.6 | 2,175 | 284 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 284 | 74.5 | 1,284.8 | 1,997.4 | 2,399 | 284 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 392.9 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 1.72 (2.09) | 1.15 (1.55) | 0.59 (2.29) | 0.42 (1.21) | 0.01 (0.07) | 1,782 | 10 / 188 | 5 / 42 | 110 / 1.5 | 5,000-5,000 | 284 / 0 | 0 |

Scans (Oro's timer, since its last start): 291, average 18.4 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 54,646
- Got live: 54,646; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 5,002, p95 16,497, p99 18,406, max 18,692
  - level 2000 (committed in its hold, 38,848): p50 7,385, p95 17,128, p99 18,415, max 18,692
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
