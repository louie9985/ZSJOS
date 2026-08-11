-- V025 sales-order workbench personal inbox and approval list support.
-- Dependencies: V023 and V024 must be integrated; do not execute while the V022/V023/V024 ordering gap remains unresolved.
-- Data scope: one approval-round reason column, one order query index, one menu, menu ordering, and copied role grants. No business rows are deleted.
-- Repeatability: DDL is guarded; menu and role grants use stable IDs and existence checks.
-- Rollback limitation: disable menu 6813 and retain the reason snapshots, index, order records, BPM tasks, and audit history.

SET NAMES utf8mb4;

SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='decision_reason'),
  'SELECT 1',
  'ALTER TABLE `zsjos_order_approval_round` ADD COLUMN `decision_reason` varchar(1000) DEFAULT NULL COMMENT ''本轮最终非通过原因快照'' AFTER `rejected_bpm_task_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND index_name='idx_tenant_submitter_status_submitted'),
  'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD KEY `idx_tenant_submitter_status_submitted` (`tenant_id`,`submitter_user_id`,`status`,`submitted_at`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6813,'我的订单','zsjos:sales-order:query-own',2,17,6735,'sales-orders/my','ep:tickets','zsjos/mySalesOrder/index','ZsjosMySalesOrder',0,b'1',b'1',b'1','migration-V025',NOW(),'migration-V025',NOW(),b'0');

UPDATE `system_menu` SET `sort`=18,`updater`='migration-V025',`update_time`=NOW()
WHERE `id`=6810 AND `deleted`=b'0' AND `sort`<>18;
UPDATE `system_menu` SET `sort`=19,`updater`='migration-V025',`update_time`=NOW()
WHERE `id`=6804 AND `deleted`=b'0' AND `sort`<>19;

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6813,'migration-V025',NOW(),'migration-V025',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source
WHERE source.menu_id=6811 AND source.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=source.role_id AND existing.menu_id=6813
      AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V025','Add sales-order workbench personal and approval views','sales-order-workbench-views-v1');

INSERT IGNORE INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V025','Add sales-order workbench personal and approval views',
        SHA2('sales-order-workbench-views-v1',256),'legacy',NOW());

SELECT 'sales_order_v025_reason_column' AS check_name,
       IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name='zsjos_order_approval_round' AND column_name='decision_reason'),'PASS','FAIL') AS result;
SELECT 'sales_order_v025_menu' AS check_name,
       IF(EXISTS(SELECT 1 FROM system_menu WHERE id=6813 AND permission='zsjos:sales-order:query-own'
         AND path='sales-orders/my' AND deleted=b'0'),'PASS','FAIL') AS result;
