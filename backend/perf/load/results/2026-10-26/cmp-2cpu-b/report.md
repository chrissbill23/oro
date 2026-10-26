# Load-test run `cmp-2cpu-b`

```
run: cmp-2cpu-b, 2026-10-26T09:41:47Z
git: 6cec4bb (backend sources changed since)
image: oro-load:capacity sha256:5121ad64fa5dfae702a0bae297c7fde95996752e28fd7e19f921ae9e5fba9d67 2026-10-26T00:26:34.365114844Z
oro: --cpus=2 --memory=2g, JAVA_OPTS -XX:MaxRAMPercentage=75, scan clamav
database: oro-plans, postgres (PostgreSQL) 17.11 (Debian 17.11-1.pgdg13+2), no CPU or memory limit, settings default, data restored: 1
docker: 10 CPUs, 16745824256 bytes, Docker Desktop
power: Battery Power, low power mode 0
workload: levels 500 1000 1500 2000 3000 4000 (ramp 60 s, hold 120 s), warm-up 200:60, streams 5000 (tracked 300), list events, busy 0/s, uploads 0.01 x 20 KB (download check 1)
restart: none; profile: none
```

**NOT COMPARABLE: level 500 ended on battery power.**

**NOT COMPARABLE: level 1000 ended on battery power.**

**NOT COMPARABLE: level 1500 ended on battery power.**

**NOT COMPARABLE: level 2000 ended on battery power.**

**NOT COMPARABLE: level 3000 ended on battery power.**

**NOT COMPARABLE: level 4000 ended on battery power.**

## Per level

Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold (max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).

| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | list | 659 | 14.0 | 28.4 | 63.5 | 97 | 659 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | preview | 7089 | 2.4 | 6.2 | 16.9 | 93 | 7089 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | after | 7748 | 2.3 | 5.8 | 10.3 | 80 | 7748 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | send | 2364 | 4.9 | 11.3 | 23.7 | 74 | 2364 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | read | 2274 | 1.4 | 3.3 | 6.1 | 71 | 2274 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | search | 239 | 17.9 | 50.9 | 105.8 | 1,674 | 239 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | upload | 90 | 11.6 | 30.2 | 55.1 | 57 | 90 | 0 | 0 | 0 | 0 | 0 | 0 |
| 500 | download | 90 | 2.0 | 4.1 | 16.0 | 62 | 90 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | list | 1319 | 11.4 | 37.8 | 89.0 | 193 | 1319 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | preview | 14178 | 2.2 | 12.3 | 50.4 | 138 | 14178 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | after | 15497 | 2.2 | 8.7 | 32.1 | 210 | 15497 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | send | 4791 | 4.3 | 14.5 | 46.7 | 92 | 4791 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | read | 4662 | 1.3 | 4.6 | 17.6 | 143 | 4662 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | search | 460 | 15.3 | 43.6 | 96.8 | 725 | 460 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | upload | 129 | 13.4 | 35.6 | 77.7 | 168 | 129 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1000 | download | 129 | 2.0 | 5.7 | 9.3 | 12 | 129 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | list | 1954 | 9.2 | 40.3 | 89.8 | 184 | 1954 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | preview | 21247 | 2.3 | 32.8 | 68.1 | 187 | 21247 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | after | 23201 | 2.4 | 15.0 | 69.6 | 181 | 23201 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | send | 7052 | 4.5 | 27.1 | 85.6 | 187 | 7052 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | read | 6837 | 1.4 | 9.0 | 59.5 | 112 | 6837 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | search | 691 | 14.2 | 73.0 | 107.6 | 170 | 691 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | upload | 215 | 11.1 | 38.7 | 107.3 | 299 | 215 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1500 | download | 215 | 2.1 | 10.5 | 55.6 | 75 | 215 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | list | 2563 | 10.3 | 155.7 | 349.1 | 1,174 | 2563 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | preview | 27897 | 18.6 | 381.8 | 862.5 | 2,701 | 27897 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | after | 30460 | 9.2 | 355.7 | 812.6 | 2,296 | 30460 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | send | 9371 | 14.8 | 320.9 | 778.3 | 2,592 | 9371 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | read | 9070 | 4.6 | 222.6 | 596.6 | 1,297 | 9070 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | search | 878 | 40.5 | 400.6 | 802.1 | 1,268 | 878 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | upload | 301 | 72.1 | 320.1 | 883.2 | 1,630 | 301 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2000 | download | 301 | 7.0 | 213.1 | 623.7 | 1,300 | 301 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | list | 3553 | 65.7 | 2,962.8 | 3,531.2 | 4,655 | 3553 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | preview | 27433 | 2,384.4 | 3,788.0 | 4,370.1 | 5,859 | 27433 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | after | 30986 | 2,203.2 | 3,697.8 | 4,285.1 | 6,182 | 30986 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | send | 9440 | 2,192.3 | 3,619.4 | 4,218.5 | 5,804 | 9440 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | read | 9105 | 2,104.9 | 3,406.4 | 3,863.1 | 5,162 | 9105 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | search | 948 | 2,252.5 | 3,765.6 | 4,318.9 | 7,505 | 948 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | upload | 335 | 2,211.2 | 3,723.4 | 4,090.9 | 4,513 | 335 | 0 | 0 | 0 | 0 | 0 | 0 |
| 3000 | download | 335 | 2,006.3 | 3,632.0 | 4,141.2 | 4,605 | 335 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | list | 6821 | 6.5 | 3,610.3 | 60,006.3 | 60,038 | 3752 | 0 | 0 | 0 | 0 | 0 | 3069 |
| 4000 | preview | 27552 | 3,107.4 | 4,876.6 | 5,510.5 | 7,118 | 27552 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | after | 31304 | 2,858.4 | 4,759.4 | 5,502.6 | 7,498 | 31304 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | send | 9640 | 2,813.8 | 4,784.0 | 5,471.8 | 7,281 | 9640 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | read | 9325 | 2,703.9 | 4,392.8 | 5,106.6 | 7,321 | 9325 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | search | 935 | 2,795.6 | 4,566.6 | 5,461.7 | 7,059 | 935 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | upload | 315 | 2,862.4 | 4,732.8 | 5,296.4 | 5,889 | 315 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4000 | download | 315 | 2,538.6 | 4,179.6 | 4,747.3 | 5,087 | 315 | 0 | 0 | 0 | 0 | 0 | 0 |

| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 107.2 | 0.00 % (0) | none | 0.34 (0.62) | 0.19 (0.36) | 0.09 (0.48) | 0.20 (0.75) | 0.00 (0.01) | 1,693 | 3 / 0 | 1 / 0 | 10 / 0.9 | 5,000-5,000 | 90 / 0 | 0 |
| 1000 | 214.6 | 0.00 % (0) | none | 0.56 (1.27) | 0.35 (0.48) | 0.14 (0.19) | 0.19 (0.56) | 0.00 (0.02) | 1,650 | 3 / 0 | 2 / 0 | 19 / 0.7 | 5,000-5,000 | 129 / 0 | 0 |
| 1500 | 320.0 | 0.00 % (0) | none | 0.94 (1.52) | 0.64 (0.95) | 0.35 (1.73) | 0.30 (0.79) | 0.01 (0.06) | 1,674 | 7 / 0 | 2 / 0 | 38 / 0.9 | 5,000-5,000 | 215 / 0 | 0 |
| 2000 | 420.8 | 0.00 % (0) | none | 1.51 (2.11) | 1.06 (1.43) | 0.51 (1.35) | 0.35 (0.64) | 0.01 (0.03) | 1,720 | 10 / 173 | 5 / 55 | 149 / 2.4 | 5,000-5,000 | 301 / 0 | 0 |
| 3000 | 418.1 | 0.00 % (0) | http_req_duration{name:list}, http_req_duration{name:send} | 1.91 (2.13) | 1.42 (2.39) | 1.13 (4.11) | 0.55 (2.17) | 0.01 (0.04) | 1,772 | 10 / 188 | 5 / 15 | 82 / 2.0 | 5,000-5,000 | 335 / 0 | 0 |
| 4000 | 410.3 | 3.56 % (3,069) | http_req_duration{name:list}, http_req_failed, http_req_duration{name:send} | 1.73 (2.10) | 1.28 (2.51) | 1.08 (3.69) | 0.53 (2.58) | 0.02 (0.06) | 1,795 | 10 / 188 | 5 / 20 | 92 / 3.1 | 5,000-5,000 | 315 / 0 | 0 |

Scans (Oro's timer, since its last start): 1,393, average 16.2 ms.

## Delivery to the tracked clients

- Tracked clients: 300; messages committed in their conversations during the run (after what they had at their first hello): 265,094
- Got live: 265,094; caught up after a reconnection: 0; **missing: 0**
- Duplicates: 0 live events received twice; 0 messages got live and again by a catch-up (the client keeps one: same conversation and seq)
- Delivery latency (commit to live event, ms), whole run: p50 1,479, p95 38,455, p99 42,407, max 43,621
  - level 500 (committed in its hold, 12,737): p50 525, p95 1,029, p99 1,075, max 1,085
  - level 1000 (committed in its hold, 23,091): p50 648, p95 1,056, p99 1,218, max 1,227
  - level 1500 (committed in its hold, 30,910): p50 732, p95 1,214, p99 1,335, max 1,597
  - level 2000 (committed in its hold, 43,606): p50 1,515, p95 4,530, p99 5,604, max 5,787
  - level 3000 (committed in its hold, 49,286): p50 18,196, p95 39,080, p99 40,273, max 40,600
  - level 4000 (committed in its hold, 44,951): p50 26,314, p95 42,649, p99 43,580, max 43,621
- Gaps seen live (a message past the next seq, the missing ones read then): 0
- Hellos: 300 (reconnections: 0); failures seen by the clients: none
