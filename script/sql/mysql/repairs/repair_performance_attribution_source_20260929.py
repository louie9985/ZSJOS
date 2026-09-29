#!/usr/bin/env python3
"""业绩归属表的来源分组与渠道回填（2026-09-29）。

背景
    zsjos_performance_attribution 的 source_group / channel_code / channel_label
    有三个来源，其中两个是坏的：

    1. 实时写入路径 PerformanceSnapshotService.base()
       按 lead.source_type 算 source_group，按 lead 的渠道字段填 channel_code /
       channel_label。这个路径本身是对的——但它的 switch 没有
       education_self_sourced 分支，那类客资一律掉进 default -> "unknown"。

    2. 离线回填脚本 backfill_performance_orders.py（2026-09-23 02:55:43 跑的那批）
       硬编码 IF(order_type='repurchase','repurchase','unknown')，压根没读 lead，
       也完全没写 channel_code / channel_label。
       后果：179 条 partner + 76 条 sales_self_sourced + 17 条 internal_new_media
       的 ORDER 行被刷成 unknown 且渠道全空。

    3. 早期回填（2026-09-23 02:57:09 起）把 education_self_sourced 当成了
       inbound，42 行。

    同一条客资的兄弟行因此互相矛盾——实测 276 条客资存在组内不一致：
        LD202606200003  ORDER=unknown(渠道空)   ASSIGNMENT=inbound(小红书私信)
    两个值不可能都对。以 lead 表为准。

口径
    source_group 按 lead.source_type 重算，对齐 base() 的正确语义并补上漏掉的分支：
        partner / internal_new_media   -> inbound
        sales_self_sourced             -> self
        education_self_sourced         -> self      <- 本次补的分支
        其余 / lead 缺失                -> unknown
    ORDER 行若 order_type='repurchase' 恒为 repurchase（沿用 order() 的最高优先级）。

    为什么 education_self_sourced 归 self 而不是 inbound：它在三处业务代码里
    始终与 sales_self_sourced 并列，中文口径是「教务自拓录」对「销售自拓录」，
    LeadManagementFilterTenantInitializer 注释明确「手动录入＝销售/教务自拓录，
    与 dispatch_mode=self 等价」。差别只在归属身份是销售还是教务，不是入站客资。

    channel_code / channel_label 按 lead 的真实渠道回填（真实来源优先）：
        channel_code  = lead.source_channel_id
        channel_label = lead.source_channel_label_snapshot
    两者都只在 attribution 侧为空时才写，已有值不动。

用法
    python3 repair_performance_attribution_source_20260929.py           # dry-run
    python3 repair_performance_attribution_source_20260929.py --apply    # 执行
"""
from __future__ import annotations

import argparse
import subprocess
import sys

CONTAINER = "zsjos-mysql-1"
DB = "zsjos"
SECRET = "/opt/zsjos-runtime/secrets/mysql-root-password"
MARK = "attribution-source-backfill-20260929"
TABLE = "zsjos_performance_attribution"

# 目标值视图。全部在库内现算，不把 id 硬编码进脚本。
# lead_no 为空的 11 行（客资已删/未关联）保持原值不动：无法判定真实来源。
TARGET = f"""
SELECT a.id,
       a.source_group   AS old_group,
       a.channel_code   AS old_code,
       a.channel_label  AS old_label,
       CASE
         WHEN o.order_type = 'repurchase' THEN 'repurchase'
         WHEN l.source_type IN ('partner', 'internal_new_media') THEN 'inbound'
         WHEN l.source_type IN ('sales_self_sourced', 'education_self_sourced') THEN 'self'
         ELSE 'unknown'
       END AS new_group,
       NULLIF(l.source_channel_id, '')              AS lead_code,
       NULLIF(l.source_channel_label_snapshot, '')  AS lead_label,
       l.source_type AS lead_type,
       a.org_source  AS org_source
  FROM {TABLE} a
  JOIN zsjos_lead  l ON l.id = a.lead_id AND l.deleted = b'0'
  LEFT JOIN zsjos_order o
         ON a.fact_type = 'ORDER' AND o.id = a.fact_id AND o.deleted = b'0'
 WHERE a.deleted = b'0' AND a.tenant_id = 1
"""

# 只改真正有差异的行
DIFF = f"""
SELECT t.id, t.old_group, t.new_group, t.old_code, t.lead_code, t.old_label, t.lead_label,
       t.lead_type, t.org_source
  FROM ({TARGET}) t
 WHERE t.new_group <> IFNULL(t.old_group, '')
    OR (IFNULL(t.old_code, '')  = '' AND t.lead_code  IS NOT NULL)
    OR (IFNULL(t.old_label, '') = '' AND t.lead_label IS NOT NULL)
"""


def run_sql(sql: str) -> str:
    password = open(SECRET).read().strip()
    proc = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", f"-p{password}",
         "--batch", "--raw", "--default-character-set=utf8mb4", DB, "-e", sql],
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

    # 前置断言：与本脚本假设不符就中止
    parts.append(f"""
-- 前置断言 1：待改行数。为 0 说明已经跑过（幂等）
SELECT 'precheck-diff-rows' AS check_name, COUNT(*) AS hits FROM ({DIFF}) t;

-- 前置断言 2：目标分组只能落在这四个值上，出现别的说明 lead.source_type 有未预期取值
SELECT 'precheck-bad-group' AS check_name, COUNT(*) AS hits FROM (
  SELECT DISTINCT CASE
         WHEN o.order_type = 'repurchase' THEN 'repurchase'
         WHEN l.source_type IN ('partner', 'internal_new_media') THEN 'inbound'
         WHEN l.source_type IN ('sales_self_sourced', 'education_self_sourced') THEN 'self'
         ELSE 'unknown' END AS g
    FROM {TABLE} a
    JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
    LEFT JOIN zsjos_order o ON a.fact_type='ORDER' AND o.id=a.fact_id AND o.deleted=b'0'
   WHERE a.deleted = b'0' AND a.tenant_id = 1
) g WHERE g.g NOT IN ('repurchase', 'inbound', 'self', 'unknown');

-- 前置断言 3：有 lead 关联但渠道为空的 attribution 行，其 lead 必须也没有渠道，否则是漏填。
-- 这条应为 0；不为 0 说明回填规则覆盖不全。
SELECT 'precheck-missable-channel' AS check_name, COUNT(*) AS hits
  FROM {TABLE} a
  JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
 WHERE a.deleted = b'0' AND a.tenant_id = 1
   AND IFNULL(a.channel_code, '') = ''
   AND NULLIF(l.source_channel_id, '') IS NOT NULL;
""")

    # 落库前把待改行的原值留档，可回滚
    parts.append(f"""
-- 备份原值（表内留档，dry-run 不建）。只存将要被改动的行。
CREATE TABLE IF NOT EXISTS zsjos_data_repair_backup (
  id          bigint       NOT NULL AUTO_INCREMENT,
  repair_key  varchar(100) NOT NULL,
  table_name  varchar(64)  NOT NULL,
  row_id      bigint       NOT NULL,
  column_name varchar(64)  NOT NULL,
  old_value   varchar(512) NULL,
  backed_up_at datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mark_row (repair_key, table_name, row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO zsjos_data_repair_backup (repair_key, table_name, row_id, column_name, old_value)
SELECT '{MARK}', '{TABLE}', t.id, 'source_group', t.old_group FROM ({DIFF}) t;
INSERT INTO zsjos_data_repair_backup (repair_key, table_name, row_id, column_name, old_value)
SELECT '{MARK}', '{TABLE}', t.id, 'channel_code', t.old_code FROM ({DIFF}) t WHERE IFNULL(t.old_code,'')='';
INSERT INTO zsjos_data_repair_backup (repair_key, table_name, row_id, column_name, old_value)
SELECT '{MARK}', '{TABLE}', t.id, 'channel_label', t.old_label FROM ({DIFF}) t WHERE IFNULL(t.old_label,'')='';
""")

    # source_group 重算（会覆盖非空错值，这是本次授权的核心）
    parts.append(f"""
-- 1) source_group 按 lead.source_type 重算，覆盖 unknown 与误判的 inbound
UPDATE {TABLE} a
  JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
  LEFT JOIN zsjos_order o
         ON a.fact_type = 'ORDER' AND o.id = a.fact_id AND o.deleted = b'0'
   SET a.source_group = CASE
         WHEN o.order_type = 'repurchase' THEN 'repurchase'
         WHEN l.source_type IN ('partner', 'internal_new_media') THEN 'inbound'
         WHEN l.source_type IN ('sales_self_sourced', 'education_self_sourced') THEN 'self'
         ELSE 'unknown'
       END,
       a.updater = '{MARK}', a.update_time = NOW()
 WHERE a.deleted = b'0' AND a.tenant_id = 1
   AND a.source_group <> CASE
         WHEN o.order_type = 'repurchase' THEN 'repurchase'
         WHEN l.source_type IN ('partner', 'internal_new_media') THEN 'inbound'
         WHEN l.source_type IN ('sales_self_sourced', 'education_self_sourced') THEN 'self'
         ELSE 'unknown'
       END;
""")

    # 渠道回填：只补空，不覆盖已有值
    parts.append(f"""
-- 2) channel_code 按真实来源补空；lead 也没有时落「其他」（字典内的兜底项）
UPDATE {TABLE} a
  JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
   SET a.channel_code = COALESCE(NULLIF(l.source_channel_id, ''), '其他'),
       a.updater = '{MARK}', a.update_time = NOW()
 WHERE a.deleted = b'0' AND a.tenant_id = 1
   AND IFNULL(a.channel_code, '') = '';

-- 3) channel_label 按真实快照补空；快照缺时回落同行的 channel_code
UPDATE {TABLE} a
  JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
   SET a.channel_label = COALESCE(NULLIF(l.source_channel_label_snapshot, ''),
                                  NULLIF(a.channel_code, ''), '其他'),
       a.updater = '{MARK}', a.update_time = NOW()
 WHERE a.deleted = b'0' AND a.tenant_id = 1
   AND IFNULL(a.channel_label, '') = '';
""")

    # 后置校验
    parts.append(f"""
-- 后置校验 1：不应再有可判定的错值
SELECT 'postcheck-group-mismatch' AS check_name, COUNT(*) AS hits FROM ({TARGET}) t
 WHERE t.new_group <> IFNULL(t.old_group, '');

-- 后置校验 2：不应再有可回填的空渠道
SELECT 'postcheck-empty-code' AS check_name, COUNT(*) AS hits
  FROM {TABLE} a JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
 WHERE a.deleted = b'0' AND a.tenant_id = 1 AND IFNULL(a.channel_code, '') = '';

-- 后置校验 3：同一条客资的兄弟行分组必须自洽（按 fact_type 之外看 lead 维度）
SELECT 'postcheck-intra-lead-conflict' AS check_name, COUNT(*) AS hits FROM (
  SELECT a.lead_id
    FROM {TABLE} a JOIN zsjos_lead l ON l.id = a.lead_id AND l.deleted = b'0'
   WHERE a.deleted = b'0' AND a.tenant_id = 1
   GROUP BY a.lead_id
  HAVING COUNT(DISTINCT CASE
           WHEN l.source_type IN ('partner','internal_new_media') THEN 'inbound'
           WHEN l.source_type IN ('sales_self_sourced','education_self_sourced') THEN 'self'
           ELSE 'unknown' END) > 1
) c;

-- 后置校验 4：分组分布
SELECT 'postcheck-distribution' AS check_name, source_group, COUNT(*) AS hits
  FROM {TABLE} WHERE deleted = b'0' AND tenant_id = 1 GROUP BY source_group ORDER BY hits DESC;

-- 打标记
INSERT INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count)
SELECT '{MARK}', '{TABLE}', 'source_group+channel', COUNT(*) FROM ({DIFF}) t
ON DUPLICATE KEY UPDATE row_count = VALUES(row_count), applied_at = NOW();

COMMIT;
""")
    return "\n".join(parts)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正执行；缺省只 dry-run")
    args = ap.parse_args()

    if not args.apply:
        print("=== DRY-RUN：以下是将要改动的行，未做任何写入 ===")
        print(run_sql(f"""
SELECT t.new_group, COUNT(*) AS rows_to_change,
       SUM(IFNULL(t.old_code,'')=''  AND t.lead_code  IS NOT NULL) AS fill_code,
       SUM(IFNULL(t.old_label,'')='' AND t.lead_label IS NOT NULL) AS fill_label,
       SUM(t.new_group <> IFNULL(t.old_group,'')) AS change_group
  FROM ({DIFF}) t GROUP BY t.new_group ORDER BY rows_to_change DESC;"""))
        print("=== source_group 变更明细 ===")
        print(run_sql(f"""
SELECT IFNULL(t.old_group,'(null)') AS old_group, t.new_group,
       t.lead_type, t.org_source, COUNT(*) AS rows_
  FROM ({DIFF}) t WHERE t.new_group <> IFNULL(t.old_group,'')
 GROUP BY 1,2,3,4 ORDER BY rows_ DESC;"""))
        print("=== 渠道回填明细（按 lead 真实来源）===")
        print(run_sql(f"""
SELECT IFNULL(t.old_code,'(null)') AS old_code, IFNULL(t.lead_code,'(lead 也空)') AS lead_code,
       IFNULL(t.old_label,'(null)') AS old_label, IFNULL(t.lead_label,'(lead 也空)') AS lead_label,
       COUNT(*) AS rows_
  FROM ({DIFF}) t
 WHERE IFNULL(t.old_code,'')='' OR IFNULL(t.old_label,'')=''
 GROUP BY 1,2,3,4 ORDER BY rows_ DESC;"""))
        print("重新执行加 --apply 落库。")
        return

    print(f"=== APPLY {MARK} ===")
    print(run_sql(build()))
    print("=== 完成。回滚用 zsjos_data_repair_backup 里 repair_key=" + MARK + " 的原值 ===")


if __name__ == "__main__":
    main()
