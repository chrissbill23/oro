#!/usr/bin/env python3
"""The results of a load-test run (run.sh), read back.

  analyze.py truth-query <run dir>   the SQL of the ground truth: every message committed during the run in the
                                     conversations of the tracked clients (one line: user conv seq created_ms)
  analyze.py compare <run dir>...    the runs side by side, by level (the comparison of the README)
  analyze.py report <run dir>        report.md: per level, the operations (p50, p95, p99, outcomes), the requests per
                                     second, CPU, pools, outbox, streams; the delivery of the messages to the tracked
                                     clients (lost, duplicated, how late); the restart (ready, reconnected, caught up,
                                     outbox drained)

Measured in the hold of each level (after its ramp): the samples of oro-metrics.txt and cpu.txt; k6's numbers are
over the whole level (ramp and hold), as k6 gives them.
"""
import json
import os
import statistics
import sys
from collections import defaultdict


def read_stages(run):
    stages, marks = [], {}
    for line in open(os.path.join(run, "stages.txt")):
        p = line.split()
        if p[0] == "level":
            stages.append({"level": int(p[1]), "start": int(p[2]), "hold": int(p[3]), "end": int(p[4]),
                           "power": p[5] if len(p) > 5 else "unknown"})
        else:
            marks[p[0]] = int(p[1])
    return stages, marks


def pct(values, q):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * q
    lo, hi = int(k), min(int(k) + 1, len(values) - 1)
    return values[lo] + (values[hi] - values[lo]) * (k - lo)


def tracked_users(run):
    users = set()
    path = os.path.join(run, "stream-events.txt")
    if os.path.exists(path):
        for line in open(path):
            p = line.split()
            if p and p[0] == "S":
                users.add(p[2])
    return sorted(users)


def truth_query(run):
    stages, marks = read_stages(run)
    users = tracked_users(run)
    if not users:
        sys.exit("analyze.py: no tracked client in stream-events.txt")
    names = ",".join("'" + u.replace("'", "") + "'" for u in users)
    print(f"""SELECT i.external_user_id, m.conversation_id, m.seq, (extract(epoch FROM m.created_at) * 1000)::bigint
FROM oro_message m
JOIN oro_conversation_member cm ON cm.conversation_id = m.conversation_id AND cm.left_at IS NULL AND cm.pending_since IS NULL
JOIN oro_messaging_identity i ON i.id = cm.user_id
WHERE i.product_id = 'UMOJA' AND i.external_user_id IN ({names})
  AND m.created_at >= to_timestamp({marks['streams']} / 1000.0) AND m.created_at <= to_timestamp({marks['end']} / 1000.0);""")


def samples(run):
    rows = []
    path = os.path.join(run, "oro-metrics.txt")
    if not os.path.exists(path):
        return rows
    lines = open(path).read().split("\n")
    head = lines[0].split()
    for line in lines[1:]:
        p = line.split()
        if len(p) == len(head) - 1 and "stale" in head:
            # the samplers before 2026-10-26 01:40 wrote nothing for a metric not there yet (stale, before its first)
            p.insert(head.index("stale"), "-")
        if len(p) != len(head):
            continue
        row = {"epoch_ms": int(p[0])}
        for k, v in zip(head[1:], p[1:]):
            try:
                row[k] = float(v)
            except ValueError:
                row[k] = None
        rows.append(row)
    return rows


def cpu(run):
    """docker stats: (time, container, cores, memory MiB)"""
    out = []
    path = os.path.join(run, "cpu.txt")
    if not os.path.exists(path):
        return out
    for line in open(path):
        p = line.split()
        if len(p) < 4 or not p[2].endswith("%"):
            continue
        mem = p[3]
        mib = float(mem[:-3]) * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024}.get(mem[-3:], 1) if mem[-3:] in ("KiB", "MiB", "GiB") else None
        out.append((int(p[0]), p[1], float(p[2][:-1]) / 100, mib))
    return out


def window(rows, start, end, key):
    # NaN: a gauge not read yet (just after a start)
    return [r[key] for r in rows if start <= r["epoch_ms"] <= end and r.get(key) is not None and r[key] == r[key]]


def fmt(v, digits=0):
    if v is None:
        return "-"
    return f"{v:,.{digits}f}"


def report(run):
    stages, marks = read_stages(run)
    print(f"# Load-test run `{os.path.basename(run.rstrip('/'))}`\n")
    readme = os.path.join(run, "README")
    if os.path.exists(readme):
        print("```\n" + open(readme).read().strip() + "\n```\n")
    rows, stats = samples(run), cpu(run)
    # a level much longer than planned: the machine slept or stalled, the run is not comparable
    for s in stages:
        if s["power"] == "battery":
            print(f"**NOT COMPARABLE: level {s['level']} ended on battery power.**\n")
        planned = s["hold"] - s["start"]
        hold = s["end"] - s["hold"]
        if hold > 3 * max(planned, 60000):
            print(f"**INVALID: level {s['level']} took {(s['end'] - s['start']) / 1000:,.0f} s (a stall or a sleep of "
                  "the machine): do not use this run.**\n")

    print("## Per level\n")
    print("Latency in ms (k6, ramp and hold). Outcomes: ok / rate limited (429) / not ready (409, a file not scanned "
          "yet) / client error / unavailable (502-504) / server error / network. CPU in cores, averaged over the hold "
          "(max in brackets); pools: most connections active, most waiting; outbox: most pending, oldest (s).\n")
    print("| Level | Op | n | p50 | p95 | p99 | max | ok | 429 | 409 | 4xx | 5xx unavail. | 5xx other | network |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    totals = []
    for s in stages:
        path = os.path.join(run, f"k6-{s['level']}.json")
        if not os.path.exists(path):
            continue
        k = json.load(open(path))
        for op, o in k["ops"].items():
            oc = o["outcomes"]
            print(f"| {s['level']} | {op} | {o['count']} | {fmt(o['p50'], 1)} | {fmt(o['p95'], 1)} | {fmt(o['p99'], 1)} | "
                  f"{fmt(o['max'])} | {oc.get('ok', 0)} | {oc.get('rate_limited', 0)} | {oc.get('not_ready', 0)} | "
                  f"{oc.get('client_error', 0)} | {oc.get('unavailable', 0)} | {oc.get('server_error', 0)} | {oc.get('network', 0)} |")
        totals.append((s, k))

    print("\n| Level | Requests/s | Failed (not 2xx, 429 or expected 409) | Thresholds failed | Oro CPU | DB CPU | k6 CPU | Streams holder CPU | ClamAV CPU | Oro memory MiB | API pool active / waiting | System pool active / waiting | Outbox pending / oldest s | Streams open | Uploads available / pending | Download before clean |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for s, k in totals:
        a, b = s["hold"], s["end"]

        def c(name):
            v = [x[2] for x in stats if x[1] == name and a <= x[0] <= b]
            return f"{fmt(statistics.mean(v), 2)} ({fmt(max(v), 2)})" if v else "-"
        mem = [x[3] for x in stats if x[1] == "oro-load" and a <= x[0] <= b and x[3]]
        top = lambda key: max(window(rows, a, b, key), default=None)
        # failed: neither ok, nor rate limited, nor a download not scanned yet (all the operations)
        counts = defaultdict(int)
        for o in k["ops"].values():
            for name, n in o["outcomes"].items():
                counts[name] += n
        total = sum(counts.values())
        bad = total - counts["ok"] - counts["rate_limited"] - counts["not_ready"]
        failed = bad / total if total else None
        print(f"| {s['level']} | {fmt(k['requestsPerSecond'], 1)} | {fmt(100 * failed if failed is not None else None, 2)} % ({bad:,}) | "
              f"{', '.join(k['thresholdsFailed']) or 'none'} | {c('oro-load')} | {c('oro-plans')} | {c('oro-load-k6')} | "
              f"{c('oro-streams')} | {c('oro-load-clamav')} | {fmt(max(mem) if mem else None)} | "
              f"{fmt(top('api_active'))} / {fmt(top('api_pending'))} | {fmt(top('sys_active'))} / {fmt(top('sys_pending'))} | "
              f"{fmt(top('outbox_pending'))} / {fmt(top('outbox_oldest_s'), 1)} | "
              f"{fmt(min(window(rows, a, b, 'streams'), default=None))}-{fmt(top('streams'))} | "
              f"{fmt(k.get('uploadsAvailable'))} / {fmt(k.get('uploadsPending'))} | {fmt(k.get('downloadBeforeClean'))} |")

    scans = [(r["epoch_ms"], r.get("scans"), r.get("scan_seconds")) for r in rows if r.get("scans") is not None]
    if scans and scans[-1][1]:
        print(f"\nScans (Oro's timer, since its last start): {fmt(scans[-1][1])}, "
              f"average {fmt(1000 * scans[-1][2] / scans[-1][1], 1)} ms.")

    delivery(run, marks, stages)
    restart(run, rows, stages)


def delivery(run, marks, stages=()):
    events = os.path.join(run, "stream-events.txt")
    truth = os.path.join(run, "truth.tsv")
    if not (os.path.exists(events) and os.path.exists(truth)):
        return
    baseline, live, caught, hellos, ends, catchups, fails = {}, defaultdict(list), set(), defaultdict(list), defaultdict(list), [], defaultdict(int)
    gaps = []
    for line in open(events):
        p = line.split()
        if not p:
            continue
        kind, t = p[0], int(p[1])
        if kind == "S":
            baseline[(p[2], p[3])] = int(p[4])
        elif kind == "L":
            live[(p[2], p[3], int(p[4]))].append(t)
        elif kind == "C":
            caught.add((p[2], p[3], int(p[4])))
        elif kind == "H":
            hellos[p[2]].append(t)
        elif kind == "E":
            ends[p[2]].append(t)
        elif kind == "K":
            catchups.append((t, int(p[3])))
        elif kind == "F":
            fails[p[3]] += 1
        elif kind == "G":
            gaps.append((t, int(p[5]) - int(p[4]) - 1))
    users = {u for u, _ in baseline}
    expected, created = set(), {}
    for line in open(truth):
        p = line.split()
        if len(p) != 4 or p[0] not in users:
            continue
        key = (p[0], p[1], int(p[2]))
        # had at the first hello: not to be delivered
        if key[2] <= baseline.get((p[0], p[1]), 0) and (p[0], p[1]) in baseline:
            continue
        expected.add(key)
        created[key] = int(p[3])
    got_live = set(live)
    missing = expected - got_live - caught
    dup_live = sum(1 for v in live.values() if len(v) > 1)
    both = len(got_live & caught)
    lat = [live[k][0] - created[k] for k in expected & got_live]
    print("\n## Delivery to the tracked clients\n")
    print(f"- Tracked clients: {len(users)}; messages committed in their conversations during the run (after what "
          f"they had at their first hello): {len(expected):,}")
    print(f"- Got live: {len(expected & got_live):,}; caught up after a reconnection: {len((expected & caught) - got_live):,}; "
          f"**missing: {len(missing):,}**")
    print(f"- Duplicates: {dup_live:,} live events received twice; {both:,} messages got live and again by a catch-up "
          f"(the client keeps one: same conversation and seq)")
    if lat:
        print(f"- Delivery latency (commit to live event, ms), whole run: p50 {fmt(pct(lat, .5))}, p95 {fmt(pct(lat, .95))}, "
              f"p99 {fmt(pct(lat, .99))}, max {fmt(max(lat))}")
        for s in stages:
            part = [live[k][0] - created[k] for k in expected & got_live if s["hold"] <= created[k] <= s["end"]]
            if part:
                print(f"  - level {s['level']} (committed in its hold, {len(part):,}): p50 {fmt(pct(part, .5))}, "
                      f"p95 {fmt(pct(part, .95))}, p99 {fmt(pct(part, .99))}, max {fmt(max(part))}")
    print(f"- Gaps seen live (a message past the next seq, the missing ones read then): {len(gaps):,}"
          f"{f', {sum(n for _, n in gaps):,} messages' if gaps else ''}")
    print(f"- Hellos: {sum(len(v) for v in hellos.values()):,} (reconnections: {sum(max(0, len(v) - 1) for v in hellos.values()):,}); "
          f"failures seen by the clients: {dict(fails) or 'none'}")
    if missing:
        sample = sorted(missing)[:10]
        print(f"- Missing, first ones: {sample}")
    return hellos, ends, catchups


def restart(run, rows, stages):
    path = os.path.join(run, "restart.txt")
    if not os.path.exists(path):
        return
    r = dict(line.strip().split("=") for line in open(path) if "=" in line)
    stop, started, ready = int(r["stop_ms"]), int(r["started_ms"]), int(r["ready_ms"])
    print("\n## Restart under load\n")
    print(f"- Stopped and started again in {(started - stop) / 1000:.1f} s; ready {(ready - stop) / 1000:.1f} s after "
          "the stop was asked")
    events = os.path.join(run, "stream-events.txt")
    if os.path.exists(events):
        ended, back, done = {}, {}, {}
        for line in open(events):
            p = line.split()
            if not p:
                continue
            t = int(p[1])
            if p[0] == "E" and stop - 1000 <= t <= ready + 60000 and p[2] not in ended:
                ended[p[2]] = t
            elif p[0] == "H" and p[2] in ended and p[2] not in back and t >= ended[p[2]]:
                back[p[2]] = t
            elif p[0] == "K" and p[2] in back and p[2] not in done and t >= back[p[2]]:
                done[p[2]] = t
        reconnect = [back[u] - ready for u in back]
        catch = [done[u] - stop for u in done]
        print(f"- Tracked streams ended by the restart: {len(ended)}; back (a hello): {len(back)}; caught up: {len(done)}")
        if reconnect:
            print(f"- Hello after readiness (ms): p50 {fmt(pct(reconnect, .5))}, p95 {fmt(pct(reconnect, .95))}, "
                  f"max {fmt(max(reconnect))}; caught up after the stop was asked (ms): p50 {fmt(pct(catch, .5))}, "
                  f"p95 {fmt(pct(catch, .95))}, max {fmt(max(catch) if catch else None)}")
    # the outbox in the minute after readiness: its oldest event (seconds), and when it is back under 3 s (its level
    # under load: a poll a second, a batch)
    after = [x for x in rows if ready <= x["epoch_ms"] <= ready + 60000 and x.get("outbox_oldest_s") is not None
             and x["outbox_oldest_s"] == x["outbox_oldest_s"]]
    worst = max((x["outbox_oldest_s"] for x in after), default=None)
    peak = max((x["outbox_pending"] for x in after if x["outbox_pending"] == x["outbox_pending"]), default=None)
    back = next((x["epoch_ms"] for x in after if x["outbox_oldest_s"] < 3), None)
    print(f"- Outbox in the minute after readiness: oldest event at most {fmt(worst, 1)} s, at most {fmt(peak)} pending; "
          f"under 3 s {fmt((back - ready) / 1000 if back else None, 1)} s after readiness (first sample)")
    errors = defaultdict(int)
    for s in stages:
        f = os.path.join(run, f"k6-{s['level']}.txt")
        if not os.path.exists(f):
            continue
        for line in open(f):
            if "OUTCOME" in line:
                p = line[line.index("OUTCOME"):].replace('"', " ").split()
                t = int(p[1])
                if stop - 5000 <= t <= ready + 60000:
                    errors[f"{p[2]} {p[3]} {p[4]}"] += 1
    print(f"- Requests that failed from 5 s before the stop to 60 s after readiness: "
          f"{dict(sorted(errors.items(), key=lambda x: -x[1])) or 'none'}")


def level_summary(run):
    """per level: the worst p95 and p99 of the main operations, failures, requests/s, CPU, pools, outbox, delivery"""
    stages, _ = read_stages(run)
    rows, stats = samples(run), cpu(run)
    out = {}
    for s in stages:
        path = os.path.join(run, f"k6-{s['level']}.json")
        if not os.path.exists(path):
            continue
        k = json.load(open(path))
        ops = {op: o for op, o in k["ops"].items() if op in ("list", "preview", "after", "send", "read", "upload")}
        counts = defaultdict(int)
        for o in k["ops"].values():
            for name, n in o["outcomes"].items():
                counts[name] += n
        total = sum(counts.values())
        bad = total - counts["ok"] - counts["rate_limited"] - counts["not_ready"]
        a, b = s["hold"], s["end"]
        core = lambda name: [x[2] for x in stats if x[1] == name and a <= x[0] <= b]
        top = lambda key: max(window(rows, a, b, key), default=None)
        out[s["level"]] = {
            "p95": max(o["p95"] for o in ops.values()), "p99": max(o["p99"] for o in ops.values()),
            "send_p95": ops["send"]["p95"], "failed": 100 * bad / total if total else None,
            "rps": k["requestsPerSecond"],
            "oro_cpu": statistics.mean(core("oro-load")) if core("oro-load") else None,
            "db_cpu": statistics.mean(core("oro-plans")) if core("oro-plans") else None,
            "api_wait": top("api_pending"), "sys_wait": top("sys_pending"),
            "outbox_oldest": top("outbox_oldest_s"), "power": s["power"],
        }
    return out


def compare(runs):
    """the runs side by side, by level (the delivery p95 from their report.md)"""
    print("| Run | Level | Worst p95 / p99 (ms) | Send p95 | Failed % | Requests/s | Oro CPU | DB CPU | API / system pool waiting (most) | Outbox oldest (s) | Delivery p95 (ms) | Power |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for run in runs:
        delivery = {}
        rep = os.path.join(run, "report.md")
        if os.path.exists(rep):
            for line in open(rep):
                if line.strip().startswith("- level "):
                    p = line.split()
                    delivery[int(p[2])] = p[p.index("p95") + 1].rstrip(",")
        for level, v in sorted(level_summary(run).items()):
            print(f"| {os.path.basename(run.rstrip('/'))} | {level:,} | {fmt(v['p95'])} / {fmt(v['p99'])} | {fmt(v['send_p95'])} | "
                  f"{fmt(v['failed'], 2)} | {fmt(v['rps'])} | {fmt(v['oro_cpu'], 2)} | {fmt(v['db_cpu'], 2)} | "
                  f"{fmt(v['api_wait'])} / {fmt(v['sys_wait'])} | {fmt(v['outbox_oldest'], 1)} | {delivery.get(level, '-')} | {v['power']} |")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "compare":
        compare(sys.argv[2:])
        sys.exit(0)
    if len(sys.argv) != 3 or sys.argv[1] not in ("truth-query", "report"):
        sys.exit(__doc__)
    {"truth-query": truth_query, "report": report}[sys.argv[1]](sys.argv[2])
