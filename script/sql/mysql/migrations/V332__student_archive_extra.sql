-- UTF-8. Internal service-specific archive extensions; requires V327 in both ledgers.
-- New empty tables only. No business options, roles, submissions or published templates are rewritten.
-- Order: V327 then V332 before matching application deployment.
-- Repeatable on MySQL 8: checks real structure before recording success; partial DDL can be replayed.
-- DDL implicitly commits. Rollback retains tables, revisions and encrypted files; no destructive down migration.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v332_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v332_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V327')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V327') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V332 requires V327';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_archive_extra_revision (
  id BIGINT NOT NULL AUTO_INCREMENT, service_relation_id BIGINT NOT NULL, revision INT NOT NULL,
  operator_user_id BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL,
  values_json MEDIUMTEXT NOT NULL COMMENT '加密的考试与证书信息快照',
  tenant_id BIGINT NOT NULL DEFAULT 1, creator VARCHAR(64) NOT NULL DEFAULT '', updater VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY(id),
  UNIQUE KEY uk_service_revision(tenant_id,service_relation_id,revision), UNIQUE KEY uk_extra_command(tenant_id,idempotency_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员考试与证书档案修订';
 CREATE TABLE IF NOT EXISTS zsjos_student_archive_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT, service_relation_id BIGINT NOT NULL, field_key VARCHAR(64) NOT NULL,
  infra_file_id BIGINT NOT NULL, file_size BIGINT NOT NULL, content_type VARCHAR(150) NOT NULL,
  tenant_id BIGINT NOT NULL DEFAULT 1, creator VARCHAR(64) NOT NULL DEFAULT '', updater VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY(id), KEY idx_service_field(tenant_id,service_relation_id,field_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员档案加密附件绑定';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision'
 AND column_name IN ('id','service_relation_id','revision','operator_user_id','reason','idempotency_key','request_hash','values_json','tenant_id','creator','updater','create_time','update_time','deleted') AND is_nullable='NO')<>14
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_attachment'
 AND column_name IN ('id','service_relation_id','field_key','infra_file_id','file_size','content_type','tenant_id','creator','updater','create_time','update_time','deleted') AND is_nullable='NO')<>12
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND index_name='uk_service_revision' AND non_unique=0)<>3
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND index_name='uk_extra_command' AND non_unique=0)<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_attachment' AND index_name='idx_service_field')<>3
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND column_name IN ('id','service_relation_id','operator_user_id','tenant_id') AND data_type='bigint')<>4
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND column_name='revision' AND data_type='int')<>1
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND column_name='values_json' AND data_type='mediumtext')<>1
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_attachment' AND column_name IN ('id','service_relation_id','infra_file_id','file_size','tenant_id') AND data_type='bigint')<>5
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND index_name='uk_service_revision')<>'tenant_id,service_relation_id,revision'
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_revision' AND index_name='uk_extra_command')<>'tenant_id,idempotency_key'
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_attachment' AND index_name='idx_service_field')<>'tenant_id,service_relation_id,field_key'
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V332 structure postcondition failed'; END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V332','学员考试与证书档案',SHA2('V332__student_archive_extra.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V332');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V332','学员考试与证书档案',SHA2('V332__student_archive_extra.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V332');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v332_apply();
DROP PROCEDURE zsjos_v332_apply;
