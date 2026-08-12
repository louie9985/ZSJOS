-- ZSJOS 客资提交、派单与抢单增量结构
-- MySQL 8.x；不删除或重建业务表，不清理任何业务数据。
-- 执行顺序：扩展主表 -> 创建子表/规则表 -> 扩展历史 -> 迁移字典 -> 初始化规则与权限菜单。
-- 可重复执行；回退时应先停用新入口并保留已产生的客资及派单历史，不建议物理删除字段或数据。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_add_column_if_absent`;
DELIMITER $$
CREATE PROCEDURE `zsjos_add_column_if_absent`(
    IN p_table_name varchar(64), IN p_column_name varchar(64), IN p_definition text)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table_name AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL zsjos_add_column_if_absent('zsjos_lead', 'province_code', "varchar(32) DEFAULT NULL COMMENT '提交时省级地区编码，OTHER 表示其他'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'province_name', "varchar(64) DEFAULT NULL COMMENT '提交时省级地区名称快照'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'city_code', "varchar(32) DEFAULT NULL COMMENT '提交时市级地区编码，OTHER 表示其他'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'city_name', "varchar(64) DEFAULT NULL COMMENT '提交时市级地区名称快照'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'lead_category', "varchar(64) DEFAULT NULL COMMENT '客资分类字典值'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'remark', "varchar(1000) DEFAULT NULL COMMENT '提交备注'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'dispatch_mode', "varchar(32) DEFAULT NULL COMMENT '派单模式：auto 或 specified'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'pending_assignee_user_id', "bigint DEFAULT NULL COMMENT '当前待接单销售用户编号'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'pending_expires_at', "datetime DEFAULT NULL COMMENT '自动派单当前接单截止时间；指定派单为空'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'assignment_attempt_count', "int NOT NULL DEFAULT 0 COMMENT '自动派单已尝试次数'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'assignment_rule_snapshot', "json DEFAULT NULL COMMENT '提交时派单规则快照'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'public_pool_at', "datetime DEFAULT NULL COMMENT '进入抢单池时间'");
CALL zsjos_add_column_if_absent('zsjos_lead', 'submission_idempotency_key', "varchar(128) DEFAULT NULL COMMENT '提交幂等键'");

CREATE TABLE IF NOT EXISTS `zsjos_lead_intended_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客资意向产品编号',
  `lead_id` bigint NOT NULL COMMENT '客资编号',
  `product_ref` varchar(128) NOT NULL COMMENT '产品配置稳定引用',
  `product_name_snapshot` varchar(200) NOT NULL COMMENT '提交时产品名称快照',
  `is_primary` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否主意向',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_product_ref` varchar(128) GENERATED ALWAYS AS (IF(`deleted` = 0, `product_ref`, NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_lead_active_product` (`tenant_id`, `lead_id`, `active_product_ref`),
  KEY `idx_tenant_lead_primary` (`tenant_id`, `lead_id`, `is_primary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资意向产品';

CREATE TABLE IF NOT EXISTS `zsjos_lead_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客资附件编号',
  `lead_id` bigint NOT NULL COMMENT '客资编号',
  `file_url` varchar(1024) NOT NULL COMMENT 'Infra 文件访问地址',
  `original_name` varchar(255) NOT NULL COMMENT '原文件名',
  `content_type` varchar(100) NOT NULL COMMENT 'MIME 类型',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_tenant_lead_sort` (`tenant_id`, `lead_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资图片附件';

CREATE TABLE IF NOT EXISTS `zsjos_lead_assignment_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '派单规则编号',
  `code` varchar(64) NOT NULL COMMENT '规则编码',
  `name` varchar(100) NOT NULL COMMENT '规则名称',
  `strategy_type` varchar(64) NOT NULL COMMENT '策略类型',
  `config_json` json NOT NULL COMMENT '策略参数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资派单规则';

CREATE TABLE IF NOT EXISTS `zsjos_lead_assignment_cursor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮询游标编号',
  `rule_id` bigint NOT NULL COMMENT '派单规则编号',
  `last_sales_user_id` bigint DEFAULT NULL COMMENT '最后一次尝试的销售用户编号',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_rule` (`tenant_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 全局轮询派单游标';

CALL zsjos_add_column_if_absent('zsjos_lead_assignment_history', 'assignment_rule_id', "bigint DEFAULT NULL COMMENT '派单规则编号'");
CALL zsjos_add_column_if_absent('zsjos_lead_assignment_history', 'attempt_no', "int DEFAULT NULL COMMENT '自动派单尝试序号'");
CALL zsjos_add_column_if_absent('zsjos_lead_assignment_history', 'candidate_user_id', "bigint DEFAULT NULL COMMENT '本次候选销售用户编号'");
CALL zsjos_add_column_if_absent('zsjos_lead_assignment_history', 'expires_at', "datetime DEFAULT NULL COMMENT '本次接单截止时间'");
CALL zsjos_add_column_if_absent('zsjos_lead_assignment_history', 'response_at', "datetime DEFAULT NULL COMMENT '响应或超时处理时间'");
DROP PROCEDURE IF EXISTS `zsjos_add_column_if_absent`;

DROP PROCEDURE IF EXISTS `zsjos_add_index_if_absent`;
DELIMITER $$
CREATE PROCEDURE `zsjos_add_index_if_absent`(
    IN p_table_name varchar(64), IN p_index_name varchar(64), IN p_definition text)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table_name AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL zsjos_add_index_if_absent('zsjos_lead', 'uk_tenant_submission_idempotency',
  'UNIQUE INDEX `uk_tenant_submission_idempotency` (`tenant_id`, `submission_idempotency_key`)');
CALL zsjos_add_index_if_absent('zsjos_lead', 'idx_tenant_pending_expiry',
  'INDEX `idx_tenant_pending_expiry` (`tenant_id`, `assignment_status`, `pending_expires_at`)');
CALL zsjos_add_index_if_absent('zsjos_lead', 'idx_tenant_public_pool',
  'INDEX `idx_tenant_public_pool` (`tenant_id`, `assignment_status`, `public_pool_at`)');
DROP PROCEDURE IF EXISTS `zsjos_add_index_if_absent`;

UPDATE `system_dict_type`
SET `name` = '来源渠道', `type` = 'zsjos_lead_source_channel', `updater` = '1', `update_time` = NOW()
WHERE `type` = 'zsjos_lead_source_platform' AND `deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `type` FROM `system_dict_type`) t WHERE t.`type` = 'zsjos_lead_source_channel');
UPDATE `system_dict_data`
SET `dict_type` = 'zsjos_lead_source_channel', `updater` = '1', `update_time` = NOW()
WHERE `dict_type` = 'zsjos_lead_source_platform' AND `deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT `value`, `dict_type`, `deleted` FROM `system_dict_data`) target
    WHERE target.`dict_type` = 'zsjos_lead_source_channel'
      AND target.`value` = `system_dict_data`.`value` AND target.`deleted` = b'0'
  );

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '客资分类', 'zsjos_lead_category', 0, '客资提交时选择的管理员维护分类', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'zsjos_lead_category' AND `deleted` = b'0');

INSERT INTO `zsjos_lead_assignment_rule`
(`code`, `name`, `strategy_type`, `config_json`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'default', '全公司轮询', 'global_round_robin', JSON_OBJECT('acceptTimeoutSeconds', 120, 'maxAttempts', 5),
       0, '1', NOW(), '1', NOW(), b'0', tenant.id
FROM `system_tenant` tenant
WHERE tenant.deleted = b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_assignment_rule` rule_data
  WHERE rule_data.tenant_id = tenant.id AND rule_data.code = 'default' AND rule_data.deleted = b'0'
);

INSERT INTO `zsjos_lead_assignment_cursor`
(`rule_id`, `last_sales_user_id`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rule_data.id, NULL, 0, '1', NOW(), '1', NOW(), b'0', rule_data.tenant_id
FROM `zsjos_lead_assignment_rule` rule_data
WHERE rule_data.code = 'default' AND rule_data.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_lead_assignment_cursor` cursor_data
    WHERE cursor_data.tenant_id = rule_data.tenant_id AND cursor_data.rule_id = rule_data.id
      AND cursor_data.deleted = b'0');

-- 菜单与角色授权只新增定义，不改变既有角色的业务数据范围。
SET @zsjos_parent_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:lead-assignment:query' AND deleted = b'0' LIMIT 1);
SET @menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @menu_id, '客资派单规则', 'zsjos:lead-rule:query', 2, 90,
       COALESCE((SELECT parent_id FROM system_menu WHERE id = @zsjos_parent_id), 0),
       'lead-rule', 'ep:setting', 'zsjos/leadRule/index', 'ZsjosLeadRule', 0, b'1', b'1', b'1',
       '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead-rule:query' AND deleted = b'0');

SET @lead_parent_id = (
  SELECT parent_id FROM system_menu WHERE permission = 'zsjos:lead:submit' AND deleted = b'0' LIMIT 1
);
SET @claim_menu_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:lead:claim' AND deleted = b'0' LIMIT 1);
SET @claim_menu_id = IFNULL(@claim_menu_id, (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu));
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @claim_menu_id, '客资抢单池', 'zsjos:lead:claim', 2, 30, @lead_parent_id,
       'claim-pool', 'ep:takeaway-box', 'zsjos/workbench/leadClaimPool', 'ZsjosLeadClaimPool',
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @lead_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead:claim' AND deleted = b'0');

SET @rule_menu_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:lead-rule:query' AND deleted = b'0' LIMIT 1);
SET @accept_parent_id = COALESCE(@claim_menu_id, @lead_parent_id);
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '客资接单', 'zsjos:lead:accept', 3, 1, @accept_parent_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead:accept' AND deleted = b'0');

SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '修改派单规则', 'zsjos:lead-rule:update', 3, 1, @rule_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @rule_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead-rule:update' AND deleted = b'0');

SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '客资异常转派', 'zsjos:lead:transfer', 3, 2, @rule_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @rule_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead:transfer' AND deleted = b'0');

-- 仅为超级管理员补齐新管理权限；销售与提交员工由管理员按岗位实际授权。
INSERT INTO system_role_menu
(role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, '1', NOW(), '1', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu ON menu.permission IN ('zsjos:lead-rule:query', 'zsjos:lead-rule:update', 'zsjos:lead:transfer')
  AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id = role.id AND rm.menu_id = menu.id AND rm.deleted = b'0');
