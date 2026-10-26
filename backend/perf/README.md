# Performance

The query plans of Oro on realistic data (`ORO-SCL-001`), and how to run them again.

## The data

`generate.sql` fills a throwaway database built by `CREATE_ORO_TABLES.sql`: 20 Umoja organizations of 500 people
(10,000 identities), 90,000 direct conversations and 10,000 groups (most of 3 to 10, some of up to 500), 448,000
memberships and 9.9 million messages, skewed: one group of a million messages, a hundred of 20,000, the rest about 70
each. One power user is in 823 conversations, the million-message group among them; a typical user is in 40. About
5 GB.

## Running them

From `oro/backend`:

```bash
# loaded with room to work (about 6 minutes)
# Asata's tables first (Oro's accounts use them), then Oro's schema by its runner, as the shared database does
DB="$PWD/../../1_ASATA_DATABASE"
docker run -d --name oro-plans -e POSTGRES_PASSWORD=plans -e POSTGRES_DB=asata -v oro-plans-data:/var/lib/postgresql/data \
  -v "$DB/CREATE_ASATA_TABLES.sql:/docker-entrypoint-initdb.d/01-asata.sql:ro" \
  -v "$DB/CREATE_ORO_TABLES.sql:/oro-schema/CREATE_ORO_TABLES.sql:ro" -v "$DB/oro:/oro-schema/oro:ro" -v "$PWD/perf:/plans" \
  postgres:17 -c shared_buffers=2GB -c maintenance_work_mem=1GB -c max_wal_size=8GB
docker exec oro-plans sh -c 'PSQL_ARGS="-U postgres -d asata" sh /oro-schema/oro/migrate.sh'
docker exec oro-plans psql -U postgres -d asata -v ON_ERROR_STOP=1 -f /plans/generate.sql
# measured as production runs: the defaults (stop it cleanly first, or it replays its WAL)
docker stop -t 120 oro-plans && docker rm oro-plans
docker run -d --name oro-plans -e POSTGRES_PASSWORD=plans -v oro-plans-data:/var/lib/postgresql/data \
  -v "$PWD/perf:/plans" postgres:17
docker exec oro-plans psql -U postgres -d asata -f /plans/explain.sql     # twice: the second, warm, counts
docker rm -f oro-plans && docker volume rm oro-plans-data
```

`explain.sql` runs each query as the API does: role `oro_app`, the product and the identity transaction-local, in a
transaction rolled back, `EXPLAIN (ANALYZE, BUFFERS)`. PostgreSQL 17 with its defaults, as the shared database runs
today (`shared_buffers` 128 MB, `work_mem` 4 MB). A laptop (Apple silicon, 10 cores); the times are for comparing,
not a promise.

## Results (2026-10-24)

[Before](plans/2026-10-24-before.txt), [after](plans/2026-10-24-after.txt).

| Query | Before | After | Target |
|---|---|---|---|
| List, typical user (40 conversations) | 33 ms | 13 ms | 20 ms |
| List, power user (823 conversations) | 106 ms | 81 ms | see below |
| A page of 40, newest, of the million-message group | 0.2 ms | 0.2 ms | 20 ms |
| A page of 40 from seq 500,000 back | 0.3 ms | 0.3 ms | 20 ms |
| What came after a seq (catch-up) | 0.2 ms | 0.2 ms | 20 ms |
| A send in the million-message group, every statement | about 6 ms | about 11 ms | 20 ms |
| Search, a frequent word, power user | 5,030 ms | 663 ms | |
| Search, the million-message group only | 4,780 ms | 416 ms | |
| Search, a rare text, typical user | 16 ms | 2 ms | |
| The members of a group of 500 | | 2 ms | |

No query reads `oro_message` sequentially. The send varies between runs with the maintenance of the trigram index.

### What changed

- **The count of the members in the list** went through the policy of the members row by row (the opaque
  `oro_member_history_from`, once per member of each conversation: 4,840 calls for 40 conversations).
  `oro_member_count` checks the membership once per conversation, then counts.
- **The search** ran under RLS, which runs the policy before any condition that is not leakproof: `ILIKE` is not,
  so the trigram index was never used, and the policy function ran on every message of every conversation of the
  user (7 million buffers). `oro_search_messages` applies the same rule written out (an active identity, an active
  member, the history it may read, its product and organization) and lets the planner use the index.
  `MessagingFeaturesTest` checks each part of the rule.

### Limits

- **The list grows with the conversations**: about 0.1 ms each (the last message, the count). 13 ms for 40, 81 ms
  for 823. Paging the list (the newest 50, then more) fixes it; it is an API change for the clients.
- **Search in a very large conversation** still reads it all when the planner prefers the conversation's index to
  the trigram index: about 0.4 s for a million messages. Full-text search (`tsvector`) would rank and bound it.
- One machine, warm caches, no concurrency: the load test (`ORO-SCL-007`) measures the rest.

## Load test (2026-10-24)

`ORO-SCL-007`. Oro in its image, limited to **2 CPUs and 2 GB** (`--cpus=2 --memory=2g`), on the database of the plans
above (PostgreSQL's defaults, no limit: the same laptop, 10 cores). ClamAV off (`ORO_ATTACHMENTS_SCAN=none`): its
cost is not measured.

- `load/chat.js` (k6 1.0.0): each virtual user is one person of the generated data, with its own token, using
  Umoja's messaging as its client does, every 8 to 12 s: the list of its conversations again (Umoja reads it after
  every event it hears), what came in one of them (`?after=`), a message 30 % of the time and its read position, a
  search 3 %, a file of 20 KB 1 %. That is about 0.3 requests a second a person, within the rate limits.
  `busy`: the members of a group of 499 sending into it at a fixed rate.
- `load/Streams.java`: 5,000 streams open the whole time (people of 10 organizations, the busy group's included),
  each opened again when it ends.
- `load/sample.sh`: Oro's metrics every 10 s (outbox lag, connections busy and waiting, CPU, streams).
- `load/Tokens.java`: a signing key of the test and a token for each generated person, valid 12 h; Oro trusts its
  public key (`oro.security.products.UMOJA.jwk-set`). Generated per run, never committed.

### Results

[The raw results](load/results/2026-10-24). With 5,000 streams open, no busy group, each level ramped in a minute,
then held 2 minutes:

| People active | List p50 / p95 / p99 | Send p50 / p95 / p99 | Errors | Requests/s | Oro CPU (of 2) | Database CPU |
|---|---|---|---|---|---|---|
| 1,000 | 14 / 123 / 221 ms | 8 / 88 / 172 ms | 0 | 212 | 0.7 to 1.4 | 1 to 2 cores |
| 1,500 | 116 / 930 / 1,430 ms | 87 / 1,070 / 1,730 ms | 0 | 300 | 2 (saturated) | 4 cores |
| 2,000 | 1,010 / 2,130 / 3,020 ms | 1,000 / 2,590 / 8,500 ms | 0.3 % | 313 | 2 (saturated) | 4 cores |

- **Capacity of one instance of this size, then: about 1,000 people active at once** (with 5,000 connected; 1,500
  since run 3, below), 300 requests a second at saturation. It breaks between 1,500 and 2,000: the CPUs of Oro
  saturate, then the requests wait for a connection (10), and after 5 s get 503.
- **The streams held**: 5,000 open from start to end, no failure, none ended for being slow; the outbox lag stayed
  under 2 s.
- **A busy group** (run 1: 499 members, all online, 10 messages a second for 12 minutes, about 5,000 stream events a
  second, 5.3 million in all) did not move the break: requests waited for a connection from 1,500 people on, as
  without it. One conversation's lock and its fan-out are not the limit at this size.
- **The list was the main cost**: Umoja read it again after every event, about 40 % of the requests and most of the
  database's work. Umoja now updates it from the message events instead (run 3, below).

### Run 3: the list updated from the events

Umoja's client since 2026-10-24 (`LIST=events`, the default of `chat.js`): the list read once, then for each event
the one message (its preview), the whole list again 2 % of the time (a stream opened again, a conversation changed).
Same size, same 5,000 streams, data generated again; `LIST=always` at 1,500 as the control, on the same data:

| People active | List p95 | Send p95 | Errors | Requests/s | Oro CPU (of 2) | Database CPU |
|---|---|---|---|---|---|---|
| 1,000 | 51 ms | 17 ms | 0 | 213 | 0.6 | 0.4 cores |
| 1,500 | 80 ms | 81 ms | 0 | 319 | 1.1 (peaks at 2) | 0.7 cores |
| 2,000 | 344 ms | 722 ms | 0 | 412 | 1.8 (saturated) | 1.1 cores |
| 3,000 | 3.9 s | 4.1 s | 0 | 400 | 2 (saturated) | 1.3 cores |
| 1,500, `LIST=always` | 382 ms | 306 ms | 0 | 314 | 1.6 | 2.7 cores (peaks at 4) |

- **Capacity of one instance of this size: about 1,500 people active at once** (with 5,000 connected), up from
  1,000: the same requests a person, but a message instead of the list. It breaks at about 2,000, 410 requests a
  second (300 before).
- **The database works 4 times less** at 1,500 (0.7 cores against 2.7): the limit is now Oro's 2 CPUs alone, so
  more CPUs for Oro come first.
- The streams held: 5,000 open from start to end, no failure (3.3 million message events delivered).

Runs 1 to 3 had ClamAV off, no restart, and their threshold on errors named a tag k6 does not set (it never
failed; their errors were read from k6's overall count, which is right). "A peak of about 500" was 1,500 divided by 3,
not an expected peak: see the campaign of 2026-10-26 below, which replaces these numbers.

### Running it (by hand, as on 2026-10-24)

`run.sh` (below) does all of this now. From `oro/backend/perf/load`, the database of the plans running (`oro-plans`), the logins `oro_api` and `oro_worker`
created on it with passwords of the run (and `oro_auth`, in `oro_account`, for Oro to be ready; the accounts are not
measured):

```bash
java Tokens.java . 20 500 12                         # jwks.json, tokens.json
# orgs.json: {"<org number>": "<organization id>"}, busy.json: {id, org, members}, from the database (see chat.js)
docker network create oro-load-net && docker network connect oro-load-net oro-plans
docker run -d --name oro-load --network oro-load-net --cpus=2 --memory=2g -p 127.0.0.1:9095:9082 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://oro-plans:5432/asata -e SPRING_DATASOURCE_PASSWORD=... \
  -e ORO_SYSTEM_DATASOURCE_PASSWORD=... -e ORO_ATTACHMENTS_SCAN=none -e UMOJA_JWKS_URI= \
  -e SPRING_APPLICATION_JSON="{\"oro\":{\"security\":{\"products\":{\"UMOJA\":{\"jwk-set\":$(jq -Rs . < jwks.json)}}}}}" oro-oro
./sample.sh metrics.txt &
docker run -d --name oro-streams --network oro-load-net -v "$PWD:/load:ro" -w /load eclipse-temurin:25-jdk \
  java Streams.java http://oro-load:8082/oro tokens.json orgs.json 5000 600
docker run --rm --network oro-load-net --user "$(id -u)" -v "$PWD:/load" -w /load -e STAGES=1000:60,1000:120 \
  -e BUSY_RATE=0 grafana/k6:1.0.0 run chat.js
```

## Capacity campaign (2026-10-26)

`ORO-SCL-007`, `ORO-LCH-012`, `ORO-LCH-014`. The same workload as run 3, with what it lacked: ClamAV on, restarts
under load, the delivery of every message checked, Oro's CPU profiled, and 2 against 4 CPUs on the same data.

### The harness

Everything is in `load/`; `run.sh` runs one measurement end to end, on the containers of the load tests only
(`oro-plans`, `oro-load`, `oro-load-clamav`, `oro-streams`, `oro-load-k6`, the network `oro-load-net`):

1. the database restored from the snapshot `oro-plans-base` (`db-snapshot.sh`): every run starts from the same data;
2. ClamAV (`clamav/clamav:1.5.4-debian13-slim`, the limits of the dev and production stacks, no CPU limit as in
   production) and Oro (`--cpus=$CPUS --memory=2g`, `ORO_ATTACHMENTS_SCAN=clamav`, `ORO_MULTIPART_DIR` as in production);
3. 5,000 streams (`Streams.java`), 300 of them tracked: they catch up on every `hello` as Umoja's client does and
   write every message they get, live or caught up;
4. the samplers: Oro's metrics every 5 s (`sample.sh`: outbox pending and oldest, pools active and waiting, CPU,
   streams, scans) and `docker stats` of every container (Oro, the database, k6, the streams, ClamAV);
5. a warm-up (200 people, 60 s; with ClamAV it first uploads the EICAR test file, which must be refused: else the run
   stops, the scanner is not on), then each level of `LEVELS`: 60 s to reach it, 120 s held;
6. a restart of Oro during `RESTART_LEVEL` if asked (`restart.sh`), a profile during `PROFILE_LEVEL` if asked;
7. a drain of 90 s, then the delivery checked against the database: every message committed in the conversations of
   the tracked clients during the run, after what they had at their first `hello` (`truth.tsv`), must be in what they
   got (`stream-events.txt`);
8. `analyze.py report` writes `report.md`; every container is removed.

`run.sh` refuses to start on battery power unless `ALLOW_BATTERY=1` (the first attempt of this campaign slept at 3 %
of battery, its runs were thrown away: `results/2026-10-26/discarded`), records the power at the end of each level,
and `report.md` marks the levels that ended on battery and a level that took far longer than planned (a sleep). Run
it under `caffeinate -ims`, which keeps the machine awake (on battery too, with `-i`).

What `chat.js` adds since run 3:

- every response counted by operation and outcome: `ok`, `rate_limited` (429: the limits doing their job),
  `not_ready` (409 on the download of a file not scanned yet: expected), `client_error` (other 4xx), `unavailable`
  (502, 503, 504: a full pool, a restart), `server_error` (other 5xx), `network` (no answer, a timeout of 60 s);
  the failures are also printed with their time, for the time line of a restart;
- p50, p95, p99 and max for each operation (`list`, `preview`, `after` (the catch-up read), `send`, `read`, `search`,
  `upload`, `download`, `busy-send`), in `k6-<level>.json`;
- uploads: `UPLOAD_RATE` of the cycles (0.01), `UPLOAD_KB` (20), each downloaded at once: 200 if the upload said
  available, 409 if not; a 200 before a clean scan is counted in `download_before_clean`, which must stay 0;
- `http_req_failed: rate<0.01`: the threshold of run 3 named a tag k6 does not set (`{expected:no}`), so it never
  failed; the errors of runs 1 to 3 were read from k6's overall count, which is right.

Oro has one new metric for it: `oro_attachments_scan_duration_seconds{result}`, the time of each scan.

Measured in each level: k6's latencies and counts over the whole level (ramp and hold, as k6 gives them); CPU, pools,
outbox and streams over the hold only. Delivery latency: from the commit of a message (`created_at`) to its live event
at a tracked client, same clock (one Docker machine). A message lost: committed, in a tracked client's conversation,
never got live nor by a catch-up. A duplicate: the same message got twice live; got live and again by a catch-up is
counted apart (the client keeps one).

### The runs

[The raw results](load/results/2026-10-26): per run its settings (`README`), its `report.md`, k6's numbers per level
(`k6-<level>.json`, `.txt`), the samples (`oro-metrics.txt`, `cpu.txt`), the streams (`streams.txt`), the restart
(`restart.txt`); the files of every message and the profiles are too large to keep (`NOT-COMMITTED.txt`: their
checksums and sizes). Runs set aside, and why: `discarded/README.md`.

All of them: image `oro-load:capacity` (the code of `6cec4bb` with the scan timer), then `oro-load:capacity-fix` for
the restarts after the fixes below; Oro `--memory=2g`, `-XX:MaxRAMPercentage=75`; pools 10 (API) and 5 (system) but
`pools-4cpu`; the database `oro-plans` (PostgreSQL 17.11, its defaults, no limit, 9.98 million messages) restored from
the same snapshot before each run; 5,000 streams, 300 tracked; `LIST=events`, no busy group (`BUSY_RATE=0`, as run
3); uploads 1 % of the cycles, 20 KB, each downloaded at once; ClamAV 1.5.4 on. Levels 500, 1,000, 1,500, 2,000,
3,000, 4,000, each 60 s to reach and 120 s held, after a warm-up of 200 for 60 s. One machine: a MacBook (Apple M5),
Docker Desktop with 10 CPUs and 16 GB, shared by the load generators and the database (k6 used at most 1.5 cores on
average, the streams 0.6: they were not the limit). Only Oro's `--cpus` differs between the comparisons.

| Run | What | Power |
|---|---|---|
| `cmp-2cpu-a`, `cmp-2cpu-b` | 2 CPUs, the 6 levels, twice | mains; battery 87 to 76 % |
| `cmp-4cpu-b`, `cmp-4cpu-a` | 4 CPUs, the 6 levels, twice | mains; battery 95 to 87 % |
| `pools-4cpu` | 4 CPUs at 2,000 and 3,000, pools 20 and 10 (`EXTRA_ENV`) | battery |
| `uploads-clamav`, `uploads-none` | 2 CPUs at 1,500, uploads 10 % of the cycles, 200 KB; ClamAV, then no scanner | battery; mains |
| `profile-2cpu`, `profile-4cpu` | 2 CPUs at 2,000, 4 CPUs at 3,000: Java Flight Recorder (`settings=profile`), 60 s in the hold | battery |
| `restart-2cpu`, `restart-4cpu` | 2 and 4 CPUs at 1,500, Oro restarted 100 s into the level (image before the fixes) | battery |
| `restart-fix-2cpu`, `restart-fix-4cpu` | the same on the image with the fixes | mains |

On battery the repeats gave the numbers of mains power (below, `a` against `b`); a battery near empty did not (the
discarded runs). The runs of 2 and 4 CPUs on mains power are the comparison; the others confirm it.

### 2 CPUs against 4

Per level: the worst p95 and p99 of `list`, `preview`, `after`, `send`, `read`, `upload`; failures (neither 2xx,
nor 429, nor the 409 of a file not scanned yet); CPU in cores averaged over the hold; most requests waiting for a
connection; the delivery p95 from commit to live event (`analyze.py compare`):

| Level | 2 CPUs: worst p95 / p99 (ms) | failed | Oro / DB CPU | API pool waiting | delivery p95 | 4 CPUs: worst p95 / p99 (ms) | failed | Oro / DB CPU | API pool waiting | delivery p95 |
|---|---|---|---|---|---|---|---|---|---|---|
| 500 | 30 / 47 (30 / 63) | 0 | 0.36 / 0.19 | 0 | 1.0 s | 32 / 63 (29 / 48) | 0 | 0.36 / 0.20 | 0 | 1.0 s |
| 1,000 | 164 / 493 (38 / 89) | 0 | 0.71 / 0.49 | 49 | 1.2 s | 42 / 75 (45 / 131) | 0 | 0.70 / 0.45 | 0 | 1.1 s |
| 1,500 | 106 / 186 (40 / 107) | 0 | 0.89 / 0.65 | 0 | 1.2 s | 47 / 91 (45 / 93) | 0 | 0.98 / 0.70 | 0 | 1.1 s |
| 2,000 | 398 / 826 (382 / 883) | 0 | 1.34 / 1.07 | 176 | 4.0 s | 75 / 136 (73 / 138) | 0 | 1.63 / 1.10 | 1 | 1.4 s |
| 3,000 | 5,254 / 5,885 (3,788 / 4,370) | 0 | 1.97 / 1.50 | 188 | 37 s | 395 / 752 (398 / 714) | 0 | 2.44 / 1.77 | 187 | 1.8 s |
| 4,000 | 5,053 / 60,003 | 3.8 % | 1.78 / 1.35 | 190 | 37 s | 1,433 / 59,999 | 2.3 % | 2.46 / 2.06 | 189 | 2.6 s |

The first numbers are the run on mains power, in brackets its repeat. Requests per second, the same at both sizes
until 2 CPUs saturate: 107, 214, 321, 423, then 416 and 386 at 2 CPUs against 629 and 610 at 4. The failures at
4,000 are timeouts of 60 s (`network`) and 503 (no connection within 5 s), at both sizes. The 1,000 of `cmp-2cpu-a`
is a spike of a minute (49 waiting); its repeat has none.

- **2 CPUs hold 1,500 people active** (all p95 under 110 ms, delivery p95 1.2 s, no failure), **2,000 on the
  requests alone** (p95 about 400 ms) but messages reach the others 4 s late at p95; at 3,000 everything waits
  seconds. Oro's 2 CPUs are the limit (1.3 to 2 cores used; the API pool waits because its requests run slower).
- **4 CPUs hold 3,000** (p95 about 400 ms, delivery p95 1.8 s, no failure) and break at 4,000 (2.3 % failures).
  Twice the CPUs, twice the people. The limit moves: at 3,000 Oro uses 2.4 of its 4 CPUs and the database 1.8, while
  the API pool is full (10 active, 187 waiting) and the system pool too (5, 43 waiting).
- **The pools at 4 CPUs** (`pools-4cpu`, 20 and 10): at 3,000 the worst p95 falls from about 400 to 201 ms and send
  p95 from 329 to 153 ms, no failure, Oro's CPU the same (2.45); the pool of 20 is still full (156 waiting) and the
  database takes 1.86 cores. Bigger pools help at 4 CPUs, not at 2 (there the CPU is the limit); the database has
  `max_connections` 100 for Oro, Umoja and Mokili together.
- **Every message reached its tracked clients**: 254,382 and 265,094 (2 CPUs), 318,326 and 318,683 (4 CPUs), none
  lost, none twice. Delivery takes about 0.6 s at p50 when Oro is not saturated: the outbox polls every second.

### Uploads and ClamAV

- **Never downloadable before its scan**: in every run each upload was downloaded at once, `download_before_clean`
  0. The scan is in the upload request: every upload of the runs came back available (clean), none pending.
- **The scan costs little**: Oro's timer (`oro_attachments_scan_duration_seconds`) gives 6 to 16 ms on average a
  file of 20 to 200 KB in the runs of many scans (1,308 to 2,426 a run; up to 26 ms in the short restarts, a few
  hundred scans); ClamAV used at most 0.04 cores on average. With 10 times the
  uploads, 200 KB each (`uploads-clamav`, 2,344 uploads at 1,500 people, about 13 a second), upload
  p50 / p95 / p99 13 / 174 / 307 ms with ClamAV against 13 / 174 / 269 ms without a scanner (`uploads-none`), the
  other operations within noise. Not measured: large files (megabytes), images (their metadata is stripped first).
- The EICAR test file was refused (422) before each run with ClamAV: the scanner really scans.
- The uploads of a run are in Oro's container, removed with it; their rows go with the database, restored before the
  next run. Nothing is left behind.

### Restart under load

`restart.sh` restarts the container `oro-load` only (its label and `LOADTEST_RESTART_OK` must name it) with 45 s to
stop, as `stop_grace_period` in production, 100 s into the level of 1,500 people with 5,000 streams open.

The first runs (`restart-2cpu`, `restart-4cpu`) showed three defects, fixed in `LiveHub` and `HttpExceptionHandler`:

- **The stop did not end within 45 s: Oro was killed.** Closing each stream told the others of its organization that
  its user went offline, and each presence went through every stream of the instance: 5,000 closes, each a pass over
  5,000 streams. Streams still open at the kill never heard `reconnect`. Now a stop sends no presence: the stop of the
  streams takes 0.2 s.
- **A stream refused during the stop answered 500, not 503** (195 and 436 times): the error had a JSON body that a
  client asking `text/event-stream` cannot take. Now a client that takes no JSON gets the status alone (503 with
  `Retry-After`, 429).
- **An event could reach a new stream before its `hello`** (the stream was registered, then its hello queued). Now
  the hello is queued first.

Tested: `LiveHubTest` (a stop sends no presence, the hello first), `CatchUpTest` (a stream refused during a stop is a
503); each test fails without its fix.

The tracked clients of the first runs caught up after the newest seq they had: 3 and 4 messages committed during the
stop were then counted lost, when a live message of the same conversation came before their catch-up. Umoja's chat
panel reads after the last message it shows on every message event, so it never loses them; the tracked clients now
do the same (a live seq past the next one: the gap is read first).

With the fixes (`restart-fix-2cpu`, `restart-fix-4cpu`):

| | 2 CPUs | 4 CPUs |
|---|---|---|
| Stop to started again | 3.4 s | 23.4 s (Spring waited its 20 s for one request still active, then went on) |
| Stop to ready | 12.6 s | 29.9 s |
| Tracked clients back and caught up | 300 of 300 | 300 of 300 |
| All the streams open again (the holder counts every 10 s) | 4,999 of 5,000, 84 s after the stop | 5,000, 73 s after the stop |
| Their `hello` after readiness (p50 / p95 / max) | 12.4 / 34.7 / 53.7 s | 23.3 / 32.2 / 40.5 s |
| Caught up, from the stop (p50 / p95) | 64 / 83 s | 70 / 70 s |
| Messages committed during the run in their conversations | 29,694: none lost, none twice | 28,046: none lost, none twice |
| Outbox in the minute after readiness | oldest at most 2.4 s, under 3 s at the first sample | oldest at most 0.7 s |
| Requests failed around the restart | about 2,700, all `network` (Oro down) | about 9,500, all `network` |
| 5xx | none | none |

- **No committed message is lost** across a restart under load, and every client reconnects and catches up.
- **The time back is the clients'**: they wait 1, 2, 5, 10, then 30 s between attempts (Umoja's delays), so after
  an outage of 13 to 30 s their next attempt comes 12 to 23 s after Oro is ready, and all of them at once. At 2
  CPUs the catch-ups of 300 clients and the reconnections of 5,000 streams on top of 1,500 people active took a minute
  (107 catch-ups waited over 30 s and were retried). A client that honoured the `Retry-After` of the 503 (5 s) and
  spread its attempts would be back sooner.
- **A reconnection of everyone costs presence**: each new stream tells its organization it is online, through every
  stream of the instance: the 5,000 coming back sent 1.25 million presence events (2.5 million in the run, against
  1.25 million for the first connections).
- **The requests in flight at the stop**: at 4 CPUs one was still active after the 20 s of the graceful shutdown;
  the stop then went on. Which request it was is not known (the logs do not say).

### Where the CPU goes

Java Flight Recorder (`settings=profile`, 60 s inside the hold, the window checked against `stages.txt`) in runs of
their own, at the levels where each size saturates: 2 CPUs at 2,000 (3,828 samples), 4 CPUs at 3,000 (5,748). The
profiles look alike (`profile-*/profile.md`, `profile.py`). By the work the threads were doing, at 4 CPUs:

| Work | Share of the samples |
|---|---|
| HTTP requests (controllers, security, transactions, Hibernate, JSON) | 43 % |
| Writing the events of the streams (`LiveHub.drain`: 5.1 million message events, 1.25 million presence in a run) | 18 % |
| The outbox | 5 % |
| Tomcat's poller and the timeouts of the async requests (5,000 streams open) | 5 % |

No single hot spot: the most of any one frame is 2.6 %. In the requests:

- Hibernate 8 % of the samples on its own code (21 % with what it calls); `IdentityService.externalIds` (the user ids
  of the identities of a response, through Hibernate) 5 %, `MessageService.extras` 6 %, `requireActiveMember` 3 %;
- the observations of Micrometer around Spring Security's filters, its authorization and the server's requests:
  about 5 % (14 % of the samples have Micrometer in them);
- the check of the tokens' signatures (RS256: `BigInteger.oddModPow`, Nimbus): about 4 %;
- the identity and organization access checked as `oro_system` on every request (`ActorResolver`): 3 %.

What would give the most, from these numbers (not done: measure each):

1. At 4 CPUs, the pools before anything: 20 and 10 halve the p95 at 3,000 (above; done since: `PoolSizes`). Then the
   system pool of every request (the identity and the access as `oro_system`, two transactions a request): a cache of
   a few seconds would take them off the pool and the database (done since: `ActorCache`, its gain not measured yet).
2. The Micrometer observations of Spring Security (about 5 %): keep the metrics of the requests, not one observation a
   filter (done since: off by default, `ORO_SECURITY_OBSERVATIONS`; its gain not measured yet).
3. The user ids of a response read through Hibernate (`externalIds`, 5 %): one query, or kept with the identity.
4. Each presence and each new stream goes through every stream of the instance (`LiveHub.presence`): the streams
   indexed by organization (done since: a reconnection of 5,000 streams of 10 organizations, 1,715 ms of the hub's
   work before, 505 ms after).

### The sizes of the pools

From the runs above on a quiet machine (the morning): on 2 CPUs the CPUs are the limit, the pools of 10 (API) and 5
(system) are enough; on 4 CPUs the pools are the limit, and 20 and 10 halved the p95 at 3,000 people (395 to 201 ms,
send 329 to 153 ms) with nothing worse at 2,000 (75 to 57 ms). So Oro sizes them from the CPUs it may use
(`PoolSizes`, Java sees `--cpus`): 5 a CPU for the API (10 to 20), 2.5 for the system (5 to 10); 10 and 5 on 2 CPUs,
20 and 10 on 4 or more. `ORO_DB_POOL_SIZE` and `ORO_WORKER_DB_POOL_SIZE` still set them. Above 20 and 10 nothing was
measured on a quiet machine: the sizes stop there. With Umoja's (10) and Mokili's, 30 connections of Oro stay well
under `max_connections` (100).

The runs of the afternoon meant to choose more finely (30/15, 40/20, each again warmed) were spoilt: another
project's load test ran on the same machine (`results/2026-10-26/discarded`). One run of 20/10 at 4 CPUs is the
evidence: to confirm on a server like production, two runs of each size, interleaved.

### Capacity: the 3 times the expected peak

**Not decided: no expected peak is given.** No document of the project, nor the TODO, nor the launch checklist,
gives the expected number of people active at once; the 500 of 2026-10-24 was 1,500 divided by 3. The criteria
here are the harness's (p95 under 500 ms, under 1 % failures) and, proposed, delivery p95 under 2 s: none is
approved. Measured, on this machine, with ClamAV and 5,000 streams:

| Oro | Holds (all criteria) | On the requests only | Breaks |
|---|---|---|---|
| 2 CPUs, pools 10 / 5 | 1,500 people active | 2,000 (delivery p95 4 s) | 3,000 |
| 4 CPUs, pools 10 / 5 | 3,000 | 3,000 | 4,000 (2.3 % failures) |
| 4 CPUs, pools 20 / 10 | 3,000 (p95 201 ms) | 3,000 | not measured above 3,000 |

So a peak of 500 people active would have its 3 times on 2 CPUs, a peak of 1,000 on 4. A restart under load loses
nothing and every client is back within about a minute and a half (`ORO-LCH-012`, `ORO-LCH-014`); nothing measured
here needs more than one instance.

Caveats: one laptop, shared with the generators and the database (production has the database on its own resources);
no busy group in these runs (run 1 measured one: it did not move the break); the population of the generated data
(20 organizations of 500, half of them with a stream open); the mix of requests of `chat.js`, not measured from users.

### Running it (run.sh)

From `oro/backend/perf/load`, once: the database of the plans (`oro-plans`, above), its logins `oro_api`, `oro_worker`,
`oro_auth` with passwords of the run (`api_pw`, `worker_pw`, `auth_pw` in `$LOAD_DIR`, mode 600), the tokens and the
ids of the data, then its snapshot:

```bash
L=/somewhere/out/of/the/repository; cd backend/perf/load
java Tokens.java $L 20 500 12                                 # $L/jwks.json, $L/tokens.json
docker exec oro-plans psql -U postgres -d asata -Atc "<the query of orgs.json, below>" > $L/orgs.json
docker exec oro-plans psql -U postgres -d asata -Atc "<the query of busy.json, below>" > $L/busy.json
./db-snapshot.sh save                                         # oro-plans-data -> oro-plans-base
docker build -t oro-load:capacity ../..                       # the image measured
```

`orgs.json`: `SELECT json_object_agg(split_part(split_part(i.external_user_id,'-',1),'u',2), a.organization_id) FROM
(SELECT DISTINCT ON (split_part(external_user_id,'-',1)) id, external_user_id FROM oro_messaging_identity) i JOIN
oro_organization_access a ON a.identity_id = i.id`. `busy.json`: the biggest group, `{id, org, members}`.

Then each run (minutes: 27 for the 6 levels, 9 for one), on mains power or `ALLOW_BATTERY=1`:

```bash
caffeinate -ims env LOAD_DIR=$L IMAGE=oro-load:capacity RUN=cmp-2cpu CPUS=2 ./run.sh
caffeinate -ims env LOAD_DIR=$L IMAGE=oro-load:capacity RUN=cmp-4cpu CPUS=4 ./run.sh
env LOAD_DIR=$L RUN=restart CPUS=2 LEVELS=1500 RESTART_LEVEL=1500 RESTART_AFTER=100 ./run.sh
env LOAD_DIR=$L RUN=profile CPUS=2 LEVELS=2000 PROFILE_LEVEL=2000 ./run.sh
env LOAD_DIR=$L RUN=pools CPUS=4 LEVELS="2000 3000" EXTRA_ENV="ORO_DB_POOL_SIZE=20 ORO_WORKER_DB_POOL_SIZE=10" ./run.sh
python3 analyze.py compare $L/cmp-2cpu $L/cmp-4cpu
docker run --rm -v $L/profile:/r eclipse-temurin:25-jdk sh -c 'jfr print --events jdk.ExecutionSample --stack-depth 96 /r/oro.jfr > /r/samples.txt'
python3 profile.py $L/profile/samples.txt > $L/profile/profile.md
```

`RUN` must be new (a folder of `$LOAD_DIR`); `run.sh` stops if a container of a run is still there, if a file is
missing, or on battery. It removes every container at its end, also when it fails (the volume `oro-plans-base` and
the data of ClamAV `oro-load-clamav-db` stay). Afterwards: `docker rm -f oro-plans`, `docker volume rm oro-plans-data
oro-plans-base oro-load-clamav-db`, `docker network rm oro-load-net`. It never touches the dev or the shared stack.
