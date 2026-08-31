-- V015 客资三级申诉。
-- Dependencies: V014、System 用户/部门/角色/菜单、BPM 模块表、zsjos_schema_version。
-- Data scope: additive lead/appeal columns, appeal status dictionary, appeal menus and role-menu grants.
-- Repeatability: every DDL/DML statement checks the target before creation; no business rows are deleted or rewritten.
-- Rollback limitation: keep appeal audit rows and disable the menus/process definition; do not drop populated columns.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='invalid_evidence_refs'),
  'SELECT 1', 'ALTER TABLE `zsjos_lead` ADD COLUMN `invalid_evidence_refs` json DEFAULT NULL COMMENT ''无效判定图片引用'' AFTER `invalid_description`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_lead_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申诉编号', `lead_id` bigint NOT NULL COMMENT '客资编号',
  `round_no` int NOT NULL COMMENT '申诉轮次', `review_stage` varchar(32) NOT NULL COMMENT '审核阶段',
  `status` varchar(32) NOT NULL COMMENT '申诉状态', `applicant_user_id` bigint NOT NULL COMMENT '申请人用户编号',
  `reason` varchar(1000) NOT NULL COMMENT '申诉原因', `evidence_refs` json DEFAULT NULL COMMENT '申诉图片引用',
  `invalid_reason_snapshot` varchar(100) DEFAULT NULL COMMENT '无效原因快照',
  `invalid_description_snapshot` varchar(2000) DEFAULT NULL COMMENT '无效说明快照',
  `invalid_evidence_refs_snapshot` json DEFAULT NULL COMMENT '无效判定图片快照',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号',
  `reviewer_user_id` bigint DEFAULT NULL COMMENT '实际处理人', `decision_reason` varchar(1000) DEFAULT NULL COMMENT '裁决意见',
  `decision_evidence_refs` json DEFAULT NULL COMMENT '裁决图片引用', `submitted_at` datetime NOT NULL COMMENT '提交时间',
  `decided_at` datetime DEFAULT NULL COMMENT '裁决时间', `submission_idempotency_key` varchar(100) NOT NULL COMMENT '提交幂等键',
  `decision_idempotency_key` varchar(100) DEFAULT NULL COMMENT '裁决幂等键',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_lead_round` (`tenant_id`,`lead_id`,`round_no`),
  UNIQUE KEY `uk_tenant_appeal_submit_key` (`tenant_id`,`submission_idempotency_key`),
  UNIQUE KEY `uk_tenant_appeal_decision_key` (`tenant_id`,`decision_idempotency_key`),
  KEY `idx_tenant_status` (`tenant_id`,`status`), KEY `idx_tenant_process` (`tenant_id`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS 客资申诉';

-- Existing bootstrap schemas already contain the table; add its new columns individually.
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='review_stage'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `review_stage` varchar(32) NOT NULL DEFAULT ''sales_manager'' COMMENT ''审核阶段'' AFTER `round_no`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='invalid_reason_snapshot'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `invalid_reason_snapshot` varchar(100) DEFAULT NULL COMMENT ''无效原因快照'' AFTER `evidence_refs`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='invalid_description_snapshot'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `invalid_description_snapshot` varchar(2000) DEFAULT NULL COMMENT ''无效说明快照'' AFTER `invalid_reason_snapshot`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='invalid_evidence_refs_snapshot'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `invalid_evidence_refs_snapshot` json DEFAULT NULL COMMENT ''无效判定图片快照'' AFTER `invalid_description_snapshot`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='process_instance_id'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `process_instance_id` varchar(64) DEFAULT NULL COMMENT ''BPM 流程实例编号'' AFTER `invalid_evidence_refs_snapshot`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='decision_evidence_refs'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `decision_evidence_refs` json DEFAULT NULL COMMENT ''裁决图片引用'' AFTER `decision_reason`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='submission_idempotency_key'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `submission_idempotency_key` varchar(100) DEFAULT NULL COMMENT ''提交幂等键'' AFTER `decided_at`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND column_name='decision_idempotency_key'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `decision_idempotency_key` varchar(100) DEFAULT NULL COMMENT ''裁决幂等键'' AFTER `submission_idempotency_key`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Historical rows may keep null idempotency keys; MySQL unique indexes allow multiple nulls.
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_appeal_submit_key'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD UNIQUE KEY `uk_tenant_appeal_submit_key` (`tenant_id`,`submission_idempotency_key`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_appeal_decision_key'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD UNIQUE KEY `uk_tenant_appeal_decision_key` (`tenant_id`,`decision_idempotency_key`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='idx_tenant_process'),'SELECT 1','ALTER TABLE `zsjos_lead_appeal` ADD KEY `idx_tenant_process` (`tenant_id`,`process_instance_id`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资申诉状态','zsjos_lead_appeal_status',0,'客资三级申诉过程状态','migration-V015',NOW(),'migration-V015',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_appeal_status' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.sort,seed.label,seed.value,'zsjos_lead_appeal_status',0,seed.color,'migration-V015',NOW(),'migration-V015',NOW(),b'0' FROM (
 SELECT 10 sort,'销售主管复核中' label,'sales_manager_reviewing' value,'warning' color UNION ALL
 SELECT 20,'质控复核中','quality_reviewing','warning' UNION ALL SELECT 30,'董事长终审中','chairman_reviewing','warning' UNION ALL
 SELECT 40,'已改判有效','overturned','success' UNION ALL SELECT 50,'维持无效','upheld','danger' UNION ALL SELECT 60,'已撤回','withdrawn','default'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.dict_type='zsjos_lead_appeal_status' AND d.value=seed.value AND d.deleted=b'0');

INSERT INTO `system_role` (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '董事长','boss',31,1,'[]',0,2,'客资申诉最终裁决角色','migration-V015',NOW(),'migration-V015',NOW(),b'0',tenants.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `system_role` WHERE code='sales_manager' AND deleted=b'0') tenants
WHERE NOT EXISTS (SELECT 1 FROM `system_role` r WHERE r.tenant_id=tenants.tenant_id AND r.code='boss' AND r.deleted=b'0');

INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6804,'申诉处理','zsjos:lead:appeal:query',2,18,6735,'appeals','ep:message-box','zsjos/leadAppeal/index','ZsjosLeadAppeal',0,b'1',b'1',b'1','migration-V015',NOW(),'migration-V015',NOW(),b'0'),
(6805,'提交客资申诉','zsjos:lead:appeal:create',3,13,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V015',NOW(),'migration-V015',NOW(),b'0'),
(6806,'销售主管处理申诉','zsjos:lead:appeal:review-sales-manager',3,1,6804,'','','',NULL,0,b'1',b'1',b'1','migration-V015',NOW(),'migration-V015',NOW(),b'0'),
(6807,'质控处理申诉','zsjos:lead:appeal:review-quality',3,2,6804,'','','',NULL,0,b'1',b'1',b'1','migration-V015',NOW(),'migration-V015',NOW(),b'0'),
(6808,'董事长终审申诉','zsjos:lead:appeal:review-chairman',3,3,6804,'','','',NULL,0,b'1',b'1',b'1','migration-V015',NOW(),'migration-V015',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6805,'migration-V015',NOW(),'migration-V015',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead:submit' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6805 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id, grants.menu_id,'migration-V015',NOW(),'migration-V015',NOW(),b'0',r.tenant_id
FROM `system_role` r JOIN (
 SELECT 'sales_manager' code,6804 menu_id UNION ALL SELECT 'sales_manager',6806 UNION ALL
 SELECT 'quality_manager',6804 UNION ALL SELECT 'quality_manager',6807 UNION ALL
 SELECT 'quality_specialist',6804 UNION ALL SELECT 'quality_specialist',6807 UNION ALL
 SELECT 'boss',6804 UNION ALL SELECT 'boss',6808
) grants ON grants.code=r.code
WHERE r.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=grants.menu_id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V015','Add three-level lead appeal workflow','lead-three-level-appeal-v1');
