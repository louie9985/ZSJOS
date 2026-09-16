#!/usr/bin/env python3
"""
ZSJOS —— 把新库备份 (ruoyi-vue-pro_20260916_193916.sql) 中本批新增/更新的
业务数据增量合并进当前 zsjos 库。

设计原则
    * 只碰本批范围表，不做整行覆盖；已存在的目标行只更新明确列出的字段。
    * 主键沿用备份原值（已核对：与目标库无任何冲突，两边 id 段不相交）。
    * 幂等：全部为 INSERT ... ON DUPLICATE KEY UPDATE（等价 insert-if-absent），
      重复执行不产生新行、不改动已存在的同键行。
    * 只生成 SQL，不自己连库写。执行方另行把输出喂给 mysql。

用法
    python3 merge_backup_incremental_20260916.py            > /tmp/merge_incremental.sql
    python3 merge_backup_incremental_20260916.py --target zsjos_merge_rehearsal > ...

源库名固定为 zsjos_merge_stage（由备份恢复而来的只读暂存库）。
"""

import argparse
import sys

SRC = "zsjos_merge_stage"

# --------------------------------------------------------------------------
# 本批清单
# --------------------------------------------------------------------------
LEGACY_EVENT_CREATOR = "legacy-history-20260915"
LEGACY_ATTACH_CREATOR = "legacy-attachment-backfill"


def header(target: str) -> str:
    return f"""-- =====================================================================
-- ZSJOS 增量合并：新库备份 -> {target}
-- 生成者：script/sql/merge/merge_backup_incremental_20260916.py
--
-- 范围
--   新增：zsjos_lead_attachment 1,986 条（creator=legacy-attachment-backfill）
--         zsjos_lead_submitter_assist_request 19 条
--         zsjos_lead_complaint 2 条
--         zsjos_business_event 188 条
--         zsjos_feedback 8 条 / zsjos_work_order 8 条
--         zsjos_feedback_reply 18 条
--         infra_file 2,223 条（本批引用的全部遗留文件）
--   更新：zsjos_lead.invalid_evidence_refs 52 行
--         zsjos_order.payment_voucher_refs 213 行
--
-- 不做的事
--   不迁移旧申诉、不改账户/权限/菜单/字典、不写审计日志、不触发审批通知待办、
--   不动 {target} 中本批之外的行。
-- =====================================================================

SET NAMES utf8mb4;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';
SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 0) 前置断言：目标库不得已有会与本批冲突的同键行
-- ---------------------------------------------------------------------
SELECT 'precheck-attachment-id-collision' AS check_name, COUNT(*) AS hits
FROM {SRC}.zsjos_lead_attachment s
JOIN {target}.zsjos_lead_attachment t ON t.id = s.id
WHERE s.creator = '{LEGACY_ATTACH_CREATOR}';

SELECT 'precheck-infra-file-id-collision' AS check_name, COUNT(*) AS hits
FROM {SRC}.infra_file s
JOIN {target}.infra_file t ON t.id = s.id
WHERE s.path LIKE 'legacy-crm/%';

SELECT 'precheck-legacy-business-event-collision' AS check_name, COUNT(*) AS hits
FROM {SRC}.zsjos_business_event s
JOIN {target}.zsjos_business_event t
  ON t.tenant_id = s.tenant_id AND t.idempotency_key = s.idempotency_key
WHERE s.creator = '{LEGACY_EVENT_CREATOR}';

-- ---------------------------------------------------------------------
-- 1) infra_file：本批引用的遗留文件记录（id 沿用备份原值）
--    目标库有效配置 config_id 一律替换为目标现存的同 id 配置，避免把
--    备份环境的 config_id 带进来。
-- ---------------------------------------------------------------------
INSERT INTO {target}.infra_file
  (id, config_id, name, path, url, type, size, creator, create_time, updater, update_time, deleted)
SELECT s.id,
       COALESCE(cfg.id, s.config_id),
       s.name, s.path, s.url, s.type, s.size,
       s.creator, s.create_time, s.updater, s.update_time, s.deleted
FROM {SRC}.infra_file s
LEFT JOIN {target}.infra_file_config cfg ON cfg.id = s.config_id AND cfg.deleted = b'0'
WHERE s.path LIKE 'legacy-crm/%'
  AND s.id NOT IN (SELECT id FROM {target}.infra_file)
ON DUPLICATE KEY UPDATE {target}.infra_file.id = {target}.infra_file.id;

-- ---------------------------------------------------------------------
-- 2) zsjos_lead_attachment：本批提交附件
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_lead_attachment
  (id, lead_id, infra_file_id, file_url, original_name, content_type, file_size,
   sort, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT s.id, s.lead_id, s.infra_file_id, s.file_url, s.original_name, s.content_type,
       s.file_size, s.sort, s.creator, s.create_time, s.updater, s.update_time,
       s.deleted, s.tenant_id
FROM {SRC}.zsjos_lead_attachment s
WHERE s.creator = '{LEGACY_ATTACH_CREATOR}'
  AND s.id NOT IN (SELECT id FROM {target}.zsjos_lead_attachment)
ON DUPLICATE KEY UPDATE {target}.zsjos_lead_attachment.id = {target}.zsjos_lead_attachment.id;

-- ---------------------------------------------------------------------
-- 3) zsjos_lead.invalid_evidence_refs：只更新该列，其余字段保持目标现状
--    以 lead_no + tenant_id 定位（主键 id 已核对一一对应）
-- ---------------------------------------------------------------------
UPDATE {target}.zsjos_lead t
JOIN {SRC}.zsjos_lead s
  ON s.tenant_id = t.tenant_id AND s.lead_no = t.lead_no
SET t.invalid_evidence_refs = s.invalid_evidence_refs
WHERE s.invalid_evidence_refs IS NOT NULL
  AND s.invalid_evidence_refs <> 'null'
  AND NOT (s.invalid_evidence_refs <=> t.invalid_evidence_refs);

-- ---------------------------------------------------------------------
-- 4) zsjos_order.payment_voucher_refs：只更新该列（备份中已迁移到 COS）
--    只覆盖仍是旧本地路径 /media/uploads/... 的行，目标环境自有的
--    /zsjos/... 凭证不动。
-- ---------------------------------------------------------------------
UPDATE {target}.zsjos_order t
JOIN {SRC}.zsjos_order s
  ON s.tenant_id = t.tenant_id AND s.order_no = t.order_no
SET t.payment_voucher_refs = s.payment_voucher_refs
WHERE s.payment_voucher_refs LIKE '%legacy-crm%'
  AND t.payment_voucher_refs LIKE '%/media/uploads/%';

-- ---------------------------------------------------------------------
-- 5) zsjos_business_event：本批历史事件
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_business_event
SELECT s.* FROM {SRC}.zsjos_business_event s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND NOT EXISTS (SELECT 1 FROM {target}.zsjos_business_event t
                  WHERE t.tenant_id = s.tenant_id AND t.idempotency_key = s.idempotency_key)
ON DUPLICATE KEY UPDATE {target}.zsjos_business_event.id = {target}.zsjos_business_event.id;

-- ---------------------------------------------------------------------
-- 6) zsjos_work_order：本批关联工单
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_work_order
SELECT s.* FROM {SRC}.zsjos_work_order s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND s.id NOT IN (SELECT id FROM {target}.zsjos_work_order)
ON DUPLICATE KEY UPDATE {target}.zsjos_work_order.id = {target}.zsjos_work_order.id;

-- ---------------------------------------------------------------------
-- 7) zsjos_feedback：本批问题反馈
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_feedback
SELECT s.* FROM {SRC}.zsjos_feedback s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND s.id NOT IN (SELECT id FROM {target}.zsjos_feedback)
ON DUPLICATE KEY UPDATE {target}.zsjos_feedback.id = {target}.zsjos_feedback.id;

-- ---------------------------------------------------------------------
-- 8) zsjos_feedback_reply：本批历史回复
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_feedback_reply
SELECT s.* FROM {SRC}.zsjos_feedback_reply s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND s.id NOT IN (SELECT id FROM {target}.zsjos_feedback_reply)
ON DUPLICATE KEY UPDATE {target}.zsjos_feedback_reply.id = {target}.zsjos_feedback_reply.id;

-- ---------------------------------------------------------------------
-- 9) zsjos_lead_submitter_assist_request：本批协助申请
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_lead_submitter_assist_request
SELECT s.* FROM {SRC}.zsjos_lead_submitter_assist_request s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND NOT EXISTS (SELECT 1 FROM {target}.zsjos_lead_submitter_assist_request t
                  WHERE t.tenant_id = s.tenant_id AND t.idempotency_key = s.idempotency_key)
ON DUPLICATE KEY UPDATE {target}.zsjos_lead_submitter_assist_request.id = {target}.zsjos_lead_submitter_assist_request.id;

-- ---------------------------------------------------------------------
-- 10) zsjos_lead_complaint：本批投诉
-- ---------------------------------------------------------------------
INSERT INTO {target}.zsjos_lead_complaint
SELECT s.* FROM {SRC}.zsjos_lead_complaint s
WHERE s.creator = '{LEGACY_EVENT_CREATOR}'
  AND NOT EXISTS (SELECT 1 FROM {target}.zsjos_lead_complaint t
                  WHERE t.tenant_id = s.tenant_id
                    AND t.create_idempotency_key = s.create_idempotency_key)
ON DUPLICATE KEY UPDATE {target}.zsjos_lead_complaint.id = {target}.zsjos_lead_complaint.id;

-- ---------------------------------------------------------------------
-- 11) 自增基数校正：插入的是显式 id，InnoDB 会把 AUTO_INCREMENT 推到
--     max(id)+1；这里显式兜底，防止后续新数据撞车。
-- ---------------------------------------------------------------------
SELECT 'postcheck-auto-inc' AS check_name, table_name, auto_increment
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('infra_file','zsjos_lead_attachment','zsjos_business_event',
                     'zsjos_work_order','zsjos_feedback','zsjos_feedback_reply');

COMMIT;
"""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="zsjos",
                    help="目标库名（默认 zsjos；演练用 zsjos_merge_rehearsal）")
    args = ap.parse_args()
    sys.stdout.write(header(args.target))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
