-- V048: account identifier rules plus personnel and partner lifecycle.
-- Dependencies/order: apply after V047 and the System user/post/menu baseline.
-- Data scope: additive personnel state, Lead source-department snapshots, safe column widening and server-owned menus.
-- Existing users, credentials, partners and Leads are preserved; no role grant or business row is invented.
-- Repeatability: information_schema guards, CREATE TABLE IF NOT EXISTS and stable menu IDs make reruns safe.
-- Rollback limitation: disable the new menus/services; retain lifecycle history and widened account columns.
-- This file must not be executed without separate environment approval.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='system_users' AND column_name='username' AND character_maximum_length < 32) > 0,
  'ALTER TABLE `system_users` MODIFY COLUMN `username` varchar(32) NOT NULL COMMENT ''用户账号''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND column_name='source_dept_id') = 0,
  'ALTER TABLE `zsjos_lead` ADD COLUMN `source_dept_id` bigint DEFAULT NULL COMMENT ''提交时组织快照'' AFTER `source_user_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_personnel_state` (
  `id` bigint NOT NULL AUTO_INCREMENT, `system_user_id` bigint NOT NULL,
  `business_state` varchar(32) NOT NULL COMMENT 'enabled/disabled/departed',
  `change_reason` varchar(500) NOT NULL, `changed_by_user_id` bigint NOT NULL, `changed_at` datetime NOT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_user` (`tenant_id`,`system_user_id`),
  KEY `idx_tenant_state` (`tenant_id`,`business_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 人员业务状态';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_partner' AND index_name='uk_tenant_bound_user') = 0,
  'ALTER TABLE `zsjos_partner` ADD UNIQUE KEY `uk_tenant_bound_user` (`tenant_id`,`bound_system_user_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6850,'人员业务状态','zsjos:personnel:query',2,30,6735,'personnel','ep:user','zsjos/personnel/index','ZsjosPersonnel',0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0'),
(6851,'变更人员状态','zsjos:personnel:update-state',3,1,6850,'','','',NULL,0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0'),
(6852,'兼职管理','zsjos:partner:query',2,31,6735,'partner','ep:user-filled','zsjos/partner/index','ZsjosPartner',0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0'),
(6853,'创建兼职','zsjos:partner:create',3,1,6852,'','','',NULL,0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0'),
(6854,'停启兼职','zsjos:partner:update-state',3,2,6852,'','','',NULL,0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0'),
(6855,'兼职转员工','zsjos:partner:convert',3,3,6852,'','','',NULL,0,b'1',b'1',b'1','migration-V048',NOW(),'migration-V048',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V048','Account personnel and partner lifecycle','account-personnel-partner-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V048','Account personnel and partner lifecycle',SHA2('account-personnel-partner-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
