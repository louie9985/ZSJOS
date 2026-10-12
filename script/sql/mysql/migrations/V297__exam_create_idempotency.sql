-- UTF-8. V297: durable exam creation replay, scoped to tenant and operator.
-- Prerequisites/order: Core V293 in both ledgers; independent of V294-V296 features.
-- Deployment: apply before the new backend and key-sending clients. Missing create keys are rejected.
-- Scope: two nullable SHA-256 digest columns and one unique index on zsjos_exam_schedule.
-- No business rows, historical requests, dictionaries or permissions are backfilled or changed.
-- Fresh environments use bootstrap and ordered migrations; existing deployments apply this upgrade.
-- Repeat/recovery: inspect actual columns/index even with a version marker; fail on incompatible
-- structure. After correcting a partial failure, rerun. Both ledgers commit after all checks.
-- DDL implicitly commits: rollback cannot undo partial DDL. Application rollback retains columns,
-- index and existing request digests. There is no automatic destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v297_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v297_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V293')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V293') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V297 requires Core V293 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
    AND table_name IN ('zsjos_exam_schedule','zsjos_schema_version','zsjos_module_schema_version')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V297 requires exam table and transactional ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='create_request_key') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN create_request_key char(64) CHARACTER SET ascii
      COLLATE ascii_bin DEFAULT NULL COMMENT '创建操作人及请求标识摘要';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name='create_request_hash') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN create_request_hash char(64) CHARACTER SET ascii
      COLLATE ascii_bin DEFAULT NULL COMMENT '首次创建内容摘要';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND column_name IN ('create_request_key','create_request_hash')
    AND data_type='char' AND character_maximum_length=64 AND is_nullable='YES' AND column_default IS NULL
    AND character_set_name='ascii' AND collation_name='ascii_bin') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V297 create request column contract mismatch';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND index_name='uk_exam_create_request') THEN
    ALTER TABLE zsjos_exam_schedule ADD UNIQUE KEY uk_exam_create_request (tenant_id,create_request_key);
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='zsjos_exam_schedule' AND index_name='uk_exam_create_request') <> 2
    OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND index_name='uk_exam_create_request' AND non_unique=0
      AND sub_part IS NULL AND ((seq_in_index=1 AND column_name='tenant_id')
        OR (seq_in_index=2 AND column_name='create_request_key'))) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V297 create request unique index contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V297','Exam create idempotency',SHA2('V297__exam_create_idempotency.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V297');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V297','Exam create idempotency',SHA2('V297__exam_create_idempotency.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V297');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v297_apply();
DROP PROCEDURE zsjos_v297_apply;
