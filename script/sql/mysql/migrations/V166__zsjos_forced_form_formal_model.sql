-- V166: formal forced-form model.
-- Dependency: V165 forced-form skeleton tables.
-- Scope: additive schema, indexes and server-owned menu/button permissions only.
-- Repeatability: metadata guards make every DDL/menu/package write safe to rerun.
-- Rollback: forward-only; disable the feature or add a reviewed repair migration. Do not
-- drop tables while any form version, batch, recipient, submission or attachment audit is required.

DROP PROCEDURE IF EXISTS `zsjos_v166_forced_form`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v166_forced_form`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V166 requires V165 zsjos_forced_form';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V166 requires V165 zsjos_forced_form_recipient';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V166 requires V165 zsjos_forced_form_submission';
  END IF;

  CREATE TABLE IF NOT EXISTS `zsjos_forced_form_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL DEFAULT 0,
    `form_id` bigint NOT NULL,
    `version_no` int NOT NULL,
    `fields_json` text NOT NULL,
    `schema_hash` varchar(64) NOT NULL,
    `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
    `published_at` datetime NULL,
    `creator` varchar(64) NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_form_version` (`tenant_id`,`form_id`,`version_no`),
    KEY `idx_tenant_form` (`tenant_id`,`form_id`),
    KEY `idx_published_at` (`tenant_id`,`published_at`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单版本';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form' AND column_name='current_version_id') THEN
    ALTER TABLE `zsjos_forced_form` ADD COLUMN `current_version_id` bigint NULL COMMENT '当前版本ID' AFTER `version`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form' AND index_name='idx_tenant_current_version') THEN
    ALTER TABLE `zsjos_forced_form` ADD KEY `idx_tenant_current_version` (`tenant_id`,`current_version_id`);
  END IF;

  CREATE TABLE IF NOT EXISTS `zsjos_forced_form_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL DEFAULT 0,
    `form_id` bigint NOT NULL,
    `version_id` bigint NOT NULL,
    `scope_type` varchar(20) NOT NULL,
    `scope_config_json` text NULL,
    `status` varchar(20) NOT NULL DEFAULT 'SENT',
    `sent_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `creator` varchar(64) NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_form` (`tenant_id`,`form_id`),
    KEY `idx_version` (`tenant_id`,`version_id`),
    KEY `idx_sent_at` (`tenant_id`,`sent_at`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单发送批次';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND column_name='batch_id') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD COLUMN `batch_id` bigint NULL COMMENT '发送批次ID' AFTER `id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND column_name='nickname_snapshot') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD COLUMN `nickname_snapshot` varchar(128) NULL COMMENT '用户昵称快照' AFTER `user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND column_name='dept_snapshot') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD COLUMN `dept_snapshot` varchar(255) NULL COMMENT '部门快照' AFTER `nickname_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND column_name='post_snapshot') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD COLUMN `post_snapshot` varchar(255) NULL COMMENT '岗位快照' AFTER `dept_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND index_name='idx_tenant_form_status') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD KEY `idx_tenant_form_status` (`tenant_id`,`form_id`,`status`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND index_name='idx_tenant_batch') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD KEY `idx_tenant_batch` (`tenant_id`,`batch_id`);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND index_name='uk_form_user') THEN
    ALTER TABLE `zsjos_forced_form_recipient` DROP INDEX `uk_form_user`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_recipient' AND index_name='uk_tenant_form_user') THEN
    ALTER TABLE `zsjos_forced_form_recipient` ADD UNIQUE KEY `uk_tenant_form_user` (`tenant_id`,`form_id`,`user_id`);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission' AND column_name='version_id') THEN
    ALTER TABLE `zsjos_forced_form_submission` ADD COLUMN `version_id` bigint NULL COMMENT '提交版本ID' AFTER `form_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission' AND index_name='idx_tenant_user_time') THEN
    ALTER TABLE `zsjos_forced_form_submission` ADD KEY `idx_tenant_user_time` (`tenant_id`,`user_id`,`create_time`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission' AND index_name='idx_tenant_version') THEN
    ALTER TABLE `zsjos_forced_form_submission` ADD KEY `idx_tenant_version` (`tenant_id`,`version_id`);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission' AND index_name='uk_form_user') THEN
    ALTER TABLE `zsjos_forced_form_submission` DROP INDEX `uk_form_user`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission' AND index_name='uk_tenant_form_user') THEN
    ALTER TABLE `zsjos_forced_form_submission` ADD UNIQUE KEY `uk_tenant_form_user` (`tenant_id`,`form_id`,`user_id`);
  END IF;

  CREATE TABLE IF NOT EXISTS `zsjos_forced_form_submission_file` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL DEFAULT 0,
    `form_id` bigint NOT NULL,
    `version_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `submission_id` bigint NULL,
    `field_key` varchar(64) NOT NULL,
    `infra_file_id` bigint NOT NULL,
    `upload_token` varchar(128) NOT NULL,
    `file_name` varchar(255) NOT NULL,
    `file_size` bigint NOT NULL DEFAULT 0,
    `content_type` varchar(128) NULL,
    `status` varchar(20) NOT NULL DEFAULT 'TEMPORARY',
    `creator` varchar(64) NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_token` (`tenant_id`,`upload_token`),
    KEY `idx_submission` (`tenant_id`,`submission_id`),
    KEY `idx_tenant_form_user_field` (`tenant_id`,`form_id`,`user_id`,`field_key`),
    KEY `idx_tenant_status_time` (`tenant_id`,`status`,`create_time`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单提交附件';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND column_name='form_id') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD COLUMN `form_id` bigint NOT NULL DEFAULT 0 COMMENT '表单ID' AFTER `tenant_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND column_name='version_id') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD COLUMN `version_id` bigint NOT NULL DEFAULT 0 COMMENT '版本ID' AFTER `form_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND column_name='user_id') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD COLUMN `user_id` bigint NOT NULL DEFAULT 0 COMMENT '上传用户ID' AFTER `version_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND column_name='infra_file_id') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD COLUMN `infra_file_id` bigint NOT NULL DEFAULT 0 COMMENT 'Infra文件ID' AFTER `field_key`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND column_name='submission_id' AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` MODIFY COLUMN `submission_id` bigint NULL COMMENT '提交ID';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND index_name='uk_upload_token') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD UNIQUE KEY `uk_upload_token` (`tenant_id`,`upload_token`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND index_name='idx_tenant_form_user_field') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD KEY `idx_tenant_form_user_field` (`tenant_id`,`form_id`,`user_id`,`field_key`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_forced_form_submission_file' AND index_name='idx_tenant_status_time') THEN
    ALTER TABLE `zsjos_forced_form_submission_file` ADD KEY `idx_tenant_status_time` (`tenant_id`,`status`,`create_time`);
  END IF;

  INSERT IGNORE INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (79980,'强制表单','zsjos:forced-form:query',2,16,1,'zsjos/forced-form','ep:document-checked',
     'zsjos/forcedForm/index','ZsjosForcedForm','admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79981,'创建强制表单','zsjos:forced-form:create',3,1,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79982,'修改强制表单','zsjos:forced-form:update',3,2,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79983,'删除强制表单','zsjos:forced-form:delete',3,3,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79984,'发布强制表单','zsjos:forced-form:publish',3,4,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79985,'撤回强制表单','zsjos:forced-form:withdraw',3,5,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79986,'发送强制表单','zsjos:forced-form:send',3,6,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79987,'提交记录查询','zsjos:forced-form:submission-query',3,7,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79988,'提交记录详情','zsjos:forced-form:submission-read',3,8,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0'),
    (79989,'导出提交记录','zsjos:forced-form:submission-export',3,9,79980,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V166',NOW(),'V166',NOW(),b'0');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79980),`updater`='V166',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$') AND NOT JSON_CONTAINS(`menu_ids`,'79980','$');
  SET @zsjos_v166_menu_id = 79981;
  WHILE @zsjos_v166_menu_id <= 79989 DO
    UPDATE `system_tenant_package`
    SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',@zsjos_v166_menu_id),`updater`='V166',`update_time`=NOW()
    WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'79980','$') AND NOT JSON_CONTAINS(`menu_ids`,CAST(@zsjos_v166_menu_id AS CHAR),'$');
    SET @zsjos_v166_menu_id = @zsjos_v166_menu_id + 1;
  END WHILE;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V166','Formal forced-form model',SHA2('V166__zsjos_forced_form_formal_model.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V166','Formal forced-form model',SHA2('V166__zsjos_forced_form_formal_model.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v166_forced_form`();
DROP PROCEDURE `zsjos_v166_forced_form`;
