-- V233: Material approval entry for the React employee workbench.
-- UTF-8. Scope: three System menus only; no business rows or role grants.
-- Prerequisites: V194 material-library directory and version registries; run after V232.
-- Repeatable by permission. Local development and future fresh/upgrade initialization.
-- Rollback: revoke these menu grants through System, then disable these menus;
-- retain BPM tasks and material versions. No schema or historical snapshot changes.
SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE zsjos_V233_apply()
BEGIN
  DECLARE directory_id BIGINT;
  DECLARE page_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  SELECT id INTO directory_id FROM system_menu
    WHERE id=80010 AND path='material-library' AND deleted=b'0';
  IF directory_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V233 requires V194 material library directory'; END IF;
  START TRANSACTION;
  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '素材审批','zsjos:material-approval:query',2,6,directory_id,'approvals','ep:finished','zsjos-workbench','MaterialApprovalPage','native',0,b'1',b'1',b'1','V233','V233',b'0'
    WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:material-approval:query' AND deleted=b'0');
  SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:material-approval:query' AND deleted=b'0';
  IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=page_id AND parent_id=directory_id AND path='approvals' AND workbench_render_mode='native') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V233 material approval menu conflicts with existing configuration';
  END IF;
  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '通过素材审批','zsjos:material-approval:approve',3,1,page_id,'',0,b'1',b'1',b'1','V233','V233',b'0'
    WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:material-approval:approve' AND deleted=b'0');
  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '驳回素材审批','zsjos:material-approval:reject',3,2,page_id,'',0,b'1',b'1',b'1','V233','V233',b'0'
    WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:material-approval:reject' AND deleted=b'0');
  INSERT INTO zsjos_schema_version (version,description,checksum,installed_at)
    VALUES ('V233','Material business approval menus',SHA2('V233__material_approval_menu.sql',256),NOW())
    ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
  INSERT INTO zsjos_module_schema_version (module_code,version,description,checksum,release_version,installed_at)
    VALUES ('core','V233','Material business approval menus',SHA2('V233__material_approval_menu.sql',256),'baseline',NOW())
    ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_V233_apply();
DROP PROCEDURE zsjos_V233_apply;
