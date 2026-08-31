-- EAM V009: anonymous asset links, employee edit codes and audit records.
-- Repeatable DDL for environments after the EAM baseline; no business rows are seeded.
SET @eam_asset_version_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='version');
SET @eam_asset_version_sql := IF(@eam_asset_version_exists=0, 'ALTER TABLE `eam_asset` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''乐观锁版本'' AFTER `previous_status`', 'SELECT 1');
PREPARE eam_asset_version_stmt FROM @eam_asset_version_sql; EXECUTE eam_asset_version_stmt; DEALLOCATE PREPARE eam_asset_version_stmt;
CREATE TABLE IF NOT EXISTS `eam_public_asset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `revoked_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 1,
  `creator` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL, `updater` varchar(64) DEFAULT NULL, `update_time` datetime NOT NULL, `deleted` bit NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_public_asset_token_hash` (`token_hash`), UNIQUE KEY `uk_eam_public_asset_token_asset` (`tenant_id`,`asset_id`,`status`), KEY `idx_eam_public_asset_token_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM资产公开链接令牌';
CREATE TABLE IF NOT EXISTS `eam_public_edit_code` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `employee_id` bigint NOT NULL, `user_id` bigint NOT NULL, `encrypted_code` varchar(512) NOT NULL, `code_hmac` varchar(128) NOT NULL, `status` tinyint NOT NULL DEFAULT 1,
  `creator` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL, `updater` varchar(64) DEFAULT NULL, `update_time` datetime NOT NULL, `deleted` bit NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_public_edit_code_employee` (`tenant_id`,`employee_id`), KEY `idx_eam_public_edit_code_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM行政员工公开编辑口令';
CREATE TABLE IF NOT EXISTS `eam_public_edit_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `asset_id` bigint NOT NULL, `employee_id` bigint DEFAULT NULL, `client_ip` varchar(64) DEFAULT NULL, `result_code` varchar(32) NOT NULL, `failure_reason` varchar(255) DEFAULT NULL,
  `creator` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL, `updater` varchar(64) DEFAULT NULL, `update_time` datetime NOT NULL, `deleted` bit NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_eam_public_edit_audit_asset` (`tenant_id`,`asset_id`), KEY `idx_eam_public_edit_audit_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM匿名编辑审计';
-- 7190 is already used by eam:purchase:close in V007. Keep this permission on a dedicated free ID.
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) SELECT 7198,'资产公开编辑口令','eam:asset:public-edit-code',3,8,7102,'','','',0,b'1',b'1',b'1','migration-eam-V009',NOW(),'migration-eam-V009',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:asset:public-edit-code' AND deleted=b'0');
