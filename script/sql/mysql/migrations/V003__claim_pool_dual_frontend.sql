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

-- Before removing the claim permission from the parent, preserve every existing claimant's action permission.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT existing_parent.role_id, 6772, 'migration-V003', NOW(), 'migration-V003', NOW(), b'0', existing_parent.tenant_id
FROM `system_role_menu` existing_parent
JOIN `system_menu` legacy_parent ON legacy_parent.id = existing_parent.menu_id
  AND legacy_parent.permission = 'zsjos:lead:claim' AND legacy_parent.deleted = b'0'
WHERE existing_parent.menu_id = 6749 AND existing_parent.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing_action
    WHERE existing_action.role_id = existing_parent.role_id AND existing_action.menu_id = 6772
      AND existing_action.tenant_id = existing_parent.tenant_id AND existing_action.deleted = b'0'
  );

UPDATE `system_menu`
SET `permission` = '', `component` = 'zsjos/leadClaimPool/index',
    `component_name` = 'ZsjosLeadClaimPool', `updater` = 'migration-V003', `update_time` = NOW()
WHERE `id` = 6749 AND `deleted` = b'0';

-- Roles that already own query-all receive the read-only route. Existing claimants keep the parent grant above.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT query_all_grant.role_id, 6749, 'migration-V003', NOW(), 'migration-V003', NOW(), b'0', query_all_grant.tenant_id
FROM `system_role_menu` query_all_grant
JOIN `system_menu` query_all_menu ON query_all_menu.id = query_all_grant.menu_id
  AND query_all_menu.permission = 'zsjos:lead:query-all' AND query_all_menu.deleted = b'0'
WHERE query_all_grant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = query_all_grant.role_id AND existing.menu_id = 6749
      AND existing.tenant_id = query_all_grant.tenant_id AND existing.deleted = b'0'
  );

-- Every role with the claim action must also own the route; this remains permission-based rather than role-name-based.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT claim_grant.role_id, 6749, 'migration-V003', NOW(), 'migration-V003', NOW(), b'0', claim_grant.tenant_id
FROM `system_role_menu` claim_grant
WHERE claim_grant.menu_id = 6772 AND claim_grant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = claim_grant.role_id AND existing.menu_id = 6749
      AND existing.tenant_id = claim_grant.tenant_id AND existing.deleted = b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V003', 'Split claim-pool menu and action permissions', 'claim-pool-dual-frontend-v1');
