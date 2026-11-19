-- UTF-8. After V378, before deploying the category-aware application. Tenant 1 only.
-- No business rows/grants are changed. Category table id = leaf category id.
-- Rerunnable after partial DDL; rollback application before removing any unused columns.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v380_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v380_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V378') OR
    NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V378') OR
    NOT EXISTS (SELECT 1 FROM zsjos_enrollment_agreement WHERE tenant_id=1 AND id=1 AND deleted=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V380 requires V378 agreement starter'; END IF;
 CREATE TABLE IF NOT EXISTS zsjos_enrollment_category_agreement LIKE zsjos_enrollment_agreement;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_category_name') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_category_name VARCHAR(255) NULL;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_category_name' AND data_type='varchar' AND character_maximum_length=255 AND is_nullable='YES')
 OR EXISTS (SELECT 1 FROM information_schema.columns src LEFT JOIN information_schema.columns dst ON dst.table_schema=src.table_schema AND dst.table_name='zsjos_enrollment_category_agreement' AND dst.column_name=src.column_name AND dst.column_type=src.column_type AND dst.is_nullable=src.is_nullable AND dst.column_key=src.column_key WHERE src.table_schema=DATABASE() AND src.table_name='zsjos_enrollment_agreement' AND dst.column_name IS NULL) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V380 agreement schema mismatch'; END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V380','末级分类报名协议',SHA2('V380__enrollment_category_agreement.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V380');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V380','末级分类报名协议',SHA2('V380__enrollment_category_agreement.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V380');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v380_apply();
DROP PROCEDURE zsjos_v380_apply;
