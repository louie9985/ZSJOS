-- Adds lead qualification timing, suspension/recycle states and permissions.
-- Dependencies: V013, lead follow-up rules/tasks/events, system dictionaries and menus.
-- Execution order: schema columns, rule defaults, dictionaries, menu definitions, version record.
-- Repeatability: information_schema guards and NOT EXISTS checks make re-execution safe.
-- Data scope: schema/configuration/permission metadata only. Existing lead state is not rewritten.
-- Recovery: forward-only. Disable permissions or adjust rules; retain task/event/assignment history.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='current_assignment_first_follow_up_deadline_at'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `current_assignment_first_follow_up_deadline_at` datetime DEFAULT NULL COMMENT ''当前归属周期首次跟进截止时间'' AFTER `current_assignment_first_follow_up_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualification_round_no'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualification_round_no` int NOT NULL DEFAULT 0 COMMENT ''有效性判定轮次'' AFTER `follow_up_count`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualification_started_at'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualification_started_at` datetime DEFAULT NULL COMMENT ''当前判定轮次开始时间'' AFTER `qualification_round_no`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualification_deadline_at'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualification_deadline_at` datetime DEFAULT NULL COMMENT ''当前判定轮次截止时间'' AFTER `qualification_started_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualification_rule_snapshot'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualification_rule_snapshot` json DEFAULT NULL COMMENT ''当前判定规则快照'' AFTER `qualification_deadline_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='suspended_at'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `suspended_at` datetime DEFAULT NULL COMMENT ''判定超时挂起时间'' AFTER `qualification_rule_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualified_by_user_id'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualified_by_user_id` bigint DEFAULT NULL COMMENT ''有效性判定人'' AFTER `suspended_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='qualified_at'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `qualified_at` datetime DEFAULT NULL COMMENT ''有效性判定时间'' AFTER `qualified_by_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='invalid_reason_label_snapshot'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `invalid_reason_label_snapshot` varchar(100) DEFAULT NULL COMMENT ''无效原因标签快照'' AFTER `invalid_reason`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='invalid_description'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `invalid_description` varchar(2000) DEFAULT NULL COMMENT ''无效判定说明'' AFTER `invalid_reason_label_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='recycle_source_owner_user_id'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `recycle_source_owner_user_id` bigint DEFAULT NULL COMMENT ''回收前销售，用于主管对象范围'' AFTER `owner_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='idx_tenant_qualification_deadline'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD KEY `idx_tenant_qualification_deadline` (`tenant_id`,`status`,`assignment_status`,`qualification_deadline_at`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='idx_tenant_recycle_source'), 'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD KEY `idx_tenant_recycle_source` (`tenant_id`,`assignment_status`,`recycle_source_owner_user_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_rule' AND column_name='qualification_timeout_minutes'), 'SELECT 1', 'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `qualification_timeout_minutes` int NOT NULL DEFAULT 4320 COMMENT ''首次跟进后有效性判定时限（分钟）'' AFTER `first_follow_up_timeout_minutes`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资主状态','zsjos_lead_status',0,'ZSJOS 客资生命周期协议状态','migration-V014',NOW(),'migration-V014',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_status' AND `deleted`=b'0');
INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资分配状态','zsjos_lead_assignment_status',0,'ZSJOS 客资归属协议状态','migration-V014',NOW(),'migration-V014',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_assignment_status' AND `deleted`=b'0');
INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资无效原因','zsjos_lead_invalid_reason',0,'管理员维护；初始化不提供业务选项','migration-V014',NOW(),'migration-V014',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_invalid_reason' AND `deleted`=b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.sort,seed.label,seed.value,seed.dict_type,0,seed.color_type,'migration-V014',NOW(),'migration-V014',NOW(),b'0'
FROM (
  SELECT 10 sort,'已提交' label,'submitted' value,'zsjos_lead_status' dict_type,'info' color_type UNION ALL
  SELECT 20,'已挂起','suspended','zsjos_lead_status','warning' UNION ALL
  SELECT 30,'有效','valid','zsjos_lead_status','success' UNION ALL
  SELECT 40,'无效','invalid','zsjos_lead_status','danger' UNION ALL
  SELECT 50,'已转换','converted','zsjos_lead_status','primary' UNION ALL
  SELECT 60,'已关闭','closed','zsjos_lead_status','default' UNION ALL
  SELECT 10,'未分配','unassigned','zsjos_lead_assignment_status','default' UNION ALL
  SELECT 20,'待接单','pending_acceptance','zsjos_lead_assignment_status','warning' UNION ALL
  SELECT 30,'已归属','owned','zsjos_lead_assignment_status','success' UNION ALL
  SELECT 40,'抢单池','public_pool','zsjos_lead_assignment_status','info' UNION ALL
  SELECT 45,'回收待处理','recycle_pending','zsjos_lead_assignment_status','warning' UNION ALL
  SELECT 50,'已结束','closed','zsjos_lead_assignment_status','default'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` existing WHERE existing.dict_type=seed.dict_type AND existing.value=seed.value AND existing.deleted=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6800,'异常客资','zsjos:lead:qualification:query',2,17,6735,'leads/qualification-exceptions','ep:warning-filled','zsjos/leadQualification/index','ZsjosLeadQualification',0,b'1',b'1',b'1','migration-V014',NOW(),'migration-V014',NOW(),b'0'),
(6801,'判定客资有效性','zsjos:lead:qualify',3,12,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V014',NOW(),'migration-V014',NOW(),b'0'),
(6802,'处置异常客资','zsjos:lead:qualification:manage',3,1,6800,'','','',NULL,0,b'1',b'1',b'1','migration-V014',NOW(),'migration-V014',NOW(),b'0'),
(6803,'处置全部异常客资','zsjos:lead:qualification:manage-all',3,2,6800,'','','',NULL,0,b'1',b'1',b'1','migration-V014',NOW(),'migration-V014',NOW(),b'0');

-- Permission codes are defined above, but V014 intentionally does not grant them to existing roles.
-- Role assignment must be reviewed and performed through administrator configuration or a separately approved script.

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V014','Add lead qualification timing and suspension workflow','lead-qualification-suspension-v1');

-- Existing rows are intentionally not assigned a new deadline by migration.
SELECT `tenant_id`,`id` AS `lead_id`,`owner_user_id`,`current_assignment_first_follow_up_at`
FROM `zsjos_lead`
WHERE `status`='submitted' AND `assignment_status`='owned'
  AND `current_assignment_first_follow_up_at` IS NOT NULL
  AND `qualification_deadline_at` IS NULL AND `deleted`=b'0'
ORDER BY `tenant_id`,`id`;
