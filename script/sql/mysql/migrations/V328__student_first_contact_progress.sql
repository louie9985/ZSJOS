-- UTF-8. First-contact progress and immutable submission snapshots; tenant 1 business data only.
-- Requires V094 contact tables, service delivery_stage/version, and configured student page.
-- Apply after existing migrations in numeric order. No business backfill, task transition or role grants.
-- Repeatable: inspect actual columns on every run, including after partial DDL failure.
-- DDL implicitly commits; rollback retains added nullable columns. Do not drop populated snapshots.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v328_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v328_apply()
BEGIN
 DECLARE parent_menu BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='checklist_result_json')
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='delivery_stage')
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='version') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V328 requires contact and delivery schema';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V328 requires one configured student page';
 END IF;
 SELECT id INTO parent_menu FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0;
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-contact:checklist-update' AND deleted=0 AND (parent_id<>parent_menu OR type<>3)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V328 checklist permission conflicts';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='study_plan_completed_at_first_contact') THEN
  ALTER TABLE zsjos_student_contact_record ADD COLUMN study_plan_completed_at_first_contact BIT(1) NULL COMMENT '首联时是否已制定学习计划，历史未知为空';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='checklist_snapshot_json') THEN
  ALTER TABLE zsjos_student_contact_record ADD COLUMN checklist_snapshot_json JSON NULL COMMENT '首联清单提交快照';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='first_contact_progress_json') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN first_contact_progress_json JSON NULL COMMENT '首联清单当前进度';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND is_nullable='YES' AND
     ((table_name='zsjos_student_contact_record' AND column_name='study_plan_completed_at_first_contact' AND data_type='bit')
      OR (table_name='zsjos_student_contact_record' AND column_name='checklist_snapshot_json' AND data_type='json')
      OR (table_name='zsjos_service_relation' AND column_name='first_contact_progress_json' AND data_type='json')))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V328 column postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '更新首联清单','zsjos:student-contact:checklist-update',3,50,parent_menu,'','','',0,1,1,0,'V328','V328',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-contact:checklist-update' AND deleted=0);
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-contact:checklist-update' AND type=3 AND parent_id=parent_menu AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V328 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V328','首联清单进度与学习计划分支',SHA2('V328__student_first_contact_progress.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V328');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V328','首联清单进度与学习计划分支',SHA2('V328__student_first_contact_progress.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V328');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v328_apply();
DROP PROCEDURE zsjos_v328_apply;
