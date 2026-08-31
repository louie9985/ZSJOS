-- Director student workflow foundation. Repeatable guards support partially upgraded development databases.
SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='operator_user_id')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `operator_user_id` bigint DEFAULT NULL AFTER `career_planner_user_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_stage')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_stage` varchar(32) NOT NULL DEFAULT ''precheck'' AFTER `operator_user_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_interview_at')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_interview_at` datetime DEFAULT NULL AFTER `director_stage`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_form_config_id')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_form_config_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_form_config_version')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_form_config_version` int DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_precheck_draft_json')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_precheck_draft_json` json DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_precheck_snapshot_json')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_precheck_snapshot_json` json DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_interview_draft_json')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_interview_draft_json` json DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='director_interview_snapshot_json')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_interview_snapshot_json` json DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND index_name='idx_tenant_operator_status')=0,
  'ALTER TABLE `zsjos_service_relation` ADD KEY `idx_tenant_operator_status` (`tenant_id`,`operator_user_id`,`status`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73471, '编导资料预审', 'zsjos:student:director-precheck', 3, 71, 73400, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:student:director-precheck' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73472, '编导学员采访', 'zsjos:student:director-interview', 3, 72, 73400, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:student:director-interview' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73473, '编导指派运营', 'zsjos:student:director-operator-assign', 3, 73, 73400, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:student:director-operator-assign' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73474, '运营确认定位卡', 'zsjos:positioning-card:operator-confirm', 3, 74, 6980, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:positioning-card:operator-confirm' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73475, '运营退回定位卡', 'zsjos:positioning-card:operator-reject', 3, 75, 6980, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:positioning-card:operator-reject' AND `deleted`=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`tenant_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT r.id, m.id, r.tenant_id, 'V128', NOW(), 'V128', NOW(), b'0'
FROM `system_role` r JOIN `system_menu` m ON m.permission IN ('zsjos:student:director-precheck','zsjos:student:director-interview',
  'zsjos:student:director-operator-assign','zsjos:positioning-card:submit-review','zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive')
WHERE r.code='content_director' AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`tenant_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT r.id, m.id, r.tenant_id, 'V128', NOW(), 'V128', NOW(), b'0'
FROM `system_role` r JOIN `system_menu` m ON m.permission IN ('zsjos:positioning-card:operator-confirm','zsjos:positioning-card:operator-reject')
WHERE r.code='new_media_operator' AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');
UPDATE `system_role_menu` rm JOIN `system_role` r ON r.id=rm.role_id JOIN `system_menu` m ON m.id=rm.menu_id
SET rm.deleted=b'1', rm.update_time=NOW(), rm.updater='V128'
WHERE r.code='new_media_operator' AND m.permission IN ('zsjos:positioning-card:submit-review','zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive')
  AND rm.deleted=b'0';
