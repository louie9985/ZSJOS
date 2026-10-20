-- UTF-8. Requires V328 contact/progress schema and published tenant-1 configuration.
-- Numeric upgrade order; preserves all existing task deadlines, extensions, records and archived config.
-- Creates one new published configuration with 4320-minute study-plan timeout; mutable drafts using
-- the previous 1440-minute default are upgraded without altering other draft values.
-- Nullable fields support unscheduled, taskless ordinary follow-up. No role grants or business deletion.
-- Repeatable and recoverable after partial DDL; DDL implicitly commits. Rollback cannot restore NOT NULL
-- once taskless/unscheduled records exist; retain new columns and snapshots on application rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v330_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v330_apply()
BEGIN
 DECLARE source_id BIGINT;
 DECLARE next_no INT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='checklist_snapshot_json')
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_config_version' AND column_name='forms_json') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V330 requires V328 and contact forms schema';
 END IF;
 IF (SELECT COUNT(*) FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND deleted=0 AND status='published')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V330 requires one published tenant-1 contact configuration';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='task_id' AND is_nullable='NO') THEN
  ALTER TABLE zsjos_student_contact_record MODIFY COLUMN task_id BIGINT NULL;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name='next_contact_at' AND is_nullable='NO') THEN
  ALTER TABLE zsjos_student_contact_record MODIFY COLUMN next_contact_at DATETIME NULL;
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_contact_record' AND column_name IN('task_id','next_contact_at') AND is_nullable='YES')<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V330 nullable postcondition failed';
 END IF;
 START TRANSACTION;
 SELECT id INTO source_id FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND deleted=0 AND status='published' FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND creator='migration-V330') THEN
  SELECT COALESCE(MAX(version_no),0)+1 INTO next_no FROM zsjos_student_contact_config_version WHERE tenant_id=1;
  INSERT INTO zsjos_student_contact_config_version(version_no,status,first_contact_timeout_minutes,study_plan_timeout_minutes,checklist_json,quick_notes_json,collaborator_tabs_json,forms_json,version,creator,updater,tenant_id)
  SELECT next_no,'published',first_contact_timeout_minutes,4320,checklist_json,quick_notes_json,collaborator_tabs_json,forms_json,0,'migration-V330','migration-V330',1
  FROM zsjos_student_contact_config_version WHERE id=source_id AND tenant_id=1;
  UPDATE zsjos_student_contact_config_version SET status='archived',updater='migration-V330' WHERE id=source_id AND tenant_id=1;
  UPDATE zsjos_student_contact_config_version SET study_plan_timeout_minutes=4320,version=version+1,updater='migration-V330'
  WHERE tenant_id=1 AND deleted=0 AND status='draft' AND study_plan_timeout_minutes=1440;
 END IF;
 IF (SELECT COUNT(*) FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND creator='migration-V330' AND study_plan_timeout_minutes=4320)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V330 configuration postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V330','学员联系预约选填与学习计划72小时',SHA2('V330__student_contact_optional_schedule.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V330');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V330','学员联系预约选填与学习计划72小时',SHA2('V330__student_contact_optional_schedule.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V330');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v330_apply();
DROP PROCEDURE zsjos_v330_apply;
