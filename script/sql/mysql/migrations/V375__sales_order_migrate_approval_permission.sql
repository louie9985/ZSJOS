-- UTF-8. V375: legacy order approval migration button metadata only.
-- Apply after V374 in normal Core order, before the matching backend.
-- Prerequisites: exactly one active order-management page and both schema ledgers.
-- Scope: one system_menu button plus V375 in both ledgers; no role grants,
-- tenant packages or business rows. System menus are global metadata, not tenant rows.
-- Replay preserves administrator labels/status/order and existing checksums.
-- Partial metadata is repaired even if a ledger marker exists; failures roll back DML.
-- Rollback: disable the button in System menu management; retain ledger history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v375_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v375_apply()
BEGIN
 DECLARE order_parent_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:query-management' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V375 requires exactly one order management page';
 END IF;
 SELECT id INTO order_parent_id FROM system_menu
 WHERE permission='zsjos:sales-order:query-management' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:migrate-approval')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:migrate-approval'
  AND (type<>3 OR parent_id<>order_parent_id OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V375 migrate-approval menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '迁移旧订单审批','zsjos:sales-order:migrate-approval',3,99,order_parent_id,'','','',NULL,
  0,b'1',b'1',b'0','V375',NOW(),'V375',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:migrate-approval');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:sales-order:migrate-approval'
  AND type=3 AND parent_id=order_parent_id AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V375 migrate-approval menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V375','旧订单审批迁移权限',SHA2('V375__sales_order_migrate_approval_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V375');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V375','旧订单审批迁移权限',SHA2('V375__sales_order_migrate_approval_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V375');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v375_apply();
DROP PROCEDURE zsjos_v375_apply;
