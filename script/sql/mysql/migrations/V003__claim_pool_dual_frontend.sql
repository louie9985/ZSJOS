-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- Registers the dual-frontend claim-pool page and separates navigation from claiming.
-- Dependencies: system_menu ids 6749 and 6771, plus system_role/system_role_menu.
-- Repeatability: inserts use NOT EXISTS or stable ids; the targeted menu update is idempotent.
-- Data scope: menu metadata, role-menu grants and schema-version metadata only.
-- Rollback limitation: permissions should be changed through system administration; this migration is forward-only.

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
  (6772, '抢单', 'zsjos:lead:claim', 3, 2, 6749, '', '', '', NULL, 0, b'1', b'1', b'1', 'migration-V003', NOW(), 'migration-V003', NOW(), b'0');

UPDATE `system_menu`
SET `permission` = '', `component` = 'zsjos/leadClaimPool/index',
    `component_name` = 'ZsjosLeadClaimPool', `updater` = 'migration-V003', `update_time` = NOW()
WHERE `id` = 6749 AND `deleted` = b'0';

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V003', 'Split claim-pool menu and action permissions', 'claim-pool-dual-frontend-v1');
