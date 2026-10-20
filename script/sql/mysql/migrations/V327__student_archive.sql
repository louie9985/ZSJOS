-- UTF-8. Student archive revisions and separate read/update permission metadata.
-- Requires deployed V323/V324 collection schema. Does not rewrite submissions, grants or configuration.
-- Repeatable DDL recovery checks actual structure before ledger writes. MySQL DDL is not atomic.
-- Rollback: retain revisions and images; application rollback alone cannot remove maintained data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v327_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v327_apply()
BEGIN
 DECLARE parent_menu BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V324')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V324')
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V327 requires collection V324';
 END IF;
 SELECT MIN(id) INTO parent_menu FROM system_menu WHERE permission='zsjos:student:query-my' AND deleted=0;
 IF parent_menu IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V327 requires configured student page menu'; END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_archive_revision (
  id BIGINT NOT NULL AUTO_INCREMENT, form_id BIGINT NOT NULL, person_id BIGINT NOT NULL,
  revision INT NOT NULL, operator_user_id BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL,
  answers_json MEDIUMTEXT NOT NULL COMMENT '加密档案答案及字典地区快照', image_ids_json TEXT NOT NULL,
  tenant_id BIGINT NOT NULL DEFAULT 1, creator VARCHAR(64) NOT NULL DEFAULT '', updater VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY(id), UNIQUE KEY uk_form_revision(tenant_id,form_id,revision), UNIQUE KEY uk_archive_command(tenant_id,idempotency_key),
  KEY idx_person(tenant_id,person_id,form_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员档案维护记录';
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='archive_only') THEN
  ALTER TABLE zsjos_student_info_image ADD COLUMN archive_only BIT(1) NOT NULL DEFAULT b'0' COMMENT '仅档案维护使用，不改变原提交';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_revision' AND column_name IN ('form_id','person_id','revision','operator_user_id','reason','idempotency_key','request_hash','answers_json','image_ids_json','tenant_id'))<>10
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='archive_only' AND data_type='bit' AND is_nullable='NO')
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_revision' AND index_name='uk_form_revision' AND non_unique=0)<>3
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_revision' AND index_name='uk_archive_command' AND non_unique=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V327 structure postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '查看学员档案','zsjos:student-archive:read',3,48,parent_menu,'','','',NULL,0,b'1',b'1',b'0','migration-V327','migration-V327',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-archive:read');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '维护学员档案','zsjos:student-archive:update',3,49,parent_menu,'','','',NULL,0,b'1',b'1',b'0','migration-V327','migration-V327',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-archive:update');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-archive:read' AND deleted=0)<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-archive:update' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V327 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V327','学员档案维护与权限',SHA2('V327__student_archive.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V327');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V327','学员档案维护与权限',SHA2('V327__student_archive.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V327');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v327_apply();
DROP PROCEDURE zsjos_v327_apply;
