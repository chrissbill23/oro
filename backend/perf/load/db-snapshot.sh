#!/bin/sh
# The database of the load tests (the container oro-plans, its volume oro-plans-data) saved to, or restored from, the
# volume oro-plans-base: every run starts from the same data. Only these names; the shared database is never touched.
#   ./db-snapshot.sh save | restore
set -eu
what=${1:?usage: db-snapshot.sh save|restore}
case "$what" in
  save) from=oro-plans-data; to=oro-plans-base ;;
  restore) from=oro-plans-base; to=oro-plans-data
    docker volume inspect oro-plans-base >/dev/null 2>&1 || { echo "db-snapshot: no oro-plans-base: save first" >&2; exit 2; } ;;
  *) echo "usage: db-snapshot.sh save|restore" >&2; exit 2 ;;
esac
docker stop -t 120 oro-plans >/dev/null
docker run --rm -v "$from":/from:ro -v "$to":/to alpine:3.22 sh -c 'rm -rf /to/* /to/.[!.]* 2>/dev/null; cp -a /from/. /to/'
docker start oro-plans >/dev/null
until docker exec oro-plans pg_isready -U postgres -d asata >/dev/null 2>&1; do sleep 1; done
echo "db-snapshot: $what done ($from -> $to)"
