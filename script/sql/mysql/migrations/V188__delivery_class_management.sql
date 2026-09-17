-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- UTF-8. V188: delivery classes, per-order-item registration assignment and class transfer requests.
-- Dependencies/order: apply after V187; registration, order/product, service-relation, exam-calendar,
-- System tenant/menu/organization/permission and BPM facilities must exist.
-- Data scope: creates one system pending class per enabled tenant and associates only historical
-- service relations whose class_id is null; their existing owner_user_id is deliberately preserved.
-- Repeatability: procedure guards additive columns/indexes and fixed menu/version records.
-- Recovery: forward-only; retain class/assignment/transfer history and disable menus in a later migration.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v188_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v188_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V187')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V187') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V188 requires V187 in both schema-version registries';
  END IF;
  START TRANSACTION;

  CREATE TABLE IF NOT EXISTS `zsjos_delivery_class` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `class_no` varchar(40) NOT NULL,
    `class_name` varchar(100) NOT NULL,
    `system_class` bit(1) NOT NULL DEFAULT b'0',
    `pending_guard` bigint GENERATED ALWAYS AS
      (CASE WHEN (`system_class`=b'1' AND `deleted`=b'0') THEN `tenant_id` ELSE NULL END) STORED,
    `category_id` bigint DEFAULT NULL,
    `category_name_snapshot` varchar(255) DEFAULT NULL,
    `category_path_snapshot` json DEFAULT NULL,
    `exam_schedule_id` bigint DEFAULT NULL,
    `exam_schedule_snapshot` varchar(255) DEFAULT NULL,
    `homeroom_user_id` bigint DEFAULT NULL,
    `homeroom_user_name_snapshot` varchar(100) DEFAULT NULL,
    `dept_id` bigint DEFAULT NULL,
    `dept_name_snapshot` varchar(100) DEFAULT NULL,
    `status` varchar(24) NOT NULL DEFAULT 'SERVING',
    `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_class_no` (`tenant_id`,`class_no`,`deleted`),
    UNIQUE KEY `uk_delivery_pending_guard` (`pending_guard`),
    KEY `idx_delivery_class_scope` (`tenant_id`,`dept_id`,`status`,`homeroom_user_id`,`deleted`),
    KEY `idx_delivery_class_category` (`tenant_id`,`category_id`,`exam_schedule_id`,`status`,`deleted`),
    CONSTRAINT `chk_delivery_class_status` CHECK (`status` IN ('SERVING','COMPLETED'))
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付班级';

  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_pending') THEN
    ALTER TABLE `zsjos_delivery_class` DROP INDEX `uk_delivery_pending`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_delivery_class' AND column_name='pending_guard') THEN
    ALTER TABLE `zsjos_delivery_class` ADD COLUMN `pending_guard` bigint GENERATED ALWAYS AS
      (CASE WHEN (`system_class`=b'1' AND `deleted`=b'0') THEN `tenant_id` ELSE NULL END) STORED AFTER `system_class`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_pending_guard') THEN
    ALTER TABLE `zsjos_delivery_class` ADD UNIQUE KEY `uk_delivery_pending_guard` (`pending_guard`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_delivery_class' AND column_name='dept_id') THEN
    ALTER TABLE `zsjos_delivery_class` ADD COLUMN `dept_id` bigint DEFAULT NULL AFTER `homeroom_user_name_snapshot`,
      ADD COLUMN `dept_name_snapshot` varchar(100) DEFAULT NULL AFTER `dept_id`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_registration_case' AND column_name='assignment_mode') THEN
    ALTER TABLE `zsjos_registration_case` ADD COLUMN `assignment_mode` varchar(24) NOT NULL
      DEFAULT 'legacy_planner' COMMENT '分班模式' AFTER `checklist_version_id`;
  END IF;

  CREATE TABLE IF NOT EXISTS `zsjos_registration_class_assignment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `registration_case_id` bigint NOT NULL,
    `order_item_id` bigint NOT NULL,
    `class_id` bigint NOT NULL,
    `class_no_snapshot` varchar(40) NOT NULL,
    `class_name_snapshot` varchar(100) NOT NULL,
    `homeroom_user_id` bigint DEFAULT NULL,
    `homeroom_user_name_snapshot` varchar(100) DEFAULT NULL,
    `category_id` bigint DEFAULT NULL,
    `category_name_snapshot` varchar(255) DEFAULT NULL,
    `category_path_snapshot` json DEFAULT NULL,
    `updated_by_user_id` bigint NOT NULL,
    `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_registration_class_item` (`tenant_id`,`registration_case_id`,`order_item_id`,`deleted`),
    KEY `idx_registration_class_target` (`tenant_id`,`class_id`,`deleted`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报名逐商品分班草稿';

  -- Development correction: category is optional display metadata, never an assignment gate.
  -- No rows or grants are changed. Reapply safely; rollback to NOT NULL requires resolving new nulls.
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
             AND table_name='zsjos_registration_class_assignment' AND column_name='category_id'
             AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_registration_class_assignment` MODIFY COLUMN `category_id` bigint DEFAULT NULL;
  END IF;

  CREATE TABLE IF NOT EXISTS `zsjos_class_transfer_request` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `service_relation_id` bigint NOT NULL,
    `service_relation_version` int NOT NULL,
    `from_class_id` bigint NOT NULL,
    `from_class_no_snapshot` varchar(40) NOT NULL,
    `from_class_name_snapshot` varchar(100) NOT NULL,
    `target_class_id` bigint NOT NULL,
    `target_class_no_snapshot` varchar(40) NOT NULL,
    `target_class_name_snapshot` varchar(100) NOT NULL,
    `from_homeroom_user_id` bigint NOT NULL,
    `from_homeroom_user_name_snapshot` varchar(100) DEFAULT NULL,
    `target_homeroom_user_id` bigint NOT NULL,
    `target_homeroom_user_name_snapshot` varchar(100) DEFAULT NULL,
    `applicant_user_id` bigint NOT NULL,
    `reviewer_user_id` bigint NOT NULL,
    `reason` varchar(500) NOT NULL,
    `status` varchar(24) NOT NULL,
    `active_guard` bigint GENERATED ALWAYS AS
      (CASE WHEN (`status`='pending' AND `deleted`=b'0') THEN `service_relation_id` ELSE NULL END) STORED,
    `process_instance_id` varchar(64) DEFAULT NULL,
    `submitted_at` datetime NOT NULL,
    `finished_at` datetime DEFAULT NULL,
    `resolution_reason` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_transfer_active` (`tenant_id`,`active_guard`),
    UNIQUE KEY `uk_class_transfer_process` (`tenant_id`,`process_instance_id`,`deleted`),
    KEY `idx_class_transfer_applicant` (`tenant_id`,`applicant_user_id`,`status`,`create_time`),
    CONSTRAINT `chk_class_transfer_status` CHECK (`status` IN ('pending','approved','rejected','cancelled','invalidated'))
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程服务调班申请';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_service_relation' AND column_name='class_id') THEN
    ALTER TABLE `zsjos_service_relation` ADD COLUMN `class_id` bigint DEFAULT NULL
      COMMENT '交付班级编号' AFTER `registration_case_id`;
  END IF;
  IF (SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_service_relation' AND column_name='owner_user_id')='NO' THEN
    ALTER TABLE `zsjos_service_relation` MODIFY COLUMN `owner_user_id` bigint DEFAULT NULL
      COMMENT '学生服务负责人用户编号';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_service_relation' AND index_name='idx_service_class') THEN
    ALTER TABLE `zsjos_service_relation` ADD KEY `idx_service_class` (`tenant_id`,`class_id`,`status`,`deleted`);
  END IF;

  INSERT INTO `zsjos_delivery_class`
  (`class_no`,`class_name`,`system_class`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT 'PENDING','待分班',b'1','SERVING',0,'V188',NOW(),'V188',NOW(),b'0',tenant_row.id
  FROM `system_tenant` tenant_row
  WHERE tenant_row.status=0 AND tenant_row.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `zsjos_delivery_class` class_row
                    WHERE class_row.tenant_id=tenant_row.id AND class_row.system_class=b'1' AND class_row.deleted=b'0');

  UPDATE `zsjos_service_relation` relation_row
  JOIN `zsjos_delivery_class` pending_row ON pending_row.tenant_id=relation_row.tenant_id
    AND pending_row.system_class=b'1' AND pending_row.deleted=b'0'
  SET relation_row.class_id=pending_row.id,relation_row.updater='V188',relation_row.update_time=NOW()
  WHERE relation_row.class_id IS NULL AND relation_row.deleted=b'0';

  SET @v188_root := (SELECT id FROM system_menu WHERE path='/zsjos' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);
  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`workbench_render_mode`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
  (73620,'班级管理','zsjos:delivery-class:query-managed',2,63,@v188_root,'class-management','ant-design:cluster-outlined','zsjos/class-management','ZsjosClassManagement','native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73621,'创建班级','zsjos:delivery-class:create',3,1,73620,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73622,'修改班级','zsjos:delivery-class:update',3,2,73620,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73623,'结课班级','zsjos:delivery-class:complete',3,3,73620,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73624,'我的班级','zsjos:delivery-class:query-my',2,64,@v188_root,'my-classes','ant-design:team-outlined','zsjos/my-classes','ZsjosMyClasses','native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73625,'主管直接调班','zsjos:delivery-class:direct-transfer',3,4,73620,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73626,'发起调班','zsjos:class-transfer:create',3,1,73624,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0'),
  (73627,'查看调班申请','zsjos:class-transfer:query',3,2,73624,'','','',NULL,'native',0,b'1',b'1',b'0','V188',NOW(),'V188',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`component`=VALUES(`component`),
    `component_name`=VALUES(`component_name`),`workbench_render_mode`=VALUES(`workbench_render_mode`),
    `status`=0,`visible`=VALUES(`visible`),`deleted`=b'0',`updater`='V188',`update_time`=NOW();

  -- Keep the existing student detail route authorized for deep links without showing it as a primary menu.
  UPDATE `system_menu` SET `visible`=b'0',`updater`='V188',`update_time`=NOW()
  WHERE `id`=73020 AND `permission`='zsjos:student:query-my' AND `deleted`=b'0';

  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73620),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73000','$') AND NOT JSON_CONTAINS(`menu_ids`,'73620','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73621),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73000','$') AND NOT JSON_CONTAINS(`menu_ids`,'73621','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73622),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73000','$') AND NOT JSON_CONTAINS(`menu_ids`,'73622','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73623),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73000','$') AND NOT JSON_CONTAINS(`menu_ids`,'73623','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73625),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73000','$') AND NOT JSON_CONTAINS(`menu_ids`,'73625','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73624),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73020','$') AND NOT JSON_CONTAINS(`menu_ids`,'73624','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73626),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73020','$') AND NOT JSON_CONTAINS(`menu_ids`,'73626','$');
  UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73627),`updater`='V188',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73020','$') AND NOT JSON_CONTAINS(`menu_ids`,'73627','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V188','Delivery class closure',SHA2('V188__delivery_class_management.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V188','Delivery class closure',SHA2('V188__delivery_class_management.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v188_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v188_apply`;
