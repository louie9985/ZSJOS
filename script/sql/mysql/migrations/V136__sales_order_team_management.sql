-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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
