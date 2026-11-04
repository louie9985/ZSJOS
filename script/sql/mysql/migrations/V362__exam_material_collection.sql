-- UTF-8. V361 -> V362 -> matching backend/Workbench/H5.
-- Scope: eight new tenant-owned tables and exam collection menu metadata only.
-- No business rows, role grants or historical form rewrites. Repeatable CREATE IF NOT EXISTS
-- restores absent tables after partial DDL; incompatible existing columns fail postconditions.
-- Development-only revision: nullable written_keys_json records new archive write intent;
-- missing columns are added conditionally. Existing rows are not assigned invented intent.
-- MySQL DDL commits implicitly. On failure retain tables, inspect structure and both ledgers,
-- fix the prerequisite/structure and rerun. Never infer completion from a version marker.
-- Rollback limitation: disable new menu/runtime; retain submissions and historical attachments.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v362_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v362_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V361')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V361') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 requires V361 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 requires unique Workbench root';
 END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_task (
  id bigint NOT NULL AUTO_INCREMENT,
  title varchar(200) NOT NULL,
  description varchar(2000) NOT NULL DEFAULT '',
  status varchar(24) NOT NULL,
  config_version_id bigint NOT NULL,
  fields_snapshot longtext NOT NULL,
  class_ids_json text DEFAULT NULL,
  create_request_key varchar(64) DEFAULT NULL,
  create_request_hash varchar(64) DEFAULT NULL,
  token_hash varchar(64) NOT NULL,
  token_ciphertext text NOT NULL,
  sequence_no bigint NOT NULL DEFAULT 0,
  revision int NOT NULL DEFAULT 0,
  completion_reason varchar(500) DEFAULT NULL,
  completed_at datetime DEFAULT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_token (token_hash), UNIQUE KEY uk_create_request (tenant_id,create_request_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 -- Development-only V362 baseline expansion: preserve previously retained fixture tables.
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND column_name='class_ids_json') THEN
  ALTER TABLE zsjos_exam_collection_task ADD COLUMN class_ids_json text DEFAULT NULL AFTER fields_snapshot;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND column_name='create_request_key') THEN
  ALTER TABLE zsjos_exam_collection_task ADD COLUMN create_request_key varchar(64) DEFAULT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND column_name='create_request_hash') THEN
  ALTER TABLE zsjos_exam_collection_task ADD COLUMN create_request_hash varchar(64) DEFAULT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND index_name='uk_create_request') THEN
  ALTER TABLE zsjos_exam_collection_task ADD UNIQUE KEY uk_create_request (tenant_id,create_request_key);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND index_name='uk_create_request'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,create_request_key' AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid task uk_create_request';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND column_name IN ('id','title','description','status','config_version_id','fields_snapshot','class_ids_json','create_request_key','create_request_hash','token_hash','token_ciphertext','sequence_no','revision','completion_reason','completed_at','creator','updater','create_time','update_time','deleted','tenant_id'))<>21 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_task';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_task PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_task' AND index_name='uk_token'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='token_hash'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_task uk_token';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_member (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  service_relation_id bigint NOT NULL,
  person_id bigint NOT NULL,
  class_id bigint NOT NULL,
  planner_user_id bigint DEFAULT NULL,
  class_name_snapshot varchar(200) NOT NULL,
  identity_hash varchar(64) DEFAULT NULL,
  active bit(1) NOT NULL DEFAULT b'1',
  last_submission_id bigint DEFAULT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_member (tenant_id,task_id,service_relation_id),
  KEY idx_identity (tenant_id,task_id,identity_hash),
  KEY idx_planner (tenant_id,planner_user_id,task_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_member' AND column_name IN ('id','task_id','service_relation_id','person_id','class_id','planner_user_id','class_name_snapshot','identity_hash','active','last_submission_id','creator','updater','create_time','update_time','deleted','tenant_id'))<>16 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_member';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_member' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_member PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_member' AND index_name='uk_member'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,service_relation_id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_member uk_member';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_member' AND index_name='idx_identity'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,identity_hash'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_member idx_identity';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_member' AND index_name='idx_planner'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,planner_user_id,task_id'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_member idx_planner';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_submission (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  session_token_hash varchar(64) NOT NULL,
  idempotency_key varchar(64) DEFAULT NULL,
  request_hash varchar(64) DEFAULT NULL,
  sequence_no bigint DEFAULT NULL,
  status varchar(24) NOT NULL,
  identity_hash varchar(64) DEFAULT NULL,
  answers_json longtext DEFAULT NULL,
  attachment_ids_json text DEFAULT NULL,
  service_relation_id bigint DEFAULT NULL,
  exception_code varchar(64) DEFAULT NULL,
  submitted_at datetime(3) DEFAULT NULL,
  expires_at datetime NOT NULL,
  revision int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_session (session_token_hash),
  UNIQUE KEY uk_submission_key (tenant_id,task_id,idempotency_key),
  UNIQUE KEY uk_sequence (tenant_id,task_id,sequence_no),
  KEY idx_identity (tenant_id,task_id,identity_hash)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND column_name IN ('id','task_id','session_token_hash','idempotency_key','request_hash','sequence_no','status','identity_hash','answers_json','attachment_ids_json','service_relation_id','exception_code','submitted_at','expires_at','revision','creator','updater','create_time','update_time','deleted','tenant_id'))<>21 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_submission';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_submission PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND index_name='uk_session'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='session_token_hash'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_submission uk_session';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND index_name='uk_submission_key'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,idempotency_key'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_submission uk_submission_key';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND index_name='uk_sequence'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,sequence_no'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_submission uk_sequence';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_submission' AND index_name='idx_identity'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,identity_hash'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_submission idx_identity';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_binding (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  identity_hash varchar(64) NOT NULL,
  service_relation_id bigint NOT NULL,
  manual bit(1) NOT NULL DEFAULT b'0',
  last_submission_id bigint DEFAULT NULL,
  last_sequence_no bigint DEFAULT NULL,
  revision int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_identity (tenant_id,task_id,identity_hash),
  KEY idx_service (tenant_id,task_id,service_relation_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_binding' AND column_name IN ('id','task_id','identity_hash','service_relation_id','manual','last_submission_id','last_sequence_no','revision','creator','updater','create_time','update_time','deleted','tenant_id'))<>14 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_binding';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_binding' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_binding PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_binding' AND index_name='uk_identity'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,identity_hash'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_binding uk_identity';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_binding' AND index_name='idx_service'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,service_relation_id'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_binding idx_service';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_attachment (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  submission_id bigint NOT NULL,
  field_key varchar(64) NOT NULL,
  side varchar(16) NOT NULL DEFAULT '',
  infra_file_id bigint NOT NULL,
  file_size bigint NOT NULL,
  content_type varchar(100) NOT NULL,
  original_name varchar(512) NOT NULL,
  request_key varchar(64) DEFAULT NULL,
  request_hash varchar(64) DEFAULT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_submission (tenant_id,submission_id,field_key),
  UNIQUE KEY uk_upload_request (tenant_id,submission_id,request_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_attachment' AND column_name='request_key') THEN
  ALTER TABLE zsjos_exam_collection_attachment ADD COLUMN request_key varchar(64) DEFAULT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_attachment' AND column_name='request_hash') THEN
  ALTER TABLE zsjos_exam_collection_attachment ADD COLUMN request_hash varchar(64) DEFAULT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_attachment' AND index_name='uk_upload_request') THEN
  ALTER TABLE zsjos_exam_collection_attachment ADD UNIQUE KEY uk_upload_request (tenant_id,submission_id,request_key);
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_attachment' AND column_name IN ('id','task_id','submission_id','field_key','side','infra_file_id','file_size','content_type','original_name','request_key','request_hash','creator','updater','create_time','update_time','deleted','tenant_id'))<>17 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_attachment';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_attachment' AND index_name='uk_upload_request'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,submission_id,request_key' AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid upload idempotency index';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_attachment' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_attachment PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_attachment' AND index_name='idx_submission'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,submission_id,field_key'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_attachment idx_submission';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_exam_collection_event (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  submission_id bigint DEFAULT NULL,
  operator_user_id bigint DEFAULT NULL,
  action varchar(32) NOT NULL,
  reason varchar(500) NOT NULL DEFAULT '',
  details_json longtext DEFAULT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_task (tenant_id,task_id,id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_event' AND column_name IN ('id','task_id','submission_id','operator_user_id','action','reason','details_json','creator','updater','create_time','update_time','deleted','tenant_id'))<>13 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_exam_collection_event';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_event' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_event PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_exam_collection_event' AND index_name='idx_task'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,task_id,id'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_exam_collection_event idx_task';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_service_archive_revision (
  id bigint NOT NULL AUTO_INCREMENT,
  service_relation_id bigint NOT NULL,
  person_id bigint NOT NULL,
  revision int NOT NULL,
  task_id bigint DEFAULT NULL,
  submission_id bigint DEFAULT NULL,
  operator_user_id bigint DEFAULT NULL,
  source varchar(32) NOT NULL,
  reason varchar(500) NOT NULL DEFAULT '',
  idempotency_key varchar(64) NOT NULL,
  request_hash varchar(64) NOT NULL,
  fields_snapshot longtext NOT NULL,
  answers_json longtext NOT NULL,
  attachments_json longtext NOT NULL,
  written_keys_json longtext DEFAULT NULL COMMENT '本次显式写入字段，用于安全纠正归属',
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_revision (tenant_id,service_relation_id,revision),
  UNIQUE KEY uk_request (tenant_id,service_relation_id,idempotency_key),
  KEY idx_submission (tenant_id,submission_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND column_name='written_keys_json') THEN
  ALTER TABLE zsjos_student_service_archive_revision ADD COLUMN written_keys_json longtext DEFAULT NULL COMMENT '本次显式写入字段，用于安全纠正归属';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND column_name IN ('id','service_relation_id','person_id','revision','task_id','submission_id','operator_user_id','source','reason','idempotency_key','request_hash','fields_snapshot','answers_json','attachments_json','written_keys_json','creator','updater','create_time','update_time','deleted','tenant_id'))<>21 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete zsjos_student_service_archive_revision';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_student_service_archive_revision PRIMARY';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND index_name='uk_revision'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,service_relation_id,revision'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_student_service_archive_revision uk_revision';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND index_name='uk_request'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,service_relation_id,idempotency_key'
  AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_student_service_archive_revision uk_request';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_revision' AND index_name='idx_submission'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,submission_id'
  AND MAX(non_unique)=1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid zsjos_student_service_archive_revision idx_submission';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_service_archive_attachment (
  id bigint NOT NULL AUTO_INCREMENT,
  service_relation_id bigint NOT NULL,
  field_key varchar(64) NOT NULL,
  side varchar(16) NOT NULL DEFAULT '',
  infra_file_id bigint NOT NULL,
  file_size bigint NOT NULL,
  content_type varchar(100) NOT NULL,
  original_name varchar(512) NOT NULL,
  creator varchar(64) DEFAULT '', updater varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(id), KEY idx_service (tenant_id, service_relation_id, field_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_attachment' AND column_name IN ('id','service_relation_id','field_key','side','infra_file_id','file_size','content_type','original_name','creator','updater','create_time','update_time','deleted','tenant_id'))<>14 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 incomplete service archive attachments';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_attachment' AND index_name='PRIMARY'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id' AND MAX(non_unique)=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid service archive attachment primary key';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_student_service_archive_attachment' AND index_name='idx_service'
  GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='tenant_id,service_relation_id,field_key') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 invalid service archive attachment index';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission LIKE 'zsjos:exam-collection:%' GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:query' AND
 (deleted<>0 OR type<>2 OR parent_id<>root_id OR path<>'exam-material-collection' OR component<>'zsjos/examCollection/index'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE parent_id=root_id AND path='exam-material-collection' AND deleted=0 AND permission<>'zsjos:exam-collection:query') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '考试资料收集','zsjos:exam-collection:query',2,44,root_id,'exam-material-collection','ep:document','zsjos/examCollection/index','native',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:query');
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:exam-collection:query';
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:create' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 create identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '发起收集任务','zsjos:exam-collection:create',3,1,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:create');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:notify' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 notify identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '通知班级','zsjos:exam-collection:notify',3,2,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:notify');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:resolve' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 resolve identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '处理匹配异常','zsjos:exam-collection:resolve',3,3,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:resolve');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:complete' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 complete identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '完成收集任务','zsjos:exam-collection:complete',3,4,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:complete');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:reopen' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 reopen identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '重新开启收集','zsjos:exam-collection:reopen',3,5,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:reopen');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:export' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 export identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '导出收集资料','zsjos:exam-collection:export',3,6,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:export');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:members' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 members identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '调整应收名单','zsjos:exam-collection:members',3,7,page_id,'',0,b'1',b'1',b'1','V362','V362',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:exam-collection:members');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:exam-collection:query','zsjos:exam-collection:create','zsjos:exam-collection:notify','zsjos:exam-collection:resolve','zsjos:exam-collection:complete','zsjos:exam-collection:reopen','zsjos:exam-collection:export','zsjos:exam-collection:members') AND deleted=0)<>8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V362 menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V362','考试资料收集与服务档案修订',SHA2('V362__exam_material_collection.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V362');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V362','考试资料收集与服务档案修订',SHA2('V362__exam_material_collection.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V362');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v362_apply();
DROP PROCEDURE zsjos_v362_apply;
