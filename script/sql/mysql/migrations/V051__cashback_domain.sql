-- V051 additive cashback schema. Apply after V050. No role grants or business seeds.
-- Repeatable through guarded ALTER, CREATE IF NOT EXISTS, and existence checks. Retain financial history on rollback.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_product' AND COLUMN_NAME='valid_cashback_amount')=0,'ALTER TABLE `zsjos_product` ADD COLUMN `valid_cashback_amount` decimal(12,2) DEFAULT NULL','SELECT 1'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_product' AND COLUMN_NAME='deal_cashback_rate')=0,'ALTER TABLE `zsjos_product` ADD COLUMN `deal_cashback_rate` decimal(8,4) DEFAULT NULL','SELECT 1'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_product_category' AND COLUMN_NAME='default_valid_cashback_amount')=0,'ALTER TABLE `zsjos_product_category` ADD COLUMN `default_valid_cashback_amount` decimal(12,2) DEFAULT NULL','SELECT 1'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_product_category' AND COLUMN_NAME='default_deal_cashback_rate')=0,'ALTER TABLE `zsjos_product_category` ADD COLUMN `default_deal_cashback_rate` decimal(8,4) DEFAULT NULL','SELECT 1'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `zsjos_cashback` (
 `id` bigint NOT NULL AUTO_INCREMENT, `cashback_no` varchar(32) NOT NULL, `business_key` varchar(100) NOT NULL, `type` varchar(20) NOT NULL, `status` varchar(32) NOT NULL,
 `beneficiary_user_id` bigint NOT NULL, `partner_id` bigint NOT NULL, `lead_id` bigint NOT NULL, `order_id` bigint DEFAULT NULL, `order_item_id` bigint DEFAULT NULL,
 `product_ref_snapshot` varchar(128) NOT NULL, `product_name_snapshot` varchar(200) NOT NULL, `rule_snapshot_json` varchar(2000) NOT NULL,
 `base_amount` decimal(12,2) DEFAULT NULL, `rate_snapshot` decimal(8,4) DEFAULT NULL, `amount` decimal(12,2) NOT NULL, `observation_days_snapshot` int NOT NULL,
 `generated_at` datetime NOT NULL, `available_at` datetime NOT NULL, `settled_at` datetime DEFAULT NULL, `cancelled_at` datetime DEFAULT NULL, `cancel_reason` varchar(500) DEFAULT NULL,
 `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_cashback_no` (`tenant_id`,`cashback_no`), UNIQUE KEY `uk_tenant_business_key` (`tenant_id`,`business_key`),
 KEY `idx_beneficiary_status` (`tenant_id`,`beneficiary_user_id`,`status`,`generated_at`), KEY `idx_settlement` (`tenant_id`,`status`,`available_at`), KEY `idx_order` (`tenant_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 兼职返现';
INSERT INTO `infra_config` (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 'ZSJOS返现',1,'返现观察期天数','zsjos.cashback.observation-days','7',b'1','0到365；生成时固化','migration-V051',NOW(),'migration-V051',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `infra_config` WHERE `config_key`='zsjos.cashback.observation-days' AND `deleted`=b'0');
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6880,'返现管理','zsjos:cashback:finance-query',2,35,6735,'cashback','ep:money','zsjos/cashback/index','ZsjosCashback',0,b'1',b'1',b'1','migration-V051',NOW(),'migration-V051',NOW(),b'0'),
(6881,'我的返现','zsjos:cashback:my-query',3,1,6880,'','','',NULL,0,b'1',b'1',b'1','migration-V051',NOW(),'migration-V051',NOW(),b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V051','Cashback domain','cashback-domain-v1') ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
