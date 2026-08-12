-- V026 freeze lead-appeal reviewer resolution at submission time.
-- Dependencies: V015, V020, V024, V025, zsjos_lead_appeal, and BPM task data.
-- Data scope: additive nullable snapshot columns only; historical appeals are not backfilled.
-- Repeatability: each ALTER is guarded by information_schema; version rows use INSERT IGNORE.
-- Rollback limitation: stop using the snapshot columns and retain all appeal/BPM history.

SET NAMES utf8mb4;

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_lead_appeal' AND column_name='owner_user_id_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `owner_user_id_snapshot` bigint DEFAULT NULL COMMENT ''提交时客资负责人快照'' AFTER `status`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_lead_appeal' AND column_name='owner_dept_id_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `owner_dept_id_snapshot` bigint DEFAULT NULL COMMENT ''提交时负责人部门快照'' AFTER `owner_user_id_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_lead_appeal' AND column_name='reviewer_dept_id_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `reviewer_dept_id_snapshot` bigint DEFAULT NULL COMMENT ''提交时审批部门快照'' AFTER `owner_dept_id_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_lead_appeal' AND column_name='reviewer_user_ids_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `reviewer_user_ids_snapshot` json DEFAULT NULL COMMENT ''本轮审批人快照'' AFTER `reviewer_dept_id_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V026','Freeze lead appeal reviewer resolution at submission time','lead-appeal-reviewer-snapshot-v1');

INSERT IGNORE INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V026','Freeze lead appeal reviewer resolution at submission time',
        SHA2('lead-appeal-reviewer-snapshot-v1',256),'legacy',NOW());
