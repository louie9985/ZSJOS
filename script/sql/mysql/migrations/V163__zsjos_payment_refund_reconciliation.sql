-- V163: ZSJOS 通联全额退款与主动对账
-- 依赖 V151 支付单/网关事件结构；本脚本只新增退款表，不执行既有业务数据删除。
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_payment_gateway_event' AND column_name='business_type'),'SELECT 1','ALTER TABLE `zsjos_payment_gateway_event` ADD COLUMN `business_type` varchar(32) DEFAULT NULL, ADD COLUMN `operation` varchar(32) DEFAULT NULL, ADD COLUMN `direction` varchar(16) DEFAULT NULL, ADD COLUMN `business_id` bigint DEFAULT NULL, ADD COLUMN `request_no` varchar(128) DEFAULT NULL, ADD COLUMN `http_status` int DEFAULT NULL, ADD COLUMN `gateway_retcode` varchar(32) DEFAULT NULL, ADD COLUMN `gateway_trxstatus` varchar(32) DEFAULT NULL, ADD COLUMN `error_category` varchar(64) DEFAULT NULL, ADD COLUMN `payload_digest` varchar(128) DEFAULT NULL')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `zsjos_payment_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '退款单编号',
  `refund_no` varchar(64) NOT NULL COMMENT '退款业务编号',
  `purchase_intent_id` bigint DEFAULT NULL,
  `payment_order_id` bigint NOT NULL,
  `payment_transaction_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `refund_amount` decimal(18,2) NOT NULL,
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `reason` varchar(500) NOT NULL,
  `requester_user_id` bigint NOT NULL,
  `executor_user_id` bigint DEFAULT NULL,
  `approval_mode` varchar(32) NOT NULL COMMENT 'direct/bpm',
  `process_instance_id` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL COMMENT 'approval_pending/approved/submitting/accepted/unknown/succeeded/failed/manual_review',
  `provider` varchar(32) NOT NULL DEFAULT 'allinpay',
  `refund_reqsn` varchar(64) NOT NULL,
  `original_reqsn` varchar(64) DEFAULT NULL,
  `original_trx_id` varchar(128) DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `failed_at` datetime DEFAULT NULL,
  `last_queried_at` datetime DEFAULT NULL,
  `next_reconcile_at` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `last_error_code` varchar(64) DEFAULT NULL,
  `last_error_message` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_refund_no` (`tenant_id`,`refund_no`),
  UNIQUE KEY `uk_tenant_refund_reqsn` (`tenant_id`,`refund_reqsn`), UNIQUE KEY `uk_tenant_refund_idem` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_refund_tx_status` (`tenant_id`,`payment_transaction_id`,`status`), KEY `idx_tenant_refund_reconcile` (`tenant_id`,`status`,`next_reconcile_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 通联退款单';
INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V163','Add ZSJOS payment refund and reconciliation','zsjos-payment-refund-v1');

