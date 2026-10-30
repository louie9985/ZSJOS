-- UTF-8. V356 -> V357 -> matching Workbench/backend. Global menu metadata only.
-- No business data, dictionary entries or role assignments are changed.
-- Repeatable; preserve administrator labels/status/order. Repair missing metadata even with markers.
-- All DML and both ledger writes are atomic; conflicting identities fail closed.
-- Rollback: disable buttons through menu management; retain version history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v357_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v357_apply()
BEGIN
 DECLARE parent bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V356')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V356') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V357 requires V356 in both Core ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:business-task:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V357 requires unique business task page';
 END IF;
 SELECT id INTO parent FROM system_menu WHERE permission='zsjos:business-task:query' AND type=2 AND deleted=0;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:business-task:cancel') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:business-task:cancel') AND (type<>3 OR parent_id<>parent OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V357 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '取消待办','zsjos:business-task:cancel',3,10,parent,'','','',0,b'1',b'1',b'1','V357',NOW(),'V357',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:business-task:cancel');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:business-task:cancel') AND type=3 AND parent_id=parent AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V357 postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V357','业务待办取消权限',SHA2('V357__business_task_cancel_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V357');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V357','业务待办取消权限',SHA2('V357__business_task_cancel_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V357');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v357_apply();
DROP PROCEDURE zsjos_v357_apply;
