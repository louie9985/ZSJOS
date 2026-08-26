-- V142: forward repair for V139/V140 executions that continued after statement failures.
-- Dependencies/order: apply after V141; legacy and module V139/V140 markers must already exist.
-- Data scope: V140 additive schema objects, System menu metadata, and affected role-menu grants only.
-- Repeatability: canonical identities are stable; initial repair grants run only before V142 is recorded.
-- Recovery: forward-only; retain command/positioning schema and use reviewed System grants for later changes.

DROP PROCEDURE IF EXISTS `zsjos_v142_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v142_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V139')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V140') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V142 blocked: V139 and V140 version markers are required';
  END IF;

  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `id`=73460
        AND `permission` NOT IN ('zsjos:student-contact-config:forms',
                                 'zsjos:director-interview-template:query')) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V142 blocked: menu ID 73460 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `id`=73483 AND `permission`<>'zsjos:director-interview-template:query') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V142 blocked: menu ID 73483 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `permission`='zsjos:director-interview-template:query'
        AND `id` NOT IN (73460,73483) AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V142 blocked: interview-template permission has another active menu';
  END IF;

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

  SET @v142_sql = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_confirmation_link'
      AND column_name='expires_at')=0,
    'ALTER TABLE `zsjos_positioning_confirmation_link` ADD COLUMN `expires_at` datetime DEFAULT NULL AFTER `created_by_user_id`',
    'SELECT 1');
  PREPARE v142_stmt FROM @v142_sql;
  EXECUTE v142_stmt;
  DEALLOCATE PREPARE v142_stmt;

  UPDATE `zsjos_positioning_confirmation_link`
  SET `expires_at`=DATE_ADD(`create_time`, INTERVAL 7 DAY),`updater`='V142',`update_time`=NOW()
  WHERE `expires_at` IS NULL AND `status`='active' AND `deleted`=b'0';

  SET @v142_sql = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_confirmation_link'
      AND index_name='idx_token_status_expires')=0,
    'ALTER TABLE `zsjos_positioning_confirmation_link` ADD KEY `idx_token_status_expires` (`token_hash`,`status`,`expires_at`)',
    'SELECT 1');
  PREPARE v142_stmt FROM @v142_sql;
  EXECUTE v142_stmt;
  DEALLOCATE PREPARE v142_stmt;

  SET @v142_sql = IF((SELECT character_maximum_length FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_card_submission'
      AND column_name='student_decision_comment') < 2000,
    'ALTER TABLE `zsjos_positioning_card_submission` MODIFY COLUMN `student_decision_comment` varchar(2000) DEFAULT NULL',
    'SELECT 1');
  PREPARE v142_stmt FROM @v142_sql;
  EXECUTE v142_stmt;
  DEALLOCATE PREPARE v142_stmt;

  START TRANSACTION;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (73483,'采访表单配置','zsjos:director-interview-template:query',2,10,73480,
          'interview-template','ep:document','zsjos/directorTemplate/index','ZsjosBusinessFormConfig',
          0,b'1',b'1',b'0','V142',NOW(),'V142',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
    `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
    `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
    `deleted`=b'0',`updater`='V142',`update_time`=NOW();

  UPDATE `system_menu`
  SET `parent_id`=73483,`updater`='V142',`update_time`=NOW()
  WHERE `permission` IN ('zsjos:director-interview-template:update',
                         'zsjos:director-interview-template:publish')
    AND `deleted`=b'0';

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT DISTINCT source_grant.role_id,73483,'V142',NOW(),'V142',NOW(),b'0',source_grant.tenant_id
  FROM `system_role_menu` source_grant
  WHERE source_grant.menu_id=73460 AND source_grant.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V142')
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` target_grant
      WHERE target_grant.role_id=source_grant.role_id AND target_grant.menu_id=73483
        AND target_grant.tenant_id=source_grant.tenant_id AND target_grant.deleted=b'0');

  UPDATE `system_role_menu` source_grant
  LEFT JOIN `system_role` role_row
    ON role_row.id=source_grant.role_id AND role_row.tenant_id=source_grant.tenant_id
  SET source_grant.deleted=b'1',source_grant.updater='V142',source_grant.update_time=NOW()
  WHERE source_grant.menu_id=73460 AND source_grant.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V142')
    AND COALESCE(role_row.code,'') NOT IN ('system_administrator','super_admin');

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (73460,'业务表单配置','zsjos:student-contact-config:forms',2,64,73400,
          'business-form-config','ep:document','zsjos/studentContactConfig/index','ZsjosBusinessFormConfig',
          0,b'1',b'1',b'0','V142',NOW(),'V142',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
    `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
    `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
    `deleted`=b'0',`updater`='V142',`update_time`=NOW();

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role_row.id,73460,'V142',NOW(),'V142',NOW(),b'0',role_row.tenant_id
  FROM `system_role` role_row
  WHERE role_row.code IN ('system_administrator','super_admin') AND role_row.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V142')
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
      WHERE existing.role_id=role_row.id AND existing.menu_id=73460
        AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_supervisor_permission`;
  CREATE TEMPORARY TABLE `tmp_v142_supervisor_permission` (
    `name` varchar(50) NOT NULL,
    `permission` varchar(100) NOT NULL,
    `sort` int NOT NULL,
    PRIMARY KEY (`permission`)
  ) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO `tmp_v142_supervisor_permission` (`name`,`permission`,`sort`) VALUES
  ('恢复挂起客资','zsjos:subordinate-sales:lead-restore',6),
  ('转派下属客资','zsjos:subordinate-sales:lead-transfer',7),
  ('回收下属客资','zsjos:subordinate-sales:lead-recycle',8),
  ('释放客资至抢单池','zsjos:subordinate-sales:lead-release-claim-pool',9),
  ('释放客资至公海池','zsjos:subordinate-sales:lead-release-public-sea',10);

  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_restore_menu`;
  CREATE TEMPORARY TABLE `tmp_v142_restore_menu` AS
  SELECT permission_row.permission,MIN(menu_row.id) AS menu_id
  FROM `tmp_v142_supervisor_permission` permission_row
  JOIN `system_menu` menu_row
    ON menu_row.permission=permission_row.permission AND menu_row.deleted=b'1'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` active_row
    WHERE active_row.permission=permission_row.permission AND active_row.deleted=b'0')
  GROUP BY permission_row.permission;

  UPDATE `system_menu` menu_row
  JOIN `tmp_v142_restore_menu` restore_row ON restore_row.menu_id=menu_row.id
  JOIN `tmp_v142_supervisor_permission` permission_row
    ON permission_row.permission=restore_row.permission
  SET menu_row.name=permission_row.name,menu_row.type=3,menu_row.sort=permission_row.sort,
      menu_row.parent_id=6814,menu_row.path='',menu_row.icon='',menu_row.component='',menu_row.component_name=NULL,
      menu_row.status=0,menu_row.visible=b'1',menu_row.keep_alive=b'1',menu_row.always_show=b'0',
      menu_row.deleted=b'0',menu_row.updater='V142',menu_row.update_time=NOW();

  INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT permission_row.name,permission_row.permission,3,permission_row.sort,6814,'','','',NULL,
         0,b'1',b'1',b'0','V142',NOW(),'V142',NOW(),b'0'
  FROM `tmp_v142_supervisor_permission` permission_row
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing
    WHERE existing.permission=permission_row.permission AND existing.deleted=b'0');

  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_keep_menu`;
  CREATE TEMPORARY TABLE `tmp_v142_keep_menu` AS
  SELECT menu_row.permission,MIN(menu_row.id) AS menu_id
  FROM `system_menu` menu_row
  JOIN `tmp_v142_supervisor_permission` permission_row
    ON permission_row.permission=menu_row.permission
  WHERE menu_row.deleted=b'0'
  GROUP BY menu_row.permission;

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT DISTINCT grant_row.role_id,keep_row.menu_id,'V142',NOW(),'V142',NOW(),b'0',grant_row.tenant_id
  FROM `system_role_menu` grant_row
  JOIN `system_menu` duplicate_row
    ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
  JOIN `tmp_v142_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
  WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
      WHERE existing.role_id=grant_row.role_id AND existing.menu_id=keep_row.menu_id
        AND existing.tenant_id=grant_row.tenant_id AND existing.deleted=b'0');

  UPDATE `system_role_menu` grant_row
  JOIN `system_menu` duplicate_row
    ON duplicate_row.id=grant_row.menu_id AND duplicate_row.deleted=b'0'
  JOIN `tmp_v142_keep_menu` keep_row ON keep_row.permission=duplicate_row.permission
  SET grant_row.deleted=b'1',grant_row.updater='V142',grant_row.update_time=NOW()
  WHERE grant_row.deleted=b'0' AND duplicate_row.id<>keep_row.menu_id;

  UPDATE `system_menu` menu_row
  JOIN `tmp_v142_keep_menu` keep_row ON keep_row.permission=menu_row.permission
  SET menu_row.deleted=b'1',menu_row.updater='V142',menu_row.update_time=NOW()
  WHERE menu_row.deleted=b'0' AND menu_row.id<>keep_row.menu_id;

  UPDATE `system_menu` menu_row
  JOIN `tmp_v142_keep_menu` keep_row ON keep_row.menu_id=menu_row.id
  JOIN `tmp_v142_supervisor_permission` permission_row
    ON permission_row.permission=keep_row.permission
  SET menu_row.name=permission_row.name,menu_row.type=3,menu_row.sort=permission_row.sort,
      menu_row.parent_id=6814,menu_row.path='',menu_row.icon='',menu_row.component='',menu_row.component_name=NULL,
      menu_row.status=0,menu_row.visible=b'1',menu_row.keep_alive=b'1',menu_row.always_show=b'0',
      menu_row.deleted=b'0',menu_row.updater='V142',menu_row.update_time=NOW();

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role_row.id,menu_row.id,'V142',NOW(),'V142',NOW(),b'0',role_row.tenant_id
  FROM `system_role` role_row
  JOIN `system_menu` menu_row
    ON menu_row.permission IN (
      'zsjos:subordinate-sales:lead-restore','zsjos:subordinate-sales:lead-transfer',
      'zsjos:subordinate-sales:lead-recycle','zsjos:subordinate-sales:lead-release-claim-pool',
      'zsjos:subordinate-sales:lead-release-public-sea')
   AND menu_row.deleted=b'0'
  WHERE role_row.code='sales_manager' AND role_row.status=0 AND role_row.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V142')
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
      WHERE existing.role_id=role_row.id AND existing.menu_id=menu_row.id
        AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_keep_menu`;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_restore_menu`;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v142_supervisor_permission`;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V142','repair partial V139 and V140 executions','V142__repair_partial_v139_v140.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V142','repair partial V139 and V140 executions',
          SHA2('V142__repair_partial_v139_v140.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v142_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v142_apply`;
