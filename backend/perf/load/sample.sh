#!/bin/sh
# Oro's own numbers during a run, every INTERVAL seconds (10), from its metrics (METRICS: the Prometheus page of its
# management port, http://localhost:9095/actuator/prometheus). One line each: the epoch time (ms), the outbox (oldest
# pending in s, pending), the pools (oro-api, oro-system: connections active and waiting), the process CPU (0 to 1,
# of the CPUs it may use), the heap (MB), the streams open, ended for being slow, the stale events, the scans (count,
# total seconds). After a restart the counters start again from 0; a line of dashes is a moment Oro did not answer.
#   ./sample.sh <out file>
set -u
out=${1:?usage: sample.sh <out file>}
metrics=${METRICS:-http://localhost:9095/actuator/prometheus}
interval=${INTERVAL:-10}
echo "epoch_ms outbox_oldest_s outbox_pending api_active api_pending sys_active sys_pending cpu heap_mb streams ended_slow stale scans scan_seconds" > "$out"
while true; do
  now=$(python3 -c 'import time; print(int(time.time() * 1000))')
  if m=$(curl -sf --max-time 5 "$metrics"); then
    # the value is the last field (labels may hold spaces); a metric not there yet: -
    v() { x=$(echo "$m" | grep -E "^$1(\{[^}]*${2:-}[^}]*\})? " | head -1 | awk '{print $NF}'); echo "${x:--}"; }
    heap=$(echo "$m" | grep -E '^jvm_memory_used_bytes\{.*area="heap"' | awk '{s+=$NF} END {printf "%.0f", s/1048576}')
    scans=$(echo "$m" | grep -E '^oro_attachments_scan_duration_seconds_count' | awk '{s+=$NF} END {printf "%.0f", s}')
    scan_s=$(echo "$m" | grep -E '^oro_attachments_scan_duration_seconds_sum' | awk '{s+=$NF} END {printf "%.3f", s}')
    echo "$now $(v oro_outbox_oldest_pending_seconds) $(v oro_outbox_pending) $(v hikaricp_connections_active 'pool="oro-api"') $(v hikaricp_connections_pending 'pool="oro-api"') $(v hikaricp_connections_active 'pool="oro-system"') $(v hikaricp_connections_pending 'pool="oro-system"') $(v process_cpu_usage) $heap $(v oro_streams_open) $(v oro_streams_ended_slow_total) $(v oro_live_events_stale_total) $scans $scan_s" >> "$out"
  else
    echo "$now - - - - - - - - - - - - -" >> "$out"
  fi
  sleep "$interval"
done
