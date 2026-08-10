 -- Adds append-only lead follow-ups and the employee today-task entry.
-- Dependencies: V007, zsjos_lead, zsjos_business_task, Infra file metadata, system dictionaries and menus.
-- Execution order: schema, deterministic assignment-history backfill, dictionaries, menus/grants, version record.
-- Repeatability: IF NOT EXISTS and NOT EXISTS guards make the script repeatable.
-- Data scope: schema metadata, approved follow-up dictionaries, menu grants, and determinable current assignment IDs.
-- Recovery: forward-only; no lead records, tasks, files, roles, or permissions are deleted.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='current_assignment_history_id'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `current_assignment_history_id` bigint DEFAULT NULL COMMENT ''当前归属周期分配历史编号'' AFTER `owner_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='current_assignment_first_follow_up_at'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `current_assignment_first_follow_up_at` datetime DEFAULT NULL COMMENT ''当前归属周期首次跟进时间'' AFTER `current_assignment_history_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='last_follow_up_at'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `last_follow_up_at` datetime DEFAULT NULL COMMENT ''最近跟进时间'' AFTER `current_assignment_first_follow_up_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='last_follow_up_record_id'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `last_follow_up_record_id` bigint DEFAULT NULL COMMENT ''最近跟进记录编号'' AFTER `last_follow_up_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='next_follow_up_at'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `next_follow_up_at` datetime DEFAULT NULL COMMENT ''下次跟进时间'' AFTER `last_follow_up_record_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
            AND column_name='follow_up_count'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `follow_up_count` int NOT NULL DEFAULT 0 COMMENT ''跟进次数'' AFTER `next_follow_up_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_lead_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL,
  `assignment_history_id` bigint NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `owner_user_id_snapshot` bigint NOT NULL,
  `owner_dept_id_snapshot` bigint DEFAULT NULL,
  `method_value` varchar(100) NOT NULL,
  `method_label_snapshot` varchar(100) NOT NULL,
  `result_value` varchar(100) NOT NULL,
  `result_label_snapshot` varchar(100) NOT NULL,
  `category_before` varchar(100) DEFAULT NULL,
  `category_before_label_snapshot` varchar(100) DEFAULT NULL,
  `category_after` varchar(100) NOT NULL,
  `category_after_label_snapshot` varchar(100) NOT NULL,
  `remark` varchar(2000) DEFAULT NULL,
  `next_follow_up_at` datetime DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `first_in_assignment` bit(1) NOT NULL DEFAULT b'0',
  `idempotency_key` varchar(64) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_lead_occurred` (`tenant_id`,`lead_id`,`occurred_at`,`id`),
  KEY `idx_tenant_assignment` (`tenant_id`,`assignment_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客资只追加跟进记录';

CREATE TABLE IF NOT EXISTS `zsjos_lead_follow_up_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follow_up_record_id` bigint NOT NULL,
  `infra_file_id` bigint NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `content_type` varchar(100) NOT NULL,
  `file_size` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_record_file` (`tenant_id`,`follow_up_record_id`,`infra_file_id`),
  KEY `idx_tenant_record_sort` (`tenant_id`,`follow_up_record_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客资跟进图片快照';

UPDATE `zsjos_lead` lead_row
JOIN (
  SELECT history.tenant_id, history.lead_id, MAX(history.id) history_id
  FROM `zsjos_lead_assignment_history` history
  JOIN `zsjos_lead` owned ON owned.id=history.lead_id AND owned.tenant_id=history.tenant_id
    AND owned.assignment_status='owned' AND owned.owner_user_id=history.to_owner_user_id AND owned.deleted=b'0'
  WHERE history.action_type IN ('accept','claim','transfer') AND history.deleted=b'0'
  GROUP BY history.tenant_id, history.lead_id
) resolved ON resolved.tenant_id=lead_row.tenant_id AND resolved.lead_id=lead_row.id
SET lead_row.current_assignment_history_id=resolved.history_id
WHERE lead_row.current_assignment_history_id IS NULL;

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资跟进方式','zsjos_lead_follow_up_method',0,'客资判定前跟进方式','migration-V008',NOW(),'migration-V008',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_follow_up_method' AND `deleted`=b'0');
INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资跟进结果','zsjos_lead_follow_up_result',0,'客资判定前跟进结果','migration-V008',NOW(),'migration-V008',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_follow_up_result' AND `deleted`=b'0');
INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资跟进快捷备注','zsjos_lead_follow_up_quick_note',0,'管理员维护；初始化不提供选项','migration-V008',NOW(),'migration-V008',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_follow_up_quick_note' AND `deleted`=b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.sort,seed.label,seed.value,seed.dict_type,0,'migration-V008',NOW(),'migration-V008',NOW(),b'0'
FROM (
  SELECT 1 sort,'电话' label,'phone' value,'zsjos_lead_follow_up_method' dict_type UNION ALL
  SELECT 2,'微信','wechat','zsjos_lead_follow_up_method' UNION ALL SELECT 3,'短信','sms','zsjos_lead_follow_up_method' UNION ALL
  SELECT 4,'上门','visit','zsjos_lead_follow_up_method' UNION ALL SELECT 5,'其他','other','zsjos_lead_follow_up_method' UNION ALL
  SELECT 1,'已联系有意向','interested','zsjos_lead_follow_up_result' UNION ALL SELECT 2,'已联系待考虑','considering','zsjos_lead_follow_up_result' UNION ALL
  SELECT 3,'已约下次跟进','scheduled_follow_up','zsjos_lead_follow_up_result' UNION ALL SELECT 4,'未联系上','unreachable','zsjos_lead_follow_up_result' UNION ALL
  SELECT 5,'明确无意向','not_interested','zsjos_lead_follow_up_result' UNION ALL SELECT 6,'其他','other','zsjos_lead_follow_up_result'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` existing WHERE existing.dict_type=seed.dict_type AND existing.value=seed.value AND existing.deleted=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6780,'今日待办','zsjos:business-task:query',2,12,6735,'tasks/today','ep:list','zsjos-workbench','TodayTasksPage',0,b'1',b'1',b'1','migration-V008',NOW(),'migration-V008',NOW(),b'0'),
(6781,'查询客资跟进','zsjos:lead-follow-up:query',3,10,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V008',NOW(),'migration-V008',NOW(),b'0'),
(6782,'新增客资跟进','zsjos:lead-follow-up:create',3,11,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V008',NOW(),'migration-V008',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6781,'migration-V008',NOW(),'migration-V008',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead:query' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6781 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6780,'migration-V008',NOW(),'migration-V008',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id
  AND m.permission IN ('zsjos:lead:submit','zsjos:lead:query-submitted','zsjos:lead:query-owned','zsjos:lead:claim','zsjos:lead:accept') AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6780 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,target.menu_id,'migration-V008',NOW(),'migration-V008',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission IN ('zsjos:lead:claim','zsjos:lead:accept') AND m.deleted=b'0'
CROSS JOIN (SELECT 6781 menu_id UNION ALL SELECT 6782) target
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=target.menu_id AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V008','Add lead follow-up records and today tasks','lead-follow-up-today-tasks-v1');

-- Manual verification list only: these rows need assignment-history review; no records or tasks are fabricated.
SELECT `tenant_id`,`id` AS `lead_id`,`owner_user_id`
FROM `zsjos_lead`
WHERE `assignment_status`='owned' AND `current_assignment_history_id` IS NULL AND `deleted`=b'0'
ORDER BY `tenant_id`,`id`;
