-- UTF-8. V365 -> V366 -> matching runtime. Scope: two empty progress tables and
-- the explicitly requested initial exam-outcome dictionary (graduated/not_graduated).
-- No service/task/order rows or role grants change. Replays preserve administrator edits.
-- DDL commits implicitly; partial failures retain tables. Repair prerequisites and rerun.
-- Both success ledgers are written only after structure checks and dictionary seeding.
-- Rollback: stop using the new runtime, retain progress and snapshots; no destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v366_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v366_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V365')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V365') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V366 requires V365 in both ledgers';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_class_exam_progress (
  class_id bigint NOT NULL, tenant_id bigint NOT NULL,
  stage varchar(24) NOT NULL, version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY(tenant_id,class_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 CREATE TABLE IF NOT EXISTS zsjos_student_exam_progress (
  service_relation_id bigint NOT NULL, tenant_id bigint NOT NULL,
  stage varchar(24) NOT NULL, outcome varchar(100) DEFAULT NULL,
  outcome_label varchar(100) DEFAULT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY(tenant_id,service_relation_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
     AND ((table_name='zsjos_class_exam_progress' AND column_name IN ('class_id','tenant_id','stage','version'))
       OR (table_name='zsjos_student_exam_progress' AND column_name IN ('service_relation_id','tenant_id','stage','outcome','outcome_label'))))<>9 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V366 incompatible progress tables; repair structure before retry';
 END IF;
 START TRANSACTION;
 INSERT INTO system_dict_type(name,type,status,remark)
 SELECT '学员考后结果','zsjos_exam_outcome',0,'完成考试后的结果分支；选择时保存名称快照'
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_exam_outcome');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status)
 SELECT 10,'结业','graduated','zsjos_exam_outcome',0
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_exam_outcome' AND value='graduated');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status)
 SELECT 20,'未结业','not_graduated','zsjos_exam_outcome',0
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_exam_outcome' AND value='not_graduated');
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V366','Class examination progress',SHA2('V366__class_exam_progress.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V366');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V366','Class examination progress',SHA2('V366__class_exam_progress.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V366');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v366_apply();
DROP PROCEDURE zsjos_v366_apply;
