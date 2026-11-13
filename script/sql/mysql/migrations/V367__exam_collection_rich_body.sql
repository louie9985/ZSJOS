-- UTF-8. Apply after V366 and before the rich-body runtime.
-- Scope: add an optional body to examination collection tasks, preserving every row.
-- Repeatable and recoverable after partial DDL. DDL commits implicitly.
-- Rollback: retain the column and data while reverting runtime. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v367_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v367_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V366')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V366') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V367 requires V366 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_exam_collection_task' AND column_name='content') THEN
  ALTER TABLE zsjos_exam_collection_task ADD COLUMN content mediumtext DEFAULT NULL AFTER description;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_exam_collection_task' AND column_name='content' AND data_type='mediumtext'
 AND character_set_name='utf8mb4' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V367 incompatible content column';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V367','Exam collection rich body',SHA2('V367__exam_collection_rich_body.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V367');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V367','Exam collection rich body',SHA2('V367__exam_collection_rich_body.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V367');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v367_apply();
DROP PROCEDURE zsjos_v367_apply;
