#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only task-statistics SQL equivalence and latency profile on existing MySQL.

Reads the actual mapper SQL. No schema/data/grant writes or service lifecycle changes.
Prints only counts, timings and equality; never prints identifiers, facts or credentials.
Results measure SQL only, excluding authorization, API, browser and network latency.
"""
import argparse
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="yudao-mysql")
    parser.add_argument("--database", default="ruoyi-vue-pro")
    parser.add_argument("--tenant", type=int, default=1)
    parser.add_argument("--samples", type=int, default=3)
    args = parser.parse_args()
    if args.tenant < 0 or not 1 <= args.samples <= 10:
        parser.error("tenant must be nonnegative; samples must be between 1 and 10")
    root = Path(__file__).resolve().parents[4]
    mapper = root / "backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceFactSql.java"
    source = mapper.read_text(encoding="utf-8")
    statements = [(body, name.lower()) for name, body in re.findall(r'String (\w+) = """(.*?)""";', source, re.S)]
    document = ET.fromstring(next(body.strip() for body, method in statements if method == "tasks"))

    def execute(statement):
        command = ["docker", "exec", "-i", args.container, "sh", "-c",
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names "$@"',
                   "--", "--database", args.database]
        result = subprocess.run(command, input=statement.encode("utf-8"), capture_output=True)
        if result.returncode:
            raise RuntimeError("Read-only SQL verification failed; connection/query payload suppressed.")
        return result.stdout.decode("utf-8")

    output = {"scope": "SQL only; no endpoint or browser acceptance", "samples": args.samples,
              "mysql_version": execute("SELECT VERSION();").strip(), "scopes": {}}
    for scope in ("SELF", "USER", "DEPT", "CENTER"):
        current = document.text or ""
        for choice in document:
            branches = list(choice)
            branch = branches[0 if scope in ("SELF", "USER") else 1 if scope == "DEPT" else 2]
            current += (branch.text or "") + (choice.tail or "")
        current = current.replace("#{tenant}", str(args.tenant)).replace("#{id}", "@scope_id")
        original, replacements = re.subn(
            r"LEFT JOIN \(\s*SELECT tenant_id,fact_id,dept_id,center_id,completed_at,outcome,assignment_id.*?\) a ON a.fact_id=t.id AND a.tenant_id=t.tenant_id",
            "LEFT JOIN zsjos_performance_attribution a ON a.fact_type IN ('QUALIFICATION','TASK') AND a.fact_id=t.id AND a.tenant_id=t.tenant_id AND a.deleted=0",
            current, count=1, flags=re.S)
        if replacements != 1:
            raise RuntimeError("Mapper query shape changed; review the baseline comparison before profiling.")
        if scope in ("SELF", "USER"):
            selector = f"SELECT assignee_id FROM zsjos_business_task WHERE tenant_id={args.tenant} AND deleted=0 AND assignee_id IS NOT NULL GROUP BY assignee_id ORDER BY COUNT(*) DESC LIMIT 1"
        else:
            column = "dept_id" if scope == "DEPT" else "center_id"
            selector = f"SELECT {column} FROM zsjos_performance_attribution WHERE tenant_id={args.tenant} AND deleted=0 AND fact_type IN ('QUALIFICATION','TASK') AND {column} IS NOT NULL GROUP BY {column} ORDER BY COUNT(*) DESC LIMIT 1"
        setup = "SET NAMES utf8mb4; SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION READ ONLY; " + f"SET @scope_id=({selector}); "
        # Compare within the same read view so concurrent business writes cannot invalidate equivalence.
        rows = execute(setup + original + "; SELECT 'PROFILE_QUERY_BOUNDARY'; " + current + "; ROLLBACK;")
        before, after = rows.split("PROFILE_QUERY_BOUNDARY\n")
        equal = sorted(before.splitlines()) == sorted(after.splitlines())
        result = {"rows": len(after.splitlines()), "identical_multiset": equal}
        if not equal:
            raise RuntimeError("Task results differ; sensitive rows suppressed.")
        for label, query in (("original", original), ("optimized", current)):
            samples = []
            for _ in range(args.samples):
                plan = execute(setup + "EXPLAIN ANALYZE " + query + "; ROLLBACK;")
                timing = re.search(r"actual time=[0-9.]+\.\.([0-9.]+)", plan)
                if timing is None:
                    raise RuntimeError("MySQL did not return actual execution timing.")
                samples.append(float(timing.group(1)))
            samples.sort()
            result[label] = {"server_median_ms": samples[len(samples) // 2], "server_max_ms": max(samples)}
        output["scopes"][scope] = result
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
