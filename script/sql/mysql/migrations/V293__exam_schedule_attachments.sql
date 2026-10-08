-- UTF-8. V293: stable file references attached to individual exam remarks.
-- Prerequisite/order: Core V287 in both ledgers, before backend and Workbench deployment.
-- Independent of V288-V292. Fresh installations run the ordered migration chain after bootstrap.
-- Scope: one nullable JSON column for all tenants; existing rows retain NULL (no attachments).
-- No business rows, role grants, dictionary options or existing version checksums are rewritten.
-- Repeat/recovery: checks actual structure even with version markers; replay after partial failure.
-- DDL commits implicitly. Back up exam table/ledgers first; rollback application while retaining
-- additive schema and files. No destructive rollback or physical attachment deletion.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v293_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v293_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V293 requires Core V287 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
    AND table_name IN ('zsjos_exam_schedule','zsjos_schema_version','zsjos_module_schema_version')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V293 requires exam table and transactional ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='attachment_ids_json') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN attachment_ids_json json DEFAULT NULL COMMENT '考期备注附件文件编号';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='attachment_ids_json' AND data_type='json'
    AND is_nullable='YES' AND column_default IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V293 exam attachment column contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V293','Exam schedule remark attachments',SHA2('V293__exam_schedule_attachments.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V293');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V293','Exam schedule remark attachments',SHA2('V293__exam_schedule_attachments.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V293');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v293_apply();
DROP PROCEDURE zsjos_v293_apply;
