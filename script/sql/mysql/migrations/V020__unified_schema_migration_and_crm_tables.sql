-- Adds unified module migration metadata and two CRM tables required by enabled code.
-- Dependencies: the existing CRM schema and zsjos_schema_version.
-- Data scope: migration metadata only; no CRM business rows are inserted or changed.
-- Repeatability: all DDL and metadata inserts are guarded.
-- Rollback limitation: retain metadata and CRM tables; disable dependent features instead of dropping data.

CREATE TABLE IF NOT EXISTS `zsjos_module_schema_version` (
  `module_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块编码',
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移版本',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移说明',
  `checksum` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移文件 SHA-256',
  `release_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发行版本',
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`module_code`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 模块数据库迁移版本';

CREATE TABLE IF NOT EXISTS `crm_owner_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` tinyint NOT NULL COMMENT 'CRM 业务类型',
  `biz_id` bigint NOT NULL COMMENT 'CRM 业务编号',
  `pre_owner_user_id` bigint DEFAULT NULL COMMENT '变更前负责人',
  `post_owner_user_id` bigint DEFAULT NULL COMMENT '变更后负责人',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_biz_create_time` (`tenant_id`,`biz_type`,`biz_id`,`create_time`) USING BTREE,
  KEY `idx_tenant_pre_owner_create_time` (`tenant_id`,`pre_owner_user_id`,`create_time`) USING BTREE,
  KEY `idx_tenant_post_owner_create_time` (`tenant_id`,`post_owner_user_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 负责人变更记录表';

CREATE TABLE IF NOT EXISTS `crm_performance_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` tinyint NOT NULL COMMENT '目标类型',
  `object_id` bigint NOT NULL COMMENT '目标对象编号',
  `object_type` tinyint NOT NULL COMMENT '目标对象类型',
  `year` int NOT NULL COMMENT '年份',
  `year_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '年度目标金额，单位：元',
  `january_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '一月目标金额，单位：元',
  `february_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '二月目标金额，单位：元',
  `march_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '三月目标金额，单位：元',
  `april_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '四月目标金额，单位：元',
  `may_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '五月目标金额，单位：元',
  `june_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '六月目标金额，单位：元',
  `july_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '七月目标金额，单位：元',
  `august_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '八月目标金额，单位：元',
  `september_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '九月目标金额，单位：元',
  `october_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '十月目标金额，单位：元',
  `november_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '十一月目标金额，单位：元',
  `december_target_price` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '十二月目标金额，单位：元',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_object_year_biz_deleted` (`tenant_id`,`object_type`,`object_id`,`year`,`biz_type`,`deleted`) USING BTREE,
  KEY `idx_tenant_year_biz` (`tenant_id`,`year`,`biz_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 业绩目标配置表';

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core', `version`, `description`, SHA2(COALESCE(`checksum`, `version`), 256), 'legacy', `installed_at`
FROM `zsjos_schema_version`;

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V020','Add unified schema migration metadata and missing CRM tables','unified-schema-migration-v1');
