SET NAMES utf8mb4;

-- Scope: add non-destructive announcement audience metadata and immutable TARGET recipients.
-- Dependency/order: run after Core V180. Repeatable through guarded DDL and version inserts.
-- Rollback limitation: recipient snapshots for published notices are historical authorization facts and must not be deleted implicitly.

-- MySQL versions used by existing installations do not support
-- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`; guard each additive DDL
-- operation through information_schema instead.
DROP PROCEDURE IF EXISTS `zsjos_v181_add_notice_columns`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v181_add_notice_columns`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='audience_type'
  ) THEN
    ALTER TABLE `system_notice`
      ADD COLUMN `audience_type` varchar(16) DEFAULT 'ALL' COMMENT '接收范围：ALL 全员，TARGET 指定部门/用户';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='target_dept_ids'
  ) THEN
    ALTER TABLE `system_notice`
      ADD COLUMN `target_dept_ids` json DEFAULT NULL COMMENT '草稿选择的部门编号';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='target_user_ids'
  ) THEN
    ALTER TABLE `system_notice`
      ADD COLUMN `target_user_ids` json DEFAULT NULL COMMENT '草稿选择的用户编号';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v181_add_notice_columns`();
DROP PROCEDURE IF EXISTS `zsjos_v181_add_notice_columns`;

CREATE TABLE IF NOT EXISTS `system_notice_recipient` (
  `id` bigint NOT NULL AUTO_INCREMENT, `notice_id` bigint NOT NULL, `user_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notice_recipient` (`tenant_id`,`notice_id`,`user_id`), KEY `idx_notice_recipient_user` (`tenant_id`,`user_id`,`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告指定接收人快照';

UPDATE `system_notice` SET `audience_type`='ALL' WHERE `audience_type` IS NULL;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V181','Targeted announcement recipients',SHA2('V181__notice_recipient_targeting.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V181');
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V181','Targeted announcement recipients',SHA2('V181__notice_recipient_targeting.sql',256),'baseline',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V181');
