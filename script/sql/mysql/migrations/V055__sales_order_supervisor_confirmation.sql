-- V055: add optional direct-supervisor confirmation to sales-order approval.
-- Dependencies/order: apply after V054 and publish a new sign-enabled BPM model version before use.
-- Data scope: creates one audit table and one unassigned menu; no order, task, role, or user rows are changed.
-- Repeatability: CREATE/INSERT IGNORE and version upserts make repeated execution safe.
-- Rollback limitation: dropping the table/menu loses supervisor-confirmation audit; preserve a backup before rollback.

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_order_approval_round' AND column_name='supervisor_confirmation_enabled'),
  'SELECT 1',
  'ALTER TABLE `zsjos_order_approval_round` ADD COLUMN `supervisor_confirmation_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否为上线后支持主管确认的轮次'' AFTER `termination_idempotency_key`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- zsjos_order_supervisor_confirmation
CREATE TABLE IF NOT EXISTS `zsjos_order_supervisor_confirmation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主管确认编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `approval_round_id` bigint NOT NULL COMMENT '审批轮次编号',
  `task_definition_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报名或财务 BPM 节点',
  `requester_user_id` bigint NOT NULL COMMENT '申请人用户编号',
  `supervisor_user_id` bigint NOT NULL COMMENT '直属部门负责人用户编号',
  `parent_task_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原普通审批任务编号',
  `supervisor_task_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BPM 向前加签任务编号',
  `request_reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请原因',
  `decision_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主管意见',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'pending/confirmed/rejected/cancelled',
  `requested_at` datetime NOT NULL COMMENT '申请时间',
  `decided_at` datetime DEFAULT NULL COMMENT '决定或取消时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '并发版本',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_round_task` (`tenant_id`,`approval_round_id`,`task_definition_key`),
  UNIQUE KEY `uk_tenant_supervisor_task` (`tenant_id`,`supervisor_task_id`),
  KEY `idx_tenant_supervisor_status_time` (`tenant_id`,`supervisor_user_id`,`status`,`requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交订单主管确认业务审计';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6850,'主管确认','zsjos:sales-order:supervisor-confirm',2,18,6735,'sales-order-supervisor-confirmations','ep:stamp','zsjos/salesOrderSupervisorConfirmation/index','ZsjosSalesOrderSupervisorConfirmation',0,b'1',b'1',b'1','migration-V055',NOW(),'migration-V055',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V055','Add sales-order supervisor confirmation','sales-order-supervisor-confirmation-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V055','Add sales-order supervisor confirmation',
        SHA2('sales-order-supervisor-confirmation-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
