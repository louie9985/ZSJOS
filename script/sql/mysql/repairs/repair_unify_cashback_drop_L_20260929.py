#!/usr/bin/env python3
"""统一到不带 L 的返现行，弃用 L 行（2026-09-29）。

背景
    旧库 ptml.commission_reward 共 3752 条 reward_no，没有一条带 L 后缀。
    新库 zsjos_cashback 里 490 条编号带 L 的行，全是导入时造的副本：它们与
    同客资、编号去掉 L 的那一行，对应旧库同一条 reward。

    实测 489 组配对（同客资 + 编号去 L 精确匹配）：
        L 行状态      非 L 行状态   组数
        available     cancelled    458
        withdrawing   cancelled     12
        withdrawn     withdrawn     11
        cancelled     cancelled      7
        cancelled     withdrawn      1

    时间戳交叉验证（旧库 reward 130 = RW202606170006）：
        旧库 created_at       2026-06-17 13:47:17
        L 行 generated_at     2026-06-17 13:47:17   <- 与旧库一致
        非 L 行 generated_at   2026-06-17 05:47:17   <- 早 8 小时（UTC 错位）
    故非 L 行时间需从 L 行回填。

处理步骤（严格按此顺序，每步只动一类对象）
    S1  腾位：删除「同单内指向目标非 L 行」的 inactive 关联行。
        唯一约束 uk_withdrawal_cashback(tenant_id, withdrawal_id, cashback_id)
        不区分 active_flag，不腾位则 S2 撞键。这些行 active_flag=0，金额早已
        被排除在单据之外。
    S2  改指：把指向 L 行的活跃关联行改指该客资的非 L 行。
        跳过两种情况：
          (a) 目标非 L 行已被别的单活跃申领（会造成双花）
          (b) 该客资的活跃承载就是这条 L 行本身，且非 L 行没有托管在别的单
              （改指会让本单挂上已取消的返现）。例：item 503@152，客资 5956，
              L 行 4587 是 5956 唯一活跃行。
    S3  恢复非 L 行：状态按 L 行还原，时间从 L 行回填。跳过有应用生成行的客资。
    S4  取消 L 行。
    S5  有应用生成行（business_key = valid:<新leadId>）的客资：非 L 行也取消，
        避免与 S3 恢复出的行重复。

用法
    python3 repair_unify_cashback_drop_L_20260929.py            # dry-run
    python3 repair_unify_cashback_drop_L_20260929.py --apply     # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "cashback-drop-L-20260929"

PAIR = """
  SELECT l.id AS l_id, l.tenant_id, l.lead_id, l.status AS l_status, l.type AS l_type,
         l.generated_at AS l_gen, l.available_at AS l_avail,
         g.id AS g_id, g.status AS g_status
    FROM zsjos_cashback l
    JOIN zsjos_cashback g
      ON g.deleted = 0 AND g.tenant_id = l.tenant_id AND g.lead_id = l.lead_id
     AND g.cashback_no = LEFT(l.cashback_no, CHAR_LENGTH(l.cashback_no) - 1)
   WHERE l.deleted = 0 AND l.cashback_no LIKE '%L'
     AND l.business_key LIKE 'valid:%'
"""

# 有应用生成行的客资（保留应用行，L 与非 L 都取消）
APP_LEADS = """
  SELECT DISTINCT a.lead_id FROM zsjos_cashback a
   WHERE a.deleted = 0 AND a.business_key = CONCAT('valid:', a.lead_id)
     AND a.lead_id IN (SELECT lead_id FROM zsjos_cashback
                        WHERE deleted = 0 AND cashback_no LIKE '%L')
"""

# 指向 L 行的活跃关联行（S2 的输入集）
ACTIVE_ON_L = """
  SELECT i.id AS item_id, i.tenant_id, i.withdrawal_id, l.l_id, l.lead_id, l.g_id,
         (SELECT COUNT(*) FROM zsjos_withdrawal_item x
           WHERE x.withdrawal_id = i.withdrawal_id AND x.cashback_id = l.g_id
             AND x.deleted = 0) AS same_wd_blockers,
         (SELECT COUNT(*) FROM zsjos_withdrawal_item o
            JOIN zsjos_cashback oc ON oc.id = o.cashback_id
           WHERE o.withdrawal_id <> i.withdrawal_id AND o.active_flag = 1 AND o.deleted = 0
             AND oc.deleted = 0 AND oc.lead_id = l.lead_id) AS other_active_claims
    FROM zsjos_withdrawal_item i
    JOIN (SELECT * FROM (SELECT l.id AS l_id, l.tenant_id, l.lead_id,
                                g.id AS g_id
                           FROM zsjos_cashback l
                           JOIN zsjos_cashback g
                             ON g.deleted = 0 AND g.tenant_id = l.tenant_id
                            AND g.lead_id = l.lead_id
                            AND g.cashback_no = LEFT(l.cashback_no, CHAR_LENGTH(l.cashback_no) - 1)
                          WHERE l.deleted = 0 AND l.cashback_no LIKE '%L'
                            AND l.business_key LIKE 'valid:%') t) l ON l.l_id = i.cashback_id
   WHERE i.active_flag = 1 AND i.deleted = 0
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

DROP TEMPORARY TABLE IF EXISTS _pair, _app_leads, _on_l, _s1_del, _s2_repoint;
CREATE TEMPORARY TABLE _pair AS SELECT * FROM ({PAIR}) t;
CREATE TEMPORARY TABLE _app_leads AS SELECT DISTINCT lead_id FROM ({APP_LEADS}) t;
CREATE TEMPORARY TABLE _on_l AS SELECT * FROM ({ACTIVE_ON_L}) t;

-- S1 待删：同单内指向目标非 L 行、且该单另有活跃行挂在 L 行上的 inactive 行
CREATE TEMPORARY TABLE _s1_del AS
SELECT DISTINCT x.id FROM zsjos_withdrawal_item x
  JOIN _on_l o ON o.withdrawal_id = x.withdrawal_id AND o.g_id = x.cashback_id
 WHERE x.active_flag = 0 AND x.deleted = 0;

-- S2 待改指：无占位行、且目标非 L 行未被别的单活跃申领
CREATE TEMPORARY TABLE _s2_repoint AS
SELECT item_id, withdrawal_id, l_id, g_id FROM _on_l
 WHERE other_active_claims = 0;

-- 前置断言
SELECT 'precheck-pairs' AS check_name, COUNT(*) AS pairs FROM _pair;
SELECT 'precheck-app-leads' AS check_name, COUNT(*) AS n FROM _app_leads;
SELECT 'precheck-s1-delete' AS check_name, COUNT(*) AS n FROM _s1_del;
SELECT 'precheck-s2-repoint' AS check_name, COUNT(*) AS n FROM _s2_repoint;

-- 前置断言：L 行不得挂在 paid 单上（否则取消它会破坏付款凭据）
SELECT 'precheck-L-on-paid' AS check_name, COUNT(*) AS hits
  FROM zsjos_withdrawal_item i JOIN zsjos_withdrawal w ON w.id = i.withdrawal_id
 WHERE i.cashback_id IN (SELECT l_id FROM _pair) AND w.status = 'paid';

-- 前置断言：S2 的改指不得违反 uk_withdrawal_cashback（同单同 cashback 只能一行）
SELECT 'precheck-s2-conflict' AS check_name, COUNT(*) AS hits
  FROM _s2_repoint r
 WHERE EXISTS (SELECT 1 FROM zsjos_withdrawal_item x
                WHERE x.withdrawal_id = r.withdrawal_id AND x.cashback_id = r.g_id
                  AND x.deleted = 0 AND x.id <> r.item_id)
   AND r.item_id NOT IN (SELECT id FROM _s1_del);

-- ========== S1 腾位 ==========
DELETE x FROM zsjos_withdrawal_item x
 WHERE x.id IN (SELECT id FROM _s1_del);

-- ========== S2 改指 ==========
UPDATE zsjos_withdrawal_item i
  JOIN _s2_repoint r ON r.item_id = i.id
   SET i.cashback_id = r.g_id, i.updater = '{MARK}', i.update_time = NOW();

-- ========== S3 恢复非 L 行 ==========
UPDATE zsjos_cashback g
  JOIN _pair p ON p.g_id = g.id
   SET g.status = CASE p.l_status
                    WHEN 'available'   THEN 'available'
                    WHEN 'withdrawing' THEN 'withdrawing'
                    ELSE g.status END,
       g.generated_at = p.l_gen,
       g.available_at = p.l_avail,
       g.cancelled_at = NULL,
       g.cancel_reason = NULL,
       g.version = g.version + 1,
       g.updater = '{MARK}', g.update_time = NOW()
 WHERE g.deleted = 0 AND g.status = 'cancelled'
   AND p.l_status IN ('available', 'withdrawing')
   AND p.lead_id NOT IN (SELECT lead_id FROM _app_leads);

UPDATE zsjos_cashback g
  JOIN _pair p ON p.g_id = g.id
   SET g.generated_at = p.l_gen, g.available_at = p.l_avail,
       g.updater = '{MARK}', g.update_time = NOW()
 WHERE g.deleted = 0 AND g.status = 'withdrawn' AND p.l_status = 'withdrawn';

-- ========== S4 取消 L 行 ==========
UPDATE zsjos_cashback
   SET status = 'cancelled', cancelled_at = NOW(),
       cancel_reason = 'L 后缀为导入副本：同客资同编号的非 L 行承载该奖励（{MARK}）',
       version = version + 1, updater = '{MARK}', update_time = NOW()
 WHERE id IN (SELECT l_id FROM _pair) AND status NOT IN ('cancelled', 'canceled');

-- ========== S5 有应用行的客资：非 L 行也取消 ==========
UPDATE zsjos_cashback g
  JOIN _pair p ON p.g_id = g.id
   SET g.status = 'cancelled', cancelled_at = NOW(),
       cancel_reason = '同客资已有应用生成行 business_key=CONCAT(valid:,leadId)（{MARK}）',
       version = g.version + 1, updater = '{MARK}', update_time = NOW()
 WHERE g.deleted = 0
   AND p.lead_id IN (SELECT lead_id FROM _app_leads)
   AND g.status NOT IN ('cancelled', 'canceled');

-- ========== 后置校验 ==========
SELECT 'postcheck-L-live' AS check_name, COUNT(*) AS hits
  FROM zsjos_cashback WHERE deleted=0 AND cashback_no LIKE '%L' AND status NOT IN ('cancelled','canceled');

SELECT 'postcheck-lead-orphan' AS check_name, COUNT(*) AS hits FROM (
  SELECT p.lead_id FROM _pair p
   WHERE NOT EXISTS (SELECT 1 FROM zsjos_cashback a WHERE a.deleted=0
                      AND a.lead_id=p.lead_id AND a.status NOT IN ('cancelled','canceled'))
   GROUP BY p.lead_id) t;

SELECT 'postcheck-lead-dup' AS check_name, COUNT(*) AS hits FROM (
  SELECT lead_id, type FROM zsjos_cashback
   WHERE deleted=0 AND status NOT IN ('cancelled','canceled')
     AND lead_id IN (SELECT lead_id FROM _pair)
   GROUP BY lead_id, type HAVING COUNT(*) > 1) t;

SELECT 'postcheck-double-claim' AS check_name, COUNT(*) AS hits FROM (
  SELECT i.cashback_id FROM zsjos_withdrawal_item i JOIN zsjos_withdrawal w ON w.id=i.withdrawal_id
   WHERE i.active_flag=1 AND i.deleted=0 AND w.deleted=0 AND w.status IN ('pending_review','approved')
   GROUP BY i.cashback_id HAVING COUNT(DISTINCT i.withdrawal_id) > 1) t;

SELECT 'postcheck-wd-amount' AS check_name, COUNT(*) AS hits
  FROM zsjos_withdrawal w WHERE w.deleted=0 AND w.status IN ('pending_review','approved')
   AND w.application_amount <> (SELECT COALESCE(SUM(i.amount_snapshot),0) FROM zsjos_withdrawal_item i
                                 WHERE i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0);

-- 明细：改指后的单据构成，人工核对
SELECT 'detail-wd' AS section, w.id, w.withdrawal_no, w.status, w.application_amount,
       (SELECT GROUP_CONCAT(CONCAT(c.cashback_no, ':', c.status) ORDER BY c.id)
          FROM zsjos_withdrawal_item i JOIN zsjos_cashback c ON c.id=i.cashback_id
         WHERE i.withdrawal_id=w.id AND i.active_flag=1 AND i.deleted=0) active_rows
  FROM zsjos_withdrawal w WHERE w.deleted=0 AND w.status IN ('pending_review','approved')
   AND w.id IN (146,149,150,152,153,154,155) ORDER BY w.id;

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
