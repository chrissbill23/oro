#!/bin/sh
# A restore drill of the files of the attachments: the latest snapshot restored into a throwaway container and compared,
# file by file (SHA-256), with the volume of the attachments. Neither the volume nor the repository is changed.
#   ./backend/backup/files-restore-check.sh            (from the root of oro)
set -eu
cd "$(dirname "$0")/.."
password=$(grep '^ORO_FILES_BACKUP_PASSWORD=' .env.local | cut -d= -f2-)
[ -n "$password" ] || { echo "ORO_FILES_BACKUP_PASSWORD unset in backend/.env.local" >&2; exit 1; }
docker run --rm -e RESTIC_REPOSITORY=/repo -e RESTIC_PASSWORD="$password" \
    -v oro_files-backup-repo:/repo:ro -v oro_attachments:/live:ro --entrypoint sh restic/restic:0.19.1 -c '
    set -e
    restic --no-lock snapshots --compact --tag oro-attachments | tail -2
    start=$(date +%s)
    restic --no-lock restore latest --tag oro-attachments --target /restored --quiet
    echo "restored in $(( $(date +%s) - start )) s"
    (cd /live && find . -type f ! -path "./.incoming/*" | sort | xargs -r sha256sum) > /tmp/live
    mkdir -p /restored/data/attachments
    (cd /restored/data/attachments && find . -type f | sort | xargs -r sha256sum) > /tmp/restored
    echo "live files: $(wc -l < /tmp/live), restored: $(wc -l < /tmp/restored)"
    cmp -s /tmp/live /tmp/restored && echo "identical: yes" || { echo "identical: NO (files changed since the snapshot?)"; exit 1; }'
