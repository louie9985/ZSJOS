-- V083: configurable registration routes, assignees and checklist attachments.
-- Dependencies/order: apply after V082 and before V084; requires V073 plus System department/role/post/user and Infra file schemas.
-- Data scope: additive structures, exact-name default route mappings, active-case snapshots, and content-director My Students grants.
-- Repeatability/recovery: schema and natural-key guards allow reruns. This is forward-only; disable rows rather than deleting snapshots.
-- Department names are resolved exactly once only when unique; runtime code uses the persisted System department ID.

SET @v083_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_registration_checklist_template_item' AND column_name='attachment_required')=0,
 'ALTER TABLE `zsjos_registration_checklist_template_item` ADD COLUMN `attachment_required` bit(1) NOT NULL DEFAULT b''0'' AFTER `system_required`','SELECT 1');
PREPARE v083_stmt FROM @v083_sql; EXECUTE v083_stmt; DEALLOCATE PREPARE v083_stmt;
SET @v083_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_registration_case_checklist_item' AND column_name='attachment_required')=0,
 'ALTER TABLE `zsjos_registration_case_checklist_item` ADD COLUMN `attachment_required` bit(1) NOT NULL DEFAULT b''0'' AFTER `checked`','SELECT 1');
PREPARE v083_stmt FROM @v083_sql; EXECUTE v083_stmt; DEALLOCATE PREPARE v083_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_registration_route_option` (
 `id` bigint NOT NULL AUTO_INCREMENT, `version_id` bigint NOT NULL, `option_key` varchar(64) NOT NULL,
 `department_id` bigint NOT NULL, `department_name_snapshot` varchar(100) NOT NULL, `assignee_type` varchar(32) NOT NULL,
 `sort` int NOT NULL, `enabled` bit(1) NOT NULL DEFAULT b'1', `system_required` bit(1) NOT NULL DEFAULT b'0',
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_version_route` (`tenant_id`,`version_id`,`option_key`,`deleted`),
 KEY `idx_registration_route_department` (`tenant_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约版本化流转部门配置';

CREATE TABLE IF NOT EXISTS `zsjos_registration_case_route` (
 `id` bigint NOT NULL AUTO_INCREMENT, `registration_case_id` bigint NOT NULL, `route_option_id` bigint NOT NULL,
 `option_key` varchar(64) NOT NULL, `department_id` bigint NOT NULL, `department_name_snapshot` varchar(100) NOT NULL,
 `assignee_type` varchar(32) NOT NULL, `selected` bit(1) NOT NULL DEFAULT b'0', `assignee_user_id` bigint DEFAULT NULL,
 `assignee_name_snapshot` varchar(100) DEFAULT NULL, `sort` int NOT NULL, `version` int NOT NULL DEFAULT 0,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_registration_case_route` (`tenant_id`,`registration_case_id`,`route_option_id`,`deleted`),
 KEY `idx_registration_route_assignee` (`tenant_id`,`assignee_user_id`,`selected`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约流转部门及负责人快照';

CREATE TABLE IF NOT EXISTS `zsjos_registration_item_attachment` (
 `id` bigint NOT NULL AUTO_INCREMENT, `registration_case_id` bigint NOT NULL, `checklist_item_id` bigint NOT NULL,
 `infra_file_id` bigint NOT NULL, `file_url` varchar(1024) NOT NULL, `original_name` varchar(255) NOT NULL,
 `content_type` varchar(128) DEFAULT NULL, `file_size` bigint NOT NULL, `uploaded_by_user_id` bigint NOT NULL,
 `uploaded_at` datetime NOT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
 PRIMARY KEY (`id`), KEY `idx_registration_attachment_item` (`tenant_id`,`checklist_item_id`,`uploaded_at`),
 KEY `idx_registration_attachment_case` (`tenant_id`,`registration_case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名履约清单附件';

INSERT INTO `zsjos_registration_route_option`
(`version_id`,`option_key`,`department_id`,`department_name_snapshot`,`assignee_type`,`sort`,`enabled`,`system_required`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT version_row.id,default_row.option_key,department_row.id,department_row.name,default_row.assignee_type,
 default_row.sort,b'1',b'1','migration-V083',NOW(),'migration-V083',NOW(),b'0',version_row.tenant_id
FROM `zsjos_registration_checklist_version` version_row
JOIN (SELECT 'student_delivery' option_key,'学生服务与交付中心' department_name,'study_planner' assignee_type,10 sort
      UNION ALL SELECT 'new_media','新媒体与客资中心','content_director',20) default_row
JOIN (SELECT tenant_id,name,MIN(id) id FROM `system_dept`
      WHERE deleted=b'0' AND status=0 AND name IN ('学生服务与交付中心','新媒体与客资中心')
      GROUP BY tenant_id,name HAVING COUNT(*)=1) department_row
 ON department_row.tenant_id=version_row.tenant_id AND department_row.name=default_row.department_name
WHERE version_row.status IN ('published','draft') AND version_row.deleted=b'0'
 AND NOT EXISTS (SELECT 1 FROM `zsjos_registration_route_option` existing_row
   WHERE existing_row.tenant_id=version_row.tenant_id AND existing_row.version_id=version_row.id
    AND existing_row.option_key=default_row.option_key AND existing_row.deleted=b'0');

INSERT INTO `zsjos_registration_case_route`
(`registration_case_id`,`route_option_id`,`option_key`,`department_id`,`department_name_snapshot`,`assignee_type`,
 `selected`,`assignee_user_id`,`assignee_name_snapshot`,`sort`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT case_row.id,option_row.id,option_row.option_key,option_row.department_id,option_row.department_name_snapshot,
 option_row.assignee_type,b'0',NULL,NULL,option_row.sort,0,'migration-V083',NOW(),'migration-V083',NOW(),b'0',case_row.tenant_id
FROM `zsjos_registration_case` case_row JOIN `zsjos_registration_route_option` option_row
 ON option_row.tenant_id=case_row.tenant_id AND option_row.version_id=case_row.checklist_version_id
 AND option_row.enabled=b'1' AND option_row.deleted=b'0'
WHERE case_row.status IN ('pending','processing') AND case_row.deleted=b'0'
 AND NOT EXISTS (SELECT 1 FROM `zsjos_registration_case_route` existing_row
   WHERE existing_row.tenant_id=case_row.tenant_id AND existing_row.registration_case_id=case_row.id
    AND existing_row.route_option_id=option_row.id AND existing_row.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id,menu_row.id,'migration-V083',NOW(),'migration-V083',NOW(),b'0',role_row.tenant_id
FROM `system_role` role_row JOIN `system_menu` menu_row ON menu_row.id=73020 AND menu_row.deleted=b'0'
WHERE role_row.code='content_director' AND role_row.status=0 AND role_row.deleted=b'0'
 AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing_row WHERE existing_row.role_id=role_row.id
  AND existing_row.menu_id=menu_row.id AND existing_row.tenant_id=role_row.tenant_id AND existing_row.deleted=b'0');

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '编导学员分配','ZSJOS_REGISTRATION_DIRECTOR_ASSIGNED','中世健消息中心','zsjos.registration.director_assigned',
 '学员已分配给你','学员{{student.name}}（{{lead.no}}）已分配给你。',
 '学员{{student.name}}（{{lead.no}}）已分配给你。',2,
 '["registration.caseId","student.name","lead.no"]',0,'V083 编导学员分配通知',
 'migration-V083',NOW(),'migration-V083',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
 WHERE `code`='ZSJOS_REGISTRATION_DIRECTOR_ASSIGNED' AND `deleted`=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '编导学员分配','zsjos.registration.director_assigned','in_app',template_row.id,
 '["content_director"]','[]','business_detail',0,'migration-V083',NOW(),'migration-V083',NOW(),b'0',tenant_row.id
FROM `system_tenant` tenant_row JOIN `system_notify_template` template_row
 ON template_row.code='ZSJOS_REGISTRATION_DIRECTOR_ASSIGNED' AND template_row.deleted=b'0'
WHERE tenant_row.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
 WHERE rule_row.tenant_id=tenant_row.id AND rule_row.scene_code='zsjos.registration.director_assigned'
  AND rule_row.creator='migration-V083' AND rule_row.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V083','Registration routes and checklist attachments','registration-routes-attachments-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
