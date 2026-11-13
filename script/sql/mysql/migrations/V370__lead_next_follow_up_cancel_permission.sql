-- Scope: add one button under lead management; preserve generic task cancellation and all role grants.
-- UTF-8. V369 -> V370 -> matching Workbench/backend. Global menu metadata only.
-- No business data, dictionary entries or role assignments are changed.
-- Repeatable; preserve administrator labels/status/order. Repair missing metadata even with markers.
-- All DML and both ledger writes are atomic; conflicting identities fail closed.
-- Rollback: disable buttons through menu management; retain version history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v370_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v370_apply()
BEGIN
 DECLARE parent bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V369')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V369') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V370 requires V369 in both Core ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V370 requires unique lead management page';
 END IF;
 SELECT id INTO parent FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=0;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:lead-follow-up:cancel-next-time') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:lead-follow-up:cancel-next-time') AND (type<>3 OR parent_id<>parent OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V370 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '取消下次跟进','zsjos:lead-follow-up:cancel-next-time',3,10,parent,'','','',0,b'1',b'1',b'1','V370',NOW(),'V370',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:lead-follow-up:cancel-next-time');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:lead-follow-up:cancel-next-time') AND type=3 AND parent_id=parent AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V370 postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V370','客资下次跟进取消权限',SHA2('V370__lead_next_follow_up_cancel_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V370');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V370','客资下次跟进取消权限',SHA2('V370__lead_next_follow_up_cancel_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V370');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v370_apply();
DROP PROCEDURE zsjos_v370_apply;
