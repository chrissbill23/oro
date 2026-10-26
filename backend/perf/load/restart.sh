#!/bin/sh
# A restart of Oro during a load test, timed: only a container of the load tests (named oro-load..., with the label
# asata.loadtest=true), and only when LOADTEST_RESTART_OK names it too, so that no other Oro can be restarted by
# mistake. docker restart gives it 45 s to stop (as stop_grace_period in production), then readiness is polled.
# Writes to <out file>: stop_ms (asked), started_ms (docker restart returned), ready_ms (readiness UP).
#   LOADTEST_RESTART_OK=oro-load ./restart.sh oro-load http://localhost:9095 restart.txt
set -eu
container=${1:?usage: restart.sh <container> <management url> <out file>}
management=${2:?usage: restart.sh <container> <management url> <out file>}
out=${3:?usage: restart.sh <container> <management url> <out file>}
case "$container" in
  oro-load|oro-load-*) ;;
  *) echo "restart.sh: $container is not a load-test container (oro-load...)" >&2; exit 2 ;;
esac
if [ "${LOADTEST_RESTART_OK:-}" != "$container" ]; then
  echo "restart.sh: set LOADTEST_RESTART_OK=$container to confirm" >&2
  exit 2
fi
if [ "$(docker inspect -f '{{index .Config.Labels "asata.loadtest"}}' "$container")" != "true" ]; then
  echo "restart.sh: $container has no label asata.loadtest=true" >&2
  exit 2
fi
ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
stop=$(ms)
docker restart -t 45 "$container" >/dev/null
started=$(ms)
until curl -sf --max-time 2 "$management/actuator/health/readiness" >/dev/null; do
  sleep 0.2
done
ready=$(ms)
printf 'stop_ms=%s\nstarted_ms=%s\nready_ms=%s\n' "$stop" "$started" "$ready" > "$out"
echo "restart: stopped and started in $(( started - stop )) ms, ready $(( ready - stop )) ms after the stop was asked"
