-- UTF-8. V191: tenant-scoped fixed-field course calendar.
-- Development-baseline scope: run after V190; requires Calendar menu 73600 and the System dictionary tables.
-- Data scope: creates the empty course-calendar table, the zsjos_course_form/LIVE dictionary metadata,
-- menu metadata 73630-73632, and the core V191 version records. It does not seed course events.
-- Repeatability: guarded DDL, natural-key dictionary checks, menu upserts, and version upserts make reruns safe.
-- Rollback limitation: no automatic rollback is provided because menus or dictionary values may already be referenced.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `zsjos_course_calendar_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程安排编号',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `course_name` varchar(200) NOT NULL COMMENT '课程名称',
  `course_form_value` varchar(64) NOT NULL COMMENT '课程形式字典值',
  `course_form_label_snapshot` varchar(100) NOT NULL COMMENT '课程形式标签快照',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `remark` varchar(2000) DEFAULT NULL COMMENT '备注',
  `attachment_ids_json` json DEFAULT NULL COMMENT '附件文件编号列表',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_course_calendar_range` (`tenant_id`,`start_time`,`end_time`,`deleted`),
  CONSTRAINT `chk_course_calendar_time` CHECK (`end_time` >= `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程日历安排';
INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '课程形式','zsjos_course_form',0,'ZSJOS课程日历课程形式','V191',NOW(),'V191',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_course_form' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1,'直播','LIVE','zsjos_course_form',0,'primary','V191',NOW(),'V191',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_course_form' AND `value`='LIVE' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (73630,'课程日历','zsjos:course-calendar:query',2,4,73600,'course-calendar','ep:calendar','zsjos/courseCalendar/index','ZsjosCourseCalendar','native',0,b'1',b'1',b'1','V191',NOW(),'V191',NOW(),b'0'),
 (73631,'查看课程日历','zsjos:course-calendar:query',3,1,73630,'','','',NULL,'native',0,b'1',b'1',b'0','V191',NOW(),'V191',NOW(),b'0'),
 (73632,'管理课程日历','zsjos:course-calendar:manage',3,2,73630,'','','',NULL,'native',0,b'1',b'1',b'0','V191',NOW(),'V191',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`deleted`=b'0',`updater`='V191',`update_time`=NOW();
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V191','Course calendar',SHA2('V191__course_calendar.sql',256),NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V191','Course calendar',SHA2('V191__course_calendar.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
