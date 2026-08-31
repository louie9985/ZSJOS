-- Splits the employee lead inbox into fixed submitter and owner routes.
-- Dependencies: V006, workbench menu 6735, lead query menu 6770, and current role-menu grants.
-- Execution order: hide the legacy mixed route, add fixed routes, derive grants from existing
-- submit/claim/accept/query-all permissions, then record V007.
-- Repeatability: the menu update is idempotent and inserts use stable IDs plus NOT EXISTS guards.
-- Data scope: menu metadata and role-menu grants only; no lead, account, or filter-scheme rows change.
-- Recovery: forward-only. Restore route visibility and manage grants through system permissions if needed.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

UPDATE `system_menu`
SET `visible`=b'0', `updater`='migration-V007', `update_time`=NOW()
WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6778,'我提交的','zsjos:lead:query-submitted',2,15,6735,'leads/submitted','ep:upload-filled','zsjos-workbench','LeadSubmittedInboxPage',0,b'1',b'1',b'1','migration-V007',NOW(),'migration-V007',NOW(),b'0'),
(6779,'我负责的','zsjos:lead:query-owned',2,16,6735,'leads/owned','ep:user-filled','zsjos-workbench','LeadOwnedInboxPage',0,b'1',b'1',b'1','migration-V007',NOW(),'migration-V007',NOW(),b'0');

-- Submit-capable roles receive only the submitted inbox plus the shared query capability.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, 'migration-V007', NOW(), 'migration-V007', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead:submit' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6778) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

-- Sales access is derived from existing claim or acceptance permissions, never from role or post names.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, 'migration-V007', NOW(), 'migration-V007', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission IN ('zsjos:lead:claim','zsjos:lead:accept') AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6779) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

-- Query-all administrators can enter either fixed view while both remain user-scoped.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, 'migration-V007', NOW(), 'migration-V007', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead:query-all' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6778 UNION ALL SELECT 6779) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V007','Split lead inbox into fixed submitter and owner routes','lead-inbox-fixed-audiences-v1');
