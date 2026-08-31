-- V082: registration planner-assignment notification.
-- Dependencies: V074 registration task notifications and V073 registration fulfillment.
-- Scope: add one system-owned template and one enabled default in-app rule per active tenant.
-- Recipient scope is enforced by the registration scene provider; this migration does not grant permissions.
-- Repeatability: template code and tenant/scene/creator guards prevent duplicates.
-- Rollback: forward-only; disable untouched V082 rules and preserve delivered message history.

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '学习规划师分配','ZSJOS_REGISTRATION_PLANNER_ASSIGNED','中世健消息中心','zsjos.registration.planner_assigned',
       '学员已分配给你','学员{{student.name}}（{{lead.no}}）已分配给你。',
       '学员{{student.name}}（{{lead.no}}）已分配给你。',2,
       '["registration.caseId","student.name","lead.no"]',0,
       'V082 学习规划师分配通知','migration-V082',NOW(),'migration-V082',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
  WHERE `code`='ZSJOS_REGISTRATION_PLANNER_ASSIGNED' AND `deleted`=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '学习规划师分配','zsjos.registration.planner_assigned','in_app',template.id,
       '["study_planner"]','[]','business_detail',0,'migration-V082',NOW(),'migration-V082',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN `system_notify_template` template ON template.code='ZSJOS_REGISTRATION_PLANNER_ASSIGNED' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
  WHERE rule_row.tenant_id=tenant.id AND rule_row.scene_code='zsjos.registration.planner_assigned'
    AND rule_row.creator='migration-V082' AND rule_row.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V082','Registration planner notifications','registration-planner-notifications-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
