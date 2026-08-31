-- V062 registers the finance-order ledger export permission only.
-- Dependency: V050 async export menu. Repeatable through stable IDs and guarded metadata.
-- Data scope: one system_menu permission row and schema-version metadata; no role grants or business rows.
-- Rollback limitation: retain the row if an administrator has granted it; otherwise disable it after application rollback.

DROP PROCEDURE IF EXISTS `zsjos_v062_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v062_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DO RELEASE_LOCK('zsjos:migration:V062');
    RESIGNAL;
  END;

  SELECT GET_LOCK('zsjos:migration:V062', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 blocked: could not acquire migration lock';
  END IF;

  START TRANSACTION;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6872
      AND NOT (`permission` = 'zsjos:export:query' AND `type` = 2 AND `status` = 0 AND `deleted` = b'0')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 blocked: async export parent menu 6872 is unexpected';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6872 AND `permission` = 'zsjos:export:query'
      AND `type` = 2 AND `status` = 0 AND `deleted` = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 blocked: async export parent menu 6872 is missing';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6879
      AND NOT (`permission` = 'zsjos:export:finance-order' AND `type` = 3 AND `parent_id` = 6872
               AND `status` = 0 AND `visible` = b'1' AND `deleted` = b'0')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 blocked: system_menu ID 6879 is owned by another menu';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'zsjos:export:finance-order'
      AND `deleted` = b'0' AND `id` <> 6879) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 blocked: finance-order export permission uses another menu ID';
  END IF;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 6879,'导出财务订单','zsjos:export:finance-order',3,5,6872,'','','',NULL,
         0,b'1',b'1',b'1','migration-V062',NOW(),'migration-V062',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6879)
    AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'zsjos:export:finance-order' AND `deleted` = b'0');

  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6879
      AND `permission` = 'zsjos:export:finance-order' AND `type` = 3 AND `parent_id` = 6872
      AND `status` = 0 AND `visible` = b'1' AND `deleted` = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V062 failed: finance-order export permission was not installed';
  END IF;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V062','Finance order export permission','V062__finance_order_export_permission.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V062','Finance order export permission',SHA2('V062__finance_order_export_permission.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
  DO RELEASE_LOCK('zsjos:migration:V062');
END$$
DELIMITER ;
CALL `zsjos_v062_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v062_apply`;
