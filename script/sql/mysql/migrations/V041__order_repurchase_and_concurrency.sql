-- Phase five: customer repurchase orders and dual-center concurrency guards.
-- Additive and repeatable. Run after V040; it does not modify business rows or start workflows.
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='formal_sales_user_id'), 'SELECT 1', 'ALTER TABLE `zsjos_order` ADD COLUMN `formal_sales_user_id` bigint DEFAULT NULL COMMENT ''正式销售归属'' AFTER `submitter_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='repurchase_reason'), 'SELECT 1', 'ALTER TABLE `zsjos_order` ADD COLUMN `repurchase_reason` varchar(1000) DEFAULT NULL COMMENT ''复购说明'' AFTER `submission_idempotency_key`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='termination_reason'), 'SELECT 1', 'ALTER TABLE `zsjos_order` ADD COLUMN `termination_reason` varchar(1000) DEFAULT NULL COMMENT ''终止原因'' AFTER `repurchase_reason`, ADD COLUMN `terminated_at` datetime DEFAULT NULL COMMENT ''终止时间'' AFTER `termination_reason`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(IS_NULLABLE='YES', 'SELECT 1', 'ALTER TABLE `zsjos_order` MODIFY COLUMN `lead_id` bigint DEFAULT NULL COMMENT ''首购来源客资编号；复购为空''') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='lead_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='active_repurchase_person_id'), 'SELECT 1', 'ALTER TABLE `zsjos_order` ADD COLUMN `active_repurchase_person_id` bigint GENERATED ALWAYS AS (CASE WHEN (`deleted`=b''0'' AND `order_type`=''repurchase'' AND `status` IN (''pending_approval'',''revision_required'')) THEN `person_id` ELSE NULL END) STORED COMMENT ''活动复购客户唯一键'', ADD UNIQUE KEY `uk_tenant_active_repurchase` (`tenant_id`,`active_repurchase_person_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='registration_decision_idempotency_key'), 'SELECT 1', 'ALTER TABLE `zsjos_order_approval_round` ADD COLUMN `registration_decision_idempotency_key` varchar(128) DEFAULT NULL COMMENT ''报名履约决策幂等键'' AFTER `submission_idempotency_key`, ADD COLUMN `finance_decision_idempotency_key` varchar(128) DEFAULT NULL COMMENT ''财务决策幂等键'' AFTER `registration_decision_idempotency_key`, ADD COLUMN `termination_idempotency_key` varchar(128) DEFAULT NULL COMMENT ''终止幂等键'' AFTER `finance_decision_idempotency_key`, ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''并发版本'' AFTER `termination_idempotency_key`, ADD UNIQUE KEY `uk_tenant_registration_decision_key` (`tenant_id`,`registration_decision_idempotency_key`), ADD UNIQUE KEY `uk_tenant_finance_decision_key` (`tenant_id`,`finance_decision_idempotency_key`), ADD UNIQUE KEY `uk_tenant_termination_key` (`tenant_id`,`termination_idempotency_key`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (6849,'历史客户复购','zsjos:sales-order:create',2,20,6735,'orders/external-repurchase','ep:refresh','zsjos-workbench','ExternalRepurchasePage',0,b'1',b'0',b'1','migration-V041',NOW(),'migration-V041',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.role_id,6849,'migration-V041',NOW(),'migration-V041',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source
WHERE source.menu_id=6811 AND source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing WHERE existing.role_id=source.role_id
    AND existing.menu_id=6849 AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V041','Order repurchase and approval concurrency','order-repurchase-concurrency-v1') ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V041','Order repurchase and approval concurrency',SHA2('order-repurchase-concurrency-v1',256),'legacy',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
