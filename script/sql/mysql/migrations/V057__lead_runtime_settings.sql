-- V057: tenant lead runtime settings.
-- Dependency/order: apply after V056.
-- Repeatability: each column is guarded by information_schema and version rows use upsert semantics.
-- Data scope: configuration columns only; existing rules receive the reviewed defaults (5 minutes, auto resolution off).
-- Rollback limitation: application rollback may ignore the columns; retain configured values for audit/redeployment.

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_follow_up_rule' AND column_name='notification_popup_duration_minutes'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `notification_popup_duration_minutes` int NOT NULL DEFAULT 5 COMMENT ''消息通知浮窗时长（分钟）'' AFTER `no_progress_grace_days`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_follow_up_rule' AND column_name='duplicate_auto_resolution_enabled'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_follow_up_rule` ADD COLUMN `duplicate_auto_resolution_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''重复客资自动判重开关'' AFTER `notification_popup_duration_minutes`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V057','tenant lead runtime settings','V057__lead_runtime_settings.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V057','tenant lead runtime settings',SHA2('V057__lead_runtime_settings.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
