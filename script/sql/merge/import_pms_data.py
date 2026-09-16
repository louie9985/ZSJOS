#!/usr/bin/env python3
"""
把备份库的 PMS 模块数据(33 张表)导入 zsjos 测试库。

背景:测试库的 schema 是按 core,hrm,fms,eam 建的,PMS 模块的表从未创建,
但 yudao-server.jar 里已经打进 yudao-module-pms。备份库(ruoyi-vue-pro)
里这 33 张表有 847 行业务数据,用户要求全部导入。

用法:
    python3 import_pms_data.py --plan     只打印计划
    python3 import_pms_data.py --apply    生成 /tmp/pms_payload.sql
    python3 import_pms_data.py --write    执行(建议先停服)
    python3 import_pms_data.py --verify   行数校验
"""
import argparse
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import merge_legacy_business_data as M

# PMS 表里的用户列语义,和主库一致:全是 system_users.id
USER_COLS = {
    "creator", "updater", "owner_user_id", "user_id", "delete_user_id",
    "reply_user_id", "assignee_user_id", "operator_user_id", "supervisor_id",
    "member_id", "share_user_ids",
}


def pms_tables(db):
    return sorted(r[0] for r in M._mysql(
        "select table_name from information_schema.tables "
        "where table_schema='%s' and table_name like 'pms_%%' order by table_name" % db, db=None))


def plan(verbose=True):
    staff_map, stg, basis = M.build_staff_map(verbose=verbose)
    tabs = pms_tables(M.SOURCE_DB)
    total = 0
    for t in tabs:
        n = int(M.count(M.SOURCE_DB, t))
        total += n
        if verbose:
            print("  %-46s %6d" % (t, n))
    if verbose:
        print("  %-46s %6d" % ("TOTAL", total))
    return staff_map, tabs


def apply_():
    staff_map, tabs = plan()
    import json
    stmts = ["SET FOREIGN_KEY_CHECKS=0;", "SET UNIQUE_CHECKS=0;",
             "SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO';"]
    dangling = []
    for t in tabs:
        cols = M.columns_of(M.TARGET_DB, t)
        gen = M.generated_of(M.TARGET_DB, t)
        use = [c for c in cols if c not in gen]
        rows = M.fetch_rows(M.SOURCE_DB, t, use)
        out = []
        for r in rows:
            vals = []
            for i, c in enumerate(use):
                v = r[i]
                if c in USER_COLS and v not in (None, "NULL", "") and v.lstrip("-").isdigit():
                    if v == "0":
                        vals.append(v)
                    elif v in staff_map:
                        vals.append(staff_map[v])
                    elif v in {u[0] for u in M.load_users(M.TARGET_DB)}:
                        vals.append(v)          # 目标库里本来就有的员工
                    else:
                        vals.append(v)
                        dangling.append("%s.%s=%s" % (t, c, v))
                else:
                    vals.append(v)
            out.append(vals)
        stmts.append(M.emit_insert(t, use, out, coltypes=M.column_types(M.TARGET_DB, t)))
        print("    %-44s %6d 行" % (t, len(out)))
    stmts.append("SET FOREIGN_KEY_CHECKS=1;")
    sql = "\n".join(stmts)
    open("/tmp/pms_payload.sql", "w", encoding="utf-8").write(sql)
    print("\nSQL: /tmp/pms_payload.sql (%.1f MB)" % (len(sql) / 1024 / 1024))
    if dangling:
        import collections
        c = collections.Counter(dangling)
        print("指向不存在的员工(原样保留):")
        for k, n in c.most_common(20):
            print("   %-40s x%d" % (k, n))


def write_payload():
    sql = open("/tmp/pms_payload.sql", encoding="utf-8").read()
    out, err = M._mysql_script(sql, M.TARGET_DB)
    print("stderr:", (err.strip() or "(clean)")[:1500])
    if out.strip():
        print("stdout:", out[:500])


def verify():
    """行数对比。导入完成后 SOURCE 已被删,退化为只报目标库行数。"""
    if M.SOURCE_DB not in {r[0] for r in
                           M._mysql("show databases", db=None)}:
        print("源库 %s 不存在,只报目标库行数:" % M.SOURCE_DB)
        for t in pms_tables(M.TARGET_DB):
            print("  %-46s %6d" % (t, int(M.count(M.TARGET_DB, t))))
        return
    tabs = pms_tables(M.SOURCE_DB)
    bad = 0
    for t in tabs:
        a = int(M.count(M.SOURCE_DB, t))
        b = int(M.count(M.TARGET_DB, t))
        flag = "" if a == b else "  <-- MISMATCH"
        if a != b:
            bad += 1
        print("  %-46s src=%-6d dst=%-6d%s" % (t, a, b, flag))
    print("mismatched tables:", bad)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--verify", action="store_true")
    a = ap.parse_args()
    if a.plan:
        plan()
    elif a.apply:
        apply_()
    elif a.write:
        write_payload()
    elif a.verify:
        verify()
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
