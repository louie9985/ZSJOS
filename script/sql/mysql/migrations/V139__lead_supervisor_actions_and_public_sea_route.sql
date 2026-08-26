-- V139: restore the canonical Lead public-sea route and add supervisor Lead action permissions.
-- Dependencies/order: apply after V138; subordinate-sales menu and Lead aging-pool menu must exist.
-- Data scope: System menu metadata and initial sales_manager role-menu grants only.
-- Repeatability: permission identities and reserved IDs are stable; initial grants run only before V139 is recorded.
-- Recovery: forward-only; administrators may remove grants while retaining permission definitions and the canonical route.

DROP PROCEDURE IF EXISTS `zsjos_v139_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v139_apply`()
BEGIN
DECLARE EXIT HANDLER FOR SQLEXCEPTION
BEGIN
  ROLLBACK;
  RESIGNAL;
END;

START TRANSACTION;

UPDATE `system_menu`
SET `name`='公海池',`path`='lead-aging-pool',`updater`='migration-V139',`update_time`=NOW()
WHERE `permission`='zsjos:lead-aging-pool:query' AND `type`=2 AND `deleted`=b'0';

DROP TEMPORARY TABLE IF EXISTS `tmp_v139_supervisor_permission`;
CREATE TEMPORARY TABLE `tmp_v139_supervisor_permission` (
  `name` varchar(50) NOT NULL,
  `permission` varchar(100) NOT NULL,
  `sort` int NOT NULL,
  PRIMARY KEY (`permission`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO `tmp_v139_supervisor_permission` (`name`,`permission`,`sort`) VALUES
('恢复挂起客资','zsjos:subordinate-sales:lead-restore',6),
('转派下属客资','zsjos:subordinate-sales:lead-transfer',7),
('回收下属客资','zsjos:subordinate-sales:lead-recycle',8),
('释放客资至抢单池','zsjos:subordinate-sales:lead-release-claim-pool',9),
('释放客资至公海池','zsjos:subordinate-sales:lead-release-public-sea',10);

-- Reuse the oldest compatible soft-deleted identity before allocating a new menu ID.
DROP TEMPORARY TABLE IF EXISTS `tmp_v139_restore_menu`;
CREATE TEMPORARY TABLE `tmp_v139_restore_menu` AS
SELECT permission_row.permission,MIN(menu_row.id) AS menu_id
FROM `tmp_v139_supervisor_permission` permission_row
JOIN `system_menu` menu_row ON menu_row.permission=permission_row.permission AND menu_row.deleted=b'1'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` active_row
                  WHERE active_row.permission=permission_row.permission AND active_row.deleted=b'0')
GROUP BY permission_row.permission;

UPDATE `system_menu` menu_row
JOIN `tmp_v139_restore_menu` restore_row ON restore_row.menu_id=menu_row.id
JOIN `tmp_v139_supervisor_permission` permission_row ON permission_row.permission=restore_row.permission
SET menu_row.name=permission_row.name,menu_row.type=3,menu_row.sort=permission_row.sort,
    menu_row.parent_id=6814,menu_row.path='',menu_row.icon='',menu_row.component='',menu_row.component_name=NULL,
    menu_row.status=0,menu_row.visible=b'1',menu_row.keep_alive=b'1',menu_row.always_show=b'0',
    menu_row.deleted=b'0',menu_row.updater='migration-V139',menu_row.update_time=NOW();

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT permission_row.name,permission_row.permission,3,permission_row.sort,6814,'','','',NULL,
       0,b'1',b'1',b'0','migration-V139',NOW(),'migration-V139',NOW(),b'0'
FROM `tmp_v139_supervisor_permission` permission_row
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing
                  WHERE existing.permission=permission_row.permission AND existing.deleted=b'0');

-- Preserve grants on duplicate active definitions, then leave exactly one active menu per permission.
DROP TEMPORARY TABLE IF EXISTS `tmp_v139_keep_menu`;
CREATE TEMPORARY TABLE `tmp_v139_keep_menu` AS
SELECT menu_row.permission,MIN(menu_row.id) AS menu_id
FROM `system_menu` menu_row
JOIN `tmp_v139_supervisor_permission` permission_row ON permission_row.permission=menu_row.permission
WHERE menu_row.deleted=b'0'
GROUP BY menu_row.permission;

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT grant_row.role_id,keep_row.menu_id,'migration-V139',NOW(),'migration-V139',NOW(),b'0',grant_row.tenant_id
FROM `system_role_menu` grant_row
JOIN `system_menu` duplicate_row ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
JOIN `tmp_v139_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=grant_row.role_id AND existing.menu_id=keep_row.menu_id
                    AND existing.tenant_id=grant_row.tenant_id AND existing.deleted=b'0');

UPDATE `system_role_menu` grant_row
JOIN `system_menu` duplicate_row ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
JOIN `tmp_v139_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
SET grant_row.deleted=b'1',grant_row.updater='migration-V139',grant_row.update_time=NOW()
WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id;

UPDATE `system_menu` menu_row
JOIN `tmp_v139_keep_menu` keep_row ON keep_row.permission=menu_row.permission
SET menu_row.deleted=b'1',menu_row.updater='migration-V139',menu_row.update_time=NOW()
WHERE menu_row.deleted=b'0' AND menu_row.id<>keep_row.menu_id;

UPDATE `system_menu` menu_row
JOIN `tmp_v139_keep_menu` keep_row ON keep_row.menu_id=menu_row.id
JOIN `tmp_v139_supervisor_permission` permission_row ON permission_row.permission=keep_row.permission
SET menu_row.name=permission_row.name,menu_row.type=3,menu_row.sort=permission_row.sort,
    menu_row.parent_id=6814,menu_row.path='',menu_row.icon='',menu_row.component='',menu_row.component_name=NULL,
    menu_row.status=0,menu_row.visible=b'1',menu_row.keep_alive=b'1',menu_row.always_show=b'0',
    menu_row.updater='migration-V139',menu_row.update_time=NOW();

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V139',NOW(),'migration-V139',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission IN (
  'zsjos:subordinate-sales:lead-restore','zsjos:subordinate-sales:lead-transfer',
  'zsjos:subordinate-sales:lead-recycle','zsjos:subordinate-sales:lead-release-claim-pool',
  'zsjos:subordinate-sales:lead-release-public-sea') AND menu.deleted=b'0'
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V139')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V139','Lead supervisor actions and canonical public-sea route',
        'V139__lead_supervisor_actions_and_public_sea_route.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V139','Lead supervisor actions and canonical public-sea route',
        SHA2('V139__lead_supervisor_actions_and_public_sea_route.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

DROP TEMPORARY TABLE IF EXISTS `tmp_v139_keep_menu`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v139_restore_menu`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v139_supervisor_permission`;

COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v139_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v139_apply`;
