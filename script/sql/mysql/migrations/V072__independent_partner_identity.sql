-- V072: split兼职合作方 from the System ADMIN account identity.
-- Dependencies/order: apply after V071 during a maintenance window with Partner writes and notification consumers stopped.
-- Data scope: enabled/disabled Partner accounts, their legacy ADMIN bindings/tokens/role grants, Partner-owned business rows,
-- and Partner in-app messages. Converted Partners and historical Flowable snapshots are preserved.
-- Repeatability: guarded DDL, deterministic joins, and version upserts make reruns safe after a successful run.
-- Recovery: take a database backup first. Schema additions may remain, but account/message/token changes require backup restore.
-- This file must not be executed against an existing environment without separate approval.

SELECT GET_LOCK(CONCAT(DATABASE(), ':V072-independent-partner-identity'), 30) INTO @v072_lock;
DROP PROCEDURE IF EXISTS `zsjos_v072_assert_preconditions`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v072_assert_preconditions`()
BEGIN
  IF COALESCE(@v072_lock, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 could not acquire the migration advisory lock';
  END IF;
  IF EXISTS (SELECT 1 FROM `zsjos_partner` WHERE `deleted`=b'0' AND `status` IN ('enabled','disabled')
             AND (`mobile` IS NULL OR TRIM(`mobile`)='') LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 blocked: enabled/disabled Partner has no mobile';
  END IF;
  IF EXISTS (SELECT 1 FROM `zsjos_partner` WHERE `deleted`=b'0' AND `status` IN ('enabled','disabled')
             GROUP BY `tenant_id`,TRIM(`mobile`) HAVING COUNT(*) > 1 LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 blocked: duplicate Partner mobile in one tenant';
  END IF;
  IF EXISTS (SELECT 1 FROM `zsjos_partner` p LEFT JOIN `system_users` u
               ON u.`id`=p.`bound_system_user_id` AND u.`tenant_id`=p.`tenant_id` AND u.`deleted`=b'0'
             WHERE p.`deleted`=b'0' AND p.`status` IN ('enabled','disabled') AND u.`id` IS NULL LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 blocked: Partner has no legacy System account';
  END IF;
  IF EXISTS (SELECT 1 FROM `zsjos_partner` WHERE `deleted`=b'0' AND `status` IN ('enabled','disabled')
             GROUP BY `tenant_id`,`bound_system_user_id` HAVING COUNT(*) > 1 LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 blocked: duplicate Partner System account binding';
  END IF;
  IF EXISTS (SELECT 1 FROM `zsjos_partner` p JOIN `system_users` u
               ON u.`id`=p.`bound_system_user_id` AND u.`tenant_id`=p.`tenant_id` AND u.`deleted`=b'0'
             WHERE p.`deleted`=b'0' AND p.`status` IN ('enabled','disabled')
               AND (u.`password` IS NULL OR u.`password` NOT REGEXP '^\\$2[aby]\\$[0-9]{2}\\$.{53}$') LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V072 blocked: legacy Partner password is not a BCrypt hash';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v072_assert_preconditions`();
DROP PROCEDURE `zsjos_v072_assert_preconditions`;

CREATE TABLE IF NOT EXISTS `zsjos_partner_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Partner 登录账号编号',
  `partner_id` bigint NOT NULL COMMENT '兼职主体编号',
  `mobile` varchar(32) NOT NULL COMMENT '登录手机号',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '账号状态：0启用，1禁用',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录 IP',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_partner` (`tenant_id`,`partner_id`),
  UNIQUE KEY `uk_tenant_mobile` (`tenant_id`,`mobile`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS Partner 独立登录账号';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_partner' AND column_name='email')=0,
  'ALTER TABLE `zsjos_partner` ADD COLUMN `email` varchar(128) DEFAULT NULL COMMENT ''邮箱'' AFTER `mobile`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_partner' AND column_name='avatar')=0,
  'ALTER TABLE `zsjos_partner` ADD COLUMN `avatar` varchar(512) DEFAULT NULL COMMENT ''头像地址'' AFTER `email`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_partner' AND column_name='sex')=0,
  'ALTER TABLE `zsjos_partner` ADD COLUMN `sex` tinyint DEFAULT NULL COMMENT ''性别'' AFTER `avatar`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_partner_account`
(`partner_id`,`mobile`,`password`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT p.`id`,TRIM(p.`mobile`),u.`password`,IF(p.`status`='enabled',0,1),
       'migration-V072',NOW(),'migration-V072',NOW(),b'0',p.`tenant_id`
FROM `zsjos_partner` p JOIN `system_users` u
  ON u.`id`=p.`bound_system_user_id` AND u.`tenant_id`=p.`tenant_id` AND u.`deleted`=b'0'
WHERE p.`deleted`=b'0' AND p.`status` IN ('enabled','disabled')
ON DUPLICATE KEY UPDATE `mobile`=VALUES(`mobile`),`password`=VALUES(`password`),`status`=VALUES(`status`),
  `updater`='migration-V072',`update_time`=NOW(),`deleted`=b'0';

SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_urge' AND column_name='submitter_user_id')='NO',
  'ALTER TABLE `zsjos_lead_urge` MODIFY COLUMN `submitter_user_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_urge' AND column_name='partner_id')=0,
  'ALTER TABLE `zsjos_lead_urge` ADD COLUMN `partner_id` bigint DEFAULT NULL AFTER `submitter_user_id`, ADD KEY `idx_tenant_partner` (`tenant_id`,`partner_id`,`urged_at`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_complaint' AND column_name='complainant_user_id')='NO',
  'ALTER TABLE `zsjos_lead_complaint` MODIFY COLUMN `complainant_user_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_complaint' AND column_name='partner_id')=0,
  'ALTER TABLE `zsjos_lead_complaint` ADD COLUMN `partner_id` bigint DEFAULT NULL AFTER `complainant_user_id`, ADD KEY `idx_tenant_partner` (`tenant_id`,`partner_id`,`id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_appeal' AND column_name='applicant_user_id')='NO',
  'ALTER TABLE `zsjos_lead_appeal` MODIFY COLUMN `applicant_user_id` bigint DEFAULT NULL COMMENT ''内部申请人用户编号''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_appeal' AND column_name='partner_id')=0,
  'ALTER TABLE `zsjos_lead_appeal` ADD COLUMN `partner_id` bigint DEFAULT NULL COMMENT ''兼职主体编号'' AFTER `applicant_user_id`, ADD KEY `idx_tenant_partner` (`tenant_id`,`partner_id`,`id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_lead_urge` r JOIN `zsjos_lead` l ON l.`id`=r.`lead_id` AND l.`tenant_id`=r.`tenant_id`
SET r.`partner_id`=l.`partner_id` WHERE r.`partner_id` IS NULL AND l.`partner_id` IS NOT NULL;
UPDATE `zsjos_lead_complaint` r JOIN `zsjos_lead` l ON l.`id`=r.`lead_id` AND l.`tenant_id`=r.`tenant_id`
SET r.`partner_id`=l.`partner_id` WHERE r.`partner_id` IS NULL AND l.`partner_id` IS NOT NULL;
UPDATE `zsjos_lead_appeal` r JOIN `zsjos_lead` l ON l.`id`=r.`lead_id` AND l.`tenant_id`=r.`tenant_id`
SET r.`partner_id`=l.`partner_id` WHERE r.`partner_id` IS NULL AND l.`partner_id` IS NOT NULL;

SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_cashback' AND column_name='beneficiary_user_id')='NO',
  'ALTER TABLE `zsjos_cashback` MODIFY COLUMN `beneficiary_user_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_partner_bank_card' AND column_name='owner_user_id')='NO',
  'ALTER TABLE `zsjos_partner_bank_card` MODIFY COLUMN `owner_user_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_withdrawal' AND column_name='applicant_user_id')='NO',
  'ALTER TABLE `zsjos_withdrawal` MODIFY COLUMN `applicant_user_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='system_notify_message' AND index_name='uk_notify_rule_user_event')>0,
  'ALTER TABLE `system_notify_message` DROP INDEX `uk_notify_rule_user_event`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='system_notify_message' AND index_name='uk_notify_rule_type_user_event')=0,
  'ALTER TABLE `system_notify_message` ADD UNIQUE KEY `uk_notify_rule_type_user_event` (`tenant_id`,`notify_rule_id`,`user_type`,`user_id`,`source_event_key`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `system_notify_message` m
JOIN `zsjos_partner` p ON p.`tenant_id`=m.`tenant_id` AND p.`bound_system_user_id`=m.`user_id` AND p.`deleted`=b'0'
JOIN `zsjos_partner_account` a ON a.`tenant_id`=p.`tenant_id` AND a.`partner_id`=p.`id` AND a.`deleted`=b'0'
SET m.`user_id`=a.`id`,m.`user_type`=3,m.`updater`='migration-V072',m.`update_time`=NOW()
WHERE m.`user_type`=1 AND p.`status` IN ('enabled','disabled');

DELETE ur FROM `system_user_role` ur JOIN `system_role` r ON r.`id`=ur.`role_id` AND r.`deleted`=b'0'
JOIN `zsjos_partner` p ON p.`bound_system_user_id`=ur.`user_id` AND p.`tenant_id`=ur.`tenant_id` AND p.`deleted`=b'0'
WHERE r.`code`='part_time_partner' AND p.`status` IN ('enabled','disabled');
UPDATE `system_users` u JOIN `zsjos_partner` p
  ON p.`bound_system_user_id`=u.`id` AND p.`tenant_id`=u.`tenant_id` AND p.`deleted`=b'0'
SET u.`status`=1,u.`updater`='migration-V072',u.`update_time`=NOW()
WHERE p.`status` IN ('enabled','disabled');
DELETE t FROM `system_oauth2_access_token` t JOIN `zsjos_partner` p
  ON p.`bound_system_user_id`=t.`user_id` AND p.`tenant_id`=t.`tenant_id` AND p.`deleted`=b'0'
WHERE t.`user_type`=1 AND p.`status` IN ('enabled','disabled');
DELETE t FROM `system_oauth2_refresh_token` t JOIN `zsjos_partner` p
  ON p.`bound_system_user_id`=t.`user_id` AND p.`tenant_id`=t.`tenant_id` AND p.`deleted`=b'0'
WHERE t.`user_type`=1 AND p.`status` IN ('enabled','disabled');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V072','Independent Partner identity','independent-partner-identity-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V072','Independent Partner identity',SHA2('independent-partner-identity-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
SELECT RELEASE_LOCK(CONCAT(DATABASE(), ':V072-independent-partner-identity'));
