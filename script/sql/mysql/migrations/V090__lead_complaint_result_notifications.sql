-- V090: notify the exact complaint submitter for founded and unfounded decisions.
-- Dependencies/order: apply after V089; requires V040 complaint scenes and System notification rules.
-- Data scope: two global templates and at most two enabled in-app rules per non-deleted tenant.
-- Existing rules and historical messages are not updated or deleted.
-- Repeatability: stable template codes and per-tenant complainant-role guards prevent duplicates.
-- Recovery: forward-only; disable untouched V090 rules while preserving delivered message history.

START TRANSACTION;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.code,'中世健消息中心',seed.scene_code,seed.title,seed.summary,seed.content,
       2,seed.params,0,'V090 投诉提交人结果通知','migration-V090',NOW(),'migration-V090',NOW(),b'0'
FROM (
  SELECT '销售投诉成立结果' name,'ZSJOS_LEAD_COMPLAINT_RESULT_FOUNDED' code,
         'zsjos.lead.complaint_founded' scene_code,'销售投诉处理结果' title,
         '客资{{lead.no}}的销售投诉已判定成立' summary,
         '客资{{lead.no}}的销售投诉已判定成立，处理意见：{{complaint.handlerOpinion}}。' content,
         '["lead.no","complaint.handlerOpinion"]' params
  UNION ALL
  SELECT '销售投诉不成立结果','ZSJOS_LEAD_COMPLAINT_RESULT_UNFOUNDED',
         'zsjos.lead.complaint_unfounded','销售投诉处理结果',
         '客资{{lead.no}}的销售投诉已判定不成立',
         '客资{{lead.no}}的销售投诉已判定不成立，处理意见：{{complaint.handlerOpinion}}。',
         '["lead.no","complaint.handlerOpinion"]'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template` existing
   WHERE existing.code=seed.code AND existing.deleted=b'0'
);

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,seed.scene_code,'in_app',template.id,'["complainant"]','[]','business_detail',0,
       'migration-V090',NOW(),'migration-V090',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
  SELECT '销售投诉成立结果通知' name,'zsjos.lead.complaint_founded' scene_code,
         'ZSJOS_LEAD_COMPLAINT_RESULT_FOUNDED' template_code
  UNION ALL
  SELECT '销售投诉不成立结果通知','zsjos.lead.complaint_unfounded',
         'ZSJOS_LEAD_COMPLAINT_RESULT_UNFOUNDED'
) seed
JOIN `system_notify_template` template
  ON template.code=seed.template_code AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_rule` existing
     WHERE existing.tenant_id=tenant.id
       AND existing.scene_code=seed.scene_code
       AND existing.channel_code='in_app'
       AND existing.action_type='business_detail'
       AND existing.deleted=b'0'
       AND JSON_CONTAINS(existing.recipient_roles,JSON_QUOTE('complainant'))
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V090','Lead complaint result notifications','V090__lead_complaint_result_notifications.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V090','Lead complaint result notifications',
        SHA2('V090__lead_complaint_result_notifications.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
