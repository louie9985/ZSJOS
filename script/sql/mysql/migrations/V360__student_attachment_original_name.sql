-- UTF-8. Upgrade existing V323 attachment tables before matching backend.
-- Scope: nullable original_name TEXT; no historical names invented, grants or data rewrites.
-- Prerequisite: V323 in both ledgers. Repeatable; inspect actual column even with version markers.
-- DDL commits implicitly; inspect column and both ledgers and replay on partial failure.
-- Application rollback retains column; dropping it would lose newly saved original names.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v360_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v360_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V323')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V360 requires V323 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_info_image' AND column_name='original_name') THEN
  ALTER TABLE zsjos_student_info_image ADD COLUMN original_name TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '原始附件名称';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_info_image' AND column_name='original_name'
  AND data_type='text' AND is_nullable='YES' AND character_set_name='utf8mb4') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V360 original_name postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V360','学员附件原始名称',SHA2('V360__student_attachment_original_name.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V360');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V360','学员附件原始名称',SHA2('V360__student_attachment_original_name.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V360');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v360_apply();
DROP PROCEDURE zsjos_v360_apply;
