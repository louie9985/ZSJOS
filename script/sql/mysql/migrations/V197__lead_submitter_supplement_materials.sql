-- Dependencies: V196 and existing system notification tables.
-- Data scope: adds one idempotent notification template/rule per tenant; no business rows are deleted or rewritten.
-- Repeatability: guarded by code/name and schema-version upserts.
SET NAMES utf8mb4;
INSERT INTO `system_notify_template` (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资补充资料','ZSJOS_LEAD_SUBMITTER_SUPPLEMENTED','中世健消息中心','zsjos.lead.submitter_supplemented','客资有新的补充资料','客资{{lead.no}}收到新的补充资料','客资{{lead.no}}收到新的补充资料，请进入客资详情查看。',2,'["lead.no"]',0,'V197 系统默认模板；租户需自行启用通知规则','migration-V197',NOW(),'migration-V197',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code`='ZSJOS_LEAD_SUBMITTER_SUPPLEMENTED' AND `deleted`=b'0');
INSERT INTO `system_notify_rule` (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '客资补充资料通知','zsjos.lead.submitter_supplemented','in_app',t.id,'["owner"]','[]','business_detail',0,'migration-V197',NOW(),'migration-V197',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_LEAD_SUBMITTER_SUPPLEMENTED' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.name='客资补充资料通知' AND r.deleted=b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V197','Lead submitter supplement materials',SHA2('V197__lead_submitter_supplement_materials.sql',256)) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V197','Lead submitter supplement materials',SHA2('V197__lead_submitter_supplement_materials.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
