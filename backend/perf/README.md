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
docker run -d --name oro-plans -e POSTGRES_PASSWORD=plans -e POSTGRES_DB=asata -v oro-plans-data:/var/lib/postgresql/data \
  -v "$PWD/../../1_ASATA_DATABASE/CREATE_ORO_TABLES.sql:/docker-entrypoint-initdb.d/01.sql:ro" -v "$PWD/perf:/plans" \
  postgres:17 -c shared_buffers=2GB -c maintenance_work_mem=1GB -c max_wal_size=8GB
docker exec oro-plans psql -U postgres -d asata -f /plans/generate.sql
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

- **Capacity of one instance of this size: about 1,000 people active at once** (with 5,000 connected), 300 requests
  a second at saturation. It breaks between 1,500 and 2,000: the CPUs of Oro saturate, then the requests wait for a
  connection (10), and after 5 s get 503.
- **The streams held**: 5,000 open from start to end, no failure, none ended for being slow; the outbox lag stayed
  under 2 s.
- **A busy group** (run 1: 499 members, all online, 10 messages a second for 12 minutes, about 5,000 stream events a
  second, 5.3 million in all) did not move the break: requests waited for a connection from 1,500 people on, as
  without it. One conversation's lock and its fan-out are not the limit at this size.
- **The list is the main cost**: Umoja read it again after every event, about 40 % of the requests and most of the
  database's work. Since 2026-10-24 Umoja updates it from the message events instead (it reads the one message, for
  its preview) and reads the whole list only on a new conversation, a change of members, or a stream opened again.
  The numbers above are from before: the load test still reads the list every 8 to 12 s, so they are a floor.

The launch capacity must be at least 3 times the expected peak: on this size, a peak of about 330 people active at
once. More: more CPUs for Oro and the database (both were the limit), then the list read less often.

### Running it

From `oro/backend/perf/load`, the database of the plans running (`oro-plans`), the logins `oro_api` and `oro_worker`
created on it with passwords of the run:

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
