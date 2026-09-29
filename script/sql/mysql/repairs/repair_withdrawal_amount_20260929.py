#!/usr/bin/env python3
"""核减未审提现单金额，使其与实际挂单一致（2026-09-29）。

背景
    清理重复返现后，部分提现单的 zsjos_withdrawal_item 活跃行被摘掉，
    但 zsjos_withdrawal.application_amount 是提交时的快照，没有回写。

    而 WithdrawalServiceImpl.recordPayout 之后 handleProcessResult 里：

        record.setStatus(STATUS_APPROVED).setApprovedAmount(record.getApplicationAmount());

    审批通过时 approved_amount 直接取 application_amount。所以不改这个值，
    财务会按旧的（偏大的）金额放款，多付钱。

处理范围
    仅 status='pending_review' 的单。approved 的 5 张经核对
    application_amount 已等于活跃挂单总额，不动。

    本批 6 张，差额合计 120.00 元：
        WDBC6976ECD0C14BC283B2   60 -> 30
        WDB0345E46BDD740DFB949  110 -> 60
        WDDCB108001BF14699B08C   90 -> 70
        WDBB785B234BC64B90A4AD   30 -> 10
        WD06E3E082BD3F43DBA367   80 -> 70
        WDD8D587A2F4C84B0D94DD   80 -> 70

用法
    python3 repair_withdrawal_amount_20260929.py            # dry-run
    python3 repair_withdrawal_amount_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "withdrawal-amount-repair-20260929"

# 待核减的单 = pending_review 且当前金额 != 活跃挂单总额
TARGETS = """
SELECT w.id, w.withdrawal_no, w.application_amount AS old_amt, v.total AS new_amt
  FROM zsjos_withdrawal w
  JOIN (SELECT i.withdrawal_id,
               COALESCE(SUM(i.amount_snapshot), 0) AS total
          FROM zsjos_withdrawal_item i
         WHERE i.active_flag = 1 AND i.deleted = 0
         GROUP BY i.withdrawal_id) v ON v.withdrawal_id = w.id
 WHERE w.deleted = 0 AND w.status = 'pending_review'
   AND w.application_amount <> v.total
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
    return f"""-- ===== {MARK} =====
SET NAMES utf8mb4;
START TRANSACTION;

-- 前置断言 1：待核减的单，新金额必须 >= 最低提现额 10.00
SELECT 'precheck-min-amount' AS check_name, COUNT(*) AS hits
  FROM ({TARGETS}) t WHERE t.new_amt < 10.00;

-- 前置断言 2：待核减的单不得有 approved_amount（未审批）
SELECT 'precheck-not-approved' AS check_name, COUNT(*) AS hits
  FROM zsjos_withdrawal WHERE id IN (SELECT id FROM ({TARGETS}) t)
    AND approved_amount IS NOT NULL;

-- 前置断言 3：待核减的单不得挂在 BPM 流程的已结束节点上
SELECT 'precheck-bpm-running' AS check_name, COUNT(*) AS hits
  FROM zsjos_withdrawal WHERE id IN (SELECT id FROM ({TARGETS}) t)
    AND process_instance_id IS NULL;

-- 核减前的对照表
SELECT 'before' AS phase, t.id, t.withdrawal_no, t.old_amt, t.new_amt, (t.old_amt - t.new_amt) AS diff
  FROM ({TARGETS}) t ORDER BY t.id;

-- 执行核减：application_amount 同步到实际挂单总额。
-- available_balance_snapshot 是「申请时可用余额」的历史快照，不改。
UPDATE zsjos_withdrawal w
  JOIN (SELECT i.withdrawal_id, COALESCE(SUM(i.amount_snapshot), 0) AS total
          FROM zsjos_withdrawal_item i
         WHERE i.active_flag = 1 AND i.deleted = 0
         GROUP BY i.withdrawal_id) v ON v.withdrawal_id = w.id
   SET w.application_amount = v.total,
       w.version = w.version + 1,
       w.updater = '{MARK}', w.update_time = NOW()
 WHERE w.deleted = 0 AND w.status = 'pending_review'
   AND w.application_amount <> v.total;

-- 后置校验 1：pending_review 的单不应再有金额不符
SELECT 'postcheck-amount-mismatch' AS check_name, COUNT(*) AS hits
  FROM ({TARGETS}) t;

-- 后置校验 2：核减后的金额分布
SELECT 'after' AS phase, w.id, w.withdrawal_no, w.application_amount,
       (SELECT COALESCE(SUM(i.amount_snapshot),0) FROM zsjos_withdrawal_item i
         WHERE i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0) AS active_total
  FROM zsjos_withdrawal w
 WHERE w.deleted=0 AND w.status='pending_review' ORDER BY w.id;

COMMIT;
"""


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
