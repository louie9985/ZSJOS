-- V075: default in-app notification for newly created Leads.
-- Dependencies: V016 Lead notification templates and V056 notification rules/outbox.
-- Data scope: one enabled default rule for each non-deleted tenant that has no Lead-created rule.
-- Repeatability: tenant/scene existence and stable migration creator guards prevent duplicates.
-- Rollback: forward-only; disable untouched V075 rules and preserve delivered message history.

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '客资新建通知','zsjos.lead.created','in_app',template.id,
       '["submitter","operator"]','[]','business_detail',0,
       'migration-V075',NOW(),'migration-V075',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN `system_notify_template` template
  ON template.code='ZSJOS_LEAD_CREATED'
 AND template.scene_code='zsjos.lead.created'
 AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
    WHERE rule_row.tenant_id=tenant.id
      AND rule_row.scene_code='zsjos.lead.created'
      AND rule_row.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V075','Lead created default notification','lead-created-default-notification-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
