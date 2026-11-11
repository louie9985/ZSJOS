-- UTF-8. Apply after V364 and deploy the backend removal in the same release.
-- Scope: only obsolete System button metadata IDs 73428 and 602311 under 73020.
-- Historical V114/V248 are preserved for deployed checksum compatibility.
-- No business data, dictionaries or system_role_menu assignments are modified.
-- Repeatable: recheck actual metadata even when either V365 marker already exists.
-- Menu updates and both success records share an InnoDB transaction and error handler.
-- Rollback: restore scoped backup only with a compatible backend release. Role links
-- remain historical. Application menu caches may require their normal refresh cycle.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v365_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v365_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V364')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V364') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V365 requires V364 in both Core ledgers';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu
     WHERE (id IN (73428,602311)
            OR permission IN ('zsjos:student-contact:delivery-stage-submit','zsjos:student:exam-date-update'))
       AND (type<>3 OR parent_id<>73020 OR permission IS NULL
            OR NOT ((id=73428 AND permission='zsjos:student-contact:delivery-stage-submit')
                 OR (id=602311 AND permission='zsjos:student:exam-date-update')))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V365 retired button identity conflict';
 END IF;
 UPDATE system_menu SET deleted=b'1', status=1, visible=b'0', updater='V365', update_time=NOW()
 WHERE id IN (73428,602311)
   AND (deleted<>b'1' OR status<>1 OR visible<>b'0');
 IF EXISTS(SELECT 1 FROM system_menu WHERE id IN (73428,602311)
           AND (deleted<>b'1' OR status<>1 OR visible<>b'0')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V365 retired button remains active';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V365','Retire obsolete student exam commands',SHA2('V365__retire_student_exam_commands.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V365');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V365','Retire obsolete student exam commands',SHA2('V365__retire_student_exam_commands.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V365');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v365_apply();
DROP PROCEDURE zsjos_v365_apply;
