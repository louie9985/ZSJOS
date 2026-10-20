-- UTF-8. Migration-Owner: ai
-- Scope: rename only the canonical subordinate-sales page after V320/V326 domain separation.
-- Prerequisites: System menu and both schema ledgers; normal numeric core migration order.
-- Repeatable, including a renamed page with incomplete ledgers. No grants or business data change.
-- Recovery: transaction rollback on failure; rerun after restoring prerequisites.
-- Rollback: restore the display label through System menu management; do not rewrite history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v334_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v334_apply()
BEGIN
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:query' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V334 requires exactly one subordinate-sales page';
 END IF;
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:subordinate-sales:query' AND deleted=0;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=page_id AND type=2 AND path IN ('subordinate-sales','/zsjos/subordinate-sales') AND component='zsjos/subordinateSales/index') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V334 subordinate-sales route metadata conflict';
 END IF;
 UPDATE system_menu SET name='下属销售',updater='V334',update_time=NOW() WHERE id=page_id AND name<>'下属销售';
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=page_id AND name='下属销售') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V334 name postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V334','恢复下属销售菜单名称',SHA2('V334__restore_subordinate_sales_menu.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V334');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V334','恢复下属销售菜单名称',SHA2('V334__restore_subordinate_sales_menu.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V334');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v334_apply();
DROP PROCEDURE zsjos_v334_apply;
