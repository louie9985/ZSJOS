-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V161','Media calendar schedule view','V161__media_calendar_all_view.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V161','Media calendar schedule view',SHA2('V161__media_calendar_all_view.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
