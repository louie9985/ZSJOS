-- V035: subordinate-sales management, manual public sea, and operation audit.
-- Dependencies/order: apply after V034 and the System department/user/post/menu baseline.
-- Data scope: additive ZSJOS tables and server-owned menu grants only; no business rows are changed.
-- Repeatability: IF NOT EXISTS DDL, stable menu IDs, and guarded role-menu inserts.
-- Recovery: forward-only; disable menu permissions to stop use and retain audit/public-sea history.

CREATE TABLE IF NOT EXISTS `zsjos_lead_public_sea_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `lead_id` bigint NOT NULL, `owner_user_id` bigint NOT NULL,
  `collaborator_user_id` bigint DEFAULT NULL, `released_by_user_id` bigint NOT NULL,
  `released_at` datetime NOT NULL, `release_reason` varchar(500) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_lead` (`tenant_id`,`lead_id`),
  KEY `idx_tenant_owner_released` (`tenant_id`,`owner_user_id`,`released_at`),
  KEY `idx_tenant_collaborator` (`tenant_id`,`collaborator_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 人工公海协作记录';

CREATE TABLE IF NOT EXISTS `zsjos_subordinate_sales_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `action_type` varchar(32) NOT NULL,
  `operator_user_id` bigint NOT NULL, `target_user_id` bigint DEFAULT NULL, `lead_id` bigint DEFAULT NULL,
  `before_value` varchar(1000) DEFAULT NULL, `after_value` varchar(1000) DEFAULT NULL,
  `reason` varchar(500) NOT NULL, `occurred_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_tenant_operator_time` (`tenant_id`,`operator_user_id`,`occurred_at`),
  KEY `idx_tenant_target_time` (`tenant_id`,`target_user_id`,`occurred_at`),
  KEY `idx_tenant_lead_time` (`tenant_id`,`lead_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 下属销售操作审计';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6814,'下属销售','zsjos:subordinate-sales:query',2,20,6735,'subordinate-sales','ep:user','zsjos/subordinateSales/index','ZsjosSubordinateSales',0,b'1',b'1',b'1','migration-V035',NOW(),'migration-V035',NOW(),b'0'),
(6815,'停启下属账号','zsjos:subordinate-sales:account-status',3,1,6814,'','','',NULL,0,b'1',b'1',b'1','migration-V035',NOW(),'migration-V035',NOW(),b'0'),
(6816,'修改下属接单','zsjos:subordinate-sales:dispatch-mode',3,2,6814,'','','',NULL,0,b'1',b'1',b'1','migration-V035',NOW(),'migration-V035',NOW(),b'0'),
(6817,'批量转派客资','zsjos:subordinate-sales:batch-transfer',3,3,6814,'','','',NULL,0,b'1',b'1',b'1','migration-V035',NOW(),'migration-V035',NOW(),b'0'),
(6818,'批量释放公海','zsjos:subordinate-sales:batch-public-sea',3,4,6814,'','','',NULL,0,b'1',b'1',b'1','migration-V035',NOW(),'migration-V035',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,target.id,'migration-V035',NOW(),'migration-V035',NOW(),b'0',source.tenant_id
FROM system_role_menu source JOIN system_menu source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead:appeal:review-sales-manager' AND source_menu.deleted=b'0'
JOIN system_menu target ON target.id BETWEEN 6814 AND 6818 AND target.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
 WHERE existing.tenant_id=source.tenant_id AND existing.role_id=source.role_id
 AND existing.menu_id=target.id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V035','Add subordinate-sales management','subordinate-sales-management-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
