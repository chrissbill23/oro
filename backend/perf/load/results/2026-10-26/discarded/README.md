# Runs set aside

Not used for the results, kept for the record (their reports only):

- `cmp-2cpu-a-battery-draining`, `cmp-4cpu-a-battery-draining` (01:36 to 02:31): the first attempt, on battery from
  62 % down to 3 %, not under `caffeinate`. At 2 CPUs it broke at 2,000 people (p95 10 to 16 s) where the runs on
  mains power held (p95 about 400 ms).
- `cmp-4cpu-b-slept` (from 02:31): the machine went to sleep at 3 % of battery; a level of 3 minutes took 72 minutes,
  the next 4.5 hours (`report.md` marks it invalid).
- `cmp-2cpu-b-partly-battery` (09:37 to 10:04): unplugged at about 09:53 during the run; run again (`cmp-2cpu-b`).

- `afternoon-*` (12:45 to 14:48): the sizes of the pools at 4 CPUs (10/5 to 40/20, cold and warmed), and the
  morning's image again. They disagree with each other and with the morning (10/5 at 3,000 people: p95 1.9 s, and
  20 s on the morning's image, against 0.4 s in the morning); the same load took twice the CPU in Oro and the
  database. Another project's load test (`sululu-perf-*`, its own k6) was running on the machine at 14:47, the load
  average about 20; when it started is not known (Docker keeps no older events). Not used. Since then `run.sh` records
  the CPU of every container and `report.md` marks a level where other containers used more than half a core.
- `pools-2cpu-20-10`: the tokens of the run expired at 13:26 (12 h), every request after it a 401. Since then `run.sh`
  checks that the tokens outlive the run.

Since then `run.sh` records the power at the end of each level, `report.md` marks the levels that ended on battery
and the levels that took far longer than planned, and the campaign runs under `caffeinate -ims`.
