#!/usr/bin/env python3
"""规范化返现状态词：旧库词 -> 新库应用词（2026-09-29）。

背景
    ptml.commission_reward.reward_status 用的是旧库词表：
        withdrawable, pending_settlement, withdrawn, canceled, withdrawing
    新库 CashbackConstants 用的是：
        pending_settlement, available, withdrawing, withdrawn, blocked, cancelled

    迁移时状态值原样照抄，于是两个旧库词进了新库，应用全都认不出来：

    (1) withdrawable -> available
        259 条，2585.99 元。应用查可用余额只认 'available'：

            default List<CashbackDO> selectAvailableByPartner(Long partnerId) {
                return selectList(... .eq(CashbackDO::getStatus, "available") ...);
            }

        所以这些钱虽然实际可提，兼职在提现页面上看不到。已核实 259 条
        无一被任何提现单引用（active 或历史都没有）。

    (2) canceled -> cancelled
        6 条，1386.00 元，全是 deal_reward，关联订单状态为 terminated
        （已终止），取消本身正确，只是拼写用了旧库的单 l 写法。
        应用只排除 'cancelled'，这 6 条被当成有效收入：

            ... AND status<>'cancelled' ...   -- selectPartnerEstimatedIncomeRanking

        实测 partner 21 的排行榜收入被虚增 1386 元（6541.29 应为 5155.29）。

用法
    python3 repair_cashback_status_vocabulary_20260929.py            # dry-run
    python3 repair_cashback_status_vocabulary_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "cashback-status-vocab-20260929"


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

-- 前置断言 1：withdrawable 不得被任何提现单引用（含已逻辑删除的关联行）
SELECT 'precheck-withdrawable-linked' AS check_name, COUNT(*) AS hits
  FROM zsjos_cashback c
 WHERE c.deleted=0 AND c.status='withdrawable'
   AND EXISTS (SELECT 1 FROM zsjos_withdrawal_item i WHERE i.cashback_id=c.id);

-- 前置断言 2：withdrawable 必须已有 available_at 落地（否则不该可提）
SELECT 'precheck-withdrawable-not-matured' AS check_name, COUNT(*) AS hits
  FROM zsjos_cashback
 WHERE deleted=0 AND status='withdrawable' AND available_at > NOW();

-- 变更前对照
SELECT 'before' AS phase, status, COUNT(*) n, SUM(amount) total
  FROM zsjos_cashback WHERE deleted=0 AND status IN ('withdrawable','canceled')
 GROUP BY status ORDER BY status;

-- (1) withdrawable -> available：让这些钱进入应用的可提余额
UPDATE zsjos_cashback
   SET status = 'available',
       version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE deleted = 0 AND status = 'withdrawable';

-- (2) canceled -> cancelled：对齐应用的排除口径
UPDATE zsjos_cashback
   SET status = 'cancelled',
       version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE deleted = 0 AND status = 'canceled';

-- 后置校验 1：两个旧库词应已归零
SELECT 'postcheck-old-vocab' AS check_name, status, COUNT(*) n
  FROM zsjos_cashback WHERE deleted=0 AND status IN ('withdrawable','canceled')
 GROUP BY status;

-- 后置校验 2：全库状态词应只落在新库词表内
SELECT 'postcheck-vocabulary' AS check_name, status, COUNT(*) n, SUM(amount) total
  FROM zsjos_cashback WHERE deleted=0
   AND status NOT IN ('pending_settlement','available','withdrawing','withdrawn','blocked','cancelled')
 GROUP BY status;

-- 后置校验 3：partner 21 的排行榜收入应回落到 5155.29
SELECT 'postcheck-partner21-income' AS check_name,
       SUM(CASE WHEN status<>'cancelled' THEN amount ELSE 0 END) AS app_sum
  FROM zsjos_cashback WHERE deleted=0 AND partner_id=21;

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
