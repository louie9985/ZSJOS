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

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V172',NOW(),'migration-V172',NOW(),b'0',role.tenant_id
FROM `system_role` role JOIN `system_menu` menu
  ON menu.permission IN ('zsjos:lead:owner-transfer','zsjos:lead:owner-release-public-sea')
 AND menu.deleted=b'0'
WHERE role.code='sales_specialist' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V172','Lead owner self-service actions','V172__lead_owner_self_actions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V172','Lead owner self-service actions',
        SHA2('V172__lead_owner_self_actions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
