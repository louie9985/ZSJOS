-- Media account rebind callback snapshots and result notifications.
-- Dependencies: V096, V102. Repeatable through information_schema and stable scene guards.
-- Data scope: additive account columns and notification metadata only; no business rows are rewritten.
-- Recovery: forward-only; preserve populated callback snapshots and disable notification rules if required.

SET @schema_name := DATABASE();
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='zsjos_media_account'
    AND column_name='rebind_target_student_person_id')=0,
  'ALTER TABLE `zsjos_media_account`
     ADD COLUMN `rebind_target_student_person_id` bigint DEFAULT NULL AFTER `rebind_process_instance_id`,
     ADD COLUMN `rebind_requested_by_user_id` bigint DEFAULT NULL AFTER `rebind_target_student_person_id`,
     ADD COLUMN `rebind_reviewer_user_id` bigint DEFAULT NULL AFTER `rebind_requested_by_user_id`,
     ADD COLUMN `rebind_status` varchar(24) DEFAULT NULL AFTER `rebind_reviewer_user_id`,
     ADD COLUMN `rebind_result_reason` varchar(1000) DEFAULT NULL AFTER `rebind_status`,
     ADD KEY `idx_tenant_rebind_status` (`tenant_id`,`rebind_status`,`rebind_reviewer_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene,'in_app',x.title,x.summary,x.content,2,'["bizNo","reason"]',0,
       'V110 账号换绑审批结果','migration-V110',NOW(),'migration-V110',NOW(),b'0'
FROM (
 SELECT '账号换绑通过' name,'ZSJOS_MEDIA_ACCOUNT_REBIND_APPROVED' code,'media.account.rebind_approved' scene,
        '账号换绑通过' title,'账号换绑申请已通过' summary,'账号 {{bizNo}} 的换绑申请已通过。' content
 UNION ALL
 SELECT '账号换绑驳回','ZSJOS_MEDIA_ACCOUNT_REBIND_REJECTED','media.account.rebind_rejected',
        '账号换绑驳回','账号换绑申请已驳回','账号 {{bizNo}} 的换绑申请已驳回，请查看审批意见。'
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing
                  WHERE existing.code=x.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene,'in_app',template.id,'["assignee"]','[]','business_detail',0,
       'migration-V110',NOW(),'migration-V110',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
 SELECT '账号换绑通过' name,'media.account.rebind_approved' scene,'ZSJOS_MEDIA_ACCOUNT_REBIND_APPROVED' code
 UNION ALL SELECT '账号换绑驳回','media.account.rebind_rejected','ZSJOS_MEDIA_ACCOUNT_REBIND_REJECTED'
) x ON 1=1
JOIN `system_notify_template` template ON template.code=x.code AND template.scene_code=x.scene AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing
                  WHERE existing.tenant_id=tenant.id AND existing.scene_code=x.scene AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V110','Media account rebind callback','V110__media_account_rebind_callback.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V110','Media account rebind callback',SHA2('V110__media_account_rebind_callback.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
