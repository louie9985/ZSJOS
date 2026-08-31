-- Adds the overdue collaboration pool without changing the existing claim pool.
-- Dependencies: V032, ZSJOS lead/opportunity/order/assignment history, System users/departments/posts,
-- notification rules and tenant metadata.
-- Execution order: add columns, create cycle/event/reminder tables, recover ownership start times from
-- explicit assignment history, register menus and permissions, seed notification templates/rules, record V034.
-- Repeatability: guarded DDL, stable menu/template IDs, unique business keys and NOT EXISTS checks.
-- Data scope: schema/configuration metadata plus deterministic ownership-start recovery. No lead owner,
-- order, opportunity, follow-up, account or permission rows are deleted or bulk replaced.
-- Recovery: forward-only. Disable menus, notification rules and the scheduler to stop new cycles; retain
-- cycle/event/message history. Columns and audit tables are not dropped after use.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND column_name='ownership_started_at'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `ownership_started_at` datetime DEFAULT NULL COMMENT ''当前销售正式持有起点'' AFTER `owner_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND index_name='idx_tenant_aging_pool_scan'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD KEY `idx_tenant_aging_pool_scan` (`tenant_id`,`assignment_status`,`status`,`ownership_started_at`,`deleted`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_follow_up_rule' AND column_name='aging_pool_timeout_days'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `aging_pool_timeout_days` int NOT NULL DEFAULT 90 COMMENT ''超期协同公海期限（自然日）'' AFTER `qualification_timeout_minutes`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_lead_aging_pool_cycle` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '周期编号', `lead_id` bigint NOT NULL COMMENT '客资编号',
  `cycle_no` int NOT NULL COMMENT '客资周期序号', `original_owner_user_id` bigint NOT NULL COMMENT '原归属销售A',
  `collaborator_user_id` bigint DEFAULT NULL COMMENT '协同销售B', `frozen_dept_id` bigint NOT NULL COMMENT '入池冻结部门',
  `status` varchar(32) NOT NULL COMMENT 'waiting_assignment/assigned/deal_pending/exited/converted',
  `ownership_started_at` datetime NOT NULL COMMENT '本轮持有起点', `due_at` datetime NOT NULL COMMENT '应进入公海时间',
  `entered_at` datetime NOT NULL COMMENT '实际进入时间', `assigned_at` datetime DEFAULT NULL COMMENT '最近指派时间',
  `exited_at` datetime DEFAULT NULL COMMENT '退出时间', `converted_at` datetime DEFAULT NULL COMMENT '成交转归时间',
  `exit_reason` varchar(500) DEFAULT NULL COMMENT '退出原因', `idempotency_key` varchar(64) NOT NULL COMMENT '入池幂等键',
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_lead_cycle` (`tenant_id`,`lead_id`,`cycle_no`),
  UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_dept_status_entered` (`tenant_id`,`frozen_dept_id`,`status`,`entered_at`,`id`),
  KEY `idx_tenant_lead_status` (`tenant_id`,`lead_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资超期协同公海周期';

CREATE TABLE IF NOT EXISTS `zsjos_lead_aging_pool_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件编号', `cycle_id` bigint NOT NULL COMMENT '周期编号',
  `lead_id` bigint NOT NULL COMMENT '客资编号', `event_type` varchar(32) NOT NULL COMMENT '事件类型',
  `operator_user_id` bigint DEFAULT NULL COMMENT '操作人', `previous_collaborator_user_id` bigint DEFAULT NULL COMMENT '原协同销售',
  `collaborator_user_id` bigint DEFAULT NULL COMMENT '新协同销售', `reason` varchar(500) DEFAULT NULL COMMENT '原因',
  `idempotency_key` varchar(128) NOT NULL COMMENT '事件幂等键', `occurred_at` datetime NOT NULL COMMENT '发生时间',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_cycle_time` (`tenant_id`,`cycle_id`,`occurred_at`,`id`),
  KEY `idx_tenant_lead_time` (`tenant_id`,`lead_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资超期协同公海事件';

CREATE TABLE IF NOT EXISTS `zsjos_lead_aging_pool_notify_stage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发送阶段编号', `lead_id` bigint NOT NULL COMMENT '客资编号',
  `cycle_no` int NOT NULL COMMENT '预期周期序号', `notify_rule_id` bigint NOT NULL COMMENT 'System通知规则编号',
  `stage` varchar(16) NOT NULL COMMENT '仅advance', `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/failed',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '投递尝试次数', `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
  `last_error_code` varchar(64) DEFAULT NULL COMMENT '最后错误编码', `sent_at` datetime DEFAULT NULL COMMENT '确认发送时间',
  `emitted_at` datetime NOT NULL COMMENT '首次创建时间',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_lead_cycle_rule` (`tenant_id`,`lead_id`,`cycle_no`,`notify_rule_id`),
  KEY `idx_tenant_status_retry` (`tenant_id`,`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 超期公海提前通知幂等阶段';

SET @aging_pool_filter = '{"groups":[{"key":"all","label":"全部公海客资","sort":0,"enabled":true,"sectionLabel":"公海状态","conditions":[],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"waiting_assignment","label":"待指派","sort":10,"enabled":true,"conditions":[{"field":"pool_status","values":["waiting_assignment"]}]},{"key":"assigned","label":"协同跟进中","sort":20,"enabled":true,"conditions":[{"field":"pool_status","values":["assigned"]}]},{"key":"deal_pending","label":"成交审批中","sort":30,"enabled":true,"conditions":[{"field":"pool_status","values":["deal_pending"]}]}]}]}';

INSERT INTO `zsjos_lead_inbox_filter_scheme`
(`audience`,`name`,`draft_config_json`,`published_config_json`,`published_version`,`published_by`,`published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'agingPool','超期公海视角',@aging_pool_filter,@aging_pool_filter,1,0,NOW(),0,
       'migration-V034',NOW(),'migration-V034',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_inbox_filter_scheme` s
  WHERE s.tenant_id=t.id AND s.audience='agingPool' AND s.deleted=b'0');

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`,`version_no`,`config_json`,`published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT s.id,1,s.published_config_json,COALESCE(s.published_by,0),COALESCE(s.published_at,NOW()),
       'migration-V034',NOW(),'migration-V034',NOW(),b'0',s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s WHERE s.audience='agingPool' AND s.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_inbox_filter_version` v
  WHERE v.tenant_id=s.tenant_id AND v.scheme_id=s.id AND v.version_no=1 AND v.deleted=b'0');

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_order' AND column_name='supersedes_order_id'), 'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD COLUMN `supersedes_order_id` bigint DEFAULT NULL COMMENT ''接续的原订单编号'' AFTER `current_approval_round_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_order' AND column_name='superseded_by_order_id'), 'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD COLUMN `superseded_by_order_id` bigint DEFAULT NULL COMMENT ''接续后的新订单编号'' AFTER `supersedes_order_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_order' AND index_name='uk_tenant_opportunity'),
  'ALTER TABLE `zsjos_order` DROP INDEX `uk_tenant_opportunity`', 'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_order' AND index_name='uk_tenant_supersedes_order'), 'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD UNIQUE KEY `uk_tenant_supersedes_order` (`tenant_id`,`supersedes_order_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_order' AND index_name='idx_tenant_opportunity'), 'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD KEY `idx_tenant_opportunity` (`tenant_id`,`opportunity_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_lead` l
JOIN (
  SELECT h.tenant_id,h.lead_id,MAX(h.occurred_at) ownership_started_at
  FROM `zsjos_lead_assignment_history` h
  WHERE h.deleted=b'0' AND h.action_type IN ('accept','claim','transfer') AND h.to_owner_user_id IS NOT NULL
  GROUP BY h.tenant_id,h.lead_id
) recovered ON recovered.tenant_id=l.tenant_id AND recovered.lead_id=l.id
SET l.ownership_started_at=recovered.ownership_started_at,
    l.updater='migration-V034',l.update_time=NOW()
WHERE l.deleted=b'0' AND l.owner_user_id IS NOT NULL AND l.ownership_started_at IS NULL;

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6794,'超期公海','zsjos:lead-aging-pool:query',2,88,6735,'lead-aging-pool','ep:management','zsjos/leadAgingPool/index','ZsjosLeadAgingPool',0,b'1',b'1',b'1','migration-V034',NOW(),'migration-V034',NOW(),b'0'),
(6795,'管理部门超期公海','zsjos:lead-aging-pool:manage',3,1,6794,'','','',NULL,0,b'1',b'1',b'1','migration-V034',NOW(),'migration-V034',NOW(),b'0'),
(6796,'管理全部超期公海','zsjos:lead-aging-pool:manage-all',3,2,6794,'','','',NULL,0,b'1',b'1',b'1','migration-V034',NOW(),'migration-V034',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT grant_row.role_id, menu.id, 'migration-V034',NOW(),'migration-V034',NOW(),b'0',grant_row.tenant_id
FROM `system_role_menu` grant_row
JOIN `system_menu` source_menu ON source_menu.id=grant_row.menu_id AND source_menu.permission='zsjos:lead:query-owned' AND source_menu.deleted=b'0'
JOIN `system_menu` menu ON menu.permission='zsjos:lead-aging-pool:query' AND menu.deleted=b'0'
WHERE grant_row.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.tenant_id=grant_row.tenant_id
  AND existing.role_id=grant_row.role_id AND existing.menu_id=menu.id AND existing.deleted=b'0');

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,x.params,0,'V034 超期协同公海模板','migration-V034',NOW(),'migration-V034',NOW(),b'0'
FROM (
 SELECT '超期公海提前提醒' name,'ZSJOS_AGING_POOL_REMINDER' code,'zsjos.lead.aging_pool_reminder' scene_code,'客资即将进入超期公海' title,'{{lead.name}}将在{{agingPool.dueAt}}进入超期公海' summary,'客资{{lead.name}}将在{{agingPool.dueAt}}进入超期公海，请及时推进成交。' content,'["lead.name","agingPool.dueAt"]' params
 UNION ALL SELECT '超期公海到期','ZSJOS_AGING_POOL_DUE','zsjos.lead.aging_pool_due','客资已进入超期公海','{{lead.name}}已进入超期公海','客资{{lead.name}}已到期进入超期公海，等待主管指派协同销售。','["lead.name"]'
 UNION ALL SELECT '超期公海指派','ZSJOS_AGING_POOL_ASSIGNED','zsjos.lead.aging_pool_assigned','超期公海协同指派','{{lead.name}}已指派协同销售','客资{{lead.name}}已完成协同销售指派。','["lead.name"]'
 UNION ALL SELECT '超期公海换派','ZSJOS_AGING_POOL_REASSIGNED','zsjos.lead.aging_pool_reassigned','超期公海协同换派','{{lead.name}}已更换协同销售','客资{{lead.name}}的协同销售已变更。','["lead.name"]'
 UNION ALL SELECT '超期公海待重派','ZSJOS_AGING_POOL_REASSIGN_REQUIRED','zsjos.lead.aging_pool_reassign_required','超期公海待重新指派','{{lead.name}}需要重新指派协同销售','客资{{lead.name}}的原协同销售已失效，请主管重新指派。','["lead.name"]'
 UNION ALL SELECT '超期公海退出','ZSJOS_AGING_POOL_EXITED','zsjos.lead.aging_pool_exited','客资退出超期公海','{{lead.name}}已退出超期公海','客资{{lead.name}}已由主管退出超期公海，恢复原销售独占推进。','["lead.name"]'
) x WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`timing_stage`,`timing_offset_minutes`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '超期公海-提前7天','zsjos.lead.aging_pool_reminder','in_app',t.id,'["owner","frozen_dept_leader"]','[]','business_detail','advance',10080,0,'migration-V034',NOW(),'migration-V034',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_AGING_POOL_REMINDER' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.name='超期公海-提前7天' AND r.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',0,'migration-V034',NOW(),'migration-V034',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '超期公海到期通知' name,'zsjos.lead.aging_pool_due' scene_code,'["owner","frozen_dept_leader"]' roles,'ZSJOS_AGING_POOL_DUE' template_code
 UNION ALL SELECT '超期公海指派通知','zsjos.lead.aging_pool_assigned','["owner","collaborator","frozen_dept_leader"]','ZSJOS_AGING_POOL_ASSIGNED'
 UNION ALL SELECT '超期公海换派通知','zsjos.lead.aging_pool_reassigned','["owner","previous_collaborator","collaborator","frozen_dept_leader"]','ZSJOS_AGING_POOL_REASSIGNED'
 UNION ALL SELECT '超期公海待重派通知','zsjos.lead.aging_pool_reassign_required','["owner","previous_collaborator","frozen_dept_leader"]','ZSJOS_AGING_POOL_REASSIGN_REQUIRED'
 UNION ALL SELECT '超期公海退出通知','zsjos.lead.aging_pool_exited','["owner","previous_collaborator","frozen_dept_leader"]','ZSJOS_AGING_POOL_EXITED'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
SELECT 'V034','lead aging collaboration pool','V034__lead_aging_collaboration_pool.sql'
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V034');

-- Read-only release check: rows returned here require manual ownership-start review before they can age into the pool.
SELECT l.tenant_id,l.id lead_id,l.owner_user_id
FROM zsjos_lead l WHERE l.deleted=b'0' AND l.owner_user_id IS NOT NULL
  AND l.assignment_status='owned' AND l.status IN ('valid','converted') AND l.ownership_started_at IS NULL;
