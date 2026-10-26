# Load-test run `restart-fix-2cpu`

```
run: restart-fix-2cpu, 2026-10-26T11:16:17Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
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
| 1500 | list | 1795 | 12.1 | 153.7 | 25,300.1 | 35,152 | 1768 | 0 | 0 | 0 | 0 | 0 | 27 |
| 1500 | preview | 14340 | 4.0 | 33,513.4 | 35,031.8 | 35,212 | 13198 | 0 | 0 | 0 | 0 | 0 | 1142 |
| 1500 | after | 16135 | 3.2 | 8,413.9 | 27,298.1 | 35,213 | 14956 | 0 | 0 | 0 | 0 | 0 | 1179 |
| 1500 | send | 4846 | 6.4 | 4,572.0 | 9,094.1 | 35,189 | 4513 | 0 | 0 | 0 | 0 | 0 | 333 |
| 1500 | read | 4370 | 2.2 | 2,709.4 | 8,059.8 | 33,600 | 4370 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 477 | 19.8 | 3,195.7 | 8,634.8 | 34,594 | 439 | 0 | 0 | 0 | 0 | 0 | 38 |
| 1500 | upload | 153 | 18.7 | 6,134.4 | 17,320.6 | 40,191 | 143 | 0 | 0 | 0 | 0 | 0 | 10 |
| 1500 | download | 143 | 3.5 | 3,890.7 | 7,004.2 | 7,080 | 143 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1500 | 217.7 | 6.46 % (2,729) | http_req_duration{name:send}, http_req_failed | 1.60 (2.13) | 0.54 (1.47) | 0.25 (1.16) | 0.51 (1.47) | 0.01 (0.02) | 1,721 | 10 / 176 | 5 / 140 | 69 / 2.4 | 0-5,000 | 143 / 0 | 0 |

Scans (Oro's timer, since its last start): 50, average 35.1 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 29,694
- Got live: 28,887; caught up after a reconnection: 807; **missing: 0**
- Duplicates: 0 live events received twice; 1,373 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 855, p95 10,174, p99 15,393, max 62,173
  - level 1500 (committed in its hold, 18,707): p50 1,035, p95 10,293, p99 16,501, max 62,173
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 707 (reconnections: 407); failures seen by the clients: {'ConnectException': 598, 'IOException': 2, 'HttpTimeoutException': 107}

## Restart under load

- Stopped and started again in 3.4 s; ready 12.6 s after the stop was asked
- Tracked streams ended by the restart: 300; back (a hello): 300; caught up: 300
- Hello after readiness (ms): p50 12,356, p95 34,704, max 53,701; caught up after the stop was asked (ms): p50 64,043, p95 82,947, max 83,144
- Outbox in the minute after readiness: oldest event at most 2.4 s, at most 69 pending; under 3 s 5.1 s after readiness (first sample)
- Requests that failed from 5 s before the stop to 60 s after readiness: {'after 0 network': 1179, 'preview 0 network': 1142, 'send 0 network': 333, 'search 0 network': 38, 'list 0 network': 27, 'upload 0 network': 10}
