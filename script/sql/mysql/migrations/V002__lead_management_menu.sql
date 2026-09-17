-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- Adds the read-only lead-management menu and permission grants.
-- Dependencies: system_menu ids 6735 (Workbench) and 6736 (Submit Lead), plus system_role/system_role_menu.
-- Repeatability: every insert is guarded by a stable id, permission, or role/menu NOT EXISTS check.
-- Data scope: menu metadata and role-menu grants only; no lead or other business rows are changed.
-- Rollback limitation: grants should be revoked through system permission administration; this migration is forward-only.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

INSERT IGNORE INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6770, '客资管理', 'zsjos:lead:query', 2, 15, 6735, 'leads/manage', 'ep:user-filled', 'zsjos/lead/index', 'ZsjosLeadManagement', 0, b'1', b'1', b'1', 'migration-V002', NOW(), 'migration-V002', NOW(), b'0'),
  (6771, '查看全部客资', 'zsjos:lead:query-all', 3, 1, 6770, '', '', '', NULL, 0, b'1', b'1', b'1', 'migration-V002', NOW(), 'migration-V002', NOW(), b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V002', 'Add read-only lead management menu and permissions', 'lead-management-menu-v1');
