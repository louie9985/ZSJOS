-- Phase four: submission channels, submitter actions, and sales complaint queue.
-- Additive and repeatable. It grants no role permissions and does not mutate business records.
SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_duplicate_review' AND column_name='submission_source_type'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `submission_source_type` varchar(32) DEFAULT NULL COMMENT ''原提交通道'' AFTER `submitter_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_duplicate_review' AND column_name='submission_partner_id'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `submission_partner_id` bigint DEFAULT NULL COMMENT ''原兼职主体'' AFTER `submission_source_type`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_lead_urge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL,
  `submitter_user_id` bigint NOT NULL,
  `target_sales_user_id` bigint NOT NULL,
  `urge_date` date NOT NULL COMMENT '北京时间自然日',
  `reason` varchar(500) NOT NULL,
  `urged_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_lead_submitter_date` (`tenant_id`,`lead_id`,`submitter_user_id`,`urge_date`),
  KEY `idx_tenant_target_urged` (`tenant_id`,`target_sales_user_id`,`urged_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资提交人催促记录';

CREATE TABLE IF NOT EXISTS `zsjos_lead_complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL, `complainant_user_id` bigint NOT NULL, `sales_user_id` bigint NOT NULL,
  `reason` varchar(1000) NOT NULL, `evidence_refs` json DEFAULT NULL,
  `status` varchar(32) NOT NULL, `result` varchar(32) DEFAULT NULL,
  `handler_user_id` bigint DEFAULT NULL, `handler_opinion` varchar(1000) DEFAULT NULL,
  `handler_evidence_refs` json DEFAULT NULL, `handled_at` datetime DEFAULT NULL,
  `create_idempotency_key` varchar(128) NOT NULL, `decision_idempotency_key` varchar(128) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_complaint_create_key` (`tenant_id`,`create_idempotency_key`),
  UNIQUE KEY `uk_tenant_complaint_decision_key` (`tenant_id`,`decision_idempotency_key`),
  KEY `idx_tenant_complaint_queue` (`tenant_id`,`status`,`create_time`,`id`),
  KEY `idx_tenant_complaint_lead` (`tenant_id`,`lead_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 销售投诉';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6844,'销售自拓','zsjos:lead:self-sourced:create',2,14,6735,'leads/self-sourced','ep:user','zsjos-workbench','LeadSelfSourcedPage',0,b'1',b'0',b'1','migration-V040',NOW(),'migration-V040',NOW(),b'0'),
(6845,'提交人补充资料','zsjos:lead:submitter-supplement',3,14,6778,'','','',NULL,0,b'1',b'1',b'1','migration-V040',NOW(),'migration-V040',NOW(),b'0'),
(6846,'提交人催促','zsjos:lead:urge',3,15,6778,'','','',NULL,0,b'1',b'1',b'1','migration-V040',NOW(),'migration-V040',NOW(),b'0'),
(6847,'发起销售投诉','zsjos:lead-complaint:create',3,16,6778,'','','',NULL,0,b'1',b'1',b'1','migration-V040',NOW(),'migration-V040',NOW(),b'0'),
(6848,'销售投诉处理','zsjos:lead-complaint:handle',2,19,6735,'leads/complaints','ep:warning','zsjos-workbench','LeadComplaintPage',0,b'1',b'0',b'1','migration-V040',NOW(),'migration-V040',NOW(),b'0');

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,x.params,0,'V040 系统默认模板','migration-V040',NOW(),'migration-V040',NOW(),b'0'
FROM (
 SELECT '提交人催促跟进' name,'ZSJOS_LEAD_SUBMITTER_URGED' code,'zsjos.lead.submitter_urged' scene_code,'客资待跟进' title,'客资{{lead.id}}收到提交人催促' summary,'客资{{lead.id}}收到提交人催促：{{urge.reason}}' content,'["lead.id","urge.reason"]' params
 UNION ALL SELECT '销售投诉成立','ZSJOS_LEAD_COMPLAINT_FOUNDED','zsjos.lead.complaint_founded','销售投诉成立','客资{{lead.id}}的销售投诉已成立','客资{{lead.id}}的销售投诉已处理并判定成立。','["lead.id"]'
) x WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',0,'migration-V040',NOW(),'migration-V040',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '提交人催促通知' name,'zsjos.lead.submitter_urged' scene_code,'["owner"]' roles,'ZSJOS_LEAD_SUBMITTER_URGED' template_code
 UNION ALL SELECT '销售投诉成立通知','zsjos.lead.complaint_founded','["owner","direct_leader"]','ZSJOS_LEAD_COMPLAINT_FOUNDED'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
  WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.creator='migration-V040' AND r.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V040','Submitter actions and sales complaint queue','submitter-actions-complaints-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V040','Submitter actions and sales complaint queue',SHA2('submitter-actions-complaints-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
