-- UTF-8. V315 requires Core V314 in both ledgers and its palette table.
-- Approved scope: tenant 1 only. Retire active legacy COURSE personal palettes;
-- retain their JSON/audit rows as recovery evidence, never touch PERSONAL or event colors.
-- Insert one COURSE_SHARED/0 row with four ordinary editable entries only on first upgrade.
-- Stop old backend writes during rollout; deploy new backend after this migration.
-- Transaction includes all data and both ledgers. Repeat preserves edited/empty shared palettes.
-- Backup palette + ledgers before use. Reverting shared settings requires reviewed data recovery;
-- retained legacy COURSE rows can be restored from the backup, not automatically merged.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v315_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v315_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V314')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V314') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V315 requires Core V314';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
 AND table_name IN ('zsjos_calendar_palette','zsjos_schema_version','zsjos_module_schema_version'))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V315 requires transactional palette and ledgers';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_calendar_palette WHERE tenant_id=1 AND calendar_type='COURSE_SHARED'
 AND (user_id<>0 OR deleted<>0 OR JSON_TYPE(colors_json)<>'ARRAY')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V315 unexpected shared palette state';
 END IF;
 IF (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V315')
 OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V315'))
 AND NOT EXISTS(SELECT 1 FROM zsjos_calendar_palette WHERE tenant_id=1 AND calendar_type='COURSE_SHARED' AND user_id=0 AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V315 shared palette missing after prior application; restore reviewed backup';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_calendar_palette(tenant_id,user_id,calendar_type,colors_json,creator,updater)
 SELECT 1,0,'COURSE_SHARED',JSON_ARRAY(
 JSON_OBJECT('id','public-nutrition','name','公共营养师','color','#6ABF6D'),
 JSON_OBJECT('id','nutrition-guide','name','营养指导员','color','#00D0FF'),
 JSON_OBJECT('id','registered-nutrition','name','注册营养师','color','#B695E8'),
 JSON_OBJECT('id','pharmacist','name','执业药师','color','#FFBE73')),'V315','V315'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_calendar_palette WHERE tenant_id=1 AND user_id=0 AND calendar_type='COURSE_SHARED');
 UPDATE zsjos_calendar_palette SET deleted=1,updater='V315' WHERE tenant_id=1 AND calendar_type='COURSE' AND deleted=0;
 IF EXISTS(SELECT 1 FROM zsjos_calendar_palette WHERE tenant_id=1 AND calendar_type='COURSE' AND deleted=0)
 OR (SELECT COUNT(*) FROM zsjos_calendar_palette WHERE tenant_id=1 AND calendar_type='COURSE_SHARED' AND user_id=0 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V315 shared palette postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V315','Shared editable course palette',SHA2('V315__shared_course_palette.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V315');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V315','Shared editable course palette',SHA2('V315__shared_course_palette.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V315');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v315_apply();
DROP PROCEDURE zsjos_v315_apply;
