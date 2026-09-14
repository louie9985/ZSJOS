SET NAMES utf8mb4;
INSERT INTO system_notify_template
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 'S0-S6交付确认待填写','ZSJOS_STUDENT_DELIVERY_CONFIRM','中世健消息中心','student.delivery.confirmation',
       '交付确认待填写','学员账号阶段交付确认任务待填写','账号{{accountName}}的{{stageCode}}交付确认待填写，请编导及时完成。',2,
       '["accountName","stageCode","event.time"]',0,'V220 阶段任务默认模板','migration-V220',NOW(),'migration-V220',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_STUDENT_DELIVERY_CONFIRM' AND deleted=b'0');
INSERT INTO system_notify_rule
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`timing_stage`,`timing_offset_minutes`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'S0-S6交付确认到期提醒','student.delivery.confirmation','in_app',t.id,'["assignee"]','[]','business_detail','due',0,0,
       'migration-V220',NOW(),'migration-V220',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_STUDENT_DELIVERY_CONFIRM' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.name='S0-S6交付确认到期提醒' AND r.deleted=b'0');
INSERT INTO zsjos_schema_version (`version`,`description`,`checksum`)
VALUES ('V220','Student delivery confirmation notification defaults','student-delivery-notify-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
