-- UTF-8. Upgrade HRM V003 before this file. Tenant 1 is the only business tenant.
-- Scope: three nullable hrm_employee fields, lossless INT -> DECIMAL(10,2),
-- one missing hrm_employee_status dictionary value (9, 试岗), and version ledgers.
-- No employee state/date backfill, role grants, notifications, or existing dictionary edits.
-- Repeatable; missing columns resume after partial DDL. DDL commits implicitly.
-- Rollback requires a backup; do not downgrade after decimal tenure or trial values are saved.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS hrm_trial_tenure_v004;
DELIMITER $$
CREATE PROCEDURE hrm_trial_tenure_v004()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V003')
     OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee'
         AND column_name IN ('company_age','version','planned_regular_time','status_label')) <> 4
     OR NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='hrm_employee_status' AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HRM V004 requires complete HRM V003 and employee status dictionary';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='trial_days') THEN
    ALTER TABLE hrm_employee ADD COLUMN trial_days int DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='planned_trial_end_time') THEN
    ALTER TABLE hrm_employee ADD COLUMN planned_trial_end_time timestamp NULL DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='actual_trial_end_time') THEN
    ALTER TABLE hrm_employee ADD COLUMN actual_trial_end_time timestamp NULL DEFAULT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='company_age' AND data_type='int') THEN
    ALTER TABLE hrm_employee MODIFY COLUMN company_age decimal(10,2) DEFAULT NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee'
      AND ((column_name='trial_days' AND data_type='int')
        OR (column_name IN ('planned_trial_end_time','actual_trial_end_time') AND data_type='timestamp')
        OR (column_name='company_age' AND data_type='decimal' AND numeric_precision=10 AND numeric_scale=2))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HRM V004 column postconditions failed';
  END IF;
  START TRANSACTION;
  INSERT INTO system_dict_data(sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
  SELECT 9,'试岗','9','hrm_employee_status',0,'warning','','HRM V004：非正式聘用试岗','hrm-v004',NOW(),'hrm-v004',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='hrm_employee_status' AND value='9' AND deleted=b'0');
  IF (SELECT COUNT(*) FROM system_dict_data WHERE dict_type='hrm_employee_status' AND value='9' AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HRM V004 trial dictionary postcondition failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  VALUES('HRM_V004','HRM trial period and decimal tenure','hrm-trial-tenure-v1')
  ON DUPLICATE KEY UPDATE version=VALUES(version);
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
  VALUES('hrm','V004','HRM trial period and decimal tenure',SHA2('V004__employee_trial_and_decimal_tenure.sql',256),'upgrade',NOW())
  ON DUPLICATE KEY UPDATE version=VALUES(version);
  COMMIT;
END$$
DELIMITER ;
CALL hrm_trial_tenure_v004();
DROP PROCEDURE IF EXISTS hrm_trial_tenure_v004;
