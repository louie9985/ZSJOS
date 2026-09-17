-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- UTF-8. V187: tenant-scoped exam schedules and Workbench permissions.
-- Dependencies/order: apply after V186; product categories and Calendar menu 73600 must exist.
-- Data scope: creates an empty schedule table, one global Infra parameter, menu metadata, package coverage,
-- and initial role-menu grants. It inserts no schedules, products, SKUs, users, departments or accounts.
-- Repeatability: fixed IDs, guarded inserts and version registries make reruns safe.
-- Recovery: forward-only; preserve schedule history and disable the menu/permissions in a later migration.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v187_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v187_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V186')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V186') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V187 requires V186 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73600 AND `type`=1
                 AND `path`='/calendar' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V187 requires Calendar menu 73600';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73610
             AND (`type`<>2 OR `parent_id` NOT IN (6735,73600) OR `permission`<>'zsjos:exam-calendar:query'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73611
             AND (`type`<>3 OR `parent_id`<>73610 OR `permission`<>'zsjos:exam-calendar:manage'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73612
             AND (`type`<>3 OR `parent_id`<>73610 OR `permission`<>'zsjos:exam-calendar:query')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V187 exam calendar menu IDs are already owned';
  END IF;

  START TRANSACTION;

  CREATE TABLE IF NOT EXISTS `zsjos_exam_schedule` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '考期安排编号',
    `tenant_id` bigint NOT NULL COMMENT '租户编号',
    `schedule_type` varchar(16) NOT NULL COMMENT '时间类型：EXACT/ROUGH',
    `exact_date` date DEFAULT NULL COMMENT '精确考试日期',
    `rough_start_date` date DEFAULT NULL COMMENT '粗略开始日期',
    `rough_end_date` date DEFAULT NULL COMMENT '粗略结束日期',
    `category_id` bigint NOT NULL COMMENT '产品分类编号',
    `category_name_snapshot` varchar(100) NOT NULL COMMENT '分类名称快照',
    `category_path_snapshot` json NOT NULL COMMENT '分类路径快照',
    `record_status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '记录状态：DRAFT/PUBLISHED/REVOKED',
    `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
    `published_at` datetime DEFAULT NULL COMMENT '发布时间',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_exam_schedule_exact` (`tenant_id`,`schedule_type`,`exact_date`,`record_status`,`deleted`),
    KEY `idx_exam_schedule_rough` (`tenant_id`,`schedule_type`,`rough_start_date`,`rough_end_date`,`record_status`,`deleted`),
    KEY `idx_exam_schedule_category` (`tenant_id`,`category_id`,`deleted`),
    CONSTRAINT `chk_exam_schedule_type` CHECK (`schedule_type` IN ('EXACT','ROUGH')),
    CONSTRAINT `chk_exam_schedule_status` CHECK (`record_status` IN ('DRAFT','PUBLISHED','REVOKED')),
    CONSTRAINT `chk_exam_schedule_dates` CHECK (
      (`schedule_type`='EXACT' AND `exact_date` IS NOT NULL AND `rough_start_date` IS NULL AND `rough_end_date` IS NULL)
      OR (`schedule_type`='ROUGH' AND `exact_date` IS NULL AND `rough_start_date` IS NOT NULL
          AND `rough_end_date` IS NOT NULL AND `rough_end_date` >= `rough_start_date`)
    )
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考期安排';

  INSERT INTO `infra_config`
  (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 'ZSJOS考期日历',1,'即将开始提前天数','zsjos.exam-calendar.upcoming-days','3',b'1',
         '全系统参数；非负整数，非法或缺失时后端使用3天','V187',NOW(),'V187',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `infra_config`
                    WHERE `config_key`='zsjos.exam-calendar.upcoming-days' AND `deleted`=b'0');

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (73610,'考期日历','zsjos:exam-calendar:query',2,3,73600,'exam-calendar','ep:calendar',
     'zsjos/examCalendar/index','ZsjosExamCalendar','native',0,b'1',b'1',b'1','V187',NOW(),'V187',NOW(),b'0'),
    (73612,'查看考期','zsjos:exam-calendar:query',3,1,73610,'','','',NULL,
     'native',0,b'1',b'1',b'0','V187',NOW(),'V187',NOW(),b'0'),
    (73611,'管理考期','zsjos:exam-calendar:manage',3,2,73610,'','','',NULL,
     'native',0,b'1',b'1',b'0','V187',NOW(),'V187',NOW(),b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),
    `parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
    `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),
    `workbench_render_mode`=VALUES(`workbench_render_mode`),`status`=0,`visible`=VALUES(`visible`),
    `keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),`deleted`=b'0',
    `updater`='V187',`update_time`=NOW();

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73610),`updater`='V187',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'73610','$');
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73611),`updater`='V187',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'73611','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73612),`updater`='V187',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73610','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'73612','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V187','Exam calendar',SHA2('V187__exam_calendar.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V187','Exam calendar',SHA2('V187__exam_calendar.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v187_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v187_apply`;
