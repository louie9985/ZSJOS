-- UTF-8. Requires Core V372. Scope: only active media-lead-target query page name.
-- Preserves permission identifiers, paths and all role-menu grants. Repeat execution is safe.
-- Transactional metadata update; rollback name only with explicit administrator instruction.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v373_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v373_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V372') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V372') THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V373 requires Core V372'; END IF;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-lead-target:query' AND deleted=b'0') <> 1 THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V373 requires one media target page'; END IF;
 UPDATE system_menu SET name='新媒体业绩设置' WHERE permission='zsjos:media-lead-target:query' AND deleted=b'0';
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:media-lead-target:query' AND deleted=b'0' AND BINARY name=BINARY '新媒体业绩设置') THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V373 menu name mismatch'; END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V373','新媒体业绩设置菜单名称',SHA2('V373__media_performance_menu_name.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V373');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V373','新媒体业绩设置菜单名称',SHA2('V373__media_performance_menu_name.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V373');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v373_apply();
DROP PROCEDURE zsjos_v373_apply;
