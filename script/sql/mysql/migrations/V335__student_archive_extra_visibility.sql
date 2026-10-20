-- UTF-8. Tenant-level field visibility for the exam and certificate archive.
-- Prerequisites: V332 tables and tenant=1 runtime; normal numeric migration order.
-- Scope: one configuration row per tenant, no role grants and no archive data rewrite.
-- Repeatability: create/check structure and ledger rows only; DDL may commit implicitly.
-- Rollback: restore visibility values through the settings endpoint; table history is retained.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v335_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v335_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V332')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V332') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V335 requires V332';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_archive_extra_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  revision INT NOT NULL DEFAULT 0,
  visibility_json MEDIUMTEXT NOT NULL COMMENT '考试字段教务可见性配置',
  tenant_id BIGINT NOT NULL DEFAULT 1,
  creator VARCHAR(64) NOT NULL DEFAULT '', updater VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_tenant (tenant_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员考试字段可见性配置';
 INSERT INTO zsjos_student_archive_extra_config(tenant_id,revision,visibility_json,creator,updater)
 SELECT 1,0,'{"channel":true,"registration_fee":true,"admission_ticket":true,"theory":true,"skill":true,"thesis":true,"assessment_result":true,"issuing_authority":true,"certificate_images":true,"certificate_shipping_address":true,"certificate_tracking_no":true}','migration-V335','migration-V335'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_student_archive_extra_config WHERE tenant_id=1);
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_config'
 AND column_name IN ('id','revision','visibility_json','tenant_id','creator','updater','create_time','update_time','deleted') AND is_nullable='NO')<>9
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_config' AND index_name='uk_tenant' AND non_unique=0)<>1
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_archive_extra_config' AND column_name='visibility_json' AND data_type='mediumtext')<>1
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V335 structure postcondition failed'; END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V335','学员考试字段教务可见性',SHA2('V335__student_archive_extra_visibility.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V335');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V335','学员考试字段教务可见性',SHA2('V335__student_archive_extra_visibility.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V335');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v335_apply();
DROP PROCEDURE zsjos_v335_apply;
