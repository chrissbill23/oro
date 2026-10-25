# Retention of deleted content

How long what a user deletes survives in Oro, and where. The rule: **a deleted message or file is gone from Oro at
once, and from everything Oro keeps after 21 days at most.** The privacy notice of Oro and the terms of Umoja's
customers state the same (`ORO-LCH-018`).

## What deleting does

A sender deletes a message (`DELETE /messages/{id}`):

| Where | Gone | How |
|---|---|---|
| The API, the clients | At once | The message is a tombstone: its place in the conversation, no text, no metadata. Nobody can read it back, its sender included |
| The database row | At once | `body` NULL and `metadata` `{}` in the same transaction (a check constraint forbids anything else); the files of the message are marked |
| The files of the message | Within 15 minutes | `AttachmentPurge` deletes them from the storage (every 15 minutes) |
| Old versions of the row in PostgreSQL | Minutes to hours | Until autovacuum reuses the space; not readable through Oro |
| Browsers | 5 minutes | A downloaded file may be cached `private, max-age=300` |
| Database backups (pgBackRest) | 21 days | Three weekly full backups kept, with their WAL: the oldest is about 21 days old when it expires |
| File backups (restic) | 21 days | A snapshot an hour for a day, then one a day for 21 days |

The 21 days can stretch by a few hours when a weekly backup runs late, and further when the backups stop altogether:
nothing expires while no new backup is made. The alerts `DatabaseBackupTooOld` and `FilesBackupTooOld` say so.

The same holds for what the purge removes by itself: uploads never attached to a message (after 24 hours), uploads
never received (after an hour), files found infected.

## Erasing a person

A person's data in Oro goes at once and for good when they ask: an Oro person with its password (`POST /me/erase`),
or Umoja for one of its people (`POST /internal/identities/{userId}/erase`; Umoja's people ask their organization).

| What | What happens |
|---|---|
| Its identity | Deactivated at once: it leaves every conversation, its streams close, its token opens nothing |
| Its messages | Deletions, within a minute (the erasure works a batch at a time): no text, no metadata, no reactions; their files purged |
| Its reactions on the messages of others, its uploads never sent, its blocks (made or received), its push subscriptions | Gone |
| The system messages that name it ("X joined, added by Y") and the mentions of it in the messages of others | Name `erased-<id>` instead: the others' texts stay, never marked edited |
| Its identity at the end | Its external id becomes `erased-<id>`: nothing that stays links to the person |
| Its Asata account, its Umoja and Mokili data | Untouched: Oro erases Oro's data only. It may come back to Oro, as someone new |

Then the backups hold it for 21 days at most, as any deletion.

## Reports

A report (`POST /reports`) keeps a copy of the messages reported (their text, their sender as an internal id), for
review, even if their sender deletes them or erases its data: **90 days**, then deleted (every hour). That is what lets
harassment be acted on; it is the one place a deleted message lives on, for that long.

## What Oro does not hold

- **Logs** never hold the content of a message or a file, nor database rows (`LogPrivacyTest`); they hold ids.
- **Metrics** (Prometheus, 15 days) hold counts and times only.
- **Outbox events** (7 days once processed) hold ids and numbers only.
- **Pushes** carry who and where, never the text, encrypted for the browser: the push services keep them at most a day
  for a device that is off. Oro keeps which device it pushed an event to (ids only) for an hour (`oro_push_delivery`).

## Out of Oro's reach

What the members of a conversation copied, saved or captured before the deletion. The clients replace a deleted
message by its tombstone when they hear of it (`message`, change `deleted`, on the stream: Umoja reads the message
again); a device that was offline keeps what it had shown until it reconnects.

## The settings

| Setting | Where | Value |
|---|---|---|
| `PGBACKREST_RETENTION_FULL` | `1_ASATA_DATABASE/docker-compose.yml` | 3 (a full backup every 7 days, `BACKUP_FULL_EVERY_DAYS`) |
| `restic forget --keep-hourly 24 --keep-within-daily 21d` | `backend/backup/files-backup.sh` | 21 days |
| `oro.attachments.purge.interval` | `application.yml` | 15 minutes |

The database backups cover the whole Asata database: Umoja's data follows the same 21 days. A change of any of these
numbers changes the policy: change this page, and the notices, with it.

## Restoring a backup

A restore to a point in time brings back what was deleted after that point, and the restored database no longer knows
it was. When the database being replaced still runs, list what it deleted since that point before restoring, and
delete it again after, before the users are let in:

```sql
-- on the database being replaced, as oro_owner
SELECT id FROM oro_message WHERE deleted_at > '<the point of the restore>';
```

When it is lost, those messages come back: say so to the users concerned. The backend README, Recovering Oro, has the
rest of the steps.
