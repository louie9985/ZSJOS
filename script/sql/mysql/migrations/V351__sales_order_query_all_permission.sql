-- UTF-8. V351: order-management read-all button metadata only.
-- Apply after V350 in normal Core order, before the matching backend.
-- Prerequisites: exactly one active order-management page and both schema ledgers.
-- Scope: one system_menu button plus V351 in both ledgers; no role grants,
-- tenant packages or business rows. System menus are global metadata, not tenant rows.
-- Replay preserves administrator labels/status/order and existing checksums.
-- Partial metadata is repaired even if a ledger marker exists; failures roll back DML.
-- Rollback: disable the button in System menu management; retain ledger history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v351_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v351_apply()
BEGIN
 DECLARE order_parent_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:query-management' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V351 requires exactly one order management page';
 END IF;
 SELECT id INTO order_parent_id FROM system_menu
 WHERE permission='zsjos:sales-order:query-management' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:query-all')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:query-all'
  AND (type<>3 OR parent_id<>order_parent_id OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V351 query-all menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '查看全部订单','zsjos:sales-order:query-all',3,99,order_parent_id,'','','',NULL,
  0,b'1',b'1',b'0','V351',NOW(),'V351',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:query-all');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:query-all'
  AND type=3 AND parent_id=order_parent_id AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V351 query-all menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V351','订单管理查看全部权限',SHA2('V351__sales_order_query_all_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V351');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V351','订单管理查看全部权限',SHA2('V351__sales_order_query_all_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V351');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v351_apply();
DROP PROCEDURE zsjos_v351_apply;
