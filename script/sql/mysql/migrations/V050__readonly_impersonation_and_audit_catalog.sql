-- V050: read-only impersonation sessions and dedicated request audit.
-- Dependencies/order: apply after V049 and the System user/menu baseline.
-- Data scope: additive empty session/audit tables and server-owned permissions; no role grants.
-- Repeatability: CREATE TABLE IF NOT EXISTS and stable menu IDs make reruns safe.
-- Rollback limitation: disable the feature; retain session and request history for audit.
-- This file must not be executed without separate environment approval.

CREATE TABLE IF NOT EXISTS `zsjos_impersonation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT, `administrator_user_id` bigint NOT NULL,
  `administrator_name_snapshot` varchar(100) NOT NULL, `target_user_id` bigint NOT NULL,
  `target_name_snapshot` varchar(100) NOT NULL, `reason` varchar(500) NOT NULL,
  `status` varchar(20) NOT NULL COMMENT 'active/ended/expired',
  `started_at` datetime NOT NULL, `last_active_at` datetime NOT NULL,
  `ended_at` datetime DEFAULT NULL, `ended_reason` varchar(500) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_admin_status` (`tenant_id`,`administrator_user_id`,`status`),
  KEY `idx_idle` (`tenant_id`,`status`,`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 只读借视图会话';

CREATE TABLE IF NOT EXISTS `zsjos_impersonation_request_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `session_id` bigint NOT NULL,
  `administrator_user_id` bigint NOT NULL, `target_user_id` bigint NOT NULL,
  `http_method` varchar(10) NOT NULL, `request_path` varchar(500) NOT NULL,
  `occurred_at` datetime NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_session_time` (`tenant_id`,`session_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 借视图请求审计';

CREATE TABLE IF NOT EXISTS `zsjos_business_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `operator_user_id` bigint DEFAULT NULL,
  `operator_name_snapshot` varchar(100) NOT NULL, `operator_role_snapshot` varchar(500) NOT NULL,
  `category_code` varchar(64) NOT NULL, `action_code` varchar(100) NOT NULL,
  `target_type` varchar(64) NOT NULL, `target_id` varchar(100) NOT NULL,
  `detail_json` varchar(2000) NOT NULL, `source_ip` varchar(50) DEFAULT NULL,
  `occurred_at` datetime NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_category_action_time` (`tenant_id`,`category_code`,`action_code`,`occurred_at`),
  KEY `idx_target` (`tenant_id`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 固定目录业务审计';

CREATE TABLE IF NOT EXISTS `zsjos_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `task_no` varchar(32) NOT NULL, `export_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL COMMENT 'queued/prechecking/generating/ready/failed/cancelled/expired',
  `creator_user_id` bigint NOT NULL, `creator_name_snapshot` varchar(100) NOT NULL,
  `creator_role_snapshot` varchar(500) NOT NULL, `filter_json` text NOT NULL,
  `permission_snapshot_json` varchar(2000) NOT NULL, `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime DEFAULT NULL, `lease_expires_at` datetime DEFAULT NULL,
  `result_file_id` bigint DEFAULT NULL, `result_file_name` varchar(255) DEFAULT NULL,
  `result_file_size` bigint DEFAULT NULL, `ready_at` datetime DEFAULT NULL, `expires_at` datetime DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL, `failure_message` varchar(500) DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL, `last_active_at` datetime NOT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_task_no` (`tenant_id`,`task_no`),
  KEY `idx_worker_scan` (`tenant_id`,`status`,`next_attempt_at`,`lease_expires_at`),
  KEY `idx_creator_time` (`tenant_id`,`creator_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 异步导出任务';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6870,'借视图','zsjos:impersonation:query',2,32,6735,'impersonation','ep:view','zsjos/impersonation/index','ZsjosImpersonation',0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6871,'开始借视图','zsjos:impersonation:start',3,1,6870,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6872,'异步导出','zsjos:export:query',2,33,6735,'export-task','ep:download','zsjos/exportTask/index','ZsjosExportTask',0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6873,'导出客资','zsjos:export:lead',3,1,6872,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6874,'导出订单','zsjos:export:order',3,2,6872,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6875,'导出返现','zsjos:export:cashback',3,3,6872,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6876,'导出提现','zsjos:export:withdrawal',3,4,6872,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6877,'业务审计','zsjos:audit:query',2,34,6735,'business-audit','ep:document','zsjos/businessAudit/index','ZsjosBusinessAudit',0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0'),
(6878,'借视图审计','zsjos:audit:query-impersonation',3,1,6877,'','','',NULL,0,b'1',b'1',b'1','migration-V050',NOW(),'migration-V050',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V050','Read-only impersonation and audit catalog','readonly-impersonation-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V050','Read-only impersonation and audit catalog',SHA2('readonly-impersonation-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`),
  `release_version`=VALUES(`release_version`),`installed_at`=VALUES(`installed_at`);
