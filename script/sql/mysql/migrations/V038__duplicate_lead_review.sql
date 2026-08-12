-- Phase two: duplicate Lead review.
-- Additive and repeatable. It creates no role grants and mutates no business rows.
CREATE TABLE IF NOT EXISTS `zsjos_lead_duplicate_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '复核任务编号',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'pending/completed',
  `submitter_user_id` bigint DEFAULT NULL COMMENT '原提交人',
  `submission_snapshot` json NOT NULL COMMENT '原始提交快照',
  `match_rules` json NOT NULL COMMENT '命中规则',
  `candidate_snapshot` json NOT NULL COMMENT '候选快照',
  `matched_person_id` bigint DEFAULT NULL COMMENT '结论选择客户',
  `matched_lead_id` bigint DEFAULT NULL COMMENT '结论选择客资',
  `result_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结构化结论',
  `review_opinion` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核意见',
  `review_attachments` json DEFAULT NULL COMMENT '复核附件快照',
  `selected_sales_user_id` bigint DEFAULT NULL COMMENT '激活时选择销售',
  `reviewer_user_id` bigint DEFAULT NULL COMMENT '实际处理人',
  `reviewed_at` datetime DEFAULT NULL COMMENT '处理时间',
  `before_snapshot` json DEFAULT NULL COMMENT '处理前资料',
  `after_snapshot` json DEFAULT NULL COMMENT '处理后资料',
  `submission_idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原提交幂等键',
  `decision_idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核决定幂等键',
  `version` int NOT NULL DEFAULT '0' COMMENT '并发版本',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_submission_idempotency` (`tenant_id`,`submission_idempotency_key`),
  UNIQUE KEY `uk_tenant_decision_idempotency` (`tenant_id`,`decision_idempotency_key`),
  KEY `idx_tenant_queue` (`tenant_id`,`status`,`create_time`,`id`),
  KEY `idx_tenant_person` (`tenant_id`,`matched_person_id`),
  KEY `idx_tenant_lead` (`tenant_id`,`matched_lead_id`),
  KEY `idx_tenant_reviewer` (`tenant_id`,`reviewer_user_id`,`reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 重复客资复核';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6840,'重复客资复核','zsjos:lead-duplicate-review:query',2,16,6735,'leads/duplicate-review','ep:copy-document','zsjos-workbench','LeadDuplicateReviewPage',0,b'1',b'0',b'1','migration-V038',NOW(),'migration-V038',NOW(),b'0'),
(6841,'处理重复客资','zsjos:lead-duplicate-review:process',3,1,6840,'','','',NULL,0,b'1',b'1',b'1','migration-V038',NOW(),'migration-V038',NOW(),b'0'),
(6842,'全局复核销售范围','zsjos:lead-duplicate-review:manage-all',3,2,6840,'','','',NULL,0,b'1',b'1',b'1','migration-V038',NOW(),'migration-V038',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V038','Duplicate Lead review queue and permissions','duplicate-lead-review-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V038','Duplicate Lead review queue and permissions',SHA2('duplicate-lead-review-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
