-- V173: permanent Lead submitter-assistance request action.
-- Dependencies/order: apply after V172; requires Lead, BusinessTask and configurable notifications.
-- Data scope: creates one empty request snapshot table, one button permission, initial sales role grants,
-- and two in-app notification defaults. No Lead, task, message or account rows are changed.
-- Repeatability: CREATE TABLE IF NOT EXISTS plus guarded menu/template/rule/grant inserts.
-- Rollback limitation: preserve request history; disable the permission and notification rules forward-only.

CREATE TABLE IF NOT EXISTS `zsjos_lead_submitter_assist_request` (
  `id` bigint NOT NULL AUTO_INCREMENT, `lead_id` bigint NOT NULL, `lead_no_snapshot` varchar(64) NOT NULL,
  `requester_user_id` bigint NOT NULL, `problem` varchar(1000) NOT NULL,
  `expected_assistance` varchar(1000) NOT NULL, `remark` varchar(2000) DEFAULT NULL,
  `attachment_snapshots_json` json DEFAULT NULL, `submitter_type_snapshot` varchar(32) NOT NULL,
  `submitter_id_snapshot` bigint NOT NULL, `submitter_name_snapshot` varchar(128) DEFAULT NULL,
  `assignee_user_id_snapshot` bigint DEFAULT NULL, `assignee_name_snapshot` varchar(128) DEFAULT NULL,
  `requested_at` datetime NOT NULL, `request_fingerprint` varchar(64) NOT NULL, `idempotency_key` varchar(128) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_lead_requested` (`tenant_id`,`lead_id`,`requested_at`,`id`),
  KEY `idx_tenant_assignee_requested` (`tenant_id`,`assignee_user_id_snapshot`,`requested_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资提交人协助请求快照';

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '请求提交人协助','zsjos:lead:request-submitter-assist',3,32,parent.id,'','','',NULL,0,b'1',b'1',b'1','migration-V173',NOW(),'migration-V173',NOW(),b'0'
FROM `system_menu` parent WHERE parent.permission='zsjos:lead:query' AND parent.type=2 AND parent.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` existing WHERE existing.permission='zsjos:lead:request-submitter-assist' AND existing.deleted=b'0')
ORDER BY parent.id LIMIT 1;

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V173',NOW(),'migration-V173',NOW(),b'0',role.tenant_id
FROM `system_role` role JOIN `system_menu` menu ON menu.permission='zsjos:lead:request-submitter-assist' AND menu.deleted=b'0'
WHERE role.code IN ('sales_specialist','sales_manager') AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing WHERE existing.role_id=role.id AND existing.menu_id=menu.id AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.code,'中世健消息中心',seed.scene,'in_app',seed.title,seed.summary,seed.content,2,seed.params,0,'V173 系统默认模板','migration-V173',NOW(),'migration-V173',NOW(),b'0'
FROM (
  SELECT '请求提交人协助' name,'ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED' code,'zsjos.lead.submitter_assist_requested' scene,'请求协助处理客资' title,
         '客资{{lead.no}}需要您协助处理' summary,
         '客资{{lead.no}}遇到的问题：{{assist.problem}}；希望协助方式：{{assist.expectedAssistance}}；备注：{{assist.remark}}' content,
         '["lead.no","assist.problem","assist.expectedAssistance","assist.remark"]' params
  UNION ALL SELECT '提醒兼职提交人协助','ZSJOS_LEAD_PARTNER_ASSIST_REMINDER','zsjos.lead.partner_assist_reminder','兼职提交客资需要协助',
         '客资{{lead.no}}需要兼职提交人协助处理',
         '客资{{lead.no}}遇到的问题：{{assist.problem}}；希望协助方式：{{assist.expectedAssistance}}。请提醒兼职人员尽快协助处理。',
         '["lead.no","assist.problem","assist.expectedAssistance"]'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing WHERE existing.code=seed.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,seed.scene,'in_app',template.id,seed.roles,'[]','business_detail',0,'migration-V173',NOW(),'migration-V173',NOW(),b'0',tenant.id
FROM `system_tenant` tenant JOIN (
  SELECT '请求提交人协助通知' name,'zsjos.lead.submitter_assist_requested' scene,'["submitter"]' roles,'ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED' template_code
  UNION ALL SELECT '兼职提交人协助提醒','zsjos.lead.partner_assist_reminder','["partner_owner"]','ZSJOS_LEAD_PARTNER_ASSIST_REMINDER'
) seed JOIN `system_notify_template` template ON template.code=seed.template_code AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing WHERE existing.tenant_id=tenant.id AND existing.scene_code=seed.scene AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V173','Lead submitter assist request','V173__lead_submitter_assist_request.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V173','Lead submitter assist request',SHA2('V173__lead_submitter_assist_request.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
