-- V101: add the planner-owned student basic-information update permission.
-- Dependencies/order: apply after V100; My Students menu 73020 must exist.
-- Data scope: one System button permission and initial role-menu relations for
-- system_administrator and study_planner. No Person, Lead, order, contact, task, or event row is changed.
-- Repeatability: stable ID/permission; initial role grants run only before the V101 marker exists.
-- Recovery: forward-only; administrators may remove role grants while retaining the permission definition.
-- Existing-environment execution requires separate approval. This file is generated but not executed here.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v101_assert_menu_identity`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v101_assert_menu_identity`()
BEGIN
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE `id`=73427 AND NOT (`permission`<=>'zsjos:student:update-basic-info')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V101 menu id 73427 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE `permission`='zsjos:student:update-basic-info' AND `id`<>73427) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V101 permission is owned by another menu id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73020 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V101 requires My Students menu 73020';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v101_assert_menu_identity`();
DROP PROCEDURE `zsjos_v101_assert_menu_identity`;

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73427,'修改学员基础信息','zsjos:student:update-basic-info',3,7,73020,'','','',NULL,0,b'1',b'1',b'0',
       'migration-V101',NOW(),'migration-V101',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73427);

UPDATE `system_menu`
SET `name`='修改学员基础信息',`type`=3,`sort`=7,`parent_id`=73020,`status`=0,`deleted`=b'0',
    `updater`='migration-V101',`update_time`=NOW()
WHERE `id`=73427 AND `permission`='zsjos:student:update-basic-info';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id,73427,'migration-V101',NOW(),'migration-V101',NOW(),b'0',role_row.tenant_id
FROM `system_role` role_row
WHERE role_row.code IN ('system_administrator','study_planner')
  AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V101')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role_row.id AND existing.menu_id=73427
                    AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V101','Student basic information update permission','V101__student_basic_info_permission.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V101','Student basic information update permission',SHA2('V101__student_basic_info_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
