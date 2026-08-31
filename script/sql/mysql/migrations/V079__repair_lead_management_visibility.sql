-- V079: repair the unified Lead management page after V078 visibility drift.
-- Dependency/order: apply after V078.
-- Data scope: menu metadata and role-menu relations only; no Lead or account data changes.
-- Repeatability: the visibility update and grant upserts are safe to rerun.

START TRANSACTION;

UPDATE `system_menu`
SET `visible`=b'1',`type`=2,`parent_id`=6735,`path`='leads/manage',
    `component`='zsjos/lead/index',`component_name`='ZsjosLeadManagement',
    `updater`='migration-V079',`update_time`=NOW()
WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0';

UPDATE `system_role_menu` target
JOIN (
  SELECT DISTINCT rm.role_id,rm.tenant_id
  FROM `system_role_menu` rm
  JOIN `system_menu` menu ON menu.id=rm.menu_id
    AND menu.permission IN ('zsjos:lead:query','zsjos:lead:query-all',
                            'zsjos:lead:query-submitted','zsjos:lead:query-owned')
    AND menu.deleted=b'0'
  WHERE rm.deleted=b'0'
) holder ON holder.role_id=target.role_id AND holder.tenant_id=target.tenant_id
SET target.deleted=b'0',target.updater='migration-V079',target.update_time=NOW()
WHERE target.menu_id=6770 AND target.deleted=b'1';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT holder.role_id,6770,'migration-V079',NOW(),'migration-V079',NOW(),b'0',holder.tenant_id
FROM (
  SELECT DISTINCT rm.role_id,rm.tenant_id
  FROM `system_role_menu` rm
  JOIN `system_menu` menu ON menu.id=rm.menu_id
    AND menu.permission IN ('zsjos:lead:query','zsjos:lead:query-all',
                            'zsjos:lead:query-submitted','zsjos:lead:query-owned')
    AND menu.deleted=b'0'
  WHERE rm.deleted=b'0'
) holder
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=holder.role_id AND existing.menu_id=6770
    AND existing.tenant_id=holder.tenant_id
);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V079','Repair unified Lead management visibility','V079__repair_lead_management_visibility.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V079','Repair unified Lead management visibility',
        SHA2('V079__repair_lead_management_visibility.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
