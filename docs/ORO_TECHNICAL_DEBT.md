# Oro technical debt

The choices that limit Oro today, why they were made, what they risk, and when to revisit them. The tasks are in
[the TODO list](ORO_PRODUCTION_READINESS_TODO.md).

## Security architecture

### One process for the API and the workers

- **Today**: two logins and two pools in one process (`context/DataSourceConfig.java`, done on 2026-10-23): the API's
  can never take `oro_system`.
- **Risk**: a flaw that runs code in the process (not just SQL) still reaches the worker's pool.
- **Revisit**: when the workers need to scale apart from the API, or before handling data that needs a stronger
  separation: run them as their own process with only the worker's login.

### The identity of the context is a setting

- **Today**: RLS and the triggers (roles included, since 2026-10-23) read the identity from `oro.identity_id`, which
  `DbSession` sets per transaction.
- **Risk**: any SQL on the API's connection could set another identity; the triggers protect against a bug in the
  services, the separate logins against `oro_system`, but not against an injection acting as another user.
- **Revisit**: if an injection is ever found, or when the API moves to per-request database credentials (not planned).

### Signing keys in the environment

- **Today**: Umoja signs RS256 with its own private key and publishes the public ones; Oro holds no product secret
  (since 2026-10-23). The private key is a variable of Umoja's environment (`UMOJA_JWT_SIGNING_KEY`), and an unset key
  means a new one at each start.
- **Risk**: whoever reads Umoja's environment can sign its tokens; a forgotten key in production makes every restart
  sign everyone out.
- **Revisit**: with the secrets of production (`ORO-LCH-008`): the key in a secret store or a KMS, and a production
  profile that refuses to start without it; a scheduled rotation (`UMOJA_JWT_PREVIOUS_KEYS`).

### Integration keys over plain HTTP

- **Today**: named keys per product, kept as SHA-256 hashes, several at once for a rotation (`IntegrationKeyFilter`,
  since 2026-10-23); a change of the list needs a restart of Oro. Umoja sends its key in a header, over plain HTTP on
  the private network.
- **Risk**: whoever can read the traffic of the private network reads the key; a key is a bearer secret, not bound to
  a request.
- **Revisit**: mTLS or signed requests (an HMAC of the request and a timestamp) when Oro and its products run on
  different networks or hosts; reload the list without a restart if rotations become frequent.

## Data model

### Strict per-conversation order under a row lock

- **Today**: each send locks its conversation and takes `last_message_seq + 1`.
- **Why**: order without gaps, stable cursors, unread counts without counting rows, idempotent retries.
- **Risk**: sends in one conversation take turns, a few hundred per second at most. Fine for chat, not for broadcast
  channels with thousands of senders.
- **Revisit**: when lock waits on `oro_conversation` pass 50 ms p99, or when broadcast channels are a product
  requirement (`ORO-SCL-003`).

### Read positions instead of per-message receipts

- **Today**: `last_read_seq` and `last_delivered_seq` per member.
- **Why**: one update per read instead of one row per message and recipient.
- **Risk**: no "seen by 12" lists in groups, no marking one older message as read.
- **Revisit**: when the product asks for read-by-whom in groups. Keep the positions for the counts even then.

### Deletion is a tombstone, and there is no edit history

- **Today**: a deleted message keeps its row and order, its content is erased; edits overwrite the text.
- **Why**: privacy first: what a sender deletes or changes is gone.
- **Risk**: old versions still live in table pages until vacuum, in the WAL and in backups; moderators cannot see
  what was said before an edit. Adding edit history later must not bring back what deletion erased.
- **Revisit**: with the retention policy (`ORO-SEC-014`, `ORO-LCH-018`), before any edit history (`ORO-FEA-003`).

### Free-form `metadata`

- **Today**: any JSON object up to 8 KB.
- **Why**: room for mentions and previews without schema changes.
- **Risk**: clients render what senders wrote: forged mentions, misleading previews, growing incompatibility between
  clients.
- **Revisit**: now (`ORO-SEC-015`).

### Unindexed search

- **Today**: `ILIKE '%…%'` over the user's readable messages, 30 results.
- **Why**: correct with RLS, no extension needed.
- **Risk**: the cost grows with the number of messages a user can read.
- **Revisit**: search p95 over 300 ms (`ORO-SCL-002`).

### No partitioning

- **Today**: `oro_message` is one table.
- **Why**: per-conversation queries use one index; partitioning adds work with no gain at this size.
- **Revisit**: 200 to 500 M rows or 100 to 200 GB, autovacuum falling behind, or a retention policy that drops old
  data in bulk (`ORO-SCL-008`).

## Runtime

### One instance: streams, presence and rate limits in memory

- **Today**: `LiveHub` keeps the streams and who is online in the memory of the instance, and `RateLimiter` the
  buckets of the rate limits (a restart refills them; two instances would each allow the full rate).
- **Why**: no message broker, nothing more to run.
- **Risk**: one instance is a single point of failure; a restart drops every stream (clients reconnect and catch up);
  capacity is bounded by one machine.
- **Revisit**: when the availability target needs a second instance, CPU passes 60 % at peak, or open streams pass
  ~10k. Use PostgreSQL `LISTEN/NOTIFY` first; Redis only if it is measured to be insufficient (`ORO-SCL-006`).

### Stream writes on the outbox worker's thread

- **Today**: `LiveOutboxEventHandler` writes to each stream directly.
- **Risk**: one client that reads slowly holds up delivery for everyone.
- **Revisit**: now (`ORO-SCL-004`).

### Stream authorization checked every 30 s

- **Today**: a stream ends with its token and closes with the commit of a revocation on this instance
  (`LiveAuthorization`, since 2026-10-23); a change made elsewhere is found by the check every 30 s.
- **Risk**: up to 30 s of events and presence after a change made outside the services.
- **Revisit**: with several instances (`ORO-SCL-006`): the closes must travel with the events.

### The outbox only feeds the streams

- **Today**: the only handler is `LiveOutboxEventHandler`; events retry for up to an hour.
- **Why**: the outbox was built ahead for push and other consumers.
- **Risk**: live events retried after minutes are useless; `failed` events have no alert yet.
- **Revisit**: now for the alert and a time-to-live (`ORO-LCH-013`); with push for deduplication (`ORO-SCL-010`).

### Quotas count abandoned uploads

- **Today**: the storage quota of a user counts every file not deleted, the uploads never attached to a message
  included, until the purge job removes them (`ORO-LCH-006`, not written); two uploads at the same moment may both
  pass the quota.
- **Risk**: a user whose uploads failed may find its quota full; a quota overshoot of one file.
- **Revisit**: with the purge job (abandoned uploads gone after 24 h), or when users report it.

### Files through the API on a local disk

- **Today**: `LocalFileStorage`; uploads and downloads stream through the API; no scanner.
- **Why**: one authorization path, nothing extra to run in development.
- **Risk**: the disk fills, the files are not backed up with the database, malware is served as clean, API threads
  are busy with large transfers.
- **Revisit**: scanner before launch (`ORO-LCH-004`); object storage before launch unless the volume is backed up
  (`ORO-LCH-003`); signed URLs only when transfers load the API (measured).

## Operations

### One idempotent schema script

- **Today**: `CREATE_ORO_TABLES.sql` adds what is missing and runs again safely.
- **Why**: simple, no migration tool (a decision, kept).
- **Risk**: it cannot change an existing column or constraint, and nothing records which version a database has.
- **Revisit**: before the first deployment (`ORO-LCH-009`): numbered SQL scripts and a version table, still no
  Flyway.

### The shared Asata database

- **Today**: Oro's tables live in the `asata` database next to Umoja's, with their own roles and RLS.
- **Why**: one PostgreSQL to run for every Asata service.
- **Risk**: one superuser, one backup and one outage for every product; heavy load from one product slows the
  others; a restore to a point in time rewinds every product.
- **Revisit**: when Oro's load or its recovery needs differ from Umoja's: a separate database first (same
  server), a separate server after.

### No CI, no metrics

- **Today**: tests run by hand; logs are plain text; only health and info are exposed.
- **Risk**: regressions merge unseen; incidents are found by users.
- **Revisit**: now (`ORO-LCH-017`, `ORO-LCH-010`).

## Product

### Oro has no accounts

- **Today**: Oro trusts tokens from an account system that does not exist yet.
- **Revisit**: before the Oro launch (`ORO-LCH-001`), after the asymmetric tokens (`ORO-SEC-003`).

### Anyone can reach anyone in Oro

- **Today**: any Oro user can open a direct conversation with, or add to a group, any other.
- **Why**: Umoja's organizations already bound who meets whom; Oro was built on the same model.
- **Risk**: spam and harassment from the first day of a public app.
- **Revisit**: before the Oro launch (`ORO-SEC-013`).

### Fixed scope of features

- No moderation by admins, no threads, no edit history, no push, no group pictures.
- **Revisit**: moderation and replies for the Umoja launch if customers ask; push for the Oro launch; the rest after
  (`ORO-FEA-001` to `ORO-FEA-007`).
