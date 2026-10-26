# Load-test run `restart-fix-4cpu`

```
run: restart-fix-4cpu, 2026-10-26T11:24:46Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: AC Power, low power mode 0
workload: levels 1500 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: 1500 at 100 s; profile: none
```

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | list | 1933 | 12.6 | 72.1 | 8,993.8 | 10,976 | 1851 | 0 | 0 | 0 | 0 | 0 | 82 |
| 1500 | preview | 19345 | 2.6 | 8,042.5 | 10,559.7 | 11,010 | 15332 | 0 | 0 | 0 | 0 | 0 | 4013 |
| 1500 | after | 21278 | 2.5 | 2,684.3 | 3,559.0 | 10,959 | 17182 | 0 | 0 | 0 | 0 | 0 | 4096 |
| 1500 | send | 6418 | 5.2 | 1,500.8 | 2,778.2 | 10,857 | 5206 | 0 | 0 | 0 | 0 | 0 | 1212 |
| 1500 | read | 5020 | 1.9 | 1,022.6 | 2,126.7 | 10,860 | 5020 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 607 | 17.2 | 1,225.0 | 2,489.4 | 10,021 | 502 | 0 | 0 | 0 | 0 | 0 | 105 |
| 1500 | upload | 228 | 11.5 | 1,644.9 | 2,665.1 | 10,931 | 186 | 0 | 0 | 0 | 0 | 0 | 42 |
| 1500 | download | 186 | 2.5 | 1,049.6 | 1,899.9 | 2,751 | 186 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 287.0 | 17.36 % (9,550) | http_req_failed, http_req_duration{name:send} | 1.43 (4.11) | 0.50 (2.41) | 0.24 (0.63) | 0.57 (2.60) | 0.01 (0.04) | 1,796 | 10 / 184 | 5 / 0 | 30 / 15.3 | 0-5,000 | 186 / 0 | 0 |

Scans (Oro's timer, since its last start): 65, average 9.1 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 28,046
- Got live: 23,533; caught up after a reconnection: 4,513; **missing: 0**
- Duplicates: 0 live events received twice; 1,067 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 831, p95 2,310, p99 3,165, max 21,548
  - level 1500 (committed in its hold, 14,435): p50 916, p95 2,720, p99 4,498, max 21,548
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 600 (reconnections: 300); failures seen by the clients: {'ConnectException': 1192, 'IOException': 8}

## Restart under load

- Stopped and started again in 23.4 s; ready 29.9 s after the stop was asked
- Tracked streams ended by the restart: 300; back (a hello): 300; caught up: 300
- Hello after readiness (ms): p50 23,278, p95 32,242, max 40,535; caught up after the stop was asked (ms): p50 69,874, p95 70,172, max 70,460
- Outbox in the minute after readiness: oldest event at most 0.7 s, at most 19 pending; under 3 s 6.3 s after readiness (first sample)
- Requests that failed from 5 s before the stop to 60 s after readiness: {'after 0 network': 4096, 'preview 0 network': 4013, 'send 0 network': 1212, 'search 0 network': 105, 'list 0 network': 82, 'upload 0 network': 42}
