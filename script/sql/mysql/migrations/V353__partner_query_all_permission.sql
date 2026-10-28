-- UTF-8. V353: Partner read-all button metadata only.
-- Apply after V352 in normal Core order, before the matching backend.
-- Prerequisites: exactly one active Partner page and both schema ledgers.
-- Scope: one system_menu button plus V353 in both ledgers; no role grants,
-- tenant packages or business rows. System menus are global metadata, not tenant rows.
-- Replay preserves administrator labels/status/order and existing checksums.
-- Partial metadata is repaired even if a ledger marker exists; failures roll back DML.
-- Rollback: disable the button in System menu management; retain ledger history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v353_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v353_apply()
BEGIN
 DECLARE partner_parent_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE component='zsjos/partner/index' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V353 requires exactly one Partner page';
 END IF;
 SELECT id INTO partner_parent_id FROM system_menu
 WHERE component='zsjos/partner/index' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:partner:query-all')>1
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:partner:query-all'
  AND (type<>3 OR parent_id<>partner_parent_id OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V353 query-all menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '查看全部兼职','zsjos:partner:query-all',3,99,partner_parent_id,'','','',NULL,
  0,b'1',b'1',b'0','V353',NOW(),'V353',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:partner:query-all');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:partner:query-all'
  AND type=3 AND parent_id=partner_parent_id AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V353 query-all menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V353','兼职管理查看全部权限',SHA2('V353__partner_query_all_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V353');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V353','兼职管理查看全部权限',SHA2('V353__partner_query_all_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V353');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v353_apply();
DROP PROCEDURE zsjos_v353_apply;
