-- V170: Partner H5 first-login invitation activation.
-- Depends on V169 and the independent Partner identity/ownership tables.
-- Scope: creates an empty Partner invitation table and server-owned admin button permissions.
-- It does not seed partner subjects, accounts, invitation codes, users, roles or business data.
-- Repeatable: guarded by IF NOT EXISTS, stable menu IDs and JSON package checks; records both schema-version registries.
-- Rollback limitation: keep the additive table while older application versions ignore it, or hide the buttons in a later migration.

DROP PROCEDURE IF EXISTS `zsjos_v170_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v170_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V169')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V169') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V170 requires V169 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6852
                 AND `permission`='zsjos:partner:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V170 requires Partner page 6852';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE `id` IN (79993,79994,79995)
               AND `permission` NOT IN ('zsjos:partner-invitation:query',
                                        'zsjos:partner-invitation:create',
                                        'zsjos:partner-invitation:void')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V170 Partner invitation menu IDs are occupied';
  END IF;

  START TRANSACTION;

  CREATE TABLE IF NOT EXISTS `zsjos_partner_invitation` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '兼职邀请码编号',
    `invite_code` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邀请码：四位大写字母加四位数字',
    `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '兼职姓名',
    `mobile` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '激活手机号',
    `assigned_operator_user_id` bigint NOT NULL COMMENT '归属运营用户编号',
    `assigned_operator_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属运营姓名快照',
    `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：active/used/voided/expired',
    `expires_at` datetime NOT NULL COMMENT '过期时间',
    `used_at` datetime DEFAULT NULL COMMENT '使用时间',
    `voided_at` datetime DEFAULT NULL COMMENT '失效时间',
    `partner_id` bigint DEFAULT NULL COMMENT '激活后兼职主体编号',
    `created_by_user_id` bigint DEFAULT NULL COMMENT '生成邀请码的管理员用户编号',
    `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
    `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_invite_code` (`tenant_id`,`invite_code`),
    KEY `idx_tenant_mobile_status` (`tenant_id`,`mobile`,`status`,`expires_at`),
    KEY `idx_tenant_operator_status` (`tenant_id`,`assigned_operator_user_id`,`status`,`id`),
    KEY `idx_tenant_partner` (`tenant_id`,`partner_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 兼职首次登录邀请码';

  INSERT IGNORE INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (79993,'查询兼职邀请码','zsjos:partner-invitation:query',3,20,6852,'','','',NULL,'admin_only',0,b'1',b'1',b'0','V170',NOW(),'V170',NOW(),b'0'),
    (79994,'生成兼职邀请码','zsjos:partner-invitation:create',3,21,6852,'','','',NULL,'admin_only',0,b'1',b'1',b'0','V170',NOW(),'V170',NOW(),b'0'),
    (79995,'作废兼职邀请码','zsjos:partner-invitation:void',3,22,6852,'','','',NULL,'admin_only',0,b'1',b'1',b'0','V170',NOW(),'V170',NOW(),b'0');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79993),`updater`='V170',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79993','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79994),`updater`='V170',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79994','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79995),`updater`='V170',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'79995','$');

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT DISTINCT source.role_id, target.menu_id, 'V170', NOW(), 'V170', NOW(), b'0', source.tenant_id
  FROM `system_role_menu` source
  JOIN (SELECT 79993 AS menu_id UNION ALL SELECT 79994 UNION ALL SELECT 79995) target
  WHERE source.menu_id=79920 AND source.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
                      AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role.id, target.menu_id, 'V170', NOW(), 'V170', NOW(), b'0', role.tenant_id
  FROM `system_role` role
  JOIN (SELECT 79993 AS menu_id UNION ALL SELECT 79994 UNION ALL SELECT 79995) target
  WHERE role.code='system_administrator' AND role.status=0 AND role.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=role.id AND existing.menu_id=target.menu_id
                      AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V170','Partner H5 invitation activation',
          SHA2('V170__partner_invitation_activation.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V170','Partner H5 invitation activation',
          SHA2('V170__partner_invitation_activation.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v170_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v170_apply`;
