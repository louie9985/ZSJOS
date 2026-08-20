-- V094: student acceptance and continuous contact task chain.
-- Dependencies/order: apply after V093, registration/service relations, generic business tasks,
-- System dictionaries/user relations/notification rules, and BPM infrastructure.
-- Data scope: additive schema and configuration; active service relations become pending acceptance;
-- historical selected content-director routes are copied to the service collaborator field.
-- Repeatability: guarded columns, CREATE IF NOT EXISTS, and natural-key guarded seeds.
-- Recovery: forward-only. Disable menus/configuration to stop new use; business/audit rows are retained.
-- This file must not be executed against an existing environment without separate approval.

SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_business_task_notify_stage' AND column_name='task_version')=0,
  'ALTER TABLE `zsjos_business_task_notify_stage` ADD COLUMN `task_version` int NOT NULL DEFAULT 0 AFTER `task_id`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_business_task_notify_stage' AND index_name='uk_tenant_task_stage'),
  'ALTER TABLE `zsjos_business_task_notify_stage` DROP INDEX `uk_tenant_task_stage`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_business_task_notify_stage' AND index_name='uk_tenant_task_version_stage'),
  'SELECT 1', 'ALTER TABLE `zsjos_business_task_notify_stage` ADD UNIQUE KEY `uk_tenant_task_version_stage` (`tenant_id`,`task_id`,`task_version`,`stage`)');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='acceptance_status')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `acceptance_status` varchar(24) NOT NULL DEFAULT ''pending'' AFTER `owner_user_id`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='accepted_by_user_id')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `accepted_by_user_id` bigint DEFAULT NULL AFTER `acceptance_status`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='accepted_at')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `accepted_at` datetime DEFAULT NULL AFTER `accepted_by_user_id`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='content_director_user_id')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `content_director_user_id` bigint DEFAULT NULL AFTER `accepted_at`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='career_planner_user_id')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `career_planner_user_id` bigint DEFAULT NULL AFTER `content_director_user_id`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND index_name='idx_tenant_content_director_status'),
  'SELECT 1', 'ALTER TABLE `zsjos_service_relation` ADD KEY `idx_tenant_content_director_status` (`tenant_id`,`content_director_user_id`,`status`)');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND index_name='idx_tenant_career_planner_status'),
  'SELECT 1', 'ALTER TABLE `zsjos_service_relation` ADD KEY `idx_tenant_career_planner_status` (`tenant_id`,`career_planner_user_id`,`status`)');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_student_contact_config_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `version_no` int NOT NULL, `status` varchar(24) NOT NULL,
  `first_contact_timeout_minutes` int NOT NULL, `study_plan_timeout_minutes` int NOT NULL,
  `checklist_json` json NOT NULL, `quick_notes_json` json NOT NULL, `collaborator_tabs_json` json NOT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_version_no` (`tenant_id`,`version_no`,`deleted`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员联系配置版本';

CREATE TABLE IF NOT EXISTS `zsjos_student_contact_config_command` (
  `id` bigint NOT NULL AUTO_INCREMENT, `operation` varchar(24) NOT NULL, `idempotency_key` varchar(64) NOT NULL,
  `config_id` bigint NOT NULL, `expected_version` int NOT NULL,
  `request_fingerprint` varchar(64) NOT NULL DEFAULT '', `result_config_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_config_operation` (`tenant_id`,`config_id`,`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员联系配置幂等命令';

SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_config_command' AND column_name='request_fingerprint')=0,
  'ALTER TABLE `zsjos_student_contact_config_command` ADD COLUMN `request_fingerprint` varchar(64) NOT NULL DEFAULT '''' AFTER `expected_version`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_student_contact_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `service_relation_id` bigint NOT NULL, `task_id` bigint NOT NULL,
  `contact_type` varchar(64) NOT NULL, `successful` bit(1) NOT NULL,
  `unsuccessful_reason_value` varchar(100) DEFAULT NULL,
  `unsuccessful_reason_label_snapshot` varchar(100) DEFAULT NULL, `remark` varchar(2000) NOT NULL,
  `attachment_file_ids_json` json NOT NULL, `checklist_result_json` json NOT NULL,
  `next_contact_at` datetime NOT NULL, `operator_user_id` bigint NOT NULL, `submitted_at` datetime NOT NULL,
  `idempotency_key` varchar(128) NOT NULL, `request_fingerprint` varchar(64) NOT NULL DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_tenant_task` (`tenant_id`,`task_id`),
  KEY `idx_tenant_relation_submitted` (`tenant_id`,`service_relation_id`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变学员联系记录';

SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_record' AND column_name='request_fingerprint')=0,
  'ALTER TABLE `zsjos_student_contact_record` ADD COLUMN `request_fingerprint` varchar(64) NOT NULL DEFAULT '''' AFTER `idempotency_key`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_student_contact_extension` (
  `id` bigint NOT NULL AUTO_INCREMENT, `service_relation_id` bigint NOT NULL, `task_id` bigint NOT NULL,
  `status` varchar(24) NOT NULL, `original_due_at` datetime NOT NULL, `requested_due_at` datetime NOT NULL,
  `reason_value` varchar(100) NOT NULL, `reason_label_snapshot` varchar(100) NOT NULL,
  `description` varchar(1000) NOT NULL, `attachment_file_ids_json` json NOT NULL,
  `applicant_user_id` bigint NOT NULL, `reviewer_user_id` bigint NOT NULL,
  `process_instance_id` varchar(64) DEFAULT NULL, `decision_reason` varchar(1000) DEFAULT NULL,
  `submitted_at` datetime NOT NULL, `resolved_at` datetime DEFAULT NULL,
  `idempotency_key` varchar(128) NOT NULL, `withdrawal_idempotency_key` varchar(64) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_tenant_withdrawal_idempotency` (`tenant_id`,`withdrawal_idempotency_key`),
  UNIQUE KEY `uk_tenant_process` (`tenant_id`,`process_instance_id`),
  KEY `idx_tenant_reviewer_status` (`tenant_id`,`reviewer_user_id`,`status`,`submitted_at`),
  KEY `idx_tenant_task_status` (`tenant_id`,`task_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员联系延期申请快照';

SET @v094_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_extension' AND column_name='withdrawal_idempotency_key')=0,
  'ALTER TABLE `zsjos_student_contact_extension` ADD COLUMN `withdrawal_idempotency_key` varchar(64) DEFAULT NULL AFTER `idempotency_key`', 'SELECT 1');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;
SET @v094_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_extension' AND index_name='uk_tenant_withdrawal_idempotency'),
  'SELECT 1', 'ALTER TABLE `zsjos_student_contact_extension` ADD UNIQUE KEY `uk_tenant_withdrawal_idempotency` (`tenant_id`,`withdrawal_idempotency_key`)');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_student_collaborator_assignment_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `service_relation_id` bigint NOT NULL,
  `collaborator_type` varchar(32) NOT NULL, `previous_user_id` bigint DEFAULT NULL,
  `assigned_user_id` bigint NOT NULL, `operator_user_id` bigint NOT NULL, `reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(128) NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_relation_type` (`tenant_id`,`service_relation_id`,`collaborator_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员协作者分配审计';

INSERT INTO `zsjos_student_contact_config_version`
(`version_no`,`status`,`first_contact_timeout_minutes`,`study_plan_timeout_minutes`,`checklist_json`,
 `quick_notes_json`,`collaborator_tabs_json`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 1,'published',120,1440,
 '[{"key":"add_student","title":"添加学员","type":"checkbox","enabled":true,"attachmentRequired":false,"sort":10},{"key":"voice_learning_plan","title":"语音沟通学习计划","type":"checkbox","enabled":true,"attachmentRequired":false,"sort":20},{"key":"invite_group_promote_teacher","title":"邀请进入学习群并推崇 IP 老师","type":"checkbox","enabled":true,"attachmentRequired":false,"sort":30},{"key":"notify_messages","title":"告知相关消息","type":"checkbox","enabled":true,"attachmentRequired":false,"sort":40},{"key":"connect_course_teacher","title":"@课程老师并完成对接","type":"checkbox","enabled":true,"attachmentRequired":false,"sort":50}]',
 '[]','{"content_director":["first-contact","study-plan","contacts"],"career_planner":["first-contact","study-plan","contacts"]}',
 0,'migration-V094',NOW(),'migration-V094',NOW(),b'0',tenant.id
FROM `system_tenant` tenant WHERE tenant.status=0 AND tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_student_contact_config_version` config
    WHERE config.tenant_id=tenant.id AND config.status='published' AND config.deleted=b'0');

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.type,0,'仅创建字典类型；业务选项由管理员维护','migration-V094',NOW(),'migration-V094',NOW(),b'0'
FROM (SELECT '学员未联系原因' name,'zsjos_student_contact_unsuccessful_reason' type
      UNION ALL SELECT '学员联系延期原因','zsjos_student_contact_extension_reason') seed
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` dict_type
  WHERE dict_type.type=seed.type AND dict_type.deleted=b'0');

INSERT INTO `zsjos_user_relation_scene`
(`name`,`code`,`source_label`,`target_label`,`source_post_code`,`target_post_code`,`status`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,seed.code,seed.source_label,seed.target_label,seed.source_post,seed.target_post,0,
       'V094 预置场景；不预置人员关系','migration-V094',NOW(),'migration-V094',NOW(),b'0',tenant.id
FROM `system_tenant` tenant JOIN (
 SELECT '报名履约主管分配学习规划师' name,'registration_manager_study_planner' code,'报名履约主管' source_label,'学习规划师' target_label,'registration_manager' source_post,'study_planner' target_post
 UNION ALL SELECT '报名履约专员分配学习规划师','registration_specialist_study_planner','报名履约专员','学习规划师','registration_specialist','study_planner'
 UNION ALL SELECT '学习规划师分配编导','study_planner_content_director','学习规划师','编导','study_planner','content_director'
 UNION ALL SELECT '学习规划师分配职业规划师','study_planner_career_planner','学习规划师','职业规划师','study_planner','career_planner'
) seed WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `zsjos_user_relation_scene` scene
  WHERE scene.tenant_id=tenant.id AND scene.code=seed.code AND scene.deleted=b'0');

-- Historical selected director routes become collaborators before route selection is retired for new registrations.
UPDATE `zsjos_service_relation` relation_row
JOIN `zsjos_registration_case_route` route_row
  ON route_row.registration_case_id=relation_row.registration_case_id
 AND route_row.tenant_id=relation_row.tenant_id AND route_row.deleted=b'0'
SET relation_row.content_director_user_id=route_row.assignee_user_id,
    relation_row.updater='migration-V094', relation_row.update_time=NOW()
WHERE relation_row.deleted=b'0' AND relation_row.content_director_user_id IS NULL
  AND route_row.assignee_type='content_director' AND route_row.selected=b'1'
  AND route_row.assignee_user_id IS NOT NULL;

UPDATE `zsjos_service_relation`
SET `acceptance_status`='pending',`accepted_by_user_id`=NULL,`accepted_at`=NULL,
    `updater`='migration-V094',`update_time`=NOW()
WHERE `status`='active' AND `deleted`=b'0' AND `acceptance_status`<>'pending'
  AND `accepted_by_user_id` IS NULL AND `accepted_at` IS NULL;

SET @v094_menu_collision := (SELECT COUNT(*) FROM `system_menu` existing JOIN (
  SELECT 73400 id,'zsjos:student-contact-config:query' permission UNION ALL
  SELECT 73401,'zsjos:student-contact-config:update' UNION ALL SELECT 73402,'zsjos:student-contact-config:publish' UNION ALL
  SELECT 73410,'zsjos:student-contact-extension:review' UNION ALL SELECT 73420,'zsjos:student:accept' UNION ALL
  SELECT 73421,'zsjos:student-contact:first-submit' UNION ALL SELECT 73422,'zsjos:student-contact:study-plan-submit' UNION ALL
  SELECT 73423,'zsjos:student-contact:submit' UNION ALL SELECT 73424,'zsjos:student-contact-extension:apply' UNION ALL
  SELECT 73425,'zsjos:student-collaborator:assign' UNION ALL SELECT 73426,'zsjos:student-collaborator:correct'
) expected ON expected.id=existing.id WHERE COALESCE(existing.permission,'')<>expected.permission);
SET @v094_sql := IF(@v094_menu_collision=0, 'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''V094 menu ID collision: existing menu ownership differs''');
PREPARE v094_stmt FROM @v094_sql; EXECUTE v094_stmt; DEALLOCATE PREPARE v094_stmt;

SET @v094_zsjos_menu_id := (SELECT `id` FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `deleted`=b'0' ORDER BY `id` LIMIT 1);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
 `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(73400,'学员联系配置','zsjos:student-contact-config:query',2,63,@v094_zsjos_menu_id,'/zsjos/student-contact-config','ep:setting','zsjos/studentContactConfig/index','ZsjosStudentContactConfig',0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73401,'更新学员联系配置','zsjos:student-contact-config:update',3,1,73400,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73402,'发布学员联系配置','zsjos:student-contact-config:publish',3,2,73400,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73410,'异常情况处理','zsjos:student-contact-extension:review',2,64,@v094_zsjos_menu_id,'/zsjos/student-contact-exceptions','ep:warning','zsjos/studentContactExceptions/index','ZsjosStudentContactExceptions',0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73420,'确认接收学员','zsjos:student:accept',3,1,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73421,'提交首次联系','zsjos:student-contact:first-submit',3,2,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73422,'提交学习计划','zsjos:student-contact:study-plan-submit',3,3,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73423,'提交普通联系','zsjos:student-contact:submit',3,4,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73424,'申请联系延期','zsjos:student-contact-extension:apply',3,5,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73425,'分配学员协作者','zsjos:student-collaborator:assign',3,6,73020,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0'),
(73426,'纠正学员协作者','zsjos:student-collaborator:correct',3,3,73400,'','','',NULL,0,b'1',b'1',b'0','migration-V094',NOW(),'migration-V094',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),
 `type`=VALUES(`type`),`sort`=VALUES(`sort`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
 `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
 `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
 `deleted`=b'0',`updater`='migration-V094',`update_time`=NOW();

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id,menu_row.id,'migration-V094',NOW(),'migration-V094',NOW(),b'0',role_row.tenant_id
FROM `system_role` role_row JOIN `system_menu` menu_row ON menu_row.deleted=b'0'
WHERE role_row.deleted=b'0'
  AND ((role_row.code='system_administrator' AND menu_row.id IN (73400,73401,73402,73410,73420,73421,73422,73423,73424,73425,73426))
    OR (role_row.code='study_planner' AND menu_row.id IN (73420,73421,73422,73423,73424,73425)))
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` grant_row
    WHERE grant_row.role_id=role_row.id AND grant_row.menu_id=menu_row.id
      AND grant_row.tenant_id=role_row.tenant_id AND grant_row.deleted=b'0');

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.code,'中世健消息中心',seed.scene_code,seed.title,
       '{{student.identifier}}的{{contact.stage}}任务提醒',
       '编号{{student.identifier}}的任务截止时间为{{contact.dueAt}}，当前阶段：{{contact.stage}}。',
       2,'["student.identifier","contact.stage","contact.dueAt"]',0,'V094 系统默认模板',
       'migration-V094',NOW(),'migration-V094',NOW(),b'0'
FROM (
 SELECT '学员首次联系提醒' name,'ZSJOS_STUDENT_FIRST_CONTACT_REMINDER' code,
        'zsjos.student.first_contact_reminder' scene_code,'首次联系提醒' title
 UNION ALL SELECT '学员学习计划提醒','ZSJOS_STUDENT_STUDY_PLAN_REMINDER',
        'zsjos.student.study_plan_reminder','制定学习计划提醒'
 UNION ALL SELECT '学员普通联系提醒','ZSJOS_STUDENT_CONTACT_REMINDER',
        'zsjos.student.contact_reminder','学员联系提醒'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` template
  WHERE template.code=seed.code AND template.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,
 `timing_stage`,`timing_offset_minutes`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,seed.scene_code,'in_app',template.id,seed.roles,'[]','business_detail',seed.stage,
       seed.offset_minutes,0,'migration-V094',NOW(),'migration-V094',NOW(),b'0',tenant.id
FROM `system_tenant` tenant JOIN (
 SELECT '学员首次联系-提前30分钟' name,'zsjos.student.first_contact_reminder' scene_code,
        '["study_planner"]' roles,'advance' stage,30 offset_minutes,'ZSJOS_STUDENT_FIRST_CONTACT_REMINDER' template_code
 UNION ALL SELECT '学员首次联系-到期','zsjos.student.first_contact_reminder',
        '["study_planner","delivery_supervisor"]','due',0,'ZSJOS_STUDENT_FIRST_CONTACT_REMINDER'
 UNION ALL SELECT '学员首次联系-逾期5分钟','zsjos.student.first_contact_reminder',
        '["study_planner","delivery_supervisor"]','overdue',5,'ZSJOS_STUDENT_FIRST_CONTACT_REMINDER'
 UNION ALL SELECT '制定学习计划-提前30分钟','zsjos.student.study_plan_reminder',
        '["study_planner"]','advance',30,'ZSJOS_STUDENT_STUDY_PLAN_REMINDER'
 UNION ALL SELECT '制定学习计划-到期','zsjos.student.study_plan_reminder',
        '["study_planner"]','due',0,'ZSJOS_STUDENT_STUDY_PLAN_REMINDER'
 UNION ALL SELECT '制定学习计划-逾期5分钟','zsjos.student.study_plan_reminder',
        '["study_planner"]','overdue',5,'ZSJOS_STUDENT_STUDY_PLAN_REMINDER'
 UNION ALL SELECT '普通联系-提前30分钟','zsjos.student.contact_reminder',
        '["study_planner"]','advance',30,'ZSJOS_STUDENT_CONTACT_REMINDER'
 UNION ALL SELECT '普通联系-到期','zsjos.student.contact_reminder',
        '["study_planner"]','due',0,'ZSJOS_STUDENT_CONTACT_REMINDER'
 UNION ALL SELECT '普通联系-逾期5分钟','zsjos.student.contact_reminder',
        '["study_planner"]','overdue',5,'ZSJOS_STUDENT_CONTACT_REMINDER'
) seed JOIN `system_notify_template` template ON template.code=seed.template_code AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
  WHERE rule_row.tenant_id=tenant.id AND rule_row.scene_code=seed.scene_code
    AND rule_row.name=seed.name AND rule_row.channel_code='in_app'
    AND rule_row.template_id=template.id AND rule_row.timing_stage=seed.stage
    AND rule_row.timing_offset_minutes=seed.offset_minutes AND rule_row.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V094','Student acceptance and continuous contact chain','V094__student_contact_chain.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V094','Student acceptance and continuous contact chain',SHA2('V094__student_contact_chain.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
