-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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
    `visible`=b'1',`keep_alive`=b'1',`always_show`=b'1',
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

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V078','Unify Lead management relation scopes','V078__unified_lead_management_scope.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V078','Unify Lead management relation scopes',
        SHA2('V078__unified_lead_management_scope.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
