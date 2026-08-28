-- V143: Partner-to-employee ownership and subordinate Partner read scope.
-- Dependencies/order: apply after V142; System menu, role and ZSJOS Partner/Lead tables must exist.
-- Data scope: additive schema, permission metadata, and the initial system_administrator assignment-button grant.
-- Repeatability: tables, columns, menus, grants and version markers are guarded.
-- Recovery: forward-only; disable menus/grants if needed and retain ownership/audit/snapshot facts.

CREATE TABLE IF NOT EXISTS `zsjos_partner_ownership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `partner_id` bigint NOT NULL,
  `employee_user_id` bigint NOT NULL,
  `employee_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `assigned_at` datetime NOT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_partner` (`tenant_id`,`partner_id`),
  KEY `idx_tenant_employee` (`tenant_id`,`employee_user_id`,`assigned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='兼职当前员工归属';

CREATE TABLE IF NOT EXISTS `zsjos_partner_ownership_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `partner_id` bigint NOT NULL,
  `previous_employee_user_id` bigint DEFAULT NULL,
  `previous_employee_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employee_user_id` bigint DEFAULT NULL,
  `employee_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `occurred_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_partner_time` (`tenant_id`,`partner_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='兼职员工归属审计';

DROP PROCEDURE IF EXISTS `zsjos_v143_add_lead_snapshot`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v143_add_lead_snapshot`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead' AND column_name='partner_owner_user_id_snapshot') THEN
    ALTER TABLE `zsjos_lead` ADD COLUMN `partner_owner_user_id_snapshot` bigint DEFAULT NULL
      COMMENT '兼职提交时归属员工内部ID快照' AFTER `partner_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead' AND column_name='partner_owner_name_snapshot') THEN
    ALTER TABLE `zsjos_lead` ADD COLUMN `partner_owner_name_snapshot` varchar(100)
      COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '兼职提交时归属员工姓名快照'
      AFTER `partner_owner_user_id_snapshot`;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v143_add_lead_snapshot`();
DROP PROCEDURE `zsjos_v143_add_lead_snapshot`;

-- Only the submission-time employee snapshot is authoritative for historical Partner attribution.
UPDATE `zsjos_lead`
SET `provider_owner_type`=COALESCE(`provider_owner_type`,'partner'),
    `provider_owner_id`=COALESCE(`provider_owner_id`,`partner_id`),
    `contribution_user_id_snapshot`=COALESCE(`contribution_user_id_snapshot`,`partner_owner_user_id_snapshot`),
    `contribution_user_name_snapshot`=COALESCE(`contribution_user_name_snapshot`,`partner_owner_name_snapshot`),
    `counted_at`=COALESCE(`counted_at`,`submitted_at`),
    `updater`='migration-V143',`update_time`=NOW()
WHERE `deleted`=b'0' AND `source_type`='partner' AND `partner_id` IS NOT NULL
  AND (`provider_owner_id` IS NULL OR (`partner_owner_user_id_snapshot` IS NOT NULL
       AND `contribution_user_id_snapshot` IS NULL));

DROP PROCEDURE IF EXISTS `zsjos_v143_assert_menu_contract`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v143_assert_menu_contract`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `path`='/zsjos' AND `type`=1 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V143 blocked: Workbench /zsjos root menu is missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:partner:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V143 blocked: Partner query page is missing';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:subordinate-partner:query'
             AND `deleted`=b'0' AND (`type`<>2 OR `path`<>'subordinate-partners')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V143 blocked: subordinate Partner permission identity conflicts';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v143_assert_menu_contract`();
DROP PROCEDURE `zsjos_v143_assert_menu_contract`;

START TRANSACTION;

# INSERT INTO `system_menu`
# (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
# SELECT '下属兼职','zsjos:subordinate-partner:query',2,16,root.id,'subordinate-partners','ep:user-filled',
#        'zsjos/subordinatePartner/index','ZsjosSubordinatePartner',0,b'1',b'1',b'0',
#        'migration-V143',NOW(),'migration-V143',NOW(),b'0'
# FROM `system_menu` root
# WHERE root.path='/zsjos' AND root.type=1 AND root.deleted=b'0'
#   AND NOT EXISTS (SELECT 1 FROM `system_menu` existing
#                   WHERE existing.permission='zsjos:subordinate-partner:query' AND existing.deleted=b'0');

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '分配兼职归属','zsjos:partner:assign-owner',3,10,page.id,'','','',NULL,0,b'1',b'1',b'0',
       'migration-V143',NOW(),'migration-V143',NOW(),b'0'
FROM `system_menu` page
WHERE page.permission='zsjos:partner:query' AND page.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` existing
                  WHERE existing.permission='zsjos:partner:assign-owner' AND existing.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V143',NOW(),'migration-V143',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission='zsjos:partner:assign-owner' AND menu.deleted=b'0'
WHERE role.code='system_administrator' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V143')
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V143','Subordinate Partner ownership and Lead snapshots',
        'V143__subordinate_partner_ownership.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V143','Subordinate Partner ownership and Lead snapshots',
        SHA2('V143__subordinate_partner_ownership.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
