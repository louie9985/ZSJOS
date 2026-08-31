-- V151: grant consolidated Partner management to the configured system administrator role.
-- Dependencies/order: apply after V150 completed its Partner permission consolidation.
-- Data scope: system_role_menu grants and schema-version markers only; no Partner or ownership data changes.
-- Repeatability: prerequisite checks and active-grant guards make reruns safe.
-- Recovery: forward-only; revoke menu 79920 from affected roles through a reviewed follow-up migration.

DROP PROCEDURE IF EXISTS `zsjos_v151_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v151_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V150')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V150') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V151 requires V150 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6852
                 AND `permission`='zsjos:partner:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V151 requires Partner page 6852';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79920 AND `parent_id`=6852
                 AND `permission`='zsjos:partner:manage' AND `type`=3 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V151 requires Partner manage menu 79920';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role.id,target.menu_id,'V151',NOW(),'V151',NOW(),b'0',role.tenant_id
  FROM `system_role` role
  JOIN (SELECT 6852 AS menu_id UNION ALL SELECT 79920) target
  WHERE role.code='system_administrator' AND role.status=0 AND role.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=role.id AND existing.menu_id=target.menu_id
                      AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V151','Repair Partner administrator manage permission',
          SHA2('V151__repair_partner_administrator_manage_permission.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V151','Repair Partner administrator manage permission',
          SHA2('V151__repair_partner_administrator_manage_permission.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v151_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v151_apply`;
