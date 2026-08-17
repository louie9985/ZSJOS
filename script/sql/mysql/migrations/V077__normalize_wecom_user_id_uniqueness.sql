-- V077: normalize optional WeCom userids and align tenant-scoped uniqueness.
-- Dependency/order: apply after V076 and before accepting writes from the normalized System user service.
-- Data scope: trim system_users.wecom_user_id and convert blank values to NULL; no user is deleted.
-- Repeatability: the audit and data normalization are stable, and every DDL statement is metadata guarded.
-- Rollback limitation: retain normalized values and the generated unique key; rollback is forward-only.

DROP PROCEDURE IF EXISTS `zsjos_v077_assert_wecom_user_ids`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v077_assert_wecom_user_ids`()
BEGIN
  DECLARE conflict_count bigint DEFAULT 0;
  DECLARE error_message varchar(255);

  SELECT COUNT(*) INTO conflict_count
  FROM (
    SELECT `tenant_id`, TRIM(`wecom_user_id`) AS normalized_wecom_user_id
    FROM `system_users`
    WHERE `wecom_user_id` IS NOT NULL AND TRIM(`wecom_user_id`) <> ''
    GROUP BY `tenant_id`, TRIM(`wecom_user_id`)
    HAVING COUNT(*) > 1
  ) conflicts;

  IF conflict_count > 0 THEN
    SET error_message = CONCAT('V077 blocked: normalized WeCom userid conflicts=', conflict_count);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v077_assert_wecom_user_ids`();
DROP PROCEDURE IF EXISTS `zsjos_v077_assert_wecom_user_ids`;

UPDATE `system_users`
SET `wecom_user_id` = NULLIF(TRIM(`wecom_user_id`), '')
WHERE `wecom_user_id` IS NOT NULL
  AND (`wecom_user_id` <> TRIM(`wecom_user_id`) OR TRIM(`wecom_user_id`) = '');

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='system_users' AND column_name='unique_wecom_user_id'
), 'SELECT 1',
  'ALTER TABLE `system_users` ADD COLUMN `unique_wecom_user_id` varchar(64) GENERATED ALWAYS AS (NULLIF(TRIM(`wecom_user_id`),'''')) STORED COMMENT ''租户内非空企业微信 userid 唯一值'' AFTER `wecom_user_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='system_users' AND index_name='uk_tenant_wecom_user_id'
), 'SELECT 1',
  'ALTER TABLE `system_users` ADD UNIQUE KEY `uk_tenant_wecom_user_id` (`tenant_id`,`unique_wecom_user_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='system_users' AND index_name='uk_system_users_tenant_wecom'
), 'ALTER TABLE `system_users` DROP INDEX `uk_system_users_tenant_wecom`', 'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V077','Normalize tenant WeCom userid uniqueness','V077__normalize_wecom_user_id_uniqueness.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V077','Normalize tenant WeCom userid uniqueness',
        SHA2('V077__normalize_wecom_user_id_uniqueness.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
