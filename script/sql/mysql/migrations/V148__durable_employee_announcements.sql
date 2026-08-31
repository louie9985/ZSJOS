-- V148: durable employee announcements with attachments and per-user read state.
-- Dependencies: V147, System notice/menu tables, and Infra file metadata.
-- Repeatable and non-destructive: historical notices become drafts; no role receives a grant.
-- Rollback limitation: published/read history must be retained and removed only by a reviewed forward migration.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v148_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v148_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V147')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V147') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V148 requires V147 in both schema-version registries';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79910 AND (`parent_id`<>6735 OR `path`<>'announcements')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 79910 is owned by another page';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id` IN (79911,79912) AND `permission` NOT IN ('system:notice:publish','system:notice:offline')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A V148 notice action menu ID is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id`<>79910 AND `permission`='system:notice:read') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Notice read permission already uses another menu ID';
  END IF;

  SET @v148_sql = IF((SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema`=DATABASE() AND `table_name`='system_notice'
      AND `column_name`='publish_status')=0,
    'ALTER TABLE `system_notice` ADD COLUMN `publish_status` varchar(16) NOT NULL DEFAULT ''DRAFT'' COMMENT ''发布状态：DRAFT、PUBLISHED、OFFLINE'' AFTER `status`',
    'SELECT 1');
  PREPARE v148_stmt FROM @v148_sql;
  EXECUTE v148_stmt;
  DEALLOCATE PREPARE v148_stmt;

  SET @v148_sql = IF((SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema`=DATABASE() AND `table_name`='system_notice'
      AND `column_name`='publish_time')=0,
    'ALTER TABLE `system_notice` ADD COLUMN `publish_time` datetime DEFAULT NULL COMMENT ''发布时间'' AFTER `publish_status`',
    'SELECT 1');
  PREPARE v148_stmt FROM @v148_sql;
  EXECUTE v148_stmt;
  DEALLOCATE PREPARE v148_stmt;

  SET @v148_sql = IF((SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema`=DATABASE() AND `table_name`='system_notice'
      AND `column_name`='offline_time')=0,
    'ALTER TABLE `system_notice` ADD COLUMN `offline_time` datetime DEFAULT NULL COMMENT ''下线时间'' AFTER `publish_time`',
    'SELECT 1');
  PREPARE v148_stmt FROM @v148_sql;
  EXECUTE v148_stmt;
  DEALLOCATE PREPARE v148_stmt;

  CREATE TABLE IF NOT EXISTS `system_notice_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT, `notice_id` bigint NOT NULL, `infra_file_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL, `mime_type` varchar(128) DEFAULT NULL, `file_size` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT '0', `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notice_file` (`tenant_id`,`notice_id`,`infra_file_id`),
  KEY `idx_notice_attachment` (`tenant_id`,`notice_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告附件';

  CREATE TABLE IF NOT EXISTS `system_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT, `notice_id` bigint NOT NULL, `user_id` bigint NOT NULL,
  `read_time` datetime NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notice_reader` (`tenant_id`,`notice_id`,`user_id`),
  KEY `idx_notice_reader` (`tenant_id`,`user_id`,`read_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告阅读记录';

  IF (SELECT COUNT(*) FROM `information_schema`.`columns`
      WHERE `table_schema`=DATABASE() AND `table_name`='system_notice'
        AND `column_name` IN ('publish_status','publish_time','offline_time'))<>3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V148 failed to install all system_notice lifecycle columns';
  END IF;

  START TRANSACTION;

INSERT INTO `system_menu`
 (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
  `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 79910,'公告中心','system:notice:read',2,95,6735,'announcements','ep:notification',
       'system/notice/workbench','AnnouncementCenterPage','native',0,b'1',b'1',b'1','V148',NOW(),'V148',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79910);

INSERT INTO `system_menu`
 (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
  `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`id`,seed.`name`,seed.`permission`,3,seed.`sort`,107,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V148',NOW(),'V148',NOW(),b'0'
FROM (
  SELECT 79911 AS `id`,'发布公告' AS `name`,'system:notice:publish' AS `permission`,5 AS `sort`
  UNION ALL SELECT 79912,'下线公告','system:notice:offline',6
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing WHERE existing.`id`=seed.`id`);

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79910),`updater`='V148',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6735','$') AND NOT JSON_CONTAINS(`menu_ids`,'79910','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79911),`updater`='V148',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'107','$') AND NOT JSON_CONTAINS(`menu_ids`,'79911','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79912),`updater`='V148',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'107','$') AND NOT JSON_CONTAINS(`menu_ids`,'79912','$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V148','Durable employee announcements',SHA2('V148__durable_employee_announcements.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V148');
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V148','Durable employee announcements',SHA2('V148__durable_employee_announcements.sql',256),'baseline',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V148');

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v148_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v148_apply`;
