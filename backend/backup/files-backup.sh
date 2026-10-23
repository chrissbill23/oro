#!/bin/sh
# The backups of the files of the attachments (the service files-backup of docker-compose.yml), with restic: encrypted,
# deduplicated snapshots of /data/attachments (the uploads being received aside), every FILES_BACKUP_INTERVAL seconds
# (an hour); 24 hourly and 14 daily kept, the window of the database's. Without RESTIC_PASSWORD (or RESTIC_PASSWORD_FILE) it makes none.
set -u
interval="${FILES_BACKUP_INTERVAL:-3600}"
if [ -z "${RESTIC_PASSWORD:-}" ] && [ ! -s "${RESTIC_PASSWORD_FILE:-/nonexistent}" ]; then
    echo "files-backup: ORO_FILES_BACKUP_PASSWORD unset: no backup is made, never in clear" >&2
    exec sleep infinity
fi
restic cat config >/dev/null 2>&1 || restic init || exit 1
while true; do
    if restic backup --quiet --tag oro-attachments --exclude /data/attachments/.incoming /data/attachments; then
        echo "files-backup: snapshot done"
        # its time, for the metrics of Oro (oro_files_backup_age_seconds) and its alert
        date +%s > /status/last-success.tmp && mv /status/last-success.tmp /status/last-success
        restic forget --quiet --tag oro-attachments --keep-hourly 24 --keep-daily 14 --prune \
            || echo "files-backup: forget FAILED" >&2
    else
        echo "files-backup: snapshot FAILED" >&2
    fi
    sleep "$interval"
done
