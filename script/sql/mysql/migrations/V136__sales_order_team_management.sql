-- V136: add department-team sales-order read management for sales supervisors.
-- Dependencies: V025 sales-order workbench menu and the current System role/menu tables.
-- Data scope: one read-only menu permission, its sales_manager grant, and no business rows.
-- Repeatability: stable permission/path guards and role-menu existence checks.
-- Rollback limitation: disable the menu and retain all order, approval, and permission history.

SET NAMES utf8mb4;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73510,'团队订单','zsjos:sales-order:query-team',2,18,6735,'sales-orders/team','ep:tickets','zsjos/mySalesOrder/index','ZsjosTeamSalesOrder',0,b'1',b'1',b'1','migration-V136',NOW(),'migration-V136',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:sales-order:query-team' AND `deleted`=b'0')
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73510);

UPDATE `system_menu`
SET `parent_id`=6735,`path`='sales-orders/team',`updater`='migration-V136',`update_time`=NOW()
WHERE `permission`='zsjos:sales-order:query-team' AND `deleted`=b'0';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V136',NOW(),'migration-V136',NOW(),b'0',role.tenant_id
FROM `system_role` role JOIN `system_menu` menu ON menu.permission='zsjos:sales-order:query-team' AND menu.deleted=b'0'
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                 WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                   AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V136','Add sales-order team management permission','V136__sales_order_team_management.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V136','Add sales-order team management permission',
        SHA2('V136__sales_order_team_management.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

SELECT 'sales_order_v136_menu' AS check_name,
       IF(EXISTS(SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:sales-order:query-team'
                 AND `path`='sales-orders/team' AND `deleted`=b'0'),'PASS','FAIL') AS result;
