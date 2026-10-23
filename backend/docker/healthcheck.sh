#!/bin/sh
# Docker's health check of Oro (HEALTHCHECK of the image, every 15 s), on the management port.
#
# The status of the container is readiness: Oro accepts traffic and the database answers. A database down makes Oro
# unhealthy (docker ps, the deployments waiting for healthy), never restarts it: a restart would not bring the
# database back.
#
# Docker never restarts an unhealthy container by itself, so liveness acts here: once Oro has been up, three failures
# in a row stop it (SIGTERM, a graceful stop), six kill it, and Docker starts it again (restart: unless-stopped). Before
# Oro was first up (its start) nothing is counted.

port=${ORO_MANAGEMENT_PORT:-9082}

probe() {
    curl -fsS -o /dev/null --max-time 5 "http://127.0.0.1:$port/actuator/health/$1"
}

# the JVM: PID 1, or a child of the init of the container (init: true)
java_pid() {
    for dir in /proc/[0-9]*; do
        [ "$(cat "$dir/comm" 2>/dev/null)" = java ] && echo "${dir#/proc/}" && return
    done
}

# in the result of the check (docker inspect) and in the logs of the container
say() {
    echo "$1"
    echo "$1" 2>/dev/null > /proc/1/fd/2
}

# the count belongs to one JVM (its pid and start time): a restarted container keeps /tmp, never the count of the
# JVM before, which would stop the new one during its start
pid=$(java_pid)
[ -n "$pid" ] || exit 1
state=/tmp/oro-health/$pid-$(cut -d' ' -f22 "/proc/$pid/stat")
mkdir -p "$state"

if probe liveness; then
    touch "$state/was-up"
    echo 0 > "$state/failures"
else
    if [ -f "$state/was-up" ]; then
        failures=$(( $(cat "$state/failures" 2>/dev/null || echo 0) + 1 ))
        echo "$failures" > "$state/failures"
        if [ "$failures" -ge 6 ]; then
            say "liveness failed $failures times: killing Oro"
            kill -KILL "$pid"
        elif [ "$failures" -ge 3 ]; then
            say "liveness failed $failures times: stopping Oro"
            kill -TERM "$pid"
        fi
    fi
    exit 1
fi

probe readiness
