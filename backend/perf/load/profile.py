#!/usr/bin/env python3
"""Where Oro's CPU went in a Java Flight Recorder file (run.sh, PROFILE_LEVEL), from its execution samples.

  docker run --rm -v <run dir>:/r eclipse-temurin:25-jdk jfr print --events jdk.ExecutionSample --stack-depth 96 \
      /r/oro.jfr > <run dir>/samples.txt
  profile.py <run dir>/samples.txt > <run dir>/profile.md

Counts each sample once per component found in its stack (inclusive: a JDBC call made by Hibernate counts in both),
the top frames (self), and Oro's own frames (the first of com.asata in each stack, inclusive).
"""
import re
import sys
from collections import Counter

COMPONENTS = [
    ("PostgreSQL driver (JDBC)", "org.postgresql."),
    ("HikariCP (pool)", "com.zaxxer.hikari."),
    ("Hibernate / JPA", "org.hibernate."),
    ("Spring transactions", "org.springframework.transaction."),
    ("Spring JDBC", "org.springframework.jdbc."),
    ("Spring Security", "org.springframework.security."),
    ("JWT (Nimbus)", "com.nimbusds."),
    ("Jackson (JSON)", "tools.jackson."),
    ("Tomcat", "org.apache.tomcat."),
    ("Tomcat (catalina, coyote)", "org.apache.catalina."),
    ("Micrometer", "io.micrometer."),
    ("Logging (logback)", "ch.qos.logback."),
    ("JDK crypto", "sun.security."),
    ("JDK crypto (javax)", "javax.crypto."),
    ("Oro: identity resolution", "com.asata.org.oro.config.web.ActorResolver"),
    ("Oro: live (streams)", "com.asata.org.oro.live."),
    ("Oro: outbox", "com.asata.org.oro.outbox."),
    ("Oro: rate limits", "com.asata.org.oro.config.ratelimit."),
    ("Oro: attachments", "com.asata.org.oro.attachment."),
    ("GC (no Java stack)", None),
]

frame = re.compile(r"^\s+([\w$.<>]+)\(")


def main(path):
    samples, stacks = 0, []
    current = None
    for line in open(path, errors="replace"):
        if line.startswith("jdk.ExecutionSample"):
            current = []
            stacks.append(current)
            samples += 1
        elif current is not None:
            m = frame.match(line)
            if m:
                current.append(m.group(1))
    if not samples:
        sys.exit("profile.py: no jdk.ExecutionSample in " + path)
    inclusive, top, oro, selfc, live = Counter(), Counter(), Counter(), Counter(), Counter()
    for stack in stacks:
        if stack:
            top[stack[0]] += 1
            # self: the code the CPU was running, by component (the first prefix that matches), else its package
            name = next((n for n, prefix in COMPONENTS if prefix and stack[0].startswith(prefix)), None)
            selfc[name or ".".join(stack[0].split(".")[:3])] += 1
        lh = next((f for f in stack if f.startswith("com.asata.org.oro.live.LiveHub.")), None)
        if lh:
            live[lh] += 1
        found = set()
        for name, prefix in COMPONENTS:
            if prefix and any(f.startswith(prefix) for f in stack):
                found.add(name)
        for name in found:
            inclusive[name] += 1
        first = next((f for f in stack if f.startswith("com.asata.")), None)
        if first:
            oro[first] += 1
    print(f"Execution samples: {samples:,}\n")
    print("| Component (inclusive) | Samples | Share |\n|---|---|---|")
    for name, n in inclusive.most_common():
        print(f"| {name} | {n:,} | {100 * n / samples:.1f} % |")
    print("\n| Self time, by component or package | Samples | Share |\n|---|---|---|")
    for name, n in selfc.most_common(25):
        print(f"| {name} | {n:,} | {100 * n / samples:.1f} % |")
    print("\n| LiveHub method in the stack (inclusive) | Samples | Share |\n|---|---|---|")
    for name, n in live.most_common(10):
        print(f"| `{name}` | {n:,} | {100 * n / samples:.1f} % |")
    print("\n| Top frame (self) | Samples | Share |\n|---|---|---|")
    for name, n in top.most_common(25):
        print(f"| `{name}` | {n:,} | {100 * n / samples:.1f} % |")
    print("\n| First Oro frame in the stack | Samples | Share |\n|---|---|---|")
    for name, n in oro.most_common(20):
        print(f"| `{name}` | {n:,} | {100 * n / samples:.1f} % |")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
