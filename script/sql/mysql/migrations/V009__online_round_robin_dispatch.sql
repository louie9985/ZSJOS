-- Adds persistent sales intake preferences for Redis-backed online round-robin dispatch.
-- Dependencies: V008, system users, and the existing zsjos_lead assignment lifecycle.
-- Execution order: create preference table, then record the schema version.
-- Repeatability: CREATE TABLE IF NOT EXISTS and INSERT IGNORE make the script repeatable.
-- Data scope: schema metadata only; no sales preference rows or lead rows are seeded or changed.
-- Recovery: forward-only; the table may remain unused if application code is rolled back.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

CREATE TABLE IF NOT EXISTS `zsjos_sales_dispatch_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '销售派单偏好编号',
  `user_id` bigint NOT NULL COMMENT '系统用户编号',
  `accepting_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否开启自动接单',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售自动派单偏好';

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V009','Add online round-robin sales dispatch preference','online-round-robin-dispatch-v1');
