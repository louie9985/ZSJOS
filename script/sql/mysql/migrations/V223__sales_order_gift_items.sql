-- Depends on V222. Adds order-level gift snapshots required by SalesOrderDO.
-- Repeatable and non-destructive: existing values are preserved; new columns default to NULL.
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='gift_items'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `gift_items` json DEFAULT NULL COMMENT ''礼品项目快照'' AFTER `material_delivery_contact`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='gift_shipping_address'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `gift_shipping_address` varchar(1000) DEFAULT NULL COMMENT ''礼品邮寄地址'' AFTER `gift_items`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

