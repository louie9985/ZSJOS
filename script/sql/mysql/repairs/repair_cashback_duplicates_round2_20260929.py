#!/usr/bin/env python3
"""清理旧库迁移造成的返现重复 —— 第二批（2026-09-29）。

背景
    第一批（repair_cashback_duplicates_20260929.py）处理了 495 对
    「legacy-reward-<旧rewardId>」与「valid:<旧leadId>」的孪生行，
    依据是旧库 commission_reward 表。

    本批处理第一批扫不到的残余。它们不来自同一次导入，而是新库应用
    在 9/23–9/25 期间重新判定有效客资时补发的——因为应用的查重键是
    'valid:' + zsjos_cashback.lead_id（新库客资 id），而迁移行的
    business_key 里存的是旧库 id，两边字符串不同，selectByBusinessKey
    查不到，于是又插了一条。

    对账关键：zsjos_cashback.lead_id 列在迁移行上存的是旧库客资 id，
    在新库生成的行上存的是新库客资 id。同一个客资在两套 id 空间里
    各有一个值，不能直接比较。

保留规则
    分两种情况，按证据强度取舍：

    (1) 组内有一行挂在 paid 提现单上 -> 保留它。
        它是唯一有付款凭据的行（有 withdrawal_item 关联、有 settled_at），
        取消它会抹掉真实付款记录。本批共 1 组属于此类（lead 3113）。

    (2) 组内没有 paid 关联 -> 保留 business_key 恰好等于
        CONCAT('valid:', lead_id) 的自洽行。
        理由见 CashbackServiceImpl.ensureValidCashback：

            String businessKey = "valid:" + leadId;
            CashbackDO existing = mapper.selectByBusinessKey(businessKey);
            if (existing != null) return reuseOrRestore(existing);

        应用只认这个键。保留数字不匹配的行，等于应用下次判定时仍然
        查不到，会再补一份——就是本脚本要清的这个坑。非 cancelled 的
        已存行会被 reuseOrRestore 直接复用返回，不会重复发钱。

用法
    python3 repair_cashback_duplicates_round2_20260929.py            # dry-run
    python3 repair_cashback_duplicates_round2_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "cashback-dup-repair-r2-20260929"

# 待处理的 16 个客资
LEADS = [3113, 3119, 3126, 3186, 3206, 5311, 5314, 5319, 5320,
         5351, 5355, 5358, 5862, 5942, 5993, 4898]

# 要取消的行 = 该客资下所有活跃行 减去 保留行。
# 保留行判定（与文档「保留规则」一致）：
#   (1) 挂在 paid 提现单上 -> 保留（有付款凭据）
#   (2) 否则 business_key = CONCAT('valid:', lead_id) 的自洽行 -> 保留
CANCEL_IDS = f"""
SELECT c.id FROM zsjos_cashback c
 WHERE c.deleted = 0 AND c.status NOT IN ('cancelled', 'canceled')
   AND c.lead_id IN ({','.join(map(str, LEADS))})
   AND NOT EXISTS (SELECT 1 FROM zsjos_withdrawal_item i
                     JOIN zsjos_withdrawal w ON w.id = i.withdrawal_id
                    WHERE i.cashback_id = c.id
                      AND i.active_flag = 1 AND i.deleted = 0
                      AND w.status = 'paid' AND w.deleted = 0)
   AND NOT (c.business_key = CONCAT('valid:', c.lead_id)
            AND NOT EXISTS (SELECT 1 FROM zsjos_withdrawal_item i2
                              JOIN zsjos_withdrawal w2 ON w2.id = i2.withdrawal_id
                             WHERE i2.cashback_id IN (
                                     SELECT c2.id FROM zsjos_cashback c2
                                      WHERE c2.lead_id = c.lead_id AND c2.deleted = 0
                                        AND c2.status NOT IN ('cancelled','canceled'))
                               AND i2.active_flag = 1 AND i2.deleted = 0
                               AND w2.status = 'paid' AND w2.deleted = 0))
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
    leads = ",".join(map(str, LEADS))
    parts = [
        f"-- ===== {MARK} =====",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        f"""
-- 前置断言 1：16 个客资应各有恰好 2 条活跃返现行；为 0 说明已跑过（幂等）
SELECT 'precheck-groups' AS check_name, COUNT(*) AS groups_with_2 FROM (
  SELECT lead_id FROM zsjos_cashback
   WHERE deleted=0 AND status NOT IN ('cancelled','canceled') AND lead_id IN ({leads})
   GROUP BY lead_id HAVING COUNT(*) = 2) t;

-- 待处理 id 先固化进临时表：MySQL 不允许 UPDATE 的目标表出现在子查询里
DROP TEMPORARY TABLE IF EXISTS _r2_cancel;
CREATE TEMPORARY TABLE _r2_cancel (id BIGINT PRIMARY KEY) AS SELECT id FROM ({CANCEL_IDS}) t;

-- 前置断言 2：待取消行数应为 16
SELECT 'precheck-cancel-count' AS check_name, COUNT(*) AS hits FROM _r2_cancel;

-- 前置断言 3：这些行里不能有任何一条挂在 paid 提现单上
-- （若有，说明保留规则选错了侧，停下人工看）
SELECT 'precheck-none-paid' AS check_name, COUNT(*) AS hits
  FROM zsjos_withdrawal_item i JOIN zsjos_withdrawal w ON w.id = i.withdrawal_id
 WHERE i.cashback_id IN (SELECT id FROM _r2_cancel)
   AND i.active_flag = 1 AND i.deleted = 0 AND w.status = 'paid';

-- 前置断言 4：每个客资恰好保留 1 条活跃行（取消集不能把整组清空）
SELECT 'precheck-survivors' AS check_name, COUNT(*) AS groups_ok FROM (
  SELECT lead_id FROM zsjos_cashback
   WHERE deleted=0 AND status NOT IN ('cancelled','canceled') AND lead_id IN ({leads})
     AND id NOT IN (SELECT id FROM _r2_cancel)
   GROUP BY lead_id HAVING COUNT(*) = 1) t;
""",
        f"""
-- 先解挂：待取消行若挂在未打款提现单上，摘掉关联行（保留历史，deactivate 而非删除）
UPDATE zsjos_withdrawal_item
   SET active_flag = b'0', updater = '{MARK}', update_time = NOW()
 WHERE active_flag = 1 AND deleted = 0
   AND cashback_id IN (SELECT id FROM _r2_cancel);
""",
        f"""
-- 取消重复行
UPDATE zsjos_cashback
   SET status = 'cancelled',
       cancelled_at = NOW(),
       cancel_reason = '旧库迁移重复：同一客资已有付款凭据行/自洽键行承载，本行为重复补发（{MARK}）',
       version = version + 1,
       updater = '{MARK}', update_time = NOW()
 WHERE id IN (SELECT id FROM _r2_cancel)
   AND status IN ('available', 'pending_settlement', 'withdrawing', 'withdrawable');
""",
        f"""
-- 后置校验 1：16 个客资各应只剩 1 条活跃行
SELECT 'postcheck-groups' AS check_name, COUNT(*) AS groups_with_2 FROM (
  SELECT lead_id FROM zsjos_cashback
   WHERE deleted=0 AND status NOT IN ('cancelled','canceled') AND lead_id IN ({leads})
   GROUP BY lead_id HAVING COUNT(*) > 1) t;

-- 后置校验 2：保留的行必须是自洽键（4898 的 2450 除外）
SELECT 'postcheck-survivor-key' AS check_name, c.id, c.business_key, c.lead_id, c.status
  FROM zsjos_cashback c
 WHERE c.deleted=0 AND c.status NOT IN ('cancelled','canceled') AND c.lead_id IN ({leads})
   AND c.id <> 2450 AND c.business_key <> CONCAT('valid:', c.lead_id);

COMMIT;
""",
    ]
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
