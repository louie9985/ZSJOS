-- V149: AI application department requirement, BUG, and technical-support feedback workspace.
-- Dependencies: V115 generic work orders, V147 Workbench navigation, BPM forms, System tenants,
-- menus, tenant packages, and both ZSJOS schema-version registries. V148 is owned elsewhere.
-- Data scope: additive feedback schema, four default BPM forms per active tenant, default feedback
-- settings, and menu/button catalog entries. Existing business rows and administrator edits are kept.
-- Repeatability: schema and seed creation are guarded by stable keys; identity, route, permission,
-- and form-marker conflicts stop execution instead of overwriting unrelated data.
-- Rollback limitation: feedback, replies, approval rounds, surveys, workflow references, and audit
-- snapshots are retained. Removal requires a separately reviewed forward migration.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v149_validate_prerequisites`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v149_validate_prerequisites`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V147'
  ) OR NOT EXISTS (
    SELECT 1 FROM `zsjos_module_schema_version`
    WHERE `module_code` = 'core' AND `version` = 'V147'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V149 requires V147 in both schema-version registries';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 6735 AND `deleted` = b'0'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V149 requires the Workbench ZSJOS root menu 6735';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `bpm_form`
    WHERE `deleted` = b'0'
      AND `remark` IN (
        'zsjos-feedback-form:requirement:1',
        'zsjos-feedback-form:bug:1',
        'zsjos-feedback-form:support:1',
        'zsjos-feedback-form:survey:1'
      )
    GROUP BY `tenant_id`, `remark`
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V149 feedback BPM form marker is duplicated within a tenant';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE (`id` = 79940 AND (`type` <> 2 OR `parent_id` <> 6735 OR `path` <> 'feedback'
            OR NOT (`component` <=> 'zsjos/feedback/index')
            OR NOT (`permission` <=> 'zsjos:feedback:query')))
       OR (`id` = 79941 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:requirement:create'))
       OR (`id` = 79942 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:bug:create'))
       OR (`id` = 79943 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:support:create'))
       OR (`id` = 79944 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:reply-self'))
       OR (`id` = 79945 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:survey:submit'))
       OR (`id` = 79946 AND (`type` <> 3 OR `parent_id` <> 79940
            OR `permission` <> 'zsjos:feedback:read'))
       OR (`id` = 79947 AND (`type` <> 1 OR `parent_id` <> 0 OR `path` <> '/feedback-management'))
       OR (`id` = 79948 AND (`type` <> 2 OR `parent_id` <> 79947 OR `path` <> 'requirements'
            OR `permission` <> 'zsjos:feedback:requirement:manage'))
       OR (`id` = 79949 AND (`type` <> 2 OR `parent_id` <> 79947 OR `path` <> 'bugs'
            OR `permission` <> 'zsjos:feedback:bug:manage'))
       OR (`id` = 79950 AND (`type` <> 2 OR `parent_id` <> 79947 OR `path` <> 'support'
            OR `permission` <> 'zsjos:feedback:support:manage'))
       OR (`id` = 79951 AND (`type` <> 2 OR `parent_id` <> 79947 OR `path` <> 'settings'
            OR `permission` <> 'zsjos:feedback:settings'))
       OR (`id` = 79952 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:query-admin'))
       OR (`id` = 79953 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:assign'))
       OR (`id` = 79954 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:reply'))
       OR (`id` = 79955 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:complete'))
       OR (`id` = 79956 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:survey'))
       OR (`id` = 79957 AND (`type` <> 3 OR `parent_id` <> 79947
            OR `permission` <> 'zsjos:feedback:settings:save'))
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V149 feedback menu ID is owned by another route or permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` NOT BETWEEN 79940 AND 79957
      AND `permission` IN (
        'zsjos:feedback:query',
        'zsjos:feedback:requirement:create',
        'zsjos:feedback:bug:create',
        'zsjos:feedback:support:create',
        'zsjos:feedback:reply-self',
        'zsjos:feedback:survey:submit',
        'zsjos:feedback:read',
        'zsjos:feedback:requirement:manage',
        'zsjos:feedback:bug:manage',
        'zsjos:feedback:support:manage',
        'zsjos:feedback:settings',
        'zsjos:feedback:query-admin',
        'zsjos:feedback:assign',
        'zsjos:feedback:reply',
        'zsjos:feedback:complete',
        'zsjos:feedback:survey',
        'zsjos:feedback:settings:save'
      )
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V149 feedback permission already uses another menu ID';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` <> 79940
      AND `parent_id` = 6735 AND `path` = 'feedback'
  ) OR EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` <> 79947
      AND `parent_id` = 0 AND `path` = '/feedback-management'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V149 feedback route already uses another menu ID';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` BETWEEN 79940 AND 79957 AND `deleted` = b'1'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V149 feedback menu ID is retired; review before restoring it';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v149_validate_prerequisites`();
DROP PROCEDURE `zsjos_v149_validate_prerequisites`;

DROP PROCEDURE IF EXISTS `zsjos_v149_add_work_order_business_type`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v149_add_work_order_business_type`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'zsjos_work_order'
      AND `column_name` = 'business_type'
  ) THEN
    ALTER TABLE `zsjos_work_order`
      ADD COLUMN `business_type` varchar(16) NOT NULL DEFAULT 'GENERIC'
      COMMENT '业务类型：GENERIC、FEEDBACK' AFTER `tenant_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'zsjos_work_order'
      AND `index_name` = 'idx_tenant_business_type'
  ) THEN
    ALTER TABLE `zsjos_work_order`
      ADD KEY `idx_tenant_business_type` (`tenant_id`, `business_type`, `create_time`);
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v149_add_work_order_business_type`();
DROP PROCEDURE `zsjos_v149_add_work_order_business_type`;

UPDATE `zsjos_work_order`
SET `business_type` = 'GENERIC'
WHERE `business_type` IS NULL OR `business_type` = '';

CREATE TABLE IF NOT EXISTS `zsjos_feedback_no_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sequence_date` date NOT NULL COMMENT '流水日期',
  `feedback_type` varchar(16) NOT NULL COMMENT '反馈类型',
  `current_value` bigint NOT NULL DEFAULT 0 COMMENT '当前流水值',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_date_type` (`tenant_id`, `sequence_date`, `feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈业务编号日流水';

CREATE TABLE IF NOT EXISTS `zsjos_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '反馈内部编号',
  `work_order_id` bigint NOT NULL COMMENT '通用工单内部编号',
  `feedback_type` varchar(16) NOT NULL COMMENT 'REQUIREMENT、BUG、SUPPORT',
  `feedback_no` varchar(32) NOT NULL COMMENT '反馈业务编号',
  `title` varchar(255) NOT NULL COMMENT '标题快照',
  `title_field_key` varchar(64) NOT NULL COMMENT '标题字段 key 快照',
  `form_id` bigint NOT NULL COMMENT 'BPM 动态表单编号',
  `form_snapshot_json` longtext NOT NULL COMMENT '表单定义快照',
  `value_snapshot_json` longtext NOT NULL COMMENT '字段值与展示标签快照',
  `support_dict_type` varchar(100) DEFAULT NULL COMMENT '技术支持类型字典',
  `support_type_value` varchar(100) DEFAULT NULL COMMENT '技术支持类型值',
  `support_type_label_snapshot` varchar(100) DEFAULT NULL COMMENT '技术支持类型标签快照',
  `status` varchar(24) NOT NULL COMMENT '员工端统一状态',
  `submitter_user_id` bigint NOT NULL COMMENT '提交人用户编号',
  `submitter_name_snapshot` varchar(128) DEFAULT NULL COMMENT '提交人名称快照',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '当前处理人用户编号',
  `assignee_name_snapshot` varchar(128) DEFAULT NULL COMMENT '当前处理人名称快照',
  `last_reply_summary` varchar(500) DEFAULT NULL COMMENT '最新回复摘要',
  `last_activity_at` datetime NOT NULL COMMENT '最后活动时间',
  `unread_for_submitter` bit(1) NOT NULL DEFAULT b'0' COMMENT '提交人未读',
  `unread_for_assignee` bit(1) NOT NULL DEFAULT b'0' COMMENT '处理人未读',
  `approval_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '需求是否启用审批',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '当前 BPM 流程实例编号',
  `approval_round_no` int NOT NULL DEFAULT 0 COMMENT '当前审批轮次',
  `reject_reason` varchar(1000) DEFAULT NULL COMMENT '审批驳回原因',
  `completed_result` text DEFAULT NULL COMMENT '处理结果',
  `result_attachment_ids_json` json DEFAULT NULL COMMENT '处理结果附件快照',
  `config_version` int NOT NULL DEFAULT 0 COMMENT '提交时设置版本',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_work_order` (`tenant_id`, `work_order_id`),
  UNIQUE KEY `uk_tenant_feedback_no` (`tenant_id`, `feedback_no`),
  KEY `idx_submitter_activity` (`tenant_id`, `submitter_user_id`, `last_activity_at`),
  KEY `idx_type_status_activity` (`tenant_id`, `feedback_type`, `status`, `last_activity_at`),
  KEY `idx_assignee_status` (`tenant_id`, `assignee_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 需求与反馈扩展';

CREATE TABLE IF NOT EXISTS `zsjos_feedback_round` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮次编号',
  `feedback_id` bigint NOT NULL COMMENT '反馈内部编号',
  `round_no` int NOT NULL COMMENT '审批轮次',
  `status` varchar(24) NOT NULL COMMENT '轮次状态',
  `form_snapshot_json` longtext NOT NULL COMMENT '本轮表单定义快照',
  `value_snapshot_json` longtext NOT NULL COMMENT '本轮字段值快照',
  `approval_context_json` longtext NOT NULL COMMENT '本轮审批人与名称快照',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例编号',
  `business_key` varchar(180) NOT NULL COMMENT 'BPM 业务键',
  `reject_reason` varchar(1000) DEFAULT NULL COMMENT '驳回原因',
  `submitted_at` datetime NOT NULL COMMENT '提交时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_round` (`tenant_id`, `feedback_id`, `round_no`),
  UNIQUE KEY `uk_feedback_round_key` (`tenant_id`, `business_key`),
  KEY `idx_feedback_round` (`tenant_id`, `feedback_id`, `round_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈提交审批轮次快照';

CREATE TABLE IF NOT EXISTS `zsjos_feedback_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回复编号',
  `feedback_id` bigint NOT NULL COMMENT '反馈内部编号',
  `author_user_id` bigint NOT NULL COMMENT '回复人用户编号',
  `author_name_snapshot` varchar(128) DEFAULT NULL COMMENT '回复人名称快照',
  `author_type` varchar(16) NOT NULL COMMENT 'EMPLOYEE、ADMIN',
  `content` text NOT NULL COMMENT '回复内容',
  `attachment_ids_json` json DEFAULT NULL COMMENT '回复附件快照',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '幂等键',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_reply_key` (`tenant_id`, `feedback_id`, `idempotency_key`),
  KEY `idx_feedback_reply_time` (`tenant_id`, `feedback_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈沟通回复';

CREATE TABLE IF NOT EXISTS `zsjos_feedback_survey` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '满意度调查编号',
  `feedback_id` bigint NOT NULL COMMENT '反馈内部编号',
  `status` varchar(16) NOT NULL COMMENT 'PENDING、SUBMITTED',
  `form_id` bigint NOT NULL COMMENT 'BPM 动态表单编号',
  `form_snapshot_json` longtext NOT NULL COMMENT '满意度表单定义快照',
  `value_snapshot_json` longtext DEFAULT NULL COMMENT '满意度字段值快照',
  `requested_by_user_id` bigint NOT NULL COMMENT '发起人用户编号',
  `requested_by_name_snapshot` varchar(128) DEFAULT NULL COMMENT '发起人名称快照',
  `requested_at` datetime NOT NULL COMMENT '发起时间',
  `submitter_user_id` bigint NOT NULL COMMENT '应答员工用户编号',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_survey` (`tenant_id`, `feedback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈满意度调查';

CREATE TABLE IF NOT EXISTS `zsjos_feedback_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设置编号',
  `feedback_type` varchar(16) NOT NULL COMMENT 'REQUIREMENT、BUG、SUPPORT、SURVEY',
  `form_id` bigint NOT NULL COMMENT 'BPM 动态表单编号',
  `title_field_key` varchar(64) NOT NULL COMMENT '标题字段 key，满意度为 rating',
  `dispatcher_user_ids_json` json NOT NULL COMMENT '分派负责人用户编号',
  `approval_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否开启需求审批',
  `bpm_process_definition_key` varchar(128) DEFAULT NULL COMMENT '已发布 BPM 流程 Key',
  `last_idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近保存幂等键',
  `last_request_fingerprint` varchar(64) DEFAULT NULL COMMENT '最近保存请求指纹',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_feedback_type` (`tenant_id`, `feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈设置';

SET @zsjos_feedback_form_conf = '{"form":{"labelPosition":"top","labelWidth":"120px","size":"default"},"submitBtn":false,"resetBtn":false}';
SET @zsjos_feedback_title = '{"type":"input","field":"title","title":"标题","props":{"maxlength":255},"validate":[{"required":true,"message":"请输入标题"}],"hidden":false,"display":true}';
SET @zsjos_feedback_bug_title = '{"type":"input","field":"title","title":"问题标题","props":{"maxlength":255},"validate":[{"required":true,"message":"请输入问题标题"}],"hidden":false,"display":true}';
SET @zsjos_feedback_description = '{"type":"textarea","field":"description","title":"需求说明","props":{"maxlength":5000},"validate":[{"required":true,"message":"请输入需求说明"}],"hidden":false,"display":true}';
SET @zsjos_feedback_problem = '{"type":"textarea","field":"description","title":"问题描述","props":{"maxlength":5000},"validate":[{"required":true,"message":"请输入问题描述"}],"hidden":false,"display":true}';
SET @zsjos_feedback_expected = '{"type":"textarea","field":"expectedEffect","title":"期望效果","props":{"maxlength":3000},"hidden":false,"display":true}';
SET @zsjos_feedback_expected_date = '{"type":"date","field":"expectedDate","title":"期望完成时间","props":{"valueFormat":"YYYY-MM-DD"},"hidden":false,"display":true}';
SET @zsjos_feedback_attachments = '{"type":"upload","field":"attachments","title":"附件","props":{"limit":20},"hidden":false,"display":true}';
SET @zsjos_feedback_support_type = '{"type":"select","field":"supportType","title":"支持类型","dictType":"zsjos_feedback_support_type","validate":[{"required":true,"message":"请选择支持类型"}],"hidden":false,"display":true}';
SET @zsjos_feedback_rating = '{"type":"rate","field":"rating","title":"总体满意度","props":{"max":5},"validate":[{"required":true,"message":"请评价总体满意度"}],"hidden":false,"display":true}';
SET @zsjos_feedback_comment = '{"type":"textarea","field":"comment","title":"文字评价","props":{"maxlength":1000},"hidden":false,"display":true}';

INSERT INTO `bpm_form`
  (`name`,`status`,`conf`,`fields`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.`name`,0,@zsjos_feedback_form_conf,seed.`fields`,seed.`marker`,
       'migration-V149',NOW(),'migration-V149',NOW(),b'0',tenant.`id`
FROM `system_tenant` tenant
CROSS JOIN (
  SELECT 'ZSJOS 需求反馈表单' AS `name`,
         JSON_ARRAY(@zsjos_feedback_title,@zsjos_feedback_description,@zsjos_feedback_expected,
                    @zsjos_feedback_expected_date,@zsjos_feedback_attachments) AS `fields`,
         'zsjos-feedback-form:requirement:1' AS `marker`
  UNION ALL
  SELECT 'ZSJOS BUG 反馈表单',
         JSON_ARRAY(@zsjos_feedback_bug_title,@zsjos_feedback_problem,@zsjos_feedback_attachments),
         'zsjos-feedback-form:bug:1'
  UNION ALL
  SELECT 'ZSJOS 技术支持表单',
         JSON_ARRAY(@zsjos_feedback_support_type,@zsjos_feedback_bug_title,
                    @zsjos_feedback_problem,@zsjos_feedback_attachments),
         'zsjos-feedback-form:support:1'
  UNION ALL
  SELECT 'ZSJOS 满意度表单',
         JSON_ARRAY(@zsjos_feedback_rating,@zsjos_feedback_comment),
         'zsjos-feedback-form:survey:1'
) seed
WHERE tenant.`deleted` = b'0' AND tenant.`status` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `bpm_form` existing
    WHERE existing.`tenant_id` = tenant.`id`
      AND existing.`remark` = seed.`marker`
      AND existing.`deleted` = b'0'
  );

INSERT INTO `zsjos_feedback_config`
  (`tenant_id`,`feedback_type`,`form_id`,`title_field_key`,`dispatcher_user_ids_json`,
   `approval_enabled`,`bpm_process_definition_key`,`version`,`creator`,`create_time`,
   `updater`,`update_time`,`deleted`)
SELECT tenant.`id`,seed.`feedback_type`,form.`id`,seed.`title_field_key`,JSON_ARRAY(),
       seed.`approval_enabled`,seed.`process_key`,0,'migration-V149',NOW(),
       'migration-V149',NOW(),b'0'
FROM `system_tenant` tenant
CROSS JOIN (
  SELECT 'REQUIREMENT' AS `feedback_type`, 'zsjos-feedback-form:requirement:1' AS `marker`,
         'title' AS `title_field_key`, b'1' AS `approval_enabled`,
         'zsjos_feedback_requirement_approval' AS `process_key`
  UNION ALL
  SELECT 'BUG','zsjos-feedback-form:bug:1','title',b'0',NULL
  UNION ALL
  SELECT 'SUPPORT','zsjos-feedback-form:support:1','title',b'0',NULL
  UNION ALL
  SELECT 'SURVEY','zsjos-feedback-form:survey:1','rating',b'0',NULL
) seed
INNER JOIN `bpm_form` form
  ON form.`tenant_id` = tenant.`id`
 AND form.`remark` = seed.`marker`
 AND form.`deleted` = b'0'
WHERE tenant.`deleted` = b'0' AND tenant.`status` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_feedback_config` existing
    WHERE existing.`tenant_id` = tenant.`id`
      AND existing.`feedback_type` = seed.`feedback_type`
  );

INSERT INTO `system_notify_template`
  (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,
   `status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`name`,seed.`code`,'中世健消息中心',seed.`scene_code`,seed.`title`,seed.`summary`,
       seed.`content`,2,'["feedbackNo","feedbackTitle","deepLink"]',0,
       'V149 需求与反馈站内通知','migration-V149',NOW(),'migration-V149',NOW(),b'0'
FROM (
  SELECT '员工补充反馈' AS `name`,'ZSJOS_FEEDBACK_EMPLOYEE_REPLIED' AS `code`,
         'zsjos.feedback.employee_replied' AS `scene_code`,'反馈有新的员工回复' AS `title`,
         '员工补充了反馈内容' AS `summary`,
         '反馈 {{feedbackNo}}「{{feedbackTitle}}」有新的员工回复，请及时查看处理。' AS `content`
  UNION ALL
  SELECT '后台回复反馈','ZSJOS_FEEDBACK_ADMIN_REPLIED','zsjos.feedback.admin_replied',
         '您的反馈有新回复','反馈处理人员回复了您的反馈',
         '反馈 {{feedbackNo}}「{{feedbackTitle}}」有新的处理回复，请及时查看。'
  UNION ALL
  SELECT '反馈处理完成','ZSJOS_FEEDBACK_COMPLETED','zsjos.feedback.completed',
         '您的反馈已处理完成','反馈处理人员已填写处理结果',
         '反馈 {{feedbackNo}}「{{feedbackTitle}}」已处理完成，请查看处理结果。'
  UNION ALL
  SELECT '反馈满意度调研','ZSJOS_FEEDBACK_SURVEY_REQUESTED','zsjos.feedback.survey_requested',
         '请评价本次反馈处理','反馈处理人员发起了满意度调研',
         '反馈 {{feedbackNo}}「{{feedbackTitle}}」已发起满意度调研，请完成评价。'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template` existing
  WHERE existing.`code` = seed.`code` AND existing.`deleted` = b'0'
);

INSERT INTO `system_notify_rule`
  (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,
   `action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.`name`,seed.`scene_code`,'in_app',template.`id`,seed.`recipient_roles`,'[]',
       'business_detail',0,'migration-V149',NOW(),'migration-V149',NOW(),b'0',tenant.`id`
FROM `system_tenant` tenant
CROSS JOIN (
  SELECT '员工补充反馈通知' AS `name`,'zsjos.feedback.employee_replied' AS `scene_code`,
         'ZSJOS_FEEDBACK_EMPLOYEE_REPLIED' AS `template_code`,'["handler"]' AS `recipient_roles`
  UNION ALL
  SELECT '后台回复反馈通知','zsjos.feedback.admin_replied',
         'ZSJOS_FEEDBACK_ADMIN_REPLIED','["submitter"]'
  UNION ALL
  SELECT '反馈处理完成通知','zsjos.feedback.completed',
         'ZSJOS_FEEDBACK_COMPLETED','["submitter"]'
  UNION ALL
  SELECT '反馈满意度调研通知','zsjos.feedback.survey_requested',
         'ZSJOS_FEEDBACK_SURVEY_REQUESTED','["submitter"]'
) seed
INNER JOIN `system_notify_template` template
  ON template.`code` = seed.`template_code` AND template.`deleted` = b'0'
WHERE tenant.`deleted` = b'0' AND tenant.`status` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_rule` existing
    WHERE existing.`tenant_id` = tenant.`id`
      AND existing.`scene_code` = seed.`scene_code`
      AND existing.`deleted` = b'0'
  );

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,
   `updater`,`update_time`,`deleted`)
SELECT seed.`id`,seed.`name`,seed.`permission`,seed.`type`,seed.`sort`,seed.`parent_id`,seed.`path`,
       seed.`icon`,seed.`component`,seed.`component_name`,seed.`render_mode`,0,b'1',b'1',b'1',
       'migration-V149',NOW(),'migration-V149',NOW(),b'0'
FROM (
  SELECT 79940 AS `id`,'需求与反馈' AS `name`,'zsjos:feedback:query' AS `permission`,
         2 AS `type`,40 AS `sort`,6735 AS `parent_id`,'feedback' AS `path`,
         'ep:chat-line-square' AS `icon`,'zsjos/feedback/index' AS `component`,
         'ZsjosFeedback' AS `component_name`,'native' AS `render_mode`
  UNION ALL SELECT 79941,'创建需求','zsjos:feedback:requirement:create',3,1,79940,'','','',NULL,'native'
  UNION ALL SELECT 79942,'创建 BUG 反馈','zsjos:feedback:bug:create',3,2,79940,'','','',NULL,'native'
  UNION ALL SELECT 79943,'创建技术支持','zsjos:feedback:support:create',3,3,79940,'','','',NULL,'native'
  UNION ALL SELECT 79944,'员工回复反馈','zsjos:feedback:reply-self',3,4,79940,'','','',NULL,'native'
  UNION ALL SELECT 79945,'提交满意度','zsjos:feedback:survey:submit',3,5,79940,'','','',NULL,'native'
  UNION ALL SELECT 79946,'查看本人反馈','zsjos:feedback:read',3,6,79940,'','','',NULL,'native'
  UNION ALL SELECT 79947,'反馈管理','',1,40,0,'/feedback-management','ep:service','',NULL,'admin_only'
  UNION ALL SELECT 79948,'需求管理','zsjos:feedback:requirement:manage',2,1,79947,'requirements','ep:document','zsjos/feedback/requirement','ZsjosFeedbackRequirement','admin_only'
  UNION ALL SELECT 79949,'BUG 管理','zsjos:feedback:bug:manage',2,2,79947,'bugs','ep:warning','zsjos/feedback/bug','ZsjosFeedbackBug','admin_only'
  UNION ALL SELECT 79950,'技术支持','zsjos:feedback:support:manage',2,3,79947,'support','ep:question-filled','zsjos/feedback/support','ZsjosFeedbackSupport','admin_only'
  UNION ALL SELECT 79951,'反馈设置','zsjos:feedback:settings',2,4,79947,'settings','ep:setting','zsjos/feedback/settings','ZsjosFeedbackSettings','admin_only'
  UNION ALL SELECT 79952,'查询反馈','zsjos:feedback:query-admin',3,1,79947,'','','',NULL,'admin_only'
  UNION ALL SELECT 79953,'分派反馈','zsjos:feedback:assign',3,2,79947,'','','',NULL,'admin_only'
  UNION ALL SELECT 79954,'回复反馈','zsjos:feedback:reply',3,3,79947,'','','',NULL,'admin_only'
  UNION ALL SELECT 79955,'完成反馈','zsjos:feedback:complete',3,4,79947,'','','',NULL,'admin_only'
  UNION ALL SELECT 79956,'发起满意度','zsjos:feedback:survey',3,5,79947,'','','',NULL,'admin_only'
  UNION ALL SELECT 79957,'保存反馈设置','zsjos:feedback:settings:save',3,6,79947,'','','',NULL,'admin_only'
) seed
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing WHERE existing.`id` = seed.`id`);

UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79940),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79940', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79941),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79941', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79942),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79942', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79943),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79943', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79944),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79944', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79945),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79945', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79946),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79946', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79947),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79947', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79948),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79948', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79949),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79949', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79950),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79950', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79951),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79951', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79952),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79952', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79953),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79953', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79954),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79954', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79955),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79955', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79956),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79956', '$');
UPDATE `system_tenant_package` package
SET package.`menu_ids` = JSON_ARRAY_APPEND(package.`menu_ids`, '$', 79957),
    package.`updater` = 'migration-V149', package.`update_time` = NOW()
WHERE package.`deleted` = b'0' AND JSON_CONTAINS(package.`menu_ids`, '6735', '$')
  AND NOT JSON_CONTAINS(package.`menu_ids`, '79957', '$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V149','Feedback management workspace',
       SHA2('V149__feedback_management.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V149');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V149','Feedback management workspace',
       SHA2('V149__feedback_management.sql',256),'baseline',NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code` = 'core' AND `version` = 'V149'
);
