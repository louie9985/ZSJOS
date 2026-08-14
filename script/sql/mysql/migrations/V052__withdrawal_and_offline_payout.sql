-- V052: withdrawal and offline payout. Apply after V051; deploy BPM key zsjos_partner_withdrawal separately.
-- Additive empty tables/configs/menus only; no role grants. Repeatable. Retain financial history on rollback.
CREATE TABLE IF NOT EXISTS `zsjos_partner_bank_card` (
 `id` bigint NOT NULL AUTO_INCREMENT, `partner_id` bigint NOT NULL, `owner_user_id` bigint NOT NULL,
 `account_name` varchar(100) NOT NULL, `card_number` varchar(32) NOT NULL, `bank_name` varchar(100) NOT NULL,
 `branch_name` varchar(100) DEFAULT NULL, `default_card` bit(1) NOT NULL DEFAULT b'0', `version` int NOT NULL DEFAULT 0,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), KEY `idx_owner` (`tenant_id`,`owner_user_id`,`default_card`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 兼职常用银行卡';
CREATE TABLE IF NOT EXISTS `zsjos_withdrawal` (
 `id` bigint NOT NULL AUTO_INCREMENT, `withdrawal_no` varchar(32) NOT NULL, `partner_id` bigint NOT NULL, `applicant_user_id` bigint NOT NULL,
 `status` varchar(32) NOT NULL, `verification_status` varchar(32) NOT NULL, `application_amount` decimal(12,2) NOT NULL,
 `available_balance_snapshot` decimal(12,2) NOT NULL, `account_name_snapshot` varchar(100) NOT NULL, `card_number_snapshot` varchar(32) NOT NULL,
 `bank_name_snapshot` varchar(100) NOT NULL, `branch_name_snapshot` varchar(100) DEFAULT NULL, `process_instance_id` varchar(64) DEFAULT NULL,
 `submitted_at` datetime NOT NULL, `approved_amount` decimal(12,2) DEFAULT NULL, `reviewed_by_user_id` bigint DEFAULT NULL,
 `reviewed_at` datetime DEFAULT NULL, `rejection_reason` varchar(500) DEFAULT NULL, `cancelled_by_user_id` bigint DEFAULT NULL,
 `cancelled_at` datetime DEFAULT NULL, `bank_transaction_no` varchar(100) DEFAULT NULL, `proof_file_id` bigint DEFAULT NULL,
 `proof_file_name_snapshot` varchar(255) DEFAULT NULL, `proof_file_type_snapshot` varchar(100) DEFAULT NULL,
 `payout_remark` varchar(500) DEFAULT NULL, `paid_by_user_id` bigint DEFAULT NULL, `paid_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_withdrawal_no` (`tenant_id`,`withdrawal_no`),
 UNIQUE KEY `uk_tenant_process_instance` (`tenant_id`,`process_instance_id`), UNIQUE KEY `uk_tenant_bank_transaction` (`tenant_id`,`bank_transaction_no`),
 KEY `idx_applicant_status` (`tenant_id`,`applicant_user_id`,`status`,`submitted_at`), KEY `idx_finance_status` (`tenant_id`,`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 提现单';
CREATE TABLE IF NOT EXISTS `zsjos_withdrawal_item` (
 `id` bigint NOT NULL AUTO_INCREMENT, `withdrawal_id` bigint NOT NULL, `cashback_id` bigint NOT NULL,
 `amount_snapshot` decimal(12,2) NOT NULL, `active_flag` bit(1) NOT NULL DEFAULT b'1',
 `active_cashback_id` bigint GENERATED ALWAYS AS (IF(`active_flag`=b'1',`cashback_id`,NULL)) STORED,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_withdrawal_cashback` (`tenant_id`,`withdrawal_id`,`cashback_id`),
 UNIQUE KEY `uk_active_cashback` (`tenant_id`,`active_cashback_id`), KEY `idx_withdrawal` (`tenant_id`,`withdrawal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 提现返现关联';
INSERT INTO `infra_config` (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 'ZSJOS提现',1,'最低提现金额','zsjos.withdrawal.minimum-amount','10.00',b'1','1到100000，最多两位小数','migration-V052',NOW(),'migration-V052',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `infra_config` WHERE `config_key`='zsjos.withdrawal.minimum-amount' AND `deleted`=b'0');
INSERT INTO `infra_config` (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 'ZSJOS提现',1,'提现逾期提醒天数','zsjos.withdrawal.reminder-overdue-days','7',b'1','1到365天；默认每周四10:30提醒','migration-V052',NOW(),'migration-V052',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `infra_config` WHERE `config_key`='zsjos.withdrawal.reminder-overdue-days' AND `deleted`=b'0');
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6890,'提现管理','',2,36,6735,'withdrawal','ep:wallet','zsjos/withdrawal/index','ZsjosWithdrawal',0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0'),
(6891,'提现审核','zsjos:withdrawal:review',3,1,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0'),
(6892,'记录打款','zsjos:withdrawal:payout',3,2,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0'),
(6893,'管理员只读提现','zsjos:withdrawal:admin-query',3,3,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0'),
(6894,'我的提现','zsjos:withdrawal:my-query',3,4,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0'),
(6895,'申请提现','zsjos:withdrawal:apply',3,5,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V052',NOW(),'migration-V052',NOW(),b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V052','Withdrawal and offline payout','withdrawal-payout-v1') ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
