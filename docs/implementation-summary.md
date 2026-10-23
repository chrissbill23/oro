# Implementation summary

Oro is the messaging service of Asata, shared by two products: Oro (personal messaging) and Umoja (business
messaging inside an organization). The backend is Spring Boot 4.1 on Java 25, with Spring Data JPA (Hibernate 7) on the
shared PostgreSQL 17 database. It runs on Docker at `http://localhost:8082/oro`.

## Features

- Conversations: direct ones (one per pair of people and per context) and groups, with an owner, admins and members.
  The admin who adds a member chooses whether they read the history; by default they do not.
- Messages in a strict order, without gaps. A retry with the same client message id returns the stored message
  instead of a duplicate. Messages can be edited and deleted (the content is erased), and searched within what the
  user may read.
- Reactions: any single emoji, skin tones, flags and ZWJ sequences included.
- Read and delivered positions, unread counts, "Seen", typing, presence and mute.
- Files uploaded through the API to a local storage, downloaded by the members only, with safe headers.
- A live stream per user (Server-Sent Events): messages, reactions, conversations, typing, reads and presence.
- Umoja team chats: one group per team, following its members, with system messages for who joins and leaves.

## Security and privacy

- Oro stores no password, email or credential. Each product signs the tokens of its users with its own secret and
  issuer; Oro checks them, and a token of one product is never accepted as the other's.
- Every row carries its product, and every reference is a composite foreign key with it, so the database itself keeps
  an Oro user out of a Umoja conversation and the reverse.
- Umoja tells Oro who belongs to each organization. Losing the access ends the conversations of that organization in
  the same transaction.
- Row-level security: the API logs in with a login that has no rights of its own and takes a limited role in each
  transaction, with the product and the identity as transaction-local settings. What a user may not see answers 404.
- The integrations call `/internal` with a key per product; user tokens are refused there.

## Reliability

- Outbox: an event is written in the same transaction as its change and published later by a worker, with retries,
  backoff and leases. Workers never claim the same event (`SKIP LOCKED`).
- A send locks its conversation, which gives the order without gaps. Concurrent openings of the same direct
  conversation give one.

## Code and tests

- 97 source files under `backend/src/main/java/com/asata/org/oro`: identity, conversation, message, attachment, live,
  outbox, config (security, web) and context (the database session).
- 66 integration tests in 13 classes, on a PostgreSQL 17 container (Testcontainers) built by the real schema script.
- The schema is in `1_ASATA_DATABASE/CREATE_ORO_TABLES.sql`, the development data in `1_ASATA_DATABASE/oro/INSERT.sql`.
- Umoja already uses Oro: its frontend has a Messages page, and its backend sends its organizations and teams to Oro
  after every change and every 10 minutes.

## Not done yet

- An account system for Oro, to issue the tokens of its users.
- An object storage and a malware scanner for the files: they go to a local disk and are recorded clean unscanned.
- Push notifications.
- Several instances: the streams and the presence live in the memory of one.
- The purge jobs for deleted files and uploads never attached.
- Rotation of the integration keys.
- Moderation by the admins, edit history and threads.

More details in the [backend README](../backend/README.md).
