-- V008: use HRM employee IDs for EAM asset ownership.
-- Operator, applicant, verifier and BPM user IDs are intentionally unchanged;
-- only fields that identify the employee holding/receiving an asset are changed.
-- Repeatability: every add/rename/drop is guarded by information_schema checks.
-- Data note: historical System user IDs are deliberately discarded instead of
-- being reinterpreted as HRM employee IDs. Development data must be reassigned
-- through the HRM employee selector after this migration.

DROP PROCEDURE IF EXISTS `eam_v008_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v008_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    DO RELEASE_LOCK('eam:migration:V008');
    RESIGNAL;
  END;

  SELECT GET_LOCK('eam:migration:V008', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V008 lock unavailable'; END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_employee_id') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `use_employee_id` bigint DEFAULT NULL
      COMMENT '使用员工编号，引用 HRM 员工档案' AFTER `use_dept_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_user_name_snapshot')
      AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_employee_name_snapshot') THEN
    ALTER TABLE `eam_asset` CHANGE COLUMN `use_user_name_snapshot` `use_employee_name_snapshot` varchar(100)
      DEFAULT NULL COMMENT '使用员工姓名快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_employee_name_snapshot') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `use_employee_name_snapshot` varchar(100) DEFAULT NULL
      COMMENT '使用员工姓名快照' AFTER `use_employee_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_user_name_snapshot') THEN
    ALTER TABLE `eam_asset` DROP COLUMN `use_user_name_snapshot`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND index_name = 'idx_eam_asset_use_user') THEN
    ALTER TABLE `eam_asset` DROP INDEX `idx_eam_asset_use_user`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'use_user_id') THEN
    ALTER TABLE `eam_asset` DROP COLUMN `use_user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'supervisor_employee_id') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `supervisor_employee_id` bigint DEFAULT NULL
      COMMENT '直属上级员工编号' AFTER `use_employee_name_snapshot`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND column_name = 'supervisor_user_id') THEN
    ALTER TABLE `eam_asset` DROP COLUMN `supervisor_user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset' AND index_name = 'idx_eam_asset_use_employee') THEN
    ALTER TABLE `eam_asset` ADD KEY `idx_eam_asset_use_employee` (`tenant_id`,`use_employee_id`);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_change_log' AND column_name = 'before_employee_id') THEN
    ALTER TABLE `eam_asset_change_log` ADD COLUMN `before_employee_id` bigint DEFAULT NULL
      COMMENT '变更前使用员工' AFTER `after_status`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_change_log' AND column_name = 'after_employee_id') THEN
    ALTER TABLE `eam_asset_change_log` ADD COLUMN `after_employee_id` bigint DEFAULT NULL
      COMMENT '变更后使用员工' AFTER `before_employee_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_change_log' AND column_name = 'before_user_id') THEN
    ALTER TABLE `eam_asset_change_log` DROP COLUMN `before_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_change_log' AND column_name = 'after_user_id') THEN
    ALTER TABLE `eam_asset_change_log` DROP COLUMN `after_user_id`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_transfer' AND column_name = 'from_employee_id') THEN
    ALTER TABLE `eam_transfer` ADD COLUMN `from_employee_id` bigint DEFAULT NULL
      COMMENT '转出使用员工' AFTER `asset_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_transfer' AND column_name = 'to_employee_id') THEN
    ALTER TABLE `eam_transfer` ADD COLUMN `to_employee_id` bigint DEFAULT NULL
      COMMENT '接收使用员工' AFTER `from_dept_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_transfer' AND column_name = 'from_user_id') THEN
    ALTER TABLE `eam_transfer` DROP COLUMN `from_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_transfer' AND column_name = 'to_user_id') THEN
    ALTER TABLE `eam_transfer` DROP COLUMN `to_user_id`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_inventory_detail' AND column_name = 'expect_employee_id') THEN
    ALTER TABLE `eam_inventory_detail` ADD COLUMN `expect_employee_id` bigint DEFAULT NULL
      COMMENT '账面使用员工' AFTER `asset_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_inventory_detail' AND column_name = 'actual_employee_id') THEN
    ALTER TABLE `eam_inventory_detail` ADD COLUMN `actual_employee_id` bigint DEFAULT NULL
      COMMENT '实盘使用员工' AFTER `expect_location`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_inventory_detail' AND column_name = 'expect_user_id') THEN
    ALTER TABLE `eam_inventory_detail` DROP COLUMN `expect_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_inventory_detail' AND column_name = 'actual_user_id') THEN
    ALTER TABLE `eam_inventory_detail` DROP COLUMN `actual_user_id`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_handover' AND column_name = 'from_employee_id') THEN
    ALTER TABLE `eam_asset_handover` ADD COLUMN `from_employee_id` bigint DEFAULT NULL AFTER `content`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_handover' AND column_name = 'to_employee_id') THEN
    ALTER TABLE `eam_asset_handover` ADD COLUMN `to_employee_id` bigint DEFAULT NULL AFTER `from_employee_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_handover' AND column_name = 'from_user_id') THEN
    ALTER TABLE `eam_asset_handover` DROP COLUMN `from_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_asset_handover' AND column_name = 'to_user_id') THEN
    ALTER TABLE `eam_asset_handover` DROP COLUMN `to_user_id`;
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_purchase_source' AND column_name = 'target_user_id') THEN
    ALTER TABLE `eam_purchase_source` DROP COLUMN `target_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_stock_reservation' AND column_name = 'target_user_id') THEN
    ALTER TABLE `eam_stock_reservation` DROP COLUMN `target_user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'eam_stock_holding' AND index_name = 'idx_eam_stock_holding_user') THEN
    ALTER TABLE `eam_stock_holding` DROP INDEX `idx_eam_stock_holding_user`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_stock_holding' AND column_name = 'user_id') THEN
    ALTER TABLE `eam_stock_holding` DROP COLUMN `user_id`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_employee_asset_task' AND column_name = 'user_id') THEN
    ALTER TABLE `eam_employee_asset_task` DROP COLUMN `user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_employee_asset_task_item' AND column_name = 'transfer_to_employee_id') THEN
    ALTER TABLE `eam_employee_asset_task_item` ADD COLUMN `transfer_to_employee_id` bigint DEFAULT NULL
      AFTER `action`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'eam_employee_asset_task_item' AND column_name = 'transfer_to_user_id') THEN
    ALTER TABLE `eam_employee_asset_task_item` DROP COLUMN `transfer_to_user_id`;
  END IF;

  DO RELEASE_LOCK('eam:migration:V008');
END$$
DELIMITER ;
CALL `eam_v008_apply`();
DROP PROCEDURE IF EXISTS `eam_v008_apply`;
