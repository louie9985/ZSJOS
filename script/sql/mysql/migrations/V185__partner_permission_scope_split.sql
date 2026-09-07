-- UTF-8. V185: split Partner visibility from tenant-wide management.
-- Dependencies/order: apply after V184 and the V150/V151 consolidated Partner permissions.
-- Data scope: System menu metadata, role-menu grants, tenant packages and schema-version records only.
-- Repeatability: fixed identities, guarded preflight checks and duplicate-safe grants make reruns safe.
-- Recovery: forward-only; restore permission identities and grants through a reviewed follow-up migration.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v185_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v185_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V184')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V184') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V185 requires V184 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6852 AND `type`=2
                 AND `path`='partner' AND `component`='zsjos/partner/index' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V185 requires Partner page 6852';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79920 AND `type`=3
                 AND `parent_id`=6852 AND `deleted`=b'0'
                 AND `permission` IN ('zsjos:partner:manage','zsjos:partner:manage-all')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V185 requires Partner management button 79920';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79996
             AND (`type`<>3 OR `parent_id`<>6852 OR `permission`<>'zsjos:partner:query')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 79996 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79997
             AND (`type`<>3 OR `parent_id`<>6852 OR `permission`<>'zsjos:partner:manage')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 79997 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id` NOT IN (6852,79996)
             AND `permission`='zsjos:partner:query') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partner query permission uses an unexpected menu ID';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id` NOT IN (79920,79997)
             AND `permission`='zsjos:partner:manage') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partner manage permission uses an unexpected menu ID';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id`<>79920
             AND `permission`='zsjos:partner:manage-all') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partner manage-all permission uses another menu ID';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (79996,'查看兼职','zsjos:partner:query',3,1,6852,'','','',NULL,0,b'1',b'1',b'0',
     'V185',NOW(),'V185',NOW(),b'0'),
    (79997,'管理兼职','zsjos:partner:manage',3,2,6852,'','','',NULL,0,b'1',b'1',b'0',
     'V185',NOW(),'V185',NOW(),b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),
    `icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),
    `status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),
    `always_show`=VALUES(`always_show`),`deleted`=b'0',`updater`='V185',`update_time`=NOW();

  -- Existing page holders retain the former query behavior through the new query button.
  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT source.role_id,79996,'V185',NOW(),'V185',NOW(),b'0',source.tenant_id
  FROM `system_role_menu` source
  WHERE source.menu_id=6852 AND source.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V185')
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=source.role_id AND existing.menu_id=79996
                      AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

  UPDATE `system_menu`
  SET `name`='兼职管理',`permission`='',`updater`='V185',`update_time`=NOW()
  WHERE `id`=6852 AND `deleted`=b'0';

  UPDATE `system_menu`
  SET `name`='管理全部兼职',`permission`='zsjos:partner:manage-all',`sort`=3,
      `updater`='V185',`update_time`=NOW()
  WHERE `id`=79920 AND `deleted`=b'0';

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79996),`updater`='V185',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79996','$');
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79997),`updater`='V185',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79997','$');
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79920),`updater`='V185',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79920','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V185','Partner permission scope split',SHA2('V185__partner_permission_scope_split.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V185','Partner permission scope split',
          SHA2('V185__partner_permission_scope_split.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v185_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v185_apply`;
