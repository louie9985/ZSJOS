-- UTF-8. V323 registration-scoped student information collection.
-- Requires V322 and zero legacy collection instances; no business rows, dictionary options or role grants seeded.
-- Additive, repeatable recovery; retain schema/data on application rollback. DDL implicitly commits.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v323_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v323_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V322') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V322') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 requires V322 in both ledgers';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_student_info_form_value v JOIN zsjos_student_info_form f ON f.id=v.form_id WHERE f.lead_id IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 legacy answers exist; review migration before proceeding';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='registration_case_id') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN registration_case_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='order_item_id') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN order_item_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='person_id') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN person_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='product_id') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN product_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='sent_by_user_id') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN sent_by_user_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='sent_at') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN sent_at DATETIME NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='fields_snapshot') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN fields_snapshot LONGTEXT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='category_path_snapshot') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN category_path_snapshot TEXT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='product_name') THEN
  ALTER TABLE zsjos_student_info_form ADD COLUMN product_name VARCHAR(255) NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form_config' AND column_name='education_category_ids_json') THEN
  ALTER TABLE zsjos_student_info_form_config ADD COLUMN education_category_ids_json TEXT NULL;
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_student_info_form WHERE registration_case_id IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 legacy forms exist; preserve and review';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_student_info_form_config WHERE JSON_LENGTH(fields_json)<>30) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 legacy configuration exists; explicit conversion required';
 END IF;
 ALTER TABLE zsjos_student_info_form MODIFY COLUMN lead_id BIGINT NULL;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND index_name='uk_student_info_case') THEN
  ALTER TABLE zsjos_student_info_form ADD UNIQUE KEY uk_student_info_case(tenant_id,registration_case_id,deleted);
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_info_image (
  id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL,
  form_id BIGINT NOT NULL, field_key VARCHAR(64) NOT NULL, side VARCHAR(8) NOT NULL DEFAULT '',
  infra_file_id BIGINT NOT NULL, content_type VARCHAR(32) NOT NULL, file_size BIGINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY(id), KEY idx_student_info_image_form(tenant_id,form_id,deleted)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员收集表图片关联';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name IN ('registration_case_id','order_item_id','person_id','product_id','sent_by_user_id','sent_at','fields_snapshot','category_path_snapshot','product_name'))<>9
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name IN ('form_id','field_key','side','infra_file_id','content_type','file_size'))<>6
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='lead_id' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-info-form:sent' AND deleted=0) THEN
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
  SELECT '确认已发送收集表','zsjos:student-info-form:sent',3,47,id,'','','',NULL,0,b'1',b'1',b'0','migration-V323','migration-V323',b'0'
  FROM system_menu WHERE permission='zsjos:registration:query-pool' AND deleted=0 LIMIT 1;
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-info-form:sent' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 requires registration pool menu';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND
   ((column_name IN ('registration_case_id','order_item_id','person_id','product_id','sent_by_user_id') AND data_type='bigint' AND is_nullable='YES')
    OR (column_name='sent_at' AND data_type='datetime' AND is_nullable='YES')
    OR (column_name='fields_snapshot' AND data_type='longtext')
    OR (column_name='category_path_snapshot' AND data_type='text')
    OR (column_name='product_name' AND data_type='varchar' AND character_maximum_length=255)))<>9
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND index_name='uk_student_info_case' AND non_unique=0)<>'tenant_id,registration_case_id,deleted'
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form_config' AND column_name='education_category_ids_json' AND data_type='text')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 existing column or unique index contract conflict';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND
   ((column_name IN ('id','tenant_id','form_id','infra_file_id','file_size') AND data_type='bigint' AND is_nullable='NO')
    OR (column_name='field_key' AND data_type='varchar' AND character_maximum_length=64)
    OR (column_name='side' AND data_type='varchar' AND character_maximum_length=8)
    OR (column_name='content_type' AND data_type='varchar' AND character_maximum_length=32)))<>8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V323 image table contract conflict';
 END IF;
 -- Move only stable permission metadata; administrators retain all existing role assignments.
 UPDATE system_menu m JOIN (SELECT MIN(id) id FROM system_menu WHERE permission='zsjos:registration:query-pool' AND deleted=0) p
 SET m.parent_id=p.id WHERE p.id IS NOT NULL AND m.deleted=0 AND m.type=3
 AND m.permission IN ('zsjos:student-info-form:create','zsjos:student-info-form:link-read','zsjos:student-info-form:regenerate','zsjos:student-info-form:revoke','zsjos:student-info-form:read','zsjos:student-info-form:sensitive-read','zsjos:student-info-form:export');
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V323','报名履约学员信息收集表',SHA2('V323__registration_student_information.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V323');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V323','报名履约学员信息收集表',SHA2('V323__registration_student_information.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v323_apply();
DROP PROCEDURE zsjos_v323_apply;

