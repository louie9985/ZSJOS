-- UTF-8. Migration-Owner: ai
-- Scope: one configurable delete-draft button under the canonical material page (V194).
-- Order: normal numeric core upgrade before matching backend/Workbench deployment.
-- No business rows, dictionaries or role assignments change. Repeatable, including a
-- previously inserted button with missing ledgers. Conflicting metadata fails without success records.
-- Rollback: disable the button in System menu management; preserve audit/history and ledgers.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v333_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v333_apply()
BEGIN
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:material:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V333 requires canonical material page';
 END IF;
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:material:query' AND type=2 AND deleted=0;
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:material:delete-draft'
  AND (parent_id<>page_id OR type<>3 OR deleted<>0))
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:material:delete-draft')>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V333 delete-draft metadata conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,
  workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '删除素材草稿','zsjos:material:delete-draft',3,20,page_id,'','','',
  'native',0,1,1,0,'V333','V333',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:material:delete-draft');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:material:delete-draft' AND parent_id=page_id AND type=3 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V333 delete-draft postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V333','素材草稿删除权限',SHA2('V333__material_draft_delete_permission.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V333');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V333','素材草稿删除权限',SHA2('V333__material_draft_delete_permission.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V333');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v333_apply();
DROP PROCEDURE zsjos_v333_apply;
