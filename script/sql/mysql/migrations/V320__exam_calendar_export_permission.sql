-- UTF-8. V320: exam export button metadata only; no role grants or business data.
-- Prerequisites: canonical exam page 73610 (V187) and both schema ledgers.
-- Order: run through the normal core migration chain before deploying the export provider.
-- Repeatable; preserves administrator labels/status/order and existing ledger checksums.
-- Rollback: disable this button in System menu management; generated files use normal export expiry.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v320_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v320_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=73610 AND deleted=0
  AND permission='zsjos:exam-calendar:query' AND type=2) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V320 requires canonical exam calendar page';
 END IF;
 IF EXISTS (SELECT 1 FROM system_menu WHERE id=73615 AND
  (permission<>'zsjos:exam-calendar:export' OR parent_id<>73610 OR type<>3 OR deleted<>0))
 OR EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:exam-calendar:export' AND id<>73615) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V320 export menu identity conflict';
 END IF;
 INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,
  workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT 73615,'导出考期','zsjos:exam-calendar:export',3,5,73610,'','','',
  'native',0,b'1',b'1',b'0','V320','V320',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73615);
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73615 AND deleted=0 AND type=3
  AND parent_id=73610 AND permission='zsjos:exam-calendar:export') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V320 export menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V320','考期日历导出权限',SHA2('V320__exam_calendar_export_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V320');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V320','考期日历导出权限',SHA2('V320__exam_calendar_export_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V320');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v320_apply();
DROP PROCEDURE zsjos_v320_apply;
