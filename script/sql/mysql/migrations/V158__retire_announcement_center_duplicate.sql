-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V158: retire the duplicate announcement-center menu and keep the original System notice menu as the
-- single source of truth for both Vue Admin and React Workbench.
-- Dependencies: V157 and the existing System menu/package tables.
-- Repeatable and non-destructive: announcement content, attachments, read records, and role grants are preserved.
-- Rollback limitation: restoring the retired menu would require a reviewed forward migration and package audit.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v158_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v158_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V157')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V157') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V158 requires V157 in both schema-version registries';
  END IF;
  UPDATE `system_menu`
  SET `name`='公告阅读',`permission`='system:notice:read',`type`=3,`sort`=7,`parent_id`=107,
      `path`='',`icon`='',`component`='',`component_name`=NULL,`workbench_render_mode`='admin_only',
      `status`=0,`visible`=b'1',`keep_alive`=b'1',`always_show`=b'1',
      `deleted`=b'0',`creator`='V158',`create_time`=COALESCE(`create_time`, NOW()),
      `updater`='V158',`update_time`=NOW()
  WHERE `id`=79913 AND `deleted`=b'1' AND `creator`='migration-V068'
    AND `path`='partner-portal' AND `component_name`='ZsjosPartnerPortal';
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79913 AND `permission`<>'system:notice:read') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 79913 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id` NOT IN (79910,79913) AND `permission`='system:notice:read') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Notice read permission already uses another active menu ID';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_menu`
   (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
    `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 79913,'公告阅读','system:notice:read',3,7,107,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V158',NOW(),'V158',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79913);

  UPDATE `system_menu`
  SET `name`='公告阅读',`permission`='system:notice:read',`type`=3,`sort`=7,`parent_id`=107,
      `path`='',`icon`='',`component`='',`component_name`=NULL,`workbench_render_mode`='admin_only',
      `status`=0,`visible`=b'1',`keep_alive`=b'1',`always_show`=b'1',
      `deleted`=b'0',`updater`='V158',`update_time`=NOW()
  WHERE `id`=79913 AND `permission`='system:notice:read';

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',107),`updater`='V158',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'79910','$') AND NOT JSON_CONTAINS(`menu_ids`,'107','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79913),`updater`='V158',`update_time`=NOW()
  WHERE `deleted`=b'0'
    AND (JSON_CONTAINS(`menu_ids`,'107','$') OR JSON_CONTAINS(`menu_ids`,'79910','$'))
    AND NOT JSON_CONTAINS(`menu_ids`,'79913','$');

  UPDATE `system_menu`
  SET `deleted`=b'1',`updater`='V158',`update_time`=NOW()
  WHERE `id`=79910 AND `deleted`=b'0';

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_REMOVE(`menu_ids`, (
        SELECT CONCAT('$[', menu_item.`ordinal` - 1, ']')
        FROM JSON_TABLE(`menu_ids`, '$[*]' COLUMNS (`ordinal` FOR ORDINALITY, `menu_id` BIGINT PATH '$')) menu_item
        WHERE menu_item.`menu_id`=79910
        LIMIT 1
      )),
      `updater`='V158',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'79910','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  SELECT 'V158','Retire duplicate announcement center menu',SHA2('V158__retire_announcement_center_duplicate.sql',256),NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V158');
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  SELECT 'core','V158','Retire duplicate announcement center menu',SHA2('V158__retire_announcement_center_duplicate.sql',256),'baseline',NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V158');

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v158_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v158_apply`;
