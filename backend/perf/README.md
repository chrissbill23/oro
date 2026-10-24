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
