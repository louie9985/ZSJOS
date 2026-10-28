-- UTF-8. V354: 我的运营 Workbench page metadata only.
-- Deployment order: V353 -> V354 -> matching backend and Workbench.
-- Prerequisites: unique active /zsjos directory and V353 in both Core ledgers.
-- Scope: one global system_menu page plus V354 in both ledgers; no role grants,
-- tenant packages, user relations, business rows or business dictionary entries.
-- Replay preserves administrator labels/status/order/visibility and existing checksums.
-- A marker never suppresses metadata repair; conflicting identities fail closed.
-- All DML and version writes share one transaction and error handler. Procedure DDL
-- is outside the transaction; failed CALL cannot register success even with --force.
-- Rollback: disable this page in System menu management; retain ledger history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v354_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v354_apply()
BEGIN
 DECLARE root_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V353')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V353') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V354 requires V353 in both Core ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V354 requires exactly one Workbench root';
 END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:my-operations:query')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:my-operations:query'
  AND (type<>2 OR parent_id<>root_id OR deleted<>0
       OR COALESCE(path,'')<>'my-operations' OR COALESCE(component,'')<>'zsjos/myOperations/index'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE deleted=0
  AND (component='zsjos/myOperations/index' OR (parent_id=root_id AND path IN ('my-operations','/zsjos/my-operations')))
  AND permission<>'zsjos:my-operations:query') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V354 My Operations page identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  workbench_render_mode,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '我的运营','zsjos:my-operations:query',2,41,root_id,'my-operations','ep:user','zsjos/myOperations/index',NULL,
  'native',0,b'1',b'1',b'1','V354',NOW(),'V354',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:my-operations:query');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:my-operations:query'
  AND type=2 AND parent_id=root_id AND path='my-operations' AND component='zsjos/myOperations/index' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V354 My Operations page postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V354','我的运营菜单',SHA2('V354__my_operations_menu.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V354');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V354','我的运营菜单',SHA2('V354__my_operations_menu.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V354');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v354_apply();
DROP PROCEDURE zsjos_v354_apply;
