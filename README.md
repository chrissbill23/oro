# oro
Oro Messaging is the Asata messaging service shared by two Asata products:

- **Oro**: personal messaging (direct messages, friends, family groups, communities).
- **Umoja**: business messaging inside an organization (direct messages, groups, one chat per team).

People sign in with their Asata account, the one Umoja and Mokili use too; each product signs its own tokens. The
messaging stores no password, email or credential: Oro's sign-in reads the Asata accounts through a database login of
its own, which reads nothing of the messaging. The two products share the service, and nothing in it lets one reach
the other's users or conversations.

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
| [`docs/`](docs) | Guides: quick start, connecting Umoja, implementation summary |
| [`docker-compose.yml`](docker-compose.yml) | Runs the backend, reading its settings from `backend/.env.local` |

The database schema is in the shared database repository, like the rest of Asata:
[`../1_ASATA_DATABASE/CREATE_ORO_TABLES.sql`](../1_ASATA_DATABASE/CREATE_ORO_TABLES.sql) (with `oro/INSERT.sql`, the
development data, and `oro/DROP.sql`).

## Docs

- [Quick start](docs/quick-start.md): run the database and Oro on Docker.
- [Connecting Umoja](docs/connecting-umoja.md): how Umoja's frontend and backend use Oro.
- [Retention](docs/retention.md): how long deleted messages and files survive, backups included (21 days at most).
- [Runbooks](docs/runbooks.md): what to do when an alert fires; revoking a user, a leaked key, restoring.
- [Deployment](docs/deployment.md): production on a server with Docker Compose: secrets, HTTPS, upgrades, rollbacks.
- [Implementation summary](docs/implementation-summary.md): what is built, how it is secured, what is left.
- [Backend](backend/README.md): API, authentication, schema, security, operations.

## Status

Working: everything listed above. Not done yet:

- Signing up, verifying an email and recovering a password for Oro's accounts (signing in to an Asata account works).
- An object storage for the files (they go to a local disk, scanned by ClamAV and backed up with restic).
- Push notifications.
- More than one instance (the live streams live in memory).

The details are under [Limitations](backend/README.md#limitations).
