-- Generic ZSJOS work-order core. Additive and repeatable; execution against an existing
-- environment requires the normal migration approval. No business rows are seeded.
CREATE TABLE IF NOT EXISTS `zsjos_work_order_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0,
  `code` varchar(64) NOT NULL, `name` varchar(128) NOT NULL, `remark` varchar(500) DEFAULT NULL,
  `source_post_code` varchar(64) NOT NULL, `target_post_code` varchar(64) NOT NULL,
  `assignment_mode` varchar(32) NOT NULL, `fields_json` json NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工单场景';
CREATE TABLE IF NOT EXISTS `zsjos_work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0, `order_no` varchar(64) NOT NULL,
  `scene_code` varchar(64) NOT NULL, `scene_name_snapshot` varchar(128) NOT NULL, `assignment_mode` varchar(32) NOT NULL,
  `source_user_id` bigint NOT NULL, `target_user_id` bigint DEFAULT NULL, `source_name_snapshot` varchar(128) DEFAULT NULL, `target_name_snapshot` varchar(128) DEFAULT NULL,
  `status` varchar(40) NOT NULL, `field_snapshot_json` json NOT NULL, `value_json` json NOT NULL, `attachment_ids_json` json NOT NULL, `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `command_user_id` bigint NOT NULL, `request_fingerprint` varchar(64) NOT NULL,
  `return_reason` varchar(1000) DEFAULT NULL, `claimed_at` datetime DEFAULT NULL, `completed_at` datetime DEFAULT NULL, `accepted_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_order_no` (`tenant_id`,`order_no`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`), KEY `idx_pool` (`tenant_id`,`scene_code`,`status`,`create_time`), KEY `idx_source_user` (`tenant_id`,`source_user_id`,`status`,`create_time`), KEY `idx_target_user` (`tenant_id`,`target_user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 通用工单';
CREATE TABLE IF NOT EXISTS `zsjos_work_order_history` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0, `work_order_id` bigint NOT NULL, `from_status` varchar(40) DEFAULT NULL, `to_status` varchar(40) NOT NULL, `operator_user_id` bigint NOT NULL, `reason` varchar(1000) DEFAULT NULL, `operated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, `operation` varchar(32) NOT NULL, `request_fingerprint` varchar(64) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_order_time` (`tenant_id`,`work_order_id`,`operated_at`), UNIQUE KEY `uk_order_key` (`tenant_id`,`work_order_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工单状态历史';
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V115','Generic work-order core','V115__generic_work_order.sql') ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V115','Generic work-order core',SHA2('V115__generic_work_order.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
