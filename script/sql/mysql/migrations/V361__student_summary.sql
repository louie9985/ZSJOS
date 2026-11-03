-- UTF-8. V360 -> V361 -> matching backend/Workbench. Global menu metadata only.
-- Adds management summary query/export permissions; no role grants or business data.
-- Repeatable and repairs missing metadata despite version markers. Existing matching metadata retained.
-- Transactional DML and ledger writes; conflicts fail closed. Rollback: disable menu, retain ledger.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v361_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v361_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V360')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V360') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V361 requires V360 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V361 requires unique Workbench root';
 END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:student-summary:query','zsjos:student-summary:export') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-summary:query' AND
  (deleted<>0 OR type<>2 OR parent_id<>root_id OR path<>'student-summary' OR component<>'zsjos/studentSummary/index'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE parent_id=root_id AND path='student-summary' AND permission<>'zsjos:student-summary:query' AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V361 menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '学员信息汇总','zsjos:student-summary:query',2,43,root_id,'student-summary','ep:document','zsjos/studentSummary/index','native',0,b'1',b'1',b'1','V361','V361',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-summary:query');
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:student-summary:query';
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-summary:export' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V361 export identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '导出学员信息','zsjos:student-summary:export',3,1,page_id,'',0,b'1',b'1',b'1','V361','V361',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-summary:export');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:student-summary:query','zsjos:student-summary:export') AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V361 postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V361','学员信息汇总菜单及导出权限',SHA2('V361__student_summary.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V361');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V361','学员信息汇总菜单及导出权限',SHA2('V361__student_summary.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V361');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v361_apply();
DROP PROCEDURE zsjos_v361_apply;
