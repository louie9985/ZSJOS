-- V162: ZSJOS purchase drafts and dual collection paths.
-- Additive and repeatable. This migration is not executed by this workstream.
CREATE TABLE IF NOT EXISTS `zsjos_purchase_intent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_intent_no` varchar(64) NOT NULL,
  `collection_mode` varchar(32) NOT NULL COMMENT 'online_link/offline_paid',
  `purchase_type` varchar(32) NOT NULL,
  `lead_id` bigint DEFAULT NULL,
  `person_id` bigint NOT NULL,
  `opportunity_id` bigint DEFAULT NULL,
  `source_key` varchar(128) DEFAULT NULL,
  `initiator_user_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `draft_json` json NOT NULL,
  `item_snapshot_json` json NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `current_order_id` bigint DEFAULT NULL,
  `snapshot_locked` bit(1) NOT NULL DEFAULT b'0',
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `last_idempotency_key` varchar(128) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_purchase_intent_no` (`tenant_id`,`purchase_intent_no`),
  KEY `idx_tenant_purchase_source` (`tenant_id`,`lead_id`,`person_id`,`purchase_type`,`initiator_user_id`,`status`),
  KEY `idx_tenant_purchase_order` (`tenant_id`,`current_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 订单购买草稿';
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_purchase_intent' AND column_name='source_key'),'SELECT 1','ALTER TABLE `zsjos_purchase_intent` ADD COLUMN `source_key` varchar(128) DEFAULT NULL COMMENT ''来源标识'' AFTER `opportunity_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='purchase_intent_id'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `purchase_intent_id` bigint DEFAULT NULL COMMENT ''购买草稿编号'' AFTER `id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='provider'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `provider` varchar(32) DEFAULT NULL COMMENT ''支付提供方'' AFTER `status`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='channel'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `channel` varchar(32) DEFAULT NULL COMMENT ''wechat/alipay'' AFTER `provider`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='reqsn'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `reqsn` varchar(64) DEFAULT NULL COMMENT ''通联商户订单号'' AFTER `channel`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='link_url'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `link_url` varchar(1024) DEFAULT NULL COMMENT ''公开支付链接'' AFTER `link_token_hash`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_order' AND column_name='queried_at'),'SELECT 1','ALTER TABLE `zsjos_payment_order` ADD COLUMN `queried_at` datetime DEFAULT NULL COMMENT ''最近查单时间'' AFTER `closed_at`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `zsjos_payment_order` MODIFY COLUMN `lead_id` bigint DEFAULT NULL COMMENT '首购来源客资编号；复购可为空';

CREATE TABLE IF NOT EXISTS `zsjos_payment_gateway_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `event_id` varchar(128) NOT NULL, `payment_order_id` bigint DEFAULT NULL,
  `event_type` varchar(32) NOT NULL, `request_payload` json DEFAULT NULL, `response_payload` json DEFAULT NULL,
  `signature_valid` bit(1) NOT NULL DEFAULT b'0', `processing_result` varchar(64) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_payment_event` (`tenant_id`,`event_id`), KEY `idx_tenant_payment_event_order` (`tenant_id`,`payment_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 通联支付网关事件';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_transaction' AND column_name='reqsn'),'SELECT 1','ALTER TABLE `zsjos_payment_transaction` ADD COLUMN `reqsn` varchar(64) DEFAULT NULL COMMENT ''通联商户订单号'' AFTER `payment_order_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_transaction' AND column_name='trx_id'),'SELECT 1','ALTER TABLE `zsjos_payment_transaction` ADD COLUMN `trx_id` varchar(128) DEFAULT NULL COMMENT ''通联交易号'' AFTER `external_transaction_no`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_transaction' AND column_name='channel_transaction_no'),'SELECT 1','ALTER TABLE `zsjos_payment_transaction` ADD COLUMN `channel_transaction_no` varchar(128) DEFAULT NULL COMMENT ''通联渠道流水号'' AFTER `trx_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_transaction' AND column_name='amount_fen'),'SELECT 1','ALTER TABLE `zsjos_payment_transaction` ADD COLUMN `amount_fen` int DEFAULT NULL COMMENT ''金额分'' AFTER `amount`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_transaction' AND column_name='source'),'SELECT 1','ALTER TABLE `zsjos_payment_transaction` ADD COLUMN `source` varchar(16) DEFAULT NULL COMMENT ''notify/query'' AFTER `amount_fen`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='purchase_intent_id'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `purchase_intent_id` bigint DEFAULT NULL COMMENT ''购买草稿编号'' AFTER `source_payment_order_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V162','Add purchase intent and dual collection payment draft','zsjos-purchase-intent-payment-v1');
