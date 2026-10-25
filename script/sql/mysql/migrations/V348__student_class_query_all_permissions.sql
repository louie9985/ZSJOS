-- UTF-8. V348: class/student query-all button metadata; no role grants or business data.
-- Prerequisites: exactly one existing class-management page and one student-management page,
-- plus both schema ledgers. Apply in normal Core order after V347, before the matching backend.
-- Scope: two missing system_menu rows and the V348 record in each ledger; no tenant-package writes.
-- Replay preserves administrator labels/status/order and existing ledger checksums. Existing
-- version markers never skip metadata checks, so partially applied metadata can be recovered.
-- Rollback: disable the buttons through System menu management; retain metadata and ledger history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v348_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v348_apply()
BEGIN
 DECLARE class_parent_id bigint;
 DECLARE student_parent_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0)<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V348 requires one class page and one student page';
 END IF;
 SELECT id INTO class_parent_id FROM system_menu
 WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0;
 SELECT id INTO student_parent_id FROM system_menu
 WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:query-all')>1
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-all')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:delivery-class:query-all'
  AND (type<>3 OR parent_id<>class_parent_id OR deleted<>0))
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student:query-all'
  AND (type<>3 OR parent_id<>student_parent_id OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V348 query-all menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '查看全部班级','zsjos:delivery-class:query-all',3,99,class_parent_id,'','','',NULL,
  0,b'1',b'1',b'0','V348',NOW(),'V348',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:delivery-class:query-all');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '查看全部学员','zsjos:student:query-all',3,99,student_parent_id,'','','',NULL,
  0,b'1',b'1',b'0','V348',NOW(),'V348',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student:query-all');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:query-all'
  AND type=3 AND parent_id=class_parent_id AND deleted=0)<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-all'
  AND type=3 AND parent_id=student_parent_id AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V348 query-all menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V348','班级和学员查看全部权限',SHA2('V348__student_class_query_all_permissions.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V348');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V348','班级和学员查看全部权限',SHA2('V348__student_class_query_all_permissions.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V348');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v348_apply();
DROP PROCEDURE zsjos_v348_apply;
