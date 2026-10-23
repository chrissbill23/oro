# Oro technical debt

The choices that limit Oro today, why they were made, what they risk, and when to revisit them. The tasks are in
[the TODO list](ORO_PRODUCTION_READINESS_TODO.md).

## Security architecture

### One database login for the API and the workers

- **Today**: `oro_service` is a member of `oro_app` and `oro_system` and picks one per transaction
  (`context/DbSession.java`).
- **Why**: one process, one pool, simple to run.
- **Risk**: a SQL injection or a code bug in the API can take `oro_system`, which reads everything of both products.
  No injection is known today, but the separation of the roles is weaker than it looks.
- **Revisit**: now, before launch (`ORO-SEC-001`).

### Roles enforced by Java, membership by the database

- **Today**: RLS and triggers decide who is a member and what it may read; who is owner or admin, and who may remove
  or add members, is checked only in `ConversationService`.
- **Why**: role rules were simpler to write in Java.
- **Risk**: the database does not back up the role checks, so one missed check in Java is a privilege escalation.
- **Revisit**: now (`ORO-SEC-002`).

### Shared HMAC secrets between products

- **Today**: Oro verifies Umoja tokens with Umoja's own HS256 secret (`config/security/SecurityConfig.java`).
- **Why**: Umoja already signed HS256 tokens; no key infrastructure was needed.
- **Risk**: whoever reads Oro's environment can sign Umoja tokens with admin roles. Each product added makes it
  worse.
- **Revisit**: now (`ORO-SEC-003`), and before Oro issues its own tokens.

### Static integration keys

- **Today**: one key per product, in the environment, compared in constant time (`IntegrationKeyFilter`).
- **Why**: enough for one integration on a private network.
- **Risk**: no rotation without downtime, no way to tell keys apart, plain HTTP inside the network.
- **Revisit**: rotation now (`ORO-SEC-006`); mTLS or signed requests when Oro and its products run on different
  networks or hosts.

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

### One instance: streams and presence in memory

- **Today**: `LiveHub` keeps the streams and who is online in the memory of the instance; rate limits will too.
- **Why**: no message broker, nothing more to run.
- **Risk**: one instance is a single point of failure; a restart drops every stream (clients reconnect and catch up);
  capacity is bounded by one machine.
- **Revisit**: when the availability target needs a second instance, CPU passes 60 % at peak, or open streams pass
  ~10k. Use PostgreSQL `LISTEN/NOTIFY` first; Redis only if it is measured to be insufficient (`ORO-SCL-006`).

### Stream writes on the outbox worker's thread

- **Today**: `LiveOutboxEventHandler` writes to each stream directly.
- **Risk**: one client that reads slowly holds up delivery for everyone.
- **Revisit**: now (`ORO-SCL-004`).

### Streams authorized once

- **Today**: a stream is checked when it opens and lives 30 minutes.
- **Risk**: presence and events keep flowing after a token expired or an access was revoked.
- **Revisit**: now (`ORO-SEC-007`).

### The outbox only feeds the streams

- **Today**: the only handler is `LiveOutboxEventHandler`; events retry for up to an hour.
- **Why**: the outbox was built ahead for push and other consumers.
- **Risk**: live events retried after minutes are useless; `failed` events have no alert yet.
- **Revisit**: now for the alert and a time-to-live (`ORO-LCH-013`); with push for deduplication (`ORO-SCL-010`).

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
