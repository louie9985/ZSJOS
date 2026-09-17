-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
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
