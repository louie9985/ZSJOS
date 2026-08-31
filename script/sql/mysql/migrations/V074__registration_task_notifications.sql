-- V074: default in-app notification for newly created registration fulfillment tasks.
-- Dependencies: V073 registration cases and V056 notification rules/outbox.
-- Data scope: one global system-owned template and one enabled default rule per active tenant.
-- Repeatability: stable template code and tenant/scene/creator guards prevent duplicates.
-- Rollback: forward-only; disable untouched rules and preserve delivered message history.

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '新报名履约任务','ZSJOS_REGISTRATION_TASK_CREATED','中世健消息中心','zsjos.registration.task_created',
       '有新的报名履约任务','订单{{order.no}}已通过报名审核，请及时处理',
       '学员{{student.name}}的订单{{order.no}}已通过报名审核，请进入报名履约公共池完成清单。',
       2,'["registration.caseId","order.id","order.no","student.name"]',0,
       'V074 报名履约新任务通知','migration-V074',NOW(),'migration-V074',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
  WHERE `code`='ZSJOS_REGISTRATION_TASK_CREATED' AND `deleted`=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '新报名履约任务','zsjos.registration.task_created','in_app',template.id,
       '["pool_handlers"]','[]','business_detail',0,'migration-V074',NOW(),'migration-V074',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN `system_notify_template` template ON template.code='ZSJOS_REGISTRATION_TASK_CREATED' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
  WHERE rule_row.tenant_id=tenant.id AND rule_row.scene_code='zsjos.registration.task_created'
    AND rule_row.creator='migration-V074' AND rule_row.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V074','Registration task notifications','registration-task-notifications-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
