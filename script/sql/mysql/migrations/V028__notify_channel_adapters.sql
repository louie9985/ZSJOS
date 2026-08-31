-- Message-center channel adapter metadata. Additive and repeatable; no messages are sent.
DROP PROCEDURE IF EXISTS `zsjos_v028_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v028_add_column`(IN t varchar(64), IN c varchar(64), IN d text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN
    SET @ddl=d; PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v028_add_column`('system_users','wecom_user_id',
  'ALTER TABLE `system_users` ADD COLUMN `wecom_user_id` varchar(64) DEFAULT NULL COMMENT ''企业微信 userid'' AFTER `mobile`');
CALL `zsjos_v028_add_column`('system_notify_template','sms_template_id',
  'ALTER TABLE `system_notify_template` ADD COLUMN `sms_template_id` varchar(64) DEFAULT NULL COMMENT ''短信模板编号'' AFTER `channel_code`');
CALL `zsjos_v028_add_column`('system_notify_template','wecom_message_type',
  'ALTER TABLE `system_notify_template` ADD COLUMN `wecom_message_type` varchar(16) DEFAULT NULL COMMENT ''企微消息类型'' AFTER `sms_template_id`');
DROP PROCEDURE IF EXISTS `zsjos_v028_add_column`;

SET @ddl = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='system_users' AND index_name='uk_system_users_tenant_wecom'), 'SELECT 1',
  'ALTER TABLE `system_users` ADD UNIQUE KEY `uk_system_users_tenant_wecom` (`tenant_id`,`wecom_user_id`)'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V028','Add message-center channel adapter metadata','notify-channel-adapters-v1');
