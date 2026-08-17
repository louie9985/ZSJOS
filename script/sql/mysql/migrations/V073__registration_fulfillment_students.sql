-- V073: registration fulfillment public pool and student service relationships.
-- Dependencies/order: apply after V072 and the sales-order/person/System role schemas.
-- Data scope: additive tables, menu metadata, reviewed role grants, and one default template for each active tenant.
-- Repeatability: CREATE IF NOT EXISTS plus natural-key guarded seeds; no historical orders are backfilled.
-- Recovery: disable the new menus and deploy a forward migration. Tables retain audit/business facts and are not dropped.
-- This file must not be executed against an existing environment without separate approval.

CREATE TABLE IF NOT EXISTS `zsjos_registration_checklist_template` (
  `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(100) NOT NULL,
  `published_version_id` bigint DEFAULT NULL, `draft_version_id` bigint DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_template_tenant` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约清单模板';

CREATE TABLE IF NOT EXISTS `zsjos_registration_checklist_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `template_id` bigint NOT NULL, `version_no` int NOT NULL,
  `status` varchar(20) NOT NULL, `published_at` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_template_version` (`tenant_id`,`template_id`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约清单版本';

CREATE TABLE IF NOT EXISTS `zsjos_registration_checklist_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `version_id` bigint NOT NULL, `item_key` varchar(64) NOT NULL,
  `item_type` varchar(32) NOT NULL, `title` varchar(100) NOT NULL, `sort` int NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1', `system_required` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_version_item` (`tenant_id`,`version_id`,`item_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约清单模板项';

CREATE TABLE IF NOT EXISTS `zsjos_registration_case` (
  `id` bigint NOT NULL AUTO_INCREMENT, `order_id` bigint NOT NULL, `status` varchar(24) NOT NULL,
  `checklist_version_id` bigint NOT NULL, `study_planner_user_id` bigint DEFAULT NULL,
  `registration_approved_at` datetime NOT NULL, `completed_by_user_id` bigint DEFAULT NULL, `completed_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL, `cancel_reason` varchar(255) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_case_order` (`tenant_id`,`order_id`,`deleted`),
  KEY `idx_registration_pool` (`tenant_id`,`status`,`registration_approved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约公共池任务';

-- The baseline already contained a registration draft model. Extend it in place without rewriting rows.
SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='checklist_version_id')=0,
  'ALTER TABLE `zsjos_registration_case` ADD COLUMN `checklist_version_id` bigint DEFAULT NULL AFTER `status`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;
SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='study_planner_user_id')=0,
  'ALTER TABLE `zsjos_registration_case` ADD COLUMN `study_planner_user_id` bigint DEFAULT NULL AFTER `checklist_version_id`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;
SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='registration_approved_at')=0,
  'ALTER TABLE `zsjos_registration_case` ADD COLUMN `registration_approved_at` datetime DEFAULT NULL AFTER `study_planner_user_id`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;
SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='completed_by_user_id')=0,
  'ALTER TABLE `zsjos_registration_case` ADD COLUMN `completed_by_user_id` bigint DEFAULT NULL AFTER `registration_approved_at`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_registration_case_checklist_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `registration_case_id` bigint NOT NULL, `template_item_id` bigint NOT NULL,
  `item_key` varchar(64) NOT NULL, `item_type` varchar(32) NOT NULL, `title_snapshot` varchar(100) NOT NULL,
  `sort` int NOT NULL, `checked` bit(1) NOT NULL DEFAULT b'0', `checked_by_user_id` bigint DEFAULT NULL,
  `checked_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_case_item` (`tenant_id`,`registration_case_id`,`template_item_id`,`deleted`),
  KEY `idx_registration_case_sort` (`tenant_id`,`registration_case_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约清单实例项';

CREATE TABLE IF NOT EXISTS `zsjos_registration_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `registration_case_id` bigint NOT NULL, `checklist_item_id` bigint NOT NULL,
  `item_type` varchar(32) NOT NULL, `item_label_snapshot` varchar(100) NOT NULL,
  `occurred_at` datetime DEFAULT NULL, `recorded_at` datetime NOT NULL, `recorded_by_user_id` bigint DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_fact_item` (`tenant_id`,`checklist_item_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约完成事实';

SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_item' AND column_name='checklist_item_id')=0,
  'ALTER TABLE `zsjos_registration_item` ADD COLUMN `checklist_item_id` bigint DEFAULT NULL AFTER `registration_case_id`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;
SET @v073_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_item' AND column_name='item_label_snapshot')=0,
  'ALTER TABLE `zsjos_registration_item` ADD COLUMN `item_label_snapshot` varchar(100) DEFAULT NULL AFTER `item_type`', 'SELECT 1');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;
SET @v073_sql := IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_item' AND index_name='uk_registration_fact_item'),
  'SELECT 1', 'ALTER TABLE `zsjos_registration_item` ADD UNIQUE KEY `uk_registration_fact_item` (`tenant_id`,`checklist_item_id`)');
PREPARE v073_stmt FROM @v073_sql; EXECUTE v073_stmt; DEALLOCATE PREPARE v073_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_service_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT, `person_id` bigint NOT NULL, `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL, `registration_case_id` bigint NOT NULL, `status` varchar(24) NOT NULL,
  `owner_user_id` bigint NOT NULL, `service_snapshot` text DEFAULT NULL, `activated_at` datetime NOT NULL,
  `paused_at` datetime DEFAULT NULL, `pause_reason` varchar(255) DEFAULT NULL, `completed_at` datetime DEFAULT NULL,
  `terminated_at` datetime DEFAULT NULL, `termination_reason` varchar(255) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_service_order_item` (`tenant_id`,`order_item_id`,`deleted`),
  KEY `idx_service_owner_person` (`tenant_id`,`owner_user_id`,`person_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员课程服务关系';

CREATE TABLE IF NOT EXISTS `zsjos_registration_command` (
  `id` bigint NOT NULL AUTO_INCREMENT, `registration_case_id` bigint NOT NULL, `command_type` varchar(32) NOT NULL,
  `idempotency_key` varchar(64) NOT NULL, `request_fingerprint` varchar(255) NOT NULL, `operator_user_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_command_key` (`tenant_id`,`idempotency_key`,`deleted`),
  KEY `idx_registration_command_case` (`tenant_id`,`registration_case_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约命令幂等与操作审计';

INSERT INTO `zsjos_registration_checklist_template`
(`name`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '报名履约默认清单',0,'migration-V073',NOW(),'migration-V073',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_registration_checklist_template` x WHERE x.tenant_id=t.id AND x.deleted=b'0');

INSERT INTO `zsjos_registration_checklist_version`
(`template_id`,`version_no`,`status`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.id,1,'published',NOW(),'migration-V073',NOW(),'migration-V073',NOW(),b'0',x.tenant_id
FROM `zsjos_registration_checklist_template` x
WHERE x.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `zsjos_registration_checklist_version` v
  WHERE v.tenant_id=x.tenant_id AND v.template_id=x.id AND v.version_no=1 AND v.deleted=b'0');

INSERT INTO `zsjos_registration_checklist_template_item`
(`version_id`,`item_key`,`item_type`,`title`,`sort`,`enabled`,`system_required`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT v.id,d.item_key,d.item_type,d.title,d.sort,b'1',d.system_required,'migration-V073',NOW(),'migration-V073',NOW(),b'0',v.tenant_id
FROM `zsjos_registration_checklist_version` v JOIN (
  SELECT 'student_join_wechat_group' item_key,'checkbox' item_type,'学员进入微信群' title,10 sort,b'0' system_required UNION ALL
  SELECT 'study_planner','study_planner','配置学习规划师',20,b'1' UNION ALL
  SELECT 'planner_join_wechat_group','checkbox','将学习规划师拉入微信群',30,b'0' UNION ALL
  SELECT 'contract_signed','checkbox','完成合同签署',40,b'0' UNION ALL
  SELECT 'wechat_notice_sent','checkbox','在微信群告知学员注意事项',50,b'0'
) d WHERE v.version_no=1 AND v.status='published' AND v.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_registration_checklist_template_item` i
    WHERE i.tenant_id=v.tenant_id AND i.version_id=v.id AND i.item_key=d.item_key AND i.deleted=b'0');

UPDATE `zsjos_registration_checklist_template` t JOIN `zsjos_registration_checklist_version` v
  ON v.template_id=t.id AND v.tenant_id=t.tenant_id AND v.version_no=1 AND v.deleted=b'0'
SET t.published_version_id=v.id,t.updater='migration-V073',t.update_time=NOW()
WHERE t.deleted=b'0' AND t.published_version_id IS NULL;

SET @v073_workbench_menu_id := (SELECT `id` FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `deleted`=b'0' ORDER BY `id` LIMIT 1);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(73000,'报名履约公共池','zsjos:registration:query-pool',2,60,@v073_workbench_menu_id,'/zsjos/registration-pool','ant-design:unordered-list-outlined','zsjos/registration-pool','ZsjosRegistrationPool',0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73001,'更新报名履约清单','zsjos:registration:update',3,1,73000,'','','',NULL,0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73002,'完成报名履约','zsjos:registration:complete',3,2,73000,'','','',NULL,0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73010,'履约清单配置','zsjos:registration-checklist-config:query',2,61,@v073_workbench_menu_id,'/zsjos/registration-checklist-config','ant-design:profile-outlined','zsjos/registrationChecklistConfig/index','ZsjosRegistrationChecklistConfig',0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73011,'更新履约清单配置','zsjos:registration-checklist-config:update',3,1,73010,'','','',NULL,0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73012,'发布履约清单配置','zsjos:registration-checklist-config:publish',3,2,73010,'','','',NULL,0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0'),
(73020,'我的学员','zsjos:student:query-my',2,62,@v073_workbench_menu_id,'/zsjos/my-students','ant-design:team-outlined','zsjos/my-students','ZsjosMyStudents',0,b'1',b'1',b'0','migration-V073',NOW(),'migration-V073',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`path`=VALUES(`path`),
 `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`deleted`=b'0',`update_time`=NOW();

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V073',NOW(),'migration-V073',NOW(),b'0',r.tenant_id
FROM `system_role` r JOIN `system_menu` m ON m.deleted=b'0'
WHERE r.deleted=b'0' AND ((r.code='system_administrator' AND m.id IN (73010,73011,73012))
  OR (r.code='study_planner' AND m.id=73020))
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm WHERE rm.role_id=r.id AND rm.menu_id=m.id
    AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V073','Registration fulfillment and student relationships','registration-fulfillment-students-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
