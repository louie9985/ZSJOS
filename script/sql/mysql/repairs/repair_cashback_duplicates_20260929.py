#!/usr/bin/env python3
"""清理旧库迁移造成的返现重复（2026-09-29）。

背景
    2026-09-16 的 merge_legacy_business_data.py 把旧库 ptml.commission_reward
    整表灌进 zsjos_cashback 时，没有避让新库里已经存在的同源行。旧库那条
    带的是 business_key='valid:<旧leadId>'，新库自己生成的是
    'valid:<新leadId>'——同一个前缀、不同数字，去重逻辑看不见彼此，
    于是同一笔奖励各留一份。

    后果：兼职端提现可选列表按 partner_id + status='available' 取数，
    两份都在列表里，勾选后同一张提现单上挂两条同客资的关联行，
    金额翻倍。

配对口径
    business_key = concat('legacy-reward-', ptml.commission_reward.id)
    再回旧库取该 reward 的 business_key（形如 'valid:<旧leadId>'），
    用这个串去新库找孪生行。绝不能用数字 id 直接对应（会撞号）。
    经查：495 对，涉及 494 个客资。

保留哪一份
    保留新库自己生成的那份（business_key 数字等于 zsjos_cashback.lead_id，
    即新库客资 id）。旧库那份置 cancelled。理由：
      - 新库行有完整的 create_time/generated_at 链路和真实编号，
        旧库行的 create_time 全是导入那一刻（2026-09-16 12:53:21）；
      - 新库行没有的旧库行也没有（beneficiary_user_id 两侧都是 NULL）；
      - 旧库行的编号带 'L' 后缀（RW202609140076L），是导入时的占位写法。

三层处理
    A. 未被任何提现单占用的旧库行（465 笔）：直接 cancelled。
    B. 挂在未打款提现单（pending_review / approved）上的旧库行（13 笔）：
       关联行 active_flag=0（保留历史，不物理删），返现回 cancelled。
       提现单金额不自动改——单子已提交，金额改动会让申请与实际不符，
       交由财务在审批时按新金额处理，脚本只打印提示。
    C. 双方都已 withdrawn 的（11 对）：钱已经付出去了，脚本不动，
       只输出清单供人工追认。

用法
    python3 repair_cashback_duplicates_20260929.py            # dry-run
    python3 repair_cashback_duplicates_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "cashback-dup-repair-20260929"

# 新旧孪生行的配对视图。用 CTE 在库内现算，避免把 495 行 id 硬编码进脚本。
PAIR_CTE = """
  SELECT c.id AS legacy_id, n.id AS twin_id, c.lead_id, c.partner_id,
         c.amount, c.status AS legacy_status, n.status AS twin_status
    FROM zsjos_cashback c
    JOIN ptml.commission_reward r
      ON c.business_key = CONCAT('legacy-reward-', r.id)
    JOIN zsjos_cashback n
      ON n.deleted = 0 AND n.tenant_id = c.tenant_id
     AND n.business_key = r.business_key
   WHERE c.deleted = 0
     AND c.business_key LIKE 'legacy-reward-%'
"""

# 未被任何活跃关联行占用的旧库行
A_IDS = f"""
SELECT p.legacy_id FROM ({PAIR_CTE}) p
 WHERE p.legacy_status = 'available'
   AND p.twin_status IN ('available', 'withdrawable')
   AND NOT EXISTS (SELECT 1 FROM zsjos_withdrawal_item i
                    WHERE i.cashback_id = p.legacy_id
                      AND i.active_flag = 1 AND i.deleted = 0)
"""

# 挂在未打款提现单上的旧库行。MySQL 不允许 UPDATE 的目标表出现在子查询里，
# 所以先把这组 id 固化进临时表，UPDATE 时只引用临时表。
B_IDS = """
SELECT p.legacy_id FROM (
  SELECT c.id AS legacy_id, n.id AS twin_id, c.lead_id, c.partner_id,
         c.amount, c.status AS legacy_status, n.status AS twin_status
    FROM zsjos_cashback c
    JOIN ptml.commission_reward r
      ON c.business_key = CONCAT('legacy-reward-', r.id)
    JOIN zsjos_cashback n
      ON n.deleted = 0 AND n.tenant_id = c.tenant_id
     AND n.business_key = r.business_key
   WHERE c.deleted = 0
     AND c.business_key LIKE 'legacy-reward-%'
) p
 WHERE p.legacy_status IN ('available', 'withdrawing')
   AND EXISTS (SELECT 1 FROM zsjos_withdrawal_item i
                 JOIN zsjos_withdrawal w ON w.id = i.withdrawal_id
                WHERE i.cashback_id = p.legacy_id
                  AND i.active_flag = 1 AND i.deleted = 0
                  AND w.status IN ('pending_review', 'approved') AND w.deleted = 0)
"""

# 双方都已 withdrawn：钱已付，只报告
C_ROWS = f"""
SELECT p.legacy_id, p.twin_id, p.lead_id, p.partner_id, p.amount
  FROM ({PAIR_CTE}) p
 WHERE p.legacy_status = 'withdrawn' AND p.twin_status = 'withdrawn'
"""


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

    # ---- 前置断言：与本脚本假设不符就中止 ----
    parts.append(f"""
-- 待处理 id 先固化进临时表：MySQL 不允许 UPDATE 的目标表出现在子查询里，
-- 而 A/B 层的判据都要读 zsjos_withdrawal_item 本身。
DROP TEMPORARY TABLE IF EXISTS _dup_a, _dup_b;
CREATE TEMPORARY TABLE _dup_a (id BIGINT PRIMARY KEY) AS SELECT legacy_id AS id FROM ({A_IDS}) t;
CREATE TEMPORARY TABLE _dup_b (id BIGINT PRIMARY KEY) AS SELECT legacy_id AS id FROM ({B_IDS}) t;

-- 前置断言 1：孪生对数应为 495；为 0 说明已经被跑过（幂等）
SELECT 'precheck-pairs' AS check_name, COUNT(*) AS hits FROM ({PAIR_CTE}) p;

-- 前置断言 2：A 层应为 465 笔（未被任何提现单占用）
SELECT 'precheck-tier-a' AS check_name, COUNT(*) AS hits FROM _dup_a;

-- 前置断言 3：B 层应为 13 笔（挂在未打款提现单上）
SELECT 'precheck-tier-b' AS check_name, COUNT(*) AS hits FROM _dup_b;

-- 前置断言 4：A 层待处理行必须仍可取消
SELECT 'precheck-tier-a-status' AS check_name, COUNT(*) AS hits
  FROM zsjos_cashback WHERE id IN (SELECT id FROM _dup_a) AND status <> 'available';
""")

    # ---- A. 未被占用的旧库行：直接取消 ----
    parts.append(f"""
-- A: 取消未被任何提现单占用的旧库重复行
UPDATE zsjos_cashback
   SET status = 'cancelled',
       cancelled_at = NOW(),
       cancel_reason = '旧库迁移重复：同源奖励已由新库 <business_key=valid:<新leadId>> 行承载（{MARK}）',
       version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE id IN (SELECT id FROM _dup_a) AND status = 'available';
""")

    # ---- B. 挂在未打款单上的：解挂 + 取消 ----
    parts.append(f"""
-- B1: 摘掉未打款提现单上的重复关联行（保留历史，deactivate 而非删除）
UPDATE zsjos_withdrawal_item
   SET active_flag = b'0', updater = '{MARK}', update_time = NOW()
 WHERE active_flag = 1 AND deleted = 0
   AND cashback_id IN (SELECT id FROM _dup_b);

-- B2: 这些旧库行改判 cancelled，避免再次进入可选列表
UPDATE zsjos_cashback
   SET status = 'cancelled',
       cancelled_at = NOW(),
       cancel_reason = '旧库迁移重复：同源奖励已由新库行承载，重复挂单已解挂（{MARK}）',
       version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE id IN (SELECT id FROM _dup_b) AND status IN ('available', 'withdrawing');
""")

    # ---- C. 已打款：只报告 ----
    parts.append(f"""
-- C: 双方都已 withdrawn，钱已付出，需人工追认（脚本不处理）
SELECT 'C-already-paid' AS tier, p.legacy_id, p.twin_id, p.lead_id, p.partner_id, p.amount
  FROM ({PAIR_CTE}) p
 WHERE p.legacy_status = 'withdrawn' AND p.twin_status = 'withdrawn';
""")

    # ---- 后置校验 ----
    parts.append(f"""
-- 后置校验 1：不应再有「旧库行 available/withdrawing 且存在新库孪生行」
SELECT 'postcheck-dup-live' AS check_name, COUNT(*) AS hits FROM ({PAIR_CTE}) p
 WHERE p.legacy_status IN ('available', 'withdrawing')
   AND p.twin_status IN ('available', 'withdrawable', 'pending_settlement');

-- 后置校验 2：不应再有「两张单挂同一客资」的未打款单
SELECT 'postcheck-open-wd-dup-lead' AS check_name, COUNT(*) AS hits FROM (
  SELECT w.id, c.lead_id
    FROM zsjos_withdrawal w
    JOIN zsjos_withdrawal_item i ON i.withdrawal_id = w.id AND i.active_flag = 1 AND i.deleted = 0
    JOIN zsjos_cashback c ON c.id = i.cashback_id
   WHERE w.status IN ('pending_review', 'approved') AND w.deleted = 0
   GROUP BY w.id, c.lead_id HAVING COUNT(*) > 1) t;

-- 后置校验 3：受影响提现单的金额变化，供财务核对
SELECT 'postcheck-wd-amount' AS check_name, v.withdrawal_no, v.wd_status,
       v.application_amount, v.remaining_amount
  FROM (
    SELECT w.id, w.withdrawal_no, w.status AS wd_status, w.application_amount,
           (SELECT COALESCE(SUM(i.amount_snapshot), 0) FROM zsjos_withdrawal_item i
             WHERE i.withdrawal_id = w.id AND i.active_flag = 1 AND i.deleted = 0) AS remaining_amount
      FROM zsjos_withdrawal w
     WHERE w.deleted = 0 AND w.status IN ('pending_review', 'approved')
       AND EXISTS (SELECT 1 FROM zsjos_withdrawal_item i
                     JOIN zsjos_cashback c ON c.id = i.cashback_id
                     LEFT JOIN zsjos_withdrawal_item i2
                       ON i2.cashback_id = i.cashback_id AND i2.active_flag = 1 AND i2.deleted = 0
                     JOIN ptml.commission_reward r ON c.business_key = CONCAT('legacy-reward-', r.id)
                     JOIN zsjos_cashback n ON n.deleted = 0 AND n.business_key = r.business_key
                    WHERE i.withdrawal_id = w.id AND i.deleted = 0)
       AND EXISTS (SELECT 1 FROM zsjos_cashback c
                     JOIN zsjos_withdrawal_item i ON i.cashback_id = c.id
                    WHERE i.withdrawal_id = w.id AND i.active_flag = 0
                      AND c.cancel_reason LIKE '%{MARK}%')
  ) v;

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
