-- V160: add registration-case close-service button permission.
-- Fresh bootstrap and upgraded environments both keep the registration public-pool page node 73000 unchanged.
-- This migration only adds the server-owned close-service menu/button metadata under that existing page node.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v160_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v160_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V159')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V159') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V160 requires V159 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73000 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V160 requires registration public-pool menu 73000';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73003 AND `permission`<>'zsjos:registration:close') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 73003 is owned by another permission';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
  (73003,'关闭服务','zsjos:registration:close',3,3,73000,'','','',NULL,0,b'1',b'1',b'0','migration-V160',NOW(),'migration-V160',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),
   `deleted`=b'0',`update_time`=NOW();

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  SELECT 'V160','Registration case close-service button permission',SHA2('V160__registration_case_close_service.sql',256),NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V160');
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  SELECT 'core','V160','Registration case close-service button permission',SHA2('V160__registration_case_close_service.sql',256),'baseline',NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V160');

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v160_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v160_apply`;
