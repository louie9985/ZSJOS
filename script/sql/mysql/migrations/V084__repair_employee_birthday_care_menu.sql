-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V084: repair the V081 employee birthday-care menu IDs that collide with V058 FMS menus.
-- Dependencies: V081 birthday-care metadata and V058 HRM/FMS menu roots.
-- Data scope: three System menu rows and their relations to enabled super_admin roles.
-- Repeatability: component, permission, ID and active role-menu guards prevent duplicates.
-- Rollback: forward-only; disable the repaired menu instead of restoring the conflicting IDs.

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602100,'生日关怀设置','hrm:birthday-care-config:query',2,90,601476,'birthday-care','ep:present','hrm/birthday-care/index','HrmBirthdayCare',0,b'1',b'1',b'1','migration-V084',NOW(),'migration-V084',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=602100)
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                   WHERE `parent_id`=601476 AND `component`='hrm/birthday-care/index' AND `deleted`=b'0');

SET @birthday_care_menu_id := (
  SELECT `id` FROM `system_menu`
   WHERE `parent_id`=601476 AND `component`='hrm/birthday-care/index' AND `deleted`=b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602101,'生日关怀查询','hrm:birthday-care-config:query',3,1,@birthday_care_menu_id,'','','',NULL,0,b'1',b'1',b'1','migration-V084',NOW(),'migration-V084',NOW(),b'0'
FROM DUAL
WHERE @birthday_care_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=602101)
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                   WHERE `parent_id`=@birthday_care_menu_id AND `permission`='hrm:birthday-care-config:query'
                     AND `type`=3 AND `deleted`=b'0');

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602102,'生日关怀修改','hrm:birthday-care-config:update',3,2,@birthday_care_menu_id,'','','',NULL,0,b'1',b'1',b'1','migration-V084',NOW(),'migration-V084',NOW(),b'0'
FROM DUAL
WHERE @birthday_care_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=602102)
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                   WHERE `parent_id`=@birthday_care_menu_id AND `permission`='hrm:birthday-care-config:update'
                     AND `type`=3 AND `deleted`=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V084','Repair employee birthday care menu IDs','repair-employee-birthday-care-menu-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
