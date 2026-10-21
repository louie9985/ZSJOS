-- UTF-8. Service-owned student names; upgrade after V336, before application rollout.
-- Scope: additive nullable columns; tenant 1, non-deleted services with a non-deleted
-- same-tenant order and a nonblank order name. Independent services are NOT backfilled.
-- Source is explicitly migration-time order data, not an invented historical snapshot.
-- Replay preserves initialized names, including deliberately cleared names with a source.
-- DDL commits implicitly; inspect columns and both ledgers after failure and replay.
-- Rollback retains columns/data; no orders, people, grants or historical messages change.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v337_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v337_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V336')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V336') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V337 requires V336 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND engine='InnoDB') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V337 requires InnoDB service relationships';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND column_name='student_name_snapshot') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN student_name_snapshot VARCHAR(100) NULL COMMENT '学员服务姓名快照' AFTER person_id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND column_name='student_name_source') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN student_name_source VARCHAR(32) NULL COMMENT '学员姓名快照来源' AFTER student_name_snapshot;
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_service_relation' AND is_nullable='YES' AND column_default IS NULL
 AND character_set_name='utf8mb4' AND ((column_name='student_name_snapshot' AND column_type='varchar(100)')
 OR (column_name='student_name_source' AND column_type='varchar(32)'))) <> 2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V337 name structure postcondition failed';
 END IF;
 START TRANSACTION;
 UPDATE zsjos_service_relation sr JOIN zsjos_order o ON o.id=sr.order_id
 AND o.tenant_id=sr.tenant_id AND o.deleted=0
 SET sr.student_name_snapshot=TRIM(o.student_name),sr.student_name_source='order_migration',
 sr.update_time=sr.update_time
 WHERE sr.tenant_id=1 AND sr.deleted=0 AND sr.student_name_snapshot IS NULL
 AND sr.student_name_source IS NULL AND NULLIF(TRIM(o.student_name),'') IS NOT NULL;
 IF EXISTS(SELECT 1 FROM zsjos_service_relation sr JOIN zsjos_order o ON o.id=sr.order_id
 AND o.tenant_id=sr.tenant_id AND o.deleted=0 WHERE sr.tenant_id=1 AND sr.deleted=0
 AND sr.student_name_snapshot IS NULL AND sr.student_name_source IS NULL
 AND NULLIF(TRIM(o.student_name),'') IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V337 name data postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V337','学员服务姓名快照',SHA2('V337__student_service_name_snapshot.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V337');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V337','学员服务姓名快照',SHA2('V337__student_service_name_snapshot.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V337');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v337_apply();
DROP PROCEDURE zsjos_v337_apply;
