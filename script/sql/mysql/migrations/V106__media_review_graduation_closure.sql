-- New-media graduation business closure.
-- Dependencies: V096, V097, V102. Repeatable through information_schema guards and idempotent inserts.
-- Data scope: graduation table and graduation notification template/rule only.
-- Rollback is forward-only: do not drop populated columns/table; disable new permissions/rules if rollback is required.

CREATE TABLE IF NOT EXISTS `zsjos_graduation_application` (
  `id` bigint NOT NULL AUTO_INCREMENT, `application_no` varchar(64) NOT NULL,
  `service_relation_id` bigint NOT NULL, `student_person_id` bigint NOT NULL,
  `planner_user_id` bigint NOT NULL, `reviewer_user_id` bigint NOT NULL,
  `director_user_id` bigint DEFAULT NULL, `operator_user_id` bigint DEFAULT NULL,
  `reason` varchar(1000) NOT NULL, `snapshot_json` json NOT NULL,
  `status` varchar(24) NOT NULL, `process_instance_id` varchar(64) DEFAULT NULL,
  `result_reason` varchar(1000) DEFAULT NULL, `submitted_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_graduation_no` (`tenant_id`,`application_no`,`deleted`),
  UNIQUE KEY `uk_tenant_graduation_process` (`tenant_id`,`process_instance_id`,`deleted`),
  KEY `idx_tenant_graduation_planner` (`tenant_id`,`planner_user_id`,`status`),
  KEY `idx_tenant_graduation_reviewer` (`tenant_id`,`reviewer_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员结业申请';

-- The runtime registers these scenes in MediaNotifySceneProvider. Keep their
-- templates and tenant rules in the same forward migration so the new flows
-- can be verified end to end after installation.
INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene,'in_app',x.title,x.summary,x.content,2,'["bizNo","reason"]',0,
       'V106 新媒体结业消息','migration-V106',NOW(),'migration-V106',NOW(),b'0'
FROM (
 SELECT '学员结业结果' name,'ZSJOS_MEDIA_GRADUATION_RESULT' code,'media.graduation.result' scene,'学员结业审批结果' title,'学员结业审批结果' summary,'学员结业申请 {{bizNo}} 已有审批结果。' content
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing
                  WHERE existing.code=x.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene,'in_app',template.id,'["assignee"]','[]','business_detail',0,
       'migration-V106',NOW(),'migration-V106',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
 SELECT '学员结业结果' name,'media.graduation.result' scene,'ZSJOS_MEDIA_GRADUATION_RESULT' code
) x ON 1=1
JOIN `system_notify_template` template ON template.code=x.code AND template.scene_code=x.scene AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing
                  WHERE existing.tenant_id=tenant.id AND existing.scene_code=x.scene AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V106','Media graduation closure','V106__media_review_graduation_closure.sql',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V106');

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V106','Media graduation closure',SHA2('V106__media_review_graduation_closure.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
