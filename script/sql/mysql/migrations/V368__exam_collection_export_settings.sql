-- UTF-8. Apply after V367 and before the material-export runtime.
-- Scope: add an optional export settings to examination collection tasks, preserving every row.
-- Repeatable and recoverable after partial DDL. DDL commits implicitly.
-- Rollback: retain the column and data while reverting runtime. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v368_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v368_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V367')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V367') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V368 requires V367 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_exam_collection_task' AND column_name='export_settings_json') THEN
  ALTER TABLE zsjos_exam_collection_task ADD COLUMN export_settings_json mediumtext DEFAULT NULL AFTER content;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_exam_collection_task' AND column_name='export_settings_json' AND data_type='mediumtext'
 AND character_set_name='utf8mb4' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V368 incompatible export settings column';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V368','Exam collection export settings',SHA2('V368__exam_collection_export_settings.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V368');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V368','Exam collection export settings',SHA2('V368__exam_collection_export_settings.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V368');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v368_apply();
DROP PROCEDURE zsjos_v368_apply;
