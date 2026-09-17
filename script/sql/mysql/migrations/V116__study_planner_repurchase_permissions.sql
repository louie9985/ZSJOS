-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V116: dedicated study-planner student-repurchase permission plus the existing own-order page.
-- Additive/repeatable. No business rows are changed; apply after V115.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v116_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v116_apply`()
BEGIN
  DECLARE v116_zsjos_menu_id bigint;

  IF (SELECT COUNT(*) FROM `system_menu`
      WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: active /zsjos root menu is missing or ambiguous';
  END IF;
  SELECT `id` INTO v116_zsjos_menu_id FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0'
  LIMIT 1;

  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73020
      AND NOT (`permission` <=> 'zsjos:student:query-my')
      AND COALESCE(`creator`,'') NOT IN ('migration-V073','migration-V114','migration-V116')) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: menu ID 73020 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `permission`='zsjos:student:query-my' AND `id`<>73020 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: student query permission is owned by another active menu';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6813
      AND NOT (`permission` <=> 'zsjos:sales-order:query-own')
      AND COALESCE(`creator`,'') NOT IN ('migration-V025','quick-init','migration-V116')) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: menu ID 6813 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `permission`='zsjos:sales-order:query-own' AND `id`<>6813 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: own-order permission is owned by another active menu';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73440
      AND NOT (`permission` <=> 'zsjos:sales-order:student-repurchase')
      AND COALESCE(`creator`,'')<>'migration-V116') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: menu ID 73440 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `permission`='zsjos:sales-order:student-repurchase'
        AND `id`<>73440 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V116 blocked: student-repurchase permission is owned by another active menu';
  END IF;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (73020,'我的学员','zsjos:student:query-my',2,62,v116_zsjos_menu_id,'/zsjos/my-students',
          'ant-design:team-outlined','zsjos/my-students','ZsjosMyStudents',0,b'1',b'1',b'0',
          'migration-V116',NOW(),'migration-V116',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
   `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
   `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
   `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
   `deleted`=b'0',`updater`='migration-V116',`update_time`=NOW();

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (6813,'我的订单','zsjos:sales-order:query-own',2,17,v116_zsjos_menu_id,'sales-orders/my',
          'ep:tickets','zsjos/mySalesOrder/index','ZsjosMySalesOrder',0,b'1',b'1',b'1',
          'migration-V116',NOW(),'migration-V116',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
   `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
   `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
   `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
   `deleted`=b'0',`updater`='migration-V116',`update_time`=NOW();

  SET @v116_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_order' AND column_name='submission_request_fingerprint'), 'SELECT 1',
    'ALTER TABLE `zsjos_order` ADD COLUMN `submission_request_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''规范化提交请求指纹'' AFTER `submission_idempotency_key`');
  PREPARE v116_stmt FROM @v116_sql;
  EXECUTE v116_stmt;
  DEALLOCATE PREPARE v116_stmt;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (73440,'学习规划师录入复购','zsjos:sales-order:student-repurchase',3,1,73020,'','','',NULL,
          0,b'1',b'1',b'0','migration-V116',NOW(),'migration-V116',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
   `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`status`=VALUES(`status`),
   `visible`=VALUES(`visible`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),
   `component_name`=VALUES(`component_name`),`keep_alive`=VALUES(`keep_alive`),
   `always_show`=VALUES(`always_show`),`deleted`=b'0',`updater`='migration-V116',`update_time`=NOW();

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
  VALUES ('V116','Study planner student repurchase permission','study-planner-repurchase-permission-v5')
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V116','Study planner student repurchase permission',
          SHA2('study-planner-repurchase-permission-v5',256),'legacy',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v116_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v116_apply`;
