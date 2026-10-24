#!/bin/sh
# The failed outbox events (the alert OroOutboxFailed): listed, then put back to pending once their cause is fixed. A
# replayed event is published again at once; a live one, being old, makes its recipients reconnect and read again what
# changed instead (LiveOutboxEventHandler).
#
#   ops/outbox-replay.sh list               the failed events: id, type, attempts, error, when
#   ops/outbox-replay.sh all                every failed event back to pending
#   ops/outbox-replay.sh <id> [<id>...]     these failed events back to pending
#
# PSQL: how to reach the database as its superuser (it bypasses row-level security), the SQL on its input. By default
# the container of the shared database: docker exec -i asata-postgres-1 psql as $POSTGRES_USER, database asata.
set -eu

psql_db() {
    if [ -n "${PSQL:-}" ]; then
        $PSQL -X -q -v ON_ERROR_STOP=1
    else
        docker exec -i asata-postgres-1 sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d asata'
    fi
}

usage() {
    sed -n '6,8p' "$0" | sed 's/^# *//' >&2
    exit 2
}

[ $# -ge 1 ] || usage

case "$1" in
    list)
        psql_db <<'SQL'
SELECT id, event_type, attempt_count AS attempts, last_error_code AS error,
       to_char(created_at, 'YYYY-MM-DD HH24:MI:SS') AS created, to_char(updated_at, 'YYYY-MM-DD HH24:MI:SS') AS failed
FROM oro_outbox_event WHERE status = 'failed' ORDER BY created_at;
SQL
        exit 0
        ;;
    all)
        filter=""
        ;;
    *)
        ids=""
        for id in "$@"; do
            # a uuid only: nothing else reaches the SQL
            echo "$id" | grep -Eqx '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}' \
                || { echo "outbox-replay: not an event id: $id" >&2; exit 2; }
            ids="$ids${ids:+,}'$id'"
        done
        filter="AND id IN ($ids)"
        ;;
esac

psql_db <<SQL
\\pset tuples_only on
WITH replayed AS (
    UPDATE oro_outbox_event SET status = 'pending', next_attempt_at = now(), attempt_count = 0, locked_by = NULL,
           last_error_code = NULL, last_error_message = NULL
    WHERE status = 'failed' $filter
    RETURNING id)
SELECT 'outbox-replay: ' || count(*) || ' failed events back to pending' FROM replayed;
SQL
