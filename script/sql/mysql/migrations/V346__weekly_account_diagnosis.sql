-- UTF-8. Scope: empty weekly diagnosis metric/member tables; no business rows or grants are changed.
-- Prerequisites: V209 account profile, V264 delivery rounds, business events and both version ledgers.
-- Order: apply after existing numeric migrations, before matching backend/Workbench.
-- Repeatable: validates existing structures, repairs missing tables, never rewrites ledger/checksum history.
-- Recovery: MySQL DDL commits independently. Inspect both tables/ledgers after failure, correct the cause and replay.
-- Rollback: application rollback retains metric/member evidence; no destructive rollback is supplied.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v346_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v346_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN
 ('zsjos_media_account_profile_entry','zsjos_business_task','zsjos_business_event','zsjos_partner_student_link','zsjos_schema_version','zsjos_module_schema_version'))<>6 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V346 prerequisite tables missing';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_account_diagnosis_metric (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id bigint NOT NULL, node_key varchar(128) NOT NULL, round_key varchar(64) NOT NULL,
  interval_days int NOT NULL, interval_cycle int NOT NULL,
  period_from datetime NOT NULL, period_to datetime NOT NULL,
  partner_id bigint NULL, partner_link_id bigint NULL,
  state varchar(24) NOT NULL, metric_json longtext NOT NULL, submission_snapshot_json longtext NOT NULL,
  failure_reason varchar(500) NULL, settled_at datetime NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 1,
  UNIQUE KEY uk_diagnosis_metric(tenant_id,account_id,node_key,interval_days),
  KEY idx_diagnosis_settlement(tenant_id,state,period_to)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号周期诊断统计结算';
 CREATE TABLE IF NOT EXISTS zsjos_account_diagnosis_member (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, metric_id bigint NOT NULL, lead_id bigint NOT NULL,
  tenant_id bigint NOT NULL DEFAULT 1,
  UNIQUE KEY uk_diagnosis_member(tenant_id,metric_id,lead_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周期截止有效客资固定集合';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_account_diagnosis_metric')<>21
  OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_account_diagnosis_member')<>4
  OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_account_diagnosis_metric' AND
     ((column_name IN ('account_id','partner_id','partner_link_id','tenant_id','id') AND data_type='bigint')
       OR (column_name IN ('interval_days','interval_cycle') AND data_type='int')
       OR (column_name IN ('metric_json','submission_snapshot_json') AND data_type='longtext')
       OR (column_name IN ('period_from','period_to','settled_at') AND data_type='datetime')) )<>12
  OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_account_diagnosis_metric' AND index_name='uk_diagnosis_metric' AND non_unique=0)<>4
  OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_account_diagnosis_member' AND index_name='uk_diagnosis_member' AND non_unique=0)<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V346 schema postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V346','周期诊断综合表统计',SHA2('V346__weekly_account_diagnosis.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V346');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V346','周期诊断综合表统计',SHA2('V346__weekly_account_diagnosis.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V346');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v346_apply();
DROP PROCEDURE zsjos_v346_apply;
