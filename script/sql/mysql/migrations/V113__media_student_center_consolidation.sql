-- V113: consolidate third-party account, content and positioning work into the media-student center.
-- Dependencies/order: apply after V112 and the V096 media schema.
-- Scope: additive configuration/talk persistence plus menu metadata and role grants; no business rows are deleted.
-- Repeatability: guarded DDL and idempotent metadata updates. Recovery is a forward migration that restores menus.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zsjos_media_account_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT, `version_no` int NOT NULL, `status` varchar(20) NOT NULL,
  `fields_json` json NOT NULL, `published_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_media_account_field_version` (`tenant_id`,`version_no`,`deleted`),
  KEY `idx_media_account_field_status` (`tenant_id`,`status`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方账号详情字段配置版本';

SET @v113_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account_field_config' AND column_name='version'), 'SELECT 1',
  'ALTER TABLE `zsjos_media_account_field_config` ADD COLUMN `version` int NOT NULL DEFAULT 0 AFTER `published_at`');
PREPARE v113_stmt FROM @v113_sql; EXECUTE v113_stmt; DEALLOCATE PREPARE v113_stmt;

SET @v113_sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND column_name='detail_config_version_id'), 'SELECT 1',
  'ALTER TABLE `zsjos_media_account` ADD COLUMN `detail_config_version_id` bigint DEFAULT NULL AFTER `nickname`, ADD COLUMN `detail_values_json` json DEFAULT NULL AFTER `detail_config_version_id`, ADD COLUMN `detail_snapshot_json` json DEFAULT NULL AFTER `detail_values_json`');
PREPARE v113_stmt FROM @v113_sql; EXECUTE v113_stmt; DEALLOCATE PREPARE v113_stmt;

CREATE TABLE IF NOT EXISTS `zsjos_media_student_talk_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `student_person_id` bigint NOT NULL,
  `account_id` bigint DEFAULT NULL, `operator_user_id` bigint NOT NULL,
  `content` varchar(2000) NOT NULL, `attachment_file_ids_json` json DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_media_student_talk` (`tenant_id`,`student_person_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编导及运营学员交谈记录';

INSERT INTO `zsjos_media_account_field_config`
(`version_no`,`status`,`fields_json`,`published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 1,'published',JSON_ARRAY(
  JSON_OBJECT('key','uid','label','UID','type','text','required',true,'enabled',true,'sort',10,'searchable',true),
  JSON_OBJECT('key','nickname','label','昵称','type','text','required',true,'enabled',true,'sort',20,'searchable',true)
),NOW(),0,'migration-V113',NOW(),'migration-V113',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_media_account_field_config` c WHERE c.tenant_id=t.id AND c.deleted=b'0');

UPDATE `system_menu` SET `parent_id`=7022,`updater`='migration-V113',`update_time`=NOW()
WHERE `parent_id` IN (6970,6974,6980) AND `type`=3 AND `deleted`=b'0';
UPDATE `system_menu` SET `status`=1,`visible`=b'0',`deleted`=b'1',`updater`='migration-V113',`update_time`=NOW()
WHERE `id` IN (6970,6974,6980) AND `deleted`=b'0';

INSERT IGNORE INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,7022,'migration-V113',NOW(),'migration-V113',NOW(),b'0',r.tenant_id
FROM `system_role` r WHERE r.code='new_media_operator' AND r.status=0 AND r.deleted=b'0';

SET @v113_zsjos_menu_id := (SELECT `id` FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `deleted`=b'0' ORDER BY `id` LIMIT 1);
SET @v113_menu_id_collision := EXISTS(
  SELECT 1 FROM `system_menu` WHERE
    (`id`=73500 AND (`permission`<>'zsjos:media-account-field-config:query' OR `path`<>'/zsjos/media-account-field-config')) OR
    (`id`=73501 AND `permission`<>'zsjos:media-account-field-config:update') OR
    (`id`=73502 AND `permission`<>'zsjos:media-account-field-config:publish')
);
SET @v113_sql := IF(@v113_menu_id_collision,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''V113 menu IDs 73500-73502 are already used by unrelated menus''',
  'SELECT 1');
PREPARE v113_stmt FROM @v113_sql; EXECUTE v113_stmt; DEALLOCATE PREPARE v113_stmt;
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
 `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(73500,'第三方账号字段配置','zsjos:media-account-field-config:query',2,65,@v113_zsjos_menu_id,
 '/zsjos/media-account-field-config','ep:setting','zsjos/mediaAccountFieldConfig/index',
 'ZsjosMediaAccountFieldConfig',0,b'1',b'1',b'0','migration-V113',NOW(),'migration-V113',NOW(),b'0'),
(73501,'更新第三方账号字段配置','zsjos:media-account-field-config:update',3,1,73500,
 '','','',NULL,0,b'1',b'1',b'0','migration-V113',NOW(),'migration-V113',NOW(),b'0'),
(73502,'发布第三方账号字段配置','zsjos:media-account-field-config:publish',3,2,73500,
 '','','',NULL,0,b'1',b'1',b'0','migration-V113',NOW(),'migration-V113',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),
 `type`=VALUES(`type`),`sort`=VALUES(`sort`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
 `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
 `visible`=VALUES(`visible`),`deleted`=b'0',`updater`='migration-V113',`update_time`=NOW();

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V113',NOW(),'migration-V113',NOW(),b'0',r.tenant_id
FROM `system_role` r JOIN `system_menu` m ON m.permission IN (
  'zsjos:media-account-field-config:query','zsjos:media-account-field-config:update',
  'zsjos:media-account-field-config:publish') AND m.deleted=b'0'
WHERE r.code='system_administrator' AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm WHERE rm.role_id=r.id AND rm.menu_id=m.id
    AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0');

UPDATE `system_dict_type` SET `name`='第三方账号平台',`remark`='第三方账号所属平台',`updater`='migration-V113',`update_time`=NOW()
WHERE `type`='zsjos_account_platform' AND `deleted`=b'0';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V113','Consolidate media workflow into student center','media-student-center-v2')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
