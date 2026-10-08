-- UTF-8. V292: per-exam optional presentation color, independent of publication status.
-- Prerequisite/order: Core V287 in both ledgers; independent of V288-V291 features.
-- Upgrade: run before deploying the new backend; historical migration files are unchanged.
-- Fresh: bootstrap followed by the ordered manifest migrations.
-- Scope: one nullable column on zsjos_exam_schedule for all tenants; no backfill, seeds or grants.
-- Repeatability/recovery: inspect actual column even with existing markers; replay after correcting
-- a partial failure. DDL implicitly commits; both success markers commit only after postconditions.
-- Rollback: revert application, retain additive column/data; no automatic destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v292_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v292_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V292 requires Core V287 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
    AND table_name IN ('zsjos_exam_schedule','zsjos_schema_version','zsjos_module_schema_version')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V292 requires exam table and transactional ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='background_color') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN background_color varchar(7) CHARACTER SET utf8mb4
      COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '考期自选底色，空为默认配色';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='background_color' AND data_type='varchar'
    AND character_maximum_length=7 AND is_nullable='YES' AND column_default IS NULL
    AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V292 exam background color column contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V292','Exam schedule background color',SHA2('V292__exam_schedule_color.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V292');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V292','Exam schedule background color',SHA2('V292__exam_schedule_color.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V292');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v292_apply();
DROP PROCEDURE zsjos_v292_apply;
