# Runs set aside

Not used for the results, kept for the record (their reports only):

- `cmp-2cpu-a-battery-draining`, `cmp-4cpu-a-battery-draining` (01:36 to 02:31): the first attempt, on battery from
  62 % down to 3 %, not under `caffeinate`. At 2 CPUs it broke at 2,000 people (p95 10 to 16 s) where the runs on
  mains power held (p95 about 400 ms).
- `cmp-4cpu-b-slept` (from 02:31): the machine went to sleep at 3 % of battery; a level of 3 minutes took 72 minutes,
  the next 4.5 hours (`report.md` marks it invalid).
- `cmp-2cpu-b-partly-battery` (09:37 to 10:04): unplugged at about 09:53 during the run; run again (`cmp-2cpu-b`).

Since then `run.sh` records the power at the end of each level, `report.md` marks the levels that ended on battery
and the levels that took far longer than planned, and the campaign runs under `caffeinate -ims`.
