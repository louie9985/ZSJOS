-- V092: add the subordinate-sales one-click pause permission for enabled sales managers.
-- Dependencies/order: apply after V091; subordinate-sales menu 6814 must exist.
-- Data scope: one System button permission and sales_manager role-menu relations only.
-- No account, dispatch preference, Lead, assignment, presence, or audit row is modified.
-- Repeatability: stable menu ID/permission; default role grants run only before the V092 marker exists.
-- Recovery: forward-only; administrators may remove the role grant while retaining the permission definition.

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 6819,'一键下班','zsjos:subordinate-sales:pause-all',3,5,6814,'','','',NULL,0,b'1',b'1',b'0',
       'migration-V092',NOW(),'migration-V092',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6819 AND `deleted`=b'0')
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission`='zsjos:subordinate-sales:pause-all' AND `deleted`=b'0');

UPDATE `system_menu`
SET `name`='一键下班',`type`=3,`sort`=5,`parent_id`=6814,`status`=0,`deleted`=b'0',
    `updater`='migration-V092',`update_time`=NOW()
WHERE `id`=6819 AND `permission`='zsjos:subordinate-sales:pause-all';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V092',NOW(),'migration-V092',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission='zsjos:subordinate-sales:pause-all' AND menu.deleted=b'0'
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V092')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V092','Subordinate sales pause-all permission','V092__subordinate_sales_pause_all_permission.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V092','Subordinate sales pause-all permission',SHA2('V092__subordinate_sales_pause_all_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
