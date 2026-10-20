#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reviewed, tenant-1 delivery-domain repair; never uses current employee departments.

Prerequisite: V322 attribution domain column and a reviewed delivery organization mapping.
Prepare outside the repository, review count/digest, then explicitly authorize --apply.
Only undeleted NULL-domain frozen snapshots in the reviewed recorded department qualify.
No schema, grant, organization/center, amount, source, time or version-ledger changes.
One InnoDB transaction; preimage guards reject drift; exact replay is a no-op.
Rollback uses the same manifest, only if every repaired row still matches its preimage
apart from business_domain. It must be authorized independently after reviewing impact.
This is a scoped repair, not a bootstrap seed or historical migration rewrite.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[4]


def digest(rows):
    return hashlib.sha256(json.dumps(rows, sort_keys=True, default=str, ensure_ascii=True,
                                     separators=(",", ":")).encode("utf-8")).hexdigest()


def deleted(value):
    return int.from_bytes(value, "big") != 0 if isinstance(value, bytes) else bool(value)


def connect(container, database):
    # Read Docker's secret into memory; never include it in commands, output or manifests.
    password = subprocess.run(["docker", "exec", container, "printenv", "MYSQL_ROOT_PASSWORD"],
                              capture_output=True, check=True).stdout.decode().strip()
    binding = subprocess.run(["docker", "port", container, "3306"], capture_output=True,
                             check=True).stdout.decode().splitlines()[0]
    return pymysql.connect(host="127.0.0.1", port=int(binding.rsplit(":", 1)[1]), user="root",
                           password=password, database=database, charset="utf8mb4", autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor, connect_timeout=8, read_timeout=30)


def inspect(cursor, department):
    cursor.execute("SELECT dept_id,center_id,kind,business_domain FROM zsjos_performance_org "
                   "WHERE tenant_id=1 AND deleted=0 AND dept_id=%s", (department,))
    mapping = cursor.fetchall()
    if len(mapping) != 1 or mapping[0]["business_domain"] != "delivery":
        raise ValueError("Reviewed department must have one active delivery mapping")
    cursor.execute("SELECT * FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 "
                   "AND business_domain IS NULL AND org_source='frozen' AND dept_id=%s ORDER BY id",
                   (department,))
    rows = cursor.fetchall()
    cursor.execute("SELECT COALESCE(org_source,'unknown') origin,COUNT(*) count "
                   "FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 "
                   "AND business_domain IS NULL AND dept_id=%s GROUP BY org_source", (department,))
    return mapping[0], rows, cursor.fetchall()


def prepare(connection, department, database):
    connection.begin()
    try:
        with connection.cursor() as cursor:
            mapping, rows, origins = inspect(cursor, department)
            return {"repair": "delivery-frozen-domain-20261020", "database": database, "tenant": 1,
                    "mapping": mapping, "ids": [x["id"] for x in rows], "count": len(rows),
                    "preimage_digest": digest(rows), "origins": origins}
    finally:
        connection.rollback()


def apply(connection, manifest, database, rollback=False):
    if manifest["database"] != database or manifest["tenant"] != 1 or manifest["repair"] != "delivery-frozen-domain-20261020":
        raise ValueError("Manifest target mismatch")
    if manifest["count"] != len(manifest["ids"]) or len(set(manifest["ids"])) != manifest["count"]:
        raise ValueError("Manifest row set is invalid")
    connection.begin()
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() "
                           "AND table_name='zsjos_performance_attribution'")
            if cursor.fetchone()["ENGINE"] != "InnoDB":
                raise ValueError("Repair requires transactional InnoDB storage")
            department = manifest["mapping"]["dept_id"]
            # Lock reviewed mapping and all recorded department rows, including repaired rows on replay.
            cursor.execute("SELECT dept_id,center_id,kind,business_domain FROM zsjos_performance_org "
                           "WHERE tenant_id=1 AND deleted=0 AND dept_id=%s FOR UPDATE", (department,))
            if cursor.fetchall() != [manifest["mapping"]]:
                raise ValueError("Reviewed mapping changed")
            cursor.execute("SELECT * FROM zsjos_performance_attribution WHERE tenant_id=1 "
                           "AND dept_id=%s ORDER BY id FOR UPDATE", (department,))
            all_rows = cursor.fetchall()
            ids = set(manifest["ids"])
            selected = [x for x in all_rows if x["id"] in ids]
            if len(selected) != len(ids):
                raise ValueError("Reviewed rows disappeared or changed department")
            for row in selected:
                if row["business_domain"] not in (None, "delivery"):
                    raise ValueError("Conflicting domain; refusing overwrite")
            normalized = [dict(row, business_domain=None) for row in selected]
            if digest(normalized) != manifest["preimage_digest"]:
                raise ValueError("Reviewed preimages changed")
            extra = [x for x in all_rows if x["id"] not in ids and not deleted(x["deleted"])
                     and x["business_domain"] is None and x["org_source"] == "frozen"]
            if extra:
                raise ValueError("New candidates require a new review manifest")
            wanted, previous = (None, "delivery") if rollback else ("delivery", None)
            changing = [x for x in selected if x["business_domain"] != wanted]
            for row in changing:
                cursor.execute("UPDATE zsjos_performance_attribution SET business_domain=%s,update_time=update_time "
                               "WHERE id=%s AND tenant_id=1 AND deleted=0 AND business_domain <=> %s",
                               (wanted, row["id"], previous))
                if cursor.rowcount != 1:
                    raise ValueError("Guarded row update failed")
            cursor.execute("SELECT * FROM zsjos_performance_attribution WHERE tenant_id=1 "
                           "AND dept_id=%s ORDER BY id", (department,))
            after = cursor.fetchall()
            expected = [dict(x, business_domain=wanted) if x["id"] in ids else x for x in all_rows]
            if after != expected:
                raise ValueError("Postcondition mismatch; rolling back")
        connection.commit()
        return len(changing)
    except BaseException:
        connection.rollback()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="yudao-mysql")
    parser.add_argument("--database", default="ruoyi-vue-pro")
    parser.add_argument("--department-id", type=int, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--apply", action="store_true")
    action.add_argument("--rollback", action="store_true")
    args = parser.parse_args()
    manifest_path = args.manifest.resolve()
    if manifest_path == ROOT or ROOT in manifest_path.parents:
        parser.error("Review manifests must be stored outside the repository")
    with connect(args.container, args.database) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SET NAMES utf8mb4")
        if args.apply or args.rollback:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            if manifest["mapping"]["dept_id"] != args.department_id:
                raise ValueError("Department does not match manifest")
            count = apply(connection, manifest, args.database, args.rollback)
            print(json.dumps({"changed": count, "rollback": args.rollback}))
        else:
            manifest = prepare(connection, args.department_id, args.database)
            manifest_path.parent.mkdir(parents=True, exist_ok=True)
            with manifest_path.open("x", encoding="utf-8") as stream:
                json.dump(manifest, stream, ensure_ascii=True, indent=2)
            print(json.dumps({k: manifest[k] for k in ("database", "mapping", "count", "preimage_digest", "origins")}))


if __name__ == "__main__":
    main()
