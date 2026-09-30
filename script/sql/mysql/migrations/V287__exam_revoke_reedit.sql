-- UTF-8. V287: five-minute exam revoke/reedit window.
-- Prerequisite: Core V286 in both ledgers, deployed before the backend and Workbench.
-- Scope: four nullable columns on existing exam records. No business rows, snapshots or grants changed.
-- Existing revoked rows retain NULL timestamps and are hidden; no invented historical window.
-- Replay checks actual columns even with a version marker. DDL implicitly commits; retain a scoped
-- schema/ledger backup and recover partial application by replay. Roll back application code while
-- retaining additive columns and all data; this script does not reverse consumed reedit claims.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v287_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v287_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V286')
    OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V286') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V287 requires Core V286 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('zsjos_exam_schedule','zsjos_schema_version','zsjos_module_schema_version') AND engine='InnoDB') <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V287 requires InnoDB exam and version ledgers';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='revoked_at') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN revoked_at datetime(3) DEFAULT NULL COMMENT '撤销时间';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='revoked_by') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN revoked_by bigint DEFAULT NULL COMMENT '撤销操作人';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='reedit_claimed_at') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN reedit_claimed_at datetime(3) DEFAULT NULL COMMENT '重新编辑领取时间';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='reedit_operation_key') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN reedit_operation_key varchar(64) DEFAULT NULL COMMENT '重新编辑幂等请求标识';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND is_nullable='YES' AND column_default IS NULL
    AND ((column_name IN ('revoked_at','reedit_claimed_at') AND data_type='datetime' AND datetime_precision=3)
      OR (column_name='revoked_by' AND data_type='bigint')
      OR (column_name='reedit_operation_key' AND data_type='varchar' AND character_maximum_length=64))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V287 reedit column postconditions failed';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V287','Exam revoke reedit window',SHA2('V287__exam_revoke_reedit.sql',256)
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V287');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V287','Exam revoke reedit window',SHA2('V287__exam_revoke_reedit.sql',256),'baseline'
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v287_apply();
DROP PROCEDURE zsjos_v287_apply;
