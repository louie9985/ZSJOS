-- Phase three: claim-pool daily quota and Opportunity public-sea semantics.
-- Additive and repeatable. It does not execute business migration or grant permissions to roles.
CREATE TABLE IF NOT EXISTS `zsjos_lead_claim_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sales_user_id` bigint NOT NULL COMMENT '销售用户',
  `claim_date` date NOT NULL COMMENT '北京时间自然日',
  `claim_count` int NOT NULL DEFAULT '0' COMMENT '主动抢单数',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sales_claim_date` (`tenant_id`,`sales_user_id`,`claim_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 销售每日主动抢单计数';

CREATE TABLE IF NOT EXISTS `zsjos_lead_transfer_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL,
  `from_owner_user_id` bigint NOT NULL,
  `requested_owner_user_id` bigint NOT NULL,
  `owner_dept_id_snapshot` bigint NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitted_at` datetime NOT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `resolution_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_transfer_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_tenant_transfer_process` (`tenant_id`,`process_instance_id`),
  KEY `idx_tenant_lead_transfer_status` (`tenant_id`,`lead_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资正式转派申请';

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND column_name='no_progress_warned_at'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `no_progress_warned_at` datetime DEFAULT NULL COMMENT ''无进展预警时间'' AFTER `next_follow_up_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_follow_up_rule' AND column_name='no_progress_warning_days'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `no_progress_warning_days` int NOT NULL DEFAULT 7 COMMENT ''判定前无进展预警天数'' AFTER `aging_pool_timeout_days`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_follow_up_rule' AND column_name='no_progress_grace_days'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `no_progress_grace_days` int NOT NULL DEFAULT 2 COMMENT ''无进展预警宽限天数'' AFTER `no_progress_warning_days`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_lead_assignment_rule`
SET `config_json` = JSON_SET(`config_json`, '$.dailyClaimLimit', 5),
    `update_time` = NOW(), `updater` = 'migration-V039'
WHERE `code` = 'default' AND `deleted` = b'0'
  AND JSON_EXTRACT(`config_json`, '$.dailyClaimLimit') IS NULL;

UPDATE `system_menu`
SET `name` = '公海池', `path` = 'opportunity-public-sea',
    `updater` = 'migration-V039', `update_time` = NOW()
WHERE `id` = 6794 AND `deleted` = b'0';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (6843,'申请正式转派','zsjos:lead-aging-pool:transfer-request',3,3,6794,'','','',NULL,0,b'1',b'1',b'1','migration-V039',NOW(),'migration-V039',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V039','Lead pools, public-sea collaboration, and daily claim quota','lead-pools-timeouts-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V039','Lead pools, public-sea collaboration, and daily claim quota',SHA2('lead-pools-timeouts-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
