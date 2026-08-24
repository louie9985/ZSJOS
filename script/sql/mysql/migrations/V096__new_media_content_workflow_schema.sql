-- V096: new-media content production workflow schema.
-- Dependencies/order: apply after V095, ZSJOS Person/Partner, business task/event/work-plan,
-- System user/department/dictionary, and BPM infrastructure.
-- Data scope: additive empty business tables and one versioned configuration baseline per tenant.
-- Repeatability: CREATE TABLE IF NOT EXISTS and natural-key guarded configuration seeds.
-- Recovery: forward-only. Stop new use by disabling menus/configuration; retain business and audit history.
-- This file must not be executed against an existing environment without separate approval.

CREATE TABLE IF NOT EXISTS `zsjos_media_account_no_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT, `sequence_date` date NOT NULL, `current_value` int NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_sequence_date` (`tenant_id`,`sequence_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交媒体账号业务编号日序列';

CREATE TABLE IF NOT EXISTS `zsjos_media_account` (
  `id` bigint NOT NULL AUTO_INCREMENT, `account_no` varchar(64) NOT NULL,
  `student_person_id` bigint DEFAULT NULL, `ownership_type` varchar(24) NOT NULL,
  `owner_operator_user_id` bigint NOT NULL, `director_user_id` bigint DEFAULT NULL,
  `platform_value` varchar(100) NOT NULL, `platform_label_snapshot` varchar(100) NOT NULL,
  `platform_account_id` varchar(255) DEFAULT NULL, `nickname` varchar(255) NOT NULL,
  `account_type_primary_value` varchar(100) DEFAULT NULL, `account_type_primary_label_snapshot` varchar(100) DEFAULT NULL,
  `account_type_secondary_value` varchar(100) DEFAULT NULL, `account_type_secondary_label_snapshot` varchar(100) DEFAULT NULL,
  `track_primary_value` varchar(100) DEFAULT NULL, `track_primary_label_snapshot` varchar(100) DEFAULT NULL,
  `track_secondary_value` varchar(100) DEFAULT NULL, `track_secondary_label_snapshot` varchar(100) DEFAULT NULL,
  `lead_direction` varchar(500) DEFAULT NULL, `s_stage` varchar(24) NOT NULL DEFAULT 's0',
  `s_stage_version` varchar(24) DEFAULT NULL, `s_stage_entered_at` datetime NOT NULL,
  `s_stage_judged_by_user_id` bigint DEFAULT NULL, `is_silent` bit(1) NOT NULL DEFAULT b'0',
  `coop_level_value` varchar(100) DEFAULT NULL, `coop_level_label_snapshot` varchar(100) DEFAULT NULL,
  `account_grade_value` varchar(100) DEFAULT NULL, `account_grade_label_snapshot` varchar(100) DEFAULT NULL,
  `health_status_value` varchar(100) DEFAULT NULL, `health_status_label_snapshot` varchar(100) DEFAULT NULL,
  `primary_problem_code_value` varchar(100) DEFAULT NULL, `primary_problem_code_label_snapshot` varchar(100) DEFAULT NULL,
  `secondary_problem_code_value` varchar(100) DEFAULT NULL, `secondary_problem_code_label_snapshot` varchar(100) DEFAULT NULL,
  `run_status` varchar(24) NOT NULL DEFAULT 'active', `rescue_status` varchar(24) NOT NULL DEFAULT 'none',
  `whitelist_status` varchar(24) NOT NULL DEFAULT 'none', `positioning_card_id` bigint DEFAULT NULL,
  `content_model_json` json DEFAULT NULL, `health_json` json DEFAULT NULL,
  `risk_level_value` varchar(100) DEFAULT NULL, `risk_level_label_snapshot` varchar(100) DEFAULT NULL,
  `rebind_process_instance_id` varchar(64) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_account_no` (`tenant_id`,`account_no`,`deleted`),
  KEY `idx_tenant_operator_stage` (`tenant_id`,`owner_operator_user_id`,`s_stage`,`run_status`),
  KEY `idx_tenant_director_stage` (`tenant_id`,`director_user_id`,`s_stage`,`run_status`),
  KEY `idx_tenant_student` (`tenant_id`,`student_person_id`,`run_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交媒体账号';

CREATE TABLE IF NOT EXISTS `zsjos_media_account_student_link` (
  `id` bigint NOT NULL AUTO_INCREMENT, `account_id` bigint NOT NULL, `student_person_id` bigint NOT NULL,
  `status` varchar(24) NOT NULL, `reason` varchar(500) NOT NULL, `started_at` datetime NOT NULL,
  `ended_at` datetime DEFAULT NULL, `operated_by_user_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_tenant_account_started` (`tenant_id`,`account_id`,`started_at`),
  KEY `idx_tenant_student_started` (`tenant_id`,`student_person_id`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交媒体账号学员绑定历史';

CREATE TABLE IF NOT EXISTS `zsjos_content` (
  `id` bigint NOT NULL AUTO_INCREMENT, `content_no` varchar(64) NOT NULL, `account_id` bigint NOT NULL,
  `production_ticket_id` bigint DEFAULT NULL, `title` varchar(255) NOT NULL, `topic` varchar(1000) DEFAULT NULL,
  `module_value` varchar(100) DEFAULT NULL, `module_label_snapshot` varchar(100) DEFAULT NULL,
  `content_class_value` varchar(100) NOT NULL, `content_class_label_snapshot` varchar(100) NOT NULL,
  `status` varchar(24) NOT NULL, `current_version_no` int NOT NULL DEFAULT 1,
  `script_text` mediumtext DEFAULT NULL, `script_url` varchar(1024) DEFAULT NULL,
  `owner_operator_user_id` bigint NOT NULL, `filming_editor_user_id` bigint DEFAULT NULL,
  `problem_codes_json` json DEFAULT NULL, `published_url` varchar(1024) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL, `reject_count` int NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_content_no` (`tenant_id`,`content_no`,`deleted`),
  KEY `idx_tenant_account_status` (`tenant_id`,`account_id`,`status`),
  KEY `idx_tenant_operator_status` (`tenant_id`,`owner_operator_user_id`,`status`),
  KEY `idx_tenant_editor_status` (`tenant_id`,`filming_editor_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体内容';

CREATE TABLE IF NOT EXISTS `zsjos_content_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `content_id` bigint NOT NULL, `version_no` int NOT NULL,
  `stage` varchar(24) NOT NULL, `material_refs_json` json DEFAULT NULL, `deliverable_url` varchar(1024) DEFAULT NULL,
  `script_text` mediumtext DEFAULT NULL, `submitted_by_user_id` bigint NOT NULL, `submitted_at` datetime NOT NULL,
  `review_decision` varchar(24) DEFAULT NULL, `review_comment` varchar(2000) DEFAULT NULL,
  `reviewed_by_user_id` bigint DEFAULT NULL, `reviewed_at` datetime DEFAULT NULL, `idempotency_key` varchar(128) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_content_version` (`tenant_id`,`content_id`,`version_no`,`deleted`),
  UNIQUE KEY `uk_tenant_content_version_idempotency` (`tenant_id`,`content_id`,`idempotency_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容追加版本';

CREATE TABLE IF NOT EXISTS `zsjos_production_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT, `ticket_no` varchar(64) NOT NULL, `account_id` bigint NOT NULL,
  `owner_operator_user_id` bigint NOT NULL, `assignee_filming_editor_user_id` bigint DEFAULT NULL,
  `reviewer_user_id` bigint NOT NULL, `script_text` mediumtext DEFAULT NULL, `script_url` varchar(1024) DEFAULT NULL,
  `material_refs_json` json DEFAULT NULL, `spec_json` json DEFAULT NULL, `ticket_version` int NOT NULL DEFAULT 1,
  `expected_delivered_at` datetime NOT NULL, `deadline_at` datetime NOT NULL,
  `entitlement_quota` int NOT NULL DEFAULT 0, `remaining_count` int NOT NULL DEFAULT 0,
  `max_revision_count` int NOT NULL DEFAULT 0, `revision_count` int NOT NULL DEFAULT 0,
  `rework_reason_type` varchar(24) DEFAULT NULL, `over_entitlement` bit(1) NOT NULL DEFAULT b'0',
  `over_entitlement_handling` varchar(24) DEFAULT NULL, `over_entitlement_process_instance_id` varchar(64) DEFAULT NULL,
  `status` varchar(24) NOT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_ticket_no` (`tenant_id`,`ticket_no`,`deleted`),
  KEY `idx_tenant_operator_status` (`tenant_id`,`owner_operator_user_id`,`status`,`deadline_at`),
  KEY `idx_tenant_editor_status` (`tenant_id`,`assignee_filming_editor_user_id`,`status`,`deadline_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拍剪工单';

CREATE TABLE IF NOT EXISTS `zsjos_production_ticket_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `ticket_id` bigint NOT NULL, `content_id` bigint NOT NULL,
  `delivered_at` datetime DEFAULT NULL, `item_status` varchar(24) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_ticket_content` (`tenant_id`,`ticket_id`,`content_id`,`deleted`),
  KEY `idx_tenant_content` (`tenant_id`,`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拍剪工单内容项';

CREATE TABLE IF NOT EXISTS `zsjos_positioning_card` (
  `id` bigint NOT NULL AUTO_INCREMENT, `card_no` varchar(64) NOT NULL, `account_id` bigint NOT NULL,
  `student_person_id` bigint DEFAULT NULL, `director_user_id` bigint NOT NULL, `version_no` int NOT NULL,
  `layer1_json` json NOT NULL, `layer2_json` json NOT NULL, `formula_json` json NOT NULL,
  `feasibility_json` json NOT NULL, `content_form_json` json NOT NULL, `compliance_json` json NOT NULL,
  `professional_risk` bit(1) NOT NULL DEFAULT b'0', `status` varchar(32) NOT NULL,
  `ip_process_instance_id` varchar(64) DEFAULT NULL, `ip_reviewer_user_id` bigint DEFAULT NULL,
  `ip_reviewed_at` datetime DEFAULT NULL, `operator_reviewed_by_user_id` bigint DEFAULT NULL,
  `operator_reviewed_at` datetime DEFAULT NULL, `operator_review_comment` varchar(2000) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_card_no` (`tenant_id`,`card_no`,`deleted`),
  KEY `idx_tenant_account_status` (`tenant_id`,`account_id`,`status`),
  KEY `idx_tenant_director_status` (`tenant_id`,`director_user_id`,`status`),
  UNIQUE KEY `uk_tenant_ip_process` (`tenant_id`,`ip_process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号定位卡';

CREATE TABLE IF NOT EXISTS `zsjos_positioning_card_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `positioning_card_id` bigint NOT NULL, `version_no` int NOT NULL,
  `before_json` json DEFAULT NULL, `after_json` json NOT NULL, `change_reason` varchar(2000) NOT NULL,
  `proposed_by_user_id` bigint NOT NULL, `adopted` bit(1) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_card_version` (`tenant_id`,`positioning_card_id`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位卡追加版本';

CREATE TABLE IF NOT EXISTS `zsjos_positioning_exec_card` (
  `id` bigint NOT NULL AUTO_INCREMENT, `exec_card_no` varchar(64) NOT NULL, `account_id` bigint NOT NULL,
  `positioning_card_id` bigint NOT NULL, `execution_json` json NOT NULL, `review_due_at` datetime NOT NULL,
  `allowed_reposition_count` int DEFAULT NULL, `student_confirmed_at` datetime DEFAULT NULL,
  `director_confirmed_at` datetime DEFAULT NULL, `operator_confirmed_at` datetime DEFAULT NULL,
  `signature_snapshot_json` json DEFAULT NULL, `effective_at` datetime DEFAULT NULL,
  `status` varchar(24) NOT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_exec_card_no` (`tenant_id`,`exec_card_no`,`deleted`),
  KEY `idx_tenant_account_status` (`tenant_id`,`account_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号定位执行卡';

CREATE TABLE IF NOT EXISTS `zsjos_interview_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `account_id` bigint NOT NULL, `student_person_id` bigint DEFAULT NULL,
  `director_user_id` bigint NOT NULL, `structured_content_json` json DEFAULT NULL, `raw_import_text` mediumtext DEFAULT NULL,
  `import_source` varchar(24) NOT NULL, `imported_at` datetime DEFAULT NULL, `attachments_json` json DEFAULT NULL,
  `confirmed` bit(1) NOT NULL DEFAULT b'0', `status` varchar(24) NOT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_tenant_account_status` (`tenant_id`,`account_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位采访记录';

CREATE TABLE IF NOT EXISTS `zsjos_cooperation_assessment` (
  `id` bigint NOT NULL AUTO_INCREMENT, `student_person_id` bigint NOT NULL, `account_id` bigint NOT NULL,
  `operator_user_id` bigint NOT NULL, `period` varchar(32) NOT NULL,
  `committed_level_value` varchar(100) NOT NULL, `committed_level_label_snapshot` varchar(100) NOT NULL,
  `current_week_level_value` varchar(100) NOT NULL, `current_week_level_label_snapshot` varchar(100) NOT NULL,
  `last30d_level_value` varchar(100) NOT NULL, `last30d_level_label_snapshot` varchar(100) NOT NULL,
  `level_gap_reason` varchar(2000) DEFAULT NULL, `company_delivery_ok` bit(1) NOT NULL,
  `special_reason` varchar(1000) DEFAULT NULL, `improve_plan_json` json DEFAULT NULL,
  `indicators_json` json NOT NULL, `evidence_refs_json` json NOT NULL, `assessed_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_tenant_account_assessed` (`tenant_id`,`account_id`,`assessed_at`),
  KEY `idx_tenant_student_assessed` (`tenant_id`,`student_person_id`,`assessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员配合度评估';

CREATE TABLE IF NOT EXISTS `zsjos_review_report` (
  `id` bigint NOT NULL AUTO_INCREMENT, `review_no` varchar(64) NOT NULL, `review_type` varchar(32) NOT NULL,
  `subject_type` varchar(32) NOT NULL, `subject_id` bigint NOT NULL, `author_user_id` bigint NOT NULL,
  `report_json` json NOT NULL, `evidence_refs_json` json DEFAULT NULL, `status` varchar(24) NOT NULL,
  `submitted_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `config_version_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_review_no` (`tenant_id`,`review_no`,`deleted`),
  KEY `idx_tenant_subject_type` (`tenant_id`,`subject_type`,`subject_id`,`review_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体复盘报告';

CREATE TABLE IF NOT EXISTS `zsjos_exception_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT, `exception_no` varchar(64) NOT NULL, `account_id` bigint NOT NULL,
  `category_value` varchar(100) NOT NULL, `category_label_snapshot` varchar(100) NOT NULL,
  `description` varchar(2000) NOT NULL, `evidence_refs_json` json NOT NULL,
  `responsibility_type` varchar(32) NOT NULL, `owner_user_id` bigint NOT NULL,
  `process_instance_id` varchar(64) DEFAULT NULL, `status` varchar(24) NOT NULL,
  `resolution` varchar(2000) DEFAULT NULL, `resolved_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_exception_no` (`tenant_id`,`exception_no`,`deleted`),
  KEY `idx_tenant_account_status` (`tenant_id`,`account_id`,`status`),
  KEY `idx_tenant_owner_status` (`tenant_id`,`owner_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体异常工单';

CREATE TABLE IF NOT EXISTS `zsjos_workbench_capacity` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `staff_type` varchar(32) NOT NULL,
  `online_status` varchar(24) NOT NULL DEFAULT 'offline', `accepting` bit(1) NOT NULL DEFAULT b'0',
  `weighted_load` decimal(10,2) NOT NULL DEFAULT 0, `capacity_limit` decimal(10,2) DEFAULT NULL,
  `last_heartbeat_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_tenant_staff_online` (`tenant_id`,`staff_type`,`online_status`,`accepting`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体员工容量和在线状态';

CREATE TABLE IF NOT EXISTS `zsjos_account_stage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `account_id` bigint NOT NULL, `from_stage` varchar(24) NOT NULL,
  `to_stage` varchar(24) NOT NULL, `stage_version` varchar(24) DEFAULT NULL,
  `direction` varchar(24) NOT NULL, `criteria_snapshot_json` json NOT NULL,
  `judgment_basis` varchar(2000) NOT NULL, `judged_by_user_id` bigint NOT NULL,
  `judged_at` datetime NOT NULL, `idempotency_key` varchar(128) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_account_judged` (`tenant_id`,`account_id`,`judged_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号S阶段流转日志';

CREATE TABLE IF NOT EXISTS `zsjos_account_weekly_diagnosis` (
  `id` bigint NOT NULL AUTO_INCREMENT, `account_id` bigint NOT NULL, `week_no` varchar(24) NOT NULL,
  `stat_start` date NOT NULL, `stat_end` date NOT NULL, `owner_operator_user_id` bigint NOT NULL,
  `basic_json` json NOT NULL, `production_funnel_json` json NOT NULL, `platform_data_json` json NOT NULL,
  `content_perf_json` json NOT NULL, `lead_funnel_json` json NOT NULL, `root_cause_json` json NOT NULL,
  `next_week_plan_json` json NOT NULL, `suggested_grade` varchar(24) DEFAULT NULL,
  `confirmed_grade` varchar(24) DEFAULT NULL, `confirmed_by_user_id` bigint DEFAULT NULL,
  `confirmation_basis` varchar(2000) DEFAULT NULL, `confirmed_at` datetime DEFAULT NULL,
  `config_version_id` bigint NOT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_account_week` (`tenant_id`,`account_id`,`week_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号周诊断';

CREATE TABLE IF NOT EXISTS `zsjos_standard_form` (
  `id` bigint NOT NULL AUTO_INCREMENT, `form_type_value` varchar(100) NOT NULL,
  `form_type_label_snapshot` varchar(100) NOT NULL, `biz_type` varchar(32) NOT NULL, `biz_id` bigint NOT NULL,
  `form_data_json` json NOT NULL, `filled_by_user_id` bigint NOT NULL, `status` varchar(24) NOT NULL,
  `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_tenant_biz_form` (`tenant_id`,`biz_type`,`biz_id`,`form_type_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体标准业务表单';

CREATE TABLE IF NOT EXISTS `zsjos_media_config_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `version_no` int NOT NULL, `status` varchar(24) NOT NULL,
  `approval_config_json` json NOT NULL, `stage_checklist_json` json NOT NULL,
  `threshold_config_json` json NOT NULL, `account_grade_rules_json` json NOT NULL,
  `published_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_version_no` (`tenant_id`,`version_no`,`deleted`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体工作流配置版本';

INSERT INTO `zsjos_media_config_version`
(`version_no`,`status`,`approval_config_json`,`stage_checklist_json`,`threshold_config_json`,`account_grade_rules_json`,
 `published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 1,'published',JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),JSON_ARRAY(),NOW(),0,
       'migration-v096',NOW(),'migration-v096',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
WHERE tenant.deleted=b'0' AND tenant.status=0
  AND NOT EXISTS (SELECT 1 FROM `zsjos_media_config_version` existing
    WHERE existing.tenant_id=tenant.id AND existing.version_no=1 AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V096','Add new-media content production workflow schema','new-media-content-workflow-schema-v1');
