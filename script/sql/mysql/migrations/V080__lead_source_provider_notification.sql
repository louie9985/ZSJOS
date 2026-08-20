-- V080: split Lead-created notifications for sales operators and linked new-media providers.
-- Dependencies: V075 Lead-created default notification and the V016 Lead-created template.
-- Data scope: one global provider template plus provider rules only for untouched V075 defaults.
-- Repeatability: stable template/rule guards and exact system-owned markers prevent duplicates.
-- Rollback: forward-only; preserve delivered history and administrator-managed rules/templates.

START TRANSACTION;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '新媒体客资来源关联','ZSJOS_LEAD_SOURCE_LINKED','中世健消息中心','zsjos.lead.created',
       '新客资来源关联',
       '{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。',
       '{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。',
       2,'["operator.name","lead.no"]',0,
       'V080 销售自拓客资关联新媒体提供方通知',
       'migration-V080',NOW(),'migration-V080',NOW(),b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template`
   WHERE `code`='ZSJOS_LEAD_SOURCE_LINKED' AND `deleted`=b'0'
);

UPDATE `system_notify_rule` rule_row
JOIN `system_notify_template` template ON template.id=rule_row.template_id
SET rule_row.recipient_roles='["operator"]',
    rule_row.updater='migration-V080',rule_row.update_time=NOW()
WHERE rule_row.scene_code='zsjos.lead.created'
  AND rule_row.channel_code='in_app'
  AND template.code='ZSJOS_LEAD_CREATED'
  AND rule_row.recipient_roles='["submitter","operator"]'
  AND rule_row.specified_user_ids='[]'
  AND rule_row.action_type='business_detail'
  AND rule_row.status=0
  AND rule_row.creator='migration-V075'
  AND rule_row.updater='migration-V075'
  AND rule_row.deleted=b'0';

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '新媒体客资来源关联','zsjos.lead.created','in_app',provider_template.id,
       '["new_media_provider"]','[]','business_detail',0,
       'migration-V080',NOW(),'migration-V080',NOW(),b'0',sales_rule.tenant_id
FROM `system_notify_rule` sales_rule
JOIN `system_notify_template` sales_template
  ON sales_template.id=sales_rule.template_id
 AND sales_template.code='ZSJOS_LEAD_CREATED'
 AND sales_template.deleted=b'0'
JOIN `system_notify_template` provider_template
  ON provider_template.code='ZSJOS_LEAD_SOURCE_LINKED'
 AND provider_template.deleted=b'0'
WHERE sales_rule.scene_code='zsjos.lead.created'
  AND sales_rule.channel_code='in_app'
  AND sales_rule.recipient_roles='["operator"]'
  AND sales_rule.specified_user_ids='[]'
  AND sales_rule.action_type='business_detail'
  AND sales_rule.status=0
  AND sales_rule.creator='migration-V075'
  AND sales_rule.updater='migration-V080'
  AND sales_rule.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_rule` existing
     WHERE existing.tenant_id=sales_rule.tenant_id
       AND existing.scene_code='zsjos.lead.created'
       AND (existing.creator='migration-V080'
         OR (existing.deleted=b'0'
           AND JSON_CONTAINS(existing.recipient_roles,JSON_QUOTE('new_media_provider'))))
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V080','Lead source provider notification','V080__lead_source_provider_notification.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V080','Lead source provider notification',
        SHA2('V080__lead_source_provider_notification.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
