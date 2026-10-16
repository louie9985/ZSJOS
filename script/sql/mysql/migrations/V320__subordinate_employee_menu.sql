-- UTF-8. V320: rename the shared employee supervision menu; metadata only.
-- Prerequisites: the V036 menu and both schema ledgers. No role grants or business data.
-- Repeatable: update only the canonical page; failure cannot register success.
-- Rollback: restore the page label through System menu management; grants remain unchanged.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v320_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v320_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=6814 AND deleted=0
  AND permission='zsjos:subordinate-sales:query' AND path='subordinate-sales'
  AND component='zsjos/subordinateSales/index') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V320 requires canonical subordinate employee menu';
 END IF;
 UPDATE system_menu SET name='下属员工', updater='V320', update_time=NOW()
 WHERE id=6814 AND deleted=0 AND name<>'下属员工';
 IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=6814 AND deleted=0 AND name='下属员工') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V320 menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V320','下属员工菜单名称',SHA2('V320__subordinate_employee_menu.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V320');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V320','下属员工菜单名称',SHA2('V320__subordinate_employee_menu.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V320');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v320_apply();
DROP PROCEDURE zsjos_v320_apply;
