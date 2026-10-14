-- UTF-8. V319 finance order void. Additive upgrade; no historical business data changes.
-- Prerequisites: order/command/gift/allocation tables, both version ledgers, order-management menu.
-- Execute before matching backend deployment. Replay and partial-DDL recovery are supported.
-- Scope: five nullable columns, nullable command BPM references, one operation permission metadata.
-- No role grants; no payment execution, service/registration cancellation or historical backfill.
-- DDL implicitly commits; rollback requires old application and manual schema review, never drop audit data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v319_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v319_apply()
BEGIN
 DECLARE menu_parent BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN
 ('zsjos_order','zsjos_order_command','zsjos_gift_purchase','zsjos_order_payment_allocation','system_menu','zsjos_schema_version','zsjos_module_schema_version')) <> 7 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 missing required tables';
 END IF;
 SELECT MIN(id) INTO menu_parent FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-order:query-management' AND type=2;
 IF menu_parent IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 requires order management page'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='void_reason') THEN
  ALTER TABLE `zsjos_order` ADD COLUMN `void_reason` varchar(1000) NULL COMMENT '作废原因';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='voided_by_user_id') THEN
  ALTER TABLE `zsjos_order` ADD COLUMN `voided_by_user_id` bigint NULL COMMENT '作废操作人';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='voided_at') THEN
  ALTER TABLE `zsjos_order` ADD COLUMN `voided_at` datetime NULL COMMENT '作废时间';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='voided_at') THEN
  ALTER TABLE `zsjos_gift_purchase` ADD COLUMN `voided_at` datetime NULL COMMENT '原订单作废时间';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_payment_allocation' AND column_name='voided_at') THEN
  ALTER TABLE `zsjos_order_payment_allocation` ADD COLUMN `voided_at` datetime NULL COMMENT '资金分配失效时间，不代表退款';
 END IF;
 ALTER TABLE zsjos_order_command MODIFY COLUMN approval_round_id bigint NULL COMMENT '审批轮次；独立作废可为空',
  MODIFY COLUMN process_instance_id varchar(128) COLLATE utf8mb4_unicode_ci NULL COMMENT 'BPM实例；独立作废为空';
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '作废订单','zsjos:sales-order:void',3,95,menu_parent,'','','',0,1,1,1,'V319','V319',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-order:void');
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-order:void' AND type=3 AND parent_id=menu_parent) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 conflicting void permission metadata';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='void_reason' AND data_type='varchar' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 zsjos_order.void_reason postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='voided_by_user_id' AND data_type='bigint' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 zsjos_order.voided_by_user_id postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='voided_at' AND data_type='datetime' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 zsjos_order.voided_at postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='voided_at' AND data_type='datetime' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 zsjos_gift_purchase.voided_at postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_payment_allocation' AND column_name='voided_at' AND data_type='datetime' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 zsjos_order_payment_allocation.voided_at postcondition failed';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_command'
 AND column_name IN ('approval_round_id','process_instance_id') AND is_nullable='YES') <> 2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 command postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V319','财务订单作废',SHA2('V319__sales_order_void.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V319');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V319','财务订单作废',SHA2('V319__sales_order_void.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V319');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v319_apply();
DROP PROCEDURE zsjos_v319_apply;
