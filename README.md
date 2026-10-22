# oro
Oro Messaging is the Asata messaging service shared by two Asata products:

- **Oro**: personal messaging (direct messages, friends, family groups, communities).
- **Umoja**: business messaging inside an organization (direct messages, groups, one chat per team).

Each product keeps its own accounts and signs its own tokens; Oro stores no password, email or credential. The two
products share the service, and nothing in it lets one reach the other's users or conversations.

## What it does

- Direct conversations and groups, with owners and admins. The admin who adds a member chooses whether they see the
  history (privacy first by default).
- Messages in a strict order, with no gaps, and safe to retry (a resend never makes a duplicate). They can be edited,
  deleted (the content is erased), reacted to and searched.
- Read and delivered positions, unread counts, "Seen", typing and presence.
- Files: uploaded, then downloaded by the conversation's members only.
- A live stream per user (Server-Sent Events) for every change.
- Umoja team chats that follow the teams of the organization.

## Repository

| Path | What |
|---|---|
| [`backend/`](backend) | The service: Spring Boot 4.1, Java 25, PostgreSQL 17. **Its [README](backend/README.md) has the full documentation**: API, authentication, schema, security, operations |
| [`docs/`](docs) | Guides: quick start, connecting Umoja |
| [`docker-compose.yml`](docker-compose.yml) | Runs the backend, reading its settings from `backend/.env.local` |

The database schema is in the shared database repository, like the rest of Asata:
[`../1_ASATA_DATABASE/CREATE_ORO_TABLES.sql`](../1_ASATA_DATABASE/CREATE_ORO_TABLES.sql) (with `oro/INSERT.sql`, the
development data, and `oro/DROP.sql`).

## Docs

- [Quick start](docs/quick-start.md): run the database and Oro on Docker.
- [Connecting Umoja](docs/connecting-umoja.md): how Umoja's frontend and backend use Oro.
- [Backend](backend/README.md): API, authentication, schema, security, operations.

## Status

Working: everything listed above. Not done yet:

- Oro's own account system, which would issue its users' tokens.
- An object storage and a malware scanner for the files (they go to a local disk, recorded clean unscanned).
- Push notifications.
- More than one instance (the live streams live in memory).
- The purge jobs for deleted files.

The details are under [Limitations](backend/README.md#limitations).
