-- UTF-8. Apply after V363 with the matching backend removal.
-- Scope: global System button metadata for the rejected legacy completion command.
-- No business rows or role-menu assignments are modified. Historical grants remain inert.
-- Repeatable, including partial application or existing markers. Both ledgers are atomic.
-- Recovery: restore the backed-up menu row only with a compatible backend release.
-- Historical V224 remains unchanged for deployed checksum compatibility.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v364_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v364_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V363')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V363') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V364 requires V363 in both Core ledgers';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu
           WHERE permission='zsjos:student-delivery:complete' AND (type<>3 OR parent_id<>6920)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V364 legacy permission identity conflict';
 END IF;
 UPDATE system_menu SET deleted=b'1', status=1, visible=b'0', updater='V364', update_time=NOW()
 WHERE permission='zsjos:student-delivery:complete' AND type=3 AND parent_id=6920
   AND (deleted<>b'1' OR status<>1 OR visible<>b'0');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-delivery:complete'
           AND (deleted<>b'1' OR status<>1 OR visible<>b'0')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V364 obsolete permission remains active';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V364','Retire obsolete student delivery completion',SHA2('V364__retire_student_delivery_complete.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V364');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V364','Retire obsolete student delivery completion',SHA2('V364__retire_student_delivery_complete.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V364');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v364_apply();
DROP PROCEDURE zsjos_v364_apply;
