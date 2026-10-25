-- UTF-8. Independent archive sensitive-read/export metadata under student management.
-- Upgrade after V349; requires the V327 archive read/update buttons and both ledgers.
-- Scope: two system_menu button definitions and V350 ledger rows. No role assignments,
-- tenant packages, business data or existing registration permissions are modified.
-- Replay checks actual metadata even with existing version markers; preserves custom
-- names/status/order and historical checksums. Failed prerequisites/ledger writes roll back
-- all DML. Routine DDL is not transactional; rerun after correcting a partial failure.
-- Rollback: restore the matching application and configure permissions through System;
-- retain ledger history. Administrators explicitly grant the new permissions after upgrade.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v350_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v350_apply()
BEGIN
 DECLARE student_parent BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V350 requires exactly one student page';
 END IF;
 SELECT id INTO student_parent FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V327')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V327')
 OR (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:student-archive:read','zsjos:student-archive:update')
     AND type=3 AND parent_id=student_parent AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V350 requires V327 archive permissions';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-archive:sensitive-read')>1
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-archive:export')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:student-archive:sensitive-read','zsjos:student-archive:export')
     AND (type<>3 OR parent_id<>student_parent OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V350 archive permission identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '查看学员档案敏感资料及附件','zsjos:student-archive:sensitive-read',3,50,student_parent,'','','',NULL,
  0,b'1',b'1',b'0','V350','V350',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-archive:sensitive-read');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '导出学员档案','zsjos:student-archive:export',3,51,student_parent,'','','',NULL,
  0,b'1',b'1',b'0','V350','V350',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-archive:export');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:student-archive:sensitive-read','zsjos:student-archive:export')
     AND type=3 AND parent_id=student_parent AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V350 archive permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V350','学员档案独立敏感读取与导出权限',SHA2('V350__student_archive_read_permissions.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V350');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V350','学员档案独立敏感读取与导出权限',SHA2('V350__student_archive_read_permissions.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V350');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v350_apply();
DROP PROCEDURE zsjos_v350_apply;
