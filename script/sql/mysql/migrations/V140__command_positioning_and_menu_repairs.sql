-- V140: persistent supervisor command idempotency, positioning-link expiry, and menu identity repair.
-- Dependencies/order: apply after V139. This migration is repeatable and does not revoke tenant permissions.
-- Data scope: schema metadata, active confirmation-link expiry, and system menu identity/authorization wiring.
-- Rollback limitation: forward-only; expiry enforcement and command replay records must not be removed after use.

DROP PROCEDURE IF EXISTS `zsjos_v140_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v140_apply`()
BEGIN
DECLARE EXIT HANDLER FOR SQLEXCEPTION
BEGIN
  ROLLBACK;
  RESIGNAL;
END;

CREATE TABLE IF NOT EXISTS `zsjos_subordinate_sales_command` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_user_id` bigint NOT NULL,
  `idempotency_key` varchar(40) NOT NULL,
  `action_type` varchar(40) NOT NULL,
  `request_fingerprint` char(64) NOT NULL,
  `result_json` longtext DEFAULT NULL,
  `completed` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_operator_idempotency` (`tenant_id`,`operator_user_id`,`idempotency_key`),
  KEY `idx_tenant_operator_created` (`tenant_id`,`operator_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主管客资命令幂等记录';

SET @schema_name = DATABASE();
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='zsjos_positioning_confirmation_link' AND column_name='expires_at')=0,
  'ALTER TABLE `zsjos_positioning_confirmation_link` ADD COLUMN `expires_at` datetime DEFAULT NULL AFTER `created_by_user_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_positioning_confirmation_link`
SET `expires_at`=DATE_ADD(`create_time`, INTERVAL 7 DAY),`updater`='V140',`update_time`=NOW()
WHERE `expires_at` IS NULL AND `status`='active' AND `deleted`=b'0';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=@schema_name AND table_name='zsjos_positioning_confirmation_link'
    AND index_name='idx_token_status_expires')=0,
  'ALTER TABLE `zsjos_positioning_confirmation_link` ADD KEY `idx_token_status_expires` (`token_hash`,`status`,`expires_at`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT character_maximum_length FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='zsjos_positioning_card_submission'
    AND column_name='student_decision_comment') < 2000,
  'ALTER TABLE `zsjos_positioning_card_submission` MODIFY COLUMN `student_decision_comment` varchar(2000) DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

START TRANSACTION;

-- 73460 was originally the student business-form page and was reused by V130. Move the interview page first.
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73483,m.name,m.permission,m.type,m.sort,m.parent_id,m.path,m.icon,m.component,m.component_name,
       m.status,m.visible,m.keep_alive,m.always_show,'V140',NOW(),'V140',NOW(),b'0'
FROM `system_menu` m
WHERE m.id=73460 AND m.permission='zsjos:director-interview-template:query' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.id=73483 OR (x.permission=m.permission AND x.deleted=b'0' AND x.id<>73460));

UPDATE `system_menu` SET `parent_id`=73483,`updater`='V140',`update_time`=NOW()
WHERE `parent_id`=73460 AND `permission` IN ('zsjos:director-interview-template:update','zsjos:director-interview-template:publish')
  AND `deleted`=b'0';

UPDATE `system_role_menu` source_grant
JOIN `system_role_menu` target_grant
  ON target_grant.role_id=source_grant.role_id AND target_grant.menu_id=73483
 AND target_grant.tenant_id=source_grant.tenant_id AND target_grant.deleted=b'0'
SET source_grant.deleted=b'1',source_grant.updater='V140',source_grant.update_time=NOW()
WHERE source_grant.menu_id=73460 AND source_grant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V140');

UPDATE `system_role_menu` SET `menu_id`=73483,`updater`='V140',`update_time`=NOW()
WHERE `menu_id`=73460 AND `deleted`=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V140')
  AND EXISTS (SELECT 1 FROM `system_menu` m WHERE m.id=73483 AND m.deleted=b'0');

UPDATE `system_menu`
SET `name`='业务表单配置',`permission`='zsjos:student-contact-config:forms',`type`=2,`sort`=64,
    `parent_id`=73400,`path`='business-form-config',`icon`='ep:document',
    `component`='zsjos/studentContactConfig/index',`component_name`='ZsjosBusinessFormConfig',
    `status`=0,`visible`=b'1',`keep_alive`=b'1',`always_show`=b'0',`deleted`=b'0',
    `updater`='V140',`update_time`=NOW()
WHERE `id`=73460;

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,73460,'V140',NOW(),'V140',NOW(),b'0',r.tenant_id
FROM `system_role` r WHERE r.code IN ('system_administrator','super_admin') AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V140')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=73460
    AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

-- Forward-repair V139 installations that created a second active definition instead of restoring
-- a compatible soft-deleted supervisor permission row.
DROP TEMPORARY TABLE IF EXISTS `tmp_v140_supervisor_permission`;
CREATE TEMPORARY TABLE `tmp_v140_supervisor_permission` (
  `name` varchar(50) NOT NULL,
  `permission` varchar(100) NOT NULL,
  `sort` int NOT NULL,
  PRIMARY KEY (`permission`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO `tmp_v140_supervisor_permission` (`name`,`permission`,`sort`) VALUES
('恢复挂起客资','zsjos:subordinate-sales:lead-restore',6),
('转派下属客资','zsjos:subordinate-sales:lead-transfer',7),
('回收下属客资','zsjos:subordinate-sales:lead-recycle',8),
('释放客资至抢单池','zsjos:subordinate-sales:lead-release-claim-pool',9),
('释放客资至公海池','zsjos:subordinate-sales:lead-release-public-sea',10);

DROP TEMPORARY TABLE IF EXISTS `tmp_v140_keep_menu`;
CREATE TEMPORARY TABLE `tmp_v140_keep_menu` AS
SELECT menu_row.permission,MIN(menu_row.id) AS menu_id
FROM `system_menu` menu_row
JOIN `tmp_v140_supervisor_permission` permission_row ON permission_row.permission=menu_row.permission
GROUP BY menu_row.permission;

UPDATE `system_menu` menu_row
JOIN `tmp_v140_keep_menu` keep_row ON keep_row.menu_id=menu_row.id
JOIN `tmp_v140_supervisor_permission` permission_row ON permission_row.permission=keep_row.permission
SET menu_row.name=permission_row.name,menu_row.type=3,menu_row.sort=permission_row.sort,
    menu_row.parent_id=6814,menu_row.path='',menu_row.icon='',menu_row.component='',menu_row.component_name=NULL,
    menu_row.status=0,menu_row.visible=b'1',menu_row.keep_alive=b'1',menu_row.always_show=b'0',
    menu_row.deleted=b'0',menu_row.updater='V140',menu_row.update_time=NOW();

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT grant_row.role_id,keep_row.menu_id,'V140',NOW(),'V140',NOW(),b'0',grant_row.tenant_id
FROM `system_role_menu` grant_row
JOIN `system_menu` duplicate_row ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
JOIN `tmp_v140_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=grant_row.role_id AND existing.menu_id=keep_row.menu_id
                    AND existing.tenant_id=grant_row.tenant_id AND existing.deleted=b'0');

UPDATE `system_role_menu` grant_row
JOIN `system_menu` duplicate_row ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
JOIN `tmp_v140_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
SET grant_row.deleted=b'1',grant_row.updater='V140',grant_row.update_time=NOW()
WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id;

UPDATE `system_menu` menu_row
JOIN `tmp_v140_keep_menu` keep_row ON keep_row.permission=menu_row.permission
SET menu_row.deleted=b'1',menu_row.updater='V140',menu_row.update_time=NOW()
WHERE menu_row.deleted=b'0' AND menu_row.id<>keep_row.menu_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_v140_keep_menu`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v140_supervisor_permission`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V140','command positioning and menu repairs','V140__command_positioning_and_menu_repairs.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V140','command positioning and menu repairs',
        SHA2('V140__command_positioning_and_menu_repairs.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v140_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v140_apply`;
