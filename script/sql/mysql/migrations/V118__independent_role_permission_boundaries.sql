-- V118: keep new-media student-operations permissions independently role-managed.
-- Forward-only and repeatable. Remove only accidental default grants from study_planner.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v118_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v118_apply`()
BEGIN
  DELETE rm
  FROM `system_role_menu` rm
  JOIN `system_role` r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
  JOIN `system_menu` m ON m.id=rm.menu_id
  WHERE r.code='study_planner' AND r.deleted=b'0' AND rm.deleted=b'0' AND m.deleted=b'0'
    AND m.permission IN ('zsjos:student-ops:query','zsjos:student-ops:create-exception',
      'zsjos:student-ops:resolve-exception','zsjos:student-ops:assess','zsjos:student-ops:graduate');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
  VALUES ('V118','Independent role-managed permission boundaries','independent-role-permission-boundaries-v1')
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V118','Independent role-managed permission boundaries',
          SHA2('independent-role-permission-boundaries-v1',256),'legacy',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;

CALL `zsjos_v118_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v118_apply`;
