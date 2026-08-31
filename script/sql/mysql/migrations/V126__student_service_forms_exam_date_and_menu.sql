-- V126: configurable student-service forms, exam-date reminder state and menu.
-- Additive/repeatable. Apply after the repository's existing V125; no business rows are deleted or rewritten.

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='exam_date')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `exam_date` date DEFAULT NULL COMMENT ''学员考试日期'' AFTER `delivery_data_json`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='exam_date_version')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `exam_date_version` int NOT NULL DEFAULT 0 COMMENT ''考试日期版本'' AFTER `exam_date`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='last_notified_exam_date')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `last_notified_exam_date` date DEFAULT NULL COMMENT ''最近已通知考试日期'' AFTER `exam_date_version`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='exam_notice_sent_at')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `exam_notice_sent_at` datetime DEFAULT NULL COMMENT ''考前通知发送时间'' AFTER `last_notified_exam_date`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_config_version' AND column_name='forms_json')=0,
  'ALTER TABLE `zsjos_student_contact_config_version` ADD COLUMN `forms_json` json DEFAULT NULL COMMENT ''发布业务表单字段定义'' AFTER `collaborator_tabs_json`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_student_exam_notice_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `advance_days` int NOT NULL DEFAULT 7,
  `send_time` time NOT NULL DEFAULT '09:00:00',
  `in_app_enabled` bit(1) NOT NULL DEFAULT b'1',
  `websocket_enabled` bit(1) NOT NULL DEFAULT b'1',
  `status` varchar(16) NOT NULL DEFAULT 'enabled',
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL,
  `updater` varchar(64) DEFAULT NULL, `update_time` datetime NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员考试提醒规则';

CREATE TABLE IF NOT EXISTS `zsjos_student_form_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL, `service_relation_id` bigint NOT NULL,
  `stage` varchar(64) NOT NULL, `config_version_no` int NOT NULL,
  `fields_json` json NOT NULL, `operator_user_id` bigint NOT NULL,
  `submitted_at` datetime NOT NULL, `idempotency_key` varchar(64) NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_relation_stage` (`tenant_id`,`service_relation_id`,`stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员业务表单不可变快照';

SET @root_id := (SELECT id FROM system_menu WHERE path='/zsjos' AND parent_id=0 AND status=0 AND deleted=b'0' LIMIT 1);
INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (73460,'业务表单配置','zsjos:student-contact-config:forms',2,64,@root_id,'business-form-config','ep:document','zsjos/studentContactConfig/index','ZsjosBusinessFormConfig',0,b'1',b'1',b'0','migration-V126',NOW(),'migration-V126',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`deleted`=b'0',`update_time`=NOW();

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V126','Student service forms, exam date and menu','V126__student_service_forms_exam_date_and_menu.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V126','Student service forms, exam date and menu',SHA2('V126__student_service_forms_exam_date_and_menu.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
