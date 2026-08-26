-- V139: restore the canonical Lead public-sea route and add supervisor Lead action permissions.
-- Dependencies/order: apply after V138; subordinate-sales menu and Lead aging-pool menu must exist.
-- Data scope: System menu metadata and initial sales_manager role-menu grants only.
-- Repeatability: permission identities and reserved IDs are stable; initial grants run only before V139 is recorded.
-- Recovery: forward-only; administrators may remove grants while retaining permission definitions and the canonical route.

START TRANSACTION;

UPDATE `system_menu`
SET `name`='公海池',`path`='lead-aging-pool',`updater`='migration-V139',`update_time`=NOW()
WHERE `permission`='zsjos:lead-aging-pool:query' AND `type`=2 AND `deleted`=b'0';

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT permission_row.name,permission_row.permission,3,permission_row.sort,6814,'','','',NULL,
       0,b'1',b'1',b'0','migration-V139',NOW(),'migration-V139',NOW(),b'0'
FROM (
  SELECT '恢复挂起客资' name,'zsjos:subordinate-sales:lead-restore' permission,6 sort
  UNION ALL SELECT '转派下属客资','zsjos:subordinate-sales:lead-transfer',7
  UNION ALL SELECT '回收下属客资','zsjos:subordinate-sales:lead-recycle',8
  UNION ALL SELECT '释放客资至抢单池','zsjos:subordinate-sales:lead-release-claim-pool',9
  UNION ALL SELECT '释放客资至公海池','zsjos:subordinate-sales:lead-release-public-sea',10
) permission_row
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing
                  WHERE existing.permission=permission_row.permission AND existing.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V139',NOW(),'migration-V139',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission IN (
  'zsjos:subordinate-sales:lead-restore','zsjos:subordinate-sales:lead-transfer',
  'zsjos:subordinate-sales:lead-recycle','zsjos:subordinate-sales:lead-release-claim-pool',
  'zsjos:subordinate-sales:lead-release-public-sea') AND menu.deleted=b'0'
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V139')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V139','Lead supervisor actions and canonical public-sea route',
        'V139__lead_supervisor_actions_and_public_sea_route.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V139','Lead supervisor actions and canonical public-sea route',
        SHA2('V139__lead_supervisor_actions_and_public_sea_route.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
