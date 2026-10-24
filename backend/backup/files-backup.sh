#!/bin/sh
# The backups of the files of the attachments (the service files-backup of docker-compose.yml), with restic: encrypted,
# deduplicated snapshots of /data/attachments (the uploads being received aside), every FILES_BACKUP_INTERVAL seconds
# (an hour); the last 24 hourly and a daily one for 21 days kept: the window of the database's, after which a deleted
# file is in no backup (oro/docs/retention.md). Without RESTIC_PASSWORD (or RESTIC_PASSWORD_FILE) it makes none.
set -u
interval="${FILES_BACKUP_INTERVAL:-3600}"
if [ -z "${RESTIC_PASSWORD:-}" ] && [ ! -s "${RESTIC_PASSWORD_FILE:-/nonexistent}" ]; then
    echo "files-backup: ORO_FILES_BACKUP_PASSWORD unset: no backup is made, never in clear" >&2
    exec sleep infinity
fi
restic cat config >/dev/null 2>&1 || restic init || exit 1

# the weekly check of the backups (FILES_CHECK_INTERVAL): a tenth of the repository's data read and verified (a
# different tenth each week: all of it in ten weeks), then 20 files restored from the latest snapshot and compared with
# the volume (SHA-256), among the ones older than two snapshots. Its success: /status/files-check-last-success (the
# metric oro_restore_check_age_seconds{what="files"}, the alert RestoreCheckTooOld). Nothing restored stays.
check() {
    week=$(( $(date +%s) / 604800 % 10 + 1 ))
    restic check --quiet --read-data-subset="$week/10" || { echo "files-backup: check of the repository FAILED" >&2; return 1; }
    sample=$(cd /data/attachments && find . -type f ! -path "./.incoming/*" -mmin +$(( interval * 2 / 60 + 1 )) | shuf -n 20)
    checked=0
    for file in $sample; do
        live=$(sha256sum "/data/attachments/$file" | cut -d' ' -f1)
        restored=$(restic dump --quiet latest "/data/attachments/${file#./}" --tag oro-attachments | sha256sum | cut -d' ' -f1)
        if [ "$live" != "$restored" ]; then
            echo "files-backup: a file restored differs from the volume: $file" >&2
            return 1
        fi
        checked=$((checked + 1))
    done
    echo "files-backup: check done (data $week/10, $checked file(s) restored and compared)"
    date +%s > /status/files-check-last-success.tmp && mv /status/files-check-last-success.tmp /status/files-check-last-success
}
check_interval="${FILES_CHECK_INTERVAL:-604800}"

while true; do
    if restic backup --quiet --tag oro-attachments --exclude /data/attachments/.incoming /data/attachments; then
        echo "files-backup: snapshot done"
        # its time, for the metrics of Oro (oro_files_backup_age_seconds) and its alert
        date +%s > /status/last-success.tmp && mv /status/last-success.tmp /status/last-success
        restic forget --quiet --tag oro-attachments --keep-hourly 24 --keep-within-daily 21d --prune \
            || echo "files-backup: forget FAILED" >&2
        last=$(cat /status/files-check-last-success 2>/dev/null || echo 0)
        if [ $(( $(date +%s) - last )) -ge "$check_interval" ]; then
            check || echo "files-backup: check FAILED" >&2
        fi
    else
        echo "files-backup: snapshot FAILED" >&2
    fi
    sleep "$interval"
done
