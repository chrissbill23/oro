# Implementation summary

Oro is the messaging service of Asata, shared by two products: Oro (personal messaging) and Umoja (business
messaging inside an organization). The backend is Spring Boot 4.1 on Java 25, with Spring Data JPA (Hibernate 7) on the
shared PostgreSQL 17 database. It runs on Docker at `http://localhost:8082/oro` in development, and behind Umoja's
Caddy in production ([Deployment](deployment.md)).

## Features

- Conversations: direct ones (one per pair of people and per context) and groups, with an owner, admins and members.
  The admin who adds a member chooses whether they read the history; by default they do not.
- Messages in a strict order, without gaps. A retry with the same client message id returns the stored message
  instead of a duplicate. Messages can be edited and deleted (the content is erased), and searched within what the
  user may read (a trigram index, through `oro_search_messages`).
- Reactions: any single emoji, skin tones, flags and ZWJ sequences included.
- Read and delivered positions, unread counts, "Seen", typing, presence and mute.
- Files uploaded through the API to a local storage, downloaded by the members only, with safe headers. Their real
  type is checked, photo metadata is removed, and each file is scanned by ClamAV before anyone can download it.
- A live stream per user (Server-Sent Events): messages, reactions, conversations, typing, reads and presence. A
  client whose stream was cut reads what it missed on its next `hello` (the contract is in the backend README).
- Umoja team chats: one group per team, following its members, with system messages for who joins and leaves.

## Security and privacy

- A message carries mentions of members only: no link preview or system event forged by its sender.
- Oro stores no password, email or credential. Each product signs the tokens of its users with its own private key
  (RS256) and publishes the public ones; Oro checks the signature, the issuer and the audience `oro`, and a token of
  one product is never accepted as the other's.
- Every row carries its product, and every reference is a composite foreign key with it, so the database itself keeps
  an Oro user out of a Umoja conversation and the reverse.
- Umoja tells Oro who belongs to each organization. Losing the access ends the conversations of that organization in
  the same transaction. Synchronizations of one organization run one at a time, and a stale one is refused.
- Row-level security: the API logs in with a login that has no rights of its own and takes a limited role in each
  transaction, with the product and the identity as transaction-local settings. The workers use a second login; the
  API's can never act as the system. Member roles are enforced by triggers, not only in Java. What a user may not see
  answers 404.
- Open streams are checked again: they end with their token, on a revocation, and every 30 s.
- The integrations call `/internal` with a key per product, kept as a hash and rotated without downtime; user tokens
  are refused there. Umoja's nginx keeps `/oro/internal` and the actuator off the public network.
- Limits: requests per user (429 with `Retry-After`), 5 streams per user and 10,000 in total, 1 GiB and 200 files a
  day per user, JSON bodies of 1 MiB, statement and idle-transaction timeouts on the database roles.
- In production Oro reads its secrets from files and refuses to start on settings of development (a weak password, no
  scanner, the API description on, an origin that is not HTTPS, ...).

## Reliability

- Outbox: an event is written in the same transaction as its change and published later by a worker, with retries,
  backoff and leases. Workers never claim the same event (`SKIP LOCKED`). A failed event alerts and is replayed with
  `ops/outbox-replay.sh`; an event too old to be worth sending makes its recipients reconnect and read again.
- Each stream has its own queue and writer, so a client that reads slowly holds up only itself; one 500 events
  behind is ended and reconnects.
- A send locks its conversation, which gives the order without gaps. Concurrent openings of the same direct
  conversation give one.
- Deleted files and uploads never attached are purged by a job.
- Backups: the database with pgBackRest (WAL every minute, point-in-time recovery, encrypted), the files with restic.
  Both have a restore drill that was run.
- Monitoring: JSON logs, metrics on a management port that is never published, Prometheus and Alertmanager with 18
  alert rules (failed events, old backups, a full disk, errors, slow sends, ...).

## Operations

- Schema changes: numbered migrations run by `1_ASATA_DATABASE/oro/migrate.sh`, each once, in a transaction, recorded
  with a checksum. They only add, so the previous backend still runs on the new schema and a rollback is the previous
  image.
- CI on GitHub Actions: the tests and the image on every push and pull request; `main` requires it to pass.
- Production: Docker Compose on one server, secrets as Docker secret files, Caddy in front with HTTPS. Only ports 80
  and 443 are open.
- Health: the container is healthy when Oro is ready (the database too), and restarted when Oro stops answering. A
  stop is graceful: the streams are told to reconnect, the outbox finishes its batch, the requests in flight finish.

## Code and tests

- 119 source files under `backend/src/main/java/com/asata/org/oro`: identity, conversation, message, attachment
  (storage, scan, purge), live, outbox, config (security, web, rate limits, metrics, production) and context (the
  database session).
- 157 integration tests in 33 classes. The query plans on 10 million messages are in `backend/perf`., on a PostgreSQL 17 container (Testcontainers) built by the real schema script.
- The schema is in `1_ASATA_DATABASE/CREATE_ORO_TABLES.sql`, its migrations in `1_ASATA_DATABASE/oro/migrations/`, the
  development data in `1_ASATA_DATABASE/oro/INSERT.sql`.
- Umoja already uses Oro: its frontend has a Messages page, and its backend sends its organizations and teams to Oro
  after every change and every 10 minutes.

## Not done yet

- An account system for Oro, to issue the tokens of its users, and blocking and reporting between Oro users.
- Deleting an account with its messages and files.
- A privacy notice (the retention of deleted content is written: [Retention](retention.md)).
- Runbooks.
- An alert receiver, the off-server backup buckets, and restore drills on a schedule.
- Object storage for the files (not needed while they stay on a backed-up volume of one server).
- Push notifications.
- Several instances: the streams, the presence and the rate limits live in the memory of one.
- Moderation by the admins, edit history and threads.

The full list is in the [readiness TODO](ORO_PRODUCTION_READINESS_TODO.md), the choices behind the limits in the
[technical debt](ORO_TECHNICAL_DEBT.md).
