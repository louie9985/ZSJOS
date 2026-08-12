-- Adds the generic business-task query contract and simplified work-plan foundation.
-- Dependencies: V020 schema; V021 is owned by the lead workstream and precedes V022 only in release integration.
-- Execution order: BusinessTask columns/indexes, plan configuration, plan/task/report tables, menus, version record.
-- Repeatability: all DDL and metadata writes are guarded; no existing business rows are deleted by this migration.
-- Data scope: schema metadata and server-owned menu/permission definitions only; no role grants are added.
-- Rollback limitation: released environments are forward-only. The approved local rebuild is guarded externally by zero-row checks.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND column_name='title_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD COLUMN `title_snapshot` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''稳定标题摘要'' AFTER `assignee_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND column_name='summary_snapshot'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD COLUMN `summary_snapshot` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''稳定内容摘要'' AFTER `title_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND column_name='action_code'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD COLUMN `action_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''受控前端动作编码'' AFTER `summary_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND column_name='remind_at'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD COLUMN `remind_at` datetime DEFAULT NULL COMMENT ''提醒时间'' AFTER `due_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND index_name='idx_tenant_assignee_status_due_v2'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD KEY `idx_tenant_assignee_status_due_v2` (`tenant_id`,`assignee_id`,`status`,`due_at`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
          AND table_name='zsjos_business_task' AND index_name='idx_tenant_assignee_status_updated'),
  'SELECT 1',
  'ALTER TABLE `zsjos_business_task` ADD KEY `idx_tenant_assignee_status_updated` (`tenant_id`,`assignee_id`,`status`,`update_time`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划类型编号',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统生成编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型说明',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`),
  KEY `idx_tenant_status_sort` (`tenant_id`,`status`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划类型';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板编号',
  `type_id` bigint NOT NULL COMMENT '计划类型编号',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统生成编码',
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板说明',
  `status` varchar(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/disabled',
  `current_version_no` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_type_code` (`tenant_id`,`type_id`,`code`),
  KEY `idx_tenant_status` (`tenant_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划模板';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板版本编号',
  `template_id` bigint NOT NULL COMMENT '模板编号',
  `version_no` int NOT NULL COMMENT '版本号',
  `status` varchar(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/published',
  `period_mode` varchar(32) NOT NULL DEFAULT 'custom' COMMENT 'day/week/month/quarter/year/custom',
  `published_at` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_template_version` (`tenant_id`,`template_id`,`version_no`),
  KEY `idx_tenant_status` (`tenant_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划模板版本';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_template_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板字段编号',
  `template_version_id` bigint NOT NULL COMMENT '模板版本编号',
  `field_key` varchar(64) NOT NULL COMMENT '系统生成稳定字段编码',
  `label` varchar(150) NOT NULL COMMENT '字段名称',
  `section` varchar(32) NOT NULL COMMENT 'plan/task/report/summary',
  `field_type` varchar(32) NOT NULL,
  `required` bit(1) NOT NULL DEFAULT b'0',
  `unit` varchar(32) DEFAULT NULL,
  `placeholder` varchar(255) DEFAULT NULL,
  `filterable` bit(1) NOT NULL DEFAULT b'0',
  `exportable` bit(1) NOT NULL DEFAULT b'1',
  `options_json` json DEFAULT NULL,
  `default_value_json` json DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_version_field` (`tenant_id`,`template_version_id`,`field_key`),
  KEY `idx_tenant_section_sort` (`tenant_id`,`template_version_id`,`section`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划模板字段';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_template_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `dept_id` bigint DEFAULT NULL,
  `include_children` bit(1) NOT NULL DEFAULT b'1',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), KEY `idx_tenant_template_dept` (`tenant_id`,`template_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划模板适用范围';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_template_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_version_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `deliverable_requirement` varchar(2000) DEFAULT NULL,
  `due_offset_days` int DEFAULT NULL,
  `due_offset_basis` varchar(16) DEFAULT NULL,
  `confirmation_required` bit(1) NOT NULL DEFAULT b'0',
  `sort` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), KEY `idx_tenant_version_sort` (`tenant_id`,`template_version_id`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划模板默认任务';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作计划编号',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `period_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '周期类型',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'draft/active/completed/cancelled',
  `creator_user_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `owner_dept_id` bigint DEFAULT NULL COMMENT '负责人部门快照',
  `plan_type_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL COMMENT '创建时模板版本快照',
  `objective` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '计划目标说明',
  `key_requirements` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重点要求',
  `published_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `cancel_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_start` (`tenant_id`,`status`,`start_date`,`id`),
  KEY `idx_tenant_template` (`tenant_id`,`template_id`,`status`,`start_date`),
  KEY `idx_tenant_owner` (`tenant_id`,`owner_user_id`,`status`,`start_date`),
  KEY `idx_tenant_dept` (`tenant_id`,`owner_dept_id`,`status`,`start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划';

CREATE TABLE IF NOT EXISTS `zsjos_work_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作任务编号',
  `plan_id` bigint DEFAULT NULL COMMENT '临时任务为空',
  `parent_task_id` bigint DEFAULT NULL COMMENT '上级任务编号',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deliverable_requirement` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignee_user_id` bigint NOT NULL,
  `assignee_dept_id` bigint DEFAULT NULL COMMENT '分派时部门快照',
  `assigner_user_id` bigint NOT NULL,
  `due_at` datetime DEFAULT NULL,
  `remind_at` datetime DEFAULT NULL,
  `confirmation_required` bit(1) NOT NULL DEFAULT b'0',
  `confirmer_user_id` bigint DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'draft/pending/awaiting_confirmation/completed/cancelled',
  `reported_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `cancel_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reminder_notified_at` datetime DEFAULT NULL,
  `overdue_notified_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_plan_parent` (`tenant_id`,`plan_id`,`parent_task_id`,`id`),
  KEY `idx_tenant_assignee_status_due` (`tenant_id`,`assignee_user_id`,`status`,`due_at`,`id`),
  KEY `idx_tenant_confirmer_status` (`tenant_id`,`confirmer_user_id`,`status`,`id`),
  KEY `idx_tenant_dept_status` (`tenant_id`,`assignee_dept_id`,`status`,`id`),
  KEY `idx_tenant_reminder_scan` (`tenant_id`,`status`,`reminder_notified_at`,`remind_at`,`id`),
  KEY `idx_tenant_overdue_scan` (`tenant_id`,`status`,`overdue_notified_at`,`due_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作任务与临时任务';

CREATE TABLE IF NOT EXISTS `zsjos_work_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '完成汇报编号',
  `task_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `completion_summary` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitter_user_id` bigint NOT NULL,
  `submitted_at` datetime NOT NULL,
  `confirmation_decision` varchar(32) DEFAULT NULL COMMENT 'approved/rejected/auto_approved',
  `confirmation_comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `confirmed_by_user_id` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_task_revision` (`tenant_id`,`task_id`,`revision_no`),
  KEY `idx_tenant_submitter_time` (`tenant_id`,`submitter_user_id`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作任务完成汇报历史';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划总结编号',
  `plan_id` bigint NOT NULL,
  `summary` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitter_user_id` bigint NOT NULL,
  `submitted_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_plan` (`tenant_id`,`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划总结';

CREATE TABLE IF NOT EXISTS `zsjos_work_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_type` varchar(32) NOT NULL COMMENT 'report/summary',
  `subject_id` bigint NOT NULL,
  `infra_file_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_subject_file` (`tenant_id`,`subject_type`,`subject_id`,`infra_file_id`),
  KEY `idx_tenant_subject_sort` (`tenant_id`,`subject_type`,`subject_id`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作汇报与计划总结附件';

CREATE TABLE IF NOT EXISTS `zsjos_work_plan_field_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `template_field_id` bigint DEFAULT NULL COMMENT '补充字段为空',
  `field_key` varchar(64) NOT NULL,
  `label` varchar(150) NOT NULL,
  `section` varchar(32) NOT NULL COMMENT 'plan/task/report/summary',
  `field_type` varchar(32) NOT NULL,
  `required` bit(1) NOT NULL DEFAULT b'0',
  `unit` varchar(32) DEFAULT NULL,
  `placeholder` varchar(255) DEFAULT NULL,
  `filterable` bit(1) NOT NULL DEFAULT b'0',
  `exportable` bit(1) NOT NULL DEFAULT b'1',
  `options_json` json DEFAULT NULL,
  `default_value_json` json DEFAULT NULL,
  `origin` varchar(32) NOT NULL COMMENT 'template/supplemental',
  `sort` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_plan_field` (`tenant_id`,`plan_id`,`field_key`),
  KEY `idx_tenant_key_section_plan` (`tenant_id`,`field_key`,`section`,`plan_id`),
  KEY `idx_tenant_plan_section_sort` (`tenant_id`,`plan_id`,`section`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划字段快照';

CREATE TABLE IF NOT EXISTS `zsjos_work_field_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `field_definition_id` bigint NOT NULL,
  `subject_type` varchar(32) NOT NULL COMMENT 'plan/task/report/summary',
  `subject_id` bigint NOT NULL,
  `value_text` varchar(4000) DEFAULT NULL,
  `value_decimal` decimal(20,6) DEFAULT NULL,
  `value_datetime` datetime DEFAULT NULL,
  `value_ref_id` bigint DEFAULT NULL,
  `value_json` json DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_subject_field` (`tenant_id`,`subject_type`,`subject_id`,`field_definition_id`),
  KEY `idx_tenant_field_decimal` (`tenant_id`,`field_definition_id`,`value_decimal`),
  KEY `idx_tenant_field_datetime` (`tenant_id`,`field_definition_id`,`value_datetime`),
  KEY `idx_tenant_field_ref` (`tenant_id`,`field_definition_id`,`value_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划统一字段值';

CREATE TABLE IF NOT EXISTS `zsjos_work_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_type` varchar(32) NOT NULL COMMENT 'plan/task',
  `subject_id` bigint NOT NULL,
  `change_type` varchar(32) NOT NULL,
  `before_snapshot` json DEFAULT NULL,
  `after_snapshot` json DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `changed_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), KEY `idx_tenant_subject_changed` (`tenant_id`,`subject_type`,`subject_id`,`changed_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工作计划与任务变更历史';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6900,'计划监管','zsjos:work-plan:query',2,2,6735,'work-plans','ep:calendar','zsjos/workPlan/index','ZsjosWorkPlan',0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6901,'创建工作计划','zsjos:work-plan:create',3,1,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6902,'调整工作计划','zsjos:work-plan:update',3,2,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6903,'发布工作计划','zsjos:work-plan:publish',3,3,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6904,'分派工作任务','zsjos:work-plan:assign',3,4,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6905,'提交完成汇报','zsjos:work-plan:complete',3,5,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6906,'确认任务完成','zsjos:work-plan:review',3,6,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6907,'取消工作计划','zsjos:work-plan:cancel',3,7,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6910,'计划模板','zsjos:work-plan-config:query',2,3,6735,'work-plan-config','ep:setting','zsjos/workPlanConfig/index','ZsjosWorkPlanConfig',0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6911,'配置计划类型','zsjos:work-plan-config:create',3,1,6910,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6912,'配置计划模板','zsjos:work-plan-config:update',3,2,6910,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6913,'发布模板版本','zsjos:work-plan-config:publish',3,3,6910,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6914,'停用计划模板','zsjos:work-plan-config:disable',3,4,6910,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6915,'分派下级任务','zsjos:work-plan:decompose',3,8,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6916,'提交计划总结','zsjos:work-plan:close',3,9,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0'),
(6917,'导出计划任务','zsjos:work-plan:export',3,10,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V022',NOW(),'migration-V022',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `sort`=VALUES(`sort`),
  `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`), `icon`=VALUES(`icon`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `updater`='migration-V022', `update_time`=NOW();

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V022','Add simplified work-plan task tree, reports, summaries and business-task foundation','workbench-foundation-v3')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
