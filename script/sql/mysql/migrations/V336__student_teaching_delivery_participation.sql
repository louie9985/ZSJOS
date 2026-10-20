-- UTF-8. Explicit teaching-delivery participation for shared student service relationships.
-- Scope: add one default-enabled flag; preserve every existing record, assignment and media asset.
-- Prerequisites: V335 in both ledgers, existing InnoDB service table. Execute in numeric order.
-- Repeatability: inspect exact column; replay retains false/true selections and original ledger rows.
-- DDL implicitly commits: on failure inspect structure and both ledgers, then replay this file.
-- Rollback: application rollback retains the column and values. No role/menu assignment or data repair.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v336_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v336_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V335')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V335') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V336 requires V335 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND engine='InnoDB') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V336 requires InnoDB service relationships';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND column_name='teaching_delivery') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN teaching_delivery BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否纳入教学交付' AFTER person_id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND column_name='teaching_delivery'
 AND column_type='bit(1)' AND is_nullable='NO' AND column_default="b'1'"
 AND column_comment='是否纳入教学交付') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V336 participation structure postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V336','新媒体学员教学交付边界',SHA2('V336__student_teaching_delivery_participation.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V336');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V336','新媒体学员教学交付边界',SHA2('V336__student_teaching_delivery_participation.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V336');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v336_apply();
DROP PROCEDURE zsjos_v336_apply;
