-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V114: normal student delivery stages and structured stage facts.
-- Additive and repeatable. Apply after V113; historical stage is derived only from owned service facts.
-- Stage payloads are planner-entered snapshots, not replacements for owning domains.

DROP PROCEDURE IF EXISTS `zsjos_v114_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v114_apply`()
BEGIN
  DECLARE v114_zsjos_menu_id bigint;

  IF (SELECT COUNT(*) FROM `system_menu`
      WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V114 blocked: active /zsjos root menu is missing or ambiguous';
  END IF;
  SELECT `id` INTO v114_zsjos_menu_id FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0'
  LIMIT 1;

  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `id`=73020 AND NOT (`permission` <=> 'zsjos:student:query-my')) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V114 blocked: menu ID 73020 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
      WHERE `permission`='zsjos:student:query-my' AND `id`<>73020 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V114 blocked: student query permission is owned by another active menu';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` existing
      WHERE (existing.id=73428
             AND COALESCE(existing.permission,'')<>'zsjos:student-contact:delivery-stage-submit')
         OR (existing.permission='zsjos:student-contact:delivery-stage-submit'
             AND existing.id<>73428 AND existing.deleted=b'0')) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V114 menu ID collision: existing menu ownership differs';
  END IF;

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
   `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES (73020,'我的学员','zsjos:student:query-my',2,62,v114_zsjos_menu_id,'/zsjos/my-students',
          'ant-design:team-outlined','zsjos/my-students','ZsjosMyStudents',0,b'1',b'1',b'0',
          'migration-V114',NOW(),'migration-V114',NOW(),b'0')
  ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
   `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
   `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
   `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
   `deleted`=b'0',`updater`='migration-V114',`update_time`=NOW();

SET @v114_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='delivery_stage')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `delivery_stage` varchar(64) DEFAULT NULL COMMENT ''学习交付阶段'' AFTER `service_snapshot`', 'SELECT 1');
PREPARE v114_stmt FROM @v114_sql; EXECUTE v114_stmt; DEALLOCATE PREPARE v114_stmt;
SET @v114_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='delivery_data_json')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `delivery_data_json` json DEFAULT NULL COMMENT ''交付阶段事实快照'' AFTER `delivery_stage`', 'SELECT 1');
PREPARE v114_stmt FROM @v114_sql; EXECUTE v114_stmt; DEALLOCATE PREPARE v114_stmt;
SET @v114_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_record' AND column_name='delivery_stage')=0,
  'ALTER TABLE `zsjos_student_contact_record` ADD COLUMN `delivery_stage` varchar(64) DEFAULT NULL COMMENT ''交付阶段'' AFTER `checklist_result_json`', 'SELECT 1');
PREPARE v114_stmt FROM @v114_sql; EXECUTE v114_stmt; DEALLOCATE PREPARE v114_stmt;
SET @v114_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_record' AND column_name='delivery_data_json')=0,
  'ALTER TABLE `zsjos_student_contact_record` ADD COLUMN `delivery_data_json` json DEFAULT NULL COMMENT ''交付阶段事实'' AFTER `delivery_stage`', 'SELECT 1');
PREPARE v114_stmt FROM @v114_sql; EXECUTE v114_stmt; DEALLOCATE PREPARE v114_stmt;
SET @v114_sql := IF((SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_contact_record' AND column_name='task_id')='NO',
  'ALTER TABLE `zsjos_student_contact_record` MODIFY COLUMN `task_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE v114_stmt FROM @v114_sql; EXECUTE v114_stmt; DEALLOCATE PREPARE v114_stmt;

UPDATE `zsjos_service_relation` SET `delivery_stage`='completed'
WHERE `delivery_stage` IS NULL AND `status`='completed';
UPDATE `zsjos_service_relation` relation_row SET `delivery_stage`='group_handoff'
WHERE relation_row.`delivery_stage` IS NULL AND EXISTS (
  SELECT 1 FROM `zsjos_student_contact_record` record_row
  WHERE record_row.`service_relation_id`=relation_row.`id`
    AND record_row.`contact_type`='student_study_plan' AND record_row.`successful`=b'1'
    AND record_row.`tenant_id`=relation_row.`tenant_id` AND record_row.`deleted`=b'0');
UPDATE `zsjos_service_relation` relation_row SET `delivery_stage`='study_plan'
WHERE relation_row.`delivery_stage` IS NULL AND EXISTS (
  SELECT 1 FROM `zsjos_student_contact_record` record_row
  WHERE record_row.`service_relation_id`=relation_row.`id`
    AND record_row.`contact_type`='student_first_contact' AND record_row.`successful`=b'1'
    AND record_row.`tenant_id`=relation_row.`tenant_id` AND record_row.`deleted`=b'0');
UPDATE `zsjos_service_relation` SET `delivery_stage`='first_contact'
WHERE `delivery_stage` IS NULL AND `acceptance_status`='accepted' AND `status` IN ('active','paused');

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,
 `keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (73428,'提交交付阶段','zsjos:student-contact:delivery-stage-submit',3,8,73020,'','','',NULL,0,b'1',b'1',b'0',
        'migration-V114',NOW(),'migration-V114',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),
 `type`=VALUES(`type`),`sort`=VALUES(`sort`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
 `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),
 `visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),
 `deleted`=b'0',`updater`='migration-V114',`update_time`=NOW();

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V114','Student delivery stages','student-delivery-stages-v6')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V114','Student delivery stages',SHA2('student-delivery-stages-v6',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v114_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v114_apply`;
