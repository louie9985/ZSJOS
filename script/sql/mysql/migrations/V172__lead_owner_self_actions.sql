-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V172: grant sales self-service Lead transfer and manual public-sea release.
-- Dependencies/order: apply after V171; the Lead management menu (permission
-- `zsjos:lead:query`) and `sales_specialist` role must already exist.
-- Data scope: two button permissions and their grants to enabled tenants'
-- `sales_specialist` role. No business rows are changed.
-- Repeatability: active permission identities and role grants are inserted only
-- when absent; administrators may revoke either grant without deleting metadata.
-- Recovery: forward-only; remove the two role-menu grants to revoke access.

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '销售本人转派客资','zsjos:lead:owner-transfer',3,30,parent.id,'','','',NULL,
       0,b'1',b'1',b'1','migration-V172',NOW(),'migration-V172',NOW(),b'0'
FROM `system_menu` parent
WHERE parent.permission='zsjos:lead:query' AND parent.type=2 AND parent.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` existing
                  WHERE existing.permission='zsjos:lead:owner-transfer' AND existing.deleted=b'0')
ORDER BY parent.id LIMIT 1;

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '销售本人释放至公海','zsjos:lead:owner-release-public-sea',3,31,parent.id,'','','',NULL,
       0,b'1',b'1',b'1','migration-V172',NOW(),'migration-V172',NOW(),b'0'
FROM `system_menu` parent
WHERE parent.permission='zsjos:lead:query' AND parent.type=2 AND parent.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` existing
                  WHERE existing.permission='zsjos:lead:owner-release-public-sea' AND existing.deleted=b'0')
ORDER BY parent.id LIMIT 1;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V172','Lead owner self-service actions','V172__lead_owner_self_actions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V172','Lead owner self-service actions',
        SHA2('V172__lead_owner_self_actions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
