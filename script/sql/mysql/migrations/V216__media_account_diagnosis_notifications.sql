SET NAMES utf8mb4;
/* Default timed notification for the three account diagnosis task types. */
INSERT INTO system_notify_template
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '账号周期诊断逾期','ZSJOS_MEDIA_ACCOUNT_DIAGNOSIS_OVERDUE','中世健消息中心','media.account.diagnosis',
       '账号周期诊断逾期','账号周期诊断任务已逾期','账号{{accountName}}的周期诊断任务已逾期，请直属上级跟进。',2,
       '["accountName","event.time"]',0,'V216 账号诊断逾期默认模板','migration-V216',NOW(),'migration-V216',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_MEDIA_ACCOUNT_DIAGNOSIS_OVERDUE' AND deleted=b'0');

INSERT INTO system_notify_rule
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`timing_stage`,`timing_offset_minutes`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '账号周期诊断首次逾期', 'media.account.diagnosis', 'in_app', t.id, '["supervisor"]', '[]', 'business_detail', 'overdue', 0, 0,
       'migration-V216', NOW(), 'migration-V216', NOW(), b'0', tenant.id
FROM system_tenant tenant JOIN system_notify_template t
  ON t.code='ZSJOS_MEDIA_ACCOUNT_DIAGNOSIS_OVERDUE' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.name='账号周期诊断首次逾期' AND r.deleted=b'0'
);

INSERT INTO zsjos_schema_version (`version`,`description`,`checksum`)
VALUES ('V216','Add media account diagnosis overdue notification defaults','media-account-diagnosis-notify-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);


