-- UTF-8. V291: notice origin, actual publisher and audience display snapshots.
-- Prerequisite: V287 in both core ledgers; independent of V288-V290 features.
-- Upgrade existing deployments using this additive migration before deploying either frontend/backend.
-- Fresh: bootstrap followed by the ordered manifest migration chain.
-- Scope: five nullable columns on system_notice, across all tenants; no historical backfill,
-- business seeds, role grants or deletion. Existing rows retain NULL metadata.
-- Repeatable including partial DDL or pre-existing markers; validates structure before recording success.
-- DDL implicitly commits. On failure inspect columns and both ledgers, correct conflicts, then retry.
-- Rollback: revert application while retaining additive columns and recorded values; no automatic DROP.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v291_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v291_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V291 requires Core V287 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
    AND table_name IN ('system_notice','zsjos_schema_version','zsjos_module_schema_version')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V291 requires notice table and transactional ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='source_dept_id') THEN
    ALTER TABLE system_notice ADD COLUMN `source_dept_id` bigint DEFAULT NULL COMMENT '来源部门编号';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='source_dept_name') THEN
    ALTER TABLE system_notice ADD COLUMN `source_dept_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源部门名称快照';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='publisher_id') THEN
    ALTER TABLE system_notice ADD COLUMN `publisher_id` bigint DEFAULT NULL COMMENT '实际发布人编号';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='publisher_name') THEN
    ALTER TABLE system_notice ADD COLUMN `publisher_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际发布人姓名快照';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='audience_summary') THEN
    ALTER TABLE system_notice ADD COLUMN `audience_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接收部门及指定用户范围快照';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND (
    (column_name='source_dept_id' AND data_type='bigint' AND is_nullable='YES') OR
    (column_name='source_dept_name' AND data_type='varchar' AND is_nullable='YES' AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci' AND character_maximum_length=255) OR
    (column_name='publisher_id' AND data_type='bigint' AND is_nullable='YES') OR
    (column_name='publisher_name' AND data_type='varchar' AND is_nullable='YES' AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci' AND character_maximum_length=255) OR
    (column_name='audience_summary' AND data_type='text' AND is_nullable='YES' AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci'))) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V291 notice metadata column contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V291','Notice origin and publisher snapshots',SHA2('V291__notice_origin.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V291');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V291','Notice origin and publisher snapshots',SHA2('V291__notice_origin.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V291');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v291_apply();
DROP PROCEDURE zsjos_v291_apply;
