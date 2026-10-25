-- UTF-8. Requires V323 in both ledgers. Scope: encrypted partial collection draft,
-- optimistic revision and save timestamp on zsjos_student_info_form only.
-- No historical answers, attachments, grants or existing text are rewritten.
-- Run after V323 before deploying draft-aware backend; fresh installs follow the
-- same migration chain. Replay and partial-DDL recovery inspect actual columns.
-- DDL commits implicitly. Retain columns/data on rollback; never drop saved drafts.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v342_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v342_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V323')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V342 requires V323 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V342 requires collection form table';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='draft_snapshot') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN draft_snapshot MEDIUMTEXT NULL COMMENT '加密的未提交草稿';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='draft_revision') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN draft_revision BIGINT NOT NULL DEFAULT 0 COMMENT '草稿乐观版本';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='draft_saved_at') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN draft_saved_at DATETIME NULL COMMENT '草稿保存时间';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form'
  AND ((column_name='draft_snapshot' AND data_type='mediumtext' AND is_nullable='YES')
   OR (column_name='draft_revision' AND data_type='bigint' AND is_nullable='NO' AND column_default='0')
   OR (column_name='draft_saved_at' AND data_type='datetime' AND is_nullable='YES')))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V342 draft column postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V342','学员信息草稿自动保存',SHA2('V342__student_information_draft.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V342');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V342','学员信息草稿自动保存',SHA2('V342__student_information_draft.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V342');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v342_apply();
DROP PROCEDURE zsjos_v342_apply;
