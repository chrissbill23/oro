#!/bin/sh
# One load-test run of Oro, end to end, on the load-test stack only (containers oro-plans, oro-load, oro-load-clamav,
# oro-streams, oro-load-k6 on the network oro-load-net): the data restored, Oro started with CPUS, ClamAV if SCAN is
# clamav, the streams, the samplers, a warm-up, then each level of LEVELS (RAMP s to reach it, HOLD s held), a
# restart during RESTART_LEVEL if asked, a drain, the delivery checked against the database, everything stopped.
# Results in $LOAD_DIR/$RUN: README (the settings), stages.txt, k6-<level>.{json,txt}, oro-metrics.txt, cpu.txt,
# streams.txt, stream-events.txt, truth.tsv, restart.txt, oro.jfr, oro.log; then analyze.py writes report.md.
#
# Needs in $LOAD_DIR (made per run, never committed: README, Running it): tokens.json, jwks.json, orgs.json,
# busy.json, api_pw, worker_pw, auth_pw; the container oro-plans with the generated data and the logins; the volume
# oro-plans-base (./db-snapshot.sh save); the image $IMAGE.
#
#   RUN=r4-2cpu CPUS=2 LOAD_DIR=... ./run.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
: "${RUN:?RUN: the name of the run (its folder)}"
: "${CPUS:?CPUS: the CPUs of Oro (docker --cpus)}"
LOAD_DIR=${LOAD_DIR:-$here}
MEMORY=${MEMORY:-2g}
IMAGE=${IMAGE:-oro-oro:latest}
LEVELS=${LEVELS:-"500 1000 1500 2000 3000 4000"}
RAMP=${RAMP:-60}
HOLD=${HOLD:-120}
WARMUP=${WARMUP:-"200:60"}
STREAMS=${STREAMS:-5000}
TRACKED=${TRACKED:-300}
SCAN=${SCAN:-clamav}
LIST=${LIST:-events}
BUSY_RATE=${BUSY_RATE:-0}
UPLOAD_RATE=${UPLOAD_RATE:-0.01}
UPLOAD_KB=${UPLOAD_KB:-20}
DOWNLOAD_CHECK=${DOWNLOAD_CHECK:-1}
RESTART_LEVEL=${RESTART_LEVEL:-}
RESTART_AFTER=${RESTART_AFTER:-$((RAMP + 40))}
PROFILE_LEVEL=${PROFILE_LEVEL:-}
PROFILE_SECONDS=${PROFILE_SECONDS:-60}
DRAIN=${DRAIN:-90}
RESTORE=${RESTORE:-1}
INTERVAL=${INTERVAL:-5}

out="$LOAD_DIR/$RUN"
[ -e "$out" ] && { echo "run.sh: $out exists: another RUN name" >&2; exit 2; }
# the scripts the containers run, beside the data they read
[ "$LOAD_DIR" != "$here" ] && cp "$here/chat.js" "$here/Streams.java" "$LOAD_DIR/"
for f in tokens.json jwks.json orgs.json busy.json api_pw worker_pw auth_pw; do
  [ -s "$LOAD_DIR/$f" ] || { echo "run.sh: $LOAD_DIR/$f missing (README, Running it)" >&2; exit 2; }
done
docker image inspect "$IMAGE" >/dev/null 2>&1 || { echo "run.sh: no image $IMAGE" >&2; exit 2; }
# a laptop on battery slows down or sleeps: no comparable run (run it under caffeinate -ims, on mains power)
if command -v pmset >/dev/null && pmset -g batt | head -1 | grep -q "Battery Power" && [ "${ALLOW_BATTERY:-}" != 1 ]; then
  echo "run.sh: on battery power: plug the machine in (or ALLOW_BATTERY=1)" >&2
  exit 2
fi
docker container inspect oro-plans >/dev/null 2>&1 || { echo "run.sh: no container oro-plans (the database of the load tests)" >&2; exit 2; }
for c in oro-load oro-load-clamav oro-streams oro-load-k6; do
  docker container inspect "$c" >/dev/null 2>&1 && { echo "run.sh: $c exists: a run in progress? (docker rm -fv $c)" >&2; exit 2; }
done
mkdir -p "$out"
ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }

samplers=""
cleanup() {
  [ -n "$samplers" ] && kill $samplers 2>/dev/null || true
  docker logs oro-load > "$out/oro.log" 2>&1 || true
  docker rm -fv oro-load oro-streams oro-load-clamav oro-load-k6 >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

# ---------- the data and the stack ----------
[ "$RESTORE" = 1 ] && "$here/db-snapshot.sh" restore
docker network inspect oro-load-net >/dev/null 2>&1 || docker network create oro-load-net >/dev/null
docker network connect oro-load-net oro-plans 2>/dev/null || true
docker start oro-plans >/dev/null
until docker exec oro-plans pg_isready -U postgres -d asata >/dev/null 2>&1; do sleep 1; done

if [ "$SCAN" = clamav ]; then
  docker volume inspect oro-load-clamav-db >/dev/null 2>&1 || {
    docker volume create oro-load-clamav-db >/dev/null
    # the signatures of the dev stack, if it has them: no download of a few minutes
    docker volume inspect oro_clamav-signatures >/dev/null 2>&1 && docker run --rm -v oro_clamav-signatures:/from:ro \
      -v oro-load-clamav-db:/to alpine:3.22 cp -a /from/. /to/
  }
  docker run -d --name oro-load-clamav --label asata.loadtest=true --network oro-load-net \
    -e CLAMD_CONF_StreamMaxLength=110M -e CLAMD_CONF_MaxFileSize=110M -e CLAMD_CONF_MaxScanSize=220M \
    -v oro-load-clamav-db:/var/lib/clamav clamav/clamav:1.5.4-debian13-slim >/dev/null
  until [ "$(docker inspect -f '{{.State.Health.Status}}' oro-load-clamav)" = healthy ]; do sleep 3; done
fi

jfr=""
if [ -n "$PROFILE_LEVEL" ]; then
  # Java Flight Recorder, settings profile, during the hold of PROFILE_LEVEL: from the start of Oro, the warm-up, the
  # levels before it and its ramp, then 30 s more (k6 starting); measured apart from the runs it would slow
  before=0
  for level in $LEVELS; do
    [ "$level" = "$PROFILE_LEVEL" ] && break
    before=$((before + RAMP + HOLD + 15))
  done
  delay=$(( ${WARMUP#*:} + 15 + before + RAMP + 30 + 40 ))
  jfr="-XX:StartFlightRecording=settings=profile,delay=${delay}s,duration=${PROFILE_SECONDS}s,filename=/tmp/oro.jfr"
fi
umask 077
python3 - "$LOAD_DIR" "$out/oro.env" "$SCAN" "$jfr" "${EXTRA_ENV:-}" <<'PY'
import json, sys
d, path, scan, jfr, extra = sys.argv[1:6]
read = lambda f: open(f"{d}/{f}").read().strip()
env = {
    "SPRING_DATASOURCE_URL": "jdbc:postgresql://oro-plans:5432/asata",
    "SPRING_DATASOURCE_USERNAME": "oro_api", "SPRING_DATASOURCE_PASSWORD": read("api_pw"),
    "ORO_SYSTEM_DATASOURCE_USERNAME": "oro_worker", "ORO_SYSTEM_DATASOURCE_PASSWORD": read("worker_pw"),
    "ORO_ACCOUNT_DATASOURCE_USERNAME": "oro_auth", "ORO_ACCOUNT_DATASOURCE_PASSWORD": read("auth_pw"),
    "ORO_OUTBOX_ENABLED": "true", "ORO_ATTACHMENTS_SCAN": scan, "ORO_CLAMAV_HOST": "oro-load-clamav",
    "ORO_MULTIPART_DIR": "/data/attachments/.incoming", "ORO_API_DOCS": "false", "UMOJA_JWKS_URI": "",
    "ORO_PUSH_ENABLED": "false", "LOGGING_STRUCTURED_FORMAT_CONSOLE": "ecs",
    "JAVA_OPTS": ("-XX:MaxRAMPercentage=75 " + jfr).strip(),
    "SPRING_APPLICATION_JSON": json.dumps({"oro": {"security": {"products": {"UMOJA": {"jwk-set": read("jwks.json"), "jwk-set-uri": ""}}}}}),
}
# EXTRA_ENV: settings of Oro changed for this run (KEY=VALUE ...), e.g. the sizes of its pools
for pair in extra.split():
    key, _, value = pair.partition("=")
    env[key] = value
with open(path, "w") as f:
    for k, v in env.items():
        f.write(f"{k}={v}\n")
PY
umask 022
docker run -d --name oro-load --label asata.loadtest=true --network oro-load-net --cpus="$CPUS" --memory="$MEMORY" \
  -p 127.0.0.1:9095:9082 --env-file "$out/oro.env" "$IMAGE" >/dev/null
rm -f "$out/oro.env"
until curl -sf --max-time 2 http://localhost:9095/actuator/health/readiness >/dev/null; do sleep 2; done

{
  echo "run: $RUN, $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git: $(git -C "$here" rev-parse --short HEAD)$(git -C "$here" diff --quiet HEAD -- ../../src || echo ' (backend sources changed since)')"
  echo "image: $IMAGE $(docker image inspect -f '{{.Id}} {{.Created}}' "$IMAGE")"
  echo "oro: --cpus=$CPUS --memory=$MEMORY, JAVA_OPTS -XX:MaxRAMPercentage=75${jfr:+ $jfr}, scan $SCAN${EXTRA_ENV:+, $EXTRA_ENV}"
  echo "database: oro-plans, $(docker exec oro-plans postgres --version), no CPU or memory limit, settings default, data restored: $RESTORE"
  echo "docker: $(docker info --format '{{.NCPU}} CPUs, {{.MemTotal}} bytes, {{.OperatingSystem}}')"
  command -v pmset >/dev/null && echo "power: $(pmset -g batt | head -1 | sed "s/Now drawing from //; s/'//g"), low power mode $(pmset -g | awk '/lowpowermode/ {print $2}')"
  echo "workload: levels $LEVELS (ramp $RAMP s, hold $HOLD s), warm-up $WARMUP, streams $STREAMS (tracked $TRACKED), list $LIST, busy $BUSY_RATE/s, uploads $UPLOAD_RATE x $UPLOAD_KB KB (download check $DOWNLOAD_CHECK)"
  echo "restart: ${RESTART_LEVEL:-none}${RESTART_LEVEL:+ at $RESTART_AFTER s}; profile: ${PROFILE_LEVEL:-none}${PROFILE_LEVEL:+ ($PROFILE_SECONDS s)}"
} > "$out/README"

# ---------- the samplers and the streams ----------
METRICS=http://localhost:9095/actuator/prometheus INTERVAL=$INTERVAL "$here/sample.sh" "$out/oro-metrics.txt" & samplers="$samplers $!"
(while true; do
   # the containers there now (docker stats fails for all when one is missing: no ClamAV with SCAN=none)
   docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' $(docker ps --format '{{.Names}}' --filter label=asata.loadtest=true; echo oro-plans) 2>/dev/null \
     | sed "s/^/$(ms) /"
   sleep "$INTERVAL"
 done) > "$out/cpu.txt" & samplers="$samplers $!"

docker run -d --name oro-streams --label asata.loadtest=true --network oro-load-net -v "$LOAD_DIR:/load" -w /load \
  eclipse-temurin:25-jdk java -Xmx1g Streams.java http://oro-load:8082/oro tokens.json orgs.json "$STREAMS" 86400 \
  "$TRACKED" "$RUN/stream-events.txt" >/dev/null
# all of them open
sleep 40
echo "streams $(ms)" > "$out/stages.txt"

k6() {
  docker run --rm --name oro-load-k6 --label asata.loadtest=true --network oro-load-net --user "$(id -u)" -v "$LOAD_DIR:/load" -w /load \
    -e STAGES="$1" -e LIST="$LIST" -e BUSY_RATE="$BUSY_RATE" -e UPLOAD_RATE="$UPLOAD_RATE" -e UPLOAD_KB="$UPLOAD_KB" \
    -e DOWNLOAD_CHECK="$DOWNLOAD_CHECK" -e SCAN_CHECK="$2" -e SUMMARY="$RUN/$3.json" \
    grafana/k6:1.0.0 run --quiet chat.js > "$out/$3.txt" 2>&1 || true
}

# ---------- the warm-up, then each level ----------
echo "warmup $(ms)" >> "$out/stages.txt"
k6 "$WARMUP" "$([ "$SCAN" = clamav ] && echo 1 || echo 0)" warmup
grep -q "SCAN_CHECK" "$out/warmup.txt" && { echo "run.sh: the scan check failed (warmup.txt)" >&2; exit 1; }
for level in $LEVELS; do
  start=$(ms)
  if [ "$level" = "$RESTART_LEVEL" ]; then
    (sleep "$RESTART_AFTER"; LOADTEST_RESTART_OK=oro-load "$here/restart.sh" oro-load http://localhost:9095 "$out/restart.txt") \
      > "$out/restart.log" 2>&1 &
  fi
  k6 "$level:$RAMP,$level:$HOLD" 0 "k6-$level"
  # the power at its end: a level that ran on battery is not comparable (report.md says so)
  power=$(command -v pmset >/dev/null && pmset -g batt | head -1 | grep -q "Battery Power" && echo battery || echo ac)
  echo "level $level $start $((start + RAMP * 1000)) $(ms) $power" >> "$out/stages.txt"
  sleep 15
done
echo "end $(ms)" >> "$out/stages.txt"

# ---------- the drain, the delivery against the database ----------
sleep "$DRAIN"
docker stop -t 30 oro-streams >/dev/null
docker logs oro-streams > "$out/streams.txt" 2>&1
[ -n "$PROFILE_LEVEL" ] && docker cp oro-load:/tmp/oro.jfr "$out/oro.jfr" 2>/dev/null || true
python3 "$here/analyze.py" truth-query "$out" > "$out/truth.sql"
docker exec -i oro-plans psql -U postgres -d asata -At -F ' ' < "$out/truth.sql" > "$out/truth.tsv"
python3 "$here/analyze.py" report "$out" > "$out/report.md"
echo "run.sh: $RUN done: $out/report.md"
