# Load-test run `warm-4cpu-10-5`

```
run: warm-4cpu-10-5, 2026-10-26T12:44:36Z
git: 5544088
image: oro-load:capacity-fix sha256:1aeb35d7e5a00b4b565f956f01f9b12293dd72ec5be462152ebaf27a1b273a73 2026-10-26T11:14:35.89306717Z
oro: --cpus=4 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 2000 ended on battery power.**

**NOT COMPARABLE: level 3000 ended on battery power.**

**NOT COMPARABLE: level 4000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | list | 2574 | 12.9 | 57.1 | 99.1 | 189 | 2574 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 28347 | 3.3 | 28.8 | 79.2 | 306 | 28347 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30921 | 3.3 | 23.7 | 57.8 | 274 | 30921 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9504 | 6.1 | 34.8 | 71.5 | 224 | 9504 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9191 | 1.9 | 14.7 | 41.9 | 243 | 9191 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 917 | 19.4 | 72.8 | 145.2 | 301 | 917 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 313 | 14.2 | 65.3 | 128.7 | 179 | 313 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 313 | 2.7 | 12.3 | 55.4 | 222 | 313 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3770 | 26.4 | 645.3 | 1,786.3 | 3,670 | 3770 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 39437 | 124.8 | 1,852.8 | 2,595.9 | 3,785 | 39437 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 43207 | 88.7 | 1,734.3 | 2,509.4 | 4,079 | 43207 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 13246 | 89.6 | 1,685.4 | 2,606.4 | 3,651 | 13246 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 12787 | 44.7 | 1,537.5 | 2,464.5 | 3,337 | 12787 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 1268 | 167.4 | 1,749.9 | 2,630.6 | 3,497 | 1268 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 459 | 110.5 | 1,550.2 | 1,958.4 | 2,870 | 459 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 459 | 41.6 | 1,474.3 | 2,536.4 | 2,792 | 459 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6841 | 9.4 | 2,359.2 | 60,000.9 | 60,019 | 3920 | 0 | 0 | 0 | 0 | 0 | 2921 |
| 4000 | preview | 37588 | 1,008.7 | 2,379.6 | 3,090.9 | 4,839 | 37588 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 41508 | 920.2 | 2,308.5 | 3,038.4 | 4,797 | 41508 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 12653 | 863.5 | 2,248.6 | 2,947.8 | 4,604 | 12653 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 12215 | 728.9 | 2,035.7 | 2,780.7 | 3,660 | 12215 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 1193 | 905.0 | 2,347.2 | 3,081.4 | 7,659 | 1193 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 437 | 789.1 | 2,250.5 | 2,922.8 | 3,296 | 437 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 437 | 727.6 | 2,059.7 | 2,824.5 | 3,555 | 437 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2000 | 427.6 | 0.00 % (0) | none | 1.36 (2.76) | 0.89 (1.63) | 0.53 (2.98) | 0.41 (0.89) | 0.01 (0.02) | 1,752 | 10 / 15 | 5 / 2 | 78 / 1.1 | 4,999-4,999 | 313 / 0 | 0 |
| 3000 | 587.0 | 0.00 % (0) | http_req_duration{name:send}, http_req_duration{name:list} | 3.20 (4.03) | 2.20 (2.88) | 1.28 (2.01) | 0.73 (1.15) | 0.03 (0.14) | 1,855 | 10 / 188 | 5 / 88 | 106 / 2.8 | 4,999-4,999 | 459 / 0 | 0 |
| 4000 | 537.1 | 2.59 % (2,921) | http_req_duration{name:list}, http_req_failed, http_req_duration{name:send} | 2.84 (3.86) | 2.05 (2.99) | 1.78 (3.95) | 0.69 (1.38) | 0.02 (0.06) | 1,865 | 10 / 189 | 5 / 14 | 173 / 1.3 | 4,999-4,999 | 437 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,217, average 10.4 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 212,767
- Got live: 212,767; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,150, p95 2,891, p99 4,456, max 5,290
  - level 2000 (committed in its hold, 44,870): p50 816, p95 1,351, p99 1,588, max 1,656
  - level 3000 (committed in its hold, 59,994): p50 1,257, p95 2,915, p99 3,360, max 3,484
  - level 4000 (committed in its hold, 53,619): p50 1,746, p95 4,242, p99 5,157, max 5,290
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
