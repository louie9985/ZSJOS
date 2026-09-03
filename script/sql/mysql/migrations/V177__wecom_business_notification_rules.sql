-- V177: mirror business in-app notification templates/rules to WeCom.
-- Dependencies: V027/V028 notification channels and the latest business notification templates/rules.
-- Execution order: clone scene templates, clone tenant rules, then register the version.
-- Repeatability: template codes and tenant/rule/template/channel checks make this migration idempotent.
-- Data scope: only non-deleted business templates (scene_code is not null) and their in-app rules;
-- no historical messages are sent, no tenant channel is enabled, and no user preference is changed.
-- Recovery: forward-only; disable the generated WeCom rules/templates if rollback is required.

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,
 `channel_code`,`sms_template_id`,`wecom_message_type`,`status`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT LEFT(CONCAT(source.`name`,'（企微）'),63),
       CASE WHEN CHAR_LENGTH(source.`code`) <= 58 THEN CONCAT(source.`code`,'_WECOM')
            ELSE CONCAT(LEFT(source.`code`,47),'_',LEFT(MD5(source.`code`),8),'_WECOM') END,
       source.`nickname`, source.`scene_code`,
       source.`title`, source.`summary`, source.`content`, source.`type`, source.`params`,
       'wecom', NULL, COALESCE(NULLIF(source.`wecom_message_type`,''),'textcard'), source.`status`,
       LEFT(CONCAT(COALESCE(source.`remark`,''),'；V177由业务站内信模板生成企微模板'),255),
       'migration-V177', NOW(), 'migration-V177', NOW(), b'0'
FROM `system_notify_template` source
WHERE source.`deleted`=b'0'
  AND source.`scene_code` IS NOT NULL AND TRIM(source.`scene_code`) <> ''
  AND (source.`channel_code` IS NULL OR source.`channel_code`='' OR source.`channel_code`='in_app')
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_template` target
    WHERE target.`code`=(CASE WHEN CHAR_LENGTH(source.`code`) <= 58 THEN CONCAT(source.`code`,'_WECOM')
                              ELSE CONCAT(LEFT(source.`code`,47),'_',LEFT(MD5(source.`code`),8),'_WECOM') END)
      AND target.`deleted`=b'0'
  );

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,
 `action_type`,`timing_stage`,`timing_offset_minutes`,`status`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT LEFT(CONCAT(source_rule.`name`,'（企微）'),64), source_rule.`scene_code`, 'wecom', wecom_template.`id`,
       source_rule.`recipient_roles`, source_rule.`specified_user_ids`, source_rule.`action_type`,
       source_rule.`timing_stage`, source_rule.`timing_offset_minutes`, source_rule.`status`,
       'migration-V177', NOW(), 'migration-V177', NOW(), b'0', source_rule.`tenant_id`
FROM `system_notify_rule` source_rule
JOIN `system_notify_template` source_template ON source_template.`id`=source_rule.`template_id`
  AND source_template.`deleted`=b'0'
JOIN `system_notify_template` wecom_template
  ON wecom_template.`code`=(CASE WHEN CHAR_LENGTH(source_template.`code`) <= 58
                                 THEN CONCAT(source_template.`code`,'_WECOM')
                                 ELSE CONCAT(LEFT(source_template.`code`,47),'_',LEFT(MD5(source_template.`code`),8),'_WECOM') END)
 AND wecom_template.`scene_code`=source_template.`scene_code`
 AND wecom_template.`channel_code`='wecom' AND wecom_template.`deleted`=b'0'
WHERE source_rule.`deleted`=b'0'
  AND (source_rule.`channel_code` IS NULL OR source_rule.`channel_code`='' OR source_rule.`channel_code`='in_app')
  AND source_rule.`scene_code` IS NOT NULL AND TRIM(source_rule.`scene_code`) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_rule` target_rule
    WHERE target_rule.`tenant_id`=source_rule.`tenant_id`
      AND target_rule.`scene_code`=source_rule.`scene_code`
      AND target_rule.`channel_code`='wecom'
      AND target_rule.`template_id`=wecom_template.`id`
      AND target_rule.`name`=LEFT(CONCAT(source_rule.`name`,'（企微）'),64)
      AND target_rule.`recipient_roles`=source_rule.`recipient_roles`
      AND target_rule.`specified_user_ids`=source_rule.`specified_user_ids`
      AND target_rule.`action_type`=source_rule.`action_type`
      AND target_rule.`timing_stage` <=> source_rule.`timing_stage`
      AND target_rule.`timing_offset_minutes` <=> source_rule.`timing_offset_minutes`
      AND target_rule.`deleted`=b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V177','Mirror business notification rules to WeCom','wecom-business-notification-rules-v1');
