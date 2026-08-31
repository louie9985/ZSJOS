-- V161 日历日程入口与独立页面权限。
-- Dependencies: V146 media account calendar menus and V132+ workbench_render_mode column.
-- Scope: server-owned System menu metadata, tenant packages, and role-menu grants only.
-- Repeatability: guarded upserts and INSERT IGNORE style role grants; no business rows are changed.
-- Rollback: disable menu 73604 and revoke its grants in a later reviewed permission migration.

START TRANSACTION;

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (73604,'日历日程','zsjos:media-calendar:all-query',2,2,73600,'all','ep:calendar',
   'zsjos/mediaCalendarAll/index','ZsjosMediaCalendarAll','native',0,b'1',b'1',b'1',
   'migration-V161',NOW(),'migration-V161',NOW(),b'0')
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),
  `parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),
  `component_name`=VALUES(`component_name`),`workbench_render_mode`=VALUES(`workbench_render_mode`),
  `status`=VALUES(`status`),`visible`=VALUES(`visible`),`updater`='migration-V161',`update_time`=NOW(),`deleted`=b'0';

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73604),`updater`='migration-V161',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73604','$');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT grant_row.`role_id`,73604,'migration-V161',NOW(),'migration-V161',NOW(),b'0',grant_row.`tenant_id`
FROM `system_role_menu` grant_row
WHERE grant_row.`deleted`=b'0' AND grant_row.`menu_id` IN (73600,73601)
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id`=grant_row.`role_id` AND existing.`tenant_id`=grant_row.`tenant_id`
      AND existing.`menu_id`=73604 AND existing.`deleted`=b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V161','Media calendar schedule view','V161__media_calendar_all_view.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V161','Media calendar schedule view',SHA2('V161__media_calendar_all_view.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
