# Runbooks

What to do when an alert fires, and the planned operations, on the production server of
[Deployment](deployment.md). Each section: what it means, what to check, what to do. The last section says which
ones were rehearsed, and where.

## Before anything

- **Who**: the person on call, and who to tell when users are affected: **to fill in** (names, phone numbers, the
  channel of the incident). Until it is, nobody is on call.
- **The server**: `ssh` to it. Prometheus and Alertmanager listen on 127.0.0.1 only:
  `ssh -L 9090:127.0.0.1:9090 -L 9093:127.0.0.1:9093 <server>`, then http://localhost:9090/alerts and
  http://localhost:9093.
- **Compose**: in each repository, `dc` below is `docker compose -f docker-compose.yml -f docker-compose.production.yml`
  (`alias dc='docker compose -f docker-compose.yml -f docker-compose.production.yml'`).
- **Logs**: JSON, one line per event. Errors of the last 30 minutes:
  `dc logs --since 30m oro | grep '"level":"ERROR"'`. A user reports a failure: the error they got carries a
  `requestId`; `dc logs oro | grep '<requestId>'` gives every line of that request (`x-request-id`). Logs never hold
  the content of a message.
- **Health**: `dc ps oro` (`healthy`: ready, the database answering); `docker exec oro-oro-1 curl -s
  localhost:9082/actuator/health/readiness`.
- **Write down** what you saw and did, with the times: the account of the incident is written from it.

## The alerts

### OroDown (critical)

Prometheus cannot reach Oro for 2 minutes: no messages, no streams.

1. `dc ps oro`. Restarting (the health check restarts a stuck Oro after about 2 minutes): wait for `healthy`.
2. Exited: `dc logs --tail 200 oro`. At its start, `ProductionChecks` lists every setting it refuses; a database it
   cannot reach shows as connection errors.
3. Not starting because of the database: [Database down](#database-down).
4. Up but unreachable: the network `asataDB` (`docker network inspect asataDB`), the management port 9082.

### OroServerErrors (critical), OroErrorsLogged (warning)

More than 5 % of the requests answer 5xx for 5 minutes; or error lines were logged.

1. `dc logs --since 30m oro | grep '"level":"ERROR"'`, grouped by `message`: the same error many times is one cause.
2. `no database connection free`, 503s: Oro is saturated, see [OroDatabasePoolExhausted](#orodatabasepoolexhausted-warning).
3. Errors of the database (`SQLState`): its logs, `docker logs --since 30m asata-postgres-1`.
4. Since a deployment: [roll back](#roll-back).

### OroSendsSlow, OroConversationLockWaits (warning)

A send takes more than 0.5 s (p99), or sends wait more than 50 ms for the lock of their conversation.

1. Prometheus: `oro_conversation_lock_seconds` by conversation is not a label; find the busy one in the database:
   `SELECT conversation_id, count(*) FROM oro_message WHERE created_at > now() - interval '5 minutes' GROUP BY 1 ORDER BY 2 DESC LIMIT 5;`
2. One conversation with many senders (an announcement to thousands): the sends of a conversation take turns, a few
   hundred a second at most (`ORO-SCL-003`). Nothing to do at once; tell the product.
3. Slow everywhere: the database (CPU, disk: `docker stats`), or Oro's CPUs saturated: see below.

### OroDatabasePoolExhausted (warning)

Requests wait for a database connection for 2 minutes.

1. `docker stats --no-stream oro-oro-1 asata-postgres-1`. Oro at the limit of its CPUs: the load test of 2026-10-24
   saturated 2 CPUs at about 1,500 people active at once (`backend/perf`). More connections do not help; more CPUs,
   or a second instance (`ORO-SCL-006`), do.
2. The database slow (its CPU, a long query): `SELECT pid, now() - query_start, state, left(query, 80) FROM pg_stat_activity WHERE datname = 'asata' AND state <> 'idle' ORDER BY 2 DESC;`
3. A request that waits 5 s gets 503 with `Retry-After`: the clients retry.

### OroStreamsNearTheCap (warning)

More than 8,000 streams open on the instance; it takes 10,000, then refuses new ones (503).

A second instance is needed (`ORO-SCL-006`). Meanwhile, `ORO_LIVE_MAXSTREAMS` (`oro.live.max-streams`) can be raised
if the memory allows (`docker stats oro-oro-1`), and Oro restarted.

### OroDatabaseMetricsUnknown (warning)

Oro cannot read its gauges from the database for 5 minutes: the database, or the worker's login.

1. `docker exec oro-oro-1 curl -s localhost:9082/actuator/health/readiness`: `DOWN`, see [Database down](#database-down).
2. Up: the worker's login (`oro_worker`): its password in `secrets/oro_worker_db_password` and in the database
   (`production-logins.sql`), its connections (`dc logs oro | grep oro-system`).

### OroOutboxLag (critical)

The oldest live event waits more than a minute: users see new messages only when they reload.

1. `dc logs --since 10m oro | grep -i outbox`. The worker stops when Oro stops; it may be stuck on the database.
2. `SELECT status, count(*), min(created_at) FROM oro_outbox_event WHERE status <> 'processed' GROUP BY 1;` (in
   `asata-postgres-1`, as `$POSTGRES_USER`).
3. Restart Oro (`dc restart oro`): events claimed and not finished are taken again after their lease (30 s). Events
   older than 2 minutes are not sent: their recipients reconnect and read again, nothing is lost.

### OroOutboxFailed (warning)

An event failed 10 times: a bug, or a dependency that keeps failing.

1. `backend/ops/outbox-replay.sh list`: the failed events, their type and `error` (the class of the exception).
2. Find the cause in the logs around `failed` (`dc logs oro | grep '<event id>'`), fix it.
3. `backend/ops/outbox-replay.sh <id>…` or `all`. Live events replayed late make their recipients reconnect.

### OroScansWaiting (warning)

Files wait more than 10 minutes for their scan: they cannot be downloaded.

1. `dc ps clamav`: its first start downloads the signatures (a few minutes); it needs about 1.5 GB of memory.
2. `dc logs --tail 100 clamav`. Restart it (`dc restart clamav`): the files waiting are scanned again every minute.
3. Never set `ORO_ATTACHMENTS_SCAN=none` in production: `ProductionChecks` refuses it.

### OroAttachmentsDiskLow (warning), OroAttachmentsDiskFull (critical)

Less than 5 GB, then 1.5 GB, on the disk of the files; under 1 GiB uploads are refused (503).

See [A disk fills up](#a-disk-fills-up).

### DatabaseBackupTooOld, DatabaseBackupRepositoryUnhealthy, DatabaseBackupStateUnknown, WalArchivingFailing

The backups of the database (pgBackRest, `1_ASATA_DATABASE`). Until fixed, a point-in-time recovery may be impossible,
and deleted data stays longer than the 21 days of [Retention](retention.md).

1. `docker exec asata-backup-1 pgbackrest info`: `status: ok`, the last backups.
2. `cd 1_ASATA_DATABASE && dc logs --tail 100 backup`: `FAILED` and why (the bucket, its keys, the network).
3. WAL archiving: `SELECT archived_count, failed_count, last_failed_wal, last_failed_time FROM pg_stat_archiver;`
   and `docker logs --since 1h asata-postgres-1 | grep archive`. The WAL piles up on the database's disk meanwhile
   ([A disk fills up](#a-disk-fills-up)).
4. State unknown: `dc ps backup-exporter`, `dc logs backup-exporter` (the passphrase, `PGBACKREST_CIPHER_PASS`).
5. Fixed: `docker exec asata-backup-1 pgbackrest backup --type=diff`, then `pgbackrest info`.

### FilesBackupTooOld (warning)

No snapshot of the files for 3 hours.

1. `dc logs --tail 100 files-backup` (in `oro`): `snapshot FAILED`, `forget FAILED` and why (the bucket, its keys,
   `secrets/files_backup_password`).
2. Fixed: `dc restart files-backup` takes a snapshot at once.

## Situations

### Database down

Every product is down with it (the database is shared).

1. `cd 1_ASATA_DATABASE && dc ps postgres && dc logs --tail 200 postgres`.
2. The disk full: [A disk fills up](#a-disk-fills-up). Recovering after a crash: it replays its WAL, wait for
   `database system is ready to accept connections`.
3. Its data lost or broken: [Restore](#restore).

### A disk fills up

```bash
df -h                                                    # which disk
docker system df                                         # images, containers, volumes, build cache
docker exec oro-oro-1 df -h /data/attachments            # the files of Oro
docker exec asata-postgres-1 sh -c 'du -sh "$PGDATA"/pg_wal "$PGDATA"'   # the database, its WAL
```

- **The files of Oro**: the purge removes what Oro no longer keeps every 15 minutes. Past that, the volume needs more
  space, or the files an object storage (`ORO-LCH-003`).
- **The WAL of the database** grows when archiving fails: fix [the backups](#databasebackuptooold-databasebackuprepositoryunhealthy-databasebackupstateunknown-walarchivingfailing),
  never delete WAL by hand.
- **Docker**: old images and build cache: `docker image prune -a` and `docker builder prune` (never `docker volume
  prune`: it deletes the data of stopped containers). The logs of the containers are rotated (5 × 50 MB each).

### Revoke a user at once

A stolen account, someone who must lose access now.

1. In Oro, at once: its streams close with the commit, its next request is refused (403). On the server (`/internal`
   is never public), with Umoja's integration key, through Oro's container (the key on the input of curl, never in a
   command line):
   ```bash
   KEY=$(grep '^UMOJA_INTEGRATION_KEY=' ~/asata/umoja/backend/.env.local | cut -d= -f2-)
   printf 'x-integration-key: %s\n' "$KEY" | docker exec -i oro-oro-1 curl -s -w '%{http_code}\n' -X POST -H @- \
     localhost:8082/oro/internal/identities/<user id>/suspend        # 204
   unset KEY
   ```
   `reactivate` undoes it; `deactivate` is final (it leaves every conversation).
2. In Umoja: stop the account from signing in. Until then it may still get tokens (its access token lives 15
   minutes, its session longer), but Oro refuses them while the identity is suspended.

### A key leaked

- **An integration key**: the backend README, Rotating an integration key: remove its hash, restart Oro, at once.
- **Umoja's signing key**: a new key in `UMOJA_JWT_SIGNING_KEY`, **no** `UMOJA_JWT_PREVIOUS_KEYS` (every token signed
  with the leaked one must die), restart Umoja, then restart Oro: Oro keeps the public keys it fetched up to 5
  minutes, a restart drops them. Everyone signs in again.
- **A password of a database login**: a new file in `secrets/` of both repositories, `production-logins.sql`, restart
  Oro (Deployment, Rotations).
- **The passphrase of a backup**: the backups made with it are readable by whoever has it and the bucket. Rotate the
  bucket's keys first (the bucket is the other half); then a new repository with a new passphrase, a full backup,
  and the old repository deleted once the new one is checked.

## Planned operations

### Deploy, upgrade

[Deployment](deployment.md): the server once, then Upgrades: CI green, the schema first (`oro/migrate.sh`), then Oro;
wait for `healthy`. The stop of Oro is graceful: the streams reconnect by themselves.

### Roll back

The previous commit of Oro, built and started again: `git checkout <previous> && dc up -d --build oro`. The schema
stays (its migrations only add, [migrations](../../1_ASATA_DATABASE/oro/migrations/README.md)). A migration that went
wrong is undone by the next one.

### Rotate

Integration keys every 6 to 12 months (backend README, Rotating an integration key); Umoja's signing key with
`UMOJA_JWT_PREVIOUS_KEYS` for 15 minutes; the passwords of the logins (Deployment, Rotations).

### Restore

- The database to a point in time: `1_ASATA_DATABASE/README.md`, Recovering the database; then Oro: the backend
  README, Recovering Oro; then what was deleted since: [Retention](retention.md), Restoring a backup.
- Checks that the backups restore: `1_ASATA_DATABASE/backup/restore-drill.sh` (the database, to a throwaway
  container) and `backend/backup/files-restore-check.sh` (the files): monthly at least (`ORO-TST-010`).

## Rehearsed

| Procedure | Rehearsed | Where |
|---|---|---|
| A stuck Oro restarted by its health check | 2026-10-24 | local stack (a frozen JVM: restarted after 2 minutes) |
| A graceful stop with streams open | 2026-10-24 | local stack (`docker stop`: 1 s, `reconnect` heard) |
| Revoke a user at once (suspend, reactivate), the commands above | 2026-10-24 | local stack |
| Replay failed events (`outbox-replay.sh list`) | 2026-10-24 | local stack; the replay itself in the tests |
| Restore drill of the database (point in time) | 2026-10-23 | local stack (`restore-drill.sh`, 5 s) |
| Restore check of the files | 2026-10-23 | local stack (`files-restore-check.sh`) |
| Schema upgrade (`migrate.sh`) | 2026-10-24 | local stack |
| Rotating an integration key | no | needs Umoja running |
| Rotating Umoja's signing key | no | |
| A full recovery in place (the database, then Oro, then the deleted again) | no | needs a staging server |
| Deploying a new server from Deployment | no | needs a server |
| Everything on staging, by following this page | no | `ORO-LCH-016`: no staging yet |
