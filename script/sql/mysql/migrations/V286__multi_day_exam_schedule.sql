-- UTF-8. User-approved replacement of estimated ranges by definite multi-day exams.
-- Prerequisites/order: cloud Core V285 in both ledgers; deploy backend and Workbench together after this file.
-- Scope: every tenant's exam rows (including logical history), EXAM notification snapshot date keys/hashes.
-- IDs, dates, names, status, references and rendered notification text are retained; no deletes/grants.
-- Repeatable and recoverable from partial DDL; inspect actual structure even if a marker exists.
-- DDL commits implicitly. Back up exam/snapshot/ledger tables first; rollback requires matching code
-- and scoped backup restoration. No old API/type/field compatibility remains in the application.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v286_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v286_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V285')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V285') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V286 requires cloud Core V285 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_exam_schedule','zsjos_calendar_notify_snapshot') AND engine='InnoDB') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V286 requires exam and snapshot tables';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_exam_schedule WHERE schedule_type NOT IN ('EXACT','ROUGH','MULTI_DAY'))
    OR EXISTS(SELECT 1 FROM zsjos_calendar_notify_snapshot WHERE calendar_type='EXAM' AND NOT JSON_VALID(details_json)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V286 invalid existing exam data';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_calendar_notify_snapshot WHERE calendar_type='EXAM'
      AND (JSON_LENGTH(details_json)<>7 OR NOT JSON_CONTAINS_PATH(details_json,'all',
        '$.categoryPathSnapshot','$.exactDate','$.frozenSkusJson','$.scheduleType','$.selectedSpecsJson')
        OR NOT (JSON_CONTAINS_PATH(details_json,'all','$.roughStartDate','$.roughEndDate')
          OR JSON_CONTAINS_PATH(details_json,'all','$.startDate','$.endDate')))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V286 unexpected snapshot shape; no fields discarded';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND constraint_name='chk_exam_schedule_dates') THEN
    ALTER TABLE zsjos_exam_schedule DROP CHECK chk_exam_schedule_dates;
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND constraint_name='chk_exam_schedule_type') THEN
    ALTER TABLE zsjos_exam_schedule DROP CHECK chk_exam_schedule_type;
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='rough_start_date') THEN
    ALTER TABLE zsjos_exam_schedule CHANGE COLUMN rough_start_date start_date date DEFAULT NULL COMMENT '考试开始日期';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='rough_end_date') THEN
    ALTER TABLE zsjos_exam_schedule CHANGE COLUMN rough_end_date end_date date DEFAULT NULL COMMENT '考试结束日期';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND index_name='idx_exam_schedule_rough') THEN
    ALTER TABLE zsjos_exam_schedule RENAME INDEX idx_exam_schedule_rough TO idx_exam_schedule_multi_day;
  END IF;
  ALTER TABLE zsjos_exam_schedule MODIFY COLUMN schedule_type varchar(16) NOT NULL COMMENT '时间类型：EXACT/MULTI_DAY';
  -- Type conversion precedes the new constraint, so a partial failure resumes without losing any row.
  UPDATE zsjos_exam_schedule SET schedule_type='MULTI_DAY',update_time=update_time WHERE schedule_type='ROUGH';
  ALTER TABLE zsjos_exam_schedule ADD CONSTRAINT chk_exam_schedule_type CHECK (schedule_type IN ('EXACT','MULTI_DAY'));
  ALTER TABLE zsjos_exam_schedule ADD CONSTRAINT chk_exam_schedule_dates CHECK (
    (schedule_type='EXACT' AND exact_date IS NOT NULL AND start_date IS NULL AND end_date IS NULL)
    OR (schedule_type='MULTI_DAY' AND exact_date IS NULL AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date>=start_date));

  START TRANSACTION;
  -- Canonical TreeMap key order matches CalendarNotificationSnapshotService, retaining null keys.
  UPDATE zsjos_calendar_notify_snapshot SET details_json=CONCAT(
    '{"categoryPathSnapshot":',COALESCE(JSON_EXTRACT(details_json,'$.categoryPathSnapshot'),'null'),
    ',"endDate":',COALESCE(JSON_EXTRACT(details_json,'$.roughEndDate'),JSON_EXTRACT(details_json,'$.endDate'),'null'),
    ',"exactDate":',COALESCE(JSON_EXTRACT(details_json,'$.exactDate'),'null'),
    ',"frozenSkusJson":',COALESCE(JSON_EXTRACT(details_json,'$.frozenSkusJson'),'null'),
    ',"scheduleType":',IF(JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.scheduleType'))='ROUGH','"MULTI_DAY"',JSON_EXTRACT(details_json,'$.scheduleType')),
    ',"selectedSpecsJson":',COALESCE(JSON_EXTRACT(details_json,'$.selectedSpecsJson'),'null'),
    ',"startDate":',COALESCE(JSON_EXTRACT(details_json,'$.roughStartDate'),JSON_EXTRACT(details_json,'$.startDate'),'null'),'}'),
    update_time=update_time
  WHERE calendar_type='EXAM' AND (JSON_CONTAINS_PATH(details_json,'one','$.roughStartDate','$.roughEndDate')
    OR JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.scheduleType'))='ROUGH');
  UPDATE zsjos_calendar_notify_snapshot SET content_hash=SHA2(CONCAT('[',
    JSON_QUOTE(calendar_type),',',calendar_id,',',calendar_version,',',
    JSON_QUOTE(record_status),',',JSON_QUOTE(title_snapshot),',',
    COALESCE(JSON_QUOTE(time_snapshot),'null'),',',COALESCE(JSON_QUOTE(remark_snapshot),'null'),',',
    JSON_QUOTE(details_json),']'),256),update_time=update_time WHERE calendar_type='EXAM';
  IF EXISTS(SELECT 1 FROM zsjos_exam_schedule WHERE schedule_type='ROUGH')
    OR EXISTS(SELECT 1 FROM zsjos_calendar_notify_snapshot WHERE calendar_type='EXAM'
      AND (JSON_CONTAINS_PATH(details_json,'one','$.roughStartDate','$.roughEndDate')
        OR JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.scheduleType'))='ROUGH'))
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name IN ('start_date','end_date') AND data_type='date')<>2
    OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND index_name='idx_exam_schedule_multi_day') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V286 multi-day postconditions failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V286','Definite multi-day exam schedules',SHA2('V286__multi_day_exam_schedule.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V286');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V286','Definite multi-day exam schedules',SHA2('V286__multi_day_exam_schedule.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V286');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v286_apply();
DROP PROCEDURE zsjos_v286_apply;
