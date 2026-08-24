-- New-media review approval and graduation business closure.
-- Dependencies: V096, V097, V102. Repeatable through information_schema guards and idempotent inserts.
-- Data scope: review columns, graduation table, review button, notification templates/rules.
-- Rollback is forward-only: do not drop populated columns/table; disable new permissions/rules if rollback is required.

SET @schema_name := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_review_report' AND column_name='reviewer_user_id')=0,
  'ALTER TABLE `zsjos_review_report` ADD COLUMN `reviewer_user_id` bigint DEFAULT NULL, ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL, ADD COLUMN `reviewed_at` datetime DEFAULT NULL, ADD COLUMN `archived_at` datetime DEFAULT NULL, ADD KEY `idx_tenant_reviewer_status` (`tenant_id`,`reviewer_user_id`,`status`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
       'V106 新媒体复盘/结业消息','migration-V106',NOW(),'migration-V106',NOW(),b'0'
FROM (
 SELECT '复盘待审核' name,'ZSJOS_MEDIA_REVIEW_PENDING' code,'media.review.pending' scene,'复盘待审核' title,'你有复盘报告待审核' summary,'复盘 {{bizNo}} 等待你审核。' content
 UNION ALL SELECT '复盘审核通过','ZSJOS_MEDIA_REVIEW_APPROVED','media.review.approved','复盘审核通过','复盘报告已通过','复盘 {{bizNo}} 已审核通过。'
 UNION ALL SELECT '复盘审核退回','ZSJOS_MEDIA_REVIEW_REJECTED','media.review.rejected','复盘审核退回','复盘报告需要修改','复盘 {{bizNo}} 已退回，请按原因修改。'
 UNION ALL SELECT '学员结业结果','ZSJOS_MEDIA_GRADUATION_RESULT','media.graduation.result','学员结业审批结果','学员结业审批结果','学员结业申请 {{bizNo}} 已有审批结果。'
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing
                  WHERE existing.code=x.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene,'in_app',template.id,'["assignee"]','[]','business_detail',0,
       'migration-V106',NOW(),'migration-V106',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
 SELECT '复盘待审核' name,'media.review.pending' scene,'ZSJOS_MEDIA_REVIEW_PENDING' code
 UNION ALL SELECT '复盘审核通过','media.review.approved','ZSJOS_MEDIA_REVIEW_APPROVED'
 UNION ALL SELECT '复盘审核退回','media.review.rejected','ZSJOS_MEDIA_REVIEW_REJECTED'
 UNION ALL SELECT '学员结业结果','media.graduation.result','ZSJOS_MEDIA_GRADUATION_RESULT'
) x ON 1=1
JOIN `system_notify_template` template ON template.code=x.code AND template.scene_code=x.scene AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing
                  WHERE existing.tenant_id=tenant.id AND existing.scene_code=x.scene AND existing.deleted=b'0');

INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7025,'审核复盘','zsjos:review:approve',3,4,6985,'','','',NULL,0,b'1',b'1',b'1','migration-V106',NOW(),'migration-V106',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:review:approve' AND deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V106','Media review graduation closure','V106__media_review_graduation_closure.sql',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V106');

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V106','Media review graduation closure',SHA2('V106__media_review_graduation_closure.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
