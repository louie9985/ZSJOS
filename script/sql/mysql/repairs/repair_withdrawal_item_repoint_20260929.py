#!/usr/bin/env python3
"""修正被误摘的提现关联行：改指向活跃孪生行（2026-09-29）。

背景
    清理重复返现时，对「同一客资的两条重复返现都挂在同一张未审提现单上」
    的情况，处理方式是摘掉其中一条关联行（active_flag=0）。这对 149/150/154
    是正确的，因为另一条仍在单上，客资本身没丢。

    但有两种情况被漏掉了，导致客资整个从单子里消失、金额少算 10 元：

    (1) 客资 1079 挂在 155(WDD8D587A2F4C84B0D94DD)
        重复对：626 legacy-reward-632 (取消) / 4134 valid:1088 (available)
        只有 626 挂在这张单上，4134 当时没挂。摘掉 626 后客资消失。
        应为 8 客资 80 元，被改成 70。

    (2) 客资 5311 挂在 146(WDBC6976ECD0C14BC283B2)
        重复对：3979 legacy-reward-3303 (取消) 挂在 146
                / 4486 valid:4378 (取消) 也挂在 146
                / 4964 valid:5311 (pending_settlement) 未挂
        两条挂单行互相重复，两条都摘掉后客资消失。
        应为 4 客资 40 元，被改成 30。

    正确做法是「改指向」而不是「摘掉」：把关联行指向该客资仍活跃的那笔返现。

不在范围内
    153(WDBB785B234BC64B90A4AD) 保持 10 元不变。它的客资 5956 的活跃孪生行
    4587 已活跃挂在 152(WD0BEBAE647FC14E0BB1AD) 上，若再指向 153 就是同一笔
    钱被两张单同时申领（双花）。partner 74 三张单 152+153+154 = 20+10+70 = 100
    元，对应 10 个不重复客资，总额无误。

处理步骤
    每对：把已摘的关联行 active_flag 复位为 1、cashback_id 改指活跃孪生行；
    孪生行状态从 available/pending_settlement 推进到 withdrawing（与其它
    挂单返现一致）；最后按「活跃客资数 × 10」回写提现单金额。

用法
    python3 repair_withdrawal_item_repoint_20260929.py            # dry-run
    python3 repair_withdrawal_item_repoint_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "withdrawal-item-repoint-20260929"

# (提现单 id, 要复位的关联行 id, 旧返现 id, 新返现 id, 客资 id, 修正后金额)
REPOINTS = [
    (155, 517, 626, 4134, 1079, "80.00"),
    (146, 436, 3979, 4964, 5311, "40.00"),
]


def run_sql(sql: str) -> str:
    password = open(SECRET).read().strip()
    proc = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", f"-p{password}",
         "--batch", "--raw", DB, "-e", sql],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(f"mysql failed ({proc.returncode})")
    return proc.stdout


def build() -> str:
    parts = [
        f"-- ===== {MARK} =====",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
    ]

    # 前置断言
    checks = []
    for wd, item, old_cb, new_cb, lead, amt in REPOINTS:
        checks.append(f"""
-- 前置断言：{wd}/{item} 仍指向 {old_cb} 且已摘；目标 {new_cb} 仍未被任何单占用
SELECT 'precheck-{wd}-{item}' AS check_name,
       (SELECT COUNT(*) FROM zsjos_withdrawal_item
         WHERE id={item} AND cashback_id={old_cb} AND withdrawal_id={wd}
           AND active_flag=0 AND deleted=0) AS item_ok,
       (SELECT COUNT(*) FROM zsjos_withdrawal_item
         WHERE cashback_id={new_cb} AND deleted=0) AS target_claims,
       (SELECT COUNT(*) FROM zsjos_cashback
         WHERE id={new_cb} AND lead_id={lead} AND deleted=0
           AND status IN ('available','pending_settlement')) AS target_status_ok;""")
    parts.append("\n".join(checks))

    # 逐对修复
    for wd, item, old_cb, new_cb, lead, amt in REPOINTS:
        parts.append(f"""
-- {wd}: 关联行 {item} 改指 {old_cb} -> {new_cb}（客资 {lead}）并复位为活跃
UPDATE zsjos_withdrawal_item
   SET cashback_id = {new_cb}, active_flag = b'1',
       updater = '{MARK}', update_time = NOW()
 WHERE id = {item} AND withdrawal_id = {wd} AND cashback_id = {old_cb}
   AND deleted = 0;

-- {new_cb}: 推进到 withdrawing（与其它挂单返现一致）
UPDATE zsjos_cashback
   SET status = 'withdrawing', version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE id = {new_cb} AND deleted = 0 AND status IN ('available','pending_settlement');

-- {wd}: 金额回写到「活跃客资数 × 10」
UPDATE zsjos_withdrawal
   SET application_amount = {amt}, version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE id = {wd} AND deleted = 0 AND status = 'pending_review';
""")

    # 后置校验
    parts.append(f"""
-- 后置校验 1：两张单的金额应等于活跃客资数 × 10
SELECT 'postcheck-amount' AS check_name, w.id, w.withdrawal_no, w.application_amount,
       COUNT(DISTINCT CASE WHEN i.active_flag=1 THEN c.lead_id END) AS active_leads,
       COUNT(DISTINCT CASE WHEN i.active_flag=1 THEN c.lead_id END)*10 AS expected
  FROM zsjos_withdrawal w
  JOIN zsjos_withdrawal_item i ON i.withdrawal_id=w.id AND i.deleted=0
  JOIN zsjos_cashback c ON c.id=i.cashback_id
 WHERE w.id IN ({','.join(str(r[0]) for r in REPOINTS)})
 GROUP BY w.id;

-- 后置校验 2：不应存在「同一返现被多张未审单活跃申领」
SELECT 'postcheck-double-claim' AS check_name, i.cashback_id, COUNT(DISTINCT i.withdrawal_id) n
  FROM zsjos_withdrawal_item i JOIN zsjos_withdrawal w ON w.id=i.withdrawal_id
 WHERE i.active_flag=1 AND i.deleted=0 AND w.deleted=0
   AND w.status IN ('pending_review','approved')
 GROUP BY i.cashback_id HAVING n>1;

-- 后置校验 3：partner 74 三张单合计（应仍为 100 = 10 客资）
SELECT 'postcheck-partner74' AS check_name, SUM(w.application_amount) total,
       COUNT(DISTINCT i.withdrawal_id) wds
  FROM zsjos_withdrawal w JOIN zsjos_withdrawal_item i ON i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0
 WHERE w.partner_id=74 AND w.deleted=0 AND w.status='pending_review';

COMMIT;
""")
    return "\n".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="执行，缺省只打印")
    args = parser.parse_args()
    script = build()
    if not args.apply:
        print(script)
        print("-- dry-run，加 --apply 执行", file=sys.stderr)
        return
    print(run_sql(script))


if __name__ == "__main__":
    main()
