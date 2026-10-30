-- UTF-8. V355 -> V356 -> matching Admin/backend. Global menu metadata only.
-- No business data, dictionary entries or role assignments are changed.
-- Repeatable; preserve administrator labels/status/order. Repair missing metadata even with markers.
-- All DML and both ledger writes are atomic; conflicting identities fail closed.
-- Rollback: disable buttons through menu management; retain version history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v356_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v356_apply()
BEGIN
 DECLARE parent bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V355')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V355') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V356 requires V355 in both Core ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:gift-purchase:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V356 requires unique gift purchase page';
 END IF;
 SELECT id INTO parent FROM system_menu WHERE permission='zsjos:gift-purchase:query' AND type=2 AND deleted=0;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:gift-purchase:export','zsjos:gift-purchase:batch-purchase') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:gift-purchase:export','zsjos:gift-purchase:batch-purchase') AND (type<>3 OR parent_id<>parent OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V356 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '导出采购','zsjos:gift-purchase:export',3,5,parent,'','','',0,b'1',b'1',b'1','V356',NOW(),'V356',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:gift-purchase:export');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '批量采购','zsjos:gift-purchase:batch-purchase',3,6,parent,'','','',0,b'1',b'1',b'1','V356',NOW(),'V356',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:gift-purchase:batch-purchase');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:gift-purchase:export','zsjos:gift-purchase:batch-purchase') AND type=3 AND parent_id=parent AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V356 postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V356','采购导出与批量采购权限',SHA2('V356__gift_purchase_export_batch_permissions.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V356');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V356','采购导出与批量采购权限',SHA2('V356__gift_purchase_export_batch_permissions.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V356');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v356_apply();
DROP PROCEDURE zsjos_v356_apply;
