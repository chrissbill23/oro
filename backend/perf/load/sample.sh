#!/bin/sh
# Oro's own numbers every 10 s during a run: outbox lag, pool waits, CPU, memory, streams
out=$1
echo "time outbox_oldest_s outbox_pending api_active api_pending sys_active sys_pending cpu heap_mb streams ended_slow stale" > $out
while true; do
  m=$(curl -s --max-time 5 localhost:9095/actuator/prometheus)
  v() { echo "$m" | grep -E "^$1(\{[^}]*$2[^}]*\})? " | head -1 | awk '{print $2}'; }
  heap=$(echo "$m" | grep -E '^jvm_memory_used_bytes\{.*area="heap"' | awk '{s+=$2} END {printf "%.0f", s/1048576}')
  echo "$(date +%H:%M:%S) $(v oro_outbox_oldest_pending_seconds) $(v oro_outbox_pending) $(v hikaricp_connections_active 'pool="oro-api"') $(v hikaricp_connections_pending 'pool="oro-api"') $(v hikaricp_connections_active 'pool="oro-system"') $(v hikaricp_connections_pending 'pool="oro-system"') $(v process_cpu_usage) $heap $(v oro_streams_open) $(v oro_streams_ended_slow_total) $(v oro_live_events_stale_total)" >> $out
  sleep 10
done
