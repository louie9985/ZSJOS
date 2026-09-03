-- V162: decouple lead submission from post checks and add the specified-sales permission.
-- Dependencies: V161 in both schema-version registries and the existing lead submit page menu 6736.
-- Scope: server-owned System menu metadata, tenant packages, and schema-version registries only.
-- Repeatability: guarded fixed-ID / fixed-permission checks plus upserts make reruns safe.
-- Rollback: disable the new button in a later reviewed permission migration; keep historical grants intact.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v162_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v162_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V161')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V161') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V162 requires V161 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6736 AND `permission`='zsjos:lead:submit' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V162 requires lead submit menu 6736';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6820 AND `deleted`=b'0' AND `permission`<>'zsjos:lead:submit:specify') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 6820 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id`<>6820 AND `permission`='zsjos:lead:submit:specify') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Permission zsjos:lead:submit:specify is already owned by another menu';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (6820,'指定销售','zsjos:lead:submit:specify',3,1,6736,'','','',NULL,0,b'1',b'1',b'1',
     'migration-V162',NOW(),'migration-V162',NOW(),b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),
    `parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),
    `component_name`=VALUES(`component_name`),`status`=VALUES(`status`),`visible`=VALUES(`visible`),
    `keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),`deleted`=b'0',
    `updater`='migration-V162',`update_time`=NOW();

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',6820),`updater`='migration-V162',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6736','$') AND NOT JSON_CONTAINS(`menu_ids`,'6820','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V162','Lead submit permission decoupling','V162__lead_submit_permission_decoupling.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V162','Lead submit permission decoupling',SHA2('V162__lead_submit_permission_decoupling.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v162_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v162_apply`;
