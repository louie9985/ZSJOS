-- UTF-8. Prerequisite: V323 in both ledgers and its attachment table.
-- Scope: widen zsjos_student_info_image.content_type VARCHAR(32) to VARCHAR(128)
-- for DOCX MIME (71 characters). No row changes, deletes, grants or baseline edits.
-- Run after V323 / before the matching Word-upload backend. Repeatable; actual
-- schema is always checked even if a version marker exists. DDL commits implicitly.
-- Recovery: inspect column and both ledgers, then replay. Rollback must retain the
-- wider column once long MIME values exist; do not truncate historical metadata.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v341_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v341_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V323')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V341 requires V323 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_info_image' AND column_name='content_type'
  AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length IN (32,128)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V341 unexpected attachment content_type structure';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_info_image' AND column_name='content_type' AND character_maximum_length=32) THEN
  ALTER TABLE zsjos_student_info_image MODIFY COLUMN content_type VARCHAR(128) NOT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_info_image' AND column_name='content_type'
  AND data_type='varchar' AND character_maximum_length=128 AND is_nullable='NO') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V341 attachment MIME postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V341','学员附件类型长度修复',SHA2('V341__student_attachment_content_type.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V341');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V341','学员附件类型长度修复',SHA2('V341__student_attachment_content_type.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V341');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v341_apply();
DROP PROCEDURE zsjos_v341_apply;
