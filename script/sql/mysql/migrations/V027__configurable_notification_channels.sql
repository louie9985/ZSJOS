-- Configurable notification channels and asynchronous delivery records.
-- Additive and repeatable; no historical messages are re-delivered.
DROP PROCEDURE IF EXISTS `zsjos_v027_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v027_add_column`(IN t varchar(64), IN c varchar(64), IN d text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN
    SET @ddl=d; PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v027_add_column`('system_notify_rule','channel_code',
  'ALTER TABLE `system_notify_rule` ADD COLUMN `channel_code` varchar(32) NOT NULL DEFAULT ''in_app'' COMMENT ''通知渠道'' AFTER `scene_code`');
CALL `zsjos_v027_add_column`('system_notify_template','channel_code',
  'ALTER TABLE `system_notify_template` ADD COLUMN `channel_code` varchar(32) NOT NULL DEFAULT ''in_app'' COMMENT ''通知渠道'' AFTER `scene_code`');
DROP PROCEDURE IF EXISTS `zsjos_v027_add_column`;

CREATE TABLE IF NOT EXISTS `system_notify_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT, `rule_id` bigint NOT NULL, `user_id` bigint NOT NULL,
  `user_type` tinyint NOT NULL DEFAULT 2, `scene_code` varchar(64) NOT NULL,
  `source_event_key` varchar(128) NOT NULL, `channel_code` varchar(32) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0, `retry_count` int NOT NULL DEFAULT 0,
  `last_error` varchar(1024) DEFAULT NULL, `last_attempt_time` datetime DEFAULT NULL,
  `next_retry_time` datetime DEFAULT NULL, `payload` text, `message_id` bigint DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notify_delivery_event` (`tenant_id`,`rule_id`,`user_id`,`source_event_key`,`channel_code`),
  KEY `idx_notify_delivery_status` (`tenant_id`,`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道异步投递记录';

CREATE TABLE IF NOT EXISTS `system_notify_channel_config` (
  `id` bigint NOT NULL AUTO_INCREMENT, `channel_code` varchar(32) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'0', `config_ref` varchar(255) DEFAULT NULL,
  `masked_config` varchar(1024) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notify_channel_tenant` (`tenant_id`,`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户通知渠道配置';

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V027','Add configurable notification channels and delivery records','configurable-notification-channels-v1');
