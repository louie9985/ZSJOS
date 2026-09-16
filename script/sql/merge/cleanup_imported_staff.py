#!/usr/bin/env python3
"""
收尾:处理合并时"沿用备份原 ID"补进来的 166 个员工账号。

问题
----
备份 system_users 有 210 行,分三类:
  * 7 个 ruoyi 模板自带的演示账号(107-113,2022 年建的)
  * 约 203 个 2026-05 之后由企微/落地页自动建的老系统账号,
    其中 118 个用手机号当用户名
两者混在一起,而测试库只按 昵称/手机号/用户名 匹配出了 44 个真实员工,
剩下 166 个就被原样补建进来了。

这 166 个里:
  * 116 个在导入的业务数据里**零引用** —— 纯占位
  * 50 个被业务数据引用(客资归属人、跟进人、审计操作人……),不能删
  * 7 个是测试期建的"壳"(id 1-61 段),与真实员工重名

本脚本只做 1 和 3 两类,50 个有引用的保持原样(禁用而非删除)。

用法:
    python3 cleanup_imported_staff.py --plan
    python3 cleanup_imported_staff.py --write
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import merge_legacy_business_data as M

# 被业务数据引用的 50 个(2026-09-16 扫描结果)
KEEP = [107, 109, 110, 111, 113, 230, 231, 232, 233, 234, 235, 236, 237, 238,
        239, 240, 241, 244, 245, 247, 248, 250, 251, 253, 254, 255, 256, 259,
        260, 261, 271, 275, 276, 288, 291, 294, 298, 299, 304, 305, 308, 309,
        310, 311, 389, 400, 415, 418, 421, 522]

# 测试期建的"壳" -> 对应的真实员工(引用已合并,见 merge_shell_refs)
SHELLS = [2, 3, 4, 6, 7, 8, 11]


def plan():
    ids = {int(r[0]) for r in M._mysql("select id from system_users;", db=M.TARGET_DB)}
    imported = sorted(i for i in ids if i >= 107)
    delete = [i for i in imported if i not in KEEP]
    print("导入的账号        : %d" % len(imported))
    print("  有业务引用,保留 : %d" % len([i for i in KEEP if i in ids]))
    print("  零引用,删除     : %d" % len(delete))
    print("  重名壳,禁用     : %d  %s" % (len([i for i in SHELLS if i in ids]),
                                          [i for i in SHELLS if i in ids]))
    return delete


def write():
    delete = plan()
    stmts = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
    ]
    if delete:
        s = ",".join(str(i) for i in delete)
        stmts += [
            "DELETE FROM system_user_role WHERE user_id IN (%s);" % s,
            "DELETE FROM system_user_post WHERE user_id IN (%s);" % s,
            "DELETE FROM system_users WHERE id IN (%s);" % s,
        ]
    stmts.append("UPDATE system_users SET deleted=1, status=1 WHERE id IN (%s);"
                 % ",".join(str(i) for i in SHELLS))
    stmts.append("COMMIT;")
    sql = "\n".join(stmts)
    open("/tmp/staff_cleanup.sql", "w", encoding="utf-8").write(sql)
    out, err = M._mysql_script(sql, M.TARGET_DB)
    print("stderr:", (err.strip() or "(clean)")[:800])
    print("done")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()
    if a.plan:
        plan()
    elif a.write:
        write()
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
