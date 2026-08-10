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

-- Preserve every current submit-capable role's access to its own submitted leads.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT submit_grant.role_id, 6770, 'migration-V002', NOW(), 'migration-V002', NOW(), b'0', submit_grant.tenant_id
FROM `system_role_menu` submit_grant
WHERE submit_grant.menu_id = 6736 AND submit_grant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = submit_grant.role_id AND existing.menu_id = 6770
      AND existing.tenant_id = submit_grant.tenant_id AND existing.deleted = b'0'
  );

-- Sales specialists receive related-lead query; administrator roles additionally receive query-all.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role.id, 6770, 'migration-V002', NOW(), 'migration-V002', NOW(), b'0', role.tenant_id
FROM `system_role` role
WHERE role.code IN ('sales_specialist', 'system_administrator', 'super_admin') AND role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = role.id AND existing.menu_id = 6770
      AND existing.tenant_id = role.tenant_id AND existing.deleted = b'0'
  );

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role.id, 6771, 'migration-V002', NOW(), 'migration-V002', NOW(), b'0', role.tenant_id
FROM `system_role` role
WHERE role.code IN ('system_administrator', 'super_admin') AND role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = role.id AND existing.menu_id = 6771
      AND existing.tenant_id = role.tenant_id AND existing.deleted = b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V002', 'Add read-only lead management menu and permissions', 'lead-management-menu-v1');
