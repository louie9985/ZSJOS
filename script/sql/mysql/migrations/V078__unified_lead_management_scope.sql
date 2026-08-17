-- V078: unify employee Lead management and align department-leader visibility.
-- Dependency/order: apply after V077.
-- Data scope: menu metadata and role-menu relations only; no Lead, user, role, task, or history row changes.
-- Deletion scope: logically retire only active query-all grants held by sales_manager or sales_specialist.
-- Repeatability: stable menu IDs/permissions, restore-before-insert grants, and version upserts make reruns safe.
-- Recovery: use a reviewed forward migration or a pre-migration role-menu snapshot; do not blindly restore query-all.

DROP PROCEDURE IF EXISTS `zsjos_v078_assert_lead_menus`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v078_assert_lead_menus`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V078 blocked: Lead management menu 6770 is missing or incompatible';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6778 AND `permission`='zsjos:lead:query-submitted' AND `deleted`=b'0')
     OR NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6779 AND `permission`='zsjos:lead:query-owned' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V078 blocked: Lead relation-scope permissions are missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:lead-follow-up:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V078 blocked: Lead follow-up query permission is missing';
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v078_assert_lead_menus`();
DROP PROCEDURE IF EXISTS `zsjos_v078_assert_lead_menus`;

START TRANSACTION;

UPDATE `system_menu`
SET `name`='客资管理',`type`=2,`sort`=15,`parent_id`=6735,`path`='leads/manage',
    `icon`='ep:user-filled',`component`='zsjos/lead/index',`component_name`='ZsjosLeadManagement',
    `visible`=b'0',`keep_alive`=b'1',`always_show`=b'1',
    `updater`='migration-V078',`update_time`=NOW()
WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0';

UPDATE `system_menu`
SET `type`=3,`parent_id`=6770,`path`='',`icon`='',`component`='',`component_name`=NULL,
    `visible`=b'1',`keep_alive`=b'1',`always_show`=b'0',
    `updater`='migration-V078',`update_time`=NOW()
WHERE `id` IN (6778,6779) AND `deleted`=b'0';

UPDATE `system_menu`
SET `parent_id`=6770,`updater`='migration-V078',`update_time`=NOW()
WHERE `id` IN (6845,6846,6847) AND `deleted`=b'0';

-- Every relation-scope permission holder needs the single routable page.
UPDATE `system_role_menu` target
JOIN (
  SELECT DISTINCT rm.role_id,rm.tenant_id
  FROM `system_role_menu` rm
  WHERE rm.menu_id IN (6778,6779) AND rm.deleted=b'0'
) holder ON holder.role_id=target.role_id AND holder.tenant_id=target.tenant_id
SET target.deleted=b'0',target.updater='migration-V078',target.update_time=NOW()
WHERE target.menu_id=6770 AND target.deleted=b'1';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT holder.role_id,6770,'migration-V078',NOW(),'migration-V078',NOW(),b'0',holder.tenant_id
FROM (
  SELECT DISTINCT rm.role_id,rm.tenant_id
  FROM `system_role_menu` rm
  WHERE rm.menu_id IN (6778,6779) AND rm.deleted=b'0'
) holder
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=holder.role_id AND existing.menu_id=6770 AND existing.tenant_id=holder.tenant_id
);

-- Sales team visibility is department-scoped, so these two role codes must not retain tenant-wide query-all.
UPDATE `system_role_menu` rm
JOIN `system_role` role ON role.id=rm.role_id AND role.tenant_id=rm.tenant_id AND role.deleted=b'0'
JOIN `system_menu` menu ON menu.id=rm.menu_id AND menu.permission='zsjos:lead:query-all' AND menu.deleted=b'0'
SET rm.deleted=b'1',rm.updater='migration-V078',rm.update_time=NOW()
WHERE rm.deleted=b'0' AND role.code IN ('sales_manager','sales_specialist');

-- Sales managers need the feature permission in addition to the object-level department scope.
UPDATE `system_role_menu` target
JOIN `system_role` role ON role.id=target.role_id AND role.tenant_id=target.tenant_id
JOIN `system_menu` menu ON menu.id=target.menu_id AND menu.permission='zsjos:lead-follow-up:query'
SET target.deleted=b'0',target.updater='migration-V078',target.update_time=NOW()
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND menu.deleted=b'0' AND target.deleted=b'1';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V078',NOW(),'migration-V078',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission='zsjos:lead-follow-up:query' AND menu.deleted=b'0'
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=role.id AND existing.menu_id=menu.id AND existing.tenant_id=role.tenant_id
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V078','Unify Lead management relation scopes','V078__unified_lead_management_scope.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V078','Unify Lead management relation scopes',
        SHA2('V078__unified_lead_management_scope.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
